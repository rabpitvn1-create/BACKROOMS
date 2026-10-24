package com.rabpit.backroom.core

const val LUC_TRAM_TICH_QUANG_SET_ID = "luc-tram:tich-quang"

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
      id = CAO_MINH_HUYET_MA_KIEM_ID, name = "Huyết Ma Kiếm", type = "BẢN MỆNH MA KIẾM", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(32),
      abilities = listOf(
        ability("Kiếm Thể", "Chém, đâm, đỡ, khóa, phá pháp bằng chính thân kiếm."),
        ability("Ngự Kiếm", "Dùng thần niệm điều khiển kiếm rời tay, đổi hướng và truy kích khi có dấu vết mục tiêu cùng quỹ đạo hợp lệ."),
        ability("Huyết Sát Ma Khí", "Vạn Quỷ Ma Tâm gia cường lưỡi kiếm hoặc phóng kiếm áp bằng ma nguyên."),
        ability("Bản Mệnh Tái Sinh", "Vạn Quỷ Ma Tâm từng bước tái tạo chính thanh kiếm đã hỏng; không tự sinh kiếm thứ hai.")
      ),
      canonRef = "CAO_MINH_CODEX R17"
    ),
    EquipmentDefinition(
      id = CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, name = "Huyết Ma Chiến Khải", type = "BẢN MỆNH MA KHẢI", primarySlot = EquipmentSlot.ARMOR,
      abilities = listOf(
        ability("Ma Khải Đồng Bộ", "Ma Kim và huyết văn ngưng kết từ Vạn Quỷ Ma Tâm, vận hành như lớp da thứ hai; tăng bộc phát và phân tán lực đánh."),
        ability("Ma Trảo", "Giáp cẳng tay và bàn tay hóa trảo để cắt, bám, khóa hoặc xé pháp khí cự ly gần."),
        ability("Huyết Ấn", "Ma niệm ở lòng bàn tay kéo vật vô chủ hoặc vật đã khóa bằng thần thức trong phạm vi gần."),
        ability("Đạp Hư Ma Văn", "Bộc phát thân pháp, đạp lên ma nguyên ngưng tụ, đổi hướng giữa không trung và giảm lực tiếp đất."),
        ability("Thu Liễm Khí Tức", "Có thể chủ động làm mờ ma văn và huyết sát khí để di chuyển kín đáo."),
        ability("Bản Mệnh Tái Ngưng Kết", "Giáp vỡ được Vạn Quỷ Ma Tâm từng bước tái ngưng kết; không đồng nghĩa hồi HP nhân vật.")
      ),
      restrictions = listOf("Không phạt tốc độ di chuyển; giáp đồng bộ với kiếm thuật và nhục thân."),
      canonRef = "CAO_MINH_CODEX R17"
    ),
    EquipmentDefinition(
      id = CAO_MINH_VAN_KHO_GIOI_CHI_ID, name = "Vạn Khố Giới Chỉ", type = "PHÁP BẢO KHÔNG GIAN", primarySlot = EquipmentSlot.RING,
      abilities = listOf(
        ability("Vạn Khố", "Lưu trữ vật vô tri với dung lượng không giới hạn theo canon; không tác động sinh vật sống."),
        ability("Quét Mẫu", "Có đúng 3 ô mẫu. Chỉ quét vật gốc chưa Marked; bản sao không được làm nguồn quét."),
        ability("Sao Chép", "Tạo bản sao vật vô tri từ mẫu còn hiệu lực; mẫu bị ghi đè không thể tiếp tục sinh bản sao."),
        ability("Hoàn Nguyên", "Hoàn nguyên vật hợp lệ về trạng thái tốt nhất từng có.", "Cooldown 24 giờ riêng cho vật vừa hoàn nguyên thành công."),
        ability("Triệu Hồi", "Đưa vật đã lưu hoặc mẫu hợp lệ đến vị trí được chỉ định."),
        ability("Tái Trang Bị", "Khôi phục hoặc thay thế trang bị hợp lệ khi các điều kiện vật phẩm được đáp ứng.")
      ),
      restrictions = listOf("Không quét hoặc sao chép bản mệnh trang bị gắn với Cao Minh.", "Không quét/hoàn nguyên nguyên khối công trình hoặc hệ lớn lắp ghép."),
      canonRef = "CAO_MINH_CODEX R17 + VAN_KHO_RUNTIME"
    ),
    EquipmentDefinition(
      id = LUC_TRAM_KIEM_KHAI_ID, name = "Thiên Cơ Bạch Kim Kiếm Khải", type = "TIÊN HIỆP KIẾM KHẢI", primarySlot = EquipmentSlot.ARMOR,
      abilities = listOf(
        ability("Bạch Linh Kim Hộ Thể", "Giáp trắng viền vàng dẫn linh lực, khớp đen linh hoạt; bảo vệ yếu huyệt mà không khóa kiếm thế."),
        ability("Kiếm Trận Hộ Vệ", "Hỗ trợ giữ tuyến rút lui, đỡ đòn phá trận, che chắn đồng đội và người bị thương."),
        ability("Linh Lực Gia Tốc", "Hỗ trợ bộc phát thân pháp và phân tán lực đổi hướng giữa không trung.")
      ),
      restrictions = listOf("Pháp bảo thuần tu tiên, không có AI, cảm biến điện tử, súng, tên lửa hoặc động cơ hiện đại."),
      canonRef = "LUC_TRAM_CODEX R05"
    ),
    EquipmentDefinition(
      id = LUC_TRAM_TICH_QUANG_SET_ID, name = "Tịch Quang", type = "BẢN MỆNH LINH KIẾM", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(24),
      abilities = listOf(
        ability("Thiên Kiếm Thất Thức", "Vận dụng kiếm thế linh hoạt: phá vọng, xuyên điểm yếu, phản kiếm và bảo vệ tuyến rút lui."),
        ability("Kiếm Niệm Hồi Kiếm", "Có thể gọi Tịch Quang trở về tay trong phạm vi thần niệm cảm nhận hợp lệ."),
        ability("Kiếm Ý Dẫn Lực", "Đổi góc nhanh, chém khớp, phá pháp khí và mượn lực phản đòn.")
      ),
      restrictions = listOf("Linh kiếm không tự tái sinh vô hạn; hư hại nặng cần vật liệu và thời gian tu sửa."),
      canonRef = "LUC_TRAM_CODEX R05"
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
      id = AN_NHIEN_OUTFIT_ID, name = AnNhienCanon.OUTFIT_NAME, type = "OUTFIT", primarySlot = EquipmentSlot.OUTFIT,
      canonRef = "AN-NHIEN-CURRENT"
    ),
    EquipmentDefinition(
      id = AN_NHIEN_FOOTWEAR_ID, name = AnNhienCanon.FOOTWEAR_NAME, type = "FOOTWEAR", primarySlot = EquipmentSlot.FOOTWEAR,
      canonRef = "AN-NHIEN-CURRENT"
    )
  )

  private val definitions = all.associateBy { it.id }

  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    LUC_TRAM_TICH_QUANG_ID -> definitions[LUC_TRAM_TICH_QUANG_SET_ID]
    else -> definitions[itemId]
  }

  fun startingLoadout(characterId: String): Map<EquipmentSlot, String> = when (characterId) {
    CAO_MINH_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to CAO_MINH_HUYET_MA_KIEM_ID,
      EquipmentSlot.ARMOR to CAO_MINH_HUYET_MA_CHIEN_KHAI_ID,
      EquipmentSlot.RING to CAO_MINH_VAN_KHO_GIOI_CHI_ID
    )
    LUC_TRAM_ID -> linkedMapOf(EquipmentSlot.WEAPON to LUC_TRAM_TICH_QUANG_SET_ID, EquipmentSlot.ARMOR to LUC_TRAM_KIEM_KHAI_ID)
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
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
    val owned = inventory.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < 1) return invalid(state, "item_not_owned")
    val def = EquipmentCatalog.definition(command.itemId)
    if (def?.classification == ItemClassification.SPECIAL_CHEAT && command.actorId != CAO_MINH_ID) return invalid(state, "special_cheat_equipment_restricted")
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
    if (def.classification == ItemClassification.SPECIAL_CHEAT && characterId != CAO_MINH_ID) return null
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
