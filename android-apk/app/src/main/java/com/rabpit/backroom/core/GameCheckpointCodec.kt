package com.rabpit.backroom.core

import org.json.JSONObject

/** One current-version manual slot: Core gameplay and its UI presentation travel together. */
data class GameCheckpoint(val core: GameState, val presentation: JSONObject)

object GameCheckpointCodec {
  fun encode(core: GameState, presentation: JSONObject): String {
    require(core.saveVersion == CURRENT_SAVE_VERSION) { "checkpoint_schema_mismatch" }
    require(core.turn.pending == null && ActionRuntime.activeSession(core) == null) { "turn_in_progress" }
    val view = CoreClientProjection.project(presentation, core)
    return JSONObject()
      .put("saveVersion", CURRENT_SAVE_VERSION)
      .put("core", JSONObject(GameStateCodec.encode(core)))
      .put("presentation", view)
      .toString()
  }

  fun decode(raw: String): GameCheckpoint {
    val root = JSONObject(raw)
    require(root.getInt("saveVersion") == CURRENT_SAVE_VERSION) { "checkpoint_schema_mismatch" }
    val core = GameStateCodec.decode(root.getJSONObject("core"))
    require(core.turn.pending == null && ActionRuntime.activeSession(core) == null) { "checkpoint_turn_in_progress" }
    val view = root.getJSONObject("presentation")
    require(view.optInt("turn", -1) == CoreClientProjection.turnIndex(core)) { "checkpoint_turn_mismatch" }
    return GameCheckpoint(core, CoreClientProjection.project(view, core))
  }
}
