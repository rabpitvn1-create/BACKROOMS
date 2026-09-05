package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

enum class ItemCategory { CONSUMABLE, TOOL, AMMO, MATERIAL, EQUIPMENT, KEY_ITEM, OTHER }
enum class ItemStackMode { STACK, INSTANCE }

data class ItemDefinition(
  val id: String,
  val name: String,
  val category: ItemCategory,
  val stackMode: ItemStackMode = ItemStackMode.STACK,
  val maxStack: Int = 99,
  val transferable: Boolean = true,
  val discardable: Boolean = true,
  val equipmentSlot: String? = null,
  val effects: Set<String> = emptySet(),
  val contentModel: String? = null,
  val stateNameFull: String? = null,
  val stateNameLow: String? = null,
  val stateNameEmpty: String? = null,
  val aliases: Set<String> = emptySet(),
  val icon: String? = null
) {
  init {
    require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "invalid_item_id:$id" }
    require(name.isNotBlank()) { "item_name_required:$id" }
    require(maxStack > 0) { "item_stack_limit_invalid:$id" }
    if (stackMode == ItemStackMode.INSTANCE) require(maxStack == 1) { "instance_item_must_have_stack_1:$id" }
    if (contentModel != null) require(contentModel == "FULL_LOW_EMPTY") { "unsupported_content_model:$id:$contentModel" }
  }

  fun instantiate(
    quantity: Int,
    origin: LootOrigin,
    sourceId: String,
    turnId: String,
    instanceNonce: String? = null
  ): ItemStack {
    require(quantity > 0) { "quantity_must_be_positive" }
    require(quantity <= maxStack || stackMode == ItemStackMode.STACK) { "item_stack_limit" }
    val contentState = if (contentModel == "FULL_LOW_EMPTY") ContentState.FULL else ContentState.NONE
    val baseId = if (stackMode == ItemStackMode.INSTANCE) "$id@${instanceNonce ?: turnId}" else id
    val runtimeId = if (contentState == ContentState.NONE) baseId else "$baseId:${contentState.name.lowercase()}"
    val displayName = if (contentState == ContentState.FULL) stateNameFull ?: name else name
    val metadata = linkedMapOf(
      "catalog.definitionId" to id,
      "catalog.category" to category.name,
      "catalog.stackMode" to stackMode.name,
      "catalog.maxStack" to maxStack.toString(),
      "catalog.transferable" to transferable.toString(),
      "catalog.discardable" to discardable.toString(),
      "catalog.effects" to effects.sorted().joinToString(","),
      "loot.origin" to origin.name,
      "loot.sourceId" to sourceId,
      "loot.turnId" to turnId
    )
    equipmentSlot?.let { metadata["catalog.equipmentSlot"] = it }
    contentModel?.let { metadata["catalog.contentModel"] = it }
    stateNameFull?.let { metadata["catalog.stateNameFull"] = it }
    stateNameLow?.let { metadata["catalog.stateNameLow"] = it }
    stateNameEmpty?.let { metadata["catalog.stateNameEmpty"] = it }
    icon?.let { metadata["catalog.icon"] = it }
    if (stackMode == ItemStackMode.INSTANCE) metadata["catalog.instanceId"] = baseId
    return ItemStack(
      itemId = runtimeId,
      name = displayName,
      quantity = quantity,
      metadata = metadata,
      archetypeId = id,
      contentState = contentState
    )
  }
}

class ItemCatalog private constructor(
  val definitions: Map<String, ItemDefinition>
) {
  fun definition(id: String): ItemDefinition? = definitions[id]

  fun aliases(): Map<String, String> = buildMap {
    definitions.values.forEach { definition ->
      put(definition.name.lowercase(), definition.id)
      put(definition.id.lowercase(), definition.id)
      definition.aliases.forEach { alias -> put(alias.lowercase(), definition.id) }
    }
  }

  companion object {
    fun fromJson(raw: String): ItemCatalog {
      val root = JSONObject(raw)
      require(root.optInt("schemaVersion", 0) == 1) { "unsupported_item_catalog_schema" }
      val array = root.optJSONArray("items") ?: JSONArray()
      val result = linkedMapOf<String, ItemDefinition>()
      val aliases = mutableMapOf<String, String>()
      for (index in 0 until array.length()) {
        val json = array.getJSONObject(index)
        val id = json.getString("id").trim()
        require(id !in result) { "duplicate_item_id:$id" }
        val aliasSet = json.optJSONArray("aliases").strings().map(String::trim).filter(String::isNotEmpty).toSet()
        val definition = ItemDefinition(
          id = id,
          name = json.getString("name").trim(),
          category = enumValueOf<ItemCategory>(json.optString("category", "OTHER").uppercase()),
          stackMode = enumValueOf<ItemStackMode>(json.optString("stackMode", "STACK").uppercase()),
          maxStack = json.optInt("maxStack", if (json.optString("stackMode", "STACK").equals("INSTANCE", true)) 1 else 99),
          transferable = json.optBoolean("transferable", true),
          discardable = json.optBoolean("discardable", true),
          equipmentSlot = json.optString("equipmentSlot").trim().takeIf(String::isNotEmpty),
          effects = json.optJSONArray("effects").strings().map { it.trim().uppercase() }.filter(String::isNotEmpty).toSet(),
          contentModel = json.optString("contentModel").trim().takeIf(String::isNotEmpty),
          stateNameFull = json.optJSONObject("stateNames")?.optString("FULL")?.trim()?.takeIf(String::isNotEmpty),
          stateNameLow = json.optJSONObject("stateNames")?.optString("LOW")?.trim()?.takeIf(String::isNotEmpty),
          stateNameEmpty = json.optJSONObject("stateNames")?.optString("EMPTY")?.trim()?.takeIf(String::isNotEmpty),
          aliases = aliasSet,
          icon = json.optString("icon").trim().takeIf(String::isNotEmpty)
        )
        (aliasSet + definition.name + definition.id).forEach { alias ->
          val key = alias.lowercase()
          val old = aliases.putIfAbsent(key, id)
          require(old == null || old == id) { "duplicate_item_alias:$alias:$old:$id" }
        }
        result[id] = definition
      }
      require(result.isNotEmpty()) { "item_catalog_empty" }
      return ItemCatalog(result)
    }
  }
}

object ItemDefinitionMetadata {
  fun definitionId(item: ItemStack): String = item.metadata["catalog.definitionId"] ?: item.archetypeId
  fun maxStack(item: ItemStack): Int = item.metadata["catalog.maxStack"]?.toIntOrNull()?.coerceAtLeast(1) ?: Int.MAX_VALUE
  fun transferable(item: ItemStack): Boolean = !item.metadata["catalog.transferable"].equals("false", true)
  fun discardable(item: ItemStack): Boolean = !item.metadata["catalog.discardable"].equals("false", true)
  fun equipmentSlot(item: ItemStack): String? = item.metadata["catalog.equipmentSlot"]?.takeIf(String::isNotBlank)
  fun effects(item: ItemStack): Set<String> = item.metadata["catalog.effects"].orEmpty()
    .split(',').map { it.trim().uppercase() }.filter(String::isNotEmpty).toSet()
  fun isInstance(item: ItemStack): Boolean = item.metadata["catalog.stackMode"].equals("INSTANCE", true)
}

private fun JSONArray?.strings(): List<String> {
  if (this == null) return emptyList()
  val out = mutableListOf<String>()
  for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(out::add)
  return out
}
