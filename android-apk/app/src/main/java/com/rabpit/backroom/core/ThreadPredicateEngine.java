package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Structured deterministic predicates for authoritative Thread resolution. */
final class ThreadPredicateEngine {
  private ThreadPredicateEngine() {}

  static JSONArray conditionsForThreadType(String threadType, JSONArray keyRefs) throws Exception {
    JSONArray conditions = new JSONArray();
    String type = threadType == null ? "" : threadType.trim();
    if ("ENTITY_ENCOUNTER".equals(type)) {
      conditions.put(new JSONObject()
          .put("op", "FLAG_EMPTY")
          .put("key", "entityEncounterKey"));
    } else if ("CHEST_AVAILABLE".equals(type)) {
      conditions.put(new JSONObject()
          .put("op", "FLAG_FALSE")
          .put("key", "chestPresent"));
    } else if ("LEVEL_ROUTE_SEARCH".equals(type)) {
      conditions.put(new JSONObject()
          .put("op", "PATH_EQUALS")
          .put("path", "levelRoute.exitAvailable")
          .put("value", true));
    }
    return conditions;
  }

  static void validate(JSONArray conditions) {
    if (conditions == null) return;
    for (int i = 0; i < conditions.length(); i++) {
      JSONObject condition = conditions.optJSONObject(i);
      if (condition == null) throw new IllegalStateException("Thread predicate must be an object");
      String op = condition.optString("op", "");
      if ("FLAG_EMPTY".equals(op) || "FLAG_FALSE".equals(op)) {
        if (condition.optString("key", "").trim().isEmpty()) {
          throw new IllegalStateException(op + " requires key");
        }
      } else if ("PATH_EQUALS".equals(op)) {
        if (condition.optString("path", "").trim().isEmpty() || !condition.has("value")) {
          throw new IllegalStateException("PATH_EQUALS requires path and value");
        }
      } else {
        throw new IllegalStateException("Unsupported thread predicate: " + op);
      }
    }
  }

  static boolean allSatisfied(JSONObject state, JSONArray conditions) {
    if (conditions == null || conditions.length() == 0) return false;
    for (int i = 0; i < conditions.length(); i++) {
      JSONObject condition = conditions.optJSONObject(i);
      if (condition == null || !satisfied(state, condition)) return false;
    }
    return true;
  }

  private static boolean satisfied(JSONObject state, JSONObject condition) {
    String op = condition.optString("op", "");
    if ("FLAG_EMPTY".equals(op)) {
      JSONObject flags = state == null ? null : state.optJSONObject("flags");
      Object value = flags == null ? null : flags.opt(condition.optString("key", ""));
      return value == null || JSONObject.NULL.equals(value) || String.valueOf(value).trim().isEmpty();
    }
    if ("FLAG_FALSE".equals(op)) {
      JSONObject flags = state == null ? null : state.optJSONObject("flags");
      return flags == null || !flags.optBoolean(condition.optString("key", ""), false);
    }
    if ("PATH_EQUALS".equals(op)) {
      Object actual = valueAtPath(state, condition.optString("path", ""));
      Object expected = condition.opt("value");
      if (expected == null || JSONObject.NULL.equals(expected)) {
        return actual == null || JSONObject.NULL.equals(actual);
      }
      return String.valueOf(expected).equals(String.valueOf(actual));
    }
    return false;
  }

  private static Object valueAtPath(JSONObject root, String path) {
    if (root == null || path == null || path.trim().isEmpty()) return null;
    String[] parts = path.split("\\.");
    Object cursor = root;
    for (String part : parts) {
      if (!(cursor instanceof JSONObject)) return null;
      cursor = ((JSONObject) cursor).opt(part);
      if (cursor == null) return null;
    }
    return cursor;
  }
}
