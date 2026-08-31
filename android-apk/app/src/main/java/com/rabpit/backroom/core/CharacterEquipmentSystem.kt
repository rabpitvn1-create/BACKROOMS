package com.rabpit.backroom.core

const val KAI_DEMON_JAW_MASK_ID = KAI_SRU_MK20_SENSOR_ID
const val KAI_TALON_GAUNTLETS_ID = KAI_SRU_MK20_ARMS_ID
const val KAI_PHANTOM_GREAVES_ID = KAI_SRU_MK20_LEGS_ID
const val IRIS_IVORY_EBONY_SET_ID = "iris:ivory-ebony-set"

enum class EquipmentSlot(val key: String) {
  WEAPON("weapon"), ARMOR("armor"), HEAD("head"), GAUNTLETS("gauntlets"), GREAVES("greaves"),
  RING("ring"), SPECIAL("special"), BLADE("blade"), WRIST("wrist"), OUTFIT("outfit"), FOOTWEAR("footwear");

  companion object {
    fun fromRaw(raw: String?): EquipmentSlot? {
      val key = raw?.trim()?.lowercase()?.replace('-', '_') ?: return null
      return when (key) {
        "weapon", "weapon_primary", "weapon_secondary" -> WEAPON
        "armor" -> ARMOR
        "head", "mask", "helmet" -> HEAD
        "gauntlet", "gauntlets", "gloves" -> GAUNTLETS
        "greave", "greaves", "boots" -> GREAVES
        "ring" -> RING
        "special" -> SPECIAL
        "blade", "knife" -> BLADE
        "wrist", "watch" -> WRIST
        "outfit" -> OUTFIT
        "footwear", "shoes", "slippers" -> FOOTWEAR
        else -> null
      }
    }
  }
}

enum class ItemClassification { CANONICAL, SPECIAL_CHEAT, GENERAL }

data class EquipmentBonuses(
  val hp: Int = 0,
  val str: Int = 0,
  val df: Int = 0,
  val agi: Int = 0,
  val crit: Int = 0
) {
  fun any() = hp != 0 || str != 0 || df != 0 || agi != 0 || crit != 0
}

data class WeaponGameplayStats(
  val dmg: Int,
  val ammoDisplay: String? = null,
  val rpmCapability: Int? = null,
  val fireModes: List<String> = emptyList()
)

data class EquipmentAbility(
  val name: String,
  val description: String,
  val importantLimit: String? = null
)

data class EquipmentComponent(
  val name: String,
  val bonuses: EquipmentBonuses = EquipmentBonuses(),
  val weapon: WeaponGameplayStats? = null
)

data class EquipmentDefinition(
  val id: String,
  val name: String,
  val type: String,
  val primarySlot: EquipmentSlot,
  val occupiesSlots: Set<EquipmentSlot> = setOf(primarySlot),
  val rarity: String? = null,
  val bonuses: EquipmentBonuses = EquipmentBonuses(),
  val weapon: WeaponGameplayStats? = null,
  val abilities: List<EquipmentAbility> = emptyList(),
  val restrictions: List<String> = emptyList(),
  val classification: ItemClassification = ItemClassification.CANONICAL,
  val canonRef: String? = null,
  val components: List<EquipmentComponent> = emptyList()
)

object EquipmentCatalog {
  private fun ability(name: String, description: String, limit: String? = null) = EquipmentAbility(name, description, limit)

  private val all = listOf(
    EquipmentDefinition(
      id = KAI_WHITE_WRAITH_ID, name = "SRU-SG Shotgun", type = "TACTICAL SHOTGUN", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8),
      weapon = WeaponGameplayStats(32, "Demon Shell ∞ / Physical Shell finite", null, listOf("Physical Shell", "Demon Shell")),
      abilities = listOf(
        ability("Dual Shell System", "Dùng shell vật lý bình thường hoặc shell quỷ lực tùy tình huống.", "Shell vật lý là vật tư hữu hạn; shell quỷ lực hình thành trực tiếp từ nguồn sức mạnh của Kai."),
        ability("Demon Shell", "Shell quỷ lực vẫn gây sát thương vật lý nhưng mạnh hơn shell vật lý hàng chục lần theo canon.", "Gameplay DMG tiếp tục đi qua CombatRuntime normalization; không tự one-shot mọi Entity."),
        ability("Shotgun Mastery", "Kai kiểm soát độ tản, đường bắn, góc đặt chùm đạn và đổi mục tiêu ở cấp UR+."),
        ability("Core Self-Repair", "SRU-SG tự sửa chữa cấu trúc khi đang là trang bị của Kai.", "Sửa trang bị không phải hồi HP nhân vật."),
        ability("Guilty Crown Override", "Tương thích Guilty Crown Override với đúng 24 lần khai hỏa Demon Shell trong thời gian dừng hoàn toàn.", "Không đổi khóa 24 phát.")
      ),
      canonRef = "KAI-EQP-SRU-SG-01"
    ),
    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "SRU-MK20 Powered Armor", type = "POWERED ARMOR / EXOSKELETON", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6),
      abilities = listOf(
        ability("Powered Musculature", "Khuếch đại lực kéo, đẩy, nâng, giữ và phát lực lên nhiều lần so với người bình thường."),
        ability("Mobility Assistance", "Tăng hiệu suất chạy, đổi hướng, né, hạ trọng tâm và cận chiến mà không biến giáp thành khối power armor cồng kềnh."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm qua khung trợ lực và các phiến giáp."),
        ability("Environmental Protection", "Bảo vệ tác chiến trước nhiệt, lạnh, độc tố và môi trường khắc nghiệt ở mức phù hợp với SRU."),
        ability("Integrated Arm / Leg Systems", "Các chức năng tay và chân legacy đã được tích hợp trực tiếp vào SRU-MK20."),
        ability("Core Self-Repair", "Mọi phần SRU-MK20 đang trang bị tự sửa chữa bằng nguồn sức mạnh của Kai.", "Không tạo vật mới và không hồi HP tức thì."),
        ability("SRU Identification", "Nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT và điểm báo trạng thái hệ thống màu xanh.")
      ),
      restrictions = listOf("Cấu hình hiện hành để lộ đầu và khuôn mặt; không có Demon Jaw Mask, sừng cơ khí, pauldron đầu rồng hoặc cape legacy."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),
    EquipmentDefinition(
      id = KAI_DEMON_JAW_MASK_ID, name = "SRU-MK20 Open-Face Sensor Suite", type = "INTEGRATED SENSOR MODULE", primarySlot = EquipmentSlot.HEAD,
      bonuses = EquipmentBonuses(hp = 5, df = 6, crit = 6),
      abilities = listOf(
        ability("Open-Face Sensor Support", "Cảm biến và hỗ trợ tác chiến của SRU-MK20 hoạt động mà không che mặt Kai."),
        ability("Targeting Assistance", "Hỗ trợ xử lý dữ liệu mục tiêu và đường bắn hợp lệ.", "Hỗ trợ không tạo auto-hit."),
        ability("Encrypted SRU Communication", "Kết nối liên lạc mã hóa của SRU khi hạ tầng khả dụng.")
      ),
      restrictions = listOf("Đây là subsystem tích hợp của SRU-MK20, không phải helmet hoặc Demon Jaw Mask độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),
    EquipmentDefinition(
      id = KAI_TALON_GAUNTLETS_ID, name = "SRU-MK20 Integrated Arm Module", type = "INTEGRATED ARM MODULE", primarySlot = EquipmentSlot.GAUNTLETS,
      bonuses = EquipmentBonuses(hp = 5, str = 12, df = 4),
      abilities = listOf(
        ability("Arm Assist", "Khung cánh tay tăng lực nắm, đẩy, kéo, phát lực và kiểm soát SRU-SG ở cự ly gần."),
        ability("Close-Quarters Control", "Hỗ trợ khóa, bám, leo và kiểm soát vật thể trong tầm với khi điều kiện vật lý cho phép."),
        ability("Core Self-Repair", "Module tay tự sửa chữa khi đang được Kai trang bị.")
      ),
      restrictions = listOf("Subsystem tích hợp SRU-MK20; không còn là Talon Gauntlets độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),
    EquipmentDefinition(
      id = KAI_PHANTOM_GREAVES_ID, name = "SRU-MK20 Integrated Leg Module", type = "INTEGRATED LEG MODULE", primarySlot = EquipmentSlot.GREAVES,
      bonuses = EquipmentBonuses(hp = 5, str = 5, df = 3, agi = 14),
      abilities = listOf(
        ability("Leg Assist", "Khung chân tăng gia tốc, đổi hướng, chạy, nhảy và khả năng tiếp đất."),
        ability("Traversal Support", "Hỗ trợ vượt địa hình và điều chỉnh quỹ đạo cơ thể trong giới hạn vận động thực tế."),
        ability("Impact Reduction", "Giảm tải lên chân khi tiếp đất hoặc va chạm."),
        ability("Core Self-Repair", "Module chân tự sửa chữa khi đang được Kai trang bị.")
      ),
      restrictions = listOf("Subsystem tích hợp SRU-MK20; không còn là Phantom Greaves độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),
    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Omnivault Ring", type = "UTILITY EQUIPMENT", primarySlot = EquipmentSlot.RING,
      abilities = listOf(
        ability("Infinite Physical Storage", "Lưu trữ và lấy lại vật vô tri đã cất với dung lượng không giới hạn theo canon.", "Không tác động lên sinh vật sống."),
        ability("Equipment Restoration", "Hoàn nguyên trang bị hiện hành của Kai đã bị mất hoặc hư hỏng.", "Mỗi trang bị có cooldown 24 giờ sau một lần hoàn nguyên thành công."),
        ability("Equipped Item Self-Repair Link", "Trang bị đang được Kai mang tiếp tục tự sửa chữa qua liên kết Core độc lập với cooldown Hoàn nguyên.")
      ),
      restrictions = listOf(
        "SCAN/COPY/CREATE/MARKED/UPGRADE đã bị loại khỏi canon hiện hành.",
        "Không tạo vật phẩm chưa từng thuộc bộ trang bị hiện hành của Kai.",
        "Không tác động lên sinh vật sống."
      ),
      canonRef = "KAI-EQP-OMNIVAULT-01"
    ),
    EquipmentDefinition(
      id = IRIS_RECON_FRAME_ID, name = "Project 07", type = "SRU MECHANICAL COMBAT ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 20, df = 14, agi = 10, crit = 4),
      abilities = listOf(
        ability("Recon Protection", "Bảo vệ Iris trước va đập, mảnh văng và nguy cơ môi trường ở mức phù hợp với trinh sát chiến đấu."),
        ability("Dual-Gun Stabilization", "Ổn định vai, cẳng tay, cổ tay, tư thế và phân bố lực khi dùng Ivory & Ebony."),
        ability("Local Sensor Suite", "Cảm biến khoảng cách, chuyển động và môi trường cung cấp dữ liệu tại khu vực Iris trực tiếp hoạt động."),
        ability("ARGUS Terrain Read Support", "Hỗ trợ đọc độ cao, vật che, đường ngắm, lối vào/rút và điểm nghẽn từ dữ liệu hiện trường."),
        ability("Mobile Firing Support", "Hỗ trợ cân bằng và đổi tư thế khi bắn từ góc khó hoặc đang di chuyển.")
      ),
      restrictions = listOf("Không có drone.", "Không có Command Slate/tablet.", "Không có launcher, pháo vai, tên lửa hoặc remote camera mesh."),
      canonRef = "IRIS-BELIAL-SRU-CODEX-20260830-R06"
    ),
    EquipmentDefinition(
      id = IRIS_IVORY_EBONY_SET_ID, name = "Ivory & Ebony", type = "DUAL_WEAPON SET", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(agi = 4, crit = 8),
      weapon = WeaponGameplayStats(24, "∞", null, listOf("Independent Dual Fire")),
      abilities = listOf(
        ability("Demonic Ammunition", "Mỗi viên đạn hình thành trực tiếp từ quỷ lực của Iris.", "ENE ∞ không tạo damage, RPM, độ bền hoặc accuracy vô hạn."),
        ability("Independent Dual Fire", "Ivory và Ebony có thể dùng riêng hoặc đồng thời; một khẩu hỏng không tự vô hiệu khẩu còn lại."),
        ability("Twosome Time", "Hai tuyến ngắm độc lập cho phép xử lý hai hướng hoặc hai mục tiêu khi điều kiện cho phép."),
        ability("Rain Storm", "Bắn song súng trong chuyển động trên không.", "Không cho phép bay hoặc lơ lửng."),
        ability("Honeycomb Fire", "Tập trung cả hai khẩu lên cùng vùng/mục tiêu.", "Bị giới hạn bởi cơ cấu súng, tư thế, ổn định, đường bắn và action economy."),
        ability("Charged Shot", "Nén thêm quỷ lực trước khi bắn để tăng uy lực.", "ENE ∞ không tạo Charged Shot DMG ∞; CombatRuntime áp trần gameplay mỗi Action.")
      ),
      components = listOf(EquipmentComponent("Ivory"), EquipmentComponent("Ebony")),
      canonRef = "IRIS-BELIAL-SRU-CODEX-20260830-R06"
    ),
    EquipmentDefinition(
      id = SYVIAL_GODKILLER_ID, name = "GodKiller", type = "MECHANICAL GREATSWORD", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(str = 14, crit = 6), weapon = WeaponGameplayStats(38),
      abilities = listOf(
        ability("Lucifer Core Synchronization", "GodKiller đồng bộ trực tiếp Lucifer Core."),
        ability("Demonic Edge Reinforcement", "Lucifer Demonic Energy gia cường kết cấu và cạnh chém.", "Không có quota energy slash hữu hạn được bịa thêm để cân bằng."),
        ability("Weapon Recall", "Syvial có thể gọi GodKiller trở lại nếu bị đánh văng.", "Mất kiếm tạm thời không khiến Syvial mất cận chiến, Lucifer Gauntlets hoặc Spatial Shift."),
        ability("GodKiller Override Compatibility", "Tương thích Twenty-Four Severance.", "Khi đủ canon: exactly 24 slashes.")
      ),
      restrictions = listOf("GodKiller không phải gunblade, firearm hoặc ranged cannon."),
      canonRef = "SYVIAL-LUCIFER-CODEX-CURRENT"
    ),
    EquipmentDefinition(
      id = SYVIAL_LUCIFER_ARMOR_ID, name = "Lucifer Armor", type = "ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 30, str = 8, df = 20, agi = 10),
      abilities = listOf(
        ability("Physical Enhancement", "Tăng sức mạnh, burst movement và hỗ trợ phản xạ vận động."),
        ability("GodKiller Stabilization", "Ổn định quỹ đạo GodKiller."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm."),
        ability("Environmental Protection", "Bảo vệ nhiệt, lạnh, độc tố và môi trường ô nhiễm."),
        ability("Combat Analysis", "Motion tracking và environmental analysis."),
        ability("Lucifer Synchronization", "Đồng bộ Lucifer Core và GodKiller."),
        ability("Self-Repair", "Tự sửa chữa bằng Lucifer Demonic Energy và hỗ trợ tái sinh cơ thể Syvial.", "Armor repair không đồng nghĩa HP được hồi tức thì."),
        ability("Soul Protection", "Bảo vệ mạnh trước tác động trực tiếp lên linh hồn."),
        ability("Short-Range Spatial Shift", "Greaves hỗ trợ Short-Range Spatial Shift.")
      ),
      restrictions = listOf("Lucifer Armor rất bền nhưng NOT ABSOLUTELY INDESTRUCTIBLE."),
      canonRef = "SYVIAL-LUCIFER-CODEX-CURRENT"
    ),
    EquipmentDefinition(
      id = LUCIA_M4A1_ID, name = "M4A1 cá nhân hóa", type = "ASSAULT RIFLE", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(26, "60 / 90 reserve", 800, listOf("Semi", "Burst", "Auto")),
      abilities = listOf(
        ability("Green Laser 5mW", "Laser xanh chỉnh điểm danh 5mW hỗ trợ chỉ thị và quét bề mặt ở cự ly gần.", "Không biến laser thành cảm biến siêu nhiên."),
        ability("60-Round Main Magazine", "Băng chính mang 60 viên khi Lucia bắt đầu Level 0."),
        ability("Fire Discipline", "Lucia ưu tiên điểm xạ và tiết kiệm đạn trong môi trường chưa xác định.")
      ),
      restrictions = listOf("Đạn vật lý hữu hạn: 60 viên nạp + 90 viên dự phòng lúc bắt đầu."),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_KNIFE_ID, name = "Dao găm chiến đấu", type = "COMBAT KNIFE", primarySlot = EquipmentSlot.BLADE,
      weapon = WeaponGameplayStats(16),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_WATCH_ID, name = "Đồng hồ định vị quân sự", type = "MILITARY WATCH", primarySlot = EquipmentSlot.WRIST,
      abilities = listOf(
        ability("Local Time Reference", "Giữ mốc thời gian cục bộ để Lucia ghi chép hành trình."),
        ability("Navigation Hardware", "Phần cứng định vị vẫn tồn tại nhưng đã mất tín hiệu vệ tinh trong Backrooms.", "Không cung cấp GPS hoặc la bàn tuyệt đối ở Level 0.")
      ),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = AN_NHIEN_OUTFIT_ID, name = AnNhienCanon.OUTFIT_NAME, type = "OUTFIT", primarySlot = EquipmentSlot.OUTFIT,
      canonRef = "AN-NHIEN-CURRENT"
    ),
    EquipmentDefinition(
      id = AN_NHIEN_FOOTWEAR_ID, name = AnNhienCanon.FOOTWEAR_NAME, type = "FOOTWEAR", primarySlot = EquipmentSlot.FOOTWEAR,
      canonRef = "AN-NHIEN-CURRENT"
    ),
  )

  private val definitions = all.associateBy { it.id }

  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    IRIS_IVORY_ID, IRIS_EBONY_ID -> definitions[IRIS_IVORY_EBONY_SET_ID]
    else -> definitions[itemId]
  }

  fun startingLoadout(characterId: String): Map<EquipmentSlot, String> = when (characterId) {
    KAI_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to KAI_WHITE_WRAITH_ID,
      EquipmentSlot.ARMOR to KAI_BLACKBLOOD_ARMOR_ID,
      EquipmentSlot.HEAD to KAI_DEMON_JAW_MASK_ID,
      EquipmentSlot.GAUNTLETS to KAI_TALON_GAUNTLETS_ID,
      EquipmentSlot.GREAVES to KAI_PHANTOM_GREAVES_ID,
      EquipmentSlot.RING to KAI_OMNIVAULT_RING_ID
    )
    IRIS_ID -> linkedMapOf(EquipmentSlot.WEAPON to IRIS_IVORY_EBONY_SET_ID, EquipmentSlot.ARMOR to IRIS_RECON_FRAME_ID)
    SYVIAL_ID -> linkedMapOf(EquipmentSlot.WEAPON to SYVIAL_GODKILLER_ID, EquipmentSlot.ARMOR to SYVIAL_LUCIFER_ARMOR_ID)
    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    LUCIA_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to LUCIA_M4A1_ID,
      EquipmentSlot.BLADE to LUCIA_KNIFE_ID,
      EquipmentSlot.WRIST to LUCIA_WATCH_ID
    )
    else -> emptyMap()
  }

  fun stackFor(itemId: String): ItemStack {
    val def = definition(itemId)
    if (def == null) return ItemStack(itemId, itemId, metadata = mapOf("category" to "equipment"))
    val metadata = linkedMapOf(
      "category" to "equipment",
      "equipmentDefinitionId" to def.id,
      "slot" to def.primarySlot.key,
      "classification" to def.classification.name,
      "statItem" to (def.bonuses.any() || def.weapon != null).toString()
    )
    def.rarity?.let { metadata["rarity"] = it }
    return ItemStack(def.id, def.name, 1, "READY", metadata)
  }

  fun mergeDefinitionMetadata(stack: ItemStack): ItemStack {
    val def = definition(stack.itemId) ?: return stack
    val canonical = stackFor(def.id)
    return stack.copy(name = def.name, metadata = canonical.metadata + stack.metadata, archetypeId = def.id)
  }
}


object InventoryCapacityPolicy {
  fun maxSlots(state: GameState, characterId: String): Int = InventoryPolicy.profileFor(state, characterId).maxTypes

  fun equippedItemIds(state: GameState, characterId: String): Set<String> =
    state.equipment[characterId]?.slots.orEmpty().values.filter { it.isNotBlank() }.toSet()

  fun carriedItemIds(state: GameState, characterId: String): Set<String> =
    carriedItemIds(state, characterId, state.inventories[characterId] ?: InventoryState(characterId))

  fun carriedItemIds(state: GameState, characterId: String, inventory: InventoryState): Set<String> {
    val owned = inventory.items.filterValues { it.quantity > 0 }.keys
    return owned - equippedItemIds(state, characterId)
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size
  fun usedSlots(state: GameState, characterId: String, inventory: InventoryState): Int = carriedItemIds(state, characterId, inventory).size

  fun consumesSlot(state: GameState, characterId: String, itemId: String): Boolean =
    itemId in carriedItemIds(state, characterId)
}

object CharacterStatEngine {
  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val character = state.characters[characterId] ?: return fallback(characterId)
    val definitions = state.equipment[character.equipmentId]?.slots.orEmpty().values
      .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }
    val hp = definitions.sumOf { it.bonuses.hp }
    val str = definitions.sumOf { it.bonuses.str }
    val df = definitions.sumOf { it.bonuses.df }
    val agi = definitions.sumOf { it.bonuses.agi }
    val crit = definitions.sumOf { it.bonuses.crit }
    val unblessedMaxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1)
    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    val partyBlessed = devilBlessingActive(state, characterId)
    fun partyBlessing(value: Int): Int = if (partyBlessed) maxOf(1, (value * 105 + 99) / 100) else value
    return EffectiveCharacterStats(
      maxHp = unblessedMaxHp + devilBlessingHp,
      equipmentHp = hp,
      str = partyBlessing(character.statProfile.str + str),
      df = partyBlessing(character.statProfile.df + df),
      agi = partyBlessing(character.statProfile.agi + agi),
      crit = character.statProfile.crit + crit,
      energy = character.statProfile.energy,
      regenPerCompletedTurn = if (character.statProfile.regen.enabled) character.statProfile.regen.amountPerCompletedTurn else 0
    )
  }

  private fun fallback(characterId: String): EffectiveCharacterStats {
    val base = CharacterStatProfiles.forId(characterId)
    return EffectiveCharacterStats(base.baseMaxHp, 0, base.str, base.df, base.agi, base.crit, base.energy, if (base.regen.enabled) base.regen.amountPerCompletedTurn else 0)
  }

  fun conditionFor(currentHp: Int, maxHp: Int, old: CharacterCondition? = null, presence: CharacterPresence? = null): CharacterCondition {
    if (presence == CharacterPresence.DEAD || old == CharacterCondition.DEAD) return CharacterCondition.DEAD
    if (currentHp <= 0) return CharacterCondition.DEFEATED
    val ratio = currentHp.toDouble() / maxHp.coerceAtLeast(1).toDouble()
    return when {
      ratio > .75 -> CharacterCondition.HEALTHY
      ratio > .50 -> CharacterCondition.HURT
      ratio > .25 -> CharacterCondition.WOUNDED
      else -> CharacterCondition.CRITICAL
    }
  }

  fun setCurrentHp(state: GameState, characterId: String, hp: Int): GameState {
    val character = state.characters[characterId] ?: return state
    val maxHp = effective(state, characterId).maxHp
    val nextHp = hp.coerceIn(0, maxHp)
    val vital = character.vitalState.copy(
      currentHp = nextHp,
      condition = conditionFor(nextHp, maxHp, character.vitalState.condition, character.presence)
    )
    return state.copy(characters = state.characters + (characterId to character.copy(vitalState = vital)))
  }

  fun preserveMissingHp(before: GameState, afterEquipment: GameState, characterId: String): GameState {
    val character = before.characters[characterId] ?: return afterEquipment
    val oldMax = effective(before, characterId).maxHp
    val newMax = effective(afterEquipment, characterId).maxHp
    val oldHp = character.vitalState.currentHp.coerceIn(0, oldMax)
    val newHp = if (oldHp <= 0) 0 else (newMax - (oldMax - oldHp)).coerceAtLeast(0)
    return setCurrentHp(afterEquipment, characterId, newHp)
  }

  fun applyCompletedTurnRegen(state: GameState, completedTurnId: String): GameState {
    var next = state
    state.characters.keys.sorted().forEach { id ->
      val character = next.characters[id] ?: return@forEach
      val effective = effective(next, id)
      val hp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      val normalizedCondition = conditionFor(hp, effective.maxHp, character.vitalState.condition, character.presence)
      if (hp <= 0 || normalizedCondition == CharacterCondition.DEFEATED || normalizedCondition == CharacterCondition.DEAD) {
        val vital = character.vitalState.copy(currentHp = hp, condition = normalizedCondition)
        next = next.copy(characters = next.characters + (id to character.copy(vitalState = vital)))
        return@forEach
      }
      val rule = character.statProfile.regen
      if (!rule.enabled || rule.amountPerCompletedTurn <= 0 || character.vitalState.lastRegenCompletedTurnId == completedTurnId) return@forEach
      val interval = rule.intervalCompletedTurns.coerceAtLeast(1)
      val completed = (character.vitalState.completedTurnsSinceRegen + 1).coerceAtMost(interval)
      val shouldHeal = completed >= interval
      val healed = if (shouldHeal) (hp + rule.amountPerCompletedTurn).coerceAtMost(effective.maxHp) else hp
      val vital = character.vitalState.copy(
        currentHp = healed,
        condition = conditionFor(healed, effective.maxHp, character.vitalState.condition, character.presence),
        lastRegenCompletedTurnId = completedTurnId,
        completedTurnsSinceRegen = if (shouldHeal) 0 else completed
      )
      next = next.copy(characters = next.characters + (id to character.copy(vitalState = vital)))
    }
    return next
  }

  fun devilBlessingActive(state: GameState, characterId: String): Boolean {
    if (characterId == KAI_ID || state.metadata["combat.entityKey"].isNullOrBlank() || characterId !in state.party.memberIds) return false
    val kai = state.characters[KAI_ID] ?: return false
    val companion = state.characters[characterId] ?: return false
    return kai.presence == CharacterPresence.ACTIVE && kai.vitalState.currentHp > 0 &&
      companion.presence == CharacterPresence.ACTIVE && companion.vitalState.currentHp > 0
  }

  fun devilBlessingHpBonus(state: GameState, characterId: String, unblessedMaxHp: Int? = null): Int {
    if (!devilBlessingActive(state, characterId)) return 0
    val companion = state.characters.getValue(characterId)
    val base = unblessedMaxHp ?: run {
      val equipmentHp = state.equipment[companion.equipmentId]?.slots.orEmpty().values
        .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }.sumOf { it.bonuses.hp }
      (companion.statProfile.baseMaxHp + equipmentHp).coerceAtLeast(1)
    }
    return maxOf(1, (base * 5 + 99) / 100)
  }

  fun devilBlessingEvasionBonus(state: GameState, characterId: String): Int =
    if (devilBlessingActive(state, characterId)) 5 else 0

  fun weaponDamage(state: GameState, characterId: String): Int {
    val weaponId = state.equipment[characterId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: 18
  }
}

object CombatStatMath {
  fun critChancePercent(rating: Int): Int = (5 + rating.coerceAtLeast(0) / 12).coerceIn(5, 25)
  fun defenseReduction(dfRating: Int): Int = (dfRating.coerceAtLeast(0) / 18).coerceIn(0, 12)
  fun agilityDefense(agiRating: Int): Int = ((agiRating - 40).coerceAtLeast(0) / 18).coerceIn(0, 5)
}

object EquipmentEngine {
  fun isEquipped(state: GameState, characterId: String, itemId: String): Boolean =
    state.equipment[characterId]?.slots.orEmpty().values.any { it == itemId }

  fun equip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
    val owned = inventory.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < 1) return invalid(state, "item_not_owned")
    val def = EquipmentCatalog.definition(command.itemId)
    val requested = EquipmentSlot.fromRaw(command.slot)
    val targetSlots = if (def != null) def.occupiesSlots.map { it.key }.toSet() else setOfNotNull(requested?.key ?: command.slot?.trim()?.lowercase())
    if (targetSlots.isEmpty()) return invalid(state, "equipment_slot_required")
    if (def != null && requested != null && requested !in def.occupiesSlots && requested != def.primarySlot) return invalid(state, "equipment_slot_mismatch")

    val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
    val nextSlots = equipment.slots.toMutableMap()
    targetSlots.forEach { nextSlots[it] = command.itemId }
    val raw = state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = nextSlots)))
    val adjusted = CharacterStatEngine.preserveMissingHp(state, raw, command.actorId)
    return changed(adjusted, "item_equipped")
  }

  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
    if (command.itemId !in equipment.slots.values) return invalid(state, "item_not_equipped")
    val nextSlots = equipment.slots.filterValues { it != command.itemId }
    val raw = state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = nextSlots)))
    val adjusted = CharacterStatEngine.preserveMissingHp(state, raw, command.actorId)
    return changed(adjusted, "item_unequipped")
  }

  fun preview(state: GameState, characterId: String, itemId: String): EffectiveCharacterStats? {
    val item = state.inventories[characterId]?.items?.get(itemId) ?: return null
    val def = EquipmentCatalog.definition(item.itemId) ?: return null
    val equipment = state.equipment[characterId] ?: EquipmentState(characterId)
    if (itemId in equipment.slots.values) return CharacterStatEngine.effective(state, characterId)
    val next = equipment.slots.toMutableMap()
    def.occupiesSlots.forEach { next[it.key] = itemId }
    return CharacterStatEngine.effective(state.copy(equipment = state.equipment + (characterId to equipment.copy(slots = next))), characterId)
  }
}

object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "2"

  private fun retiredMadGodId(value: String?): Boolean =
    value?.trim()?.lowercase()?.startsWith("madgod:") == true

  private fun retiredMadGodItem(item: ItemStack): Boolean =
    retiredMadGodId(item.itemId) || item.name.contains("MadGod", ignoreCase = true) ||
      item.metadata["madGod"].equals("true", ignoreCase = true)

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true, fillStartingHp = true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION, fillStartingHp = false)

  private fun normalizeInternal(source: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.mapValues { (ownerId, inventory) ->
      inventory.copy(items = inventory.items.filterValues { !retiredMadGodItem(it) })
    }.toMutableMap()
    val equipment = input.equipment.mapValues { (ownerId, equipped) ->
      equipped.copy(slots = equipped.slots.filterValues { !retiredMadGodId(it) })
    }.toMutableMap()
    val cleanedMetadata = input.metadata.filterKeys { key ->
      !key.startsWith("madGod", ignoreCase = true) && !key.startsWith("madgod", ignoreCase = true)
    }

    input.characters.keys.forEach { characterId ->
      var inv = inventories[characterId] ?: InventoryState(characterId)
      var eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()

      val equipmentMigrations = when (characterId) {
        KAI_ID -> linkedMapOf(
          KAI_LEGACY_WHITE_WRAITH_ID to KAI_SRU_SG_ID,
          KAI_LEGACY_BLACKBLOOD_ARMOR_ID to KAI_SRU_MK20_ID,
          KAI_LEGACY_DEMON_JAW_ID to KAI_SRU_MK20_SENSOR_ID,
          KAI_LEGACY_TALON_ID to KAI_SRU_MK20_ARMS_ID,
          KAI_LEGACY_PHANTOM_GREAVES_ID to KAI_SRU_MK20_LEGS_ID
        )
        IRIS_ID -> linkedMapOf(IRIS_LEGACY_RECON_FRAME_ID to IRIS_PROJECT_07_ID)
        else -> emptyMap()
      }
      equipmentMigrations.forEach { (oldId, newId) ->
        slots.entries.filter { it.value == oldId }.forEach { it.setValue(newId) }
        inv.items[oldId]?.let { legacy ->
          val canonical = EquipmentCatalog.stackFor(newId).copy(
            condition = legacy.condition ?: "READY",
            metadata = EquipmentCatalog.stackFor(newId).metadata + legacy.metadata + mapOf("migratedFrom" to oldId)
          )
          inv = inv.copy(items = (inv.items - oldId) + (newId to canonical))
        }
      }

      // Collapse the historical two-slot Ivory/Ebony representation into one unique dual-weapon Item.
      if (characterId == IRIS_ID && slots.values.any { it == IRIS_IVORY_ID || it == IRIS_EBONY_ID }) {
        slots.remove("weapon_primary"); slots.remove("weapon_secondary")
        slots[EquipmentSlot.WEAPON.key] = IRIS_IVORY_EBONY_SET_ID
      }

      val loadout = EquipmentCatalog.startingLoadout(characterId)
      if (seedStarting) {
        loadout.forEach { (slot, itemId) ->
          if (slot.key !in slots) slots[slot.key] = itemId
          if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
        }
      }

      // Equipment references ownership. Never create a second Equipment-side Item object.
      slots.values.distinct().forEach { itemId ->
        val def = EquipmentCatalog.definition(itemId)
        val normalizedId = def?.id ?: itemId
        if (normalizedId !in inv.items) inv = inv.copy(items = inv.items + (normalizedId to EquipmentCatalog.stackFor(normalizedId)))
      }

      // Merge immutable definition metadata into the one inventory instance.
      val merged = inv.items.mapValues { (_, stack) -> EquipmentCatalog.mergeDefinitionMetadata(stack) }
      inv = inv.copy(items = merged)
      inventories[characterId] = inv
      equipment[characterId] = eq.copy(slots = slots)
    }

    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      omnivault = input.omnivault.copy(scanSlots = emptyList(), markedSourceIds = emptySet()),
      metadata = cleanedMetadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )

    val characters = next.characters.toMutableMap()
    next.characters.forEach { (id, character) ->
      val effective = CharacterStatEngine.effective(next, id)
      val rawHp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      val hp = if (
        fillStartingHp &&
        rawHp == character.statProfile.baseMaxHp &&
        character.vitalState.condition == CharacterCondition.HEALTHY &&
        character.vitalState.lastRegenCompletedTurnId == null
      ) effective.maxHp else rawHp
      characters[id] = character.copy(
        vitalState = character.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, effective.maxHp, character.vitalState.condition, character.presence)
        ),
        metadata = character.metadata + mapOf(
          "derived.equipmentHp" to effective.equipmentHp.toString(),
          "derived.effectiveMaxHp" to effective.maxHp.toString()
        )
      )
    }
    next = next.copy(characters = characters)
    return next
  }
}
