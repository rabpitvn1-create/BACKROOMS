package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject

/**
 * Deterministic, Core-owned ordinary intent stage.
 * Never writes SQLite or draws unscoped RNG. The caller must replay the saved
 * tape under the primary writer before committing any receipt.
 */
internal object CompanionNativeActionStage {
  private val ordinary = setOf(DecisionPreflight.Intent.TALK,
    DecisionPreflight.Intent.MOVE, DecisionPreflight.Intent.SEARCH, DecisionPreflight.Intent.INSPECT)
  data class Result(
    val after: String, val intent: DecisionPreflight.Intent, val minutes: Int,
    val fromStop: String, val toStop: String, val streak: Int,
    val completed: Boolean, val exitWon: Boolean, val rolls: String, val commandId: String,
    val utterance: String? = null)

  fun minutes(intent: DecisionPreflight.Intent) = when (intent) {
    DecisionPreflight.Intent.TALK -> 1
    DecisionPreflight.Intent.MOVE -> 10
    DecisionPreflight.Intent.SEARCH, DecisionPreflight.Intent.INSPECT -> 5
    else -> error("ordinary_intent_unsupported")
  }
  fun kind(intent: DecisionPreflight.Intent) = when (intent) {
    DecisionPreflight.Intent.MOVE -> ActionKind.EXPLORE
    DecisionPreflight.Intent.SEARCH, DecisionPreflight.Intent.INSPECT -> ActionKind.SEARCH
    DecisionPreflight.Intent.TALK -> ActionKind.EXECUTE
    else -> error("ordinary_intent_unsupported")
  }
  fun description(intent: DecisionPreflight.Intent, stop: String): String = when (intent) {
    DecisionPreflight.Intent.TALK -> "Cao Minh trao đổi và xác nhận thông tin tại " + stop + "."
    DecisionPreflight.Intent.MOVE -> "Cao Minh thăm dò tuyến đường kế tiếp từ " + stop + "."
    DecisionPreflight.Intent.SEARCH -> "Cao Minh tìm kiếm manh mối trong khu vực " + stop + "."
    DecisionPreflight.Intent.INSPECT -> "Cao Minh kiểm tra chi tiết môi trường tại " + stop + "."
    else -> error("ordinary_intent_unsupported")
  }

  fun apply(original: GameState, turnId: String, revision: Long,
            intent: DecisionPreflight.Intent, target: String?,
            draw: (CompanionRollTape.Purpose, Int) -> Int,
            utterance: String? = null): Result {
    require(intent in ordinary && revision in 0 until Int.MAX_VALUE.toLong()) { "ordinary_intent_invalid" }
    require(if (intent == DecisionPreflight.Intent.TALK) {
      utterance != null && utterance.isNotBlank() && utterance.toByteArray(Charsets.UTF_8).size <= 240 &&
        utterance.none { it.isISOControl() }
    } else utterance == null) { "ordinary_actor_speech_invalid" }
    require(original.turn.pending == null && ActionRuntime.activeSession(original) == null &&
      !Combat93Runtime.active(original) && CombatRuntime.active(original) == null) { "ordinary_core_busy" }
    require(original.party.leaderId == KAI_ID && KAI_ID in original.party.memberIds &&
      original.characters[KAI_ID]?.presence == CharacterPresence.ACTIVE) { "ordinary_actor_absent" }

    val stop = original.world["journeyStopKey"] ?: error("ordinary_stop_missing")
    val levelNo = FeaturedJourneyRoutes.stopLevelNumber(stop) ?: error("ordinary_stop_unknown")
    val node = FeaturedJourneyRoutes.nodeIdAt(stop) ?: error("ordinary_node_unknown")
    val nativeLevel = JSONObject(original.world.getValue("levelJson"))
    require(original.world["worldNodeId"] == node &&
      nativeLevel.getInt("number") == levelNo &&
      nativeLevel.getString("stopKey") == stop &&
      nativeLevel.getString("nodeId") == node) { "ordinary_route_mismatch" }
    val flags = JSONObject(original.world.getValue("flagsJson"))
    val exploration = flags.getJSONObject("exploration")
    require(exploration.getString("exitStreakNode") == stop) { "ordinary_streak_node_invalid" }
    val previous = exploration.getInt("exitStreak")
    require(previous in 0 until ExitStreakEngine.REQUIRED_WINS) { "ordinary_streak_invalid" }
    val next = FeaturedJourneyRoutes.next(stop)
    when (intent) {
      DecisionPreflight.Intent.MOVE -> require(next != null && target == next.targetStopKey) { "ordinary_move_unreachable" }
      DecisionPreflight.Intent.SEARCH -> require(target == null || target == stop) { "ordinary_search_target_invalid" }
      DecisionPreflight.Intent.INSPECT -> require(target == stop) { "ordinary_inspect_target_invalid" }
      DecisionPreflight.Intent.TALK -> require(target != null && target != KAI_ID &&
        target in original.party.memberIds &&
        original.characters[target]?.presence == CharacterPresence.ACTIVE) { "ordinary_talk_target_absent" }
      else -> error("ordinary_intent_invalid")
    }
    val description = description(intent,stop)
    check(ExitStreakEngine.hasMinimumInput(description)) { "ordinary_action_text_invalid" }
    val exit = ExitStreakEngine.advance(previous,description,false) {
      draw(CompanionRollTape.Purpose.EXIT_STREAK,it)
    }
    check(exit.accepted && exit.evaluated && exit.success != null) { "ordinary_exit_unresolved" }
    val completed = exit.completed && next != null
    val destination = if (completed) next!!.targetStopKey else stop
    val streak = if (exit.completed) 0 else exit.streak
    val minutes = minutes(intent)
    val kind = kind(intent)
    val session = "actor-" + turnId
    val started = ActionRuntime.start(original,session,turnId,KAI_ID,kind,description,
      plannedMinutes=minutes,
      searchDepth=if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null)
    check(started.applied) { started.error ?: "ordinary_action_start" }
    val progressed = ActionRuntime.advance(started.state,session,"resolve",minutes)
    check(progressed.applied && !progressed.duplicate &&
      progressed.state.time.elapsedSubjectiveMinutes-original.time.elapsedSubjectiveMinutes == minutes.toLong()) {
        progressed.error ?: "ordinary_time_invalid"
      }
    val flagsJson = JSONObject(original.world.getValue("flagsJson"))
    val progress = flagsJson.getJSONObject("exploration")
    val oldTurns = progress.optInt("levelTurns",0)
    require(oldTurns in 0 until Int.MAX_VALUE) { "ordinary_level_turn_overflow" }
    progress.put("exitStreak",streak).put("exitStreakNode",destination)
      .put("levelTurns",if (completed) 0 else oldTurns+1)

    val beforeRoll = CompanionWaitCapture.project(original,revision)
    val beforeRollExploration = beforeRoll.getJSONObject("flags").getJSONObject("exploration")
    beforeRollExploration.put("exitStreak",streak)
    val rolls = if (completed) JSONObject().put("turn",revision+1).put("meta",false)
      else CompanionNativeGameplayRolls { purpose,bound -> draw(purpose,bound) }
        .make(beforeRoll,kind.name,description,false)
    rolls.put("exitStreak",JSONObject().put("evaluated",true)
      .put("success",exit.success).put("streak",streak)
      .put("target",ExitStreakEngine.REQUIRED_WINS)
      .put("completed",completed).put("fromStopKey",stop)
      .put("toStopKey",destination).put("chance","50/50"))
    val route = if (completed) next!! else null
    val title = route?.let {
      if (it.targetIsNamedArea) "Level " + it.targetLevelNumber + " / " + it.targetTitle
      else "Level " + it.targetStopKey.removePrefix("level-") + " - " + it.targetTitle
    }
    val levelJson = route?.let {
      JSONObject().put("number",it.targetLevelNumber).put("nodeId",it.targetNodeId)
        .put("stopKey",it.targetStopKey).put("name",title).toString()
    } ?: original.world.getValue("levelJson")
    val commandId = turnId + ":NATIVE:" + intent.name
    val command = ValidatedLegacyStateCommand(
      commandId=commandId, turnId=turnId,source=CommandSource.SYSTEM,
      location=if (completed) "" else null,title=title,
      levelJson=levelJson,flagsJson=flagsJson.toString(),validatedByGameEngine=true)
    val staged = CompanionCoreStage.stage(progressed.state,turnId,description,listOf(command))
    check(staged.error == null && staged.candidate != null) { staged.error ?: "ordinary_core_stage_failed" }
    var state = GameStateCodec.decode(staged.candidate.afterSnapshot)
      .copy(world = GameStateCodec.decode(staged.candidate.afterSnapshot).world +
        mapOf("journeyStopKey" to destination,
          "worldNodeId" to FeaturedJourneyRoutes.nodeIdAt(destination)!!))
    if (kind == ActionKind.SEARCH) {
      val covered = ActionRuntime.markSearchCoverage(state,session,setOf("depth:normal"))
      check(covered.applied) { covered.error ?: "ordinary_search_coverage" }
      state=covered.state
    }
    val finished = ActionRuntime.complete(state,session)
    check(finished.applied) { finished.error ?: "ordinary_finish" }
    state=finished.state
    if (!completed && rolls.optJSONObject("entityEncounter")?.optBoolean("success") == true) {
      require(kind == ActionKind.EXPLORE) { "ordinary_encounter_wrong_kind" }
      state = Combat93Runtime.start(state,listOf(rolls.getString("roamingEntityKey")),
        (revision+1).toInt(),Combat93Runtime.stageIndex(state))
      check(Combat93Runtime.active(state)) { "ordinary_combat_not_started" }
    }
    check(ActionRuntime.activeSession(state) == null && state.turn.pending == null &&
      turnId in state.turn.completedTurnIds) { "ordinary_stage_incomplete" }
    val encoded=GameStateCodec.encode(state)
    check(GameStateCodec.decode(encoded)==state) { "ordinary_stage_roundtrip" }
    return Result(encoded,intent,minutes,stop,destination,streak,completed,
      exit.success == true,CompanionWaitCapture.canonical(rolls),commandId,utterance)
  }
}
