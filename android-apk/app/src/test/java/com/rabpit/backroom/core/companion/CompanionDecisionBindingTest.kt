package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CompanionDecisionBindingTest {
  private val slot = "slot-a"
  private val request = "req-a"
  private val turnId = "turn-a"
  private val policy = "policy-1"
  private val input = "Tôi muốn đứng đợi tại chỗ này một lát"
  private fun state(location: String = "level-0") =
    GameStateCodec.decode(GameStateCodec.encode(GameState.initial().copy(world = mapOf("location" to location))))
  private fun bytes(state: GameState): ByteArray =
    GameStateCodec.encode(state).toByteArray(StandardCharsets.UTF_8)
  private fun payload(location: String, revision: Long = 0, minutes: Int = 30): String =
    "companion_decision.v1|cao_minh|WAIT|" + revision + "|" + minutes + "|" +
      location.toByteArray(StandardCharsets.UTF_8).size + ":" + location
  private fun turn(input: String = this.input, location: String = "level-0",
                   policy: String = this.policy, revision: Long = 0,
                   payload: String = payload(location, revision)): CompanionPendingTurn {
    val req = CompanionPendingTurn.Request.fromPlayerInput(slot, request, revision, "cao_minh", input)
    return CompanionPendingTurn.begin(req, turnId, revision)
      .lockDecision(CompanionPendingTurn.DecisionLock("cao_minh", revision, policy, payload))
  }
  private fun check(state: GameState = state(), record: CompanionPendingTurn = turn(),
                    persisted: ByteArray = bytes(state), slotId: String = slot,
                    requestId: String = request, revision: Long = 0, policy: String = this.policy,
                    input: String = this.input) =
    CompanionDecisionBinding.verifyWait(persisted, state, record, slotId, requestId, revision, policy, input)

  @Test fun validPersistedWaitBindingAcceptsWithoutChangingCore() {
    val initial = state()
    val before = GameStateCodec.encode(initial)
    val result = check(state = initial)
    assertTrue(result.error ?: "expected acceptance", result.accepted)
    assertNull(result.error)
    assertEquals(before, GameStateCodec.encode(initial))
  }

  @Test fun wrongSlotRequestRevisionPolicyAndInputFailClosed() {
    assertEquals("slot_mismatch", check(slotId = "slot-b").error)
    assertEquals("request_alias_unknown", check(requestId = "req-b").error)
    assertEquals("revision_mismatch", check(revision = 1).error)
    assertEquals("policy_mismatch", check(policy = "policy-2").error)
    assertEquals("input_mismatch", check(input = "Tôi sẽ chờ ở chỗ khác").error)
  }

  @Test fun forgedTypedPayloadOrLocationFailsDespiteValidDecisionDigest() {
    assertEquals("decision_payload_mismatch", check(record = turn(payload = payload("level-1"))).error)
    assertEquals("decision_payload_mismatch", check(record = turn(payload = payload("level-0", minutes = 999))).error)
    assertEquals("decision_payload_mismatch", check(record = turn(payload = "companion_decision.v1|cao_minh|NONE|0|0|7:level-0")).error)
    assertEquals("snapshot_mismatch", check(state = state("level-1"), persisted = bytes(state())).error)
  }

  @Test fun preparingAndShortInputAreNotAcceptedAsGameplayAuthority() {
    val prep = CompanionPendingTurn.begin(
      CompanionPendingTurn.Request.fromPlayerInput(slot, request, 0, "cao_minh", input), turnId, 0)
    assertEquals("decision_phase_invalid", check(record = prep).error)
    val tooShort = "chờ chút"
    assertEquals("input_too_short", check(record = turn(input = tooShort), input = tooShort).error)
  }

  @Test fun missingEffectiveCoreActorFailsClosed() {
    val missing = state().copy(characters = emptyMap(), party = PartyState(memberIds = emptyList()))
    assertEquals("actor_unavailable", check(state = missing, persisted = bytes(missing)).error)
  }
}
