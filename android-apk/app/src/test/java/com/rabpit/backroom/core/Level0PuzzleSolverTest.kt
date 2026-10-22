package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** Offline agents do not call Gemini/Gehihi. The solver is allowed to inspect the
 * private generated graph as an independent reachability oracle. The random
 * agent is deliberately restricted to Core-public actions. */
class Level0PuzzleSolverTest {
  private fun fresh(seed: Long) =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)
  private fun progress(state: GameState) = MiniMapRuntime.progress(state)
  private fun choices(state: GameState) = MiniMapScenario.objects(MiniMapRuntime.actions(state))

  private fun take(state: GameState, target: String): GameState {
    val choice = choices(state).firstOrNull { it.getString("targetId") == target }
      ?: error("unavailable_action:$target")
    val command = JSONObject(choice.toString()).apply { remove("label") }
    val staged = MiniMapRuntime.stage(state, command)
    return state.copy(world = state.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }

  private fun environmentRoom(state: GameState, identity: String): String =
    MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
      .first { it.getString("environmentId") == identity }.getString("id")

  private fun corridorWalk(initial: GameState, target: String): GameState {
    var state = initial
    val rooms = MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
    val graph = rooms.associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val origin = progress(state).getString("roomId")
    val paths = mutableMapOf(origin to emptyList<String>())
    val queue = java.util.ArrayDeque<String>()
    queue.add(origin)
    while (target !in paths && queue.isNotEmpty()) {
      val here = queue.removeFirst()
      graph.getValue(here).forEach { next ->
        if (next !in paths) {
          paths[next] = paths.getValue(here) + next
          queue.add(next)
        }
      }
    }
    for (room in paths.getValue(target)) state = take(state, "room:$room")
    return state
  }

  @Test fun offlineSolverCompletesFourChainedLawsAcrossSeedsAndReturnsHome() {
    for (seed in listOf(0L, 1L, 41L, 73L, -42L, Long.MAX_VALUE)) {
      var state = fresh(seed)
      val home = progress(state).getJSONObject("worldGraph").getString("entryRoomId")
      val lawIds = Level0WorldDefinition.evidenceLaws.map { it.id }
      assertEquals(4, lawIds.size)
      state = Level0RiddleTestSupport.solveAll(state)
      state = GameStateCodec.decode(GameStateCodec.encode(state))
      assertEquals(lawIds.toSet(), progress(state).getJSONObject("ruleFlags")
        .keys().asSequence().toSet())
      assertEquals(16, progress(state).getJSONObject("evidenceState").length())
      assertEquals(8, progress(state).getJSONObject("inferenceDecisions").length())

      state = Level0RiddleTestSupport.visit(state, "phong_ngap")
      state = take(take(state, "inspect"), "passage:p:00")
      state = take(state, "passage:p:00")
      assertEquals(environmentRoom(state, "khoang_ky_thuat"), progress(state).getString("roomId"))

      state = Level0RiddleTestSupport.visit(state, "hanh_lang_lap")
      state = take(take(state, "inspect"), "passage:p:01")
      state = take(state, "passage:p:01")
      assertEquals(environmentRoom(state, "phong_ky_uc"), progress(state).getString("roomId"))
      assertFalse(choices(state).any { it.getString("targetId") == "passage:p:01" })
      state = corridorWalk(state, home)
      assertEquals(home, progress(state).getString("roomId"))
      assertFalse(WorldGraphRuntime.exitUnlocked(progress(state))) // Still requires visiting every zone.
      val checkpoint = GameCheckpointCodec.decode(GameCheckpointCodec.encode(state, JSONObject()))
      assertEquals(progress(state).toString(), progress(checkpoint.core).toString())
    }
  }

  @Test fun publicOnlyRandomAgentMeasuresAccidentalVerificationWithoutLeakingSecrets() {
    val rng = Random(20261018L)
    var verified = 0
    val runs = 8
    val turnsPerRun = 160
    repeat(runs) { seed ->
      var state = fresh(seed.toLong())
      repeat(turnsPerRun) {
        val choices = choices(state).filter {
          it.getString("kind") in setOf("MOVE", "SEARCH", "INTERACT")
        }
        assertTrue(choices.isNotEmpty())
        state = take(state, choices[rng.nextInt(choices.size)].getString("targetId"))
        val public = MiniMapRuntime.publicView(state).toString()
        assertFalse(public.contains("wet-ceiling"))
        assertFalse(public.contains("loop-reflection"))
        assertFalse(MiniMapRuntime.complete(state))
      }
      verified += progress(state).getJSONObject("ruleFlags").length()
      GameStateCodec.decode(GameStateCodec.encode(state)) // Random progress still serializes.
    }
    System.out.println("level0_random_agent: runs=$runs turns=$turnsPerRun verified=$verified; NOT a human difficulty estimate")
    assertTrue(verified in 0..(runs * 4))
  }
}
