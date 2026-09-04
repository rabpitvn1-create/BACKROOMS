package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class AiProviderRouterTest {
  private static final class RecordingObserver implements AiProviderRouter.Observer {
    final List<String> events = new ArrayList<>();

    @Override public void onSelected(String provider) {
      events.add("selected:" + provider);
    }

    @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
      events.add("fallback:" + fromProvider + "->" + toProvider);
    }
  }

  @Test public void hakuSuccessDoesNotCallLunaOrGemini() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    AtomicInteger gemini = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { haku.incrementAndGet(); return "haku-result"; },
      prompt -> { luna.incrementAndGet(); return "luna-result"; },
      error -> true,
      observer
    );

    assertEquals("haku-result", result);
    assertEquals(1, haku.get());
    assertEquals(0, luna.get());
    assertEquals(0, gemini.get());
    assertEquals(List.of("selected:HAKU"), observer.events);
  }

  @Test public void fallbackEligibleHakuFailureCallsLunaOnly() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    AtomicInteger gemini = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { haku.incrementAndGet(); throw new Exception("provider unavailable"); },
      prompt -> { luna.incrementAndGet(); return "luna-result"; },
      error -> true,
      observer
    );

    assertEquals("luna-result", result);
    assertEquals(1, haku.get());
    assertEquals(1, luna.get());
    assertEquals(0, gemini.get());
    assertEquals(
      List.of("selected:HAKU", "fallback:HAKU->LUNA", "selected:LUNA"),
      observer.events
    );
  }

  @Test public void malformedHakuResponseFallsBackToValidatedLunaResponse() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { haku.incrementAndGet(); return "plain prose, not JSON"; },
      prompt -> { luna.incrementAndGet(); return "{\"reply\":\"ok\"}"; },
      error -> true,
      (provider, response) -> {
        String text = response == null ? "" : response.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
          throw new Exception("AI trả JSON không hợp lệ.");
        }
        return text;
      },
      observer
    );

    assertEquals("{\"reply\":\"ok\"}", result);
    assertEquals(1, haku.get());
    assertEquals(1, luna.get());
    assertEquals(
      List.of("selected:HAKU", "fallback:HAKU->LUNA", "selected:LUNA"),
      observer.events
    );
  }

  @Test public void hakuAndLunaFailureReturnsControlledErrorWithoutGemini() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    AtomicInteger gemini = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    try {
      AiProviderRouter.route(
        "prompt",
        prompt -> { haku.incrementAndGet(); throw new Exception("haku down"); },
        prompt -> { luna.incrementAndGet(); throw new Exception("luna down"); },
        error -> true,
        observer
      );
      fail("Expected ProviderChainException");
    } catch (AiProviderRouter.ProviderChainException error) {
      assertEquals("AI provider chain failed: HAKU -> LUNA.", error.getMessage());
    }

    assertEquals(1, haku.get());
    assertEquals(1, luna.get());
    assertEquals(0, gemini.get());
  }

  @Test public void geminiCredentialsHaveNoRoutingEffect() throws Exception {
    String geminiCredentialStillConfigured = "present-but-runtime-locked";
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    AtomicInteger gemini = new AtomicInteger();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { haku.incrementAndGet(); return "ok"; },
      prompt -> { luna.incrementAndGet(); return "fallback"; },
      error -> true,
      new RecordingObserver()
    );

    assertEquals("present-but-runtime-locked", geminiCredentialStillConfigured);
    assertEquals("ok", result);
    assertEquals(1, haku.get());
    assertEquals(0, luna.get());
    assertEquals(0, gemini.get());
  }

  @Test public void nonFallbackEligibleHakuFailureStopsAtHaku() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    try {
      AiProviderRouter.route(
        "prompt",
        prompt -> { haku.incrementAndGet(); throw new IllegalArgumentException("invalid request"); },
        prompt -> { luna.incrementAndGet(); return "should-not-run"; },
        error -> false,
        observer
      );
      fail("Expected validation failure");
    } catch (IllegalArgumentException error) {
      assertTrue(error.getMessage().contains("invalid request"));
    }

    assertEquals(1, haku.get());
    assertEquals(0, luna.get());
    assertEquals(List.of("selected:HAKU"), observer.events);
  }
}
