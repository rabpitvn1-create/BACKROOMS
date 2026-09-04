from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "SyvialHellscarRendTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 01.
# The final combat architecture converts the legacy percentage-proc kit into
# explicit AP skills. Hellscar Rend is intentionally a new, independent AUTO
# attack layered after that refactor instead of restoring the retired proc model.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Hellscar Rend", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    spatial_index = next(
        (i for i, line in enumerate(lines) if 's("Spatial Dominion",' in line),
        -1,
    )
    if spatial_index < 0:
        raise RuntimeError("Hellscar Rend catalog: Spatial Dominion row missing")
    lines.insert(
        spatial_index,
        '    s("Hellscar Rend", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Syvial", "100% DMG vũ khí; Bleed 2 lượt."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val SYVIAL_HELLSCAR_REND_CHANCE_PERCENT = 40\n"
if "SYVIAL_HELLSCAR_REND_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Hellscar Rend proc constant",
    )

# Insert after the final AP Counterphase block and before its authoritative
# post-player-action death gate. This keeps AUTO resolution on Syvial's own
# serialized actor turn and does not make the skill manually selectable/AP-paid.
if "SYVIAL_HELLSCAR_REND_R01" not in combat:
    counter_marker = (
        '    if (partyTurnActorMatches(resolvedState, SYVIAL_ID) &&\n'
        '        partyTurnSkillName(resolvedState) == "Counterphase" && c.entityHp > 0) {'
    )
    counter_pos = combat.find(counter_marker)
    if counter_pos < 0:
        raise RuntimeError("Hellscar Rend runtime: final Counterphase block missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", counter_pos)
    if death_pos < 0:
        raise RuntimeError("Hellscar Rend runtime: post-player-action death gate missing")
    block = '''    // SYVIAL_HELLSCAR_REND_R01: one independent 40% AUTO roll on Syvial's own valid actor turn.
    // Reuse the existing bounded Syvial Bleed counter; max/refresh semantics remain capped at 3.
    val hellscarSyvial = activePartyCharacter(resolvedState, SYVIAL_ID)
    if (
      hellscarSyvial != null && partyTurnActorMatches(resolvedState, SYVIAL_ID) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 307), 100) < SYVIAL_HELLSCAR_REND_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID),
        100,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      syvialBleedTurns = max(syvialBleedTurns, 2)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      log += "Syvial sử dụng Hellscar Rend gây sát thương -$damage HP lên ${c.entityName} và gây Bleed 2 lượt."
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "SYVIAL_HELLSCAR_REND_CHANCE_PERCENT = 40",
    "SYVIAL_HELLSCAR_REND_R01",
    "partyTurnActorMatches(resolvedState, SYVIAL_ID)",
    "Syvial sử dụng Hellscar Rend gây sát thương -$damage HP lên ${c.entityName} và gây Bleed 2 lượt.",
):
    if marker not in combat:
        raise RuntimeError("Hellscar Rend runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialHellscarRendTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" &&
        (skill.effect.contains("DMG") || skill.effect.contains("sát thương", ignoreCase = true))
    }

  private fun syvialCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    val syvial = ensured.characters.getValue(SYVIAL_ID)
    var state = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)),
      characters = ensured.characters + (SYVIAL_ID to syvial.copy(presence = presence))
    )
    state = CombatRuntime.start(state, "diep_minh")
    return state.copy(metadata = state.metadata + ("partyCombat.actorContext" to SYVIAL_ID))
  }

  @Test fun hellscarRendIsAFortyPercentAttackingAutoWithoutRevivingLegacyAutos() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Hellscar Rend" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertEquals(40, CombatRuntime.SYVIAL_HELLSCAR_REND_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(SYVIAL_ID).count { it.name == "Hellscar Rend" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun hellscarRendOnlyUsesSyvialsOwnActiveActorTurnAndCompactLog() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..600) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Hellscar Rend")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Hellscar Rend did not proc in deterministic search window")
    assertTrue(
      result.reply.contains(Regex("Syvial sử dụng Hellscar Rend gây sát thương -\\d+ HP lên Diệp Minh và gây Bleed 2 lượt\\."))
    )
    assertFalse(result.reply.contains("40%"))
    assertFalse(result.reply.contains("Weapon DMG"))
    assertFalse(result.reply.contains("Armor"))
    assertTrue((result.state.metadata["combat.syvialBleedTurns"]?.toIntOrNull() ?: 0) in 2..3)
  }

  @Test fun hellscarRendDoesNotProcForAnotherActorOrInactiveSyvial() {
    for (counter in 0..160) {
      var wrongActor = syvialCombat().copy(
        metadata = syvialCombat().metadata + mapOf(
          "partyCombat.actorContext" to KAI_ID,
          "combat.eventCounter" to counter.toString()
        )
      )
      val wrongActorResult = CombatRuntime.resolve(wrongActor, "SEARCH", "giữ đội hình")
      assertFalse(wrongActorResult.reply.contains("Hellscar Rend"))

      var inactive = syvialCombat(CharacterPresence.SEPARATED)
      inactive = inactive.copy(metadata = inactive.metadata + ("combat.eventCounter" to counter.toString()))
      val inactiveResult = CombatRuntime.resolve(inactive, "SEARCH", "giữ đội hình")
      assertFalse(inactiveResult.reply.contains("Hellscar Rend"))
    }
  }

  @Test fun hellscarBleedPersistsAcrossSaveLoadAndExpiresWithoutInfiniteStacking() {
    var procState: GameState? = null
    for (counter in 0..600) {
      var state = syvialCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Hellscar Rend") && CombatRuntime.active(result.state) != null) {
        procState = result.state
        break
      }
    }
    var state = procState ?: error("Could not capture live combat after Hellscar Rend")
    val beforeSave = state.metadata["combat.syvialBleedTurns"]?.toIntOrNull() ?: 0
    assertTrue(beforeSave in 2..3)
    state = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(beforeSave, state.metadata["combat.syvialBleedTurns"]?.toIntOrNull())

    val syvial = state.characters.getValue(SYVIAL_ID)
    state = state.copy(
      characters = state.characters + (SYVIAL_ID to syvial.copy(presence = CharacterPresence.SEPARATED))
    )
    repeat(4) {
      if (CombatRuntime.active(state) != null) {
        state = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình").state
      }
    }
    assertEquals(0, state.metadata["combat.syvialBleedTurns"]?.toIntOrNull() ?: 0)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 01 applied: Syvial Hellscar Rend, 40% personal-turn proc, "
    "100% weapon damage, bounded Bleed 2, compact combat log."
)
