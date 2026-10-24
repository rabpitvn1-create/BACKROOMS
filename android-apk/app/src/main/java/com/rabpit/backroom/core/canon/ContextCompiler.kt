package com.rabpit.backroom.core.canon

import kotlin.math.ceil

/**
 * Ước lượng token BẢO THỦ cho packet (ARCHITECTURE §7.2).
 *
 * Engine cũ dùng ceil(chars/4); tiếng Việt dấu nhiều ký tự nên mẫu số 3.5
 * bảo thủ hơn. Đây vẫn chỉ là estimator — ngân sách thật còn bị chặn bởi
 * safety margin trong [ContextBudget] và usage quan sát được từ provider.
 */
object CanonTokens {
  fun estimate(text: String): Int = ceil(text.length / 3.5).toInt().coerceAtLeast(1)
}

/** Mức hồ sơ biên soạn theo §7.2. OFFLINE dùng COMPACT, không tạo enum riêng. */
enum class ContextProfile { COMPACT, STANDARD, RICH }

/**
 * Hồ sơ provider/model cho một lượt biên soạn (§14 dùng kiểu này).
 * [kind] quyết định trần optional/continuity; providerId/model chỉ để trace.
 */
data class ProviderProfile(
  val kind: ContextProfile = ContextProfile.STANDARD,
  val providerId: String = "unknown",
  val model: String = ""
)

/**
 * Ngân sách input theo công thức §7.2:
 * usable = min(window − outputReserve − safetyMargin, tpmAvailable, configuredMax).
 * Mọi thành phần tính bằng token ƯỚC LƯỢNG của [CanonTokens].
 */
data class ContextBudget(
  val providerWindowTokens: Int,
  val outputReserveTokens: Int,
  val safetyMarginTokens: Int,
  val tpmAvailableTokens: Int = Int.MAX_VALUE,
  val configuredMaxInputTokens: Int
) {
  val usableInputTokens: Int
    get() = minOf(
      providerWindowTokens - outputReserveTokens - safetyMarginTokens,
      tpmAvailableTokens,
      configuredMaxInputTokens
    ).coerceAtLeast(0)
}

/**
 * Kết quả biên soạn — 4 trạng thái theo §14. Không bao giờ trả chuỗi rỗng
 * rồi bảo GM tự đoán.
 */
sealed class ContextPacketResult {
  data class Ready(
    val packet: String,
    val estimatedTokens: Int,
    val includedIds: List<String>,
    val droppedOptionalIds: List<String>
  ) : ContextPacketResult()

  data class MissingRequiredCanon(val missingIds: List<String>) : ContextPacketResult()
  data class BudgetUnsatisfiable(val requiredTokens: Int, val usableTokens: Int) : ContextPacketResult()
  data class PackError(val message: String) : ContextPacketResult()
}

/**
 * Trần theo profile — "ngưỡng gợi ý để đem đo" theo tinh thần §7.2,
 * KHÔNG phải tham số đã benchmark. Trần này chỉ chặn lớp OPTIONAL;
 * lớp required chỉ bị chặn bởi usableInputTokens của [ContextBudget].
 */
private object ProfileCaps {
  fun optionalTokenCap(kind: ContextProfile): Int = when (kind) {
    ContextProfile.COMPACT -> 400
    ContextProfile.STANDARD -> 1400
    ContextProfile.RICH -> Int.MAX_VALUE
  }

  /** Số fact continuity gần nhất được đưa vào packet (§7.2: COMPACT chỉ 1–2). */
  fun continuityFactCap(kind: ContextProfile): Int = when (kind) {
    ContextProfile.COMPACT -> 2
    ContextProfile.STANDARD -> 4
    ContextProfile.RICH -> 8
  }
}

/**
 * Biên soạn packet từ [CanonSelection] theo thứ tự lớp §7.1/§7.3.
 *
 * Bất biến:
 * - Record luôn được cộng NGUYÊN VẸN (append_only_whole_records). Không tồn
 *   tại cơ chế cắt cụt chuỗi trong file này; packet không bao giờ kết thúc
 *   bằng marker cắt cụt và không record nào bị cắt giữa chừng.
 * - Thiếu canon cứng ([CanonSelection.missingRequiredIds]) được báo TRƯỚC
 *   khi tính ngân sách — không "bù" bằng cách cắt optional.
 * - Required không vừa usable → thử lại bằng compactForm (author-approved)
 *   của record có sẵn; vẫn không vừa → [ContextPacketResult.BudgetUnsatisfiable].
 * - Định dạng giữ tương thích truy vết của engine cũ: header
 *   `[KNOWLEDGE_PACKET v1]` + footer `[END_KNOWLEDGE_PACKET]`, bên trong là
 *   các section §7.3: [ROLE] / [CURRENT_SCENE] / [CANON_REQUIRED] /
 *   [CONTINUITY_RELEVANT] / [OUTPUT]. Mỗi record một dòng `<ID> <text>`
 *   như engine cũ để benchmark/audit cũ còn đối chiếu được.
 *
 * Thuần Kotlin: không Context, không I/O — test trực tiếp bằng JUnit.
 */
class ContextCompiler {

  fun compile(
    scene: SceneSnapshot,
    selection: CanonSelection,
    profile: ProviderProfile,
    budget: ContextBudget
  ): ContextPacketResult {
    // 1) Thiếu canon cứng thắng mọi tính toán ngân sách (§7.2).
    if (selection.missingRequiredIds.isNotEmpty()) {
      return ContextPacketResult.MissingRequiredCanon(
        selection.missingRequiredIds.distinct().sorted()
      )
    }

    val usable = budget.usableInputTokens
    val facts = scene.knownFacts.toList().takeLast(ProfileCaps.continuityFactCap(profile.kind))
    val framingTokens = CanonTokens.estimate(framingText(scene, profile, facts))

    // 2) Required nguyên bản; không vừa mới dùng compactForm (§7.2:
    //    compact_presentation_without_dropping_constraints).
    var required = selection.required
    var requiredTotal = framingTokens + required.sumOf { costOf(it) }
    if (requiredTotal > usable) {
      val compacted = required.map { record ->
        val compact = record.compactForm
        if (compact != null && compact.isNotBlank()) {
          record.copy(text = compact, compactForm = null)
        } else {
          record
        }
      }
      val compactTotal = framingTokens + compacted.sumOf { costOf(it) }
      if (compactTotal < requiredTotal) {
        required = compacted
        requiredTotal = compactTotal
      }
    }
    if (requiredTotal > usable) {
      return ContextPacketResult.BudgetUnsatisfiable(
        requiredTokens = requiredTotal,
        usableTokens = usable
      )
    }

    // 3) Optional: theo đúng thứ tự deterministic của CanonSelection,
    //    cộng nguyên record khi còn vừa usable VÀ còn vừa trần profile.
    val optionalCap = ProfileCaps.optionalTokenCap(profile.kind)
    val requiredIds = required.mapTo(HashSet()) { it.id }
    val includedOptional = ArrayList<CanonRecord>()
    val droppedOptionalIds = ArrayList<String>()
    var optionalUsed = 0
    for (record in selection.optional) {
      if (record.id in requiredIds) continue // đã ở lớp required, không tính dropped
      val cost = costOf(record)
      if (requiredTotal + optionalUsed + cost <= usable && optionalUsed + cost <= optionalCap) {
        includedOptional += record
        optionalUsed += cost
      } else {
        droppedOptionalIds += record.id
      }
    }

    // 4) Đóng gói. Source/provenance đầy đủ KHÔNG vào packet (§7.2) —
    //    chúng ở SelectionTrace phía local.
    val packet = buildString {
      appendLine("[KNOWLEDGE_PACKET v1]")
      appendLine("Compiler=CanonRuntime2 profile=${profile.kind.name} usable=$usable")
      appendLine()
      appendLine("[ROLE]")
      appendLine(ROLE_TEXT)
      appendLine()
      appendLine("[CURRENT_SCENE]")
      append(sceneSection(scene))
      appendLine()
      appendLine("[CANON_REQUIRED]")
      for (record in required) appendLine("${record.id} ${record.text}")
      appendLine()
      appendLine("[CONTINUITY_RELEVANT]")
      if (facts.isNotEmpty()) appendLine("Known facts: ${facts.joinToString(", ")}")
      for (record in includedOptional) appendLine("${record.id} ${record.text}")
      appendLine()
      appendLine("[OUTPUT]")
      appendLine(OUTPUT_TEXT)
      append("[END_KNOWLEDGE_PACKET]")
    }

    return ContextPacketResult.Ready(
      packet = packet,
      estimatedTokens = CanonTokens.estimate(packet),
      includedIds = required.map { it.id } + includedOptional.map { it.id },
      droppedOptionalIds = droppedOptionalIds
    )
  }

  /**
   * Chi phí token của một record trong packet: lấy max giữa estimatedTokens
   * của record và ước lượng bảo thủ [CanonTokens] + 8 (hệ số overhead của
   * engine cũ), để tổng các phần không bao giờ thấp hơn ước lượng của
   * toàn packet.
   */
  private fun costOf(record: CanonRecord): Int =
    maxOf(record.estimatedTokens, CanonTokens.estimate(record.text) + 8)

  /** Phần khung cố định của packet (mọi thứ trừ thân record) để tính framing. */
  private fun framingText(
    scene: SceneSnapshot,
    profile: ProviderProfile,
    facts: List<String>
  ): String = buildString {
    appendLine("[KNOWLEDGE_PACKET v1]")
    appendLine("Compiler=CanonRuntime2 profile=${profile.kind.name} usable=0")
    appendLine("[ROLE]")
    appendLine(ROLE_TEXT)
    appendLine("[CURRENT_SCENE]")
    append(sceneSection(scene))
    appendLine("[CANON_REQUIRED]")
    appendLine("[CONTINUITY_RELEVANT]")
    if (facts.isNotEmpty()) appendLine("Known facts: ${facts.joinToString(", ")}")
    appendLine("[OUTPUT]")
    appendLine(OUTPUT_TEXT)
    append("[END_KNOWLEDGE_PACKET]")
  }

  /**
   * Lớp 1 + 3 của §7.1: kết quả Core đã khóa và scene facts.
   * "Confirmed" và "mentioned/rumored" tách rõ hai mục — mentioned ≠ present.
   */
  private fun sceneSection(scene: SceneSnapshot): String = buildString {
    appendLine("Turn=${scene.turnId} | Level=${scene.levelId} | POV=${scene.povActorId}")
    scene.locationNodeId?.let { appendLine("Location node=$it") }
    appendLine("Actors=${scene.actorsPresent.joinToString(", ")}")
    val confirmed = scene.entityEncounters
      .filter { it.status == EncounterStatus.CONFIRMED_PRESENT }
      .map { it.entityId }
    appendLine("Confirmed present=${if (confirmed.isEmpty()) "none" else confirmed.joinToString(", ")}")
    val unconfirmed = LinkedHashSet<String>().apply {
      addAll(scene.mentionedUnconfirmed)
      scene.entityEncounters
        .filter { it.status != EncounterStatus.CONFIRMED_PRESENT }
        .forEach { add(it.entityId) }
    }
    appendLine("Unconfirmed mention=${if (unconfirmed.isEmpty()) "none" else unconfirmed.joinToString(", ")}")
    appendLine("Player action=${scene.actionTextNormalized}")
    appendLine("Locked rolls=${if (scene.rollsLocked) "locked" else "none"}")
    if (scene.objectsInFocus.isNotEmpty()) {
      appendLine("Objects in focus=${scene.objectsInFocus.joinToString(", ")}")
    }
    if (scene.storyThreadsActive.isNotEmpty()) {
      appendLine("Open story threads=${scene.storyThreadsActive.joinToString(", ")}")
    }
  }

  private companion object {
    const val ROLE_TEXT =
      "Bạn là GM. Viết tiếp ngắn gọn, tự nhiên bằng tiếng Việt. " +
        "Không quyết định hành động có chủ ý thay người chơi. " +
        "Chỉ mô tả diễn biến được Core cho phép; không tự thêm loot/spawn/chuyển Level."

    const val OUTPUT_TEXT =
      "Trả JSON theo schema hiện hành; chỉ đề xuất ops, không tự commit."
  }
}
