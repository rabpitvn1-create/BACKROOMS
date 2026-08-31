package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DevilTriggerCombatIntegrationTest {
  @Test fun kaiPassiveIsExclusiveAndSyvialKeepsHerSeparateState() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)))
    state = CombatRuntime.start(state, "diep_minh").copy(metadata = CombatRuntime.start(state, "diep_minh").metadata + mapOf(
      "passive.devilTrigger.kai.activeTurns" to "3",
      "passive.devilTrigger.syvial.activeTurns" to "3"
    ))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals("2", result.state.metadata["passive.devilTrigger.kai.activeTurns"])
    assertNull(result.state.metadata["passive.devilTrigger.syvial.activeTurns"])
    assertEquals("true", result.state.metadata["combat.syvialDevilTrigger"])
    assertTrue(result.reply.contains("Syvial kích hoạt Devil Trigger theo Lucifer Core"))
    assertFalse(result.reply.contains("DEVIL TRIGGER — Lucifer Core"))
  }

  @Test fun devilBlessingAddsFivePercentToCompanionButNeverKai() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID)))
    val kaiBefore = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val irisBefore = CharacterStatEngine.effective(state, IRIS_ID).maxHp
    state = CombatRuntime.start(state, "hound")
    assertEquals(kaiBefore, CharacterStatEngine.effective(state, KAI_ID).maxHp)
    assertEquals(irisBefore + (irisBefore * 5 + 99) / 100, CharacterStatEngine.effective(state, IRIS_ID).maxHp)
  }
}
