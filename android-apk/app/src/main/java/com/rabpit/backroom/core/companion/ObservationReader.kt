package com.rabpit.backroom.core.companion

/**
 * M1b.4 actor-filtered observation retrieval + load completeness (issue #501).
 *
 * Boundary rules:
 * - Filter slot AND ownerActor BEFORE any ranking; bounded query, stable order.
 * - Never returns raw GM payloads (only observation columns, never event records).
 * - Load/reopen verifies event/receipt/manifest provenance and the complete record
 *   set: missing / extra / wrong-owner / future-revision rows are rejected; a
 *   corrupt database file is preserved, never deleted or silently repaired.
 * - Historical reads return the committed owner + event; speaker certainty never
 *   becomes proposition truth (that distinction lives in the Claim layer, #505+).
 *
 * Pure Kotlin over caller-supplied rows: no Android, no SQLite, no I/O.
 * The SQL strings below are the exact queries the Android binding runs; they are
 * validated by the M1b.4 sqlite3 harness.
 */
internal object ObservationReader {
  /** Bounded, stable-order observation query. Slot AND owner filter first. */
  const val QUERY_OBSERVATIONS = """
SELECT observation_id, actor_id, event_id, created_turn_id, committed_revision,
       access_kind, source_actor_id, certainty, observation_digest
FROM actor_observation
WHERE slot_id = ? AND actor_id = ?
ORDER BY committed_revision ASC, created_turn_id ASC, observation_id ASC
LIMIT ?"""

  /** Ordered manifest identities for one committed turn. */
  const val QUERY_MANIFEST = """
SELECT observation_id, observation_digest
FROM observation_manifest
WHERE slot_id = ? AND turn_id = ? AND committed_revision = ?
ORDER BY ordinal ASC"""

  /** Receipt existence for provenance. */
  const val QUERY_RECEIPT = """
SELECT committed_revision FROM turn_control
WHERE turn_id = ? AND committed_revision = ?"""

  data class Row(
    val observationId: String,
    val ownerActorId: String,
    val sourceEventId: String,
    val turnId: String,
    val revision: Long,
    val accessKind: String,
    val digest: String
  )

  /**
   * Verifies a loaded record set for one committed turn:
   * manifest identities == observation rows (no missing, no extra),
   * every row's owner matches, no row from a future revision.
   * Throws IllegalArgumentException naming the failure; caller preserves the file.
   */
  fun verifyComplete(
    slotId: String,
    turnId: String,
    revision: Long,
    manifestIds: List<String>,
    rows: List<Row>
  ): List<Row> {
    for (row in rows) {
      require(row.ownerActorId.isNotEmpty()) { "load_wrong_owner" }
      require(row.revision <= revision) { "load_future_revision" }
    }
    val rowIds = rows.map { it.observationId }.toSet()
    val manifestSet = manifestIds.toSet()
    require(manifestSet.size == manifestIds.size) { "load_manifest_duplicate" }
    val missing = manifestSet - rowIds
    val extra = rowIds - manifestSet
    require(missing.isEmpty()) { "load_missing_observations" }
    require(extra.isEmpty()) { "load_extra_observations" }
    // Stable order: revision, turn, observation id.
    return rows.sortedWith(compareBy({ it.revision }, { it.turnId }, { it.observationId }))
  }

  /**
   * Owner filter applied before any ranking or projection. Returns only rows
   * owned by [ownerActorId]; cross-actor rows are dropped, never merged.
   */
  fun filterOwner(rows: List<Row>, ownerActorId: String): List<Row> =
    rows.filter { it.ownerActorId == ownerActorId }
}
