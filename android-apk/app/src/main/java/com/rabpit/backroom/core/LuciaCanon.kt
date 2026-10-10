package com.rabpit.backroom.core

const val LUCIA_ID = "lucia"
const val LUCIA_M4A1_ID = "lucia:m4a1-custom"
const val LUCIA_KNIFE_ID = "lucia:combat-knife"
const val LUCIA_WATCH_ID = "lucia:military-watch"

object LuciaCanon {
  const val NAME = "Lucia \"Lục\""
  const val AGE = 19
  const val HOME_LEVEL = 0
  const val ENCOUNTER_CHANCE = "50%"
  const val AVATAR_REF = "avatars/lucia_avatar.jpg"
  const val BATTLEFIELD_RECON_LOOT_BONUS_PERCENT = 5

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to LUCIA_M4A1_ID,
    "blade" to LUCIA_KNIFE_ID,
    "wrist" to LUCIA_WATCH_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUCIA_ID,
      name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterVitalState(currentHp = 100)
    )
    return base.copy(
      id = LUCIA_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = LUCIA_ID,
      equipmentId = LUCIA_ID,
      healthState = base.healthState ?: "HEALTHY",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = base.vitalState.copy(currentHp = base.vitalState.currentHp.coerceIn(0, 100)),
      metadata = base.metadata + mapOf(
        "age" to AGE.toString(),
        "species" to "human",
        "gender" to "female",
        "militaryRank" to "Binh nhì",
        "militaryRole" to "Tư lệnh cấp tiểu đội trong biên chế đặc nhiệm",
        "npcType" to "follower",
        "entity" to "false",
        "combatant" to "true",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "homeLevel" to HOME_LEVEL.toString(),
        "encounterLevels" to "0",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterAction" to "EXPLORE",
        "inventoryProfile" to "lucia_gift_inventory",
        "startingLoadedAmmo" to "60",
        "startingReserveAmmo" to "90",
        "startingTotalAmmo" to "150",
        "ammoNote" to "60 viên trong M4A1 + 3 băng dự phòng 30 viên; không tính vào 3 loại vật phẩm quà tặng",
        "tacticalDoctrine" to "Kỷ luật hỏa lực; chỉ nổ súng khi bắt buộc phải đột phá hoặc xác định chính xác cổng ra",
        "level0Method" to "Điểm tựa bức tường; mở rộng xoắn ốc; đánh dấu đường bằng phấn; laser quét mặt sàn",
        "level0EntityKnowledge" to "Tiếng động giờ thứ 4 chỉ bị Lucia nghi là Hound; Level 0 không xác nhận Hound cư trú",
        "goal" to "Tìm lối sang Level 1",
        "passiveSkill" to "Trinh sát chiến trường",
        "lootChanceBonusPercent" to BATTLEFIELD_RECON_LOOT_BONUS_PERCENT.toString(),
        "avatarBuild" to "EXIF_STRIPPED_JPEG_R02"
      )
    )
  }

  fun inventory(existing: InventoryState? = null): InventoryState =
    InventoryState(LUCIA_ID, existing?.items.orEmpty())

  fun equipment(existing: EquipmentState? = null): EquipmentState =
    EquipmentState(LUCIA_ID, existing?.slots.orEmpty() + equipmentSlots)

  fun ensure(state: GameState): GameState {
    val character = character(state.characters[LUCIA_ID])
    return state.copy(
      characters = state.characters + (LUCIA_ID to character),
      inventories = state.inventories + (LUCIA_ID to inventory(state.inventories[LUCIA_ID])),
      equipment = state.equipment + (LUCIA_ID to equipment(state.equipment[LUCIA_ID]))
    )
  }
}
