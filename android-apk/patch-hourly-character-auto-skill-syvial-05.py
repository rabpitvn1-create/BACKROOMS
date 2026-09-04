from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
TEST = TESTS / "SyvialTorqueSeverTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill: fifth and final dedicated Syvial attack.
# Latest main 1d3686... has dedicated attacking AUTO counts Kai=2, Syvial=4,
# Iris=1, Lucia=2. Deterministic selection uses 0x1d % 4 == 1, choosing Syvial.
# Torque Sever remains a physical GodKiller mechanical-greatsword strike and does
# not invent a supernatural payload or extra status layer.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Torque Sever", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    rivetline_index = next(
        (i for i, line in enumerate(lines) if 's("Rivetline Sever", "AUTO"' in line),
        -1,
    )
    if rivetline_index < 0:
        raise RuntimeError("Torque Sever catalog: Rivetline Sever row missing")
    lines.insert(
        rivetline_index + 1,
        '    s("Torque Sever", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Syvial", "106% sát thương vũ khí bằng GodKiller."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_test = replace_once(
    catalog_test,
    "    assertEquals(14, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "    assertEquals(15, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)\n",
    "Torque Sever Syvial catalog count regression",
)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val SYVIAL_TORQUE_SEVER_CHANCE_PERCENT = 40\n"
if "SYVIAL_TORQUE_SEVER_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Torque Sever proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun syvialTorqueSeverEligible(state: GameState): Boolean =
    activePartyCharacter(state, SYVIAL_ID) != null &&
      state.metadata["partyCombat.actorContext"]?.trim() == SYVIAL_ID

'''
if "syvialTorqueSeverEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Torque Sever eligibility helper",
    )

if "SYVIAL_TORQUE_SEVER_R01" not in combat:
    rivetline_marker = "    // SYVIAL_RIVETLINE_SEVER_R01:"
    rivetline_pos = combat.find(rivetline_marker)
    if rivetline_pos < 0:
        raise RuntimeError("Torque Sever runtime: Rivetline Sever AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", rivetline_pos)
    if death_pos < 0:
        raise RuntimeError("Torque Sever runtime: post-player-action death gate missing")
    block = '''    // SYVIAL_TORQUE_SEVER_R01: one independent 40% AUTO roll on Syvial's own valid actor turn.
    if (
      syvialTorqueSeverEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 431), 100) < SYVIAL_TORQUE_SEVER_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID),
        106,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Syvial sử dụng Torque Sever gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "SYVIAL_TORQUE_SEVER_CHANCE_PERCENT = 40",
    "syvialTorqueSeverEligible",
    "SYVIAL_TORQUE_SEVER_R01",
    "Syvial sử dụng Torque Sever gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Torque Sever runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialTorqueSeverTest {
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

  @Test fun torqueSeverIsFortyPercentFifthAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Torque Sever" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("GodKiller"))
    assertEquals(40, CombatRuntime.SYVIAL_TORQUE_SEVER_CHANCE_PERCENT)
    assertEquals(5, attackingAutos(SYVIAL_ID).size)
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun torqueSeverOnlyUsesSyvialsOwnActiveActorTurn() {
    val valid = syvialCombat()
    assertTrue(CombatRuntime.syvialTorqueSeverEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
    assertFalse(CombatRuntime.syvialTorqueSeverEligible(wrongActor))

    val missingActor = valid.copy(metadata = valid.metadata - "partyCombat.actorContext")
    assertFalse(CombatRuntime.syvialTorqueSeverEligible(missingActor))

    val inactive = syvialCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.syvialTorqueSeverEligible(inactive))
  }

  @Test fun torqueSeverUsesGodKillerArmorPathAndExactCompactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..1200) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Torque Sever")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Torque Sever did not proc in deterministic search window")
    val skillLog = Regex("Syvial sử dụng Torque Sever gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertNotNull(skillLog)
    assertFalse(result.reply.contains(Regex("Syvial sử dụng Torque Sever gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(skillLog!!.contains("40%"))
    assertFalse(skillLog.contains("Armor"))
    assertFalse(skillLog.contains("HP còn"))
  }

  @Test fun torqueSeverCreatesNoStatusAndStateRoundTrips() {
    var captured: GameState? = null
    for (counter in 0..1200) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val beforeStatuses = state.statuses
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Torque Sever")) {
        assertEquals(beforeStatuses, result.state.statuses)
        captured = result.state
        break
      }
    }
    val state = captured ?: error("Could not capture Torque Sever proc")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.statuses, roundTrip.statuses)
    assertEquals(state.metadata, roundTrip.metadata)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill applied: Syvial Torque Sever, fifth attacking AUTO, "
    "40% personal-turn proc, 106% GodKiller weapon damage, compact statusless combat log."
)
