package com.rabpit.backroom.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Phase-6B schema validator for the canon source registry. */
public final class CanonRegistry {
  public static final int SCHEMA_VERSION = 1;

  private static final Set<String> ROOT_KEYS =
      new HashSet<>(Arrays.asList("schemaVersion", "sources"));
  private static final Set<String> SOURCE_KEYS = new HashSet<>(Arrays.asList(
      "id", "path", "contentPath", "type", "authority", "status", "version", "owner",
      "dependencies", "mandatoryFor", "supersedes", "note"));
  private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
      "WORLD", "CHARACTER", "HISTORY", "ENVIRONMENT", "ENTITY_REFERENCE"));
  private static final Set<String> AUTHORITIES = new HashSet<>(Arrays.asList(
      "PROJECT_OVERRIDE", "WORLD_CANON", "CHARACTER_CANON", "SCOPED_USER_RETCON",
      "REFERENCE", "UNCLASSIFIED"));
  private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
      "CURRENT", "CANDIDATE", "REFERENCE", "UNCLASSIFIED"));

  private CanonRegistry() {}

  public static JSONObject validate(String rawJson) {
    try {
      return validate(new JSONObject(rawJson == null ? "{}" : rawJson));
    } catch (Exception error) {
      return result(false, new JSONArray().put("registry_json_invalid"), 0);
    }
  }

  public static JSONObject validate(JSONObject root) {
    JSONArray errors = new JSONArray();
    if (root == null) return result(false, errors.put("registry_missing"), 0);

    unknownKeys(root, ROOT_KEYS, "root", errors);
    if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
      errors.put("schema_version_invalid");
    }

    JSONArray sources = root.optJSONArray("sources");
    if (sources == null) return result(false, errors.put("sources_missing"), 0);

    Map<String, JSONObject> byId = new LinkedHashMap<>();
    Set<String> paths = new HashSet<>();
    Set<String> contentPaths = new HashSet<>();
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.optJSONObject(i);
      if (source == null) {
        errors.put("source_not_object:" + i);
        continue;
      }
      unknownKeys(source, SOURCE_KEYS, "source:" + i, errors);

      String id = required(source, "id", errors, i);
      String path = required(source, "path", errors, i);
      String contentPath = required(source, "contentPath", errors, i);
      String type = required(source, "type", errors, i);
      String authority = required(source, "authority", errors, i);
      String status = required(source, "status", errors, i);

      if (!id.isEmpty() && !id.matches("[a-z0-9][a-z0-9._-]*")) {
        errors.put("source_id_invalid:" + id);
      }
      if (!path.isEmpty()
          && (!path.endsWith(".md") || path.contains("/") || path.contains("\\"))) {
        errors.put("source_path_invalid:" + path);
      }
      if (!contentPath.isEmpty() && !validContentPath(contentPath)) {
        errors.put("source_content_path_invalid:" + id);
      }
      if (!type.isEmpty() && !TYPES.contains(type)) errors.put("source_type_invalid:" + id);
      if (!authority.isEmpty() && !AUTHORITIES.contains(authority)) {
        errors.put("source_authority_invalid:" + id);
      }
      if (!status.isEmpty() && !STATUSES.contains(status)) {
        errors.put("source_status_invalid:" + id);
      }

      if (!id.isEmpty()) {
        if (byId.containsKey(id)) errors.put("source_id_duplicate:" + id);
        else byId.put(id, source);
      }
      if (!path.isEmpty() && !paths.add(path)) errors.put("source_path_duplicate:" + path);
      if (!contentPath.isEmpty() && !contentPaths.add(contentPath)) {
        errors.put("source_content_path_duplicate:" + contentPath);
      }

      requireStringArray(source, "dependencies", errors, id);
      requireStringArray(source, "mandatoryFor", errors, id);
      requireStringArray(source, "supersedes", errors, id);

      String owner = source.optString("owner", "").trim();
      if (("CHARACTER_CANON".equals(authority) || "SCOPED_USER_RETCON".equals(authority))
          && owner.isEmpty()) {
        errors.put("source_owner_required:" + id);
      }
    }

    for (Map.Entry<String, JSONObject> entry : byId.entrySet()) {
      String id = entry.getKey();
      JSONObject source = entry.getValue();
      validateRefs(id, source.optJSONArray("dependencies"), byId, "dependency", errors);
      validateRefs(id, source.optJSONArray("supersedes"), byId, "supersedes", errors);
    }

    detectDependencyCycles(byId, errors);
    return result(errors.length() == 0, errors, sources.length());
  }

  public static Map<String, JSONObject> byId(JSONObject root) throws JSONException {
    JSONObject validation = validate(root);
    if (!validation.optBoolean("valid", false)) {
      throw new IllegalArgumentException("Invalid canon registry: "
          + validation.optJSONArray("errors"));
    }
    Map<String, JSONObject> result = new LinkedHashMap<>();
    JSONArray sources = root.getJSONArray("sources");
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.getJSONObject(i);
      result.put(source.getString("id"), new JSONObject(source.toString()));
    }
    return result;
  }

  private static boolean validContentPath(String path) {
    if (path == null || path.trim().isEmpty() || !path.equals(path.trim())) return false;
    if (!path.endsWith(".md") || path.startsWith("/") || path.contains("\\")
        || path.contains("..") || path.contains("//")) return false;
    String[] roots = {
        "world/", "levels/", "sublevels/", "entities/", "items/", "factions/",
        "phenomena/", "characters/", "history/", "wiki/", "codex/", "continuity/"
    };
    for (String root : roots) {
      if (path.startsWith(root) && path.length() > root.length()) return true;
    }
    return false;
  }

  private static void validateRefs(
      String sourceId, JSONArray refs, Map<String, JSONObject> byId, String kind, JSONArray errors) {
    if (refs == null) return;
    for (int i = 0; i < refs.length(); i++) {
      String target = refs.optString(i, "").trim();
      if (target.isEmpty()) continue;
      if (sourceId.equals(target)) errors.put(kind + "_self_reference:" + sourceId);
      else if (!byId.containsKey(target)) errors.put(kind + "_missing:" + sourceId + "->" + target);
    }
  }

  private static void detectDependencyCycles(
      Map<String, JSONObject> byId, JSONArray errors) {
    Map<String, Integer> state = new HashMap<>();
    List<String> stack = new ArrayList<>();
    for (String id : byId.keySet()) {
      if (state.getOrDefault(id, 0) == 0) {
        dfs(id, byId, state, stack, errors);
      }
    }
  }

  private static void dfs(
      String id, Map<String, JSONObject> byId, Map<String, Integer> state,
      List<String> stack, JSONArray errors) {
    state.put(id, 1);
    stack.add(id);
    JSONArray deps = byId.get(id).optJSONArray("dependencies");
    if (deps != null) {
      for (int i = 0; i < deps.length(); i++) {
        String next = deps.optString(i, "").trim();
        if (!byId.containsKey(next)) continue;
        int nextState = state.getOrDefault(next, 0);
        if (nextState == 0) {
          dfs(next, byId, state, stack, errors);
        } else if (nextState == 1) {
          int start = stack.indexOf(next);
          StringBuilder cycle = new StringBuilder();
          for (int j = Math.max(0, start); j < stack.size(); j++) {
            if (cycle.length() > 0) cycle.append("->");
            cycle.append(stack.get(j));
          }
          if (cycle.length() > 0) cycle.append("->");
          cycle.append(next);
          String marker = "dependency_cycle:" + cycle;
          if (!contains(errors, marker)) errors.put(marker);
        }
      }
    }
    stack.remove(stack.size() - 1);
    state.put(id, 2);
  }

  private static void unknownKeys(
      JSONObject object, Set<String> allowed, String scope, JSONArray errors) {
    java.util.Iterator<String> keys = object.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!allowed.contains(key)) errors.put("unknown_field:" + scope + ":" + key);
    }
  }

  private static String required(
      JSONObject object, String key, JSONArray errors, int index) {
    String value = object.optString(key, "").trim();
    if (value.isEmpty()) errors.put("source_required:" + index + ":" + key);
    return value;
  }

  private static void requireStringArray(
      JSONObject source, String key, JSONArray errors, String id) {
    JSONArray values = source.optJSONArray(key);
    if (values == null) {
      errors.put("source_array_required:" + id + ":" + key);
      return;
    }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < values.length(); i++) {
      String value = values.optString(i, "").trim();
      if (value.isEmpty()) errors.put("source_array_value_invalid:" + id + ":" + key);
      else if (!seen.add(value)) errors.put("source_array_duplicate:" + id + ":" + key + ":" + value);
    }
  }

  private static boolean contains(JSONArray array, String value) {
    for (int i = 0; i < array.length(); i++) {
      if (value.equals(array.optString(i, ""))) return true;
    }
    return false;
  }

  private static JSONObject result(boolean valid, JSONArray errors, int sourceCount) {
    try {
      return new JSONObject()
          .put("valid", valid)
          .put("schemaVersion", SCHEMA_VERSION)
          .put("sourceCount", sourceCount)
          .put("errors", errors == null ? new JSONArray() : errors);
    } catch (JSONException error) {
      return new JSONObject();
    }
  }
}
