package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONObject

interface SaveRepository {
  fun save(state: GameState)
  fun load(): GameState
  fun exists(): Boolean
  fun clear()
  fun saveCheckpoint(state: GameState, presentation: JSONObject)
  fun loadCheckpoint(): GameCheckpoint?
}

class SharedPreferencesSaveRepository(context: Context) : SaveRepository {
  private val preferences = context.getSharedPreferences("backroom_game_state_core", Context.MODE_PRIVATE)

  @Synchronized override fun save(state: GameState) {
    check(preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()) { "core_save_failed" }
  }

  @Synchronized override fun load(): GameState {
    val raw = preferences.getString(KEY_STATE, null) ?: return GameState.initial()
    return runCatching { GameStateCodec.decode(raw) }
      .getOrElse { GameState.initial().copy(metadata = mapOf("loadError" to (it.message ?: "invalid_save"))) }
  }

  override fun exists(): Boolean = preferences.contains(KEY_STATE)

  @Synchronized override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
    val encoded = GameCheckpointCodec.encode(state, presentation)
    check(preferences.edit().putString(KEY_CHECKPOINT, encoded).commit()) { "checkpoint_save_failed" }
  }

  @Synchronized override fun loadCheckpoint(): GameCheckpoint? =
    preferences.getString(KEY_CHECKPOINT, null)?.let(GameCheckpointCodec::decode)

  @Synchronized override fun clear() {
    check(preferences.edit().remove(KEY_STATE).remove(KEY_CHECKPOINT).commit()) { "core_clear_failed" }
  }

  companion object {
    private const val KEY_STATE = "game_state"
    private const val KEY_CHECKPOINT = "manual_checkpoint_v5"
  }
}
