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
    val overdue = active.plan.events.any { it.turn.toLong() < relative && it.id !in applied }
    require(!overdue) { "milestone_turn_missed" }
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
    state.world.containsKey(RESULT) && StoryMilestonePlan.prepared(state) == null &&
      StoryMilestonePlan.current(state) != null

  private fun appliedIds(state: GameState): Set<String> {
    val raw = state.world[APPLIED] ?: return emptySet()
    val arr = JSONArray(raw)
    return (0 until arr.length()).map { arr.getString(it) }.toSet()
  }

  /** Strictly bind receipts to the Core-authorized event set of this turn. */
  fun acceptTurn(
    state: GameState, receiptJson: String, gmFactsJson: String = "[]"
  ): GameState {
    val required = due(state)
    val receipts = JSONArray(receiptJson)
    val received = (0 until receipts.length()).map { receipts.getString(it) }
    require(received == required.map { it.id }) { "milestone_receipt_mismatch" }
    val updatedMemory = StoryFactLedger.recordTurn(state, required, gmFactsJson)
    if (received.isEmpty()) return updatedMemory

    val previously = appliedIds(state)
    val all = JSONArray().apply {
      previously.sorted().forEach { put(it) }
      received.forEach { put(it) }
    }
    var updated = updatedMemory.copy(world = updatedMemory.world + (APPLIED to all.toString()))
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
    require(RESULT !in state.world) { "milestone_boss_result_duplicate" }
    val result = if (destroyed) "DEFEATED" else "ESCAPED"
    return activateIfReady(state.copy(world = state.world + (RESULT to result)))
  }

  /** Prepared plan and a concluded Combat are both required to promote a CP. */
  fun activateIfReady(state: GameState): GameState {
    if (!state.world.containsKey(RESULT) || CombatRuntime.active(state) != null) return state
    val position = requireNotNull(StoryCheckpointCore.current(state))
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val next = active.plan.nextPosition(position)
    val prepared = StoryMilestonePlan.prepared(state) ?: return state
    require(prepared.checkpointId == next.id) { "milestone_next_checkpoint_mismatch" }
    val world = state.world.toMutableMap()
    listOf("story.milestone.active", "story.milestone.prepared", APPLIED, BOSS, RESULT)
      .forEach { world.remove(it) }
    world["story.checkpoint"] = JSONObject()
      .put("level", next.level).put("index", next.index).put("total", next.total).toString()

    // Exit is an authored Milestone resolution, never a random streak or a
    // WebView set_level operation. Combat and Inventory remain independent.
    val target = active.plan.nextNode()
    if (target != null) {
      val oldNode = JSONObject(state.world["levelJson"] ?: "{}")
      world["story.levelExitLast"] = JSONObject()
        .put("fromCheckpoint", position.id)
        .put("fromNodeId", oldNode.optString("id", position.level.toString()))
        .put("targetNodeId", target.id)
        .put("outcome", requireNotNull(active.plan.completion.outcome))
        .put("combatResult", state.world[RESULT])
        .toString()
      world["levelJson"] = target.asJson().toString()
      world["location"] = target.location()
      world["title"] = target.title()
      val flags = JSONObject(world["flagsJson"] ?: "{}")
      flags.put("currentLevel", target.asJson())
      flags.put("entityEncounterKey", "")
      world["flagsJson"] = flags.toString()
    }
    val switched = state.copy(world = world)
    return StoryMilestonePlan.installCurrent(switched, prepared.toJson().toString())
  }

  fun bossEncounterId(state: GameState): String? = state.world[BOSS]
  fun bossResult(state: GameState): String? = state.world[RESULT]
}
