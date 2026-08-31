package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONObject

interface SaveRepository {
  fun save(state: GameState)
  fun load(): GameState
  fun exists(): Boolean
  fun clear()
}

object SaveCompatibility {
  const val MIGRATABLE_SAVE_VERSION = 4

  fun normalize(raw: String): String? = runCatching {
    val root = JSONObject(raw)
    when (root.optInt("saveVersion", -1)) {
      CURRENT_SAVE_VERSION -> root.toString()
      MIGRATABLE_SAVE_VERSION -> {
        root.put("saveVersion", CURRENT_SAVE_VERSION)
        root.remove("levelInstance")
        val metadata = root.optJSONObject("metadata") ?: JSONObject()
        metadata.put("migratedFromVersion", MIGRATABLE_SAVE_VERSION.toString())
        root.put("metadata", metadata)
        root.toString()
      }
      else -> null
    }
  }.getOrNull()
}

class SharedPreferencesSaveRepository(context: Context) : SaveRepository {
  private val preferences = context.getSharedPreferences("backroom_game_state_core", Context.MODE_PRIVATE)

  @Synchronized override fun save(state: GameState) {
    preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()
  }

  @Synchronized override fun load(): GameState {
    val raw = preferences.getString(KEY_STATE, null) ?: return GameState.initial()
    val normalizedRaw = SaveCompatibility.normalize(raw)
    if (normalizedRaw == null) {
      clear()
      return GameState.initial()
    }
    val decoded = runCatching { GameStateCodec.decode(normalizedRaw) }.getOrElse {
      clear()
      return GameState.initial()
    }
    if (normalizedRaw != raw) save(decoded)
    return decoded
  }

  override fun exists(): Boolean = preferences.contains(KEY_STATE)

  @Synchronized override fun clear() { preferences.edit().remove(KEY_STATE).commit() }

  companion object { private const val KEY_STATE = "game_state" }
}
