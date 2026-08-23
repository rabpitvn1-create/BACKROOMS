from pathlib import Path

ROOT = Path(__file__).resolve().parent
SYSTEM = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterEquipmentSystem.kt"
text = SYSTEM.read_text(encoding="utf-8")

old_seed = '''  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)

  private fun normalizeInternal(input: GameState, seedStarting: Boolean): GameState {
'''
new_seed = '''  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true, fillStartingHp = true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION, fillStartingHp = false)

  private fun normalizeInternal(input: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
'''
if new_seed not in text:
    if old_seed not in text:
        raise RuntimeError("CharacterEquipmentSystem fresh seed anchor missing")
    text = text.replace(old_seed, new_seed, 1)

old_hp = '''      val hp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      characters[id] = character.copy(
'''
new_hp = '''      val rawHp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      val hp = if (
        fillStartingHp &&
        rawHp == character.statProfile.baseMaxHp &&
        character.vitalState.condition == CharacterCondition.HEALTHY &&
        character.vitalState.lastRegenCompletedTurnId == null
      ) effective.maxHp else rawHp
      characters[id] = character.copy(
'''
if new_hp not in text:
    if old_hp not in text:
        raise RuntimeError("CharacterEquipmentSystem fresh HP anchor missing")
    text = text.replace(old_hp, new_hp, 1)

if 'fillStartingHp = true' not in text or ') effective.maxHp else rawHp' not in text:
    raise RuntimeError("Fresh effective HP contract missing")

SYSTEM.write_text(text, encoding="utf-8")
print("Fresh canonical loadout now starts at full Effective HP; later equip/unequip still preserves missing HP.")
