from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
TEST = TESTS / "SyvialBlacklineCleaveTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 04.
# Deterministic-random selection from latest-main SHA 31534e...:
# 0x31 % 4 == 1 -> Syvial among Kai/Syvial/Iris/Lucia. The final AP authority
# leaves only the dedicated hourly AUTO attacks as attacking AUTO skills; before
# this patch Syvial has Hellscar Rend only, so she remains below the five-skill cap.
# Keep the new attack inside GodKiller's established mechanical greatsword role.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Blackline Cleave", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    hellscar_index = next(
        (i for i, line in enumerate(lines) if 's("Hellscar Rend", "AUTO"' in line),
        -1,
    )
    if hellscar_index < 0:
        raise RuntimeError("Blackline Cleave catalog: Hellscar Rend row missing")
    lines.insert(
        hellscar_index + 1,
        '    s("Blackline Cleave", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Syvial", "105% sát thương vũ khí bằng GodKiller."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

# Account only for the one new Syvial row; do not weaken unrelated assertions.
catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_test = replace_once(
    catalog_test,
    "    assertEquals(11, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "    assertEquals(12, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "Blackline Cleave Syvial catalog count regression",
)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val SYVIAL_BLACKLINE_CLEAVE_CHANCE_PERCENT = 40\n"
if "SYVIAL_BLACKLINE_CLEAVE_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Blackline Cleave proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun syvialBlacklineCleaveEligible(state: GameState): Boolean =
    activePartyCharacter(state, SYVIAL_ID) != null && partyTurnActorMatches(state, SYVIAL_ID)

'''
if "syvialBlacklineCleaveEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Blackline Cleave eligibility helper",
    )

# Resolve beside the existing dedicated character AUTO layers and before the
# post-player-action death gate. The roll occurs only on Syvial's serialized actor
# turn, uses GodKiller's normal weapon damage, and still respects Entity Armor.
if "SYVIAL_BLACKLINE_CLEAVE_R01" not in combat:
    iris_marker = "    // IRIS_PARALLAX_BURST_R01:"
    iris_pos = combat.find(iris_marker)
    if iris_pos < 0:
        raise RuntimeError("Blackline Cleave runtime: Iris AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", iris_pos)
    if death_pos < 0:
        raise RuntimeError("Blackline Cleave runtime: post-player-action death gate missing")
    block = '''    // SYVIAL_BLACKLINE_CLEAVE_R01: one independent 40% AUTO roll on Syvial's own valid actor turn.
    if (
      syvialBlacklineCleaveEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 359), 100) < SYVIAL_BLACKLINE_CLEAVE_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID),
        105,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Syvial sử dụng Blackline Cleave gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "SYVIAL_BLACKLINE_CLEAVE_CHANCE_PERCENT = 40",
    "syvialBlacklineCleaveEligible",
    "SYVIAL_BLACKLINE_CLEAVE_R01",
    "Syvial sử dụng Blackline Cleave gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Blackline Cleave runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialBlacklineCleaveTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" && skill.effect.contains("sát thương", ignoreCase = true)
    }

  private fun syvialCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    var state = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID))
    )
    state = CombatRuntime.start(state, "diep_minh")
    val syvial = state.characters.getValue(SYVIAL_ID)
    return state.copy(
      characters = state.characters + (SYVIAL_ID to syvial.copy(presence = presence)),
      metadata = state.metadata + ("partyCombat.actorContext" to SYVIAL_ID)
    )
  }

  @Test fun blacklineCleaveIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Blackline Cleave" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("GodKiller"))
    assertEquals(40, CombatRuntime.SYVIAL_BLACKLINE_CLEAVE_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(SYVIAL_ID).count { it.name == "Blackline Cleave" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun blacklineCleaveOnlyUsesSyvialsOwnActiveActorTurn() {
    val valid = syvialCombat()
    assertTrue(CombatRuntime.syvialBlacklineCleaveEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.syvialBlacklineCleaveEligible(wrongActor))

    val inactive = syvialCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.syvialBlacklineCleaveEligible(inactive))
  }

  @Test fun blacklineCleaveDealsArmoredGodKillerDamageAndUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..600) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Blackline Cleave")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Blackline Cleave did not proc in deterministic search window")
    val skillLog = Regex("Syvial sử dụng Blackline Cleave gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertTrue(skillLog != null)
    assertFalse(result.reply.contains(Regex("Syvial sử dụng Blackline Cleave gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(result.reply.contains("Blackline Cleave 40%"))
    assertFalse(result.reply.contains("Blackline Cleave Weapon DMG"))
    assertFalse(result.reply.contains("Blackline Cleave Armor"))
  }

  @Test fun blacklineCleaveCreatesNoPersistentStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..600) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Blackline Cleave")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Blackline Cleave proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 04 applied: Syvial Blackline Cleave, 40% personal-turn proc, "
    "105% GodKiller weapon damage, no status, compact combat log."
)
