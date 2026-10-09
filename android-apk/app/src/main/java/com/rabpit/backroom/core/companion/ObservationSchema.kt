package com.rabpit.backroom.core.companion

/**
 * M1b.3 observation storage schema v1 (issue #500).
 *
 * Fresh-slot-only: applied at slot creation alongside the Core receipt tables.
 * Opening a database without this schema (or with a different version) fails
 * closed — no import, no silent recreate. Immutability enforced by triggers.
 *
 * SQL is validated by the M1b.3 atomicity harness (Python sqlite3); the same
 * statements run on Android SQLite (API 24/35) via ObservationPublisher.
 */
internal object ObservationSchema {
  const val SCHEMA_VERSION = 1

  const val CREATE_ACTOR_OBSERVATION = """
CREATE TABLE actor_observation(
  slot_id TEXT NOT NULL,
  observation_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  created_turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL CHECK(committed_revision > 0),
  access_kind TEXT NOT NULL CHECK(access_kind IN ('SEEN','HEARD','TOLD','INFERRED')),
  source_actor_id TEXT,
  certainty TEXT NOT NULL CHECK(certainty IN ('CERTAIN','PLAUSIBLE','UNCERTAIN')),
  scene_id TEXT NOT NULL,
  policy_version TEXT NOT NULL,
  public_payload TEXT NOT NULL CHECK(length(public_payload) <= 131072),
  observation_digest TEXT NOT NULL CHECK(length(observation_digest) = 64),
  CHECK(access_kind != 'INFERRED' OR certainty != 'CERTAIN'),
  CHECK((access_kind != 'TOLD') OR (source_actor_id IS NOT NULL)),
  PRIMARY KEY (slot_id, observation_id),
  UNIQUE (slot_id, observation_id, actor_id),
  UNIQUE (slot_id, actor_id, event_id, access_kind),
  UNIQUE (slot_id, observation_id, created_turn_id, committed_revision, observation_digest),
  FOREIGN KEY (slot_id) REFERENCES slot_meta(slot_id),
  FOREIGN KEY (event_id, created_turn_id, committed_revision)
    REFERENCES native_event(event_id, turn_id, revision),
  FOREIGN KEY (created_turn_id, committed_revision)
    REFERENCES turn_control(turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED
)"""

  const val CREATE_OBSERVATION_MANIFEST = """
CREATE TABLE observation_manifest(
  slot_id TEXT NOT NULL,
  turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL CHECK(committed_revision > 0),
  ordinal INTEGER NOT NULL CHECK(ordinal >= 0),
  observation_id TEXT NOT NULL,
  observation_digest TEXT NOT NULL,
  PRIMARY KEY (slot_id, turn_id, committed_revision, ordinal),
  UNIQUE (slot_id, turn_id, committed_revision, observation_id),
  FOREIGN KEY (slot_id, observation_id, turn_id, committed_revision, observation_digest)
    REFERENCES actor_observation(slot_id, observation_id, created_turn_id, committed_revision, observation_digest),
  FOREIGN KEY (turn_id, committed_revision)
    REFERENCES turn_control(turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED
)"""

  const val CREATE_IMMUTABILITY_TRIGGERS = """
CREATE TRIGGER actor_observation_no_update BEFORE UPDATE ON actor_observation
  BEGIN SELECT RAISE(ABORT,'immutable_observation'); END;
CREATE TRIGGER actor_observation_no_delete BEFORE DELETE ON actor_observation
  BEGIN SELECT RAISE(ABORT,'immutable_observation'); END;
CREATE TRIGGER observation_manifest_no_update BEFORE UPDATE ON observation_manifest
  BEGIN SELECT RAISE(ABORT,'immutable_manifest'); END;
CREATE TRIGGER observation_manifest_no_delete BEFORE DELETE ON observation_manifest
  BEGIN SELECT RAISE(ABORT,'immutable_manifest'); END;
"""

  /** All statements in creation order. Runs inside the slot-creation transaction. */
  fun createStatements(): List<String> {
    val triggers = CREATE_IMMUTABILITY_TRIGGERS.trim().split(Regex("(?<=END;)\\s*"))
      .map { it.trim() }.filter { it.isNotEmpty() }
    return listOf("CREATE UNIQUE INDEX event_observation_binding ON native_event(event_id,turn_id,revision)",
      CREATE_ACTOR_OBSERVATION, CREATE_OBSERVATION_MANIFEST) + triggers
  }
}
