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
  val bonuses: EquipmentBonuses? = null
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
  val level: Int = 0,
  val exp: Int = 0,
  val expToNextLevel: Int? = 10,
  val role: String = "UNSPECIFIED",
  val energyDisplay: String = "N/A",
  val regenPerCompletedTurn: Int = 0,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val str: StatLineProjection = StatLineProjection(5),
  val dex: StatLineProjection = StatLineProjection(5),
  val agi: StatLineProjection = StatLineProjection(5),
  val vit: StatLineProjection = StatLineProjection(5),
  val eva: StatLineProjection = StatLineProjection(5),
  val defense: Int = 0,
  val attackPercent: Int = 100,
  val skillDamagePercent: Int = 100,
  val criticalChancePercent: Int = 5,
  val criticalDamagePercent: Int = 400,
  val evasionPercent: Int = 5,
  val criticalResistancePercent: Int = 0,
  val evasionResistancePercent: Int = 0,
  val criticalChanceBasisPoints: Int = 500,
  val evasionBasisPoints: Int = 500,
  val criticalResistanceBasisPoints: Int = 0,
  val evasionResistanceBasisPoints: Int = 0,
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
  /** Equipment's additive contribution to a stat, decomposed as effective - base - passive.
   *  Mirrors CharacterStatCore's passive formula so the split stays consistent with the engine. */
  private fun equipmentContribution(base: Int, effective: Int, characterId: String): Int {
    val b = base.coerceIn(CharacterProgressionCore.BASE_STAT, CharacterProgressionCore.MAX_STAT)
    val passive = if (CharacterStatCore.isCaoMinh(characterId))
      (b * CharacterStatCore.DAI_DAO_MA_TON_STAT_BONUS_PERCENT + 50) / 100 else 0
    return (effective - b - passive).coerceAtLeast(0)
  }

  private fun statLine(base: Int, effective: Int, characterId: String): StatLineProjection {
    val equipment = equipmentContribution(base, effective, characterId)
    return StatLineProjection(
      base = base, equipment = equipment, effective = effective,
      passive = effective - base - equipment
    )
  }

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
        statItem = def?.bonuses?.any() == true || def?.weapon != null, classification = def?.classification?.name,
        weapon = def?.weapon, abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(),
        components = def?.components.orEmpty(), bonuses = def?.bonuses
      )
    }
    val details = inventory.map(::itemDetail)
    val equippedDetails = equipment.values.distinct().map { EquipmentCatalog.stackFor(it) }.map(::itemDetail)
    return CharacterDetailProjection(
      id = c.id, name = c.name,
      avatarRef = if (c.id == CAO_MINH_ID) {
        if (equipment["weapon"] == MADGOD_SET_ID) MadGodCanon.AVATAR_ASSET else CAO_MINH_AVATAR_REF
      } else c.avatarRef,
      presence = c.presence,
      isLeader = normalized.party.leaderId == c.id, healthState = c.healthState,
      currentHp = c.vitalState.currentHp.coerceIn(0, effective.maxHp), maxHp = effective.maxHp,
      level = c.progression.level, exp = c.progression.exp,
      expToNextLevel = if (c.progression.level < CharacterProgressionCore.MAX_LEVEL)
        CharacterProgressionCore.expRequired(c.progression.level) else null,
      role = base.combatRole,
      energyDisplay = when (base.energy.mode) {
        EnergyMode.INFINITE -> "∞"
        EnergyMode.FINITE -> (base.energy.max ?: 0).toString()
        EnergyMode.NOT_APPLICABLE -> "N/A"
      },
      regenPerCompletedTurn = effective.regenPerCompletedTurn,
      condition = c.vitalState.condition,
      str = statLine(base.str, effective.str, c.id),
      dex = statLine(base.dex, effective.dex, c.id),
      agi = statLine(base.agi, effective.agi, c.id),
      vit = statLine(base.vit, effective.vit, c.id),
      eva = statLine(base.eva, effective.eva, c.id),
      defense = effective.def,
      attackPercent = effective.attackPercent,
      skillDamagePercent = effective.skillDamagePercent,
      criticalChancePercent = effective.criticalChancePercent,
      criticalDamagePercent = effective.criticalDamagePercent,
      evasionPercent = effective.evasionPercent,
      criticalResistancePercent = effective.resCriticalPercent,
      evasionResistancePercent = effective.resEvasionPercent,
      criticalChanceBasisPoints = effective.criticalChanceBasisPoints,
      evasionBasisPoints = effective.evasionBasisPoints,
      criticalResistanceBasisPoints = effective.resCriticalBasisPoints,
      evasionResistanceBasisPoints = effective.resEvasionBasisPoints,
      injuries = c.injuries.toList(), physiology = PhysiologyStatusPolicy.derive(c.physiology),
      inventory = inventory.toList(), inventoryDetails = details,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(normalized, c.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(normalized, c.id),
      equipment = equipment, equipmentDetails = equippedDetails,
      statusEffects = c.statusIds.mapNotNull(normalized.statuses::get)
    )
  }
}
