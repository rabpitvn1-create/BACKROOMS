from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"

# The final equipment stack has already added fillStartingHp by the time Lucia runs.
text = SYSTEM.read_text(encoding="utf-8")
marker = "    val input = LuciaCanon.ensure(source)\n"
if marker not in text:
    old = '''  private fun normalizeInternal(input: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val inventories = input.inventories.toMutableMap()
'''
    new = '''  private fun normalizeInternal(source: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
'''
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia final normalizer anchor: expected exactly 1, found {count}")
    text = text.replace(old, new, 1)
if marker not in text:
    raise RuntimeError("Lucia final normalizer contract missing")
SYSTEM.write_text(text, encoding="utf-8")

# An Nhien has inserted her own profile before this point. Add Lucia without removing it,
# and order routing so patch-lucia-follower.py can recognize the already-applied Lucia branch.
policy = POLICY.read_text(encoding="utf-8")
if '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' not in policy:
    anchor = '  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)\n'
    if anchor not in policy:
        raise RuntimeError("Lucia final inventory profile anchor missing")
    policy = policy.replace(anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' + anchor, 1)

old_route = '''    if (characterId == KAI_ID) return KAI
    if (characterId == AN_NHIEN_ID) return AN_NHIEN
    val character = state.characters[characterId]
'''
new_route = '''    if (characterId == KAI_ID) return KAI
    if (characterId == LUCIA_ID) return LUCIA
    val character = state.characters[characterId]
    if (characterId == AN_NHIEN_ID) return AN_NHIEN
'''
if new_route not in policy:
    count = policy.count(old_route)
    if count != 1:
        raise RuntimeError(f"Lucia final inventory routing anchor: expected exactly 1, found {count}")
    policy = policy.replace(old_route, new_route, 1)

for required in (
    'val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)',
    'if (characterId == LUCIA_ID) return LUCIA',
    'if (characterId == AN_NHIEN_ID) return AN_NHIEN',
):
    if required not in policy:
        raise RuntimeError("Lucia final inventory compatibility missing: " + required)
POLICY.write_text(policy, encoding="utf-8")

print("Lucia compatibility applied to final equipment normalizer and inventory policy stack.")
