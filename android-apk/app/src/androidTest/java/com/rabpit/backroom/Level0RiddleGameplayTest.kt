package com.rabpit.backroom

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rabpit.backroom.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque

/** Genuine Level 0 Core puzzle actions; a logical agent uses the private map
 * for pathfinding, but each executed action must be legal in the public UI. */
@RunWith(AndroidJUnit4::class)
class Level0RiddleGameplayTest {
  private fun p(s: GameState) = MiniMapRuntime.progress(s)
  private fun public(s: GameState) = MiniMapRuntime.publicView(s)
  private fun actions(s: GameState) = MiniMapScenario.objects(MiniMapRuntime.actions(s))
  private fun act(s: GameState, target: String): GameState {
    val a = actions(s).singleOrNull { it.getString("targetId") == target }
      ?: error("Core action not offered: $target")
    val cmd = JSONObject(a.toString()).apply { remove("label") }
    val staged = MiniMapRuntime.stage(s, cmd)
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }
  private fun travel(s: GameState, env: String): GameState {
    val rooms = MiniMapScenario.objects(p(s).getJSONObject("worldGraph").getJSONArray("rooms"))
    val dest = rooms.first { it.getString("environmentId") == env }.getString("id")
    val edges = rooms.associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val start = p(s).getString("roomId")
    val paths = mutableMapOf(start to emptyList<String>())
    val queue = ArrayDeque<String>();queue.add(start)
    while (dest !in paths && queue.isNotEmpty()) {
      val at = queue.removeFirst()
      edges.getValue(at).forEach { next ->
        if (next !in paths) {
          paths[next] = paths.getValue(at) + next
          queue.add(next)
        }
      }
    }
    return paths.getValue(dest).fold(s) { state, room -> act(state,"room:$room") }
  }
  private fun unlocked(s: GameState, rule: String) =
    p(s).getJSONObject("ruleFlags").optBoolean(rule)

  @Test fun incompleteGuessesNeverUnlockAndBothRiddlesSurviveSaveReload() {
    for (seed in listOf(41L,73L)) {
      var state = WorldGraphRuntime.initialize(
        CoreClientProjection.initialize(GameState.initial()),seed)
      val initialItems = state.inventories
      val initialParty = state.party.memberIds
      assertFalse(public(state).toString().contains("wet-ceiling"))
      assertFalse(public(state).toString().contains("loop-reflection"))
      // Wrong physical inference: no independent evidence yet.
      state = travel(state,"khoang_ky_thuat")
      state = act(state,"verify")
      assertFalse(unlocked(state,"wet-ceiling"))
      assertEquals(1,p(state).getJSONObject("environmentChanges")
        .getJSONObject(p(state).getString("roomId")).getInt("failedTests"))
      state = travel(state,"phong_ngap")
      state = act(state,"probe:floor")
      state = travel(state,"khoang_ky_thuat")
      state = act(state,"verify")
      assertFalse("One clue cannot unlock the physical shortcut",unlocked(state,"wet-ceiling"))
      state = act(state,"probe:ceiling")
      state = act(state,"verify")
      assertTrue(unlocked(state,"wet-ceiling"))
      // The reflection puzzle also requires user-made physical anchors.
      state = travel(state,"hanh_lang_lap")
      state = act(state,"probe:markings")
      state = travel(state,"phong_dao")
      state = act(state,"probe:reflection")
      state = travel(state,"hanh_lang_lap")
      state = act(state,"verify")
      assertFalse(unlocked(state,"loop-reflection"))
      state = act(state,"trace")
      state = act(state,"verify")
      assertFalse("One physical marker is insufficient",unlocked(state,"loop-reflection"))
      state = travel(state,"phong_dao")
      state = act(state,"trace")
      state = travel(state,"hanh_lang_lap")
      state = act(state,"verify")
      assertTrue(unlocked(state,"loop-reflection"))
      assertEquals(initialItems,state.inventories)
      assertEquals(initialParty,state.party.memberIds)
      assertFalse(MiniMapRuntime.complete(state))
      val restored = GameStateCodec.decode(GameStateCodec.encode(state))
      assertEquals(p(state).toString(),p(restored).toString())
      assertTrue(unlocked(restored,"wet-ceiling"))
      assertTrue(unlocked(restored,"loop-reflection"))
      assertEquals(2,MiniMapScenario.objects(p(restored).getJSONArray("records"))
        .count { it.optString("kind")=="VERIFICATION" })
      assertFalse(public(restored).toString().contains("wet-ceiling"))
      assertFalse(public(restored).toString().contains("loop-reflection"))
    }
  }
}
