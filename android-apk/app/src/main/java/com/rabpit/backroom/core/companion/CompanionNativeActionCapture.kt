package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.CompanionCombatRngBridge
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Native ordinary-action tape. A model supplies an intent, never dice, a
 * snapshot or a destination. Reservation and replay are the sole sources of
 * Core action results. No RNG is consumed while replaying a reserved request.
 */
internal object CompanionNativeActionCapture {
  const val VERSION = "companion_native_action_capture.v1"
  const val DECISION_VERSION = "companion_native_action_decision.v1"
  private const val MAX_BYTES = 131072

  data class Result(
    val envelope: String, val tape: CompanionRollTape.Tape,
    val staged: CompanionNativeActionStage.Result, val turnId: String
  )

  fun lockPayload(intent: DecisionPreflight.Intent, target: String?,
                  revision: Long, stop: String, snapshot: ByteArray): String =
    CompanionWaitCapture.canonical(JSONObject()
      .put("version",DECISION_VERSION)
      .put("actor","cao_minh")
      .put("intent",intent.name)
      .put("target",target ?: JSONObject.NULL)
      .put("revision",revision)
      .put("stop",stop)
      .put("snapshotDigest",CompanionDigests.sha256(snapshot)))

  private fun source(view: CompanionSlotStore.NativeView, requestId: String,
                     revision: Long, input: String): Pair<DecisionPreflight.Intent,String?> {
    require(view.slotId == view.turn.slotId && view.revision == revision &&
      view.turn.expectedRevision == revision &&
      view.policyVersion == CompanionWaitAuthorizer.WAIT_POLICY &&
      view.turn.decision != null &&
      view.turn.decision.actorId == "cao_minh" &&
      view.turn.decision.sceneRevision == revision &&
      view.turn.decision.policyVersion == view.policyVersion) { "native_action_binding_invalid" }
    val req = CompanionPendingTurn.Request.fromPlayerInput(
      view.slotId,requestId,revision,"cao_minh",input)
    require(requestId in view.turn.requestAliases && req.inputDigest == view.turn.inputDigest) {
      "native_action_input_binding_invalid"
    }
    val before = String(view.snapshot(),StandardCharsets.UTF_8)
    val state = GameStateCodec.decode(before)
    require(GameStateCodec.encode(state) == before) { "native_action_snapshot_noncanonical" }
    val lock = JSONObject(view.turn.decision.canonicalPayload)
    require(lock.keys().asSequence().toSet() ==
      setOf("version","actor","intent","target","revision","stop","snapshotDigest")) {
      "native_action_decision_schema_invalid"
    }
    val rawIntent = lock.getString("intent")
    val intent = DecisionPreflight.Intent.entries.firstOrNull { it.name == rawIntent }
      ?: error("native_action_intent_unknown")
    require(intent in setOf(DecisionPreflight.Intent.TALK,DecisionPreflight.Intent.MOVE,
      DecisionPreflight.Intent.SEARCH,DecisionPreflight.Intent.INSPECT)) { "native_action_unsupported" }
    val target = when (val raw = lock.get("target")) {
      JSONObject.NULL -> null
      is String -> raw
      else -> error("native_action_target_type")
    }
    val exact = lockPayload(intent,target,revision,
      state.world["journeyStopKey"] ?: error("native_action_stop_missing"),view.snapshot())
    require(lock.getString("version") == DECISION_VERSION &&
      lock.getString("actor") == "cao_minh" &&
      lock.getLong("revision") == revision &&
      lock.getString("snapshotDigest") == CompanionDigests.sha256(view.snapshot()) &&
      view.turn.decision.canonicalPayload == exact) { "native_action_lock_mismatch" }
    return intent to target
  }

  @JvmStatic @Throws(IOException::class)
  fun reserve(store: CompanionSlotStore, requestId: String, revision: Long,
              input: String, nextInt: java.util.function.IntUnaryOperator): CompanionPendingTurn =
    store.reserveVerified(requestId, { view ->
      source(view,requestId,revision,input)
      if (view.turn.reservation != null)
        replay(view,requestId,revision,input,view.turn.reservation.canonicalPayload)
    }, { view ->
      val outcome = capture(view,requestId,revision,input) { nextInt.applyAsInt(it) }
      CompanionPendingTurn.Reservation(
        "rng-" + view.turn.turnId,view.turn.decision.digest,view.policyVersion,outcome.envelope)
    })

  internal fun capture(view: CompanionSlotStore.NativeView, requestId: String,
                       revision: Long, input: String, random: (Int)->Int): Result {
    val (intent,target) = source(view,requestId,revision,input)
    val state = GameStateCodec.decode(String(view.snapshot(),StandardCharsets.UTF_8))
    val recorder = CompanionRollTape.Capture(random)
    val staged = CompanionCombatRngBridge.capture({
      CompanionNativeActionStage.apply(state,view.turn.turnId,revision,intent,target,recorder::next)
    }, {bound,value -> recorder.record(CompanionRollTape.Purpose.COMBAT_INITIAL,bound,value)})
    val route = CompanionRollTape.Route(
      FeaturedJourneyRoutes.stopLevelNumber(staged.fromStop)!!,staged.fromStop,
      FeaturedJourneyRoutes.stopLevelNumber(staged.toStop)!!,staged.toStop,staged.completed)
    val tape = recorder.seal(route)
    return result(view,staged,tape)
  }

  internal fun replay(view: CompanionSlotStore.NativeView, requestId: String,
                      revision: Long,input: String,encoded: String): Result {
    val (intent,target)=source(view,requestId,revision,input)
    require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "native_action_reservation_large" }
    val envelope = JSONObject(encoded)
    require(envelope.getString("version") == VERSION &&
      envelope.getString("policyDigest") == CompanionNativeGameplayRolls.POLICY_DIGEST &&
      envelope.getString("decisionDigest") == view.turn.decision.digest &&
      envelope.getString("snapshotDigest") == CompanionDigests.sha256(view.snapshot())) {
        "native_action_reservation_policy"
      }
    val tape = CompanionRollTape.decode(envelope.getString("tape"))
    val playback = tape.replay()
    val state = GameStateCodec.decode(String(view.snapshot(),StandardCharsets.UTF_8))
    val staged = CompanionCombatRngBridge.replay({
      CompanionNativeActionStage.apply(state,view.turn.turnId,revision,intent,target,playback::next)
    }, { bound -> playback.next(CompanionRollTape.Purpose.COMBAT_INITIAL,bound) })
    playback.finish()
    val expectedRoute = CompanionRollTape.Route(
      FeaturedJourneyRoutes.stopLevelNumber(staged.fromStop)!!,staged.fromStop,
      FeaturedJourneyRoutes.stopLevelNumber(staged.toStop)!!,staged.toStop,staged.completed)
    require(tape.route == expectedRoute) { "native_action_route_mismatch" }
    val native=result(view,staged,tape)
    require(native.envelope == encoded) { "native_action_reservation_mismatch" }
    return native
  }

  private fun result(view: CompanionSlotStore.NativeView,
                     staged: CompanionNativeActionStage.Result,
                     tape: CompanionRollTape.Tape): Result {
    val json = JSONObject()
      .put("version",VERSION).put("policyDigest",CompanionNativeGameplayRolls.POLICY_DIGEST)
      .put("turn",view.turn.turnId).put("revision",view.revision)
      .put("intent",staged.intent.name).put("minutes",staged.minutes)
      .put("decisionDigest",view.turn.decision.digest)
      .put("snapshotDigest",CompanionDigests.sha256(view.snapshot()))
      .put("afterDigest",CompanionDigests.sha256(staged.after))
      .put("rolls",JSONObject(staged.rolls))
      .put("tape",tape.encode())
    val encoded=CompanionWaitCapture.canonical(json)
    require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "native_action_envelope_large" }
    return Result(encoded,tape,staged,view.turn.turnId)
  }
}
