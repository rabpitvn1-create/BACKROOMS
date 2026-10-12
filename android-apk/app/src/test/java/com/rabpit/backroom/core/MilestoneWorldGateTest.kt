package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MilestoneWorldGateTest {
  private fun plan() = JSONObject()
    .put("schemaVersion", 1).put("checkpointId", "CP-L00-01")
    .put("events", JSONArray().put(JSONObject()
      .put("id", "final").put("turn", 3).put("kind", "FINAL_ENTITY")
      .put("event", "TEST_ONLY_ENTITY_ENCOUNTER")
      .put("outcome", "TEST_ONLY_START_COMBAT")
      .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9)))
    .toString()

  private fun scripted(level: String = "0"): GameState {
    val fresh = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    val node = requireNotNull(LevelRouteRegistry.node(level))
    return StoryMilestonePlan.installCurrent(
      fresh.copy(world = fresh.world + ("levelJson" to node.asJson().toString())),
      plan())
  }

  @Test fun onlyAdjoiningNodesInsideSameParentLevelAreTraversable() {
    assertTrue(MilestoneWorldGate.allowScriptedRoute("0", "0.1"))
    assertTrue(MilestoneWorldGate.allowScriptedRoute("0", "epsilon"))
    assertTrue(MilestoneWorldGate.allowScriptedRoute("0.1", "0.2"))
    assertFalse(MilestoneWorldGate.allowScriptedRoute("0.1", "0"))
    assertFalse(MilestoneWorldGate.allowScriptedRoute("0", "1"))
    // The graph offers red_rooms -> 1, but no Level-1 CP schema is configured.
    assertTrue(LevelRouteRegistry.canMove("red_rooms", "1"))
    assertFalse(MilestoneWorldGate.allowScriptedRoute("red_rooms", "1"))
    assertFalse(MilestoneWorldGate.allowScriptedRoute("nonexistent", "1"))
    assertFalse(MilestoneWorldGate.allowScriptedRoute("0", "nonexistent"))
  }

  @Test fun scriptedWorldStripsLegacyExitAndRetainsExplorationAndClues() {
    val flags = JSONObject()
      .put("exploration", JSONObject()
        .put("hiddenExitStreak", 5)
        .put("exitReady", true)
        .put("transitionReady", true)
        .put("exitProgress", "READY")
        .put("exitCandidate", "obsolete")
        .put("confirmedExit", "obsolete")
        .put("snapshotStep", 7)
        .put("levelTurns", 11))
      .put("exitProgress", "READY")
      .put("exitChanceThreshold", 9000)
      .put("entityEncounterKey", "hound")
      .put("storyContinuity", JSONObject().put("clue", "TEST_ONLY_CLUE"))
    val clean = MilestoneWorldGate.stripLegacyExitProof(flags)
    assertFalse(clean.has("exitProgress"))
    assertFalse(clean.has("exitChanceThreshold"))
    for (field in listOf(
      "hiddenExitStreak", "exitReady", "transitionReady", "exitProgress",
      "confirmedExit", "exitCandidate")) {
      assertFalse(clean.getJSONObject("exploration").has(field))
    }
    assertEquals(7, clean.getJSONObject("exploration").getInt("snapshotStep"))
    assertEquals(11, clean.getJSONObject("exploration").getInt("levelTurns"))
    assertEquals("hound", clean.getString("entityEncounterKey"))
    assertEquals("TEST_ONLY_CLUE", clean.getJSONObject("storyContinuity").getString("clue"))
    // The caller's original snapshot is not altered.
    assertTrue(flags.getJSONObject("exploration").getBoolean("exitReady"))
  }

  @Test fun validatedCoreCommandRejectsCrossLevelEvenWithForgedReadyFlags() {
    val state = scripted("red_rooms")
    val request = JSONObject()
      .put("level", LevelRouteRegistry.node("1")!!.asJson())
      .put("flags", JSONObject().put("exploration", JSONObject()
        .put("hiddenExitStreak", 5).put("exitReady", true).put("transitionReady", true)))
    assertFalse(MilestoneWorldGate.safeguardCandidate(state, request))
    assertEquals("red_rooms", LevelRouteRegistry.currentId(JSONObject()
      .put("level", JSONObject(state.world.getValue("levelJson")))))
    assertTrue(StoryMilestoneRuntime.due(state).isEmpty())
  }

  @Test fun acceptedSameLevelChangeCannotKeepLegacyExitProofOrMutateCoreCombat() {
    val state = scripted()
    val next = JSONObject()
      .put("level", LevelRouteRegistry.node("0.1")!!.asJson())
      .put("flags", JSONObject()
        .put("exploration", JSONObject().put("exitReady", true)
          .put("hiddenExitStreak", 5).put("snapshotStep", 4))
        .put("storyContinuity", JSONObject().put("thread", "TEST_ONLY_THREAD")))
    assertTrue(MilestoneWorldGate.safeguardCandidate(state, next))
    val exploration = next.getJSONObject("flags").getJSONObject("exploration")
    assertFalse(exploration.has("exitReady"))
    assertFalse(exploration.has("hiddenExitStreak"))
    assertEquals(4, exploration.getInt("snapshotStep"))
    assertEquals(state.characters, state.characters)
    assertEquals("CP-L00-01", StoryCheckpointCore.current(state)!!.id)
    assertNull(CombatRuntime.active(state))
  }

  @Test fun withoutMilestoneExistingExitContractsRemainUntouched() {
    val state = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    val flags = JSONObject().put("exploration", JSONObject()
      .put("hiddenExitStreak", 5).put("exitReady", true))
    val candidate = JSONObject()
      .put("level", LevelRouteRegistry.node("1")!!.asJson())
      .put("flags", flags)
    assertTrue(MilestoneWorldGate.safeguardCandidate(state, candidate))
    assertTrue(candidate.getJSONObject("flags").getJSONObject("exploration").getBoolean("exitReady"))
  }
}
