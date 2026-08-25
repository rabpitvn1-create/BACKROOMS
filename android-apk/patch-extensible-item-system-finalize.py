from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
POLICY = CORE / "InventoryPolicy.kt"
ENGINES = CORE / "Engines.kt"
CODEC = CORE / "GameStateCodec.kt"
FACADE = CORE / "GameCoreFacade.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
DETAIL_PROJECTION = CORE / "CharacterDetailProjection.kt"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Character capacity and restrictions come from CharacterState.metadata. The named
# constants remain as locked balance documentation, but no runtime routing uses names.
policy = POLICY.read_text(encoding="utf-8")
old_profile = '''  fun profileFor(state: GameState, characterId: String): InventoryProfile {
    if (characterId == KAI_ID) return KAI
    if (characterId == LUCIA_ID) return LUCIA
    val character = state.characters[characterId]
    if (characterId == AN_NHIEN_ID) return AN_NHIEN
    val key = (character?.id.orEmpty() + " " + character?.name.orEmpty()).lowercase()
    return if (key.contains("iris") || key.contains("syvial")) SPECIAL_COMPANION else NORMAL
  }
'''
new_profile = '''  fun profileFor(state: GameState, characterId: String): InventoryProfile {
    val capacity = ItemSystem.capacityFor(state, characterId)
    return InventoryProfile(capacity.maxTypes, capacity.maxPerType)
  }
'''
policy = replace_once(policy, old_profile, new_profile, "data-driven inventory profile")
old_category = '''    val normalized = ItemContentRules.normalize(item)
    if (ownerId == AN_NHIEN_ID && !AnNhienCanon.isFoodItem(normalized)) return "an_nhien_food_only"
    val profile = profileFor(state, ownerId)
'''
new_category = '''    val normalized = ItemContentRules.normalize(item)
    if (!ItemSystem.allowsItem(state, ownerId, normalized)) return "inventory_item_category_forbidden"
    val profile = profileFor(state, ownerId)
'''
policy = replace_once(policy, old_category, new_category, "data-driven inventory restriction")
POLICY.write_text(policy, encoding="utf-8")


# DROP is a location transfer, not deletion. Preserve physical identity when the
# finalized Omnivault identity layer is present.
engines = ENGINES.read_text(encoding="utf-8")
old_drop = '''      ItemCommand.Operation.DROP -> {
        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")
        if (MadGodCanon.isId(command.itemId) && state.equipment[command.actorId]?.slots?.values?.contains(command.itemId)==true) return invalid(state, "madgod_equipment_permanent")
        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        changed(state.copy(inventories = state.inventories + (command.actorId to next)), "inventory_remove")
      }
'''
new_drop = '''      ItemCommand.Operation.DROP -> {
        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")
        if (MadGodCanon.isId(command.itemId) && state.equipment[command.actorId]?.slots?.values?.contains(command.itemId)==true) return invalid(state, "madgod_equipment_permanent")
        val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val withoutItem = state.copy(inventories = state.inventories + (command.actorId to removal.inventory))
        changed(ItemSystem.placeInWorld(withoutItem, removal.taken), "inventory_dropped_to_world")
      }
'''
engines = replace_once(engines, old_drop, new_drop, "drop world transfer")
ENGINES.write_text(engines, encoding="utf-8")


# This first test build deliberately starts a new save generation. Old WebView/core
# payloads are never decoded or migrated into the new item authority.
codec = CODEC.read_text(encoding="utf-8")
old_decode = '''  fun decode(root: JSONObject): GameState {
    val version = root.optInt("saveVersion", 0)
    val decoded = when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
    return CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded)))
  }
'''
new_decode = '''  fun decode(root: JSONObject): GameState {
    val version = root.optInt("saveVersion", 0)
    require(version == CURRENT_SAVE_VERSION) { "incompatible_save_version" }
    val decoded = decodeCurrent(root)
    return CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded)))
  }
'''
codec = replace_once(codec, old_decode, new_decode, "strict save generation")
CODEC.write_text(codec, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
old_load = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameStateCodec.decode(legacy)
    val normalized = normalizeVisualPresence(loaded)
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }
'''
new_load = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else CharacterEquipmentSystem.normalize(GameState.initial())
    val normalized = normalizeVisualPresence(loaded)
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }
'''
facade = replace_once(facade, old_load, new_load, "fresh save authority")
FACADE.write_text(facade, encoding="utf-8")


# Inventory cards expose useful information for every item. Unknown future items use
# their own metadata and never require a new UI branch.
projection = DETAIL_PROJECTION.read_text(encoding="utf-8")
old_projection_field = '''  val components: List<EquipmentComponent>,
  val comparison: ItemComparisonProjection? = null,
  val baseItemEffect: ItemComparisonProjection? = null
)'''
new_projection_field = '''  val components: List<EquipmentComponent>,
  val inspection: ItemInspection,
  val comparison: ItemComparisonProjection? = null,
  val baseItemEffect: ItemComparisonProjection? = null
)'''
projection = replace_once(projection, old_projection_field, new_projection_field, "item inspection projection field")
old_projection_value = '''      abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(), components = def?.components.orEmpty(),
      comparison = comparison, baseItemEffect = baseItemEffect
'''
new_projection_value = '''      abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(), components = def?.components.orEmpty(),
      inspection = ItemSystem.inspect(item, ownerId = character.id),
      comparison = comparison, baseItemEffect = baseItemEffect
'''
projection = replace_once(projection, old_projection_value, new_projection_value, "item inspection projection value")
DETAIL_PROJECTION.write_text(projection, encoding="utf-8")

detail = DETAIL_JSON.read_text(encoding="utf-8")
old_inventory_details = '''      if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails.forEach { put(item(it)) }
      else c.inventory.forEach { stack -> put(JSONObject().apply {
        put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
        stack.condition?.let { put("state", it) }; put("contentState", stack.contentState.name)
      }) }
'''
new_inventory_details = '''      if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails.forEach { put(item(it)) }
      else c.inventory.forEach { stack -> put(JSONObject().apply {
        val inspection = ItemSystem.inspect(stack, ownerId = c.id)
        put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
        put("description", inspection.description); put("itemType", inspection.itemType); put("ownerId", c.id)
        put("capabilities", JSONArray(inspection.capabilities.toList()))
        stack.condition?.let { put("state", it) }; put("contentState", stack.contentState.name)
      }) }
'''
detail = replace_once(detail, old_inventory_details, new_inventory_details, "inventory inspection projection")
old_item = '''  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    put("id", x.id); put("name", x.name); put("quantity", x.quantity); x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
'''
new_item = '''  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    val inspection = x.inspection
    put("id", x.id); put("name", x.name); put("quantity", x.quantity); x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
    put("description", inspection.description); put("itemType", inspection.itemType); inspection.ownerId?.let { put("ownerId", it) }
    put("capabilities", JSONArray(inspection.capabilities.toList()))
'''
detail = replace_once(detail, old_item, new_item, "item detail inspection")
DETAIL_JSON.write_text(detail, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")
old_meta = "q('equipmentDetailMeta').textContent=[item.type,item.slot,item.rarity,item.equipped?'EQUIPPED':'UNEQUIPPED'].filter(Boolean).join(' · ');"
new_meta = "q('equipmentDetailMeta').textContent=[item.type||item.itemType,item.slot,item.rarity,item.equipped?'EQUIPPED':'UNEQUIPPED',item.description].filter(Boolean).join(' · ');"
html = replace_once(html, old_meta, new_meta, "item description UI")
INDEX.write_text(html, encoding="utf-8")


combined = "\n".join(path.read_text(encoding="utf-8") for path in (POLICY, ENGINES, CODEC, FACADE, DETAIL_PROJECTION, DETAIL_JSON, INDEX, CORE / "ItemSystem.kt"))
for marker in (
    "ItemSystem.capacityFor(state, characterId)",
    "ItemSystem.allowsItem(state, ownerId, normalized)",
    "inventory_dropped_to_world",
    'require(version == CURRENT_SAVE_VERSION) { "incompatible_save_version" }',
    "CharacterEquipmentSystem.normalize(GameState.initial())",
    'put("description", inspection.description)',
    "inspection = ItemSystem.inspect(item, ownerId = character.id)",
    "item.description",
    "ItemCatalog.ALMOND_WATER to \"Nước Hạnh Nhân",
):
    if marker not in combined:
        raise RuntimeError("Extensible item contract missing: " + marker)

print("Extensible item system finalized: data-driven characters/items/world, strict fresh saves, inspectable items and lossless DROP.")
