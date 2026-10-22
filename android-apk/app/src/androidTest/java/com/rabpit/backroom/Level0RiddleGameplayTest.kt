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

  private fun prepared(s: GameState, law: WorldEvidenceLaw): GameState {
    var state = s
    for (probe in law.probes) {
      state = travel(state, probe.environmentId)
      state = act(state, "probe:${probe.method}")
    }
    for ((first, second) in law.comparisonPairs) {
      state = travel(state, first)
      state = act(state, "study")
      val firstRoom = p(state).getString("roomId")
      state = travel(state, second)
      state = act(state, "study")
      state = act(state, "compare:$firstRoom")
    }
    for (anchor in law.physicalAnchors) {
      state = travel(state, anchor)
      state = act(state, "trace")
    }
    return travel(state, law.verifyEnvironmentId)
  }

  @Test fun wrongHypothesesDoNotUnlockAndAllFourChainsSurviveReload() {
    for (seed in listOf(41L, 73L)) {
      var state = WorldGraphRuntime.initialize(
        CoreClientProjection.initialize(GameState.initial()), seed)
      val initialItems = state.inventories
      val initialParty = state.party.memberIds
      val laws = Level0WorldDefinition.evidenceLaws
      assertEquals(4, laws.size)
      // Premature verification is rejected even at the correct verification site.
      state = travel(state, laws.first().verifyEnvironmentId)
      state = act(state, "verify")
      assertFalse(unlocked(state, laws.first().id))

      for ((lawIndex, law) in laws.withIndex()) {
        state = prepared(state, law)
        assertFalse(unlocked(state, law.id))
        for ((stepIndex, step) in law.inferenceSteps.withIndex()) {
          if (lawIndex == 0 && stepIndex == 0) {
            val wrong = step.options.first { it.id != step.answerId }
            state = act(state, "deduce:${step.id}:${wrong.id}")
            assertFalse(unlocked(state, law.id))
            assertFalse(p(state).getJSONObject("inferenceDecisions").has(step.id))
          }
          state = act(state, "deduce:${step.id}:${step.answerId}")
        }
        state = act(state, "verify")
        assertTrue("unsolved:${law.id}", unlocked(state, law.id))
      }
      assertEquals(initialItems, state.inventories)
      assertEquals(initialParty, state.party.memberIds)
      assertEquals(8, p(state).getJSONObject("inferenceDecisions").length())
      assertEquals(4, MiniMapScenario.objects(p(state).getJSONArray("records"))
        .count { it.optString("kind") == "VERIFICATION" })
      assertFalse(MiniMapRuntime.complete(state))
      val restored = GameStateCodec.decode(GameStateCodec.encode(state))
      assertEquals(p(state).toString(), p(restored).toString())
      for (law in laws) {
        assertTrue(unlocked(restored, law.id))
        assertFalse(public(restored).toString().contains(law.id))
      }
    }
  }

}
