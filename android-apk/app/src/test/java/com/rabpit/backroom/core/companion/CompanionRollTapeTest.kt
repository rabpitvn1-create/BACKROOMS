package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.ExitStreakEngine
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.junit.Assert.*
import org.junit.Test

class CompanionRollTapeTest {
  private val ordinary = "Tôi muốn chờ tại vị trí hiện tại trong ít phút"
  private fun noRoute() = CompanionRollTape.Route(0, "level-0", 0, "level-0", false)
  private fun denied(block: () -> Unit) {
    try { block(); fail("must fail closed") }
    catch (_: IllegalArgumentException) { }
    catch (_: IllegalStateException) { }
  }
  private fun captured(): CompanionRollTape.Tape {
    val capture = CompanionRollTape.Capture { bound -> assertEquals(2, bound); 0 }
    val progress = ExitStreakEngine.advance(0, ordinary, false) {
      capture.next(CompanionRollTape.Purpose.EXIT_STREAK, it)
    }
    assertTrue(progress.evaluated)
    return capture.seal(noRoute())
  }

  @Test fun realExitStreakNativeCaptureAndReplayWithoutRedraw() {
    var calls = 0
    val capture = CompanionRollTape.Capture { bound ->
      calls++
      assertEquals(2, bound)
      0
    }
    val first = ExitStreakEngine.advance(4, ordinary, false) {
      capture.next(CompanionRollTape.Purpose.EXIT_STREAK, it)
    }
    assertTrue(first.completed)
    val nativeRoute = FeaturedJourneyRoutes.next("level-0")!!
    val tape = capture.seal(CompanionRollTape.Route(
      0, "level-0", nativeRoute.targetLevelNumber, nativeRoute.targetStopKey, true))
    assertEquals(1, calls)
    val replay = CompanionRollTape.decode(tape.encode()).replay()
    val second = ExitStreakEngine.advance(4, ordinary, false) {
      replay.next(CompanionRollTape.Purpose.EXIT_STREAK, it)
    }
    replay.finish()
    assertEquals(first, second)
    assertEquals(1, calls)
  }

  @Test fun missingExtraPurposeAndBoundMismatchReject() {
    val tape = captured()
    denied { tape.replay().next(CompanionRollTape.Purpose.SURVIVOR, 2) }
    denied { tape.replay().next(CompanionRollTape.Purpose.EXIT_STREAK, 10000) }
    val replay = tape.replay()
    assertEquals(0, replay.next(CompanionRollTape.Purpose.EXIT_STREAK, 2))
    replay.finish()
    denied { replay.next(CompanionRollTape.Purpose.EXIT_STREAK, 2) }

    val extraCapture = CompanionRollTape.Capture { 0 }
    extraCapture.next(CompanionRollTape.Purpose.EXIT_STREAK, 2)
    extraCapture.next(CompanionRollTape.Purpose.SURVIVOR, 10000)
    val extra = extraCapture.seal(noRoute()).replay()
    extra.next(CompanionRollTape.Purpose.EXIT_STREAK, 2)
    denied { extra.finish() }
    denied { CompanionRollTape.Capture { 0 }.seal(noRoute()).replay().next(CompanionRollTape.Purpose.EXIT_STREAK, 2) }
  }

  @Test fun invalidNativeRollForgedRouteAndCorruptEncodingFailClosed() {
    denied { CompanionRollTape.Capture { 2 }.next(CompanionRollTape.Purpose.EXIT_STREAK, 2) }
    denied { CompanionRollTape.Capture { 0 }.next(CompanionRollTape.Purpose.EXIT_STREAK, 0) }
    denied { CompanionRollTape.Capture { 0 }.seal(
      CompanionRollTape.Route(0, "level-0", 13, "level-13", true)) }
    val tape = captured()
    denied { CompanionRollTape.decode(tape.encode() + "\nextra") }
    denied { CompanionRollTape.decode("companion_roll_tape.v999") }
  }

  @Test fun existingPendingCodecRetainsNativeOrderedReservationPayload() {
    val tape = captured()
    val request = CompanionPendingTurn.Request.fromPlayerInput(
      "slot-a", "req-a", 0, "cao_minh", ordinary)
    val decision = CompanionPendingTurn.DecisionLock("cao_minh", 0, "policy-1", "native_wait")
    val reservation = CompanionPendingTurn.Reservation("res-a", decision.digest, "policy-1", tape.encode())
    val turn = CompanionPendingTurn.begin(request, "turn-a", 0).lockDecision(decision).reserve(reservation)
    val recovered = CompanionPendingTurnCodec.decode(CompanionPendingTurnCodec.encode(turn))
    assertEquals(tape.encode(), recovered.reservation!!.canonicalPayload)
    val playback = CompanionRollTape.decode(recovered.reservation!!.canonicalPayload).replay()
    assertEquals(0, playback.next(CompanionRollTape.Purpose.EXIT_STREAK, 2))
    playback.finish()
  }
}
