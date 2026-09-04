package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;

public class AiProviderRouterTest {
  private static String validateModelJson(String response) throws Exception {
    if (response == null) throw new Exception("AI không trả dữ liệu.");
    String text = response.trim();
    if (text.startsWith("```")) {
      int firstNewline = text.indexOf('\n');
      if (firstNewline >= 0) text = text.substring(firstNewline + 1);
      int fence = text.lastIndexOf("```");
      if (fence >= 0) text = text.substring(0, fence);
      text = text.trim();
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Exception("AI trả JSON không hợp lệ.");
    return new JSONObject(text.substring(start, end + 1)).toString();
  }

  private static final class RecordingObserver implements AiProviderRouter.Observer {
    final List<String> events = new ArrayList<>();

    @Override public void onSelected(String provider) {
      events.add("selected:" + provider);
    }

    @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
      events.add("fallback:" + fromProvider + "->" + toProvider);
    }

    @Override public void onFailure(String provider, Exception error) {
      events.add("failure:" + provider);
    }
  }

  @Test public void validGeminiJsonStopsBeforeHakuAndLuna() throws Exception {
    AtomicInteger gemini = new AtomicInteger();
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { gemini.incrementAndGet(); return "{\"reply\":\"gemini-result\"}"; },
      prompt -> { haku.incrementAndGet(); return "{\"reply\":\"haku-result\"}"; },
      prompt -> { luna.incrementAndGet(); return "{\"reply\":\"luna-result\"}"; },
      error -> true,
      (provider, response) -> validateModelJson(response),
      observer
    );

    assertEquals("{\"reply\":\"gemini-result\"}", result);
    assertEquals(1, gemini.get());
    assertEquals(0, haku.get());
    assertEquals(0, luna.get());
    assertEquals(List.of("selected:GEMINI"), observer.events);
  }

  @Test public void geminiFailureFallsBackToHakuAndStops() throws Exception {
    AtomicInteger gemini = new AtomicInteger();
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { gemini.incrementAndGet(); throw new Exception("gemini unavailable"); },
      prompt -> { haku.incrementAndGet(); return "haku-result"; },
      prompt -> { luna.incrementAndGet(); return "luna-result"; },
      error -> true,
      observer
    );

    assertEquals("haku-result", result);
    assertEquals(1, gemini.get());
    assertEquals(1, haku.get());
    assertEquals(0, luna.get());
    assertEquals(
      List.of("selected:GEMINI", "failure:GEMINI", "fallback:GEMINI->HAKU", "selected:HAKU"),
      observer.events
    );
  }

  @Test public void geminiAndHakuFailuresFallBackToLuna() throws Exception {
    AtomicInteger gemini = new AtomicInteger();
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { gemini.incrementAndGet(); throw new Exception("gemini unavailable"); },
      prompt -> { haku.incrementAndGet(); throw new Exception("haku unavailable"); },
      prompt -> { luna.incrementAndGet(); return "luna-result"; },
      error -> true,
      observer
    );

    assertEquals("luna-result", result);
    assertEquals(1, gemini.get());
    assertEquals(1, haku.get());
    assertEquals(1, luna.get());
    assertEquals(
      List.of(
        "selected:GEMINI",
        "failure:GEMINI",
        "fallback:GEMINI->HAKU",
        "selected:HAKU",
        "failure:HAKU",
        "fallback:HAKU->LUNA",
        "selected:LUNA"
      ),
      observer.events
    );
  }

  @Test public void malformedResponsesFallThroughUntilValidLunaJson() throws Exception {
    RecordingObserver observer = new RecordingObserver();

    String result = AiProviderRouter.route(
      "prompt",
      prompt -> "plain Gemini prose",
      prompt -> "{malformed Haku JSON}",
      prompt -> "{\"reply\":\"luna-ok\"}",
      error -> true,
      (provider, response) -> validateModelJson(response),
      observer
    );

    assertEquals("{\"reply\":\"luna-ok\"}", result);
    assertEquals(
      List.of(
        "selected:GEMINI",
        "failure:GEMINI",
        "fallback:GEMINI->HAKU",
        "selected:HAKU",
        "failure:HAKU",
        "fallback:HAKU->LUNA",
        "selected:LUNA"
      ),
      observer.events
    );
  }

  @Test public void allThreeFailuresReturnControlledFailureWithLunaCause() throws Exception {
    RecordingObserver observer = new RecordingObserver();

    try {
      AiProviderRouter.route(
        "prompt",
        prompt -> { throw new Exception("gemini down"); },
        prompt -> { throw new Exception("haku down"); },
        prompt -> { throw new Exception("luna down"); },
        error -> true,
        observer
      );
      fail("Expected ProviderChainException");
    } catch (AiProviderRouter.ProviderChainException error) {
      assertEquals("AI provider chain failed: GEMINI -> HAKU -> LUNA.", error.getMessage());
      assertTrue(error.getCause() != null);
      assertEquals("luna down", error.getCause().getMessage());
      assertEquals(2, error.getSuppressed().length);
    }

    assertEquals(
      List.of(
        "selected:GEMINI",
        "failure:GEMINI",
        "fallback:GEMINI->HAKU",
        "selected:HAKU",
        "failure:HAKU",
        "fallback:HAKU->LUNA",
        "selected:LUNA",
        "failure:LUNA"
      ),
      observer.events
    );
  }

  @Test public void nonFallbackEligibleGeminiFailureStopsImmediately() throws Exception {
    AtomicInteger haku = new AtomicInteger();
    AtomicInteger luna = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    try {
      AiProviderRouter.route(
        "prompt",
        prompt -> { throw new IllegalArgumentException("invalid request"); },
        prompt -> { haku.incrementAndGet(); return "should-not-run"; },
        prompt -> { luna.incrementAndGet(); return "should-not-run"; },
        error -> false,
        observer
      );
      fail("Expected Gemini failure");
    } catch (IllegalArgumentException error) {
      assertTrue(error.getMessage().contains("invalid request"));
    }

    assertEquals(0, haku.get());
    assertEquals(0, luna.get());
    assertEquals(List.of("selected:GEMINI", "failure:GEMINI"), observer.events);
  }

  @Test public void nonFallbackEligibleHakuFailureStopsBeforeLuna() throws Exception {
    AtomicInteger luna = new AtomicInteger();
    AtomicInteger policyCalls = new AtomicInteger();
    RecordingObserver observer = new RecordingObserver();

    try {
      AiProviderRouter.route(
        "prompt",
        prompt -> { throw new Exception("gemini retryable"); },
        prompt -> { throw new IllegalArgumentException("haku invalid request"); },
        prompt -> { luna.incrementAndGet(); return "should-not-run"; },
        error -> policyCalls.incrementAndGet() == 1,
        observer
      );
      fail("Expected Haku failure");
    } catch (IllegalArgumentException error) {
      assertTrue(error.getMessage().contains("haku invalid request"));
    }

    assertEquals(0, luna.get());
    assertEquals(
      List.of(
        "selected:GEMINI",
        "failure:GEMINI",
        "fallback:GEMINI->HAKU",
        "selected:HAKU",
        "failure:HAKU"
      ),
      observer.events
    );
  }

  @Test public void twoProviderCompatibilityOverloadStillUsesHakuThenLuna() throws Exception {
    RecordingObserver observer = new RecordingObserver();
    String result = AiProviderRouter.route(
      "prompt",
      prompt -> { throw new Exception("haku down"); },
      prompt -> "luna-result",
      error -> true,
      observer
    );

    assertEquals("luna-result", result);
    assertEquals(
      List.of("selected:HAKU", "failure:HAKU", "fallback:HAKU->LUNA", "selected:LUNA"),
      observer.events
    );
  }
}
