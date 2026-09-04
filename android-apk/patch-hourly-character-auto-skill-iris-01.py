from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
TEST = TESTS / "IrisParallaxBurstTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 03.
# Deterministic-random selection from latest-main SHA c12bfb...:
# 0xc1 % 4 == 1 -> Iris among Kai/Syvial/Iris/Lucia, all still below five
# attacking AUTO skills in the final gameplay catalog. Keep the skill inside
# Iris's established Ivory & Ebony gunslinger / target-eliminator role.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Parallax Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    ultimate_index = next(
        (i for i, line in enumerate(lines) if 's("ARGUS // Thousandfold Execution",' in line),
        -1,
    )
    if ultimate_index < 0:
        raise RuntimeError("Parallax Burst catalog: Iris ultimate row missing")
    lines.insert(
        ultimate_index,
        '    s("Parallax Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Iris", "100% sát thương vũ khí bằng Ivory & Ebony."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

# Preserve the catalog regression suite while accounting for the single new row.
catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_test = replace_once(
    catalog_test,
    "    assertEquals(8, CompanionSkillCatalog.forCharacter(IRIS_ID).size)\n",
    "    assertEquals(9, CompanionSkillCatalog.forCharacter(IRIS_ID).size)\n",
    "Parallax Burst Iris catalog count regression",
)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val IRIS_PARALLAX_BURST_CHANCE_PERCENT = 40\n"
if "IRIS_PARALLAX_BURST_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Parallax Burst proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun irisParallaxBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, IRIS_ID) != null && partyTurnActorMatches(state, IRIS_ID)

'''
if "irisParallaxBurstEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Parallax Burst eligibility helper",
    )

# Resolve after the already-installed character AUTO layers and before the
# authoritative post-player-action death gate. The proc is rolled only on
# Iris's own serialized actor turn; normal weapon damage still goes through Armor.
if "IRIS_PARALLAX_BURST_R01" not in combat:
    lucia_marker = "    // LUCIA_CENTERLINE_BURST_R01:"
    lucia_pos = combat.find(lucia_marker)
    if lucia_pos < 0:
        raise RuntimeError("Parallax Burst runtime: Lucia AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", lucia_pos)
    if death_pos < 0:
        raise RuntimeError("Parallax Burst runtime: post-player-action death gate missing")
    block = '''    // IRIS_PARALLAX_BURST_R01: one independent 40% AUTO roll on Iris's own valid actor turn.
    if (
      irisParallaxBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 347), 100) < IRIS_PARALLAX_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID),
        100,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Iris sử dụng Parallax Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "IRIS_PARALLAX_BURST_CHANCE_PERCENT = 40",
    "irisParallaxBurstEligible",
    "IRIS_PARALLAX_BURST_R01",
    "Iris sử dụng Parallax Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Parallax Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrisParallaxBurstTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" && skill.effect.contains("sát thương", ignoreCase = true)
    }

  private fun irisCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    var state = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID))
    )
    state = CombatRuntime.start(state, "diep_minh")
    val iris = state.characters.getValue(IRIS_ID)
    return state.copy(
      characters = state.characters + (IRIS_ID to iris.copy(presence = presence)),
      metadata = state.metadata + ("partyCombat.actorContext" to IRIS_ID)
    )
  }

  @Test fun parallaxBurstIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(IRIS_ID).single { it.name == "Parallax Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("Ivory & Ebony"))
    assertEquals(40, CombatRuntime.IRIS_PARALLAX_BURST_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(IRIS_ID).count { it.name == "Parallax Burst" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun parallaxBurstOnlyUsesIrissOwnActiveActorTurn() {
    val valid = irisCombat()
    assertTrue(CombatRuntime.irisParallaxBurstEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.irisParallaxBurstEligible(wrongActor))

    val inactive = irisCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.irisParallaxBurstEligible(inactive))
  }

  @Test fun parallaxBurstDealsNormalArmoredWeaponDamageAndUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..600) {
      var state = irisCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Iris sử dụng Parallax Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Parallax Burst did not proc in deterministic search window")
    val skillLog = Regex("Iris sử dụng Parallax Burst gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertTrue(skillLog != null)
    assertFalse(result.reply.contains(Regex("Iris sử dụng Parallax Burst gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(result.reply.contains("Parallax Burst 40%"))
    assertFalse(result.reply.contains("Parallax Burst Weapon DMG"))
    assertFalse(result.reply.contains("Parallax Burst Armor"))
  }

  @Test fun parallaxBurstCreatesNoPersistentStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..600) {
      var state = irisCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Iris sử dụng Parallax Burst")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Parallax Burst proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 03 applied: Iris Parallax Burst, 40% personal-turn proc, "
    "100% Ivory & Ebony weapon damage, no status, compact combat log."
)
