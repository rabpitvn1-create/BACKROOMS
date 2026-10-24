package com.rabpit.backroom.core

data class StatLineProjection(val base: Int, val equipment: Int = 0, val effective: Int = base, val passive: Int = effective - base)

data class ItemDetailProjection(
  val id: String,
  val name: String,
  val quantity: Int,
  val type: String? = null,
  val slot: String? = null,
  val rarity: String? = null,
  val equipped: Boolean = false,
  val equippedSlots: List<String> = emptyList(),
  val statItem: Boolean = false,
  val classification: String? = null,
  val weapon: WeaponGameplayStats? = null,
  val abilities: List<EquipmentAbility> = emptyList(),
  val restrictions: List<String> = emptyList(),
  val components: List<EquipmentComponent> = emptyList(),
  val iconRef: String? = null,
  val bonuses: EquipmentBonuses = EquipmentBonuses(),
  val consumesInventorySlot: Boolean? = null
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
  val role: String = "UNSPECIFIED",
  val energyDisplay: String = "N/A",
  val regenPerCompletedTurn: Int = 0,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val str: StatLineProjection = StatLineProjection(5),
  val def: StatLineProjection = StatLineProjection(5),
  val skl: StatLineProjection = StatLineProjection(5),
  val vit: StatLineProjection = StatLineProjection(5),
  val passives: List<String> = emptyList(),
  val criticalChancePercent: Int = 5,
  val criticalDamagePercent: Int = 150,
  val evasionPercent: Int = 0,
  val criticalResistancePercent: Int = 0,
  val evasionResistancePercent: Int = 0,
  val injuries: List<String>,
  val physiology: DerivedPhysiologyStatus,
  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection> = emptyList(),
  val inventoryCapacityUsed: Int = 0,
  val inventoryCapacityMax: Int = 9,
  val equipment: Map<String, String>,
  val equipmentDetails: List<ItemDetailProjection> = emptyList(),
  val statusEffects: List<StatusEffect>
) {
}

data class PartyDetailProjection(
  val leaderId: String,
  val maxMembers: Int,
  val elapsedSubjectiveMinutes: Long,
  val members: List<CharacterDetailProjection>
)

object CharacterDetailProjector {
  fun projectParty(state: GameState): PartyDetailProjection {
    val normalized = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(state))
    return PartyDetailProjection(
      normalized.party.leaderId, normalized.party.maxMembers, normalized.time.elapsedSubjectiveMinutes,
      normalized.party.memberIds.mapNotNull { projectCharacter(normalized, it) }
    )
  }

  fun projectCharacter(state: GameState, characterId: String): CharacterDetailProjection? {
    val normalized = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(state))
    val c = normalized.characters[characterId] ?: return null
    val effective = CharacterStatCore.effective(normalized, characterId)
    val base = c.statProfile
    val inventory = normalized.inventories[c.inventoryId]?.items?.values.orEmpty().sortedBy { it.itemId }
    val equipment = normalized.equipment[c.equipmentId]?.slots.orEmpty().toSortedMap()
    val equippedIds = equipment.values.toSet()
    fun carried(itemId: String) = itemId !in equippedIds &&
      !InventoryCapacityPolicy.isOwnerCanon(characterId, itemId)
    fun itemDetail(item: ItemStack): ItemDetailProjection {
      val def = EquipmentCatalog.definition(item.itemId)
      val slots = equipment.filterValues { it == item.itemId }.keys.sorted()
      return ItemDetailProjection(
        id = item.itemId, name = def?.name ?: item.name, quantity = item.quantity,
        type = def?.type, slot = def?.primarySlot?.key ?: item.metadata["slot"], rarity = def?.rarity,
        equipped = slots.isNotEmpty(), equippedSlots = slots,
        statItem = def?.weapon != null || def?.bonuses?.any() == true, classification = def?.classification?.name,
        weapon = def?.weapon, abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(),
        components = def?.components.orEmpty(), iconRef = def?.iconRef,
        bonuses = def?.bonuses ?: EquipmentBonuses(),
        consumesInventorySlot = carried(item.itemId)
      )
    }
    val details = inventory.map(::itemDetail)
    // Canon owner gear is projected only under Equipment, not carried Inventory.
    val carriedIds = inventory.filter { carried(it.itemId) }.map { it.itemId }.toSet()
    val carriedDetails = details.filter { it.id in carriedIds }
    val equipmentDetails = details.filter {
      it.equipped || InventoryCapacityPolicy.isOwnerCanon(characterId, it.id)
    }
    val gear = CharacterStatEngine.equippedBonuses(normalized, characterId)
    fun statLine(raw: Int, value: Int, percent: Int = 0): StatLineProjection {
      val equipmentBonus = (CharacterStatCore.scaleByPercent(raw, 100 + percent) - raw)
        .coerceIn(0, (CharacterProgressionCore.MAX_STAT - raw).coerceAtLeast(0))
      return StatLineProjection(raw, equipmentBonus, value, value - raw - equipmentBonus)
    }
    return CharacterDetailProjection(
      id = c.id, name = c.name, avatarRef = c.avatarRef, presence = c.presence,
      isLeader = normalized.party.leaderId == c.id, healthState = c.healthState,
      currentHp = c.vitalState.currentHp.coerceIn(0, effective.maxHp), maxHp = effective.maxHp,
      role = base.combatRole,
      energyDisplay = when (base.energy.mode) {
        EnergyMode.INFINITE -> "∞"
        EnergyMode.FINITE -> (base.energy.max ?: 0).toString()
        EnergyMode.NOT_APPLICABLE -> "N/A"
      },
      regenPerCompletedTurn = effective.regenPerCompletedTurn,
      condition = c.vitalState.condition,
      str = statLine(base.str, effective.str),
      def = statLine(base.def, effective.def, gear.defPercent),
      skl = statLine(base.skl, effective.skl),
      vit = statLine(base.vit, effective.vit, gear.vitPercent),
      criticalChancePercent = effective.criticalChancePercent,
      criticalDamagePercent = effective.criticalDamagePercent,
      evasionPercent = effective.evasionPercent,
      criticalResistancePercent = effective.resCriticalPercent,
      evasionResistancePercent = effective.resEvasionPercent,
      injuries = c.injuries.toList(), physiology = PhysiologyStatusPolicy.derive(c.physiology),
      inventory = inventory.filter { it.itemId in carriedIds }, inventoryDetails = carriedDetails,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(normalized, c.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(normalized, c.id),
      equipment = equipment, equipmentDetails = equipmentDetails,
      passives = CompanionSkillCatalog.forCharacter(c.id).filter { it.kind == "PASSIVE" }.map { it.name },
      statusEffects = c.statusIds.mapNotNull(normalized.statuses::get)
    )
  }
}
