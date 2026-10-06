from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
DETAIL = CORE / "CharacterDetailProjection.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
INVENTORY_POLICY = CORE / "InventoryPolicy.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
TEST = TESTS / "InventoryCapacityNewGameTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def regex_once(source: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, source, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 semantic match, found {count}")
    return updated

# ---------------------------------------------------------------------------
# 1) Inventory capacity = owned carried Item types, excluding unique equipped
#    itemIds. Equipment remains the ONE Item instance owned by Inventory.
# ---------------------------------------------------------------------------
system = CORE / "CharacterEquipmentSystem.kt"
system_text = system.read_text(encoding="utf-8")
capacity_object = r'''
object InventoryCapacityPolicy {
  fun maxSlots(state: GameState, characterId: String): Int = InventoryPolicy.profileFor(state, characterId).maxTypes

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
        raise RuntimeError("CharacterStatEngine anchor missing for InventoryCapacityPolicy")
    system_text = system_text.replace(anchor, capacity_object + anchor, 1)
system.write_text(system_text, encoding="utf-8")

policy = INVENTORY_POLICY.read_text(encoding="utf-8")
legacy_capacity_patterns = [
    r'''\s*val carriedTypes = inventory\.items\.values\.count \{ EquipmentCatalog\.definition\(it\.itemId\) == null \}\n\s*val addingEquipment = EquipmentCatalog\.definition\(normalized\.itemId\) != null\n\s*if \(old == null && !addingEquipment && carriedTypes >= profile\.maxTypes\) return "inventory_slot_limit"\n''',
    r'''\s*if \(old == null && inventory\.items\.size >= profile\.maxTypes\) return "inventory_slot_limit"\n''',
]
capacity_replacement = '''
    val carriedTypes = InventoryCapacityPolicy.usedSlots(state, ownerId)
    if (old == null && carriedTypes >= profile.maxTypes) return "inventory_slot_limit"
'''
if 'val carriedTypes = InventoryCapacityPolicy.usedSlots(state, ownerId)' not in policy:
    replaced = False
    for pattern in legacy_capacity_patterns:
        updated, count = re.subn(pattern, capacity_replacement, policy, count=1)
        if count == 1:
            policy = updated
            replaced = True
            break
    if not replaced:
        raise RuntimeError("InventoryPolicy capacity enforcement anchor missing")
INVENTORY_POLICY.write_text(policy, encoding="utf-8")

detail = DETAIL.read_text(encoding="utf-8")
if 'val inventoryCapacityUsed: Int' not in detail:
    detail = regex_once(
        detail,
        r'(\s+val inventoryDetails: List<ItemDetailProjection>(?:\s*=\s*emptyList\(\))?,\n)',
        r'\1  val inventoryCapacityUsed: Int = 0,\n  val inventoryCapacityMax: Int = 9,\n',
        "Character inventory capacity projection fields",
    )
if 'inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots' not in detail:
    detail = regex_once(
        detail,
        r'(\s+inventory\s*=\s*inventory,\s*inventoryDetails\s*=\s*inventoryDetails,)(\s*equipment\s*=\s*equipment,)',
        r'\1\n      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(state, character.id),\n      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(state, character.id),\n      \2',
        "Character capacity projection assignment",
    )
DETAIL.write_text(detail, encoding="utf-8")

json_text = DETAIL_JSON.read_text(encoding="utf-8")
if 'put("inventoryCapacity", JSONObject().put("used"' not in json_text:
    equipment_anchor = '    put("equipment", JSONObject(c.equipment))\n'
    if equipment_anchor not in json_text:
        raise RuntimeError("CharacterDetailJson equipment anchor missing")
    json_text = json_text.replace(
        equipment_anchor,
        '    put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed).put("max", c.inventoryCapacityMax))\n' + equipment_anchor,
        1,
    )
item_marker = '    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.statItem); x.classification?.let { put("classification", it) }\n'
if 'put("consumesInventorySlot", !x.equipped)' not in json_text:
    if item_marker not in json_text:
        raise RuntimeError("ItemDetail JSON equipped marker missing")
    json_text = json_text.replace(item_marker, item_marker + '    put("consumesInventorySlot", !x.equipped)\n', 1)
DETAIL_JSON.write_text(json_text, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) New Game / cold start owns a real Core state immediately.
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
    facade = replace_once(facade, old_party, new_party, "Authoritative New Game facade")
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
        String message = e.getMessage() == null ? "Core unavailable" : e.getMessage();
        return "{\\\"ok\\\":false,\\\"error\\\":\\\"CORE_UNAVAILABLE\\\",\\\"message\\\":" + JSONObject.quote(message) + "}";
      }
    }

    @JavascriptInterface public String resetNewGameCore() {
      try {
        return new JSONObject()
          .put("ok", true)
          .put("data", new JSONObject(requireGameCore().resetNewGame()))
          .toString();
      } catch (Exception e) {
        String message = e.getMessage() == null ? "New Game core reset failed" : e.getMessage();
        return "{\\\"ok\\\":false,\\\"error\\\":\\\"NEW_GAME_CORE_FAILED\\\",\\\"message\\\":" + JSONObject.quote(message) + "}";
      }
    }
'''
if '@JavascriptInterface public String resetNewGameCore()' not in main:
    main = replace_once(main, old_bridge, new_bridge, "Lazy Core Character/New Game bridge")
if 'gameCore.currentPartyDetails()' in main:
    raise RuntimeError("Direct lazy Core bypass survived: gameCore.currentPartyDetails()")
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) UI reads Core capacity, hides equipped Items from Inventory, and resets Core.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
html = html.replace('Android.getPartyDetails();', 'Android.getPartyDetails(JSON.stringify(state||{}));')
old_parse = '''const details=JSON.parse(raw||'{}');
        if(details&&Array.isArray(details.members)&&details.members.length){'''
new_parse = '''const response=JSON.parse(raw||'{}');
        const details=response&&response.ok===true?response.data:null;
        if(details&&Array.isArray(details.members)&&details.members.length){'''
html = html.replace(old_parse, new_parse)
old_capacity = "    capacity.textContent=inv.length+' / 9 loại vật phẩm';\n"
new_capacity = "    const cap=member&&member.inventoryCapacity;const used=cap&&Number.isFinite(Number(cap.used))?Number(cap.used):inv.filter(x=>!(x&&x.equipped)).length;const max=cap&&Number.isFinite(Number(cap.max))?Number(cap.max):9;capacity.textContent=used+' / '+max+' loại vật phẩm';\n"
if old_capacity in html:
    html = html.replace(old_capacity, new_capacity, 1)
render_anchor = "    if(inventory){inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n"
render_with_capacity = "    const visibleInventory=(member.inventory||[]).filter(x=>x&&x.equipped!==true);if(inventory){inventory.innerHTML=visibleInventory.map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n    const capEl=document.getElementById('characterInventoryCapacity'),cap=member.inventoryCapacity||{};if(capEl){const used=Number.isFinite(Number(cap.used))?Number(cap.used):(member.inventory||[]).filter(x=>x&&x.consumesInventorySlot!==false).length;const max=Number.isFinite(Number(cap.max))?Number(cap.max):9;capEl.textContent=used+' / '+max+' loại vật phẩm'}\n"
if render_with_capacity not in html:
    if render_anchor not in html:
        raise RuntimeError("Redesigned Inventory renderer anchor missing")
    html = html.replace(render_anchor, render_with_capacity, 1)
old_reset = '''    clearAuthoritativeCore();
    state=freshInitial();
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1. Game State Core và Snapshot cũ đã được xóa.";
'''
new_reset = '''    state=freshInitial();
    try{
      if(window.Android&&typeof Android.resetNewGameCore==='function'){
        const response=JSON.parse(Android.resetNewGameCore()||'{}');
        if(response&&response.ok===true&&response.data){state.partyDetails=response.data}
        else throw new Error((response&&response.message)||'Core reset failed');
      }else{
        clearAuthoritativeCore();
      }
    }catch(e){statusEl.textContent="NEW GAME Core lỗi: "+(e&&e.message?e.message:"bridge error");return}
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1 với Character Core mới.";
'''
if 'Android.resetNewGameCore()' not in html:
    html = replace_once(html, old_reset, new_reset, "New Game Core initialization")
for marker in (
    'Android.getPartyDetails(JSON.stringify(state||{}))',
    'response&&response.ok===true?response.data:null',
    'member.inventoryCapacity||{}',
    'x&&x.consumesInventorySlot!==false',
    'const visibleInventory=(member.inventory||[]).filter(x=>x&&x.equipped!==true)',
    'Android.resetNewGameCore()',
    'NEW GAME đã tạo và lưu ở Turn 1 với Character Core mới.',
):
    if marker not in html:
        raise RuntimeError("New Game / Inventory capacity UI contract missing: " + marker)
if "inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null))" in html:
    raise RuntimeError("Equipped Items can still render in the Character Inventory list")
INDEX.write_text(html, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression gates.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryCapacityNewGameTest {
  private fun freshAll(): GameState = CharacterEquipmentSystem.normalize(
    SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial()))
  )

  @Test fun equippedItemsConsumeZeroCapacityForAllFourCharacters() {
    val state = freshAll()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertTrue("character must exist: $id", state.characters.containsKey(id))
      val slots = state.equipment[id]?.slots.orEmpty()
      assertTrue("expected equipped loadout: $id", slots.isNotEmpty())
      val equippedIds = InventoryCapacityPolicy.equippedItemIds(state, id)
      assertTrue(equippedIds.isNotEmpty())
      equippedIds.forEach { itemId ->
        assertTrue("equipped item remains Inventory-owned: $id/$itemId", state.inventories.getValue(id).items.containsKey(itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
      assertEquals(0, InventoryCapacityPolicy.usedSlots(state, id))
      assertEquals(InventoryPolicy.profileFor(state, id).maxTypes, InventoryCapacityPolicy.maxSlots(state, id))
    }
  }

  @Test fun unequipMakesTheSameOwnedItemConsumeOneSlotAndReequipReleasesIt() {
    val initial = freshAll()
    val unequip = EquipmentEngine.unequip(initial, ItemCommand(
      "U", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"
    ))
    assertTrue(unequip.applied)
    assertTrue(unequip.state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    assertEquals(1, InventoryCapacityPolicy.usedSlots(unequip.state, KAI_ID))
    val reEquip = EquipmentEngine.equip(unequip.state, ItemCommand(
      "E", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"
    ))
    assertTrue(reEquip.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(reEquip.state, KAI_ID))
  }

  @Test fun madGodOccupiesTwoEquipmentSlotsButIsOneOwnedZeroCapacityItem() {
    var state = freshAll()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID)))))
    val equip = EquipmentEngine.equip(state, ItemCommand(
      "M", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=MADGOD_SET_ID, itemName="MadGod Set", slot="weapon"
    ))
    assertTrue(equip.applied)
    val slots = equip.state.equipment.getValue(KAI_ID).slots
    assertEquals(2, slots.values.count { it == MADGOD_SET_ID })
    assertEquals(1, InventoryCapacityPolicy.equippedItemIds(equip.state, KAI_ID).count { it == MADGOD_SET_ID })
    assertTrue(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
    assertEquals(0, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameKaiProjectionIsImmediatelyAuthoritative() {
    val state = CharacterEquipmentSystem.normalize(GameState.initial())
    val kai = CharacterDetailProjector.projectParty(state).members.first { it.id == KAI_ID }
    assertEquals(140, kai.currentHp)
    assertEquals(140, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(4, kai.regenPerCompletedTurn)
    assertEquals(107, kai.str.effective)
    assertEquals(109, kai.df.effective)
    assertEquals(112, kai.agi.effective)
    assertEquals(109, kai.crit.effective)
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(InventoryPolicy.KAI.maxTypes, kai.inventoryCapacityMax)
    assertEquals(6, kai.equipment.values.toSet().size)
    assertTrue(kai.inventoryDetails.count { it.equipped } >= 6)
  }
}
''', encoding="utf-8")

combined = (
    system.read_text(encoding="utf-8") + DETAIL.read_text(encoding="utf-8") + DETAIL_JSON.read_text(encoding="utf-8") +
    FACADE.read_text(encoding="utf-8") + MAIN.read_text(encoding="utf-8") + INDEX.read_text(encoding="utf-8") +
    INVENTORY_POLICY.read_text(encoding="utf-8") + TEST.read_text(encoding="utf-8")
)
for marker in (
    'object InventoryCapacityPolicy {',
    'return owned - equippedItemIds(state, characterId)',
    'InventoryCapacityPolicy.usedSlots(state, ownerId)',
    'inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(state, character.id)',
    'put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed)',
    'put("consumesInventorySlot", !x.equipped)',
    'fun resetNewGame(): String',
    '@JavascriptInterface public String getPartyDetails(String stateJson)',
    'requireGameCore().currentPartyDetails(stateJson)',
    '@JavascriptInterface public String resetNewGameCore()',
    'requireGameCore().resetNewGame()',
    'JSONObject.quote(message)',
    'equippedItemsConsumeZeroCapacityForAllFourCharacters',
    'madGodOccupiesTwoEquipmentSlotsButIsOneOwnedZeroCapacityItem',
    'freshNewGameKaiProjectionIsImmediatelyAuthoritative',
    'const visibleInventory=(member.inventory||[]).filter(x=>x&&x.equipped!==true)',
):
    if marker not in combined:
        raise RuntimeError("Final New Game/capacity contract missing: " + marker)
if 'gameCore.currentPartyDetails()' in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Forbidden direct lazy-Core Character bridge remains")

print("New Game + Inventory capacity fixed: authoritative cold start; equipped Items remain owned but are hidden from Inventory UI and consume zero slots.")