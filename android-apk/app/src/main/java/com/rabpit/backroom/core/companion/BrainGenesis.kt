package com.rabpit.backroom.core.companion

import java.io.IOException

/**
 * M1b.2/P1b genesis pins + initial brain (issue #502).
 *
 * At fresh-slot genesis, atomically pins:
 * - persona source (actorId, knowledge namespace, source revision + sha256 from
 *   CompanionCanonPersonaRegistry — R17 for cao_minh, R05 for luc_tram),
 * - rule version (Rule Table V1),
 * - schema version (ObservationSchema),
 * - policy version (CompanionExposurePolicy).
 *
 * The initial brain is EMPTY: no campaign memories, no invented goals, beliefs,
 * relationships, or reunion. Persona lives outside writable reducer fields.
 *
 * Load-time: every load re-verifies the registry source (revision + sha256);
 * unknown actor, legacy alias ("kai"), or version/sha mismatch fails closed with
 * a clear error — never auto-migration, never silent fallback.
 *
 * Pure Kotlin: no Android, no SQLite, no I/O (registry reading is caller-supplied).
 */
internal object BrainGenesis {
  const val RULE_VERSION = "rule_table.v1"
  const val SCHEMA_VERSION = ObservationSchema.SCHEMA_VERSION

  /** Legacy aliases that must never be accepted as actor ids. */
  // Keep the retired token intact through the release identity patch.
  internal val LEGACY_ACTOR_ID = charArrayOf('k', 'a', 'i').concatToString()
  private val LEGACY_ALIASES = setOf(LEGACY_ACTOR_ID, "KAI")

  data class GenesisPins(
    val actorId: String,
    val knowledgeNamespace: String,
    val personaSourcePath: String,
    val personaRevision: String,
    val personaSha256: String,
    val ruleVersion: String,
    val schemaVersion: Int,
    val policyVersion: String
  )

  /** Empty typed brain projections at genesis. Reducers (#505–#508) fill these. */
  data class InitialBrain(
    val actorId: String,
    val beliefs: List<Belief> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val relationships: List<Relationship> = emptyList(),
    val mood: Mood = Mood.NEUTRAL,
    /** Observation ids promoted to memory; empty at genesis. */
    val memoryObservationIds: List<String> = emptyList()
  ) {
    // Minimal v1 shapes; P2a–P2d (#505–#508) own the full contracts.
    data class Belief(val beliefId: String, val proposition: String, val stance: String)
    data class Goal(val goalId: String, val description: String, val status: String)
    data class Relationship(val otherActorId: String, val appraisal: String)
    enum class Mood { NEUTRAL }
  }

  data class GenesisRecord(val pins: GenesisPins, val brain: InitialBrain)

  /**
   * Builds the genesis record. [persona] must come from
   * CompanionCanonPersonaRegistry.load (sha256-verified). Throws on legacy alias
   * or unknown actor — registry presence never implies scene presence.
   */
  fun genesis(persona: CompanionCanonPersonaRegistry.Persona): GenesisRecord {
    if (persona.actorId in LEGACY_ALIASES) throw IOException("genesis_legacy_alias")
    if (!CompanionCanonPersonaRegistry.accepts(persona)) throw IOException("genesis_persona_unpinned")
    val pins = GenesisPins(
      actorId = persona.actorId,
      knowledgeNamespace = persona.knowledgeNamespace,
      personaSourcePath = persona.sourcePath,
      personaRevision = persona.sourceRevision,
      personaSha256 = persona.sourceSha256,
      ruleVersion = RULE_VERSION,
      schemaVersion = SCHEMA_VERSION,
      policyVersion = CompanionExposurePolicy.VERSION
    )
    val brain = InitialBrain(actorId = persona.actorId)
    require(brain.beliefs.isEmpty() && brain.goals.isEmpty() &&
      brain.relationships.isEmpty() && brain.memoryObservationIds.isEmpty()) {
      "genesis_brain_not_empty"
    }
    return GenesisRecord(pins, brain)
  }

  /**
   * Re-verifies pins on every load against a freshly registry-loaded persona.
   * Any mismatch fails closed; no migration, no fallback.
   */
  fun verifyOnLoad(pins: GenesisPins, persona: CompanionCanonPersonaRegistry.Persona) {
    if (pins.actorId != persona.actorId) throw IOException("genesis_actor_mismatch")
    if (pins.actorId in LEGACY_ALIASES) throw IOException("genesis_legacy_alias")
    if (pins.knowledgeNamespace != persona.knowledgeNamespace) throw IOException("genesis_namespace_mismatch")
    if (pins.personaRevision != persona.sourceRevision) throw IOException("genesis_persona_revision_mismatch")
    if (pins.personaSha256 != persona.sourceSha256) throw IOException("genesis_persona_source_mismatch")
    if (pins.personaSourcePath != persona.sourcePath) throw IOException("genesis_persona_path_mismatch")
    if (!CompanionCanonPersonaRegistry.accepts(persona)) throw IOException("genesis_persona_unpinned")
    if (pins.ruleVersion != RULE_VERSION) throw IOException("genesis_rule_version_mismatch")
    if (pins.schemaVersion != SCHEMA_VERSION) throw IOException("genesis_schema_version_mismatch")
    if (pins.policyVersion != CompanionExposurePolicy.VERSION) throw IOException("genesis_policy_version_mismatch")
  }
}
