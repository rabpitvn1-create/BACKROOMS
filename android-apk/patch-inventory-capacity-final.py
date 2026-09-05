from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"

POLICY = CORE / "InventoryPolicy.kt"
policy = POLICY.read_text(encoding="utf-8")

# Final inventory capacity authority. Equipment lives in EquipmentState and never consumes
# these normal inventory type slots.
profile_pattern = re.compile(
    r"(?m)^  val ([A-Z0-9_]+) = InventoryProfile\(maxTypes = \d+, maxPerType = \d+\)$"
)
profile_names = profile_pattern.findall(policy)
if "KAI" not in profile_names or "NORMAL" not in profile_names:
    raise RuntimeError(f"Inventory profile anchors missing: {profile_names}")


def profile_replacement(match: re.Match[str]) -> str:
    name = match.group(1)
    if name == "KAI":
        return "  val KAI = InventoryProfile(maxTypes = 14, maxPerType = 9999)"
    return f"  val {name} = InventoryProfile(maxTypes = 8, maxPerType = 99)"


policy = profile_pattern.sub(profile_replacement, policy)
if "val KAI = InventoryProfile(maxTypes = 14, maxPerType = 9999)" not in policy:
    raise RuntimeError("Kai inventory profile was not finalized")
for name in profile_names:
    expected = (
        "val KAI = InventoryProfile(maxTypes = 14, maxPerType = 9999)"
        if name == "KAI"
        else f"val {name} = InventoryProfile(maxTypes = 8, maxPerType = 99)"
    )
    if expected not in policy:
        raise RuntimeError(f"Inventory profile was not finalized: {name}")
POLICY.write_text(policy, encoding="utf-8")

# Replace the legacy policy regression test with the final boundary contract. This validates
# exact slot limits, exact per-type stack limits, and the fact that equipment slots are separate.
(TESTS / "InventoryPolicyTest.kt").write_text(
    '''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
  private fun stateWith(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(KAI_ID, "Kai Akechi")) + characters
    return GameState.initial().copy(
      characters = all.associateBy { it.id },
      inventories = all.associate { it.id to InventoryState(it.id) },
      equipment = all.associate { it.id to EquipmentState(it.id) }
    )
  }

  @Test fun profilesMatchFinalCharacterRules() {
    val state = stateWith(
      CharacterState("iris", "Iris"),
      CharacterState("syvial", "Syvial"),
      CharacterState("lucia", "Lucia Lục"),
      CharacterState("survivor", "Survivor")
    )
    assertEquals(InventoryProfile(14, 9999), InventoryPolicy.profileFor(state, KAI_ID))
    assertEquals(InventoryProfile(8, 99), InventoryPolicy.profileFor(state, "iris"))
    assertEquals(InventoryProfile(8, 99), InventoryPolicy.profileFor(state, "syvial"))
    assertEquals(InventoryProfile(8, 99), InventoryPolicy.profileFor(state, "lucia"))
    assertEquals(InventoryProfile(8, 99), InventoryPolicy.profileFor(state, "survivor"))
  }

  @Test fun kaiAllowsFourteenTypesAndCapsEachTypeAt9999() {
    val thirteen = (1..13).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    var state = stateWith().copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, thirteen)))
    assertNull(InventoryPolicy.validateAddition(
      state, KAI_ID, state.inventories.getValue(KAI_ID), ItemStack("i14", "Item 14"), 1
    ))

    val fourteen = (1..14).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    state = state.copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, fourteen)))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(
      state, KAI_ID, state.inventories.getValue(KAI_ID), ItemStack("i15", "Item 15"), 1
    ))

    state = state.copy(inventories = mapOf(
      KAI_ID to InventoryState(KAI_ID, mapOf("water" to ItemStack("water", "Water", 9998)))
    ))
    assertNull(InventoryPolicy.validateAddition(
      state, KAI_ID, state.inventories.getValue(KAI_ID), ItemStack("water", "Water"), 1
    ))
    state = state.copy(inventories = mapOf(
      KAI_ID to InventoryState(KAI_ID, mapOf("water" to ItemStack("water", "Water", 9999)))
    ))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(
      state, KAI_ID, state.inventories.getValue(KAI_ID), ItemStack("water", "Water"), 1
    ))
  }

  @Test fun everyNonKaiCharacterAllowsEightTypesAndCapsEachTypeAt99() {
    val follower = CharacterState("follower", "Follower")
    var state = stateWith(follower)
    val seven = (1..7).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    state = state.copy(inventories = state.inventories + ("follower" to InventoryState("follower", seven)))
    assertNull(InventoryPolicy.validateAddition(
      state, "follower", state.inventories.getValue("follower"), ItemStack("i8", "Item 8"), 1
    ))

    val eight = (1..8).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    state = state.copy(inventories = state.inventories + ("follower" to InventoryState("follower", eight)))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(
      state, "follower", state.inventories.getValue("follower"), ItemStack("i9", "Item 9"), 1
    ))

    state = state.copy(inventories = state.inventories + (
      "follower" to InventoryState("follower", mapOf("water" to ItemStack("water", "Water", 98)))
    ))
    assertNull(InventoryPolicy.validateAddition(
      state, "follower", state.inventories.getValue("follower"), ItemStack("water", "Water"), 1
    ))
    state = state.copy(inventories = state.inventories + (
      "follower" to InventoryState("follower", mapOf("water" to ItemStack("water", "Water", 99)))
    ))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(
      state, "follower", state.inventories.getValue("follower"), ItemStack("water", "Water"), 1
    ))
  }

  @Test fun equipmentSlotsDoNotConsumeNormalInventorySlots() {
    val thirteen = (1..13).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    val manyEquipmentSlots = (1..12).associate { "slot$it" to "equipped$it" }
    val state = stateWith().copy(
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, thirteen)),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, manyEquipmentSlots))
    )
    assertNull(InventoryPolicy.validateAddition(
      state, KAI_ID, state.inventories.getValue(KAI_ID), ItemStack("i14", "Item 14"), 1
    ))
  }

  @Test fun equippedKaiSignatureItemCannotBeScanned() {
    val gun = ItemStack("kai-gun", "Kai Gun", 1)
    val state = stateWith().copy(
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(gun.itemId to gun))),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, mapOf("weapon" to gun.itemId)))
    )
    val result = StateReducer.execute(
      state,
      OmnivaultCommand(
        "scan", "TURN_1", KAI_ID,
        source = CommandSource.RULE,
        operation = OmnivaultCommand.Operation.SCAN,
        itemId = gun.itemId,
        itemName = gun.name
      )
    )
    assertEquals("signature_equipment_locked", result.validation.reason)
  }
}
''',
    encoding="utf-8",
)

# Finalize the Character Detail capacity UI after all legacy follower/UI patches have run.
html = INDEX.read_text(encoding="utf-8")
static_old = 'id="characterInventoryCapacity">0 / 9 loại vật phẩm</div>'
static_new = 'id="characterInventoryCapacity">0 / 14 ô vật phẩm</div>'
if static_new not in html:
    if html.count(static_old) != 1:
        raise RuntimeError(f"Inventory capacity static anchor count: {html.count(static_old)}")
    html = html.replace(static_old, static_new, 1)

limit_old = '<div class="inventory-limit">Inventory của nhân vật đang chọn</div>'
limit_new = '<div class="inventory-limit" id="characterInventoryLimit">Kai: tối đa 9999 đơn vị mỗi loại · Ô trang bị không tính vào Kho đồ</div>'
if limit_new not in html:
    if html.count(limit_old) != 1:
        raise RuntimeError(f"Inventory limit label anchor count: {html.count(limit_old)}")
    html = html.replace(limit_old, limit_new, 1)

ui_marker = "const inventoryProfile=member&&member.id==='kai'?{slots:14,maxPerType:9999}:{slots:8,maxPerType:99};"
if ui_marker not in html:
    capacity_pattern = re.compile(r"(?m)^    capacity\.textContent=[^\n]+;$")
    matches = capacity_pattern.findall(html)
    if len(matches) != 1:
        raise RuntimeError(f"Inventory capacity renderer anchor count: {len(matches)}")
    replacement = "\n".join([
        "    const inventoryProfile=member&&member.id==='kai'?{slots:14,maxPerType:9999}:{slots:8,maxPerType:99};",
        "    capacity.textContent=inv.length+' / '+inventoryProfile.slots+' ô vật phẩm';",
        "    const inventoryLimit=document.getElementById('characterInventoryLimit');",
        "    if(inventoryLimit)inventoryLimit.textContent='Tối đa '+inventoryProfile.maxPerType+' đơn vị mỗi loại · Ô trang bị không tính vào Kho đồ';",
    ])
    html = capacity_pattern.sub(replacement, html, count=1)

for token in [
    "slots:14,maxPerType:9999",
    "slots:8,maxPerType:99",
    "Ô trang bị không tính vào Kho đồ",
    "0 / 14 ô vật phẩm",
]:
    if token not in html:
        raise RuntimeError(f"Final inventory UI contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")

# Keep the final writer prompt aligned. The knowledge-context patch replaces the older KAI LOADOUT
# prompt before this finalizer runs, so anchor to the current writer rule that is guaranteed to exist.
java = MAIN.read_text(encoding="utf-8")
prompt_anchor = "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. "
capacity_prompt = "INVENTORY CAPACITY: Kai có 14 slot vật phẩm thường, mỗi loại tối đa x9999. Mọi nhân vật khác có 8 slot vật phẩm, mỗi loại tối đa x99. Equipment là vùng riêng và không chiếm Inventory slot. "
if capacity_prompt not in java:
    if java.count(prompt_anchor) != 1:
        raise RuntimeError(f"Final inventory writer prompt anchor count: {java.count(prompt_anchor)}")
    java = java.replace(prompt_anchor, capacity_prompt + prompt_anchor, 1)
if capacity_prompt not in java:
    raise RuntimeError("Final inventory GM policy marker missing")
MAIN.write_text(java, encoding="utf-8")

if KNOWLEDGE.is_file():
    knowledge = KNOWLEDGE.read_text(encoding="utf-8")
    knowledge = knowledge.replace(
        "Inventory capacity is 8 item types, up to 100 units per type; Equipment is separate.",
        "Inventory capacity is 8 item types, up to 99 units per type; Equipment is separate.",
    )
    KNOWLEDGE.write_text(knowledge, encoding="utf-8")

print("Final inventory capacity applied: Kai 14x9999, all other characters 8x99; equipment slots excluded.")
