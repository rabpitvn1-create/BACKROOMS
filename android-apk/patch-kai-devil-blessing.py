from pathlib import Path

ROOT = Path(__file__).resolve().parent
STATS = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterEquipmentSystem.kt"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiDevilBlessingTest.kt"
STATUS_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CharacterStatusEquipmentSystemTest.kt"
NEW_GAME_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/InventoryCapacityNewGameTest.kt"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


stats = STATS.read_text(encoding="utf-8")
effective_old = '''    val unblessedMaxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1)
    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    return EffectiveCharacterStats(
      maxHp = unblessedMaxHp + devilBlessingHp,
      equipmentHp = hp,
      str = character.statProfile.str + str,
      df = character.statProfile.df + df,
      agi = character.statProfile.agi + agi,
      crit = character.statProfile.crit + crit,
'''
effective_new = '''    val unblessedMaxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1)
    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    val kaiBlessed = characterId == KAI_ID
    fun kaiBlessing(value: Int): Int = if (kaiBlessed) maxOf(1, (value * 105 + 99) / 100) else value
    return EffectiveCharacterStats(
      maxHp = kaiBlessing(unblessedMaxHp) + devilBlessingHp,
      equipmentHp = hp,
      str = kaiBlessing(character.statProfile.str + str),
      df = kaiBlessing(character.statProfile.df + df),
      agi = kaiBlessing(character.statProfile.agi + agi),
      crit = character.statProfile.crit + crit,
'''
stats = once(stats, effective_old, effective_new, "Kai Devil Blessing effective stats")

weapon_old = '''  fun weaponDamage(state: GameState, characterId: String): Int {
    val weaponId = state.equipment[characterId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: 18
  }
'''
weapon_new = '''  fun kaiDevilBlessingEvasionBonus(characterId: String): Int = if (characterId == KAI_ID) 5 else 0

  fun weaponDamage(state: GameState, characterId: String): Int {
    val weaponId = state.equipment[characterId]?.slots?.get(EquipmentSlot.WEAPON.key)
    val raw = weaponId?.let { EquipmentCatalog.definition(it)?.weapon?.dmg } ?: 18
    return if (characterId == KAI_ID) maxOf(1, (raw * 105 + 99) / 100) else raw
  }
'''
stats = once(stats, weapon_old, weapon_new, "Kai Devil Blessing attack and evasion")

for marker in (
    'fun kaiBlessing(value: Int)',
    'maxHp = kaiBlessing(unblessedMaxHp)',
    'str = kaiBlessing(',
    'df = kaiBlessing(',
    'agi = kaiBlessing(',
    'kaiDevilBlessingEvasionBonus',
    '(raw * 105 + 99) / 100',
):
    if marker not in stats:
        raise RuntimeError("Kai Devil Blessing stat contract missing: " + marker)
STATS.write_text(stats, encoding="utf-8")

catalog = CATALOG.read_text(encoding="utf-8")
old_skill = '    s("Devil Blessing", "PASSIVE", "Khi đồng đội ACTIVE chiến đấu cùng Kai", "+10% DMG và +10% Max HP cho đồng đội; không áp dụng lên Kai.", "Chỉ hoạt động trong combat có Kai."),\n'
new_skill = '    s("DEVIL BLESSING", "PASSIVE", "Luôn hoạt động trên Kai; hiệu ứng đồng đội kích hoạt khi họ ACTIVE chiến đấu cùng Kai", "Kai: +5% Tấn công, +5% Phòng thủ, +5% Né tránh và +5% Max HP. Đồng đội: giữ +10% DMG và +10% Max HP.", "Cộng đúng một lần; không nhân lặp với chính nó."),\n'
catalog = once(catalog, old_skill, new_skill, "Kai Devil Blessing catalog")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
evasion_old = 'quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)'
evasion_new = 'quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive) + CharacterStatEngine.kaiDevilBlessingEvasionBonus(targetId)'
if combat.count(evasion_old) != 2:
    raise RuntimeError(f"Kai Devil Blessing targeted evasion: expected two anchors, found {combat.count(evasion_old)}")
combat = combat.replace(evasion_old, evasion_new)

fallback_old = '      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)\n'
fallback_new = '      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive) + CharacterStatEngine.kaiDevilBlessingEvasionBonus(KAI_ID)\n'
combat = once(combat, fallback_old, fallback_new, "Kai Devil Blessing fallback evasion")

if combat.count('CharacterStatEngine.kaiDevilBlessingEvasionBonus') < 3:
    raise RuntimeError("Kai Devil Blessing evasion did not reach every combat response path")
COMBAT.write_text(combat, encoding="utf-8")

# Update exact-value regressions that intentionally described Kai before the restored +5% passive.
status_test = STATUS_TEST.read_text(encoding="utf-8")
for old, new in (
    ('assertEquals(100, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)', 'assertEquals(105, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)'),
    ('assertEquals(125, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp); assertEquals(95,', 'assertEquals(132, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp); assertEquals(97,'),
    ('assertEquals(100, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp)', 'assertEquals(105, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp)'),
    ('assertEquals(140, e.maxHp); assertEquals(107, e.str); assertEquals(109, e.df); assertEquals(112, e.agi)', 'assertEquals(147, e.maxHp); assertEquals(113, e.str); assertEquals(115, e.df); assertEquals(118, e.agi)'),
    ('assertEquals(107, p.str.effective)', 'assertEquals(113, p.str.effective)'),
):
    status_test = once(status_test, old, new, "Kai Devil Blessing status regression")
STATUS_TEST.write_text(status_test, encoding="utf-8")

new_game_test = NEW_GAME_TEST.read_text(encoding="utf-8")
for old, new in (
    ('assertEquals(140, kai.currentHp)', 'assertEquals(147, kai.currentHp)'),
    ('assertEquals(140, kai.maxHp)', 'assertEquals(147, kai.maxHp)'),
    ('assertEquals(107, kai.str.effective)', 'assertEquals(113, kai.str.effective)'),
    ('assertEquals(109, kai.df.effective)', 'assertEquals(115, kai.df.effective)'),
    ('assertEquals(112, kai.agi.effective)', 'assertEquals(118, kai.agi.effective)'),
):
    new_game_test = once(new_game_test, old, new, "Kai Devil Blessing new-game regression")
NEW_GAME_TEST.write_text(new_game_test, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiDevilBlessingTest {
  @Test fun kaiReceivesFivePercentCombatStatsExactlyOnce() {
    val state = GameState.initial()
    val kai = state.characters.getValue(KAI_ID)
    val equipmentHp = state.equipment[kai.equipmentId]?.slots.orEmpty().values
      .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }.sumOf { it.bonuses.hp }
    val rawMaxHp = kai.statProfile.baseMaxHp + equipmentHp
    val effective = CharacterStatEngine.effective(state, KAI_ID)
    assertEquals((rawMaxHp * 105 + 99) / 100, effective.maxHp)
    assertEquals(5, CharacterStatEngine.kaiDevilBlessingEvasionBonus(KAI_ID))
    assertEquals(0, CharacterStatEngine.kaiDevilBlessingEvasionBonus(IRIS_ID))
    assertTrue(CharacterStatEngine.weaponDamage(state, KAI_ID) >= 19)
  }
}
''', encoding="utf-8")

print("Kai DEVIL BLESSING restored: +5% Attack, Defense, Evasion and Max HP; existing companion blessing remains intact.")
