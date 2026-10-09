package com.rabpit.backroom.core.companion
import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject

/** Pure native WAIT staging; invoked inside capture OR replay's combat-draw scope. */
internal object CompanionWaitStage {
  fun apply(original: GameState, bound: CompanionWaitAuthorizer.Bound,
            route: CompanionRollTape.Route, rollsJson: String, streak: Int): GameState {
    require(TimeCostPolicy.estimateMinutes("wait") == 30) { "wait_time_policy_changed" }
    val sessionId = "wait-" + bound.turnId
    val started = ActionRuntime.start(original, sessionId, bound.turnId, KAI_ID,
      ActionKind.EXECUTE, "wait", plannedMinutes = 30)
    check(started.applied) { started.error ?: "wait_session_start" }
    val progressed = ActionRuntime.advance(started.state, sessionId, "resolve", 30)
    check(progressed.applied && !progressed.duplicate) { progressed.error ?: "wait_time_rejected" }
    require(progressed.state.time.elapsedSubjectiveMinutes - original.time.elapsedSubjectiveMinutes == 30L) { "wait_time_incomplete" }

    val target = if (route.completed) FeaturedJourneyRoutes.next(bound.sourceStop)!! else null
    val flags = JSONObject(original.world.getValue("flagsJson"))
    val exploration = flags.getJSONObject("exploration")
    exploration.put("exitStreak", streak).put("exitStreakNode", route.targetStop)
    val priorLevelTurns = exploration.optInt("levelTurns", 0).coerceAtLeast(0)
    require(priorLevelTurns < Int.MAX_VALUE) { "wait_level_turn_overflow" }
    exploration.put("levelTurns", if (route.sourceLevel.coerceIn(0, 6) == route.targetLevel.coerceIn(0, 6)) priorLevelTurns + 1 else 0)
    val title = target?.let {
      if (it.targetIsNamedArea) "Level ${it.targetLevelNumber} / ${it.targetTitle}"
      else "Level ${it.targetStopKey.removePrefix("level-")} - ${it.targetTitle}"
    }
    val level = if (target == null) original.world.getValue("levelJson") else JSONObject()
      .put("number", target.targetLevelNumber).put("nodeId", target.targetNodeId)
      .put("stopKey", target.targetStopKey).put("name", title).toString()
    val command = ValidatedLegacyStateCommand(commandId = "${bound.turnId}:NATIVE:WAIT", turnId = bound.turnId,
      source = CommandSource.SYSTEM, location = if (target == null) null else "", title = title,
      levelJson = level, flagsJson = flags.toString(), validatedByGameEngine = true)
    // Time was checkpointed by ActionRuntime. Do not append a second TimeAdvanceCommand.
    // The coordinator sees only the native verb, so player prose cannot trigger sleep.
    val staged = CompanionCoreStage.stage(progressed.state, bound.turnId, "wait", listOf(command))
    check(staged.error == null && staged.candidate != null) { staged.error ?: "wait_stage_failed" }
    var state = GameStateCodec.decode(staged.candidate.afterSnapshot)
    state = state.copy(world = state.world + mapOf("journeyStopKey" to route.targetStop,
      "worldNodeId" to FeaturedJourneyRoutes.nodeIdAt(route.targetStop)!!))
    val completed = ActionRuntime.complete(state, sessionId)
    check(completed.applied) { completed.error ?: "wait_session_complete" }
    state = completed.state
    val rolls = JSONObject(rollsJson)
    if (!route.completed && rolls.getJSONObject("entityEncounter").getBoolean("success")) {
      val key = rolls.getString("roamingEntityKey")
      state = Combat93Runtime.start(state, listOf(key), (bound.revision + 1).toInt(), Combat93Runtime.stageIndex(state))
      check(Combat93Runtime.active(state)) { "wait_native_encounter_rejected" }
    }
    check(ActionRuntime.activeSession(state) == null && state.turn.pending == null) { "wait_stage_incomplete" }
    val encoded = GameStateCodec.encode(state)
    require(GameStateCodec.decode(encoded) == state) { "wait_stage_not_stable" }
    return state
  }
}
