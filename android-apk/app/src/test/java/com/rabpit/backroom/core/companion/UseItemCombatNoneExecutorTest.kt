package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.UseItemCombatNoneExecutor.CombatNativeFacts
import com.rabpit.backroom.core.companion.UseItemCombatNoneExecutor.CombatResult
import com.rabpit.backroom.core.companion.UseItemCombatNoneExecutor.ItemNativeFacts
import com.rabpit.backroom.core.companion.UseItemCombatNoneExecutor.NoneResult
import com.rabpit.backroom.core.companion.UseItemCombatNoneExecutor.UseItemResult
import org.junit.Assert.*
import org.junit.Test

/**
 * A2c typed USE_ITEM / COMBAT_ACTION / NONE tests (issue #514).
 *
 * Covers: resources/actor/range, combat revision, dice capture, duplicate
 * safety (deterministic ids), complete tape, fault cases, NONE's no-artifacts.
 */
class UseItemCombatNoneExecutorTest {
  private fun decided(intent: Intent, target: String? = null, item: String? = null) = Decided(
    binding = DecisionPreflight.DecisionBinding(
      proposalDigest = "d".repeat(64), actorId = "luc_tram", slotId = "slot-1",
      slotRevision = 42, intent = intent, targetId = target, itemId = item,
      canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION),
    intent = intent, targetId = target, itemId = item,
    providerCalls = 1)

  private fun itemFacts() = ItemNativeFacts(
    actorId = "luc_tram", itemId = "torch", itemCommand = "light",
    owned = true, usable = true, chargesBefore = 3, costCharges = 1,
    targetId = null, targetInRange = true, inventoryRevision = 11L)

  // ---- USE_ITEM ----

  @Test fun useItem_happyPath_typedDelta() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"), itemFacts(), "turn-9", "obs-1", 10L)
    assertTrue(r is UseItemResult.Used)
    val b = (r as UseItemResult.Used).bundle
    assertEquals("light", b.event.itemCommand)   // typed command, not prose
    assertEquals(2, b.inventoryDelta.chargesAfter)
    assertFalse(b.inventoryDelta.consumed)
    assertEquals(11L, b.inventoryDelta.inventoryRevision)
    assertEquals("USE_ITEM", b.tape.actionType)
  }

  @Test fun useItem_lastCharge_consumed() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"),
      itemFacts().copy(chargesBefore = 1), "turn-9", "obs-1", 10L)
    val b = (r as UseItemResult.Used).bundle
    assertEquals(0, b.inventoryDelta.chargesAfter)
    assertTrue(b.inventoryDelta.consumed)
  }

  @Test fun useItem_notOwned_rejected() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"),
      itemFacts().copy(owned = false), "turn-9", "obs-1", 10L)
    assertEquals("item_not_owned", (r as UseItemResult.NotUsed).reason)
  }

  @Test fun useItem_unpayableCost_rejected() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"),
      itemFacts().copy(chargesBefore = 0), "turn-9", "obs-1", 10L)
    assertEquals("cost_unpayable", (r as UseItemResult.NotUsed).reason)
  }

  @Test fun useItem_untypedCommand_rejected() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"),
      itemFacts().copy(itemCommand = ""), "turn-9", "obs-1", 10L)
    assertEquals("item_command_untyped", (r as UseItemResult.NotUsed).reason)
  }

  @Test fun useItem_targetOutOfRange_rejected() {
    val r = UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, target = "crate", item = "torch"),
      itemFacts().copy(targetId = "crate", targetInRange = false),
      "turn-9", "obs-1", 10L)
    assertEquals("target_out_of_range", (r as UseItemResult.NotUsed).reason)
  }

  @Test fun useItem_deterministicEventId() {
    val a = (UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"), itemFacts(), "turn-9", "obs-1", 10L)
      as UseItemResult.Used).bundle.event.eventId
    val b = (UseItemCombatNoneExecutor.executeUseItem(
      decided(Intent.USE_ITEM, item = "torch"), itemFacts(), "turn-9", "obs-1", 10L)
      as UseItemResult.Used).bundle.event.eventId
    assertEquals(a, b)  // duplicate-safe: same inputs, same id
  }

  // ---- COMBAT_ACTION ----

  private fun combatFacts() = CombatNativeFacts(
    actorId = "luc_tram", combatActive = true, combatRevision = 5L,
    expectedCombatRevision = 5L, actorIsCombatant = true,
    targetId = "husk", targetLegal = true, choiceId = "strike",
    choiceLegal = true, costPayable = true,
    dice = listOf(4, 6), rngScope = "combat93/scope-5")

  @Test fun combat_happyPath_diceCaptured() {
    val r = UseItemCombatNoneExecutor.executeCombat(
      decided(Intent.COMBAT_ACTION, target = "husk"), combatFacts(),
      "turn-9", "obs-2", 11L)
    assertTrue(r is CombatResult.Acted)
    val b = (r as CombatResult.Acted).bundle
    assertEquals(listOf(4, 6), b.event.dice)          // scoped original RNG, captured
    assertEquals("combat93/scope-5", b.event.rngScope)
    assertEquals(5L, b.event.combatRevision)
    assertEquals("COMBAT_ACTION", b.tape.actionType)
  }

  @Test fun combat_inactive_rejected() {
    val r = UseItemCombatNoneExecutor.executeCombat(
      decided(Intent.COMBAT_ACTION, target = "husk"),
      combatFacts().copy(combatActive = false), "turn-9", "obs-2", 11L)
    assertEquals("combat_inactive", (r as CombatResult.NotActed).reason)
  }

  @Test fun combat_revisionMismatch_rejected() {
    val r = UseItemCombatNoneExecutor.executeCombat(
      decided(Intent.COMBAT_ACTION, target = "husk"),
      combatFacts().copy(combatRevision = 6L), "turn-9", "obs-2", 11L)
    assertEquals("combat_revision_mismatch", (r as CombatResult.NotActed).reason)
  }

  @Test fun combat_illegalTarget_rejected() {
    val r = UseItemCombatNoneExecutor.executeCombat(
      decided(Intent.COMBAT_ACTION, target = "husk"),
      combatFacts().copy(targetLegal = false), "turn-9", "obs-2", 11L)
    assertEquals("target_illegal", (r as CombatResult.NotActed).reason)
  }

  @Test fun combat_missingDice_rejected() {
    val r = UseItemCombatNoneExecutor.executeCombat(
      decided(Intent.COMBAT_ACTION, target = "husk"),
      combatFacts().copy(dice = emptyList()), "turn-9", "obs-2", 11L)
    assertEquals("dice_missing", (r as CombatResult.NotActed).reason)
  }

  // ---- NONE ----

  @Test fun none_noArtifacts() {
    val r = UseItemCombatNoneExecutor.executeNone(
      decided(Intent.NONE), "luc_tram", "turn-9")
    assertTrue(r is NoneResult.Acknowledged)
    val ack = (r as NoneResult.Acknowledged).ack
    assertEquals("luc_tram", ack.actorId)
    // By type, Acknowledged carries no event, tape, time, brain delta,
    // or observation — NONE cannot invent artifacts.
  }

  @Test fun none_wrongIntent_rejected() {
    val r = UseItemCombatNoneExecutor.executeNone(
      decided(Intent.WAIT), "luc_tram", "turn-9")
    assertEquals("intent_not_none", (r as NoneResult.Rejected).reason)
  }
}
