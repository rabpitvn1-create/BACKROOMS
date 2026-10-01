package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only player-visible evidence for exactly one committed turn. */
public final class CommittedTurnNarrationEvidence {
  public static final int SCHEMA_VERSION = 1;

  private CommittedTurnNarrationEvidence() {}

  public static JSONObject fromState(JSONObject state, String turnId) throws JSONException {
    JSONObject out = new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("available", false)
        .put("turnId", safe(turnId))
        .put("events", new JSONArray())
        .put("claims", new JSONArray());

    JSONObject root = state == null ? null : state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    if (root == null) return out.put("reason", "emergent_state_missing");
    String expected = safe(turnId);
    if (expected.isEmpty()) return out.put("reason", "turn_id_missing");
    if (!expected.equals(root.optString("lastCommittedTurnId", ""))) {
      return out.put("reason", "turn_not_latest_commit");
    }

    JSONObject commit = null;
    JSONArray commits = root.optJSONArray("commitLog");
    if (commits != null) {
      for (int i = commits.length() - 1; i >= 0; i--) {
        JSONObject candidate = commits.optJSONObject(i);
        if (candidate != null && expected.equals(candidate.optString("turnId", ""))) {
          commit = candidate;
          break;
        }
      }
    }
    if (commit == null) return out.put("reason", "commit_not_found");

    JSONArray visible = new JSONArray();
    JSONArray claims = new JSONArray();
    JSONArray events = commit.optJSONArray("events");
    if (events != null) {
      for (int i = 0; i < events.length(); i++) {
        JSONObject event = events.optJSONObject(i);
        if (event == null) continue;
        JSONObject params = event.optJSONObject("params");
        if (params != null && !params.optBoolean("observedByPlayer", false)) continue;
        visible.put(projectEvent(event));
        appendClaims(claims, event);
      }
    }

    JSONObject gm = commit.optJSONObject("gmTransaction");
    out.put("available", true)
        .put("reason", "")
        .put("commitSeq", commit.optInt("commitSeq", -1))
        .put("stateVersion", commit.optInt("stateVersion", -1))
        .put("authority", gm == null ? "CORE_V2" : gm.optString("authority", "GM_TRANSACTION"))
        .put("events", visible)
        .put("claims", claims);
    if (gm != null) out.put("transactionHash", gm.optString("transactionHash", ""));
    return out;
  }

  public static boolean hasClaim(JSONObject evidence, String kind, String subject) {
    JSONArray claims = evidence == null ? null : evidence.optJSONArray("claims");
    if (claims == null) return false;
    for (int i = 0; i < claims.length(); i++) {
      JSONObject claim = claims.optJSONObject(i);
      if (claim == null || !safe(kind).equals(claim.optString("kind", ""))) continue;
      if (safe(subject).isEmpty()
          || safe(subject).equalsIgnoreCase(claim.optString("subject", ""))) return true;
    }
    return false;
  }

  private static JSONObject projectEvent(JSONObject event) throws JSONException {
    JSONObject params = event.optJSONObject("params");
    JSONObject out = new JSONObject()
        .put("eventId", event.optString("eventId", ""))
        .put("eventType", event.optString("eventType", ""))
        .put("impactScope", event.optString("impactScope", ""))
        .put("targetRefs", copyArray(event.optJSONArray("targetRefs")));
    if (params != null) {
      copyString(params, out, "factPredicate");
      if (params.has("factValue") && params.opt("factValue") != JSONObject.NULL) {
        out.put("factValue", params.opt("factValue"));
      }
      copyString(params, out, "causedBy");
      copyString(params, out, "gmCommandType");
      copyString(params, out, "droppedItem");
      if (params.has("coreReward")) out.put("coreReward", Math.max(0, params.optInt("coreReward", 0)));
    }
    return out;
  }

  private static void appendClaims(JSONArray claims, JSONObject event) throws JSONException {
    String eventId = event.optString("eventId", "");
    String type = event.optString("eventType", "");
    JSONObject params = event.optJSONObject("params");
    String fact = params == null || params.opt("factValue") == null
        ? "" : String.valueOf(params.opt("factValue"));
    String target = first(event.optJSONArray("targetRefs"));

    if ("PLAYER_ACTION_RESOLVED".equals(type)) add(claims, eventId, "ACTION_RESOLVED", target, fact);
    else if ("LEVEL_TRANSITIONED".equals(type)) add(claims, eventId, "LEVEL_ENTERED", choose(fact, target), fact);
    else if ("ENTITY_ENCOUNTER_STARTED".equals(type)) add(claims, eventId, "ENTITY_ENCOUNTER_STARTED", choose(fact, target), fact);
    else if ("CHEST_SPAWNED".equals(type)) add(claims, eventId, "CHEST_DISCOVERED", target, fact);
    else if ("CHEST_OPENED".equals(type)) {
      add(claims, eventId, "ITEM_ACQUIRED", fact, fact);
      if (params != null && params.optInt("coreReward", 0) > 0) {
        add(claims, eventId, "CORE_ACQUIRED", "core", String.valueOf(params.optInt("coreReward", 0)));
      }
    } else if ("ITEM_ACTION_RESOLVED".equals(type)) {
      String item = fact.contains(":") ? fact.substring(0, fact.indexOf(':')) : fact;
      add(claims, eventId, "ITEM_ACTION", item, fact);
    } else if ("CHARACTER_ENCOUNTERED".equals(type)) add(claims, eventId, "CHARACTER_ENCOUNTERED", choose(fact, target), fact);
    else if ("CHARACTER_REUNION".equals(type)) add(claims, eventId, "CHARACTER_REUNION", choose(fact, target), fact);
    else if ("CHARACTER_STAT_UPGRADED".equals(type)) add(claims, eventId, "STAT_UPGRADED", target, fact);
    else if ("COMBAT_STARTED".equals(type)) add(claims, eventId, "COMBAT_STARTED", choose(fact, target), fact);
    else if ("COMBAT_VICTORY".equals(type) || "COMBAT_DEFEAT".equals(type)
        || "COMBAT_HAND_RESOLVED".equals(type)) {
      add(claims, eventId, "COMBAT_RESULT", target, fact);
      if (params != null) {
        String dropped = params.optString("droppedItem", "").trim();
        if (!dropped.isEmpty()) add(claims, eventId, "ITEM_ACQUIRED", dropped, dropped);
        int reward = Math.max(0, params.optInt("coreReward", 0));
        if (reward > 0) add(claims, eventId, "CORE_ACQUIRED", "core", String.valueOf(reward));
      }
    }
  }

  private static void add(JSONArray claims, String eventId, String kind, String subject, String value)
      throws JSONException {
    claims.put(new JSONObject().put("eventId", safe(eventId)).put("kind", safe(kind))
        .put("subject", safe(subject)).put("value", safe(value)));
  }

  private static JSONArray copyArray(JSONArray array) throws JSONException {
    return array == null ? new JSONArray() : new JSONArray(array.toString());
  }

  private static String first(JSONArray array) {
    return array == null || array.length() == 0 ? "" : array.optString(0, "");
  }

  private static String choose(String preferred, String fallback) {
    String value = safe(preferred);
    return value.isEmpty() || "null".equals(value) ? safe(fallback) : value;
  }

  private static void copyString(JSONObject from, JSONObject to, String key) throws JSONException {
    String value = from.optString(key, "").trim();
    if (!value.isEmpty()) to.put(key, value);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
