package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Production slot + codec + authorizer; no host database or fake reducer. */
object CompanionWaitStorageFixtures {
  private const val INPUT = "Tôi đứng chờ tại nơi này trong ba mươi phút"
  private const val POLICY = CompanionWaitAuthorizer.WAIT_POLICY
  private fun snapshot(): ByteArray {
    val stop = "level-0"
    val node = FeaturedJourneyRoutes.nodeIdAt(stop)!!
    val level = JSONObject().put("number", 0).put("stopKey", stop).put("nodeId", node)
    val flags = JSONObject().put("exploration", JSONObject().put("exitStreakNode", stop).put("exitStreak", 0))
    val state = GameState.initial().copy(world = mapOf("location" to "present-scene",
      "journeyStopKey" to stop, "worldNodeId" to node, "levelJson" to level.toString(), "flagsJson" to flags.toString()))
    return GameStateCodec.encode(GameStateCodec.decode(GameStateCodec.encode(state))).toByteArray(StandardCharsets.UTF_8)
  }
  private inline fun rejects(reason: String, operation: () -> Unit) {
    try { operation() } catch (error: IOException) {
      check(error.message == reason) { "expected $reason got ${error.message}" }; return
    }
    error("missing rejection $reason")
  }
  @JvmStatic fun nativeBinding(directory: File) {
    val bytes = snapshot()
    CompanionSlotStore.createIn(directory, bytes, POLICY).use { store ->
      val req = CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "native", 0, "cao_minh", INPUT)
      store.admit(req)
      rejects("decision_phase_invalid") { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT) }
      store.lockDecision("native", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      val bound = CompanionWaitSlotBinding.verify(store, "native", 0, INPUT)
      check(bound.sourceStop == "level-0" && bound.previousStreak == 0)
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "alias", 0, "cao_minh", INPUT))
      check(CompanionWaitSlotBinding.verify(store, "alias", 0, INPUT).snapshotDigest == bound.snapshotDigest)
      rejects("input_mismatch") { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT + "!") }
      rejects("revision_mismatch") { CompanionWaitSlotBinding.verify(store, "native", 1, INPUT) }
      rejects("request_unknown") { CompanionWaitSlotBinding.verify(store, "unknown", 0, INPUT) }
      // Returning a copied snapshot cannot change the following authoritative read.
      store.inspectNative("native") { view -> view.snapshot().fill(0); null }
      check(CompanionWaitSlotBinding.verify(store, "native", 0, INPUT).snapshotDigest == bound.snapshotDigest)
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT); error("fault missing") }
      catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(store.request("native").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
    }
  }
  @JvmStatic fun nativeReservationRetry(directory: File) {
    // Control seam fixture only. Full WAIT capture is qualified in its own slice.
    CompanionSlotStore.createIn(directory, snapshot(), POLICY).use { store ->
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "r", 0, "cao_minh", INPUT))
      store.lockDecision("r", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      var draws = 0
      val verify = CompanionSlotStore.NativeValidator { view -> CompanionWaitSlotBinding.verifyWithin(view, "r", 0, INPUT) }
      val capture = CompanionSlotStore.NativeCapture { view ->
        draws++
        CompanionPendingTurn.Reservation("fixture", view.turn.decision.digest, POLICY, "control-seam-fixture")
      }
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { store.reserveVerified("r", verify, capture); error("fault missing") } catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(store.request("r").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
      val saved = store.reserveVerified("r", verify, capture)
      check(draws == 2)
      check(store.reserveVerified("r", verify) { error("redraw") }.reservation.digest == saved.reservation.digest)
      rejects("input_mismatch") {
        store.reserveVerified("r", { view -> CompanionWaitSlotBinding.verifyWithin(view, "r", 0, INPUT + "!") }) { error("redraw") }
      }
      check(draws == 2)
    }
  }
  @JvmStatic fun fullNativeCapture(directory: File) {
    val store = CompanionSlotStore.createIn(directory, snapshot(), POLICY)
    val slot = store.slotId
    var draws = 0
    try {
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(slot, "full", 0, "cao_minh", INPUT))
      store.lockDecision("full", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { CompanionWaitCapture.reserve(store, "full", 0, INPUT) { draws++; it - 1 }; error("fault missing") }
      catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(draws == 5 && store.request("full").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
      val saved = CompanionWaitCapture.reserve(store, "full", 0, INPUT) { draws++; it - 1 }
      check(draws == 10)
      val replay = CompanionWaitCapture.readReserved(store, "full", 0, INPUT)
      check(replay.tape.draws.size == 5 && replay.encoded == saved.reservation.canonicalPayload)
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(slot, "full-alias", 0, "cao_minh", INPUT))
      check(CompanionWaitCapture.reserve(store, "full-alias", 0, INPUT) { error("redraw") }
        .reservation.digest == saved.reservation.digest)
      rejects("input_mismatch") { CompanionWaitCapture.reserve(store, "full", 0, INPUT + "!") { error("redraw") } }
      store.close()
      CompanionSlotStore.openIn(directory, slot, POLICY).use { loaded ->
        check(CompanionWaitCapture.readReserved(loaded, "full-alias", 0, INPUT).encoded == replay.encoded)
        check(CompanionWaitCapture.reserve(loaded, "full", 0, INPUT) { error("redraw") }
          .reservation.digest == saved.reservation.digest)
      }
    } finally { store.close() }
  }
  @JvmStatic fun concurrentNativeCapture(directory: File) {
    val first = CompanionSlotStore.createIn(directory, snapshot(), POLICY)
    val second = CompanionSlotStore.openIn(directory, first.slotId, POLICY)
    val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
    val start = java.util.concurrent.CountDownLatch(1)
    val draws = java.util.concurrent.atomic.AtomicInteger()
    try {
      first.admit(CompanionPendingTurn.Request.fromPlayerInput(first.slotId, "left", 0, "cao_minh", INPUT))
      first.admit(CompanionPendingTurn.Request.fromPlayerInput(first.slotId, "right", 0, "cao_minh", INPUT))
      first.lockDecision("left", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      val left = executor.submit<CompanionPendingTurn> { start.await(); CompanionWaitCapture.reserve(first, "left", 0, INPUT) { draws.incrementAndGet(); 0 } }
      val right = executor.submit<CompanionPendingTurn> { start.await(); CompanionWaitCapture.reserve(second, "right", 0, INPUT) { draws.incrementAndGet(); 0 } }
      start.countDown()
      val a = left.get(30, java.util.concurrent.TimeUnit.SECONDS)
      val b = right.get(30, java.util.concurrent.TimeUnit.SECONDS)
      check(a.reservation.digest == b.reservation.digest && draws.get() == 6)
      check(CompanionWaitCapture.readReserved(first, "left", 0, INPUT).encoded == a.reservation.canonicalPayload)
    } finally { executor.shutdownNow(); first.close(); second.close() }
  }

}
