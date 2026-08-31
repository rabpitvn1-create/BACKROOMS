package com.rabpit.backroom.core

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
  val inspection: ItemInspection,
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
  val role: String = "UNSPECIFIED",
  val energyDisplay: String = "N/A",
  val regenPerCompletedTurn: Int = 0,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val str: StatLineProjection = StatLineProjection(10, 0, 10),
  val df: StatLineProjection = StatLineProjection(10, 0, 10),
  val agi: StatLineProjection = StatLineProjection(10, 0, 10),
  val crit: StatLineProjection = StatLineProjection(10, 0, 10),
  val injuries: List<String>,
  val physiology: DerivedPhysiologyStatus,
  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection> = emptyList(),
  val inventoryCapacityUsed: Int = 0,
  val inventoryCapacityMax: Int = 9,
  val equipment: Map<String, String>,
  val equipmentDetails: List<ItemDetailProjection> = emptyList(),
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
      id = character.id, name = character.name, avatarRef = if (character.id == KAI_ID && (character.avatarRef.isNullOrBlank() || character.avatarRef == "avatars/kai_avatar.png" || character.avatarRef == "avatars/Kai_New_Avatar.jpg" || character.avatarRef == "avatars/Kai2_avatar.jpg" || character.avatarRef == "avatars/MadGod.jpg")) "avatars/SRU_AVATAR.jpg" else character.avatarRef, presence = character.presence,
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
      inventory = inventory, inventoryDetails = inventoryDetails,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(state, character.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(state, character.id),
       equipment = equipment,
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
      inspection = ItemSystem.inspect(item, ownerId = character.id),
      comparison = comparison, baseItemEffect = baseItemEffect
    )
  }
}
