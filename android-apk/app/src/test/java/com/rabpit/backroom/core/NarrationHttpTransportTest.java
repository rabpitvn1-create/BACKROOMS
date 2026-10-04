package com.rabpit.backroom.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class NarrationHttpTransportTest {
  private static final class Connection extends HttpURLConnection {
    final CountDownLatch disconnected = new CountDownLatch(1);
    final ByteArrayOutputStream upload = new ByteArrayOutputStream();
    boolean blockUpload;
    boolean drip;
    boolean failRead;
    boolean streamClosed;
    int status = 200;
    int reads;
    Connection() throws Exception { super(new URL("http://localhost/unused")); }
    @Override public void disconnect() { disconnected.countDown(); }
    @Override public boolean usingProxy() { return false; }
    @Override public void connect() {}
    @Override public OutputStream getOutputStream() {
      if (!blockUpload) return upload;
      return new OutputStream() {
        @Override public void write(int value) throws IOException {
          try {
            if (!disconnected.await(3, TimeUnit.SECONDS)) throw new IOException("Watchdog did not disconnect");
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException(error);
          }
          throw new IOException("Connection closed during upload");
        }
      };
    }
    @Override public int getResponseCode() { return status; }
    @Override public String getHeaderField(String key) { return "Retry-After".equals(key) ? "120" : null; }
    @Override public InputStream getErrorStream() { return getInputStream(); }
    @Override public InputStream getInputStream() {
      if (!drip && !failRead) return new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)) {
        @Override public void close() throws IOException { streamClosed = true; super.close(); }
      };
      return new InputStream() {
        @Override public int read() throws IOException {
          if (failRead) throw new IOException("Broken body");
          try { Thread.sleep(20); }
          catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
          if (disconnected.getCount() == 0) throw new IOException("Connection closed");
          return ++reads < 200 ? 'x' : -1;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
          int value = read();
          if (value >= 0) buffer[offset] = (byte)value;
          return value < 0 ? -1 : 1;
        }
        @Override public void close() { streamClosed = true; }
      };
    }
  }

  @Test public void successPreservesHeadersUtf8BodyAndClosesConnection() throws Exception {
    Connection connection = new Connection();
    NarrationHttpTransport.Response response = NarrationHttpTransport.post(connection,
        Collections.singletonMap("Authorization", "Bearer test-only"), "Cao Minh: tiếng Việt",
        NarrationHttpTransport.deadlineAfterMillis(1000));
    assertEquals(200, response.status);
    assertEquals("{\"ok\":true}", response.body);
    assertEquals("Cao Minh: tiếng Việt", new String(connection.upload.toByteArray(), StandardCharsets.UTF_8));
    assertEquals("Bearer test-only", connection.getRequestProperty("Authorization"));
    assertEquals(0, connection.disconnected.getCount());
    assertTrue(connection.streamClosed);
  }

  @Test public void errorStatusPreservesQuotaBodyAndRetryAfter() throws Exception {
    Connection connection = new Connection(); connection.status = 429;
    NarrationHttpTransport.Response response = NarrationHttpTransport.post(connection,
        Collections.emptyMap(), "{}", NarrationHttpTransport.deadlineAfterMillis(1000));
    assertEquals(429, response.status);
    assertEquals("120", response.retryAfter);
    assertEquals("{\"ok\":true}", response.body);
    assertTrue(connection.streamClosed);
    assertEquals(0, connection.disconnected.getCount());
  }

  @Test public void failedReadStillClosesBodyAndConnection() throws Exception {
    Connection connection = new Connection(); connection.failRead = true;
    try {
      NarrationHttpTransport.post(connection, Collections.emptyMap(), "{}",
          NarrationHttpTransport.deadlineAfterMillis(1000));
      fail("Expected broken body");
    } catch (IOException expected) { assertEquals("Broken body", expected.getMessage()); }
    assertTrue(connection.streamClosed);
    assertEquals(0, connection.disconnected.getCount());
  }

  @Test public void slowDripCannotRestartTheDeadlineForEveryByte() throws Exception {
    Connection connection = new Connection(); connection.drip = true;
    long started = System.nanoTime();
    try {
      NarrationHttpTransport.post(connection, Collections.emptyMap(), "{}",
          NarrationHttpTransport.deadlineAfterMillis(150));
      fail("Expected absolute deadline");
    } catch (SocketTimeoutException expected) {}
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
    assertTrue(connection.reads < 200);
    assertTrue(connection.streamClosed);
    assertEquals(0, connection.disconnected.getCount());
  }

  @Test public void stalledUploadIsDisconnectedAtTheSameDeadline() throws Exception {
    Connection connection = new Connection(); connection.blockUpload = true;
    long started = System.nanoTime();
    try {
      NarrationHttpTransport.post(connection, Collections.emptyMap(), "{}",
          NarrationHttpTransport.deadlineAfterMillis(150));
      fail("Expected upload deadline");
    } catch (SocketTimeoutException expected) {}
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
    assertEquals(0, connection.disconnected.getCount());
  }

  @Test public void expiredSharedBudgetDoesNotStartAnotherUpload() throws Exception {
    Connection connection = new Connection();
    try {
      NarrationHttpTransport.post(connection, Collections.emptyMap(), "{}", System.nanoTime() - 1);
      fail("Expected exhausted shared budget");
    } catch (SocketTimeoutException expected) {}
    assertEquals(0, connection.upload.size());
    assertEquals(0, connection.disconnected.getCount());
  }

  @Test public void realHttpConnectionStopsAContinuouslyDrippingResponse() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      CountDownLatch responseStarted = new CountDownLatch(1);
      Thread writer = new Thread(() -> {
        try (Socket socket = server.accept()) {
          socket.setSoTimeout(2000);
          BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
          String line; int length = 0;
          while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) length = Integer.parseInt(line.substring(15).trim());
          }
          for (int i = 0; i < length; i++) reader.read();
          OutputStream output = socket.getOutputStream();
          output.write("HTTP/1.1 200 OK\r\nContent-Length: 200\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
          output.flush(); responseStarted.countDown();
          for (int i = 0; i < 200; i++) {
            output.write('x'); output.flush(); Thread.sleep(20);
          }
        } catch (IOException expectedDisconnect) {
          // The client closes the socket before this four-second response finishes.
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      }, "test-drip-server");
      writer.setDaemon(true); writer.start();
      long started = System.nanoTime();
      try {
        NarrationHttpTransport.post("http://127.0.0.1:" + server.getLocalPort() + "/",
            Collections.emptyMap(), "{}", NarrationHttpTransport.deadlineAfterMillis(500));
        fail("Expected real socket deadline");
      } catch (SocketTimeoutException expected) {}
      assertEquals(0, responseStarted.getCount());
      assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
      writer.join(1000);
    }
  }
}
