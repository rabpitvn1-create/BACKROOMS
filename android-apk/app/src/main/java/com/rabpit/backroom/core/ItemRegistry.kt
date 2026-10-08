package com.rabpit.backroom.core

/**
 * Core-owned Item type registry.
 *
 * Runtime acquisition never derives identity from display text. ENTITY and CHEST
 * sources must reference one of these IDs (or an EquipmentCatalog ID) exactly.
 */
data class CoreItemDefinition(
  val id: String,
  val displayName: String,
  val category: String,
  val canonId: String? = null,
  val defaultMetadata: Map<String, String> = emptyMap()
)

object ItemRegistry {
  const val ITEM_ALMOND_WATER_ID = "almond-water"
  const val ITEM_BANDAGE_ID = "medical:bandage"
  const val ITEM_ANTISEPTIC_ID = "medical:antiseptic"
  const val ITEM_GREEK_FIRE_ID = "backrooms:greek-fire"
  const val ITEM_LIQUID_PAIN_ID = "backrooms:liquid-pain"

  private val coreDefinitions: Map<String, CoreItemDefinition> = linkedMapOf(
    ITEM_ALMOND_WATER_ID to CoreItemDefinition(
      ITEM_ALMOND_WATER_ID,
      "Almond Water",
      "resource",
      canonId = "ITEM.ALMOND_WATER",
      defaultMetadata = mapOf("physiologyEffect" to "WATER", "consumedOnUse" to "true")
    ),
    ITEM_BANDAGE_ID to CoreItemDefinition(
      ITEM_BANDAGE_ID,
      "Băng gạc",
      "medical",
      defaultMetadata = mapOf("consumable" to "true", "consumedOnUse" to "true")
    ),
    ITEM_ANTISEPTIC_ID to CoreItemDefinition(
      ITEM_ANTISEPTIC_ID,
      "Thuốc sát trùng",
      "medical",
      defaultMetadata = mapOf("consumable" to "true", "consumedOnUse" to "true")
    ),
    ITEM_GREEK_FIRE_ID to CoreItemDefinition(
      ITEM_GREEK_FIRE_ID,
      "Greek Fire",
      "resource",
      canonId = "ITEM.GREEK_FIRE"
    ),
    ITEM_LIQUID_PAIN_ID to CoreItemDefinition(
      ITEM_LIQUID_PAIN_ID,
      "Liquid Pain",
      "hazardous_resource",
      canonId = "ITEM.LIQUID_PAIN"
    )
  )

  fun definition(itemId: String): CoreItemDefinition? {
    val id = itemId.trim()
    coreDefinitions[id]?.let { return it }
    val equipment = EquipmentCatalog.definition(id) ?: return null
    return CoreItemDefinition(
      id = equipment.id,
      displayName = equipment.name,
      category = "equipment"
    )
  }

  fun contains(itemId: String): Boolean = definition(itemId) != null

  fun displayName(itemId: String): String? = definition(itemId)?.displayName

  fun stack(
    itemId: String,
    quantity: Int = 1,
    metadata: Map<String, String> = emptyMap()
  ): ItemStack {
    val definition = requireNotNull(definition(itemId)) { "unknown_item_id:$itemId" }
    val equipment = EquipmentCatalog.definition(definition.id)
    val base = if (equipment != null) {
      EquipmentCatalog.stackFor(definition.id)
    } else {
      ItemStack(
        itemId = definition.id,
        name = definition.displayName,
        quantity = 1,
        metadata = definition.defaultMetadata,
        archetypeId = definition.id
      )
    }
    return ItemContentRules.normalize(
      base.copy(
        quantity = quantity.coerceAtLeast(1),
        name = definition.displayName,
        archetypeId = definition.id,
        metadata = base.metadata + definition.defaultMetadata + metadata +
          ("registryItemId" to definition.id)
      )
    )
  }
}
