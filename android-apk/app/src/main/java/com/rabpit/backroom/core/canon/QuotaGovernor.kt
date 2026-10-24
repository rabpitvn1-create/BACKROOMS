package com.rabpit.backroom.core.canon

import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/**
 * Đồng hồ injectable để unit test máy trạng thái quota không cần ngủ thật.
 */
fun interface Clock {
  fun nowMs(): Long
}

enum class GenerationKind { META_QUERY, CORE_ONLY, NARRATION }

enum class QuotaState { READY, COOLDOWN, EXHAUSTED_UNTIL_RESET, AUTH_BLOCKED, MODEL_BLOCKED, CIRCUIT_OPEN }

data class GenerationRequest(
  val providerId: String,          // vd "gemini"
  val projectId: String?,          // null nếu không xác định được — ngân sách tính theo provider+model
  val model: String,
  val kind: GenerationKind,
  val estimatedInputTokens: Int,
  val deadlineMs: Long
)

/**
 * Quyết định admission (§9.2):
 * - [NoCall]: hành động meta/core-only — Core trả lời chính xác được, 0 lần gọi GM.
 * - [SingleCall]: cần prose — cho phép đúng 1 lần gọi, đã reserve request + token.
 * - [Defer]: provider đang cooldown/circuit-open nhưng sẽ mở lại trong deadline của lượt.
 * - [LocalDeterministic]: không dùng cloud cho lượt này (hết quota ngày / auth / model /
 *   cooldown dài hơn deadline) — phía gọi trả kết quả deterministic/an toàn, không bịa prose.
 */
sealed class AdmissionDecision {
  data class NoCall(val reason: String) : AdmissionDecision()
  data class SingleCall(val reservationId: String) : AdmissionDecision()
  data class Defer(val untilEpochMs: Long, val state: QuotaState, val reason: String) : AdmissionDecision()
  data class LocalDeterministic(val state: QuotaState, val reason: String) : AdmissionDecision()
}

data class ProviderAttemptResult(
  val providerId: String,
  val model: String,
  val httpCode: Int?,              // null = lỗi transport/timeout
  val retryAfterMs: Long?,         // từ header Retry-After nếu đọc được, else null
  val observedInputTokens: Int? = null,
  val observedOutputTokens: Int? = null,
  val dailyQuotaExhausted: Boolean = false,
  val projectId: String? = null,
  val reservationId: String? = null
)

/** Ảnh chụp trạng thái một khóa (provider, project, model) — phục vụ trace/test. */
data class QuotaBucketSnapshot(
  val state: QuotaState,
  val stateUntilEpochMs: Long,
  val outstandingReservations: Int,
  val reservedTokens: Int,
  val observedRequests: Int,
  val observedInputTokens: Int,
  val observedOutputTokens: Int
)

interface QuotaGovernor {
  fun admit(request: GenerationRequest): AdmissionDecision
  fun recordResult(result: ProviderAttemptResult)
}

/**
 * Admission control + máy trạng thái 429 theo §9.2/§9.3.
 *
 * Nguyên tắc:
 * - Khóa ngân sách là (providerId, projectId, model): quota tính theo PROJECT,
 *   không phải theo key; governor không quay vòng key để né quota.
 * - Meta/core-only → [AdmissionDecision.NoCall] trong MỌI trạng thái (§9.2).
 * - 429 transient: tôn trọng Retry-After; không có thì backoff mũ + jitter
 *   (base 5s, ×2, trần 5 phút) — KHÔNG mặc định 60s.
 * - 429 daily: [QuotaState.EXHAUSTED_UNTIL_RESET] tới nửa đêm Pacific
 *   (America/Los_Angeles, mốc reset RPD do Google công bố) — tính bằng
 *   TimeZone của provider, TUYỆT ĐỐI không hardcode UTC+7.
 * - 401/403 → AUTH_BLOCKED (không retry mù); 400/404 model/schema → MODEL_BLOCKED;
 *   transport/5xx lặp lại (≥3 liên tiếp) → CIRCUIT_OPEN, thời gian mở tăng dần.
 * - Reserve request + estimated tokens TRƯỚC khi gọi; recordResult luôn
 *   release đúng một reservation của khóa đó (kể cả lỗi transport/timeout)
 *   và reconcile theo usage quan sát được khi có.
 * - Trạng thái chỉ giữ in-memory trong tiến trình (đợt này không persist);
 *   restart app thì governor học lại từ 429 thật.
 */
class DefaultQuotaGovernor(
  private val clock: Clock = Clock { System.currentTimeMillis() },
  private val jitterMs: () -> Long = { Random.nextLong(0, JITTER_BOUND_MS + 1) }
) : QuotaGovernor {

  private data class Key(val providerId: String, val projectId: String, val model: String)

  private data class Reservation(val id: String, val estimatedInputTokens: Int)

  private class Bucket {
    var state: QuotaState = QuotaState.READY
    var stateUntilEpochMs: Long = 0L
    var consecutive429: Int = 0
    var consecutiveTransportFailures: Int = 0
    var circuitOpenCount: Int = 0
    val reservations = ArrayDeque<Reservation>()
    var reservedTokens: Int = 0
    var observedRequests: Int = 0
    var observedInputTokens: Int = 0
    var observedOutputTokens: Int = 0
  }

  private val lock = Any()
  private val buckets = HashMap<Key, Bucket>()
  private var reservationSeq: Long = 0L

  override fun admit(request: GenerationRequest): AdmissionDecision {
    // §9.2: việc Core trả lời chính xác được thì 0 lần gọi GM, bất kể trạng thái quota.
    if (request.kind != GenerationKind.NARRATION) {
      return AdmissionDecision.NoCall(REASON_CORE_ONLY)
    }
    synchronized(lock) {
      val now = clock.nowMs()
      val bucket = buckets.getOrPut(keyOf(request.providerId, request.projectId, request.model)) { Bucket() }
      refresh(bucket, now)
      return when (bucket.state) {
        QuotaState.READY -> {
          val reservation = Reservation(
            id = "rsv-${++reservationSeq}",
            estimatedInputTokens = request.estimatedInputTokens.coerceAtLeast(0)
          )
          bucket.reservations.addLast(reservation)
          bucket.reservedTokens += reservation.estimatedInputTokens
          AdmissionDecision.SingleCall(reservation.id)
        }
        QuotaState.COOLDOWN, QuotaState.CIRCUIT_OPEN ->
          if (bucket.stateUntilEpochMs - now <= request.deadlineMs) {
            AdmissionDecision.Defer(bucket.stateUntilEpochMs, bucket.state, "provider_unavailable_until_retry_window")
          } else {
            AdmissionDecision.LocalDeterministic(bucket.state, "provider_unavailable_beyond_turn_deadline")
          }
        QuotaState.EXHAUSTED_UNTIL_RESET ->
          AdmissionDecision.LocalDeterministic(bucket.state, "daily_quota_exhausted_until_provider_reset")
        QuotaState.AUTH_BLOCKED ->
          AdmissionDecision.LocalDeterministic(bucket.state, "provider_auth_blocked")
        QuotaState.MODEL_BLOCKED ->
          AdmissionDecision.LocalDeterministic(bucket.state, "provider_model_blocked")
      }
    }
  }

  override fun recordResult(result: ProviderAttemptResult) {
    synchronized(lock) {
      val now = clock.nowMs()
      for (bucket in targetBuckets(result)) {
        val released = releaseOneReservation(bucket, result.reservationId)
        val code = result.httpCode
        when {
          code != null && code in 200..299 -> {
            bucket.state = QuotaState.READY
            bucket.stateUntilEpochMs = 0L
            bucket.consecutive429 = 0
            bucket.consecutiveTransportFailures = 0
            bucket.circuitOpenCount = 0
            bucket.observedRequests += 1
            bucket.observedInputTokens += result.observedInputTokens ?: released?.estimatedInputTokens ?: 0
            bucket.observedOutputTokens += result.observedOutputTokens ?: 0
          }
          code == 429 -> {
            if (result.dailyQuotaExhausted) {
              bucket.state = QuotaState.EXHAUSTED_UNTIL_RESET
              bucket.stateUntilEpochMs = nextPacificMidnightMs(now)
            } else {
              val retryAfter = result.retryAfterMs?.takeIf { it > 0 }
              val backoff = if (retryAfter != null) {
                retryAfter
              } else {
                exponentialBackoffMs(bucket.consecutive429) + jitterMs().coerceAtLeast(0)
              }
              bucket.consecutive429 += 1
              bucket.state = QuotaState.COOLDOWN
              bucket.stateUntilEpochMs = now + backoff
            }
          }
          code == 401 || code == 403 -> {
            bucket.state = QuotaState.AUTH_BLOCKED
            bucket.stateUntilEpochMs = 0L
          }
          code == 400 || code == 404 -> {
            bucket.state = QuotaState.MODEL_BLOCKED
            bucket.stateUntilEpochMs = 0L
          }
          code == null || code == 408 || (code != null && code in 500..599) -> {
            bucket.consecutiveTransportFailures += 1
            if (bucket.consecutiveTransportFailures >= TRANSPORT_FAILURE_THRESHOLD) {
              val openMs = minOf(
                CIRCUIT_OPEN_BASE_MS shl minOf(bucket.circuitOpenCount, 4),
                CIRCUIT_OPEN_MAX_MS
              )
              bucket.circuitOpenCount += 1
              bucket.state = QuotaState.CIRCUIT_OPEN
              bucket.stateUntilEpochMs = now + openMs
            }
          }
          else -> Unit // 4xx khác: chỉ release reservation, không đổi trạng thái.
        }
      }
    }
  }

  /** Trạng thái hiện tại của một khóa sau khi áp các chuyển trạng thái theo thời gian. */
  fun snapshot(providerId: String, projectId: String?, model: String): QuotaBucketSnapshot {
    synchronized(lock) {
      val bucket = buckets[keyOf(providerId, projectId, model)] ?: return QuotaBucketSnapshot(
        state = QuotaState.READY, stateUntilEpochMs = 0L, outstandingReservations = 0,
        reservedTokens = 0, observedRequests = 0, observedInputTokens = 0, observedOutputTokens = 0
      )
      refresh(bucket, clock.nowMs())
      return QuotaBucketSnapshot(
        state = bucket.state,
        stateUntilEpochMs = bucket.stateUntilEpochMs,
        outstandingReservations = bucket.reservations.size,
        reservedTokens = bucket.reservedTokens,
        observedRequests = bucket.observedRequests,
        observedInputTokens = bucket.observedInputTokens,
        observedOutputTokens = bucket.observedOutputTokens
      )
    }
  }

  private fun keyOf(providerId: String, projectId: String?, model: String) =
    Key(providerId, projectId ?: "-", model)

  /** COOLDOWN/CIRCUIT_OPEN/EXHAUSTED hết hạn thì quay về READY (giữ bộ đếm để backoff tiếp tục leo thang nếu 429 quay lại). */
  private fun refresh(bucket: Bucket, now: Long) {
    when (bucket.state) {
      QuotaState.COOLDOWN, QuotaState.CIRCUIT_OPEN, QuotaState.EXHAUSTED_UNTIL_RESET -> {
        if (bucket.stateUntilEpochMs in 1..now) {
          bucket.state = QuotaState.READY
          bucket.stateUntilEpochMs = 0L
        }
      }
      else -> Unit
    }
  }

  private fun targetBuckets(result: ProviderAttemptResult): List<Bucket> {
    if (result.reservationId != null) {
      val owner = buckets.values.firstOrNull { bucket ->
        bucket.reservations.any { it.id == result.reservationId }
      }
      if (owner != null) return listOf(owner)
    }
    return buckets.entries
      .filter { (key, _) ->
        key.providerId == result.providerId && key.model == result.model &&
          (result.projectId == null || key.projectId == (result.projectId ?: "-"))
      }
      .map { it.value }
  }

  private fun releaseOneReservation(bucket: Bucket, reservationId: String?): Reservation? {
    val released = when {
      reservationId != null ->
        bucket.reservations.firstOrNull { it.id == reservationId }?.also { bucket.reservations.remove(it) }
      bucket.reservations.isNotEmpty() -> bucket.reservations.removeFirst()
      else -> null
    }
    if (released != null) {
      bucket.reservedTokens = (bucket.reservedTokens - released.estimatedInputTokens).coerceAtLeast(0)
    }
    return released
  }

  private fun exponentialBackoffMs(consecutive429: Int): Long =
    minOf(BACKOFF_BASE_MS shl minOf(consecutive429, 6), BACKOFF_MAX_MS)

  companion object {
    const val REASON_CORE_ONLY = "core_answers_exactly_no_provider_call"

    private const val BACKOFF_BASE_MS = 5_000L
    private const val BACKOFF_MAX_MS = 300_000L
    private const val JITTER_BOUND_MS = 1_000L
    private const val TRANSPORT_FAILURE_THRESHOLD = 3
    private const val CIRCUIT_OPEN_BASE_MS = 30_000L
    private const val CIRCUIT_OPEN_MAX_MS = 600_000L

    /**
     * Nửa đêm KẾ TIẾP theo giờ Pacific (America/Los_Angeles) — mốc reset RPD
     * của Gemini. Dùng TimeZone của provider, không hardcode UTC+7 của máy người dùng.
     */
    internal fun nextPacificMidnightMs(nowMs: Long): Long {
      val pacific = TimeZone.getTimeZone("America/Los_Angeles")
      val cal = Calendar.getInstance(pacific)
      cal.timeInMillis = nowMs
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      cal.add(Calendar.DAY_OF_YEAR, 1)
      return cal.timeInMillis
    }
  }
}
