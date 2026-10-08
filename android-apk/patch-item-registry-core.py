from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
REDUCER = CORE / "StateReducer.kt"
ENGINES = CORE / "Engines.kt"
COMBAT = CORE / "Combat93Runtime.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
REGISTRY = CORE / "ItemRegistry.kt"
SOURCE_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemSourceAuthorityFinalTest.kt"
INVENTORY_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/InventoryAuthorityRegressionTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


registry = REGISTRY.read_text(encoding="utf-8")
for marker in (
    'object ItemRegistry',
    'ITEM_ALMOND_WATER_ID = "almond-water"',
    'ITEM_BANDAGE_ID = "medical:bandage"',
    'ITEM_ANTISEPTIC_ID = "medical:antiseptic"',
    'ITEM_GREEK_FIRE_ID = "backrooms:greek-fire"',
    'ITEM_LIQUID_PAIN_ID = "backrooms:liquid-pain"',
):
    if marker not in registry:
        raise RuntimeError("Item registry marker missing: " + marker)


facade = FACADE.read_text(encoding="utf-8")
old_available = '''      val name = item.optString("name").trim()
      val id = item.optString("id").trim()
      if (name.isBlank() && id.isBlank()) continue
      available += index to item
'''
new_available = '''      val id = item.optString("id").trim()
      if (id.isBlank() || !ItemRegistry.contains(id)) continue
      available += index to item
'''
facade = replace_once(facade, old_available, new_available, "registry-gated world item selection")

old_selected = '''    val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
    val take = 1
    val remaining = quantity - take
    val instanceId = item.optString("instanceId").ifBlank { "world:${item.optString("id").ifBlank { stableItemId(item.optString("name")) }}:$index" }
    val metadata = jsonObjectStrings(item.optJSONObject("metadata")) + mapOf(
'''
new_selected = '''    val itemId = item.optString("id").trim()
    val definition = ItemRegistry.definition(itemId) ?: return null
    val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
    val take = 1
    val remaining = quantity - take
    val instanceId = item.optString("instanceId").ifBlank { "world:${definition.id}:$index" }
    val metadata = jsonObjectStrings(item.optJSONObject("metadata")) + mapOf(
'''
facade = replace_once(facade, old_selected, new_selected, "registry world pickup identity")

old_return = '''      itemId = item.optString("id").ifBlank { stableItemId(item.optString("name")) },
      itemName = item.optString("name").ifBlank { item.optString("id") },
'''
new_return = '''      itemId = definition.id,
      itemName = definition.displayName,
'''
facade = replace_once(facade, old_return, new_return, "registry world pickup projection")
if 'item.optString("id").ifBlank { stableItemId(item.optString("name")) }' in facade:
    raise RuntimeError("Dynamic world Item ID fallback survived in GameCoreFacade")
FACADE.write_text(facade, encoding="utf-8")


reducer = REDUCER.read_text(encoding="utf-8")
old_reducer = '''      if (origin != null && origin !in setOf("ENTITY", "CHEST")) {
        return ValidationResult(false, "item_source_not_authoritative")
      }
      if (origin == "CHEST" && command.metadata["chestId"].isNullOrBlank()) {
'''
new_reducer = '''      if (origin != null && origin !in setOf("ENTITY", "CHEST")) {
        return ValidationResult(false, "item_source_not_authoritative")
      }
      if (origin in setOf("ENTITY", "CHEST") && !ItemRegistry.contains(command.itemId)) {
        return ValidationResult(false, "unknown_item_id")
      }
      if (origin == "CHEST" && command.metadata["chestId"].isNullOrBlank()) {
'''
reducer = replace_once(reducer, old_reducer, new_reducer, "reducer Item registry gate")
REDUCER.write_text(reducer, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
old_engine = '''  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (ItemContentRules.hasForbiddenPreciseAmount(command.itemName)) return invalid(state, "precise_content_amount_forbidden")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val item = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))
    return when (command.operation) {
'''
new_engine = '''  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    val acquisitionOrigin = command.metadata["itemOrigin"]?.trim()?.uppercase()
    if (command.operation == ItemCommand.Operation.PICKUP && acquisitionOrigin != null) {
      if (acquisitionOrigin !in setOf("ENTITY", "CHEST")) return invalid(state, "item_source_not_authoritative")
      if (!ItemRegistry.contains(command.itemId)) return invalid(state, "unknown_item_id")
      if (acquisitionOrigin == "CHEST" && command.metadata["chestId"].isNullOrBlank()) return invalid(state, "chest_source_missing")
    }
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val item = if (command.operation == ItemCommand.Operation.PICKUP && acquisitionOrigin in setOf("ENTITY", "CHEST")) {
      ItemRegistry.stack(command.itemId, command.quantity, command.metadata)
    } else {
      ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))
    }
    if (ItemContentRules.hasForbiddenPreciseAmount(item.name)) return invalid(state, "precise_content_amount_forbidden")
    return when (command.operation) {
'''
engines = replace_once(engines, old_engine, new_engine, "InventoryEngine registry gate")
ENGINES.write_text(engines, encoding="utf-8")


combat = COMBAT.read_text(encoding="utf-8")
old_entity_item = '''      val bandage = drop.getString("id") == "bandage"
      val item = ItemContentRules.normalize(ItemStack(
        if (bandage) BANDAGE_ID else "almond-water",
        if (bandage) HealingItems.BANDAGE_NAME else "Almond Water",
        metadata = mapOf("itemOrigin" to "ENTITY", "omnivaultOriginal" to "true", "consumedOnUse" to "true") +
          if (bandage) emptyMap() else mapOf("physiologyEffect" to "WATER")))
'''
new_entity_item = '''      val bandage = drop.getString("id") == "bandage"
      val itemId = if (bandage) ItemRegistry.ITEM_BANDAGE_ID else ItemRegistry.ITEM_ALMOND_WATER_ID
      val item = ItemRegistry.stack(
        itemId,
        metadata = mapOf("itemOrigin" to "ENTITY", "omnivaultOriginal" to "true")
      )
'''
combat = replace_once(combat, old_entity_item, new_entity_item, "Entity loot registry construction")

old_overflow = '''        items.put(JSONObject().put("id", item.itemId).put("name", item.name).put("quantity", 1)
          .put("available", true).put("instanceId", "combat:${revision(state)}:$index"))
'''
new_overflow = '''        items.put(JSONObject().put("id", item.itemId).put("name", item.name).put("quantity", 1)
          .put("available", true).put("instanceId", "combat:${revision(state)}:$index")
          .put("metadata", JSONObject(item.metadata)))
'''
combat = replace_once(combat, old_overflow, new_overflow, "Entity overflow provenance")
COMBAT.write_text(combat, encoding="utf-8")


omnivault = OMNIVAULT.read_text(encoding="utf-8")
old_world_source = '''  private fun worldSource(state: GameState, c: OmnivaultCommand): ScanSource? {
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: return null) }.getOrNull() ?: return null
    val items = flags.optJSONArray("worldItems") ?: return null
    var best: Pair<Int, JSONObject>? = null
    var bestScore = 0
    for (index in 0 until items.length()) {
      val raw = items.optJSONObject(index) ?: continue
      if (!raw.optBoolean("available", true)) continue
      val id = raw.optString("id").trim()
      val name = raw.optString("name").trim()
      val score = matchScore(id, name, c.itemId, c.itemName)
      if (score > bestScore) {
        bestScore = score
        best = index to raw
      }
    }
    val selected = best ?: return null
    val index = selected.first
    val raw = selected.second
    val name = raw.optString("name").trim().ifBlank { c.itemName }
    val id = raw.optString("id").trim().ifBlank { stableItemId(name) }
    val metadata = linkedMapOf<String, String>()
    raw.optJSONObject("metadata")?.let { json -> json.keys().forEach { key -> metadata[key] = json.optString(key) } }
    val instanceId = raw.optString("instanceId").trim().ifBlank { "world:${safe(id)}:$index" }
    metadata["worldInstanceId"] = instanceId
    metadata["itemOrigin"] = "WORLD"
    metadata["omnivaultOriginal"] = "true"
    for (key in listOf("isLiving", "living", "isLargeAssembly", "largeAssembly")) {
      if (raw.has(key)) metadata[key] = raw.optBoolean(key, false).toString()
    }
    val item = ItemContentRules.normalize(ItemStack(id, name, raw.optInt("quantity", 1).coerceAtLeast(1), metadata = metadata))
    return ScanSource(SourceKind.WORLD, item.itemId, item, index)
  }

'''
new_world_source = '''  private fun worldSource(state: GameState, c: OmnivaultCommand): ScanSource? {
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: return null) }.getOrNull() ?: return null
    val items = flags.optJSONArray("worldItems") ?: return null
    var best: Pair<Int, JSONObject>? = null
    var bestScore = 0
    for (index in 0 until items.length()) {
      val raw = items.optJSONObject(index) ?: continue
      if (!raw.optBoolean("available", true)) continue
      val id = raw.optString("id").trim()
      val rawMetadata = raw.optJSONObject("metadata")
      val origin = rawMetadata?.optString("itemOrigin", "")?.trim()?.uppercase().orEmpty()
      val authoritative = origin == "ENTITY" ||
        (origin == "CHEST" && rawMetadata?.optString("chestId", "")?.trim()?.isNotEmpty() == true)
      if (!authoritative || id.isBlank() || !ItemRegistry.contains(id)) continue
      val name = ItemRegistry.displayName(id) ?: continue
      val score = matchScore(id, name, c.itemId, c.itemName)
      if (score > bestScore) {
        bestScore = score
        best = index to raw
      }
    }
    val selected = best ?: return null
    val index = selected.first
    val raw = selected.second
    val id = raw.optString("id").trim()
    val definition = ItemRegistry.definition(id) ?: return null
    val metadata = linkedMapOf<String, String>()
    raw.optJSONObject("metadata")?.let { json -> json.keys().forEach { key -> metadata[key] = json.optString(key) } }
    val instanceId = raw.optString("instanceId").trim().ifBlank { "world:${safe(definition.id)}:$index" }
    metadata["worldInstanceId"] = instanceId
    metadata["omnivaultOriginal"] = "true"
    for (key in listOf("isLiving", "living", "isLargeAssembly", "largeAssembly")) {
      if (raw.has(key)) metadata[key] = raw.optBoolean(key, false).toString()
    }
    val item = ItemRegistry.stack(definition.id, raw.optInt("quantity", 1).coerceAtLeast(1), metadata)
    return ScanSource(SourceKind.WORLD, item.itemId, item, index)
  }

'''
omnivault = replace_once(omnivault, old_world_source, new_world_source, "Omnivault authoritative world source")
OMNIVAULT.write_text(omnivault, encoding="utf-8")


source_test = SOURCE_TEST.read_text(encoding="utf-8")
source_test = source_test.replace(
    'itemId = "entity-water", itemName = "Almond Water", metadata = mapOf("itemOrigin" to "ENTITY")',
    'itemId = ItemRegistry.ITEM_ALMOND_WATER_ID, itemName = "Almond Water", metadata = mapOf("itemOrigin" to "ENTITY")'
)
source_test = source_test.replace(
    'itemId = "chest-bandage", itemName = "Băng gạc",\n      metadata = mapOf("itemOrigin" to "CHEST", "chestId" to "chest:test:1")',
    'itemId = ItemRegistry.ITEM_BANDAGE_ID, itemName = "Băng gạc",\n      metadata = mapOf("itemOrigin" to "CHEST", "chestId" to "chest:test:1")'
)
SOURCE_TEST.write_text(source_test, encoding="utf-8")

inventory_test = INVENTORY_TEST.read_text(encoding="utf-8")
inventory_test = inventory_test.replace("bandagePickupDoesNotRunContentUseValidation", "bandageEntityPickupDoesNotRunContentUseValidation")
inventory_test = inventory_test.replace('"world-bandage-pickup"', '"entity-bandage-pickup"')
inventory_test = inventory_test.replace(
    'metadata = mapOf("worldInstanceId" to "world:bandage:1", "itemOrigin" to "WORLD", "omnivaultOriginal" to "true")',
    'metadata = mapOf("entityDropId" to "entity:bandage:1", "itemOrigin" to "ENTITY", "omnivaultOriginal" to "true")'
)
INVENTORY_TEST.write_text(inventory_test, encoding="utf-8")


combined = "\n".join(path.read_text(encoding="utf-8") for path in (
    REGISTRY, FACADE, REDUCER, ENGINES, COMBAT, OMNIVAULT, SOURCE_TEST, INVENTORY_TEST
))
for marker in (
    "object ItemRegistry",
    "unknown_item_id",
    "ItemRegistry.stack(command.itemId",
    "ItemRegistry.ITEM_ALMOND_WATER_ID",
    "ItemRegistry.ITEM_BANDAGE_ID",
    'origin == "CHEST"',
    'origin == "ENTITY"',
    '.put("metadata", JSONObject(item.metadata))',
    "bandageEntityPickupDoesNotRunContentUseValidation",
):
    if marker not in combined:
        raise RuntimeError("Final Item registry contract missing: " + marker)

for retired in (
    'item.optString("id").ifBlank { stableItemId(item.optString("name")) }',
    'metadata["itemOrigin"] = "WORLD"',
):
    if retired in combined:
        raise RuntimeError("Retired dynamic Item identity/source survived: " + retired)

print("Core Item registry applied: runtime Item identity is registry-owned; ENTITY/CHEST must use registered IDs.")
