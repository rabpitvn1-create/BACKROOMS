package com.rabpit.backroom.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One deadline covers connect, upload, headers and body, including a slow drip response. */
public final class NarrationHttpTransport {
  private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
  private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(2, runnable -> {
    Thread thread = new Thread(runnable, "narration-http-deadline");
    thread.setDaemon(true);
    return thread;
  });
  static { DEADLINES.setRemoveOnCancelPolicy(true); }

  public static final class Response {
    public final int status;
    public final String body;
    public final String retryAfter;
    private Response(int status, String body, String retryAfter) {
      this.status = status; this.body = body; this.retryAfter = retryAfter;
    }
  }

  private NarrationHttpTransport() {}

  public static long deadlineAfterMillis(long milliseconds) {
    return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
  }

  public static Response post(String endpoint, Map<String, String> headers, String payload,
                              long deadlineNanos) throws IOException {
    remainingMillis(deadlineNanos);
    return post((HttpURLConnection)new URL(endpoint).openConnection(), headers, payload, deadlineNanos);
  }

  static Response post(HttpURLConnection connection, Map<String, String> headers, String payload,
                       long deadlineNanos) throws IOException {
    AtomicBoolean expired = new AtomicBoolean();
    ScheduledFuture<?> alarm = null;
    try {
      int timeout = remainingMillis(deadlineNanos);
      alarm = DEADLINES.schedule(() -> {
        expired.set(true);
        connection.disconnect();
      }, Math.max(1L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json");
      for (Map.Entry<String, String> header : headers.entrySet()) {
        connection.setRequestProperty(header.getKey(), header.getValue());
      }
      byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(bytes.length);
      try (OutputStream output = connection.getOutputStream()) {
        remainingMillis(deadlineNanos);
        output.write(bytes);
      }
      remainingMillis(deadlineNanos);
      connection.setReadTimeout(remainingMillis(deadlineNanos));
      int status = connection.getResponseCode();
      remainingMillis(deadlineNanos);
      InputStream stream = status >= 200 && status < 300
          ? connection.getInputStream() : connection.getErrorStream();
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      if (stream != null) {
        try (InputStream input = stream) {
          byte[] chunk = new byte[4096];
          while (true) {
            connection.setReadTimeout(remainingMillis(deadlineNanos));
            int count = input.read(chunk);
            remainingMillis(deadlineNanos);
            if (count < 0) break;
            if (body.size() + count > MAX_BODY_BYTES) throw new IOException("Provider response too large");
            body.write(chunk, 0, count);
          }
        }
      }
      remainingMillis(deadlineNanos);
      return new Response(status, new String(body.toByteArray(), StandardCharsets.UTF_8),
          connection.getHeaderField("Retry-After"));
    } catch (IOException error) {
      if (expired.get() || deadlineNanos - System.nanoTime() <= 0L) {
        SocketTimeoutException timeout = new SocketTimeoutException("Narration request deadline exceeded");
        timeout.initCause(error);
        throw timeout;
      }
      throw error;
    } finally {
      if (alarm != null) alarm.cancel(false);
      connection.disconnect();
    }
  }

  private static int remainingMillis(long deadlineNanos) throws SocketTimeoutException {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0L) throw new SocketTimeoutException("Narration request deadline exceeded");
    return (int)Math.max(1L, Math.min(Integer.MAX_VALUE,
        TimeUnit.NANOSECONDS.toMillis(remaining)));
  }
}
