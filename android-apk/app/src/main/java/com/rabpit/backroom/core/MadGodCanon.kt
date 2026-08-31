package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_SET_ID = "retired:madgod-disabled"

object MadGodCanon {
  const val MADGOD_SET_ID = "retired:madgod-disabled"
  const val SPECIAL_CHEAT = "DISABLED"
  const val ARMOR_ID = "retired:madgod-armor-disabled"
  const val MAGNUM_ID = "retired:madgod-magnum-disabled"
  const val RING_ID = "retired:madgod-ring-disabled"
  const val CHEAT_CODE = ""
  const val SET_NAME = ""
  const val SCALING_MODE = "DISABLED"
  const val MULTIPLIER = 1
  const val MAGNUM_RPM = 0
  const val MAGNUM_DMG = 0
  const val ARMOR_DF = 0
  const val ARMOR_STR = 0
  const val ARMOR_AGI = 0
  const val ARMOR_HP = 0
  const val ARMOR_ENE = 0
  const val ARMOR_CRIT = 0
  const val AVATAR_ASSET = ""
  const val SNAPSHOT_OVERLAY_ASSET = ""

  data class Spawn(val state: GameState, val added: Boolean)

  fun cheat(x: String): Boolean = false
  fun isSetId(x: String?): Boolean = false
  fun isLegacyId(x: String?): Boolean = false
  fun isId(x: String?): Boolean = false
  fun isItem(x: ItemStack?): Boolean = false
  fun slot(id: String, name: String): String? = null
  fun setItem(): ItemStack = ItemStack(MADGOD_SET_ID, "Retired item", 1, metadata = mapOf("retired" to "true"))
  fun weapon(): ItemStack = setItem()
  fun armor(): ItemStack = setItem()
  fun spawn(s: GameState): Spawn = Spawn(s, false)
  fun legacy(s: GameState): JSONObject = JSONObject()
}
