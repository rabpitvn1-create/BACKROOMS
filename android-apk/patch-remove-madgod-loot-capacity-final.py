from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ASSETS = ROOT / "app/src/main/assets"

SYSTEM = CORE / "CharacterEquipmentSystem.kt"
MADGOD = CORE / "MadGodCanon.kt"
FACADE = CORE / "GameCoreFacade.kt"
COMMAND = CORE / "CommandPipeline.kt"
ENGINES = CORE / "Engines.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
ITEMS = CORE / "ItemCatalog.kt"
ITEM_SYSTEM = CORE / "ItemSystem.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"
FINAL_TEST = TESTS / "MadGodRemovalLootCapacityTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


def matching_brace(source: str, open_index: int, open_char: str = "{", close_char: str = "}") -> int:
    depth = 0
    quote = None
    escape = False
    i = open_index
    while i < len(source):
        ch = source[i]
        if quote is not None:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == quote:
                quote = None
            i += 1
            continue
        if ch in ("'", '"'):
            quote = ch
            i += 1
            continue
        if ch == open_char:
            depth += 1
        elif ch == close_char:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise RuntimeError(f"Unbalanced {open_char}{close_char} block at {open_index}")


def remove_named_function(source: str, name: str) -> str:
    patterns = [
        rf"(?m)^\s*(?:private\s+|internal\s+|public\s+)?fun\s+{re.escape(name)}\s*\(",
        rf"(?m)^\s*function\s+{re.escape(name)}\s*\(",
    ]
    for pattern in patterns:
        match = re.search(pattern, source)
        if not match:
            continue
        brace = source.find("{", match.start())
        if brace < 0:
            raise RuntimeError(f"Function {name} has no body")
        end = matching_brace(source, brace)
        line_start = source.rfind("\n", 0, match.start()) + 1
        line_end = source.find("\n", end)
        if line_end < 0:
            line_end = len(source)
        else:
            line_end += 1
        return source[:line_start] + source[line_end:]
    return source


def remove_if_block(source: str, marker: str) -> str:
    pos = source.find(marker)
    if pos < 0:
        return source
    line_start = source.rfind("\n", 0, pos) + 1
    brace = source.find("{", pos)
    if brace < 0:
        raise RuntimeError(f"if block has no brace: {marker}")
    end = matching_brace(source, brace)
    line_end = source.find("\n", end)
    if line_end < 0:
        line_end = len(source)
    else:
        line_end += 1
    return source[:line_start] + source[line_end:]


def remove_equipment_definition(source: str, id_marker: str) -> str:
    marker_pos = source.find(id_marker)
    if marker_pos < 0:
        return source
    start = source.rfind("    EquipmentDefinition(", 0, marker_pos)
    if start < 0:
        raise RuntimeError(f"EquipmentDefinition start missing for {id_marker}")
    open_paren = source.find("(", start)
    end = matching_brace(source, open_paren, "(", ")")
    cursor = end + 1
    if cursor < len(source) and source[cursor] == ",":
        cursor += 1
    if cursor < len(source) and source[cursor] == "\n":
        cursor += 1
    return source[:start] + source[cursor:]


def remove_madgod_tests(source: str) -> str:
    pattern = re.compile(r"(?m)^\s*@(?:org\.junit\.)?Test\s+fun\s+([A-Za-z0-9_]+)\s*\(")
    while True:
        removed = False
        for match in list(pattern.finditer(source)):
            brace = source.find("{", match.end())
            if brace < 0:
                continue
            end = matching_brace(source, brace)
            block = source[match.start():end + 1]
            if "madgod" not in block.lower():
                continue
            line_start = source.rfind("\n", 0, match.start()) + 1
            line_end = source.find("\n", end)
            if line_end < 0:
                line_end = len(source)
            else:
                line_end += 1
            source = source[:line_start] + source[line_end:]
            removed = True
            break
        if not removed:
            return source


def drop_lines(source: str, needles: tuple[str, ...]) -> str:
    lines = source.splitlines(keepends=True)
    return "".join(line for line in lines if not any(needle.lower() in line.lower() for needle in needles))


# ---------------------------------------------------------------------------
# 1) MadGod is retired from gameplay. Keep only a deliberately inert ABI shim
# while old generated layers are being unwound, so no historical patch can make
# the set obtainable, equipable, copyable, or visible again.
# ---------------------------------------------------------------------------
MADGOD.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_SET_ID = "retired:madgod-disabled"

object MadGodCanon {
  const val MADGOD_SET_ID = "retired:madgod-disabled"
  const val SPECIAL_CHEAT = "DISABLED"
  const val ARMOR_ID = "retired:madgod-armor-disabled"
  const val MAGNUM_ID = "retired:madgod-magnum-disabled"
  const val RING_ID = "retired:madgod-ring-disabled"
  const val CHEAT_CODE = ""
  const val SET_NAME = ""
  const val SCALING_MODE = "DISABLED"
  const val MULTIPLIER = 1
  const val MAGNUM_RPM = 0
  const val MAGNUM_DMG = 0
  const val ARMOR_DF = 0
  const val ARMOR_STR = 0
  const val ARMOR_AGI = 0
  const val ARMOR_HP = 0
  const val ARMOR_ENE = 0
  const val ARMOR_CRIT = 0
  const val AVATAR_ASSET = ""
  const val SNAPSHOT_OVERLAY_ASSET = ""

  data class Spawn(val state: GameState, val added: Boolean)

  fun cheat(x: String): Boolean = false
  fun isSetId(x: String?): Boolean = false
  fun isLegacyId(x: String?): Boolean = false
  fun isId(x: String?): Boolean = false
  fun isItem(x: ItemStack?): Boolean = false
  fun slot(id: String, name: String): String? = null
  fun setItem(): ItemStack = ItemStack(MADGOD_SET_ID, "Retired item", 1, metadata = mapOf("retired" to "true"))
  fun weapon(): ItemStack = setItem()
  fun armor(): ItemStack = setItem()
  fun spawn(s: GameState): Spawn = Spawn(s, false)
  fun legacy(s: GameState): JSONObject = JSONObject()
}
''', encoding="utf-8")

# Equipment catalog and state normalization: no MadGod definition survives, and
# old saves carrying historical madgod:* IDs are purged before normal loadout seeding.
system = SYSTEM.read_text(encoding="utf-8")
system = remove_equipment_definition(system, "id = MADGOD_SET_ID")
system = system.replace(
    '''    if (def?.classification == ItemClassification.SPECIAL_CHEAT && command.actorId != KAI_ID) return invalid(state, "madgod_equipment_slot_mismatch")\n''',
    "",
)
system = system.replace(
    '''    val lockedByMadGod = targetSlots.any { slot -> equipment.slots[slot] == MADGOD_SET_ID && command.itemId != MADGOD_SET_ID }\n    if (lockedByMadGod) return invalid(state, "madgod_equipment_permanent")\n    if (command.itemId == MADGOD_SET_ID && equipment.slots.values.count { it == MADGOD_SET_ID } >= 2) return changed(state, "item_equipped")\n\n''',
    "",
)
system = system.replace('    if (command.itemId == MADGOD_SET_ID) return invalid(state, "madgod_equipment_permanent")\n', "")
system = system.replace('    if (def.classification == ItemClassification.SPECIAL_CHEAT && characterId != KAI_ID) return null\n', "")
system = system.replace('    if (def.occupiesSlots.any { equipment.slots[it.key] == MADGOD_SET_ID && itemId != MADGOD_SET_ID }) return null\n', "")
system = system.replace(
    '''          val madGodOccupies = characterId == KAI_ID && slot in setOf(EquipmentSlot.WEAPON, EquipmentSlot.ARMOR) &&\n            slots.values.any { it == MADGOD_SET_ID }\n          if (!madGodOccupies && slot.key !in slots) slots[slot.key] = itemId\n''',
    '''          if (slot.key !in slots) slots[slot.key] = itemId\n''',
)
system = system.replace('  private const val SCHEMA_VERSION = "1"\n', '  private const val SCHEMA_VERSION = "2"\n')
helper_anchor = 'object CharacterEquipmentSystem {\n  private const val SCHEMA_VERSION = "2"\n'
retired_helpers = '''object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "2"

  private fun retiredMadGodId(value: String?): Boolean =
    value?.trim()?.lowercase()?.startsWith("madgod:") == true

  private fun retiredMadGodItem(item: ItemStack): Boolean =
    retiredMadGodId(item.itemId) || item.name.contains("MadGod", ignoreCase = true) ||
      item.metadata["madGod"].equals("true", ignoreCase = true)
'''
if 'private fun retiredMadGodId(' not in system:
    if helper_anchor not in system:
        raise RuntimeError("CharacterEquipmentSystem schema anchor missing")
    system = system.replace(helper_anchor, retired_helpers, 1)

normalize_anchor = '''    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()

'''
normalize_new = '''    val inventories = input.inventories.mapValues { (ownerId, inventory) ->
      inventory.copy(items = inventory.items.filterValues { !retiredMadGodItem(it) })
    }.toMutableMap()
    val equipment = input.equipment.mapValues { (ownerId, equipped) ->
      equipped.copy(slots = equipped.slots.filterValues { !retiredMadGodId(it) })
    }.toMutableMap()
    val cleanedMetadata = input.metadata.filterKeys { key ->
      !key.startsWith("madGod", ignoreCase = true) && !key.startsWith("madgod", ignoreCase = true)
    }

'''
if 'val cleanedMetadata = input.metadata.filterKeys' not in system:
    system = replace_once(system, normalize_anchor, normalize_new, "retired MadGod save cleanup")
system = system.replace(
    '      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)\n',
    '      metadata = cleanedMetadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)\n',
)
SYSTEM.write_text(system, encoding="utf-8")

# Remove active command/cheat routes and MadGod-only validation branches.
facade = FACADE.read_text(encoding="utf-8")
facade = remove_if_block(facade, "if (isMadGodEquipRequest(action)) {")
facade = remove_named_function(facade, "isMadGodEquipRequest")
facade = remove_named_function(facade, "applyMadGodCheat")
facade = drop_lines(facade, (
    "MadGodCanon.cheat(action)",
    'output.put("equipment",MadGodCanon.legacy(state))',
    '"madgod_equipment_permanent" ->',
    '"madgod_omnivault_copy_forbidden" ->',
    '"madgod_equipment_slot_mismatch" ->',
))
FACADE.write_text(facade, encoding="utf-8")

command = COMMAND.read_text(encoding="utf-8")
command = drop_lines(command, ("MadGodCanon.slot",))
COMMAND.write_text(command, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
engines = drop_lines(engines, (
    "MadGodCanon.isId(",
    "MadGodCanon.isItem(",
    "madgod_equipment_permanent",
    "madgod_equipment_slot_mismatch",
))
ENGINES.write_text(engines, encoding="utf-8")

omnivault = OMNIVAULT.read_text(encoding="utf-8")
omnivault = drop_lines(omnivault, (
    "MadGodCanon.isItem(",
    "madgod_omnivault_copy_forbidden",
))
OMNIVAULT.write_text(omnivault, encoding="utf-8")

# Web/runtime visuals always use SRU visuals. Any historical MadGod portrait or
# overlay reference is normalized away; the old set detector cannot affect UI.
main = MAIN.read_text(encoding="utf-8")
main = main.replace("Kai_MadGod_snapshot_overlay.png", "SRU_IDLE.png")
main = main.replace("avatars/MadGod.jpg", "avatars/SRU_AVATAR.jpg")
main = main.replace("/madgod", "/retired-command-disabled")
main = main.replace("if(e.set&&String(e.set.id||e.set)==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;", "")
main = main.replace("function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||x.name||x).toLowerCase().indexOf('madgod')>=0)}", "")
main = main.replace("function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||'').indexOf('madgod:')===0)}", "")
main = re.sub(
    r"function kaiOverlaySource\(\)\{if\(kaiCombatActive\(\)\)return 'SRU_AIM\.png';.*?return 'SRU_IDLE\.png'\}",
    "function kaiOverlaySource(){if(kaiCombatActive())return 'SRU_AIM.png';return 'SRU_IDLE.png'}",
    main,
    count=1,
)
MAIN.write_text(main, encoding="utf-8")

index = INDEX.read_text(encoding="utf-8")
index = index.replace("avatars/MadGod.jpg", "avatars/SRU_AVATAR.jpg")
index = index.replace("Kai_MadGod_snapshot_overlay.png", "SRU_IDLE.png")
index = index.replace("/madgod", "/retired-command-disabled")
index = index.replace("function madGodSetEquipped(){try{const e=state&&state.equipment||{};if(e.set&&String(e.set.id||e.set)==='madgod:set')return true;if(['weapon','armor'].some(k=>String((e[k]&&e[k].id)||e[k]||'').toLowerCase().includes('madgod')))return true;const members=state&&state.partyDetails&&state.partyDetails.members;const kai=Array.isArray(members)&&members.find(m=>String(m&&m.id)==='kai');return !!(kai&&kai.equipment&&['weapon','armor'].some(k=>String((kai.equipment[k]&&kai.equipment[k].id)||kai.equipment[k]||'').toLowerCase().includes('madgod')))}catch(ignore){return false}}", "")
index = index.replace("function madGodSetEquipped(){try{const e=state&&state.equipment;return !!(e&&e.set&&String(e.set.id||'')==='madgod:set')}catch(ignore){return false}}", "")
index = index.replace("madGodSetEquipped()", "false")
INDEX.write_text(index, encoding="utf-8")

# Historical dedicated regression file no longer describes a supported feature.
legacy_test = TESTS / "MadGodEquipmentTest.kt"
if legacy_test.exists():
    legacy_test.unlink()
for test_path in TESTS.glob("*.kt"):
    if test_path == FINAL_TEST:
        continue
    source = test_path.read_text(encoding="utf-8")
    updated = remove_madgod_tests(source)
    if updated != source:
        test_path.write_text(updated, encoding="utf-8")

# Retired visual assets must not enter the APK even if an old source checkout had them.
for path in (ASSETS / "Kai_MadGod_snapshot_overlay.png", ASSETS / "avatars/MadGod.jpg"):
    if path.exists():
        path.unlink()


# ---------------------------------------------------------------------------
# 2) Loot: add a flat +5 percentage points to both exploration and Entity drops,
# preserving the existing +1/+2 pity increments and all follower bonuses.
# ---------------------------------------------------------------------------
items = ITEMS.read_text(encoding="utf-8")
items = replace_once(
    items,
    '''  const val DROP_STEP_PERCENT = 2
  const val DROP_CHANCE_PERCENT = DROP_STEP_PERCENT
''',
    '''  const val DROP_STEP_PERCENT = 2
  const val BASE_DROP_BONUS_PERCENT = 5
  const val DROP_CHANCE_PERCENT = BASE_DROP_BONUS_PERCENT + DROP_STEP_PERCENT
''',
    "Entity loot flat bonus constants",
)
items = replace_once(
    items,
    '''  fun dropChancePercent(state: GameState): Int =
    ((killsWithoutDrop(state) + 1) * DROP_STEP_PERCENT).coerceIn(DROP_STEP_PERCENT, 100)
''',
    '''  fun dropChancePercent(state: GameState): Int =
    (BASE_DROP_BONUS_PERCENT + (killsWithoutDrop(state) + 1) * DROP_STEP_PERCENT).coerceIn(DROP_CHANCE_PERCENT, 100)
''',
    "Entity loot flat bonus preview",
)
items = replace_once(
    items,
    '    val chance = ((failures + 1) * DROP_STEP_PERCENT).coerceAtMost(100)\n',
    '    val chance = (BASE_DROP_BONUS_PERCENT + (failures + 1) * DROP_STEP_PERCENT).coerceAtMost(100)\n',
    "Entity loot flat bonus roll",
)
items = replace_once(
    items,
    '''  const val PITY_STEP_BASIS_POINTS = 100
  const val GUARANTEED_TURN = 100
''',
    '''  const val PITY_STEP_BASIS_POINTS = 100
  const val BASE_EXPLORATION_BONUS_BASIS_POINTS = 500
  const val GUARANTEED_TURN = 100
''',
    "Exploration loot flat bonus constant",
)
items = replace_once(
    items,
    '    val threshold = (base + pity + follower).coerceAtMost(10000)\n',
    '    val threshold = (base + BASE_EXPLORATION_BONUS_BASIS_POINTS + pity + follower).coerceAtMost(10000)\n',
    "Exploration loot flat bonus threshold",
)
ITEMS.write_text(items, encoding="utf-8")

# Align historical pity regression expectations with the new flat bonus.
official_test = TESTS / "OfficialItemSystemTest.kt"
if official_test.exists():
    source = official_test.read_text(encoding="utf-8")
    source = source.replace('repeat(49) { index ->', 'repeat(47) { index ->')
    source = source.replace('assertEquals(49, EntityLootEngine.killsWithoutDrop(state))', 'assertEquals(47, EntityLootEngine.killsWithoutDrop(state))')
    source = source.replace('"pity-guaranteed-50"', '"pity-guaranteed-48"')
    source = source.replace('assertEquals(2, EntityLootEngine.dropChancePercent(guaranteed))', 'assertEquals(7, EntityLootEngine.dropChancePercent(guaranteed))')
    official_test.write_text(source, encoding="utf-8")
action_test = TESTS / "ActionRuntimeTest.kt"
if action_test.exists():
    source = action_test.read_text(encoding="utf-8")
    source = source.replace('assertEquals(135, firstPreview.threshold)', 'assertEquals(635, firstPreview.threshold)')
    action_test.write_text(source, encoding="utf-8")

# The flat +5 type-slot balance changes every inventory profile, so all older
# generated regression tests must exercise the new limits instead of retaining
# their pre-balance boundary values. Keep these rewrites here beside the runtime
# balance change so future patch-chain runs cannot recreate stale assertions.
test_replacements = {
    "ExtensibleItemSystemTest.kt": (
        ('ItemCapacity(9, 999)', 'ItemCapacity(14, 999)'),
        ('"special_companion" to ItemCapacity(6, 20)', '"special_companion" to ItemCapacity(11, 20)'),
        ('"lucia_gift_inventory" to ItemCapacity(3, 100)', '"lucia_gift_inventory" to ItemCapacity(8, 100)'),
        ('"an_nhien_food_only" to ItemCapacity(2, 20)', '"an_nhien_food_only" to ItemCapacity(7, 20)'),
        ('"normal" to ItemCapacity(2, 2)', '"normal" to ItemCapacity(7, 2)'),
        ('assertEquals(InventoryProfile(7, 42), InventoryPolicy.profileFor(state, character.id))',
         'assertEquals(InventoryProfile(12, 42), InventoryPolicy.profileFor(state, character.id))'),
    ),
    "InventoryCapacityNewGameTest.kt": (
        ('assertEquals(InventoryPolicy.KAI.maxTypes, kai.inventoryCapacityMax)',
         'assertEquals(ItemSystem.capacityFor(state, KAI_ID).maxTypes, kai.inventoryCapacityMax)'),
    ),
    "InventoryPolicyTest.kt": (
        ('InventoryProfile(9, 999)', 'InventoryProfile(14, 999)'),
        ('InventoryProfile(6, 20)', 'InventoryProfile(11, 20)'),
        ('InventoryProfile(2, 2)', 'InventoryProfile(7, 2)'),
        ('fun kaiRejectsTenthTypeAndThousandthItem()', 'fun kaiRejectsFifteenthTypeAndThousandthItem()'),
        ('val items = (1..9).associate', 'val items = (1..14).associate'),
        ('ItemStack("i10", "Item 10")', 'ItemStack("i15", "Item 15")'),
        ('(iris.id to InventoryState(iris.id, (1..6).associate',
         '(iris.id to InventoryState(iris.id, (1..11).associate'),
        ('ItemStack("i7", "I7")', 'ItemStack("i12", "I12")'),
        ('("bob" to InventoryState("bob", mapOf("a" to ItemStack("a", "A", 2), "b" to ItemStack("b", "B", 1))))',
         '("bob" to InventoryState("bob", (listOf("a", "b", "c", "d", "e", "f", "g")).associate { it to ItemStack(it, it.uppercase(), if (it == "a") 2 else 1) }))'),
        ('ItemStack("c", "C")', 'ItemStack("h", "H")'),
    ),
    "SpecialFollowerInventoryPolicyTest.kt": (
        ('fun irisAndSyvialUseSixTypesAndTwentyPerType()', 'fun irisAndSyvialUseElevenTypesAndTwentyPerType()'),
        ('assertEquals(6, profile.maxTypes)', 'assertEquals(11, profile.maxTypes)'),
        ('fun seventhItemTypeIsRejectedForBothSpecialFollowers()', 'fun twelfthItemTypeIsRejectedForBothSpecialFollowers()'),
        ('val sixItems = (1..6).associate', 'val elevenItems = (1..11).associate'),
        ('InventoryState(ownerId, sixItems)', 'InventoryState(ownerId, elevenItems)'),
        ('ItemStack("item-7", "Item 7", 1)', 'ItemStack("item-12", "Item 12", 1)'),
    ),
    "LuciaFollowerTest.kt": (
        ('fun luciaGiftInventoryAllowsThreeTypesAndOneHundredEach()', 'fun luciaGiftInventoryAllowsEightTypesAndOneHundredEach()'),
        ('assertEquals(3, profile.maxTypes)', 'assertEquals(8, profile.maxTypes)'),
        ('val three = InventoryState(LUCIA_ID, mapOf(\n      "a" to ItemStack("a", "A", 100),\n      "b" to ItemStack("b", "B", 1),\n      "c" to ItemStack("c", "C", 1)\n    ))',
         'val eight = InventoryState(LUCIA_ID, (listOf("a", "b", "c", "d", "e", "f", "g", "h")).associate { id ->\n      id to ItemStack(id, id.uppercase(), if (id == "a") 100 else 1)\n    })'),
        ('InventoryPolicy.validateAddition(state, LUCIA_ID, three, ItemStack("d", "D", 1), 1)',
         'InventoryPolicy.validateAddition(state, LUCIA_ID, eight, ItemStack("i", "I", 1), 1)'),
    ),
    "AnNhienFollowerTest.kt": (
        ('fun inventoryAcceptsOnlyFoodAndHasTwoTypeSlots()', 'fun inventoryAcceptsOnlyFoodAndHasSevenTypeSlots()'),
        ('val twoFoods = InventoryState(AN_NHIEN_ID, mapOf(\n      "food-1" to food,\n      "food-2" to ItemStack("food-2", "Bánh", metadata = mapOf("category" to "FOOD"))\n    ))',
         'val sevenFoods = InventoryState(AN_NHIEN_ID, (1..7).associate { index ->\n      val id = "food-$index"\n      id to ItemStack(id, "Food $index", metadata = mapOf("category" to "FOOD"))\n    })'),
        ('        twoFoods,\n        ItemStack("food-3", "Kẹo", metadata = mapOf("category" to "FOOD")),',
         '        sevenFoods,\n        ItemStack("food-8", "Kẹo", metadata = mapOf("category" to "FOOD")),'),
    ),
}
for test_name, replacements in test_replacements.items():
    path = TESTS / test_name
    if not path.exists():
        continue
    source = path.read_text(encoding="utf-8")
    for old_value, new_value in replacements:
        source = source.replace(old_value, new_value)
    path.write_text(source, encoding="utf-8")

# The earlier compatibility rewrite updates the generated defeat ID but the
# historical assertion used a separate literal. Keep both sides identical.
if official_test.exists():
    source = official_test.read_text(encoding="utf-8")
    source = source.replace(
        'containsKey("entityLoot:pity-guaranteed-50")',
        'containsKey("entityLoot:pity-guaranteed-48")',
    )
    official_test.write_text(source, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Final regression contract: every inventory profile has five more type slots,
# loot has the requested flat +5pp, and retired MadGod state cannot survive load.
# ---------------------------------------------------------------------------
FINAL_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class MadGodRemovalLootCapacityTest {
  @Test fun everyInventoryProfileHasFiveAdditionalTypeSlots() {
    var state = LuciaCanon.ensure(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))
    assertEquals(14, ItemSystem.capacityFor(state, KAI_ID).maxTypes)
    assertEquals(11, ItemSystem.capacityFor(state, IRIS_ID).maxTypes)
    assertEquals(11, ItemSystem.capacityFor(state, SYVIAL_ID).maxTypes)
    assertEquals(8, ItemSystem.capacityFor(state, LUCIA_ID).maxTypes)
    assertEquals(7, ItemSystem.capacityFor(state, AN_NHIEN_ID).maxTypes)

    val future = CharacterState(id = "future", name = "Future")
    state = state.copy(characters = state.characters + (future.id to future))
    assertEquals(7, ItemSystem.capacityFor(state, future.id).maxTypes)
  }

  @Test fun lootGetsFlatFivePointBonusWithoutRemovingPity() {
    val state = GameState.initial().copy(world = GameState.initial().world + ("levelJson" to "{\"number\":0}"))
    assertEquals(7, EntityLootEngine.dropChancePercent(state))
    val prepared = LevelLootEngine.prepareAction(state, "FLAT-LOOT-5", ActionKind.SEARCH, "Level 0")
    val preview = requireNotNull(LevelLootEngine.preparedPreview(prepared))
    assertEquals(35, preview.baseThreshold)
    assertEquals(1, preview.pityTurn)
    assertEquals(635, preview.threshold)
  }

  @Test fun retiredMadGodCannotTriggerAndOldStateIsPurged() {
    assertFalse(MadGodCanon.cheat("/madgod"))
    assertNull(EquipmentCatalog.definition("madgod:set"))

    val base = GameState.initial()
    val kaiInventory = base.inventories.getValue(KAI_ID)
    val kaiEquipment = base.equipment.getValue(KAI_ID)
    val legacyItem = ItemStack("madgod:set", "MadGod Set", 1, metadata = mapOf("madGod" to "true"))
    val contaminated = base.copy(
      inventories = base.inventories + (KAI_ID to kaiInventory.copy(items = kaiInventory.items + (legacyItem.itemId to legacyItem))),
      equipment = base.equipment + (KAI_ID to kaiEquipment.copy(slots = kaiEquipment.slots + mapOf("weapon" to legacyItem.itemId, "armor" to legacyItem.itemId))),
      metadata = base.metadata + ("madGod.spawned" to "true")
    )
    val cleaned = CharacterEquipmentSystem.normalize(contaminated)
    assertFalse(cleaned.inventories.getValue(KAI_ID).items.keys.any { it.startsWith("madgod:") })
    assertFalse(cleaned.equipment.getValue(KAI_ID).slots.values.any { it.startsWith("madgod:") })
    assertFalse(cleaned.metadata.keys.any { it.startsWith("madgod", ignoreCase = true) })
  }
}
''', encoding="utf-8")

# Final sanity checks. The compatibility shim is intentionally allowed to keep
# the retired identifier so old generated code can compile; supported gameplay is not.
final_system = SYSTEM.read_text(encoding="utf-8")
final_facade = FACADE.read_text(encoding="utf-8")
final_main = MAIN.read_text(encoding="utf-8")
final_index = INDEX.read_text(encoding="utf-8")
final_items = ITEMS.read_text(encoding="utf-8")
final_item_system = ITEM_SYSTEM.read_text(encoding="utf-8")

for marker in (
    'private const val SCHEMA_VERSION = "2"',
    'private fun retiredMadGodId(',
    'const val BASE_DROP_BONUS_PERCENT = 5',
    'const val BASE_EXPLORATION_BONUS_BASIS_POINTS = 500',
    'BASE_DROP_BONUS_PERCENT + (killsWithoutDrop(state) + 1) * DROP_STEP_PERCENT',
    'base + BASE_EXPLORATION_BONUS_BASIS_POINTS + pity + follower',
    '"kai" to ItemCapacity(14, 999)',
    '"special_companion" to ItemCapacity(11, 20)',
    '"lucia_gift_inventory" to ItemCapacity(8, 100)',
    '"an_nhien_food_only" to ItemCapacity(7, 20)',
    '"normal" to ItemCapacity(7, 2)',
):
    haystack = "\n".join((final_system, final_items, final_item_system))
    if marker not in haystack:
        raise RuntimeError("Final removal/balance contract missing: " + marker)

for forbidden in (
    'id = MADGOD_SET_ID',
    'if (isMadGodEquipRequest(action))',
    'applyMadGodCheat(',
    'commandId = "$turnId:MADGOD:EQUIP"',
    'Kai_MadGod_snapshot_overlay.png',
    'avatars/MadGod.jpg',
    'const val CHEAT_CODE = "/madgod"',
):
    combined = "\n".join((final_system, final_facade, final_main, final_index, MADGOD.read_text(encoding="utf-8")))
    if forbidden in combined:
        raise RuntimeError("Retired MadGod gameplay marker survived: " + forbidden)

for path in (ASSETS / "Kai_MadGod_snapshot_overlay.png", ASSETS / "avatars/MadGod.jpg"):
    if path.exists():
        raise RuntimeError("Retired MadGod asset survived finalization: " + str(path))

print(
    "Final balance applied: MadGod gameplay retired and stale state purged; "
    "exploration/Entity loot +5 percentage points; inventory type slots +5 for every profile."
)
