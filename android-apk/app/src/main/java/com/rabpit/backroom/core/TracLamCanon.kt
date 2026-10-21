package com.rabpit.backroom.core

const val TRAC_LAM_ID = "trac_lam"
const val TRAC_LAM_GEMINI_ID = "trac_lam:m92-gemini"
const val TRAC_LAM_AEGIS_ID = "trac_lam:aegis-k4"
const val TRAC_LAM_SCOUT_ID = "trac_lam:scout-kit"
const val TRAC_LAM_MEDKIT_ID = "trac_lam:sru-medkit"

/** Drive /Novel/Characters/TRAC_LAM_CODEX is authoritative for identity and first contact. */
object TracLamCanon {
  const val NAME = "Trác Lâm"
  const val CANON_REF = "TRAC_LAM_CODEX"
  const val AVATAR_REF = "avatars/TracLam_avatar.jpg"
  const val SPRITE_REF = "TracLam.png"
  const val ENCOUNTER_RATE_BP = 300 // 3% of eligible Level 0.7 exploration turns

  val equipmentSlots = linkedMapOf(
    "weapon" to TRAC_LAM_GEMINI_ID,
    "armor" to TRAC_LAM_AEGIS_ID,
    "special" to TRAC_LAM_SCOUT_ID,
    "wrist" to TRAC_LAM_MEDKIT_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val source = existing ?: CharacterState(
      id = TRAC_LAM_ID, name = NAME, physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(TRAC_LAM_ID)
    )
    return source.copy(
      id = TRAC_LAM_ID, name = NAME, avatarRef = AVATAR_REF,
      inventoryId = TRAC_LAM_ID, equipmentId = TRAC_LAM_ID,
      metadata = source.metadata + mapOf(
        "npcType" to "follower", "species" to "human", "gender" to "male",
        "entity" to "false", "combatant" to "true", "joinEligible" to "true",
        "followsPlayer" to "true", "encounterPolicy" to "CORE_SPAWN",
        "encounterAction" to "PHYSICAL", "encounterLevels" to "0.7",
        "encounterChance" to "3%", "age" to "28", "originYear" to "2099",
        "origin" to "Đà Nẵng, Việt Nam", "organization" to "SRU-03",
        "role" to "Special Response Unit Captain", "firstContact" to "Level 0.7",
        "spriteRef" to SPRITE_REF, "canonRef" to CANON_REF,
        "inventoryProfile" to "special_companion"
      )
    )
  }

  /** Safe for the current save schema: inject only missing character/equipment. */
  fun ensure(state: GameState): GameState {
    val current = state.equipment[TRAC_LAM_ID]?.slots
    return state.copy(
      characters = state.characters + (TRAC_LAM_ID to character(state.characters[TRAC_LAM_ID])),
      inventories = state.inventories + (TRAC_LAM_ID to
        (state.inventories[TRAC_LAM_ID] ?: InventoryState(TRAC_LAM_ID))),
      equipment = state.equipment + (TRAC_LAM_ID to EquipmentState(
        TRAC_LAM_ID, equipmentSlots + current.orEmpty()))
    )
  }
}
