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
# Syvial was selected from characters still below five attacking AUTO skills.
# The new attack stays deliberately modest because it has a high 40% proc rate
# and reuses the existing bounded Syvial Bleed lifecycle rather than creating a
# parallel status implementation.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Hellscar Rend", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    spatial_index = next(
        (i for i, line in enumerate(lines) if 's("Spatial Dominion", "AUTO"' in line),
        -1,
    )
    if spatial_index < 0:
        raise RuntimeError("Hellscar Rend catalog: Spatial Dominion anchor missing")
    lines.insert(
        spatial_index,
        '    s("Hellscar Rend", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ", "100% DMG vũ khí; Bleed 2 lượt."),',
    )
    catalog = "\n".join(lines) + "\n"
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
constant_anchor = "  private const val SYVIAL_ULTIMATE_INTERVAL_TURNS = 3\n"
constant = "  internal const val SYVIAL_HELLSCAR_REND_CHANCE_PERCENT = 40\n"
if "SYVIAL_HELLSCAR_REND_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant,
        "Hellscar Rend proc constant",
    )

response_anchor = "    if (anNhienActive && c.entityHp > 0) {\n"
block = '''    // SYVIAL_HELLSCAR_REND_R01: independent 40% attacking AUTO check.
    // It shares the existing Syvial Bleed counter, so refresh is bounded by the
    // current three-turn Crimson Guillotine cap and never stacks a second DoT.
    if (
      syvialActive && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 307), 100) < SYVIAL_HELLSCAR_REND_CHANCE_PERCENT
    ) {
      val syvialWeapon = CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID)
      val syvialDamagePercent = if (syvialDevilTrigger) 125 else 100
      val damage = companionSkillDamage(
        syvialWeapon,
        (100 * syvialDamagePercent + 99) / 100,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      syvialBleedTurns = max(syvialBleedTurns, 2)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      log += "Syvial sử dụng Hellscar Rend gây sát thương -$damage HP lên ${c.entityName} và gây Bleed 2 lượt."
    }

'''
if "SYVIAL_HELLSCAR_REND_R01" not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        block + response_anchor,
        "Hellscar Rend runtime insertion",
    )

for marker in (
    "SYVIAL_HELLSCAR_REND_CHANCE_PERCENT = 40",
    "SYVIAL_HELLSCAR_REND_R01",
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
      skill.kind == "AUTO" && (skill.effect.contains("DMG") || skill.effect.contains("sát thương", ignoreCase = true))
    }

  private fun syvialState(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    val syvial = ensured.characters.getValue(SYVIAL_ID)
    return ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)),
      characters = ensured.characters + (SYVIAL_ID to syvial.copy(presence = presence))
    )
  }

  @Test fun hellscarRendIsTheFifthSyvialAttackingAutoAtExactlyFortyPercent() {
    val skill = CompanionSkillCatalog.forCharacter(SYVIAL_ID).single { it.name == "Hellscar Rend" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertEquals(40, CombatRuntime.SYVIAL_HELLSCAR_REND_CHANCE_PERCENT)
    assertEquals(5, attackingAutos(SYVIAL_ID).size)
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun hellscarRendUsesCompactCombatLogAndPersistsBoundedBleed() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..600) {
      var state = syvialState()
      state = CombatRuntime.start(state, "slenderman")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Hellscar Rend")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Hellscar Rend did not proc in deterministic search window")
    assertTrue(
      result.reply.contains(Regex("Syvial sử dụng Hellscar Rend gây sát thương -\\d+ HP lên Slenderman và gây Bleed 2 lượt\\."))
    )
    assertFalse(result.reply.contains("40%"))
    assertFalse(result.reply.contains("Weapon DMG"))
    assertFalse(result.reply.contains("Armor"))
    val bleedTurns = result.state.metadata["combat.syvialBleedTurns"]?.toIntOrNull() ?: 0
    assertTrue(bleedTurns in 2..3)
  }

  @Test fun hellscarRendDoesNotProcWhenSyvialIsNotActive() {
    for (counter in 0..160) {
      var state = syvialState(CharacterPresence.SEPARATED)
      state = CombatRuntime.start(state, "slenderman")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      assertFalse(result.reply.contains("Hellscar Rend"))
    }
  }

  @Test fun sharedBleedExpiresWithoutInfiniteRefreshWhenSyvialLeavesCombat() {
    var procState: GameState? = null
    for (counter in 0..600) {
      var state = syvialState()
      state = CombatRuntime.start(state, "slenderman")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Syvial sử dụng Hellscar Rend") && CombatRuntime.active(result.state) != null) {
        procState = result.state
        break
      }
    }
    var state = procState ?: error("Could not capture live combat after Hellscar Rend")
    val syvial = state.characters.getValue(SYVIAL_ID)
    state = state.copy(characters = state.characters + (SYVIAL_ID to syvial.copy(presence = CharacterPresence.SEPARATED)))
    repeat(3) {
      if (CombatRuntime.active(state) != null) {
        state = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình").state
      }
    }
    val remaining = state.metadata["combat.syvialBleedTurns"]?.toIntOrNull() ?: 0
    assertEquals(0, remaining)
  }
}
''', encoding="utf-8")
