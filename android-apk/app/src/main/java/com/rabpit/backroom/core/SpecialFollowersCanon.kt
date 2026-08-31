package com.rabpit.backroom.core

const val IRIS_ID = "iris"
const val SYVIAL_ID = "syvial"

const val IRIS_IVORY_ID = "iris:ivory"
const val IRIS_EBONY_ID = "iris:ebony"
const val IRIS_PROJECT_07_ID = "iris:project-07"
const val IRIS_LEGACY_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"
const val IRIS_RECON_FRAME_ID = IRIS_PROJECT_07_ID
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val ENCOUNTER_LEVELS = "0-6"
  const val IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"
  const val SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"
  const val IRIS_PARTY_CHEAT_CODE = "/iris123"
  const val SYVIAL_PARTY_CHEAT_CODE = "/Syv123"

  val irisEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to IRIS_IVORY_EBONY_SET_ID,
    "armor" to IRIS_RECON_FRAME_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun irisCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = IRIS_ID,
      name = "Iris",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = IRIS_ID,
      name = "Iris",
      avatarRef = IRIS_AVATAR_REF,
      inventoryId = IRIS_ID,
      equipmentId = IRIS_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Scout / Target Eliminator",
        "combatStyle" to "Gunslinger",
        "signatureWeapons" to "Ivory & Ebony",
        "armor" to "Project 07",
        "canonRef" to "IRIS-BELIAL-SRU-CODEX-20260830-R06",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun syvialCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = SYVIAL_ID,
      name = "Syvial",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      avatarRef = SYVIAL_AVATAR_REF,
      inventoryId = SYVIAL_ID,
      equipmentId = SYVIAL_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "combatTier" to "UR+",
        "role" to "SRU Deputy Leader / High-Speed Swordswoman",
        "signatureWeapon" to "GodKiller",
        "armor" to "Lucifer Armor",
        "canonRef" to "SYVIAL-LUCIFER-CODEX-CURRENT",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun matchesPartyCheatCode(action: String): String? = when (action.trim()) {
    IRIS_PARTY_CHEAT_CODE -> IRIS_ID
    SYVIAL_PARTY_CHEAT_CODE -> SYVIAL_ID
    else -> null
  }

  fun forceIntoParty(state: GameState, targetId: String): Pair<GameState, String?> {
    if (targetId != IRIS_ID && targetId != SYVIAL_ID) return state to "unknown_follower"
    val ensured = ensure(state)
    if (targetId in ensured.party.memberIds) return ensured to null
    if (ensured.party.memberIds.size >= ensured.party.maxMembers) return ensured to "party_full"
    return ensured.copy(
      party = ensured.party.copy(memberIds = ensured.party.memberIds + targetId)
    ) to null
  }

  fun ensure(state: GameState): GameState {
    val iris = irisCharacter(state.characters[IRIS_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val irisInventory = state.inventories[IRIS_ID] ?: InventoryState(IRIS_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val irisEquipment = state.equipment[IRIS_ID]?.slots.orEmpty() + irisEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (IRIS_ID to iris) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (IRIS_ID to irisInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (IRIS_ID to EquipmentState(IRIS_ID, irisEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
}
