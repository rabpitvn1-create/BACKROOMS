package com.rabpit.backroom.core

import android.content.Context

interface SaveRepository {
  fun save(state: GameState)
  /** Explicit New Game only. Implementations may atomically archive prior bytes. */
  fun saveFresh(state: GameState) = save(state)
  fun load(): GameState
  fun exists(): Boolean
  fun clear()
}

class SharedPreferencesSaveRepository(context: Context) : SaveRepository {
  private val preferences = context.getSharedPreferences("backroom_game_state_core", Context.MODE_PRIVATE)

  @Synchronized override fun save(state: GameState) {
    check(preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()) { "core_save_failed" }
  }

  @Synchronized override fun saveFresh(state: GameState) {
    val encoded = GameStateCodec.encode(state)
    val existing = preferences.getString(KEY_STATE, null)
    val editor = preferences.edit()
    // Retain the very first replaced Core bytes, including unsupported saves.
    // Backup and fresh state commit together; a failed commit changes neither.
    if (existing != null && !preferences.contains(KEY_PRE_NEW_GAME_BACKUP))
      editor.putString(KEY_PRE_NEW_GAME_BACKUP, existing)
    check(editor.putString(KEY_STATE, encoded).commit()) { "core_save_failed" }
  }

  @Synchronized override fun load(): GameState {
    val raw = preferences.getString(KEY_STATE, null) ?: return GameState.initial()
    return try {
      val saved = org.json.JSONObject(raw)
      require(saved.optInt("saveVersion", -1) == CURRENT_SAVE_VERSION) {
        "save_version_unsupported"
      }
      GameStateCodec.decode(saved)
    } catch (error: Exception) {
      // Never silently convert an obsolete version or replace unreadable bytes.
      if (error.message == "save_version_unsupported") throw error
      throw IllegalStateException("core_save_decode_failed", error)
    }
  }

  override fun exists(): Boolean = preferences.contains(KEY_STATE)

  @Synchronized override fun clear() { preferences.edit().remove(KEY_STATE).commit() }

  companion object {
    private const val KEY_STATE = "game_state"
    private const val KEY_PRE_NEW_GAME_BACKUP = "game_state_before_fresh"
  }
}
