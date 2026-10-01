package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase-4D pure resolver. It combines the already-prepared deterministic turn with a verified GM
 * transaction, but never persists state and never mutates the retained PreparedTurn objects.
 */
final class GmAuthoritativeTurnResolver {
  private final GmCommandAuthority authority;
  private final EmergentTurnEngine engine;

  GmAuthoritativeTurnResolver(GmCommandAuthority authority, EmergentTurnEngine engine) {
    if (authority == null || engine == null) throw new IllegalArgumentException("resolver_dependency_missing");
    this.authority = authority;
    this.engine = engine;
  }

  JSONObject resolve(
      JSONObject executionBase,
      JSONArray preparedEvents,
      JSONObject selectedCandidate,
      JSONObject selectionAuthorization,
      JSONObject proposal,
      String turnId,
      String baseStateHash) throws JSONException {
    JSONObject out = new JSONObject().put("valid", false);
    try {
      JSONObject base = copy(executionBase);
      JSONArray events = preparedEvents == null
          ? new JSONArray() : new JSONArray(preparedEvents.toString());
      JSONObject draft = new GmTransactionExecutor(authority).execute(
          base, proposal, turnId, baseStateHash, selectionAuthorization);
      if (!draft.optBoolean("valid", false)) {
        return reject(out, draft.optString("reason", "transaction_execution_invalid"));
      }

      JSONObject envelope = GmCommitCandidateBuilder.build(base, proposal, draft);
      if (!envelope.optBoolean("valid", false)) {
        return reject(out, envelope.optString("reason", "commit_candidate_invalid"));
      }
      if (!GmCommitCandidateBuilder.verify(base, proposal, envelope)) {
        return reject(out, "commit_candidate_verify_failed");
      }

      JSONObject candidate = envelope.getJSONObject("candidate");
      String selectionReason = selectedSituationReason(selectedCandidate, candidate);
      if (!selectionReason.isEmpty()) return reject(out, selectionReason);

      JSONObject finalState = AuthoritativeStatePatch.apply(
          base, candidate.getJSONObject("stateDelta"));
      if (!GmShadowPlanner.canonicalJson(finalState).equals(
          GmShadowPlanner.canonicalJson(draft.getJSONObject("afterState")))) {
        return reject(out, "resolved_state_replay_mismatch");
      }

      GmCommittedEventAdapter.append(
          engine, turnId, events, candidate.getJSONArray("committedEvents"));
      engine.appendThreadResolutionEvents(
          finalState, events, turnId, Math.max(1, finalState.optInt("turn", 1)));
      engine.appendDormancyEvents(
          finalState, events, turnId, Math.max(1, finalState.optInt("turn", 1)));
      if (events.length() > 64) return reject(out, "domain_event_batch_too_large");
      engine.validateBatch(turnId, events);

      return out.put("valid", true)
          .put("reason", "")
          .put("finalState", finalState)
          .put("events", events)
          .put("candidateEnvelope", envelope);
    } catch (Exception error) {
      return reject(out, stableReason(error));
    }
  }

  private static String selectedSituationReason(JSONObject selected, JSONObject candidate) {
    JSONObject source = selected == null ? new JSONObject() : selected;
    boolean none = source.optBoolean("selectedNone", false)
        || "NONE".equals(source.optString("situationKey", ""));
    JSONObject evidence = candidate == null ? null : candidate.optJSONObject("selectionEvidence");
    boolean hasEvidence = evidence != null && evidence.length() > 0;

    if (none) return hasEvidence ? "selection_evidence_unexpected_for_none" : "";

    String kind = source.optString("kind", "");
    if (!("ENTITY".equals(kind) || "CHEST".equals(kind) || "CHARACTER".equals(kind))) {
      return "selected_situation_kind_unsupported";
    }
    if (!hasEvidence) return "selected_situation_not_committed";
    if (!source.optString("situationKey", "")
        .equals(evidence.optString("selectedSituationKey", ""))) {
      return "selected_situation_evidence_mismatch";
    }
    return "";
  }

  private static JSONObject reject(JSONObject out, String reason) throws JSONException {
    return out.put("reason", reason == null ? "gm_authoritative_resolve_failed" : reason)
        .put("finalState", new JSONObject())
        .put("events", new JSONArray())
        .put("candidateEnvelope", new JSONObject());
  }

  private static JSONObject copy(JSONObject source) {
    try {
      return new JSONObject(source == null ? "{}" : source.toString());
    } catch (Exception error) {
      throw new IllegalArgumentException("state_copy_failed", error);
    }
  }

  private static String stableReason(Exception error) {
    String message = error == null ? "" : String.valueOf(error.getMessage()).trim();
    if (message.startsWith("gm_") || message.startsWith("selection_")
        || message.startsWith("commit_") || message.startsWith("transaction_")
        || message.startsWith("domain_") || message.startsWith("selected_")
        || message.startsWith("resolved_")) {
      return message;
    }
    return "gm_authoritative_resolve_failed:" +
        (error == null ? "Unknown" : error.getClass().getSimpleName());
  }
}
