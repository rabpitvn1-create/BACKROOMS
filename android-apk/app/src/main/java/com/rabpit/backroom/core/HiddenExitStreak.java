package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Authoritative hidden route progression for Level exits. */
public final class HiddenExitStreak {
  public static final int REQUIRED_STREAK = 5;
  public static final int SUCCESS_PERCENT = 50;
  public static final String ROLL_KEY = "_hiddenExitStreak";
  public static final String STREAK_KEY = "hiddenExitStreak";

  private HiddenExitStreak() {}

  public static int current(JSONObject state) {
    JSONObject exploration = exploration(state);
    return exploration == null
        ? 0
        : Math.max(0, Math.min(REQUIRED_STREAK, exploration.optInt(STREAK_KEY, 0)));
  }

  public static boolean ready(JSONObject state) {
    return current(state) >= REQUIRED_STREAK;
  }

  public static boolean rollEligible(JSONObject rolls) {
    JSONObject roll = rolls == null ? null : rolls.optJSONObject(ROLL_KEY);
    return roll != null && roll.optBoolean("eligible", false);
  }

  public static boolean rollSucceeded(JSONObject rolls) {
    JSONObject roll = rolls == null ? null : rolls.optJSONObject(ROLL_KEY);
    return roll != null && roll.optBoolean("eligible", false) && roll.optBoolean("success", false);
  }

  public static boolean failedThisTurn(JSONObject rolls) {
    return rollEligible(rolls) && !rollSucceeded(rolls);
  }

  public static int projectedStreak(JSONObject before, JSONObject rolls) {
    int streak = current(before);
    if (streak >= REQUIRED_STREAK || !rollEligible(rolls)) return streak;
    return rollSucceeded(rolls) ? Math.min(REQUIRED_STREAK, streak + 1) : 0;
  }

  public static boolean projectedReady(JSONObject before, JSONObject rolls) {
    return projectedStreak(before, rolls) >= REQUIRED_STREAK;
  }

  public static String gmDirective(JSONObject before, JSONObject rolls) {
    if (ready(before)) {
      return "HIDDEN EXIT ROUTE: một Exit thật đã available. Có thể để người chơi nhận biết nó, nhưng không tự chuyển Level nếu hành động chưa chủ động đi qua. Không nhắc streak, bộ đếm, roll hay xác suất.";
    }
    if (!rollEligible(rolls)) {
      return "HIDDEN EXIT ROUTE: lượt này không thay đổi tiến trình Exit. Không suy diễn thêm Exit và không nhắc cơ chế ẩn.";
    }
    if (!rollSucceeded(rolls)) {
      return "HIDDEN EXIT ROUTE: exploration lần này thất bại về tuyến thoát. Giữ nguyên location trước lượt, xóa mọi Exit/candidate transition chưa hoàn tất và tuyệt đối không nói về streak, reset, roll hay xác suất.";
    }
    if (projectedReady(before, rolls)) {
      return "HIDDEN EXIT ROUTE: tuyến thoát vừa đủ điều kiện. Có thể hé lộ một Exit thật hợp canon, nhưng người chơi vẫn phải chủ động đi qua; tuyệt đối không nói về streak, số lần, roll hay xác suất.";
    }
    return "HIDDEN EXIT ROUTE: exploration có tiến triển nhưng Exit chưa available. Không hé lộ Exit thật và tuyệt đối không nói về streak, số lần, roll hay xác suất.";
  }

  public static void apply(
      JSONObject before,
      JSONObject state,
      JSONObject rolls,
      int oldLevel,
      int newLevel) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();

    if (oldLevel != newLevel) {
      setState(flags, exploration, 0, false);
    } else if (rollEligible(rolls)) {
      boolean success = rollSucceeded(rolls);
      int streak = success ? Math.min(REQUIRED_STREAK, current(before) + 1) : 0;
      boolean exitReady = streak >= REQUIRED_STREAK;
      setState(flags, exploration, streak, exitReady);
      if (!success && before != null && before.has("location")) {
        state.put("location", before.get("location"));
      }
    } else {
      int streak = current(before);
      setState(flags, exploration, streak, streak >= REQUIRED_STREAK);
    }

    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

  private static void setState(
      JSONObject flags,
      JSONObject exploration,
      int streak,
      boolean exitReady) throws Exception {
    exploration.put(STREAK_KEY, Math.max(0, Math.min(REQUIRED_STREAK, streak)));
    exploration.put("exitReady", exitReady);
    exploration.put("transitionReady", exitReady);
    if (exitReady) {
      exploration.put("exitProgress", "READY");
      flags.put("exitProgress", "READY");
    } else {
      exploration.remove("confirmedExit");
      exploration.remove("exitCandidate");
      exploration.remove("exitProgress");
      flags.remove("exitProgress");
      flags.remove("exitChanceThreshold");
    }
  }

  private static JSONObject exploration(JSONObject state) {
    if (state == null) return null;
    JSONObject flags = state.optJSONObject("flags");
    return flags == null ? null : flags.optJSONObject("exploration");
  }
}
