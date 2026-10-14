package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryCheckpointQuotaTest {
  private fun plan(id: String = "CP-L00-01", bossTurn: Int = 12): String {
    val boss = JSONObject().put("id", "boss").put("turn", bossTurn).put("kind", "FINAL_ENTITY")
      .put("action", "TEST_ONLY Boss approaches").put("event", "TEST_ONLY direct Boss encounter")
      .put("outcome", "TEST_ONLY Combat opens").put("entityKey", "hound")
      .put("hpMultiplier", 9).put("expMultiplier", 9)
    val events = TestCheckpointQuota.events(JSONArray().put(boss))
    events.getJSONObject(events.length() - 1).put("turn", bossTurn)
    return JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION).put("checkpointId", id).put("storyArc", TestCheckpointQuota.storyArc()).put("events", events)
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY CP ends"))
      .toString()
  }

  private fun initial(raw: String = plan()): GameState = StoryMilestonePlan.installCurrent(
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), raw)

  private fun reject(raw: JSONObject) = assertThrows(Exception::class.java) {
    StoryMilestonePlan.parse(raw.toString(), StoryCheckpointCore.Position(0, 1, 40))
  }

  @Test fun twelveExplorerTurnsAndThreeMandatoryCombatEncountersAreRequired() {
    val accepted = StoryMilestonePlan.parse(plan(), StoryCheckpointCore.Position(0, 1, 40))
    assertEquals(12, accepted.finalEncounter.turn)
    assertEquals(3, accepted.events.count { it.kind == StoryMilestonePlan.Kind.ENTITY })
    assertTrue(accepted.events.filter { it.kind == StoryMilestonePlan.Kind.ENTITY }.all { it.combatRequired })
    reject(JSONObject(plan(bossTurn = 11)))
    reject(JSONObject(plan()).apply { getJSONArray("events").remove(0) })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(1).put("turn", 25) })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("entityKey", "fake_entity") })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("combatRequired", false) })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("hpMultiplier", 9) })
    reject(JSONObject(plan(bossTurn = 13)))
  }

  @Test fun onlyAcceptedEncountersCountAndReloadOrDuplicateReceiptDoesNotIncreaseCount() {
    val planned = initial()
    assertEquals(0, StoryMilestoneRuntime.entityEncounterCount(planned))
    val beforeBoss = TestCheckpointQuota.reachBoss(planned)
    assertEquals(12, CoreClientProjection.turnIndex(beforeBoss))
    assertEquals(3, StoryMilestoneRuntime.entityEncounterCount(beforeBoss))
    assertNull(CombatRuntime.active(beforeBoss))
    val boss = TestMilestoneTurn.accept(beforeBoss, "[\"boss\"]")
    assertEquals(3, StoryMilestoneRuntime.entityEncounterCount(boss))
    val restored = GameStateCodec.decode(GameStateCodec.encode(boss))
    StoryMilestoneRuntime.validateStoredState(restored)
    assertEquals(3, StoryMilestoneRuntime.entityEncounterCount(restored))
    assertThrows(Exception::class.java) { TestMilestoneTurn.accept(restored, "[\"boss\"]") }
    assertEquals(restored, TestMilestoneTurn.accept(restored, "[]"))
    // Manual restore changes response leases, but must not change committed encounter identities.
    StoryMilestoneRuntime.validateStoredState(restored.copy(world = restored.world +
      ("story.runToken" to "TEST_ONLY new manual-load lease")))
    val records = JSONObject(restored.world.getValue("story.milestone.eventRecords"))
    val ids = records.keys().asSequence().mapNotNull { key ->
      records.getJSONObject(key).optString("encounterId").takeIf { it.isNotBlank() }
    }.toList()
    assertEquals(4, ids.toSet().size)
  }

  @Test fun completionChecksActualTurnAndHistoryKeepsVerifiedEncounterSources() {
    val boss = CoreClientProjection.advance(TestMilestoneTurn.accept(TestCheckpointQuota.reachBoss(initial()), "[\"boss\"]"))
    val staged = StoryMilestonePlan.prepareNextDuringCombat(boss, plan("CP-L00-02"))
    val fight = CombatRuntime.active(staged)!!
    val defeated = CombatRuntime.resolve(staged.copy(metadata = staged.metadata +
      ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY observe")
    val concluded = defeated.state.copy(world = defeated.state.world +
      ("story.milestone.bossResult" to "DEFEATED"))
    // Forged rewind cannot use the already confirmed Boss to bypass 12 Explorer turns.
    val early = concluded.copy(metadata = concluded.metadata + ("core.viewTurn" to "12"))
    assertEquals(12, CoreClientProjection.turnIndex(early))
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.activateIfReady(early) }
    val next = StoryMilestoneRuntime.afterCombat(defeated.state, fight.encounterId, true, false)
    assertEquals("CP-L00-02", StoryCheckpointCore.current(next)!!.id)
    val history = JSONArray(next.world.getValue("story.checkpointHistory"))
    assertEquals(12L, history.getJSONObject(0).getLong("turnCount"))
    assertEquals(12L, history.getJSONObject(0).getLong("endTurn"))
    assertEquals(3, history.getJSONObject(0).getInt("entityEncounterCount"))
    StoryMilestoneRuntime.validateStoredState(next)
    assertFalse(next.world.containsKey("story.factLedger"))
    val committed = history.getJSONObject(0).getJSONArray("events")
    assertEquals(history.getJSONObject(0).getJSONArray("eventRefs").length(), committed.length())
    val brokenHistory = JSONArray(history.toString()).apply {
      getJSONObject(0).getJSONArray("entityEncounterRefs").put(0, "TEST_ONLY invented encounter")
    }
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.validateStoredState(next.copy(world = next.world +
      ("story.checkpointHistory" to brokenHistory.toString()))) }
  }
}
