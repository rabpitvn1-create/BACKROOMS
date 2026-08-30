from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ITEMS = CORE / "ItemCatalog.kt"
TEST = TESTS / "EntityLootPlusThreeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Issue #122 asks for another +3 percentage points specifically for monster/Entity
# drops. The existing finalized balance is +5 flat plus the +2-per-failure pity
# step, so the first-kill chance moves from 7% to 10%. Environment loot keeps its
# independent +5-point bonus unchanged.
items = ITEMS.read_text(encoding="utf-8")
items = replace_once(
    items,
    "  const val BASE_DROP_BONUS_PERCENT = 5\n",
    "  const val BASE_DROP_BONUS_PERCENT = 8\n",
    "Issue 122 Entity drop bonus",
)
ITEMS.write_text(items, encoding="utf-8")

# Keep historical generated assertions aligned with the new Entity baseline.
for path in TESTS.glob("*.kt"):
    source = path.read_text(encoding="utf-8")
    updated = source.replace(
        "assertEquals(7, EntityLootEngine.dropChancePercent(",
        "assertEquals(10, EntityLootEngine.dropChancePercent(",
    )
    if updated != source:
        path.write_text(updated, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityLootPlusThreeTest {
  private class FixedRng(private val chanceRoll: Int, private val itemRoll: Int = 0) : LootRng {
    override fun nextInt(bound: Int): Int = if (bound == 100) chanceRoll.coerceIn(0, 99) else itemRoll.mod(bound)
  }

  @Test fun firstEntityKillStartsAtTenPercent() {
    val state = GameState.initial()
    assertEquals(10, EntityLootEngine.dropChancePercent(state))
  }

  @Test fun exactTenPercentBoundaryIsApplied() {
    val state = GameState.initial()

    val success = EntityLootEngine.onDefeat(state, "issue-122-success", FixedRng(9))
    assertNotEquals("NONE", success.world["entityLootRolled:issue-122-success"])
    assertNotNull(success.world["entityLoot:issue-122-success"])

    val failure = EntityLootEngine.onDefeat(state, "issue-122-failure", FixedRng(10))
    assertEquals("NONE", failure.world["entityLootRolled:issue-122-failure"])
    assertNull(failure.world["entityLoot:issue-122-failure"])
    assertEquals(12, EntityLootEngine.dropChancePercent(failure))
  }

  @Test fun duplicateDefeatIdCannotRerollLoot() {
    val state = EntityLootEngine.onDefeat(GameState.initial(), "same-defeat", FixedRng(10))
    val rerolled = EntityLootEngine.onDefeat(state, "same-defeat", FixedRng(0))
    assertEquals(state, rerolled)
    assertEquals(12, EntityLootEngine.dropChancePercent(rerolled))
  }
}
''', encoding="utf-8")

combined = ITEMS.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    "const val BASE_DROP_BONUS_PERCENT = 8",
    "class EntityLootPlusThreeTest",
    "assertEquals(10, EntityLootEngine.dropChancePercent(state))",
    "assertEquals(12, EntityLootEngine.dropChancePercent(failure))",
):
    if marker not in combined:
        raise RuntimeError("Issue 122 Entity loot contract missing: " + marker)

# Exploration uses a separate basis-point constant and must not receive this extra
# monster-only +3 percentage-point adjustment.
if "const val BASE_EXPLORATION_BONUS_BASIS_POINTS = 500" not in items:
    raise RuntimeError("Issue 122 unexpectedly changed environment loot bonus")

print("Issue #122 applied: Entity drops gain +3 percentage points; first-kill chance is now 10% with pity preserved.")
