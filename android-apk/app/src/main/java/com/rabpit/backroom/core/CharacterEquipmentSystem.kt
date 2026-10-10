package com.rabpit.backroom.core

const val KAI_DEMON_JAW_MASK_ID = "kai:demon-jaw-mask"
const val KAI_TALON_GAUNTLETS_ID = "kai:talon-gauntlets"
const val KAI_PHANTOM_GREAVES_ID = "kai:phantom-greaves"
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

class EquipmentBonuses {
  fun any() = false
  override fun equals(other: Any?): Boolean = other is EquipmentBonuses
  override fun hashCode(): Int = 0
  override fun toString(): String = "EquipmentBonuses()"
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
      id = KAI_WHITE_WRAITH_ID, name = "Huyết Ma Kiếm", type = "MA ĐẠO ĐẠI KIẾM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(), weapon = WeaponGameplayStats(32),
      abilities = listOf(ability("Ngự kiếm", "Điều khiển kiếm bằng thần niệm."), ability("Huyết Sát Ma Khí", "Gia cường kiếm bằng ma nguyên."), ability("Triệu hồi bản mệnh", "Gọi Huyết Ma Kiếm trở về.")),
      canonRef = "CAO-EQP-HUYET-MA-KIEM-01"
    ),
    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "Huyết Ma Chiến Khải", type = "MA KHẢI", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(),
      abilities = listOf(ability("Ma Kim hộ thể", "Ma khải bảo vệ nhục thân và phân tán lực va chạm."), ability("Ma nguyên tự phục hồi", "Tự phục hồi pháp bảo; không đồng nghĩa hồi HP tức thì.")),
      canonRef = "CAO-EQP-HUYET-MA-KHAI-01"
    ),
    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Nhẫn Vạn Tàng", type = "NHẪN KHÔNG GIAN", primarySlot = EquipmentSlot.RING,
      abilities = listOf(ability("Tiểu không gian", "Lưu trữ vật phẩm theo authority của hệ thống hiện hành."), ability("Scan / Copy", "Giữ cơ chế Scan / Copy hiện hành theo yêu cầu đổi tên.")),
      canonRef = "CAO-EQP-VANTANG-01"
    ),
    EquipmentDefinition(
      id = IRIS_RECON_FRAME_ID, name = "Blackblood Recon Frame R03", type = "RECON ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(),
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
      id = SYVIAL_LUCIFER_ARMOR_ID, name = "Lucifer Armor", type = "ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(),
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

  fun startingLoadout(characterId: String): Map<EquipmentSlot, String> = when (characterId) {
    KAI_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to KAI_WHITE_WRAITH_ID,
      EquipmentSlot.ARMOR to KAI_BLACKBLOOD_ARMOR_ID,
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
  fun effective(state: GameState, characterId: String): EffectiveCharacterStats =
    CharacterStatCore.effective(state, characterId)

  private fun fallback(characterId: String): EffectiveCharacterStats {
    val p = CharacterStatProfiles.forId(characterId)
    val str = p.str.coerceIn(5, 999)
    val def = p.def.coerceIn(5, 999)
    val skl = p.skl.coerceIn(5, 999)
    val vit = p.vit.coerceIn(5, 999)
    return EffectiveCharacterStats(
      maxHp = CharacterStatCore.scaleByPercent(CharacterProgressionCore.BASE_MAX_HP, CharacterProgressionCore.statPercent(vit)),
      str = str, def = def, skl = skl, vit = vit,
      criticalChancePercent = CharacterStatCore.criticalChance(skl),
      evasionPercent = CharacterStatCore.evasion(vit),
      resCriticalPercent = CharacterStatCore.criticalResistance(def),
      resEvasionPercent = CharacterStatCore.evasionResistance(skl),
      energy = p.energy
    )
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
    return state.copy(characters = state.characters + (characterId to character.copy(
      vitalState = character.vitalState.copy(
        currentHp = nextHp,
        condition = conditionFor(nextHp, maxHp, character.vitalState.condition, character.presence)
      )
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
    val sklScaled = CharacterStatCore.scaleByPercent(
      weaponDamage(state, characterId),
      CharacterProgressionCore.statPercent(effective(state, characterId).skl)
    )
    return if (CharacterStatCore.isCaoMinh(characterId)) {
      CharacterStatCore.scaleByPercent(sklScaled, CaoMinhCombatPassive.attackPercent(state))
    } else sklScaled
  }
}

object CombatStatMath {
  fun critChancePercent(skl: Int): Int = CharacterStatCore.criticalChance(skl)
  fun defenseReduction(defRating: Int): Int = 0
  fun agilityDefense(vitRating: Int): Int = 0
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
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
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
  private const val SCHEMA_VERSION = "2-core-stats-1193a"

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)
  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)

  private fun normalizeInternal(source: GameState, seedStarting: Boolean): GameState {
    // Preserve the follower compatibility installed earlier in the patch chain.
    // Lucia exists in Core state without being forced into Party.
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()
    input.characters.keys.forEach { characterId ->
      var inv = inventories[characterId] ?: InventoryState(characterId)
      val eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()
      val loadout = EquipmentCatalog.startingLoadout(characterId)
      if (seedStarting) {
        loadout.forEach { (slot, itemId) ->
          if (slot.key !in slots) slots[slot.key] = itemId
          if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
        }
      }
      slots.values.distinct().forEach { itemId ->
        if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
      }
      inv = inv.copy(items = inv.items.mapValues { (_, stack) -> EquipmentCatalog.mergeDefinitionMetadata(stack) })
      inventories[characterId] = inv
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
