package com.rabpit.backroom.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded read-only runtime projection of the user-approved story milestone. */
public final class MilestoneCore {
  static final int SCHEMA_VERSION = 1;
  static final int MAX_CONTEXT_CHARS = 2400;
  static final String ASSET = "knowledge/milestone_runtime.json";
  static final String SOURCE_SHA256 =
      "e7e7f66a9327e5945b4f2f5931f7c6f0908e539aa695c6418016b5824d8e6d69";

  private final JSONObject root;

  private MilestoneCore(JSONObject root) {
    this.root = root;
  }

  public static MilestoneCore fromAssets(Context context) throws Exception {
    if (context == null) throw new IllegalArgumentException("Context is required");
    try (InputStream in = context.getAssets().open(ASSET)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
      return fromText(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }
  }

  static MilestoneCore fromText(String raw) throws Exception {
    JSONObject root = new JSONObject(raw == null ? "{}" : raw);
    validate(root);
    return new MilestoneCore(new JSONObject(root.toString()));
  }

  public String promptContext(JSONObject state) throws Exception {
    String levelKey = state == null ? "0"
        : state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0))).trim();
    JSONObject level = root.getJSONObject("levels").optJSONObject(levelKey);
    StringBuilder out = new StringBuilder("MILESTONE — CURRENT LEVEL ONLY\n");
    if (level == null) {
      out.append("CURRENT NODE: outside configured milestone scope.\n");
    } else {
      out.append("MILESTONE ID: ").append(level.optString("milestoneId", "")).append('\n');
      out.append("CURRENT NODE: ").append(levelKey).append(" — ").append(level.getString("act")).append('\n');
      appendList(out, "GUIDANCE", level.getJSONArray("guidance"));
    }
    if ("6".equals(levelKey)) {
      out.append("BOUNDARY: milestone ends at Level 6; Level 6.1 is outside this milestone.\n");
    }
    if (out.length() > MAX_CONTEXT_CHARS) {
      throw new IllegalStateException("milestone_context_budget_exceeded:" + out.length());
    }
    return out.toString();
  }

  public String promptContext(JSONObject state, String ignoredAction) throws Exception {
    return promptContext(state);
  }

  private static void validate(JSONObject root) throws Exception {
    if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
      throw new IllegalArgumentException("milestone_schema_version_invalid");
    }
    if (!SOURCE_SHA256.equals(root.optString("sourceSha256", ""))) {
      throw new IllegalArgumentException("milestone_source_hash_invalid");
    }
    JSONArray route = root.optJSONArray("route");
    JSONObject levels = root.optJSONObject("levels");
    if (route == null || levels == null || route.length() != 25) {
      throw new IllegalArgumentException("milestone_route_invalid");
    }
    for (int i = 0; i < route.length(); i++) {
      String key = route.optString(i, "");
      if (key.isEmpty() || levels.optJSONObject(key) == null) {
        throw new IllegalArgumentException("milestone_level_missing:" + key);
      }
    }
    if (!"0".equals(route.optString(0)) || !"6".equals(route.optString(route.length() - 1))) {
      throw new IllegalArgumentException("milestone_route_boundary_invalid");
    }
    JSONObject scope = root.optJSONObject("scope");
    if (scope == null || !"0".equals(scope.optString("startLevelKey"))
        || !"6".equals(scope.optString("endLevelKey"))
        || !scope.optBoolean("terminalAtEnd", false)) {
      throw new IllegalArgumentException("milestone_scope_invalid");
    }
  }

  private static void appendList(StringBuilder out, String title, JSONArray values) {
    out.append(title).append(":\n");
    for (int i = 0; values != null && i < values.length(); i++) {
      String value = values.optString(i, "").trim();
      if (!value.isEmpty()) out.append("- ").append(value).append('\n');
    }
  }

}
