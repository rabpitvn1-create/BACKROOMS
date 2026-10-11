package com.rabpit.backroom.core

/**
 * R06 baseline; an encounter is not a guaranteed reunion or recognition.
 * The character remains in Core but outside Party until the Level 4+ roll succeeds.
 */
const val LUC_TRAM_ID = "luc_tram"
const val LUC_TRAM_TICH_QUANG_ID = "luc-tram:tich-quang"
const val LUC_TRAM_KIEM_KHAI_ID = "luc-tram:thien-co-bach-kim-kiem-khai"

object LucTramCanon {
  const val NAME = "Lục Trầm"
  const val AVATAR_REF = "avatars/luctram_avatar.png"
  const val ENCOUNTER_CHANCE = "0.25%"
  const val FIRST_ELIGIBLE_LEVEL = 4

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUC_TRAM_ID,
      name = NAME,
      physiology = PhysiologyState.freshRunBaseline()
    )
    // Never overwrite stateful injuries, health, equipment, progression or continuity.
    return base.copy(
      id = LUC_TRAM_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = LUC_TRAM_ID,
      equipmentId = LUC_TRAM_ID,
      metadata = base.metadata + mapOf(
        "species" to "human",
        "gender" to "female",
        "npcType" to "follower",
        "combatant" to "true",
        "joinEligible" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to "4-6",
        "encounterAction" to "PHYSICAL",
        "followsPlayer" to "true",
        "role" to "Thiên Kiếm Môn / Chính Đạo Kiếm Tu",
        "signatureWeapon" to "Tịch Quang",
        "armor" to "Thiên Cơ Bạch Kim Kiếm Khải",
        "canonRef" to "LUC-TRAM-THIEN-KIEM-CODEX-R05+LUC-PRO-R06",
        "postRevivalMemory" to "CAO_MINH_FORGOTTEN",
        "transformation" to "DEMONIC_TRANSFORMATION_BEGUN",
        "equipmentAfterRevival" to "UNCONFIRMED"
      )
    )
  }

  /** Registration does not spawn or join Lục Trầm. Never seed uncertain post-revival gear. */
  fun ensure(state: GameState): GameState {
    return state.copy(
      characters = state.characters + (LUC_TRAM_ID to character(state.characters[LUC_TRAM_ID])),
      inventories = if (LUC_TRAM_ID in state.inventories) state.inventories
        else state.inventories + (LUC_TRAM_ID to InventoryState(LUC_TRAM_ID)),
      equipment = if (LUC_TRAM_ID in state.equipment) state.equipment
        else state.equipment + (LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID))
    )
  }

  // R06 does not guarantee that the sword survived the revival.
  fun hasConfirmedSword(state: GameState): Boolean =
    state.equipment[LUC_TRAM_ID]?.slots?.get(EquipmentSlot.WEAPON.key) == LUC_TRAM_TICH_QUANG_ID

  fun domainKey(encounterId: String, waveIndex: Int) = "$encounterId:wave:$waveIndex"

  fun canEstablishDomain(state: GameState, opening: Int, waveId: String): Boolean =
    hasConfirmedSword(state) && opening >= 2 &&
      state.metadata["combat.lucTramDomainWave"] != waveId

  fun currentLevel(state: GameState): Int {
    val level = runCatching { org.json.JSONObject(state.world["levelJson"] ?: "{}") }.getOrNull()
      ?: return 0
    val scene = org.json.JSONObject().put("level", level)
    return LevelRouteRegistry.node(LevelRouteRegistry.currentId(scene))?.parent ?: 0
  }

  fun eligible(state: GameState): Boolean =
    currentLevel(state) >= FIRST_ELIGIBLE_LEVEL &&
      LUC_TRAM_ID !in state.party.memberIds &&
      state.party.memberIds.size <= state.party.maxMembers

  /** No roleplay/candidate boolean alone can manufacture the reunion. */
  fun validEncounterRoll(state: GameState, rolls: org.json.JSONObject?): Boolean {
    if (!eligible(state) || rolls == null || rolls.optBoolean("meta", true) ||
        rolls.optInt("turn", -1) != CoreClientProjection.turnIndex(state)) return false
    val result = rolls.optJSONObject("lucTramEncounter") ?: return false
    val value = result.optInt("roll", -1)
    return result.optBoolean("eligible", false) &&
      result.optBoolean("success", false) &&
      result.optInt("max", -1) == 10000 &&
      result.optInt("threshold", -1) == 25 &&
      value in 1..25
  }
}
