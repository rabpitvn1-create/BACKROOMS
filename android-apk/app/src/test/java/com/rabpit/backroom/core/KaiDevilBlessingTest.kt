package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class KaiDevilBlessingTest {
  @Test fun blessingTargetsActiveCompanionsAndExcludesKai() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID))
    )
    val kaiBefore = CharacterStatEngine.effective(state, KAI_ID)
    val irisBefore = CharacterStatEngine.effective(state, IRIS_ID)
    state = CombatRuntime.start(state, "hound")
    val kaiAfter = CharacterStatEngine.effective(state, KAI_ID)
    val irisAfter = CharacterStatEngine.effective(state, IRIS_ID)
    assertEquals(kaiBefore, kaiAfter)
    assertEquals((irisBefore.maxHp * 105 + 99) / 100, irisAfter.maxHp)
    assertEquals((irisBefore.str * 105 + 99) / 100, irisAfter.str)
    assertEquals((irisBefore.df * 105 + 99) / 100, irisAfter.df)
    assertEquals((irisBefore.agi * 105 + 99) / 100, irisAfter.agi)
    assertEquals(5, CharacterStatEngine.devilBlessingEvasionBonus(state, IRIS_ID))
    assertEquals(0, CharacterStatEngine.devilBlessingEvasionBonus(state, KAI_ID))
  }

  @Test fun blessingEvasionUsesScopedCombatTargetIds() {
    val source = File("src/main/java/com/rabpit/backroom/core/CombatRuntime.kt").readText()
    val generic = "CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, targetId)"
    val violet = "CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, duelTargetId)"
    assertEquals(1, source.split(generic).size - 1)
    assertEquals(1, source.split(violet).size - 1)
  }

  @Test fun blessingIsVisibleAsPassiveSkill() {
    val source = File("src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt").readText()
    assertEquals(1, source.split("s(\"Devil Blessing\", \"PASSIVE\"").size - 1)
    assertFalse(source.contains("s(\"DEVIL BLESSING\""))
  }
}
