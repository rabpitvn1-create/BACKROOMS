package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real Core behavior with synthetic SOL-authored event schedules. */
class StoryFactLedgerTest {
  private fun initial(): GameState =
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))

  private fun event(id: String, turn: Int, facts: JSONArray = JSONArray(),
                    kind: String = "STORY"): JSONObject =
    JSONObject().put("id", id).put("turn", turn).put("kind", kind)
      .put("action", "TEST_ONLY scripted action $id")
      .put("event", "TEST_ONLY scripted event $id")
      .put("outcome", "TEST_ONLY scripted outcome $id")
      .apply {
        if (facts.length() > 0) put("storyFacts", facts)
        if (kind == "ENTITY") put("entityKey", "hound")
      }

  private fun fact(kind: String, subject: String, detail: String): JSONObject =
    JSONObject().put("kind", kind).put("subject", subject).put("fact", detail)

  private fun plan(events: JSONArray): String =
    JSONObject().put("schemaVersion", 2).put("checkpointId", "CP-L00-01")
      .put("events", TestCheckpointQuota.events(JSONArray(events.toString())
        .put(event("boss", 18, kind = "FINAL_ENTITY").put("entityKey", "hound")
          .put("hpMultiplier", 9).put("expMultiplier", 9))))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT")
        .put("outcome", "TEST_ONLY checkpoint completion")).toString()

  @Test fun scriptedFactsAreAbsentUntilCoreReachesTheirTurn() {
    val id = "fixture.clue.alpha"
    val script = plan(JSONArray().put(event("beat", 1,
      JSONArray().put(fact("CLUE", id, "TEST_ONLY persistent clue")))))
    val state = StoryMilestonePlan.installCurrent(initial(), script)
    assertEquals(0, JSONObject(StoryFactLedger.context(state)).getInt("openTotal"))
    val committed = StoryMilestoneRuntime.acceptTurn(state)
    assertTrue(StoryFactLedger.context(committed).contains("TEST_ONLY persistent clue"))
    assertEquals(state.characters, committed.characters)
    assertEquals(state.inventories, committed.inventories)
    assertEquals(state.equipment, committed.equipment)
    assertEquals(state.statuses, committed.statuses)
    val stored = GameStateCodec.decode(GameStateCodec.encode(committed))
    StoryFactLedger.validateStoredState(stored)
    assertTrue(StoryFactLedger.context(stored).contains(id))
  }

  @Test fun onlySolCanAuthorPersistentStoryFacts() {
    val scripted = StoryMilestonePlan.installCurrent(initial(), plan(
      JSONArray().put(event("beat", 1))))
    val after = StoryMilestoneRuntime.acceptTurn(scripted)
    assertEquals(0, JSONObject(StoryFactLedger.context(after)).getInt("openTotal"))
    val history = JSONObject(after.world.getValue("story.factLedger")).getJSONArray("history")
    assertEquals(1, history.length())
    assertEquals("MILESTONE", history.getJSONObject(0).getString("source"))
    assertFalse(history.getJSONObject(0).has("verified"))
    assertFalse(history.getJSONObject(0).has("evidence"))
  }

  @Test fun unsupportedEntityInSolFactIsRejectedBeforeGameplay() {
    val script = plan(JSONArray().put(event("beat", 1,
      JSONArray().put(fact("ENTITY", "fake_entity", "TEST_ONLY invalid")))))
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlan.installCurrent(initial(), script)
    }
  }

  @Test fun scriptedEntityFactsUseCoreEncounterIdentityWithoutStartingCombat() {
    val script = plan(JSONArray().put(event("encounter", 1,
      JSONArray().put(fact("ENTITY", "hound", "TEST_ONLY direct sighting")), kind = "ENTITY")))
    val state = StoryMilestonePlan.installCurrent(initial(), script)
    val next = StoryMilestoneRuntime.acceptTurn(state)
    assertNull(CombatRuntime.active(next))
    assertEquals("hound", JSONObject(next.world.getValue("flagsJson")).getString("entityEncounterKey"))
    val saved = JSONObject(next.world.getValue("story.factLedger")).getJSONArray("open").getJSONObject(0)
    assertTrue(saved.getString("entityEncounterId").startsWith("MSENC:"))
    StoryFactLedger.validateStoredState(next)
  }

  @Test fun laterSolFactSupersedesAnOlderScriptedFact() {
    val first = event("first", 1, JSONArray().put(fact("CLUE", "fixture.clue", "TEST_ONLY initial")))
    val second = event("second", 2, JSONArray().put(fact("CLUE", "fixture.clue", "TEST_ONLY updated")))
    val planned = StoryMilestonePlan.installCurrent(initial(), plan(JSONArray().put(first).put(second)))
    val afterFirst = CoreClientProjection.advance(StoryMilestoneRuntime.acceptTurn(planned))
    val afterSecond = StoryMilestoneRuntime.acceptTurn(afterFirst)
    val ledger = JSONObject(afterSecond.world.getValue("story.factLedger"))
    assertEquals("TEST_ONLY updated", ledger.getJSONArray("open").getJSONObject(0).getString("fact"))
    assertTrue(ledger.getJSONArray("history").toString().contains("SUPERSEDED"))
    StoryFactLedger.validateStoredState(afterSecond)
  }
}
