package com.rabpit.backroom.core

const val NGUYEN_QUOC_NAM_ID = "nguyen_quoc_nam"
const val HUA_TIEU_LAN_ID = "hua_tieu_lan"

/** Source: Novel/Characters/NGUYEN_QUOC_NAM_CODEX R01 and HUA_TIEU_LAN_CODEX R02. */
object VietnameseFollowersCanon {
  val ids: List<String> = listOf(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)

  fun name(id: String): String = when (id) {
    NGUYEN_QUOC_NAM_ID -> "Nguyễn Quốc Nam"
    HUA_TIEU_LAN_ID -> "Hứa Tiểu Lan"
    else -> throw IllegalArgumentException("Unknown Vietnamese follower: $id")
  }

  fun character(id: String, existing: CharacterState? = null): CharacterState {
    require(id in ids) { "Unknown Vietnamese follower: $id" }
    val nam = id == NGUYEN_QUOC_NAM_ID
    val base = existing ?: CharacterState(
      id = id, name = name(id), presence = CharacterPresence.MISSING,
      physiology = PhysiologyState.freshRunBaseline(),
      metadata = mapOf("encountered" to "false")
    )
    return base.copy(
      id = id, name = name(id),
      avatarRef = if (nam) "avatars/NguyenQuocNam_avatar.webp" else "avatars/HuaTieuLan_avatar.webp",
      inventoryId = id, equipmentId = id,
      metadata = base.metadata + mapOf(
        "species" to "human", "npcType" to "follower", "entity" to "false",
        "combatant" to "true", "joinEligible" to "true", "automaticJoin" to "false",
        "age" to if (nam) "36" else "18",
        "originYear" to if (nam) "2001" else "1895",
        "encounterChance" to "5%", "encounterLevels" to "ALL",
        "canonRef" to if (nam) "NGUYEN-QUOC-NAM-CODEX-R01" else "HUA-TIEU-LAN-CODEX-R02",
        "overlayRef" to if (nam) "NguyenQuocNam.webp" else "HuaTieuLan.webp"
      )
    )
  }

  fun startingLoadout(id: String): Map<EquipmentSlot, String> = when (id) {
    NGUYEN_QUOC_NAM_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to "nam:corridor-pistol",
      EquipmentSlot.OUTFIT to "nam:lecturer-shirt",
      EquipmentSlot.WRIST to "nam:promise-watch",
      EquipmentSlot.FOOTWEAR to "nam:corridor-shoes"
    )
    HUA_TIEU_LAN_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to "tieu-lan:truong-xuan",
      EquipmentSlot.OUTFIT to "tieu-lan:lam-cuc-ao-dai",
      EquipmentSlot.WRIST to "tieu-lan:jade-bangle",
      EquipmentSlot.FOOTWEAR to "tieu-lan:white-heels"
    )
    else -> emptyMap()
  }

  /** Seeds an unencountered registry entry, never refills, heals or re-equips an existing follower. */
  fun ensure(state: GameState): GameState {
    var next = state
    ids.forEach { id ->
      if (id !in next.characters) {
        val loadout = startingLoadout(id)
        next = next.copy(
          characters = next.characters + (id to character(id)),
          inventories = next.inventories + (id to InventoryState(id,
            loadout.values.associateWith(EquipmentCatalog::stackFor))),
          equipment = next.equipment + (id to EquipmentState(id,
            loadout.mapKeys { it.key.key }))
        )
      }
    }
    return next
  }

  val definitions: List<EquipmentDefinition> = listOf(
    EquipmentDefinition(
      id = "nam:corridor-pistol", name = "Hành Lang 01", type = "SEMIAUTOMATIC PISTOL",
      primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(19, "8 loaded / one spare magazine, reserve unknown", fireModes = listOf("SEMI")),
      restrictions = listOf("Mẫu súng chưa xác định; được nhặt trong Backrooms, không do trường đại học cấp.", "Đạn hữu hạn theo truyện; Core chưa mô phỏng tiêu hao từng viên."),
      canonRef = "NGUYEN-QUOC-NAM-CODEX-R01 §V", iconRef = "item-icons/nam-pistol.webp"
    ),
    EquipmentDefinition(
      id = "nam:lecturer-shirt", name = "Áo Sơ Mi Ca Trực", type = "OUTFIT",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(vitPercent = 10),
      abilities = listOf(EquipmentAbility("Giữ Tư Thế", "+10% VIT theo chỉ số hiện tại.")),
      restrictions = listOf("Sơ mi thường, không phải áo giáp."),
      canonRef = "NGUYEN-QUOC-NAM-CODEX-R01 §V", iconRef = "item-icons/nam-shirt.webp"
    ),
    EquipmentDefinition(
      id = "nam:promise-watch", name = "Đồng Hồ Lời Hứa", type = "WRISTWATCH",
      primarySlot = EquipmentSlot.WRIST, bonuses = EquipmentBonuses(accuracyPercent = 10),
      abilities = listOf(EquipmentAbility("Định Nhịp", "+10% DEX trong nguồn: nhân Accuracy ×1.10 trong schema hiện hành, tối đa 99%.")),
      restrictions = listOf("Không đo sinh hiệu, phân tích Entity hoặc phát tín hiệu về thế giới thực."),
      canonRef = "NGUYEN-QUOC-NAM-CODEX-R01 §V", iconRef = "item-icons/nam-watch.webp"
    ),
    EquipmentDefinition(
      id = "nam:corridor-shoes", name = "Giày Hành Lang", type = "FOOTWEAR",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(defPercent = 10),
      abilities = listOf(EquipmentAbility("Trụ Vững", "+10% DEF theo chỉ số hiện tại.")),
      restrictions = listOf("Không đi xuyên tường hoặc loại bỏ tiếng bước chân."),
      canonRef = "NGUYEN-QUOC-NAM-CODEX-R01 §V", iconRef = "item-icons/nam-shoes.webp"
    ),
    EquipmentDefinition(
      id = "tieu-lan:truong-xuan", name = "Trường Xuân", type = "ANTIQUE SINGLE-SHOT RIFLE",
      primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(25, "1 loaded / finite reserve", fireModes = listOf("SINGLE")),
      restrictions = listOf("Không tự xác định hãng, cỡ đạn hoặc khóa nòng.", "Đạn hữu hạn, cần nạp lại theo truyện; không bắn liên thanh hoặc xuyên tường."),
      canonRef = "HUA-TIEU-LAN-CODEX-R02 §VI", iconRef = "item-icons/tieu-lan-rifle.webp"
    ),
    EquipmentDefinition(
      id = "tieu-lan:lam-cuc-ao-dai", name = "Lam Cúc Áo Dài", type = "OUTFIT",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(evasionPercent = 10),
      abilities = listOf(EquipmentAbility("Tà Lụa Linh Hoạt", "+10% AGI trong nguồn: nhân Evasion ×1.10 trong schema hiện hành.")),
      restrictions = listOf("Áo dài thường hai tà, không chống đạn."),
      canonRef = "HUA-TIEU-LAN-CODEX-R02 §VI", iconRef = "item-icons/tieu-lan-ao-dai.webp"
    ),
    EquipmentDefinition(
      id = "tieu-lan:jade-bangle", name = "Vòng Ngọc Bình An", type = "JADE BRACELET",
      primarySlot = EquipmentSlot.WRIST, bonuses = EquipmentBonuses(accuracyPercent = 10),
      abilities = listOf(EquipmentAbility("Giữ Nhịp Ngắm", "+10% DEX trong nguồn: nhân Accuracy ×1.10 trong schema hiện hành, tối đa 99%.")),
      restrictions = listOf("Không có phép chữa thương hoặc thông điệp từ bên ngoài."),
      canonRef = "HUA-TIEU-LAN-CODEX-R02 §VI", iconRef = "item-icons/tieu-lan-jade-bracelet.webp"
    ),
    EquipmentDefinition(
      id = "tieu-lan:white-heels", name = "Bạch Liên Hài", type = "FOOTWEAR",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(defPercent = 10),
      abilities = listOf(EquipmentAbility("Trụ Bộ", "+10% DEF theo chỉ số hiện tại.")),
      restrictions = listOf("Giày trắng thấp gót, không giúp đi trên không."),
      canonRef = "HUA-TIEU-LAN-CODEX-R02 §VI", iconRef = "item-icons/tieu-lan-shoes.webp"
    )
  )
}
