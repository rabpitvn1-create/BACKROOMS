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

  private static String[][] keyPairs() {
    return new String[][]{
      {"GEHIHI_API_KEY", BuildConfig.GEHIHI_API_KEY},
      {"SOL_API_KEY", BuildConfig.SOL_API_KEY},
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

  static String build(String webJson, boolean includeSensitive, String milestoneProvider, int inFlight, int lastWorker, int lastModel) throws Exception {
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
        .put("milestone", milestoneProvider)
        .put("writer", "GEHIHI")
        .put("lastGeminiWorker", lastWorker)
        .put("lastGeminiModel", lastModel)
        .put("milestonePlansInFlight", inFlight))
      .put("credentials", credentials)
      .put("nativeEvents", events)
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
