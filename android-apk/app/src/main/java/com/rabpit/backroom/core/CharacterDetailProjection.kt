package com.rabpit.backroom.core

data class CharacterDetailProjection(
  val id: String,
  val name: String,
  val avatarRef: String?,
  val presence: CharacterPresence,
  val isLeader: Boolean,
  val healthState: String?,
  val currentHp: Int,
  val maxHp: Int,
  val injuries: List<String>,
  val physiology: DerivedPhysiologyStatus,
  val inventory: List<ItemStack>,
  val equipment: Map<String, String>,
  val statusEffects: List<StatusEffect>
)

data class PartyDetailProjection(
  val leaderId: String,
  val maxMembers: Int,
  val elapsedSubjectiveMinutes: Long,
  val members: List<CharacterDetailProjection>
)

/** Read-only projection for party/character UI. It never mutates or persists derived values. */
object CharacterDetailProjector {
  fun projectParty(state: GameState): PartyDetailProjection {
    val members = state.party.memberIds.mapNotNull { id ->
      state.characters[id]?.let { projectCharacter(state, it) }
    }
    return PartyDetailProjection(
      leaderId = state.party.leaderId,
      maxMembers = state.party.maxMembers,
      elapsedSubjectiveMinutes = state.time.elapsedSubjectiveMinutes,
      members = members
    )
  }

  fun projectCharacter(state: GameState, characterId: String): CharacterDetailProjection? =
    state.characters[characterId]?.let { projectCharacter(state, it) }

  private fun projectCharacter(state: GameState, character: CharacterState): CharacterDetailProjection {
    val inventory = state.inventories[character.inventoryId]?.items?.values.orEmpty()
      .sortedWith(compareBy<ItemStack> { it.name.lowercase() }.thenBy { it.itemId })
    val equipment = state.equipment[character.equipmentId]?.slots.orEmpty().toSortedMap()
    val effects = character.statusIds.mapNotNull(state.statuses::get)
      .sortedWith(compareBy<StatusEffect> { it.type }.thenBy { it.id })
    val health = healthFor(state, character)

    return CharacterDetailProjection(
      id = character.id,
      name = character.name,
      avatarRef = character.avatarRef,
      presence = character.presence,
      isLeader = character.id == state.party.leaderId,
      healthState = character.healthState,
      currentHp = health.first,
      maxHp = health.second,
      injuries = character.injuries.toList(),
      physiology = PhysiologyStatusPolicy.derive(character.physiology),
      inventory = inventory,
      equipment = equipment,
      statusEffects = effects
    )
  }

  private fun healthFor(state: GameState, character: CharacterState): Pair<Int, Int> {
    val metadata = if (character.id == KAI_ID) state.metadata else character.metadata
    val maxHp = (
      metadata[if (character.id == KAI_ID) "combat.playerMaxHp" else "maxHp"]?.toIntOrNull()
        ?: metadata["healthMax"]?.toIntOrNull()
        ?: 100
      ).coerceIn(1, 999)
    val currentHp = (
      metadata[if (character.id == KAI_ID) "combat.playerHp" else "hp"]?.toIntOrNull()
        ?: metadata["healthCurrent"]?.toIntOrNull()
        ?: maxHp
      ).coerceIn(0, maxHp)
    return currentHp to maxHp
  }
}
