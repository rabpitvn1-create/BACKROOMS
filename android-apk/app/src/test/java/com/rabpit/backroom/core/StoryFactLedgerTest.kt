package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic identifiers only: fixtures below are NOT authored game events. */
class StoryFactLedgerTest {
  private fun initial(): GameState =
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))

  private fun add(kind: String, subject: String, fact: String): String =
    JSONArray().put(JSONObject().put("op", "ADD").put("kind", kind)
      .put("subject", subject).put("fact", fact)).toString()

  private fun resolve(id: String, fact: String): String =
    JSONArray().put(JSONObject().put("op", "RESOLVE")
      .put("referenceId", id).put("fact", fact)).toString()

  @Test fun storyFactNeverMutatesCharacterEquipmentCombatOrInventory() {
    val baseline = initial()
    val fixtureFact = "TEST_ONLY_FACT_A"
    val remembered = StoryFactLedger.recordTurn(baseline, emptyList(),
      add("EVENT", "fixture.event.a", fixtureFact))
    assertEquals(baseline.characters, remembered.characters)
    assertEquals(baseline.equipment, remembered.equipment)
    assertEquals(baseline.inventories, remembered.inventories)
    assertEquals(baseline.statuses, remembered.statuses)
    assertEquals(baseline.metadata, remembered.metadata)
    assertTrue(StoryFactLedger.context(remembered).contains(fixtureFact))

    val otherLevel = remembered.copy(world = remembered.world +
      ("levelJson" to JSONObject().put("id", "6").put("number", 6).toString()))
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(otherLevel))
    assertTrue(StoryFactLedger.context(reloaded).contains(fixtureFact))
    assertEquals(baseline.characters, reloaded.characters)
    assertEquals(baseline.equipment, reloaded.equipment)
    assertEquals(baseline.inventories, reloaded.inventories)
  }

  @Test fun milestoneMemoryIsCommittedOnlyAfterReceipt() {
    val subject = "fixture.event.b"
    val spec = JSONObject().put("kind", "EVENT")
      .put("subject", subject).put("fact", "TEST_ONLY_FACT_B")
    val plan = JSONObject().put("schemaVersion", 2).put("checkpointId", "CP-L00-01")
      .put("events", JSONArray().put(JSONObject()
        .put("id", "beat").put("turn", 1).put("kind", "STORY")
        .put("action", "TEST_ONLY_ACTION").put("event", "TEST_EVENT").put("outcome", "TEST_OUTCOME")
        .put("storyFacts", JSONArray().put(spec))).put(JSONObject()
        .put("id", "boss").put("turn", 8).put("kind", "FINAL_ENTITY")
        .put("action", "TEST_ONLY_ACTION").put("event", "TEST_FINAL_EVENT").put("outcome", "Combat bắt đầu")
        .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9)))
    val installed = StoryMilestonePlan.installCurrent(initial(), plan.toString())
    assertEquals(0, JSONObject(StoryFactLedger.context(installed))
      .getJSONArray("open").length())
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(installed, "[]")
    }
    val accepted = StoryMilestoneRuntime.acceptTurn(installed, "[\"beat\"]")
    assertTrue(StoryFactLedger.context(accepted).contains(subject))
    assertEquals(installed.characters, accepted.characters)
    assertEquals(installed.statuses, accepted.statuses)
    assertEquals(installed.inventories, accepted.inventories)
    assertEquals(installed.equipment, accepted.equipment)
    val next = CoreClientProjection.advance(accepted)
    assertTrue(StoryMilestoneRuntime.due(next).isEmpty())
    assertTrue(StoryFactLedger.context(GameStateCodec.decode(GameStateCodec.encode(next)))
      .contains(subject))
  }

  @Test fun openFactsRemainAcrossManyTurnsAndLevelTransitions() {
    var state = StoryFactLedger.recordTurn(initial(), emptyList(),
      add("EVENT", "fixture.event.long", "TEST_ONLY_PERSISTENT_FACT"))
    for (i in 1..25) {
      state = CoreClientProjection.advance(state)
      state = StoryFactLedger.recordTurn(state, emptyList(),
        add("CLUE", "fixture.clue.$i", "TEST_ONLY_CLUE_$i"))
    }
    val advanced = state.copy(world = state.world + ("levelJson" to JSONObject()
      .put("id", "6").put("number", 6).toString()))
    val memory = StoryFactLedger.context(GameStateCodec.decode(GameStateCodec.encode(advanced)))
    assertTrue(memory.contains("TEST_ONLY_PERSISTENT_FACT"))
    assertTrue(memory.contains("fixture.clue.25"))
  }

  @Test fun resolutionClosesOnlyReferencedFactAndDoesNotChangeInventory() {
    val state = initial()
    val created = StoryFactLedger.recordTurn(state, emptyList(),
      add("EVENT", "fixture.event.to_resolve", "TEST_ONLY_PENDING_FACT"))
    val id = JSONObject(StoryFactLedger.context(created))
      .getJSONArray("open").getJSONObject(0).getString("id")
    val closed = StoryFactLedger.recordTurn(CoreClientProjection.advance(created), emptyList(),
      resolve(id, "TEST_ONLY_RESOLVED_FACT"))
    val memory = JSONObject(StoryFactLedger.context(closed))
    assertEquals(0, memory.getJSONArray("open").length())
    assertTrue(memory.getJSONArray("recent").toString().contains("TEST_ONLY_RESOLVED_FACT"))
    assertEquals(created.equipment, closed.equipment)
    assertEquals(created.inventories, closed.inventories)
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(closed, emptyList(), resolve(id, "resolve twice"))
    }
  }

  @Test fun unknownEntityAndConflictingFactsAreRejectedWithoutMutation() {
    val state = initial()
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(state, emptyList(),
        add("ENTITY", "fake_entity", "TEST_ONLY_ENTITY"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(state, emptyList(),
        add("ENTITY", "hound", "TEST_ONLY_ENTITY"))
    }
    val after = StoryFactLedger.recordTurn(state, emptyList(),
      add("CLUE", "fixture.clue.unknown", "TEST_ONLY_CLUE_ORIGINAL"))
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(after, emptyList(),
        add("CLUE", "fixture.clue.unknown", "TEST_ONLY_CLUE_CONFLICT"))
    }
    assertEquals(state.characters, after.characters)
    assertTrue(StoryFactLedger.context(after).contains("TEST_ONLY_CLUE_ORIGINAL"))
  }

  @Test fun entityMemoryRequiresPreviouslyConfirmedEncounter() {
    val state = initial()
    val flagged = state.copy(world = state.world + ("flagsJson" to JSONObject()
      .put("entityEncounterKey", "hound").toString()))
    val remembered = StoryFactLedger.recordTurn(flagged, emptyList(),
      add("ENTITY", "hound", "TEST_ONLY_CONFIRMED_ENTITY"))
    assertTrue(StoryFactLedger.context(remembered).contains("TEST_ONLY_CONFIRMED_ENTITY"))
    assertNull(CombatRuntime.active(remembered))
    assertEquals(flagged.characters, remembered.characters)
  }
}
