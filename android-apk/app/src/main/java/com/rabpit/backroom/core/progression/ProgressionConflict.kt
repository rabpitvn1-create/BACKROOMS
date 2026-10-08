package com.rabpit.backroom.core.progression

/**
 * Narrative-conflict repair hook for issue #453.
 *
 * Shape only — wiring into GameCoreFacade / GM context is implementation phase,
 * after design review.
 *
 * Trust boundary (locked):
 * - `candidate.level` from Gemini is a NARRATIVE CLAIM, never progression authority.
 * - On mismatch with the authoritative Core node: do NOT mutate progression,
 *   keep the authoritative node, record the conflict, surface it in the next
 *   GM context so the narrative repairs itself, and emit an audit event.
 * - "reject/repair" must be code + tests, never a comment or soft convention.
 */
data class ProgressionConflict(
  /** Raw AI claim, e.g. "level-2", "2", "Level 2". Never trusted as authority. */
  val claimed: String,
  val authoritativeNodeId: WorldNodeId,
)

object ProgressionConflictPolicy {

  /**
   * Best-effort claim normalizer for CONFLICT DETECTION ONLY.
   * Detection may be fuzzy; AUTHORITY never flows from this function.
   * Authority always comes from [WorldProgressionCore.validateTransition].
   */
  fun normalizeClaim(rawClaim: String): String {
    val t = rawClaim.trim().lowercase()
    val withoutPrefix = t.removePrefix("level").trim().trimStart('-', ' ', ':')
    return if (withoutPrefix.matches(Regex("\\d+"))) "level-$withoutPrefix" else t
  }

  /**
   * Null = no conflict. Non-null = reject the narrative claim and keep the
   * authoritative node; the caller attaches the conflict to the next GM
   * context and emits an audit event.
   */
  fun detect(rawClaim: String?, authoritativeNodeId: WorldNodeId): ProgressionConflict? {
    if (rawClaim.isNullOrBlank()) return null
    val normalized = normalizeClaim(rawClaim)
    return if (normalized == authoritativeNodeId.value) null
    else ProgressionConflict(claimed = rawClaim, authoritativeNodeId = authoritativeNodeId)
  }
}
