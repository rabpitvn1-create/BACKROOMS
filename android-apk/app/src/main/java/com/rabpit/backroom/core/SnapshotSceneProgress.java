package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Authoritative visual progression for bundled environment snapshots (not a gameplay exit). */
public final class SnapshotSceneProgress {
  public static final String STEP_KEY = "snapshotStep";

  private SnapshotSceneProgress() {}

  public static void apply(JSONObject before, JSONObject next, JSONObject rolls, String actionKind)
      throws Exception {
    if (before == null || next == null) return;
    JSONObject oldFlags = before.optJSONObject("flags");
    JSONObject oldExploration = oldFlags == null ? null : oldFlags.optJSONObject("exploration");
    int previous = oldExploration == null ? 0 : Math.max(0, oldExploration.optInt(STEP_KEY, 0));

    JSONObject flags = next.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();

    boolean nodeChanged = !LevelRouteRegistry.currentId(before).equals(LevelRouteRegistry.currentId(next));
    boolean locationChanged = !before.optString("location", "").equals(next.optString("location", ""));
    String oldArea = oldFlags == null ? "" : oldFlags.optString("visualAreaKey", "");
    boolean areaChanged = !oldArea.equals(flags.optString("visualAreaKey", ""));
    boolean explore = "EXPLORE".equalsIgnoreCase(actionKind == null ? "" : actionKind.trim());
    boolean failed = HiddenExitStreak.failedThisTurn(rolls);

    // A failed hidden-exit probe must not fake movement. A confirmed exploration can
    // reveal a new view within the same location; actual location/node changes also count.
    boolean advance = nodeChanged || (!failed && (explore || locationChanged || areaChanged));
    int step = advance ? (previous == Integer.MAX_VALUE ? 0 : previous + 1) : previous;
    // Ignore any snapshotStep supplied by the narrative model; Core alone advances it.
    exploration.put(STEP_KEY, step);
    flags.put("exploration", exploration);
    next.put("flags", flags);
  }
}
