package com.rabpit.backroom;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;

/** Bounded, read-only diagnostic timeline and portable export codec. No network calls. */
final class DiagnosticLog {
  private static final int MAX_EVENTS = 250;
  private static final int MAX_TIMINGS = 40;
  private static final Object TIMING_LOCK = new Object();
  private static final String TIMING_PREF = "diagnostic_performance_v1";
  private static volatile Context applicationContext;

  static void initialize(Context context) {
    if (context != null) applicationContext = context.getApplicationContext();
  }
  private static final ArrayDeque<JSONObject> EVENTS = new ArrayDeque<>();

  static void record(String category, String detail) {
    try {
      JSONObject event = new JSONObject()
        .put("epochMs", System.currentTimeMillis())
        .put("category", category)
        .put("detail", detail == null ? "" : detail.substring(0, Math.min(detail.length(), 12000)));
      synchronized (EVENTS) {
        EVENTS.addLast(event);
        while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
      }
    } catch (Exception ignored) { /* Never interrupt gameplay for diagnostics. */ }
  }

  static void error(String category, Throwable throwable) {
    record(category, Log.getStackTraceString(throwable));
  }

  /**
   * Persist metadata-only performance spans across Activity/process restarts.
   * Never persist prompt text, API response, credentials, or other game content.
   */
  static void timing(Context context, String stage, long durationMs, int characterCount) {
    if (context == null || stage == null || !stage.matches("[a-z._]{1,40}")) return;
    try {
      JSONObject event = new JSONObject()
        .put("epochMs", System.currentTimeMillis())
        .put("stage", stage)
        .put("durationMs", Math.max(0, Math.min(durationMs, 600000)))
        .put("chars", Math.max(0, Math.min(characterCount, 2000000)));
      synchronized (TIMING_LOCK) {
        android.content.SharedPreferences prefs = context.getApplicationContext()
          .getSharedPreferences(TIMING_PREF, Context.MODE_PRIVATE);
        JSONArray prior = new JSONArray(prefs.getString("spans", "[]"));
        JSONArray updated = new JSONArray();
        for (int i = Math.max(0, prior.length() - (MAX_TIMINGS - 1)); i < prior.length(); i++)
          updated.put(prior.get(i));
        updated.put(event);
        prefs.edit().putString("spans", updated.toString()).apply();
      }
      record("performance." + stage, "durationMs=" + durationMs + "; chars=" + characterCount);
    } catch (Exception ignored) { /* Diagnostics never affect the GM. */ }
  }

  private static JSONArray persistedTimings(Context context) {
    try {
      synchronized (TIMING_LOCK) {
        return new JSONArray(context.getApplicationContext()
          .getSharedPreferences(TIMING_PREF, Context.MODE_PRIVATE)
          .getString("spans", "[]"));
      }
    } catch (Exception ignored) { return new JSONArray(); }
  }

  private static String[][] keyPairs() {
    return new String[][]{
      {"GEHIHI_API_KEY", BuildConfig.GEHIHI_API_KEY},
      {"HAKU_API_KEY", BuildConfig.HAKU_API_KEY},
      {"GEMINI_API_KEY_1", BuildConfig.GEMINI_API_KEY_1},
      {"GEMINI_API_KEY_2", BuildConfig.GEMINI_API_KEY_2},
      {"GEMINI_API_KEY_3", BuildConfig.GEMINI_API_KEY_3},
      {"GEMINI_API_KEY_4", BuildConfig.GEMINI_API_KEY_4},
      {"GEMINI_API_KEY_5", BuildConfig.GEMINI_API_KEY_5}
    };
  }

  private static void redactFields(Object value) throws Exception {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject)value;
      java.util.Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (key.matches("(?i).*(api.?key|secret|password|authorization|access.?token|refresh.?token|credential).*")) {
          object.put(key, "[REDACTED]");
        } else redactFields(object.opt(key));
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray)value;
      for (int i=0;i<array.length();i++) redactFields(array.opt(i));
    }
  }

  static String build(String webJson, boolean includeSensitive, int lastWorker, int lastModel) throws Exception {
    if (webJson == null || webJson.length() > 6_000_000) throw new IllegalArgumentException("diagnostic_payload_too_large");
    JSONObject credentials = new JSONObject();
    for (String[] item : keyPairs()) {
      String key = item[1] == null ? "" : item[1];
      credentials.put(item[0], key.isEmpty() ? "[NOT_CONFIGURED]" : includeSensitive ? key : "[REDACTED]");
    }
    JSONArray events = new JSONArray();
    synchronized (EVENTS) {
      for (JSONObject event : EVENTS) events.put(new JSONObject(event.toString()));
    }
    JSONObject report = new JSONObject()
      .put("format", "backroom.diagnostic.v1")
      .put("capturedAtEpochMs", System.currentTimeMillis())
      .put("containsCredentials", includeSensitive)
      .put("app", new JSONObject()
        .put("package", BuildConfig.APPLICATION_ID)
        .put("version", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("device", Build.MANUFACTURER + " " + Build.MODEL)
        .put("androidSdk", Build.VERSION.SDK_INT))
      .put("provider", new JSONObject()
        .put("lastGeminiWorker", lastWorker)
        .put("lastGeminiModel", lastModel))
      .put("credentials", credentials)
      .put("nativeEvents", events)
      .put("performanceTimings", applicationContext == null ? new JSONArray() : persistedTimings(applicationContext))
      .put("web", new JSONObject(webJson));
    if (!includeSensitive) {
      redactFields(report.getJSONObject("web"));
      redactFields(events);
    }
    String result = report.toString(2);
    if (!includeSensitive) {
      for (String[] item : keyPairs())
        if (item[1] != null && !item[1].isEmpty()) result = result.replace(item[1], "[REDACTED]");
      result = result.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+", "$1[REDACTED]");
    }
    return result;
  }
}
