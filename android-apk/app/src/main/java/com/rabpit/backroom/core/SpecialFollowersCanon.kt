package com.rabpit.backroom.core

const val LUC_TRAM_ID = "luc-tram"
const val SYVIAL_ID = "syvial"

const val LUC_TRAM_TICH_QUANG_ID = "luc-tram:tich-quang"
const val LUC_TRAM_KIEM_KHAI_ID = "luc-tram:thien-co-bach-kim-kiem-khai"
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val ENCOUNTER_LEVELS = "0-6"

  val lucTramEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to LUC_TRAM_TICH_QUANG_SET_ID,
    "armor" to LUC_TRAM_KIEM_KHAI_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun lucTramCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      inventoryId = LUC_TRAM_ID,
      equipmentId = LUC_TRAM_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Chính Đạo kiếm tu / Bạch Hồng Kiếm Nữ",
        "combatStyle" to "Kiếm tu",
        "signatureWeapon" to "Tịch Quang",
        "armor" to "Thiên Cơ Bạch Kim Kiếm Khải",
        "canonRef" to "LUC_TRAM_CODEX R05 + PROLOGUE ADDENDUM R06",
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
        "role" to "High-level supernatural swordswoman",
        "signatureWeapon" to "GodKiller",
        "armor" to "Lucifer Armor",
        "canonRef" to "SYVIAL-LUCIFER-CODEX-20260816-R03",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState {
    val lucTram = lucTramCharacter(state.characters[LUC_TRAM_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val lucTramInventory = state.inventories[LUC_TRAM_ID] ?: InventoryState(LUC_TRAM_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val lucTramEquipment = state.equipment[LUC_TRAM_ID]?.slots.orEmpty() + lucTramEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (LUC_TRAM_ID to lucTram) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (LUC_TRAM_ID to lucTramInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, lucTramEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
}
