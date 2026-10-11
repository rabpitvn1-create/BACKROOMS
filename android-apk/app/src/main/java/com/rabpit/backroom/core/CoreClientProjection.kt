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
      "location" to "Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip",
      "title" to "Level 0 – The Lobby",
      "levelJson" to JSONObject().put("number", 0).put("name", "The Lobby").toString(),
      "flagsJson" to JSONObject()
        .put("communication", JSONObject().put("blackBlood", "OFFLINE").put("iris", "OFFLINE").put("syvial", "OFFLINE"))
        .put("iris", JSONObject().put("exists", true).put("continuity", "SEPARATED").put("reunionEligible", true))
        .put("syvial", JSONObject().put("exists", true).put("continuity", "SEPARATED").put("reunionEligible", true))
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
      "location", "title", "level", "flags")) out.remove(key)
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
    out.put("gameTime", JSONObject()
      .put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      .put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      .apply { state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) } })
    out.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    out.put("equipment", MadGodCanon.equipmentProjection(state))
    out.put("inventory", JSONArray().apply {
      state.inventories[KAI_ID]?.items?.values.orEmpty().forEach { item ->
        put(JSONObject().put("id", item.itemId).put("name", item.name)
          .put("quantity", item.quantity).put("metadata", JSONObject(item.metadata)))
      }
    })
    out.put("party", JSONArray().apply {
      state.party.memberIds.filter { it != KAI_ID }.forEach { id ->
        state.characters[id]?.let { c ->
          put(JSONObject().put("id", c.id).put("name", c.name)
            .put("presence", c.presence.name).apply { c.avatarRef?.let { put("avatar", it) } })
        }
      }
    })
    val kai = state.characters.getValue(KAI_ID)
    out.put("player", JSONObject().put("name", kai.name)
      .put("hp", kai.vitalState.currentHp).put("condition", kai.vitalState.condition.name))
    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      out.put("combat", combat)
    }
    out.put("coreSystem", PokerDiceCore.statsJson(state, KAI_ID))
    return out
  }
}
