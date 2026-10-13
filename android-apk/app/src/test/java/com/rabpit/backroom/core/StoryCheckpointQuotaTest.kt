package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryCheckpointQuotaTest {
  private fun plan(id: String = "CP-L00-01", bossTurn: Int = 50): String {
    val boss = JSONObject().put("id", "boss").put("turn", bossTurn).put("kind", "FINAL_ENTITY")
      .put("action", "TEST_ONLY Boss approaches").put("event", "TEST_ONLY direct Boss encounter")
      .put("outcome", "TEST_ONLY Combat opens").put("entityKey", "hound")
      .put("hpMultiplier", 9).put("expMultiplier", 9)
    val events = TestCheckpointQuota.events(JSONArray().put(boss))
    events.getJSONObject(events.length() - 1).put("turn", bossTurn)
    return JSONObject().put("schemaVersion", 2).put("checkpointId", id).put("events", events)
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY CP ends"))
      .toString()
  }

  private fun initial(raw: String = plan()): GameState = StoryMilestonePlan.installCurrent(
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), raw)

  private fun reject(raw: JSONObject) = assertThrows(Exception::class.java) {
    StoryMilestonePlan.parse(raw.toString(), StoryCheckpointCore.Position(0, 1, 20))
  }

  @Test fun fiftyTurnsAndTwentyDistinctEncounterTurnsAreRequiredByParser() {
    val accepted = StoryMilestonePlan.parse(plan(), StoryCheckpointCore.Position(0, 1, 20))
    assertEquals(50, accepted.finalEncounter.turn)
    assertEquals(20, accepted.events.count { it.isEntityEncounter })
    reject(JSONObject(plan(bossTurn = 49)))
    reject(JSONObject(plan()).apply { getJSONArray("events").remove(0) })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(1).put("turn", 10) })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("entityKey", "fake_entity") })
    reject(JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("hpMultiplier", 9) })
    reject(JSONObject(plan()).apply {
      val item = getJSONArray("events").getJSONObject(0)
      item.put("kind", "STORY").remove("entityKey")
    })
    assertEquals(51, StoryMilestonePlan.parse(plan(bossTurn = 51),
      StoryCheckpointCore.Position(0, 1, 20)).finalEncounter.turn)
  }

  @Test fun onlyAcceptedEncountersCountAndReloadOrDuplicateReceiptDoesNotIncreaseCount() {
    val planned = initial()
    assertEquals(0, StoryMilestoneRuntime.entityEncounterCount(planned))
    val beforeBoss = TestCheckpointQuota.reachBoss(planned)
    assertEquals(50, CoreClientProjection.turnIndex(beforeBoss))
    assertEquals(19, StoryMilestoneRuntime.entityEncounterCount(beforeBoss))
    assertNull(CombatRuntime.active(beforeBoss))
    val boss = TestMilestoneEvidence.accept(beforeBoss, "[\"boss\"]")
    assertEquals(20, StoryMilestoneRuntime.entityEncounterCount(boss))
    val restored = GameStateCodec.decode(GameStateCodec.encode(boss))
    StoryMilestoneRuntime.validateStoredState(restored)
    assertEquals(20, StoryMilestoneRuntime.entityEncounterCount(restored))
    assertThrows(Exception::class.java) { TestMilestoneEvidence.accept(restored, "[\"boss\"]") }
    assertEquals(restored, TestMilestoneEvidence.accept(restored, "[]"))
    // Manual restore changes response leases, but must not change committed encounter identities.
    StoryMilestoneRuntime.validateStoredState(restored.copy(world = restored.world +
      ("story.runToken" to "TEST_ONLY new manual-load lease")))
    val ledger = JSONObject(restored.world.getValue("story.factLedger"))
    val history = ledger.getJSONArray("history")
    val ids = (0 until history.length()).map { history.getJSONObject(it).getString("entityEncounterId") }
    assertEquals(20, ids.toSet().size)
  }

  @Test fun completionChecksActualTurnAndHistoryKeepsVerifiedEncounterSources() {
    val boss = TestMilestoneEvidence.accept(TestCheckpointQuota.reachBoss(initial()), "[\"boss\"]")
    val staged = StoryMilestonePlan.prepareNextDuringCombat(boss, plan("CP-L00-02"))
    val fight = CombatRuntime.active(staged)!!
    val defeated = CombatRuntime.resolve(staged.copy(metadata = staged.metadata +
      ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY observe")
    val concluded = defeated.state.copy(world = defeated.state.world +
      ("story.milestone.bossResult" to "DEFEATED"))
    // Forged rewind cannot use the already confirmed Boss to bypass fifty turns.
    val early = concluded.copy(metadata = concluded.metadata + ("core.viewTurn" to "49"))
    assertEquals(49, CoreClientProjection.turnIndex(early))
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.activateIfReady(early) }
    val next = StoryMilestoneRuntime.afterCombat(defeated.state, fight.encounterId, true, false)
    assertEquals("CP-L00-02", StoryCheckpointCore.current(next)!!.id)
    val history = JSONArray(next.world.getValue("story.checkpointHistory"))
    assertEquals(50L, history.getJSONObject(0).getLong("turnCount"))
    assertEquals(20, history.getJSONObject(0).getInt("entityEncounterCount"))
    StoryMilestoneRuntime.validateStoredState(next)
    val brokenHistory = JSONArray(history.toString()).apply {
      getJSONObject(0).getJSONArray("entityEncounterRefs").put(0, "TEST_ONLY invented encounter")
    }
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.validateStoredState(next.copy(world = next.world +
      ("story.checkpointHistory" to brokenHistory.toString()))) }
  }
}
