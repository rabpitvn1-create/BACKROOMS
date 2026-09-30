package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Phase-4A fail-closed gate for planner transaction commits.
 *
 * <p>This class does not mutate state. It only decides whether a caller is even allowed to enter
 * the future commit protocol. The default build flag is OFF, and Phase 4A deliberately refuses
 * to arm a real commit even when the flag is ON.
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

      // Phase 4A intentionally stops here. A future sub-phase must explicitly replace this
      // sentinel only after atomic execution + persistence + immutable ledger tests are green.
      return out.put("allowed", false)
          .put("reason", "phase4_commit_not_armed");
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
