package com.rabpit.backroom.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase 4B.2 commit-candidate builder.
 *
 * <p>This class has no persistence dependency. It converts a valid Phase-4B.1 execution draft into
 * a replay-verified candidate containing only the deterministic state delta and evidence ledger.
 * The full afterState remains an execution detail and is deliberately not embedded in the candidate.
 */
public final class GmCommitCandidateBuilder {
  public static final int SCHEMA_VERSION = 1;

  private GmCommitCandidateBuilder() {}

  public static JSONObject build(JSONObject beforeState, JSONObject proposal, JSONObject executionDraft)
      throws JSONException {
    JSONObject rejected = new JSONObject().put("valid", false);

    if (beforeState == null) return reject(rejected, "before_state_missing");
    String structural = GmTransactionContract.validateProposal(proposal);
    if (!structural.isEmpty()) return reject(rejected, structural);
    if (executionDraft == null || !executionDraft.optBoolean("valid", false)) {
      return reject(rejected, "execution_draft_invalid");
    }

    String turnId = proposal.optString("turnId", "");
    String baseStateHash = proposal.optString("baseStateHash", "");
    if (!turnId.equals(executionDraft.optString("turnId", ""))) {
      return reject(rejected, "execution_turn_mismatch");
    }
    if (!baseStateHash.equals(executionDraft.optString("baseStateHash", ""))) {
      return reject(rejected, "execution_base_state_mismatch");
    }

    JSONObject afterState = executionDraft.optJSONObject("afterState");
    if (afterState == null) return reject(rejected, "execution_after_state_missing");

    String beforeHash = hash(beforeState);
    String afterHash = hash(afterState);
    if (!beforeHash.equals(executionDraft.optString("simulatedBeforeHash", ""))) {
      return reject(rejected, "execution_before_hash_mismatch");
    }
    if (!afterHash.equals(executionDraft.optString("simulatedAfterHash", ""))) {
      return reject(rejected, "execution_after_hash_mismatch");
    }

    JSONObject validation = new JSONObject()
        .put("turnId", turnId)
        .put("commandResults", copyArray(executionDraft.optJSONArray("commandResults")));
    JSONObject resolvedExpected = GmTransactionContract.resolveValidatedTurn(proposal, validation);
    JSONObject resolvedActual = executionDraft.optJSONObject("resolvedTurn");
    if (resolvedActual == null
        || !canonical(resolvedExpected).equals(canonical(resolvedActual))) {
      return reject(rejected, "execution_ledger_mismatch");
    }

    JSONObject stateDelta;
    JSONObject replayed;
    try {
      stateDelta = AuthoritativeStatePatch.diff(beforeState, afterState);
      replayed = AuthoritativeStatePatch.apply(beforeState, stateDelta);
    } catch (Exception error) {
      return reject(rejected, "state_delta_error");
    }

    // Stronger than the legacy commit check: excluded/derived roots must also remain identical.
    // If an adapter changed one, the top-level authoritative patch cannot replay it and 4B.2 fails.
    if (!canonical(replayed).equals(canonical(afterState))) {
      return reject(rejected, "replay_state_mismatch");
    }
    if (!afterHash.equals(hash(replayed))) {
      return reject(rejected, "replay_hash_mismatch");
    }

    JSONArray committedGroups = new JSONArray();
    JSONArray rejectedGroups = new JSONArray();
    JSONArray groupLedger = resolvedExpected.getJSONArray("groups");
    for (int i = 0; i < groupLedger.length(); i++) {
      JSONObject group = groupLedger.getJSONObject(i);
      JSONObject copy = new JSONObject(group.toString());
      if ("ACCEPTED".equals(group.optString("status", ""))) committedGroups.put(copy);
      else rejectedGroups.put(copy);
    }

    JSONArray committedEvents = copyArray(resolvedExpected.optJSONArray("committedEvents"));
    String proposalFingerprint = hashCanonical(proposal);

    JSONObject candidate = new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("turnId", turnId)
        .put("baseStateHash", baseStateHash)
        .put("beforeStateHash", beforeHash)
        .put("afterStateHash", afterHash)
        .put("proposalFingerprint", proposalFingerprint)
        .put("stateDelta", new JSONObject(stateDelta.toString()))
        .put("committedGroups", committedGroups)
        .put("rejectedGroups", rejectedGroups)
        .put("committedEvents", committedEvents)
        .put("replayVerified", true);

    String transactionHash = hashCanonical(candidate);
    candidate.put("transactionHash", transactionHash);

    return new JSONObject()
        .put("valid", true)
        .put("reason", "")
        .put("candidate", candidate);
  }

  public static boolean verify(
      JSONObject beforeState, JSONObject proposal, JSONObject candidateEnvelope) {
    try {
      if (candidateEnvelope == null || !candidateEnvelope.optBoolean("valid", false)) return false;
      JSONObject candidate = candidateEnvelope.optJSONObject("candidate");
      if (candidate == null) return false;
      if (candidate.optInt("schemaVersion", -1) != SCHEMA_VERSION) return false;
      if (!proposal.optString("turnId", "").equals(candidate.optString("turnId", ""))) return false;
      if (!proposal.optString("baseStateHash", "").equals(candidate.optString("baseStateHash", ""))) {
        return false;
      }
      if (!hash(beforeState).equals(candidate.optString("beforeStateHash", ""))) return false;
      if (!hashCanonical(proposal).equals(candidate.optString("proposalFingerprint", ""))) return false;

      JSONObject stateDelta = candidate.optJSONObject("stateDelta");
      if (stateDelta == null) return false;
      JSONObject replayed = AuthoritativeStatePatch.apply(beforeState, stateDelta);
      if (!hash(replayed).equals(candidate.optString("afterStateHash", ""))) return false;

      JSONObject candidateForHash = new JSONObject(candidate.toString());
      String expectedHash = candidateForHash.optString("transactionHash", "");
      candidateForHash.remove("transactionHash");
      return expectedHash.equals(hashCanonical(candidateForHash));
    } catch (Exception error) {
      return false;
    }
  }

  private static JSONObject reject(JSONObject output, String reason) throws JSONException {
    return output.put("reason", reason == null ? "commit_candidate_rejected" : reason)
        .put("candidate", new JSONObject());
  }

  private static JSONArray copyArray(JSONArray array) throws JSONException {
    return array == null ? new JSONArray() : new JSONArray(array.toString());
  }

  private static String canonical(Object value) {
    return GmShadowPlanner.canonicalJson(value);
  }

  private static String hash(JSONObject state) {
    return hashText(canonical(state == null ? new JSONObject() : state));
  }

  private static String hashCanonical(Object value) {
    return hashText(canonical(value));
  }

  private static String hashText(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
      return hex.toString();
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
