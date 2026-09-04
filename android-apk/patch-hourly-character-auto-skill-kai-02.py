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


# Hourly character AUTO skill: second dedicated Kai attack on current main.
# Keep the skill on the same canonical SRU Assault Rifle MK19 damage path as
# Lockline Burst, but give it an independent 40% roll and no persistent status.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Zero-Line Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    lockline_index = next(
        (i for i, line in enumerate(lines) if 's("Lockline Burst", "AUTO"' in line),
        -1,
    )
    if lockline_index < 0:
        raise RuntimeError("Zero-Line Burst catalog: Lockline Burst row missing")
    lines.insert(
        lockline_index + 1,
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
    activePartyCharacter(state, KAI_ID) != null &&
      state.metadata["partyCombat.actorContext"]?.trim() == KAI_ID

'''
if "kaiZeroLineBurstEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Zero-Line Burst eligibility helper",
    )

if "KAI_ZERO_LINE_BURST_R02" not in combat:
    lockline_marker = "    // KAI_LOCKLINE_BURST_R01:"
    lockline_pos = combat.find(lockline_marker)
    if lockline_pos < 0:
        raise RuntimeError("Zero-Line Burst runtime: Lockline Burst AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", lockline_pos)
    if death_pos < 0:
        raise RuntimeError("Zero-Line Burst runtime: post-player-action death gate missing")
    block = '''    // KAI_ZERO_LINE_BURST_R02: one independent 40% AUTO roll on Kai's own explicit actor turn.
    if (
      kaiZeroLineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 419), 100) < KAI_ZERO_LINE_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, KAI_ID),
        105,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "KAI_ZERO_LINE_BURST_CHANCE_PERCENT = 40",
    "kaiZeroLineBurstEligible",
    "KAI_ZERO_LINE_BURST_R02",
    "Kai sử dụng Zero-Line Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Zero-Line Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiZeroLineBurstTest {
  private fun attackingAutos(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter { skill ->
      skill.kind == "AUTO" && skill.effect.contains("sát thương", ignoreCase = true)
    }

  private fun kaiCombat(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    var state = GameState.initial()
    state = CombatRuntime.start(state, "diep_minh")
    val kai = state.characters.getValue(KAI_ID)
    return state.copy(
      characters = state.characters + (KAI_ID to kai.copy(presence = presence)),
      metadata = state.metadata + ("partyCombat.actorContext" to KAI_ID)
    )
  }

  @Test fun zeroLineBurstIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Zero-Line Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("SRU Assault Rifle MK19"))
    assertEquals(40, CombatRuntime.KAI_ZERO_LINE_BURST_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(KAI_ID).count { it.name == "Zero-Line Burst" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun zeroLineBurstOnlyUsesKaisOwnExplicitActiveActorTurn() {
    val valid = kaiCombat()
    assertTrue(CombatRuntime.kaiZeroLineBurstEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to LUCIA_ID)
    )
    assertFalse(CombatRuntime.kaiZeroLineBurstEligible(wrongActor))

    val missingActor = valid.copy(
      metadata = valid.metadata - "partyCombat.actorContext"
    )
    assertFalse(CombatRuntime.kaiZeroLineBurstEligible(missingActor))

    val inactive = kaiCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.kaiZeroLineBurstEligible(inactive))
  }

  @Test fun zeroLineBurstUsesCompactStatuslessLogAndCurrentArmorPath() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..1200) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Zero-Line Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Zero-Line Burst did not proc in deterministic search window")
    val skillLog = Regex("Kai sử dụng Zero-Line Burst gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertNotNull(skillLog)
    assertFalse(result.reply.contains(Regex("Kai sử dụng Zero-Line Burst gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(skillLog!!.contains("40%"))
    assertFalse(skillLog.contains("Armor"))
    assertFalse(skillLog.contains("HP còn"))
  }

  @Test fun zeroLineBurstAddsNoPersistentStatusAndStateStillRoundTrips() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..1200) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Zero-Line Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Zero-Line Burst did not proc for save/load regression")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(result.state))
    assertEquals(result.state.statuses, roundTrip.statuses)
    assertEquals(result.state.metadata, roundTrip.metadata)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill applied: Kai Zero-Line Burst, 40% explicit personal-turn proc, "
    "105% SRU Assault Rifle MK19 weapon damage, compact statusless combat log."
)
