package com.rabpit.backroom.core.canon

import java.util.Locale

/** One trace entry per selection decision: which gate picked or rejected a record, and why. */
data class SelectionTrace(val recordId: String, val gate: Int, val reason: String)

/**
 * Result of canon resolution for one scene.
 *
 * [required] records are operationally mandatory for this turn (hard rules, mandatory
 * context, identity/address locks of the actors being interacted with, confirmed
 * scene facts). [missingRequiredIds] lists required ids the store does not have —
 * reported explicitly, never silently skipped (the old engine's `add()` swallowed
 * them); the compiler turns a non-empty list into `MISSING_REQUIRED_CANON`.
 */
data class CanonSelection(
  val required: List<CanonRecord>,
  val optional: List<CanonRecord>,
  val missingRequiredIds: List<String>,
  val trace: List<SelectionTrace>
)

/**
 * CR2 resolver — ARCHITECTURE §6.2 gates 0–4, graph-bounded. Gate 5 (semantic) does
 * not exist this round. Selection semantics (mandatory set, level direct id, present
 * runtime cards, relationship/address pairs, state-driven hard locks) are ported from
 * `KnowledgeContextEngine.Builder`.
 *
 * Classification: gate 1 is REQUIRED by construction; gate 2 is REQUIRED only for
 * hard kinds (hard-lock / hard-canon / address-lock), otherwise OPTIONAL; gates 3–4
 * are OPTIONAL. A record whose truth status is below its activation minimum evidence
 * is DEMOTED out of the required layer (never presented as confirmed fact), per §6.2
 * gate 0 — it is not silently dropped. Ordering is deterministic: required by
 * (priority, id), optional by (score desc, priority, id).
 */
class CanonResolver(private val store: CanonStore) {

  /** Gate 0 outcome for one record: pass, demote-to-optional, or hard deny. */
  private sealed class GateVerdict {
    object Pass : GateVerdict()
    data class Demote(val reason: String) : GateVerdict()
    data class Deny(val reason: String) : GateVerdict()
  }

  fun resolve(scene: SceneSnapshot, snapshot: CanonSnapshot): CanonSelection {
    val scope = CanonScope(
      universeId = scene.universeId,
      levelId = scene.levelId,
      povActorId = scene.povActorId,
      knownByActorIds = scene.actorsPresent
    )
    val trace = mutableListOf<SelectionTrace>()
    val required = linkedMapOf<String, CanonRecord>()
    val optional = linkedMapOf<String, CanonRecord>()
    val optionalScores = mutableMapOf<String, Int>()
    val missing = linkedSetOf<String>()

    fun isSelected(id: String): Boolean = id in required || id in optional

    /**
     * Gate 0 — scope / POV / visibility.
     * [viaActionSubject] is true when the record surfaced because the player's own
     * action named its subject (gates 1–2 and 4). The store additionally pre-filters
     * gates 2–4 by scope; this check is the resolver-side enforcement and the one
     * that applies to gate 1 (store `loadRecordsByIds` is intentionally unfiltered).
     */
    fun gate0(record: CanonRecord, viaActionSubject: Boolean): GateVerdict {
      if (record.universeId != scene.universeId) {
        return GateVerdict.Deny("gate0: universe ${record.universeId} outside scene universe ${scene.universeId}")
      }
      // Newly ingested named followers require confirmed scene presence and a selected
      // category even when the player mentions their name. A name never opens a codex.
      if (record.entityId in setOf("CHAR.NGUYEN_QUOC_NAM", "CHAR.HUA_TIEU_LAN")) {
        val actor = actorIdForEntity(record.entityId)
        if (actor !in scene.actorsPresent) return GateVerdict.Deny("named follower is not confirmed present")
        val intents = record.activation.anySceneIntent
        if (intents.isNotEmpty() && intents.none { it in scene.actionIntents }) {
          return GateVerdict.Deny("named follower category is not selected for this action")
        }
      }
      // SUBLEVEL_ENVIRONMENT_SCOPE: the parent's room description is not the sublevel's environment.
      if (scene.locationNodeId != null && record.id == scene.levelId &&
        scene.locationNodeId.removePrefix("LEVEL.NODE.") != scene.levelId.removePrefix("LEVEL.").toIntOrNull()?.toString()) {
        return GateVerdict.Deny("gate0: parent environment is not the current sublevel")
      }
      // LEVEL_NODE_EXACT_SCOPE: mentioning another node cannot load its environment.
      if (scene.locationNodeId != null && record.id.startsWith("LEVEL.NODE.") && record.id != scene.locationNodeId) {
        return GateVerdict.Deny("gate0: not the current location node")
      }
      val levelScope = record.activation.levelScope
      if (levelScope != CanonActivation.LEVEL_SCOPE_ANY && levelScope != scene.levelId) {
        return GateVerdict.Deny("gate0: level scope $levelScope outside current level ${scene.levelId}")
      }
      when (record.spoilerPolicy) {
        SpoilerPolicy.NEVER_SHOW ->
          return GateVerdict.Deny("gate0: spoiler policy NEVER_SHOW")
        SpoilerPolicy.HIDE_UNTIL_DISCLOSED -> {
          val disclosed = record.id in scene.knownFacts ||
            record.id == scene.levelId ||
            (actorIdForEntity(record.entityId)?.let { it in scene.actorsPresent } == true)
          if (!disclosed) return GateVerdict.Deny("gate0: spoiler policy HIDE_UNTIL_DISCLOSED and not disclosed in scene")
        }
        SpoilerPolicy.SHOW_IF_RELEVANT -> Unit
      }
      if (record.knowledgeVisibility == KnowledgeVisibility.RESTRICTED) {
        val knowers = record.knownBy + (scene.knownByByFact[record.id] ?: emptySet())
        val audience = scene.actorsPresent + scene.povActorId
        if (knowers.isNotEmpty() && knowers.none { it in audience }) {
          return GateVerdict.Deny("gate0: restricted record; none of its knowers is present")
        }
      }
      val requiredActor = record.activation.requireActorPresentOrSubject
      if (requiredActor != null && record.activation.denyIfUnauthorizedPov && !viaActionSubject) {
        val actorId = actorIdForEntity(requiredActor)
        if (actorId != null && actorId != scene.povActorId && actorId !in scene.actorsPresent) {
          return GateVerdict.Deny("gate0: subject actor $actorId not present and not the subject of the action (unauthorized POV)")
        }
      }
      if (record.truthStatus.ordinal > record.activation.minEvidence.ordinal) {
        return GateVerdict.Demote(
          "gate0: truth status ${record.truthStatus} below minimum evidence ${record.activation.minEvidence}; demoted from required"
        )
      }
      return GateVerdict.Pass
    }

    fun loadById(ids: Set<String>): Map<String, CanonRecord> =
      store.loadRecordsByIds(ids, snapshot).associateBy { it.id }

    /** Batch-load, preserving the caller's id order; ids absent from the store drop out. */
    fun loadAll(ids: Set<String>): List<CanonRecord> {
      val byId = loadById(ids)
      return ids.mapNotNull { byId[it] }
    }

    /** Shared gate tail: gate-0 filter, then classify into required/optional. */
    fun admit(
      records: Collection<CanonRecord>,
      gate: Int,
      reason: (CanonRecord) -> String,
      viaActionSubject: Boolean,
      forceRequired: Boolean,
      score: (CanonRecord) -> Int = { 0 }
    ) {
      records.forEach { record ->
        if (isSelected(record.id)) return@forEach
        var demoted: String? = null
        when (val verdict = gate0(record, viaActionSubject)) {
          is GateVerdict.Deny -> {
            trace += SelectionTrace(record.id, 0, verdict.reason)
            return@forEach
          }
          is GateVerdict.Demote -> demoted = verdict.reason
          GateVerdict.Pass -> Unit
        }
        val asRequired = demoted == null && (forceRequired || record.kind in HARD_KINDS)
        if (asRequired) required[record.id] = record else {
          optional[record.id] = record
          optionalScores[record.id] = score(record)
        }
        val note = if (demoted != null) "${reason(record)}; $demoted" else reason(record)
        trace += SelectionTrace(record.id, gate, note)
      }
    }

    // ------------------------------------------------------------ gate 1: exact ids
    // 1a. Hard-required fixed ids: the old engine's mandatory set + current level.
    //     Absence here is a pack defect -> missingRequiredIds (never silent).
    val hardReasons = linkedMapOf<String, String>()
    MANDATORY_IDS.forEach { hardReasons[it] = "mandatory hard context" }
    val nodeId = scene.locationNodeId
    val isMainLevel = nodeId?.removePrefix("LEVEL.NODE.")?.matches(Regex("[0-9]+")) ?: true
    if (isMainLevel) hardReasons[scene.levelId] = "current level direct id"
    if (nodeId != null) hardReasons[nodeId] = "current location node direct id"
    // 1b. Fixed state-driven ids (same status as 1a: fixed ids, pack must carry them).
    if (scene.storyThreadsActive.any { it.startsWith("THREAD.REUNITE") }) {
      hardReasons["STORY.MAIN.OBJECTIVE"] = "active main-campaign objective"
      hardReasons["STORY.MAIN.SEPARATION"] = "active separation continuity"
    }
    if (scene.entityEncounters.isNotEmpty() || "direct_threat" in scene.actionIntents) {
      hardReasons["ENTITY.GLOBAL_HARD_LOCK"] = "entity state/scene requires entity rules"
    }
    if (scene.objectsInFocus.isNotEmpty()) {
      hardReasons["ITEM.GLOBAL_HARD_LOCK"] = "item/resource state or action"
    }
    val hardLoaded = loadById(hardReasons.keys)
    hardReasons.keys.forEach { id ->
      if (id !in hardLoaded) {
        missing += id
        trace += SelectionTrace(id, 1, "required canon id missing from store")
      }
    }
    admit(
      records = hardReasons.keys.mapNotNull { hardLoaded[it] },
      gate = 1,
      reason = { hardReasons[it.id] ?: "gate 1 direct id" },
      viaActionSubject = true,
      forceRequired = true
    )

    // 1c. Actor-convention candidates for present actors, derived from the record-id
    //     conventions the old engine hardcoded (CHAR.<ACTOR>.RUNTIME_CORE,
    //     REL.<POV>.<ACTOR>.BASELINE, ADDR.<ACTOR>.<POV>, REL.<A>.<B>.BASELINE).
    //     These are candidates, not pack requirements: an actor may legitimately
    //     have no runtime card / relationship record in a pack, so absence is traced
    //     but does NOT go into missingRequiredIds.
    val candidateReasons = linkedMapOf<String, String>()
    val povSegment = idSegment(scene.povActorId)
    scene.actorsPresent.forEach { actor ->
      val segment = idSegment(actor)
      candidateReasons["CHAR.$segment.RUNTIME_CORE"] = "present actor runtime core"
      if (actor in setOf("nguyen_quoc_nam", "hua_tieu_lan")) {
        candidateReasons["CHAR.$segment.LIMITS"] = "named follower capability limits"
        if ("first_encounter_$actor" in scene.actionIntents) {
          candidateReasons["CHAR.$segment.ENCOUNTER"] = "locked first encounter"
          if (actor == "hua_tieu_lan") candidateReasons["CHAR.$segment.FIRST_ENCOUNTER"] = "locked first meeting address exception"
        }
      }
      if (actor != scene.povActorId) {
        candidateReasons["REL.$povSegment.$segment.BASELINE"] = "present relationship edge"
        candidateReasons["ADDR.$segment.$povSegment"] = "present address lock"
      }
    }
    val companions = scene.actorsPresent.filter { it != scene.povActorId }.sorted()
    for (i in companions.indices) {
      for (j in i + 1 until companions.size) {
        candidateReasons["REL.${idSegment(companions[i])}.${idSegment(companions[j])}.BASELINE"] =
          "present relationship edge"
      }
    }
    candidateReasons.keys.removeAll(hardReasons.keys)
    val candidateLoaded = loadById(candidateReasons.keys)
    candidateReasons.keys.forEach { id ->
      if (id !in candidateLoaded) {
        trace += SelectionTrace(id, 1, "actor candidate id not in store (no such card in pack)")
      }
    }
    admit(
      records = candidateReasons.keys.mapNotNull { candidateLoaded[it] },
      gate = 1,
      reason = { candidateReasons[it.id] ?: "gate 1 actor candidate" },
      viaActionSubject = true,
      forceRequired = true
    )

    // 1d. Confirmed scene objects (entities/items) are gate-1 direct ids too, but
    //     they are scene facts, not canon requirements: a missing record is no error.
    val sceneObjectIds = linkedSetOf<String>()
    scene.entityEncounters
      .filter { it.status == EncounterStatus.CONFIRMED_PRESENT }
      .forEach { sceneObjectIds += it.entityId }
    sceneObjectIds += scene.objectsInFocus
    admit(
      records = loadAll(sceneObjectIds),
      gate = 1,
      reason = { record ->
        if (record.id in scene.objectsInFocus) "confirmed item in focus" else "confirmed entity present in scene"
      },
      viaActionSubject = true,
      forceRequired = true
    )

    // ------------------------------------------------------------ gate 2: controlled aliases
    // Candidate phrases are contiguous n-grams (1..3 words) of the normalized action.
    // Precision is controlled by the store: resolveAliases only returns ids for
    // EXACT alias-index keys (tags / aliases / entity names from record data), so a
    // proposed phrase that is not a known term is ignored. Resolution runs per term
    // so ambiguity stays visible: a single-word term hitting records of more than
    // one entity is only admitted for entities already in scene scope (the "Rose"
    // guard of §6.2); otherwise the term's hits are traced and dropped.
    candidateTerms(scene.actionTextNormalized).forEach { term ->
      val ids = store.resolveAliases(setOf(term), scope)
      if (ids.isEmpty()) return@forEach
      val records = loadAll(ids)
      val entityIds = records.mapNotNull { it.entityId }.toSet()
      val ambiguous = ' ' !in term && entityIds.size > 1
      val encounteredIds = scene.entityEncounters.map { it.entityId }.toSet()
      val admitted = if (!ambiguous) records else records.filter { record ->
        val actor = actorIdForEntity(record.entityId)
        (actor != null && (actor in scene.actorsPresent || actor == scene.povActorId)) ||
          record.id == scene.levelId ||
          record.entityId in encounteredIds
      }
      if (ambiguous && admitted.isEmpty()) {
        records.forEach {
          trace += SelectionTrace(it.id, 2, "ambiguous alias '$term' spans multiple entities and none is in scene scope; dropped")
        }
        return@forEach
      }
      admit(
        records = admitted,
        gate = 2,
        reason = { "controlled alias match: $term" },
        viaActionSubject = true,
        forceRequired = false
      )
    }

    // ------------------------------------------------------------ gate 3: typed edges + affordances
    // Typed edges: only REFERENCES has data this round; the store bounds the walk
    // (GraphBounds defaults: maxHops = 1 per §6.2, plus node/byte/deadline caps).
    if (required.isNotEmpty()) {
      val edgeIds = store.traverseTypedEdges(required.keys.toSet(), setOf(EdgeType.REFERENCES), GraphBounds())
      admit(
        records = loadAll(edgeIds),
        gate = 3,
        reason = { "typed edge REFERENCES from required set (graph-bounded)" },
        viaActionSubject = false,
        forceRequired = false
      )
    }
    // Affordances: eligibility only (CAN_DO, never WILL_DO — the resolver does not
    // make any actor act). The presence guard of the old engine's affordance path
    // lives on here as gate 0 with viaActionSubject = false.
    if (scene.actionIntents.isNotEmpty()) {
      val affordanceIds = store.queryAffordances(scene.actionIntents, scope)
      admit(
        records = loadAll(affordanceIds),
        gate = 3,
        reason = { "scene affordance eligibility (CAN_DO, not WILL_DO): ${scene.actionIntents.joinToString(", ")}" },
        viaActionSubject = false,
        forceRequired = false
      )
    }

    // ------------------------------------------------------------ gate 4: optional text
    // Light lexical lookup over the already scope-filtered store. No match is a
    // valid outcome (NO_CANON_MATCH): nothing is force-fitted.
    val candidates = store.findOptionalText(scene.actionTextNormalized, scope, OPTIONAL_TEXT_LIMIT)
    admit(
      records = candidates.map { it.record },
      gate = 4,
      reason = { record ->
        val candidate = candidates.first { it.record.id == record.id }
        "optional text match: ${candidate.matchedTerm} (score ${candidate.score})"
      },
      viaActionSubject = true,
      forceRequired = false,
      score = { record -> candidates.firstOrNull { it.record.id == record.id }?.score ?: 0 }
    )

    // ------------------------------------------------------------ deterministic order
    val sortedRequired = required.values.sortedWith(compareBy<CanonRecord> { it.priority }.thenBy { it.id })
    val sortedOptional = optional.values.sortedWith(
      compareByDescending<CanonRecord> { optionalScores[it.id] ?: 0 }.thenBy { it.priority }.thenBy { it.id }
    )
    return CanonSelection(
      required = sortedRequired,
      optional = sortedOptional,
      missingRequiredIds = missing.sorted(),
      trace = trace
    )
  }

  /** "luc-tram" -> "LUC_TRAM" (record-id segment form). */
  private fun idSegment(actorId: String): String = actorId.uppercase(Locale.ROOT).replace('-', '_')

  /** `CHAR.LUC_TRAM` -> `luc-tram`; non-actor entity ids yield null. */
  private fun actorIdForEntity(entityId: String?): String? {
    val raw = entityId ?: return null
    if (!raw.startsWith("CHAR.")) return null
    return JsonCanonStore.actorIdForEntity(raw)
  }

  /**
   * Contiguous word n-grams (1..3 words, min length 3 — parity with the store-side
   * term extraction) of the normalized action, longest first for determinism.
   */
  private fun candidateTerms(normalizedAction: String): List<String> {
    if (normalizedAction.isBlank()) return emptyList()
    val words = normalizedAction.split(' ').filter { it.isNotEmpty() }
    val terms = linkedSetOf<String>()
    for (n in 3 downTo 1) {
      if (words.size < n) continue
      for (i in 0..words.size - n) {
        val term = (i until i + n).joinToString(" ") { words[it] }
        if (term.length >= 3) terms += term
      }
    }
    return terms.toList()
  }

  private companion object {
    /** The old engine's mandatory set (`Builder.addMandatory`), kept verbatim. */
    val MANDATORY_IDS: List<String> = listOf(
      "GAME.TEXT.CORE",
      "GAME.GM.FAIRNESS",
      "WORLD.CORE",
      "WRITING.KNOWLEDGE_BOUNDARY",
      "WRITING.COMPETENCE",
      "WRITING.PLAYER_AGENCY",
      "CHAR.CAO_MINH.RUNTIME_CORE"
    )

    /** Kinds that classify a gate-2 hit as REQUIRED (hard rule / address lock). */
    val HARD_KINDS: Set<String> = setOf(CanonKinds.HARD_LOCK, CanonKinds.HARD_CANON, CanonKinds.ADDRESS_LOCK)

    const val OPTIONAL_TEXT_LIMIT = 8
  }
}
