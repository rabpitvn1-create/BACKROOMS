from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "LuciaCenterlineBurstTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 02.
# Selection is deterministic-random from latest-main SHA 43c545... (0x43 % 4 -> Lucia)
# among Kai/Syvial/Iris/Lucia, all of whom remain below five attacking AUTO skills.
# Keep Lucia inside her human/M4A1 power scale: this is a controlled rifle burst,
# not supernatural ammunition or a new capability.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Centerline Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    too_young_index = next(
        (i for i, line in enumerate(lines) if 's("Too Young To Die",' in line),
        -1,
    )
    if too_young_index < 0:
        raise RuntimeError("Centerline Burst catalog: Too Young To Die row missing")
    lines.insert(
        too_young_index + 1,
        '    s("Centerline Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Lucia", "100% sát thương vũ khí M4A1."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val LUCIA_CENTERLINE_BURST_CHANCE_PERCENT = 40\n"
if "LUCIA_CENTERLINE_BURST_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Centerline Burst proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun luciaCenterlineBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, LUCIA_ID) != null && partyTurnActorMatches(state, LUCIA_ID)

'''
if "luciaCenterlineBurstEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Centerline Burst eligibility helper",
    )

# Run after the existing Syvial AUTO layer and before the authoritative
# post-player-action death gate. The roll only occurs on Lucia's own serialized
# actor turn and the damage still uses the normal weapon/Armor helper.
if "LUCIA_CENTERLINE_BURST_R01" not in combat:
    syvial_marker = "    // SYVIAL_HELLSCAR_REND_R01:"
    syvial_pos = combat.find(syvial_marker)
    if syvial_pos < 0:
        raise RuntimeError("Centerline Burst runtime: Syvial AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", syvial_pos)
    if death_pos < 0:
        raise RuntimeError("Centerline Burst runtime: post-player-action death gate missing")
    block = '''    // LUCIA_CENTERLINE_BURST_R01: one independent 40% AUTO roll on Lucia's own valid actor turn.
    if (
      luciaCenterlineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 331), 100) < LUCIA_CENTERLINE_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID),
        100,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Lucia sử dụng Centerline Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "LUCIA_CENTERLINE_BURST_CHANCE_PERCENT = 40",
    "luciaCenterlineBurstEligible",
    "LUCIA_CENTERLINE_BURST_R01",
    "Lucia sử dụng Centerline Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Centerline Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuciaCenterlineBurstTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" && skill.effect.contains("sát thương", ignoreCase = true)
    }

  private fun luciaCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    var state = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID))
    )
    state = CombatRuntime.start(state, "diep_minh")
    val lucia = state.characters.getValue(LUCIA_ID)
    return state.copy(
      characters = state.characters + (LUCIA_ID to lucia.copy(presence = presence)),
      metadata = state.metadata + ("partyCombat.actorContext" to LUCIA_ID)
    )
  }

  @Test fun centerlineBurstIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).single { it.name == "Centerline Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertEquals(40, CombatRuntime.LUCIA_CENTERLINE_BURST_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(LUCIA_ID).count { it.name == "Centerline Burst" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun centerlineBurstOnlyUsesLuciasOwnActiveActorTurn() {
    val valid = luciaCombat()
    assertTrue(CombatRuntime.luciaCenterlineBurstEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.luciaCenterlineBurstEligible(wrongActor))

    val inactive = luciaCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.luciaCenterlineBurstEligible(inactive))
  }

  @Test fun centerlineBurstDealsNormalArmoredWeaponDamageAndUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..600) {
      var state = luciaCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Lucia sử dụng Centerline Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Centerline Burst did not proc in deterministic search window")
    assertTrue(
      result.reply.contains(Regex("Lucia sử dụng Centerline Burst gây sát thương -\\d+ HP lên Diệp Minh(?:\\.|$| )"))
    )
    assertFalse(result.reply.contains("Centerline Burst gây sát thương", ignoreCase = true) && result.reply.contains("40%"))
    assertFalse(result.reply.contains("Centerline Burst gây sát thương", ignoreCase = true) && result.reply.contains("Armor"))
    assertFalse(result.reply.contains("Centerline Burst gây sát thương", ignoreCase = true) && result.reply.contains("lượt."))
  }

  @Test fun centerlineBurstCreatesNoPersistentStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..600) {
      var state = luciaCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Lucia sử dụng Centerline Burst")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Centerline Burst proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 02 applied: Lucia Centerline Burst, 40% personal-turn proc, "
    "100% M4A1 weapon damage, no status, compact combat log."
)
