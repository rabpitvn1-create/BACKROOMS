from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/SyvialIrisSkillGeneratedTest.kt"

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIrisSkillGeneratedTest {
  @Test fun cycleTwoHasFourActiveAndTwoPassivePerCharacter() {
    assertEquals(4, SyvialSkillBook.activeSkills.size)
    assertEquals(2, SyvialSkillBook.passiveSkills.size)
    assertEquals(4, IrisSkillBook.activeSkills.size)
    assertEquals(2, IrisSkillBook.passiveSkills.size)
    assertEquals(6, SyvialSkillBook.skillsFor("Syvial").size)
    assertEquals(6, IrisSkillBook.skillsFor("IRIS").size)
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
    assertEquals(CombatEffectType.BLEED, SyvialSkillBook.GODKILLER_EXECUTION_ARC.statusType)
    assertEquals(CombatEffectType.STUN, SyvialSkillBook.LUCIFER_COUNTERBREAK.statusType)
    assertEquals(CombatEffectType.STUN, IrisSkillBook.IVORY_EBONY_SUPPRESSION.statusType)
  }
}
''', encoding="utf-8")

print("Syvial/Iris cycle 2 test compatibility applied: generated aggregate test now expects 4 Active + 2 Passive per character.")
