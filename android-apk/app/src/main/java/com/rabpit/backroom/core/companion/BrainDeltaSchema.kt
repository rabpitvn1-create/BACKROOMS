package com.rabpit.backroom.core.companion

/** Fresh-slot immutable rule application ledger, reserved for native-proven deltas only. */
internal object BrainDeltaSchema {
  @JvmStatic fun createStatements(): List<String> = listOf(
    """
CREATE TABLE actor_brain_delta(
 slot_id TEXT NOT NULL,
 application_id TEXT NOT NULL,
 actor_id TEXT NOT NULL,
 created_turn_id TEXT NOT NULL,
 committed_revision INTEGER NOT NULL CHECK(committed_revision>0),
 rule_version TEXT NOT NULL CHECK(rule_version='rule_table.v1'),
 rule_id TEXT NOT NULL CHECK(rule_id IN ('BR01','BR02','GR01','GR02','GR03','RR01','MR01','MR02')),
 source_event_id TEXT NOT NULL,
 source_observation_id TEXT CHECK(rule_id NOT IN ('BR01','BR02') OR source_observation_id IS NOT NULL),
 target_kind TEXT NOT NULL CHECK(target_kind IN ('BELIEF','GOAL','APPRAISAL','MOOD')),
 target_id TEXT NOT NULL,
 delta_json TEXT NOT NULL CHECK(length(delta_json)<=8192),
 delta_digest TEXT NOT NULL CHECK(length(delta_digest)=64),
 PRIMARY KEY(slot_id,application_id),
 UNIQUE(slot_id,actor_id,rule_version,rule_id,source_event_id,target_kind,target_id),
 UNIQUE(slot_id,application_id,committed_revision,delta_digest),
 FOREIGN KEY(slot_id,actor_id) REFERENCES genesis_pins(slot_id,actor_id),
 FOREIGN KEY(source_event_id,created_turn_id,committed_revision)
  REFERENCES native_event(event_id,turn_id,revision),
 FOREIGN KEY(slot_id,source_observation_id,actor_id)
  REFERENCES actor_observation(slot_id,observation_id,actor_id),
 FOREIGN KEY(created_turn_id,committed_revision)
  REFERENCES turn_control(turn_id,committed_revision)
  DEFERRABLE INITIALLY DEFERRED
)""".trim(),
    """
CREATE TABLE brain_manifest(
 slot_id TEXT NOT NULL,
 turn_id TEXT NOT NULL,
 committed_revision INTEGER NOT NULL CHECK(committed_revision>0),
 ordinal INTEGER NOT NULL CHECK(ordinal>=0),
 application_id TEXT NOT NULL,
 delta_digest TEXT NOT NULL,
 PRIMARY KEY(slot_id,turn_id,committed_revision,ordinal),
 UNIQUE(slot_id,turn_id,committed_revision,application_id),
 FOREIGN KEY(slot_id,application_id,committed_revision,delta_digest)
  REFERENCES actor_brain_delta(slot_id,application_id,committed_revision,delta_digest),
 FOREIGN KEY(turn_id,committed_revision)
  REFERENCES turn_control(turn_id,committed_revision)
  DEFERRABLE INITIALLY DEFERRED
)""".trim(),
    "CREATE TRIGGER actor_brain_delta_no_update BEFORE UPDATE ON actor_brain_delta BEGIN SELECT RAISE(ABORT,'immutable_brain_delta'); END",
    "CREATE TRIGGER actor_brain_delta_no_delete BEFORE DELETE ON actor_brain_delta BEGIN SELECT RAISE(ABORT,'immutable_brain_delta'); END",
    "CREATE TRIGGER brain_manifest_no_update BEFORE UPDATE ON brain_manifest BEGIN SELECT RAISE(ABORT,'immutable_brain_manifest'); END",
    "CREATE TRIGGER brain_manifest_no_delete BEFORE DELETE ON brain_manifest BEGIN SELECT RAISE(ABORT,'immutable_brain_manifest'); END",
    "CREATE INDEX brain_application_by_actor ON actor_brain_delta(slot_id,actor_id,committed_revision)"
  )
}
