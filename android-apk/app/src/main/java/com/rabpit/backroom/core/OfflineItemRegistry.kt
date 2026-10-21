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
      mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "WATER", "waterRecoveryMinutes" to "360")),
    Definition("greek-fire", "Greek Fire"),
    Definition(LIQUID_PAIN_ID, "Liquid Pain",
      mapOf("consumable" to "true", "consumedOnUse" to "true",
        "description" to HealingItems.LIQUID_PAIN_DESCRIPTION)),
    Definition("medical:bandage", "Băng gạc",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical")),
    Definition("medical:antiseptic", "Thuốc sát trùng",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical")),
    Definition("southern:ben-tre-coconut", "Dừa Xiêm Bến Tre",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "WATER", "waterRecoveryMinutes" to "180",
        "rarity" to "common", "itemCategory" to "food_drink",
        "description" to "Một trái dừa xiêm xanh mướt, phần đầu được chặt gọn để lộ lớp cơm dừa trắng bên trong. Nước dừa trong veo, có vị ngọt thanh tự nhiên, mang hương vị quen thuộc của miền Tây Nam Bộ. Khi uống, giảm 180 phút thiếu nước. Không chữa thương, không giải độc và không tác động đến trạng thái đói.")),
    Definition("southern:banh-tet", "Bánh Tét Nhân Thịt",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "FOOD", "foodRecoveryMinutes" to "240", "healHp" to "5",
        "rarity" to "common", "itemCategory" to "food",
        "description" to "Một khoanh bánh tét được cắt từ đòn bánh gói lá chuối xanh. Lớp nếp dẻo bao quanh nhân đậu xanh vàng ươm và thịt ba rọi béo ngậy. Khi ăn, giảm 240 phút thiếu thức ăn và hồi 5 HP, không vượt quá Max HP.")),
    Definition("southern:saigon-com-tam", "Cơm Tấm Sườn Bì Chả Sài Gòn",
      mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "FOOD", "foodRecoveryMinutes" to "720", "healHp" to "10",
        "rarity" to "uncommon", "itemCategory" to "food",
        "description" to "Một đĩa cơm tấm Sài Gòn đầy đặn với miếng sườn nướng vàng cạnh, bì heo trộn thính, chả trứng hấp, đồ chua và chén nước mắm pha chua ngọt. Khi ăn, giảm 720 phút thiếu thức ăn và hồi 10 HP, không vượt quá Max HP."))
  )
  private val byId = definitions.associateBy { it.id }

  init {
    require(byId.size == definitions.size) { "Duplicate local item IDs" }
  }

  fun all(): List<Definition> = definitions.toList()
  fun get(id: String): Definition? = byId[id]
  fun random(seed: Long): Definition = definitions[Random(seed).nextInt(definitions.size)]
}

