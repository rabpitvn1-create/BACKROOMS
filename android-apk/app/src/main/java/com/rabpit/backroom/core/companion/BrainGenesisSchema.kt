package com.rabpit.backroom.core.companion

/** Immutable per-actor, per-slot canon pins and neutral initial brain for fresh v4 slots. */
internal object BrainGenesisSchema {
  const val SCHEMA_VERSION = 1
  const val CREATE_GENESIS_PINS = """
CREATE TABLE genesis_pins(
  slot_id TEXT NOT NULL,
  actor_id TEXT NOT NULL CHECK(actor_id NOT IN (char(107,97,105),'KAI')),
  knowledge_namespace TEXT NOT NULL,
  persona_source_path TEXT NOT NULL,
  persona_revision TEXT NOT NULL,
  persona_sha256 TEXT NOT NULL CHECK(length(persona_sha256) = 64),
  rule_version TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  policy_version TEXT NOT NULL,
  PRIMARY KEY(slot_id,actor_id),
  FOREIGN KEY(slot_id) REFERENCES slot_meta(slot_id)
)"""
  const val CREATE_INITIAL_BRAIN = """
CREATE TABLE initial_brain(
  slot_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  state_json TEXT NOT NULL CHECK(length(state_json) <= 4096),
  state_digest TEXT NOT NULL CHECK(length(state_digest) = 64),
  PRIMARY KEY(slot_id,actor_id),
  FOREIGN KEY(slot_id,actor_id) REFERENCES genesis_pins(slot_id,actor_id)
)"""
  @JvmStatic fun createStatements(): List<String> = listOf(
    CREATE_GENESIS_PINS,CREATE_INITIAL_BRAIN,
    "CREATE TRIGGER genesis_pins_no_update BEFORE UPDATE ON genesis_pins BEGIN SELECT RAISE(ABORT,'immutable_genesis'); END",
    "CREATE TRIGGER genesis_pins_no_delete BEFORE DELETE ON genesis_pins BEGIN SELECT RAISE(ABORT,'immutable_genesis'); END",
    "CREATE TRIGGER initial_brain_no_update BEFORE UPDATE ON initial_brain BEGIN SELECT RAISE(ABORT,'immutable_brain'); END",
    "CREATE TRIGGER initial_brain_no_delete BEFORE DELETE ON initial_brain BEGIN SELECT RAISE(ABORT,'immutable_brain'); END"
  )
}
