package com.rabpit.backroom;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded, on-device diagnostics. No logcat scraping, prompt bodies, credentials,
 * inventory contents, raw save JSON or untrusted exception messages are exported.
 * Export happens only after the player presses the button and picks a file.
 */
final class AppDiagnosticLog {
  private static final int MAX_EVENTS = 80;
  private static final int MAX_UI_BYTES = 16000;
  private final SharedPreferences preferences;
  private final ArrayDeque<JSONObject> events = new ArrayDeque<>();

  AppDiagnosticLog(Context context) {
    preferences = context.getSharedPreferences("backroom_diagnostic_events", Context.MODE_PRIVATE);
    try {
      JSONArray previous = new JSONArray(preferences.getString("events", "[]"));
      int first = Math.max(0, previous.length() - MAX_EVENTS);
      for (int i = first; i < previous.length(); i++) {
        JSONObject item = previous.optJSONObject(i);
        if (item != null) events.addLast(item);
      }
    } catch (JSONException ignored) {
      // Diagnostics may be reset without ever touching the game save.
    }
    record("app", "session_started");
  }

  // A permissive identifier regex could export an API key that happens to
  // contain only letters, digits or hyphens. Never echo unrecognized messages.
  private static final Set<String> SAFE_CODES = new HashSet<>(Arrays.asList(
    "unknown", "unknown_failure", "details_redacted",
    "app", "startup", "javascript", "turn", "combat", "storage", "export",
    "core_create", "core_diagnostics", "export_prepare", "export_picker",
    "export_write", "startup_initialize", "startup_resume", "core_snapshot",
    "core_stats", "core_upgrade", "turn_processing", "combat_action", "combat_dice",
    "session_started", "diagnostics_ready", "response_received",
    "uncaught_exception", "unhandled_rejection", "browser_save_parse_failed",
    "save_version_unsupported", "level_game_not_initialized",
    "core_save_decode_failed", "core_save_failed", "level_travel_not_configured",
    "stale_game_run", "stale_level_turn", "stale_exit_turn",
    "exit_action_conflict", "invalid_random_source", "invalid_save",
    "native_exception", "requested", "file_saved", "cancelled", "failed",
    "missing_report", "busy", "bridge_unavailable", "resume_success",
    "core_unavailable", "android_bridge_unavailable", "invalid_ui_summary", "unavailable", "core_summary_failed"
  ));

  static String safeCode(String value) {
    if (value == null) return "unknown";
    String code = value.trim();
    return SAFE_CODES.contains(code) ? code : "details_redacted";
  }

  private static String safeNode(String value) {
    if (value == null) return "";
    return value.matches("LEVEL\\.NODE\\.[a-zA-Z0-9_.-]{1,80}") ? value : "";
  }

  synchronized void record(String stage, String code) {
    try {
      JSONObject e = new JSONObject()
        .put("timestampMs", System.currentTimeMillis())
        .put("stage", safeCode(stage))
        .put("code", safeCode(code));
      append(e);
    } catch (JSONException ignored) { /* Diagnostic logging cannot interrupt gameplay. */ }
  }

  synchronized void recordException(String stage, Throwable failure) {
    try {
      JSONObject e = new JSONObject()
        .put("timestampMs", System.currentTimeMillis())
        .put("stage", safeCode(stage))
        .put("code", "native_exception");
      JSONArray causes = new JSONArray();
      Throwable cause = failure;
      for (int i = 0; cause != null && i < 5; i++, cause = cause.getCause()) {
        causes.put(new JSONObject().put("type", cause.getClass().getSimpleName())
          .put("code", safeCode(cause.getMessage())));
      }
      e.put("causes", causes);
      JSONArray frames = new JSONArray();
      for (StackTraceElement frame : failure.getStackTrace()) {
        if (!frame.getClassName().startsWith("com.rabpit.backroom.")) continue;
        frames.put(frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber());
        if (frames.length() >= 8) break;
      }
      e.put("frames", frames);
      append(e);
    } catch (Exception ignored) { /* Never fail a game action while writing diagnostics. */ }
  }

  private void append(JSONObject event) {
    events.addLast(event);
    while (events.size() > MAX_EVENTS) events.removeFirst();
    // Keep error history across an app restart, separate from the game save.
    preferences.edit().putString("events", new JSONArray(events).toString()).apply();
  }

  static JSONObject safeUiSummary(String raw) throws JSONException {
    JSONObject out = new JSONObject();
    if (raw == null || raw.length() > MAX_UI_BYTES) return out.put("summaryError", "invalid_ui_summary");
    JSONObject input = new JSONObject(raw);
    for (String name : new String[]{"hasState", "hasBrowserSave", "hasCoreSnapshot",
      "startupRecoveryVisible", "combatActive", "displayLocationKnown"}) {
      if (input.has(name)) out.put(name, input.optBoolean(name, false));
    }
    for (String name : new String[]{"turn", "partySize", "browserSaveVersion"}) {
      if (input.has(name)) out.put(name, Math.max(0, Math.min(1000000, input.optInt(name, 0))));
    }
    if (input.has("levelNodeId"))
      out.put("levelNodeId", safeNode(input.optString("levelNodeId", "")));
    if (input.has("startupErrorCode"))
      out.put("startupErrorCode", safeCode(input.optString("startupErrorCode", "")));
    JSONArray source = input.optJSONArray("events");
    JSONArray whitelisted = new JSONArray();
    if (source != null) {
      for (int i = Math.max(0, source.length() - MAX_EVENTS); i < source.length(); i++) {
        JSONObject event = source.optJSONObject(i);
        if (event == null) continue;
        JSONObject item = new JSONObject()
          .put("timestampMs", Math.max(0L, Math.min(9000000000000000L,
            event.optLong("timestampMs", 0L))))
          .put("stage", safeCode(event.optString("stage")))
          .put("code", safeCode(event.optString("code")));
        if (event.has("line")) item.put("line",
          Math.max(0, Math.min(1000000, event.optInt("line"))));
        if (event.has("column")) item.put("column",
          Math.max(0, Math.min(1000000, event.optInt("column"))));
        String script = event.optString("source", "");
        if (script.matches("[a-zA-Z0-9_.-]+\\.(?:js|html)"))
          item.put("script", script);
        whitelisted.put(item);
      }
    }
    out.put("events", whitelisted);
    return out;
  }

  static JSONObject safeCoreSummary(String raw) throws JSONException {
    JSONObject out = new JSONObject();
    if (raw == null) return out.put("coreAvailable", false);
    JSONObject core = new JSONObject(raw);
    out.put("coreAvailable", true)
      .put("saveVersion", core.optInt("saveVersion", 0));
    JSONObject world = core.optJSONObject("world");
    out.put("levelTravelPresent", world != null && world.has("levelTravel"));
    if (world != null) {
      JSONObject travel = new JSONObject(world.optString("levelTravel", "{}"));
      out.put("levelNodeId", safeNode(travel.optString("nodeId", "")))
        .put("levelTurn", Math.max(0, Math.min(1000000,
          Integer.parseInt(world.optString("levelTravelUiTurn", "0").matches("[0-9]{1,6}")
            ? world.optString("levelTravelUiTurn") : "0"))));
    }
    JSONObject chars = core.optJSONObject("characters");
    JSONObject statuses = core.optJSONObject("statuses");
    JSONObject party = core.optJSONObject("party");
    out.put("characterCount", chars == null ? 0 : chars.length());
    out.put("statusCount", statuses == null ? 0 : statuses.length());
    out.put("partySize", party == null ? 0 :
      (party.optJSONArray("memberIds") == null ? 0 : party.optJSONArray("memberIds").length()));
    return out;
  }

  synchronized JSONObject buildReport(String uiJson, String coreJson, String coreError) throws JSONException {
    JSONObject report = new JSONObject()
      .put("schemaVersion", 1)
      .put("capturedAtMs", System.currentTimeMillis())
      .put("app", new JSONObject()
        .put("version", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("debugBuild", BuildConfig.DEBUG)
        .put("androidApi", Build.VERSION.SDK_INT))
      .put("privacy", "No API keys, provider payloads, narrative, raw save or personal identifiers")
      .put("nativeEvents", new JSONArray(events));
    try { report.put("ui", safeUiSummary(uiJson)); }
    catch (Exception error) { report.put("ui", new JSONObject().put("summaryError", "unavailable")); }
    try { report.put("core", safeCoreSummary(coreJson)); }
    catch (Exception error) { report.put("core", new JSONObject().put("coreAvailable", false)
      .put("summaryError", "core_summary_failed")); }
    if (coreError != null) report.put("coreErrorCode", safeCode(coreError));
    return report;
  }
}
