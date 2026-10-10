package com.rabpit.backroom.core.companion;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Observation schema specimen publisher. No production store caller is wired yet.
 * Caller owns the writer transaction and must roll it back on every failure.
 * Existing identities are compared exactly; constraint errors are never ignored.
 */
public final class ObservationPublisher {
  private ObservationPublisher() {}
  public interface Fault { void at(String point); }

  public static void publish(SQLiteDatabase db, String slotId, String turnId, long revision,
                             List<ObservationCandidate> candidates, Fault fault) {
    if (db == null || !db.inTransaction()) throw new IllegalArgumentException("publisher_requires_transaction");
    if (slotId == null || turnId == null || revision <= 0 || fault == null)
      throw new IllegalArgumentException("publisher_binding_invalid");
    if (candidates == null || candidates.size() > 128)
      throw new IllegalArgumentException("publisher_candidates_bound");
    Set<String> ids = new HashSet<>();
    for (ObservationCandidate c : candidates) {
      if (c == null || !slotId.equals(c.getSlotId()) || !turnId.equals(c.getTurnId())
          || revision != c.getRevision() || !ids.add(c.getObservationId()))
        throw new IllegalArgumentException("publisher_scope_mismatch");
    }
    int ordinal = 0;
    for (ObservationCandidate c : candidates) {
      String digest = observationDigest(c);
      ContentValues row = new ContentValues();
      row.put("slot_id", slotId); row.put("observation_id", c.getObservationId());
      row.put("actor_id", c.getOwnerActorId()); row.put("event_id", c.getSourceEventId());
      row.put("created_turn_id", turnId); row.put("committed_revision", revision);
      row.put("access_kind", c.getAccess().name()); row.put("source_actor_id", (String) null);
      row.put("certainty", c.getCertainty().name()); row.put("scene_id", c.getSceneId());
      row.put("policy_version", c.getPolicyVersion());
      row.put("public_payload", c.getPublicPayloadJson());
      row.put("observation_digest", digest);
      // Compare every persisted field on retry. A digest is not authorization.
      try (Cursor old = db.query("actor_observation", null,
          "slot_id=? AND observation_id=?", new String[]{slotId,c.getObservationId()}, null,null,null)) {
        if (old.moveToFirst()) {
          for (String key : row.keySet()) {
            String expected = row.getAsString(key);
            String actual = old.getString(old.getColumnIndexOrThrow(key));
            if (!java.util.Objects.equals(expected,actual))
              throw new IllegalStateException("publisher_observation_conflict");
          }
        } else db.insertOrThrow("actor_observation", null, row);
      }
      fault.at("after_observation_write");
      ContentValues manifest = new ContentValues();
      manifest.put("slot_id", slotId); manifest.put("turn_id", turnId);
      manifest.put("committed_revision", revision); manifest.put("ordinal", ordinal);
      manifest.put("observation_id", c.getObservationId()); manifest.put("observation_digest", digest);
      try (Cursor old = db.query("observation_manifest", null,
          "slot_id=? AND turn_id=? AND committed_revision=? AND ordinal=?",
          new String[]{slotId,turnId,Long.toString(revision),Integer.toString(ordinal)},null,null,null)) {
        if (old.moveToFirst()) {
          if (!c.getObservationId().equals(old.getString(old.getColumnIndexOrThrow("observation_id")))
              || !digest.equals(old.getString(old.getColumnIndexOrThrow("observation_digest"))))
            throw new IllegalStateException("publisher_manifest_conflict");
        } else db.insertOrThrow("observation_manifest",null,manifest);
      }
      fault.at("after_manifest_write"); ordinal++;
    }
    try (Cursor count = db.rawQuery("SELECT COUNT(*) FROM observation_manifest WHERE slot_id=? AND turn_id=? AND committed_revision=?",
        new String[]{slotId,turnId,Long.toString(revision)})) {
      if (!count.moveToFirst() || count.getInt(0) != candidates.size())
        throw new IllegalStateException("publisher_manifest_incomplete");
    }
  }

  static String observationDigest(ObservationCandidate c) {
    return ObservationPublisherDigest.of(c);
  }
}
