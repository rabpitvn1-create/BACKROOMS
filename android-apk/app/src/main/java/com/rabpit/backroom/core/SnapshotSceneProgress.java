package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Authoritative visual progression for bundled environment snapshots (not a gameplay exit). */
public final class SnapshotSceneProgress {
  public static final String STEP_KEY = "snapshotStep";

  private SnapshotSceneProgress() {}

  public static void apply(JSONObject before, JSONObject next, JSONObject rolls, String actionKind, String actionText)
      throws Exception {
    if (before == null || next == null) return;
    JSONObject oldFlags = before.optJSONObject("flags");
    JSONObject oldExploration = oldFlags == null ? null : oldFlags.optJSONObject("exploration");
    int previous = oldExploration == null ? 0 : Math.max(0, oldExploration.optInt(STEP_KEY, 0));

    JSONObject flags = next.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();

    // Device/pin-pad input changes only the puzzle state. Even if story
    // continuity revises visual flags, preserve the same backdrop/character
    // snapshot until the player actually moves or another visual event occurs.
    if ("DEVICE".equalsIgnoreCase(actionKind == null ? "" : actionKind.trim())) {
      exploration.put(STEP_KEY, previous);
      flags.put("exploration", exploration);
      if (oldFlags != null && oldFlags.has("visualAreaKey"))
        flags.put("visualAreaKey", oldFlags.get("visualAreaKey"));
      else flags.remove("visualAreaKey");
      next.put("flags", flags);
      return;
    }

    boolean nodeChanged = !LevelRouteRegistry.currentId(before).equals(LevelRouteRegistry.currentId(next));
    boolean locationChanged = !before.optString("location", "").equals(next.optString("location", ""));
    String oldArea = oldFlags == null ? "" : oldFlags.optString("visualAreaKey", "");
    boolean areaChanged = !oldArea.equals(flags.optString("visualAreaKey", ""));
    // This marker is emitted only after Core validates a scene MOVE. Room
    // labels ("Kho hồ sơ", etc.) do not need to contain movement verbs.
    boolean sceneFacing = "SCENE_MOVE".equalsIgnoreCase(actionKind == null ? "" : actionKind.trim())
        || ("EXECUTE".equalsIgnoreCase(actionKind == null ? "" : actionKind.trim())
            && PhysicalActionDetector.isSceneFacing(actionText == null ? "" : actionText));
    boolean failed = HiddenExitStreak.failedThisTurn(rolls);

    // Discovering an Exit is independent from exploring the surroundings.
    // An Entity encounter preempts travel, even if the narrative suggests a new view.
    JSONObject entityRoll = rolls == null ? null : rolls.optJSONObject("entityEncounter");
    boolean encounter = entityRoll != null && entityRoll.optBoolean("eligible", false) &&
        entityRoll.optBoolean("success", false);
    boolean advance = !encounter &&
        (nodeChanged || sceneFacing || (!failed && (locationChanged || areaChanged)));
    int step = advance ? (previous == Integer.MAX_VALUE ? 0 : previous + 1) : previous;
    // Ignore any snapshotStep supplied by the narrative model; Core alone advances it.
    exploration.put(STEP_KEY, step);
    flags.put("exploration", exploration);
    next.put("flags", flags);
  }
}
