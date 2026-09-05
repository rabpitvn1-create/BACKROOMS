from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Syvial/Iris cycle 2 {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
if "SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED" not in combat:
    raise RuntimeError("Syvial/Iris cycle 2 requires cycle 1 skill layer")

syvial_old = '''  val passiveSkills = listOf(LUCIFER_ARMOR_COMBAT_SYNC)
  val activeSkills = listOf(GODKILLER_CRIMSON_CLEAVE, LUCIFER_BREAKLINE)
'''
syvial_new = '''  val GODKILLER_EXECUTION_ARC = KaiSkillDefinition(
    "syvial.godkiller_execution_arc", "GodKiller Execution Arc", CombatSkillCategory.ACTIVE, 0.34,
    "27 Base DMG, +15% Crit, bỏ qua 30% DF; nhát chém áp chế bằng GodKiller, khi trúng có 28% gây [Chảy máu] 2 lượt.",
    attack = true,
    baseDamage = 27,
    bonusCritChance = 0.15,
    defenseIgnore = 0.30,
    ranged = false,
    statusType = CombatEffectType.BLEED,
    statusChance = 0.28,
    statusTurns = 2
  )
  val LUCIFER_COUNTERBREAK = KaiSkillDefinition(
    "syvial.lucifer_counterbreak", "Lucifer Counterbreak", CombatSkillCategory.ACTIVE, 0.22,
    "24 Base DMG, +20% Crit, bỏ qua 25% DF; cú phản kích trọng lực bằng GodKiller, khi trúng có 25% gây [Choáng] 1 lượt.",
    attack = true,
    baseDamage = 24,
    bonusCritChance = 0.20,
    defenseIgnore = 0.25,
    ranged = false,
    statusType = CombatEffectType.STUN,
    statusChance = 0.25,
    statusTurns = 1
  )
  val GODKILLER_TARGET_LOCK = KaiSkillDefinition(
    "syvial.godkiller_target_lock", "GodKiller Target Lock", CombatSkillCategory.PASSIVE, 0.26,
    "Đầu lượt Syvial: cảm biến Lucifer Armor khóa trọng tâm mục tiêu, +10% Crit và bỏ qua thêm 10% DF trong lượt."
  )

  val passiveSkills = listOf(LUCIFER_ARMOR_COMBAT_SYNC, GODKILLER_TARGET_LOCK)
  val activeSkills = listOf(GODKILLER_CRIMSON_CLEAVE, LUCIFER_BREAKLINE, GODKILLER_EXECUTION_ARC, LUCIFER_COUNTERBREAK)
'''
combat = replace_once(combat, syvial_old, syvial_new, "Syvial skill expansion")

iris_old = '''  val passiveSkills = listOf(THOUSANDFOLD_FIRING_SOLUTION)
  val activeSkills = listOf(ARGUS_CROSSFIRE, IVORY_EBONY_KILLBOX)
'''
iris_new = '''  val ARGUS_WEAKPOINT_VOLLEY = KaiSkillDefinition(
    "iris.argus_weakpoint_volley", "ARGUS Weakpoint Volley", CombatSkillCategory.ACTIVE, 0.32,
    "20 Base DMG, +20% Crit, bỏ qua 30% DF; Ivory & Ebony tập trung vào điểm yếu do ARGUS Terrain Read xác định.",
    attack = true,
    baseDamage = 20,
    bonusCritChance = 0.20,
    defenseIgnore = 0.30,
    ranged = true
  )
  val IVORY_EBONY_SUPPRESSION = KaiSkillDefinition(
    "iris.ivory_ebony_suppression", "Ivory & Ebony Suppression", CombatSkillCategory.ACTIVE, 0.25,
    "19 Base DMG, +10% Crit, bỏ qua 20% DF; hỏa lực song súng ghìm đường tiến, khi trúng có 24% gây [Choáng] 1 lượt.",
    attack = true,
    baseDamage = 19,
    bonusCritChance = 0.10,
    defenseIgnore = 0.20,
    ranged = true,
    statusType = CombatEffectType.STUN,
    statusChance = 0.24,
    statusTurns = 1
  )
  val ARGUS_SIGHTLINE_DISCIPLINE = KaiSkillDefinition(
    "iris.argus_sightline_discipline", "ARGUS Sightline Discipline", CombatSkillCategory.PASSIVE, 0.28,
    "Đầu lượt Iris: ARGUS Terrain Read giữ đường ngắm sạch, +10% Crit và bỏ qua thêm 10% DF trong lượt."
  )

  val passiveSkills = listOf(THOUSANDFOLD_FIRING_SOLUTION, ARGUS_SIGHTLINE_DISCIPLINE)
  val activeSkills = listOf(ARGUS_CROSSFIRE, IVORY_EBONY_KILLBOX, ARGUS_WEAKPOINT_VOLLEY, IVORY_EBONY_SUPPRESSION)
'''
combat = replace_once(combat, iris_old, iris_new, "Iris skill expansion")

syvial_passive_old = '''    if (proc(SyvialSkillBook.LUCIFER_ARMOR_COMBAT_SYNC)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Lucifer Armor Combat Sync]."
      )
    }

    val triggered = SyvialSkillBook.activeSkills.filter { proc(it) }
'''
syvial_passive_new = '''    if (proc(SyvialSkillBook.LUCIFER_ARMOR_COMBAT_SYNC)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Lucifer Armor Combat Sync]."
      )
    }
    if (proc(SyvialSkillBook.GODKILLER_TARGET_LOCK)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.10
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [GodKiller Target Lock]."
      )
    }

    val triggered = SyvialSkillBook.activeSkills.filter { proc(it) }
'''
combat = replace_once(combat, syvial_passive_old, syvial_passive_new, "Syvial passive resolver")

iris_passive_old = '''    if (proc(IrisSkillBook.THOUSANDFOLD_FIRING_SOLUTION)) {
      buff.critBonus += 0.15
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Thousandfold Firing Solution]."
      )
    }

    val triggered = IrisSkillBook.activeSkills.filter { proc(it) }
'''
iris_passive_new = '''    if (proc(IrisSkillBook.THOUSANDFOLD_FIRING_SOLUTION)) {
      buff.critBonus += 0.15
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Thousandfold Firing Solution]."
      )
    }
    if (proc(IrisSkillBook.ARGUS_SIGHTLINE_DISCIPLINE)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.10
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [ARGUS Sightline Discipline]."
      )
    }

    val triggered = IrisSkillBook.activeSkills.filter { proc(it) }
'''
combat = replace_once(combat, iris_passive_old, iris_passive_new, "Iris passive resolver")

if "SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED" not in combat:
    combat = combat.replace(
        "// SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED",
        "// SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED\n// SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED",
        1,
    )

for marker in [
    "SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED",
    "GodKiller Execution Arc",
    "Lucifer Counterbreak",
    "GodKiller Target Lock",
    "ARGUS Weakpoint Volley",
    "Ivory & Ebony Suppression",
    "ARGUS Sightline Discipline",
]:
    if marker not in combat:
        raise RuntimeError(f"cycle 2 generated marker missing: {marker}")
COMBAT.write_text(combat, encoding="utf-8")

(TESTS / "SyvialIrisSkillCycle2GeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIrisSkillCycle2GeneratedTest {
  @Test fun cycleTwoReachesFourActiveAndTwoPassivePerCharacter() {
    assertEquals(4, SyvialSkillBook.activeSkills.size)
    assertEquals(2, SyvialSkillBook.passiveSkills.size)
    assertEquals(4, IrisSkillBook.activeSkills.size)
    assertEquals(2, IrisSkillBook.passiveSkills.size)
    assertEquals(6, SyvialSkillBook.allSkills.size)
    assertEquals(6, IrisSkillBook.allSkills.size)
  }

  @Test fun everyCycleTwoProcAndStatusChanceStaysInsideTwentyToFortyPercent() {
    val cycleTwo = listOf(
      SyvialSkillBook.GODKILLER_EXECUTION_ARC,
      SyvialSkillBook.LUCIFER_COUNTERBREAK,
      SyvialSkillBook.GODKILLER_TARGET_LOCK,
      IrisSkillBook.ARGUS_WEAKPOINT_VOLLEY,
      IrisSkillBook.IVORY_EBONY_SUPPRESSION,
      IrisSkillBook.ARGUS_SIGHTLINE_DISCIPLINE,
    )
    assertTrue(cycleTwo.all { it.procChance in 0.20..0.40 })
    assertTrue(SyvialSkillBook.GODKILLER_EXECUTION_ARC.statusChance in 0.20..0.40)
    assertTrue(SyvialSkillBook.LUCIFER_COUNTERBREAK.statusChance in 0.20..0.40)
    assertTrue(IrisSkillBook.IVORY_EBONY_SUPPRESSION.statusChance in 0.20..0.40)
  }

  @Test fun rolesRemainDistinct() {
    assertTrue(SyvialSkillBook.activeSkills.all { !it.ranged })
    assertTrue(IrisSkillBook.activeSkills.all { it.ranged })
    assertEquals(CombatEffectType.BLEED, SyvialSkillBook.GODKILLER_EXECUTION_ARC.statusType)
    assertEquals(CombatEffectType.STUN, SyvialSkillBook.LUCIFER_COUNTERBREAK.statusType)
    assertEquals(CombatEffectType.STUN, IrisSkillBook.IVORY_EBONY_SUPPRESSION.statusType)
  }
}
''', encoding="utf-8")

print("Syvial/Iris cycle 2 finalized: each now has 4 Active + 2 Passive, with all new proc/status chances in 20-40%.")
