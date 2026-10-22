package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Combat operates on the same GameState but cannot roll back world investigation. */
class Level0CombatContinuityTest {
  private fun fresh() =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 2034L)
  private fun progress(s: GameState) = MiniMapRuntime.progress(s)
  private fun act(s: GameState, target: String): GameState {
    val action = MiniMapScenario.objects(MiniMapRuntime.actions(s)).first {
      it.getString("targetId") == target
    }
    val command = JSONObject(action.toString()).apply { remove("label") }
    val staged = MiniMapRuntime.stage(s, command)
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }
  private fun investigated(): GameState {
    var s = fresh()
    s = act(s, "observe")
    s = act(s, "mark")
    s = act(s, "trace")
    return s
  }

  @Test fun fleeingEntityDoesNotResetGraphMarkersOrPhysicalEvidence() {
    val scene = investigated()
    val savedWorld = progress(scene).toString()
    val combat = CombatRuntime.start(scene, "smiler")
    assertEquals(savedWorld, progress(combat).toString())
    assertEquals(0, MiniMapRuntime.actions(combat).length())
    val ready = combat.copy(metadata = combat.metadata + ("combat.escapeProgress" to "95"))
    val result = CombatRuntime.resolve(ready, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertFalse(result.entityDestroyed)
    assertNull(CombatRuntime.active(result.state))
    assertEquals(savedWorld, progress(result.state).toString())
    assertEquals(savedWorld,
      progress(GameStateCodec.decode(GameStateCodec.encode(result.state))).toString())
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(result.state)).isNotEmpty())
  }

  @Test fun defeatingEntityAndAwardingRewardsNeverRewriteExploration() {
    val scene = investigated()
    val savedWorld = progress(scene).toString()
    var state = CombatRuntime.start(scene, "hound")
    var destroyed = false
    repeat(24) {
      if (destroyed) return@repeat
      val resolved = CombatRuntime.resolve(state, "EXECUTE", "bắn Hound bằng Magnum")
      assertTrue(resolved.handled)
      state = resolved.state
      destroyed = resolved.entityDestroyed
      assertEquals(savedWorld, progress(state).toString())
    }
    assertTrue("Existing combat must still defeat Hound", destroyed)
    assertNull(CombatRuntime.active(state))
    assertEquals(savedWorld, progress(GameStateCodec.decode(GameStateCodec.encode(state))).toString())
    assertTrue(progress(state).getJSONObject("markers").length() > 0)
  }
}
