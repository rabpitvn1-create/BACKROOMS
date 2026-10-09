package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

/** #485: authorized results, not merely well-formed routes or matching string policies. */
class CompanionWaitAuthorizerTest {
  private val input = "Tôi đứng chờ tại nơi này trong ba mươi phút"
  private val slot = "slot-wait"
  private val requestId = "request-wait"

  private fun snapshot(stop: String = "level-0", prior: Int = 0,
                       streakNode: String = stop): GameState {
    val level = FeaturedJourneyRoutes.stopLevelNumber(stop)!!
    val node = FeaturedJourneyRoutes.nodeIdAt(stop)!!
    val levelJson = JSONObject().put("number", level).put("stopKey", stop).put("nodeId", node)
    val flags = JSONObject().put("exploration", JSONObject()
      .put("exitStreakNode", streakNode).put("exitStreak", prior))
    return GameStateCodec.decode(GameStateCodec.encode(GameState.initial().copy(world = mapOf(
      "location" to "present-scene", "levelJson" to levelJson.toString(),
      "journeyStopKey" to stop, "worldNodeId" to node, "flagsJson" to flags.toString()
    ))))
  }

  private fun bytes(state: GameState) = GameStateCodec.encode(state).toByteArray(StandardCharsets.UTF_8)
  private fun locked(policy: String = CompanionWaitAuthorizer.WAIT_POLICY): CompanionPendingTurn {
    val req = CompanionPendingTurn.Request.fromPlayerInput(slot, requestId, 0, "cao_minh", input)
    val payload = "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"
    return CompanionPendingTurn.begin(req, "turn-wait", 0).lockDecision(
      CompanionPendingTurn.DecisionLock("cao_minh", 0, policy, payload))
  }
  private fun gate(state: GameState = snapshot(),
                   policy: String = CompanionWaitAuthorizer.WAIT_POLICY,
                   turn: CompanionPendingTurn = locked(policy),
                   persisted: ByteArray = bytes(state), revision: Long = 0L
  ) = CompanionWaitAuthorizer.preflight(persisted, state, turn, slot,
    requestId, revision, policy, input)

  private fun bound(state: GameState = snapshot()): CompanionWaitAuthorizer.Bound {
    val result = gate(state)
    assertNull(result.error)
    return result.bound ?: throw AssertionError("missing native bound")
  }

  private fun evidence(bound: CompanionWaitAuthorizer.Bound, value: Int,
                       claimCompletion: Boolean? = null,
                       claimStreak: Int? = null,
                       forgedRoute: CompanionRollTape.Route? = null,
                       extraDraw: Boolean = false): CompanionWaitAuthorizer.Evidence {
    val capture = CompanionRollTape.Capture { requested ->
      if (requested == 2) value else 0
    }
    val result = ExitStreakEngine.advance(bound.previousStreak, input, false) {
      capture.next(CompanionRollTape.Purpose.EXIT_STREAK, it)
    }
    assertTrue(result.accepted && result.evaluated)
    if (extraDraw) capture.next(CompanionRollTape.Purpose.SURVIVOR, 100)
    val next = FeaturedJourneyRoutes.next(bound.sourceStop)
    val completed = claimCompletion ?: (result.completed && next != null)
    val route = forgedRoute ?: if (completed && next != null) {
      CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
        next.targetLevelNumber, next.targetStopKey, true)
    } else CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
      bound.sourceLevel, bound.sourceStop, false)
    val tape = capture.seal(route)
    return CompanionWaitAuthorizer.Evidence(CompanionWaitAuthorizer.EVIDENCE_VERSION,
      CompanionWaitAuthorizer.WAIT_POLICY, bound.revision, bound.snapshotDigest,
      bound.previousStreak, route, tape.encode(), result.success == true,
      claimStreak ?: if (result.completed) 0 else result.streak, completed, bound.decisionDigest)
  }

  @Test fun reviewerProbeUnknownPolicyMustRejectBeforeAnyRng() {
    val invalid = gate(policy = "unsupported-v999", turn = locked("unsupported-v999"))
    assertNull(invalid.bound)
    assertEquals("policy_version_unsupported", invalid.error)
    val mismatched = gate(policy = "unsupported-v999", turn = locked())
    assertNull(mismatched.bound)
    assertNotNull(mismatched.error)
  }

  @Test fun reviewerProbeGraphEdgeCannotForgeCompletionAtStreakZero() {
    val bound = bound()
    val next = FeaturedJourneyRoutes.next(bound.sourceStop)!!
    val forgedRoute = CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
      next.targetLevelNumber, next.targetStopKey, true)
    val forged = evidence(bound, 0, claimCompletion = true, forgedRoute = forgedRoute)
    assertNull(CompanionWaitAuthorizer.replay(bound, forged).validated)
  }

  @Test fun winLossAndFourToFiveFollowRealExitStreak() {
    for (prior in 0..4) {
      val bound = bound(snapshot(prior = prior))
      val win = CompanionWaitAuthorizer.replay(bound, evidence(bound, 0))
      assertNull(win.error)
      assertEquals(prior == 4, win.validated!!.completed)
      assertEquals(if (prior == 4) 0 else prior + 1, win.validated!!.nextStreak)
      val loss = CompanionWaitAuthorizer.replay(bound, evidence(bound, 1))
      assertNull(loss.error)
      assertFalse(loss.validated!!.completed)
      assertEquals(0, loss.validated!!.nextStreak)
    }
  }

  @Test fun suppressedFifthWinAndForgedRecordedStreakAreDenied() {
    val b = bound(snapshot(prior = 4))
    assertNull(CompanionWaitAuthorizer.replay(b, evidence(b, 0, claimCompletion = false)).validated)
    assertNull(CompanionWaitAuthorizer.replay(b, evidence(b, 0, claimStreak = 4)).validated)
  }

  @Test fun levelThirteenNoOutboundRouteResetsWithoutTransition() {
    val b = bound(snapshot(stop = "level-13", prior = 4))
    val result = CompanionWaitAuthorizer.replay(b, evidence(b, 0))
    assertNull(result.error)
    assertFalse(result.validated!!.completed)
    assertEquals(0, result.validated!!.nextStreak)
  }

  @Test fun wrongSourceChangedPriorAndChangedSnapshotFailClosed() {
    val original = bound(snapshot(prior = 4))
    val winning = evidence(original, 0)
    assertNull(CompanionWaitAuthorizer.replay(bound(snapshot("level-1", 4)), winning).validated)
    assertNull(CompanionWaitAuthorizer.replay(bound(snapshot(prior = 0)), winning).validated)
    assertNull(CompanionWaitAuthorizer.replay(original, winning.copy(snapshotDigest = "0".repeat(64))).validated)
    assertEquals("snapshot_mismatch", gate(state = snapshot(prior = 4),
      persisted = bytes(snapshot(prior = 0))).error)
  }

  @Test fun unknownSchemaAndForgedOutcomeVersionFailClosed() {
    val b = bound()
    assertNull(CompanionWaitAuthorizer.replay(b,
      evidence(b, 0).copy(version = "companion_wait_outcome.v999")).validated)
    assertNull(CompanionWaitAuthorizer.replay(b,
      evidence(b, 0).copy(policy = "unsupported-v999")).validated)
    assertNull(CompanionWaitAuthorizer.replay(b,
      evidence(b, 0).copy(revision = 17L)).validated)
    assertEquals("decision_payload_mismatch", gate(turn = CompanionPendingTurn.begin(
      CompanionPendingTurn.Request.fromPlayerInput(slot, requestId, 0, "cao_minh", input),
      "turn-wait", 0).lockDecision(CompanionPendingTurn.DecisionLock(
      "cao_minh", 0, CompanionWaitAuthorizer.WAIT_POLICY,
      "companion_decision.v999|cao_minh|WAIT|0|30|13:present-scene"))).error)
  }

  @Test fun strictReplayForbidsExtraMissingAndMistypedDraws() {
    val b = bound()
    assertNull(CompanionWaitAuthorizer.replay(b, evidence(b, 0, extraDraw = true)).validated)
    val one = evidence(b, 0)
    val wrongPurpose = one.copy(tape = one.tape.replace("EXIT_STREAK", "SURVIVOR"))
    assertNull(CompanionWaitAuthorizer.replay(b, wrongPurpose).validated)
    val missing = CompanionRollTape.Capture { 0 }.seal(
      CompanionRollTape.Route(b.sourceLevel, b.sourceStop, b.sourceLevel, b.sourceStop, false))
    assertNull(CompanionWaitAuthorizer.replay(b, one.copy(tape = missing.encode())).validated)
  }

  @Test fun rejectedPreflightCannotConsumeAnyExistingRngCallback() {
    var draws = 0
    val bad = gate(state = snapshot(prior = 5))
    if (bad.bound != null) {
      ExitStreakEngine.advance(bad.bound.previousStreak, input, false) { draws++; 0 }
    }
    assertNull(bad.bound)
    assertEquals(0, draws)
  }
}
