package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Replayable top-level authoritative state patch stored in each CommitRecord.
 * Derived/runtime roots are intentionally excluded.
 */
final class AuthoritativeStatePatch {
  private static final Set<String> EXCLUDED = new TreeSet<>();
  static {
    EXCLUDED.add(EmergentTurnEngine.ROOT_KEY);
    EXCLUDED.add("log");
    EXCLUDED.add("partyDetails");
    EXCLUDED.add("characterCanon");
    EXCLUDED.add("saveVersion");
    EXCLUDED.add("mode");
  }

  private AuthoritativeStatePatch() {}

  static JSONObject diff(JSONObject before, JSONObject after) throws Exception {
    JSONObject set = new JSONObject();
    JSONArray remove = new JSONArray();
    Set<String> keys = new TreeSet<>();
    if (before != null) addKeys(before, keys);
    if (after != null) addKeys(after, keys);

    for (String key : keys) {
      if (EXCLUDED.contains(key)) continue;
      boolean had = before != null && before.has(key);
      boolean has = after != null && after.has(key);
      if (had && !has) {
        remove.put(key);
        continue;
      }
      if (!has) continue;
      Object previous = had ? before.opt(key) : null;
      Object next = after.opt(key);
      if (!had || !jsonEquals(previous, next)) set.put(key, copy(next));
    }
    return new JSONObject().put("set", set).put("remove", remove);
  }

  static JSONObject apply(JSONObject base, JSONObject patch) throws Exception {
    JSONObject output = base == null ? new JSONObject() : new JSONObject(base.toString());
    if (patch == null) return output;
    JSONArray remove = patch.optJSONArray("remove");
    if (remove != null) {
      for (int i = 0; i < remove.length(); i++) output.remove(remove.optString(i, ""));
    }
    JSONObject set = patch.optJSONObject("set");
    if (set != null) {
      Iterator<String> keys = set.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        output.put(key, copy(set.opt(key)));
      }
    }
    return output;
  }

  static JSONArray changedRoots(JSONObject patch) {
    JSONArray output = new JSONArray();
    if (patch == null) return output;
    JSONObject set = patch.optJSONObject("set");
    if (set != null) {
      TreeSet<String> keys = new TreeSet<>();
      addKeys(set, keys);
      for (String key : keys) output.put(key);
    }
    JSONArray remove = patch.optJSONArray("remove");
    if (remove != null) {
      for (int i = 0; i < remove.length(); i++) {
        String key = remove.optString(i, "");
        if (!contains(output, key)) output.put(key);
      }
    }
    return output;
  }

  static boolean isEmpty(JSONObject patch) {
    if (patch == null) return true;
    JSONObject set = patch.optJSONObject("set");
    JSONArray remove = patch.optJSONArray("remove");
    return (set == null || set.length() == 0) && (remove == null || remove.length() == 0);
  }

  private static void addKeys(JSONObject value, Set<String> output) {
    Iterator<String> it = value.keys();
    while (it.hasNext()) output.add(it.next());
  }

  private static boolean contains(JSONArray values, String expected) {
    for (int i = 0; i < values.length(); i++) if (expected.equals(values.optString(i))) return true;
    return false;
  }

  private static boolean jsonEquals(Object a, Object b) {
    if (a == b) return true;
    if (a == null || b == null || JSONObject.NULL.equals(a) || JSONObject.NULL.equals(b)) {
      return (a == null || JSONObject.NULL.equals(a)) && (b == null || JSONObject.NULL.equals(b));
    }
    if (a instanceof JSONObject && b instanceof JSONObject) {
      return ((JSONObject) a).toString().equals(((JSONObject) b).toString());
    }
    if (a instanceof JSONArray && b instanceof JSONArray) {
      return ((JSONArray) a).toString().equals(((JSONArray) b).toString());
    }
    return a.equals(b);
  }

  private static Object copy(Object value) throws Exception {
    if (value instanceof JSONObject) return new JSONObject(value.toString());
    if (value instanceof JSONArray) return new JSONArray(value.toString());
    return value == null ? JSONObject.NULL : value;
  }
}
