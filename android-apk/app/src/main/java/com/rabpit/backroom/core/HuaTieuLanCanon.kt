package com.rabpit.backroom.core

const val HUA_TIEU_LAN_ID = "hua_tieu_lan"
const val TIEU_LAN_RIFLE_ID = "tieu-lan:truong-xuan"
const val TIEU_LAN_AO_DAI_ID = "tieu-lan:lam-cuc-ao-dai"
const val TIEU_LAN_JADE_ID = "tieu-lan:jade-bangle"
const val TIEU_LAN_SHOES_ID = "tieu-lan:white-heels"

/**
 * Alternate-history daughter of historical Hui Bon Hoa, lost on her 18th birthday in 1895.
 * Historical sources report three sons and no daughters; this character is FICTION.
 */
object HuaTieuLanCanon {
  const val NAME = "Hứa Tiểu Lan"
  const val CANON_REF = "HUA-TIEU-LAN-CODEX-R01"
  const val AVATAR_REF = "avatars/HuaTieuLan_avatar.webp"
  const val SPRITE_REF = "HuaTieuLan.webp"
  const val ENCOUNTER_RATE_BP = 500

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to TIEU_LAN_RIFLE_ID,
    "outfit" to TIEU_LAN_AO_DAI_ID,
    "wrist" to TIEU_LAN_JADE_ID,
    "footwear" to TIEU_LAN_SHOES_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = HUA_TIEU_LAN_ID, name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(HUA_TIEU_LAN_ID)
    )
    return base.copy(
      id = HUA_TIEU_LAN_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = HUA_TIEU_LAN_ID,
      equipmentId = HUA_TIEU_LAN_ID,
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
        "age" to "18",
        "birthYear" to "1877",
        "originYear" to "1895",
        "disappearance" to "18th birthday, 1895",
        "origin" to "Sài Gòn – Chợ Lớn, Nam Kỳ",
        "father" to "Hứa Bổn Hòa (alternate-history fiction)",
        "historyScope" to "ALTERNATE_HISTORY_FICTION",
        "combatStyle" to "nineteenth-century rifle / careful aim / cover / defensive countershot",
        "signatureWeapon" to "Trường Xuân",
        "spriteRef" to SPRITE_REF,
        "canonRef" to CANON_REF,
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState = state.copy(
    characters = state.characters + (HUA_TIEU_LAN_ID to character(state.characters[HUA_TIEU_LAN_ID])),
    inventories = state.inventories + (HUA_TIEU_LAN_ID to
      (state.inventories[HUA_TIEU_LAN_ID] ?: InventoryState(HUA_TIEU_LAN_ID))),
    equipment = state.equipment + (HUA_TIEU_LAN_ID to EquipmentState(
      HUA_TIEU_LAN_ID, state.equipment[HUA_TIEU_LAN_ID]?.slots.orEmpty() + equipmentSlots))
  )
}
