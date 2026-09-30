package com.rabpit.backroom.core

/**
 * Consumable catalog ported from BACKROOMsV2 ItemCore.
 *
 * The current Game State Core remains authoritative for inventory mutation. This catalog only
 * canonicalizes V2 item identities/effects so the existing InventoryEngine can consume them.
 */
object V2ItemCatalog {
  const val ALMOND_WATER_ID = "almond-water"
  const val BANDAGE_ID = "bandage"
  const val FIRST_AID_KIT_ID = "first-aid-kit"
  const val LAVIE_WATER_ID = "lavie-water"
  const val COCONUT_WATER_ID = "coconut-water"
  const val BANH_MI_THIT_ID = "banh-mi-thit"
  const val HOT_SOY_MILK_ID = "hot-soy-milk"
  const val COM_TAM_SUON_BI_CHA_ID = "com-tam-suon-bi-cha"

  data class Definition(
    val id: String,
    val name: String,
    val category: String,
    val hunger: Int = 0,
    val thirst: Int = 0,
    val healHp: Int = 0
  )

  private val definitions = listOf(
    Definition(ALMOND_WATER_ID, "Almond Water", "FOOD_DRINK", hunger = 50, thirst = 100),
    Definition(BANDAGE_ID, "Băng Gạc Y Tế", "HEALING", healHp = 15),
    Definition(FIRST_AID_KIT_ID, "Túi Sơ Cứu", "HEALING", healHp = 35),
    Definition(LAVIE_WATER_ID, "Nước Suối Lavie", "DRINK", thirst = 50),
    Definition(COCONUT_WATER_ID, "Nước Dừa", "FOOD_DRINK", hunger = 10, thirst = 70),
    Definition(BANH_MI_THIT_ID, "Bánh Mì Thịt", "FOOD", hunger = 45),
    Definition(HOT_SOY_MILK_ID, "Sữa Đậu Nành Nóng", "FOOD_DRINK", hunger = 25, thirst = 30),
    Definition(COM_TAM_SUON_BI_CHA_ID, "Cơm Tấm Sườn Bì Chả", "FOOD", hunger = 80)
  )

  val chestPool: List<String> = definitions.map { it.id }
  val entityPool: List<String> = listOf(ALMOND_WATER_ID, BANDAGE_ID)

  private val byId = definitions.associateBy { it.id }
  private val aliases = buildMap {
    definitions.forEach { definition ->
      put(key(definition.id), definition.id)
      put(key(definition.name), definition.id)
    }
    put("bandage", BANDAGE_ID)
    put("băng gạc", BANDAGE_ID)
    put("bang gac", BANDAGE_ID)
    put("medical bandage", BANDAGE_ID)
    put("medical:bandage", BANDAGE_ID)
    put("first aid kit", FIRST_AID_KIT_ID)
    put("túi sơ cứu", FIRST_AID_KIT_ID)
    put("tui so cuu", FIRST_AID_KIT_ID)
    put("lavie", LAVIE_WATER_ID)
    put("nước lavie", LAVIE_WATER_ID)
    put("nuoc lavie", LAVIE_WATER_ID)
    put("coconut water", COCONUT_WATER_ID)
    put("nước dừa", COCONUT_WATER_ID)
    put("nuoc dua", COCONUT_WATER_ID)
    put("bánh mì thịt", BANH_MI_THIT_ID)
    put("banh mi thit", BANH_MI_THIT_ID)
    put("sữa đậu nành nóng", HOT_SOY_MILK_ID)
    put("sua dau nanh nong", HOT_SOY_MILK_ID)
    put("cơm tấm sườn bì chả", COM_TAM_SUON_BI_CHA_ID)
    put("com tam suon bi cha", COM_TAM_SUON_BI_CHA_ID)
  }

  fun definition(raw: String): Definition? {
    val normalized = key(raw)
    return byId[aliases[normalized] ?: normalized]
  }

  fun normalize(item: ItemStack): ItemStack? {
    val definition = definition(item.itemId) ?: definition(item.name) ?: return null
    val effects = buildList {
      if (definition.hunger > 0) add("FOOD")
      if (definition.thirst > 0) add("WATER")
    }
    val metadata = item.metadata.toMutableMap()
    metadata["v2Item"] = "true"
    metadata["category"] = definition.category
    metadata["consumable"] = "true"
    metadata["consumedOnUse"] = "true"
    if (effects.isNotEmpty()) metadata["physiologyEffect"] = effects.joinToString(",")
    if (definition.hunger > 0) metadata["hunger"] = definition.hunger.toString()
    if (definition.thirst > 0) metadata["thirst"] = definition.thirst.toString()
    if (definition.healHp > 0) metadata["healHp"] = definition.healHp.toString()

    return item.copy(
      itemId = definition.id,
      archetypeId = definition.id,
      name = definition.name,
      quantity = item.quantity.coerceAtLeast(1),
      metadata = metadata,
      contentState = ContentState.NONE
    )
  }

  fun itemName(id: String): String = definition(id)?.name ?: id
  fun effectValue(id: String, stat: String): Int {
    val definition = definition(id) ?: return 0
    return when (stat.trim().lowercase()) {
      "hunger" -> definition.hunger
      "thirst" -> definition.thirst
      "hp" -> definition.healHp
      else -> 0
    }
  }

  private fun key(raw: String): String = raw.trim().lowercase()
}
