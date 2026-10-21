package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Real-time training is independent of combat RNG, loot, world routes and the Explorer clock. */
data class DungeonRun(
  val slot: Int,
  val id: Long,
  val followerId: String,
  val environmentId: String,
  val startedAtMs: Long,
  val creditedMinutes: Int = 0
)

data class DungeonState(
  val serial: Long = 0,
  val clockMs: Long = 0,
  val runs: List<DungeonRun> = emptyList()
)

object DungeonRuntime {
  val durations = listOf(15, 30, 60, 120, 180)
  const val MINUTE_MS = 60_000L
  // Existing ordinary Level-0 Entity reward: 2 base EXP x 2 ordinary multiplier.
  const val EXP_PER_ENTITY = 4
  // Future book system: ONE combined roll, mutually exclusive book types.
  const val BOOK_DROP_BASIS_POINTS = 300

  fun isTraining(state: GameState, id: String): Boolean = state.dungeon.runs.any { it.followerId == id }

  fun eligible(state: GameState, actor: CharacterState): Boolean = actor.id != CAO_MINH_ID &&
    actor.metadata["npcType"] == "follower" && actor.presence == CharacterPresence.ACTIVE &&
    actor.vitalState.currentHp > 0 && actor.vitalState.condition != CharacterCondition.DEAD && !isTraining(state, actor.id) &&
    (actor.id in state.party.memberIds || actor.metadata["dungeon.unlocked"] == "true" ||
      actor.metadata["encountered"] == "true" && actor.metadata["followsPlayer"] == "true")

  /** One persisted cursor per run; reconnect/polling cannot award the same minute twice. */
  fun settle(input: GameState, nowMs: Long): GameState {
    require(nowMs >= 0) { "dungeon_invalid_clock" }
    if (input.dungeon.runs.isEmpty()) return input
    val now = maxOf(nowMs, input.dungeon.clockMs)
    var state = input
    val runs = input.dungeon.runs.sortedBy { it.slot }.map { run ->
      val elapsed = ((now - run.startedAtMs).coerceAtLeast(0) / MINUTE_MS)
        .coerceAtMost(durations[run.slot].toLong()).toInt()
      for (minute in run.creditedMinutes until elapsed) {
        // Per-minute seeds keep the exact stat rolls independent of polling frequency.
        state = CharacterProgressionCore.grantExp(state, run.followerId, EXP_PER_ENTITY,
          Random("dungeon:${run.id}:${run.startedAtMs}:${run.followerId}:$minute".hashCode())).state
      }
      run.copy(creditedMinutes = maxOf(run.creditedMinutes, elapsed))
    }
    return state.copy(dungeon = input.dungeon.copy(clockMs = now, runs = runs))
  }

  /** Core selects the image once per assignment; it never moves the campaign's location. */
  fun start(input: GameState, slot: Int, followerId: String, nowMs: Long): GameState {
    require(slot in durations.indices) { "dungeon_invalid_slot" }
    require(input.dungeon.runs.none { it.slot == slot }) { "dungeon_slot_occupied" }
    val actor = input.characters[followerId] ?: error("dungeon_unknown_follower")
    require(eligible(input, actor)) { "dungeon_follower_unavailable" }
    val state = settle(input, nowMs)
    val start = maxOf(nowMs, state.dungeon.clockMs)
    require(start <= Long.MAX_VALUE - durations[slot] * MINUTE_MS) { "dungeon_invalid_clock" }
    val serial = Math.addExact(state.dungeon.serial, 1L)
    val environments = Level0Environments.all
    val environment = environments[Random("$serial:$start:$followerId".hashCode()).nextInt(environments.size)]
    val run = DungeonRun(slot, serial, followerId, environment.id, start)
    val trained = state.characters.getValue(followerId).copy(presence = CharacterPresence.SEPARATED,
      metadata = actor.metadata + ("dungeon.unlocked" to "true"))
    return state.copy(
      characters = state.characters + (followerId to trained),
      party = state.party.copy(memberIds = state.party.memberIds - followerId),
      dungeon = DungeonState(serial, start, state.dungeon.runs + run)
    )
  }

  /** Early return keeps only full minutes; completed slots never auto-repeat. */
  fun release(input: GameState, slot: Int, expectedRunId: Long, nowMs: Long): GameState {
    val run = input.dungeon.runs.singleOrNull { it.slot == slot } ?: error("dungeon_slot_empty")
    require(run.id == expectedRunId) { "dungeon_stale_run" }
    val state = settle(input, nowMs)
    val actor = state.characters.getValue(run.followerId)
    val scene = state.world[MiniMapRuntime.STATE]?.let(::JSONObject)
    val roomMetadata = if (scene != null) mapOf("world.nodeId" to scene.getString("nodeId"),
      "world.roomId" to scene.getString("roomId")) else emptyMap()
    return state.copy(characters = state.characters + (actor.id to actor.copy(
      presence = CharacterPresence.ACTIVE, metadata = actor.metadata + roomMetadata)),
      dungeon = state.dungeon.copy(runs = state.dungeon.runs.filterNot { it.slot == slot }))
  }

  fun validate(state: GameState) {
    val data = state.dungeon
    require(data.serial >= 0 && data.clockMs >= 0 && data.runs.size <= 5) { "dungeon_invalid_state" }
    require(data.runs.map { it.slot }.distinct().size == data.runs.size &&
      data.runs.map { it.followerId }.distinct().size == data.runs.size &&
      data.runs.map { it.id }.distinct().size == data.runs.size) { "dungeon_duplicate_assignment" }
    data.runs.forEach { run ->
      require(run.slot in durations.indices && run.id in 1..data.serial &&
        run.startedAtMs in 0..data.clockMs &&
        run.startedAtMs <= Long.MAX_VALUE - durations[run.slot] * MINUTE_MS &&
        run.creditedMinutes in 0..durations[run.slot] &&
        run.creditedMinutes.toLong() <= (data.clockMs - run.startedAtMs) / MINUTE_MS &&
        Level0Environments.all.any { it.id == run.environmentId }) { "dungeon_invalid_run" }
      val actor = state.characters[run.followerId]
      require(actor != null && actor.id != CAO_MINH_ID && actor.metadata["npcType"] == "follower" &&
        actor.presence == CharacterPresence.SEPARATED && actor.vitalState.currentHp > 0 &&
        actor.vitalState.condition != CharacterCondition.DEAD &&
        actor.id !in state.party.memberIds) { "dungeon_invalid_follower" }
    }
  }

  fun encode(data: DungeonState): JSONObject = JSONObject()
    .put("serial", data.serial).put("clockMs", data.clockMs).put("runs", JSONArray().apply {
      data.runs.sortedBy { it.slot }.forEach { run -> put(JSONObject()
        .put("slot", run.slot).put("id", run.id).put("followerId", run.followerId)
        .put("environmentId", run.environmentId).put("startedAtMs", run.startedAtMs)
        .put("creditedMinutes", run.creditedMinutes)) }
    })

  fun decode(json: JSONObject): DungeonState {
    val runs = json.getJSONArray("runs")
    require(runs.length() <= 5 && (0 until runs.length()).all { runs.opt(it) is JSONObject }) {
      "dungeon_invalid_runs"
    }
    return DungeonState(json.getLong("serial"), json.getLong("clockMs"),
      MiniMapScenario.objects(runs).map { run ->
        DungeonRun(run.getInt("slot"), run.getLong("id"), run.getString("followerId"),
          run.getString("environmentId"), run.getLong("startedAtMs"), run.getInt("creditedMinutes"))
      })
  }

  fun view(state: GameState, nowMs: Long): JSONObject = JSONObject().apply {
    val now = maxOf(nowMs, state.dungeon.clockMs)
    put("serverNowMs", now)
    put("expPerEntity", EXP_PER_ENTITY)
    put("booksEnabled", false)
    put("bookDropBasisPoints", BOOK_DROP_BASIS_POINTS)
    put("followers", JSONArray().apply {
      state.characters.values.filter { eligible(state, it) }.sortedBy { it.name }.forEach { actor ->
        put(characterView(actor).put("inParty", actor.id in state.party.memberIds))
      }
    })
    put("slots", JSONArray().apply { durations.forEachIndexed { index, duration ->
      val run = state.dungeon.runs.singleOrNull { it.slot == index }
      put(JSONObject().put("slot", index).put("durationMinutes", duration).apply {
        if (run == null) put("status", "empty") else {
          put("runId", run.id).put("startedAtMs", run.startedAtMs)
          put("endsAtMs", run.startedAtMs + duration * MINUTE_MS)
          put("creditedMinutes", run.creditedMinutes).put("earnedExp", run.creditedMinutes * EXP_PER_ENTITY)
          put("status", if (run.creditedMinutes == duration) "complete" else "training")
          put("follower", characterView(state.characters.getValue(run.followerId)))
          put("environmentId", run.environmentId)
          put("environmentName", Level0Environments.all.first { it.id == run.environmentId }.name)
        }
      })
    } })
  }

  private fun characterView(actor: CharacterState) = JSONObject().put("id", actor.id).put("name", actor.name)
    .put("avatar", actor.avatarRef ?: "").put("level", actor.progression.level).put("exp", actor.progression.exp)
    .put("expRequired", if (actor.progression.level == CharacterProgressionCore.MAX_LEVEL) JSONObject.NULL
      else CharacterProgressionCore.expRequired(actor.progression.level))
}
