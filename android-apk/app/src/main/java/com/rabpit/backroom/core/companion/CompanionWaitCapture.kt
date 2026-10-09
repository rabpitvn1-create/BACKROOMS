package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Complete ordered native WAIT fixture; no provider, UI, gameplay commit or new RNG. */
object CompanionWaitCapture {
  private const val VERSION = "companion_wait_capture.v2"
  private const val MAX_BYTES = 131072
  class Outcome internal constructor(val encoded: String, val tape: CompanionRollTape.Tape,
    val rolls: String, val afterSnapshot: String, val success: Boolean, val nextStreak: Int, val completed: Boolean)

  @JvmStatic @Throws(IOException::class)
  fun reserve(store: CompanionSlotStore, requestId: String, revision: Long, input: String,
              nativeNextInt: java.util.function.IntUnaryOperator): CompanionPendingTurn =
    store.reserveVerified(requestId, { view ->
      val bound = CompanionWaitSlotBinding.verifyWithin(view, requestId, revision, input)
      if (view.turn.reservation != null) replay(decodeState(view.snapshot()), bound, view.turn.reservation.canonicalPayload)
    }, { view ->
      val bound = CompanionWaitSlotBinding.verifyWithin(view, requestId, revision, input)
      val outcome = capture(decodeState(view.snapshot()), bound) { nativeNextInt.applyAsInt(it) }
      CompanionPendingTurn.Reservation("rng-" + view.turn.turnId, view.turn.decision.digest,
        view.policyVersion, outcome.encoded)
    })

  @JvmStatic @Throws(IOException::class)
  fun readReserved(store: CompanionSlotStore, requestId: String, revision: Long, input: String): Outcome =
    store.inspectNative(requestId) { view ->
      val bound = CompanionWaitSlotBinding.verifyWithin(view, requestId, revision, input)
      val reservation = view.turn.reservation ?: throw IOException("wait_not_reserved")
      replay(decodeState(view.snapshot()), bound, reservation.canonicalPayload)
    }

  internal fun capture(state: GameState, bound: CompanionWaitAuthorizer.Bound,
                       nextInt: (Int) -> Int): Outcome {
    validateSource(state, bound)
    val recorder = CompanionRollTape.Capture(nextInt)
    val result = compute(state, bound, recorder::next)
    val staged = CompanionCombatRngBridge.capture({
      CompanionWaitStage.apply(state, bound, result.route, result.rolls, result.streak)
    }, { size, value -> recorder.record(CompanionRollTape.Purpose.COMBAT_INITIAL, size, value) })
    val tape = recorder.seal(result.route)
    return outcome(bound, result, tape, GameStateCodec.encode(staged))
  }

  internal fun replay(state: GameState, bound: CompanionWaitAuthorizer.Bound, encoded: String): Outcome {
    validateSource(state, bound)
    require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "wait_capture_size" }
    val json = JSONObject(encoded)
    require(json.getString("version") == VERSION &&
      json.getString("policyDigest") == CompanionNativeGameplayRolls.POLICY_DIGEST) { "wait_capture_policy" }
    val tape = CompanionRollTape.decode(json.getString("tape"))
    val playback = tape.replay()
    val result = compute(state, bound, playback::next)
    val staged = CompanionCombatRngBridge.replay({
      CompanionWaitStage.apply(state, bound, result.route, result.rolls, result.streak)
    }, { size -> playback.next(CompanionRollTape.Purpose.COMBAT_INITIAL, size) })
    playback.finish()
    require(result.route == tape.route) { "wait_capture_route" }
    val expected = outcome(bound, result, tape, GameStateCodec.encode(staged))
    // Exact envelope comparison rejects extra fields and all forged outcome/binding assertions.
    require(expected.encoded == encoded) { "wait_capture_outcome_or_binding" }
    return expected
  }

  private data class Computed(val route: CompanionRollTape.Route, val rolls: String,
                              val success: Boolean, val streak: Int)
  private fun compute(state: GameState, bound: CompanionWaitAuthorizer.Bound,
                      draw: (CompanionRollTape.Purpose, Int) -> Int): Computed {
    val exit = ExitStreakEngine.advance(bound.previousStreak, bound.input, false) {
      draw(CompanionRollTape.Purpose.EXIT_STREAK, it)
    }
    check(exit.accepted && exit.evaluated && exit.success != null) { "wait_exit_rejected" }
    val next = FeaturedJourneyRoutes.next(bound.sourceStop)
    val completed = exit.completed && next != null
    val streak = if (exit.completed) 0 else exit.streak
    val route = if (completed) CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
      next!!.targetLevelNumber, next.targetStopKey, true)
      else CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop, bound.sourceLevel, bound.sourceStop, false)
    val before = project(state, bound.revision)
    before.getJSONObject("flags").getJSONObject("exploration").put("exitStreak", streak)
    // A fifth win with an outbound route suppresses ALL ordinary destination rolls.
    val rolls = if (completed) JSONObject().put("turn", before.getInt("turn")).put("meta", false)
      else CompanionNativeGameplayRolls { purpose, size -> draw(purpose, size) }
        .make(before, "EXECUTE", "wait", false)
    rolls.put("exitStreak", JSONObject().put("evaluated", true).put("success", exit.success)
      .put("streak", streak).put("target", ExitStreakEngine.REQUIRED_WINS).put("completed", completed)
      .put("fromLevel", bound.sourceLevel.coerceIn(0, 6)).put("fromStopKey", bound.sourceStop)
      .put("toStopKey", route.targetStop).put("toLevel", route.targetLevel.coerceIn(0, 6))
      .put("chance", "50/50").put("reason", if (exit.completed && !completed) "no_route" else "ok"))
    return Computed(route, canonical(rolls), exit.success == true, streak)
  }
  private fun outcome(bound: CompanionWaitAuthorizer.Bound, result: Computed, tape: CompanionRollTape.Tape, after: String): Outcome {
    val json = JSONObject().put("version", VERSION).put("policy", CompanionWaitAuthorizer.WAIT_POLICY)
      .put("policyDigest", CompanionNativeGameplayRolls.POLICY_DIGEST).put("revision", bound.revision)
      .put("turn", bound.turnId).put("afterSnapshotDigest", hash(after))
      .put("snapshotDigest", bound.snapshotDigest).put("decisionDigest", bound.decisionDigest)
      .put("kind", "EXECUTE").put("action", "wait").put("minutes", 30)
      .put("tape", tape.encode()).put("rolls", result.rolls)
      .put("success", result.success).put("nextStreak", result.streak).put("completed", result.route.completed)
    val encoded = canonical(json)
    require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "wait_capture_size" }
    return Outcome(encoded, tape, result.rolls, after, result.success, result.streak, result.route.completed)
  }
  private fun validateSource(state: GameState, bound: CompanionWaitAuthorizer.Bound) {
    val bytes = GameStateCodec.encode(state).toByteArray(StandardCharsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    require(digest == bound.snapshotDigest && GameStateCodec.decode(String(bytes, StandardCharsets.UTF_8)) == state) { "wait_capture_snapshot" }
    require(bound.revision in 0 until Int.MAX_VALUE.toLong()) { "wait_turn_overflow" }
    require(state.turn.pending == null && ActionRuntime.activeSession(state) == null) { "wait_core_busy" }
  }
  internal fun project(state: GameState, revision: Long): JSONObject = JSONObject()
    .put("turn", revision + 1).put("level", JSONObject(state.world.getValue("levelJson")))
    .put("flags", JSONObject(state.world.getValue("flagsJson")))
    .put("party", JSONArray().apply { state.party.memberIds.filter { it != KAI_ID }.forEach { id ->
      val actor = state.characters.getValue(id)
      put(JSONObject().put("id", actor.id).put("name", actor.name).put("presence", actor.presence.name))
    } })
  private fun decodeState(bytes: ByteArray): GameState = GameStateCodec.decode(String(bytes, StandardCharsets.UTF_8))

  private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

  internal fun canonical(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
      JSONObject.quote(it) + ":" + canonical(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
    is String -> JSONObject.quote(value)
    is Boolean -> value.toString()
    is Number -> JSONObject.numberToString(value)
    else -> throw IllegalArgumentException("wait_capture_json_type")
  }
}
