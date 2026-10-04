package com.rabpit.backroom.core;

import java.util.Calendar;
import java.util.TimeZone;

/** Per-source health and admission control; identifiers never contain credentials. */
public final class NarrationProviderScheduler {
  public static final int GEMINI_COUNT = 5;
  public static final int HAKU = 5;
  public static final int LUNA = 6;
  public static final int SOL = 7;
  public static final int GEHIHI = 8;
  public static final int SOURCE_COUNT = 9;
  private final boolean[] busyBackground = new boolean[SOURCE_COUNT];
  private final boolean[] busyForeground = new boolean[SOURCE_COUNT];
  private final boolean[] disabled = new boolean[SOURCE_COUNT];
  private final long[] retryAt = new long[SOURCE_COUNT];
  private final long[] latency = new long[SOURCE_COUNT];
  private final int[] failures = new int[SOURCE_COUNT];
  private final long[] healthVersion = new long[SOURCE_COUNT];
  private final long[] backgroundVersion = new long[SOURCE_COUNT];
  private final long[] foregroundVersion = new long[SOURCE_COUNT];
  private int cursor;
  private int overloadFailures;
  private long geminiRetryAt;

  /** Reserve one available source. Gemini aliases represent separately configured projects. */
  public synchronized int acquire(boolean background, boolean urgent, boolean[] configured,
                                  boolean[] attempted, int geminiAttempts, long now) {
    int gemini = -1;
    int geminiLimit = background && !urgent ? 2 : 1;
    if (geminiAttempts < geminiLimit && now >= geminiRetryAt) {
      for (int offset = 0; offset < GEMINI_COUNT; offset++) {
        int candidate = (cursor + offset) % GEMINI_COUNT;
        if (available(candidate, background, configured, attempted, now)) { gemini = candidate; break; }
      }
    }
    boolean foregroundPriority = !background || urgent;
    int best = gemini;
    long bestScore = gemini < 0 ? Long.MAX_VALUE : score(gemini, foregroundPriority);
    for (int source = HAKU; source < SOURCE_COUNT; source++) {
      if (!available(source, background, configured, attempted, now)) continue;
      long candidateScore = score(source, foregroundPriority);
      if (candidateScore < bestScore) { best = source; bestScore = candidateScore; }
    }
    if (best >= 0) {
      (background ? busyBackground : busyForeground)[best] = true;
      (background ? backgroundVersion : foregroundVersion)[best] = healthVersion[best];
      if (best < GEMINI_COUNT) cursor = (best + 1) % GEMINI_COUNT;
    }
    return best;
  }

  public synchronized void succeeded(int source, boolean background, long elapsedMs) {
    (background ? busyBackground : busyForeground)[source] = false;
    boolean fresh = (background ? backgroundVersion : foregroundVersion)[source] == healthVersion[source];
    if (fresh) { failures[source] = 0; retryAt[source] = 0; }
    latency[source] = latency[source] == 0 ? Math.max(1, elapsedMs)
        : (latency[source] * 3 + Math.max(1, elapsedMs)) / 4;
    if (source < GEMINI_COUNT && fresh) overloadFailures = 0;
  }

  public synchronized void failed(int source, boolean background, int status, long now,
                                  long retryAfterMs, long dailyResetDelayMs, long jitterMs) {
    (background ? busyBackground : busyForeground)[source] = false;
    healthVersion[source]++;
    failures[source] = Math.min(6, failures[source] + 1);
    if (status == 401 || status == 403) disabled[source] = true;
    long delay = Math.min(60_000L, 1_000L << failures[source]);
    if (status == 429) delay = Math.max(30_000L, Math.max(retryAfterMs, dailyResetDelayMs));
    else delay = Math.max(delay, retryAfterMs);
    delay = Math.min(7L * 24 * 60 * 60_000L, delay);
    retryAt[source] = Math.max(retryAt[source], now + delay + Math.max(0L, Math.min(500L, jitterMs)));
    // Two overloaded Gemini sources indicate a shared model outage, not five useful retries.
    if (source < GEMINI_COUNT && status == 503 && ++overloadFailures >= 2) {
      geminiRetryAt = now + 30_000L;
    }
  }

  public static long nextPacificDailyResetDelay(long wallClockMs) {
    Calendar reset = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
    reset.setTimeInMillis(wallClockMs);
    reset.add(Calendar.DAY_OF_MONTH, 1);
    reset.set(Calendar.HOUR_OF_DAY, 0);
    reset.set(Calendar.MINUTE, 0);
    reset.set(Calendar.SECOND, 0);
    reset.set(Calendar.MILLISECOND, 0);
    return Math.max(1L, reset.getTimeInMillis() - wallClockMs);
  }

  private boolean available(int source, boolean background, boolean[] configured, boolean[] attempted, long now) {
    boolean busy = (background ? busyBackground : busyForeground)[source];
    // Gehihi has one reserved lane per workload; fallback sources admit one request total.
    if (source != GEHIHI) busy = busyBackground[source] || busyForeground[source];
    return configured[source] && !attempted[source] && !busy
        && !disabled[source] && now >= retryAt[source];
  }

  private long score(int source, boolean foregroundPriority) {
    long measured = latency[source] == 0 ? 2_000L : latency[source];
    long preference;
    if (foregroundPriority) {
      preference = source == GEHIHI ? 0L : source == HAKU ? 1_000L : source == SOL ? 2_000L : source < GEMINI_COUNT ? 3_000L : 5_000L;
    } else {
      preference = source == GEHIHI ? 0L : source == HAKU ? 1_000L : source < GEMINI_COUNT ? 2_000L : source == SOL ? 3_000L : 5_000L;
    }
    return measured + preference;
  }
}
