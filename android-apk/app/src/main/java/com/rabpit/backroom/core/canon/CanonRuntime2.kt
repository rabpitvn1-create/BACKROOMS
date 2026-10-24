package com.rabpit.backroom.core.canon

import android.content.Context
import android.util.Log

/**
 * Kết quả một lượt chuẩn bị ngữ cảnh CR2 cho phía gọi (MainActivity):
 * - [Ready]: packet sẵn sàng đưa vào prompt.
 * - [SafeLocalResolution]: CR2 chạy được nhưng không đủ điều kiện an toàn để
 *   gọi GM (thiếu canon cứng / ngân sách không thỏa) — phía gọi trả phản hồi
 *   hạn chế an toàn, KHÔNG commit state, KHÔNG quay về engine cũ để đoán tiếp.
 * - [FallbackToLegacy]: lỗi hạ tầng của chính CR2 (flag tắt, store không nạp
 *   được, lỗi nội bộ) — phía gọi dùng đường cũ cho lượt đó.
 */
sealed class CanonOutcome {
  data class Ready(val packet: String) : CanonOutcome()
  data class SafeLocalResolution(val reason: String, val missingIds: List<String> = emptyList()) : CanonOutcome()
  data class FallbackToLegacy(val reason: String) : CanonOutcome()
}

/**
 * Mặt tiền Canon Runtime 2.0 — giữ đúng hình dạng đầu vào của
 * `KnowledgeContextEngine.build(context, stateJson, action, rollsJson)`
 * để điểm gọi thay thế nhau 1-1.
 *
 * Điều phối: SceneResolver → CanonResolver → ContextCompiler.
 * Mọi ngoại lệ nội bộ bị nuốt thành outcome an toàn — CR2 không được làm sập lượt.
 * Phân tầng lỗi (mapping §4):
 * - flag OFF → [CanonOutcome.FallbackToLegacy] (đường cũ nguyên vẹn);
 * - store/pack lỗi → [CanonOutcome.FallbackToLegacy] cho lượt đó;
 * - thiếu canon cứng / ngân sách không thỏa → [CanonOutcome.SafeLocalResolution].
 *
 * Admission quota (meta/core-only → 0 lần gọi GM) KHÔNG nằm ở facade này;
 * nó thuộc QuotaGovernor tại điểm gọi provider.
 */
object CanonRuntime2 {
  private const val TAG = "CanonRuntime2"

  // Bundled asset is immutable for the life of this APK. Build the exact-ID,
  // alias, tag, affordance and edge indexes once, not on every GM turn.
  private val storeLock = Any()
  @Volatile private var indexedStore: JsonCanonStore? = null

  private fun storeFor(context: Context): JsonCanonStore =
    indexedStore ?: synchronized(storeLock) {
      indexedStore ?: JsonCanonStore.fromAssets(context.applicationContext).also {
        indexedStore = it
      }
    }

  private val DEFAULT_PROFILE = ProviderProfile(
    kind = ContextProfile.STANDARD,
    providerId = "gemini"
  )

  // usable = min(1_048_576 − 2_048 − 512, 250_000, 6_000) = 6_000 token ước lượng.
  private val DEFAULT_BUDGET = ContextBudget(
    providerWindowTokens = 1_048_576,
    outputReserveTokens = 2_048,
    safetyMarginTokens = 512,
    tpmAvailableTokens = 250_000,
    configuredMaxInputTokens = 6_000
  )

  @JvmStatic
  fun buildPacket(context: Context, stateJson: String, action: String, rollsJson: String): CanonOutcome =
    buildPacket(context, stateJson, action, rollsJson, isMeta = false)

  @JvmStatic
  fun buildPacket(
    context: Context,
    stateJson: String,
    action: String,
    rollsJson: String,
    isMeta: Boolean
  ): CanonOutcome {
    if (!CanonRuntime2Flags.isEnabled(context)) {
      return CanonOutcome.FallbackToLegacy(REASON_DISABLED)
    }
    val store = try {
      storeFor(context)
    } catch (e: Exception) {
      return CanonOutcome.FallbackToLegacy("$REASON_STORE_UNAVAILABLE:${e.javaClass.simpleName}")
    }
    return try {
      val snapshot = store.snapshot()
      val scene = LegacyJsonSceneResolver().resolve(stateJson, action, rollsJson, isMeta)
      val selection = CanonResolver(store).resolve(scene, snapshot)
      when (val result = ContextCompiler().compile(scene, selection, DEFAULT_PROFILE, DEFAULT_BUDGET)) {
        is ContextPacketResult.Ready -> {
          Log.d(TAG, traceOf(scene, snapshot, selection, result).toLogLine())
          CanonOutcome.Ready(result.packet)
        }
        is ContextPacketResult.MissingRequiredCanon ->
          CanonOutcome.SafeLocalResolution(REASON_MISSING_REQUIRED, result.missingIds)
        is ContextPacketResult.BudgetUnsatisfiable ->
          CanonOutcome.SafeLocalResolution(
            "$REASON_BUDGET_UNSATISFIABLE:required=${result.requiredTokens},usable=${result.usableTokens}",
            emptyList()
          )
        is ContextPacketResult.PackError ->
          CanonOutcome.FallbackToLegacy("$REASON_PACK_ERROR:${result.message}")
      }
    } catch (e: Exception) {
      CanonOutcome.FallbackToLegacy("$REASON_INTERNAL_ERROR:${e.javaClass.simpleName}")
    }
  }

  private fun traceOf(
    scene: SceneSnapshot,
    snapshot: CanonSnapshot,
    selection: CanonSelection,
    result: ContextPacketResult.Ready
  ): CanonTurnTrace = CanonTurnTrace(
    turnId = scene.turnId,
    packVersion = snapshot.packId,
    stateRevision = scene.gameStateRevision,
    presentEntityIds = scene.entityEncounters.map { it.entityId },
    retrieved = selection.trace.map { RetrievedRecordTrace(it.recordId, it.gate, it.reason) },
    requiredMissing = selection.missingRequiredIds,
    estimatedTokens = result.estimatedTokens,
    profile = DEFAULT_PROFILE.kind.name,
    providerModel = DEFAULT_PROFILE.model,
    quotaDecision = "not_evaluated_in_facade"
  )

  private const val REASON_DISABLED = "cr2_disabled"
  private const val REASON_STORE_UNAVAILABLE = "canon_store_unavailable"
  private const val REASON_MISSING_REQUIRED = "MISSING_REQUIRED_CANON"
  private const val REASON_BUDGET_UNSATISFIABLE = "BUDGET_UNSATISFIABLE"
  private const val REASON_PACK_ERROR = "pack_error"
  private const val REASON_INTERNAL_ERROR = "cr2_internal_error"
}
