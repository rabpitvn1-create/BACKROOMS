package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrative memory, independent of Combat HP, equipment, inventory and statuses.
 * Important unresolved story facts survive across Levels; a bounded history holds
 * completed events. No narrative text is interpreted as a gameplay command.
 */
object StoryFactLedger {
  private const val KEY = "story.factLedger"
  private const val MAX_OPEN = 48
  private const val MAX_HISTORY = 96
  private const val MAX_PER_TURN = 6
  private val subjectPattern = Regex("[a-z0-9_.:-]{2,64}")
  private val referencePattern = Regex("[a-zA-Z0-9_.:-]{1,110}")

  enum class Kind { EVENT, ENTITY, CLUE }

  data class FactSpec(val kind: Kind, val subject: String, val fact: String) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind.name)
      .put("subject", subject).put("fact", fact)
  }

  /** Shared strict contract for authored Milestone and GM continuity proposals. */
  fun parseSpecs(raw: JSONArray, limit: Int = MAX_PER_TURN): List<FactSpec> {
    require(raw.length() <= limit) { "story_facts_count_invalid" }
    return (0 until raw.length()).map { index ->
      val item = raw.getJSONObject(index)
      require(item.keys().asSequence().toSet() == setOf("kind", "subject", "fact")) {
        "story_fact_fields_invalid"
      }
      val kind = Kind.valueOf(readString(item, "kind", 16))
      val subject = readString(item, "subject", 64)
      require(subjectPattern.matches(subject)) { "story_subject_invalid" }
      FactSpec(kind, subject, readString(item, "fact", 220))
    }
  }

  fun recordTurn(
    state: GameState, milestoneEvents: List<StoryMilestonePlan.Event>, gmProposal: String
  ): GameState {
    require(gmProposal.length <= 5000) { "story_proposal_too_large" }
    val proposals = JSONArray(gmProposal)
    require(proposals.length() <= MAX_PER_TURN) { "story_proposal_count_invalid" }

    val open = readArray(state, "open")
    val history = readArray(state, "history")
    val position = StoryCheckpointCore.current(state)?.id ?: "FREE"
    val turn = CoreClientProjection.turnIndex(state)
    var sequence = 0

    fun add(spec: FactSpec, source: String) {
      if (spec.kind == Kind.ENTITY) {
        require(CombatRuntime.supportsEntityKey(spec.subject)) { "story_entity_unknown" }
        val visible = JSONObject(state.world["flagsJson"] ?: "{}")
          .optString("entityEncounterKey", "")
        val active = CombatRuntime.active(state)?.entityKey
        val scripted = milestoneEvents.any { it.entityKey == spec.subject }
        require(visible == spec.subject || active == spec.subject || scripted) {
          "story_entity_not_confirmed"
        }
      }
      val sameSubject = (0 until open.length()).mapNotNull { open.optJSONObject(it) }
        .firstOrNull { it.optString("subject") == spec.subject && it.optString("kind") == spec.kind.name }
      if (sameSubject != null) {
        require(sameSubject.optString("fact") == spec.fact) { "story_fact_conflict_requires_resolution" }
        return
      }
      require(open.length() < MAX_OPEN) { "story_open_facts_capacity_reached" }
      open.put(spec.toJson()
        .put("id", "FACT:$position:$turn:${sequence++}")
        .put("turn", turn).put("source", source))
    }

    // Only accepted Milestone receipts are recorded; not future planned events.
    for (event in milestoneEvents) {
      val key = "MS:$position:${event.id}"
      history.put(JSONObject().put("id", key).put("turn", turn)
        .put("kind", if (event.kind == StoryMilestonePlan.Kind.FINAL_ENTITY) "ENTITY" else "EVENT")
        .put("fact", "${event.event} → ${event.outcome}".take(500))
        .put("source", "MILESTONE"))
      event.storyFacts.forEach { add(it, "MILESTONE:$key") }
    }

    for (index in 0 until proposals.length()) {
      val item = proposals.getJSONObject(index)
      when (readString(item, "op", 16)) {
        "ADD" -> {
          require(item.keys().asSequence().toSet() == setOf("op", "kind", "subject", "fact")) {
            "story_proposal_fields_invalid"
          }
          val spec = parseSpecs(JSONArray().put(JSONObject(item.toString()).apply { remove("op") })).single()
          add(spec, "GM")
        }
        "RESOLVE" -> {
          require(item.keys().asSequence().toSet() == setOf("op", "referenceId", "fact")) {
            "story_resolve_fields_invalid"
          }
          val ref = readString(item, "referenceId", 110)
          require(referencePattern.matches(ref)) { "story_reference_invalid" }
          val resolution = readString(item, "fact", 220)
          val pos = (0 until open.length()).firstOrNull { open.optJSONObject(it)?.optString("id") == ref }
            ?: throw IllegalArgumentException("story_resolution_unknown_fact")
          val original = open.getJSONObject(pos)
          open.remove(pos)
          history.put(JSONObject(original.toString()).put("resolution", resolution)
            .put("resolvedTurn", turn).put("source", "GM_RESOLUTION"))
        }
        else -> throw IllegalArgumentException("story_operation_unsupported")
      }
    }

    if (milestoneEvents.isEmpty() && proposals.length() == 0) return state
    val retained = JSONArray()
    val start = (history.length() - MAX_HISTORY).coerceAtLeast(0)
    for (i in start until history.length()) retained.put(history.get(i))
    val packed = JSONObject().put("version", 1).put("open", open).put("history", retained)
    return state.copy(world = state.world + (KEY to packed.toString()))
  }

  /**
   * Deliver all unresolved facts, even from Level 0, plus recent history.
   * Never silently trim unresolved facts to a last-N turns buffer.
   */
  fun context(state: GameState): String {
    val open = readArray(state, "open")
    val allHistory = readArray(state, "history")
    val recent = JSONArray()
    for (i in (allHistory.length() - 8).coerceAtLeast(0) until allHistory.length()) {
      recent.put(allHistory.get(i))
    }
    return JSONObject().put("open", open).put("recent", recent)
      .apply {
        state.world["story.levelExitLast"]?.let {
          put("lastLevelExit", JSONObject(it))
        }
      }.toString()
  }

  private fun readArray(state: GameState, name: String): JSONArray {
    val raw = state.world[KEY] ?: return JSONArray()
    val root = JSONObject(raw)
    require(root.getInt("version") == 1) { "story_memory_version_invalid" }
    return JSONArray(root.getJSONArray(name).toString())
  }

  private fun readString(source: JSONObject, key: String, maxLength: Int): String {
    val raw = source.opt(key)
    require(raw is String) { "story_string_type_invalid:$key" }
    val value = raw.trim()
    require(value.isNotEmpty() && value.length <= maxLength &&
      value.none { Character.isISOControl(it) }) { "story_string_length_invalid:$key" }
    return value
  }
}
