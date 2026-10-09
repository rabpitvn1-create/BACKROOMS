package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameStateCodec
import java.nio.charset.StandardCharsets
import java.util.function.IntUnaryOperator

/** Isolated test driver only; no UI/provider, legacy persistence or alternate reducer. */
object CompanionWaitTestDriver {
  @JvmStatic fun submit(store: CompanionSlotStore, id: String, revision: Long, input: String,
                       nativeDraw: IntUnaryOperator): CompanionPendingTurn.Receipt {
    val identity=CompanionPendingTurn.Request.fromPlayerInput(store.slotId,id,revision,"cao_minh",input)
    store.committedReceipt(identity)?.let { return it }
    val trimmed=input.trim()
    require(trimmed.codePointCount(0,trimmed.length)>=15) { "input_too_short" }
    val admitted=store.admit(identity)
    require(admitted.admission in setOf(CompanionPendingTurn.AdmissionKind.CREATED,
      CompanionPendingTurn.AdmissionKind.PENDING_REPLAY,CompanionPendingTurn.AdmissionKind.ALIAS)) { "admission_rejected" }
    if(admitted.turn.phase==CompanionPendingTurn.Phase.PREPARING) {
      val state=GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8))
      val location=state.world.getValue("location")
      store.lockDecision(id,CompanionPendingTurn.DecisionLock("cao_minh",revision,CompanionWaitAuthorizer.WAIT_POLICY,
        "companion_decision.v1|cao_minh|WAIT|$revision|30|${location.toByteArray(StandardCharsets.UTF_8).size}:$location"))
    }
    CompanionWaitCapture.reserve(store,id,revision,input,nativeDraw)
    return store.commitWait(id,revision,input,CompanionWaitBatch.prepare(store,id,revision,input))
  }
}
