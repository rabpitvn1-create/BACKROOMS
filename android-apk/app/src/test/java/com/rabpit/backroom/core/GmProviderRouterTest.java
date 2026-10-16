package com.rabpit.backroom.core;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class GmProviderRouterTest {
  @Test public void successfulGeminiNeverCallsGehihi() throws Exception {
    AtomicInteger fallbackCalls = new AtomicInteger();
    String result = GmProviderRouter.execute(
      () -> "{\"reply\":\"gemini\"}",
      () -> { fallbackCalls.incrementAndGet(); return "gehihi"; },
      error -> fallbackCalls.incrementAndGet());
    assertEquals("{\"reply\":\"gemini\"}", result);
    assertEquals(0, fallbackCalls.get());
  }

  @Test public void failedGeminiSwitchesToGehihiOnce() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<Exception> recorded = new AtomicReference<>();
    Exception geminiFailure = new Exception("quota");
    String result = GmProviderRouter.execute(
      () -> { calls.incrementAndGet(); throw geminiFailure; },
      () -> { calls.incrementAndGet(); return "{\"reply\":\"gehihi\"}"; },
      recorded::set);
    assertEquals("{\"reply\":\"gehihi\"}", result);
    assertEquals(2, calls.get());
    assertSame(geminiFailure, recorded.get());
  }

  @Test public void bothProvidersFailWithoutMaskingFallbackError() {
    Exception geminiFailure = new Exception("Gemini unavailable");
    Exception gehihiFailure = new Exception("Gehihi unavailable");
    Exception actual = assertThrows(Exception.class, () -> GmProviderRouter.execute(
      () -> { throw geminiFailure; },
      () -> { throw gehihiFailure; },
      error -> {}));
    assertSame(gehihiFailure, actual);
    assertArrayEquals(new Throwable[] {geminiFailure}, actual.getSuppressed());
  }
}
