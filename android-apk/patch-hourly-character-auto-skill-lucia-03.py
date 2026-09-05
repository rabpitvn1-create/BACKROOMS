from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "LuciaCrosslineBurstTest.kt"


def once(src: str, old: str, new: str, label: str) -> str:
    if new in src:
        return src
    count = src.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return src.replace(old, new, 1)


# Add exactly one new attacking AUTO skill for Lucia. Keep her grounded in the
# current human-combatant/M4A1 canon: independent 40% own-turn proc, normal
# weapon/armor path, no invented special ammunition, and no persistent status.
catalog = CATALOG.read_text(encoding="utf-8")
if 's("Crossline Burst", "AUTO"' not in catalog:
    rows = catalog.splitlines()
    i = next((n for n, row in enumerate(rows) if 's("Anchorline Burst", "AUTO"' in row), -1)
    if i < 0:
        raise RuntimeError("Crossline Burst catalog: Anchorline Burst row missing")
    rows.insert(
        i + 1,
        '    s("Crossline Burst", "AUTO", "40% ở mỗi lượt chiến đấu hợp lệ của Lucia", "104% sát thương vũ khí bằng M4A1."),',
    )
    catalog = "\n".join(rows) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constants = (
    '  internal const val LUCIA_CROSSLINE_BURST_CHANCE_PERCENT = 40\n'
    '  internal const val LUCIA_CROSSLINE_BURST_DAMAGE_PERCENT = 104\n'
)
if "LUCIA_CROSSLINE_BURST_CHANCE_PERCENT" not in combat:
    combat = once(combat, constant_anchor, constant_anchor + constants, "Crossline Burst constants")

helper_anchor = "  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {\n"
helper = '''  internal fun luciaCrosslineBurstEligible(state: GameState): Boolean =
    activePartyCharacter(state, LUCIA_ID) != null &&
      state.metadata["partyCombat.actorContext"]?.trim() == LUCIA_ID

'''
if "luciaCrosslineBurstEligible" not in combat:
    combat = once(combat, helper_anchor, helper + helper_anchor, "Crossline Burst eligibility")

if "LUCIA_CROSSLINE_BURST_R01" not in combat:
    p = combat.find("    // LUCIA_ANCHORLINE_BURST_R01:")
    if p < 0:
        raise RuntimeError("Crossline Burst runtime: Anchorline Burst layer missing")
    d = combat.find("    if (c.entityHp <= 0) {\n", p)
    if d < 0:
        raise RuntimeError("Crossline Burst runtime: death gate missing")
    block = '''    // LUCIA_CROSSLINE_BURST_R01: independent 40% AUTO roll on Lucia's own ACTIVE/alive turn.
    if (
      luciaCrosslineBurstEligible(resolvedState) && c.entityHp > 0 &&
      roll(c.copy(eventCounter = c.eventCounter + 467), 100) < LUCIA_CROSSLINE_BURST_CHANCE_PERCENT
    ) {
      val damage = companionSkillDamage(
        CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID),
        LUCIA_CROSSLINE_BURST_DAMAGE_PERCENT,
        profile.armor
      )
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Lucia sử dụng Crossline Burst gây sát thương -$damage HP lên ${c.entityName}"
    }

'''
    combat = combat[:d] + block + combat[d:]

for marker in (
    "LUCIA_CROSSLINE_BURST_CHANCE_PERCENT = 40",
    "LUCIA_CROSSLINE_BURST_DAMAGE_PERCENT = 104",
    "luciaCrosslineBurstEligible",
    "LUCIA_CROSSLINE_BURST_R01",
    "Lucia sử dụng Crossline Burst gây sát thương -$damage HP lên ${c.entityName}",
):
    if marker not in combat:
        raise RuntimeError("Crossline Burst runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaCrosslineBurstTest {
  private fun attackingAutos(id: String) = CompanionSkillCatalog.forCharacter(id).filter {
    it.kind == "AUTO" && it.effect.contains("sát thương", ignoreCase = true)
  }

  private fun state(
    presence: CharacterPresence = CharacterPresence.ACTIVE,
    hp: Int? = null,
    actor: String? = LUCIA_ID
  ): GameState {
    var s = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID))
    )
    s = CombatRuntime.start(s, "diep_minh")
    val lucia = s.characters.getValue(LUCIA_ID)
    val updated = lucia.copy(
      presence = presence,
      vitalState = if (hp == null) lucia.vitalState else lucia.vitalState.copy(currentHp = hp)
    )
    val metadata = if (actor == null) s.metadata - "partyCombat.actorContext"
      else s.metadata + ("partyCombat.actorContext" to actor)
    return s.copy(characters = s.characters + (LUCIA_ID to updated), metadata = metadata)
  }

  @Test fun contractProcDamageWeaponAndCap() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).single { it.name == "Crossline Burst" }
    assertEquals("AUTO", skill.kind)
    assertTrue(skill.trigger.contains("40%"))
    assertTrue(skill.effect.contains("104%"))
    assertTrue(skill.effect.contains("M4A1"))
    assertEquals(40, CombatRuntime.LUCIA_CROSSLINE_BURST_CHANCE_PERCENT)
    assertEquals(104, CombatRuntime.LUCIA_CROSSLINE_BURST_DAMAGE_PERCENT)
    assertEquals(1, attackingAutos(LUCIA_ID).count { it.name == "Crossline Burst" })
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID).forEach { id ->
      assertTrue("$id exceeds five attacking AUTO skills", attackingAutos(id).size <= 5)
    }
  }

  @Test fun onlyLuciasOwnActiveAliveTurnIsEligible() {
    assertTrue(CombatRuntime.luciaCrosslineBurstEligible(state()))
    assertFalse(CombatRuntime.luciaCrosslineBurstEligible(state(actor = KAI_ID)))
    assertFalse(CombatRuntime.luciaCrosslineBurstEligible(state(actor = null)))
    assertFalse(CombatRuntime.luciaCrosslineBurstEligible(state(presence = CharacterPresence.SEPARATED)))
    assertFalse(CombatRuntime.luciaCrosslineBurstEligible(state(hp = 0)))
  }

  @Test fun procUsesCompactExactStatuslessLog() {
    var observed: CombatRuntime.Resolution? = null
    var statusesBefore: List<StatusEffect> = emptyList()
    for (counter in 0..1400) {
      var s = state()
      s = s.copy(metadata = s.metadata + ("combat.eventCounter" to counter.toString()))
      val r = CombatRuntime.resolve(s, "SEARCH", "giữ đội hình")
      if (r.reply.contains("Lucia sử dụng Crossline Burst")) {
        observed = r
        statusesBefore = s.statuses
        break
      }
    }
    val r = observed ?: error("Crossline Burst did not proc in deterministic search window")
    val line = r.reply.lines().single { it.contains("Lucia sử dụng Crossline Burst") }
    assertTrue(Regex("^Lucia sử dụng Crossline Burst gây sát thương -\\d+ HP lên Diệp Minh$").matches(line))
    assertFalse(line.contains("40%"))
    assertFalse(line.contains("Armor", ignoreCase = true))
    assertFalse(line.contains("HP còn"))
    assertFalse(line.contains("và gây"))
    assertEquals(statusesBefore, r.state.statuses)
  }

  @Test fun statuslessSkillNeedsNoExpiryAndRoundTripsWithoutSkillStatusMetadata() {
    val s = state()
    val roundTrip = GameStateCodec.decode(GameStateCodec.encode(s))
    assertEquals(s.statuses, roundTrip.statuses)
    assertEquals(s.metadata, roundTrip.metadata)
    assertFalse(roundTrip.metadata.keys.any { it.contains("crossline", ignoreCase = true) })
  }
}
''', encoding="utf-8")

print(
    "Hourly character AUTO skill applied: Lucia Crossline Burst, 40% explicit own-turn proc, "
    "104% M4A1 weapon damage through normal armor, compact statusless log."
)
