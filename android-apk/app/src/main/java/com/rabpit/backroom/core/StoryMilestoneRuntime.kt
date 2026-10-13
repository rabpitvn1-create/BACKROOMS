package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative, saved checkpoint dispatcher. Events are read from the
 * installed SOL plan on each committed Core turn, never from GM claims.
 * Only FINAL_ENTITY starts the special Boss Combat.
 */
object StoryMilestoneRuntime {
  private const val APPLIED = "story.milestone.applied"
  private const val BOSS = "story.milestone.bossEncounter"
  private const val RESULT = "story.milestone.bossResult"
  private const val EVENT_RECORDS = "story.milestone.eventRecords"
  private const val STATS = "story.stats"
  private const val CP_HISTORY = "story.checkpointHistory"
  private const val LEVEL_HISTORY = "story.levelHistory"

  fun statsJson(state: GameState): JSONObject = JSONObject(state.world[STATS] ?: """
    {"version":1,"committedEvents":0,"defeatedBosses":0,"completedCheckpoints":0,"completedLevels":0}
  """)
  
  /** History for GM/planner from committed SaveState events, not a parallel ledger. */
  fun continuityContext(state: GameState): String {
    val recent = java.util.ArrayDeque<JSONObject>()
    fun remember(item: JSONObject) {
      recent.addLast(item)
      if (recent.size > 12) recent.removeFirst()
    }
    val checkpoints = JSONArray(state.world[CP_HISTORY] ?: "[]")
    for (i in 0 until checkpoints.length()) {
      val checkpoint = checkpoints.getJSONObject(i)
      val committed = checkpoint.getJSONArray("events")
      for (j in 0 until committed.length()) {
        remember(JSONObject(committed.getJSONObject(j).toString())
          .put("checkpointId", checkpoint.getString("checkpointId")))
      }
    }
    val active = StoryMilestonePlan.current(state)
    if (active != null) {
      val records = JSONObject(state.world[EVENT_RECORDS] ?: "{}")
      for (event in active.plan.events) {
        if (!records.has(event.id)) continue
        remember(event.toJson().put("checkpointId", active.plan.checkpointId)
          .put("turn", records.getJSONObject(event.id).getInt("turn")))
      }
    }
    return JSONObject().put("recentCommittedEvents", JSONArray().apply { recent.forEach { put(it) } })
      .apply {
        state.world["story.milestone.lastCompletion"]?.let { put("lastCompletion", JSONObject(it)) }
      }.toString()
  }

  private fun incrementStats(state: GameState, vararg deltas: Pair<String, Long>): GameState {
    val stats = statsJson(state)
    deltas.forEach { (key, amount) -> stats.put(key, Math.addExact(stats.getLong(key), amount)) }
    return state.copy(world = state.world + (STATS to stats.toString()))
  }

  /** Milestone events are committed by Core, with no AI evidence verifier. */
  private fun digest(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

  fun due(state: GameState): List<StoryMilestonePlan.Event> {
    val active = StoryMilestonePlan.current(state) ?: return emptyList()
    val now = CoreClientProjection.turnIndex(state)
    val applied = appliedIds(state)
    val relative = now.toLong() - active.startTurn.toLong() + 1L
    if (relative < 1L) return emptyList()
    require(active.plan.events.none { it.turn.toLong() < relative && it.id !in applied }) {
      "milestone_missed_turn_restore_checkpoint"
    }
    return active.plan.events.filter { it.turn.toLong() == relative && it.id !in applied }
  }

  fun dueJson(state: GameState): String = JSONArray().apply {
    due(state).forEach { put(it.toJson()) }
  }.toString()

  /** Count only plans accepted into Core: completed CPs, current plan and prefetch. */
  fun generatedPlanCount(state: GameState): Int =
    JSONArray(state.world[CP_HISTORY] ?: "[]").length() +
      (if (StoryMilestonePlan.current(state) != null) 1 else 0) +
      (if (StoryMilestonePlan.prepared(state) != null) 1 else 0)

  fun hasPlan(state: GameState): Boolean = StoryMilestonePlan.current(state) != null

  fun hasUnresolvedBoss(state: GameState): Boolean =
    state.world.containsKey(BOSS) && !state.world.containsKey(RESULT) &&
      CombatRuntime.active(state) == null

  fun awaitingPlan(state: GameState): Boolean =
    state.world[RESULT] == "DEFEATED" && StoryMilestonePlan.prepared(state) == null

  private fun appliedIds(state: GameState): Set<String> {
    val raw = state.world[APPLIED] ?: return emptySet()
    val arr = JSONArray(raw)
    return (0 until arr.length()).map { arr.getString(it) }.toSet()
  }

  /** Identity is issued by Core; repeated receipts/load/retry keep the same instance. */
  fun entityEncounterId(state: GameState, event: StoryMilestonePlan.Event): String {
    require(event.isEntityEncounter) { "milestone_not_entity_encounter" }
    if (event.kind == StoryMilestonePlan.Kind.FINAL_ENTITY) return requireNotNull(state.world[BOSS])
    val active = requireNotNull(StoryMilestonePlan.current(state))
    return "MSENC:" + digest(JSONObject().put("visit", state.world["story.levelVisitId"] ?: "INITIAL")
      .put("checkpoint", active.plan.checkpointId).put("start", active.startTurn).put("event", event.id).toString())
  }

  fun entityEncounterCount(state: GameState): Int {
    val plan = StoryMilestonePlan.current(state)?.plan ?: return 0
    val applied = appliedIds(state)
    return plan.events.count { it.kind == StoryMilestonePlan.Kind.ENTITY && it.id in applied }
  }

  /** Apply exactly the events authored for this Explorer turn in the installed SOL plan. */
  fun acceptTurn(state: GameState): GameState {
    val required = due(state)
    if (required.isEmpty()) return state
    var working = state
    val boss = required.lastOrNull { it.kind == StoryMilestonePlan.Kind.FINAL_ENTITY }
    if (boss != null) {
      require(CombatRuntime.active(working) == null && BOSS !in working.world) {
        "milestone_boss_already_started"
      }
      working = CombatRuntime.startMilestoneBoss(working, requireNotNull(boss.entityKey))
      val fight = requireNotNull(CombatRuntime.active(working))
      val flags = JSONObject(working.world["flagsJson"] ?: "{}")
        .put("entityEncounterKey", fight.entityKey)
      working = working.copy(world = working.world + mapOf(
        BOSS to fight.encounterId, "flagsJson" to flags.toString()
      ))
    }
    val ordinaryEntity = required.lastOrNull { it.kind == StoryMilestonePlan.Kind.ENTITY }
    if (ordinaryEntity != null) {
      // Present the SOL-authored encounter without letting GM invent or start Combat.
      val flags = JSONObject(working.world["flagsJson"] ?: "{}")
        .put("entityEncounterKey", requireNotNull(ordinaryEntity.entityKey))
      working = working.copy(world = working.world + ("flagsJson" to flags.toString()))
    }
    val applied = JSONArray().apply {
      appliedIds(state).sorted().forEach { put(it) }
      required.forEach { put(it.id) }
    }
    val records = JSONObject(state.world[EVENT_RECORDS] ?: "{}")
    for (event in required) {
      records.put(event.id, JSONObject()
        .put("checkpointId", StoryCheckpointCore.current(state)!!.id)
        .put("turn", CoreClientProjection.turnIndex(state))
        .apply {
          if (event.isEntityEncounter) put("encounterId", entityEncounterId(working, event))
        })
    }
    val updated = working.copy(world = working.world +
      mapOf(APPLIED to applied.toString(), EVENT_RECORDS to records.toString()))
    return incrementStats(updated, "committedEvents" to required.size.toLong())
  }

  /** Called only after the actual Combat Core confirms an encounter ended. */
  fun afterCombat(
    state: GameState, endedEncounterId: String, destroyed: Boolean, escaped: Boolean
  ): GameState {
    if (state.world[BOSS] != endedEncounterId) return state
    require(!escaped) { "milestone_boss_escape_forbidden" }
    if (!destroyed) return state
    require(CombatRuntime.active(state) == null) { "milestone_combat_not_concluded" }
    if (destroyed) require(state.metadata.containsKey("exp.awarded.$endedEncounterId")) {
      "milestone_defeat_not_verified"
    }
    val result = "DEFEATED"
    if (state.world[RESULT] == result) return state
    require(RESULT !in state.world) { "milestone_boss_result_conflict" }
    var concluded = state.copy(world = state.world + (RESULT to result))
    if (destroyed) concluded = incrementStats(concluded, "defeatedBosses" to 1L)
    return activateIfReady(concluded)
  }

  /** Prepared plan and a concluded Combat are both required to promote a CP. */
  fun activateIfReady(state: GameState): GameState {
    if (state.world[RESULT] != "DEFEATED" || CombatRuntime.active(state) != null) return state
    val position = requireNotNull(StoryCheckpointCore.current(state))
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val next = active.plan.completion.nextAddress(position)
    val prepared = StoryMilestonePlan.prepared(state) ?: return state
    require(prepared.checkpointId == next.id) { "milestone_next_checkpoint_mismatch" }
    require(appliedIds(state) == active.plan.events.map { it.id }.toSet()) {
      "milestone_checkpoint_events_incomplete"
    }
    // turnIndex points to the NEXT Explorer turn after the final authored turn.
    // Combat turns never advance it and the Boss does not consume a new Explorer turn.
    val turnCount = CoreClientProjection.turnIndex(state).toLong() - active.startTurn.toLong()
    require(turnCount >= active.plan.finalEncounter.turn && turnCount >= StoryMilestonePlan.MIN_CHECKPOINT_TURNS) {
      "milestone_checkpoint_turn_quota_unmet"
    }
    require(entityEncounterCount(state).toLong() >= StoryMilestonePlan.minimumEntityEncounters(turnCount)) {
      "milestone_checkpoint_entity_quota_unmet"
    }
    val encounterRefs = JSONArray().apply {
      active.plan.events.filter { it.isEntityEncounter }.forEach { put(entityEncounterId(state, it)) }
    }
    require((0 until encounterRefs.length()).map { encounterRefs.getString(it) }.toSet().size == encounterRefs.length()) {
      "milestone_checkpoint_encounter_duplicate"
    }
    val world = state.world.toMutableMap()
    val visitId = world["story.levelVisitId"] ?: "INITIAL:${position.level}"
    val completionId = "${state.world[BOSS]}:${position.id}"
    val records = JSONObject(world.getValue(EVENT_RECORDS))
    val refs = JSONArray().apply {
      active.plan.events.forEach { event ->
        put("MS:${position.id}:${records.getJSONObject(event.id).getInt("turn")}:${event.id}")
      }
    }
    // Persist the committed milestones in the existing Checkpoint history.
    val committed = JSONArray().apply {
      active.plan.events.forEach { event ->
        val record = records.getJSONObject(event.id)
        val turn = record.getInt("turn")
        val id = "MS:${position.id}:$turn:${event.id}"
        put(JSONObject().put("id", id).put("eventId", event.id)
          .put("turn", turn).put("kind", event.kind.name)
          .put("action", event.action).put("event", event.event).put("outcome", event.outcome)
          .apply {
            if (event.isEntityEncounter) {
              put("entityKey", requireNotNull(event.entityKey))
              put("encounterId", record.getString("encounterId"))
            }
          })
      }
    }
    val checkpoints = JSONArray(world[CP_HISTORY] ?: "[]")
    require((0 until checkpoints.length()).none { checkpoints.getJSONObject(it).getString("id") == completionId }) {
      "milestone_checkpoint_history_duplicate"
    }
    checkpoints.put(JSONObject().put("id", completionId).put("visitId", visitId)
      .put("checkpointId", position.id).put("startTurn", active.startTurn)
      .put("endTurn", CoreClientProjection.turnIndex(state) - 1).put("turnCount", turnCount)
      .put("entityEncounterCount", entityEncounterCount(state)).put("entityEncounterRefs", encounterRefs)
      .put("eventRefs", refs).put("events", committed)
      .put("bossEncounterId", state.world[BOSS]).put("bossResult", "DEFEATED")
      .put("completion", active.plan.completion.toJson()))
    world[CP_HISTORY] = checkpoints.toString()
    listOf("story.milestone.active", "story.milestone.prepared", APPLIED, EVENT_RECORDS, BOSS, RESULT)
      .forEach(world::remove)
    world["story.checkpoint"] = JSONObject()
      .put("level", next.level).put("index", next.index).put("total", next.total).toString()
    if (active.plan.completion.type == StoryMilestonePlan.CompletionType.EXIT_LEVEL) {
      val target = requireNotNull(LevelRouteRegistry.node(active.plan.completion.targetNodeId))
      world["levelJson"] = target.asJson().toString()
      world["location"] = target.location()
      world["title"] = target.title()
      val visits = JSONArray(world[LEVEL_HISTORY] ?: "[]")
      val visitCheckpoints = (0 until checkpoints.length()).map { checkpoints.getJSONObject(it) }
        .filter { it.getString("visitId") == visitId }
      visits.put(JSONObject().put("id", visitId).put("level", position.level)
        .put("endedBy", completionId).put("turn", CoreClientProjection.turnIndex(state))
        .put("checkpointRefs", JSONArray().apply { visitCheckpoints.forEach { put(it.getString("id")) } })
        .put("committedEventCount", visitCheckpoints.sumOf { it.getJSONArray("eventRefs").length() })
        .put("completion", active.plan.completion.toJson()))
      world[LEVEL_HISTORY] = visits.toString()
      world["story.levelVisitId"] = "LEVEL:$completionId:${target.id}"
      val flags = JSONObject(world["flagsJson"] ?: "{}")
      flags.put("currentLevel", target.asJson()).put("entityEncounterKey", "")
      // Old Exit readiness has no authority in the newly authored Level.
      flags.optJSONObject("exploration")?.let { exploration ->
        exploration.put(HiddenExitStreak.STREAK_KEY, 0).put("exitReady", false).put("transitionReady", false)
          .put("levelTurns", 0).put(SnapshotSceneProgress.STEP_KEY, 0)
        listOf("confirmedExit", "exitCandidate", "exitProgress").forEach(exploration::remove)
      }
      flags.remove("exitProgress")
      flags.remove("exitChanceThreshold")
      world["flagsJson"] = flags.toString()
    }
    world["story.milestone.lastCompletion"] = JSONObject()
      .put("id", completionId)
      .put("checkpointId", position.id).put("turn", CoreClientProjection.turnIndex(state))
      .put("bossEncounterId", state.world[BOSS]).put("bossResult", "DEFEATED")
      .put("completion", active.plan.completion.toJson()).toString()
    var switched = incrementStats(state.copy(world = world), "completedCheckpoints" to 1L)
    if (active.plan.completion.type == StoryMilestonePlan.CompletionType.EXIT_LEVEL)
      switched = incrementStats(switched, "completedLevels" to 1L)
    return StoryMilestonePlan.installCurrent(switched, prepared.toJson().toString())
  }

  fun bossEncounterId(state: GameState): String? = state.world[BOSS]
  fun bossResult(state: GameState): String? = state.world[RESULT]

  fun validateStoredState(state: GameState) {
    val active = StoryMilestonePlan.current(state)
    StoryMilestonePlan.prepared(state)
    val applied = appliedIds(state)
    val records = JSONObject(state.world[EVENT_RECORDS] ?: "{}")
    require(records.keys().asSequence().toSet() == applied) { "milestone_saved_event_records_mismatch" }
    require(applied.all { id -> active?.plan?.events?.any { it.id == id } == true }) {
      "milestone_saved_event_unknown"
    }
    applied.forEach { id ->
      val item = records.getJSONObject(id)
      val event = active!!.plan.events.single { it.id == id }
      require(item.keys().asSequence().toSet() ==
        if (event.isEntityEncounter) setOf("checkpointId", "turn", "encounterId")
        else setOf("checkpointId", "turn")) { "milestone_saved_event_fields_invalid" }
      require(item.getString("checkpointId") == active.plan.checkpointId &&
        item.getInt("turn").toLong() == active.startTurn.toLong() + event.turn - 1L) {
        "milestone_saved_event_record_invalid"
      }
      if (event.isEntityEncounter) require(item.optString("encounterId") == entityEncounterId(state, event)) {
        "milestone_saved_encounter_invalid"
      }
    }
    val stats = statsJson(state)
    require(stats.getInt("version") == 1 && listOf("committedEvents", "defeatedBosses",
      "completedCheckpoints", "completedLevels").all { stats.getLong(it) >= 0 }) { "story_stats_invalid" }
    val checkpoints = JSONArray(state.world[CP_HISTORY] ?: "[]")
    val levels = JSONArray(state.world[LEVEL_HISTORY] ?: "[]")
    require(stats.getLong("completedCheckpoints") == checkpoints.length().toLong() &&
      stats.getLong("completedLevels") == levels.length().toLong()) { "story_history_stats_mismatch" }
    val checkpointIds = (0 until checkpoints.length()).map { checkpoints.getJSONObject(it).getString("id") }
    val levelIds = (0 until levels.length()).map { levels.getJSONObject(it).getString("id") }
    require(checkpointIds.size == checkpointIds.toSet().size && levelIds.size == levelIds.toSet().size) {
      "story_history_id_duplicate"
    }
    var events = applied.size.toLong()
    val allRefs = mutableListOf<String>()
    for (i in 0 until checkpoints.length()) {
      val entry = checkpoints.getJSONObject(i)
      val refs = entry.getJSONArray("eventRefs")
      val committed = entry.getJSONArray("events")
      require(refs.length() == committed.length()) { "story_history_event_source_missing" }
      events += committed.length().toLong()
      val turns = entry.getLong("endTurn") - entry.getLong("startTurn") + 1L
      val encounters = entry.getJSONArray("entityEncounterRefs")
      val encounterIds = (0 until encounters.length()).map { encounters.getString(it) }
      val sourceEncounters = mutableListOf<String>()
      for (j in 0 until committed.length()) {
        val event = committed.getJSONObject(j)
        val ref = refs.getString(j)
        val turn = event.getInt("turn")
        val id = "MS:${entry.getString("checkpointId")}:$turn:${event.getString("eventId")}"
        require(event.getString("id") == ref && ref == id &&
          turn.toLong() in entry.getLong("startTurn")..entry.getLong("endTurn") &&
          event.getString("action").isNotBlank() && event.getString("event").isNotBlank() &&
          event.getString("outcome").isNotBlank()) { "story_history_event_source_invalid" }
        val kind = StoryMilestonePlan.Kind.valueOf(event.getString("kind"))
        if (kind != StoryMilestonePlan.Kind.STORY) {
          require(CombatRuntime.supportsEntityKey(event.getString("entityKey"))) {
            "story_history_entity_unknown"
          }
          sourceEncounters += event.getString("encounterId")
        }
        allRefs += ref
      }
      require(turns >= StoryMilestonePlan.MIN_CHECKPOINT_TURNS && entry.getLong("turnCount") == turns &&
        entry.getInt("entityEncounterCount") == encounters.length() - 1 &&
        (encounters.length() - 1).toLong() >= StoryMilestonePlan.minimumEntityEncounters(turns) &&
        encounterIds.size == encounterIds.toSet().size && encounterIds == sourceEncounters &&
        encounterIds.last() == entry.getString("bossEncounterId")) { "story_history_checkpoint_quota_invalid" }
      require(entry.getString("bossResult") == "DEFEATED" &&
        state.metadata.containsKey("exp.awarded.${entry.getString("bossEncounterId")}")) {
        "story_history_defeat_unverified"
      }
    }
    require(allRefs.size == allRefs.toSet().size && allRefs.size.toLong() + applied.size.toLong() == events) {
      "story_history_events_inconsistent"
    }
    for (i in 0 until levels.length()) {
      val entry = levels.getJSONObject(i)
      val refs = entry.getJSONArray("checkpointRefs")
      for (j in 0 until refs.length()) require(refs.getString(j) in checkpointIds) {
        "story_history_checkpoint_source_missing"
      }
    }
    require(stats.getLong("committedEvents") == events && stats.getLong("defeatedBosses") ==
      checkpoints.length().toLong() + if (state.world[RESULT] == "DEFEATED") 1L else 0L) {
      "story_history_commit_counts_mismatch"
    }
  }
}


