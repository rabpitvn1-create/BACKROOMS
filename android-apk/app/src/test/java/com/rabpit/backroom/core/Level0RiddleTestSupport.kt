package com.rabpit.backroom.core

import org.json.JSONObject

/** Graph access is TEST ONLY: the production player sees only discovered corridors. */
internal object Level0RiddleTestSupport {
  fun progress(state: GameState) = MiniMapRuntime.progress(state)
  fun choices(state: GameState) = MiniMapScenario.objects(MiniMapRuntime.actions(state))

  fun take(state: GameState, target: String): GameState {
    val option = choices(state).firstOrNull { it.getString("targetId") == target }
      ?: error("unavailable_action:$target")
    val command = JSONObject(option.toString()).apply { remove("label") }
    val staged = MiniMapRuntime.stage(state, command)
    return state.copy(world = state.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }

  fun roomId(state: GameState, environmentId: String): String =
    MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
      .first { it.getString("environmentId") == environmentId }.getString("id")

  fun walk(state: GameState, destination: String): GameState {
    val rooms = MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
    val neighbors = rooms.associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val origin = progress(state).getString("roomId")
    val paths = mutableMapOf(origin to emptyList<String>())
    val queue = java.util.ArrayDeque<String>(); queue.add(origin)
    while (destination !in paths && queue.isNotEmpty()) {
      val from = queue.removeFirst()
      neighbors.getValue(from).forEach { to ->
        if (to !in paths) {
          paths[to] = paths.getValue(from) + to
          queue.add(to)
        }
      }
    }
    require(destination in paths) { "test_graph_unreachable" }
    var next = state
    for (id in paths.getValue(destination)) next = take(next, "room:$id")
    return next
  }

  fun visit(state: GameState, environmentId: String) = walk(state, roomId(state, environmentId))

  fun solveLaw(input: GameState, law: WorldEvidenceLaw): GameState {
    var state = input
    for (probe in law.probes) {
      state = visit(state, probe.environmentId)
      state = take(state, "probe:${probe.method}")
    }
    for ((first, second) in law.comparisonPairs) {
      state = visit(state, first)
      state = take(state, "study")
      val firstRoomId = progress(state).getString("roomId")
      state = visit(state, second)
      state = take(state, "study")
      state = take(state, "compare:$firstRoomId")
    }
    for (environment in law.physicalAnchors) {
      state = visit(state, environment)
      state = take(state, "trace")
    }
    state = visit(state, law.verifyEnvironmentId)
    for (step in law.inferenceSteps) {
      state = take(state, "deduce:${step.id}:${step.answerId}")
    }
    state = take(state, "verify")
    require(progress(state).getJSONObject("ruleFlags").optBoolean(law.id)) { "test_law_unsolved:${law.id}" }
    return state
  }

  fun solveAll(input: GameState): GameState {
    var state = input
    for (law in Level0WorldDefinition.evidenceLaws) state = solveLaw(state, law)
    return state
  }

  fun visitEveryRoom(input: GameState): GameState {
    var state = input
    val rooms = MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
    for (room in rooms) state = walk(state, room.getString("id"))
    return state
  }
}
