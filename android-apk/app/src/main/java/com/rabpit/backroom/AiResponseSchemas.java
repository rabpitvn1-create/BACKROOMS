package com.rabpit.backroom;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Strict semantic validation runs inside each provider attempt, enabling safe fallback. */
public final class AiResponseSchemas {
  public enum Role { WRITER, REPAIR, AUDIT }

  private static final Set<String> AUDIT_RULES = new HashSet<>(Arrays.asList(
    "canon_conflict", "knowledge_leak", "state_narrative_mismatch", "competence_suppression",
    "ability_overreach", "unsupported_claim", "character_voice", "address_error"
  ));
  private static final Set<String> OP_TYPES = new HashSet<>(Arrays.asList(
    "set_location", "set_level", "patch_player", "inventory_upsert", "inventory_remove",
    "party_upsert", "party_remove", "world_item_upsert", "flag_patch"
  ));

  private AiResponseSchemas() {}

  public static String validate(Role role, String canonicalJson) throws Exception {
    JSONObject value = new JSONObject(canonicalJson);
    if (role == Role.AUDIT) validateAudit(value); else validateWriter(value);
    return value.toString();
  }

  private static void validateWriter(JSONObject value) throws Exception {
    requireOnly(value, "reply", "ops", "snapshotEvent");
    String reply = requiredString(value, "reply", 1, 7000);
    if (reply.trim().isEmpty()) fail("reply must not be blank");
    JSONArray operations = value.optJSONArray("ops");
    if (operations == null || operations.length() > 24) fail("ops must be an array with at most 24 entries");
    for (int index = 0; index < operations.length(); index++) {
      JSONObject operation = operations.optJSONObject(index);
      if (operation == null) fail("every op must be an object");
      String type = requiredString(operation, "type", 1, 64).toLowerCase();
      if (!OP_TYPES.contains(type)) fail("unsupported op type: " + type);
    }
    JSONObject snapshot = value.optJSONObject("snapshotEvent");
    if (snapshot == null) fail("snapshotEvent must be an object");
    requireOnly(snapshot, "shouldGenerate", "kind", "reason");
    if (!snapshot.has("shouldGenerate") || !(snapshot.get("shouldGenerate") instanceof Boolean)) {
      fail("snapshotEvent.shouldGenerate must be boolean");
    }
    requiredString(snapshot, "kind", 0, 80);
    requiredString(snapshot, "reason", 0, 500);
  }

  private static void validateAudit(JSONObject value) throws Exception {
    requireOnly(value, "pass", "issues");
    if (!value.has("pass") || !(value.get("pass") instanceof Boolean)) fail("pass must be boolean");
    JSONArray issues = value.optJSONArray("issues");
    if (issues == null || issues.length() > 16) fail("issues must be an array with at most 16 entries");
    for (int index = 0; index < issues.length(); index++) {
      JSONObject issue = issues.optJSONObject(index);
      if (issue == null) fail("every issue must be an object");
      requireOnly(issue, "rule", "severity", "claim", "reason");
      String rule = requiredString(issue, "rule", 1, 80).toLowerCase();
      String severity = requiredString(issue, "severity", 1, 16).toLowerCase();
      requiredString(issue, "claim", 1, 800);
      requiredString(issue, "reason", 1, 1200);
      if (!AUDIT_RULES.contains(rule)) fail("unsupported audit rule: " + rule);
      if (!"hard".equals(severity) && !"soft".equals(severity)) fail("invalid audit severity");
    }
    if (value.getBoolean("pass") != (issues.length() == 0)) fail("pass must agree with issues");
  }

  private static void requireOnly(JSONObject value, String... keys) throws Exception {
    Set<String> allowed = new HashSet<>(Arrays.asList(keys));
    Iterator<String> iterator = value.keys();
    while (iterator.hasNext()) {
      String key = iterator.next();
      if (!allowed.contains(key)) fail("unexpected key: " + key);
    }
    for (String key : keys) if (!value.has(key)) fail("missing key: " + key);
  }

  private static String requiredString(JSONObject value, String key, int minimum, int maximum) throws Exception {
    if (!value.has(key) || !(value.get(key) instanceof String)) fail(key + " must be a string");
    String result = value.getString(key);
    if (result.length() < minimum || result.length() > maximum) fail(key + " length is invalid");
    return result;
  }

  private static void fail(String message) throws Exception {
    throw new Exception("AI response schema violation: " + message);
  }
}
