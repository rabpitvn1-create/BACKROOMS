package com.rabpit.backroom.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phase-1 contract for the transactional GM rollout.
 *
 * <p>This class is intentionally not wired into the live turn path yet. It freezes the envelope,
 * atomic causal-group semantics, validation ledger, and committed-event evidence that later phases
 * must preserve.
 */
public final class GmTransactionContract {
  public static final int SCHEMA_VERSION = 1;

  private GmTransactionContract() {}

  /**
   * Returns an empty string when the proposal envelope is valid; otherwise a stable rejection
   * reason suitable for tests and logs.
   */
  public static String validateProposal(JSONObject proposal) {
    if (proposal == null) return "proposal_missing";
    if (proposal.optInt("schemaVersion", -1) != SCHEMA_VERSION) return "schema_version_invalid";

    String turnId = proposal.optString("turnId", "").trim();
    if (turnId.isEmpty()) return "turn_id_missing";
    String baseStateHash = proposal.optString("baseStateHash", "").trim();
    if (baseStateHash.isEmpty()) return "base_state_hash_missing";

    JSONArray groups = proposal.optJSONArray("causalGroups");
    if (groups == null) return "causal_groups_missing";

    Set<String> groupIds = new LinkedHashSet<>();
    Set<String> commandIds = new LinkedHashSet<>();
    for (int i = 0; i < groups.length(); i++) {
      JSONObject group = groups.optJSONObject(i);
      if (group == null) return "causal_group_invalid";

      String groupId = group.optString("groupId", "").trim();
      if (groupId.isEmpty()) return "group_id_missing";
      if (!groupIds.add(groupId)) return "group_id_duplicate";
      if (!group.has("atomic") || !group.optBoolean("atomic", false)) {
        return "causal_group_must_be_atomic";
      }

      JSONArray commands = group.optJSONArray("commands");
      if (commands == null || commands.length() == 0) return "commands_missing";
      for (int c = 0; c < commands.length(); c++) {
        JSONObject command = commands.optJSONObject(c);
        if (command == null) return "command_invalid";

        String commandId = command.optString("commandId", "").trim();
        if (commandId.isEmpty()) return "command_id_missing";
        if (!commandIds.add(commandId)) return "command_id_duplicate";

        String type = command.optString("type", "").trim();
        if (type.isEmpty()) return "command_type_missing";
        if (command.optJSONObject("payload") == null) return "command_payload_missing";
      }
    }
    return "";
  }

  /**
   * Resolves a planner proposal against Core validation results without mutating GameState.
   *
   * <p>Every causal group is atomic. One rejected command rejects the whole group. Accepted
   * commands must carry a Core-produced event; only those events become committed evidence.
   */
  public static JSONObject resolveValidatedTurn(JSONObject proposal, JSONObject validation) {
    String proposalError = validateProposal(proposal);
    if (!proposalError.isEmpty()) {
      throw new IllegalArgumentException(proposalError);
    }
    if (validation == null) throw new IllegalArgumentException("validation_missing");

    String turnId = proposal.optString("turnId", "");
    if (!turnId.equals(validation.optString("turnId", ""))) {
      throw new IllegalArgumentException("validation_turn_mismatch");
    }

    JSONArray rawResults = validation.optJSONArray("commandResults");
    if (rawResults == null) throw new IllegalArgumentException("command_results_missing");

    Map<String, JSONObject> byCommandId = new LinkedHashMap<>();
    for (int i = 0; i < rawResults.length(); i++) {
      JSONObject result = rawResults.optJSONObject(i);
      if (result == null) throw new IllegalArgumentException("command_result_invalid");
      String commandId = result.optString("commandId", "").trim();
      if (commandId.isEmpty()) throw new IllegalArgumentException("result_command_id_missing");
      if (byCommandId.put(commandId, result) != null) {
        throw new IllegalArgumentException("result_command_id_duplicate");
      }
    }

    JSONArray committedEvents = new JSONArray();
    JSONArray groupLedger = new JSONArray();
    JSONArray groups = proposal.getJSONArray("causalGroups");

    for (int i = 0; i < groups.length(); i++) {
      JSONObject group = groups.getJSONObject(i);
      JSONArray commands = group.getJSONArray("commands");
      JSONArray commandLedger = new JSONArray();
      boolean groupAccepted = true;

      for (int c = 0; c < commands.length(); c++) {
        JSONObject command = commands.getJSONObject(c);
        String commandId = command.getString("commandId");
        JSONObject result = byCommandId.get(commandId);
        if (result == null) throw new IllegalArgumentException("command_result_missing:" + commandId);

        boolean accepted = result.optBoolean("accepted", false);
        if (!accepted) groupAccepted = false;
        commandLedger.put(new JSONObject()
            .put("commandId", commandId)
            .put("type", command.getString("type"))
            .put("accepted", accepted)
            .put("reason", result.optString("reason", "")));
      }

      if (groupAccepted) {
        for (int c = 0; c < commands.length(); c++) {
          String commandId = commands.getJSONObject(c).getString("commandId");
          JSONObject result = byCommandId.get(commandId);
          JSONObject event = result.optJSONObject("event");
          if (event == null) {
            throw new IllegalArgumentException("accepted_command_event_missing:" + commandId);
          }
          committedEvents.put(new JSONObject(event.toString()));
        }
      }

      groupLedger.put(new JSONObject()
          .put("groupId", group.getString("groupId"))
          .put("status", groupAccepted ? "ACCEPTED" : "REJECTED")
          .put("commandResults", commandLedger));
    }

    return new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("turnId", turnId)
        .put("baseStateHash", proposal.getString("baseStateHash"))
        .put("groups", groupLedger)
        .put("committedEvents", committedEvents);
  }

  /** Returns true only when the committed ledger contains evidence for the requested state claim. */
  public static boolean supportsClaim(
      JSONObject committedTurn, String eventType, String subjectKey) {
    if (committedTurn == null || eventType == null || eventType.isEmpty()) return false;
    JSONArray events = committedTurn.optJSONArray("committedEvents");
    if (events == null) return false;

    String expectedSubject = subjectKey == null ? "" : subjectKey;
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (event == null) continue;
      if (!eventType.equals(event.optString("eventType", ""))) continue;
      if (expectedSubject.equals(event.optString("subjectKey", ""))) return true;
    }
    return false;
  }
}
