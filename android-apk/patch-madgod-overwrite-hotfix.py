from pathlib import Path

ROOT = Path(__file__).resolve().parent
ENGINES = ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"

engines = ENGINES.read_text(encoding="utf-8")

# MadGod R3 already binds both weapon + armor atomically. The real failure is narrower:
# CommandResolver sends normal EQUIP text through the weapon slot, while R3 rejected anything
# except its synthetic "set" slot. Remove only that parser/engine mismatch. Existing weapon and
# armor are deliberately overwritten by boundSlots; unrelated slots such as the ring survive.
strict_gate = '          if (command.actorId != KAI_ID || slot != "set" || MadGodCanon.slot(command.itemId, source.items[command.itemId]?.name.orEmpty()) != "set") return invalid(state,"madgod_equipment_slot_mismatch")\n'
forced_gate = '          if (command.actorId != KAI_ID) return invalid(state,"madgod_equipment_slot_mismatch")\n'
if forced_gate not in engines:
    count = engines.count(strict_gate)
    if count != 1:
        raise RuntimeError(f"MadGod synthetic-slot gate: expected 1 match, found {count}")
    engines = engines.replace(strict_gate, forced_gate, 1)

bound = '          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)\n'
if bound not in engines:
    raise RuntimeError("MadGod atomic weapon+armor binding missing")

ENGINES.write_text(engines, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
needle = '''  @Test fun omnivaultCannotCopyTheSet() {
'''
extra = r'''  @Test fun equipOverwritesKaisExistingWeaponAndArmorFromNormalWeaponSlot() {
    val spawned = MadGodCanon.spawn(GameState.initial()).state
    val before = spawned.equipment.getValue(KAI_ID).slots
    assertEquals(KAI_WHITE_WRAITH_ID, before["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, before["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, before["ring"])

    val equip = InventoryEngine.execute(
      spawned,
      ItemCommand(
        "e-overwrite", "TURN_1", KAI_ID,
        source = CommandSource.UI,
        operation = ItemCommand.Operation.EQUIP,
        itemId = MADGOD_SET_ID,
        itemName = MadGodCanon.SET_NAME,
        slot = "weapon"
      )
    )

    assertTrue(equip.applied)
    val after = equip.state.equipment.getValue(KAI_ID).slots
    assertEquals(MADGOD_SET_ID, after["weapon"])
    assertEquals(MADGOD_SET_ID, after["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, after["ring"])
    assertFalse(after.values.contains(KAI_WHITE_WRAITH_ID))
    assertFalse(after.values.contains(KAI_BLACKBLOOD_ARMOR_ID))
  }

'''
if "equipOverwritesKaisExistingWeaponAndArmorFromNormalWeaponSlot" not in test:
    if needle not in test:
        raise RuntimeError("MadGod overwrite test insertion anchor missing")
    test = test.replace(needle, extra + needle, 1)

TEST.write_text(test, encoding="utf-8")

combined = ENGINES.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'if (command.actorId != KAI_ID) return invalid(state,"madgod_equipment_slot_mismatch")',
    'val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)',
    'equipOverwritesKaisExistingWeaponAndArmorFromNormalWeaponSlot',
    'assertFalse(after.values.contains(KAI_WHITE_WRAITH_ID))',
    'assertFalse(after.values.contains(KAI_BLACKBLOOD_ARMOR_ID))',
):
    if marker not in combined:
        raise RuntimeError("MadGod overwrite hotfix contract missing: " + marker)

if 'slot != "set" || MadGodCanon.slot(command.itemId' in ENGINES.read_text(encoding="utf-8"):
    raise RuntimeError("MadGod still requires the synthetic set slot")

print("MadGod overwrite hotfix applied: normal Equip now replaces Kai's old weapon and armor while preserving the ring.")
