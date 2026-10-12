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
    for (i in 0 until facts.length()) factClaims.put(JSONObject()
      .put("sourceId", "GM:$i").put("claim", facts.getJSONObject(i)))
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

  /** Strictly bind receipts to the Core-authorized event set of this turn. */
  fun acceptTurn(state: GameState, receiptJson: String, narrative: String = "", evidenceJson: String = "{}"): GameState {
    val required = due(state)
    val receipts = JSONArray(receiptJson)
    val received = (0 until receipts.length()).map { receipts.getString(it) }
    require(received == required.map { it.id }) { "milestone_receipt_mismatch" }
    val proof = validateEvidence(state, narrative, evidenceJson)
    if (received.isEmpty()) return state

    val previously = appliedIds(state)
    val all = JSONArray().apply {
      previously.sorted().forEach { put(it) }
      received.forEach { put(it) }
    }
    val proofs = JSONObject(state.world[EVIDENCE] ?: "{}")
    for (i in received.indices) proofs.put(received[i], JSONObject(proof.getJSONArray("events").getJSONObject(i).toString())
      .put("checkpointId", StoryCheckpointCore.current(state)!!.id)
      .put("turn", CoreClientProjection.turnIndex(state)).put("requestToken", proof.getString("requestToken")))
    var updated = state.copy(world = state.world + mapOf(APPLIED to all.toString(), EVIDENCE to proofs.toString()))
    val boss = required.lastOrNull { it.kind == StoryMilestonePlan.Kind.FINAL_ENTITY }
    if (boss != null) {
      require(CombatRuntime.active(updated) == null && BOSS !in updated.world) {
        "milestone_boss_already_started"
      }
      updated = CombatRuntime.startMilestoneBoss(updated, requireNotNull(boss.entityKey))
      val fight = requireNotNull(CombatRuntime.active(updated))
      val flags = JSONObject(updated.world["flagsJson"] ?: "{}")
        .put("entityEncounterKey", fight.entityKey)
      updated = updated.copy(world = updated.world + mapOf(
        BOSS to fight.encounterId,
        "flagsJson" to flags.toString()
      ))
    }
    return updated
  }

  /** Called only after the actual Combat Core confirms an encounter ended. */
  fun afterCombat(
    state: GameState, endedEncounterId: String, destroyed: Boolean, escaped: Boolean
  ): GameState {
    if (state.world[BOSS] != endedEncounterId || (!destroyed && !escaped)) return state
    require(CombatRuntime.active(state) == null) { "milestone_combat_not_concluded" }
    if (destroyed) require(state.metadata.containsKey("exp.awarded.$endedEncounterId")) {
      "milestone_defeat_not_verified"
    }
    val result = if (destroyed) "DEFEATED" else "ESCAPED"
    if (state.world[RESULT] == result) return state
    require(RESULT !in state.world) { "milestone_boss_result_conflict" }
    return activateIfReady(state.copy(world = state.world + (RESULT to result)))
  }

  /** Prepared plan and a concluded Combat are both required to promote a CP. */
  fun activateIfReady(state: GameState): GameState {
    if (state.world[RESULT] != "DEFEATED" || CombatRuntime.active(state) != null) return state
    val position = requireNotNull(StoryCheckpointCore.current(state))
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val next = active.plan.completion.nextAddress(position)
    val prepared = StoryMilestonePlan.prepared(state) ?: return state
    require(prepared.checkpointId == next.id) { "milestone_next_checkpoint_mismatch" }
    val world = state.world.toMutableMap()
    listOf("story.milestone.active", "story.milestone.prepared", APPLIED, EVIDENCE, BOSS, RESULT)
      .forEach(world::remove)
    world["story.checkpoint"] = JSONObject()
      .put("level", next.level).put("index", next.index).put("total", next.total).toString()
    if (active.plan.completion.type == StoryMilestonePlan.CompletionType.EXIT_LEVEL) {
      val target = requireNotNull(LevelRouteRegistry.node(active.plan.completion.targetNodeId))
      world["levelJson"] = target.asJson().toString()
      world["location"] = target.location()
      world["title"] = target.title()
      val flags = JSONObject(world["flagsJson"] ?: "{}")
      flags.put("currentLevel", target.asJson()).put("entityEncounterKey", "")
      // Old Exit readiness has no authority in the newly authored Level.
      flags.optJSONObject("exploration")?.let { exploration ->
        exploration.put(HiddenExitStreak.STREAK_KEY, 0).put("exitReady", false).put("transitionReady", false)
        listOf("confirmedExit", "exitCandidate", "exitProgress").forEach(exploration::remove)
      }
      flags.remove("exitProgress")
      flags.remove("exitChanceThreshold")
      world["flagsJson"] = flags.toString()
    }
    world["story.milestone.lastCompletion"] = JSONObject()
      .put("id", "${state.world[BOSS]}:${position.id}")
      .put("checkpointId", position.id).put("turn", CoreClientProjection.turnIndex(state))
      .put("bossEncounterId", state.world[BOSS]).put("bossResult", "DEFEATED")
      .put("completion", active.plan.completion.toJson()).toString()
    val switched = state.copy(world = world)
    return StoryMilestonePlan.installCurrent(switched, prepared.toJson().toString())
  }

  fun bossEncounterId(state: GameState): String? = state.world[BOSS]
  fun bossResult(state: GameState): String? = state.world[RESULT]

  fun canRetryBoss(state: GameState): Boolean = state.world[RESULT] == "ESCAPED" &&
    state.world.containsKey("story.milestone.suspendedBoss") && CombatRuntime.active(state) == null

  fun retryBoss(state: GameState): GameState {
    require(canRetryBoss(state)) { "milestone_boss_retry_unavailable" }
    val resumed = CombatRuntime.resumeMilestoneBoss(state)
    val fight = requireNotNull(CombatRuntime.active(resumed))
    require(fight.encounterId == state.world[BOSS]) { "milestone_boss_retry_mismatch" }
    val flags = JSONObject(resumed.world["flagsJson"] ?: "{}").put("entityEncounterKey", fight.entityKey)
    return resumed.copy(world = (resumed.world - RESULT) + ("flagsJson" to flags.toString()))
  }

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
        item.getInt("turn") >= active.startTurn && item.getString("requestToken").length == 64) {
        "milestone_saved_evidence_invalid"
      }
    }
  }
}

