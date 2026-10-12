package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestonePlanTest {
  private fun state(): GameState =
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))

  private fun story(id: String, turn: Int): JSONObject = JSONObject()
    .put("id", id).put("turn", turn).put("kind", "STORY")
    .put("event", "Biến cố $id sẽ xảy ra").put("outcome", "Kết quả $id bắt buộc")

  private fun finale(turn: Int = 8, entity: String = "hound"): JSONObject = JSONObject()
    .put("id", "boss").put("turn", turn).put("kind", "FINAL_ENTITY")
    .put("event", "Hound xuất hiện").put("outcome", "Combat được bắt đầu")
    .put("entityKey", entity).put("hpMultiplier", 9).put("expMultiplier", 9)

  private fun plan(checkpoint: String = "CP-L00-01",
                   entries: JSONArray = JSONArray().put(story("beat1", 1))
                     .put(story("beat2", 3)).put(finale())): String =
    JSONObject().put("schemaVersion", 1).put("checkpointId", checkpoint)
      .put("events", entries).toString()

  private fun reject(raw: String) {
    assertThrows(Exception::class.java) {
      StoryMilestonePlan.parse(raw, StoryCheckpointCore.Position(0, 1, 20))
    }
  }

  @Test fun schedulesCompulsoryEventsAndOneFinalEntityInCheckpointLocalTurns() {
    val scheduled = StoryMilestonePlan.parse(plan(), StoryCheckpointCore.Position(0, 1, 20))
    assertEquals("CP-L00-01", scheduled.checkpointId)
    assertEquals("Kết quả beat2 bắt buộc", scheduled.eventsAt(3).single().outcome)
    assertTrue(scheduled.eventsAt(4).isEmpty())
    assertEquals("hound", scheduled.finalEncounter.entityKey)
    assertEquals(StoryMilestonePlan.Kind.FINAL_ENTITY, scheduled.finalEncounter.kind)
    assertEquals(plan(), scheduled.toJson().toString())
    val refetched = StoryMilestonePlan.parse(scheduled.toJson().toString(),
      StoryCheckpointCore.Position(0, 1, 20))
    assertEquals(scheduled, refetched)
  }

  @Test fun multipleEventsMayHappenAtSameTurnInDeclaredOrder() {
    val entries = JSONArray().put(story("alpha", 2)).put(story("beta", 2)).put(finale(5))
    val parsed = StoryMilestonePlan.parse(plan(entries = entries), StoryCheckpointCore.Position(0, 1, 20))
    assertEquals(listOf("alpha", "beta"), parsed.eventsAt(2).map { it.id })
  }

  @Test fun rejectsChangedResultsInvalidTurnsAndMissingFinale() {
    val entries = JSONArray().put(story("alpha", 3)).put(story("beta", 2)).put(finale())
    reject(plan(entries = entries))
    reject(plan(entries = JSONArray().put(story("same", 1)).put(story("same", 2)).put(finale())))
    reject(plan(entries = JSONArray().put(story("beat1", 1))))
    reject(plan(entries = JSONArray().put(finale(3)).put(story("late", 4))))
    reject(plan(entries = JSONArray().put(finale(2)).put(finale(3))))
    reject(plan(entries = JSONArray().put(story("b", 0)).put(finale())))
    reject(plan(entries = JSONArray().put(story("b", 1).remove("outcome")).put(finale())))
  }

  @Test fun rejectsWrongCheckpointUnknownEntityOrRewardMultiplier() {
    reject(plan(checkpoint = "CP-L00-02"))
    reject(plan(entries = JSONArray().put(finale(entity = "fake_entity"))))
    reject(plan(entries = JSONArray().put(finale().put("hpMultiplier", 8))))
    reject(plan(entries = JSONArray().put(finale().put("expMultiplier", 10))))
    reject(plan(entries = JSONArray().put(finale().put("required", false))))
    reject(JSONObject(plan()).put("schemaVersion", 2).toString())
    reject(JSONObject(plan()).put("extraGMInstructions", "override outcomes").toString())
  }

  @Test fun activePlanIsPersistedAndUsesRelativeNotGlobalTurn() {
    val before = CoreClientProjection.advance(CoreClientProjection.advance(state()))
    assertEquals(3, CoreClientProjection.turnIndex(before))
    val installed = StoryMilestonePlan.installCurrent(before, plan())
    val restored = GameStateCodec.decode(GameStateCodec.encode(installed))
    val active = StoryMilestonePlan.current(restored)!!
    assertEquals(3, active.startTurn)
    assertEquals("beat1", active.eventsAtGlobalTurn(3).single().id)
    assertEquals("beat2", active.eventsAtGlobalTurn(5).single().id)
    assertTrue(active.eventsAtGlobalTurn(2).isEmpty())
    assertTrue(active.eventsAtGlobalTurn(7).isEmpty())
    assertEquals("boss", active.eventsAtGlobalTurn(10).single().id)
    assertEquals(installed, StoryMilestonePlan.installCurrent(installed, plan()))
    val rewritten = JSONObject(plan()).getJSONArray("events")
    rewritten.getJSONObject(1).put("outcome", "Kết quả khác")
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.installCurrent(installed, plan(entries = rewritten))
    }
    val nextTurn = CoreClientProjection.advance(installed)
    assertEquals("CP-L00-01", StoryCheckpointCore.current(nextTurn)!!.id)
    assertEquals(active, StoryMilestonePlan.current(nextTurn))
  }

  @Test fun onlyFinalEntityCombatCanStageFollowingCheckpointPlan() {
    val installed = StoryMilestonePlan.installCurrent(state(), plan())
    val successor = plan(checkpoint = "CP-L00-02")
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.prepareNextDuringCombat(installed, successor)
    }
    val wrong = CombatRuntime.start(installed, "clump")
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.prepareNextDuringCombat(wrong, successor)
    }
    val fighting = CombatRuntime.start(installed, "hound")
    val prepared = StoryMilestonePlan.prepareNextDuringCombat(fighting, successor)
    assertEquals("CP-L00-01", StoryCheckpointCore.current(prepared)!!.id)
    assertEquals("CP-L00-01", StoryMilestonePlan.current(prepared)!!.plan.checkpointId)
    assertEquals("CP-L00-02", StoryMilestonePlan.prepared(prepared)!!.checkpointId)
    assertEquals(prepared, StoryMilestonePlan.prepareNextDuringCombat(prepared, successor))
    assertEquals("CP-L00-02", StoryMilestonePlan.prepared(GameStateCodec.decode(
      GameStateCodec.encode(prepared)))!!.checkpointId)
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.prepareNextDuringCombat(prepared,
        JSONObject(successor).apply { getJSONArray("events").getJSONObject(0)
          .put("outcome", "Changed outcome") }.toString())
    }
  }

  @Test fun cannotSilentlyPlanLevelOneAfterLastLevelZeroCheckpoint() {
    val first = state()
    val atLast = first.copy(world = first.world +
      ("story.checkpoint" to JSONObject().put("level", 0).put("index", 20).put("total", 20).toString()))
    val active = StoryMilestonePlan.installCurrent(atLast, plan(checkpoint = "CP-L00-20"))
    val fighting = CombatRuntime.start(active, "hound")
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.prepareNextDuringCombat(fighting, plan(checkpoint = "CP-L01-01"))
    }
  }
}
