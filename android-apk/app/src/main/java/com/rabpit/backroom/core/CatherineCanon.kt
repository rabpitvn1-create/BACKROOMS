package com.rabpit.backroom.core

const val CATHERINE_ID = "catherine"
const val CATHERINE_VESPER_ID = "catherine:vesper-nocturne"
const val CATHERINE_COAT_ID = "catherine:ashcombe-coat"
const val CATHERINE_GLOVES_ID = "catherine:velvet-gauntlets"
const val CATHERINE_BOOTS_ID = "catherine:midnight-boots"

/** Victorian gunslinger displaced into Backrooms; encounter is not automatic recruitment. */
object CatherineCanon {
  const val NAME = "Catherine Valeria Ashcombe"
  const val CANON_REF = "CATHERINE-ASHCOMBE-CODEX-R01"
  const val AVATAR_REF = "avatars/Catherine_avatar.webp"
  const val SPRITE_REF = "Catherine.webp"
  const val ENCOUNTER_RATE_BP = 500 // exactly 5% of 10,000 eligible physical Explorer rolls

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to CATHERINE_VESPER_ID,
    "outfit" to CATHERINE_COAT_ID,
    "gauntlets" to CATHERINE_GLOVES_ID,
    "footwear" to CATHERINE_BOOTS_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = CATHERINE_ID, name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(CATHERINE_ID)
    )
    return base.copy(
      id = CATHERINE_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = CATHERINE_ID,
      equipmentId = CATHERINE_ID,
      // Never replace level, earned base stats, HP, continuity, or combat state.
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
        "originEra" to "1899 (late Victorian)",
        "origin" to "Grayhaven, fictional English coastal town",
        "combatStyle" to "Victorian occult revolver / precision / countershot",
        "signatureWeapon" to "Vesper Nocturne",
        "spriteRef" to SPRITE_REF,
        "canonRef" to CANON_REF,
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState = state.copy(
    characters = state.characters + (CATHERINE_ID to character(state.characters[CATHERINE_ID])),
    inventories = state.inventories + (CATHERINE_ID to
      (state.inventories[CATHERINE_ID] ?: InventoryState(CATHERINE_ID))),
    equipment = state.equipment + (CATHERINE_ID to EquipmentState(
      CATHERINE_ID, state.equipment[CATHERINE_ID]?.slots.orEmpty() + equipmentSlots))
  )
}
