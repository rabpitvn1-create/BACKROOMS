package com.rabpit.backroom.core.companion

/**
 * UI1b single INTERACT input + committed UI projection contract (issue #516).
 *
 * In companion flow the three gameplay inputs are replaced by ONE INTERACT
 * input. The input is player speech/advice — a SUGGESTION the character
 * considers; Cao Minh (and companions) decide for themselves. It is never a
 * command and never direct control.
 *
 * UI projection rules (enforced here, UI-agnostic):
 * - The authoritative receipt is rendered ONLY after durable success. No
 *   optimistic authoritative mutation is ever projected.
 * - Pending/resume/cancel and request aliases map to explicit display states.
 * - WebView/localStorage hold PROJECTIONS only: public events, receipts,
 *   pending states. They never receive direct-control writes, route splits,
 *   actor metadata writes, or GM/private contexts — by type, this contract
 *   has no fields for them.
 *
 * Activation/release still waits for R1. Pure Kotlin: no Android, no WebView.
 */
internal object InteractUiContract {
  const val MAX_INPUT_CHARS = 500

  /**
   * The single player input in companion flow.
   * A suggestion, never a command.
   */
  data class InteractInput(
    val slotId: String,
    /** Companion actor the suggestion is addressed to. */
    val actorId: String,
    val text: String,
    /** Stable alias for retry/resume/cancel correlation. */
    val requestAlias: String
  )

  sealed class InputValidation {
    /** Normalized valid suggestion. */
    data class Valid(val suggestion: Suggestion) : InputValidation()
    data class Invalid(val reason: String) : InputValidation()
  }

  data class Suggestion(
    val slotId: String,
    val actorId: String,
    val text: String,
    val requestAlias: String,
    /** Structural marker: suggestions are never commands. */
    val isSuggestion: Boolean = true
  )

  /** UI-side validation of the single input. */
  fun validateInput(input: InteractInput): InputValidation {
    if (input.slotId.isBlank()) return InputValidation.Invalid("slot_blank")
    if (input.actorId.isBlank()) return InputValidation.Invalid("actor_blank")
    // The player suggests TO a companion actor; direct addressing of the
    // protagonist as a puppet is not a valid INTERACT target.
    if (input.actorId == "cao_minh") return InputValidation.Invalid("direct_control")
    val text = input.text.trim()
    if (text.isEmpty()) return InputValidation.Invalid("text_blank")
    if (text.length > MAX_INPUT_CHARS) return InputValidation.Invalid("text_too_long")
    if (input.requestAlias.isBlank()) return InputValidation.Invalid("alias_blank")
    return InputValidation.Valid(Suggestion(input.slotId, input.actorId, text, input.requestAlias))
  }

  // ---- Committed UI projection ----

  enum class PendingState { PENDING, RESUMED, CANCELLED }

  data class PublicEventView(val eventId: String, val summary: String)
  data class ReceiptView(val receiptId: String, val turnId: String, val actionType: String)
  data class PendingView(val requestAlias: String, val state: PendingState)

  /**
   * Everything the UI may render. Projection only — there is no field for GM
   * context, private packets, actor metadata, or control writes.
   */
  data class UiProjection(
    val publicEvents: List<PublicEventView>,
    /** Present only when the receipt is durably committed. */
    val receipt: ReceiptView?,
    val pending: PendingView?
  )

  data class ProjectionInput(
    val publicEvents: List<PublicEventView>,
    /** Authoritative receipt; rendered only if durable. */
    val receiptId: String?,
    val receiptTurnId: String?,
    val receiptActionType: String?,
    val receiptDurable: Boolean,
    val pendingAlias: String?,
    val pendingState: PendingState?
  )

  /** Builds the projection. Non-durable receipts are never rendered. */
  fun project(input: ProjectionInput): UiProjection {
    val receipt = if (input.receiptDurable && input.receiptId != null &&
      input.receiptTurnId != null && input.receiptActionType != null)
      ReceiptView(input.receiptId, input.receiptTurnId, input.receiptActionType)
    else null  // no optimistic authoritative mutation, ever
    val pending = if (input.pendingAlias != null && input.pendingState != null)
      PendingView(input.pendingAlias, input.pendingState) else null
    return UiProjection(
      publicEvents = input.publicEvents,
      receipt = receipt,
      pending = pending)
  }
}
