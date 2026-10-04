package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NarrationFutureBufferTest {
  private JSONArray steps(int start, int count) throws Exception {
    JSONArray result = new JSONArray();
    for (int i = start; i < start + count; i++) result.put(step(i, "QUIET"));
    return result;
  }

  private JSONObject step(int i, String kind) throws Exception {
    return new JSONObject().put("turnId", "t" + i).put("authorityHash", "h" + i)
        .put("worldKind", kind);
  }

  private JSONArray replies(NarrationFutureBuffer.Request request, int count) throws Exception {
    JSONArray result = new JSONArray();
    for (int i = 0; i < Math.min(count, request.steps.length()); i++) result.put(new JSONObject()
        .put("stepId", request.steps.getJSONObject(i).getString("turnId"))
        .put("reply", "reply " + request.steps.getJSONObject(i).getString("turnId")));
    return result;
  }

  private NarrationFutureBuffer buffer(JSONArray steps) throws Exception {
    NarrationFutureBuffer buffer = new NarrationFutureBuffer();
    buffer.forecast(buffer.epoch(), steps);
    return buffer;
  }

  private NarrationFutureBuffer fullBuffer(JSONArray steps) throws Exception {
    NarrationFutureBuffer buffer = buffer(steps);
    while (buffer.needsRefill()) {
      NarrationFutureBuffer.Request work = buffer.reserve();
      assertNotNull(work);
      buffer.accept(work, replies(work, work.steps.length()));
      buffer.finish(work);
    }
    return buffer;
  }

  @Test public void consumeOneKeepsNineAndForecastOnlyAppendsNewTail() throws Exception {
    NarrationFutureBuffer buffer = fullBuffer(steps(1, 10));
    assertEquals(10, buffer.readyCount());

    assertNotNull(buffer.poll("changed-hash", "t1", step -> true));
    assertEquals(9, buffer.readyCount());

    buffer.forecast(buffer.epoch(), steps(2, 10));
    assertEquals(9, buffer.readyCount());
    NarrationFutureBuffer.Request tail = buffer.reserve();
    assertNotNull(tail);
    assertEquals(1, tail.steps.length());
    assertEquals("t11", tail.steps.getJSONObject(0).getString("turnId"));
    assertEquals(1, buffer.accept(tail, replies(tail, 1)));
    buffer.finish(tail);
    assertEquals(10, buffer.readyCount());
  }

  @Test public void authorityHashChangeCannotReplacePreparedSlot() throws Exception {
    NarrationFutureBuffer buffer = fullBuffer(steps(1, 10));
    JSONArray changed = steps(1, 10);
    changed.getJSONObject(0).put("authorityHash", "different").put("worldKind", "CHARACTER");
    buffer.forecast(buffer.epoch(), changed);

    JSONObject first = buffer.poll("different", "t1", step -> true);
    assertNotNull(first);
    assertEquals("QUIET", first.getString("worldKind"));
    assertEquals("reply t1", first.getJSONObject("payload").getString("reply"));
    assertEquals(9, buffer.readyCount());
  }

  @Test public void mismatchedHeadDropsOnlyThatTurnAndPreservesTail() throws Exception {
    NarrationFutureBuffer buffer = fullBuffer(steps(1, 10));
    assertNull(buffer.poll("wrong", "wrong-turn", step -> false));
    assertEquals(9, buffer.readyCount());
    assertNotNull(buffer.poll("h2", "t2", step -> true));
    assertEquals(8, buffer.readyCount());
  }

  @Test public void characterEntityAndChestTurnsNeverResetFollowingSlots() throws Exception {
    JSONArray prepared = steps(1, 10);
    prepared.put(0, step(1, "CHARACTER"));
    prepared.put(1, step(2, "ENTITY"));
    prepared.put(2, step(3, "CHEST"));
    NarrationFutureBuffer buffer = fullBuffer(prepared);

    assertEquals("CHARACTER", buffer.poll("h1", "t1", step -> true).getString("worldKind"));
    assertEquals(9, buffer.readyCount());
    assertEquals("ENTITY", buffer.poll("h2", "t2", step -> true).getString("worldKind"));
    assertEquals(8, buffer.readyCount());
    assertEquals("CHEST", buffer.poll("h3", "t3", step -> true).getString("worldKind"));
    assertEquals(7, buffer.readyCount());
  }

  @Test public void inFlightTailRefillSurvivesPlayerAdvance() throws Exception {
    NarrationFutureBuffer buffer = fullBuffer(steps(1, 10));
    assertNotNull(buffer.poll("h1", "t1", step -> true));
    buffer.forecast(buffer.epoch(), steps(2, 10));
    NarrationFutureBuffer.Request tail = buffer.reserve();
    assertEquals("t11", tail.steps.getJSONObject(0).getString("turnId"));

    assertNotNull(buffer.poll("h2", "t2", step -> true));
    assertEquals(8, buffer.readyCount());
    assertEquals(1, buffer.accept(tail, replies(tail, 1)));
    buffer.finish(tail);
    assertEquals(9, buffer.readyCount());
    assertNotNull(buffer.poll("h3", "t3", step -> true));
  }

  @Test public void oldCallbackCannotOverwriteResetOrFinishNewRequest() throws Exception {
    NarrationFutureBuffer buffer = buffer(steps(1, 10));
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
    NarrationFutureBuffer buffer = buffer(steps(1, 10));
    NarrationFutureBuffer.Request first = buffer.reserve();
    assertEquals(0, buffer.readyCount());
    assertNull(buffer.reserve());
    JSONArray output = new JSONArray().put(new JSONObject().put("stepId", "t2").put("reply", "second"));
    assertEquals(1, buffer.accept(first, output));
    buffer.finish(first);
    assertEquals(0, buffer.readyCount());
    assertEquals("t1", buffer.reserve().steps.getJSONObject(0).getString("turnId"));
    assertNull(buffer.poll("h1", "t1", step -> true));
    assertEquals("second", buffer.poll("h2", "t2", step -> true)
        .getJSONObject("payload").getString("reply"));
  }
}
