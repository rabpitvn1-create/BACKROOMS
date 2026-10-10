package com.rabpit.backroom.core.companion

/**
 * M2a episodic memory storage schema v1 (issue #503).
 *
 * Per Technical Design V1 §5.2 actor_memory specimen, bound to the real
 * turn_control / actor_observation tables. Fresh-slot-only, immutable via
 * triggers. Corrections are new rows (supersedes_memory_id); UPDATE/DELETE
 * are forbidden.
 */
internal object EpisodicMemorySchema {
  const val SCHEMA_VERSION = 1

  const val CREATE_ACTOR_MEMORY = """
CREATE TABLE actor_memory(
  slot_id TEXT NOT NULL,
  memory_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  observation_id TEXT NOT NULL,
  created_turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL CHECK(committed_revision > 0),
  topic TEXT NOT NULL CHECK(length(topic) BETWEEN 1 AND 64),
  subjective_summary TEXT NOT NULL CHECK(length(subjective_summary) <= 280),
  interpretation_source TEXT NOT NULL CHECK(interpretation_source IN ('NATIVE','MODEL_AUDITED')),
  salience TEXT NOT NULL CHECK(salience IN ('ORDINARY','IMPORTANT','PIVOTAL')),
  status TEXT NOT NULL CHECK(status IN ('ACTIVE','SUPERSEDED')),
  supersedes_memory_id TEXT,
  PRIMARY KEY (slot_id, memory_id),
  UNIQUE (slot_id, memory_id, actor_id),
  FOREIGN KEY (slot_id, observation_id, actor_id)
    REFERENCES actor_observation(slot_id, observation_id, actor_id),
  UNIQUE (slot_id, actor_id, supersedes_memory_id),
  CHECK(supersedes_memory_id IS NULL OR supersedes_memory_id != memory_id),
  FOREIGN KEY (slot_id, supersedes_memory_id, actor_id)
    REFERENCES actor_memory(slot_id, memory_id, actor_id),
  FOREIGN KEY (created_turn_id, committed_revision)
    REFERENCES turn_control(turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED
)"""

  const val CREATE_IMMUTABILITY_TRIGGERS = """
CREATE TRIGGER actor_memory_no_update BEFORE UPDATE ON actor_memory
  BEGIN SELECT RAISE(ABORT,'immutable_memory'); END;
CREATE TRIGGER actor_memory_no_delete BEFORE DELETE ON actor_memory
  BEGIN SELECT RAISE(ABORT,'immutable_memory'); END;
"""

  const val CREATE_MEMORY_MANIFEST = """
CREATE TABLE memory_manifest(
  slot_id TEXT NOT NULL,
  turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL CHECK(committed_revision>0),
  ordinal INTEGER NOT NULL CHECK(ordinal>=0),
  memory_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  PRIMARY KEY(slot_id,turn_id,committed_revision,ordinal),
  UNIQUE(slot_id,turn_id,committed_revision,memory_id),
  FOREIGN KEY(slot_id,memory_id,actor_id) REFERENCES actor_memory(slot_id,memory_id,actor_id),
  FOREIGN KEY(turn_id,committed_revision) REFERENCES turn_control(turn_id,committed_revision)
    DEFERRABLE INITIALLY DEFERRED
)"""
  const val CREATE_MANIFEST_IMMUTABLE = """
CREATE TRIGGER memory_manifest_no_update BEFORE UPDATE ON memory_manifest
  BEGIN SELECT RAISE(ABORT,'immutable_memory_manifest'); END;
CREATE TRIGGER memory_manifest_no_delete BEFORE DELETE ON memory_manifest
  BEGIN SELECT RAISE(ABORT,'immutable_memory_manifest'); END;
"""

  const val CREATE_INDEX = """
CREATE INDEX memory_by_actor ON actor_memory(slot_id, actor_id, committed_revision);
"""

  @JvmStatic fun createStatements(): List<String> {
    val triggers = CREATE_IMMUTABILITY_TRIGGERS.trim().split(Regex("(?<=END;)\\s*"))
      .map { it.trim() }.filter { it.isNotEmpty() }
    val manifestTriggers=CREATE_MANIFEST_IMMUTABLE.trim().split(Regex("(?<=END;)\\s*"))
      .map { it.trim() }.filter { it.isNotEmpty() }
    return listOf(CREATE_ACTOR_MEMORY,CREATE_MEMORY_MANIFEST) +
      triggers + manifestTriggers + listOf(CREATE_INDEX.trim())
  }
}
