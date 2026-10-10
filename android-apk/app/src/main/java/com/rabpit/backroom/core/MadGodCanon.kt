package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_SET_ID = "madgod:set"

object MadGodCanon {
  const val CHEAT_CODE = "/madgod"
  const val SET_NAME = "Huyết Ma Kiếm · Ma Tôn"
  const val MULTIPLIER = 1
  const val SCALING_MODE = "GAMEPLAY_NORMALIZED"
  const val MAGNUM_RPM = 600
  const val AVATAR_ASSET = "avatars/MadGod.jpg"
  const val SNAPSHOT_OVERLAY_ASSET = "Kai_MadGod_snapshot_overlay.png"

  const val MAGNUM_DMG = 55
  const val ARMOR_DF = 0
  const val ARMOR_STR = 0
  const val ARMOR_AGI = 0
  const val ARMOR_HP = 0
  const val ARMOR_ENE = 0
  const val ARMOR_CRIT = 0

  data class Spawn(val state: GameState, val added: Boolean)

  fun cheat(x: String) = x.trim().equals(CHEAT_CODE, true)
  fun isSetId(x: String?) = x == MADGOD_SET_ID
  fun isId(x: String?) = isSetId(x)
  fun isItem(x: ItemStack?) = x != null && (isId(x.itemId) || x.metadata["madGod"].equals("true", true))
  fun slot(id: String, name: String) = if (isId(id) || name.contains(SET_NAME, true) || name.contains("MadGod Armor", true) || name.contains("MadGod Magnum", true)) "set" else null

  fun setItem() = EquipmentCatalog.stackFor(MADGOD_SET_ID).let {
    it.copy(metadata = it.metadata + mapOf("madGod" to "true", "permanentWhenEquipped" to "true"))
  }

  fun spawn(s: GameState): Spawn {
    val equipped = s.equipment[KAI_ID] ?: EquipmentState(KAI_ID)
    if (equipped.slots["weapon"] == MADGOD_SET_ID) return Spawn(s, false)
    val next = s.copy(
      equipment = s.equipment + (KAI_ID to equipped.copy(slots = equipped.slots + ("weapon" to MADGOD_SET_ID))),
      metadata = s.metadata + mapOf("madGod.spawned" to "true", "madGod.form" to "set", "madGod.multiplierMode" to SCALING_MODE)
    )
    return Spawn(next, true)
  }

  fun legacy(s: GameState) = JSONObject().apply {
    s.equipment[KAI_ID]?.slots.orEmpty().forEach { (slot, id) ->
      put(slot, JSONObject().put("id", id)
        .put("name", EquipmentCatalog.definition(id)?.name ?: id)
        .put("permanent", isId(id)).put("scalingMode", SCALING_MODE))
    }
  }
}
