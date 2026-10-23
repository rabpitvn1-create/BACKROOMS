package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Core owns gameplay fields. WebView owns only the presentation log and UI preferences.
 * Client-supplied inventory, party, stats, combat, flags, equipment and turn are ignored.
 */
object CoreClientProjection {
  private const val TURN_INDEX = "core.viewTurn"

  fun turnIndex(state: GameState): Int =
    state.metadata[TURN_INDEX]?.toIntOrNull()?.coerceAtLeast(1)
      ?: ((state.turn.currentTurnId.removePrefix("TURN_").toIntOrNull() ?: 1) +
          if (state.turn.currentTurnId in state.turn.completedTurnIds) 1 else 0)

  fun advance(state: GameState): GameState =
    state.copy(metadata = state.metadata + (TURN_INDEX to (turnIndex(state) + 1).toString()))

  fun initialize(state: GameState): GameState {
    val initialWorld = mapOf(
      "location" to "Level 0 / The Lobby — khu phòng vàng ban đầu",
      "title" to "Level 0 – The Lobby",
      "levelJson" to JSONObject().put("number", 0).put("name", "The Lobby").toString(),
      "flagsJson" to JSONObject()
        .put("communication", JSONObject())
        .put("iris", JSONObject().put("exists", true).put("continuity", "UNKNOWN").put("reunionEligible", true))
        .put("syvial", JSONObject().put("exists", true).put("continuity", "UNKNOWN").put("reunionEligible", true))
        .toString()
    )
    val current = state.turn.currentTurnId.removePrefix("TURN_").toIntOrNull() ?: 1
    val inferredTurn = current + if (state.turn.currentTurnId in state.turn.completedTurnIds) 1 else 0
    return state.copy(
      world = initialWorld + state.world,
      metadata = if (TURN_INDEX in state.metadata) state.metadata
        else state.metadata + (TURN_INDEX to inferredTurn.toString())
    )
  }

  fun project(client: JSONObject, rawState: GameState): JSONObject {
    val state = initialize(rawState)
    val out = JSONObject(client.toString())
    for (key in arrayOf("turn", "saveVersion", "inventory", "party", "partyDetails",
      "equipment", "player", "gameOver", "coreSystem", "combat", "combatFeedback", "postCombatContinuation", "gameTime",
      "location", "title", "level", "flags", "story", "pendingEncounter", "encounteredCharacters", "scene", "dungeon")) out.remove(key)
    out.put("turn", turnIndex(state))
    out.put("saveVersion", CURRENT_SAVE_VERSION)
    out.put("gameOver", PartyCombatHealth.isDefeated(state))
    out.put("location", state.world.getValue("location"))
    out.put("title", state.world.getValue("title"))
    out.put("level", JSONObject(state.world.getValue("levelJson")))
    val flags = JSONObject(state.world.getValue("flagsJson"))
    if (state.metadata["madGod.spawned"] == "true") {
      flags.put("madGod", JSONObject().put("spawned", true).put("spawnSource", "cheat"))
    }
    out.put("flags", flags)
    out.put("gameTime", JSONObject()
      .put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      .put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      .apply { state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) } })
    out.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    out.put("equipment", MadGodCanon.equipmentProjection(state))
    out.put("inventory", JSONArray().apply {
      state.inventories[CAO_MINH_ID]?.items?.values.orEmpty().forEach { item ->
        put(JSONObject().put("id", item.itemId).put("name", item.name)
          .put("quantity", item.quantity).put("metadata", JSONObject(item.metadata)))
      }
    })
    out.put("party", JSONArray().apply {
      state.party.memberIds.filter { it != CAO_MINH_ID }.forEach { id ->
        state.characters[id]?.let { c ->
          put(JSONObject().put("id", c.id).put("name", c.name)
            .put("presence", c.presence.name).apply { c.avatarRef?.let { put("avatar", it) } })
        }
      }
    })
    out.put("encounteredCharacters", JSONArray().apply {
      state.characters.values.filter { it.metadata["encountered"] == "true" || it.metadata["dungeon.unlocked"] == "true" }.forEach { character ->
        put(JSONObject().put("id", character.id).put("name", character.name)
          .put("kind", character.metadata["encounterKind"])
          .apply { character.avatarRef?.let { put("avatar", it) } }
          .put("encounterId", character.metadata["encounterId"])
          .put("turn", character.metadata["encounterTurn"]?.toIntOrNull())
          .put("present", character.presence == CharacterPresence.ACTIVE &&
            (MiniMapRuntime.STATE !in state.world || !WorldGraphRuntime.active(MiniMapRuntime.progress(state)) ||
              WorldGraphRuntime.characterPresent(state, character)))
          .put("inParty", character.id in state.party.memberIds)
          .put("joinEligible", character.metadata["joinEligible"] == "true"))
      }
    })
    val caoMinh = state.characters.getValue(CAO_MINH_ID)
    out.put("player", JSONObject().put("name", caoMinh.name)
      .put("hp", caoMinh.vitalState.currentHp).put("condition", caoMinh.vitalState.condition.name))
    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      out.put("combat", combat)
    }
    if (MiniMapRuntime.PENDING !in state.world) CombatRuntime.pendingJson(state)?.let { out.put("pendingEncounter", it) }
    if (MiniMapRuntime.STATE in state.world) {
      val progress = MiniMapRuntime.progress(state)
      val currentMap = "${progress.getString("nodeId")}:${progress.getInt("index")}"
      val endingComplete = progress.optBoolean("endingComplete", false)
      // Presentation owns its log, but completed puzzle dialogue must not travel
      // into another mini-map. Preserve untagged global/canonical narration.
      out.optJSONArray("log")?.let { oldLog ->
        val kept = JSONArray()
        for (i in 0 until oldLog.length()) {
          val entry = oldLog.optJSONObject(i)
          if (entry != null && (!entry.has("sceneMap") ||
              (!endingComplete && entry.optString("sceneMap") == currentMap)))
            kept.put(entry)
        }
        out.put("log", kept)
      }
      out.put("scene", MiniMapRuntime.publicView(state))
      PlayerActionPresentation.project(out, state)
      if (!endingComplete) state.world[MiniMapRuntime.RECEIPT]?.let { receipt ->
        val r = JSONObject(receipt)
        val log = out.optJSONArray("log") ?: JSONArray().also { out.put("log", it) }
        val pendingLine = (0 until log.length()).firstOrNull { index ->
          log.optJSONObject(index)?.let { it.optString("sceneActionId") == r.getString("actionId") &&
            it.optBoolean("pendingSceneAction") } == true
        }
        if (pendingLine != null) {
          val entry = log.getJSONObject(pendingLine)
          entry.remove("pendingSceneAction"); entry.remove("actionStatus")
          val updated = JSONArray()
          for (i in 0 until log.length()) {
            updated.put(log.get(i))
            if (i == pendingLine && CombatRuntime.pending(state) == null && !r.optBoolean("specialDialogueTalk")) updated.put(JSONObject().put("role", "gm")
              .put("text", r.getString("reply")).put("sceneMap", currentMap)
              .apply { if (r.optBoolean("automaticContinuation")) put("sceneActionId", r.getString("actionId")) })
          }
          out.put("log", updated)
        }
        if ((0 until log.length()).none { index ->
          val entry = log.optJSONObject(index)
          entry?.optString("sceneActionId") == r.getString("actionId") ||
            entry?.optJSONArray("sceneBatchActions")?.let { ids ->
              (0 until ids.length()).any { ids.optString(it) == r.getString("actionId") }
            } == true
        }) {
          if (!r.optBoolean("automaticContinuation") && !r.optBoolean("specialDialogueTalk")) log.put(JSONObject().put("role", "player").put("text", r.optString("playerText", r.getString("label")))
            .put("sceneMap", currentMap).put("sceneActionId", r.getString("actionId")))
          if (CombatRuntime.pending(state) == null && !r.optBoolean("specialDialogueTalk")) log.put(JSONObject().put("role", "gm")
            .put("text", r.getString("reply")).put("sceneMap", currentMap)
              .apply { if (r.optBoolean("automaticContinuation")) put("sceneActionId", r.getString("actionId")) })
        }
      }
    }
    // A staged direct choice remains visible even if the writer/network fails.
    // Batch-owned pending commands are already represented by their utterance.
    state.world[MiniMapRuntime.PENDING]?.let { raw ->
      val pending = JSONObject(raw)
      if (!pending.has("playerActionId") && !pending.optBoolean("automaticContinuation")) {
        val log = out.optJSONArray("log") ?: JSONArray().also { out.put("log", it) }
        val id = pending.getString("actionId")
        if ((0 until log.length()).none { log.optJSONObject(it)?.optString("sceneActionId") == id }) {
          val scene = out.getJSONObject("scene")
          log.put(JSONObject().put("role", "player").put("text", pending.optString("playerText", pending.getString("label")))
            .put("sceneMap", scene.getString("nodeId") + ":" + scene.getInt("miniIndex"))
            .put("sceneActionId", id).put("pendingSceneAction", true)
            .put("actionStatus", "Core đang giữ bước chờ; GM chưa hoàn tất. Nhấn Retry để tiếp tục bước này."))
        }
      }
    }
    state.metadata["combat.encounterNarrative"]?.let { raw ->
      val entry = JSONObject(raw)
      val log = out.optJSONArray("log") ?: JSONArray().also { out.put("log", it) }
      if ((0 until log.length()).none {
          log.optJSONObject(it)?.optString("encounterId") == entry.getString("encounterId")
        }) log.put(entry)
    }
    out.put("coreSystem", PokerDiceCore.statsJson(state, CAO_MINH_ID))
    return out
  }
}


