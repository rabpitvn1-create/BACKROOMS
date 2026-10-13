package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative, saved checkpoint dispatcher. Never advances on a narrative
 * claim alone: receipt IDs are checked against the due events and stored with
 * the accepted Game Core turn. Only FINAL_ENTITY starts a special Combat.
 */
object StoryMilestoneRuntime {
  private const val APPLIED = "story.milestone.applied"
  private const val BOSS = "story.milestone.bossEncounter"
  private const val RESULT = "story.milestone.bossResult"
  private const val EVIDENCE = "story.milestone.evidence"
  private const val STATS = "story.stats"
  private const val CP_HISTORY = "story.checkpointHistory"
  private const val LEVEL_HISTORY = "story.levelHistory"

  fun statsJson(state: GameState): JSONObject = JSONObject(state.world[STATS] ?: """
    {"version":1,"committedEvents":0,"defeatedBosses":0,"completedCheckpoints":0,"completedLevels":0}
  """)

  private fun incrementStats(state: GameState, vararg deltas: Pair<String, Long>): GameState {
    val stats = statsJson(state)
    deltas.forEach { (key, amount) -> stats.put(key, Math.addExact(stats.getLong(key), amount)) }
    return state.copy(world = state.world + (STATS to stats.toString()))
  }

  fun evidenceRequest(state: GameState, narrative: String, factsJson: String = "[]"): JSONObject {
    require(narrative.length <= 32000 && factsJson.length <= 5000) { "story_evidence_payload_too_large" }
    val required = due(state)
    val facts = JSONArray(factsJson)
    require(facts.length() <= 6) { "story_proposal_count_invalid" }
    val factClaims = JSONArray()
    required.forEach { event ->
      val authored = event.toJson().optJSONArray("storyFacts") ?: JSONArray()
      for (i in 0 until authored.length()) factClaims.put(JSONObject()
        .put("sourceId", "MS:${event.id}:$i").put("claim", authored.getJSONObject(i)))
    }
    for (i in 0 until facts.length()) {
      val claim = facts.getJSONObject(i)
      val item = JSONObject().put("sourceId", "GM:$i").put("claim", claim)
      if (claim.optString("op") == "RESOLVE") {
        val open = state.world["story.factLedger"]?.let { JSONObject(it).getJSONArray("open") } ?: JSONArray()
        val ref = claim.optString("referenceId")
        for (j in 0 until open.length()) if (open.getJSONObject(j).optString("id") == ref)
          item.put("originalFact", open.getJSONObject(j))
      }
      factClaims.put(item)
    }
    val request = JSONObject()
      .put("runToken", state.world["story.runToken"].orEmpty())
      .put("checkpointId", StoryCheckpointCore.current(state)?.id.orEmpty())
      .put("turn", CoreClientProjection.turnIndex(state))
      .put("events", JSONArray().apply { required.forEach { put(it.toJson()) } })
      .put("facts", factClaims).put("narrative", narrative)
    return request.put("requestToken", digest(request.toString()))
  }

  /** Evidence is supplied by the separate validator, never the narrating GM. */
  fun validateEvidence(state: GameState, narrative: String, evidenceJson: String, factsJson: String = "[]"): JSONObject {
    val request = evidenceRequest(state, narrative, factsJson)
    val required = request.getJSONArray("events")
    val requiredFacts = request.getJSONArray("facts")
    if (required.length() == 0 && requiredFacts.length() == 0) return JSONObject()
      .put("verified", true).put("events", JSONArray()).put("facts", JSONArray())
    require(narrative.isNotBlank() && evidenceJson.length <= 65536) { "story_evidence_required" }
    val proof = JSONObject(evidenceJson)
    require(proof.keys().asSequence().toSet() == setOf("verified", "requestToken", "events", "facts") &&
      proof.opt("verified") == true && proof.opt("requestToken") == request.getString("requestToken")) {
      "story_evidence_not_verified_or_stale"
    }
    fun quote(item: JSONObject, key: String) {
      val value = item.opt(key)
      require(value is String && value.length in 1..700 && narrative.contains(value)) {
        "story_evidence_quote_not_in_accepted_narrative"
      }
    }
    val events = proof.getJSONArray("events")
    require(events.length() == required.length()) { "milestone_evidence_count_mismatch" }
    for (i in 0 until events.length()) {
      val item = events.getJSONObject(i)
      require(item.keys().asSequence().toSet() == setOf("id", "actionQuote", "eventQuote", "outcomeQuote") &&
        item.opt("id") == required.getJSONObject(i).getString("id")) { "milestone_evidence_event_mismatch" }
      listOf("actionQuote", "eventQuote", "outcomeQuote").forEach { quote(item, it) }
    }
    val factProofs = proof.getJSONArray("facts")
    require(factProofs.length() == requiredFacts.length()) { "story_fact_evidence_count_mismatch" }
    for (i in 0 until factProofs.length()) {
      val item = factProofs.getJSONObject(i)
      require(item.keys().asSequence().toSet() == setOf("sourceId", "quote") &&
        item.opt("sourceId") == requiredFacts.getJSONObject(i).getString("sourceId")) { "story_fact_evidence_mismatch" }
      quote(item, "quote")
    }
    return proof
  }

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
    return plan.events.count { it.isEntityEncounter && it.id in applied }
  }

  /** Strictly bind receipts to the Core-authorized event set of this turn. */
  fun acceptTurn(state: GameState, receiptJson: String, narrative: String = "", evidenceJson: String = "{}", gmFactsJson: String = "[]"): GameState {
    val required = due(state)
    val receipts = JSONArray(receiptJson)
    val received = (0 until receipts.length()).map { receipts.getString(it) }
    require(received == required.map { it.id }) { "milestone_receipt_mismatch" }
    val proof = validateEvidence(state, narrative, evidenceJson, gmFactsJson)
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
        BOSS to fight.encounterId,
        "flagsJson" to flags.toString()
      ))
    }
    val remembered = StoryFactLedger.recordTurn(working, required, gmFactsJson, narrative, evidenceJson)
    if (received.isEmpty()) return remembered

    val previously = appliedIds(state)
    val all = JSONArray().apply {
      previously.sorted().forEach { put(it) }
      received.forEach { put(it) }
    }
    val proofs = JSONObject(state.world[EVIDENCE] ?: "{}")
    for (i in received.indices) proofs.put(received[i], JSONObject(proof.getJSONArray("events").getJSONObject(i).toString())
      .put("checkpointId", StoryCheckpointCore.current(state)!!.id)
      .put("turn", CoreClientProjection.turnIndex(state)).put("requestToken", proof.getString("requestToken"))
      .apply { required[i].takeIf { it.isEntityEncounter }?.let { put("encounterId", entityEncounterId(working, it)) } })
    var updated = remembered.copy(world = remembered.world + mapOf(APPLIED to all.toString(), EVIDENCE to proofs.toString()))
    updated = incrementStats(updated, "committedEvents" to received.size.toLong())
    return updated
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
    val turnCount = CoreClientProjection.turnIndex(state).toLong() - active.startTurn.toLong() + 1L
    require(turnCount >= StoryMilestonePlan.MIN_CHECKPOINT_TURNS) { "milestone_checkpoint_turn_quota_unmet" }
    require(entityEncounterCount(state) >= StoryMilestonePlan.MIN_ENTITY_ENCOUNTERS) {
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
    val proof = JSONObject(world.getValue(EVIDENCE))
    val refs = JSONArray().apply {
      active.plan.events.forEach { event ->
        put("MS:${position.id}:${proof.getJSONObject(event.id).getInt("turn")}:${event.id}")
      }
    }
    val checkpoints = JSONArray(world[CP_HISTORY] ?: "[]")
    require((0 until checkpoints.length()).none { checkpoints.getJSONObject(it).getString("id") == completionId }) {
      "milestone_checkpoint_history_duplicate"
    }
    checkpoints.put(JSONObject().put("id", completionId).put("visitId", visitId)
      .put("checkpointId", position.id).put("startTurn", active.startTurn)
      .put("endTurn", CoreClientProjection.turnIndex(state)).put("turnCount", turnCount)
      .put("entityEncounterCount", encounterRefs.length()).put("entityEncounterRefs", encounterRefs).put("eventRefs", refs)
      .put("bossEncounterId", state.world[BOSS]).put("bossResult", "DEFEATED")
      .put("completion", active.plan.completion.toJson()))
    world[CP_HISTORY] = checkpoints.toString()
    listOf("story.milestone.active", "story.milestone.prepared", APPLIED, EVIDENCE, BOSS, RESULT)
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
      val open = world["story.factLedger"]?.let { JSONObject(it).getJSONArray("open") } ?: JSONArray()
      val continuing = JSONArray().apply {
        for (i in 0 until open.length()) put(open.getJSONObject(i).getString("id"))
      }
      visits.put(JSONObject().put("id", visitId).put("level", position.level)
        .put("endedBy", completionId).put("turn", CoreClientProjection.turnIndex(state))
        .put("checkpointRefs", JSONArray().apply { visitCheckpoints.forEach { put(it.getString("id")) } })
        .put("committedEventCount", visitCheckpoints.sumOf { it.getJSONArray("eventRefs").length() })
        .put("continuingFactIds", continuing).put("completion", active.plan.completion.toJson()))
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
    val proof = JSONObject(state.world[EVIDENCE] ?: "{}")
    require(proof.keys().asSequence().toSet() == applied) { "milestone_saved_evidence_mismatch" }
    require(applied.all { id -> active?.plan?.events?.any { it.id == id } == true }) {
      "milestone_saved_event_unknown"
    }
    applied.forEach { id ->
      val item = proof.getJSONObject(id)
      require(item.getString("checkpointId") == active!!.plan.checkpointId &&
        item.getInt("turn").toLong() == active.startTurn.toLong() + active.plan.events.single { it.id == id }.turn - 1L && item.getString("requestToken").length == 64) {
        "milestone_saved_evidence_invalid"
      }
      val event = active.plan.events.single { it.id == id }
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
    val ledger = state.world["story.factLedger"]?.let(::JSONObject)
    val committedIds = hashSetOf<String>()
    val committedEncounters = hashMapOf<String, String>()
    if (ledger != null) for (name in listOf("history", "archive")) {
      val entries = ledger.getJSONArray(name)
      for (i in 0 until entries.length()) {
        val id = entries.getJSONObject(i).getString("id")
        if (id.startsWith("MS:")) {
          committedIds += id
          val entry = entries.getJSONObject(i)
          if (entry.optString("kind") == "ENTITY") committedEncounters[id] = entry.getString("entityEncounterId")
        }
      }
    }
    for (i in 0 until checkpoints.length()) {
      val entry = checkpoints.getJSONObject(i)
      val refs = entry.getJSONArray("eventRefs")
      events += refs.length()
      val turns = entry.getLong("endTurn") - entry.getLong("startTurn") + 1L
      val encounters = entry.getJSONArray("entityEncounterRefs")
      val encounterIds = (0 until encounters.length()).map { encounters.getString(it) }
      val sourceEncounters = (0 until refs.length()).mapNotNull { committedEncounters[refs.getString(it)] }
      require(turns >= StoryMilestonePlan.MIN_CHECKPOINT_TURNS && entry.getLong("turnCount") == turns &&
        entry.getInt("entityEncounterCount") == encounters.length() &&
        encounters.length() >= StoryMilestonePlan.MIN_ENTITY_ENCOUNTERS &&
        encounterIds.size == encounterIds.toSet().size && encounterIds == sourceEncounters &&
        encounterIds.last() == entry.getString("bossEncounterId")) { "story_history_checkpoint_quota_invalid" }
      require(entry.getString("bossResult") == "DEFEATED" &&
        state.metadata.containsKey("exp.awarded.${entry.getString("bossEncounterId")}")) {
        "story_history_defeat_unverified"
      }
      for (j in 0 until refs.length()) {
        val id = refs.getString(j)
        require(id in committedIds) { "story_history_event_source_missing" }
        allRefs += id
      }
    }
    require(allRefs.size == allRefs.toSet().size && committedIds.size.toLong() == events) {
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


