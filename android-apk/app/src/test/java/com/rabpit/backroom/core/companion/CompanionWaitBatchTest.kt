package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CompanionWaitBatchTest {
  private val input = "Tôi đứng chờ tại nơi này trong ba mươi phút"
  private fun fixture(prior: Int = 0, zero: Boolean = false): CompanionSlotStore.NativeView {
    val node = FeaturedJourneyRoutes.nodeIdAt("level-0")!!
    val flags = JSONObject().put("exploration", JSONObject().put("exitStreakNode", "level-0").put("exitStreak", prior))
    val raw = GameState.initial().copy(world = mapOf("location" to "present-scene", "journeyStopKey" to "level-0",
      "worldNodeId" to node, "levelJson" to JSONObject().put("number", 0).put("stopKey", "level-0").put("nodeId", node).toString(),
      "flagsJson" to flags.toString()))
    val state = GameStateCodec.decode(GameStateCodec.encode(raw))
    val bytes = GameStateCodec.encode(state).toByteArray(StandardCharsets.UTF_8)
    var turn = CompanionPendingTurn.begin(CompanionPendingTurn.Request.fromPlayerInput("slot", "request", 0, "cao_minh", input), "batch-turn", 0)
      .lockDecision(CompanionPendingTurn.DecisionLock("cao_minh", 0, CompanionWaitAuthorizer.WAIT_POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
    val bound = CompanionWaitAuthorizer.preflight(bytes, state, turn, "slot", "request", 0,
      CompanionWaitAuthorizer.WAIT_POLICY, input).bound!!
    val tape = CompanionWaitCapture.capture(state, bound) { if (zero) 0 else it - 1 }
    turn = turn.reserve(CompanionPendingTurn.Reservation("reservation", turn.decision.digest,
      CompanionWaitAuthorizer.WAIT_POLICY, tape.encoded))
    return CompanionSlotStore.NativeView("slot", CompanionWaitAuthorizer.WAIT_POLICY, 0, turn, bytes)
  }
  private fun build(view: CompanionSlotStore.NativeView) = CompanionWaitBatch.build(view, "request", 0, input)
  private fun rejects(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return } catch (_: IllegalStateException) { return }
    fail("forged batch accepted")
  }
  private fun alter(batch: CompanionWaitBatch.Batch, snapshot: String = batch.afterSnapshot,
                    manifest: String = batch.manifest, events: List<CompanionWaitBatch.Event> = batch.events) =
    CompanionWaitBatch.Batch(batch.slotId, batch.turnId, batch.requestId, batch.expectedRevision,
      batch.inputDigest, batch.decisionDigest, batch.reservationDigest, batch.beforeSnapshot, snapshot,
      manifest, batch.finalResult, events)

  @Test fun nativeWaitStagesThirtyMinutesWithoutSleepOrSourcePublication() {
    val view = fixture(); val before = String(view.snapshot(), StandardCharsets.UTF_8)
    val original = GameStateCodec.decode(before)
    val batch = build(view); val after = GameStateCodec.decode(batch.afterSnapshot)
    assertEquals(before, batch.beforeSnapshot)
    assertEquals(30L, after.time.elapsedSubjectiveMinutes - original.time.elapsedSubjectiveMinutes)
    assertNull(ActionRuntime.activeSession(after))
    assertEquals("COMPLETED", after.metadata["lastAction.phase"])
    assertEquals("30", after.metadata["lastAction.elapsedMinutes"])
    assertTrue("batch-turn" in after.turn.completedTurnIds)
    assertNull(after.turn.pending)
    assertFalse(after.turn.executedCommandIds.any { it.contains("REST") })
    assertEquals(listOf("WAIT_COMPLETED", "EXIT_STREAK_RESOLVED"), batch.events.map { it.type })
    assertEquals(before, String(view.snapshot(), StandardCharsets.UTF_8))
    assertEquals(GameStateCodec.encode(original), before)
  }
  @Test fun fifthWinProducesNativeRouteAndNoDestinationEncounter() {
    val view = fixture(4, true); val batch = build(view)
    val after = GameStateCodec.decode(batch.afterSnapshot)
    val target = FeaturedJourneyRoutes.next("level-0")!!
    assertEquals(target.targetStopKey, after.world["journeyStopKey"])
    assertEquals(target.targetNodeId, after.world["worldNodeId"])
    assertEquals(target.targetStopKey, JSONObject(after.world.getValue("levelJson")).getString("stopKey"))
    assertEquals(0, JSONObject(after.world.getValue("flagsJson")).getJSONObject("exploration").getInt("exitStreak"))
    assertEquals(target.targetStopKey, JSONObject(after.world.getValue("flagsJson")).getJSONObject("exploration").getString("exitStreakNode"))
    assertFalse(Combat93Runtime.active(after))
    assertEquals(listOf("WAIT_COMPLETED", "EXIT_STREAK_RESOLVED", "WORLD_TRANSITION"), batch.events.map { it.type })
  }
  @Test fun encounterIsStartedByRealCoreFromPersistedNativeSelection() {
    val view = fixture(zero = true); val batch = build(view)
    val after = GameStateCodec.decode(batch.afterSnapshot)
    assertTrue(Combat93Runtime.active(after))
    val combat = Combat93Runtime.toJson(after)!!
    val rolls = JSONObject(JSONObject(view.turn.reservation.canonicalPayload).getString("rolls"))
    assertEquals(rolls.getString("roamingEntityKey"), combat.getJSONArray("entities").getJSONObject(0).getString("key"))
    assertTrue(batch.events.any { it.type == "COMBAT_STARTED" })
    assertEquals(30L, after.time.elapsedSubjectiveMinutes)
  }
  @Test fun deterministicReplayBindsEveryRecordAndDigest() {
    val view = fixture(); val one = build(view); val two = build(view)
    assertEquals(one.afterSnapshot, two.afterSnapshot)
    assertEquals(one.manifest, two.manifest)
    assertEquals(one.finalResult, two.finalResult)
    assertEquals(one.events.map { it.digest }, two.events.map { it.digest })
    assertEquals(one.manifest, CompanionWaitBatch.verify(view, "request", 0, input, one).manifest)
    val manifest = JSONObject(one.manifest)
    assertEquals(CompanionWaitBatch.hash(one.afterSnapshot), manifest.getString("afterSnapshotDigest"))
    assertEquals(one.events.size, manifest.getJSONArray("events").length())
    assertEquals(0, manifest.getJSONArray("observations").length())
  }
  @Test fun validHashesCannotAuthorizeForgedStateOrRecordSets() {
    val view = fixture(); val batch = build(view)
    val snapshot = GameStateCodec.encode(GameStateCodec.decode(batch.afterSnapshot).copy(world = mapOf("cheat" to "true")))
    val manifest = JSONObject(batch.manifest).put("afterSnapshotDigest", CompanionWaitBatch.hash(snapshot))
    rejects { CompanionWaitBatch.verify(view, "request", 0, input, alter(batch, snapshot,
      CompanionWaitCapture.canonical(manifest))) }
    rejects { CompanionWaitBatch.verify(view, "request", 0, input, alter(batch, events = batch.events.dropLast(1))) }
    rejects { CompanionWaitBatch.verify(view, "request", 0, input, alter(batch, events = batch.events + batch.events.first())) }
    rejects { CompanionWaitBatch.verify(view, "request", 0, input, alter(batch, events = batch.events.reversed())) }
  }
  @Test fun immutableEventsAndChangedPendingCannotBeReinterpreted() {
    val view = fixture(); val batch = build(view)
    try { (batch.events as MutableList).clear(); fail("events mutable") } catch (_: UnsupportedOperationException) { }
    val preparing = CompanionPendingTurn.begin(CompanionPendingTurn.Request.fromPlayerInput("slot", "request", 0, "cao_minh", input), "batch-turn", 0)
    val changed = CompanionSlotStore.NativeView("slot", CompanionWaitAuthorizer.WAIT_POLICY, 0, preparing, view.snapshot())
    rejects { CompanionWaitBatch.verify(changed, "request", 0, input, batch) }
  }

  private fun ledger(batch: CompanionWaitBatch.Batch) = batch.events.map {
    CompanionSlotStore.LedgerEvent(it.id,batch.turnId,batch.expectedRevision+1,it.ordinal,it.type,it.record,it.digest)
  }
  private fun committed(view: CompanionSlotStore.NativeView, batch: CompanionWaitBatch.Batch,
      manifest: String = batch.manifest) = view.turn.markCommitted(0,batch.decisionDigest,batch.reservationDigest,manifest,batch.finalResult)
  private fun ledgerRejects(block: () -> Unit) {
    try { block() } catch (_: java.io.IOException) { return }; fail("invalid ledger accepted")
  }
  @Test fun receiptAndChainValidateCompleteNativeProjection() {
    val view=fixture(); val batch=build(view); val turn=committed(view,batch)
    CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,1,turn,ledger(batch))
    CompanionLedgerVerifier.verifyHead(batch.afterSnapshot.toByteArray(StandardCharsets.UTF_8),turn)
    assertEquals(CompanionWaitBatch.hash(batch.afterSnapshot),
      CompanionLedgerVerifier.verifyChainLink(1,CompanionWaitBatch.hash(batch.beforeSnapshot),turn))
  }
  @Test fun receiptRejectsMissingExtraAndForeignEventRows() {
    val view=fixture(); val batch=build(view); val turn=committed(view,batch); val rows=ledger(batch)
    ledgerRejects { CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,1,turn,rows.dropLast(1)) }
    ledgerRejects { CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,1,turn,rows+rows.first()) }
    ledgerRejects { CompanionLedgerVerifier.verifyReceipt("foreign",view.policyVersion,1,turn,rows) }
    ledgerRejects { CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,0,turn,rows) }
    ledgerRejects { CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,1,turn,rows.reversed()) }
  }
  @Test fun headAndChainRejectMatchingReceiptWithWrongStateOrPredecessor() {
    val view=fixture(); val batch=build(view); val turn=committed(view,batch)
    ledgerRejects { CompanionLedgerVerifier.verifyHead(batch.beforeSnapshot.toByteArray(StandardCharsets.UTF_8),turn) }
    ledgerRejects { CompanionLedgerVerifier.verifyChainLink(1,"0".repeat(64),turn) }
    ledgerRejects { CompanionLedgerVerifier.verifyChainLink(2,CompanionWaitBatch.hash(batch.beforeSnapshot),turn) }
  }
  @Test fun receiptRejectsUnknownVersionsAndUnmanifestedCollections() {
    val view=fixture(); val batch=build(view)
    for (m in listOf(JSONObject(batch.manifest).put("version","unknown"),
      JSONObject(batch.manifest).put("resultDigest","0".repeat(64)),
      JSONObject(batch.manifest).put("observations",org.json.JSONArray().put("forged")),
      JSONObject(batch.manifest).put("extra","forged"))) {
      ledgerRejects { CompanionLedgerVerifier.verifyReceipt("slot",view.policyVersion,1,
        committed(view,batch,CompanionWaitCapture.canonical(m)),ledger(batch)) }
    }
  }
}
