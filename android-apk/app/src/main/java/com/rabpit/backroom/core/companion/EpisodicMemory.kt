package com.rabpit.backroom.core.companion

/**
 * M2a durable episodic memory (issue #503).
 *
 * A memory references exactly one observation of the SAME slot and owner;
 * provenance (creation turn/revision) is pinned at creation. Memories are
 * immutable; corrections append a new linked record (SUPERSEDED), never delete.
 *
 * v1 native rules (reviewed):
 * - Summary uses typed bounded templates over the observation's PUBLIC payload
 *   only — no invented facts, max 280 chars.
 * - Salience: native creation assigns ORDINARY only. IMPORTANT/PIVOTAL require
 *   the approved salience map (P2a, #505); without it, self-promotion is denied.
 * - interpretationSource NATIVE for adapter-derived memories. A MODEL_AUDITED
 *   interpretation is subjective only: no mutation authority, and never invoked
 *   after Core commit to patch the brain.
 *
 * Pure Kotlin: no Android, no SQLite, no I/O, no provider calls.
 */
internal object EpisodicMemory {
  const val SUMMARY_MAX_CHARS = 280

  enum class Salience { ORDINARY, IMPORTANT, PIVOTAL }
  enum class InterpretationSource { NATIVE, MODEL_AUDITED }
  enum class Status { ACTIVE, SUPERSEDED }

  data class MemoryRecord(
    val memoryId: String,
    val slotId: String,
    val ownerActorId: String,
    val observationId: String,
    val createdTurnId: String,
    val committedRevision: Long,
    val summary: String,
    val topic: String,
    val salience: Salience,
    val interpretationSource: InterpretationSource,
    val status: Status,
    /** Non-null when this record corrects an earlier one; the old row is preserved. */
    val supersedesMemoryId: String? = null
  )

  /**
   * Creates a memory from a verified observation candidate. The candidate must
   * belong to the same slot and owner; otherwise fails closed.
   */
  fun fromObservation(
    candidate: ObservationCandidate,
    slotId: String,
    ownerActorId: String,
    topic: String
  ): MemoryRecord {
    require(candidate.slotId == slotId) { "memory_slot_mismatch" }
    require(candidate.ownerActorId == ownerActorId) { "memory_owner_mismatch" }
    require(topic.matches(Regex("[A-Za-z0-9_.:-]{1,64}"))) { "memory_topic_invalid" }
    val memoryId = CompanionDigests.sha256(
      listOf(slotId, ownerActorId, candidate.observationId, "memory").joinToString("|")
    ).take(32)
    return MemoryRecord(
      memoryId = memoryId,
      slotId = slotId,
      ownerActorId = ownerActorId,
      observationId = candidate.observationId,
      createdTurnId = candidate.turnId,
      committedRevision = candidate.revision,
      summary = summarize(candidate),
      topic = topic,
      // v1: native creation is ORDINARY only; no approved salience map yet.
      salience = Salience.ORDINARY,
      interpretationSource = InterpretationSource.NATIVE,
      status = Status.ACTIVE
    )
  }

  /**
   * Appends a correction as a NEW linked record. The old record is preserved
   * untouched (storage triggers forbid UPDATE); readers resolve the latest by
   * following supersedesMemoryId. Evidence is never deleted.
   */
  fun correct(old: MemoryRecord, evidence: ObservationCandidate, topic: String): MemoryRecord {
    require(old.status == Status.ACTIVE) { "memory_correct_superseded" }
    require(evidence.slotId == old.slotId && evidence.ownerActorId == old.ownerActorId) { "memory_correction_owner" }
    require(evidence.revision > old.committedRevision) { "memory_correction_revision" }
    val correctedSummary=summarize(evidence)
    require(topic.matches(Regex("[A-Za-z0-9_.:-]{1,64}"))) { "memory_topic_invalid" }
    return old.copy(
      memoryId = CompanionDigests.sha256(
        listOf(old.slotId, old.ownerActorId, old.observationId, "correction",
          old.memoryId,evidence.observationId,ObservationPublisherDigest.of(evidence),topic).joinToString("|")).take(32),
      observationId = evidence.observationId,
      createdTurnId = evidence.turnId,
      committedRevision = evidence.revision,
      summary = correctedSummary,
      topic = topic,
      status = Status.ACTIVE,
      supersedesMemoryId = old.memoryId
    )
  }

  /** Resolves the latest record per memory chain (follow supersedes links). */
  fun latest(records: List<MemoryRecord>): List<MemoryRecord> {
    val byId=records.associateBy { Triple(it.slotId,it.ownerActorId,it.memoryId) }
    require(byId.size == records.size) { "memory_duplicate_identity" }
    val parents=hashSetOf<Triple<String,String,String>>()
    for (record in records) record.supersedesMemoryId?.let { id ->
      val key=Triple(record.slotId,record.ownerActorId,id)
      val parent=byId[key] ?: throw IllegalArgumentException("memory_correction_parent_missing")
      require(record.committedRevision > parent.committedRevision) { "memory_correction_revision" }
      require(parents.add(key)) { "memory_correction_branch" }
    }
    return records.filter { Triple(it.slotId,it.ownerActorId,it.memoryId) !in parents }
  }

  /** Typed bounded summary templates over public payload fields only. */
  internal fun summarize(candidate: ObservationCandidate): String {
    val p = candidate.publicPayload
    val access = candidate.access.name
    val base = when {
      p.has("location") -> "[$access] ${p.optString("actor")} at ${p.optString("location")}"
      p.has("target") && p.optBoolean("completed", false) ->
        "[$access] moved ${p.optString("source")} -> ${p.optString("target")}"
      p.has("entities") -> "[$access] combat started (${p.optJSONArray("entities")?.length() ?: 0} entities)"
      else -> "[$access] observed ${candidate.sourceEventId}"
    }
    return base.take(SUMMARY_MAX_CHARS)
  }
}
