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
      "equipment", "player", "coreSystem", "combat", "combatFeedback", "gameTime",
      "location", "title", "level", "flags", "story", "pendingEncounter")) out.remove(key)
    out.put("turn", turnIndex(state))
    out.put("saveVersion", CURRENT_SAVE_VERSION)
    out.put("location", state.world.getValue("location"))
    out.put("title", state.world.getValue("title"))
    out.put("level", JSONObject(state.world.getValue("levelJson")))
    val flags = JSONObject(state.world.getValue("flagsJson"))
    if (state.metadata["madGod.spawned"] == "true") {
      flags.put("madGod", JSONObject().put("spawned", true).put("spawnSource", "cheat"))
    }
    out.put("flags", flags)
    // A confirmed Lucia roll is an actual NPC event, independent of narrative
    // compliance. Show it once in this turn, and reconstruct it on same-turn reload.
    val luciaTurn = flags.optJSONObject("lucia")?.optInt("encounterTurn", -1) ?: -1
    if (luciaTurn >= 1 && turnIndex(state).toLong() == luciaTurn.toLong() + 1L) {
      val eventId = "NPC:LUCIA:$luciaTurn"
      val log = out.optJSONArray("log") ?: JSONArray().also { out.put("log", it) }
      if ((0 until log.length()).none { log.optJSONObject(it)?.optString("eventId") == eventId }) {
        log.put(JSONObject().put("role", "event").put("eventId", eventId)
          .put("text", "Đã gặp ${LuciaCanon.NAME}."))
      }
    }
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
    val caoMinh = state.characters.getValue(CAO_MINH_ID)
    out.put("player", JSONObject().put("name", caoMinh.name)
      .put("hp", caoMinh.vitalState.currentHp).put("condition", caoMinh.vitalState.condition.name))
    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      out.put("combat", combat)
    }
    CombatRuntime.pendingJson(state)?.let { out.put("pendingEncounter", it) }
    state.metadata["combat.encounterNarrative"]?.let { raw ->
      val entry = JSONObject(raw)
      val log = out.optJSONArray("log") ?: JSONArray().also { out.put("log", it) }
      if ((0 until log.length()).none {
          log.optJSONObject(it)?.optString("encounterId") == entry.getString("encounterId")
        }) log.put(entry)
    }
    out.put("story", JSONObject()
      .put("runToken", state.world["story.runToken"].orEmpty())
      .put("checkpointId", StoryCheckpointCore.current(state)?.id.orEmpty())
      .put("hasPlan", StoryMilestoneRuntime.hasPlan(state))
      .put("generatedMilestones", StoryMilestoneRuntime.generatedPlanCount(state))
      .put("waiting", StoryMilestoneRuntime.awaitingPlan(state))
      .put("stats", StoryMilestoneRuntime.statsJson(state))
      .apply { state.world["story.milestone.lastCompletion"]?.let { put("lastCompletion", JSONObject(it)) } })
    out.put("coreSystem", PokerDiceCore.statsJson(state, CAO_MINH_ID))
    return out
  }
}


