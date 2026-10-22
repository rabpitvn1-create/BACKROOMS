package com.rabpit.backroom.core

const val JANET_ID = "janet"
const val JANET_BAT_ID = "janet:bayou-belle"
const val JANET_VEST_ID = "janet:varsity-vest"
const val JANET_GLOVES_ID = "janet:grip-gloves"
const val JANET_BOOTS_ID = "janet:road-boots"

/** American student displaced from New York in 1988; a physical melee Follower. */
object JanetCanon {
  const val NAME = "Janet the Lady"
  const val FULL_NAME = "Janet Marie Boudreaux"
  const val NICKNAME = NAME
  const val CANON_REF = "JANET-THE-LADY-CODEX-R01"
  const val AVATAR_REF = "avatars/Janet_avatar.webp"
  const val SPRITE_REF = "Janet.webp"
  const val ENCOUNTER_RATE_BP = 500

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to JANET_BAT_ID,
    "outfit" to JANET_VEST_ID,
    "gauntlets" to JANET_GLOVES_ID,
    "footwear" to JANET_BOOTS_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = JANET_ID, name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(JANET_ID)
    )
    return base.copy(
      id = JANET_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = JANET_ID,
      equipmentId = JANET_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "species" to "human",
        "gender" to "female",
        "entity" to "false",
        "combatant" to "true",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterPolicy" to "CORE_SPAWN",
        "encounterAction" to "PHYSICAL",
        "encounterLevels" to "ALL",
        "encounterChance" to "5%",
        "nickname" to NICKNAME,
        "fullName" to FULL_NAME,
        "age" to "19",
        "originYear" to "1988",
        "origin" to "New York, USA; hometown Louisiana",
        "school" to "New Island University (fictional)",
        "combatStyle" to "close-range bat / impulsive counterattack / protecting friends",
        "signatureWeapon" to "Bayou Belle",
        "spriteRef" to SPRITE_REF,
        "canonRef" to CANON_REF,
        "inventoryProfile" to "special_companion"
      )
    )
  }

  /** Normalize current state without replacing the player's progress or known encounters. */
  fun ensure(state: GameState): GameState = state.copy(
    characters = state.characters + (JANET_ID to character(state.characters[JANET_ID])),
    inventories = state.inventories + (JANET_ID to
      (state.inventories[JANET_ID] ?: InventoryState(JANET_ID))),
    equipment = state.equipment + (JANET_ID to EquipmentState(
      JANET_ID, state.equipment[JANET_ID]?.slots.orEmpty() + equipmentSlots))
  )
}
