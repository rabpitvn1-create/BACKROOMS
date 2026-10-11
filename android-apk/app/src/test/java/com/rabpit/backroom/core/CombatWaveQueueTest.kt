package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CombatWaveQueueTest {
  @Test fun acceptsExplicitWavesButKeepsOrdinaryEncountersSingleEntity() {
    val initial = GameState.initial()
    assertEquals(1, CombatRuntime.toJson(CombatRuntime.start(initial, "hound"))!!.getInt("waveCount"))
    val four = CombatRuntime.startWaves(initial, listOf("hound", "clump", "hound", "smiler"))
    val view = CombatRuntime.toJson(four)!!
    assertEquals(4, view.getInt("waveCount"))
    assertEquals(1, view.getInt("waveNumber"))
    assertEquals(4, view.getInt("remainingEntityCount"))
    assertEquals(initial, CombatRuntime.startWaves(initial, emptyList()))
    assertEquals(initial, CombatRuntime.startWaves(initial, List(5) { "hound" }))
    assertEquals(initial, CombatRuntime.startWaves(initial, listOf("hound", "bogus")))
  }

  @Test fun defeatedEntityAdvancesWithoutEndingEncounter() {
    var state = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "clump"))
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "0"))
    val first = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertTrue(first.handled)
    assertFalse(first.entityDestroyed)
    assertTrue(first.reply.contains("Đợt 2/2"))
    assertEquals("clump", CombatRuntime.active(first.state)!!.entityKey)
    assertEquals(2, CombatRuntime.toJson(first.state)!!.getInt("waveNumber"))
    assertEquals("1", first.state.metadata["combat.waveIndex"])
    assertEquals(1, first.state.metadata.keys.count { it.startsWith("loot.awarded.") })
  }

  @Test fun identicalEntityTypesUseUniqueInstanceKeysAndFinishOnlyAtTheEnd() {
    var state = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "hound"))
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "0"))
    val first = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertFalse(first.entityDestroyed)
    val secondState = first.state.copy(metadata = first.state.metadata + ("combat.entityHp" to "0"))
    val second = CombatRuntime.resolve(secondState, "SEARCH", "quan sát")
    assertTrue(second.entityDestroyed)
    assertNull(CombatRuntime.active(second.state))
    assertEquals(2, second.state.metadata.keys.count { it.startsWith("loot.awarded.") })
    assertFalse(second.state.metadata.keys.any { it.startsWith("combat.") })
  }

  @Test fun transitionKeepsRoundAndPartyEffectsButClearsEntityEffects() {
    val initial = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "smiler"))
    val marked = initial.copy(metadata = initial.metadata + mapOf(
      "combat.entityHp" to "0", "combat.eventCounter" to "2",
      "combat.irisAnalyzedTurns" to "2", "combat.kaiBleedTurns" to "2",
      "combat.lifeformBleedTurns" to "2", "combat.kaiQuickStepTurns" to "2"
    ))
    val result = CombatRuntime.resolve(marked, "SEARCH", "quan sát")
    assertEquals("smiler", CombatRuntime.active(result.state)!!.entityKey)
    assertEquals(3, CombatRuntime.active(result.state)!!.eventCounter)
    assertNull(result.state.metadata["combat.irisAnalyzedTurns"])
    assertNull(result.state.metadata["combat.kaiBleedTurns"])
    assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
    assertEquals("1", result.state.metadata["combat.lifeformBleedTurns"])
    assertEquals(115, CombatRuntime.active(result.state)!!.entityMaxHp)
  }

  @Test fun clearRemovesQueue() {
    val state = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "smiler"))
    val cleared = CombatRuntime.clear(state)
    assertNull(CombatRuntime.active(cleared))
    assertNull(cleared.metadata["combat.waveKeys"])
    assertNull(cleared.metadata["combat.waveIndex"])
  }
}
