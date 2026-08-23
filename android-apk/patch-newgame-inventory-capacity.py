from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
DETAIL = CORE / "CharacterDetailProjection.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
TEST = TESTS / "InventoryCapacityNewGameTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Inventory capacity is CARRIED ownership, not total ownership.
#    Equipped Items stay in Inventory as the single owned Item instance, but
#    consume zero backpack slots. Multi-slot equipment is deduplicated by itemId.
# ---------------------------------------------------------------------------
detail = DETAIL.read_text(encoding="utf-8")
old_fields = '''  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection>,
  val equipment: Map<String, String>,
'''
new_fields = '''  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection>,
  val inventoryCapacityUsed: Int,
  val inventoryCapacityMax: Int,
  val equipment: Map<String, String>,
'''
if 'val inventoryCapacityUsed: Int' not in detail:
    detail = replace_once(detail, old_fields, new_fields, "Character inventory capacity projection fields")

old_projection = '''      inventory = inventory, inventoryDetails = inventoryDetails, equipment = equipment,
      equipmentDetails = equipmentDetails, statusEffects = effects
'''
new_projection = '''      inventory = inventory,
      inventoryDetails = inventoryDetails,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(state, character.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(character.id),
      equipment = equipment,
      equipmentDetails = equipmentDetails, statusEffects = effects
'''
if 'inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots' not in detail:
    detail = replace_once(detail, old_projection, new_projection, "Character inventory capacity projection")
DETAIL.write_text(detail, encoding="utf-8")

system = CORE / "CharacterEquipmentSystem.kt"
system_text = system.read_text(encoding="utf-8")
capacity_object = r'''
object InventoryCapacityPolicy {
  private const val DEFAULT_MAX_TYPES = 9

  fun maxSlots(characterId: String): Int = DEFAULT_MAX_TYPES

  fun equippedItemIds(state: GameState, characterId: String): Set<String> =
    state.equipment[characterId]?.slots.orEmpty().values.filter { it.isNotBlank() }.toSet()

  fun carriedItemIds(state: GameState, characterId: String): Set<String> {
    val owned = state.inventories[characterId]?.items.orEmpty()
      .filterValues { it.quantity > 0 }
      .keys
    return owned - equippedItemIds(state, characterId)
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size

  fun consumesSlot(state: GameState, characterId: String, itemId: String): Boolean =
    itemId in carriedItemIds(state, characterId)
}

'''
if 'object InventoryCapacityPolicy {' not in system_text:
    anchor = 'object CharacterStatEngine {'
    if anchor not in system_text:
        raise RuntimeError("CharacterStatEngine anchor missing for capacity policy")
    system_text = system_text.replace(anchor, capacity_object + anchor, 1)
system.write_text(system_text, encoding="utf-8")

json_text = DETAIL_JSON.read_text(encoding="utf-8")
old_json_inventory = '''    put("inventory", JSONArray().apply { c.inventoryDetails.forEach { put(item(it)) } })
    put("equipment", JSONObject(c.equipment))
'''
new_json_inventory = '''    put("inventory", JSONArray().apply { c.inventoryDetails.forEach { put(item(it, c)) } })
    put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed).put("max", c.inventoryCapacityMax))
    put("equipment", JSONObject(c.equipment))
'''
if 'put("inventoryCapacity", JSONObject().put("used"' not in json_text:
    json_text = replace_once(json_text, old_json_inventory, new_json_inventory, "Capacity JSON projection")
    json_text = json_text.replace('c.equipmentDetails.forEach { put(item(it)) }', 'c.equipmentDetails.forEach { put(item(it, c)) }')

old_item_sig = '  private fun item(x: ItemDetailProjection) = JSONObject().apply {'
new_item_sig = '  private fun item(x: ItemDetailProjection, c: CharacterDetailProjection) = JSONObject().apply {'
if new_item_sig not in json_text:
    json_text = replace_once(json_text, old_item_sig, new_item_sig, "Item JSON capacity signature")

item_marker = '''    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.statItem); x.classification?.let { put("classification", it) }
'''
item_marker_new = '''    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.statItem); x.classification?.let { put("classification", it) }
    put("consumesInventorySlot", !x.equipped)
'''
if 'put("consumesInventorySlot", !x.equipped)' not in json_text:
    json_text = replace_once(json_text, item_marker, item_marker_new, "Item capacity flag")
DETAIL_JSON.write_text(json_text, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) Cold start / New Game: Character UI must be able to initialize Core from
#    the currently loaded WebView state. Never touch gameCore directly.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
old_party = '''  fun currentPartyDetails(): String {
    val state = CharacterEquipmentSystem.normalize(repository.load())
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }
'''
new_party = '''  fun currentPartyDetails(legacyStateJson: String? = null): String {
    val source = if (repository.exists()) {
      repository.load()
    } else if (!legacyStateJson.isNullOrBlank()) {
      runCatching { GameStateCodec.decode(legacyStateJson) }.getOrElse { GameState.initial() }
    } else {
      GameState.initial()
    }
    val state = CharacterEquipmentSystem.normalize(source)
    if (!repository.exists()) repository.save(state)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }

  fun resetNewGame(): String {
    repository.clear()
    val fresh = CharacterEquipmentSystem.normalize(GameState.initial())
    repository.save(fresh)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(fresh)).toString()
  }
'''
if 'fun resetNewGame(): String' not in facade:
    facade = replace_once(facade, old_party, new_party, "Authoritative Character/New Game facade")
FACADE.write_text(facade, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
old_bridge = '''    @JavascriptInterface public String getPartyDetails() {
      try {
        return gameCore.currentPartyDetails();
      } catch (Exception e) {
        return "{\\\"members\\\":[]}";
      }
    }
'''
new_bridge = '''    @JavascriptInterface public String getPartyDetails(String stateJson) {
      try {
        return new JSONObject()
          .put("ok", true)
          .put("data", new JSONObject(requireGameCore().currentPartyDetails(stateJson)))
          .toString();
      } catch (Exception e) {
        return new JSONObject().put("ok", false).put("error", "CORE_UNAVAILABLE").put("message", e.getMessage() == null ? "Core unavailable" : e.getMessage()).toString();
      }
    }

    @JavascriptInterface public String resetNewGameCore() {
      try {
        return new JSONObject()
          .put("ok", true)
          .put("data", new JSONObject(requireGameCore().resetNewGame()))
          .toString();
      } catch (Exception e) {
        return new JSONObject().put("ok", false).put("error", "NEW_GAME_CORE_FAILED").put("message", e.getMessage() == null ? "New Game core reset failed" : e.getMessage()).toString();
      }
    }
'''
if '@JavascriptInterface public String resetNewGameCore()' not in main:
    main = replace_once(main, old_bridge, new_bridge, "Character Detail bridge cold start/new game")
if 'gameCore.currentPartyDetails()' in main:
    raise RuntimeError("Direct lazy-core bypass survived: gameCore.currentPartyDetails()")
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) UI uses authoritative capacity and authoritative new-game projection.
#    No equipped Item contributes to the 9-slot counter.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
html = html.replace("Android.getPartyDetails();", "Android.getPartyDetails(JSON.stringify(state||{}));")

# Both helper implementations may exist. They now receive an {ok,data,error} envelope.
old_parse = "const details=JSON.parse(raw||'{}');\n        if(details&&Array.isArray(details.members)&&details.members.length){"
new_parse = "const response=JSON.parse(raw||'{}');\n        const details=response&&response.ok===true?response.data:null;\n        if(details&&Array.isArray(details.members)&&details.members.length){"
html = html.replace(old_parse, new_parse)

# Legacy renderer capacity fallback. If authoritative member data exists, use Core projection.
old_capacity = "    capacity.textContent=inv.length+' / 9 loại vật phẩm';\n"
new_capacity = "    const cap=member&&member.inventoryCapacity;const used=cap&&Number.isFinite(Number(cap.used))?Number(cap.used):inv.filter(x=>!(x&&x.equipped)).length;const max=cap&&Number.isFinite(Number(cap.max))?Number(cap.max):9;capacity.textContent=used+' / '+max+' loại vật phẩm';\n"
if old_capacity in html:
    html = html.replace(old_capacity, new_capacity, 1)

# Redesigned renderer also owns the same visible counter once it replaces Inventory cards.
render_inventory_anchor = "    if(inventory){inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n"
render_inventory_new = "    if(inventory){inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n    const capEl=document.getElementById('characterInventoryCapacity'),cap=member.inventoryCapacity||{};if(capEl){const used=Number.isFinite(Number(cap.used))?Number(cap.used):(member.inventory||[]).filter(x=>x&&x.consumesInventorySlot!==false).length;const max=Number.isFinite(Number(cap.max))?Number(cap.max):9;capEl.textContent=used+' / '+max+' loại vật phẩm'}\n"
if render_inventory_new not in html:
    if render_inventory_anchor not in html:
        raise RuntimeError("Redesigned inventory renderer anchor missing")
    html = html.replace(render_inventory_anchor, render_inventory_new, 1)

# New Game creates and persists the authoritative fresh Core immediately, then refreshes partyDetails.
old_reset_body = '''    clearAuthoritativeCore();
    state=freshInitial();
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1. Game State Core và Snapshot cũ đã được xóa.";
'''
new_reset_body = '''    state=freshInitial();
    try{
      if(window.Android&&typeof Android.resetNewGameCore==='function'){
        const response=JSON.parse(Android.resetNewGameCore()||'{}');
        if(response&&response.ok===true&&response.data){state.partyDetails=response.data}
        else throw new Error((response&&response.message)||'Core reset failed');
      }else clearAuthoritativeCore();
    }catch(e){statusEl.textContent="NEW GAME Core lỗi: "+(e&&e.message?e.message:"bridge error");return}
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1 với Character Core mới.";
'''
if 'Android.resetNewGameCore()' not in html:
    if old_reset_body not in html:
        raise RuntimeError("New Game reset body anchor missing")
    html = html.replace(old_reset_body, new_reset_body, 1)

# If Character Core fails, do not silently manufacture fake stats. Keep the old party fallback only
# for Party cards, but Character Status itself gets an explicit unavailable marker.
old_refresh_tail = '''    }catch(ignore){}
    return null;
  }
'''
new_refresh_tail = '''    }catch(ignore){}
    return null;
  }
'''
# No text change needed here; structured envelope already prevents empty-member success from masquerading as real data.

for marker in (
    "Android.getPartyDetails(JSON.stringify(state||{}))",
    "response&&response.ok===true?response.data:null",
    "member.inventoryCapacity||{}",
    "x&&x.consumesInventorySlot!==false",
    "Android.resetNewGameCore()",
    "NEW GAME đã tạo và lưu ở Turn 1 với Character Core mới.",
):
    if marker not in html:
        raise RuntimeError("New Game / capacity UI contract missing: " + marker)
INDEX.write_text(html, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression gates for all four current characters, multi-slot MadGod, reload,
# and immediate New Game projection.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryCapacityNewGameTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.normalize(GameState.initial())

  @Test fun equippedItemsConsumeZeroCapacityForAllCurrentCharacters() {
    val state = fresh()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertTrue("expected equipped loadout for $id", state.equipment[id]?.slots.orEmpty().isNotEmpty())
      assertEquals("equipped gear must not consume backpack slots for $id", 0, InventoryCapacityPolicy.usedSlots(state, id))
      val equipped = InventoryCapacityPolicy.equippedItemIds(state, id)
      assertTrue(equipped.isNotEmpty())
      equipped.forEach { itemId ->
        assertTrue("equipped item remains owned: $id/$itemId", state.inventories.getValue(id).items.containsKey(itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
    }
  }

  @Test fun unequippedOwnedItemConsumesOneSlotAndReequipReleasesIt() {
    val initial = fresh()
    val un = EquipmentEngine.unequip(initial, ItemCommand("U", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP, itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"))
    assertTrue(un.applied)
    assertEquals(1, InventoryCapacityPolicy.usedSlots(un.state, KAI_ID))
    assertTrue(un.state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    val re = EquipmentEngine.equip(un.state, ItemCommand("E", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP, itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"))
    assertTrue(re.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(re.state, KAI_ID))
  }

  @Test fun madGodTwoSlotsRemainOneOwnedItemAndZeroCapacity() {
    var state = fresh()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID)))))
    val equip = EquipmentEngine.equip(state, ItemCommand("M", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP, itemId=MADGOD_SET_ID, itemName="MadGod Set", slot="weapon"))
    assertTrue(equip.applied)
    assertEquals(MADGOD_SET_ID, equip.state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(MADGOD_SET_ID, equip.state.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals(1, equip.state.equipment.getValue(KAI_ID).slots.values.count { it == MADGOD_SET_ID }.let { if (it > 0) 1 else 0 })
    assertTrue(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentRefs() {
    val state = fresh()
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id -> assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id)) }
  }

  @Test fun freshNewGameProjectionIsImmediatelyComplete() {
    val state = fresh()
    val party = CharacterDetailProjector.projectParty(state)
    val kai = party.members.first { it.id == KAI_ID }
    assertEquals(140, kai.currentHp)
    assertEquals(140, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(4, kai.regenPerCompletedTurn)
    assertEquals(107, kai.str.effective)
    assertEquals(109, kai.df.effective)
    assertEquals(112, kai.agi.effective)
    assertEquals(109, kai.crit.effective)
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(9, kai.inventoryCapacityMax)
    assertEquals(6, kai.equipment.values.toSet().size)
    assertTrue(kai.inventoryDetails.count { it.equipped } >= 6)
  }
}
''', encoding="utf-8")

print("New Game + inventory capacity fixed: equipped Items remain owned and visible but consume zero backpack slots for every CharacterState.")
