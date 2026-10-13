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
  /** Explicit accepted schedules for facade tests; never a production fallback. */
  fun withPlan(state: GameState, scheduled: List<JSONObject> = emptyList()): GameState {
    val initialized = CoreClientProjection.initialize(state)
    val position = StoryCheckpointCore.current(initialized) ?: StoryCheckpointCore.Position(
      LevelRouteRegistry.node(LevelRouteRegistry.currentId(CoreClientProjection.project(JSONObject(), initialized)))!!.parent, 1, 20)
    val seeded = initialized.copy(world = initialized.world + ("story.checkpoint" to JSONObject()
      .put("level", position.level).put("index", position.index).put("total", position.total).toString()))
    val events = events(JSONArray().put(JSONObject().put("id", "boss").put("turn", 18)
      .put("kind", "FINAL_ENTITY").put("entityKey", "hound").put("action", "Boss xuất hiện")
      .put("event", "Boss chặn lối").put("outcome", "Chờ Battle Start").put("hpMultiplier", 9).put("expMultiplier", 9)))
    val values = (0 until events.length()).map { events.getJSONObject(it) } + scheduled
    val raw = JSONObject().put("schemaVersion", 2).put("checkpointId", position.id)
      .put("events", JSONArray(values.sortedBy { it.getInt("turn") }))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "Qua CP")).toString()
    return StoryMilestonePlan.installCurrent(seeded, raw)
  }

  fun events(original: JSONArray): JSONArray {
    val values = (0 until original.length()).map { JSONObject(original.getJSONObject(it).toString()) }.toMutableList()
    values.filter { it.getString("kind") == "FINAL_ENTITY" }.forEach { it.put("turn", 18) }
    for (index in 1..8) values += JSONObject().put("id", "encounter$index").put("turn", 9 + index)
      .put("kind", "ENTITY").put("entityKey", "hound").put("combatRequired", false).put("action", "TEST_ONLY Entity approaches $index")
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
