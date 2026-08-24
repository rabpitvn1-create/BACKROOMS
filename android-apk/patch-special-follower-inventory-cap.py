from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

policy_path = CORE / "InventoryPolicy.kt"
policy = policy_path.read_text(encoding="utf-8")

old = '  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 4, maxPerType = 20)\n'
new = '  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 6, maxPerType = 20)\n'
if old in policy:
    policy = policy.replace(old, new, 1)
elif new not in policy:
    raise RuntimeError("Special companion inventory profile anchor missing")

policy_path.write_text(policy, encoding="utf-8")

test_path = TESTS / "SpecialFollowerInventoryPolicyTest.kt"
test_path.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerInventoryPolicyTest {
  @Test fun irisAndSyvialUseSixTypesAndTwentyPerType() {
    val state = GameState.initial()
    for (id in listOf(IRIS_ID, SYVIAL_ID)) {
      val profile = InventoryPolicy.profileFor(state, id)
      assertEquals(6, profile.maxTypes)
      assertEquals(20, profile.maxPerType)
    }
  }

  @Test fun seventhItemTypeIsRejectedForBothSpecialFollowers() {
    val state = GameState.initial()
    val sixItems = (1..6).associate { index ->
      val id = "item-$index"
      id to ItemStack(id, "Item $index", 1)
    }
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(ownerId, sixItems)
      val error = InventoryPolicy.validateAddition(
        state,
        ownerId,
        inventory,
        ItemStack("item-7", "Item 7", 1),
        1
      )
      assertEquals("inventory_slot_limit", error)
    }
  }

  @Test fun eachExistingTypeCanReachTwentyButNotTwentyOne() {
    val state = GameState.initial()
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(
        ownerId,
        mapOf(ItemCatalog.ALMOND_WATER to ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 19))
      )
      assertNull(
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 1),
          1
        )
      )
      assertEquals(
        "inventory_stack_limit",
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 2),
          2
        )
      )
    }
  }
}
''', encoding="utf-8")

combined = policy + test_path.read_text(encoding="utf-8")
for marker in [
    'SPECIAL_COMPANION = InventoryProfile(maxTypes = 6, maxPerType = 20)',
    'assertEquals(6, profile.maxTypes)',
    'assertEquals(20, profile.maxPerType)',
    '"inventory_slot_limit"',
    '"inventory_stack_limit"',
]:
    if marker not in combined:
        raise RuntimeError(f"Special follower inventory contract missing: {marker}")

print("Iris/Syvial inventory policy updated: 6 item types maximum, 20 units maximum per type.")
