package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Phase-4A fail-closed gate for planner transaction commits.
 *
 * <p>This class does not mutate state. It only decides whether a caller may enter the Phase-4D
 * commit protocol. The default build flag remains OFF; enabled builds must still pass every
 * turn/base/proposal verification before any authoritative write is attempted.
 */
public final class GmTransactionCommitGate {
  private GmTransactionCommitGate() {}

  public static JSONObject preflight(
      boolean featureEnabled,
      String expectedTurnId,
      String expectedBaseStateHash,
      JSONObject proposal) {
    JSONObject out = new JSONObject();
    try {
      if (!featureEnabled) {
        return out.put("allowed", false)
            .put("reason", "gm_transaction_commit_disabled");
      }

      String structural = GmTransactionContract.validateProposal(proposal);
      if (!structural.isEmpty()) {
        return out.put("allowed", false).put("reason", structural);
      }

      String turnId = proposal.optString("turnId", "");
      String baseStateHash = proposal.optString("baseStateHash", "");
      if (!safe(expectedTurnId).equals(turnId)) {
        return out.put("allowed", false).put("reason", "turn_id_mismatch");
      }
      if (!safe(expectedBaseStateHash).equals(baseStateHash)) {
        return out.put("allowed", false).put("reason", "base_state_hash_mismatch");
      }

      return out.put("allowed", true)
          .put("reason", "");
    } catch (Exception error) {
      try {
        return out.put("allowed", false).put("reason", "commit_gate_error");
      } catch (Exception ignored) {
        return new JSONObject();
      }
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
