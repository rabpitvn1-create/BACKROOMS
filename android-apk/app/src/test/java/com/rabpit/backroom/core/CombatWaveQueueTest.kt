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
    assertEquals("1", result.state.metadata["combat.kaiQuickStepTurns"])
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
  @Test fun remainingPartyActorsContinueAgainstNextEntityWithinOneRound() {
    var initial = SpecialFollowersCanon.ensure(GameState.initial())
    initial = LuciaCanon.ensure(initial)
    initial = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID)))
    val original = CombatRuntime.startWaves(initial, listOf("hound", "diep_minh"))
    var verified = false
    for (counter in 0..100) {
      val state = original.copy(metadata = original.metadata + mapOf("combat.entityHp" to "1", "combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      val text = result.reply
      val promoted = text.indexOf("Đợt 2/2: Diệp Minh")
      val lucia = text.indexOf("PARTY TURN 2/4: Lucia")
      if (promoted < 0 || lucia < 0 || promoted > lucia) continue
      assertTrue(text.indexOf("PARTY TURN 3/4: Iris") > lucia)
      assertTrue(text.indexOf("PARTY TURN 4/4: Syvial") > lucia)
      assertFalse(result.entityDestroyed)
      assertEquals("diep_minh", CombatRuntime.active(result.state)!!.entityKey)
      assertEquals(counter + 1, CombatRuntime.active(result.state)!!.eventCounter)
      verified = true
      break
    }
    assertTrue("Unspent Party actions must reach replacement without another dice round", verified)
  }

  @Test fun aReplacementAfterLastPartyActorDoesNotCounterattackForFree() {
    val started = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "diep_minh"))
    val base = started.copy(metadata = started.metadata + ("combat.entityHp" to "1"))
    var verified = false
    for (counter in 0..100) {
      val candidate = base.copy(metadata = base.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(candidate, "EXECUTE", "Cả Party cùng tấn công")
      if (!result.reply.contains("Đợt 2/2")) continue
      assertEquals("diep_minh", CombatRuntime.active(result.state)!!.entityKey)
      assertTrue(result.reply.contains("chờ lượt giao chiến tiếp theo"))
      assertFalse(result.reply.contains("Diệp Minh phản công"))
      verified = true
      break
    }
    assertTrue(verified)
  }

  @Test fun nonAttackCommandsPreserveIrisAndSyvialPassives() {
    val initial = SpecialFollowersCanon.ensure(GameState.initial())
    val party = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID)))
    val injured = CharacterStatEngine.setCurrentHp(party, SYVIAL_ID, 10)
    val state = CombatRuntime.start(injured, "hound")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertTrue(result.reply.contains("ARGUS Terrain Read"))
    assertTrue(result.reply.contains("Lucifer Core hồi Syvial"))
    assertTrue(result.reply.contains("Syvial kích hoạt Devil Trigger"))
  }

  @Test fun anNhienEscapeUltimateStillTriggersOnFifthRound() {
    val initial = AnNhienCanon.ensure(GameState.initial())
    val party = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, AN_NHIEN_ID)))
    val started = CombatRuntime.start(party, "hound")
    val state = started.copy(metadata = started.metadata + ("combat.eventCounter" to "4"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng bỏ chạy")
    assertTrue(result.reply.contains("Kế Hoạch Không Có Trong Kế Hoạch"))
    assertTrue(result.reply.contains("+30 Escape Progress"))
  }

}
