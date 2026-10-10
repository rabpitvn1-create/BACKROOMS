package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.TalkExecutor.TapeEntry

/**
 * A2c typed USE_ITEM / COMBAT_ACTION / NONE executors (issue #514).
 *
 * Authority rules:
 * - USE_ITEM: only a legal, owned, typed item command with payable cost. The
 *   item type/command comes from native inventory authority — never granted by
 *   text, UI, or model prose. The inventory delta references the native
 *   inventory revision.
 * - COMBAT_ACTION: only a legal active CombatChoice/Combat93 target with
 *   payable cost and dice from the scoped ORIGINAL RNG (locked upstream, tape-
 *   captured here). Never the exit roll. Combat must be active at the pinned
 *   combat revision.
 * - NONE: invalid/no-op action. Produces NOTHING: no dialogue, no time, no
 *   event, no brain delta, no observation. Local/meta queries are a separate
 *   path and likewise produce no observations and no exit progress.
 * - Failed attempts keep their intent (never rewritten).
 *
 * Output bundles are isolated specimens. Native inventory/combat capture and
 * the atomic Core commit adapter are still required before runtime use.
 *
 * Pure Kotlin: no Android, no I/O, no provider, no RNG (dice arrive as native
 * facts from the locked RNG).
 */
internal object UseItemCombatNoneExecutor {

  // ---- USE_ITEM ----

  /** Native item facts from inventory authority. */
  data class ItemNativeFacts(
    val actorId: String,
    val itemId: String,
    /** Typed native command id (e.g. "heal", "light"). Never free text. */
    val itemCommand: String,
    val owned: Boolean,
    val usable: Boolean,
    val chargesBefore: Int,
    val costCharges: Int,
    val targetId: String?,
    val targetInRange: Boolean,
    val inventoryRevision: Long
  )

  /** Typed inventory delta — the only mutation this path may describe. */
  data class InventoryDelta(
    val itemId: String,
    val chargesAfter: Int,
    val consumed: Boolean,
    val inventoryRevision: Long
  )

  data class UseItemEvent(
    val eventId: String, val turnId: String, val actorId: String,
    val itemId: String, val itemCommand: String, val targetId: String?
  )

  data class UseItemObservation(
    val observationId: String, val turnId: String, val actorId: String,
    val itemId: String, val itemCommand: String
  )

  data class UseItemBundle(
    val event: UseItemEvent,
    val observation: UseItemObservation,
    val inventoryDelta: InventoryDelta,
    val tape: TapeEntry
  )

  sealed class UseItemResult {
    data class Used(val bundle: UseItemBundle) : UseItemResult()
    data class NotUsed(val reason: String) : UseItemResult()
  }

  fun executeUseItem(
    decided: Decided, facts: ItemNativeFacts,
    turnId: String, observationId: String, tapeSequence: Long
  ): UseItemResult {
    if (decided.intent != Intent.USE_ITEM) return UseItemResult.NotUsed("intent_not_use_item")
    if (!CompanionLockedProposal.consistent(decided)) return UseItemResult.NotUsed("decision_binding_mismatch")
    if (!CompanionLockedProposal.turnMatches(decided,turnId)) return UseItemResult.NotUsed("turn_mismatch")
    if (decided.binding.actorId != facts.actorId) return UseItemResult.NotUsed("actor_mismatch")
    if (decided.targetId != facts.targetId) return UseItemResult.NotUsed("target_mismatch")
    if (facts.chargesBefore < 0 || facts.costCharges < 0 || facts.inventoryRevision < 0) return UseItemResult.NotUsed("inventory_invalid")
    if (decided.itemId != facts.itemId) return UseItemResult.NotUsed("item_mismatch")
    if (!facts.owned) return UseItemResult.NotUsed("item_not_owned")
    if (!facts.usable) return UseItemResult.NotUsed("item_unusable")
    if (facts.chargesBefore < facts.costCharges) return UseItemResult.NotUsed("cost_unpayable")
    if (facts.itemCommand.isBlank()) return UseItemResult.NotUsed("item_command_untyped")
    val target = decided.targetId
    if (target != null && !facts.targetInRange) return UseItemResult.NotUsed("target_out_of_range")
    val chargesAfter = facts.chargesBefore - facts.costCharges
    val consumed = chargesAfter <= 0
    val eventId = "useitem-" + CompanionDigests.sha256(
      listOf(turnId, facts.actorId, facts.itemId, facts.itemCommand).joinToString("|")).take(16)
    val bundle = UseItemBundle(
      event = UseItemEvent(eventId, turnId, facts.actorId, facts.itemId, facts.itemCommand, target),
      observation = UseItemObservation(observationId, turnId, facts.actorId, facts.itemId, facts.itemCommand),
      inventoryDelta = InventoryDelta(facts.itemId, chargesAfter, consumed, facts.inventoryRevision),
      tape = TapeEntry(tapeSequence, turnId, facts.actorId,
        actionType = "USE_ITEM", durationMinutes = 1, eventId = eventId))
    return UseItemResult.Used(bundle)
  }

  // ---- COMBAT_ACTION ----

  /** Native combat facts. Dice come from the scoped original RNG (locked). */
  data class CombatNativeFacts(
    val actorId: String,
    val combatActive: Boolean,
    val combatRevision: Long,
    val expectedCombatRevision: Long,
    val actorIsCombatant: Boolean,
    val targetId: String,
    val targetLegal: Boolean,
    val choiceId: String,
    val choiceLegal: Boolean,
    val costPayable: Boolean,
    /** Dice captured from the scoped original RNG — never the exit roll. */
    val dice: List<Int>,
    val rngScope: String
  )

  data class CombatEvent(
    val eventId: String, val turnId: String, val actorId: String,
    val targetId: String, val choiceId: String,
    val dice: List<Int>, val rngScope: String, val combatRevision: Long
  )

  data class CombatObservation(
    val observationId: String, val turnId: String, val actorId: String,
    val targetId: String, val choiceId: String
  )

  data class CombatBundle(
    val event: CombatEvent,
    val observation: CombatObservation,
    val tape: TapeEntry
  )

  sealed class CombatResult {
    data class Acted(val bundle: CombatBundle) : CombatResult()
    data class NotActed(val reason: String) : CombatResult()
  }

  fun executeCombat(
    decided: Decided, facts: CombatNativeFacts,
    turnId: String, observationId: String, tapeSequence: Long
  ): CombatResult {
    if (decided.intent != Intent.COMBAT_ACTION) return CombatResult.NotActed("intent_not_combat")
    if (!CompanionLockedProposal.consistent(decided)) return CombatResult.NotActed("decision_binding_mismatch")
    if (!CompanionLockedProposal.turnMatches(decided,turnId)) return CombatResult.NotActed("turn_mismatch")
    if (decided.binding.actorId != facts.actorId) return CombatResult.NotActed("actor_mismatch")
    if (facts.combatRevision < 0 || facts.rngScope.isBlank()) return CombatResult.NotActed("combat_scope_invalid")
    if (facts.dice.any { it !in 1..6 }) return CombatResult.NotActed("dice_invalid")
    if (!facts.combatActive) return CombatResult.NotActed("combat_inactive")
    if (facts.combatRevision != facts.expectedCombatRevision)
      return CombatResult.NotActed("combat_revision_mismatch")
    if (!facts.actorIsCombatant) return CombatResult.NotActed("actor_not_combatant")
    if (decided.targetId != facts.targetId) return CombatResult.NotActed("target_mismatch")
    if (!facts.targetLegal) return CombatResult.NotActed("target_illegal")
    if (!facts.choiceLegal) return CombatResult.NotActed("choice_illegal")
    if (!facts.costPayable) return CombatResult.NotActed("cost_unpayable")
    if (facts.dice.isEmpty()) return CombatResult.NotActed("dice_missing")
    val eventId = "combat-" + CompanionDigests.sha256(
      listOf(turnId, facts.actorId, facts.targetId, facts.choiceId,
        facts.dice.joinToString(","), facts.combatRevision.toString()).joinToString("|")).take(16)
    val bundle = CombatBundle(
      event = CombatEvent(eventId, turnId, facts.actorId, facts.targetId,
        facts.choiceId, facts.dice, facts.rngScope, facts.combatRevision),
      observation = CombatObservation(observationId, turnId, facts.actorId, facts.targetId, facts.choiceId),
      tape = TapeEntry(tapeSequence, turnId, facts.actorId,
        actionType = "COMBAT_ACTION", durationMinutes = 1, eventId = eventId))
    return CombatResult.Acted(bundle)
  }

  // ---- NONE ----

  /**
   * NONE acknowledgment. Carries no event, no tape entry, no time, no brain
   * delta, no observation — by type it cannot invent any of them.
   */
  data class NoneAcknowledged(val actorId: String, val turnId: String)

  sealed class NoneResult {
    data class Acknowledged(val ack: NoneAcknowledged) : NoneResult()
    data class Rejected(val reason: String) : NoneResult()
  }

  fun executeNone(decided: Decided, actorId: String, turnId: String): NoneResult {
    if (decided.intent != Intent.NONE) return NoneResult.Rejected("intent_not_none")
    if (!CompanionLockedProposal.consistent(decided)) return NoneResult.Rejected("decision_binding_mismatch")
    if (decided.binding.actorId != actorId) return NoneResult.Rejected("actor_mismatch")
    return NoneResult.Acknowledged(NoneAcknowledged(actorId, turnId))
  }
}
