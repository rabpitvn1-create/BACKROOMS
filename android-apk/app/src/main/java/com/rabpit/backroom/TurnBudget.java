package com.rabpit.backroom;

import android.os.SystemClock;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/** One monotonic deadline shared by writer, audits, repair and provider fallback. */
public final class TurnBudget {
  public interface Clock { long nowMs(); }

  private final Clock clock;
  private final long deadlineMs;

  private TurnBudget(Clock clock, long totalMs) {
    if (totalMs <= 0L) throw new IllegalArgumentException("totalMs must be positive");
    this.clock = clock;
    this.deadlineMs = clock.nowMs() + totalMs;
  }

  public static TurnBudget start(long totalMs) {
    return new TurnBudget(SystemClock::elapsedRealtime, totalMs);
  }

  public static TurnBudget start(Clock clock, long totalMs) {
    return new TurnBudget(clock, totalMs);
  }

  public long remainingMs() {
    return Math.max(0L, deadlineMs - clock.nowMs());
  }

  public int timeoutMs(int preferredMs, int minimumMs) throws SocketTimeoutException {
    long remaining = remainingMs();
    if (remaining < minimumMs) throw new SocketTimeoutException("Turn deadline exhausted.");
    return (int)Math.max(minimumMs, Math.min((long)preferredMs, Math.min(remaining, Integer.MAX_VALUE)));
  }

  public long futureTimeout(TimeUnit unit) throws SocketTimeoutException {
    long remaining = remainingMs();
    if (remaining <= 0L) throw new SocketTimeoutException("Turn deadline exhausted.");
    return Math.max(1L, unit.convert(remaining, TimeUnit.MILLISECONDS));
  }

  public void throwIfExpired() throws SocketTimeoutException {
    if (remainingMs() <= 0L) throw new SocketTimeoutException("Turn deadline exhausted.");
  }
}
