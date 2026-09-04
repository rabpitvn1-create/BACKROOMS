from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "KaiZeroLineBurstTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


catalog = CATALOG.read_text(encoding="utf-8")
if 's("Zero-Line Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    ultimate_index = next((i for i, line in enumerate(lines) if 's("Guilty Crown Override",' in line), -1)
    if ultimate_index < 0:
        raise RuntimeError("Zero-Line Burst catalog: Guilty Crown Override row missing")
    lines.insert(
        ultimate_index,
        '    s("Zero-Line Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Kai", "105% sát thương vũ khí bằng SRU Assault Rifle MK19."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
if "KAI_ZERO_LINE_BURST_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + "  internal const val KAI_ZERO_LINE_BURST_CHANCE_PERCENT = 40\n",
        "Zero-Line Burst proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helpers = '''  internal fun kaiZeroLineBurstEligibility(activeAlive: Boolean, ownActorTurn: Boolean): Boolean =
    activeAlive && ownActorTurn

  internal fun kaiZeroLineBurstEligible(state: GameState): Boolean =
    kaiZeroLineBurstEligibility(
      activePartyCharacter(state, KAI_ID) != null,
      partyTurnActorMatches(state, KAI_ID)
    )

  internal fun kaiZeroLineBurstDamage(weaponDamage: Int, armor: Int): Int =
    companionSkillDamage(weaponDamage, 105, armor)

  internal fun kaiZeroLineBurstLog(damage: Int, entityName: String): String =
    "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên $entityName"

'''
helper_start = combat.find("  internal fun kaiZeroLineBurst")
helper_end = combat.find(helper_anchor, max(0, helper_start))
if helper_start >= 0 and helper_end >= 0:
    combat = combat[:helper_start] + helpers + combat[helper_end:]
elif "kaiZeroLineBurstEligibility" not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "Zero-Line Burst helpers")

if "KAI_ZERO_LINE_BURST_R01" not in combat:
    syvial_pos = combat.find("    // SYVIAL_BLACKLINE_CLEAVE_R01:")
    if syvial_pos < 0:
        raise RuntimeError("Zero-Line Burst runtime: latest scheduled Syvial AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", syvial_pos)
    if death_pos < 0:
        raise RuntimeError("Zero-Line Burst runtime: post-player-action death gate missing")
    block = '''    // KAI_ZERO_LINE_BURST_R01: one independent 40% AUTO roll on Kai's own valid actor turn.
    if (
      kaiZeroLineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 371), 100) < KAI_ZERO_LINE_BURST_CHANCE_PERCENT
    ) {
      val damage = kaiZeroLineBurstDamage(
        CharacterStatEngine.weaponDamage(resolvedState, KAI_ID),
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += kaiZeroLineBurstLog(damage, c.entityName)
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]
else:
    combat = combat.replace(
        '''      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, KAI_ID),
        105,
        profile.armor
      )''',
        '''      val damage = kaiZeroLineBurstDamage(
        CharacterStatEngine.weaponDamage(resolvedState, KAI_ID),
        profile.armor
      )''',
        1,
    )
    combat = combat.replace(
        '      log += "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên ${c.entityName}"\n',
        '      log += kaiZeroLineBurstLog(damage, c.entityName)\n',
        1,
    )

for marker in (
    "KAI_ZERO_LINE_BURST_CHANCE_PERCENT = 40",
    "kaiZeroLineBurstEligibility",
    "kaiZeroLineBurstEligible",
    "kaiZeroLineBurstDamage",
    "kaiZeroLineBurstLog",
    "KAI_ZERO_LINE_BURST_R01",
    "log += kaiZeroLineBurstLog(damage, c.entityName)",
):
    if marker not in combat:
        raise RuntimeError("Zero-Line Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiZeroLineBurstTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" && skill.effect.contains("sát thương", ignoreCase = true)
    }

  @Test fun zeroLineBurstCatalogAndProcContractStayWithinAttackAutoCap() {
    val skill = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Zero-Line Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("SRU Assault Rifle MK19"))
    assertEquals(40, CombatRuntime.KAI_ZERO_LINE_BURST_CHANCE_PERCENT)
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun zeroLineBurstEligibilityRequiresBothAliveActiveAndOwnTurn() {
    assertTrue(CombatRuntime.kaiZeroLineBurstEligibility(activeAlive = true, ownActorTurn = true))
    assertFalse(CombatRuntime.kaiZeroLineBurstEligibility(activeAlive = false, ownActorTurn = true))
    assertFalse(CombatRuntime.kaiZeroLineBurstEligibility(activeAlive = true, ownActorTurn = false))
    assertFalse(CombatRuntime.kaiZeroLineBurstEligibility(activeAlive = false, ownActorTurn = false))
  }

  @Test fun zeroLineBurstUsesNormalArmorDamageAndAddsNoStatusContract() {
    assertEquals(85, CombatRuntime.kaiZeroLineBurstDamage(100, 20))
    assertEquals(105, CombatRuntime.kaiZeroLineBurstDamage(100, 0))
    val effect = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Zero-Line Burst" }.effect
    listOf("Poison", "Bleed", "Stun", "Freeze", "Burn").forEach { status ->
      assertFalse(effect.contains(status, ignoreCase = true))
    }
  }

  @Test fun zeroLineBurstLogIsExactlyCompactFormat() {
    assertEquals(
      "Kai sử dụng Zero-Line Burst gây sát thương -18 HP lên Hound",
      CombatRuntime.kaiZeroLineBurstLog(18, "Hound")
    )
  }
}
''', encoding="utf-8")

print("Scheduled Kai AUTO skill applied: Zero-Line Burst, 40%, 105% MK19 damage, compact log.")
