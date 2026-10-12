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
  fun acceptTurn(state: GameState, receiptJson: String): GameState {
    val required = due(state)
    val receipts = JSONArray(receiptJson)
    val received = (0 until receipts.length()).map { receipts.getString(it) }
    require(received == required.map { it.id }) { "milestone_receipt_mismatch" }
    if (received.isEmpty()) return state

    val previously = appliedIds(state)
    val all = JSONArray().apply {
      previously.sorted().forEach { put(it) }
      received.forEach { put(it) }
    }
    var updated = state.copy(world = state.world + (APPLIED to all.toString()))
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
    listOf("story.milestone.active", "story.milestone.prepared", APPLIED, BOSS, RESULT)
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
}

