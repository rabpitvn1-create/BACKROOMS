package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONObject

interface SaveRepository {
  fun save(state: GameState)
  fun load(): GameState
  fun exists(): Boolean
  fun clear()
}

class SharedPreferencesSaveRepository(context: Context) : SaveRepository {
  private val preferences = context.getSharedPreferences("backroom_game_state_core", Context.MODE_PRIVATE)

  @Synchronized override fun save(state: GameState) {
    preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()
  }

  @Synchronized override fun load(): GameState {
    val raw = preferences.getString(KEY_STATE, null) ?: return GameState.initial()
    val version = runCatching { JSONObject(raw).optInt("saveVersion", -1) }.getOrDefault(-1)
    if (version != CURRENT_SAVE_VERSION && version != MIGRATABLE_SAVE_VERSION) {
      clear()
      return GameState.initial()
    }
    val decoded = runCatching { GameStateCodec.decode(raw) }.getOrElse {
      clear()
      return GameState.initial()
    }
    if (version != CURRENT_SAVE_VERSION) save(decoded)
    return decoded
  }

  override fun exists(): Boolean = preferences.contains(KEY_STATE)

  @Synchronized override fun clear() { preferences.edit().remove(KEY_STATE).commit() }

  companion object {
    private const val KEY_STATE = "game_state"
    private const val MIGRATABLE_SAVE_VERSION = 4
  }
}
