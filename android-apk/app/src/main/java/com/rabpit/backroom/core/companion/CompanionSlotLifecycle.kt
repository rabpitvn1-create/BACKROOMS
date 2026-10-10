package com.rabpit.backroom.core.companion

/**
 * UI1a explicit companion new-game + slot lifecycle (issue #515).
 *
 * Orchestrates the companion campaign slot lifecycle ABOVE the native store
 * (#500/#502). The dedicated companion DB remains the authority; this layer
 * adds the explicit lifecycle semantics:
 *
 * - Bootstrap is EXPLICIT: a fresh campaign is created only when this function
 *   is called with an approved native seed + brain/persona pins. App updates
 *   never auto-switch or auto-create campaigns.
 * - Legacy keys/data are never touched: this lifecycle operates only on
 *   companion slot ids through the [LifecycleStore] seam.
 * - Load of a corrupt/missing/incompatible slot reports a typed error — it
 *   NEVER silently starts a new game.
 * - Switch/close/delete invalidate handles and cache in the correct scope;
 *   after close, no cached slot resurrects without an explicit load.
 *
 * Preview/test-gated: not production-activated. The native SQLite
 * implementation of [LifecycleStore] is the #517 boundary.
 *
 * Pure Kotlin: no Android, no I/O (the store seam is injected).
 */
internal object CompanionSlotLifecycle {
  /** Lifecycle metadata format version. */
  const val SLOT_FORMAT_VERSION = CompanionSlotStore.FORMAT_VERSION

  data class SlotMetadata(
    val slotId: String,
    val formatVersion: Int,
    val seedDigest: String,
    val personaRevision: String,
    val personaSha256: String,
    val ruleVersion: String,
    val policyVersion: String
  ) {
    /** Structural validation: corrupt rows fail here, never silently. */
    fun validate(): Boolean =
      slotId.isNotBlank() && seedDigest.matches(Regex("[0-9a-f]{64}")) &&
        personaRevision.isNotBlank() && personaSha256.matches(Regex("[0-9a-f]{64}")) &&
        ruleVersion == BrainContracts.RULE_VERSION && policyVersion == CompanionExposurePolicy.VERSION
  }

  data class BootstrapInput(
    /** Approved native seed bytes (provenance verified by the caller). */
    val approvedSeed: ByteArray,
    val personaRevision: String,
    val personaSha256: String,
    val ruleVersion: String,
    val policyVersion: String
  )

  /** Native store seam (SQLite in production, fake in tests). */
  interface LifecycleStore {
    fun exists(slotId: String): Boolean
    fun create(metadata: SlotMetadata)
    fun load(slotId: String): SlotMetadata?
    fun delete(slotId: String): Boolean
    /** Close handles/cache for exactly this slot's scope. */
    fun closeScope(slotId: String)
  }

  enum class LifecycleError {
    SEED_UNAPPROVED, PINS_MISSING, ALREADY_EXISTS,
    SLOT_MISSING, SLOT_CORRUPT, VERSION_INCOMPATIBLE, DELETE_FAILED
  }

  sealed class LifecycleResult {
    data class Ready(val metadata: SlotMetadata) : LifecycleResult()
    data class Deleted(val slotId: String) : LifecycleResult()
    data class Failed(val error: LifecycleError) : LifecycleResult()
  }

  /** Explicit fresh-campaign bootstrap. Never called implicitly. */
  fun bootstrapNewCampaign(
    store: LifecycleStore, slotId: String, input: BootstrapInput
  ): LifecycleResult {
    if (input.approvedSeed.isEmpty()) return LifecycleResult.Failed(LifecycleError.SEED_UNAPPROVED)
    if (slotId.isBlank() || input.personaRevision.isBlank() || !input.personaSha256.matches(Regex("[0-9a-f]{64}")) ||
      input.ruleVersion != BrainContracts.RULE_VERSION || input.policyVersion != CompanionExposurePolicy.VERSION)
      return LifecycleResult.Failed(LifecycleError.PINS_MISSING)
    if (store.exists(slotId)) return LifecycleResult.Failed(LifecycleError.ALREADY_EXISTS)
    val metadata = SlotMetadata(
      slotId = slotId, formatVersion = SLOT_FORMAT_VERSION,
      seedDigest = CompanionDigests.sha256(input.approvedSeed),
      personaRevision = input.personaRevision, personaSha256 = input.personaSha256,
      ruleVersion = input.ruleVersion, policyVersion = input.policyVersion)
    store.create(metadata)
    return LifecycleResult.Ready(metadata)
  }

  /** Load with explicit failure modes — never a silent new game. */
  fun loadSlot(store: LifecycleStore, slotId: String): LifecycleResult {
    val metadata = store.load(slotId)
      ?: return LifecycleResult.Failed(LifecycleError.SLOT_MISSING)
    if (metadata.formatVersion != SLOT_FORMAT_VERSION)
      return LifecycleResult.Failed(LifecycleError.VERSION_INCOMPATIBLE)
    if (metadata.slotId != slotId || !metadata.validate()) return LifecycleResult.Failed(LifecycleError.SLOT_CORRUPT)
    return LifecycleResult.Ready(metadata)
  }

  /** Switch: validate the target first, then close the old scope. */
  fun switchSlot(store: LifecycleStore, session: SlotSession, newSlotId: String): LifecycleResult {
    val loaded = loadSlot(store, newSlotId)
    if (loaded is LifecycleResult.Failed) return loaded
    session.currentSlotId?.let { store.closeScope(it) }
    session.open((loaded as LifecycleResult.Ready).metadata.slotId)
    return loaded
  }

  /** Delete: close the scope first, then delete. */
  fun deleteSlot(store: LifecycleStore, session: SlotSession, slotId: String): LifecycleResult {
    if (!store.exists(slotId)) return LifecycleResult.Failed(LifecycleError.SLOT_MISSING)
    store.closeScope(slotId)
    if (session.currentSlotId == slotId) session.close()
    if (!store.delete(slotId)) return LifecycleResult.Failed(LifecycleError.DELETE_FAILED)
    return LifecycleResult.Deleted(slotId)
  }

  /** Tracks the explicitly-open slot. Close clears it: no cache resurrection. */
  class SlotSession {
    var currentSlotId: String? = null
      private set
    fun open(slotId: String) { currentSlotId = slotId }
    fun close() { currentSlotId = null }
  }
}
