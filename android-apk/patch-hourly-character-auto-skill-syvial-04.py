from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
TEST = TESTS / "SyvialRivetlineSeverTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 08.
# Latest-main inspection finds three dedicated Syvial attacking AUTO skills:
# Hellscar Rend, Blackline Cleave, and Iron Arc Sever. This patch adds exactly
# one more, keeping Syvial below the five-skill automation cap.
# Rivetline Sever stays inside GodKiller's established mechanical greatsword role.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Rivetline Sever", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    iron_arc_index = next(
        (i for i, line in enumerate(lines) if 's("Iron Arc Sever", "AUTO"' in line),
        -1,
    )
    if iron_arc_index < 0:
        raise RuntimeError("Rivetline Sever catalog: Iron Arc Sever row missing")
    lines.insert(
        iron_arc_index + 1,
        '    s("Rivetline Sever", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Syvial", "108% sát thương vũ khí bằng GodKiller."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_test = replace_once(
    catalog_test,
    "    assertEquals(13, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "    assertEquals(14, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "Rivetline Sever Syvial catalog count regression",
)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val SYVIAL_RIVETLINE_SEVER_CHANCE_PERCENT = 40\n"
if "SYVIAL_RIVETLINE_SEVER_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Rivetline Sever proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun syvialRivetlineSeverEligible(state: GameState): Boolean =
    activePartyCharacter(state, SYVIAL_ID) != null && partyTurnActorMatches(state, SYVIAL_ID)

'''
if "syvialRivetlineSeverEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Rivetline Sever eligibility helper",
    )

if "SYVIAL_RIVETLINE_SEVER_R01" not in combat:
    iron_arc_marker = "    // SYVIAL_IRON_ARC_SEVER_R01:"
    iron_arc_pos = combat.find(iron_arc_marker)
    if iron_arc_pos < 0:
        raise RuntimeError("Rivetline Sever runtime: Iron Arc Sever AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", iron_arc_pos)
    if death_pos < 0:
        raise RuntimeError("Rivetline Sever runtime: post-player-action death gate missing")
    block = '''    // SYVIAL_RIVETLINE_SEVER_R01: one independent 40% AUTO roll on Syvial's own valid actor turn.
    if (
      syvialRivetlineSeverEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 389), 100) < SYVIAL_RIVETLINE_SEVER_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID),
        108,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Syvial sử dụng Rivetline Sever gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "SYVIAL_RIVETLINE_SEVER_CHANCE_PERCENT = 40",
    "syvialRivetlineSeverEligible",
    "SYVIAL_RIVETLINE_SEVER_R01",
    "Syvial sử dụng Rivetline Sever gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Rivetline Sever runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialRivetlineSeverTest {
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

  @Test fun rivetlineSeverIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Rivetline Sever" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("GodKiller"))
    assertEquals(40, CombatRuntime.SYVIAL_RIVETLINE_SEVER_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(SYVIAL_ID).count { it.name == "Rivetline Sever" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun rivetlineSeverOnlyUsesSyvialsOwnActiveActorTurn() {
    val valid = syvialCombat()
    assertTrue(CombatRuntime.syvialRivetlineSeverEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.syvialRivetlineSeverEligible(wrongActor))

    val inactive = syvialCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.syvialRivetlineSeverEligible(inactive))
  }

  @Test fun rivetlineSeverDealsArmoredGodKillerDamageAndUsesCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..800) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Rivetline Sever")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Rivetline Sever did not proc in deterministic search window")
    val skillLog = Regex("Syvial sử dụng Rivetline Sever gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertTrue(skillLog != null)
    assertFalse(result.reply.contains(Regex("Syvial sử dụng Rivetline Sever gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(result.reply.contains("Rivetline Sever 40%"))
    assertFalse(result.reply.contains("Rivetline Sever Weapon DMG"))
    assertFalse(result.reply.contains("Rivetline Sever Armor"))
  }

  @Test fun rivetlineSeverCreatesNoPersistentStatusAndSaveLoadRemainsStable() {
    var captured: GameState? = null
    for (counter in 0..800) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Rivetline Sever")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Rivetline Sever proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 08 applied: Syvial Rivetline Sever, 40% personal-turn proc, "
    "108% GodKiller weapon damage, no status, compact combat log."
)
