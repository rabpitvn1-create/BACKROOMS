package com.rabpit.backroom.core.companion;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/**
 * M1b.3 observation publication (issue #500).
 *
 * Writes observation candidates + manifest inside the CALLER's SQLite transaction,
 * so Core snapshot, native events, observations and the turn receipt commit
 * atomically. Follows CompanionSlotStore's transaction + fault-injection pattern.
 *
 * - Idempotent retry: INSERT OR IGNORE on both tables; re-publication of the same
 *   (slot, turn, revision) produces no double effects.
 * - Immutability: enforced by schema triggers (immutable_observation/_manifest).
 * - Fault injection: Fault.at points mirror the store's commit path.
 * - Fresh-slot-only: the schema is created at slot genesis; this publisher never
 *   creates or migrates tables.
 */
public final class ObservationPublisher {
  private ObservationPublisher() {}

  /** Failure-injection hook, mirroring CompanionSlotStore.Fault. */
  public interface Fault { void at(String point); }

  /**
   * Publishes candidates + manifest. Must be called inside an already-open
   * SQLite transaction owned by the Core receipt commit.
   */
  public static void publish(SQLiteDatabase db, String slotId, String turnId, long revision,
                             List<ObservationCandidate> candidates, Fault fault) {
    if (db == null || !db.inTransaction()) throw new IllegalArgumentException("publisher_requires_transaction");
    if (slotId == null || turnId == null || revision <= 0) throw new IllegalArgumentException("publisher_binding_invalid");
    if (candidates == null) throw new IllegalArgumentException("publisher_candidates_null");
    int ordinal = 0;
    for (ObservationCandidate c : candidates) {
      if (c == null) throw new IllegalArgumentException("publisher_null_candidate");
      if (!slotId.equals(c.getSlotId()) || !turnId.equals(c.getTurnId())
          || revision != c.getRevision()) {
        throw new IllegalArgumentException("publisher_scope_mismatch");
      }
      ContentValues row = new ContentValues();
      row.put("slot_id", slotId);
      row.put("observation_id", c.getObservationId());
      row.put("actor_id", c.getOwnerActorId());
      row.put("event_id", c.getSourceEventId());
      row.put("created_turn_id", turnId);
      row.put("committed_revision", revision);
      row.put("access_kind", c.getAccess().name());
      row.put("source_actor_id", (String) null);
      row.put("certainty", c.getCertainty().name());
      row.put("observation_digest", observationDigest(c));
      long obsRow = db.insertWithOnConflict("actor_observation", null, row,
        SQLiteDatabase.CONFLICT_IGNORE);
      fault.at("after_observation_write");
      ContentValues manifest = new ContentValues();
      manifest.put("slot_id", slotId);
      manifest.put("turn_id", turnId);
      manifest.put("committed_revision", revision);
      manifest.put("ordinal", ordinal);
      manifest.put("observation_id", c.getObservationId());
      manifest.put("observation_digest", observationDigest(c));
      long manRow = db.insertWithOnConflict("observation_manifest", null, manifest,
        SQLiteDatabase.CONFLICT_IGNORE);
      if ((obsRow == -1) != (manRow == -1)) {
        throw new IllegalStateException("publisher_partial_manifest");
      }
      fault.at("after_manifest_write");
      ordinal++;
    }
  }

  /** Canonical digest of an observation candidate's bound fields. */
  static String observationDigest(ObservationCandidate c) {
    String canonical = String.join("|",
      c.getSlotId(), c.getObservationId(), c.getOwnerActorId(), c.getSourceEventId(),
      c.getAccess().name(), c.getCertainty().name(),
      c.getTurnId(), Long.toString(c.getRevision()), c.getSceneId(), c.getPolicyVersion());
    return CompanionDigests.sha256(canonical);
  }
}
