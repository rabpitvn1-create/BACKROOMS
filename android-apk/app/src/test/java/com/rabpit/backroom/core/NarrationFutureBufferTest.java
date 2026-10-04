package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NarrationFutureBufferTest {
  private JSONArray steps(int start, int count) throws Exception {
    JSONArray result = new JSONArray();
    for (int i = start; i < start + count; i++) result.put(new JSONObject()
        .put("turnId", "t" + i).put("authorityHash", "h" + i).put("worldKind", "QUIET"));
    return result;
  }
  private JSONArray replies(NarrationFutureBuffer.Request request, int count) throws Exception {
    JSONArray result = new JSONArray();
    for (int i = 0; i < Math.min(count, request.steps.length()); i++) result.put(new JSONObject()
        .put("stepId", request.steps.getJSONObject(i).getString("turnId"))
        .put("reply", "reply " + request.steps.getJSONObject(i).getString("turnId")));
    return result;
  }
  private NarrationFutureBuffer buffer() throws Exception {
    NarrationFutureBuffer buffer = new NarrationFutureBuffer();
    buffer.forecast(buffer.epoch(), steps(1, 10));
    return buffer;
  }

  @Test public void lateBatchDropsConsumedSlotsAndKeepsRefillDemand() throws Exception {
    NarrationFutureBuffer buffer = buffer();
    NarrationFutureBuffer.Request old = buffer.reserve();
    buffer.forecast(buffer.epoch(), steps(5, 10));
    assertEquals(0, buffer.accept(old, replies(old, 2)));
    assertTrue(buffer.finish(old));
    assertEquals("t5", buffer.reserve().steps.getJSONObject(0).getString("turnId"));
  }
  @Test public void partialOutputRefillsMissingTailAndPreservesReadyPayload() throws Exception {
    NarrationFutureBuffer buffer = buffer();
    NarrationFutureBuffer.Request first = buffer.reserve();
    assertEquals(1, buffer.accept(first, replies(first, 1)));
    assertTrue(buffer.finish(first));
    assertEquals(1, buffer.readyCount());
    NarrationFutureBuffer.Request next = buffer.reserve();
    assertEquals("t2", next.steps.getJSONObject(0).getString("turnId"));
    assertEquals("reply t1", next.precedingReplies.getString(0));
    assertEquals("reply t1", buffer.poll("h1", "t1", step -> true)
        .getJSONObject("payload").getString("reply"));
  }
  @Test public void refillContinuesPastLowWaterUntilTenAndStartsAgainAtSix() throws Exception {
    NarrationFutureBuffer buffer = buffer();
    while (buffer.needsRefill()) {
      NarrationFutureBuffer.Request work = buffer.reserve();
      assertNotNull(work);
      buffer.accept(work, replies(work, 4));
      buffer.finish(work);
    }
    assertEquals(10, buffer.readyCount());
    for (int i = 1; i <= 4; i++) assertNotNull(buffer.poll("h" + i, "t" + i, step -> true));
    assertEquals(6, buffer.readyCount());
    assertTrue(buffer.needsRefill()); // Demand persists until the forecast gains new tail slots.
    buffer.forecast(buffer.epoch(), steps(5, 10));
    assertTrue(buffer.needsRefill());
    assertEquals("t11", buffer.reserve().steps.getJSONObject(0).getString("turnId"));
  }
  @Test public void exactLaterHashWinsOverRepeatedOutcome() throws Exception {
    assertEquals(4, NarrationFutureBuffer.alignment("h4", "h0", "t1", steps(1, 10), s -> true));
    assertEquals(-1, NarrationFutureBuffer.alignment("different", "h0", "", steps(1, 10), s -> true));
    assertEquals(4, NarrationFutureBuffer.alignment("combat-bookkeeping", "h0", "t4", steps(1, 10), s -> true));
  }
  @Test public void oldCallbackCannotOverwriteResetOrFinishNewRequest() throws Exception {
    NarrationFutureBuffer buffer = buffer();
    NarrationFutureBuffer.Request old = buffer.reserve();
    long epoch = buffer.epoch();
    buffer.reset();
    assertFalse(buffer.forecast(epoch, steps(1, 10)));
    buffer.forecast(buffer.epoch(), steps(100, 10));
    NarrationFutureBuffer.Request current = buffer.reserve();
    assertEquals(0, buffer.accept(old, replies(old, 2)));
    assertFalse(buffer.finish(old));
    assertNull(buffer.reserve());
    assertEquals(2, buffer.accept(current, replies(current, 2)));
    assertEquals(2, buffer.readyCount());
  }
  @Test public void missingMiddleCannotShiftCapsulesAndInFlightIsNotReady() throws Exception {
    NarrationFutureBuffer buffer = buffer();
    NarrationFutureBuffer.Request first = buffer.reserve();
    assertEquals(0, buffer.readyCount());
    assertNull(buffer.reserve());
    JSONArray output = new JSONArray().put(new JSONObject().put("stepId", "t2").put("reply", "second"));
    assertEquals(1, buffer.accept(first, output));
    buffer.finish(first);
    assertEquals(0, buffer.readyCount());
    assertEquals("t1", buffer.reserve().steps.getJSONObject(0).getString("turnId"));
    assertNull(buffer.poll("h1", "t1", s -> true));
    assertEquals("second", buffer.poll("h2", "t2", s -> true).getJSONObject("payload").getString("reply"));
  }
}
