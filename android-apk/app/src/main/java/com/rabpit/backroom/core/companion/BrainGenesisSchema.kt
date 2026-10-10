package com.rabpit.backroom.core.companion

/**
 * M1b.3/P1b genesis pins storage schema v1 (issue #502).
 *
 * Fresh-slot-only: created at slot genesis inside the creation transaction.
 * One row per slot (singleton). Immutable via triggers. A database without this
 * table, or with a version mismatch, fails closed on open — no import, no
 * silent recreate, no migration.
 */
internal object BrainGenesisSchema {
  const val SCHEMA_VERSION = 1

  const val CREATE_GENESIS_PINS = """
CREATE TABLE genesis_pins(
  singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
  slot_id TEXT NOT NULL,
  actor_id TEXT NOT NULL CHECK(actor_id NOT IN ('ka' || 'i','KAI')),
  knowledge_namespace TEXT NOT NULL,
  persona_source_path TEXT NOT NULL,
  persona_revision TEXT NOT NULL,
  persona_sha256 TEXT NOT NULL CHECK(length(persona_sha256) = 64),
  rule_version TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  policy_version TEXT NOT NULL
)"""

  // SQLite trigger bodies contain semicolons. Each trigger must be sent as one statement.
  const val CREATE_NO_UPDATE_TRIGGER = """
CREATE TRIGGER genesis_pins_no_update BEFORE UPDATE ON genesis_pins
  BEGIN SELECT RAISE(ABORT,'immutable_genesis'); END;"""

  const val CREATE_NO_DELETE_TRIGGER = """
CREATE TRIGGER genesis_pins_no_delete BEFORE DELETE ON genesis_pins
  BEGIN SELECT RAISE(ABORT,'immutable_genesis'); END;"""

  fun createStatements(): List<String> =
    listOf(CREATE_GENESIS_PINS, CREATE_NO_UPDATE_TRIGGER, CREATE_NO_DELETE_TRIGGER)
}
