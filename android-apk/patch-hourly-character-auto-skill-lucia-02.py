from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "LuciaSightlineBurstTest.kt"


def once(src, old, new, label):
    if new in src:
        return src
    if src.count(old) != 1:
        raise RuntimeError(f"{label}: anchor count={src.count(old)}")
    return src.replace(old, new, 1)


# latest main 87641e... => 0x87 % 4 == 3 => Lucia.
# Dedicated hourly attacking AUTO counts before this patch:
# Kai=0, Syvial=3, Iris=1, Lucia=1. Lucia remains below the cap of five.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Sightline Burst", "AUTO"' not in catalog:
    rows = catalog.splitlines()
    i = next((n for n, row in enumerate(rows) if 's("Centerline Burst", "AUTO"' in row), -1)
    if i < 0:
        raise RuntimeError("Sightline Burst: Centerline Burst catalog anchor missing")
    rows.insert(i + 1, '    s("Sightline Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Lucia", "105% sát thương vũ khí bằng M4A1."),')
    catalog = "\n".join(rows) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant = '  internal const val LUCIA_SIGHTLINE_BURST_CHANCE_PERCENT = 40\n'
if "LUCIA_SIGHTLINE_BURST_CHANCE_PERCENT" not in combat:
    combat = once(combat, anchor, anchor + constant, "Sightline constant")

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun luciaSightlineBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, LUCIA_ID) != null && partyTurnActorMatches(state, LUCIA_ID)

'''
if "luciaSightlineBurstEligible" not in combat:
    combat = once(combat, helper_anchor, helper + helper_anchor, "Sightline eligibility")

if "LUCIA_SIGHTLINE_BURST_R01" not in combat:
    p = combat.find("    // SYVIAL_IRON_ARC_SEVER_R01:")
    if p < 0:
        raise RuntimeError("Sightline runtime: Iron Arc Sever layer missing")
    d = combat.find("    if (c.entityHp <= 0) {\n", p)
    if d < 0:
        raise RuntimeError("Sightline runtime: death gate missing")
    block = '''    // LUCIA_SIGHTLINE_BURST_R01: independent 40% AUTO roll on Lucia's own active actor turn.
    if (
      luciaSightlineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 389), 100) < LUCIA_SIGHTLINE_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID), 105, profile.armor)
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Lucia sử dụng Sightline Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:d] + block + combat[d:]

for marker in (
    "LUCIA_SIGHTLINE_BURST_CHANCE_PERCENT = 40",
    "luciaSightlineBurstEligible",
    "LUCIA_SIGHTLINE_BURST_R01",
    "Lucia sử dụng Sightline Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Sightline runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaSightlineBurstTest {
  private fun autos(id: String) = CompanionSkillCatalog.forCharacter(id).filter {
    it.kind == "AUTO" && it.effect.contains("sát thương", ignoreCase = true)
  }

  private fun state(presence: CharacterPresence = CharacterPresence.ACTIVE): GameState {
    var s = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID))
    )
    s = CombatRuntime.start(s, "diep_minh")
    val lucia = s.characters.getValue(LUCIA_ID)
    return s.copy(
      characters = s.characters + (LUCIA_ID to lucia.copy(presence = presence)),
      metadata = s.metadata + ("partyCombat.actorContext" to LUCIA_ID)
    )
  }

  @Test fun contractAndCap() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).single { it.name == "Sightline Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("M4A1"))
    assertEquals(40, CombatRuntime.LUCIA_SIGHTLINE_BURST_CHANCE_PERCENT)
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { assertTrue(autos(it).size <= 5) }
  }

  @Test fun onlyLuciasActiveTurnIsEligible() {
    val valid = state()
    assertTrue(CombatRuntime.luciaSightlineBurstEligible(valid))
    assertFalse(CombatRuntime.luciaSightlineBurstEligible(valid.copy(metadata = valid.metadata + ("partyCombat.actorContext" to KAI_ID))))
    assertFalse(CombatRuntime.luciaSightlineBurstEligible(state(CharacterPresence.SEPARATED)))
  }

  @Test fun compactLogAndNoStatusPersist() {
    var hit: CombatRuntime.Resolution? = null
    for (counter in 0..900) {
      val s = state().copy(metadata = state().metadata + ("combat.eventCounter" to counter.toString()))
      val r = CombatRuntime.resolve(s, "SEARCH", "giữ đội hình")
      if (r.reply.contains("Lucia sử dụng Sightline Burst")) { hit = r; break }
    }
    val r = hit ?: error("Sightline Burst did not proc")
    assertNotNull(Regex("Lucia sử dụng Sightline Burst gây sát thương -\\d+ HP lên Diệp Minh").find(r.reply))
    assertFalse(r.reply.contains(Regex("Sightline Burst gây sát thương -\\d+ HP lên Diệp Minh và gây")))
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(r.state))
    assertEquals(r.state.statuses, roundTrip.statuses)
  }
}
''', encoding="utf-8")

print("Applied Lucia Sightline Burst: 40% personal-turn AUTO, 105% M4A1 damage, compact log.")
