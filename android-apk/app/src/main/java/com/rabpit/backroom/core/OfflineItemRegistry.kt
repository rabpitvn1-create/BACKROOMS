package com.rabpit.backroom.core

import kotlin.random.Random

/** Built-in, offline definitions for ordinary loot. Bound character gear is not an item. */
object OfflineItemRegistry {
  data class Definition(
    val id: String,
    val name: String,
    val metadata: Map<String, String> = emptyMap()
  )

  private val definitions = listOf(
    Definition("almond-water", "Almond Water",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "WATER")),
    Definition("greek-fire", "Greek Fire"),
    Definition("liquid-pain", "Liquid Pain"),
    Definition("medical:bandage", "Băng gạc",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical")),
    Definition("medical:antiseptic", "Thuốc sát trùng",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical"))
  )
  private val byId = definitions.associateBy { it.id }

  init {
    require(byId.size == definitions.size) { "Duplicate local item IDs" }
  }

  fun all(): List<Definition> = definitions.toList()
  fun get(id: String): Definition? = byId[id]
  fun random(seed: Long): Definition = definitions[Random(seed).nextInt(definitions.size)]
}
