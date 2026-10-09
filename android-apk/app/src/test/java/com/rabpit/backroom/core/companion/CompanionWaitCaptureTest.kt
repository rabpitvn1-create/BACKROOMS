package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CompanionWaitCaptureTest {
  private val input = "Tôi đứng chờ tại nơi này trong ba mươi phút"
  private fun state(prior: Int = 0, stop: String = "level-0", flags: JSONObject = JSONObject()): GameState {
    val level = FeaturedJourneyRoutes.stopLevelNumber(stop)!!
    val node = FeaturedJourneyRoutes.nodeIdAt(stop)!!
    flags.put("exploration", JSONObject().put("exitStreakNode", stop).put("exitStreak", prior))
    val raw = GameState.initial().copy(world = mapOf("location" to "present-scene",
      "levelJson" to JSONObject().put("number", level).put("stopKey", stop).put("nodeId", node).toString(),
      "journeyStopKey" to stop, "worldNodeId" to node, "flagsJson" to flags.toString()))
    return GameStateCodec.decode(GameStateCodec.encode(raw))
  }
  private fun bound(state: GameState, text: String = input): CompanionWaitAuthorizer.Bound {
    val req = CompanionPendingTurn.Request.fromPlayerInput("slot", "r", 0, "cao_minh", text)
    val turn = CompanionPendingTurn.begin(req, "turn", 0).lockDecision(CompanionPendingTurn.DecisionLock(
      "cao_minh", 0, CompanionWaitAuthorizer.WAIT_POLICY, "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
    return CompanionWaitAuthorizer.preflight(GameStateCodec.encode(state).toByteArray(StandardCharsets.UTF_8),
      state, turn, "slot", "r", 0, CompanionWaitAuthorizer.WAIT_POLICY, text).bound!!
  }
  private fun denies(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return } catch (_: IllegalStateException) { return }
    fail("forged full WAIT capture accepted")
  }
  @Test fun extractedFinalNativePolicyMatchesOriginalBodiesAcrossEligibility() {
    var fixtures = 0
    for (level in 0..13) for (allowed in listOf(true, false)) for (win in listOf(true, false))
      for (kind in listOf("EXECUTE", "SEARCH", "EXPLORE")) for (action in listOf("wait", "tìm nước", "đi kiểm tra")) {
        val native = JSONObject().put("turn", 1).put("level", JSONObject().put("number", level))
          .put("flags", JSONObject().put("survivorEncountersAllowed", allowed).put("entityEncountersAllowed", allowed))
          .put("party", JSONArray().put(JSONObject().put("name", "An Nhiên")).put(JSONObject().put("name", "Lục Trầm")))
        val left = arrayListOf<Int>(); val right = arrayListOf<Int>()
        val actual = CompanionNativeGameplayRolls { _, size -> left.add(size); if (win) 0 else size - 1 }
          .make(JSONObject(native.toString()), kind, action, false)
        val expected = CompanionNativeRollReference { size -> right.add(size); if (win) 0 else size - 1 }
          .make(JSONObject(native.toString()), kind, action, false)
        assertEquals(right, left)
        assertEquals(CompanionWaitCapture.canonical(expected), CompanionWaitCapture.canonical(actual))
        fixtures++
      }
    assertEquals(504, fixtures)
    println("WAIT_NATIVE_POLICY_PARITY_FIXTURES=$fixtures")
  }
  @Test fun fullWaitRecordsCompatibilityConditionalAndFinalEntityDraws() {
    val state = state(); val bound = bound(state)
    val captured = CompanionWaitCapture.capture(state, bound) { 0 }
    assertEquals(listOf("EXIT_STREAK", "SURVIVOR", "DIEP_MINH_ENCOUNTER", "ENTITY_ENCOUNTER",
      "ROAMING_ENTITY_KEY", "LEVEL_BOUND_ENTITY"), captured.tape.draws.map { it.purpose.name })
    assertEquals(listOf(2, 10000, 10000, 10000, 18, 10000), captured.tape.draws.map { it.bound })
    assertEquals(captured.encoded, CompanionWaitCapture.replay(state, bound, captured.encoded).encoded)
    val fail = CompanionWaitCapture.capture(state, bound) { it - 1 }
    assertFalse(fail.tape.draws.any { it.purpose == CompanionRollTape.Purpose.ROAMING_ENTITY_KEY })
    assertEquals(5, fail.tape.draws.size)
    assertEquals(fail.encoded, CompanionWaitCapture.replay(state, bound, fail.encoded).encoded)
  }
  @Test fun nativeFlagsAndFifthWinSuppressOnlyTheirAuthorizedDraws() {
    val disabled = state(flags = JSONObject().put("survivorEncountersAllowed", false).put("entityEncountersAllowed", false))
    val quiet = CompanionWaitCapture.capture(disabled, bound(disabled)) { 0 }
    assertEquals(listOf(CompanionRollTape.Purpose.EXIT_STREAK), quiet.tape.draws.map { it.purpose })
    val fifth = state(4); val b = bound(fifth)
    val transitioned = CompanionWaitCapture.capture(fifth, b) { 0 }
    assertTrue(transitioned.completed); assertEquals(0, transitioned.nextStreak)
    assertEquals(1, transitioned.tape.draws.size)
    assertEquals(transitioned.encoded, CompanionWaitCapture.replay(fifth, b, transitioned.encoded).encoded)
    val terminal = state(4, "level-13")
    val noRoute = CompanionWaitCapture.capture(terminal, bound(terminal)) { 0 }
    assertFalse(noRoute.completed); assertEquals(0, noRoute.nextStreak)
    assertTrue(noRoute.tape.draws.size > 1)
  }
  @Test fun rawPlayerProseCannotChangeLockedWaitIntoSearchSleepOrMeta() {
    val state = state()
    val prose = "sleep search traverse_exit xem trạng thái tìm nước ngủ nhặt đồ Level 13"
    val captured = CompanionWaitCapture.capture(state, bound(state, prose)) { 0 }
    assertFalse(captured.tape.draws.any { it.purpose in listOf(CompanionRollTape.Purpose.LOOT,
      CompanionRollTape.Purpose.ALMOND_WATER, CompanionRollTape.Purpose.HAZARD) })
    val envelope = JSONObject(captured.encoded)
    assertEquals("wait", envelope.getString("action")); assertEquals(30, envelope.getInt("minutes"))
    assertEquals("EXECUTE", envelope.getString("kind"))
  }
  @Test fun fullReplayRejectsMissingExtraReorderedDrawsAndForgedAssertions() {
    val state = state(); val b = bound(state)
    val captured = CompanionWaitCapture.capture(state, b) { it - 1 }
    for (field in listOf("snapshotDigest", "decisionDigest", "policyDigest", "version", "rolls")) {
      val changed = JSONObject(captured.encoded).put(field, "forged")
      denies { CompanionWaitCapture.replay(state, b, CompanionWaitCapture.canonical(changed)) }
    }
    for (changedTape in listOf(captured.tape.encode().replace("SURVIVOR|10000", "HAZARD|10000"),
        captured.tape.encode().replace("SURVIVOR|10000", "SURVIVOR|10001"),
        captured.tape.encode().replace("5\n", "4\n").replace("SURVIVOR|10000|9999\n", ""))) {
      val changed = JSONObject(captured.encoded).put("tape", changedTape)
      denies { CompanionWaitCapture.replay(state, b, CompanionWaitCapture.canonical(changed)) }
    }
    denies { CompanionWaitCapture.replay(state, b, CompanionWaitCapture.canonical(
      JSONObject(captured.encoded).put("completed", true))) }
    denies { CompanionWaitCapture.replay(state, b, CompanionWaitCapture.canonical(
      JSONObject(captured.encoded).put("extra", "field"))) }
  }
  @Test fun staleSnapshotAndActiveSessionRejectBeforeLiveDraw() {
    val original = state(); val b = bound(original); var draws = 0
    val changed = original.copy(world = original.world + ("title" to "changed"))
    denies { CompanionWaitCapture.capture(changed, b) { draws++; 0 } }
    val busy = ActionRuntime.start(original, "session", "session-turn", KAI_ID, ActionKind.EXECUTE, "wait").state
    denies { CompanionWaitCapture.capture(busy, bound(busy)) { draws++; 0 } }
    assertEquals(0, draws)
  }
}
