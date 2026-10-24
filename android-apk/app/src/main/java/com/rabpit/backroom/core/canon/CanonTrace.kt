package com.rabpit.backroom.core.canon

/** Một record đã truy xuất, kèm cổng + lý do chọn (từ SelectionTrace của CanonResolver). */
data class RetrievedRecordTrace(
  val recordId: String,
  val gate: Int,
  val reason: String
)

/**
 * Trace cục bộ cho MỘT lượt theo §16: turn_id, pack_version, state_revision,
 * present_entity_ids, retrieved_record_ids + reasons, required_missing,
 * estimated/observed tokens, provider_model, quota_decision, latency,
 * validator_codes.
 *
 * An toàn nội dung: trace chỉ chứa ID, mã quyết định và số đo — KHÔNG chứa
 * API key, KHÔNG chứa full prompt, KHÔNG chứa thân record canon.
 * [toLogLine] nén mọi trường tự do về một dòng, cắt lý do ở 48 ký tự
 * (lý do là mã truy vết ngắn, không phải nội dung canon).
 */
data class CanonTurnTrace(
  val turnId: String,
  val packVersion: String,
  val stateRevision: Int,
  val presentEntityIds: List<String> = emptyList(),
  val retrieved: List<RetrievedRecordTrace> = emptyList(),
  val requiredMissing: List<String> = emptyList(),
  val estimatedTokens: Int = 0,
  val observedTokens: Int? = null,
  val profile: String = "",
  val providerModel: String = "",
  val quotaDecision: String = "",
  val validatorCodes: List<String> = emptyList(),
  val latencyLocalMs: Long? = null,
  val latencyProviderMs: Long? = null
) {
  fun toLogLine(): String {
    val retrievedText = retrieved.joinToString(",") { trace ->
      "${trace.recordId}@g${trace.gate}:${sanitize(trace.reason)}"
    }
    return buildString {
      append("CR2 turn=").append(sanitize(turnId))
      append(" pack=").append(sanitize(packVersion))
      append(" rev=").append(stateRevision)
      append(" profile=").append(sanitize(profile))
      append(" model=").append(sanitize(providerModel))
      append(" estTokens=").append(estimatedTokens)
      append(" obsTokens=").append(observedTokens?.toString() ?: "-")
      append(" quota=").append(sanitize(quotaDecision))
      append(" present=[").append(presentEntityIds.joinToString(",")).append("]")
      append(" missing=[").append(requiredMissing.joinToString(",")).append("]")
      append(" retrieved=[").append(retrievedText).append("]")
      append(" validators=[").append(validatorCodes.joinToString(",")).append("]")
      append(" latencyLocalMs=").append(latencyLocalMs?.toString() ?: "-")
      append(" latencyProviderMs=").append(latencyProviderMs?.toString() ?: "-")
    }
  }

  private fun sanitize(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ').take(MAX_FIELD_CHARS)

  private companion object {
    const val MAX_FIELD_CHARS = 48
  }
}
