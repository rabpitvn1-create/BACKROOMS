from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
STATS = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterEquipmentSystem.kt"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
INTEGRATION_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/DevilTriggerCombatIntegrationTest.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiDevilBlessingTest.kt"


def once(text: str, old: str, new: str, label: str) -> str:
    if new and new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


stats = STATS.read_text(encoding="utf-8")
hp_old = '''  fun devilBlessingHpBonus(state: GameState, characterId: String, unblessedMaxHp: Int? = null): Int {
    if (characterId == KAI_ID || state.metadata["combat.entityKey"].isNullOrBlank() || characterId !in state.party.memberIds) return 0
    val kai = state.characters[KAI_ID] ?: return 0
    val companion = state.characters[characterId] ?: return 0
    if (kai.presence != CharacterPresence.ACTIVE || kai.vitalState.currentHp <= 0 || companion.presence != CharacterPresence.ACTIVE || companion.vitalState.currentHp <= 0) return 0
    val base = unblessedMaxHp ?: run {
      val equipmentHp = state.equipment[companion.equipmentId]?.slots.orEmpty().values
        .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }.sumOf { it.bonuses.hp }
      (companion.statProfile.baseMaxHp + equipmentHp).coerceAtLeast(1)
    }
    return maxOf(1, (base * 10 + 99) / 100)
  }
'''
hp_new = '''  fun devilBlessingActive(state: GameState, characterId: String): Boolean {
    if (characterId == KAI_ID || state.metadata["combat.entityKey"].isNullOrBlank() || characterId !in state.party.memberIds) return false
    val kai = state.characters[KAI_ID] ?: return false
    val companion = state.characters[characterId] ?: return false
    return kai.presence == CharacterPresence.ACTIVE && kai.vitalState.currentHp > 0 &&
      companion.presence == CharacterPresence.ACTIVE && companion.vitalState.currentHp > 0
  }

  fun devilBlessingHpBonus(state: GameState, characterId: String, unblessedMaxHp: Int? = null): Int {
    if (!devilBlessingActive(state, characterId)) return 0
    val companion = state.characters.getValue(characterId)
    val base = unblessedMaxHp ?: run {
      val equipmentHp = state.equipment[companion.equipmentId]?.slots.orEmpty().values
        .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }.sumOf { it.bonuses.hp }
      (companion.statProfile.baseMaxHp + equipmentHp).coerceAtLeast(1)
    }
    return maxOf(1, (base * 5 + 99) / 100)
  }

  fun devilBlessingEvasionBonus(state: GameState, characterId: String): Int =
    if (devilBlessingActive(state, characterId)) 5 else 0
'''
stats = once(stats, hp_old, hp_new, "Party-only Devil Blessing helper")

effective_old = '''    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    return EffectiveCharacterStats(
      maxHp = unblessedMaxHp + devilBlessingHp,
      equipmentHp = hp,
      str = character.statProfile.str + str,
      df = character.statProfile.df + df,
      agi = character.statProfile.agi + agi,
'''
effective_new = '''    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    val partyBlessed = devilBlessingActive(state, characterId)
    fun partyBlessing(value: Int): Int = if (partyBlessed) maxOf(1, (value * 105 + 99) / 100) else value
    return EffectiveCharacterStats(
      maxHp = unblessedMaxHp + devilBlessingHp,
      equipmentHp = hp,
      str = partyBlessing(character.statProfile.str + str),
      df = partyBlessing(character.statProfile.df + df),
      agi = partyBlessing(character.statProfile.agi + agi),
'''
stats = once(stats, effective_old, effective_new, "Party-only Devil Blessing stats")
for marker in ("return maxOf(1, (base * 5 + 99) / 100)", "fun devilBlessingEvasionBonus", "fun partyBlessing(value: Int)"):
    if marker not in stats:
        raise RuntimeError("Party-only Devil Blessing stat contract missing: " + marker)
STATS.write_text(stats, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
combat = once(combat, '    return max(1, (resolved * 110 + 99) / 100)\n', '    return max(1, (resolved * 105 + 99) / 100)\n', "companion attack +5%")
combat = once(combat, '          val luciaRawBurstDamage = (LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor * 110 + 99) / 100\n', '          val luciaRawBurstDamage = (LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor * 105 + 99) / 100\n', "Lucia attack +5%")
combat = once(combat, '        val blessedDamage = min(c.entityHp, (24 * damagePerHit * 110 + 99) / 100)\n', '        val blessedDamage = min(c.entityHp, (24 * damagePerHit * 105 + 99) / 100\n', "Syvial attack +5%")

target_old = 'quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)'
target_new = 'quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive) + CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, targetId)'
if combat.count(target_old) != 2:
    raise RuntimeError(f"Party blessing targeted evasion: expected two anchors, found {combat.count(target_old)}")
combat = combat.replace(target_old, target_new)
fallback_old = '      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)\n'
fallback_new = '      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive) + CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, KAI_ID)\n'
combat = once(combat, fallback_old, fallback_new, "Kai exclusion in fallback evasion")
COMBAT.write_text(combat, encoding="utf-8")

# The passive remains runtime-authoritative but has no SkillCatalog row and therefore no UI entry.
catalog = CATALOG.read_text(encoding="utf-8")
skill_pattern = re.compile(
    r'^[ \t]+s\("(?:Devil Blessing|DEVIL BLESSING)",[^\n]*\),\n',
    re.MULTILINE,
)
catalog, hidden_count = skill_pattern.subn("", catalog)
# Zero matches is already the desired hidden state. More than one means the catalog is malformed.
if hidden_count > 1:
    raise RuntimeError(f"hide Devil Blessing: expected at most one skill row, found {hidden_count}")
if skill_pattern.search(catalog):
    raise RuntimeError("DEVIL BLESSING must stay hidden from the skill table")
CATALOG.write_text(catalog, encoding="utf-8")

integration = INTEGRATION_TEST.read_text(encoding="utf-8")
integration = once(integration, '  @Test fun devilBlessingAddsTenPercentCompanionMaxHpButNotKaiHp() {', '  @Test fun devilBlessingAddsFivePercentToCompanionButNeverKai() {', "integration test name")
integration = once(integration, 'assertEquals(irisBefore + (irisBefore * 10 + 99) / 100, CharacterStatEngine.effective(state, IRIS_ID).maxHp)', 'assertEquals(irisBefore + (irisBefore * 5 + 99) / 100, CharacterStatEngine.effective(state, IRIS_ID).maxHp)', "companion HP regression")
INTEGRATION_TEST.write_text(integration, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class KaiDevilBlessingTest {
  @Test fun blessingTargetsActiveCompanionsAndExcludesKai() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID))
    )
    val kaiBefore = CharacterStatEngine.effective(state, KAI_ID)
    val irisBefore = CharacterStatEngine.effective(state, IRIS_ID)
    state = CombatRuntime.start(state, "hound")
    val kaiAfter = CharacterStatEngine.effective(state, KAI_ID)
    val irisAfter = CharacterStatEngine.effective(state, IRIS_ID)
    assertEquals(kaiBefore, kaiAfter)
    assertEquals((irisBefore.maxHp * 105 + 99) / 100, irisAfter.maxHp)
    assertEquals((irisBefore.str * 105 + 99) / 100, irisAfter.str)
    assertEquals((irisBefore.df * 105 + 99) / 100, irisAfter.df)
    assertEquals((irisBefore.agi * 105 + 99) / 100, irisAfter.agi)
    assertEquals(5, CharacterStatEngine.devilBlessingEvasionBonus(state, IRIS_ID))
    assertEquals(0, CharacterStatEngine.devilBlessingEvasionBonus(state, KAI_ID))
  }

  @Test fun blessingIsHiddenFromSkillTable() {
    val source = File("src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt").readText()
    assertFalse(source.contains("s(\"Devil Blessing\""))
    assertFalse(source.contains("s(\"DEVIL BLESSING\""))
  }
}
''', encoding="utf-8")

print("Hidden DEVIL BLESSING: ACTIVE Party companions +5% Attack/Defense/Evasion/HP; Kai receives no bonus.")
