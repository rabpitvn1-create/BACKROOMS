package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Assistance is derived entirely from Core evidence and never starts a turn. */
class WorldHintTest {
  private fun fresh(seed: Long = 77L) =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)

  private fun progress(state: GameState) = MiniMapRuntime.progress(state)

  private fun move(state: GameState, target: String): GameState {
    val choice = MiniMapScenario.objects(MiniMapRuntime.actions(state)).first {
      it.getString("targetId") == target
    }
    val staged = MiniMapRuntime.stage(state, JSONObject(choice.toString()).apply { remove("label") })
    return state.copy(world = state.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }

  @Test fun hintHasNoSideEffectsAndNeverEntersRegularScene() {
    val state = fresh()
    val before = GameStateCodec.encode(state)
    val ordinary = MiniMapRuntime.publicView(state).toString()
    assertFalse(ordinary.contains("Gợi ý cấp"))
    for (tier in 1..3) {
      val result = WorldGraphRuntime.hint(state, tier)
      assertEquals(tier, result.getInt("tier"))
      assertEquals(progress(state).getString("roomId"), result.getString("roomId"))
      assertEquals(progress(state).getInt("version"), result.getInt("sceneVersion"))
      assertTrue(result.getString("text").isNotBlank())
      assertFalse(result.toString().contains("passage:p:"))
      assertFalse(result.toString().contains("wet-ceiling"))
      assertFalse(result.toString().contains("loop-reflection"))
      assertFalse(result.toString().contains("signal-distortion"))
      assertFalse(result.toString().contains("fracture-boundary"))
      assertEquals(before, GameStateCodec.encode(state))
    }
    for (badTier in listOf(0, -1, 4)) {
      assertEquals("world_hint_tier",
        assertThrows(IllegalArgumentException::class.java) {
          WorldGraphRuntime.hint(state, badTier)
        }.message)
    }
  }

  @Test fun tierTwoMentionsOnlyAlreadyObservedEvidenceAndTierThreeNeverRevealsHiddenIds() {
    var state = fresh(143L)
    val rooms = MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
    val adjacency = rooms.associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val destination = rooms.first { it.getString("environmentId") == "phong_ngap" }.getString("id")
    val initial = progress(state).getString("roomId")
    val paths = mutableMapOf(initial to emptyList<String>())
    val queue = java.util.ArrayDeque<String>(); queue.add(initial)
    while (destination !in paths && queue.isNotEmpty()) {
      val from = queue.removeFirst()
      adjacency.getValue(from).forEach { to ->
        if (to !in paths) { paths[to] = paths.getValue(from) + to; queue.add(to) }
      }
    }
    paths.getValue(destination).forEach { state = move(state, "room:$it") }
    state = move(state, "probe:floor")
    val before = GameStateCodec.encode(state)
    val second = WorldGraphRuntime.hint(state, 2).getString("text")
    assertTrue(second.contains("Phòng Ngập"))
    assertFalse(second.contains("Khoang Kỹ Thuật"))
    val third = WorldGraphRuntime.hint(state, 3).getString("text")
    assertFalse(third.contains("p:00"))
    assertFalse(third.contains("wet-ceiling"))
    assertEquals(before, GameStateCodec.encode(state))
    assertEquals(before, GameStateCodec.encode(GameStateCodec.decode(before)))
  }
}
