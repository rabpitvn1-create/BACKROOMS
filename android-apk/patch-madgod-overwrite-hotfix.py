from pathlib import Path

ROOT = Path(__file__).resolve().parent
ENGINES = ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"

engines = ENGINES.read_text(encoding="utf-8")

old = '''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        if (MadGodCanon.isId(command.itemId)) {
          if (command.actorId != KAI_ID || slot != "set" || MadGodCanon.slot(command.itemId, source.items[command.itemId]?.name.orEmpty()) != "set") return invalid(state,"madgod_equipment_slot_mismatch")
          val weaponCurrent = equipment.slots["weapon"]
          val armorCurrent = equipment.slots["armor"]
          if ((MadGodCanon.isId(weaponCurrent) && weaponCurrent != command.itemId) || (MadGodCanon.isId(armorCurrent) && armorCurrent != command.itemId)) return invalid(state,"madgod_equipment_permanent")
          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = boundSlots))), "item_equipped")
        } else {
          val current = equipment.slots[slot]
          if (MadGodCanon.isId(current)) return invalid(state,"madgod_equipment_permanent")
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
        }
      }
'''

new = '''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        if (MadGodCanon.isId(command.itemId)) {
          if (command.actorId != KAI_ID) return invalid(state,"madgod_equipment_slot_mismatch")
          // MadGod is a full override set. It does not negotiate with Kai's current weapon/armor
          // and it does not depend on a synthetic "set" slot surviving the text parser.
          // Equip always replaces both combat slots atomically while preserving unrelated slots
          // such as the Omnivault Ring.
          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = boundSlots))), "item_equipped")
        } else {
          val slot = command.slot ?: return invalid(state, "equipment_slot_required")
          val current = equipment.slots[slot]
          if (MadGodCanon.isId(current)) return invalid(state,"madgod_equipment_permanent")
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
        }
      }
'''

if new not in engines:
    if old not in engines:
        raise RuntimeError("MadGod overwrite equip anchor missing")
    engines = engines.replace(old, new, 1)

ENGINES.write_text(engines, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
needle = '''  @Test fun omnivaultCannotCopyTheSet() {
'''
extra = r'''  @Test fun equipOverwritesKaisExistingWeaponAndArmorEvenWithoutSyntheticSetSlot() {
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

  @Test fun typedTrangBiMadGodSetResolvesAndOverwritesExistingGear() {
    val spawned = MadGodCanon.spawn(GameState.initial()).state
    val aliases = spawned.inventories.values
      .flatMap { it.items.values }
      .associate { it.name.lowercase() to it.itemId }
    val context = GameContext(spawned, itemAliases = aliases)
    val candidate = RuleIntentInterpreter().interpretSync("trang bị MadGod Set", context).candidates.single()
    assertEquals(GameIntent.EQUIP_ITEM, candidate.intent)
    val command = CommandResolver().resolve(candidate, 0, "TURN_1", context) as ItemCommand
    val pending = TurnCoordinator.createPending(spawned, "TURN_1", "trang bị MadGod Set")
    val committed = TurnCoordinator.commit(pending.state, listOf(command))
    assertEquals(null, committed.error)
    val after = committed.state.equipment.getValue(KAI_ID).slots
    assertEquals(MADGOD_SET_ID, after["weapon"])
    assertEquals(MADGOD_SET_ID, after["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, after["ring"])
  }

'''
if "typedTrangBiMadGodSetResolvesAndOverwritesExistingGear" not in test:
    if needle not in test:
        raise RuntimeError("MadGod overwrite test insertion anchor missing")
    test = test.replace(needle, extra + needle, 1)

TEST.write_text(test, encoding="utf-8")

combined = ENGINES.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)',
    'equipOverwritesKaisExistingWeaponAndArmorEvenWithoutSyntheticSetSlot',
    'typedTrangBiMadGodSetResolvesAndOverwritesExistingGear',
    'assertFalse(after.values.contains(KAI_WHITE_WRAITH_ID))',
    'assertFalse(after.values.contains(KAI_BLACKBLOOD_ARMOR_ID))',
):
    if marker not in combined:
        raise RuntimeError("MadGod overwrite hotfix contract missing: " + marker)

print("MadGod overwrite hotfix applied: equipping the set forcibly replaces Kai's old weapon and armor while preserving the ring.")
