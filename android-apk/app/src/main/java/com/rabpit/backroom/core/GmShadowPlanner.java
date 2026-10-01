package com.rabpit.backroom.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase-2 shadow planner contract.
 *
 * <p>The planner may propose future changes, but this class never mutates GameState and never
 * commits a proposal. It only builds a bounded prompt, validates the transaction envelope, and
 * produces comparison telemetry against the V2 authoritative result.
 */
public final class GmShadowPlanner {
  public static final int MAX_GROUPS = 8;
  public static final int MAX_COMMANDS = 32;
  public static final int MAX_PROPOSAL_CHARS = 12_000;
  public static final int MAX_PROMPT_CHARS = 32_000;

  private static final Set<String> FORBIDDEN_COMMAND_KEYS = Set.of(
      "state", "stateJson", "statePatch", "rawState", "patch", "jsonPatch", "jsonPointer", "path");
  private static final Set<String> PROPOSAL_KEYS = Set.of(
      "schemaVersion", "turnId", "baseStateHash", "causalGroups");
  private static final Set<String> GROUP_KEYS = Set.of("groupId", "atomic", "commands");
  private static final Set<String> COMMAND_KEYS = Set.of("commandId", "type", "payload");

  private GmShadowPlanner() {}

  public static String buildPrompt(
      JSONObject plannerContext,
      JSONObject knowledgeView,
      String levelContext,
      String entityContext,
      String itemContext,
      String characterContext,
      String commandRegistryContext,
      String canonContext) throws JSONException {
    if (plannerContext == null) throw new IllegalArgumentException("planner_context_missing");
    String turnId = plannerContext.optString("turnId", "").trim();
    String baseStateHash = plannerContext.optString("baseStateHash", "").trim();
    String action = plannerContext.optString("action", "").trim();
    if (turnId.isEmpty()) throw new IllegalArgumentException("turn_id_missing");
    if (baseStateHash.isEmpty()) throw new IllegalArgumentException("base_state_hash_missing");
    if (action.isEmpty()) throw new IllegalArgumentException("action_missing");

    StringBuilder out = new StringBuilder();
    out.append("GM TRANSACTION PLANNER — PHASE 4D.\n")
        .append(KnowledgeContinuityFirewall.promptContext()).append("\n")
        .append("Bạn chỉ lập kế hoạch giao dịch cho một lượt. Proposal không được phép sửa GameState trực tiếp, ")
        .append("không được quyết định canon và không được viết narration cuối; Core mới là nơi ACCEPT/REJECT và commit.\n")
        .append("Mọi thay đổi phải nằm trong causalGroups atomic và commands có type + payload. ")
        .append("Không dùng raw JSON patch, path, jsonPointer, statePatch hoặc chép lại GameState như một mutation.\n")
        .append("Nếu selectionAuthorization.valid=true và selectedNone=false, phải đề xuất đúng MỘT command ")
        .append("START_ENTITY_ENCOUNTER / START_CHARACTER_ENCOUNTER / DISCOVER_CHEST khớp chính xác ")
        .append("selectedKind + payloadKey; không được đổi candidate. Nếu selectedNone=true, không đề xuất các command selection-gated.\n")
        .append("Ngoài selection bắt buộc nói trên, nếu không có thay đổi hợp lý thì có thể trả causalGroups rỗng. ")
        .append("Không lấp OPEN/UNKNOWN. Không biến writer knowledge thành character knowledge.\n")
        .append("OUTPUT CHỈ JSON đúng schema:\n")
        .append("{\"schemaVersion\":1,\"turnId\":\"").append(escape(turnId))
        .append("\",\"baseStateHash\":\"").append(escape(baseStateHash))
        .append("\",\"causalGroups\":[{\"groupId\":\"g1\",\"atomic\":true,")
        .append("\"commands\":[{\"commandId\":\"c1\",\"type\":\"UPPER_SNAKE_CASE\",")
        .append("\"payload\":{}}]}]}\n");

    appendSection(out, "TURN_CONTEXT", canonicalJson(plannerContext), 6500);
    appendSection(out, "KNOWLEDGE_VIEW", canonicalJson(
        knowledgeView == null ? new JSONObject() : knowledgeView), 6000);
    appendSection(out, "LEVEL_CORE", safe(levelContext), 3200);
    appendSection(out, "ENTITY_CORE", safe(entityContext), 2400);
    appendSection(out, "ITEM_CORE", safe(itemContext), 2400);
    appendSection(out, "CHARACTER_CORE", safe(characterContext), 3200);
    appendSection(out, "TYPED_COMMAND_REGISTRY", safe(commandRegistryContext), 2600);
    appendSection(out, "CANON", safe(canonContext), 6500);

    if (out.length() > MAX_PROMPT_CHARS) {
      throw new IllegalArgumentException("planner_prompt_budget_exceeded:" + out.length());
    }
    return out.toString();
  }

  public static JSONObject validateProposal(JSONObject raw, String expectedTurnId, String expectedBaseHash)
      throws JSONException {
    JSONObject result = new JSONObject();
    if (raw == null) return rejected(result, "proposal_missing");

    String serialized = raw.toString();
    if (serialized.length() > MAX_PROPOSAL_CHARS) {
      return rejected(result, "proposal_too_large");
    }
    if (!hasOnlyKeys(raw, PROPOSAL_KEYS)) return rejected(result, "proposal_unknown_field");

    String contractReason = GmTransactionContract.validateProposal(raw);
    if (!contractReason.isEmpty()) return rejected(result, contractReason);
    if (!safe(expectedTurnId).equals(raw.optString("turnId", ""))) {
      return rejected(result, "turn_id_mismatch");
    }
    if (!safe(expectedBaseHash).equals(raw.optString("baseStateHash", ""))) {
      return rejected(result, "base_state_hash_mismatch");
    }

    JSONArray groups = raw.getJSONArray("causalGroups");
    if (groups.length() > MAX_GROUPS) return rejected(result, "too_many_causal_groups");
    int commandCount = 0;
    for (int i = 0; i < groups.length(); i++) {
      JSONObject group = groups.getJSONObject(i);
      if (!hasOnlyKeys(group, GROUP_KEYS)) return rejected(result, "causal_group_unknown_field");
      JSONArray commands = group.getJSONArray("commands");
      commandCount += commands.length();
      if (commandCount > MAX_COMMANDS) return rejected(result, "too_many_commands");
      for (int c = 0; c < commands.length(); c++) {
        JSONObject command = commands.getJSONObject(c);
        if (!hasOnlyKeys(command, COMMAND_KEYS)) return rejected(result, "command_unknown_field");
        String type = command.getString("type");
        if (!type.matches("[A-Z][A-Z0-9_]{1,63}")) return rejected(result, "command_type_invalid");
        for (String key : FORBIDDEN_COMMAND_KEYS) {
          if (command.has(key)) return rejected(result, "raw_state_mutation_forbidden:" + key);
        }
        JSONObject payload = command.getJSONObject("payload");
        if (containsForbiddenMutationKey(payload)) {
          return rejected(result, "raw_state_mutation_forbidden:payload");
        }
        if (payload.toString().length() > 2400) return rejected(result, "command_payload_too_large");
      }
    }

    JSONObject proposal = new JSONObject(raw.toString());
    result.put("valid", true)
        .put("reason", "")
        .put("proposal", proposal)
        .put("proposalFingerprint", sha256(canonicalJson(proposal)))
        .put("commandCount", commandCount);
    return result;
  }

  /**
   * Stable key used to reuse an accepted shadow proposal for the same turn context instead of
   * asking a stochastic provider to plan the same transaction twice.
   */
  public static String plannerKey(JSONObject plannerContext, String prompt) {
    return sha256(canonicalJson(plannerContext == null ? new JSONObject() : plannerContext)
        + "\n" + safe(prompt));
  }

  /**
   * Diagnostic-only comparison. It intentionally does not decide semantic parity; Phase 3 owns
   * command-to-Core semantics.
   */
  public static JSONObject compareToV2(
      JSONObject proposal, JSONObject beforeState, JSONObject afterState, JSONObject selectedCandidate)
      throws JSONException {
    JSONObject before = beforeState == null ? new JSONObject() : beforeState;
    JSONObject after = afterState == null ? new JSONObject() : afterState;

    JSONArray proposedTypes = new JSONArray();
    if (proposal != null) {
      JSONArray groups = proposal.optJSONArray("causalGroups");
      if (groups != null) for (int i = 0; i < groups.length(); i++) {
        JSONObject group = groups.optJSONObject(i);
        JSONArray commands = group == null ? null : group.optJSONArray("commands");
        if (commands == null) continue;
        for (int c = 0; c < commands.length(); c++) {
          JSONObject command = commands.optJSONObject(c);
          if (command != null) proposedTypes.put(command.optString("type", ""));
        }
      }
    }

    JSONArray changedKeys = new JSONArray();
    Set<String> keys = new LinkedHashSet<>();
    Iterator<String> beforeKeys = before.keys();
    while (beforeKeys.hasNext()) keys.add(beforeKeys.next());
    Iterator<String> afterKeys = after.keys();
    while (afterKeys.hasNext()) keys.add(afterKeys.next());
    List<String> ordered = new ArrayList<>(keys);
    Collections.sort(ordered);
    for (String key : ordered) {
      if ("log".equals(key)) continue;
      Object a = before.opt(key);
      Object b = after.opt(key);
      if (!canonicalJson(a).equals(canonicalJson(b))) changedKeys.put(key);
    }

    return new JSONObject()
        .put("proposalCommandTypes", proposedTypes)
        .put("v2ChangedTopLevelKeys", changedKeys)
        .put("v2SelectedSituation", selectedCandidate == null
            ? "" : selectedCandidate.optString("situationKey", ""))
        .put("beforeFingerprint", sha256(canonicalJson(before)))
        .put("afterFingerprint", sha256(canonicalJson(after)));
  }

  public static String canonicalJson(Object value) {
    if (value == null || value == JSONObject.NULL) return "null";
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject)value;
      List<String> keys = new ArrayList<>();
      Iterator<String> iterator = object.keys();
      while (iterator.hasNext()) keys.add(iterator.next());
      Collections.sort(keys);
      StringBuilder out = new StringBuilder("{");
      for (int i = 0; i < keys.size(); i++) {
        if (i > 0) out.append(',');
        String key = keys.get(i);
        out.append(JSONObject.quote(key)).append(':').append(canonicalJson(object.opt(key)));
      }
      return out.append('}').toString();
    }
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray)value;
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < array.length(); i++) {
        if (i > 0) out.append(',');
        out.append(canonicalJson(array.opt(i)));
      }
      return out.append(']').toString();
    }
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    return JSONObject.quote(String.valueOf(value));
  }

  private static boolean hasOnlyKeys(JSONObject object, Set<String> allowed) {
    Iterator<String> keys = object.keys();
    while (keys.hasNext()) if (!allowed.contains(keys.next())) return false;
    return true;
  }

  private static boolean containsForbiddenMutationKey(Object value) {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject)value;
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (FORBIDDEN_COMMAND_KEYS.contains(key)) return true;
        if (containsForbiddenMutationKey(object.opt(key))) return true;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray)value;
      for (int i = 0; i < array.length(); i++) {
        if (containsForbiddenMutationKey(array.opt(i))) return true;
      }
    }
    return false;
  }

  private static JSONObject rejected(JSONObject result, String reason) throws JSONException {
    return result.put("valid", false)
        .put("reason", reason)
        .put("proposal", new JSONObject())
        .put("proposalFingerprint", "")
        .put("commandCount", 0);
  }

  private static void appendSection(StringBuilder out, String name, String text, int maxChars) {
    String bounded = text == null ? "" : text;
    if (bounded.length() > maxChars) bounded = bounded.substring(0, maxChars) + "\n[TRUNCATED]";
    out.append("\n=== ").append(name).append(" ===\n").append(bounded).append('\n');
  }

  private static String escape(String value) {
    return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String safe(String value) { return value == null ? "" : value; }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(safe(value).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
