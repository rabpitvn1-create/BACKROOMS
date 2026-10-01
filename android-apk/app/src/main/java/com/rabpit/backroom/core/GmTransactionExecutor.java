package com.rabpit.backroom.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase 4B.1 pure transaction executor.
 *
 * <p>Execution happens only against deep copies supplied by this class. It has no Context,
 * SharedPreferences, GameCoreFacade or persistence dependency. Accepted groups advance the private
 * working copy. A rejected group is discarded in full, including mutations made by commands that
 * were accepted earlier inside that same group.
 */
public final class GmTransactionExecutor {
  private final GmCommandAuthority authority;

  GmTransactionExecutor(GmCommandAuthority authority) {
    if (authority == null) throw new IllegalArgumentException("authority is required");
    this.authority = authority;
  }

  public JSONObject execute(
      JSONObject beforeState, JSONObject proposal, String expectedTurnId, String expectedBaseHash)
      throws JSONException {
    JSONObject output = new JSONObject();

    String structural = GmTransactionContract.validateProposal(proposal);
    if (!structural.isEmpty()) return rejectedDraft(output, structural);
    if (!safe(expectedTurnId).equals(proposal.optString("turnId", ""))) {
      return rejectedDraft(output, "turn_id_mismatch");
    }
    if (!safe(expectedBaseHash).equals(proposal.optString("baseStateHash", ""))) {
      return rejectedDraft(output, "base_state_hash_mismatch");
    }

    JSONObject immutableInputCopy = copy(beforeState);
    JSONObject working = copy(beforeState);
    JSONArray commandResults = new JSONArray();
    JSONArray groups = proposal.getJSONArray("causalGroups");
    int acceptedGroups = 0;
    int rejectedGroups = 0;

    for (int i = 0; i < groups.length(); i++) {
      JSONObject group = groups.getJSONObject(i);
      JSONObject groupState = copy(working);
      JSONArray commands = group.getJSONArray("commands");
      List<JSONObject> groupResults = new ArrayList<>();
      boolean groupAccepted = true;

      for (int c = 0; c < commands.length(); c++) {
        JSONObject command = commands.getJSONObject(c);
        JSONObject result = authority.evaluateCommand(groupState, command);
        groupResults.add(result);
        if (!result.optBoolean("accepted", false)) groupAccepted = false;
      }

      for (JSONObject result : groupResults) commandResults.put(result);
      if (groupAccepted) {
        working = groupState;
        acceptedGroups++;
      } else {
        rejectedGroups++;
      }
    }

    // Defensive invariant: adapters must never have been handed the caller's original object.
    if (!canonical(immutableInputCopy).equals(canonical(beforeState))) {
      throw new IllegalStateException("transaction_input_mutated");
    }

    JSONObject validation = new JSONObject()
        .put("turnId", proposal.getString("turnId"))
        .put("commandResults", commandResults);
    JSONObject resolved = GmTransactionContract.resolveValidatedTurn(proposal, validation);

    return output.put("valid", true)
        .put("reason", "")
        .put("turnId", proposal.getString("turnId"))
        .put("baseStateHash", proposal.getString("baseStateHash"))
        .put("commandResults", commandResults)
        .put("resolvedTurn", resolved)
        .put("acceptedGroups", acceptedGroups)
        .put("rejectedGroups", rejectedGroups)
        .put("simulatedBeforeHash", hash(beforeState))
        .put("simulatedAfterHash", hash(working))
        .put("afterState", copy(working));
  }

  private static JSONObject rejectedDraft(JSONObject output, String reason) throws JSONException {
    return output.put("valid", false)
        .put("reason", reason)
        .put("commandResults", new JSONArray())
        .put("resolvedTurn", new JSONObject())
        .put("acceptedGroups", 0)
        .put("rejectedGroups", 0)
        .put("simulatedBeforeHash", "")
        .put("simulatedAfterHash", "")
        .put("afterState", new JSONObject());
  }

  private static JSONObject copy(JSONObject source) {
    try {
      return new JSONObject(source == null ? "{}" : source.toString());
    } catch (Exception error) {
      throw new IllegalArgumentException("state_copy_failed", error);
    }
  }

  private static String canonical(JSONObject state) {
    return GmShadowPlanner.canonicalJson(state == null ? new JSONObject() : state);
  }

  private static String hash(JSONObject state) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical(state).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
      return hex.toString();
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
