from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
STATE = CORE / "GameState.kt"
MADGOD = CORE / "MadGodCanon.kt"
INTENT = CORE / "IntentPipeline.kt"
ENGINES = CORE / "Engines.kt"
VAULT = CORE / "OmnivaultEngine.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"


def rep(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 anchor, found {n}")
    return text.replace(old, new, 1)


state = STATE.read_text(encoding="utf-8")
anchor = '  const val RING_NAME = "Omnivault Ring"\n'
block = anchor + '''\n  const val WW_MAGNUM_DMG = 500
  const val BLACKBLOOD_DF = 500
  const val BLACKBLOOD_STR = 100
  const val BLACKBLOOD_AGI = 100
  const val BLACKBLOOD_HP = 100
  const val BLACKBLOOD_ENE = 100
  const val BLACKBLOOD_CRIT = 100
'''
if "WW_MAGNUM_DMG = 500" not in state:
    state = rep(state, anchor, block, "baseline stats")
STATE.write_text(state, encoding="utf-8")

MADGOD.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_MAGNUM_ID = "madgod:magnum"
const val MADGOD_ARMOR_ID = "madgod:armor"

object MadGodCanon {
  const val CHEAT_CODE = "/madgod"
  const val MAGNUM_NAME = "MadGod Magnum"
  const val ARMOR_NAME = "MadGod Armor"
  const val RARITY = "UR+ UNIQUE"
  const val MULTIPLIER = 50
  const val SCALING_MODE = "BASELINE_ONCE"
  const val MAGNUM_RPM = 600

  const val WW_MAGNUM_DMG = KaiStartingEquipment.WW_MAGNUM_DMG
  const val BLACKBLOOD_DF = KaiStartingEquipment.BLACKBLOOD_DF
  const val BLACKBLOOD_STR = KaiStartingEquipment.BLACKBLOOD_STR
  const val BLACKBLOOD_AGI = KaiStartingEquipment.BLACKBLOOD_AGI
  const val BLACKBLOOD_HP = KaiStartingEquipment.BLACKBLOOD_HP
  const val BLACKBLOOD_ENE = KaiStartingEquipment.BLACKBLOOD_ENE
  const val BLACKBLOOD_CRIT = KaiStartingEquipment.BLACKBLOOD_CRIT

  const val MAGNUM_DMG = WW_MAGNUM_DMG * MULTIPLIER
  const val ARMOR_DF = BLACKBLOOD_DF * MULTIPLIER
  const val ARMOR_STR = BLACKBLOOD_STR * MULTIPLIER
  const val ARMOR_AGI = BLACKBLOOD_AGI * MULTIPLIER
  const val ARMOR_HP = BLACKBLOOD_HP * MULTIPLIER
  const val ARMOR_ENE = BLACKBLOOD_ENE * MULTIPLIER
  const val ARMOR_CRIT = BLACKBLOOD_CRIT * MULTIPLIER

  data class SpawnResult(val state: GameState, val applied: Boolean, val alreadySpawned: Boolean)

  fun matchesCheat(action: String) = action.trim().equals(CHEAT_CODE, true)
  fun isId(id: String?) = id == MADGOD_MAGNUM_ID || id == MADGOD_ARMOR_ID
  fun isItem(item: ItemStack?) = item != null && (isId(item.itemId) || item.metadata["madGod"].equals("true", true))
  fun isPermanentlyEquipped(state: GameState, actor: String, id: String) =
    actor == KAI_ID && isId(id) && state.equipment[KAI_ID]?.slots?.values?.contains(id) == true

  fun slotFor(id: String, name: String): String? {
    val key = "$id $name".lowercase()
    return when {
      id == MADGOD_MAGNUM_ID || key.contains("madgod magnum") -> "weapon"
      id == MADGOD_ARMOR_ID || key.contains("madgod armor") -> "armor"
      else -> null
    }
  }

  fun weapon() = ItemStack(MADGOD_MAGNUM_ID, MAGNUM_NAME, 1, "PERFECT", linkedMapOf(
    "category" to "weapon", "slot" to "weapon", "rarity" to RARITY, "unique" to "true",
    "madGod" to "true", "kaiOnly" to "true", "permanentAfterEquip" to "true",
    "omnivaultCopyable" to "false", "baseEquivalent" to KaiStartingEquipment.WEAPON_NAME,
    "baseDMG" to WW_MAGNUM_DMG.toString(), "multiplier" to MULTIPLIER.toString(),
    "scalingMode" to SCALING_MODE, "stackMultiplier" to "false", "userStatMultiplier" to "false",
    "DMG" to MAGNUM_DMG.toString(), "ammo" to "infinite", "ammoSource" to "Sparda Core",
    "fireModes" to "single,full_auto", "RPM" to MAGNUM_RPM.toString()
  ))

  fun armor() = ItemStack(MADGOD_ARMOR_ID, ARMOR_NAME, 1, "PERFECT", linkedMapOf(
    "category" to "armor", "slot" to "armor", "rarity" to RARITY, "unique" to "true",
    "madGod" to "true", "kaiOnly" to "true", "permanentAfterEquip" to "true",
    "omnivaultCopyable" to "false", "baseEquivalent" to KaiStartingEquipment.ARMOR_NAME,
    "baseDF" to BLACKBLOOD_DF.toString(), "baseSTR" to BLACKBLOOD_STR.toString(),
    "baseAGI" to BLACKBLOOD_AGI.toString(), "baseHP" to BLACKBLOOD_HP.toString(),
    "baseENE" to BLACKBLOOD_ENE.toString(), "baseCRIT" to BLACKBLOOD_CRIT.toString(),
    "multiplier" to MULTIPLIER.toString(), "scalingMode" to SCALING_MODE,
    "stackMultiplier" to "false", "userStatMultiplier" to "false",
    "DF" to ARMOR_DF.toString(), "STR" to ARMOR_STR.toString(), "AGI" to ARMOR_AGI.toString(),
    "HP" to ARMOR_HP.toString(), "ENE" to ARMOR_ENE.toString(), "CRIT" to ARMOR_CRIT.toString(),
    "functions" to "Blackblood Armor equivalent functions"
  ))

  fun canonicalize(item: ItemStack): ItemStack {
    val key = (item.itemId + " " + item.name).lowercase()
    return when {
      item.itemId == MADGOD_MAGNUM_ID || key.contains("madgod magnum") -> weapon().copy(quantity=item.quantity, condition=item.condition ?: "PERFECT")
      item.itemId == MADGOD_ARMOR_ID || key.contains("madgod armor") -> armor().copy(quantity=item.quantity, condition=item.condition ?: "PERFECT")
      else -> item
    }
  }

  fun spawn(state: GameState): SpawnResult {
    val ids = state.inventories.values.flatMap { it.items.keys }.toSet() +
      state.omnivault.storedItems.keys + state.equipment.values.flatMap { it.slots.values }
    val already = state.metadata["madGod.spawned"].equals("true", true) || MADGOD_MAGNUM_ID in ids || MADGOD_ARMOR_ID in ids
    if (already) return SpawnResult(
      if (state.metadata["madGod.spawned"].equals("true", true)) state else state.copy(metadata=state.metadata + ("madGod.spawned" to "true")),
      false, true
    )
    val inv = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val next = inv.copy(items=inv.items + mapOf(MADGOD_MAGNUM_ID to weapon(), MADGOD_ARMOR_ID to armor()))
    return SpawnResult(state.copy(
      inventories=state.inventories + (KAI_ID to next),
      metadata=state.metadata + mapOf("madGod.spawned" to "true", "madGod.spawnSource" to "cheat", "madGod.multiplierMode" to SCALING_MODE)
    ), true, false)
  }

  fun displayName(id: String) = when (id) {
    MADGOD_MAGNUM_ID -> MAGNUM_NAME
    MADGOD_ARMOR_ID -> ARMOR_NAME
    else -> KaiStartingEquipment.displayName(id)
  }

  private fun stats(id: String) = when (id) {
    KAI_WHITE_WRAITH_ID -> JSONObject().put("DMG", WW_MAGNUM_DMG)
    KAI_BLACKBLOOD_ARMOR_ID -> JSONObject().put("DF", BLACKBLOOD_DF).put("STR", BLACKBLOOD_STR)
      .put("AGI", BLACKBLOOD_AGI).put("HP", BLACKBLOOD_HP).put("ENE", BLACKBLOOD_ENE).put("CRIT", BLACKBLOOD_CRIT)
    MADGOD_MAGNUM_ID -> JSONObject().put("DMG", MAGNUM_DMG).put("RPM", MAGNUM_RPM).put("ammo", "infinite")
    MADGOD_ARMOR_ID -> JSONObject().put("DF", ARMOR_DF).put("STR", ARMOR_STR).put("AGI", ARMOR_AGI)
      .put("HP", ARMOR_HP).put("ENE", ARMOR_ENE).put("CRIT", ARMOR_CRIT)
    else -> JSONObject()
  }

  fun legacyEquipment(state: GameState) = JSONObject().apply {
    val slots = state.equipment[KAI_ID]?.slots.orEmpty()
    listOf("weapon", "armor", "ring").forEach { slot ->
      val id = slots[slot] ?: return@forEach
      put(slot, JSONObject().put("id", id).put("name", displayName(id) ?: id)
        .put("permanent", isId(id)).put("stats", stats(id))
        .put("multiplier", if (isId(id)) MULTIPLIER else 1)
        .put("scalingMode", if (isId(id)) SCALING_MODE else "BASE"))
    }
  }
}
''', encoding="utf-8")

intent = INTENT.read_text(encoding="utf-8")
intent = rep(intent,
  '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, "weapon") }\n',
  '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, equipmentSlot(it)) }\n',
  "equip slot")
intent = rep(intent,
  '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, "weapon") }\n',
  '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, equipmentSlot(it)) }\n',
  "unequip slot")
helper = '''  private fun equipmentSlot(item: Pair<String, String>): String =
    MadGodCanon.slotFor(item.first, item.second)
      ?: KaiStartingEquipment.slotFor(item.first, item.second)
      ?: if ((item.first + " " + item.second).contains("armor", true) || (item.first + " " + item.second).contains("giáp", true)) "armor" else "weapon"

'''
intent = rep(intent, '  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =\n', helper + '  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =\n', "slot helper")
INTENT.write_text(intent, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
engines = rep(engines,
  '    val item = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))\n',
  '    val item = MadGodCanon.canonicalize(ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata)))\n',
  "canonical item")
engines = rep(engines, '''      ItemCommand.Operation.DROP -> {
        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        changed(state.copy(inventories = state.inventories + (command.actorId to next)), "inventory_remove")
      }
''', '''      ItemCommand.Operation.DROP -> {
        if (MadGodCanon.isPermanentlyEquipped(state, command.actorId, command.itemId)) return invalid(state, "madgod_equipment_permanent")
        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        changed(state.copy(inventories = state.inventories + (command.actorId to next)), "inventory_remove")
      }
''', "drop lock")
engines = rep(engines, '''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
      }
''', '''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        val current = equipment.slots[slot]
        if (current != null && MadGodCanon.isId(current) && current != command.itemId) return invalid(state, "madgod_equipment_permanent")
        if (MadGodCanon.isId(command.itemId)) {
          if (command.actorId != KAI_ID) return invalid(state, "madgod_kai_only")
          if (MadGodCanon.slotFor(command.itemId, source.items[command.itemId]?.name.orEmpty()) != slot) return invalid(state, "madgod_equipment_slot_mismatch")
        }
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        if (MadGodCanon.isId(command.itemId)) return invalid(state, "madgod_equipment_permanent")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
      }
''', "permanent equip")
ENGINES.write_text(engines, encoding="utf-8")

vault = VAULT.read_text(encoding="utf-8")
vault = rep(vault,
  '    val source = state.inventories[c.actorId]?.items?.get(c.itemId) ?: state.omnivault.storedItems[c.itemId] ?: return invalid(state, "scan_source_missing")\n',
  '    val source = state.inventories[c.actorId]?.items?.get(c.itemId) ?: state.omnivault.storedItems[c.itemId] ?: return invalid(state, "scan_source_missing")\n    if (MadGodCanon.isItem(source)) return invalid(state, "madgod_omnivault_copy_forbidden")\n',
  "scan lock")
vault = rep(vault,
  '    val template = state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == c.itemId }?.templateItem ?: return invalid(state, "scan_template_missing")\n',
  '    val template = state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == c.itemId }?.templateItem ?: return invalid(state, "scan_template_missing")\n    if (MadGodCanon.isItem(template)) return invalid(state, "madgod_omnivault_copy_forbidden")\n',
  "copy lock")
VAULT.write_text(vault, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
facade = rep(facade, '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val turnId = nextTurnId(legacy, state)
''', '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (MadGodCanon.matchesCheat(action)) return applyMadGodCheat(legacy, state)
    val turnId = nextTurnId(legacy, state)
''', "cheat intercept")
facade = rep(facade, '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
''', '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (MadGodCanon.matchesCheat(action)) return actionStartResponse(true, null, null)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
''', "action bypass")
handler = r'''  private fun applyMadGodCheat(legacy: JSONObject, state: GameState): String {
    val spawn = MadGodCanon.spawn(state)
    repository.save(spawn.state)
    val result = syncLegacy(legacy, spawn.state, incrementTurn = false)
    val flags = result.optJSONObject("flags") ?: JSONObject().also { result.put("flags", it) }
    val madGod = flags.optJSONObject("madGod") ?: JSONObject().also { flags.put("madGod", it) }
    madGod.put("spawned", true).put("spawnSource", "cheat")
      .put("multiplier", MadGodCanon.MULTIPLIER).put("scalingMode", MadGodCanon.SCALING_MODE)
    val reply = if (spawn.alreadySpawned)
      "MadGod Set đã tồn tại trong campaign này; /madgod không tạo bản sao thứ hai."
    else "MadGod Armor và MadGod Magnum đã xuất hiện trong Inventory. Sau khi trang bị, chúng khóa vĩnh viễn vào slot."
    appendLog(result, MadGodCanon.CHEAT_CODE, reply)
    logger.log(PipelineLogEvent("CHEAT_COMMIT", details=mapOf("command" to "madgod", "result" to if (spawn.alreadySpawned) "already_spawned" else "spawned")))
    return response(true, result, null, "cheat_committed", reply)
  }

'''
facade = rep(facade, '  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n', handler + '  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n', "cheat handler")
facade = rep(facade,
  '    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))\n',
  '    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))\n    output.put("equipment", MadGodCanon.legacyEquipment(state))\n',
  "equipment projection")
facade = rep(facade,
  '      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."\n',
  '      "madgod_equipment_permanent" -> "MadGod đã khóa vĩnh viễn vào slot sau khi trang bị; không thể tháo, đổi, bỏ hoặc chuyển nó."\n      "madgod_omnivault_copy_forbidden" -> "Omnivault không thể quét hoặc sao chép MadGod Armor / MadGod Magnum."\n      "madgod_kai_only" -> "MadGod Set chỉ tương thích với Kai Akechi."\n      "madgod_equipment_slot_mismatch" -> "MadGod Magnum chỉ vào slot vũ khí và MadGod Armor chỉ vào slot giáp."\n      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."\n',
  "validation replies")
FACADE.write_text(facade, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = rep(main, ".snapshot-placeholder{display:none}",
  ".snapshot-placeholder{display:none}.snapshot .snapshot-equipment-badge{position:absolute;right:8px;top:8px;z-index:4;max-width:58%;padding:6px 8px;border:1px solid rgba(218,180,88,.62);border-radius:8px;background:rgba(7,9,11,.78);color:#f2dfad;font-size:10px;line-height:1.35;letter-spacing:.04em;pointer-events:none;text-align:right}.snapshot .snapshot-equipment-badge b{display:block;color:#fff4cf;font-size:11px}",
  "snapshot badge css")
default_overlay = "kai_snapshot_overlay.png" if "kai_snapshot_overlay.png" in main else "kai_snapshot_overlay.webp"
helpers = (
  "function equippedItem(slot){try{return state&&state.equipment&&state.equipment[slot]?state.equipment[slot]:null;}catch(e){return null;}}"
  "function madGodEquipped(slot){var x=equippedItem(slot);return !!(x&&String(x.id||'').indexOf('madgod:')===0);}"
  "function kaiOverlaySource(){var a=madGodEquipped('armor'),w=madGodEquipped('weapon');if(a&&w)return 'kai_snapshot_overlay_madgod.webp';if(a)return 'kai_snapshot_overlay_madgod_armor.webp';if(w)return 'kai_snapshot_overlay_madgod_magnum.webp';return '" + default_overlay + "';}"
  "function appendEquipmentBadge(box){var a=equippedItem('armor'),w=equippedItem('weapon');if(!madGodEquipped('armor')&&!madGodEquipped('weapon'))return;var d=document.createElement('div');d.className='snapshot-equipment-badge';if(a&&madGodEquipped('armor')){var b=document.createElement('b');b.textContent=String(a.name||'MadGod Armor');d.appendChild(b);}if(w&&madGodEquipped('weapon')){var q=document.createElement('span');q.textContent=String(w.name||'MadGod Magnum');d.appendChild(q);}box.appendChild(d);}"
)
main = rep(main, "function cachedSnapshot(){", helpers + "function cachedSnapshot(){", "snapshot helpers")
for old in [
  "kai.src='kai_snapshot_overlay.png';kai.alt='Kai Akechi';box.appendChild(kai);",
  "kai.src='kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);",
  "kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);",
]:
    if old in main:
        new = "kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='" + default_overlay + "';};kai.alt='Kai Akechi';box.appendChild(kai);appendEquipmentBadge(box);"
        main = main.replace(old, new, 1)
        break
else:
    raise RuntimeError("snapshot Kai overlay anchor missing")
prompt = '      "Player: " + clipped(state.optJSONObject("player"), 1800) + "\\n" +\n'
if prompt in main:
    main = rep(main, prompt, prompt + '      "Equipment: " + clipped(state.optJSONObject("equipment"), 2200) + "\\n" +\n', "snapshot prompt")
MAIN.write_text(main, encoding="utf-8")

TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text(r'''package com.rabpit.backroom.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MadGodEquipmentTest {
  @Test fun baselineScalingIsExactlyOnce() {
    assertEquals(500, MadGodCanon.WW_MAGNUM_DMG)
    assertEquals(25_000, MadGodCanon.MAGNUM_DMG)
    assertEquals(25_000, MadGodCanon.ARMOR_DF)
    assertEquals(5_000, MadGodCanon.ARMOR_STR)
    assertEquals(5_000, MadGodCanon.ARMOR_AGI)
    assertEquals(5_000, MadGodCanon.ARMOR_HP)
    assertEquals(5_000, MadGodCanon.ARMOR_ENE)
    assertEquals(5_000, MadGodCanon.ARMOR_CRIT)
    assertEquals("BASELINE_ONCE", MadGodCanon.SCALING_MODE)
    assertEquals("false", MadGodCanon.weapon().metadata["userStatMultiplier"])
  }

  @Test fun cheatIsUniqueAndWeaponContractIsLocked() {
    val one = MadGodCanon.spawn(GameState.initial())
    assertTrue(one.applied)
    val two = MadGodCanon.spawn(one.state)
    assertFalse(two.applied)
    assertTrue(two.alreadySpawned)
    assertEquals(1, two.state.inventories.getValue(KAI_ID).items.getValue(MADGOD_MAGNUM_ID).quantity)
    assertEquals("infinite", MadGodCanon.weapon().metadata["ammo"])
    assertEquals("Sparda Core", MadGodCanon.weapon().metadata["ammoSource"])
    assertEquals("600", MadGodCanon.weapon().metadata["RPM"])
  }

  @Test fun equippedMadGodCannotBeRemovedOrReplaced() {
    val s = MadGodCanon.spawn(GameState.initial()).state
    val equip = InventoryEngine.execute(s, ItemCommand("e", "TURN_1", KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP, itemId=MADGOD_ARMOR_ID, itemName=MadGodCanon.ARMOR_NAME, slot="armor"))
    assertTrue(equip.applied)
    val remove = InventoryEngine.execute(equip.state, ItemCommand("u", "TURN_1", KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP, itemId=MADGOD_ARMOR_ID, itemName=MadGodCanon.ARMOR_NAME, slot="armor"))
    assertFalse(remove.applied)
    assertEquals("madgod_equipment_permanent", remove.validation.reason)
  }

  @Test fun omnivaultCannotScanMadGod() {
    val s = MadGodCanon.spawn(GameState.initial()).state
    val r = OmnivaultEngine.execute(s, OmnivaultCommand("s", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.SCAN, itemId=MADGOD_MAGNUM_ID, itemName=MadGodCanon.MAGNUM_NAME, timestampEpochMs=1L))
    assertFalse(r.applied)
    assertEquals("madgod_omnivault_copy_forbidden", r.validation.reason)
  }
}
''', encoding="utf-8")

combined = "\n".join(p.read_text(encoding="utf-8") for p in [STATE, MADGOD, INTENT, ENGINES, VAULT, FACADE, MAIN, TEST])
for marker in [
  'CHEAT_CODE = "/madgod"', 'WW_MAGNUM_DMG = 500', 'MAGNUM_DMG = WW_MAGNUM_DMG * MULTIPLIER',
  'ARMOR_DF = BLACKBLOOD_DF * MULTIPLIER', '"DMG" to MAGNUM_DMG.toString()', '"RPM" to MAGNUM_RPM.toString()',
  '"ammoSource" to "Sparda Core"', '"userStatMultiplier" to "false"', 'madgod_equipment_permanent',
  'madgod_omnivault_copy_forbidden', 'output.put("equipment", MadGodCanon.legacyEquipment(state))',
  'kaiOverlaySource()', 'appendEquipmentBadge(box)', 'baselineScalingIsExactlyOnce'
]:
    if marker not in combined:
        raise RuntimeError("missing MadGod contract: " + marker)

print("MadGod R2 applied: cheat, one-pass x50 stats, infinite Sparda ammo 600 RPM, permanent equip, Omnivault copy lock, Snapshot overlay routing.")
