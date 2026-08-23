from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"
TEST = TESTS / "InventoryCapacityNewGameTest.kt"

system = SYSTEM.read_text(encoding="utf-8")
old = '''  fun carriedItemIds(state: GameState, characterId: String): Set<String> {
    val owned = state.inventories[characterId]?.items.orEmpty()
      .filterValues { it.quantity > 0 }
      .keys
    return owned - equippedItemIds(state, characterId)
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size
'''
new = '''  fun carriedItemIds(state: GameState, characterId: String): Set<String> =
    carriedItemIds(state, characterId, state.inventories[characterId] ?: InventoryState(characterId))

  fun carriedItemIds(state: GameState, characterId: String, inventory: InventoryState): Set<String> {
    val owned = inventory.items.filterValues { it.quantity > 0 }.keys
    return owned - equippedItemIds(state, characterId)
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size
  fun usedSlots(state: GameState, characterId: String, inventory: InventoryState): Int = carriedItemIds(state, characterId, inventory).size
'''
if new not in system:
    if old not in system:
        raise RuntimeError("InventoryCapacityPolicy carried-item anchor missing")
    system = system.replace(old, new, 1)
SYSTEM.write_text(system, encoding="utf-8")

policy = POLICY.read_text(encoding="utf-8")
old_policy = '    val carriedTypes = InventoryCapacityPolicy.usedSlots(state, ownerId)\n'
new_policy = '    val carriedTypes = InventoryCapacityPolicy.usedSlots(state, ownerId, inventory)\n'
if new_policy not in policy:
    if old_policy not in policy:
        raise RuntimeError("InventoryPolicy candidate-capacity anchor missing")
    policy = policy.replace(old_policy, new_policy, 1)
POLICY.write_text(policy, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
old_expectation = '''    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
    assertEquals(0, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))
'''
new_expectation = '''    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
    // MadGod itself consumes zero slots while equipped. The displaced White Wraith and
    // Blackblood Armor remain owned but are now unequipped, so they correctly consume two slots.
    assertEquals(2, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))
    assertTrue(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, KAI_WHITE_WRAITH_ID))
    assertTrue(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, KAI_BLACKBLOOD_ARMOR_ID))
'''
if new_expectation not in test:
    if old_expectation not in test:
        raise RuntimeError("MadGod capacity regression anchor missing")
    test = test.replace(old_expectation, new_expectation, 1)
TEST.write_text(test, encoding="utf-8")

combined = SYSTEM.read_text(encoding="utf-8") + POLICY.read_text(encoding="utf-8") + TEST.read_text(encoding="utf-8")
for marker in (
    'fun usedSlots(state: GameState, characterId: String, inventory: InventoryState)',
    'InventoryCapacityPolicy.usedSlots(state, ownerId, inventory)',
    'assertEquals(2, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))',
):
    if marker not in combined:
        raise RuntimeError("Final capacity regression contract missing: " + marker)

print("Inventory capacity final fix applied: validation uses candidate inventory; equipped items cost zero slots; displaced gear costs slots.")
