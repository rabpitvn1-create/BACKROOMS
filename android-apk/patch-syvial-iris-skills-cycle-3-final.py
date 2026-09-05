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
        raise RuntimeError(f"Syvial/Iris cycle 3 {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
if "SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED" not in combat:
    raise RuntimeError("Syvial/Iris cycle 3 requires cycle 2 skill layer")

syvial_old = '''  val passiveSkills = listOf(LUCIFER_ARMOR_COMBAT_SYNC, GODKILLER_TARGET_LOCK)
  val activeSkills = listOf(GODKILLER_CRIMSON_CLEAVE, LUCIFER_BREAKLINE, GODKILLER_EXECUTION_ARC, LUCIFER_COUNTERBREAK)
'''
syvial_new = '''  val GODKILLER_PURSUIT_CUT = KaiSkillDefinition(
    "syvial.godkiller_pursuit_cut", "GodKiller Pursuit Cut", CombatSkillCategory.ACTIVE, 0.30,
    "26 Base DMG, +12% Crit, bỏ qua 25% DF; Syvial bám nhịp di chuyển của mục tiêu bằng GodKiller, khi trúng có 32% gây [Chảy máu] 2 lượt.",
    attack = true,
    baseDamage = 26,
    bonusCritChance = 0.12,
    defenseIgnore = 0.25,
    ranged = false,
    statusType = CombatEffectType.BLEED,
    statusChance = 0.32,
    statusTurns = 2
  )
  val LUCIFER_ARMOR_BREAKSTEP = KaiSkillDefinition(
    "syvial.lucifer_armor_breakstep", "Lucifer Armor Breakstep", CombatSkillCategory.ACTIVE, 0.23,
    "23 Base DMG, +18% Crit, bỏ qua 35% DF; Lucifer Armor hỗ trợ đổi trọng tâm để Syvial chém xuyên tuyến phòng thủ bằng GodKiller.",
    attack = true,
    baseDamage = 23,
    bonusCritChance = 0.18,
    defenseIgnore = 0.35,
    ranged = false
  )
  val LUCIFER_CORE_EDGE_DISCIPLINE = KaiSkillDefinition(
    "syvial.lucifer_core_edge_discipline", "Lucifer Core Edge Discipline", CombatSkillCategory.PASSIVE, 0.27,
    "Đầu lượt Syvial: quỷ lực từ Lucifer Core được giữ ổn định qua Lucifer Armor, +5% Crit và bỏ qua thêm 15% DF trong lượt."
  )

  val passiveSkills = listOf(LUCIFER_ARMOR_COMBAT_SYNC, GODKILLER_TARGET_LOCK, LUCIFER_CORE_EDGE_DISCIPLINE)
  val activeSkills = listOf(GODKILLER_CRIMSON_CLEAVE, LUCIFER_BREAKLINE, GODKILLER_EXECUTION_ARC, LUCIFER_COUNTERBREAK, GODKILLER_PURSUIT_CUT, LUCIFER_ARMOR_BREAKSTEP)
'''
combat = replace_once(combat, syvial_old, syvial_new, "Syvial final skill expansion")

iris_old = '''  val passiveSkills = listOf(THOUSANDFOLD_FIRING_SOLUTION, ARGUS_SIGHTLINE_DISCIPLINE)
  val activeSkills = listOf(ARGUS_CROSSFIRE, IVORY_EBONY_KILLBOX, ARGUS_WEAKPOINT_VOLLEY, IVORY_EBONY_SUPPRESSION)
'''
iris_new = '''  val ARGUS_FLANK_PUNISHER = KaiSkillDefinition(
    "iris.argus_flank_punisher", "ARGUS Flank Punisher", CombatSkillCategory.ACTIVE, 0.35,
    "19 Base DMG, +18% Crit, bỏ qua 25% DF; Iris khai thác góc lệch sườn do ARGUS Terrain Read xác định rồi khai hỏa Ivory & Ebony.",
    attack = true,
    baseDamage = 19,
    bonusCritChance = 0.18,
    defenseIgnore = 0.25,
    ranged = true
  )
  val IVORY_EBONY_TWIN_BURST = KaiSkillDefinition(
    "iris.ivory_ebony_twin_burst", "Ivory & Ebony Twin Burst", CombatSkillCategory.ACTIVE, 0.21,
    "22 Base DMG, +12% Crit, bỏ qua 20% DF; hai loạt đạn quỷ lực liên tiếp, khi trúng có 27% gây [Chảy máu] 2 lượt.",
    attack = true,
    baseDamage = 22,
    bonusCritChance = 0.12,
    defenseIgnore = 0.20,
    ranged = true,
    statusType = CombatEffectType.BLEED,
    statusChance = 0.27,
    statusTurns = 2
  )
  val THOUSANDFOLD_THREAT_SORT = KaiSkillDefinition(
    "iris.thousandfold_threat_sort", "Thousandfold Threat Sort", CombatSkillCategory.PASSIVE, 0.30,
    "Đầu lượt Iris: Thousandfold Cognition ưu tiên mục tiêu và đường bắn đã quan sát, +10% Crit và bỏ qua thêm 5% DF trong lượt."
  )

  val passiveSkills = listOf(THOUSANDFOLD_FIRING_SOLUTION, ARGUS_SIGHTLINE_DISCIPLINE, THOUSANDFOLD_THREAT_SORT)
  val activeSkills = listOf(ARGUS_CROSSFIRE, IVORY_EBONY_KILLBOX, ARGUS_WEAKPOINT_VOLLEY, IVORY_EBONY_SUPPRESSION, ARGUS_FLANK_PUNISHER, IVORY_EBONY_TWIN_BURST)
'''
combat = replace_once(combat, iris_old, iris_new, "Iris final skill expansion")

syvial_passive_old = '''    if (proc(SyvialSkillBook.GODKILLER_TARGET_LOCK)) {
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
syvial_passive_new = '''    if (proc(SyvialSkillBook.GODKILLER_TARGET_LOCK)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.10
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [GodKiller Target Lock]."
      )
    }
    if (proc(SyvialSkillBook.LUCIFER_CORE_EDGE_DISCIPLINE)) {
      buff.critBonus += 0.05
      buff.defenseIgnore += 0.15
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Lucifer Core Edge Discipline]."
      )
    }

    val triggered = SyvialSkillBook.activeSkills.filter { proc(it) }
'''
combat = replace_once(combat, syvial_passive_old, syvial_passive_new, "Syvial final passive resolver")

iris_passive_old = '''    if (proc(IrisSkillBook.ARGUS_SIGHTLINE_DISCIPLINE)) {
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
iris_passive_new = '''    if (proc(IrisSkillBook.ARGUS_SIGHTLINE_DISCIPLINE)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.10
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [ARGUS Sightline Discipline]."
      )
    }
    if (proc(IrisSkillBook.THOUSANDFOLD_THREAT_SORT)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.05
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Thousandfold Threat Sort]."
      )
    }

    val triggered = IrisSkillBook.activeSkills.filter { proc(it) }
'''
combat = replace_once(combat, iris_passive_old, iris_passive_new, "Iris final passive resolver")

if "SYVIAL_IRIS_SKILLS_CYCLE_3_PATCHED" not in combat:
    combat = combat.replace(
        "// SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED",
        "// SYVIAL_IRIS_SKILLS_CYCLE_2_PATCHED\n// SYVIAL_IRIS_SKILLS_CYCLE_3_PATCHED",
        1,
    )

for marker in [
    "SYVIAL_IRIS_SKILLS_CYCLE_3_PATCHED",
    "GodKiller Pursuit Cut",
    "Lucifer Armor Breakstep",
    "Lucifer Core Edge Discipline",
    "ARGUS Flank Punisher",
    "Ivory & Ebony Twin Burst",
    "Thousandfold Threat Sort",
]:
    if marker not in combat:
        raise RuntimeError(f"cycle 3 generated marker missing: {marker}")
COMBAT.write_text(combat, encoding="utf-8")

(TESTS / "SyvialIrisSkillCycle2GeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIrisSkillCycle2GeneratedTest {
  @Test fun finalCycleReachesSixActiveAndThreePassivePerCharacter() {
    assertEquals(6, SyvialSkillBook.activeSkills.size)
    assertEquals(3, SyvialSkillBook.passiveSkills.size)
    assertEquals(6, IrisSkillBook.activeSkills.size)
    assertEquals(3, IrisSkillBook.passiveSkills.size)
    assertEquals(9, SyvialSkillBook.allSkills.size)
    assertEquals(9, IrisSkillBook.allSkills.size)
  }

  @Test fun everyFinalCycleProcAndStatusChanceStaysInsideTwentyToFortyPercent() {
    val cycleThree = listOf(
      SyvialSkillBook.GODKILLER_PURSUIT_CUT,
      SyvialSkillBook.LUCIFER_ARMOR_BREAKSTEP,
      SyvialSkillBook.LUCIFER_CORE_EDGE_DISCIPLINE,
      IrisSkillBook.ARGUS_FLANK_PUNISHER,
      IrisSkillBook.IVORY_EBONY_TWIN_BURST,
      IrisSkillBook.THOUSANDFOLD_THREAT_SORT,
    )
    assertTrue(cycleThree.all { it.procChance in 0.20..0.40 })
    assertTrue(SyvialSkillBook.GODKILLER_PURSUIT_CUT.statusChance in 0.20..0.40)
    assertTrue(IrisSkillBook.IVORY_EBONY_TWIN_BURST.statusChance in 0.20..0.40)
  }

  @Test fun finalCyclePreservesRoleAndCanonBoundaries() {
    assertTrue(SyvialSkillBook.activeSkills.all { !it.ranged })
    assertTrue(IrisSkillBook.activeSkills.all { it.ranged })
    assertEquals(CombatEffectType.BLEED, SyvialSkillBook.GODKILLER_PURSUIT_CUT.statusType)
    assertEquals(CombatEffectType.BLEED, IrisSkillBook.IVORY_EBONY_TWIN_BURST.statusType)
  }
}
''', encoding="utf-8")

print("Syvial/Iris cycle 3 finalized: each now has exactly 6 Active + 3 Passive, with all new proc/status chances in 20-40%.")
