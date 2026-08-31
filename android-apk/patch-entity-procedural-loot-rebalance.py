from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


# A small Core policy owns both the deterministic 70% scaling rule and procedural eligibility.
(CORE / "EntityEncounterPolicy.kt").write_text(r'''package com.rabpit.backroom.core

/** Safe Entity encounter policy. It consumes no puzzle progress or hidden escape data. */
object EntityEncounterPolicy {
  const val SCALE_NUMERATOR = 7
  const val SCALE_DENOMINATOR = 10

  /** Integer floor is the single deterministic rounding rule; the RNG denominator stays unchanged. */
  @JvmStatic fun scaledThreshold(currentThreshold: Int): Int {
    if (currentThreshold <= 0) return 0
    return ((currentThreshold.toLong() * SCALE_NUMERATOR) / SCALE_DENOMINATOR)
      .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  @JvmStatic fun randomEncounterAllowed(constraints: ProceduralGenerationConstraints?): Boolean =
    constraints?.allowEntities ?: true
}
''', encoding="utf-8")


# Expose only visible encounter inputs to the Android bridge. Hidden blueprint/facts/actions never
# enter this object.
facade_path = CORE / "GameCoreFacade.kt"
facade = facade_path.read_text(encoding="utf-8")
anchor = '''        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        LevelLootEngine.preparedPreview(state)?.let { loot ->
'''
replacement = '''        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        val level = state.levelInstance
        val definition = level?.levelId?.let(levelRegistry::get)
        put("entityEncounter", JSONObject().apply {
          put("allowed", EntityEncounterPolicy.randomEncounterAllowed(definition?.generationConstraints))
          if (level != null) {
            put("zoneId", level.currentZoneId)
            put("zoneTags", JSONArray(level.zones[level.currentZoneId]?.tags.orEmpty().sorted()))
            put("environmentTags", JSONArray(level.environmentTags.sorted()))
          }
        })
        LevelLootEngine.preparedPreview(state)?.let { loot ->
'''
facade = replace_once(facade, anchor, replacement, "sanitized encounter context")
facade_path.write_text(facade, encoding="utf-8")


# Scale every actual random Entity channel at its existing threshold call. No RNG or selection-pool
# weighting changes are introduced.
main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    "import com.rabpit.backroom.core.GameCoreFacade;\n",
    "import com.rabpit.backroom.core.GameCoreFacade;\nimport com.rabpit.backroom.core.EntityEncounterPolicy;\n",
    "EntityEncounterPolicy Java import",
)
main = replace_once(
    main,
    '''    boolean entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true);
''',
    '''    JSONObject runtimeContext = new JSONObject(requireGameCore().currentActionContext());
    JSONObject proceduralEntityContext = runtimeContext.optJSONObject("entityEncounter");
    boolean proceduralEntitiesAllowed = proceduralEntityContext == null || proceduralEntityContext.optBoolean("allowed", true);
    boolean entityAllowed = (flags == null || flags.optBoolean("entityEncountersAllowed", true)) && proceduralEntitiesAllowed;
''',
    "procedural allowEntities gate",
)
for old, new, label in [
    ('thresholdRoll("diepMinhEncounter", 10000, 300,', 'thresholdRoll("diepMinhEncounter", 10000, EntityEncounterPolicy.scaledThreshold(300),', "Diep Minh"),
    ('thresholdRoll("monsterXEncounter", 10000, 1000,', 'thresholdRoll("monsterXEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),', "Monster X"),
    ('thresholdRoll("johnDoeEncounter", 10000, 1000,', 'thresholdRoll("johnDoeEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),', "John Doe"),
    ('thresholdRoll("scp173Encounter", 10000, 500,', 'thresholdRoll("scp173Encounter", 10000, EntityEncounterPolicy.scaledThreshold(500),', "SCP-173"),
    ('thresholdRoll("violetWardenEncounter", 10000, 1000,', 'thresholdRoll("violetWardenEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),', "Violet Warden"),
    ('thresholdRoll("kaiDevilWithinEncounter", 10000, 200,', 'thresholdRoll("kaiDevilWithinEncounter", 10000, EntityEncounterPolicy.scaledThreshold(200),', "Kai Devil Within"),
    ('thresholdRoll("entityEncounter", 10000, entityThresholds[level],', 'thresholdRoll("entityEncounter", 10000, EntityEncounterPolicy.scaledThreshold(entityThresholds[level]),', "normal roaming"),
]:
    main = replace_once(main, old, new, f"scale {label}")
MAIN.write_text(main, encoding="utf-8")


# Guaranteed one-item selection followed by the existing authoritative acquisition path. The marker
# stores the chosen item before acquisition, so a rejected pickup can retry the same item only.
items_path = CORE / "ItemCatalog.kt"
items = items_path.read_text(encoding="utf-8")
start = items.index("object EntityLootEngine {\n")
end = items.index("object LevelLootEngine {\n", start)
entity_engine = r'''object EntityLootEngine {
  const val DROP_CHANCE_PERCENT = 100

  fun dropChancePercent(state: GameState): Int = DROP_CHANCE_PERCENT

  fun onDefeat(state: GameState, defeatId: String, rng: LootRng): GameState {
    if (defeatId.isBlank()) return state
    val marker = "entityLootRolled:$defeatId"
    val lootId = "entityLoot:$defeatId"
    val alreadySelected = state.world[marker]

    if (alreadySelected != null) {
      if (state.world[lootId] == null) return state
      return WorldLootAcquisition.acquire(state, lootId, KAI_ID).state
    }

    val item = ItemCatalog.items[rng.nextInt(ItemCatalog.items.size)].stack()
    val selected = state.copy(world = state.world + mapOf(
      marker to item.itemId,
      lootId to "${item.itemId}|${item.name}|1|ENTITY_DROP"
    ))
    return WorldLootAcquisition.acquire(selected, lootId, KAI_ID).state
  }
}

'''
items = items[:start] + entity_engine + items[end:]
items_path.write_text(items, encoding="utf-8")


# Acquisition provenance is non-physical stack metadata. Ignoring it for stack compatibility lets a
# guaranteed drop increment an existing catalog stack while retaining the latest authoritative source.
content_path = CORE / "ItemContent.kt"
content = content_path.read_text(encoding="utf-8")
content = replace_once(
    content,
    '''    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
''',
    '''    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId", "acquisitionSource"
''',
    "acquisitionSource stack metadata",
)
content_path.write_text(content, encoding="utf-8")


# Centralized combat text reads only the Core-recorded result. All defeat exits already converge on
# clearCombatOnly, which calls EntityLootEngine exactly-once by encounter/defeat ID.
combat_path = CORE / "CombatRuntime.kt"
combat = combat_path.read_text(encoding="utf-8")
clear_anchor = '''  private fun clearCombatOnly(state: GameState): GameState {
'''
helper = '''  private fun defeatLootNarration(state: GameState, defeatId: String): String {
    val itemId = state.world["entityLootRolled:$defeatId"] ?: return ""
    val itemName = ItemCatalog.find(itemId)?.name ?: itemId
    return if (state.world["entityLoot:$defeatId"] == null)
      " $itemName đã rơi ra và được tự động thêm vào Inventory của Kai."
    else " $itemName đã được Core khóa cho lần nhận lại qua InventoryEngine."
  }

  private fun clearCombatOnly(state: GameState): GameState {
'''
combat = replace_once(combat, clear_anchor, helper, "combat loot feedback helper")
old_suffix = 'localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)'
new_suffix = 'localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt." + defeatLootNarration(cleared, c.encounterId), entityDestroyed = true)'
count = combat.count(old_suffix)
if count != 4:
    raise RuntimeError(f"combat defeat exits: expected 4, found {count}")
combat = combat.replace(old_suffix, new_suffix)
combat_path.write_text(combat, encoding="utf-8")


# Replace stale percentage/pity tests with focused guaranteed-drop and exactly-once coverage.
(TESTS / "EntityLootPlusThreeTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityLootPlusThreeTest {
  private class SequenceRng(private vararg val values: Int) : LootRng {
    var calls = 0
    override fun nextInt(bound: Int): Int = values[calls++].mod(bound)
  }

  @Test fun everyDefeatDropsExactlyOneCatalogItemAndAutoPicksItUp() {
    val rng = SequenceRng(6)
    val result = EntityLootEngine.onDefeat(GameState.initial(), "guaranteed", rng)
    val itemId = result.world.getValue("entityLootRolled:guaranteed")
    assertEquals(100, EntityLootEngine.dropChancePercent(result))
    assertEquals(1, rng.calls)
    assertTrue(itemId in ItemCatalog.ids)
    assertFalse(result.world.containsKey("entityLoot:guaranteed"))
    val stack = result.inventories.getValue(KAI_ID).items.getValue(itemId)
    assertEquals(1, stack.quantity)
    assertEquals("ENTITY_DROP", stack.metadata["acquisitionSource"])
  }

  @Test fun sameDefeatIdCannotRerollOrDuplicateInventory() {
    val rng = SequenceRng(7)
    val first = EntityLootEngine.onDefeat(GameState.initial(), "same-defeat", rng)
    val itemId = first.world.getValue("entityLootRolled:same-defeat")
    val quantity = first.inventories.getValue(KAI_ID).items.getValue(itemId).quantity
    val duplicate = EntityLootEngine.onDefeat(first, "same-defeat", LootRng { fail("must not reroll"); 0 })
    assertEquals(first, duplicate)
    assertEquals(quantity, duplicate.inventories.getValue(KAI_ID).items.getValue(itemId).quantity)
  }

  @Test fun differentDefeatIdsProduceTwoAcquisitions() {
    val first = EntityLootEngine.onDefeat(GameState.initial(), "defeat-a", SequenceRng(8))
    val second = EntityLootEngine.onDefeat(first, "defeat-b", SequenceRng(8))
    val itemId = second.world.getValue("entityLootRolled:defeat-a")
    assertEquals(itemId, second.world.getValue("entityLootRolled:defeat-b"))
    assertEquals(2, second.inventories.getValue(KAI_ID).items.getValue(itemId).quantity)
  }
}
''', encoding="utf-8")


official_path = TESTS / "OfficialItemSystemTest.kt"
official = official_path.read_text(encoding="utf-8")
first_test = official.index("  @Test fun entityLootStartsAtTwoPercentTotalOneItemAndIdempotent()")
next_test = official.index("  @Test fun playerAndUnprovenGeminiCannotManufacturePickup()", first_test)
official = official[:first_test] + official[next_test:]
pity_start = official.index("  @Test fun entityLootPityAddsTwoPointsPerMissAndGuaranteesKillFortySix()")
pity_end = official.index("\n  }\n", pity_start) + len("\n  }\n")
official = official[:pity_start] + official[pity_end:]
official_path.write_text(official, encoding="utf-8")


removal_path = TESTS / "MadGodRemovalLootCapacityTest.kt"
removal = removal_path.read_text(encoding="utf-8").replace(
    "assertEquals(10, EntityLootEngine.dropChancePercent(state))",
    "assertEquals(100, EntityLootEngine.dropChancePercent(state))",
)
removal_path.write_text(removal, encoding="utf-8")


(TESTS / "EntityEncounterPolicyTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityEncounterPolicyTest {
  @Test fun everyRandomThresholdUsesSeventyPercentWithIntegerFloor() {
    assertEquals(listOf(1263, 1400, 1505, 1505, 1267, 1540, 1263),
      listOf(1805, 2000, 2150, 2150, 1810, 2200, 1805).map(EntityEncounterPolicy::scaledThreshold))
    assertEquals(210, EntityEncounterPolicy.scaledThreshold(300))
    assertEquals(700, EntityEncounterPolicy.scaledThreshold(1000))
    assertEquals(350, EntityEncounterPolicy.scaledThreshold(500))
    assertEquals(140, EntityEncounterPolicy.scaledThreshold(200))
  }

  @Test fun proceduralConstraintIsTheOnlyLevelInputToEligibility() {
    assertFalse(EntityEncounterPolicy.randomEncounterAllowed(ProceduralGenerationConstraints(allowEntities = false)))
    assertTrue(EntityEncounterPolicy.randomEncounterAllowed(ProceduralGenerationConstraints(allowEntities = true)))
    assertTrue(EntityEncounterPolicy.randomEncounterAllowed(null))
  }

  @Test fun arbitraryLevelIdsDoNotEnterEncounterPolicy() {
    val definition = LevelDefinition(
      id = "level:future/alpha", name = "Future", initialZoneId = "z",
      zones = mapOf("z" to ZoneState("z", "Zone")),
      escapeBlueprint = EscapeBlueprintState("hidden", emptySet(), emptyList()), evidence = emptyMap(),
      generationConstraints = ProceduralGenerationConstraints(allowEntities = false)
    )
    assertFalse(EntityEncounterPolicy.randomEncounterAllowed(definition.generationConstraints))
  }
}
''', encoding="utf-8")


print("Procedural Entity integration, 70% random encounter scaling, and guaranteed auto-pickup installed.")
