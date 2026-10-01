package com.rabpit.backroom.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase 4C selection/RNG authorization for GM commands that would create a world situation.
 *
 * <p>The GM never rolls or chooses eligibility. An authorization packet can only be issued from
 * the SituationCandidate that Core already selected for the prepared turn, plus the exact
 * CANDIDATE_SELECTION trace produced by that turn's TurnRng.
 */
public final class GmSelectionGate {
  public static final int SCHEMA_VERSION = 1;
  private static final String RNG_SCOPE = TurnRng.Scope.CANDIDATE_SELECTION.name();

  private GmSelectionGate() {}

  static JSONObject issue(
      String turnId,
      String baseStateHash,
      JSONObject selectedCandidate,
      JSONObject workingState,
      TurnRng rng) throws JSONException {
    JSONObject selected = selectedCandidate == null ? new JSONObject() : selectedCandidate;
    JSONObject trace = latestSelectionTrace(workingState);
    JSONObject packet = new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("turnId", safe(turnId))
        .put("baseStateHash", safe(baseStateHash))
        .put("selectedSituationKey", selected.optString("situationKey", ""))
        .put("selectedKind", selected.optString("kind", ""))
        .put("payloadKey", selected.optString("payloadKey", ""))
        .put("eligibilityRuleId", selected.optString("eligibilityRuleId", ""))
        .put("selectedNone", selected.optBoolean("selectedNone", false));

    if (trace == null) {
      return packet.put("valid", false)
          .put("reason", "selection_trace_missing")
          .put("authorizationHash", "");
    }
    if (!packet.getString("selectedSituationKey")
        .equals(trace.optString("selectedSituationKey", ""))) {
      return packet.put("valid", false)
          .put("reason", "selection_trace_candidate_mismatch")
          .put("authorizationHash", "");
    }
    if (packet.getBoolean("selectedNone") != trace.optBoolean("selectedNone", false)) {
      return packet.put("valid", false)
          .put("reason", "selection_trace_none_mismatch")
          .put("authorizationHash", "");
    }

    String selectionMode = trace.optString("selectionMode", "").trim();
    if ("MANDATORY".equals(selectionMode)) {
      packet.put("selectionMode", "MANDATORY")
          .put("rngScope", "")
          .put("rngDrawSeq", -1)
          .put("rngDrawsUsed", rng == null ? 0 : rng.drawsUsed(TurnRng.Scope.CANDIDATE_SELECTION))
          .put("rngDrawKey", "")
          .put("valid", true)
          .put("reason", "");
    } else {
      String traceScope = trace.optString("rngScope", "");
      int drawSeq = trace.optInt("rngDrawSeq", -1);
      if (!RNG_SCOPE.equals(traceScope) || drawSeq < 0 || rng == null) {
        return packet.put("valid", false)
            .put("reason", "selection_rng_evidence_missing")
            .put("authorizationHash", "");
      }
      int drawsUsed = rng.drawsUsed(TurnRng.Scope.CANDIDATE_SELECTION);
      if (drawsUsed != drawSeq + 1) {
        return packet.put("valid", false)
            .put("reason", "selection_rng_counter_mismatch")
            .put("authorizationHash", "");
      }
      packet.put("selectionMode", "WEIGHTED_RNG")
          .put("rngScope", traceScope)
          .put("rngDrawSeq", drawSeq)
          .put("rngDrawsUsed", drawsUsed)
          .put("rngDrawKey", rng.drawKey(TurnRng.Scope.CANDIDATE_SELECTION, drawSeq))
          .put("valid", true)
          .put("reason", "");
    }
    packet.put("authorizationHash", hashWithoutAuthorizationHash(packet));
    return packet;
  }

  public static JSONObject authorize(
      JSONObject authorization,
      String expectedTurnId,
      String expectedBaseStateHash,
      String commandType,
      JSONObject payload) throws JSONException {
    JSONObject output = new JSONObject().put("allowed", false);
    String packetReason = validationReason(
        authorization, expectedTurnId, expectedBaseStateHash);
    if (!packetReason.isEmpty()) return output.put("reason", packetReason);

    if (authorization.optBoolean("selectedNone", true)) {
      return output.put("reason", "selection_none");
    }

    String kind = authorization.optString("selectedKind", "");
    String payloadKey = authorization.optString("payloadKey", "");
    String situationKey = authorization.optString("selectedSituationKey", "");
    JSONObject body = payload == null ? new JSONObject() : payload;

    if ("START_ENTITY_ENCOUNTER".equals(commandType)) {
      String entityKey = body.optString("entityKey", "").trim();
      if (!"ENTITY".equals(kind) || entityKey.isEmpty()
          || !entityKey.equals(payloadKey) || !("entity:" + entityKey).equals(situationKey)) {
        return output.put("reason", "selection_candidate_mismatch");
      }
    } else if ("START_CHARACTER_ENCOUNTER".equals(commandType)) {
      String characterId = body.optString("characterId", "").trim();
      if (!"CHARACTER".equals(kind) || characterId.isEmpty()
          || !characterId.equals(payloadKey) || !("character:" + characterId).equals(situationKey)) {
        return output.put("reason", "selection_candidate_mismatch");
      }
    } else if ("DISCOVER_CHEST".equals(commandType)) {
      if (!"CHEST".equals(kind) || !situationKey.startsWith("resource:chest:")) {
        return output.put("reason", "selection_candidate_mismatch");
      }
    } else {
      return output.put("reason", "selection_gate_not_required");
    }

    return output.put("allowed", true)
        .put("reason", "")
        .put("selectionEvidence", evidence(authorization));
  }

  public static String validationReason(
      JSONObject authorization, String expectedTurnId, String expectedBaseStateHash) {
    if (authorization == null) return "selection_authorization_missing";
    if (!authorization.optBoolean("valid", false)) {
      String reason = authorization.optString("reason", "").trim();
      return reason.isEmpty() ? "selection_authorization_invalid" : reason;
    }
    if (authorization.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
      return "selection_authorization_schema_invalid";
    }
    if (!safe(expectedTurnId).equals(authorization.optString("turnId", ""))) {
      return "selection_turn_mismatch";
    }
    if (!safe(expectedBaseStateHash).equals(authorization.optString("baseStateHash", ""))) {
      return "selection_base_state_mismatch";
    }
    String selectionMode = authorization.optString("selectionMode", "WEIGHTED_RNG");
    int drawSeq = authorization.optInt("rngDrawSeq", -1);
    int drawsUsed = authorization.optInt("rngDrawsUsed", -1);
    if ("MANDATORY".equals(selectionMode)) {
      if (!authorization.optString("rngScope", "").isEmpty()
          || drawSeq != -1
          || drawsUsed < 0
          || !authorization.optString("rngDrawKey", "").isEmpty()) {
        return "selection_mandatory_evidence_invalid";
      }
    } else if ("WEIGHTED_RNG".equals(selectionMode)) {
      if (!RNG_SCOPE.equals(authorization.optString("rngScope", ""))) {
        return "selection_rng_scope_invalid";
      }
      if (drawSeq < 0 || drawsUsed != drawSeq + 1) {
        return "selection_rng_counter_mismatch";
      }
      if (authorization.optString("rngDrawKey", "").trim().isEmpty()) {
        return "selection_rng_draw_key_missing";
      }
    } else {
      return "selection_mode_invalid";
    }
    String expectedHash = authorization.optString("authorizationHash", "");
    if (expectedHash.isEmpty() || !expectedHash.equals(hashWithoutAuthorizationHash(authorization))) {
      return "selection_authorization_hash_mismatch";
    }
    return "";
  }

  public static JSONObject evidence(JSONObject authorization) throws JSONException {
    if (authorization == null) return new JSONObject();
    return new JSONObject()
        .put("schemaVersion", authorization.optInt("schemaVersion", -1))
        .put("turnId", authorization.optString("turnId", ""))
        .put("baseStateHash", authorization.optString("baseStateHash", ""))
        .put("selectedSituationKey", authorization.optString("selectedSituationKey", ""))
        .put("selectedKind", authorization.optString("selectedKind", ""))
        .put("payloadKey", authorization.optString("payloadKey", ""))
        .put("eligibilityRuleId", authorization.optString("eligibilityRuleId", ""))
        .put("selectedNone", authorization.optBoolean("selectedNone", false))
        .put("selectionMode", authorization.optString("selectionMode", "WEIGHTED_RNG"))
        .put("rngScope", authorization.optString("rngScope", ""))
        .put("rngDrawSeq", authorization.optInt("rngDrawSeq", -1))
        .put("rngDrawsUsed", authorization.optInt("rngDrawsUsed", -1))
        .put("rngDrawKey", authorization.optString("rngDrawKey", ""))
        .put("valid", authorization.optBoolean("valid", false))
        .put("reason", authorization.optString("reason", ""))
        .put("authorizationHash", authorization.optString("authorizationHash", ""));
  }

  private static JSONObject latestSelectionTrace(JSONObject state) {
    JSONObject root = state == null ? null : state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONArray traces = root == null ? null : root.optJSONArray("selectionTrace");
    if (traces == null || traces.length() == 0) return null;
    return traces.optJSONObject(traces.length() - 1);
  }

  private static String hashWithoutAuthorizationHash(JSONObject packet) {
    try {
      JSONObject copy = new JSONObject(packet == null ? "{}" : packet.toString());
      copy.remove("authorizationHash");
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(GmShadowPlanner.canonicalJson(copy).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
      return hex.toString();
    } catch (Exception error) {
      throw new IllegalStateException("selection_authorization_hash_failed", error);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
