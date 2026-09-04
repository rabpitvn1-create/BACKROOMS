from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "KaiLocklineBurstTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Hourly character AUTO skill 08.
# Deterministic-random selection from latest-main SHA 146fc3...:
# 0x14 % 4 == 0 -> Kai among Kai/Syvial/Iris/Lucia.
# Dedicated attacking AUTO counts before this patch are Kai=0, Syvial=3,
# Iris=1, Lucia=2, all below the cap of five. Legacy percentage-proc kits were
# converted to explicit AP skills by patch-ap-skill-authority-final.py and are
# therefore not counted as current attacking AUTO skills.
#
# Skill-name choice uses the next SHA byte: 0x6f % 5 == 1 -> Lockline Burst.
# Keep it inside Kai's current R10 SRU Assault Rifle MK19 role and do not revive
# legacy SRU-SG / handgun motion language.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Lockline Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    ultimate_index = next(
        (i for i, line in enumerate(lines) if 's("Guilty Crown Override",' in line),
        -1,
    )
    if ultimate_index < 0:
        raise RuntimeError("Lockline Burst catalog: Kai ultimate row missing")
    lines.insert(
        ultimate_index,
        '    s("Lockline Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Kai", "105% sát thương vũ khí bằng SRU Assault Rifle MK19."),',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
skill_context_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = "  internal const val KAI_LOCKLINE_BURST_CHANCE_PERCENT = 40\n"
if "KAI_LOCKLINE_BURST_CHANCE_PERCENT" not in combat:
    combat = replace_once(
        combat,
        skill_context_anchor,
        skill_context_anchor + constant,
        "Lockline Burst proc constant",
    )

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun kaiLocklineBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, KAI_ID) != null && partyTurnActorMatches(state, KAI_ID)

'''
if "kaiLocklineBurstEligible" not in combat:
    combat = replace_once(
        combat,
        helper_anchor,
        helper + helper_anchor,
        "Lockline Burst eligibility helper",
    )

# Layer after the latest character AUTO patch and before the authoritative
# post-player-action death gate. The proc is rolled only on Kai's own serialized
# actor turn and normal weapon damage still passes through existing Armor math.
if "KAI_LOCKLINE_BURST_R01" not in combat:
    latest_character_marker = "    // LUCIA_SIGHTLINE_BURST_R01:"
    latest_character_pos = combat.find(latest_character_marker)
    if latest_character_pos < 0:
        raise RuntimeError("Lockline Burst runtime: latest Lucia AUTO layer missing")
    death_pos = combat.find("    if (c.entityHp <= 0) {\n", latest_character_pos)
    if death_pos < 0:
        raise RuntimeError("Lockline Burst runtime: post-player-action death gate missing")
    block = '''    // KAI_LOCKLINE_BURST_R01: one independent 40% AUTO roll on Kai's own valid actor turn.
    if (
      kaiLocklineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 401), 100) < KAI_LOCKLINE_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, KAI_ID),
        105,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Kai sử dụng Lockline Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:death_pos] + block + combat[death_pos:]

for marker in (
    "KAI_LOCKLINE_BURST_CHANCE_PERCENT = 40",
    "kaiLocklineBurstEligible",
    "KAI_LOCKLINE_BURST_R01",
    "Kai sử dụng Lockline Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Lockline Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiLocklineBurstTest {
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

  @Test fun locklineBurstIsFortyPercentAttackingAutoAndAllCharactersRemainUnderCap() {
    val skill = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Lockline Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("SRU Assault Rifle MK19"))
    assertEquals(40, CombatRuntime.KAI_LOCKLINE_BURST_CHANCE_PERCENT)
    assertEquals(1, attackingAutos(KAI_ID).count { it.name == "Lockline Burst" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun locklineBurstOnlyUsesKaisOwnActiveActorTurn() {
    val valid = kaiCombat()
    assertTrue(CombatRuntime.kaiLocklineBurstEligible(valid))

    val wrongActor = valid.copy(
      metadata = valid.metadata + ("partyCombat.actorContext" to LUCIA_ID)
    )
    assertFalse(CombatRuntime.kaiLocklineBurstEligible(wrongActor))

    val inactive = kaiCombat(CharacterPresence.SEPARATED)
    assertFalse(CombatRuntime.kaiLocklineBurstEligible(inactive))
  }

  @Test fun locklineBurstUsesCompactStatuslessLogAndNormalArmorPath() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..900) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Lockline Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Lockline Burst did not proc in deterministic search window")
    val skillLog = Regex("Kai sử dụng Lockline Burst gây sát thương -\\d+ HP lên Diệp Minh").find(result.reply)?.value
    assertNotNull(skillLog)
    assertFalse(result.reply.contains(Regex("Kai sử dụng Lockline Burst gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    assertFalse(skillLog!!.contains("40%"))
    assertFalse(skillLog.contains("Armor"))
    assertFalse(skillLog.contains("HP còn"))
  }

  @Test fun locklineBurstAddsNoPersistentStatusAndStateStillRoundTrips() {
    var observed: CombatRuntime.Resolution? = null
    for (counter in 0..900) {
      var state = kaiCombat()
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")
      if (result.reply.contains("Kai sử dụng Lockline Burst")) {
        observed = result
        break
      }
    }
    val result = observed ?: error("Lockline Burst did not proc for save/load regression")
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(result.state))
    assertEquals(result.state.statuses, roundTrip.statuses)
    assertEquals(result.state.metadata, roundTrip.metadata)
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill 08 applied: Kai Lockline Burst, 40% personal-turn proc, "
    "105% SRU Assault Rifle MK19 weapon damage, compact statusless combat log."
)
