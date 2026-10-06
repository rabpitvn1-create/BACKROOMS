package com.rabpit.backroom.core

const val AN_NHIEN_ID = "an-nhien"
const val AN_NHIEN_OUTFIT_ID = "an-nhien:pink-patterned-outfit"
const val AN_NHIEN_FOOTWEAR_ID = "an-nhien:baby-tree-pink-slippers"

object AnNhienCanon {
  const val NAME = "An Nhiên"
  const val AGE = 7
  const val SPECIES = "human"
  const val HOME_LEVEL = 0
  const val SURVIVAL_MULTIPLIER = 0.70
  const val LOOT_BONUS_POINTS = 1000
  const val EXIT_BONUS_POINTS = 200
  const val AVATAR_REF = "avatars/an_nhien_avatar.png"
  const val OUTFIT_NAME = "Bộ quần áo hoa văn màu hồng"
  const val FOOTWEAR_NAME = "Đôi dép màu hồng có hình Baby Tree"

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "outfit" to AN_NHIEN_OUTFIT_ID,
    "footwear" to AN_NHIEN_FOOTWEAR_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = AN_NHIEN_ID,
      name = NAME,
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = AN_NHIEN_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = AN_NHIEN_ID,
      equipmentId = AN_NHIEN_ID,
      metadata = base.metadata + mapOf(
        "age" to AGE.toString(),
        "species" to SPECIES,
        "homeLevel" to HOME_LEVEL.toString(),
        "npcType" to "follower",
        "entity" to "false",
        "joinEligible" to "true",
        "mandatoryEncounter" to "true",
        "encounterChance" to "100%",
        "nonCombat" to "true",
        "canUseWeapons" to "false",
        "followsPlayer" to "true",
        "survivalMultiplier" to SURVIVAL_MULTIPLIER.toString(),
        "lootBonusPoints" to LOOT_BONUS_POINTS.toString(),
        "exitBonusPoints" to EXIT_BONUS_POINTS.toString(),
        "inventoryProfile" to "an_nhien_food_only"
      )
    )
  }

  fun inventory(existing: InventoryState? = null): InventoryState {
    val valid = existing?.items.orEmpty().values
      .filter(::isFoodItem)
      .sortedBy { it.itemId }
      .take(2)
      .associateBy { it.itemId }
    return InventoryState(AN_NHIEN_ID, valid)
  }

  fun equipment(): EquipmentState = EquipmentState(AN_NHIEN_ID, equipmentSlots)

  fun ensure(state: GameState): GameState {
    val existing = state.characters[AN_NHIEN_ID]
    return state.copy(
      characters = state.characters + (AN_NHIEN_ID to character(existing)),
      inventories = state.inventories + (AN_NHIEN_ID to inventory(state.inventories[AN_NHIEN_ID])),
      equipment = state.equipment + (AN_NHIEN_ID to equipment())
    )
  }

  fun isFollowing(state: GameState): Boolean = AN_NHIEN_ID in state.party.memberIds

  fun survivalMultiplierFor(character: CharacterState): Double =
    if (character.id == AN_NHIEN_ID) SURVIVAL_MULTIPLIER else 1.0

  fun isFoodItem(raw: ItemStack): Boolean {
    val item = ItemContentRules.normalize(raw)
    val metadata = item.metadata.mapKeys { it.key.lowercase() }.mapValues { it.value.lowercase() }
    if (metadata["category"] == "food" || metadata["type"] == "food" || metadata["itemtype"] == "food") return true
    if (metadata["food"] == "true") return true
    val physiology = metadata["physiologyeffect"].orEmpty().uppercase()
    if (physiology.split(',', ';', '|').map { it.trim() }.contains("FOOD")) return true
    val key = (item.name + " " + item.archetypeId).lowercase()
    return listOf("thức ăn", "đồ ăn", "food", "ration", "lương khô", "bánh", "kẹo", "thịt", "cơm", "mì").any(key::contains)
  }
}
