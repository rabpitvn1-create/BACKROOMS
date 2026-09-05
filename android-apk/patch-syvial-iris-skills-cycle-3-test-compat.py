from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/SyvialIrisSkillGeneratedTest.kt"

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIrisSkillGeneratedTest {
  @Test fun finalSkillSetHasSixActiveAndThreePassivePerCharacter() {
    assertEquals(6, SyvialSkillBook.activeSkills.size)
    assertEquals(3, SyvialSkillBook.passiveSkills.size)
    assertEquals(6, IrisSkillBook.activeSkills.size)
    assertEquals(3, IrisSkillBook.passiveSkills.size)
    assertEquals(9, SyvialSkillBook.skillsFor("Syvial").size)
    assertEquals(9, IrisSkillBook.skillsFor("IRIS").size)
  }

  @Test fun everyRandomProcStaysInsideTwentyToFortyPercent() {
    val randomProcSkills = SyvialSkillBook.allSkills + IrisSkillBook.allSkills
    assertTrue(randomProcSkills.all { it.procChance in 0.20..0.40 })
    val statusSkills = randomProcSkills.filter { it.statusType != null }
    assertTrue(statusSkills.all { it.statusChance in 0.20..0.40 })
  }

  @Test fun skillsKeepTheCharactersDistinctCombatRoles() {
    assertTrue(SyvialSkillBook.activeSkills.all { !it.ranged })
    assertTrue(IrisSkillBook.activeSkills.all { it.ranged })
    assertEquals(CombatEffectType.BLEED, SyvialSkillBook.GODKILLER_PURSUIT_CUT.statusType)
    assertEquals(CombatEffectType.BLEED, IrisSkillBook.IVORY_EBONY_TWIN_BURST.statusType)
    assertTrue(SyvialSkillBook.LUCIFER_ARMOR_BREAKSTEP.defenseIgnore > IrisSkillBook.ARGUS_FLANK_PUNISHER.defenseIgnore)
  }
}
''', encoding="utf-8")

print("Syvial/Iris cycle 3 test compatibility applied: generated aggregate test now expects exactly 6 Active + 3 Passive per character.")
