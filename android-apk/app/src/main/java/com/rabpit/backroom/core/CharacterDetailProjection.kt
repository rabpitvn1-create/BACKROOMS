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
  val components: List<EquipmentComponent> = emptyList()
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
    fun itemDetail(item: ItemStack): ItemDetailProjection {
      val def = EquipmentCatalog.definition(item.itemId)
      val slots = equipment.filterValues { it == item.itemId }.keys.sorted()
      return ItemDetailProjection(
        id = item.itemId, name = def?.name ?: item.name, quantity = item.quantity,
        type = def?.type, slot = def?.primarySlot?.key ?: item.metadata["slot"], rarity = def?.rarity,
        equipped = slots.isNotEmpty(), equippedSlots = slots,
        statItem = def?.weapon != null, classification = def?.classification?.name,
        weapon = def?.weapon, abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(),
        components = def?.components.orEmpty()
      )
    }
    val details = inventory.map(::itemDetail)
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
      str = StatLineProjection(base.str, 0, effective.str),
      def = StatLineProjection(base.def, 0, effective.def),
      skl = StatLineProjection(base.skl, 0, effective.skl),
      vit = StatLineProjection(base.vit, 0, effective.vit),
      criticalChancePercent = effective.criticalChancePercent,
      criticalDamagePercent = effective.criticalDamagePercent,
      evasionPercent = effective.evasionPercent,
      criticalResistancePercent = effective.resCriticalPercent,
      evasionResistancePercent = effective.resEvasionPercent,
      injuries = c.injuries.toList(), physiology = PhysiologyStatusPolicy.derive(c.physiology),
      inventory = inventory.toList(), inventoryDetails = details,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(normalized, c.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(normalized, c.id),
      equipment = equipment, equipmentDetails = details.filter { it.equipped },
      statusEffects = c.statusIds.mapNotNull(normalized.statuses::get)
    )
  }
}
