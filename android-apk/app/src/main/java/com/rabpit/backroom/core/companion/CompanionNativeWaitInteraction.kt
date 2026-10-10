package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.GameStateCodec
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Audited actor-owned WAIT writer using the existing exact native slot,
 * RNG tape, staged Core mutation, SQLite transaction and durable receipt.
 * Other intents are deliberately refused until their native writers exist.
 */
internal class CompanionNativeWaitInteraction(
  private val context: Context,
  private val store: CompanionSlotStore,
  private val model: Model,
  private val auditor: Auditor,
  private val rng: NativeRandom
) {
  fun interface Model { fun propose(privatePrompt: String): String }
  fun interface Auditor { fun check(privatePrompt: String): String }
  fun interface NativeRandom { fun nextInt(bound: Int): Int }
  data class Committed(val revision: Long, val receipt: String, val coreSnapshot: String)

  @Throws(IOException::class)
  fun submit(exactInput: String, requestId: String): Committed {
    if (!requestId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")))
      throw IOException("companion_request_invalid")
    // Historical aliases are resolved BEFORE reading current revision. A retry
    // after commit must return its durable receipt, never decide another turn.
    val historical = store.request(requestId)
    if (historical != null && historical.phase == CompanionPendingTurn.Phase.COMMITTED) {
      val oldRequest = CompanionPendingTurn.Request.fromPlayerInput(store.slotId,
        requestId, historical.expectedRevision, "cao_minh", exactInput)
      val receipt = store.committedReceipt(oldRequest) ?: throw IOException("receipt_replay_mismatch")
      return Committed(receipt.committedRevision, receipt.finalResult,
        String(store.currentSnapshot(), StandardCharsets.UTF_8))
    }
    val bound = CompanionNativeDecisionContext.bind(context, store, requestId, exactInput)
    val request = CompanionPendingTurn.Request.fromPlayerInput(
      store.slotId, requestId, bound.revision, "cao_minh", exactInput)
    val admitted = store.admit(request)
    if (admitted.admission == CompanionPendingTurn.AdmissionKind.COMMITTED_REPLAY) {
      val receipt = store.committedReceipt(request) ?: throw IOException("receipt_missing")
      return Committed(receipt.committedRevision, receipt.finalResult,
        String(store.currentSnapshot(), StandardCharsets.UTF_8))
    }
    if (admitted.admission !in setOf(CompanionPendingTurn.AdmissionKind.CREATED,
        CompanionPendingTurn.AdmissionKind.PENDING_REPLAY,
        CompanionPendingTurn.AdmissionKind.ALIAS))
      throw IOException("companion_admission_" + admitted.admission.name.lowercase())
    if (!CompanionNativeDecisionContext.exactSameSnapshot(store, bound))
      throw IOException("native_snapshot_changed")

    val prompt = "Bạn là CAO MINH, nhân vật độc lập. Người đồng hành KHÔNG điều khiển ngươi. " +
      "Không kể kết quả trước khi Core xác nhận. Canon chỉ là phong cách và đạo đức, không phải ký ức mới.\n" +
      "Private context:\n" + ActorContextBuilder.canonical(bound.packet) +
      "\nScene: " + bound.scope.sceneId +
      "\nLời khuyên từ người đồng hành (không phải lệnh):\n" + JSONObject.quote(exactInput) +
      "\nHãy tự quyết định. Trả JSON có intent trong TALK, SEARCH, MOVE, WAIT, NONE; " +
      "targetId phải là target đã có native authority, itemId null. " +
      "Nếu quyết định chưa có native executor, hệ thống sẽ từ chối chứ không tự đổi ý ngươi."
    val raw = try { model.propose(prompt) } catch (error: Exception) {
      throw IOException("actor_provider_failed", error)
    }
    val selected = CompanionActorIntentGateway.select(raw, bound.scope, bound.interaction)
    if (selected !is CompanionActorIntentGateway.Result.Accepted)
      throw IOException((selected as CompanionActorIntentGateway.Result.Rejected).reason)
    if (selected.selected.proposal.intent != DecisionPreflight.Intent.WAIT)
      throw IOException("actor_intent_not_yet_atomically_supported")

    val scene = bound.scope.sceneId
    val payload = "companion_decision.v1|cao_minh|WAIT|" + bound.revision +
      "|30|" + scene.toByteArray(StandardCharsets.UTF_8).size + ":" + scene
    val ledger = object: CharacterDecisionOrchestrator.DecisionLedger {
      override fun get(bindingDigest: String): CharacterDecisionOrchestrator.Decided? {
        val turn = store.request(requestId) ?: return null
        val existing = turn.decision ?: return null
        if (existing.canonicalPayload != payload ||
            existing.policyVersion != CompanionWaitAuthorizer.WAIT_POLICY ||
            existing.sceneRevision != bound.revision) throw IOException("decision_lock_mismatch")
        return CharacterDecisionOrchestrator.Decided(selected.selected.binding,
          DecisionPreflight.Intent.WAIT, null, null, 0)
      }
      override fun put(decided: CharacterDecisionOrchestrator.Decided): Boolean {
        if (decided.binding != selected.selected.binding ||
            decided.intent != DecisionPreflight.Intent.WAIT) return false
        val turn = store.request(requestId) ?: throw IOException("pending_turn_missing")
        if (turn.decision != null) return false
        store.lockDecision(requestId, CompanionPendingTurn.DecisionLock(
          "cao_minh", bound.revision, CompanionWaitAuthorizer.WAIT_POLICY, payload))
        return true
      }
    }
    val native = CharacterDecisionOrchestrator.NativeInteractionVerifier { packet, scope, identity, input ->
      packet === bound.packet && scope == bound.scope &&
        identity == bound.interaction && input == exactInput &&
        CompanionNativeDecisionContext.exactSameSnapshot(store, bound)
    }
    val audit = CharacterDecisionOrchestrator.TypedDecisionAuditor { packet, binding, proposal ->
      val auditPrompt = "Kiểm toán quyết định CAO MINH theo canon đã ghim. " +
        "Cấm bịa hồi ức, tiết lộ canon khóa, giả quyền Core, ép lời khuyên thành lệnh.\n" +
        "Private packet:\n" + ActorContextBuilder.canonical(packet) +
        "\nDecision binding: " + binding.proposalDigest +
        "\nProposal: " + JSONObject.quote(proposal) +
        "\nTrả JSON: {\"verdict\":\"PASS\"} hoặc {\"verdict\":\"HARD\"}."
      val verdict = try { JSONObject(auditor.check(auditPrompt)).optString("verdict") }
        catch (_: Exception) { "" }
      if (verdict == "PASS") CharacterDecisionOrchestrator.AuditVerdict(
        CharacterDecisionOrchestrator.AuditDisposition.PASS)
      else CharacterDecisionOrchestrator.AuditVerdict(
        CharacterDecisionOrchestrator.AuditDisposition.HARD, "actor_audit_failed")
    }
    val durable = CharacterDecisionOrchestrator.ReservationGate { choice ->
      if (choice.binding != selected.selected.binding ||
          !CompanionNativeDecisionContext.exactSameSnapshot(store, bound)) return@ReservationGate false
      CompanionWaitCapture.reserve(store, requestId, bound.revision, exactInput) { size ->
        rng.nextInt(size)
      }
      true
    }
    val requestInput = CharacterDecisionOrchestrator.Input(
      packet = bound.packet,
      scope = bound.scope,
      proposal = selected.selected.proposal,
      provider = object: CharacterDecisionOrchestrator.DecisionProvider {
        override fun propose(packet: ActorContextBuilder.Packet,
          binding: DecisionPreflight.DecisionBinding,
          repairHint: String?): CharacterDecisionOrchestrator.DecisionProvider.CallResult =
          CharacterDecisionOrchestrator.DecisionProvider.CallResult(raw, null, 1)
      },
      ledger = ledger,
      typedAuditor = audit,
      interaction = bound.interaction,
      exactPlayerInput = exactInput,
      nativeVerifier = native,
      reservationGate = durable)
    val outcome = CharacterDecisionOrchestrator.decideAuthoritative(requestInput)
    if (outcome !is CharacterDecisionOrchestrator.Outcome.DecidedOutcome)
      throw IOException("actor_decision_" +
        (outcome as CharacterDecisionOrchestrator.Outcome.Rejected).reason)
    val batch = CompanionWaitBatch.prepare(store, requestId, bound.revision, exactInput)
    val receipt = store.commitWait(requestId, bound.revision, exactInput, batch)
    val encoded = String(store.currentSnapshot(), StandardCharsets.UTF_8)
    if (batch.turnId !in GameStateCodec.decode(encoded).turn.completedTurnIds)
      throw IOException("receipt_core_turn_missing")
    return Committed(receipt.committedRevision, receipt.finalResult, encoded)
  }
}
