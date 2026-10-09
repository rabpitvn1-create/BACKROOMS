package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.BootstrapInput
import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.LifecycleError
import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.LifecycleResult
import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.LifecycleStore
import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.SlotMetadata
import com.rabpit.backroom.core.companion.CompanionSlotLifecycle.SlotSession
import org.junit.Assert.*
import org.junit.Test

/**
 * UI1a explicit companion new-game + slot lifecycle tests (issue #515).
 *
 * Covers: explicit bootstrap, duplicate guard, seed/pin validation, load
 * missing/corrupt/incompatible (never silent new game), switch scope handling,
 * delete scope handling, no cache resurrection.
 */
class CompanionSlotLifecycleTest {
  private class FakeStore : LifecycleStore {
    val slots = mutableMapOf<String, SlotMetadata>()
    val closedScopes = mutableListOf<String>()
    override fun exists(slotId: String) = slots.containsKey(slotId)
    override fun create(metadata: SlotMetadata) { slots[metadata.slotId] = metadata }
    override fun load(slotId: String) = slots[slotId]
    override fun delete(slotId: String) = slots.remove(slotId) != null
    override fun closeScope(slotId: String) { closedScopes.add(slotId) }
  }

  private fun input() = BootstrapInput(
    approvedSeed = "native-seed-bytes".toByteArray(),
    personaRevision = "R17", personaSha256 = "a".repeat(64),
    ruleVersion = BrainContracts.RULE_VERSION,
    policyVersion = CompanionExposurePolicy.VERSION)

  private fun bootstrap(store: FakeStore, slotId: String = "slot-1") =
    CompanionSlotLifecycle.bootstrapNewCampaign(store, slotId, input())

  @Test fun bootstrap_explicit_createsWithPins() {
    val store = FakeStore()
    val r = bootstrap(store)
    assertTrue(r is LifecycleResult.Ready)
    val m = (r as LifecycleResult.Ready).metadata
    assertEquals("slot-1", m.slotId)
    assertEquals(CompanionSlotLifecycle.SLOT_FORMAT_VERSION, m.formatVersion)
    assertEquals(64, m.seedDigest.length)
    assertEquals("R17", m.personaRevision)
    assertTrue(store.exists("slot-1"))
  }

  @Test fun bootstrap_duplicate_neverOverwrites() {
    val store = FakeStore()
    val first = (bootstrap(store) as LifecycleResult.Ready).metadata
    val r = CompanionSlotLifecycle.bootstrapNewCampaign(store, "slot-1", input())
    assertTrue(r is LifecycleResult.Failed)
    assertEquals(LifecycleError.ALREADY_EXISTS, (r as LifecycleResult.Failed).error)
    assertEquals(first.seedDigest, store.load("slot-1")!!.seedDigest)  // original preserved
  }

  @Test fun bootstrap_emptySeed_rejected() {
    val store = FakeStore()
    val r = CompanionSlotLifecycle.bootstrapNewCampaign(
      store, "slot-1", input().copy(approvedSeed = ByteArray(0)))
    assertEquals(LifecycleError.SEED_UNAPPROVED, (r as LifecycleResult.Failed).error)
    assertFalse(store.exists("slot-1"))
  }

  @Test fun bootstrap_missingPins_rejected() {
    val store = FakeStore()
    val r = CompanionSlotLifecycle.bootstrapNewCampaign(
      store, "slot-1", input().copy(personaRevision = ""))
    assertEquals(LifecycleError.PINS_MISSING, (r as LifecycleResult.Failed).error)
  }

  @Test fun load_missing_neverSilentNewGame() {
    val store = FakeStore()
    val r = CompanionSlotLifecycle.loadSlot(store, "nope")
    assertTrue(r is LifecycleResult.Failed)
    assertEquals(LifecycleError.SLOT_MISSING, (r as LifecycleResult.Failed).error)
    assertFalse(store.exists("nope"))  // nothing auto-created
  }

  @Test fun load_incompatibleVersion_reported() {
    val store = FakeStore()
    bootstrap(store)
    val bad = store.load("slot-1")!!.copy(formatVersion = 999)
    store.slots["slot-1"] = bad
    val r = CompanionSlotLifecycle.loadSlot(store, "slot-1")
    assertEquals(LifecycleError.VERSION_INCOMPATIBLE, (r as LifecycleResult.Failed).error)
  }

  @Test fun load_corrupt_reported() {
    val store = FakeStore()
    bootstrap(store)
    val bad = store.load("slot-1")!!.copy(seedDigest = "short")
    store.slots["slot-1"] = bad
    val r = CompanionSlotLifecycle.loadSlot(store, "slot-1")
    assertEquals(LifecycleError.SLOT_CORRUPT, (r as LifecycleResult.Failed).error)
  }

  @Test fun load_roundTrip_ok() {
    val store = FakeStore()
    bootstrap(store)
    val r = CompanionSlotLifecycle.loadSlot(store, "slot-1")
    assertTrue(r is LifecycleResult.Ready)
  }

  @Test fun switch_closesOldScope_opensNew() {
    val store = FakeStore()
    bootstrap(store, "slot-1")
    bootstrap(store, "slot-2")
    val session = SlotSession()
    session.open("slot-1")
    val r = CompanionSlotLifecycle.switchSlot(store, session, "slot-2")
    assertTrue(r is LifecycleResult.Ready)
    assertEquals(listOf("slot-1"), store.closedScopes)  // old scope closed
    assertEquals("slot-2", session.currentSlotId)
  }

  @Test fun switch_missingTarget_keepsOldOpen() {
    val store = FakeStore()
    bootstrap(store, "slot-1")
    val session = SlotSession()
    session.open("slot-1")
    val r = CompanionSlotLifecycle.switchSlot(store, session, "ghost")
    assertEquals(LifecycleError.SLOT_MISSING, (r as LifecycleResult.Failed).error)
    assertTrue(store.closedScopes.isEmpty())  // old scope untouched
    assertEquals("slot-1", session.currentSlotId)
  }

  @Test fun delete_closesScope_removes() {
    val store = FakeStore()
    bootstrap(store, "slot-1")
    val session = SlotSession()
    session.open("slot-1")
    val r = CompanionSlotLifecycle.deleteSlot(store, session, "slot-1")
    assertTrue(r is LifecycleResult.Deleted)
    assertEquals("slot-1", (r as LifecycleResult.Deleted).slotId)
    assertEquals(listOf("slot-1"), store.closedScopes)
    assertFalse(store.exists("slot-1"))
    assertNull(session.currentSlotId)
    // Reload after delete: missing, not resurrected.
    val reload = CompanionSlotLifecycle.loadSlot(store, "slot-1")
    assertEquals(LifecycleError.SLOT_MISSING, (reload as LifecycleResult.Failed).error)
  }

  @Test fun delete_missing_reported() {
    val store = FakeStore()
    val r = CompanionSlotLifecycle.deleteSlot(store, SlotSession(), "ghost")
    assertEquals(LifecycleError.SLOT_MISSING, (r as LifecycleResult.Failed).error)
  }

  @Test fun noCacheResurrection_afterClose() {
    val session = SlotSession()
    session.open("slot-1")
    session.close()
    assertNull(session.currentSlotId)  // explicit load required to reopen
  }
  @Test fun wrongSlotAndInvalidDigestAreCorrupt() {
    val store=FakeStore(); bootstrap(store)
    val original=store.slots.getValue("slot-1")
    for (bad in listOf(original.copy(slotId="other"),original.copy(seedDigest="z".repeat(64)),original.copy(policyVersion="unknown"))) {
      store.slots["slot-1"]=bad
      assertEquals(LifecycleError.SLOT_CORRUPT,(CompanionSlotLifecycle.loadSlot(store,"slot-1") as LifecycleResult.Failed).error)
    }
  }
  @Test fun failedDeleteIsNotSuccess() {
    val delegate=FakeStore(); bootstrap(delegate)
    val store=object: LifecycleStore by delegate { override fun delete(slotId:String)=false }
    assertEquals(LifecycleError.DELETE_FAILED,(CompanionSlotLifecycle.deleteSlot(store,SlotSession(),"slot-1") as LifecycleResult.Failed).error)
    assertTrue(delegate.exists("slot-1"))
  }

}
