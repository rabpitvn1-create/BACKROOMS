package com.rabpit.backroom.core

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
  val agiPct: Int = 0,
  val dexPct: Int = 0,
  val vitPct: Int = 0,
  val strPct: Int = 0,
  val defPct: Int = 0
) {
  fun any() = agiPct != 0 || dexPct != 0 || vitPct != 0 || strPct != 0 || defPct != 0
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
  /** Skill-only damage multiplier (separate from additive STR/DEX/AGI percentages). */
  val skillDamageMultiplier: Int = 1,
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
      id = CAO_MINH_SWORD_ID, name = "Huyết Ma Kiếm", type = "MA ĐẠO ĐẠI KIẾM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(strPct = 500, dexPct = 100), weapon = WeaponGameplayStats(32),
      abilities = listOf(ability("Ngự kiếm", "Điều khiển kiếm bằng thần niệm."), ability("Huyết Sát Ma Khí", "Gia cường kiếm bằng ma nguyên."), ability("Triệu hồi bản mệnh", "Gọi Huyết Ma Kiếm trở về.")),
      canonRef = "CAO-EQP-HUYET-MA-KIEM-01"
    ),
    EquipmentDefinition(
      id = CAO_MINH_ARMOR_ID, name = "Huyết Ma Chiến Khải", type = "MA KHẢI", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(agiPct = 100, dexPct = 30, vitPct = 20, strPct = 20),
      abilities = listOf(ability("Ma Kim hộ thể", "Ma khải bảo vệ nhục thân và phân tán lực va chạm."), ability("Ma nguyên tự phục hồi", "Tự phục hồi pháp bảo; không đồng nghĩa hồi HP tức thì.")),
      canonRef = "CAO-EQP-HUYET-MA-KHAI-01"
    ),
    EquipmentDefinition(
      id = CAO_MINH_BOOTS_ID, name = "Giày Huyết Ma", type = "MA ĐẠO CHIẾN NGOA",
      primarySlot = EquipmentSlot.FOOTWEAR,
      bonuses = EquipmentBonuses(agiPct = 100, dexPct = 100),
      skillDamageMultiplier = 3,
      abilities = listOf(ability("Huyết Ảnh Bộ", "AGI +100%, DEX +100%; sát thương kỹ năng ×3 khi trang bị.",
        "Khi phối hợp Găng tay Huyết Ma: ×3 ×3 = ×9 Skill DMG; không cộng vào sát thương đánh thường.")),
      restrictions = listOf("Chỉ Cao Minh sử dụng; không chuyển nhượng hoặc tháo pháp bảo bản mệnh."),
      canonRef = "CAO-EQP-HUYET-MA-BOOTS-01"
    ),
    EquipmentDefinition(
      id = CAO_MINH_GAUNTLETS_ID, name = "Găng tay Huyết Ma", type = "MA ĐẠO HỘ THỦ",
      primarySlot = EquipmentSlot.GAUNTLETS,
      bonuses = EquipmentBonuses(agiPct = 100, dexPct = 100),
      skillDamageMultiplier = 3,
      abilities = listOf(ability("Ma Trảo Cường Hóa", "AGI +100%, DEX +100%; sát thương kỹ năng ×3 khi trang bị.",
        "Khi phối hợp Giày Huyết Ma: ×3 ×3 = ×9 Skill DMG; không cộng vào sát thương đánh thường.")),
      restrictions = listOf("Chỉ Cao Minh sử dụng; không chuyển nhượng hoặc tháo pháp bảo bản mệnh."),
      canonRef = "CAO-EQP-HUYET-MA-GAUNTLETS-01"
    ),
    EquipmentDefinition(
      id = IRIS_IVORY_EBONY_SET_ID, name = "Ivory & Ebony", type = "DUAL_WEAPON SET", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(),
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
      canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"
    ),
    EquipmentDefinition(
      id = SYVIAL_GODKILLER_ID, name = "GodKiller", type = "MECHANICAL GREATSWORD", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(), weapon = WeaponGameplayStats(38),
      abilities = listOf(
        ability("Lucifer Core Synchronization", "GodKiller đồng bộ trực tiếp Lucifer Core."),
        ability("Demonic Edge Reinforcement", "Lucifer Demonic Energy gia cường kết cấu và cạnh chém.", "Không có quota energy slash hữu hạn được bịa thêm để cân bằng."),
        ability("Weapon Recall", "Syvial có thể gọi GodKiller trở lại nếu bị đánh văng.", "Mất kiếm tạm thời không khiến Syvial mất cận chiến, Lucifer Gauntlets hoặc Spatial Shift."),
        ability("GodKiller Override Compatibility", "Tương thích Twenty-Four Severance.", "Khi đủ canon: exactly 24 slashes.")
      ),
      restrictions = listOf("GodKiller không phải gunblade, firearm hoặc ranged cannon."),
      canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"
    ),
    EquipmentDefinition(
      id = LUC_TRAM_TICH_QUANG_ID, name = "Tịch Quang",
      type = "BẢN MỆNH LINH KIẾM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(strPct = 200, agiPct = 100),
      abilities = listOf(
        ability("Kiếm niệm triệu hồi", "Triệu hồi khi ở trong phạm vi cảm nhận hợp lệ.", "Không tái sinh vô hạn khi đã bị phá."),
        ability("Tịch Quang Phản Kiếm", "Dẫn lực và phản chém từ đường đánh hụt của đối thủ."),
        ability("Thiên Kiếm Định Giới", "Dựng Kiếm Vực cục bộ khi có điểm neo hợp lệ.", "Không kiểm soát hình học Backrooms.")
      ), canonRef = "LUC-TRAM-THIEN-KIEM-CODEX-R05+LUC-PRO-R06"
    ),
    EquipmentDefinition(
      id = LUC_TRAM_KIEM_KHAI_ID, name = "Thiên Cơ Bạch Kim Kiếm Khải",
      type = "PHÁP BẢO KIẾM KHẢI", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(defPct = 75, dexPct = 50, agiPct = 50, vitPct = 20),
      abilities = listOf(ability("Phân tán phản lực", "Ổn định chuyển hướng và chịu va đập.")),
      restrictions = listOf("Là pháp bảo tiên hiệp; không có AI, reactor hoặc HUD."),
      canonRef = "LUC-TRAM-THIEN-KIEM-CODEX-R05+LUC-PRO-R06"
    ),
    EquipmentDefinition(
      id = CATHERINE_VESPER_ID, name = "Vesper Nocturne", type = "SIX-SHOT OCCULT REVOLVER",
      primarySlot = EquipmentSlot.WEAPON, weapon = WeaponGameplayStats(22, "6 / 36 reserve", null, listOf("Single Action", "Measured Double Action")),
      abilities = listOf(ability("Gương ngắm hổ phách", "Điểm ngắm hoàng đồng phản sáng trong ánh đèn yếu; không nhìn xuyên tường."),
        ability("Ổ đạn khắc kinh văn", "Ổ quay sáu viên ghi khế ước từ xưởng Ashcombe; không tự tạo đạn.")),
      restrictions = listOf("Đạn hữu hạn trong Canon; hệ thống Combat hiện không mô phỏng từng viên đạn.", "Không dịch chuyển xuyên tường hoặc tự khám phá Exit."),
      canonRef = CatherineCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = CATHERINE_COAT_ID, name = "Ashcombe Mourning Coat", type = "GOTHIC VICTORIAN OUTFIT",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(defPct = 15, vitPct = 5),
      abilities = listOf(ability("Lớp lót giấu chì", "Vải nhung đen và mảnh kim loại giấu dưới corset, phân tán mảnh đạn nhẹ.")),
      canonRef = CatherineCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = CATHERINE_GLOVES_ID, name = "Velvet Trigger Gloves", type = "LEATHER GLOVES",
      primarySlot = EquipmentSlot.GAUNTLETS, bonuses = EquipmentBonuses(dexPct = 10),
      canonRef = CatherineCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = CATHERINE_BOOTS_ID, name = "Midnight Riding Boots", type = "LACED LEATHER BOOTS",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(agiPct = 10),
      canonRef = CatherineCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = JANET_BAT_ID, name = "Bayou Belle", type = "BARBED-WIRE WOODEN BASEBALL BAT",
      primarySlot = EquipmentSlot.WEAPON, weapon = WeaponGameplayStats(24),
      abilities = listOf(ability("Bayou Swing", "Đòn đánh bồi từ quỹ đạo gậy cận chiến."),
        ability("Barbed Encore", "Dây thép gai xé qua lớp bảo hộ mềm của mục tiêu.")),
      restrictions = listOf("Vũ khí vật lý cận chiến: không đánh xuyên tường, không bắn tầm xa.",
        "Dây thép gai và gỗ có độ bền hữu hạn trong Canon."),
      canonRef = JanetCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = JANET_VEST_ID, name = "New Island Varsity Vest", type = "1988 COLLEGE VARSITY OUTFIT",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(vitPct = 10),
      restrictions = listOf("Áo vải; không phải áo chống đạn."),
      canonRef = JanetCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = JANET_GLOVES_ID, name = "Iron Grip Gloves", type = "PADDED LEATHER GLOVES",
      primarySlot = EquipmentSlot.GAUNTLETS, bonuses = EquipmentBonuses(strPct = 10),
      canonRef = JanetCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = JANET_BOOTS_ID, name = "Bayou Road Boots", type = "HIGH-LACE LEATHER BOOTS",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(defPct = 10),
      canonRef = JanetCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = NAM_PISTOL_ID, name = "Hành Lang 01", type = "FOUND SEMIAUTOMATIC PISTOL",
      primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(19, "8 + reserve (Canon)", null, listOf("Single")),
      restrictions = listOf("Vũ khí nhặt trong Backrooms, không mang từ trường học.",
        "Combat hiện chưa mô phỏng tiêu hao đạn; súng và đạn hữu hạn theo Canon.",
        "Không bắn xuyên tường, không bắn chính xác từ vô hạn khoảng cách."),
      canonRef = NguyenQuocNamCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = NAM_SHIRT_ID, name = "Áo Sơ Mi Ca Trực", type = "WHITE LECTURER SHIRT",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(vitPct = 10),
      restrictions = listOf("Không phải áo chống đạn hoặc giáp bảo hộ."),
      canonRef = NguyenQuocNamCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = NAM_WATCH_ID, name = "Đồng Hồ Lời Hứa", type = "ANALOG WRISTWATCH",
      primarySlot = EquipmentSlot.WRIST, bonuses = EquipmentBonuses(dexPct = 10),
      restrictions = listOf("Không đo sinh hiệu, không liên lạc với gia đình hoặc chỉ đường Exit."),
      canonRef = NguyenQuocNamCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = NAM_SHOES_ID, name = "Giày Hành Lang", type = "BLACK LEATHER SHOES",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(defPct = 10),
      canonRef = NguyenQuocNamCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = TIEU_LAN_RIFLE_ID, name = "Trường Xuân", type = "ANTIQUE SINGLE-SHOT RIFLE",
      primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(25, "1 loaded / finite reserve (Canon)", null, listOf("Single shot")),
      abilities = listOf(ability("Thước ngắm cơ khí", "Giữ điểm ngắm ở tầm nhìn thực, không phát hiện Exit."),
        ability("Báng gỗ chạm hoa", "Điểm tựa ổn định khi ngắm bắn, không tăng phạm vi xuyên vật chắn.")),
      restrictions = listOf("Súng cổ chỉ một phát mỗi lần nạp; đạn hữu hạn trong Canon.",
        "Combat hiện không mô phỏng nạp và trừ đạn từng lượt; không được suy ra đạn vô hạn.",
        "Không bắn xuyên tường hoặc tự động ngắm mục tiêu ngoài tầm nhìn."),
      canonRef = HuaTieuLanCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = TIEU_LAN_AO_DAI_ID, name = "Lam Cúc Áo Dài", type = "BLUE DAISY VIETNAMESE AO DAI",
      primarySlot = EquipmentSlot.OUTFIT, bonuses = EquipmentBonuses(agiPct = 10),
      restrictions = listOf("Áo dài có hai tà trước và sau, không phải giáp."),
      canonRef = HuaTieuLanCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = TIEU_LAN_JADE_ID, name = "Vòng Ngọc Bình An", type = "JADE WRIST BANGLE",
      primarySlot = EquipmentSlot.WRIST, bonuses = EquipmentBonuses(dexPct = 10),
      restrictions = listOf("Vật kỷ niệm, không có phép chữa lành hoặc liên lạc gia đình."),
      canonRef = HuaTieuLanCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = TIEU_LAN_SHOES_ID, name = "Bạch Liên Hài", type = "WHITE LOW HEEL SHOES",
      primarySlot = EquipmentSlot.FOOTWEAR, bonuses = EquipmentBonuses(defPct = 10),
      restrictions = listOf("Giày thường, không bay hoặc bước xuyên tường."),
      canonRef = HuaTieuLanCanon.CANON_REF
    ),
    EquipmentDefinition(
      id = LUCIA_M4A1_ID, name = "M4A1 cá nhân hóa", type = "ASSAULT RIFLE", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(strPct = 100, agiPct = 50),
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
      bonuses = EquipmentBonuses(agiPct = 25),
      weapon = WeaponGameplayStats(16),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_WATCH_ID, name = "Đồng hồ định vị quân sự", type = "MILITARY WATCH", primarySlot = EquipmentSlot.WRIST,
      bonuses = EquipmentBonuses(defPct = 10),
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
    EquipmentDefinition(
      id = MADGOD_SET_ID, name = "Huyết Ma Kiếm · Ma Tôn", type = "SPECIAL SWORD", primarySlot = EquipmentSlot.WEAPON,
      occupiesSlots = setOf(EquipmentSlot.WEAPON), rarity = "SPECIAL / CHEAT",
      bonuses = EquipmentBonuses(), weapon = WeaponGameplayStats(55),
      abilities = listOf(ability("Bản mệnh ma kiếm", "Vũ khí cheat giữ projection DMG 55; giáp MadGod đã chuyển thành passive Ma Tôn Vạn Giới.")),
      restrictions = listOf("Cao Minh Only", "Cannot Unequip after activation", "Cannot be copied or scanned"),
      classification = ItemClassification.SPECIAL_CHEAT,
      components = listOf(EquipmentComponent("Huyết Ma Kiếm", weapon = WeaponGameplayStats(55)))
    )
  )

  private val definitions = all.associateBy { it.id }

  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    IRIS_IVORY_ID, IRIS_EBONY_ID -> definitions[IRIS_IVORY_EBONY_SET_ID]
    else -> definitions[itemId]
  }

  /** Only Cao Minh may benefit from his bound Huyết Ma accessories. */
  fun isUsableBy(itemId: String, characterId: String): Boolean =
    itemId !in setOf(CAO_MINH_BOOTS_ID, CAO_MINH_GAUNTLETS_ID) ||
      CharacterStatCore.isCaoMinh(characterId)

  /**
   * Independent ×3 multipliers compound (both equipped => ×9).
   * Ordinary weapon and basic attacks do not use this factor.
   */
  fun equippedSkillDamageMultiplier(state: GameState, characterId: String): Int {
    val equipmentId = state.characters[characterId]?.equipmentId ?: characterId
    val slots = state.equipment[equipmentId]?.slots ?: return 1
    var result = 1
    for ((slot, itemId) in slots) {
      val def = definition(itemId) ?: continue
      if (!isUsableBy(itemId, characterId) || slot != def.primarySlot.key) continue
      result = (result.toLong() * def.skillDamageMultiplier.coerceAtLeast(1))
        .coerceAtMost(999L).toInt()
    }
    return result
  }

  fun startingLoadout(characterId: String): Map<EquipmentSlot, String> = when (characterId) {
    CAO_MINH_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to CAO_MINH_SWORD_ID,
      EquipmentSlot.ARMOR to CAO_MINH_ARMOR_ID,
      EquipmentSlot.FOOTWEAR to CAO_MINH_BOOTS_ID,
      EquipmentSlot.GAUNTLETS to CAO_MINH_GAUNTLETS_ID,
    )
    IRIS_ID -> linkedMapOf(EquipmentSlot.WEAPON to IRIS_IVORY_EBONY_SET_ID)
    SYVIAL_ID -> linkedMapOf(EquipmentSlot.WEAPON to SYVIAL_GODKILLER_ID)
    CATHERINE_ID -> linkedMapOf(EquipmentSlot.WEAPON to CATHERINE_VESPER_ID,
      EquipmentSlot.OUTFIT to CATHERINE_COAT_ID, EquipmentSlot.GAUNTLETS to CATHERINE_GLOVES_ID,
      EquipmentSlot.FOOTWEAR to CATHERINE_BOOTS_ID)
    JANET_ID -> linkedMapOf(EquipmentSlot.WEAPON to JANET_BAT_ID,
      EquipmentSlot.OUTFIT to JANET_VEST_ID, EquipmentSlot.GAUNTLETS to JANET_GLOVES_ID,
      EquipmentSlot.FOOTWEAR to JANET_BOOTS_ID)
    NGUYEN_QUOC_NAM_ID -> linkedMapOf(EquipmentSlot.WEAPON to NAM_PISTOL_ID,
      EquipmentSlot.OUTFIT to NAM_SHIRT_ID, EquipmentSlot.WRIST to NAM_WATCH_ID,
      EquipmentSlot.FOOTWEAR to NAM_SHOES_ID)
    HUA_TIEU_LAN_ID -> linkedMapOf(EquipmentSlot.WEAPON to TIEU_LAN_RIFLE_ID,
      EquipmentSlot.OUTFIT to TIEU_LAN_AO_DAI_ID, EquipmentSlot.WRIST to TIEU_LAN_JADE_ID,
      EquipmentSlot.FOOTWEAR to TIEU_LAN_SHOES_ID)
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
    return ItemStack(def.id, def.name, 1, metadata = metadata)
  }

  fun mergeDefinitionMetadata(stack: ItemStack): ItemStack {
    val def = definition(stack.itemId) ?: return stack
    val canonical = stackFor(def.id)
    return stack.copy(name = def.name, metadata = canonical.metadata + stack.metadata)
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
    return owned
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size
  fun usedSlots(state: GameState, characterId: String, inventory: InventoryState): Int = carriedItemIds(state, characterId, inventory).size

  fun consumesSlot(state: GameState, characterId: String, itemId: String): Boolean =
    itemId in carriedItemIds(state, characterId)
}

object CharacterStatEngine {
  fun effective(state: GameState, characterId: String): EffectiveCharacterStats =
    CharacterStatCore.effective(state, characterId)

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
    // Ordinary healing first consumes the recoverable portion: rest cannot heal it twice.
    var healed = (nextHp - character.vitalState.currentHp).coerceAtLeast(0)
    val p = character.physiology
    val foodUsed = minOf(healed, p.recoverableFoodHp); healed -= foodUsed
    val waterUsed = minOf(healed, p.recoverableWaterHp); healed -= waterUsed
    val sleepUsed = minOf(healed, p.recoverableSleepHp)
    // The recoverable ledger must never exceed missing HP, including after
    // equipment changes and explicit HP normalization.
    var gap = (maxHp - nextHp).coerceAtLeast(0)
    val foodRemaining = minOf(gap, (p.recoverableFoodHp - foodUsed).coerceAtLeast(0))
    gap -= foodRemaining
    val waterRemaining = minOf(gap, (p.recoverableWaterHp - waterUsed).coerceAtLeast(0))
    gap -= waterRemaining
    val sleepRemaining = minOf(gap, (p.recoverableSleepHp - sleepUsed).coerceAtLeast(0))
    val nextPhysiology = p.copy(
      recoverableFoodHp = foodRemaining,
      recoverableWaterHp = waterRemaining,
      recoverableSleepHp = sleepRemaining,
      restRecoveryMinutes = if (foodRemaining > 0 || waterRemaining > 0 || sleepRemaining > 0)
        p.restRecoveryMinutes else 0
    )
    return state.copy(characters = state.characters + (characterId to character.copy(
      physiology = nextPhysiology,
      vitalState = character.vitalState.copy(
        currentHp = nextHp,
        condition = conditionFor(nextHp, maxHp, character.vitalState.condition, character.presence)
      )
    )))
  }

  /** Damage bypasses armor because starvation/dehydration/exhaustion are not physical hits. */
  fun applySurvivalDamage(state: GameState, characterId: String, amount: Long, source: String): GameState {
    if (amount <= 0L) return state
    val c = state.characters[characterId] ?: return state
    if (c.presence == CharacterPresence.DEAD || c.vitalState.currentHp <= 0) return state
    val loss = minOf(amount, c.vitalState.currentHp.toLong()).toInt()
    val damaged = setCurrentHp(state, characterId, c.vitalState.currentHp - loss)
    val updated = damaged.characters.getValue(characterId)
    val p = updated.physiology
    fun plus(value: Int) = (value.toLong() + loss).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val withSource = when (source) {
      "FOOD" -> p.copy(recoverableFoodHp = plus(p.recoverableFoodHp))
      "WATER" -> p.copy(recoverableWaterHp = plus(p.recoverableWaterHp))
      "SLEEP" -> p.copy(recoverableSleepHp = plus(p.recoverableSleepHp))
      else -> error("survival_damage_source_invalid")
    }
    val dead = updated.vitalState.currentHp == 0
    return damaged.copy(characters = damaged.characters + (characterId to updated.copy(
      physiology = withSource,
      presence = if (dead) CharacterPresence.DEAD else updated.presence,
      vitalState = if (dead) updated.vitalState.copy(condition = CharacterCondition.DEAD) else updated.vitalState
    )))
  }

  fun preserveMissingHp(before: GameState, afterEquipment: GameState, characterId: String): GameState {
    val hp = before.characters[characterId]?.vitalState?.currentHp ?: return afterEquipment
    return setCurrentHp(afterEquipment, characterId, hp)
  }

  fun applyCompletedTurnRegen(state: GameState, completedTurnId: String): GameState =
    CharacterProgressionCore.normalize(state)

  fun weaponDamage(state: GameState, characterId: String): Int {
    val equipmentId = state.characters[characterId]?.equipmentId ?: characterId
    val weaponId = state.equipment[equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: 18
  }

  fun skillBaseDamage(state: GameState, characterId: String): Int {
    val strScaled = CharacterStatCore.basicDamage(
      weaponDamage(state, characterId), effective(state, characterId).str
    )
    val tactical = if (CharacterStatCore.isCaoMinh(characterId)) {
      CharacterStatCore.scaleByPercent(strScaled, CaoMinhCombatPassive.attackPercent(state))
    } else strScaled
    val multiplier = EquipmentCatalog.equippedSkillDamageMultiplier(state, characterId)
    return CharacterStatCore.scaleByPercent(tactical, multiplier * 100)
  }
}

object CombatStatMath {
  fun critChancePercent(agi: Int): Int = CharacterStatCore.criticalChance(agi)
  fun defenseReduction(defRating: Int): Int = (100L * defRating.coerceAtLeast(0) / (100L + defRating.coerceAtLeast(0))).toInt()
  fun vitalityDefense(vitRating: Int): Int = CharacterStatCore.defense(vitRating)
}

object EquipmentEngine {
  fun isEquipped(state: GameState, characterId: String, itemId: String): Boolean =
    state.equipment[characterId]?.slots.orEmpty().values.any { it == itemId }

  fun preview(state: GameState, characterId: String, itemId: String): EffectiveCharacterStats? =
    if (itemId in state.equipment[characterId]?.slots.orEmpty().values) CharacterStatEngine.effective(state, characterId)
    else null
}

object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "4-bound-equipment"

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)
  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)

  private fun normalizeInternal(source: GameState, seedStarting: Boolean): GameState {
    // Lucia exists in Core state without being forced into Party.
    val balanced = CharacterStatProfiles.rebalanceCurrentFollowers(source)
    val input = HuaTieuLanCanon.ensure(NguyenQuocNamCanon.ensure(JanetCanon.ensure(CatherineCanon.ensure(LucTramCanon.ensure(LuciaCanon.ensure(balanced))))))
    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()
    input.characters.keys.forEach { characterId ->
      val inv = inventories[characterId] ?: InventoryState(characterId)
      val eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()
      if (seedStarting || characterId == CAO_MINH_ID) {
        // Seed new bound Huyết Ma pieces in existing CURRENT-version runs too;
        // leave manually populated non-empty slots untouched.
        EquipmentCatalog.startingLoadout(characterId).forEach { (slot, itemId) ->
          slots.putIfAbsent(slot.key, itemId)
        }
      }
      // Bound Forever gear exists only in EquipmentState, never as inventory stacks.
      inventories[characterId] = inv.copy(items = inv.items.filterKeys { EquipmentCatalog.definition(it) == null })
      equipment[characterId] = eq.copy(slots = slots)
    }
    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )
    val chars = next.characters.toMutableMap()
    next.characters.forEach { (id, character) ->
      val maxHp = CharacterStatCore.effective(next, id).maxHp
      val hp = character.vitalState.currentHp.coerceIn(0, maxHp)
      chars[id] = character.copy(
        vitalState = character.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, maxHp, character.vitalState.condition, character.presence)
        ),
        metadata = character.metadata.filterKeys { !it.startsWith("derived.equipment") && it != "derived.effectiveMaxHp" }
      )
    }
    next = next.copy(characters = chars)
    // Fresh/load normalization must expose the canonical 1.1.93a stat schema too,
    // otherwise follower constructors can temporarily leak their retired HP/stat baseline.
    return CharacterProgressionCore.normalize(next)
  }
}
