package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** Synthetic Core fixtures only. No AI evidence and no GM-generated event receipts. */
object TestMilestoneTurn {
  /** Supply explicit synthetic scenes even when testing the dispatcher directly. */
  fun dispatch(state: GameState): GameState {
    val due = JSONArray(StoryMilestoneRuntime.dueJson(state))
    return StoryMilestoneRuntime.acceptTurn(state, narration(due).getJSONArray("eventNarrations"))
  }

  /** Test-only GM envelope; never a production fallback. */
  fun narration(due: JSONArray, prelude: String = "TEST_ONLY prose"): JSONObject = JSONObject()
    .put("reply", prelude)
    .put("completedEventIds", JSONArray((0 until due.length()).map { due.getJSONObject(it).getString("id") }))
    .put("eventNarrations", JSONArray().apply {
      for (i in 0 until due.length()) {
        val event = due.getJSONObject(i)
        put(JSONObject().put("eventId", event.getString("id"))
          .put("occurrence", event.getString("action") + ". " + event.getString("event"))
          .put("consequence", event.getString("outcome"))
          .apply { if (event.optString("kind") in listOf("NPC", "FOLLOWER") || event.has("communicationCharacterId"))
            put("communication", JSONObject().put("speakerId", event.optString("characterId", event.optString("communicationCharacterId")))
              .put("text", "TEST_ONLY cô cảnh báo hắn về lối đi nguy hiểm.")) })
      }
    })

  fun commit(core: GameCoreFacade, before: String, candidate: String, action: String,
             prelude: String = "TEST_ONLY prose"): String {
    if (prelude.isBlank()) return core.processValidatedCandidate(before, candidate, action, prelude)
    val due = JSONArray(core.milestoneDueEvents())
    val generated = narration(due, prelude)
    val reply = MilestoneNarrationValidator.validate(generated, due).reply
    return core.processValidatedCandidate(before, candidate, action, reply, generated.toString())
  }

  fun accept(state: GameState, expectedDueJson: String): GameState {
    val expected = JSONArray(expectedDueJson).let { values ->
      (0 until values.length()).map { values.getString(it) }
    }
    require(StoryMilestoneRuntime.due(state).map { it.id } == expected) { "test_expected_due_mismatch" }
    val committed = TestMilestoneTurn.dispatch(state)
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
      LevelRouteRegistry.node(LevelRouteRegistry.currentId(CoreClientProjection.project(JSONObject(), initialized)))!!.parent, 1, StoryCheckpointCore.CHECKPOINTS_PER_LEVEL)
    val seeded = initialized.copy(world = initialized.world + ("story.checkpoint" to JSONObject()
      .put("level", position.level).put("index", position.index).put("total", position.total).toString()))
    val events = events(JSONArray().put(JSONObject().put("id", "boss").put("turn", 12)
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
    values.filter { it.getString("kind") == "FINAL_ENTITY" }.forEach { it.put("turn", StoryMilestonePlan.CHECKPOINT_TURNS) }
    for (index in 1..3) values += JSONObject().put("id", "encounter$index").put("turn", 4 + index * 2)
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
