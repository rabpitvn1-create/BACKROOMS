package com.rabpit.backroom.core

import android.content.Context

interface SaveRepository {
  fun save(state: GameState)
  fun load(): GameState
  fun exists(): Boolean
  fun clear()
}

class SharedPreferencesSaveRepository(context: Context) : SaveRepository {
  private val preferences = context.getSharedPreferences("backroom_game_state_core", Context.MODE_PRIVATE)

  @Synchronized override fun save(state: GameState) {
    check(preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()) { "core_save_failed" }
  }

  @Synchronized override fun load(): GameState {
    val raw = preferences.getString(KEY_STATE, null) ?: return GameState.initial()
    return try {
      GameStateCodec.decode(raw)
    } catch (error: Exception) {
      // A failed decode is not a New Game. Keep the original bytes untouched
      // so startup can recover a validated current-version browser snapshot.
      throw IllegalStateException("core_save_decode_failed", error)
    }
  }

  override fun exists(): Boolean = preferences.contains(KEY_STATE)

  @Synchronized override fun clear() { preferences.edit().remove(KEY_STATE).commit() }

  companion object { private const val KEY_STATE = "game_state" }
}
