package com.rabpit.backroom.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticLogTest {
  @Test public void exportPreservesPayloadStackAndTraceButRedactsCredentials() throws Exception {
    File directory = Files.createTempDirectory("backroom-diagnostics").toFile();
    File export = new File(directory, "export.jsonl");
    String secret = "fake-test-key-123456";
    DiagnosticLog.initialize(directory, secret);
    DiagnosticLog.snapshot(export, new JSONObject());
    long priorFailures = new JSONObject(Files.readAllLines(export.toPath()).get(0)).getJSONObject("details").getLong("writeFailures");
    DiagnosticLog.beginTrace("turn-test");
    DiagnosticLog.record("provider.request", "payload", new JSONObject().put("prompt", "inspect scene").put("api_key", secret));
    DiagnosticLog.record("provider.error", "error", new IllegalStateException("Bearer " + secret));
    DiagnosticLog.record("malformed", "body", "not JSON {broken " + secret);
    DiagnosticLog.endTrace();
    DiagnosticLog.snapshot(export, new JSONObject().put("versionName", "test"));
    String text = new String(Files.readAllBytes(export.toPath()), StandardCharsets.UTF_8);
    assertFalse(text.contains(secret));
    assertTrue(text.contains("[REDACTED]"));
    boolean request = false, error = false, malformed = false;
    for (String line : text.split("\n")) {
      JSONObject entry = new JSONObject(line);
      if ("provider.request".equals(entry.optString("event"))) {
        request = true;
        assertTrue(entry.getString("traceId").startsWith("turn-test:"));
        assertEquals("inspect scene", entry.getJSONObject("details").getJSONObject("payload").getString("prompt"));
        assertTrue(entry.getString("source").contains("DiagnosticLogTest"));
      }
      if ("provider.error".equals(entry.optString("event"))) {
        error = true;
        assertTrue(entry.getJSONObject("details").getString("error").contains("IllegalStateException"));
      }
      if ("malformed".equals(entry.optString("event"))) malformed = true;
    }
    assertTrue(request && error && malformed);
    JSONObject metadata = new JSONObject(text.split("\n")[0]).getJSONObject("details");
    assertEquals(priorFailures, metadata.getLong("writeFailures"));
    assertTrue(metadata.has("readingGuide"));
  }

  @Test public void loggingFailureDoesNotThrowAndExportReportsIncompleteEvidence() throws Exception {
    File notDirectory = File.createTempFile("backroom-invalid-log", ".tmp");
    DiagnosticLog.initialize(notDirectory, "another-fake-key");
    DiagnosticLog.record("failure", "state", "{}");
    File output = File.createTempFile("backroom-failure-export", ".jsonl");
    DiagnosticLog.snapshot(output, new JSONObject());
    JSONObject metadata = new JSONObject(Files.readAllLines(output.toPath()).get(0)).getJSONObject("details");
    assertTrue(metadata.getLong("writeFailures") >= 2);
  }

  @Test public void rotationReportsDiscardedHistoryAndRetainsWholeEvents() throws Exception {
    File directory = Files.createTempDirectory("backroom-rotating-log").toFile();
    DiagnosticLog.initialize(directory);
    File output = new File(directory, "snapshot.jsonl");
    DiagnosticLog.snapshot(output, new JSONObject());
    long priorDiscarded = new JSONObject(Files.readAllLines(output.toPath()).get(0)).getJSONObject("details").getLong("discardedSegmentsThisSession");
    String payload = new String(new char[2 * 1024 * 1024]).replace('\0', 'x');
    for (int i=0;i<34;i++) DiagnosticLog.record("large.event", "index", i, "payload", payload);
    DiagnosticLog.snapshot(output, new JSONObject());
    try (java.io.BufferedReader input = Files.newBufferedReader(output.toPath())) {
      JSONObject metadata = new JSONObject(input.readLine()).getJSONObject("details");
      assertTrue(metadata.getLong("discardedSegmentsThisSession") > priorDiscarded);
      String line; int retained = 0;
      while ((line = input.readLine()) != null) {
        JSONObject event = new JSONObject(line);
        if (!"large.event".equals(event.optString("event"))) continue;
        assertEquals(payload.length(), event.getJSONObject("details").getString("payload").length());
        retained++;
      }
      assertTrue(retained > 0 && retained < 34);
    }
  }

  @Test public void concurrentWritesProduceParseableOrderedEventsAndStableSnapshot() throws Exception {
    File directory = Files.createTempDirectory("backroom-concurrent-log").toFile();
    DiagnosticLog.initialize(directory);
    Thread a = new Thread(() -> { for (int i=0;i<30;i++) DiagnosticLog.record("worker.a", "index", i); });
    Thread b = new Thread(() -> { for (int i=0;i<30;i++) DiagnosticLog.record("worker.b", "index", i); });
    a.start(); b.start(); a.join(); b.join();
    File output = new File(directory, "snapshot.jsonl");
    DiagnosticLog.snapshot(output, new JSONObject());
    List<String> lines = Files.readAllLines(output.toPath());
    assertEquals(62, lines.size());
    long last = 0;
    for (int i=1;i<lines.size();i++) {
      long sequence = new JSONObject(lines.get(i)).getLong("sequence");
      assertTrue(sequence > last); last = sequence;
    }
    DiagnosticLog.record("after.snapshot");
    assertEquals(lines, Files.readAllLines(output.toPath()));
  }
}
