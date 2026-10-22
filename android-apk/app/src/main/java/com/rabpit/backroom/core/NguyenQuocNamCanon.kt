package com.rabpit.backroom.core

const val NGUYEN_QUOC_NAM_ID = "nguyen_quoc_nam"
const val NAM_PISTOL_ID = "nam:corridor-pistol"
const val NAM_SHIRT_ID = "nam:lecturer-shirt"
const val NAM_WATCH_ID = "nam:promise-watch"
const val NAM_SHOES_ID = "nam:corridor-shoes"

/** Vietnamese medical lecturer lost in 2001. Family stays outside the Backrooms. */
object NguyenQuocNamCanon {
  const val NAME = "Nguyễn Quốc Nam"
  const val CANON_REF = "NGUYEN-QUOC-NAM-CODEX-R01"
  const val AVATAR_REF = "avatars/NguyenQuocNam_avatar.webp"
  const val SPRITE_REF = "NguyenQuocNam.webp"
  const val ENCOUNTER_RATE_BP = 500

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to NAM_PISTOL_ID,
    "outfit" to NAM_SHIRT_ID,
    "wrist" to NAM_WATCH_ID,
    "footwear" to NAM_SHOES_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = NGUYEN_QUOC_NAM_ID, name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(NGUYEN_QUOC_NAM_ID)
    )
    return base.copy(
      id = NGUYEN_QUOC_NAM_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = NGUYEN_QUOC_NAM_ID,
      equipmentId = NGUYEN_QUOC_NAM_ID,
      // Do not reset HP, XP, party continuity, earned stats or encounter history.
      metadata = base.metadata + mapOf(
        "npcType" to "follower", "species" to "human", "gender" to "male",
        "entity" to "false", "combatant" to "true",
        "joinEligible" to "true", "followsPlayer" to "true",
        "encounterPolicy" to "CORE_SPAWN",
        "encounterAction" to "PHYSICAL",
        "encounterLevels" to "ALL",
        "encounterChance" to "5%",
        "age" to "36",
        "birthYear" to "1965",
        "originYear" to "2001",
        "origin" to "Thành phố Hồ Chí Minh, Việt Nam",
        "profession" to "Giảng viên Trường Đại học Y Dược Thành phố Hồ Chí Minh",
        "wife" to "Nguyễn Hồng Lan",
        "daughter" to "Nguyễn Thùy Dương",
        "daughterAgeAtDisappearance" to "6",
        "familyWorld" to "OUTSIDE_BACKROOMS",
        "familyStatus" to "Không biết Quốc Nam sống hay chết",
        "combatStyle" to "precision defensive pistol / field first aid / threat assessment",
        "signatureWeapon" to "Hành Lang 01",
        "spriteRef" to SPRITE_REF,
        "canonRef" to CANON_REF,
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState = state.copy(
    characters = state.characters + (NGUYEN_QUOC_NAM_ID to character(state.characters[NGUYEN_QUOC_NAM_ID])),
    inventories = state.inventories + (NGUYEN_QUOC_NAM_ID to
      (state.inventories[NGUYEN_QUOC_NAM_ID] ?: InventoryState(NGUYEN_QUOC_NAM_ID))),
    equipment = state.equipment + (NGUYEN_QUOC_NAM_ID to EquipmentState(
      NGUYEN_QUOC_NAM_ID, state.equipment[NGUYEN_QUOC_NAM_ID]?.slots.orEmpty() + equipmentSlots))
  )
}
