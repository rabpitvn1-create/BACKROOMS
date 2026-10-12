package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini is the sole author of Checkpoint-local Turn, scripted in-world action,
 * compulsory outcome and (at a Level boundary) how the Level ends.
 * Neither a player's free-text action nor the GM's narration is an Exit permit.
 */
object StoryMilestonePlan {
  const val SCHEMA_VERSION = 2
  const val FINAL_HP_MULTIPLIER = 9
  const val FINAL_EXP_MULTIPLIER = 9
  private const val ACTIVE_KEY = "story.milestone.active"
  private const val PREPARED_KEY = "story.milestone.prepared"
  private const val MAX_PLAN_CHARS = 32768
  private const val MAX_EVENTS = 64
  private val EVENT_ID = Regex("[A-Za-z][A-Za-z0-9_-]{0,47}")
  private val ENTITY_KEY = Regex("[a-z0-9_-]{1,80}")

  enum class Kind { STORY, FINAL_ENTITY }
  enum class CompletionType { NEXT_CHECKPOINT, EXIT_LEVEL }

  data class Completion(
    val type: CompletionType,
    val targetNodeId: String? = null,
    val nextCheckpointTotal: Int? = null,
    val outcome: String? = null
  ) {
    fun toJson(): JSONObject = JSONObject().put("type", type.name).apply {
      if (type == CompletionType.EXIT_LEVEL) {
        put("targetNodeId", requireNotNull(targetNodeId))
        put("nextCheckpointTotal", requireNotNull(nextCheckpointTotal))
        put("outcome", requireNotNull(outcome))
      }
    }
  }

  data class Event(
    val id: String,
    val turn: Int,
    val kind: Kind,
    /** Scripted in-world action. NOT a required phrase from the player. */
    val action: String,
    val event: String,
    val outcome: String,
    val entityKey: String? = null,
    val storyFacts: List<StoryFactLedger.FactSpec> = emptyList()
  ) {
    fun toJson(): JSONObject = JSONObject()
      .put("id", id).put("turn", turn).put("kind", kind.name)
      .put("action", action).put("event", event).put("outcome", outcome)
      .apply {
        if (kind == Kind.FINAL_ENTITY) {
          put("entityKey", requireNotNull(entityKey))
          put("hpMultiplier", FINAL_HP_MULTIPLIER)
          put("expMultiplier", FINAL_EXP_MULTIPLIER)
        }
        if (storyFacts.isNotEmpty()) {
          put("storyFacts", JSONArray().apply { storyFacts.forEach { put(it.toJson()) } })
        }
      }
  }

  data class Plan(val checkpointId: String, val events: List<Event>, val completion: Completion) {
    val finalEncounter: Event get() = events.last()
    fun eventsAt(relativeTurn: Int): List<Event> =
      if (relativeTurn < 1) emptyList() else events.filter { it.turn == relativeTurn }

    fun nextPosition(position: StoryCheckpointCore.Position): StoryCheckpointCore.Position {
      require(position.id == checkpointId) { "milestone_next_position_mismatch" }
      return when (completion.type) {
        CompletionType.NEXT_CHECKPOINT ->
          requireNotNull(position.nextAddress()) { "milestone_unexpected_checkpoint_end" }
        CompletionType.EXIT_LEVEL -> {
          val node = requireNotNull(LevelRouteRegistry.node(completion.targetNodeId)) {
            "milestone_exit_target_unknown"
          }
          StoryCheckpointCore.Position(node.parent, 1, requireNotNull(completion.nextCheckpointTotal))
        }
      }
    }

    fun nextNode(): LevelRouteRegistry.Node? =
      if (completion.type == CompletionType.EXIT_LEVEL) LevelRouteRegistry.node(completion.targetNodeId)
      else null

    fun toJson(): JSONObject = JSONObject()
      .put("schemaVersion", SCHEMA_VERSION).put("checkpointId", checkpointId)
      .put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
      .put("completion", completion.toJson())
  }

  data class Active(val startTurn: Int, val plan: Plan) {
    fun eventsAtGlobalTurn(globalTurn: Int): List<Event> {
      val relative = globalTurn.toLong() - startTurn.toLong() + 1L
      return if (relative in 1..Int.MAX_VALUE.toLong()) plan.eventsAt(relative.toInt()) else emptyList()
    }
  }

  /** A strict schema, not an adapter for old saves or unverifiable GM claims. */
  fun parse(raw: String, expected: StoryCheckpointCore.Position): Plan {
    require(raw.length in 2..MAX_PLAN_CHARS) { "milestone_payload_size_invalid" }
    val root = JSONObject(raw)
    exactFields(root, setOf("schemaVersion", "checkpointId", "events", "completion"))
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
      val allowed = mutableSetOf("id", "turn", "kind", "action", "event", "outcome")
      if (kind == Kind.FINAL_ENTITY) allowed += setOf("entityKey", "hpMultiplier", "expMultiplier")
      if (item.has("storyFacts")) allowed += "storyFacts"
      exactFields(item, allowed)

      val id = string(item, "id", 48)
      require(EVENT_ID.matches(id) && ids.add(id)) { "milestone_event_id_invalid" }
      val turn = integer(item, "turn")
      require(turn in 1..1_000_000) { "milestone_turn_invalid" }
      require(events.isEmpty() || events.last().turn <= turn) { "milestone_turn_order_invalid" }
      val action = string(item, "action", 500)
      val event = string(item, "event", 500)
      val outcome = string(item, "outcome", 500)
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
      val facts = if (item.has("storyFacts"))
        StoryFactLedger.parseSpecs(item.getJSONArray("storyFacts"), 4) else emptyList()
      events += Event(id, turn, kind, action, event, outcome, key, facts)
    }
    require(events.last().kind == Kind.FINAL_ENTITY &&
      events.count { it.kind == Kind.FINAL_ENTITY } == 1) {
      "milestone_final_encounter_required"
    }

    val completionJson = root.getJSONObject("completion")
    val type = CompletionType.valueOf(string(completionJson, "type", 24))
    val completion = when (type) {
      CompletionType.NEXT_CHECKPOINT -> {
        exactFields(completionJson, setOf("type"))
        require(!expected.isLast) { "milestone_last_checkpoint_must_exit_level" }
        Completion(type)
      }
      CompletionType.EXIT_LEVEL -> {
        exactFields(completionJson,
          setOf("type", "targetNodeId", "nextCheckpointTotal", "outcome"))
        require(expected.isLast) { "milestone_level_exit_before_final_checkpoint" }
        val id = string(completionJson, "targetNodeId", 64)
        val node = requireNotNull(LevelRouteRegistry.node(id)) { "milestone_exit_target_unknown" }
        require(node.kind == "MAIN" && node.parent != expected.level) {
          "milestone_exit_target_invalid"
        }
        val total = integer(completionJson, "nextCheckpointTotal")
        require(total in 1..100 && (node.parent != 0 ||
          total == StoryCheckpointCore.LEVEL_ZERO_TOTAL)) {
          "milestone_next_level_checkpoint_count_invalid"
        }
        Completion(type, id, total, string(completionJson, "outcome", 500))
      }
    }
    return Plan(checkpointId, events, completion)
  }

  /** The authored schedule cannot be rewritten after installation. */
  fun installCurrent(state: GameState, raw: String): GameState {
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val plan = parse(raw, position)
    val existing = current(state)
    if (existing != null) {
      require(existing.plan == plan) { "milestone_active_plan_conflict" }
      return state
    }
    val value = JSONObject().put("startTurn", CoreClientProjection.turnIndex(state))
      .put("plan", plan.toJson()).toString()
    return state.copy(world = state.world + (ACTIVE_KEY to value))
  }

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
   * Prepare the author's NEXT_CHECKPOINT or EXIT_LEVEL target when its final
   * Combat starts. Late model responses may arrive after verified combat end.
   */
  fun prepareNextDuringCombat(state: GameState, raw: String): GameState {
    val active = requireNotNull(current(state)) { "milestone_current_plan_missing" }
    val position = requireNotNull(StoryCheckpointCore.current(state))
    val next = active.plan.nextPosition(position)
    val bossEncounterId = state.world["story.milestone.bossEncounter"]
    val fight = CombatRuntime.active(state)
    val finished = state.world["story.milestone.bossResult"] != null
    require(bossEncounterId != null &&
      ((fight?.encounterId == bossEncounterId &&
        fight.entityKey == active.plan.finalEncounter.entityKey) ||
        (finished && fight == null))) { "milestone_final_combat_required" }
    val lastTurn = active.startTurn.toLong() + active.plan.finalEncounter.turn.toLong() - 1L
    require(CoreClientProjection.turnIndex(state).toLong() >= lastTurn) {
      "milestone_final_turn_not_reached"
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
    val active = requireNotNull(current(state)) { "milestone_current_plan_missing" }
    val pos = requireNotNull(StoryCheckpointCore.current(state))
    return parse(raw, active.plan.nextPosition(pos))
  }

  private fun exactFields(obj: JSONObject, allowed: Set<String>) {
    require(obj.keys().asSequence().toSet() == allowed) { "milestone_fields_invalid" }
  }
  private fun string(obj: JSONObject, key: String, max: Int): String {
    val raw = obj.opt(key)
    require(raw is String) { "milestone_field_type_invalid:$key" }
    val value = raw.trim()
    require(value.isNotEmpty() && value.length <= max &&
      value.none { Character.isISOControl(it) }) { "milestone_field_length_invalid:$key" }
    return value
  }
  private fun integer(obj: JSONObject, key: String): Int {
    val raw = obj.opt(key)
    require(raw is Int) { "milestone_field_type_invalid:$key" }
    return raw
  }
}
