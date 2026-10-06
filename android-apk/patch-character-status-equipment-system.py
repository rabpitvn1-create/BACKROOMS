from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"

GAME_STATE = CORE / "GameState.kt"
CODEC = CORE / "GameStateCodec.kt"
ENGINES = CORE / "Engines.kt"
TURN = CORE / "TurnCoordinator.kt"
DETAIL = CORE / "CharacterDetailProjection.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
FACADE = CORE / "GameCoreFacade.kt"
COMBAT = CORE / "CombatRuntime.kt"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
AN_NHIEN = CORE / "AnNhienCanon.kt"
MADGOD = CORE / "MadGodCanon.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
TEST = TESTS / "CharacterStatusEquipmentSystemTest.kt"


def one(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 anchor, found {count}")
    return text.replace(old, new, 1)


SYSTEM.write_text(r'''package com.rabpit.backroom.core

const val KAI_DEMON_JAW_MASK_ID = "kai:demon-jaw-mask"
const val KAI_TALON_GAUNTLETS_ID = "kai:talon-gauntlets"
const val KAI_PHANTOM_GREAVES_ID = "kai:phantom-greaves"
const val IRIS_IVORY_EBONY_SET_ID = "iris:ivory-ebony-set"

enum class EquipmentSlot(val key: String) {
  WEAPON("weapon"), ARMOR("armor"), HEAD("head"), GAUNTLETS("gauntlets"), GREAVES("greaves"),
  RING("ring"), SPECIAL("special"), OUTFIT("outfit"), FOOTWEAR("footwear");

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
      id = KAI_WHITE_WRAITH_ID, name = "White Wraith Magnum", type = "MAGNUM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8),
      weapon = WeaponGameplayStats(32, "∞", 600, listOf("Single Shot", "Full Auto")),
      abilities = listOf(
        ability("Demonic Ammunition", "Đạn được hình thành trực tiếp từ Sparda Core.", "Không dùng magazine vật lý làm nguồn đạn chính."),
        ability("Single Shot", "Bắn từng viên với nhịp ngắm chính xác."),
        ability("Full Auto", "Cơ cấu có khả năng bắn tự động tới khoảng 600 RPM.", "RPM không đồng nghĩa một Action bắn 600 viên."),
        ability("Core Self-Repair", "Tự sửa chữa cấu trúc bằng quỷ lực từ Sparda Core.", "Armor/weapon repair không hồi HP nhân vật."),
        ability("Guilty Crown Compatibility", "Tương thích Guilty Crown Override.", "Override giữ đúng 24 shots khi đủ điều kiện canon; không phải passive instant-kill.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "Blackblood Armor", type = "ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6),
      abilities = listOf(
        ability("Physical Enhancement", "Tăng sức mạnh và tốc độ vận động."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm."),
        ability("Stealth Movement", "Giảm tiếng bước chân và hỗ trợ di chuyển kín đáo."),
        ability("Environmental Protection", "Bảo vệ trước độc tố, nhiệt, lạnh và áp suất."),
        ability("Core Self-Repair", "Tự sửa chữa bằng Sparda Core."),
        ability("Battlefield Tracking", "Theo dõi chiến trường, hỗ trợ combat analysis và đồng bộ dữ liệu tác chiến."),
        ability("Omnivault Integration", "Kết nối trực tiếp Omnivault Ring.")
      ),
      restrictions = listOf("Không áp Heavy Armor mobility penalty; canon xác định giáp vận hành như phần mở rộng của cơ thể."),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_DEMON_JAW_MASK_ID, name = "Demon Jaw Mask", type = "HEAD / MASK", primarySlot = EquipmentSlot.HEAD,
      bonuses = EquipmentBonuses(hp = 5, df = 6, crit = 6),
      abilities = listOf(
        ability("Head & Neck Protection", "Bảo vệ đầu, cổ và phần dưới khuôn mặt."),
        ability("Toxin Filtration", "Lọc khí độc."),
        ability("Enhanced Vision", "Enhanced vision, motion tracking và biological analysis."),
        ability("Demonic Identification", "Nhận diện demonic-energy."),
        ability("Encrypted Communication", "Liên lạc mã hóa và combat HUD."),
        ability("Target-Lock Assistance", "Hỗ trợ khóa mục tiêu.", "Chỉ hỗ trợ, không tạo auto-hit.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_TALON_GAUNTLETS_ID, name = "Talon Gauntlets", type = "GAUNTLETS", primarySlot = EquipmentSlot.GAUNTLETS,
      bonuses = EquipmentBonuses(hp = 5, str = 12, df = 4),
      abilities = listOf(
        ability("Mechanical Talons", "Triển khai móng vuốt cơ khí và tăng lực đấm."),
        ability("Grip / Hook / Climbing", "Hỗ trợ grip, hook, climbing và di chuyển trên bề mặt."),
        ability("Short-Range EM Field", "Tạo trường điện từ tầm ngắn tác động lên vật thể kim loại."),
        ability("Weapon Control", "Có thể giật, khóa hoặc nghiền vũ khí khi đủ gần.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_PHANTOM_GREAVES_ID, name = "Phantom Greaves", type = "GREAVES", primarySlot = EquipmentSlot.GREAVES,
      bonuses = EquipmentBonuses(hp = 5, str = 5, df = 3, agi = 14),
      abilities = listOf(
        ability("Burst Acceleration", "Tăng gia tốc chân và burst movement."),
        ability("Traversal", "High jump, air-direction correction và wall running."),
        ability("Impact Reduction", "Giảm impact khi tiếp đất."),
        ability("Kinetic Kick", "Tăng lực đá và hỗ trợ pursuit trên địa hình phức tạp.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Omnivault Ring", type = "UTILITY EQUIPMENT", primarySlot = EquipmentSlot.RING,
      abilities = listOf(
        ability("Infinite Physical Storage", "Lưu trữ vật vô tri không giới hạn theo canon.", "Không tác động lên sinh vật sống."),
        ability("Scan Template", "Lưu mẫu để sao chép.", "Có đúng 3 template slots."),
        ability("Copy", "Tạo bản sao từ template còn tồn tại.", "Không tự tăng số template slot."),
        ability("Summon", "Triệu hồi vật đã lưu hoặc template hợp lệ."),
        ability("Rapid Re-Equip", "Hỗ trợ khôi phục hoặc thay thế trang bị gần như tức thời khi điều kiện hợp lệ.")
      ),
      restrictions = listOf("Giới hạn Omnivault lấy trực tiếp từ Character Codex; không tác động lên sinh vật sống."),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = IRIS_RECON_FRAME_ID, name = "Blackblood Recon Frame R03", type = "RECON ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 20, df = 14, agi = 10, crit = 4),
      abilities = listOf(
        ability("Recon Protection", "Bảo vệ trước va đập, mảnh văng và môi trường ở mức trinh sát chiến đấu."),
        ability("Dual-Gun Stabilization", "Ổn định vai, cẳng tay, cổ tay, tư thế và phân bố lực khi sử dụng song súng."),
        ability("Sensor Suite", "Range sensor, motion sensor và environmental sensor."),
        ability("ARGUS Terrain Read Support", "Hỗ trợ ARGUS Terrain Read từ dữ liệu quan sát/cảm biến hợp lệ."),
        ability("Mobile Firing Support", "Hỗ trợ bắn từ góc khó hoặc trong khi di chuyển.")
      ),
      restrictions = listOf("Không có drone.", "Không có missile, shoulder cannon, launcher hoặc remote camera mesh."),
      canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"
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
      canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"
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
      canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"
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
      canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"
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
      id = MADGOD_SET_ID, name = "MadGod Set", type = "SPECIAL EQUIPMENT SET", primarySlot = EquipmentSlot.WEAPON,
      occupiesSlots = setOf(EquipmentSlot.WEAPON, EquipmentSlot.ARMOR), rarity = "SPECIAL / CHEAT",
      bonuses = EquipmentBonuses(hp = 50, str = 15, df = 30, agi = 12, crit = 12),
      weapon = WeaponGameplayStats(55, "∞", 600, listOf("Single", "Full Auto")),
      abilities = listOf(
        ability("Demonic Ammunition", "Đạn được hình thành trực tiếp từ Sparda Core.", "Không cần magazine hoặc ammo inventory."),
        ability("Infinite Ammo", "Nguồn đạn gameplay hiển thị ∞.", "Không tạo damage hoặc số Action vô hạn."),
        ability("Single / Full Auto", "Hỗ trợ Single và Full Auto tới khoảng 600 RPM.", "600 RPM là capability, không phải số viên mặc định mỗi turn."),
        ability("Core Self-Repair", "MadGod Magnum và MadGod Armor tự sửa bằng Sparda Core.", "Armor repair và Character HP regeneration là hai hệ riêng."),
        ability("Environmental Protection", "Giữ chức năng bảo vệ nhiệt, lạnh, độc tố, áp suất và tác động môi trường tương đương Blackblood Armor."),
        ability("Physical Amplification", "Tăng STR và khả năng phát lực của Kai."),
        ability("Mobility Amplification", "Tăng AGI.", "Không tạo thêm Action miễn phí."),
        ability("Permanent Binding", "Sau khi Kai Equip, set khóa vĩnh viễn vào Weapon + Armor."),
        ability("Omnivault Copy Immunity", "Omnivault không thể Scan hoặc Copy MadGod Set."),
        ability("MadGod Power Conversion", "Đầu ra cao hơn White Wraith thông thường nhưng đi qua Gameplay Combat Normalization.", "Không dùng raw canon power để one-shot mọi Entity.")
      ),
      restrictions = listOf(
        "Kai Only", "Occupies Weapon + Armor", "Cannot Unequip after activation", "Cannot Drop after activation",
        "Cannot be copied or scanned", "Cannot stack with another MadGod Set", "Does not multiply base stats or HP regeneration"
      ),
      classification = ItemClassification.SPECIAL_CHEAT,
      components = listOf(
        EquipmentComponent("MadGod Magnum", bonuses = EquipmentBonuses(crit = 12), weapon = WeaponGameplayStats(55, "∞", 600, listOf("Single", "Full Auto"))),
        EquipmentComponent("MadGod Armor", bonuses = EquipmentBonuses(hp = 50, str = 15, df = 30, agi = 12))
      )
    )
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
    return EffectiveCharacterStats(
      maxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1),
      equipmentHp = hp,
      str = character.statProfile.str + str,
      df = character.statProfile.df + df,
      agi = character.statProfile.agi + agi,
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
      val healed = (hp + rule.amountPerCompletedTurn).coerceAtMost(effective.maxHp)
      val vital = character.vitalState.copy(
        currentHp = healed,
        condition = conditionFor(healed, effective.maxHp, character.vitalState.condition, character.presence),
        lastRegenCompletedTurnId = completedTurnId
      )
      next = next.copy(characters = next.characters + (id to character.copy(vitalState = vital)))
    }
    return next
  }

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
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
    val owned = inventory.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < 1) return invalid(state, "item_not_owned")
    val def = EquipmentCatalog.definition(command.itemId)
    if (def?.classification == ItemClassification.SPECIAL_CHEAT && command.actorId != KAI_ID) return invalid(state, "madgod_equipment_slot_mismatch")
    val requested = EquipmentSlot.fromRaw(command.slot)
    val targetSlots = if (def != null) def.occupiesSlots.map { it.key }.toSet() else setOfNotNull(requested?.key ?: command.slot?.trim()?.lowercase())
    if (targetSlots.isEmpty()) return invalid(state, "equipment_slot_required")
    if (def != null && requested != null && requested !in def.occupiesSlots && requested != def.primarySlot) return invalid(state, "equipment_slot_mismatch")

    val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
    val lockedByMadGod = targetSlots.any { slot -> equipment.slots[slot] == MADGOD_SET_ID && command.itemId != MADGOD_SET_ID }
    if (lockedByMadGod) return invalid(state, "madgod_equipment_permanent")
    if (command.itemId == MADGOD_SET_ID && equipment.slots.values.count { it == MADGOD_SET_ID } >= 2) return changed(state, "item_equipped")

    val nextSlots = equipment.slots.toMutableMap()
    targetSlots.forEach { nextSlots[it] = command.itemId }
    val raw = state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = nextSlots)))
    val adjusted = CharacterStatEngine.preserveMissingHp(state, raw, command.actorId)
    return changed(adjusted, "item_equipped")
  }

  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.itemId == MADGOD_SET_ID) return invalid(state, "madgod_equipment_permanent")
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
    if (def.classification == ItemClassification.SPECIAL_CHEAT && characterId != KAI_ID) return null
    if (def.occupiesSlots.any { equipment.slots[it.key] == MADGOD_SET_ID && itemId != MADGOD_SET_ID }) return null
    val next = equipment.slots.toMutableMap()
    def.occupiesSlots.forEach { next[it.key] = itemId }
    return CharacterStatEngine.effective(state.copy(equipment = state.equipment + (characterId to equipment.copy(slots = next))), characterId)
  }
}

object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "1"

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)

  private fun normalizeInternal(input: GameState, seedStarting: Boolean): GameState {
    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()

    input.characters.keys.forEach { characterId ->
      var inv = inventories[characterId] ?: InventoryState(characterId)
      var eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()

      // Collapse the historical two-slot Ivory/Ebony representation into one unique dual-weapon Item.
      if (characterId == IRIS_ID && slots.values.any { it == IRIS_IVORY_ID || it == IRIS_EBONY_ID }) {
        slots.remove("weapon_primary"); slots.remove("weapon_secondary")
        slots[EquipmentSlot.WEAPON.key] = IRIS_IVORY_EBONY_SET_ID
      }

      val loadout = EquipmentCatalog.startingLoadout(characterId)
      if (seedStarting) {
        loadout.forEach { (slot, itemId) ->
          val madGodOccupies = characterId == KAI_ID && slot in setOf(EquipmentSlot.WEAPON, EquipmentSlot.ARMOR) &&
            slots.values.any { it == MADGOD_SET_ID }
          if (!madGodOccupies && slot.key !in slots) slots[slot.key] = itemId
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
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )

    val characters = next.characters.toMutableMap()
    next.characters.forEach { (id, character) ->
      val effective = CharacterStatEngine.effective(next, id)
      val hp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
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
''', encoding="utf-8")

# --- Kai starting slots and fresh-state normalization -----------------------
state = GAME_STATE.read_text(encoding="utf-8")
if '"head" to KAI_DEMON_JAW_MASK_ID' not in state:
    slot_anchor = '''    "weapon" to KAI_WHITE_WRAITH_ID,
    "armor" to KAI_BLACKBLOOD_ARMOR_ID,
    "ring" to KAI_OMNIVAULT_RING_ID
'''
    slot_new = '''    "weapon" to KAI_WHITE_WRAITH_ID,
    "armor" to KAI_BLACKBLOOD_ARMOR_ID,
    "head" to KAI_DEMON_JAW_MASK_ID,
    "gauntlets" to KAI_TALON_GAUNTLETS_ID,
    "greaves" to KAI_PHANTOM_GREAVES_ID,
    "ring" to KAI_OMNIVAULT_RING_ID
'''
    state = one(state, slot_anchor, slot_new, "Kai full equipment slots")

if 'KAI_DEMON_JAW_MASK_ID -> "Demon Jaw Mask"' not in state:
    display_anchor = '''    KAI_BLACKBLOOD_ARMOR_ID -> ARMOR_NAME
    KAI_OMNIVAULT_RING_ID -> RING_NAME
'''
    display_new = '''    KAI_BLACKBLOOD_ARMOR_ID -> ARMOR_NAME
    KAI_DEMON_JAW_MASK_ID -> "Demon Jaw Mask"
    KAI_TALON_GAUNTLETS_ID -> "Talon Gauntlets"
    KAI_PHANTOM_GREAVES_ID -> "Phantom Greaves"
    KAI_OMNIVAULT_RING_ID -> RING_NAME
'''
    state = one(state, display_anchor, display_new, "Kai equipment display names")

if 'key.contains("demon jaw")' not in state:
    slot_for_anchor = '''      key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"
      key.contains("omnivault ring") || key.contains("nhẫn omnivault") || key.contains("nhẫn vạn tàng") || key.contains("van tang") -> "ring"
'''
    slot_for_new = '''      key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"
      key.contains("demon jaw") -> "head"
      key.contains("talon gauntlet") -> "gauntlets"
      key.contains("phantom greave") -> "greaves"
      key.contains("omnivault ring") || key.contains("nhẫn omnivault") || key.contains("nhẫn vạn tàng") || key.contains("van tang") -> "ring"
'''
    state = one(state, slot_for_anchor, slot_for_new, "Kai equipment slot resolver")

if 'CharacterEquipmentSystem.seedFresh(GameState(' not in state:
    start = '    fun initial(): GameState = GameState(\n'
    if start not in state:
        raise RuntimeError("GameState.initial anchor missing")
    state = state.replace(start, '    fun initial(): GameState = CharacterEquipmentSystem.seedFresh(GameState(\n', 1)
    tail = '    )\n  }\n}\n'
    pos = state.rfind(tail)
    if pos < 0:
        raise RuntimeError("GameState.initial closing anchor missing")
    state = state[:pos] + '    ))\n  }\n}\n' + state[pos + len(tail):]
GAME_STATE.write_text(state, encoding="utf-8")

# --- Iris uses one dual-weapon set in UI/data --------------------------------
special = SPECIAL.read_text(encoding="utf-8")
pattern = re.compile(r'''  val irisEquipmentSlots: Map<String, String> = linkedMapOf\(\n(?:.|\n)*?  \)\n\n  val syvialEquipmentSlots''')
match = pattern.search(special)
if not match:
    raise RuntimeError("Iris equipment slot block missing")
replacement = '''  val irisEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to IRIS_IVORY_EBONY_SET_ID,
    "armor" to IRIS_RECON_FRAME_ID
  )

  val syvialEquipmentSlots'''
special = special[:match.start()] + replacement + special[match.end():]
SPECIAL.write_text(special, encoding="utf-8")

# An Nhiên owns her equipped outfit/footwear in Inventory while retaining max two food item types.
an = AN_NHIEN.read_text(encoding="utf-8")
inv_start = an.find('  fun inventory(existing: InventoryState? = null): InventoryState {')
inv_end = an.find('\n  fun equipment(): EquipmentState', inv_start)
if inv_start < 0 or inv_end < 0:
    raise RuntimeError("An Nhien inventory function missing")
an_inventory = '''  fun inventory(existing: InventoryState? = null): InventoryState {
    val all = existing?.items.orEmpty().values
    val equipmentItems = all.filter { it.itemId == AN_NHIEN_OUTFIT_ID || it.itemId == AN_NHIEN_FOOTWEAR_ID }.associateBy { it.itemId }
    val foodItems = all.filter(::isFoodItem).sortedBy { it.itemId }.take(2).associateBy { it.itemId }
    return InventoryState(AN_NHIEN_ID, equipmentItems + foodItems)
  }
'''
an = an[:inv_start] + an_inventory + an[inv_end:]
AN_NHIEN.write_text(an, encoding="utf-8")

# --- Save/load: recalculate from Base + currently equipped Items every load ---
codec = CODEC.read_text(encoding="utf-8")
for candidate in (
    '    return SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded))\n',
    '    return AnNhienCanon.ensure(decoded)\n',
):
    if candidate in codec:
        codec = codec.replace(candidate, candidate.replace('return ', 'return CharacterEquipmentSystem.normalize(').rstrip('\n') + ')\n', 1)
        break
else:
    # Generic current decoder: wrap the version switch if prior follower patches did not introduce `decoded`.
    old = '''    return when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
'''
    new = '''    return CharacterEquipmentSystem.normalize(when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    })
'''
    codec = one(codec, old, new, "GameState decode normalization")
CODEC.write_text(codec, encoding="utf-8")

# --- Inventory/Equipment mutation uses the shared Item instance --------------
engines = ENGINES.read_text(encoding="utf-8")
# Replace whatever EQUIP/UNEQUIP implementation earlier patches produced.
start = engines.find('      ItemCommand.Operation.EQUIP -> {')
end = engines.find('      ItemCommand.Operation.STORE, ItemCommand.Operation.WITHDRAW', start)
if start < 0 or end < 0:
    raise RuntimeError("InventoryEngine equipment operation block missing")
new_ops = '''      ItemCommand.Operation.EQUIP -> EquipmentEngine.equip(state, command)
      ItemCommand.Operation.UNEQUIP -> EquipmentEngine.unequip(state, command)
'''
engines = engines[:start] + new_ops + engines[end:]

if 'if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")' not in engines:
    engines = engines.replace(
        '      ItemCommand.Operation.DROP -> {\n',
        '      ItemCommand.Operation.DROP -> {\n        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")\n',
        1,
    )
    engines = engines.replace(
        '      ItemCommand.Operation.TRANSFER -> {\n',
        '      ItemCommand.Operation.TRANSFER -> {\n        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")\n',
        1,
    )
ENGINES.write_text(engines, encoding="utf-8")

# --- Exactly-once regeneration after a successfully completed core turn ------
turn = TURN.read_text(encoding="utf-8")
old_turn = '''    val completed = execution.state.copy(turn = execution.state.turn.copy(
      pending = null,
      completedTurnIds = execution.state.turn.completedTurnIds + pending.turnId
    ))
    return TurnResult(completed, execution.copy(state = completed))
'''
new_turn = '''    val completed = execution.state.copy(turn = execution.state.turn.copy(
      pending = null,
      completedTurnIds = execution.state.turn.completedTurnIds + pending.turnId
    ))
    val regenerated = CharacterStatEngine.applyCompletedTurnRegen(completed, pending.turnId)
    return TurnResult(regenerated, execution.copy(state = regenerated))
'''
turn = one(turn, old_turn, new_turn, "completed-turn regeneration")
TURN.write_text(turn, encoding="utf-8")

# --- Pressure Combat reads/writes the authoritative Character VitalState ------
combat = COMBAT.read_text(encoding="utf-8")
combat = combat.replace('  private const val PLAYER_HP = "combat.playerHp"\n  private const val PLAYER_MAX_HP = "combat.playerMaxHp"\n', '')
combat = one(combat,
'''    val playerMax = state.metadata[PLAYER_MAX_HP]?.toIntOrNull()?.coerceIn(1, 999) ?: 100
    val playerHp = state.metadata[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax
''',
'''    val effective = CharacterStatEngine.effective(state, KAI_ID)
    val playerMax = effective.maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
''', "combat start authoritative HP")

combat = one(combat,
'''          val variance = 4 + roll(c.copy(eventCounter = c.eventCounter + 17), 9)
          val base = 18 + variance + c.opening * 7 + max(0, c.momentum) * 3
          val damage = max(1, base - profile.armor)
''',
'''          val variance = 2 + roll(c.copy(eventCounter = c.eventCounter + 17), 7)
          val effective = CharacterStatEngine.effective(state, KAI_ID)
          val weaponDamage = CharacterStatEngine.weaponDamage(state, KAI_ID)
          val critChance = CombatStatMath.critChancePercent(effective.crit)
          val critical = roll(c.copy(eventCounter = c.eventCounter + 23), 100) < critChance
          val base = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val normalized = if (critical) base * 3 / 2 else base
          val damage = min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))
''', "combat normalized weapon damage")

combat = one(combat,
'''      val damage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
''',
'''      val effective = CharacterStatEngine.effective(state, KAI_ID)
      val mitigation = CombatStatMath.defenseReduction(effective.df) + CombatStatMath.agilityDefense(effective.agi)
      val damage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) -
        when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 } - mitigation)
''', "combat normalized defense")

combat = combat.replace('    metadata[PLAYER_HP] = c.playerHp.toString()\n    metadata[PLAYER_MAX_HP] = c.playerMaxHp.toString()\n', '')
combat = one(combat,
'''    return state.copy(metadata = metadata)
  }

  private fun decode(state: GameState): Snapshot? {
''',
'''    return CharacterStatEngine.setCurrentHp(state.copy(metadata = metadata), KAI_ID, c.playerHp)
  }

  private fun decode(state: GameState): Snapshot? {
''', "combat encode HP sync")
combat = one(combat,
'''    val playerMax = m[PLAYER_MAX_HP]?.toIntOrNull()?.coerceAtLeast(1) ?: 100
    return Snapshot(
''',
'''    val playerMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    return Snapshot(
''', "combat decode max HP")
combat = one(combat,
'''      playerHp = m[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax,
''',
'''      playerHp = playerHp,
''', "combat decode current HP")

clear_start = combat.find('  private fun clearCombatOnly(state: GameState): GameState {')
clear_end = combat.find('\n  private fun classify(', clear_start)
if clear_start < 0 or clear_end < 0:
    raise RuntimeError("combat clear function missing")
combat = combat[:clear_start] + '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }
''' + combat[clear_end:]
COMBAT.write_text(combat, encoding="utf-8")

# Combat actions are completed gameplay turns too. Regen token derives from the UI turn so save/load cannot reapply it.
facade = FACADE.read_text(encoding="utf-8")
combat_time_anchor = '''    if (time.applied) next = time.state
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
'''
combat_time_new = '''    if (time.applied) next = time.state
    next = CharacterStatEngine.applyCompletedTurnRegen(next, "COMBAT_TURN_${legacy.optInt("turn", 1)}")
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
'''
facade = one(facade, combat_time_anchor, combat_time_new, "combat turn regeneration")

# Gemini candidate inventory can never silently delete equipment definitions. Equipment remains one owned Item.
protect_anchor = '''    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
'''
protect_block = '''    current.filterKeys { EquipmentCatalog.definition(it) != null }.forEach { (id, stack) -> desiredById[id] = stack }

    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
'''
facade = one(facade, protect_anchor, protect_block, "protect equipment inventory ownership")
FACADE.write_text(facade, encoding="utf-8")

# --- MadGod is normalized into the same 100-HP gameplay scale ----------------
if MADGOD.exists():
    mg = MADGOD.read_text(encoding="utf-8")
    mg = re.sub(r'const val MULTIPLIER = \d+', 'const val MULTIPLIER = 1', mg)
    mg = mg.replace('const val SCALING_MODE = "BASELINE_ONCE"', 'const val SCALING_MODE = "GAMEPLAY_NORMALIZED"')
    replacements = {
      r'const val MAGNUM_DMG = .*': 'const val MAGNUM_DMG = 55',
      r'const val ARMOR_DF = .*': 'const val ARMOR_DF = 30',
      r'const val ARMOR_STR = .*': 'const val ARMOR_STR = 15',
      r'const val ARMOR_AGI = .*': 'const val ARMOR_AGI = 12',
      r'const val ARMOR_HP = .*': 'const val ARMOR_HP = 50',
      r'const val ARMOR_ENE = .*': 'const val ARMOR_ENE = 0',
      r'const val ARMOR_CRIT = .*': 'const val ARMOR_CRIT = 0',
    }
    for pattern, repl in replacements.items():
        mg = re.sub(pattern, repl, mg, count=1)
    MADGOD.write_text(mg, encoding="utf-8")

# --- Rich Character projection, derived from Base + unique equipped Items -----
DETAIL.write_text(r'''package com.rabpit.backroom.core

data class StatLineProjection(val base: Int, val equipment: Int, val effective: Int)
data class StatComparisonProjection(val before: Int, val after: Int, val delta: Int)
data class ItemComparisonProjection(
  val maxHp: StatComparisonProjection,
  val str: StatComparisonProjection,
  val df: StatComparisonProjection,
  val agi: StatComparisonProjection,
  val crit: StatComparisonProjection
)

data class ItemDetailProjection(
  val id: String,
  val name: String,
  val quantity: Int,
  val type: String?,
  val slot: String?,
  val rarity: String?,
  val equipped: Boolean,
  val equippedSlots: List<String>,
  val statItem: Boolean,
  val classification: String?,
  val bonuses: EquipmentBonuses,
  val weapon: WeaponGameplayStats?,
  val abilities: List<EquipmentAbility>,
  val restrictions: List<String>,
  val components: List<EquipmentComponent>,
  val comparison: ItemComparisonProjection? = null,
  val baseItemEffect: ItemComparisonProjection? = null
)

data class CharacterDetailProjection(
  val id: String,
  val name: String,
  val avatarRef: String?,
  val presence: CharacterPresence,
  val isLeader: Boolean,
  val healthState: String?,
  val currentHp: Int,
  val maxHp: Int,
  val role: String,
  val energyDisplay: String,
  val regenPerCompletedTurn: Int,
  val condition: CharacterCondition,
  val str: StatLineProjection,
  val df: StatLineProjection,
  val agi: StatLineProjection,
  val crit: StatLineProjection,
  val injuries: List<String>,
  val physiology: DerivedPhysiologyStatus,
  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection>,
  val equipment: Map<String, String>,
  val equipmentDetails: List<ItemDetailProjection>,
  val statusEffects: List<StatusEffect>
)

data class PartyDetailProjection(
  val leaderId: String,
  val maxMembers: Int,
  val elapsedSubjectiveMinutes: Long,
  val members: List<CharacterDetailProjection>
)

object CharacterDetailProjector {
  fun projectParty(state: GameState): PartyDetailProjection {
    val normalized = CharacterEquipmentSystem.normalize(state)
    val members = normalized.party.memberIds.mapNotNull { id -> normalized.characters[id]?.let { projectCharacter(normalized, it) } }
    return PartyDetailProjection(normalized.party.leaderId, normalized.party.maxMembers, normalized.time.elapsedSubjectiveMinutes, members)
  }

  fun projectCharacter(state: GameState, characterId: String): CharacterDetailProjection? {
    val normalized = CharacterEquipmentSystem.normalize(state)
    return normalized.characters[characterId]?.let { projectCharacter(normalized, it) }
  }

  private fun projectCharacter(state: GameState, character: CharacterState): CharacterDetailProjection {
    val inventory = state.inventories[character.inventoryId]?.items?.values.orEmpty()
      .sortedWith(compareBy<ItemStack> { it.name.lowercase() }.thenBy { it.itemId })
    val equipment = state.equipment[character.equipmentId]?.slots.orEmpty().toSortedMap()
    val effects = character.statusIds.mapNotNull(state.statuses::get).sortedWith(compareBy<StatusEffect> { it.type }.thenBy { it.id })
    val effective = CharacterStatEngine.effective(state, character.id)
    val defs = equipment.values.mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }
    val bonus = EquipmentBonuses(
      hp = defs.sumOf { it.bonuses.hp }, str = defs.sumOf { it.bonuses.str }, df = defs.sumOf { it.bonuses.df },
      agi = defs.sumOf { it.bonuses.agi }, crit = defs.sumOf { it.bonuses.crit }
    )
    val current = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
    val inventoryDetails = inventory.map { itemDetail(state, character, it) }
    val equipmentDetails = equipment.values.distinct().mapNotNull { id -> inventory.find { it.itemId == id }?.let { itemDetail(state, character, it) } }

    return CharacterDetailProjection(
      id = character.id, name = character.name, avatarRef = character.avatarRef, presence = character.presence,
      isLeader = character.id == state.party.leaderId, healthState = character.healthState,
      currentHp = current, maxHp = effective.maxHp, role = character.statProfile.combatRole,
      energyDisplay = when (character.statProfile.energy.mode) { EnergyMode.INFINITE -> "∞"; EnergyMode.FINITE -> (character.statProfile.energy.max ?: 0).toString(); EnergyMode.NOT_APPLICABLE -> "N/A" },
      regenPerCompletedTurn = effective.regenPerCompletedTurn,
      condition = CharacterStatEngine.conditionFor(current, effective.maxHp, character.vitalState.condition, character.presence),
      str = StatLineProjection(character.statProfile.str, bonus.str, effective.str),
      df = StatLineProjection(character.statProfile.df, bonus.df, effective.df),
      agi = StatLineProjection(character.statProfile.agi, bonus.agi, effective.agi),
      crit = StatLineProjection(character.statProfile.crit, bonus.crit, effective.crit),
      injuries = character.injuries.toList(), physiology = PhysiologyStatusPolicy.derive(character.physiology),
      inventory = inventory, inventoryDetails = inventoryDetails, equipment = equipment,
      equipmentDetails = equipmentDetails, statusEffects = effects
    )
  }

  private fun itemDetail(state: GameState, character: CharacterState, item: ItemStack): ItemDetailProjection {
    val def = EquipmentCatalog.definition(item.itemId)
    val slots = state.equipment[character.equipmentId]?.slots.orEmpty().filterValues { it == item.itemId }.keys.sorted()
    val current = CharacterStatEngine.effective(state, character.id)
    val preview = if (slots.isEmpty()) EquipmentEngine.preview(state, character.id, item.itemId) else null
    val base = character.statProfile
    fun cmp(before: Int, after: Int) = StatComparisonProjection(before, after, after - before)
    val comparison = preview?.let { ItemComparisonProjection(
      cmp(current.maxHp, it.maxHp), cmp(current.str, it.str), cmp(current.df, it.df), cmp(current.agi, it.agi), cmp(current.crit, it.crit)
    ) }
    val baseItemEffect = def?.let { ItemComparisonProjection(
      cmp(base.baseMaxHp, base.baseMaxHp + it.bonuses.hp), cmp(base.str, base.str + it.bonuses.str),
      cmp(base.df, base.df + it.bonuses.df), cmp(base.agi, base.agi + it.bonuses.agi), cmp(base.crit, base.crit + it.bonuses.crit)
    ) }
    return ItemDetailProjection(
      id = item.itemId, name = def?.name ?: item.name, quantity = item.quantity, type = def?.type,
      slot = def?.primarySlot?.key ?: item.metadata["slot"], rarity = def?.rarity ?: item.metadata["rarity"],
      equipped = slots.isNotEmpty(), equippedSlots = slots, statItem = def?.let { it.bonuses.any() || it.weapon != null } ?: false,
      classification = def?.classification?.name, bonuses = def?.bonuses ?: EquipmentBonuses(), weapon = def?.weapon,
      abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(), components = def?.components.orEmpty(),
      comparison = comparison, baseItemEffect = baseItemEffect
    )
  }
}
''', encoding="utf-8")

DETAIL_JSON.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object CharacterDetailJson {
  fun encodeParty(projection: PartyDetailProjection): JSONObject = JSONObject().apply {
    put("leaderId", projection.leaderId); put("maxMembers", projection.maxMembers); put("elapsedSubjectiveMinutes", projection.elapsedSubjectiveMinutes)
    put("members", JSONArray().apply { projection.members.forEach { put(encodeCharacter(it)) } })
  }

  fun encodeCharacter(c: CharacterDetailProjection): JSONObject = JSONObject().apply {
    put("id", c.id); put("name", c.name); c.avatarRef?.let { put("avatar", it) }; put("presence", c.presence.name); put("isLeader", c.isLeader)
    c.healthState?.let { put("healthState", it) }; put("currentHp", c.currentHp); put("maxHp", c.maxHp)
    put("role", c.role); put("energy", c.energyDisplay); put("hpRegen", c.regenPerCompletedTurn); put("condition", c.condition.name)
    put("stats", JSONObject().apply { put("STR", stat(c.str)); put("DF", stat(c.df)); put("AGI", stat(c.agi)); put("CRIT", stat(c.crit)) })
    put("injuries", JSONArray(c.injuries))
    put("physiology", JSONObject().apply {
      put("hunger", c.physiology.hunger.name); put("thirst", c.physiology.thirst.name); put("sleepDeprivation", c.physiology.sleepDeprivation.name)
      c.physiology.foodPercent?.let { put("foodPercent", it) }; c.physiology.waterPercent?.let { put("waterPercent", it) }; c.physiology.restPercent?.let { put("restPercent", it) }
      c.physiology.pain?.let { put("pain", it) }; c.physiology.infection?.let { put("infection", it) }; c.physiology.thermal?.let { put("thermal", it) }
    })
    put("inventory", JSONArray().apply { c.inventoryDetails.forEach { put(item(it)) } })
    put("equipment", JSONObject(c.equipment))
    put("equipmentItems", JSONArray().apply { c.equipmentDetails.forEach { put(item(it)) } })
    put("statuses", JSONArray().apply { c.statusEffects.forEach { e -> put(JSONObject().put("id", e.id).put("type", e.type).put("persistent", e.persistent)) } })
  }

  private fun stat(x: StatLineProjection) = JSONObject().put("base", x.base).put("equipment", x.equipment).put("effective", x.effective)
  private fun comparison(x: StatComparisonProjection) = JSONObject().put("before", x.before).put("after", x.after).put("delta", x.delta)
  private fun itemComparison(x: ItemComparisonProjection) = JSONObject().apply {
    put("maxHp", comparison(x.maxHp)); put("STR", comparison(x.str)); put("DF", comparison(x.df)); put("AGI", comparison(x.agi)); put("CRIT", comparison(x.crit))
  }
  private fun bonuses(x: EquipmentBonuses) = JSONObject().put("HP", x.hp).put("STR", x.str).put("DF", x.df).put("AGI", x.agi).put("CRIT", x.crit)
  private fun weapon(x: WeaponGameplayStats) = JSONObject().apply {
    put("DMG", x.dmg); x.ammoDisplay?.let { put("ammo", it) }; x.rpmCapability?.let { put("rpm", it) }; put("fireModes", JSONArray(x.fireModes))
  }
  private fun component(x: EquipmentComponent) = JSONObject().apply { put("name", x.name); put("bonuses", bonuses(x.bonuses)); x.weapon?.let { put("weapon", weapon(it)) } }
  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    put("id", x.id); put("name", x.name); put("quantity", x.quantity); x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.statItem); x.classification?.let { put("classification", it) }
    put("bonuses", bonuses(x.bonuses)); x.weapon?.let { put("weapon", weapon(it)) }
    put("abilities", JSONArray().apply { x.abilities.forEach { a -> put(JSONObject().put("name", a.name).put("description", a.description).also { o -> a.importantLimit?.let { o.put("limit", it) } }) } })
    put("restrictions", JSONArray(x.restrictions)); put("components", JSONArray().apply { x.components.forEach { put(component(it)) } })
    x.comparison?.let { put("comparison", itemComparison(it)) }; x.baseItemEffect?.let { put("baseItemEffect", itemComparison(it)) }
  }
}
''', encoding="utf-8")

# --- Shared Character + Inventory Item Detail UI -----------------------------
html = INDEX.read_text(encoding="utf-8")
if 'id="equipmentDetailModal"' not in html:
    body_anchor = '</body>'
    modal = r'''
<div id="equipmentDetailModal" class="equipment-detail-modal" hidden>
  <div class="equipment-detail-sheet" role="dialog" aria-modal="true" aria-labelledby="equipmentDetailName">
    <button type="button" class="equipment-detail-close" id="equipmentDetailClose">×</button>
    <div class="equipment-detail-header"><div class="equipment-detail-icon" id="equipmentDetailIcon">EQ</div><div><div class="eyebrow" id="equipmentDetailClass"></div><h2 id="equipmentDetailName">Equipment</h2><div class="equipment-detail-meta" id="equipmentDetailMeta"></div></div></div>
    <section><h3>STATS</h3><div id="equipmentDetailStats"></div></section>
    <section id="equipmentDetailComparisonSection" hidden><h3>EFFECT / COMPARISON</h3><div id="equipmentDetailComparison"></div></section>
    <section><h3>SPECIAL ABILITIES</h3><div id="equipmentDetailAbilities"></div></section>
    <section><h3>CANON / RESTRICTIONS</h3><div id="equipmentDetailRestrictions"></div></section>
  </div>
</div>
'''
    if body_anchor not in html: raise RuntimeError("HTML body anchor missing")
    html = html.replace(body_anchor, modal + body_anchor, 1)

css_anchor = '</style>'
css = r'''
.character-role{margin-top:4px;color:#93a0a8;font-size:11px;letter-spacing:.04em}.character-core-stats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:10px 0}.character-core-stat{border:1px solid #30383d;background:#0a0d0f;padding:9px}.character-core-stat b{display:block;color:#7e8992;font-size:10px;letter-spacing:.12em}.character-core-stat strong{display:block;margin-top:4px;font-size:17px}.equipment-card,.inventory-item-card{display:grid;grid-template-columns:38px 1fr auto;gap:9px;align-items:center;border:1px solid #313940;background:#0b0f12;padding:8px;cursor:pointer}.equipment-card:hover,.inventory-item-card:hover{border-color:#52606a}.equipment-card-icon{width:38px;height:38px;display:grid;place-items:center;border:1px solid #39434a;background:#11171b;font-weight:900;font-size:11px}.equipment-card-main{min-width:0}.equipment-card-main strong{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.equipment-card-main small{display:block;color:#7f8b93;margin-top:3px}.equipment-badges{display:flex;gap:4px;flex-wrap:wrap;justify-content:flex-end}.equipment-badge{border:1px solid #45515a;padding:3px 5px;font-size:8px;letter-spacing:.08em}.equipment-badge.equipped{border-color:#3c8466;color:#8ed3b1}.equipment-badge.cheat{border-color:#8c7042;color:#e4c07e}.equipment-detail-modal{position:fixed;inset:0;z-index:120;background:rgba(0,0,0,.72);display:flex;align-items:flex-end;justify-content:center}.equipment-detail-modal[hidden]{display:none}.equipment-detail-sheet{position:relative;width:min(720px,100%);max-height:88vh;overflow:auto;background:#0b0e11;border:1px solid #3a444b;border-bottom:0;padding:18px}.equipment-detail-close{position:absolute;right:10px;top:10px;width:38px;height:38px}.equipment-detail-header{display:grid;grid-template-columns:58px 1fr;gap:12px;align-items:center;padding-right:42px}.equipment-detail-icon{width:58px;height:58px;border:1px solid #46525a;display:grid;place-items:center;font-weight:900}.equipment-detail-header h2{margin:3px 0}.equipment-detail-meta{color:#89949c;font-size:11px}.equipment-detail-sheet section{border-top:1px solid #2c3338;margin-top:15px;padding-top:13px}.equipment-detail-sheet section h3{font-size:11px;letter-spacing:.14em;margin:0 0 9px}.equipment-detail-row,.ability-row,.restriction-row{border:1px solid #2e373d;padding:8px;margin-top:6px}.equipment-detail-row{display:flex;justify-content:space-between;gap:12px}.ability-row strong{display:block}.ability-row p{margin:5px 0 0;color:#c0c8cd;font-size:12px}.ability-row em{display:block;margin-top:5px;color:#d0af77;font-style:normal;font-size:11px}.restriction-row{color:#c7b38b;font-size:12px}.stat-delta-positive{color:#8fd2ad}.stat-delta-negative{color:#dc9b9b}@media(max-width:520px){.character-core-stats{grid-template-columns:1fr 1fr}.equipment-detail-sheet{padding:14px}.equipment-card,.inventory-item-card{grid-template-columns:34px 1fr}.equipment-badges{grid-column:2;justify-content:flex-start}}
'''
if '.equipment-detail-modal{' not in html:
    if css_anchor not in html: raise RuntimeError("HTML style anchor missing")
    html = html.replace(css_anchor, css + css_anchor, 1)

# Expose selected character id to the shared detail UI.
if 'view.dataset.characterId=selectedCharacterId' not in html:
    html = html.replace("    selectedCharacterId=member.id||'kai';\n", "    selectedCharacterId=member.id||'kai';\n    if(view)view.dataset.characterId=selectedCharacterId;\n", 1)

script_anchor = '</body>'
script = r'''
<script>
(function(){
  const view=document.getElementById('characterInventoryView');
  const status=document.getElementById('characterStatusList');
  const equipment=document.getElementById('characterEquipmentList');
  const inventory=document.getElementById('characterInventoryItems');
  const nameEl=document.getElementById('characterInventoryName');
  const modal=document.getElementById('equipmentDetailModal');
  const close=document.getElementById('equipmentDetailClose');
  const q=id=>document.getElementById(id);
  const e=s=>typeof esc==='function'?esc(s):String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function members(){return state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[]}
  function selected(){const id=view&&view.dataset.characterId||'kai';return members().find(x=>String(x.id)===String(id))||members()[0]}
  function statText(s){if(!s)return '—';const b=Number(s.equipment)||0;return String(s.effective)+(b?(' ('+(b>0?'+':'')+b+')'):'')}
  function iconFor(item){return String((item&&item.type)||'EQ').split(/[ /_-]+/).filter(Boolean).map(x=>x[0]).join('').slice(0,3).toUpperCase()||'EQ'}
  function itemById(member,id){return (member&&member.inventory||[]).find(x=>String(x.id)===String(id))||(member&&member.equipmentItems||[]).find(x=>String(x.id)===String(id))}
  function card(item,slot){
    const badges=[];if(item.equipped)badges.push('<span class="equipment-badge equipped">EQUIPPED</span>');if(item.statItem)badges.push('<span class="equipment-badge">STAT ITEM</span>');if(item.classification==='SPECIAL_CHEAT')badges.push('<span class="equipment-badge cheat">SPECIAL / CHEAT</span>');
    return '<div class="'+(slot?'equipment-card':'inventory-item-card')+'" data-item-id="'+e(item.id)+'"><div class="equipment-card-icon">'+e(iconFor(item))+'</div><div class="equipment-card-main"><strong>'+e(item.name||item.id)+'</strong><small>'+e(slot?String(slot).toUpperCase():(item.rarity||('×'+(item.quantity||1))))+'</small></div><div class="equipment-badges">'+badges.join('')+'</div></div>';
  }
  function render(member){
    if(!member)return;
    if(nameEl){let role=document.getElementById('characterRole');if(!role){role=document.createElement('div');role.id='characterRole';role.className='character-role';nameEl.insertAdjacentElement('afterend',role)}role.textContent=member.role||'UNSPECIFIED'}
    const rows=[
      ['ENE',member.energy||'N/A'],['HP REGEN',(Number(member.hpRegen)||0)>0?('+'+member.hpRegen+' / completed turn'):'0'],['CONDITION',member.condition||'HEALTHY'],
      ['STR',statText(member.stats&&member.stats.STR)],['DF',statText(member.stats&&member.stats.DF)],['AGI',statText(member.stats&&member.stats.AGI)],['CRIT',statText(member.stats&&member.stats.CRIT)]
    ];
    if(status)status.innerHTML='<div class="character-core-stats">'+rows.map(r=>'<div class="character-core-stat"><b>'+e(r[0])+'</b><strong>'+e(r[1])+'</strong></div>').join('')+'</div>'+
      ((member.statuses&&member.statuses.length)?'<div class="character-status-row"><b>Status Effects</b><span>'+e(member.statuses.map(x=>x.type||x.id).join(', '))+'</span></div>':'');
    const eq=member.equipment||{},details=member.equipmentItems||[];
    if(equipment){const rendered=[];Object.keys(eq).sort().forEach(slot=>{const id=eq[slot],item=details.find(x=>String(x.id)===String(id))||itemById(member,id);if(item)rendered.push(card(item,slot))});equipment.innerHTML=rendered.length?rendered.join(''):'<span>Không có trang bị được ghi nhận.</span>'}
    if(inventory){inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null)).join('')||'<span>Trống.</span>'}
  }
  function statRows(item){
    const b=item.bonuses||{},w=item.weapon||{},rows=[];if(Number(b.HP))rows.push(['HP',(b.HP>0?'+':'')+b.HP]);if(Number(b.DF))rows.push(['DF',(b.DF>0?'+':'')+b.DF]);if(Number(b.STR))rows.push(['STR',(b.STR>0?'+':'')+b.STR]);if(Number(b.AGI))rows.push(['AGI',(b.AGI>0?'+':'')+b.AGI]);if(Number(b.CRIT))rows.push(['CRIT',(b.CRIT>0?'+':'')+b.CRIT]);if(w.DMG!=null)rows.push(['DMG',w.DMG]);if(w.ammo!=null)rows.push(['Ammo',w.ammo]);if(w.rpm!=null)rows.push(['Full Auto',w.rpm+' RPM']);return rows
  }
  function comparisonRows(c){if(!c)return[];return ['maxHp','STR','DF','AGI','CRIT'].map(k=>{const x=c[k];if(!x)return null;const d=Number(x.delta)||0;return [k==='maxHp'?'MAX HP':k,x.before+' → '+x.after+' ('+(d>=0?'+':'')+d+')',d]}).filter(Boolean)}
  function openItem(item){if(!item||!modal)return;q('equipmentDetailName').textContent=item.name||item.id;q('equipmentDetailIcon').textContent=iconFor(item);q('equipmentDetailClass').textContent=item.classification==='SPECIAL_CHEAT'?'SPECIAL / CHEAT':(item.type||'ITEM');q('equipmentDetailMeta').textContent=[item.type,item.slot,item.rarity,item.equipped?'EQUIPPED':'UNEQUIPPED'].filter(Boolean).join(' · ');
    const sr=statRows(item);q('equipmentDetailStats').innerHTML=sr.length?sr.map(r=>'<div class="equipment-detail-row"><span>'+e(r[0])+'</span><strong>'+e(r[1])+'</strong></div>').join(''):'<div class="equipment-detail-row"><span>Combat bonus</span><strong>0</strong></div>';
    const compare=item.comparison||((item.classification==='SPECIAL_CHEAT')?item.baseItemEffect:null),cr=comparisonRows(compare);const cs=q('equipmentDetailComparisonSection');cs.hidden=!cr.length;q('equipmentDetailComparison').innerHTML=cr.map(r=>'<div class="equipment-detail-row"><span>'+e(r[0])+'</span><strong class="'+(r[2]>=0?'stat-delta-positive':'stat-delta-negative')+'">'+e(r[1])+'</strong></div>').join('');
    const abs=item.abilities||[];q('equipmentDetailAbilities').innerHTML=abs.length?abs.map(a=>'<div class="ability-row"><strong>'+e(a.name)+'</strong><p>'+e(a.description)+'</p>'+(a.limit?'<em>'+e(a.limit)+'</em>':'')+'</div>').join(''):'<div class="ability-row"><p>Không có Special Ability được ghi nhận.</p></div>';
    const rr=item.restrictions||[];q('equipmentDetailRestrictions').innerHTML=rr.length?rr.map(x=>'<div class="restriction-row">'+e(x)+'</div>').join(''):'<div class="restriction-row">Không có restriction bổ sung.</div>';modal.hidden=false}
  if(view)view.addEventListener('click',ev=>{const card=ev.target.closest('[data-item-id]');if(!card)return;const member=selected();openItem(itemById(member,card.getAttribute('data-item-id')))});
  if(close)close.addEventListener('click',()=>modal.hidden=true);if(modal)modal.addEventListener('click',ev=>{if(ev.target===modal)modal.hidden=true});
  window.renderCharacterStatusEquipment=render;
  const oldRender=window.render;if(typeof oldRender==='function')window.render=function(){oldRender();render(selected())};
  setTimeout(()=>render(selected()),0);
})();
</script>
'''
if 'window.renderCharacterStatusEquipment=render;' not in html:
    if script_anchor not in html: raise RuntimeError("HTML script injection anchor missing")
    html = html.replace(script_anchor, script + script_anchor, 1)
INDEX.write_text(html, encoding="utf-8")

# --- Required regression tests ----------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CharacterStatusEquipmentSystemTest {
  private fun state() = CharacterEquipmentSystem.seedFresh(GameState.initial())
  private fun cmd(op: ItemCommand.Operation, item: String, slot: String? = null) = ItemCommand("T", null, KAI_ID, source = CommandSource.UI, operation = op, itemId = item, itemName = EquipmentCatalog.definition(item)?.name ?: item, slot = slot)

  @Test fun noEquipmentMeans100Over100() {
    val raw = GameState.initial()
    val kai = raw.characters.getValue(KAI_ID)
    val stripped = raw.copy(equipment = raw.equipment + (KAI_ID to EquipmentState(KAI_ID)), characters = raw.characters + (KAI_ID to kai.copy(vitalState = CharacterVitalState(100))))
    assertEquals(100, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)
    assertEquals(100, stripped.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun blackbloodAdds25MaxHpAndMissingHpIsPreservedBothWays() {
    var s = state()
    val kai = s.characters.getValue(KAI_ID)
    s = s.copy(equipment = s.equipment + (KAI_ID to EquipmentState(KAI_ID)), characters = s.characters + (KAI_ID to kai.copy(vitalState = CharacterVitalState(70))))
    val equip = EquipmentEngine.equip(s, cmd(ItemCommand.Operation.EQUIP, KAI_BLACKBLOOD_ARMOR_ID, "armor"))
    assertTrue(equip.applied); assertEquals(125, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp); assertEquals(95, equip.state.characters.getValue(KAI_ID).vitalState.currentHp)
    val unequip = EquipmentEngine.unequip(equip.state, cmd(ItemCommand.Operation.UNEQUIP, KAI_BLACKBLOOD_ARMOR_ID, "armor"))
    assertTrue(unequip.applied); assertEquals(100, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp); assertEquals(70, unequip.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun bonusesApplyOnceAndSaveLoadDoesNotMultiply() {
    val s = state(); val e = CharacterStatEngine.effective(s, KAI_ID)
    assertEquals(140, e.maxHp); assertEquals(107, e.str); assertEquals(109, e.df); assertEquals(112, e.agi); assertEquals(109, e.crit)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(s)); val e2 = CharacterStatEngine.effective(loaded, KAI_ID)
    assertEquals(e, e2)
  }

  @Test fun inventoryOwnsSameItemReferencedByEquipment() {
    val s = state(); val id = s.equipment.getValue(KAI_ID).slots.getValue("armor")
    assertSame(s.inventories.getValue(KAI_ID).items.getValue(id), s.inventories.getValue(KAI_ID).items[id])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, id)
  }

  @Test fun energyAndRegenProfilesAreCorrect() {
    val s = state()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID).forEach { id -> assertEquals(EnergyMode.INFINITE, s.characters.getValue(id).statProfile.energy.mode); assertEquals(4, s.characters.getValue(id).statProfile.regen.amountPerCompletedTurn) }
    assertEquals(EnergyMode.NOT_APPLICABLE, s.characters.getValue(AN_NHIEN_ID).statProfile.energy.mode); assertFalse(s.characters.getValue(AN_NHIEN_ID).statProfile.regen.enabled)
  }

  @Test fun regenRunsExactlyOnceAndZeroHpCannotBeRescued() {
    var s = state(); s = CharacterStatEngine.setCurrentHp(s, KAI_ID, 50)
    val once = CharacterStatEngine.applyCompletedTurnRegen(s, "TURN_X"); assertEquals(54, once.characters.getValue(KAI_ID).vitalState.currentHp)
    val twice = CharacterStatEngine.applyCompletedTurnRegen(once, "TURN_X"); assertEquals(54, twice.characters.getValue(KAI_ID).vitalState.currentHp)
    val zero = CharacterStatEngine.setCurrentHp(s, KAI_ID, 0); val after = CharacterStatEngine.applyCompletedTurnRegen(zero, "TURN_Z")
    assertEquals(0, after.characters.getValue(KAI_ID).vitalState.currentHp); assertEquals(CharacterCondition.DEFEATED, after.characters.getValue(KAI_ID).vitalState.condition)
  }

  @Test fun omnivaultMayHaveZeroCombatStatsWithAbilities() {
    val d = EquipmentCatalog.definition(KAI_OMNIVAULT_RING_ID)!!; assertFalse(d.bonuses.any()); assertTrue(d.abilities.isNotEmpty())
  }

  @Test fun reconFrameHasNoForbiddenWeapons() {
    val text = EquipmentCatalog.definition(IRIS_RECON_FRAME_ID)!!.abilities.joinToString(" ") { it.name + " " + it.description }.lowercase()
    assertFalse(text.contains("drone")); assertFalse(text.contains("launcher")); assertFalse(text.contains("missile")); assertFalse(text.contains("shoulder cannon"))
  }

  @Test fun irisInfiniteEnergyDoesNotCreateInfiniteWeaponDamageOrRpm() {
    val d = EquipmentCatalog.definition(IRIS_IVORY_EBONY_SET_ID)!!; assertEquals(24, d.weapon!!.dmg); assertNull(d.weapon!!.rpmCapability)
  }

  @Test fun godKillerRemainsMechanicalGreatsword() {
    val d = EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!; assertEquals("MECHANICAL GREATSWORD", d.type); assertFalse(d.restrictions.joinToString().lowercase().contains("gunblade allowed"))
  }

  @Test fun madGodIsNotCanonicalStartingLoadoutAndCountsOnceAcrossTwoSlots() {
    assertFalse(EquipmentCatalog.startingLoadout(KAI_ID).values.contains(MADGOD_SET_ID))
    var s = state(); val inv = s.inventories.getValue(KAI_ID); s = s.copy(inventories = s.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID)))))
    val r = EquipmentEngine.equip(s, cmd(ItemCommand.Operation.EQUIP, MADGOD_SET_ID, "weapon")); assertTrue(r.applied)
    assertEquals(MADGOD_SET_ID, r.state.equipment.getValue(KAI_ID).slots["weapon"]); assertEquals(MADGOD_SET_ID, r.state.equipment.getValue(KAI_ID).slots["armor"])
    assertTrue(r.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    val e = CharacterStatEngine.effective(r.state, KAI_ID); assertEquals(165, e.maxHp); assertEquals(114, e.str); assertEquals(121, e.df); assertEquals(118, e.agi); assertEquals(113, e.crit)
  }

  @Test fun madGodPermanentLockAndNormalizedDamage() {
    val d = EquipmentCatalog.definition(MADGOD_SET_ID)!!; assertEquals(55, d.weapon!!.dmg); assertEquals(50, d.bonuses.hp)
    var s = state(); val inv = s.inventories.getValue(KAI_ID); s = s.copy(inventories = s.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID)))))
    val equip = EquipmentEngine.equip(s, cmd(ItemCommand.Operation.EQUIP, MADGOD_SET_ID, "weapon")); val un = EquipmentEngine.unequip(equip.state, cmd(ItemCommand.Operation.UNEQUIP, MADGOD_SET_ID, "weapon"))
    assertFalse(un.applied); assertEquals("madgod_equipment_permanent", un.validation.reason)
  }

  @Test fun projectionAfterReloadEqualsBasePlusEquippedItems() {
    val s = GameStateCodec.decode(GameStateCodec.encode(state())); val p = CharacterDetailProjector.projectCharacter(s, KAI_ID)!!
    assertEquals(107, p.str.effective); assertEquals(25, p.str.equipment); assertEquals(82, p.str.base)
    assertEquals(CharacterStatEngine.effective(s, KAI_ID).maxHp, p.maxHp)
  }

  @Test fun futureCharacterGetsSafeFallback() {
    val x = CharacterState("future", "Future"); assertEquals(100, x.statProfile.baseMaxHp); assertEquals(10, x.statProfile.str); assertEquals(EnergyMode.NOT_APPLICABLE, x.statProfile.energy.mode)
  }

  @Test fun itemDetailDataOrdersStatsAbilitiesRestrictionsAndSharedUiExists() {
    val s = state(); val p = CharacterDetailProjector.projectCharacter(s, KAI_ID)!!; val armor = p.inventoryDetails.first { it.id == KAI_BLACKBLOOD_ARMOR_ID }
    assertEquals(25, armor.bonuses.hp); assertTrue(armor.abilities.isNotEmpty()); assertTrue(armor.equipped)
    val html = File("src/main/assets/index.html").readText()
    assertTrue(html.contains("equipmentDetailModal")); assertTrue(html.contains("SPECIAL ABILITIES")); assertTrue(html.contains("CANON / RESTRICTIONS")); assertTrue(html.contains("data-item-id"))
  }

  @Test fun ivoryEbonyIsOneDualWeaponSetAndGodKillerHas38Damage() {
    val s = state(); assertEquals(IRIS_IVORY_EBONY_SET_ID, s.equipment.getValue(IRIS_ID).slots["weapon"]); assertEquals(24, EquipmentCatalog.definition(IRIS_IVORY_EBONY_SET_ID)!!.weapon!!.dmg); assertEquals(38, EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!.weapon!!.dmg)
  }

  @Test fun derivedCacheIsRecalculatedInsteadOfTrusted() {
    val s = state(); val kai = s.characters.getValue(KAI_ID); val corrupt = s.copy(characters = s.characters + (KAI_ID to kai.copy(metadata = kai.metadata + mapOf("derived.equipmentHp" to "9999", "derived.effectiveMaxHp" to "9999"))))
    val fixed = CharacterEquipmentSystem.normalize(corrupt); assertEquals("40", fixed.characters.getValue(KAI_ID).metadata["derived.equipmentHp"]); assertEquals("140", fixed.characters.getValue(KAI_ID).metadata["derived.effectiveMaxHp"])
  }
}
''', encoding="utf-8")

# Contract check at patch time.
combined = SYSTEM.read_text(encoding="utf-8") + DETAIL.read_text(encoding="utf-8") + DETAIL_JSON.read_text(encoding="utf-8") + INDEX.read_text(encoding="utf-8") + ENGINES.read_text(encoding="utf-8") + COMBAT.read_text(encoding="utf-8")
required = [
  'EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6)', 'WeaponGameplayStats(32, "∞", 600',
  'IRIS_IVORY_EBONY_SET_ID', 'WeaponGameplayStats(38)', 'bonuses = EquipmentBonuses(hp = 50, str = 15, df = 30, agi = 12, crit = 12)',
  'CharacterStatEngine.applyCompletedTurnRegen', 'CharacterStatEngine.preserveMissingHp', 'EquipmentEngine.equip(state, command)',
  'window.renderCharacterStatusEquipment=render;', 'id="equipmentDetailModal"', 'SPECIAL ABILITIES', 'CANON / RESTRICTIONS',
  'CharacterStatEngine.weaponDamage(state, KAI_ID)', 'CombatStatMath.defenseReduction',
]
for marker in required:
    if marker not in combined:
        raise RuntimeError("Character Status/Equipment contract missing: " + marker)

print("Character Status + Equipment + Inventory Detail System installed: shared Item ownership, normalized stats, HP preservation, regen, UI detail, MadGod normalization, combat integration, and tests.")
