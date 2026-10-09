package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.InteractUiContract.InputValidation
import com.rabpit.backroom.core.companion.InteractUiContract.InteractInput
import com.rabpit.backroom.core.companion.InteractUiContract.PendingState
import com.rabpit.backroom.core.companion.InteractUiContract.ProjectionInput
import com.rabpit.backroom.core.companion.InteractUiContract.PublicEventView
import org.junit.Assert.*
import org.junit.Test

/**
 * UI1b single INTERACT input + committed UI projection tests (issue #516).
 *
 * Covers: single-input validation, suggestion-not-command, receipt only after
 * durable success, pending/resume/cancel states, projection isolation.
 */
class InteractUiContractTest {
  private fun input() = InteractInput(
    slotId = "slot-1", actorId = "luc_tram",
    text = "Nên đi về phía đông.", requestAlias = "req-1")

  private fun projectionInput() = ProjectionInput(
    publicEvents = listOf(PublicEventView("ev-1", "Luc Tram spoke.")),
    receiptId = "rcpt-1", receiptTurnId = "turn-9", receiptActionType = "TALK",
    receiptDurable = true, pendingAlias = null, pendingState = null)

  @Test fun singleInput_validSuggestion() {
    val r = InteractUiContract.validateInput(input())
    assertTrue(r is InputValidation.Valid)
    val s = (r as InputValidation.Valid).suggestion
    assertTrue(s.isSuggestion)  // structurally a suggestion, never a command
    assertEquals("Nên đi về phía đông.", s.text)
  }

  @Test fun input_blank_rejected() {
    val r = InteractUiContract.validateInput(input().copy(text = "   "))
    assertEquals("text_blank", (r as InputValidation.Invalid).reason)
  }

  @Test fun input_tooLong_rejected() {
    val r = InteractUiContract.validateInput(input().copy(text = "x".repeat(501)))
    assertEquals("text_too_long", (r as InputValidation.Invalid).reason)
  }

  @Test fun input_directControl_rejected() {
    val r = InteractUiContract.validateInput(input().copy(actorId = "cao_minh"))
    assertEquals("direct_control", (r as InputValidation.Invalid).reason)
  }

  @Test fun input_blankAlias_rejected() {
    val r = InteractUiContract.validateInput(input().copy(requestAlias = ""))
    assertEquals("alias_blank", (r as InputValidation.Invalid).reason)
  }

  @Test fun receipt_durable_rendered() {
    val p = InteractUiContract.project(projectionInput())
    assertNotNull(p.receipt)
    assertEquals("rcpt-1", p.receipt!!.receiptId)
    assertEquals(1, p.publicEvents.size)
  }

  @Test fun receipt_notDurable_neverRendered() {
    val p = InteractUiContract.project(projectionInput().copy(receiptDurable = false))
    assertNull(p.receipt)  // no optimistic authoritative mutation
    assertEquals(1, p.publicEvents.size)  // public projection still renders
  }

  @Test fun receipt_incomplete_notRendered() {
    val p = InteractUiContract.project(projectionInput().copy(receiptId = null))
    assertNull(p.receipt)
  }

  @Test fun pending_states_projected() {
    for (state in PendingState.values()) {
      val p = InteractUiContract.project(projectionInput().copy(
        pendingAlias = "req-1", pendingState = state))
      assertEquals(state, p.pending!!.state)
      assertEquals("req-1", p.pending.requestAlias)
    }
  }

  @Test fun projection_isolation_noPrivateFields() {
    // Structural: UiProjection exposes only public events, receipt, pending.
    // There is no field for GM context, private packets, actor metadata,
    // control writes, or route splits — by type they cannot leak.
    val fields = InteractUiContract.UiProjection::class.java.declaredFields
      .map { it.name }.toSet()
    assertEquals(setOf("publicEvents", "receipt", "pending"), fields)
  }

  @Test fun reload_sameInput_sameProjection() {
    val a = InteractUiContract.project(projectionInput())
    val b = InteractUiContract.project(projectionInput())
    assertEquals(a, b)
  }
}
