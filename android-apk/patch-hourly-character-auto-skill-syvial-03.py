from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
TEST = TESTS / "SyvialIronArcSeverTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 05.
# Deterministic-random selection from latest-main SHA 99b2b7...:
# 0x99 % 4 == 1 -> Syvial among Kai/Syvial/Iris/Lucia. Final AP authority
# converts the legacy percentage-proc kit into explicit AP skills, so only the
# dedicated hourly AUTO attacks count toward this automation cap. Syvial has
# Hellscar Rend and Blackline Cleave before this patch, leaving room below five.
# Iron Arc Sever stays inside GodKiller's established mechanical greatsword role.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Iron Arc Sever", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    blackline_index = next(
        (i for i, line in enumerate(lines) if 's("Blackline Cleave", "AUTO"' in line),
        -1,
    )
    if blackline_index < 0:
        raise RuntimeError("Iron Arc Sever catalog: Blackline Cleave row missing")
    lines.insert(
        blackline_index + 1,
        '    s("Iron Arc Sever", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Syvial", "110% sát thương vũ khí bằng GodKiller."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

# Update only the one expected Syvial catalog-size assertion after the new row.
catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_test = replace_once(
    catalog_test,
    "    assertEquals(12, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "    assertEquals(13, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "Iron Arc Sever Syvial catalog count regression",
)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val SYVIAL_IRON_ARC_SEVER_CHANCE_PERCENT = 40\n"
if "SYVIAL_IRON_ARC_SEVER_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Iron Arc Sever proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun syvialIronArcSeverEligible(state: GameState): Boolean =
    activePartyCharacter(state, SYVIAL_ID) != null && partyTurnActorMatches(state, SYVIAL_ID)

'''
if "syvialIronArcSeverEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Iron Arc Sever eligibility helper",
    )

# Resolve beside the existing dedicated character AUTO layers before the
# post-player-action death gate. This is an independent 40% personal-turn roll,
# uses GodKiller's normal weapon damage, and respects Entity Armor.
if "SYVIAL_IRON_ARC_SEVER_R01" not in combat:
    blackline_marker = "    // SYVIAL_BLACKLINE_CLEAVE_R01:"
    blackline_pos = combat.find(blackline_marker)
    if blackline_pos < 0:
        raise RuntimeError("Iron Arc Sever runtime: Blackline Cleave AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", blackline_pos)
    if death_pos < 0:
        raise RuntimeError("Iron Arc Sever runtime: post-player-action death gate missing")
    block = '''    // SYVIAL_IRON_ARC_SEVER_R01: one independent 40% AUTO roll on Syvial's own valid actor turn.
    if (
      syvialIronArcSeverEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 373), 100) < SYVIAL_IRON_ARC_SEVER_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID),
        110,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Syvial sử dụng Iron Arc Sever gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "SYVIAL_IRON_ARC_SEVER_CHANCE_PERCENT = 40",
    "syvialIronArcSeverEligible",
    "SYVIAL_IRON_ARC_SEVER_R01",
    "Syvial sử dụng Iron Arc Sever gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Iron Arc Sever runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIronArcSeverTest {
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

  @Test fun ironArcSeverIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Iron Arc Sever" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("GodKiller"))
    assertEquals(40, CombatRuntime.SYVIAL_IRON_ARC_SEVER_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(SYVIAL_ID).count { it.name == "Iron Arc Sever" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun ironArcSeverOnlyUsesSyvialsOwnActiveActorTurn() {
    val valid = syvialCombat()
    assertTrue(CombatRuntime.syvialIronArcSeverEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.syvialIronArcSeverEligible(wrongActor))

    val inactive = syvialCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.syvialIronArcSeverEligible(inactive))
  }

  @Test fun ironArcSeverDealsArmoredGodKillerDamageAndUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..800) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Iron Arc Sever")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Iron Arc Sever did not proc in deterministic search window")
    val skillLog = Regex("Syvial sử dụng Iron Arc Sever gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertTrue(skillLog != null)
    assertFalse(result.reply.contains(Regex("Syvial sử dụng Iron Arc Sever gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(result.reply.contains("Iron Arc Sever 40%"))
    assertFalse(result.reply.contains("Iron Arc Sever Weapon DMG"))
    assertFalse(result.reply.contains("Iron Arc Sever Armor"))
  }

  @Test fun ironArcSeverCreatesNoPersistentStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..800) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Iron Arc Sever")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Iron Arc Sever proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 05 applied: Syvial Iron Arc Sever, 40% personal-turn proc, "
    "110% GodKiller weapon damage, no status, compact combat log."
)
