package com.rabpit.backroom;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, human-readable diagnostic ledger for the in-game TXT export.
 *
 * The ledger intentionally stores metadata only. Callers must never pass raw prompts,
 * API credentials, authorization headers, or full private state snapshots.
 */
public final class RuntimeDiagnostics {
  private static final String NONE = "NONE";

  private static final class Event {
    final String timestamp;
    final String correlationId;
    final String component;
    final String phase;
    final String result;
    final String provider;
    final String credential;
    final String errorType;
    final String message;

    Event(
        String timestamp,
        String correlationId,
        String component,
        String phase,
        String result,
        String provider,
        String credential,
        String errorType,
        String message
    ) {
      this.timestamp = clean(timestamp);
      this.correlationId = clean(correlationId);
      this.component = clean(component);
      this.phase = clean(phase);
      this.result = clean(result);
      this.provider = clean(provider);
      this.credential = clean(credential);
      this.errorType = clean(errorType);
      this.message = clean(message);
    }

    String line() {
      return timestamp
        + " | correlation=" + value(correlationId)
        + " | component=" + value(component)
        + " | phase=" + value(phase)
        + " | result=" + value(result)
        + " | provider=" + value(provider)
        + " | credential=" + value(credential)
        + " | errorType=" + value(errorType)
        + " | message=" + value(message);
    }
  }

  private final int limit;
  private final ArrayDeque<Event> events = new ArrayDeque<>();
  private String currentCorrelationId = "";

  public RuntimeDiagnostics(int limit) {
    this.limit = Math.max(16, limit);
  }

  public synchronized void beginTurn(String correlationId) {
    currentCorrelationId = clean(correlationId);
  }

  public synchronized String currentCorrelationId() {
    return currentCorrelationId;
  }

  public synchronized void record(
      String component,
      String phase,
      String result,
      String provider,
      String credential,
      String errorType,
      String message
  ) {
    events.addLast(new Event(
      timestamp(), currentCorrelationId, component, phase, result,
      provider, credential, errorType, message
    ));
    while (events.size() > limit) events.removeFirst();
  }

  public synchronized String renderSummary() {
    Event root = rootCause();
    if (root == null) {
      return "status=OK\ncomponent=NONE\nphase=NONE\nprovider=NONE\ncredential=NONE\n"
        + "errorType=NONE\nmessage=No terminal runtime failure recorded.\n"
        + "fallbackPath=" + fallbackPath() + "\ncorrelationId=" + value(currentCorrelationId);
    }
    return "status=FAILED\n"
      + "component=" + value(root.component) + "\n"
      + "phase=" + value(root.phase) + "\n"
      + "provider=" + value(root.provider) + "\n"
      + "credential=" + value(root.credential) + "\n"
      + "errorType=" + value(root.errorType) + "\n"
      + "message=" + value(root.message) + "\n"
      + "fallbackPath=" + fallbackPath() + "\n"
      + "correlationId=" + value(root.correlationId);
  }

  public synchronized String renderRootCause() {
    Event root = rootCause();
    return root == null ? "(none)" : root.line();
  }

  public synchronized String renderTimeline() {
    if (events.isEmpty()) return "(none)";
    StringBuilder out = new StringBuilder();
    for (Event event : events) out.append(event.line()).append('\n');
    return out.toString().trim();
  }

  public static String redact(String input, String... secrets) {
    String safe = input == null ? "" : input;
    if (secrets != null) {
      for (String secret : secrets) {
        if (secret != null && secret.length() >= 4) safe = safe.replace(secret, "[REDACTED]");
      }
    }
    safe = safe.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [REDACTED]");
    safe = safe.replaceAll("(?i)sk-[A-Za-z0-9_-]{8,}", "[REDACTED]");
    safe = safe.replaceAll("AIza[A-Za-z0-9_-]{20,}", "[REDACTED]");
    return safe;
  }

  private Event rootCause() {
    List<Event> scope = scopedEvents();
    for (int i = scope.size() - 1; i >= 0; i--) {
      Event event = scope.get(i);
      if ("BLOCKED".equalsIgnoreCase(event.result)) return event;
    }
    for (int i = scope.size() - 1; i >= 0; i--) {
      Event event = scope.get(i);
      if ("FAILED_FINAL".equalsIgnoreCase(event.result)) {
        // A generic provider-chain wrapper is less useful than the concrete provider
        // failure immediately before it, so prefer that concrete error when present.
        if (event.message.toLowerCase(Locale.ROOT).contains("provider chain failed")) continue;
        return event;
      }
    }
    for (int i = scope.size() - 1; i >= 0; i--) {
      Event event = scope.get(i);
      if ("FAILED".equalsIgnoreCase(event.result)) return event;
    }
    return null;
  }

  private List<Event> scopedEvents() {
    List<Event> all = new ArrayList<>(events);
    if (currentCorrelationId.isEmpty()) return all;
    List<Event> scoped = new ArrayList<>();
    for (Event event : all) {
      if (currentCorrelationId.equals(event.correlationId)) scoped.add(event);
    }
    return scoped.isEmpty() ? all : scoped;
  }

  private String fallbackPath() {
    StringBuilder out = new StringBuilder();
    String previous = "";
    for (Event event : scopedEvents()) {
      boolean providerEvent = !event.credential.isEmpty()
        || "PROVIDER_ROUTE".equalsIgnoreCase(event.phase)
        || "FALLBACK".equalsIgnoreCase(event.phase);
      if (!providerEvent) continue;
      String token = !event.credential.isEmpty()
        ? value(event.provider) + "_" + event.credential
        : value(event.provider);
      if ("FAILED".equalsIgnoreCase(event.result) || "FAILED_TRANSIENT".equalsIgnoreCase(event.result)) {
        token += "[FAILED]";
      } else if ("OK".equalsIgnoreCase(event.result)) {
        token += "[OK]";
      }
      if (token.equals(previous)) continue;
      if (out.length() > 0) out.append(" -> ");
      out.append(token);
      previous = token;
    }
    return out.length() == 0 ? NONE : out.toString();
  }

  private static String timestamp() {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
  }

  private static String clean(String value) {
    if (value == null) return "";
    String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').trim();
    return normalized.length() <= 700 ? normalized : normalized.substring(0, 700) + "…";
  }

  private static String value(String value) {
    return value == null || value.isEmpty() ? NONE : value;
  }
}
