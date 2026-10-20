package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Level0ObjectiveTest {
  private fun fresh() =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 125L)
  private fun progress(s: GameState) = MiniMapRuntime.progress(s)
  private fun act(s: GameState, target: String): GameState {
    val command = MiniMapScenario.objects(MiniMapRuntime.actions(s)).first {
      it.getString("targetId") == target
    }
    val staged = MiniMapRuntime.stage(s, JSONObject(command.toString()).apply { remove("label") })
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }

  @Test fun goalsTrackGenuineProgressWithoutChangingRouteOrExposingAnswers() {
    var state = fresh()
    val initial = MiniMapRuntime.publicView(state).getString("objective")
    assertTrue(initial.contains("Ghi dấu"))
    state = act(state, "mark")
    assertTrue(MiniMapRuntime.publicView(state).getString("objective").contains("Sổ tay"))
    val graph = progress(state).getJSONObject("worldGraph")
    val rooms = MiniMapScenario.objects(graph.getJSONArray("rooms"))
    val target = rooms.first { it.getString("environmentId") == "phong_moc" }.getString("id")
    val edges = rooms.associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val start = progress(state).getString("roomId")
    val paths = mutableMapOf(start to emptyList<String>())
    val queue = java.util.ArrayDeque<String>(); queue.add(start)
    while (target !in paths && queue.isNotEmpty()) {
      val here = queue.removeFirst()
      edges.getValue(here).forEach { next ->
        if (next !in paths) { paths[next] = paths.getValue(here) + next; queue.add(next) }
      }
    }
    paths.getValue(target).forEach { state = act(state, "room:$it") }
    state = act(state, "study")
    val next = MiniMapRuntime.publicView(state).getString("objective")
    assertNotEquals(initial, next)
    assertFalse(next.contains("passage:"))
    assertFalse(next.contains("wet-ceiling"))
    assertFalse(next.contains("loop-reflection"))
    assertFalse(next.contains("signal-distortion"))
    assertFalse(next.contains("fracture-boundary"))
    assertFalse(MiniMapRuntime.complete(state))
    val saved = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(next, MiniMapRuntime.publicView(saved).getString("objective"))
  }
}
