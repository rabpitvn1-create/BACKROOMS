from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"
TEST = TESTS / "InventoryCapacityNewGameTest.kt"
INDEX = ROOT / "app/src/main/assets/index.html"

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

# Character Detail must render one card per equipped item, even when one item
# intentionally occupies multiple equipment slots (for example MadGod weapon+armor).
html = INDEX.read_text(encoding="utf-8")
old_equipment_renderer = '''    if(equipment){const rendered=[];Object.keys(eq).sort().forEach(slot=>{const id=eq[slot],item=details.find(x=>String(x.id)===String(id))||itemById(member,id);if(item)rendered.push(card(item,slot))});equipment.innerHTML=rendered.length?rendered.join(''):'<span>Không có trang bị được ghi nhận.</span>'}
'''
new_equipment_renderer = '''    if(equipment){const grouped=new Map();Object.keys(eq).sort().forEach(slot=>{const id=String(eq[slot]||'');if(!id)return;const slots=grouped.get(id)||[];slots.push(slot);grouped.set(id,slots)});const rendered=[];grouped.forEach((slots,id)=>{const item=details.find(x=>String(x.id)===id)||itemById(member,id);if(item)rendered.push(card(item,slots.join(' / ')))});equipment.innerHTML=rendered.length?rendered.join(''):'<span>Không có trang bị được ghi nhận.</span>'}
'''
if new_equipment_renderer not in html:
    if old_equipment_renderer not in html:
        raise RuntimeError("Character Equipment renderer anchor missing")
    html = html.replace(old_equipment_renderer, new_equipment_renderer, 1)
if old_equipment_renderer in html:
    raise RuntimeError("Per-slot duplicate Equipment renderer survived")
INDEX.write_text(html, encoding="utf-8")

combined = SYSTEM.read_text(encoding="utf-8") + POLICY.read_text(encoding="utf-8") + TEST.read_text(encoding="utf-8") + INDEX.read_text(encoding="utf-8")
for marker in (
    'fun usedSlots(state: GameState, characterId: String, inventory: InventoryState)',
    'InventoryCapacityPolicy.usedSlots(state, ownerId, inventory)',
    'assertEquals(2, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))',
    'const grouped=new Map();Object.keys(eq).sort()',
    "rendered.push(card(item,slots.join(' / ')))",
):
    if marker not in combined:
        raise RuntimeError("Final capacity/equipment regression contract missing: " + marker)

print("Inventory capacity final fix applied; multi-slot Equipment now renders one card per item with combined slot labels.")

# This is the last patch in the release chain. Keep Snapshot/overlay state synchronized only after
# every earlier gameplay, combat, equipment and UI transform has finished touching the runtime.
runpy.run_path(str(ROOT / "patch-visual-state-sync-final.py"), run_name="__main__")

# The existing final equipment patch adds fillStartingHp to normalizeInternal. Adapt that exact
# final signature before Lucia attaches her save/backfill hook.
runpy.run_path(str(ROOT / "patch-lucia-normalize-compat.py"), run_name="__main__")

# Lucia is applied after all existing runtime/UI transforms so later patches cannot erase her
# stats, inventory policy, three-slot loadout, encounter gate, or prompt contract.
runpy.run_path(str(ROOT / "patch-lucia-follower.py"), run_name="__main__")

# Final Entity action authority runs after Lucia because Lucia still adds an EXPLORE-only follower
# encounter contract to MainActivity. This keeps roaming Entity generation available to all three
# primary actions without changing Lucia's own Level 0 follower rules.
runpy.run_path(str(ROOT / "patch-entity-encounter-all-actions.py"), run_name="__main__")

# Final combat balance authority applies after all combat/runtime generation is complete so no
# earlier pressure-combat or character-status patch can overwrite Entity durability semantics.
runpy.run_path(str(ROOT / "patch-entity-combat-durability.py"), run_name="__main__")
