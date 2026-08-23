from pathlib import Path

ROOT = Path(__file__).resolve().parent
SYSTEM = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterEquipmentSystem.kt"
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
print("Lucia compatibility applied to final fillStartingHp equipment normalizer.")
