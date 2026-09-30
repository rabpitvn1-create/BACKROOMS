package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProviderRetryPolicyTest {
  @Test public void malformedJsonDoesNotRetryOrRotateKeys() {
    assertTrue(ProviderRetryPolicy.isContentOrJsonError(0, "AI trả JSON không hợp lệ."));
    assertFalse(ProviderRetryPolicy.shouldRetrySameProvider(0, "AI trả JSON không hợp lệ."));
    assertFalse(ProviderRetryPolicy.shouldRotateGeminiKey(0, "AI trả JSON không hợp lệ."));
  }

  @Test public void transientProviderFailuresRemainRetryable() {
    assertTrue(ProviderRetryPolicy.shouldRetrySameProvider(503, "server unavailable"));
    assertTrue(ProviderRetryPolicy.shouldRetrySameProvider(429, "rate limited"));
    assertTrue(ProviderRetryPolicy.shouldRotateGeminiKey(503, "server unavailable"));
  }

  @Test public void authMayRotateKeyButBadRequestDoesNot() {
    assertTrue(ProviderRetryPolicy.shouldRotateGeminiKey(401, "invalid key"));
    assertFalse(ProviderRetryPolicy.shouldRetrySameProvider(401, "invalid key"));
    assertFalse(ProviderRetryPolicy.shouldRotateGeminiKey(400, "bad request"));
  }
}
