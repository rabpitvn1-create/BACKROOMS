from pathlib import Path

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

# Legacy An Nhien regression keeps the FOOD-only rule but must follow the user's final
# cross-character capacity: every non-Kai character has 8 normal item types, x99 each.
an_path = TESTS / "AnNhienFollowerTest.kt"
an = an_path.read_text(encoding="utf-8")
start_marker = "  @Test fun inventoryAcceptsOnlyFoodAndHasTwoTypeSlots() {"
next_marker = "  @Test fun survivalCapacityIsThirtyPercentLowerThroughExistingPhysiologyPolicy() {"
if start_marker in an:
    start = an.index(start_marker)
    end = an.index(next_marker, start)
    replacement = '''  @Test fun inventoryAcceptsOnlyFoodAndHasEightTypeSlotsAt99Each() {
    val state = GameState.initial()
    val inventory = state.inventories[AN_NHIEN_ID]!!
    val food = ItemStack("food-1", "Lương khô", metadata = mapOf("category" to "FOOD"))
    val tool = ItemStack("tool-1", "Cờ lê", metadata = mapOf("category" to "TOOL"))
    assertEquals(null, InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, food, 1))
    assertEquals("an_nhien_food_only", InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, tool, 1))

    val sevenFoods = (1..7).associate { i ->
      "food-$i" to ItemStack("food-$i", "Food $i", metadata = mapOf("category" to "FOOD"))
    }
    val sevenState = InventoryState(AN_NHIEN_ID, sevenFoods)
    assertEquals(null, InventoryPolicy.validateAddition(
      state, AN_NHIEN_ID, sevenState,
      ItemStack("food-8", "Food 8", metadata = mapOf("category" to "FOOD")), 1
    ))

    val eightFoods = (1..8).associate { i ->
      "food-$i" to ItemStack("food-$i", "Food $i", metadata = mapOf("category" to "FOOD"))
    }
    val eightState = InventoryState(AN_NHIEN_ID, eightFoods)
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(
      state, AN_NHIEN_ID, eightState,
      ItemStack("food-9", "Food 9", metadata = mapOf("category" to "FOOD")), 1
    ))

    val at98 = InventoryState(AN_NHIEN_ID, mapOf(
      "food-1" to ItemStack("food-1", "Lương khô", 98, metadata = mapOf("category" to "FOOD"))
    ))
    assertEquals(null, InventoryPolicy.validateAddition(state, AN_NHIEN_ID, at98, food, 1))
    val at99 = InventoryState(AN_NHIEN_ID, mapOf(
      "food-1" to ItemStack("food-1", "Lương khô", 99, metadata = mapOf("category" to "FOOD"))
    ))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, AN_NHIEN_ID, at99, food, 1))
  }

'''
    an = an[:start] + replacement + an[end:]

if "inventoryAcceptsOnlyFoodAndHasEightTypeSlotsAt99Each" not in an:
    raise RuntimeError("An Nhien final inventory regression was not aligned")
an_path.write_text(an, encoding="utf-8")

# CharacterCanonR07 generates this test during the patch chain. The user instruction supersedes
# Lucia R03's older x100 stack limit while preserving her 8 item-type capacity.
lucia_path = TESTS / "CharacterCanonR07Test.kt"
if not lucia_path.is_file():
    raise RuntimeError("Generated CharacterCanonR07Test.kt missing")
lucia = lucia_path.read_text(encoding="utf-8")
lucia = lucia.replace("@Test fun luciaInventoryCapacityMatchesR03()", "@Test fun luciaInventoryCapacityMatchesFinalRule()")
lucia = lucia.replace("assertEquals(100, profile.maxPerType)", "assertEquals(99, profile.maxPerType)")
if "luciaInventoryCapacityMatchesFinalRule" not in lucia or "assertEquals(99, profile.maxPerType)" not in lucia:
    raise RuntimeError("Lucia final inventory regression was not aligned")
lucia_path.write_text(lucia, encoding="utf-8")

print("Legacy character inventory regressions aligned: An Nhien FOOD-only 8x99; Lucia 8x99.")
