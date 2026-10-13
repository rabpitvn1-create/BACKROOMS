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
  fun storyArc(): String = ("TEST_ONLY Cao Minh tham gia mot chuong truyen Backrooms co tiep dien tu cac su kien truoc. " +
    "Anh phai tiep tuc tim manh moi va tuong tac voi cac nhan vat, trong khi cac tran Entity dien ra. ").repeat(3).take(500)

  /** Explicit accepted schedules for facade tests; never a production fallback. */
  fun withPlan(state: GameState, scheduled: List<JSONObject> = emptyList()): GameState {
    val initialized = CoreClientProjection.initialize(state)
    val position = StoryCheckpointCore.current(initialized) ?: StoryCheckpointCore.Position(
      LevelRouteRegistry.node(LevelRouteRegistry.currentId(CoreClientProjection.project(JSONObject(), initialized)))!!.parent, 1, 20)
    val seeded = initialized.copy(world = initialized.world + ("story.checkpoint" to JSONObject()
      .put("level", position.level).put("index", position.index).put("total", position.total).toString()))
    val events = events(JSONArray().put(JSONObject().put("id", "boss").put("turn", 50)
      .put("kind", "FINAL_ENTITY").put("entityKey", "hound").put("action", "Boss xuất hiện")
      .put("event", "Boss chặn lối").put("outcome", "Chờ Battle Start").put("hpMultiplier", 9).put("expMultiplier", 9)))
    val values = (0 until events.length()).map { events.getJSONObject(it) } + scheduled
    val raw = JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION).put("checkpointId", position.id).put("storyArc", storyArc())
      .put("events", JSONArray(values.sortedBy { it.getInt("turn") }))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "Qua CP")).toString()
    return StoryMilestonePlan.installCurrent(seeded, raw)
  }

  fun events(original: JSONArray): JSONArray {
    val values = (0 until original.length()).map { JSONObject(original.getJSONObject(it).toString()) }.toMutableList()
    values.filter { it.getString("kind") == "FINAL_ENTITY" }.forEach { it.put("turn", 50) }
    for (index in 1..20) values += JSONObject().put("id", "encounter$index").put("turn", 24 + index)
      .put("kind", "ENTITY").put("entityKey", "hound").put("action", "TEST_ONLY Entity approaches $index")
      .put("event", "TEST_ONLY direct Entity encounter $index").put("outcome", "TEST_ONLY combat required $index")
    return JSONArray().apply { values.sortedBy { it.getInt("turn") }.forEach { put(it) } }
  }

  fun reachBoss(initial: GameState): GameState {
    var state = initial
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val target = active.startTurn + active.plan.finalEncounter.turn - 1
    while (CoreClientProjection.turnIndex(state) < target) {
      val ids = JSONArray().apply { StoryMilestoneRuntime.due(state).forEach { put(it.id) } }.toString()
      val accepted = TestMilestoneTurn.accept(state, ids)
      val fight = CombatRuntime.active(accepted)
      val settled = if (fight != null) {
        val outcome = CombatRuntime.resolve(accepted.copy(metadata = accepted.metadata +
          ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY instant ordinary Combat victory")
        require(outcome.entityDestroyed) { "test_ordinary_combat_failed" }
        outcome.state
      } else accepted
      state = CoreClientProjection.advance(settled)
    }
    return state
  }
}
