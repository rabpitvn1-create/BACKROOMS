package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.util.UUID;

/** Structured, private diagnostics. Logging failures must never change game outcomes. */
public final class DiagnosticLog {
  // ponytail: retain two 32 MiB segments; a streaming archive is the upgrade path.
  private static final long SEGMENT_BYTES = 32L * 1024 * 1024;
  private static File active, previous;
  private static String[] secrets = new String[0];
  private static final String SESSION = UUID.randomUUID().toString();
  private static final ThreadLocal<String> TRACE = new ThreadLocal<>();
  private static long sequence, failures, discardedSegments;
  private static boolean crashHandlerInstalled;

  private DiagnosticLog() {}

  public static synchronized void initialize(File directory, String... credentials) {
    secrets = credentials.clone();
    active = new File(directory, "diagnostic-current.jsonl");
    previous = new File(directory, "diagnostic-previous.jsonl");
    record("session.start", "session", SESSION);
    if (!crashHandlerInstalled) {
      Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
        record("app.uncaught", "thread", thread.getName(), "error", error);
        if (previousHandler != null) previousHandler.uncaughtException(thread, error);
        else System.err.println(redact(error.toString()));
      });
      crashHandlerInstalled = true;
    }
  }

  private static String utcNow() {
    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT);
    format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    return format.format(new java.util.Date());
  }

  private static void copy(File file, FileOutputStream output) throws IOException {
    try (FileInputStream input = new FileInputStream(file)) {
      byte[] bytes = new byte[8192]; int count;
      while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
    }
  }

  public static void beginTrace(String name) {
    TRACE.set(name + ":" + UUID.randomUUID());
  }
  public static void endTrace() { TRACE.remove(); }

  public static synchronized String redact(String input) {
    String text = input == null ? "" : input;
    for (String secret : secrets) {
      if (secret == null || secret.isEmpty()) continue;
      text = text.replace(secret, "[REDACTED]");
      String quoted = JSONObject.quote(secret);
      if (quoted.length() > 2) text = text.replace(quoted.substring(1, quoted.length() - 1), "[REDACTED]");
      try { text = text.replace(java.net.URLEncoder.encode(secret, "UTF-8"), "[REDACTED]"); }
      catch (Exception ignored) {}
    }
    text = text.replaceAll("(?i)(Bearer\\s+)[^\\s\\\"'<>]+", "$1[REDACTED]");
    text = text.replaceAll("(?i)((?:api[_-]?key|x-api-key|x-goog-api-key|authorization|access[_-]?token|password)\\\"?\\s*[:=]\\s*\\\"?)[^\\s\\\"&,}]+", "$1[REDACTED]");
    return text;
  }

  public static synchronized void record(String event, Object... fields) {
    if (active == null) return;
    try {
      JSONObject details = new JSONObject();
      for (int i = 0; i + 1 < fields.length; i += 2) {
        Object value = fields[i + 1];
        if (value instanceof Throwable) {
          StringWriter stack = new StringWriter();
          ((Throwable)value).printStackTrace(new PrintWriter(stack));
          value = stack.toString();
        } else if (value instanceof String) {
          String raw = ((String)value).trim();
          try {
            if (raw.startsWith("{")) value = new JSONObject(raw);
            else if (raw.startsWith("[")) value = new JSONArray(raw);
          } catch (Exception ignored) { /* Preserve malformed payload verbatim. */ }
        }
        details.put(String.valueOf(fields[i]), value == null ? JSONObject.NULL : value);
      }
      String caller = "unknown";
      for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
        if (!frame.getClassName().equals(DiagnosticLog.class.getName()) && !frame.getClassName().equals(Thread.class.getName())) {
          caller = frame.toString(); break;
        }
      }
      JSONObject entry = new JSONObject().put("schema", "backroom.diagnostics.v1")
          .put("session", SESSION).put("sequence", ++sequence)
          .put("timestampUtc", utcNow())
          .put("thread", Thread.currentThread().getName())
          .put("traceId", TRACE.get() == null ? "background:" + Thread.currentThread().getName() : TRACE.get())
          .put("source", caller).put("event", event).put("details", details);
      byte[] bytes = (redact(entry.toString()) + "\n").getBytes(StandardCharsets.UTF_8);
      if (active.length() > 0 && active.length() + bytes.length > SEGMENT_BYTES) {
        if (previous.exists()) {
          if (!previous.delete()) throw new IOException("Cannot rotate diagnostic archive");
          discardedSegments++;
        }
        if (!active.renameTo(previous)) throw new IOException("Cannot rotate diagnostic log");
      }
      try (FileOutputStream output = new FileOutputStream(active, true)) { output.write(bytes); }
    } catch (Exception error) { failures++; }
  }

  /** Snapshot is consistent with event writes; exported file outlives subsequent rotations. */
  public static synchronized void snapshot(File target, JSONObject metadata) throws Exception {
    if (active == null) throw new IOException("Diagnostic logging was not initialized");
    metadata.put("schema", "backroom.diagnostics.v1").put("exportedAtUtc", utcNow())
        .put("session", SESSION).put("writeFailures", failures).put("discardedSegmentsThisSession", discardedSegments)
        .put("retention", "Two 32 MiB segments; older history may be absent. No per-event truncation.")
        .put("coverage", "Instrumented Core/bridge/UI/provider/cache/guard boundaries and state changes, not every instruction or provider private reasoning.")
        .put("readingGuide", "Group by session/traceId; order by sequence within each session. Compare Core input/state.commit, provider request/response, narration.guard, bridge.emit. Locate source and stack for errors. Read writeFailures/retention before inferring missing events.")
        .put("sourceMap", new JSONObject().put("core", "android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.java")
            .put("aiBridge", "android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java")
            .put("ui", "android-apk/app/src/main/assets/index.html"));
    try (FileOutputStream output = new FileOutputStream(target)) {
      output.write((redact(new JSONObject().put("event", "export.metadata").put("details", metadata).toString()) + "\n").getBytes(StandardCharsets.UTF_8));
      if (previous.exists()) copy(previous, output);
      if (active.exists()) copy(active, output);
    }
  }
}
