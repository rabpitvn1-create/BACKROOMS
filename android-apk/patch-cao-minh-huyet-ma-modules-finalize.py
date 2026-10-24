"""Preserve Cao Minh's Canon gloves and boots after generated equipment rewrites.

This is not a save migration. Both modules are linked parts of the one Huyet
Ma armor and therefore carry no independent stat bonuses.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
STATE = CORE / "GameState.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"


def once(source: str, old: str, replacement: str, description: str) -> str:
    if source.count(old) != 1:
        raise RuntimeError(f"{description}: expected exactly one anchor, found {source.count(old)}")
    return source.replace(old, replacement, 1)


state = STATE.read_text(encoding="utf-8")
if "const val CAO_MINH_HUYET_MA_GAUNTLETS_ID" not in state:
    old = 'const val CAO_MINH_HUYET_MA_CHIEN_KHAI_ID = "cao-minh:huyet-ma-chien-khai"'
    state = once(state, old, old + '\n' +
        'const val CAO_MINH_HUYET_MA_GAUNTLETS_ID = "cao-minh:huyet-ma-gauntlets"\n'
        'const val CAO_MINH_HUYET_MA_BOOTS_ID = "cao-minh:huyet-ma-boots"',
        "Cao Minh linked equipment IDs")
if "const val GAUNTLETS_NAME" not in state:
    old = '  const val ARMOR_NAME = "Huyết Ma Chiến Khải"'
    state = once(state, old, old + '\n'
        '  const val GAUNTLETS_NAME = "Găng Huyết Ma"\n'
        '  const val BOOTS_NAME = "Giày Huyết Ma"', "Cao Minh linked equipment names")
if '"gauntlets" to CAO_MINH_HUYET_MA_GAUNTLETS_ID' not in state:
    old = '    "armor" to CAO_MINH_HUYET_MA_CHIEN_KHAI_ID,'
    state = once(state, old, old + '\n'
        '    "gauntlets" to CAO_MINH_HUYET_MA_GAUNTLETS_ID,\n'
        '    "greaves" to CAO_MINH_HUYET_MA_BOOTS_ID,', "Cao Minh starting slot map")
if "CAO_MINH_HUYET_MA_GAUNTLETS_ID -> GAUNTLETS_NAME" not in state:
    old = '    CAO_MINH_HUYET_MA_CHIEN_KHAI_ID -> ARMOR_NAME'
    state = once(state, old, old + '\n'
        '    CAO_MINH_HUYET_MA_GAUNTLETS_ID -> GAUNTLETS_NAME\n'
        '    CAO_MINH_HUYET_MA_BOOTS_ID -> BOOTS_NAME', "Cao Minh display names")
if 'key.contains("găng huyết ma")' not in state:
    old = '      key.contains("huyết ma chiến khải") || key.contains("huyet ma chien khai") -> "armor"'
    state = once(state, old, old + '\n'
        '      key.contains("găng huyết ma") || key.contains("gang huyet ma") || key.contains("huyet ma gauntlet") -> "gauntlets"\n'
        '      key.contains("giày huyết ma") || key.contains("giay huyet ma") || key.contains("huyet ma boot") -> "greaves"', "Cao Minh signature slot resolver")
STATE.write_text(state, encoding="utf-8")

system = SYSTEM.read_text(encoding="utf-8")
if 'CAO_MINH_HUYET_MA_GAUNTLETS_ID, name = "Găng Huyết Ma"' not in system:
    anchor = '''    EquipmentDefinition(
      id = CAO_MINH_HUYET_MA_CHIEN_KHAI_ID'''
    first = system.find(anchor)
    if first < 0:
        raise RuntimeError("Canon Huyet Ma armor definition is missing")
    pos = system.find("    EquipmentDefinition(", first + len(anchor))
    if pos < 0:
        raise RuntimeError("Cannot place linked Huyet Ma modules")
    definitions = '''    EquipmentDefinition(
      id = CAO_MINH_HUYET_MA_GAUNTLETS_ID, name = "Găng Huyết Ma",
      type = "BỘ PHẬN LIÊN KẾT HUYẾT MA CHIẾN KHẢI", primarySlot = EquipmentSlot.GAUNTLETS,
      abilities = listOf(
        EquipmentAbility("Ma Trảo", "Giáp cẳng tay và bàn tay hóa trảo để cắt, bám, khóa hoặc xé pháp khí cự ly gần."),
        EquipmentAbility("Huyết Ấn", "Ma niệm ở lòng bàn tay tác động vật vô chủ hoặc đã khóa bằng thần thức trong phạm vi gần.")
      ),
      restrictions = listOf("Bộ phận liên kết Huyết Ma Chiến Khải; không phải pháp bảo độc lập, không nhân đôi hiệu ứng."),
      canonRef = "CAO_MINH_CODEX R17 §10",
      iconRef = "https://res.cloudinary.com/csz2me5b/image/upload/v1792832619/Item_Cao_Minh_Huyet_Ma_Gauntlets.webp"
    ),
    EquipmentDefinition(
      id = CAO_MINH_HUYET_MA_BOOTS_ID, name = "Giày Huyết Ma",
      type = "BỘ PHẬN LIÊN KẾT HUYẾT MA CHIẾN KHẢI", primarySlot = EquipmentSlot.GREAVES,
      abilities = listOf(
        EquipmentAbility("Đạp Hư Ma Văn", "Ma văn ở chân hỗ trợ bộc phát, đạp lên ma nguyên, đổi hướng giữa không trung và giảm lực tiếp đất.")
      ),
      restrictions = listOf("Bộ phận liên kết Huyết Ma Chiến Khải; không phải pháp bảo độc lập, không nhân đôi hiệu ứng."),
      canonRef = "CAO_MINH_CODEX R17 §10",
      iconRef = "https://res.cloudinary.com/csz2me5b/image/upload/v1792832622/Item_Cao_Minh_Huyet_Ma_Boots.webp"
    ),
'''
    if 'val iconRef: String?' not in system:
        raise RuntimeError("Canon image model is missing iconRef; do not drop item artwork silently")
    system = system[:pos] + definitions + system[pos:]

if "EquipmentSlot.GAUNTLETS to CAO_MINH_HUYET_MA_GAUNTLETS_ID" not in system:
    old = '      EquipmentSlot.ARMOR to CAO_MINH_HUYET_MA_CHIEN_KHAI_ID,'
    system = once(system, old, old + '\n'
        '      EquipmentSlot.GAUNTLETS to CAO_MINH_HUYET_MA_GAUNTLETS_ID,\n'
        '      EquipmentSlot.GREAVES to CAO_MINH_HUYET_MA_BOOTS_ID,', "Cao Minh Core starting loadout")

if 'private const val SCHEMA_VERSION = "2-core-stats-1193a"' in system:
    system = system.replace('private const val SCHEMA_VERSION = "2-core-stats-1193a"',
        'private const val SCHEMA_VERSION = "3-canon-linked-gear"', 1)
SYSTEM.write_text(system, encoding="utf-8")

for marker in ('CAO_MINH_HUYET_MA_GAUNTLETS_ID, name = "Găng Huyết Ma"',
               'CAO_MINH_HUYET_MA_BOOTS_ID, name = "Giày Huyết Ma"',
               'Item_Cao_Minh_Huyet_Ma_Gauntlets.webp',
               'Item_Cao_Minh_Huyet_Ma_Boots.webp',
               'EquipmentSlot.GAUNTLETS to CAO_MINH_HUYET_MA_GAUNTLETS_ID',
               'EquipmentSlot.GREAVES to CAO_MINH_HUYET_MA_BOOTS_ID'):
    if marker not in system:
        raise RuntimeError("Generated Huyet Ma Canon module missing: " + marker)
print("Cao Minh linked Huyet Ma Gauntlets and Boots preserved after generated patches.")
