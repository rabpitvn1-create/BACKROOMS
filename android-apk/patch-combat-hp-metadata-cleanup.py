from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
DETAIL = CORE / "CharacterDetailProjection.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
INVENTORY_POLICY = CORE / "InventoryPolicy.kt"
EQUIPMENT_SYSTEM = CORE / "CharacterEquipmentSystem.kt"
CODEC_TEST = TESTS / "GameStateCodecTest.kt"
COMBAT_TEST = TESTS / "CombatRuntimeTest.kt"
MADGOD_TEST = TESTS / "MadGodEquipmentTest.kt"

text = COMBAT.read_text(encoding="utf-8")

# CharacterVitalState is the sole source of truth for Kai HP. Remove every residual reference
# to the retired combat.playerHp / combat.playerMaxHp constants after the larger status patch runs.
text = text.replace('  private const val PLAYER_HP = "combat.playerHp"\n', '')
text = text.replace('  private const val PLAYER_MAX_HP = "combat.playerMaxHp"\n', '')
text = text.replace(
    '    val playerMax = state.metadata[PLAYER_MAX_HP]?.toIntOrNull()?.coerceIn(1, 999) ?: 100\n'
    '    val playerHp = state.metadata[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax\n',
    '    val effective = CharacterStatEngine.effective(state, KAI_ID)\n'
    '    val playerMax = effective.maxHp\n'
    '    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax\n'
)
text = text.replace('    metadata[PLAYER_HP] = c.playerHp.toString()\n', '')
text = text.replace('    metadata[PLAYER_MAX_HP] = c.playerMaxHp.toString()\n', '')
text = text.replace(
    '    val playerMax = m[PLAYER_MAX_HP]?.toIntOrNull()?.coerceAtLeast(1) ?: 100\n'
    '    return Snapshot(\n',
    '    val playerMax = CharacterStatEngine.effective(state, KAI_ID).maxHp\n'
    '    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax\n'
    '    return Snapshot(\n'
)
text = text.replace(
    '      playerHp = m[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax,\n',
    '      playerHp = playerHp,\n'
)
legacy_clear = '''  private fun clearCombatOnly(state: GameState): GameState {
    val preservedHp = state.metadata[PLAYER_HP]
    val preservedMax = state.metadata[PLAYER_MAX_HP]
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    if (preservedHp != null) metadata[PLAYER_HP] = preservedHp
    if (preservedMax != null) metadata[PLAYER_MAX_HP] = preservedMax
    return state.copy(metadata = metadata)
  }
'''
modern_clear = '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }
'''
text = text.replace(legacy_clear, modern_clear)
if "PLAYER_HP" in text or "PLAYER_MAX_HP" in text:
    remaining = [line.strip() for line in text.splitlines() if "PLAYER_HP" in line or "PLAYER_MAX_HP" in line]
    raise RuntimeError("Legacy combat HP metadata reference remains: " + " | ".join(remaining))
if "CharacterStatEngine.effective(state, KAI_ID).maxHp" not in text:
    raise RuntimeError("CombatRuntime no longer reads effective Kai Max HP")
if "CharacterStatEngine.setCurrentHp" not in text:
    raise RuntimeError("CombatRuntime no longer writes authoritative Kai HP")
COMBAT.write_text(text, encoding="utf-8")

# CharacterDetailProjection remains source-compatible with older named-constructor call sites.
detail = DETAIL.read_text(encoding="utf-8")
replacements = {
    '  val role: String,\n': '  val role: String = "UNSPECIFIED",\n',
    '  val energyDisplay: String,\n': '  val energyDisplay: String = "N/A",\n',
    '  val regenPerCompletedTurn: Int,\n': '  val regenPerCompletedTurn: Int = 0,\n',
    '  val condition: CharacterCondition,\n': '  val condition: CharacterCondition = CharacterCondition.HEALTHY,\n',
    '  val str: StatLineProjection,\n': '  val str: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val df: StatLineProjection,\n': '  val df: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val agi: StatLineProjection,\n': '  val agi: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val crit: StatLineProjection,\n': '  val crit: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val inventoryDetails: List<ItemDetailProjection>,\n': '  val inventoryDetails: List<ItemDetailProjection> = emptyList(),\n',
    '  val equipmentDetails: List<ItemDetailProjection>,\n': '  val equipmentDetails: List<ItemDetailProjection> = emptyList(),\n',
}
for old, new in replacements.items():
    if new not in detail:
        if old not in detail:
            raise RuntimeError("CharacterDetailProjection compatibility anchor missing: " + old.strip())
        detail = detail.replace(old, new, 1)
DETAIL.write_text(detail, encoding="utf-8")

# Older projection fixtures only populate `inventory`, while the redesigned projector populates
# `inventoryDetails`. Preserve the metadata-safe old JSON shape when detailed data is absent.
detail_json = DETAIL_JSON.read_text(encoding="utf-8")
old_inventory_json = '    put("inventory", JSONArray().apply { c.inventoryDetails.forEach { put(item(it)) } })\n'
new_inventory_json = '''    put("inventory", JSONArray().apply {
      if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails.forEach { put(item(it)) }
      else c.inventory.forEach { stack -> put(JSONObject().apply {
        put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
        stack.condition?.let { put("state", it) }; put("contentState", stack.contentState.name)
      }) }
    })
'''
if new_inventory_json not in detail_json:
    if old_inventory_json not in detail_json:
        raise RuntimeError("CharacterDetailJson inventory compatibility anchor missing")
    detail_json = detail_json.replace(old_inventory_json, new_inventory_json, 1)
DETAIL_JSON.write_text(detail_json, encoding="utf-8")

# Equipped clothing is owned by An Nhiên's Inventory but does not consume her two FOOD carry slots.
policy = INVENTORY_POLICY.read_text(encoding="utf-8")
old_capacity = '    if (old == null && inventory.items.size >= profile.maxTypes) return "inventory_slot_limit"\n'
new_capacity = '''    val carriedTypes = inventory.items.values.count { EquipmentCatalog.definition(it.itemId) == null }
    val addingEquipment = EquipmentCatalog.definition(normalized.itemId) != null
    if (old == null && !addingEquipment && carriedTypes >= profile.maxTypes) return "inventory_slot_limit"
'''
if new_capacity not in policy:
    if old_capacity not in policy:
        raise RuntimeError("InventoryPolicy capacity anchor missing")
    policy = policy.replace(old_capacity, new_capacity, 1)
INVENTORY_POLICY.write_text(policy, encoding="utf-8")

# An Nhiên's outfit/footwear remain fixed even though all Equipment now uses the shared engine.
equipment_system = EQUIPMENT_SYSTEM.read_text(encoding="utf-8")
equip_anchor = '''  fun equip(state: GameState, command: ItemCommand): ExecutionResult {
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
'''
equip_locked = '''  fun equip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
'''
if equip_locked not in equipment_system:
    if equip_anchor not in equipment_system:
        raise RuntimeError("EquipmentEngine equip anchor missing")
    equipment_system = equipment_system.replace(equip_anchor, equip_locked, 1)
unequip_anchor = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.itemId == MADGOD_SET_ID) return invalid(state, "madgod_equipment_permanent")
'''
unequip_locked = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    if (command.itemId == MADGOD_SET_ID) return invalid(state, "madgod_equipment_permanent")
'''
if unequip_locked not in equipment_system:
    if unequip_anchor not in equipment_system:
        raise RuntimeError("EquipmentEngine unequip anchor missing")
    equipment_system = equipment_system.replace(unequip_anchor, unequip_locked, 1)
EQUIPMENT_SYSTEM.write_text(equipment_system, encoding="utf-8")

# Migrate pre-redesign regression expectations to the new Inventory-owned Equipment architecture.
codec_test = CODEC_TEST.read_text(encoding="utf-8")
codec_test = codec_test.replace(
    '    val canonicalState = SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))\n',
    '    val canonicalState = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state)))\n'
)
codec_test = codec_test.replace(
'''  @Test fun freshStateKeepsSignatureGearOnlyInEquipment() {
    val state = GameState.initial()
    assertTrue(state.inventories.getValue(KAI_ID).items.isEmpty())
    assertEquals(KAI_WHITE_WRAITH_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, state.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, state.equipment.getValue(KAI_ID).slots["ring"])
  }
''',
'''  @Test fun freshStateInventoryOwnsSignatureGearReferencedByEquipment() {
    val state = GameState.initial()
    val owned = state.inventories.getValue(KAI_ID).items
    state.equipment.getValue(KAI_ID).slots.values.distinct().forEach { assertTrue(it in owned) }
    assertEquals(KAI_WHITE_WRAITH_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, state.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, state.equipment.getValue(KAI_ID).slots["ring"])
  }
''')
codec_test = codec_test.replace(
    '    assertEquals(1, migrated.inventories.getValue(KAI_ID).items.size)\n    assertEquals(2, migrated.inventories.getValue(KAI_ID).items.values.single().quantity)\n',
'''    val migratedItems = migrated.inventories.getValue(KAI_ID).items
    val normalItems = migratedItems.values.filter { EquipmentCatalog.definition(it.itemId) == null }
    assertEquals(1, normalItems.size)
    assertEquals(2, normalItems.single().quantity)
    migrated.equipment.getValue(KAI_ID).slots.values.distinct().forEach { assertTrue(it in migratedItems) }
''')
codec_test = codec_test.replace(
    '    assertEquals(setOf("rope"), migrated.inventories.getValue(KAI_ID).items.keys)\n',
'''    val migratedItems = migrated.inventories.getValue(KAI_ID).items
    assertEquals(setOf("rope"), migratedItems.filterValues { EquipmentCatalog.definition(it.itemId) == null }.keys)
    migrated.equipment.getValue(KAI_ID).slots.values.distinct().forEach { assertTrue(it in migratedItems) }
''')
CODEC_TEST.write_text(codec_test, encoding="utf-8")

combat_test = COMBAT_TEST.read_text(encoding="utf-8")
combat_test = combat_test.replace(
    '    assertEquals(100, combat.playerMaxHp)\n    assertEquals(100, combat.playerHp)\n',
'''    val expectedMaxHp = CharacterStatEngine.effective(GameState.initial(), KAI_ID).maxHp
    assertEquals(140, expectedMaxHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedMaxHp, combat.playerHp)
''')
COMBAT_TEST.write_text(combat_test, encoding="utf-8")

# Historical MadGod tests encoded the retired x50 implementation and removal-from-Inventory model.
# Replace them with normalized, single-Item, multi-slot tests matching the current specification.
if MADGOD_TEST.exists():
    MADGOD_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class MadGodEquipmentTest {
  private fun withMadGod(): GameState {
    val state = GameState.initial()
    val inv = state.inventories.getValue(KAI_ID)
    return state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID)))))
  }

  @Test fun oneSetContainsBothNormalizedComponents() {
    val def = EquipmentCatalog.definition(MADGOD_SET_ID)!!
    assertEquals(ItemClassification.SPECIAL_CHEAT, def.classification)
    assertEquals(setOf(EquipmentSlot.WEAPON, EquipmentSlot.ARMOR), def.occupiesSlots)
    assertEquals(55, def.weapon!!.dmg)
    assertEquals(50, def.bonuses.hp)
    assertEquals(15, def.bonuses.str)
    assertEquals(30, def.bonuses.df)
    assertEquals(12, def.bonuses.agi)
    assertEquals(12, def.bonuses.crit)
    assertEquals(1, MadGodCanon.MULTIPLIER)
    assertEquals("GAMEPLAY_NORMALIZED", MadGodCanon.SCALING_MODE)
  }

  @Test fun equipOverwritesWeaponAndArmorButKeepsTheSingleOwnedItem() {
    val state = withMadGod()
    val result = EquipmentEngine.equip(state, ItemCommand(
      "madgod-equip", state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.EQUIP, itemId = MADGOD_SET_ID, itemName = "MadGod Set", slot = "weapon"
    ))
    assertTrue(result.applied)
    val slots = result.state.equipment.getValue(KAI_ID).slots
    assertEquals(MADGOD_SET_ID, slots["weapon"])
    assertEquals(MADGOD_SET_ID, slots["armor"])
    assertEquals(KAI_DEMON_JAW_MASK_ID, slots["head"])
    assertEquals(KAI_TALON_GAUNTLETS_ID, slots["gauntlets"])
    assertEquals(KAI_PHANTOM_GREAVES_ID, slots["greaves"])
    assertEquals(KAI_OMNIVAULT_RING_ID, slots["ring"])
    assertTrue(result.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    val effective = CharacterStatEngine.effective(result.state, KAI_ID)
    assertEquals(165, effective.maxHp)
    assertEquals(114, effective.str)
    assertEquals(121, effective.df)
    assertEquals(118, effective.agi)
    assertEquals(113, effective.crit)
  }

  @Test fun equippedMadGodIsPermanentAndCannotBeDuplicatedByBonusCounting() {
    val state = withMadGod()
    val equipped = EquipmentEngine.equip(state, ItemCommand(
      "madgod-equip", state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.EQUIP, itemId = MADGOD_SET_ID, itemName = "MadGod Set", slot = "weapon"
    )).state
    assertEquals(50, CharacterStatEngine.effective(equipped, KAI_ID).equipmentHp - 15) // canonical head/gauntlets/greaves add 15 HP
    val unequip = EquipmentEngine.unequip(equipped, ItemCommand(
      "madgod-unequip", equipped.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.UNEQUIP, itemId = MADGOD_SET_ID, itemName = "MadGod Set", slot = "weapon"
    ))
    assertFalse(unequip.applied)
    assertEquals("madgod_equipment_permanent", unequip.validation.reason)
  }
}
''', encoding="utf-8")

print("Combat HP cleanup, UI compatibility, An Nhien equipment rules, and redesigned regression expectations applied.")
