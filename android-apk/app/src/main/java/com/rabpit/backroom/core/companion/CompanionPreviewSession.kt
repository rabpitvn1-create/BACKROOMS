package com.rabpit.backroom.core.companion

import java.io.Closeable
import java.io.IOException

/** Native lifecycle session. No legacy store, caller seed, provider or synthetic decision fallback. */
internal class CompanionPreviewSession(private val factory: Factory?) : Closeable {
  /** Implemented by the qualified fresh-campaign bootstrap, not by the UI. */
  interface Factory {
    fun createFresh(): CompanionSlotStore
    fun openFresh(slotId: String): CompanionSlotStore
    /** Re-read durable genesis and verify native seed, schema and registry-loaded persona pins. */
    fun verifyPins(store: CompanionSlotStore)
  }

  data class CallbackScope internal constructor(val generation: Long, val slotId: String?)
  data class Projection(val slotId: String, val revision: Long, val requestAlias: String?,
    val pendingPhase: String?, val committedRevision: Long?)

  private var store: CompanionSlotStore? = null
  private var generation = 0L
  private var closed = false
  val available: Boolean get() = factory != null

  @Synchronized fun scope() = CallbackScope(generation, store?.slotId)
  @Synchronized fun isCurrent(scope: CallbackScope): Boolean =
    !closed && scope.generation == generation && scope.slotId == store?.slotId

  @Synchronized fun createFresh(): Projection = replace(requiredFactory().createFresh())
  @Synchronized fun load(slotId: String): Projection {
    if (!slotId.matches(Regex("[0-9a-f]{32}"))) throw IOException("slot_identity_invalid")
    val candidate = requiredFactory().openFresh(slotId)
    if (candidate.slotId != slotId) { candidate.close(); throw IOException("slot_identity_mismatch") }
    return replace(candidate)
  }

  private fun replace(candidate: CompanionSlotStore): Projection {
    try { requiredFactory().verifyPins(candidate); candidate.currentRevision(); candidate.recover() }
    catch (failure: Exception) { candidate.close(); throw failure }
    generation++
    val previous = store
    store = candidate
    previous?.close()
    return project()
  }

  /** Receipt provenance and durability come from the DB, never a UI boolean. */
  @Synchronized fun project(requestAlias: String? = null): Projection {
    val current = requiredStore()
    requiredFactory().verifyPins(current)
    val turn = if (requestAlias == null) current.recover() else current.request(requestAlias)
    if (requestAlias != null && turn == null) throw IOException("request_unknown")
    val alias = requestAlias ?: turn?.requestAliases?.firstOrNull()
    val receipt = if (turn != null && alias != null) current.committedReceipt(
      CompanionPendingTurn.Request(current.slotId, alias, turn.expectedRevision, turn.inputDigest)) else null
    return Projection(current.slotId, current.currentRevision(), alias,
      turn?.phase?.name, receipt?.committedRevision)
  }

  @Synchronized fun resume(alias: String): Projection {
    val current = requiredStore(); requiredFactory().verifyPins(current)
    current.resume(alias); return project(alias)
  }
  @Synchronized fun cancel(alias: String): Projection {
    val current = requiredStore(); requiredFactory().verifyPins(current)
    current.cancel(alias); return project(alias)
  }
  @Synchronized fun deleteCurrent() {
    val current = requiredStore(); requiredFactory().verifyPins(current)
    generation++; store = null
    try { current.deleteSlot() } finally { current.close() }
  }
  @Synchronized fun closeSlot() { generation++; val previous = store; store = null; previous?.close() }
  @Synchronized override fun close() { closed = true; closeSlot() }
  private fun requiredFactory(): Factory {
    if (closed) throw IOException("session_closed")
    return factory ?: throw IOException("qualified_foundation_unavailable")
  }
  private fun requiredStore(): CompanionSlotStore { requiredFactory(); return store ?: throw IOException("slot_not_open") }
}
