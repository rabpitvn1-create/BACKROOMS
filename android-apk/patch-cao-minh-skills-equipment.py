"""Port Cao Minh's 1.1.99 kit after the legacy runtime patch chain."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'

def edit(path, old, new):
    source = path.read_text(encoding='utf-8')
    if old not in source:
        raise RuntimeError(f'{path.name}: missing anchor {old[:70]}')
    path.write_text(source.replace(old, new), encoding='utf-8')

names = {
    'The Last Requiem': 'Huyết Ma Tứ Liên',
    'Silent Lullaby': 'Ma Tâm Trấn Hồn',
    'Salvation': 'Huyết Ảnh Ma Độn',
    'Quick Step': 'Thiên Ma Bộ',
    'Guilty Crown Override': 'Huyết Ma Nhị Thập Tứ Trảm',
    'Omnivault Ring': 'Nhẫn Vạn Tàng',
    'nhẫn Omnivault': 'Nhẫn Vạn Tàng',
    'White Wraith Magnum': 'Huyết Ma Kiếm',
    'W.W Magnum': 'Huyết Ma Kiếm',
    'Blackblood Armor & linked modules': 'Huyết Ma Chiến Khải',
    'Blackblood Armor': 'Huyết Ma Chiến Khải',
    'Sparda Core': 'Vạn Quỷ Ma Tâm',
}
for path in [*CORE.glob('*.kt'), ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java', ROOT / 'app/src/main/assets/index.html']:
    source = path.read_text(encoding='utf-8')
    for old, new in names.items():
        source = source.replace(old, new)
    path.write_text(source, encoding='utf-8')

state = CORE / 'GameState.kt'
for old, new in [('kai:white-wraith-magnum', 'cao_minh:huyet-ma-kiem'), ('kai:blackblood-armor', 'cao_minh:huyet-ma-chien-khai'), ('kai:omnivault-ring', 'cao_minh:nhan-van-tang')]:
    edit(state, old, new)
for slot, const in [('head', 'KAI_DEMON_JAW_MASK_ID'), ('gauntlets', 'KAI_TALON_GAUNTLETS_ID'), ('greaves', 'KAI_PHANTOM_GREAVES_ID')]:
    edit(state, f'    "{slot}" to {const},\n', '')
edit(state, 'key.contains("w.w magnum")', 'key.contains("huyết ma kiếm") || key.contains("huyet ma kiem") || key.contains("w.w magnum")')
edit(state, 'key.contains("blackblood armor")', 'key.contains("huyết ma chiến khải") || key.contains("huyet ma chien khai") || key.contains("blackblood armor")')

equipment = CORE / 'CharacterEquipmentSystem.kt'
source = equipment.read_text(encoding='utf-8')
start = source.index('    EquipmentDefinition(\n      id = KAI_WHITE_WRAITH_ID')
end = source.index('    EquipmentDefinition(\n      id = IRIS_', start)
source = source[:start] + '''    EquipmentDefinition(
      id = KAI_WHITE_WRAITH_ID, name = "Huyết Ma Kiếm", type = "MA ĐẠO ĐẠI KIẾM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8), weapon = WeaponGameplayStats(32),
      abilities = listOf(ability("Ngự kiếm", "Điều khiển kiếm bằng thần niệm."), ability("Huyết Sát Ma Khí", "Gia cường kiếm bằng ma nguyên."), ability("Triệu hồi bản mệnh", "Gọi Huyết Ma Kiếm trở về.")),
      canonRef = "CAO-EQP-HUYET-MA-KIEM-01"
    ),
    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "Huyết Ma Chiến Khải", type = "MA KHẢI", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6),
      abilities = listOf(ability("Ma Kim hộ thể", "Ma khải bảo vệ nhục thân và phân tán lực va chạm."), ability("Ma nguyên tự phục hồi", "Tự phục hồi pháp bảo; không đồng nghĩa hồi HP tức thì.")),
      canonRef = "CAO-EQP-HUYET-MA-KHAI-01"
    ),
    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Nhẫn Vạn Tàng", type = "NHẪN KHÔNG GIAN", primarySlot = EquipmentSlot.RING,
      abilities = listOf(ability("Tiểu không gian", "Lưu trữ vật phẩm theo authority của hệ thống hiện hành."), ability("Scan / Copy", "Giữ cơ chế Scan / Copy hiện hành theo yêu cầu đổi tên.")),
      canonRef = "CAO-EQP-VANTANG-01"
    ),
''' + source[end:]
for slot, const in [('HEAD', 'KAI_DEMON_JAW_MASK_ID'), ('GAUNTLETS', 'KAI_TALON_GAUNTLETS_ID'), ('GREAVES', 'KAI_PHANTOM_GREAVES_ID')]:
    source = source.replace(f'      EquipmentSlot.{slot} to {const},\n', '')
# The legacy cheat weapon remains available; its armor component is now intrinsic.
start = source.index('    EquipmentDefinition(\n      id = MADGOD_SET_ID')
end = source.index('\n  )\n\n  private val definitions', start)
source = source[:start] + '''    EquipmentDefinition(
      id = MADGOD_SET_ID, name = "Huyết Ma Kiếm · Ma Tôn", type = "SPECIAL SWORD", primarySlot = EquipmentSlot.WEAPON,
      occupiesSlots = setOf(EquipmentSlot.WEAPON), rarity = "SPECIAL / CHEAT",
      bonuses = EquipmentBonuses(crit = 12), weapon = WeaponGameplayStats(55),
      abilities = listOf(ability("Bản mệnh ma kiếm", "Vũ khí cheat giữ projection DMG 55; giáp MadGod đã chuyển thành passive Ma Tôn Vạn Giới.")),
      restrictions = listOf("Cao Minh Only", "Cannot Unequip after activation", "Cannot be copied or scanned"),
      classification = ItemClassification.SPECIAL_CHEAT,
      components = listOf(EquipmentComponent("Huyết Ma Kiếm", weapon = WeaponGameplayStats(55)))
    )''' + source[end:]
source = source.replace('    val hp = definitions.sumOf', '    val passive = if (characterId == KAI_ID) EquipmentBonuses(hp = MadGodCanon.ARMOR_HP, str = MadGodCanon.ARMOR_STR, df = MadGodCanon.ARMOR_DF, agi = MadGodCanon.ARMOR_AGI) else EquipmentBonuses()\n    val hp = definitions.sumOf')
source = source.replace('(character.statProfile.baseMaxHp + hp)', '(character.statProfile.baseMaxHp + passive.hp + hp)')
for key in ['str', 'df', 'agi', 'crit']:
    source = source.replace(f'character.statProfile.{key} + {key},', f'character.statProfile.{key} + passive.{key} + {key},')
equipment.write_text(source, encoding='utf-8')

catalog = CORE / 'CompanionSkillCatalog.kt'
source = catalog.read_text(encoding='utf-8')
start = source.index('  private val kai = listOf(')
end = source.index('\n  private val lucia', start)
source = source[:start] + '''  private val kai = listOf(
    s("Ma Tôn Vạn Giới", "PASSIVE", "Luôn hoạt động", "+50 Max HP, +15 STR, +30 DF, +12 AGI; kế thừa bonus giáp MadGod theo gameplay hiện hành.", "Không chiếm ô trang bị, không nhân/stack qua save-load hoặc equip."),
    s("Huyết Ma Tứ Liên", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm, 170% Weapon DMG; Chảy máu 3 turn x 5% Max HP."),
    s("Ma Tâm Trấn Hồn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm cùng điểm, 130% Weapon DMG; Choáng phản ứng hiện tại."),
    s("Huyết Ảnh Ma Độn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Dịch chuyển theo Huyết Ma Kiếm, đúng 2 trảm, 147% Weapon DMG."),
    s("Thiên Ma Bộ", "AUTO", "30% khi TẤN CÔNG hoặc NÉ TRÁNH", "+50 điểm phần trăm Evasion trong 3 turn; không chặn AoE bắt buộc."),
    s("Huyết Sát Kiếm Ấn", "AUTO", "50% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+25% DMG; Chảy máu 2 turn x 3% Max HP."),
    s("Phá Giáp Ma Kiếm", "AUTO", "48% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Xuyên giáp 10% trong 2 turn."),
    s("Ma Tâm Chấn", "AUTO", "45% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+15% DMG; Choáng phản ứng hiện tại."),
    s("Huyết Độc Ma Khí", "AUTO", "47% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Trúng độc 2 turn x 3% Max HP."),
    s("Huyết Liệt Ma Ấn", "AUTO", "51% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Chảy máu 2 turn x 4% Max HP."),
    s("Huyết Ma Nhị Thập Tứ Trảm", "ULTIMATE", "Mỗi 3 combat turn khi TẤN CÔNG", "Đúng 24 trảm, mỗi trảm 115% current Weapon DMG; bỏ qua Evasion.")
  )
''' + source[end:]
catalog.write_text(source, encoding='utf-8')

combat = CORE / 'CombatRuntime.kt'
edit(combat, 'val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT', 'val damagePerSlash = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID) * 115 / 100\n      val totalDamage = KAI_GUILTY_CROWN_SHOTS * damagePerSlash')
edit(combat, 'mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP', 'mỗi trảm -$damagePerSlash HP')
for old, new in [('phát trúng liên tiếp', 'trảm trúng liên tiếp'), ('4 phát vào khớp vai', '4 trảm vào điểm nối hộ thể'), ('4 viên ghim cùng điểm trên ngực', '4 đường ma kiếm vào cùng điểm trên ngực'), ('Cao Minh ném súng ra sau mục tiêu, dịch chuyển tức thời tới vị trí súng và bắn nhanh 2 phát', 'Cao Minh ngự Huyết Ma Kiếm qua mục tiêu, dịch chuyển theo kiếm và chém nhanh đúng 2 trảm')]:
    edit(combat, old, new)

# Keep the existing authoritative combat counters and add only the five 1.1.99 procs.
edit(combat, '    var entityStunnedThisTurn = false', '''    var entityStunnedThisTurn = false
    val procSpecs = listOf(
      listOf("Huyết Sát Kiếm Ấn", "50", "25", "bleed", "3"),
      listOf("Phá Giáp Ma Kiếm", "48", "20", "armorBreak", "10"),
      listOf("Ma Tâm Chấn", "45", "15", "stun", "0"),
      listOf("Huyết Độc Ma Khí", "47", "20", "poison", "3"),
      listOf("Huyết Liệt Ma Ấn", "51", "20", "bleed", "4")
    )
    // Persistent per-skill durations: refresh, never stack the same proc twice.
    procSpecs.forEachIndexed { index, spec ->
      val key = "combat.caoMinhProc$index"
      val turns = state.metadata[key]?.toIntOrNull() ?: 0
      if (turns > 0) {
        if (spec[3] == "bleed" || spec[3] == "poison") {
          val damage = c.entityMaxHp * spec[4].toInt() / 100
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "${spec[0]}: -$damage HP (${spec[3]})."
        }
        resolvedState = withCombatCounter(resolvedState, key, turns - 1)
      }
    }''')
edit(combat, '    if (c.entityHp > 0) {\n      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)', '''    if (intent == Intent.ATTACK && c.entityHp > 0 && !isGuiltyCrownTurn) {
      procSpecs.forEachIndexed { index, spec ->
        if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 401 + index * 17), 100) < spec[1].toInt()) {
          val damage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID) * spec[2].toInt() / 100
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          if (spec[3] == "stun") entityStunnedThisTurn = true
          else resolvedState = withCombatCounter(resolvedState, "combat.caoMinhProc$index", 2)
          log += "${spec[0]} kích hoạt: -$damage HP; ${spec[3]}."
        }
      }
    }
    if (c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)''')
# The armor-break proc reduces the armor used by all Cao Minh damage paths.
edit(combat, 'val profile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)', '''val originalProfile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val profile = if ((state.metadata["combat.caoMinhProc1"]?.toIntOrNull() ?: 0) > 0) originalProfile.copy(armor = originalProfile.armor * 90 / 100) else originalProfile''')

stats = CORE / 'CharacterStats.kt'
edit(stats, 'COMMANDER / SUPREME MARKSMAN / HIGH-MOBILITY COMBATANT', 'VẠN GIỚI MA TÔN / MA ĐẠO KIẾM TU')
edit(stats, 'kai:passive-regeneration', 'cao_minh:passive-regeneration')

projection = CORE / 'CharacterDetailProjection.kt'
for key, value in [('str', 15), ('df', 30), ('agi', 12)]:
    edit(projection, f'StatLineProjection(character.statProfile.{key},', f'StatLineProjection(character.statProfile.{key} + if (character.id == KAI_ID) {value} else 0,')

madgod = CORE / 'MadGodCanon.kt'
edit(madgod, 'mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)', 'mapOf("weapon" to MADGOD_SET_ID)')
edit(madgod, 'MadGod Armor + MadGod Magnum', 'Huyết Ma Kiếm; phần giáp đã chuyển thành passive Ma Tôn Vạn Giới')
edit(madgod, 'const val SET_NAME = "MadGod Set"', 'const val SET_NAME = "Huyết Ma Kiếm · Ma Tôn"')
source = madgod.read_text(encoding='utf-8')
start = source.index('  fun setItem()')
end = source.index('\n  fun spawn(', start)
source = source[:start] + '''  fun setItem() = EquipmentCatalog.stackFor(MADGOD_SET_ID).let {
    it.copy(metadata = it.metadata + mapOf("madGod" to "true", "permanentWhenEquipped" to "true"))
  }
''' + source[end:]
start = source.index('  fun legacy(s: GameState)')
source = source[:start] + '''  fun legacy(s: GameState) = JSONObject().apply {
    s.equipment[KAI_ID]?.slots.orEmpty().forEach { (slot, id) ->
      put(slot, JSONObject().put("id", id)
        .put("name", EquipmentCatalog.definition(id)?.name ?: id)
        .put("permanent", isId(id)).put("scalingMode", SCALING_MODE))
    }
  }
}
'''
madgod.write_text(source, encoding='utf-8')
facade = CORE / 'GameCoreFacade.kt'
edit(facade, 'MadGod Set đã ghi đè Huyết Ma Kiếm và Huyết Ma Chiến Khải của Cao Minh. Nhẫn Vạn Tàng được giữ nguyên.', 'Vũ khí MadGod đã trang bị; Ma Tôn Vạn Giới là passive và không chiếm ô giáp. Huyết Ma Chiến Khải và Nhẫn Vạn Tàng được giữ nguyên.')
edit(facade, 'trang bị một lần sẽ kích hoạt đồng thời MadGod Armor và MadGod Magnum', 'trang bị một lần sẽ kích hoạt vũ khí; phần giáp đã chuyển thành passive Ma Tôn Vạn Giới')
edit(facade, 'một lần Equip sẽ chiếm đồng thời slot vũ khí và giáp', 'chỉ chiếm slot vũ khí; giáp MadGod đã chuyển thành passive Ma Tôn Vạn Giới')

tests = ROOT / 'app/src/test/java/com/rabpit/backroom/core'
for path in tests.glob('*.kt'):
    text = path.read_text(encoding='utf-8')
    for old, new in names.items():
        text = text.replace(old, new)
    path.write_text(text, encoding='utf-8')

# Update assertions whose contracts changed with the three-item loadout/passive split.
for filename in ['CharacterStatusEquipmentSystemTest.kt', 'PokerDiceCoreBackportTest.kt', 'InventoryCapacityNewGameTest.kt', 'MadGodEquipmentTest.kt']:
    path = tests / filename
    text = path.read_text(encoding='utf-8')
    for old, new in [(140,175),(107,105),(109,126),(112,110),(165,175),(114,105),(121,126),(118,110),(113,107)]:
        text = text.replace(f'assertEquals({old},', f'assertEquals({new},')
    # Crit differs from DEF; keep the exact expected stat explicitly.
    text = text.replace('assertEquals(126, e.crit)', 'assertEquals(103, e.crit)').replace('assertEquals(126, before.crit)', 'assertEquals(103, before.crit)').replace('assertEquals(126, kai.crit.effective)', 'assertEquals(103, kai.crit.effective)')
    text = text.replace('assertEquals(MADGOD_SET_ID, slots["armor"])', 'assertEquals(KAI_BLACKBLOOD_ARMOR_ID, slots["armor"])')
    text = text.replace('assertEquals(MADGOD_SET_ID, r.state.equipment.getValue(KAI_ID).slots["armor"])', 'assertEquals(KAI_BLACKBLOOD_ARMOR_ID, r.state.equipment.getValue(KAI_ID).slots["armor"])')
    text = text.replace('assertEquals(2, slots.values.count { it == MADGOD_SET_ID })', 'assertEquals(1, slots.values.count { it == MADGOD_SET_ID })')
    text = text.replace('setOf(EquipmentSlot.WEAPON, EquipmentSlot.ARMOR)', 'setOf(EquipmentSlot.WEAPON)')
    text = text.replace('assertEquals(50, def.bonuses.hp)', 'assertEquals(0, def.bonuses.hp)').replace('assertEquals(15, def.bonuses.str)', 'assertEquals(0, def.bonuses.str)').replace('assertEquals(30, def.bonuses.df)', 'assertEquals(0, def.bonuses.df)').replace('assertEquals(12, def.bonuses.agi)', 'assertEquals(0, def.bonuses.agi)').replace('assertEquals(50, d.bonuses.hp)', 'assertEquals(0, d.bonuses.hp)')
    for slot in ['head','gauntlets','greaves']:
        text = re.sub(r'assertEquals\(KAI_[A-Z_]+, slots\["' + slot + r'"\]\)', f'assertNull(slots["{slot}"])', text)
    text = text.replace('assertEquals(50, CharacterStatEngine.effective(equipped, KAI_ID).equipmentHp - 15)', 'assertEquals(25, CharacterStatEngine.effective(equipped, KAI_ID).equipmentHp)')
    path.write_text(text, encoding='utf-8')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals(100, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)', 'assertEquals(150, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals(125, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp)', 'assertEquals(175, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp)')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals(100, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp)', 'assertEquals(150, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp)')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals(25, p.str.equipment); assertEquals(82, p.str.base)', 'assertEquals(8, p.str.equipment); assertEquals(97, p.str.base)')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals("40",', 'assertEquals("25",')
edit(tests / 'CharacterStatusEquipmentSystemTest.kt', 'assertEquals("140",', 'assertEquals("175",')
edit(tests / 'PokerDiceCoreBackportTest.kt', 'assertEquals(110, CharacterStatEngine.effective(upgraded.state, KAI_ID).str)', 'assertEquals(116, CharacterStatEngine.effective(upgraded.state, KAI_ID).str)')
edit(tests / 'PokerDiceCoreBackportTest.kt', 'assertEquals(154, effective.maxHp)', 'assertEquals(193, effective.maxHp)')
edit(tests / 'PokerDiceCoreBackportTest.kt', 'assertEquals(105, result.state.characters.getValue(KAI_ID).vitalState.currentHp)', 'assertEquals(118, result.state.characters.getValue(KAI_ID).vitalState.currentHp)')
edit(tests / 'InventoryCapacityNewGameTest.kt', 'assertEquals(2, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))', 'assertEquals(1, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))')
edit(tests / 'InventoryCapacityNewGameTest.kt', 'assertTrue(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, KAI_BLACKBLOOD_ARMOR_ID))', 'assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, KAI_BLACKBLOOD_ARMOR_ID))')
edit(tests / 'InventoryCapacityNewGameTest.kt', 'assertEquals(6, kai.equipment.values.toSet().size)', 'assertEquals(3, kai.equipment.values.toSet().size)')
edit(tests / 'InventoryCapacityNewGameTest.kt', 'assertTrue(kai.inventoryDetails.count { it.equipped } >= 6)', 'assertEquals(3, kai.inventoryDetails.count { it.equipped })')
path = tests / 'CombatRuntimeTest.kt'
text = path.read_text(encoding='utf-8').replace('assertEquals(140, expectedMaxHp)', 'assertEquals(175, expectedMaxHp)').replace('24/24 phát trúng liên tiếp', '24/24 trảm trúng liên tiếp').replace('mỗi phát -10 HP', 'mỗi trảm -36 HP').replace('tổng -240 HP', 'tổng -864 HP')
path.write_text(text, encoding='utf-8')

main = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'
source = main.read_text(encoding='utf-8')
legacy_prompt_methods = (
    r'  private String canonSection\(String source, String start, String end\) \{.*?\n  \}\n',
    r'  private String canonLineStarting\(String source, String prefix\) \{.*?\n  \}\n',
    r'  private boolean actionDialogue\(String action\) \{.*?\n  \}\n',
    r'  private boolean actionCombat\(String action\) \{.*?\n  \}\n',
    r'  private boolean actionOmnivault\(String action\) \{.*?\n  \}\n',
    r'  private boolean actionItem\(String action\) \{.*?\n  \}\n',
    r'  private boolean actionEntity\(String action\) \{.*?\n  \}\n',
    r'  private String compactDriveCanon\(JSONObject state, String action, JSONObject rolls\) \{.*?\n  \}\n',
    r'  private String compactKaiCanon\(String action\) \{.*?\n  \}\n',
)
for pattern in legacy_prompt_methods:
    source = re.sub(pattern, '', source, count=1, flags=re.S)
source = re.sub(r'^  private static final String KAI_CANON = .*;\n', '', source, count=1, flags=re.M)
source = re.sub(r'^  private static final String DRIVE_CANON = .*;\n', '', source, count=1, flags=re.M)
for retired in ('compactDriveCanon(', 'compactKaiCanon(', 'KAI_CANON', 'DRIVE_CANON ='):
    if retired in source:
        raise RuntimeError('Retired canon runtime remains: ' + retired)
main.write_text(source, encoding='utf-8')

(tests / 'CaoMinhSkillsEquipmentTest.kt').write_text('''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test

class CaoMinhSkillsEquipmentTest {
  @Test fun threeCanonicalItemsAndIntrinsicPassiveSurviveReload() {
    val state = GameState.initial()
    assertEquals(setOf("weapon", "armor", "ring"), state.equipment.getValue(KAI_ID).slots.keys)
    assertEquals(setOf("Huyết Ma Kiếm", "Huyết Ma Chiến Khải", "Nhẫn Vạn Tàng"), state.inventories.getValue(KAI_ID).items.values.map { it.name }.toSet())
    assertEquals(175, CharacterStatEngine.effective(state, KAI_ID).maxHp)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(CharacterStatEngine.effective(state, KAI_ID), CharacterStatEngine.effective(loaded, KAI_ID))
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    val stats = CharacterStatEngine.effective(stripped, KAI_ID)
    assertEquals(150, stats.maxHp); assertEquals(97, stats.str); assertEquals(108, stats.df); assertEquals(104, stats.agi)
  }
  @Test fun skillListIncludesTheWhole199KitAndNoGunSkills() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID)
    assertEquals(11, skills.size)
    assertEquals("PASSIVE", skills.single { it.name == "Ma Tôn Vạn Giới" }.kind)
    assertTrue(skills.any { it.name == "Huyết Ma Nhị Thập Tứ Trảm" })
    assertFalse(skills.any { it.name == "Salvation" || it.name == "Guilty Crown Override" })
  }
  @Test fun procPoolResolvesAndUltimateExcludesNewRolls() {
    val observed = mutableSetOf<String>()
    for (counter in 0..200) {
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      for (name in listOf("Huyết Sát Kiếm Ấn", "Phá Giáp Ma Kiếm", "Ma Tâm Chấn", "Huyết Độc Ma Khí", "Huyết Liệt Ma Ấn")) {
        if (result.reply.contains("$name kích hoạt")) observed += name
        if (result.reply.contains("Huyết Ma Nhị Thập Tứ Trảm")) assertFalse(result.reply.contains("$name kích hoạt"))
      }
    }
    assertEquals(5, observed.size)
  }
}
''', encoding='utf-8')

# Do not advertise a technological armor or change Cao Minh's avatar on the old cheat.
html = ROOT / 'app/src/main/assets/index.html'
edit(html, 'function madGodSetEquipped(){', 'function madGodSetEquipped(){return false;}\n  function legacyMadGodSetEquipped(){')
print('Cao Minh 1.1.99 skills/equipment installed; Ma Tôn Vạn Giới is intrinsic; Nhẫn Vạn Tàng renamed.')
