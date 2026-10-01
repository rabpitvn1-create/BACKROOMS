package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmTransactionCommitGateTest {
  @Test public void disabledFlagAlwaysFailsClosed() throws Exception {
    JSONObject proposal = proposal();

    JSONObject gate = GmTransactionCommitGate.preflight(
        false, "turn-4", "base-hash", proposal);

    assertFalse(gate.getBoolean("allowed"));
    assertEquals("gm_transaction_commit_disabled", gate.getString("reason"));
  }

  @Test public void enabledFlagArmsOnlyAfterTurnAndBaseValidation() throws Exception {
    JSONObject proposal = proposal();

    JSONObject gate = GmTransactionCommitGate.preflight(
        true, "turn-4", "base-hash", proposal);

    assertTrue(gate.getBoolean("allowed"));
    assertEquals("", gate.getString("reason"));
  }

  @Test public void staleTurnAndStateFailBeforeArming() throws Exception {
    JSONObject proposal = proposal();

    JSONObject wrongTurn = GmTransactionCommitGate.preflight(
        true, "turn-other", "base-hash", proposal);
    JSONObject wrongState = GmTransactionCommitGate.preflight(
        true, "turn-4", "other-hash", proposal);

    assertFalse(wrongTurn.getBoolean("allowed"));
    assertEquals("turn_id_mismatch", wrongTurn.getString("reason"));
    assertFalse(wrongState.getBoolean("allowed"));
    assertEquals("base_state_hash_mismatch", wrongState.getString("reason"));
  }

  private static JSONObject proposal() throws Exception {
    JSONObject command = new JSONObject()
        .put("commandId", "c1")
        .put("type", "UPGRADE_STAT")
        .put("payload", new JSONObject()
            .put("characterId", "cao_minh")
            .put("stat", "STR"));
    JSONObject group = new JSONObject()
        .put("groupId", "g1")
        .put("atomic", true)
        .put("commands", new JSONArray().put(command));
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-4")
        .put("baseStateHash", "base-hash")
        .put("causalGroups", new JSONArray().put(group));
  }
}
