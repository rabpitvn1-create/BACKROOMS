package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * SOL-authored script data; not a narrating GM or a combat resolver.
 * Every event and its outcome are compulsory. GM controls presentation, not the planned result.
 *
 * This contract does not trigger events or award EXP. The runtime dispatcher belongs to step 6.
 */
object StoryMilestonePlan {
  const val SCHEMA_VERSION = 2
  const val MIN_CHECKPOINT_TURNS = 18
  const val MIN_ENTITY_PERCENT = 40

  /** Quota applies to Explorer turns and ordinary Entity encounters, never Boss or Combat. */
  fun minimumEntityEncounters(explorerTurns: Long): Long {
    require(explorerTurns >= MIN_CHECKPOINT_TURNS) { "milestone_checkpoint_too_short" }
    return (explorerTurns * MIN_ENTITY_PERCENT + 99L) / 100L
  }
  const val FINAL_HP_MULTIPLIER = 9
  const val FINAL_EXP_MULTIPLIER = 9
  private const val ACTIVE_KEY = "story.milestone.active"
  private const val PREPARED_KEY = "story.milestone.prepared"
  private const val MAX_PLAN_CHARS = 65536
  private const val MAX_EVENTS = 64
  private const val MAX_DESCRIPTION_CHARS = 500
  private val EVENT_ID = Regex("[A-Za-z][A-Za-z0-9_-]{0,47}")
  private val ENTITY_KEY = Regex("[a-z0-9_-]{1,80}")

  enum class Kind { STORY, FOLLOWER, NPC, ENTITY, FINAL_ENTITY }
  enum class CompletionType { NEXT_CHECKPOINT, EXIT_LEVEL }

  data class Completion(
    val type: CompletionType,
    val outcome: String,
    val targetNodeId: String? = null,
    val nextCheckpointTotal: Int? = null
  ) {
    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("outcome", outcome).apply {
      if (type == CompletionType.EXIT_LEVEL) {
        put("targetNodeId", requireNotNull(targetNodeId))
        put("nextCheckpointTotal", requireNotNull(nextCheckpointTotal))
      }
    }

    fun nextAddress(position: StoryCheckpointCore.Position): StoryCheckpointCore.Position =
      when (type) {
        CompletionType.NEXT_CHECKPOINT -> requireNotNull(position.nextAddress()) {
          "milestone_completion_requires_level_exit"
        }
        CompletionType.EXIT_LEVEL -> {
          val target = requireNotNull(LevelRouteRegistry.node(requireNotNull(targetNodeId))) {
            "milestone_exit_target_unknown"
          }
          StoryCheckpointCore.Position(target.parent, 1, requireNotNull(nextCheckpointTotal))
        }
      }
  }

  data class Event(
    val id: String,
    val turn: Int,
    val kind: Kind,
    val action: String,
    val event: String,
    val outcome: String,
    val entityKey: String? = null,
    val combatRequired: Boolean = kind == Kind.FINAL_ENTITY,
    val characterId: String? = null
  ) {
    val isEntityEncounter: Boolean get() = kind == Kind.ENTITY || kind == Kind.FINAL_ENTITY
    val isCharacterEncounter: Boolean get() = kind == Kind.FOLLOWER || kind == Kind.NPC
    val isEncounter: Boolean get() = isEntityEncounter || isCharacterEncounter

    fun toJson(): JSONObject = JSONObject()
      .put("id", id)
      .put("turn", turn)
      .put("kind", kind.name)
      .put("action", action)
      .put("event", event)
      .put("outcome", outcome)
      .apply {
        if (isEntityEncounter) put("entityKey", requireNotNull(entityKey))
        if (kind == Kind.ENTITY) put("combatRequired", combatRequired)
        if (isCharacterEncounter) put("characterId", requireNotNull(characterId))
        if (kind == Kind.FINAL_ENTITY) {
          put("hpMultiplier", FINAL_HP_MULTIPLIER)
          put("expMultiplier", FINAL_EXP_MULTIPLIER)
        }
      }
  }

  data class Plan(val checkpointId: String, val events: List<Event>, val completion: Completion) {
    val finalEncounter: Event get() = events.last()

    /** Relative 1-based turn within this Checkpoint; no per-action quotas. */
    fun eventsAt(relativeTurn: Int): List<Event> =
      if (relativeTurn < 1) emptyList() else events.filter { it.turn == relativeTurn }

    fun toJson(): JSONObject = JSONObject()
      .put("schemaVersion", SCHEMA_VERSION)
      .put("checkpointId", checkpointId)
      .put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
      .put("completion", completion.toJson())
  }

  data class Active(val startTurn: Int, val plan: Plan) {
    fun eventsAtGlobalTurn(globalTurn: Int): List<Event> {
      val relative = globalTurn.toLong() - startTurn.toLong() + 1L
      return if (relative in 1..Int.MAX_VALUE.toLong()) plan.eventsAt(relative.toInt()) else emptyList()
    }
  }

  /** Reject incomplete, speculative, duplicated and unsupported SOL plans. */
  @JvmOverloads fun parse(raw: String, expected: StoryCheckpointCore.Position,
                         characters: Map<String, CharacterState> = GameState.initial().characters): Plan {
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
      if (kind == Kind.ENTITY || kind == Kind.FINAL_ENTITY) allowed += "entityKey"
      if (kind == Kind.FOLLOWER || kind == Kind.NPC) allowed += "characterId"
      if (kind == Kind.ENTITY) allowed += "combatRequired"
      if (kind == Kind.FINAL_ENTITY) allowed += setOf("hpMultiplier", "expMultiplier")
      // Ignore non-authoritative AI metadata; it must not block a valid schedule.
      item.remove("storyFacts")
      exactFields(item, allowed)

      val id = string(item, "id", 48)
      require(EVENT_ID.matches(id) && ids.add(id)) { "milestone_event_id_invalid" }
      val turn = integer(item, "turn")
      require(turn in 1..1_000_000) { "milestone_turn_invalid" }
      require(events.isEmpty() || events.last().turn <= turn) { "milestone_turn_order_invalid" }
      val event = string(item, "event", MAX_DESCRIPTION_CHARS)
      val action = string(item, "action", MAX_DESCRIPTION_CHARS)
      val outcome = string(item, "outcome", MAX_DESCRIPTION_CHARS)
      val key = if (kind == Kind.ENTITY || kind == Kind.FINAL_ENTITY) {
        val value = string(item, "entityKey", 80)
        require(ENTITY_KEY.matches(value) && CombatRuntime.supportsEntityKey(value)) {
          "milestone_entity_unknown"
        }
        if (kind == Kind.FINAL_ENTITY) require(integer(item, "hpMultiplier") == FINAL_HP_MULTIPLIER &&
            integer(item, "expMultiplier") == FINAL_EXP_MULTIPLIER) {
          "milestone_final_multiplier_invalid"
        }
        value
      } else null
      require(!item.has("combatRequired") || item.get("combatRequired") is Boolean) {
        "milestone_combat_required_invalid"
      }
      val characterId = if (kind == Kind.FOLLOWER || kind == Kind.NPC) {
        val id = string(item, "characterId", 80)
        val registered = characters[id]
        require(id != CAO_MINH_ID && registered != null) { "milestone_character_unknown" }
        require(kind != Kind.FOLLOWER || registered.metadata["npcType"] == "follower") {
          "milestone_character_not_follower"
        }
        id
      } else null
      events += Event(id, turn, kind, action, event, outcome, key,
        kind == Kind.FINAL_ENTITY || item.optBoolean("combatRequired", false), characterId)
    }

    require(events.last().kind == Kind.FINAL_ENTITY &&
      events.count { it.kind == Kind.FINAL_ENTITY } == 1) {
      "milestone_final_encounter_required"
    }
    require(events.last().turn >= MIN_CHECKPOINT_TURNS) { "milestone_checkpoint_too_short" }
    require(events.filter { it.combatRequired }.groupBy { it.turn }.values.all { it.size == 1 }) {
      "milestone_multiple_combats_same_turn"
    }
    val encounters = events.filter { it.kind == Kind.ENTITY }
    require(encounters.size.toLong() >= minimumEntityEncounters(events.last().turn.toLong())) {
      "milestone_entity_encounters_insufficient"
    }
    // Only Explorer turns count toward the quota; the final Boss is a separate ending.
    require(encounters.map { it.turn }.toSet().size == encounters.size) {
      "milestone_entity_encounters_same_turn"
    }
    require(encounters.all { it.turn < events.last().turn }) { "milestone_entity_boss_turn_conflict" }
    val ending = root.getJSONObject("completion")
    val type = CompletionType.valueOf(string(ending, "type", 24))
    val fields = mutableSetOf("type", "outcome")
    if (type == CompletionType.EXIT_LEVEL) fields += setOf("targetNodeId", "nextCheckpointTotal")
    exactFields(ending, fields)
    require((type == CompletionType.EXIT_LEVEL) == expected.isLast) {
      "milestone_completion_checkpoint_mismatch"
    }
    val completion = if (type == CompletionType.EXIT_LEVEL) {
      val targetId = string(ending, "targetNodeId", 64)
      val target = requireNotNull(LevelRouteRegistry.node(targetId)) { "milestone_exit_target_unknown" }
      require(target.parent != expected.level) { "milestone_exit_same_level" }
      val total = integer(ending, "nextCheckpointTotal")
      // Position validates the supported address/count, including Level 0's
      // fixed twenty CPs. Neither MAIN-only nor immediate route gates apply.
      StoryCheckpointCore.Position(target.parent, 1, total)
      Completion(type, string(ending, "outcome", MAX_DESCRIPTION_CHARS), targetId, total)
    } else Completion(type, string(ending, "outcome", MAX_DESCRIPTION_CHARS))
    return Plan(checkpointId, events, completion)
  }

  /** Install once at Checkpoint activation; never rewrite the authored schedule mid-story. */
  fun installCurrent(state: GameState, raw: String): GameState {
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val plan = parse(raw, position, state.characters)
    val existing = current(state)
    if (existing != null) {
      require(existing.plan == plan) { "milestone_active_plan_conflict" }
      return state
    }
    validateCharactersBeforeAcceptance(state, plan, position.level)
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
    return Active(startTurn, parse(wrapper.getJSONObject("plan").toString(), position, state.characters))
  }

  /**
   * Pre-store the following Checkpoint while its predecessor's final Entity
   * is in active combat. Does NOT activate it or move the Story Checkpoint.
   * The following address comes from the current plan's authored completion.
   */
  fun prepareNextDuringCombat(state: GameState, raw: String): GameState {
    val currentPosition = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val active = requireNotNull(current(state)) { "milestone_current_plan_missing" }
    val next = active.plan.completion.nextAddress(currentPosition)
    val combat = CombatRuntime.active(state)
    val bossId = StoryMilestoneRuntime.bossEncounterId(state)
    require(bossId != null &&
      ((combat?.encounterId == bossId && combat.entityKey == active.plan.finalEncounter.entityKey) ||
        (combat == null && StoryMilestoneRuntime.bossResult(state) == "DEFEATED"))) {
      "milestone_final_combat_required"
    }
    val finalTurn = active.startTurn.toLong() + active.plan.finalEncounter.turn.toLong() - 1L
    require(CoreClientProjection.turnIndex(state).toLong() >= finalTurn) {
      "milestone_final_turn_not_reached"
    }
    val plan = parse(raw, next, state.characters)
    val existing = prepared(state)
    if (existing != null) {
      require(existing == plan) { "milestone_prepared_plan_conflict" }
      return state
    }
    validateCharactersBeforeAcceptance(state, plan, next.level)
    return state.copy(world = state.world + (PREPARED_KEY to plan.toJson().toString()))
  }

  fun prepared(state: GameState): Plan? {
    val raw = state.world[PREPARED_KEY] ?: return null
    val position = requireNotNull(StoryCheckpointCore.current(state)) { "checkpoint_not_initialized" }
    val active = requireNotNull(current(state)) { "milestone_current_plan_missing" }
    val next = active.plan.completion.nextAddress(position)
    return parse(raw, next, state.characters)
  }

  /** Resolve impossible Registry states before accepting a compulsory schedule. */
  private fun validateCharactersBeforeAcceptance(state: GameState, plan: Plan, level: Int) {
    plan.events.filter { it.isCharacterEncounter }.forEach {
      val character = state.characters.getValue(requireNotNull(it.characterId))
      require(character.presence != CharacterPresence.DEAD) {
        "milestone_character_dead"
      }
      character.metadata["encounterLevels"]?.let { allowed ->
        val bounds = allowed.split('-').map(String::toInt)
        require(level in bounds.first()..bounds.last()) { "milestone_character_level_invalid" }
      }
    }
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



