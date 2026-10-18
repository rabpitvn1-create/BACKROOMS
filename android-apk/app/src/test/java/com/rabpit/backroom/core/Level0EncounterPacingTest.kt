package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Level0EncounterPacingTest {
  private fun initial(seed: Long = 200L) =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)
  private fun progress(state: GameState) = MiniMapRuntime.progress(state)
  private fun perform(initial: GameState, targetId: String): Pair<GameState, Int> {
    val prior = progress(initial)
    val action = MiniMapScenario.objects(MiniMapRuntime.actions(initial))
      .first { it.getString("targetId") == targetId }
    val staged = MiniMapRuntime.stage(initial, JSONObject(action.toString()).apply { remove("label") })
    val rate = WorldGraphRuntime.movementEncounterChanceBp(prior, staged.progress)
    return initial.copy(world = initial.world +
      (MiniMapRuntime.STATE to staged.progress.toString())) to rate
  }

  @Test fun all37CanonEnvironmentsGetStablePacingWithoutSpecifyingEntitySpecies() {
    val expected = mapOf(
      "yellow_rot" to (650 to 250), "deadlight" to (1100 to 400),
      "wetfloor" to (1050 to 400), "silent_hall" to (800 to 300),
      "hollow_ceiling" to (1150 to 450), "afterimage" to (850 to 350),
      "the_loop" to (950 to 350), "the_fracture" to (1200 to 450)
    )
    Level0WorldDefinition.environments.forEach { e ->
      val pair = expected.getValue(e.zoneId)
      assertEquals(pair.first, Level0WorldDefinition.movementEncounterChanceBp(e.id, false))
      assertEquals(pair.second, Level0WorldDefinition.movementEncounterChanceBp(e.id, true))
      assertTrue(pair.second <= pair.first)
      assertTrue(pair.first < EntitySpawnPool.TOTAL_BP)
    }
    assertEquals(37, Level0WorldDefinition.environments.size)
  }

  @Test fun destinationNoveltyUsesPreMoveVisitedStateAndBacktrackHasLowerRisk() {
    var state = initial(203)
    val entry = progress(state).getString("roomId")
    val first = MiniMapScenario.objects(MiniMapRuntime.actions(state))
      .first { it.getString("kind") == "MOVE" }.getString("targetId")
    val (moved, firstRate) = perform(state, first)
    state = moved
    val room = MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
      .first { it.getString("id") == progress(state).getString("roomId") }
    assertEquals(Level0WorldDefinition.movementEncounterChanceBp(room.getString("environmentId"), false), firstRate)
    val (back, backRate) = perform(state, "room:$entry")
    state = back
    assertEquals(250, backRate) // Yellow Rot was the first visited environment.
    val (_, repeatedRate) = perform(state, first)
    assertEquals(Level0WorldDefinition.movementEncounterChanceBp(room.getString("environmentId"), true), repeatedRate)
    assertTrue(repeatedRate < firstRate)
  }

  @Test fun oneRngTicketHasExactlyConfiguredSuccessesAndOriginalRarityPool() {
    for (chance in listOf(0, 250, 650, 1200, 3500)) {
      var success = 0
      var calls = 0
      for (ticket in 0..9999) {
        val entity = EntitySpawnPool.rollEntityWithChance("0", chance) { bound ->
          assertEquals(10000, bound); calls++; ticket
        }
        if (entity != null) {
          success++
          assertTrue(entity in EntitySpawnPool.poolForLevel("0"))
        }
      }
      assertEquals(chance, success)
      assertEquals(if (chance == 0) 0 else 10000, calls)
    }
    assertThrows(IllegalArgumentException::class.java) {
      EntitySpawnPool.rollEntityWithChance("0", 3501) { 0 }
    }
    assertThrows(IllegalArgumentException::class.java) {
      EntitySpawnPool.rollEntityWithChance("0", -1) { 0 }
    }
  }

  @Test fun changingEntityChanceDoesNotChangeNpcTableOrPhysicalActionEligibility() {
    val state = initial()
    fun outcome(cap: Int): Pair<SpawnResult, Int> {
      var calls = 0
      val rolled = SpawnMechanism.rollSceneSpawns(state, "MOVE", cap) { bound ->
        calls++
        bound - 1
      }
      return rolled to calls
    }
    val (high, highCalls) = outcome(3500)
    val (low, lowCalls) = outcome(650)
    assertEquals(highCalls, lowCalls)
    assertEquals(high.npcEncounters, low.npcEncounters)
    assertNull(high.entityKey)
    assertNull(low.entityKey)
    var noneCalls = 0
    val none = SpawnMechanism.rollSceneSpawns(state, "INTERACT", 650) {
      noneCalls++; 0
    }
    assertNull(none.entityKey)
    assertTrue(none.npcEncounters.isEmpty())
    assertEquals(0, noneCalls)
  }
}
