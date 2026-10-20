package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Only the Core-owned graph can unlock an exact-node exit. */
class WorldExitProgressionTest {
  private fun initial(node: String): GameState {
    val base = CoreClientProjection.initialize(GameState.initial())
    return base.copy(world = base.world +
      ("levelJson" to LevelRouteRegistry.node(node).asJson().toString()))
  }

  private fun allVisitedWithProofs(): GameState {
    val state = WorldGraphRuntime.initialize(initial("0"), 777L, Level0WorldDefinition)
    val p = MiniMapRuntime.progress(state)
    val rooms = p.getJSONObject("worldGraph").getJSONArray("rooms")
    val roomList = (0 until rooms.length()).map { rooms.getJSONObject(it) }
    val visited = p.getJSONArray("visitedRooms")
    for (r in roomList) if ((0 until visited.length()).none { visited.getString(it) == r.getString("id") })
      visited.put(r.getString("id"))
    val proof = p.getJSONObject("evidenceState")
    val flags = p.getJSONObject("ruleFlags")
    val records = p.getJSONArray("records")
    for (law in Level0WorldDefinition.evidenceLaws) {
      for (probe in law.probes) {
        val place = roomList.first { it.getString("environmentId") == probe.environmentId }
        proof.put(probe.id, true)
        records.put(JSONObject().put("id", place.getString("id"))
          .put("kind", "EVIDENCE").put("text", probe.clue))
      }
      for (anchor in law.physicalAnchors) {
        val place = roomList.first { it.getString("environmentId") == anchor }
        p.getJSONObject("environmentChanges")
          .put(place.getString("id"), JSONObject().put("trace", true))
      }
      val verifyAt = roomList.first { it.getString("environmentId") == law.verifyEnvironmentId }
      flags.put(law.id, true)
      records.put(JSONObject().put("id", verifyAt.getString("id"))
        .put("kind", "VERIFICATION").put("text", law.successText))
    }
    WorldGraphRuntime.validateSavedProgress(p, Level0WorldDefinition)
    return state.copy(world = state.world + (MiniMapRuntime.STATE to p.toString()))
  }

  @Test fun level0ExitRemainsLockedUntilAll37AreasAndBothCoreProofs() {
    val s = WorldGraphRuntime.initialize(initial("0"), 777L, Level0WorldDefinition)
    val p = MiniMapRuntime.progress(s)
    assertFalse(WorldGraphRuntime.exitUnlocked(p))
    assertFalse(WorldGraphRuntime.publicView(s, p).getBoolean("nodeExitUnlocked"))
    assertFalse(MiniMapScenario.objects(WorldGraphRuntime.actions(s, p))
      .any { it.optString("targetId") == "exit:0.1" })

    val completed = allVisitedWithProofs()
    val completedProgress = MiniMapRuntime.progress(completed)
    assertTrue(WorldGraphRuntime.exitUnlocked(completedProgress))
    assertTrue(WorldGraphRuntime.publicView(completed, completedProgress).getBoolean("nodeExitUnlocked"))
    assertTrue(MiniMapScenario.objects(WorldGraphRuntime.actions(completed, completedProgress))
      .any { it.optString("targetId") == "exit:0.1" })

    val unproven = JSONObject(completedProgress.toString())
    unproven.getJSONObject("ruleFlags").remove("wet-ceiling")
    assertFalse(WorldGraphRuntime.exitUnlocked(unproven))
    val unvisited = JSONObject(completedProgress.toString())
    val arr = unvisited.getJSONArray("visitedRooms")
    val missed = unvisited.getJSONObject("worldGraph").getJSONArray("rooms").getJSONObject(0).getString("id")
    val kept = JSONArray()
    for (i in 0 until arr.length()) if (arr.getString(i) != missed) kept.put(arr.getString(i))
    unvisited.put("visitedRooms", kept)
    assertFalse(WorldGraphRuntime.exitUnlocked(unvisited))
  }

  @Test fun authorizedExitStagesPreviousGraphThenCreatesNextGraphOnCommit() {
    val completed = allVisitedWithProofs()
    val p = MiniMapRuntime.progress(completed)
    val departure = MiniMapScenario.objects(WorldGraphRuntime.actions(completed, p))
      .first { it.getString("targetId") == "exit:0.1" }
    val cmd = JSONObject().put("actionId", departure.getString("actionId"))
      .put("sceneVersion", departure.getInt("sceneVersion"))
      .put("roomId", departure.getString("roomId"))
      .put("kind", departure.getString("kind")).put("targetId", departure.getString("targetId"))
    val staged = WorldGraphRuntime.stage(completed, cmd, JSONObject(p.toString()))
    assertEquals("0.1", staged.nodeId)
    assertTrue(staged.event.getBoolean("transition"))
    assertEquals("0", staged.progress.getString("nodeId"))
    assertEquals("0", MiniMapRuntime.progress(completed).getString("nodeId"))
    assertEquals("0.1", LevelRouteRegistry.exits("0").single())
    assertFalse(LevelRouteRegistry.canMove("0", "manila_room"))
    assertFalse(LevelRouteRegistry.canMove("0.1", "0"))
  }
}
