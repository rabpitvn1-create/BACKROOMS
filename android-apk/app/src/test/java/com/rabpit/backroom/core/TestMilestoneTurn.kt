package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** Synthetic Core fixtures only. No AI evidence and no GM-generated event receipts. */
object TestMilestoneTurn {
  fun accept(state: GameState, expectedDueJson: String): GameState {
    val expected = JSONArray(expectedDueJson).let { values ->
      (0 until values.length()).map { values.getString(it) }
    }
    require(StoryMilestoneRuntime.due(state).map { it.id } == expected) { "test_expected_due_mismatch" }
    val committed = StoryMilestoneRuntime.acceptTurn(state)
    // Existing Boss-resolution fixtures explicitly include the player's confirmation.
    val pending = CombatRuntime.pending(committed) ?: return committed
    return CombatRuntime.confirmEncounter(committed, pending.encounterId)
  }
}

/** Quota-compliant synthetic schedules, never a production fallback. */
object TestCheckpointQuota {
  fun events(original: JSONArray): JSONArray {
    val values = (0 until original.length()).map { JSONObject(original.getJSONObject(it).toString()) }.toMutableList()
    values.filter { it.getString("kind") == "FINAL_ENTITY" }.forEach { it.put("turn", 18) }
    for (index in 1..8) values += JSONObject().put("id", "encounter$index").put("turn", 9 + index)
      .put("kind", "ENTITY").put("entityKey", "hound").put("action", "TEST_ONLY Entity approaches $index")
      .put("event", "TEST_ONLY direct Entity encounter $index").put("outcome", "TEST_ONLY Entity moves away $index")
    return JSONArray().apply { values.sortedBy { it.getInt("turn") }.forEach { put(it) } }
  }

  fun reachBoss(initial: GameState): GameState {
    var state = initial
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val target = active.startTurn + active.plan.finalEncounter.turn - 1
    while (CoreClientProjection.turnIndex(state) < target) {
      val ids = JSONArray().apply { StoryMilestoneRuntime.due(state).forEach { put(it.id) } }.toString()
      state = CoreClientProjection.advance(TestMilestoneTurn.accept(state, ids))
    }
    return state
  }
}
