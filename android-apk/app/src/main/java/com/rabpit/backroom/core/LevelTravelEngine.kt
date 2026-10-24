package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

const val EXIT_REQUIRED_STREAK = 3
const val EXIT_SUCCESS_CHANCE = 0.50
const val EXIT_FAILURE_CHANCE = 0.50

/** Immutable policy is loaded from APK assets, never from a GM or WebView proposal. */
class LevelTravelPolicy(root: JSONObject) {
  val nodes: Map<String, JSONObject>
  val startingIds: List<String>
  init {
    require(root.getInt("schemaVersion") == 1) { "level_policy_version" }
    val list = root.getJSONArray("nodes")
    val indexed = linkedMapOf<String, JSONObject>()
    for (i in 0 until list.length()) {
      val node = list.getJSONObject(i)
      val id = node.getString("id")
      require(id.isNotBlank() && id !in indexed) { "level_policy_duplicate_id" }
      indexed[id] = JSONObject(node.toString())
    }
    nodes = indexed
    startingIds = strings(root.getJSONArray("startingIds")).distinct()
    require(startingIds.isNotEmpty()) { "starting_levels_unconfigured" }
    startingIds.forEach { id ->
      val n = node(id)
      require(n.optBoolean("independentExit") && n.optJSONArray("hardEntryFrom") == null) { "starting_level_locked:$id" }
      validateEntry(n)
      destinations(id) // Reject a broken start pool before consuming RNG.
    }
  }
  fun node(id: String): JSONObject = nodes[id] ?: error("level_unconfigured:$id")
  fun destinations(id: String): List<String> {
    val source = node(id)
    check(source.optBoolean("independentExit")) { "exit_unconfigured:$id" }
    val configured = strings(source.getJSONArray("destinations")).distinct().filter { it != id }
    val hard = source.optJSONArray("hardDestinations")?.let(::strings)
    check(hard == null || configured.all { it in hard }) { "canon_exit_conflict:$id" }
    val result = configured.filter { target ->
      val n = node(target)
      validateEntry(n)
      val entryFrom = n.optJSONArray("hardEntryFrom")?.let(::strings)
      entryFrom == null || id in entryFrom
    }
    check(result.isNotEmpty()) { "exit_destinations_unconfigured:$id" }
    return result
  }
  private fun validateEntry(node: JSONObject) {
    check(node.getString("entryId").isNotBlank() && node.getString("entryLocation").isNotBlank() &&
      node.getString("background").startsWith("https://res.cloudinary.com/")) { "level_entry_unconfigured:${node.optString("id")}" }
  }
  companion object {
    fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }
  }
}

/** Core-owned travel transaction. Ordinary turns never call attempt() or consume its RNG. */
class LevelTravelEngine(
  private val policy: LevelTravelPolicy,
  private val random: () -> Double = SecureRandom()::nextDouble
) {
  data class Outcome(val state: GameState, val receipt: JSONObject)

  fun start(state: GameState, runId: String = UUID.randomUUID().toString()): GameState {
    check(!state.world.containsKey(KEY)) { "game_already_started" }
    val node = policy.node(choose(policy.startingIds))
    val travel = JSONObject().put("runId", runId).put("nodeId", node.getString("id"))
      .put("positionId", node.getString("entryId")).put("exitStreak", 0).put("revision", 0)
      .put("receipts", JSONObject())
    return arrival(state, travel, node, "new_game", null)
  }

  fun attempt(state: GameState, runId: String, turn: Int, action: String): Outcome {
    val travel = read(state)
    check(travel.getString("runId") == runId) { "stale_game_run" }
    val key = "TURN_$turn"
    val receipts = travel.getJSONObject("receipts")
    receipts.optJSONObject(key)?.let { old ->
      check(old.getString("action") == action) { "exit_action_conflict" }
      return Outcome(state, JSONObject(old.toString()))
    }
    check(turn == (state.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) { "stale_exit_turn" }
    check(isExitAction(action)) { "exit_intent_required" }
    check(key !in state.turn.completedTurnIds) { "exit_turn_completed" }
    check(CombatRuntime.active(state) == null) { "exit_combat_active" }
    val id = travel.getString("nodeId")
    // Resolve configuration/hard locks BEFORE the 50/50 draw; failures preserve the entire state.
    val destinations = policy.destinations(id)
    val streak = travel.getInt("exitStreak")
    check(streak in 0 until EXIT_REQUIRED_STREAK) { "exit_streak_invalid" }
    val success = draw() < EXIT_SUCCESS_CHANCE
    val nextStreak = if (success) streak + 1 else 0
    val completed = nextStreak == EXIT_REQUIRED_STREAK
    val destination = if (completed) policy.node(choose(destinations)) else null
    val receipt = JSONObject().put("action", action).put("turnId", key).put("eligible", true)
      .put("success", success).put("chancePercent", 50).put("streak", nextStreak)
      .put("requiredStreak", EXIT_REQUIRED_STREAK).put("completed", completed)
      .put("from", id).put("to", destination?.getString("id") ?: id)
    receipts.put(key, receipt)
    travel.put("exitStreak", if (completed) 0 else nextStreak)
      .put("revision", travel.getInt("revision") + 1)
    val changed = if (destination != null) {
      travel.put("nodeId", destination.getString("id")).put("positionId", destination.getString("entryId"))
      arrival(state, travel, destination, "level_exit", key)
    } else state.copy(world = state.world + (KEY to travel.toString()))
    // All of level, location, streak, receipt and continuity are returned in ONE immutable snapshot.
    return Outcome(changed.copy(world = changed.world + ("levelTravelUiTurn" to (turn + 1).toString()), turn = changed.turn.copy(
      currentTurnId = "TURN_${turn + 1}", completedTurnIds = changed.turn.completedTurnIds + key,
      pending = null
    )), receipt)
  }

  private fun arrival(state: GameState, travel: JSONObject, node: JSONObject, kind: String, turn: String?): GameState {
    val level = JSONObject().put("id", node.getString("id")).put("nodeId", node.getString("id"))
      .put("canonScope", node.getString("scope")).put("number", node.getString("nodeKey").substringBefore('/'))
      .put("name", node.getString("name"))
    val flags = JSONObject(state.world["flagsJson"] ?: "{}")
    flags.remove("currentLevel"); flags.remove("visualAreaKey"); flags.remove("visualEventKey")
    // A discovery in the old location cannot authorize or describe a new Level.
    flags.remove("exploration"); flags.remove("entityRegistry"); flags.remove("entitiesConfirmedLocal")
    flags.remove("entityEncounterKey"); flags.remove("worldItems"); flags.remove("lastRolls")
    listOf("confirmedExit", "transitionReady", "exitReady").forEach { flags.remove(it) }
    val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject()
    val events = continuity.optJSONArray("levelTravelEvents") ?: JSONArray()
    events.put(JSONObject().put("kind", kind).put("turnId", turn ?: "NEW_GAME")
      .put("nodeId", node.getString("id")).put("positionId", node.getString("entryId")))
    continuity.put("levelTravelEvents", events); flags.put("storyContinuity", continuity)
    return state.copy(world = state.world + mapOf(KEY to travel.toString(),
      "levelJson" to level.toString(), "location" to node.getString("entryLocation"),
      "title" to node.getString("name"), "levelBackground" to node.getString("background"),
      "flagsJson" to flags.toString(), "levelTravelUiTurn" to (turn?.substringAfter("TURN_")?.toIntOrNull()?.plus(1) ?: 1).toString()))
  }
  private fun choose(ids: List<String>): String = ids[(draw() * ids.size).toInt().coerceAtMost(ids.lastIndex)]
  private fun draw(): Double = random().also { check(it >= 0.0 && it < 1.0) { "invalid_random_source" } }

  companion object {
    const val KEY = "levelTravel"
    fun read(state: GameState): JSONObject = JSONObject(state.world[KEY] ?: error("level_game_not_initialized"))
    // Deliberately a small complete-action grammar: searching/talking about exits is not attempting one.
    fun isExitAction(action: String): Boolean {
      val a = action.trim().lowercase(Locale.ROOT).trimEnd('.', '!').replace(Regex("\\s+"), " ")
      return Regex("(?:(?:tôi|cao minh) )?(?:thử thoát(?: khỏi)?(?: level(?: hiện tại)?)?|rời khỏi level(?: hiện tại)?|đi qua lối thoát|attempt (?:to )?exit|exit level)").matches(a)
    }
    fun canMove(action: String, location: String): Boolean {
      val a = action.trim().lowercase(Locale.ROOT)
      if (isExitAction(action) || Regex("(?i)\\b(?:level|tầng)\\s*[0-9ε]").containsMatchIn(location)) return false
      return Regex("^(?:(?:tôi|cao minh) )?(?:đi|bước|chạy|leo|bò|tiến|lùi|khám phá)\\b").containsMatchIn(a)
    }
    private const val UNKNOWN_LOCATION = "Chưa xác định"
    private const val UNKNOWN_TITLE = "MAIN_BACKROOMS — Cao Minh"
    private const val IDENTIFIED_NODE_KEY = "knowledge.identifiedLevelNodeId"

    /** Only a Core-verified identification of this exact node can reveal its canonical name. */
    private fun presentLocation(state: GameState, output: JSONObject): JSONObject {
      val identifiedNode = state.metadata[IDENTIFIED_NODE_KEY]
      val currentNode = read(state).optString("nodeId")
      val confirmed = currentNode.isNotBlank() && identifiedNode == currentNode
      return output.put("displayLocation",
        if (confirmed) state.world["location"] ?: UNKNOWN_LOCATION else UNKNOWN_LOCATION)
        .put("displayTitle",
          if (confirmed) state.world["title"] ?: UNKNOWN_TITLE else UNKNOWN_TITLE)
    }

    fun protectCandidate(state: GameState, candidate: JSONObject, action: String): JSONObject {
      if (KEY !in state.world) return candidate
      val output = presentLocation(state, JSONObject(candidate.toString()))
      output.put("level", JSONObject(state.world.getValue("levelJson")))
      output.put("title", state.world.getValue("title"))
      if (!canMove(action, output.optString("location"))) output.put("location", state.world.getValue("location"))
      output.put(KEY, read(state)).put("levelBackground", state.world.getValue("levelBackground"))
      val flags = output.optJSONObject("flags") ?: JSONObject()
      listOf("currentLevel", "visualAreaKey", "visualEventKey", "confirmedExit", "transitionReady", "exitReady").forEach { flags.remove(it) }
      val old = JSONObject(state.world["flagsJson"] ?: "{}")
      val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject()
      old.optJSONObject("storyContinuity")?.optJSONArray("levelTravelEvents")?.let { continuity.put("levelTravelEvents", it) }
      flags.put("storyContinuity", continuity); output.put("flags", flags)
      return output
    }
    fun project(state: GameState, legacy: JSONObject): JSONObject {
      val output = JSONObject(legacy.toString())
      if (KEY !in state.world) return output
      presentLocation(state, output)
      output.put(KEY, read(state)).put("level", JSONObject(state.world.getValue("levelJson")))
        .put("location", state.world.getValue("location")).put("title", state.world.getValue("title"))
        .put("levelBackground", state.world.getValue("levelBackground"))
      val flags = output.optJSONObject("flags") ?: JSONObject()
      val old = JSONObject(state.world["flagsJson"] ?: "{}")
      val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject()
      old.optJSONObject("storyContinuity")?.optJSONArray("levelTravelEvents")?.let { continuity.put("levelTravelEvents", it) }
      flags.put("storyContinuity", continuity); output.put("flags", flags)
      return output
    }
  }
}
