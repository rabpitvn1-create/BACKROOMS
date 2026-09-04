package com.rabpit.backroom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeDiagnosticsTest {
  @Test public void summaryKeepsConcreteAuditRootCauseAndFallbackPath() {
    RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(32);
    diagnostics.beginTurn("turn-12");
    diagnostics.record("GEMINI", "PROVIDER_REQUEST", "START", "GEMINI", "K1", "", "request");
    diagnostics.record("GEMINI", "PROVIDER_RESPONSE", "FAILED_TRANSIENT", "GEMINI", "K1", "HttpError", "HTTP 429");
    diagnostics.record("GEMINI", "PROVIDER_REQUEST", "START", "GEMINI", "K2", "", "request");
    diagnostics.record("GEMINI", "PROVIDER_RESPONSE", "OK", "GEMINI", "K2", "", "response accepted");
    diagnostics.record("CANON_AUDIT", "CANON_GATE", "BLOCKED", "GEMINI", "K2", "state_narrative_mismatch",
      "source=android_reducer claim=set_level reason=Android reducer rejected operation");

    String summary = diagnostics.renderSummary();
    assertTrue(summary.contains("component=CANON_AUDIT"));
    assertTrue(summary.contains("phase=CANON_GATE"));
    assertTrue(summary.contains("state_narrative_mismatch"));
    assertTrue(summary.contains("GEMINI_K1[FAILED]"));
    assertTrue(summary.contains("GEMINI_K2[OK]"));
    assertTrue(diagnostics.renderRootCause().contains("source=android_reducer"));
  }

  @Test public void transientProviderFailureAloneIsNotTerminalRootCause() {
    RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(32);
    diagnostics.beginTurn("turn-3");
    diagnostics.record("GEMINI", "PROVIDER_RESPONSE", "FAILED_TRANSIENT", "GEMINI", "K1", "HttpError", "HTTP 429");
    diagnostics.record("GEMINI", "PROVIDER_RESPONSE", "OK", "GEMINI", "K2", "", "ok");
    assertTrue(diagnostics.renderSummary().contains("status=OK"));
  }

  @Test public void redactionRemovesConfiguredAndBearerSecrets() {
    String secret = "AIzaExampleSecretValue1234567890";
    String raw = "key=" + secret + " Authorization: Bearer abc.def.ghi sk-secret_123456";
    String safe = RuntimeDiagnostics.redact(raw, secret);
    assertFalse(safe.contains(secret));
    assertFalse(safe.contains("abc.def.ghi"));
    assertFalse(safe.contains("sk-secret_123456"));
    assertTrue(safe.contains("[REDACTED]"));
  }

  @Test public void ledgerRemainsBounded() {
    RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(16);
    diagnostics.beginTurn("turn-bounded");
    for (int i = 0; i < 40; i++) {
      diagnostics.record("TEST", "PHASE", "OK", "", "", "", "event-" + i);
    }
    String timeline = diagnostics.renderTimeline();
    assertFalse(timeline.contains("event-0"));
    assertTrue(timeline.contains("event-39"));
  }
}
