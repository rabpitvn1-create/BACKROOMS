from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

an_path = TESTS / "AnNhienFollowerTest.kt"
an = an_path.read_text(encoding="utf-8")
pattern = re.compile(
    r'''  @Test fun inventoryAcceptsOnlyFoodAndHasTwoTypeSlots\(\) \{.*?\n  \}\n\n  @Test fun survivalCapacityIsThirtyPercentLowerThroughExistingPhysiologyPolicy''',
    re.S,
)
replacement = '''  @Test fun inventoryAcceptsOnlyFoodAndHasEightTypeSlots() {
    val state = GameState.initial()
    val inventory = state.inventories[AN_NHIEN_ID]!!
    val food = ItemStack("food-1", "Lương khô", metadata = mapOf("category" to "FOOD"))
    val tool = ItemStack("tool-1", "Cờ lê", metadata = mapOf("category" to "TOOL"))
    assertEquals(null, InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, food, 1))
    assertEquals("an_nhien_food_only", InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, tool, 1))

    val eightFoods = InventoryState(AN_NHIEN_ID, (1..8).associate {
      "food-$it" to ItemStack("food-$it", "Food $it", metadata = mapOf("category" to "FOOD"))
    })
    assertEquals(
      "inventory_slot_limit",
      InventoryPolicy.validateAddition(
        state,
        AN_NHIEN_ID,
        eightFoods,
        ItemStack("food-9", "Food 9", metadata = mapOf("category" to "FOOD")),
        1
      )
    )
  }

  @Test fun survivalCapacityIsThirtyPercentLowerThroughExistingPhysiologyPolicy'''
an, count = pattern.subn(replacement, an, count=1)
if count != 1:
    raise RuntimeError(f"An Nhien inventory regression anchor count: {count}")
an_path.write_text(an, encoding="utf-8")

lucia_path = TESTS / "CharacterCanonR07Test.kt"
if not lucia_path.is_file():
    raise RuntimeError("CharacterCanonR07Test.kt missing after R07 patch")
lucia = lucia_path.read_text(encoding="utf-8")
lucia = lucia.replace("@Test fun luciaInventoryCapacityMatchesR03()", "@Test fun luciaInventoryCapacityMatchesFinalRule()", 1)
old = "    assertEquals(100, profile.maxPerType)"
new = "    assertEquals(99, profile.maxPerType)"
if new not in lucia:
    if lucia.count(old) != 1:
        raise RuntimeError(f"Lucia stack-limit regression anchor count: {lucia.count(old)}")
    lucia = lucia.replace(old, new, 1)
if "assertEquals(8, profile.maxTypes)" not in lucia or new not in lucia:
    raise RuntimeError("Lucia final inventory regression contract missing")
lucia_path.write_text(lucia, encoding="utf-8")

print("Inventory regression tests aligned: An Nhiên stays FOOD-only at 8x99; Lucia and all non-Kai characters use 8x99.")
