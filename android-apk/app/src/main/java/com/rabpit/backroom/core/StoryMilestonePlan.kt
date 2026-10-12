package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Verified script data from the Gemini planner; not a narrating GM or a combat resolver.
 * Every event and its outcome are compulsory. GM controls presentation, not the planned result.
 *
 * This contract does not trigger events or award EXP. The runtime dispatcher belongs to step 6.
 */
object StoryMilestonePlan {
  const val SCHEMA_VERSION = 1
  const val FINAL_HP_MULTIPLIER = 9
  const val FINAL_EXP_MULTIPLIER = 9
  private const val ACTIVE_KEY = "story.milestone.active"
  private const val PREPARED_KEY = "story.milestone.prepared"
  private const val MAX_PLAN_CHARS = 32768
  private const val MAX_EVENTS = 64
  private const val MAX_DESCRIPTION_CHARS = 500
  private val EVENT_ID = Regex("[A-Za-z][A-Za-z0-9_-]{0,47}")
  private val ENTITY_KEY = Regex("[a-z0-9_-]{1,80}")

  enum class Kind { STORY, FINAL_ENTITY }

  data class Event(
    val id: String,
    val turn: Int,
    val kind: Kind,
    val event: String,
    val outcome: String,
    val entityKey: String? = null
  ) {
    fun toJson(): JSONObject = JSONObject()
      .put("id", id)
      .put("turn", turn)
      .put("kind", kind.name)
      .put("event", event)
      .put("outcome", outcome)
      .apply {
        if (kind == Kind.FINAL_ENTITY) {
          put("entityKey", requireNotNull(entityKey))
          put("hpMultiplier", FINAL_HP_MULTIPLIER)
          put("expMultiplier", FINAL_EXP_MULTIPLIER)
        }
      }
  }

  data class Plan(val checkpointId: String, val events: List<Event>) {
    val finalEncounter: Event get() = events.last()

    /** Relative 1-based turn within this Checkpoint; no per-action quotas. */
    fun eventsAt(relativeTurn: Int): List<Event> =
      if (relativeTurn < 1) emptyList() else events.filter { it.turn == relativeTurn }

    fun toJson(): JSONObject = JSONObject()
      .put("schemaVersion", SCHEMA_VERSION)
      .put("checkpointId", checkpointId)
      .put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
  }

  data class Active(val startTurn: Int, val plan: Plan) {
    fun eventsAtGlobalTurn(globalTurn: Int): List<Event> {
      val relative = globalTurn.toLong() - startTurn.toLong() + 1L
      return if (relative in 1..Int.MAX_VALUE.toLong()) plan.eventsAt(relative.toInt()) else emptyList()
    }
  }

  /** Reject incomplete, speculative, duplicated and unsupported Gemini output. */
  fun parse(raw: String, expected: StoryCheckpointCore.Position): Plan {
    require(raw.length in 2..MAX_PLAN_CHARS) { "milestone_payload_size_invalid" }
    val root = JSONObject(raw)
    exactFields(root, setOf("schemaVersion", "checkpointId", "events"))
    require(integer(root, "schemaVersion") == SCHEMA_VERSION) { "milestone_schema_invalid" }
    val checkpointId = string(root, "checkpointId", 32)
    require(checkpointId == expected.id) { "milestone_checkpoint_mismatch" }
    val entries = root.getJSONArray("events")
    require(entries.length() in 1..MAX_EVENTS) { "milestone_events_count_invalid" }

    val events = ArrayList<Event>(entries.length())
    val ids = hashSetOf<String>()
    for (i in 0 until entries.length()) {
      val item = entries.getJSONObject(i)
      val kind = Kind.valueOf(string(item, "kind", 24))
      val allowed = mutableSetOf("id", "turn", "kind", "event", "outcome")
      if (kind == Kind.FINAL_ENTITY) allowed += setOf("entityKey", "hpMultiplier", "expMultiplier")
      exactFields(item, allowed)

      val id = string(item, "id", 48)
      require(EVENT_ID.matches(id) && ids.add(id)) { "milestone_event_id_invalid" }
      val turn = integer(item, "turn")
      require(turn in 1..1_000_000) { "milestone_turn_invalid" }
      require(events.isEmpty() || events.last().turn <= turn) { "milestone_turn_order_invalid" }
      val event = string(item, "event", MAX_DESCRIPTION_CHARS)
      val outcome = string(item, "outcome", MAX_DESCRIPTION_CHARS)
      val key = if (kind == Kind.FINAL_ENTITY) {
        val value = string(item, "entityKey", 80)
        require(ENTITY_KEY.matches(value) && CombatRuntime.supportsEntityKey(value)) {
          "milestone_entity_unknown"
        }
        require(integer(item, "hpMultiplier") == FINAL_HP_MULTIPLIER &&
            integer(item, "expMultiplier") == FINAL_EXP_MULTIPLIER) {
          "milestone_final_multiplier_invalid"
        }
        value
      } else null
      events += Event(id, turn, kind, event, outcome, key)
    }

    require(events.last().kind == Kind.FINAL_ENTITY &&
      events.count { it.kind == Kind.FINAL_ENTITY } == 1) {
      "milestone_final_encounter_required"
    }
    return Plan(checkpointId, events)
  }

  /** Install once at Checkpoint activation; never rewrite the authored schedule mid-story. */
  fun installCurrent(state: GameState, raw: String): GameState {
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val plan = parse(raw, position)
    val existing = current(state)
    if (existing != null) {
      require(existing.plan == plan) { "milestone_active_plan_conflict" }
      return state
    }
    val startTurn = CoreClientProjection.turnIndex(state)
    val value = JSONObject().put("startTurn", startTurn).put("plan", plan.toJson()).toString()
    return state.copy(world = state.world + (ACTIVE_KEY to value))
  }

  /** Read-only access. The dispatcher will consume these events in step 6. */
  fun current(state: GameState): Active? {
    val stored = state.world[ACTIVE_KEY] ?: return null
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val wrapper = JSONObject(stored)
    exactFields(wrapper, setOf("startTurn", "plan"))
    val startTurn = integer(wrapper, "startTurn")
    require(startTurn >= 1) { "milestone_start_turn_invalid" }
    return Active(startTurn, parse(wrapper.getJSONObject("plan").toString(), position))
  }

  /**
   * Pre-store the following Checkpoint while its predecessor's final Entity
   * is in active combat. Does NOT activate it or move the Story Checkpoint.
   * The Level boundary after CP20 is deliberately left for Level routing.
   */
  fun prepareNextDuringCombat(state: GameState, raw: String): GameState {
    val currentPosition = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val next = requireNotNull(currentPosition.nextAddress()) { "next_level_checkpoint_not_configured" }
    val active = requireNotNull(current(state)) { "milestone_current_plan_missing" }
    val combat = CombatRuntime.active(state)
    require(combat != null && combat.entityKey == active.plan.finalEncounter.entityKey) {
      "milestone_final_combat_required"
    }
    val plan = parse(raw, next)
    val existing = prepared(state)
    if (existing != null) {
      require(existing == plan) { "milestone_prepared_plan_conflict" }
      return state
    }
    return state.copy(world = state.world + (PREPARED_KEY to plan.toJson().toString()))
  }

  fun prepared(state: GameState): Plan? {
    val raw = state.world[PREPARED_KEY] ?: return null
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val next = requireNotNull(position.nextAddress()) { "next_level_checkpoint_not_configured" }
    return parse(raw, next)
  }

  private fun exactFields(value: JSONObject, allowed: Set<String>) {
    require(value.keys().asSequence().toSet() == allowed) { "milestone_fields_invalid" }
  }

  private fun string(value: JSONObject, field: String, maxChars: Int): String {
    val raw = value.opt(field)
    require(raw is String) { "milestone_field_type_invalid:$field" }
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= maxChars &&
      trimmed.none { it == '\u0000' }) { "milestone_field_length_invalid:$field" }
    return trimmed
  }

  private fun integer(value: JSONObject, field: String): Int {
    val raw = value.opt(field)
    require(raw is Int) { "milestone_field_type_invalid:$field" }
    return raw
  }
}
