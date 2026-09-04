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


# Scheduled character AUTO skill iteration.
# Latest-main SHA starts with 0x76; among the remaining semantically eligible
# characters Kai/Lucia, 0x76 % 2 selects Kai. Keep the skill inside Kai R10:
# SRU Assault Rifle MK19, ordinary weapon/Armor resolution, no invented ammo trait.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Zero-Line Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    ultimate_index = next(
        (i for i, line in enumerate(lines) if 's("Guilty Crown Override",' in line),
        -1,
    )
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
constant = "  internal const val KAI_ZERO_LINE_BURST_CHANCE_PERCENT = 40\n"
if "KAI_ZERO_LINE_BURST_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Zero-Line Burst proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun kaiZeroLineBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, KAI_ID) != null && partyTurnActorMatches(state, KAI_ID)

  internal fun kaiZeroLineBurstDamage(weaponDamage: Int, armor: Int): Int =
    companionSkillDamage(weaponDamage, 105, armor)

'''
if "kaiZeroLineBurstEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Zero-Line Burst helpers",
    )
elif "kaiZeroLineBurstDamage" not in combat:
    combat = replace_once(
        combat,
        "  internal fun kaiZeroLineBurstEligible(state: GameState): Boolean =\n    activePartyCharacter(state, KAI_ID) != null && partyTurnActorMatches(state, KAI_ID)\n\n",
        helper,
        "Zero-Line Burst damage helper",
    )

# Resolve after the existing scheduled AUTO layers and before the authoritative
# post-player-action death gate. The RNG roll only exists on Kai's own actor turn.
if "KAI_ZERO_LINE_BURST_R01" not in combat:
    syvial_marker = "    // SYVIAL_BLACKLINE_CLEAVE_R01:"
    syvial_pos = combat.find(syvial_marker)
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
      log += "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên ${c.entityName}"
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

for marker in (
    "KAI_ZERO_LINE_BURST_CHANCE_PERCENT = 40",
    "kaiZeroLineBurstEligible",
    "kaiZeroLineBurstDamage",
    "KAI_ZERO_LINE_BURST_R01",
    "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên ${c.entityName}",
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
      skill.kind == "AUTO" && (
        skill.effect.contains("DMG", ignoreCase = true) ||
          skill.effect.contains("sát thương", ignoreCase = true)
        )
    }

  private fun kaiCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    var state = GameState.initial().copy(
      party = PartyState(memberIds = listOf(KAI_ID))
    )
    state = CombatRuntime.start(state, "hound")
    val kai = state.characters.getValue(KAI_ID)
    return state.copy(
      characters = state.characters + (KAI_ID to kai.copy(presence = presence)),
      metadata = state.metadata + mapOf(
        "partyCombat.actorContext" to KAI_ID,
        "combat.entityHp" to "999",
        "combat.entityMaxHp" to "999"
      )
    )
  }

  @Test fun zeroLineBurstIsFortyPercentAttackingAutoAndDoesNotExceedKaiCap() {
    val skill = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Zero-Line Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("SRU Assault Rifle MK19"))
    assertEquals(40, CombatRuntime.KAI_ZERO_LINE_BURST_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(KAI_ID).count { it.name == "Zero-Line Burst" })
    assertTrue("Kai exceeds five attacking AUTO skills", attackingAutos(KAI_ID).size <= 5)
  }

  @Test fun zeroLineBurstOnlyUsesKaisOwnActiveActorTurn() {
    val valid = kaiCombat()
    assertTrue(CombatRuntime.kaiZeroLineBurstEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to LUCIA_ID)
    )
    assertFalse(CombatRuntime.kaiZeroLineBurstEligible(wrongActor))

    val inactive = kaiCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.kaiZeroLineBurstEligible(inactive))
  }

  @Test fun zeroLineBurstDamageUsesExistingWeaponArmorFormula() {
    assertEquals(85, CombatRuntime.kaiZeroLineBurstDamage(100, 20))
    assertEquals(105, CombatRuntime.kaiZeroLineBurstDamage(100, 0))
  }

  @Test fun zeroLineBurstUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..800) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Zero-Line Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Zero-Line Burst did not proc in deterministic search window")
    val skillLog = Regex("Kai sử dụng Zero-Line Burst gây sát thương -\\d+ HP lên Hound").find(result.reply)?.value
    assertTrue(skillLog != null)
    assertFalse(result.reply.contains(Regex("Kai sử dụng Zero-Line Burst gây sát thương -\\d+ HP lên Hound và gây")))
  }

  @Test fun zeroLineBurstAddsNoOwnedStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..800) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Zero-Line Burst")) {
        assertFalse(result.state.statuses.values.any { effect ->
          effect.source.contains("Zero-Line Burst", ignoreCase = true) ||
            effect.id.contains("zero-line", ignoreCase = true)
        })
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Zero-Line Burst proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Scheduled character AUTO skill applied: Kai Zero-Line Burst, 40% personal-turn proc, "
    "105% SRU Assault Rifle MK19 weapon damage, no status, compact combat log."
)
