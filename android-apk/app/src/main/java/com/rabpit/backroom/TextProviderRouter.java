package com.rabpit.backroom;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.Callable;

/** Routes text requests without exposing credentials or provider response bodies. */
public final class TextProviderRouter {
  public interface GeminiCall { String call(String key, int index) throws Exception; }
  public static class HttpFailure extends Exception {
    public final int status;
    public final long retryAfterMs;
    public HttpFailure(int status, long retryAfterMs) {
      super("HTTP " + status);
      this.status = status;
      this.retryAfterMs = retryAfterMs;
    }
  }
  private final LongSupplier clock;
  private final long[] cooldownUntil = new long[5];
  private int nextKey;
  public TextProviderRouter() { this(System::currentTimeMillis); }
  public TextProviderRouter(LongSupplier clock) { this.clock = clock; }

  public String generate(boolean primaryConfigured, Callable<String> primary,
      String[] keys, GeminiCall gemini, Consumer<String> onAttempt) throws Exception {
    String primaryFailure = "chưa cấu hình";
    if (primaryConfigured) {
      onAttempt.accept("Gehihi");
      try { return primary.call(); }
      catch (Exception error) { primaryFailure = safeFailure(error); }
    }
    String fallbackFailure = "không có key khả dụng";
    Set<String> used = new HashSet<>();
    final int start = nextKey;
    for (int offset = 0; offset < 5; offset++) {
      int index = (start + offset) % 5;
      String key = index < keys.length && keys[index] != null ? keys[index].trim() : "";
      if (key.isEmpty() || !used.add(key) || clock.getAsLong() < cooldownUntil[index]) continue;
      onAttempt.accept("Gemini K" + (index + 1));
      try {
        String result = gemini.call(key, index);
        nextKey = (index + 1) % 5;
        return result;
      } catch (Exception error) {
        fallbackFailure = safeFailure(error);
        if (error instanceof HttpFailure) {
          HttpFailure failure = (HttpFailure) error;
          if (failure.status == 429 || failure.status == 401 || failure.status == 403) {
            long delay = failure.status == 429 ? Math.max(60_000L, failure.retryAfterMs) : 300_000L;
            cooldownUntil[index] = clock.getAsLong() + Math.min(delay, 86_400_000L);
          }
        }
      }
    }
    throw new Exception("Gehihi: " + primaryFailure + "; Gemini: " + fallbackFailure + ".");
  }

  private static String safeFailure(Exception error) {
    return error instanceof HttpFailure ? "HTTP " + ((HttpFailure) error).status : "yêu cầu thất bại";
  }
}
