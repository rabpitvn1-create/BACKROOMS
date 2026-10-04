package com.rabpit.backroom.core;

import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/** Writer-only rolling window. All network/forecast work happens outside this monitor. */
public final class NarrationFutureBuffer {
  public static final int TARGET = 10;
  public static final int EMERGENCY = 2;
  private JSONArray slots = new JSONArray();
  private long epoch;
  private long sequence;
  private Request active;
  private boolean demand;

  public static final class Request {
    public final long epoch;
    public final long id;
    public final JSONArray steps;
    public final JSONArray precedingReplies;
    private Request(long epoch, long id, JSONArray steps, JSONArray precedingReplies) {
      this.epoch = epoch;
      this.id = id;
      this.steps = steps;
      this.precedingReplies = precedingReplies;
    }
  }

  public synchronized void reset() {
    epoch++;
    slots = new JSONArray();
    active = null;
    demand = true;
  }

  public synchronized long epoch() { return epoch; }

  public synchronized void requestRefill() {
    if (readyCount() < TARGET) demand = true;
  }

  public synchronized boolean needsRefill() {
    return demand && readyCount() < TARGET;
  }

  public synchronized int readyCount() {
    int ready = 0;
    while (ready < slots.length()) {
      JSONObject slot = slots.optJSONObject(ready);
      if (slot == null || slot.optJSONObject("payload") == null) break;
      ready++;
    }
    return ready;
  }

  /** Append only missing tail steps. Existing prepared slots are immutable until consumed or reset. */
  public synchronized boolean forecast(long expectedEpoch, JSONArray steps) throws JSONException {
    if (expectedEpoch != epoch) return false;
    for (int i = 0; steps != null && i < steps.length() && slots.length() < TARGET; i++) {
      JSONObject step = steps.optJSONObject(i);
      if (step == null || step.optString("turnId", "").isEmpty()) break;
      boolean known = false;
      for (int j = 0; j < slots.length(); j++) {
        if (sameStep(slots.optJSONObject(j), step)) {
          known = true;
          break;
        }
      }
      if (known) continue;
      JSONObject slot = copy(step);
      slot.remove("payload");
      slots.put(slot);
    }
    requestRefill();
    return true;
  }

  public synchronized Request reserve() throws JSONException {
    if (active != null || !needsRefill()) return null;
    JSONArray steps = new JSONArray();
    JSONArray preceding = new JSONArray();
    int first = readyCount();
    int limit = first <= EMERGENCY ? 2 : 4;
    for (int i = 0; i < first; i++) {
      preceding.put(slots.getJSONObject(i).getJSONObject("payload").optString("reply", ""));
    }
    for (int i = first; i < slots.length() && steps.length() < limit; i++) {
      JSONObject slot = slots.getJSONObject(i);
      if (slot.optJSONObject("payload") != null) break;
      steps.put(copy(slot));
    }
    if (steps.length() == 0) return null;
    active = new Request(epoch, ++sequence, steps, preceding);
    return active;
  }

  /** IDs bind each capsule to its reserved Core step, even if the writer omits a middle item. */
  public synchronized int accept(Request request, JSONArray future) throws JSONException {
    if (request == null || request != active || request.epoch != epoch) return 0;
    int accepted = 0;
    for (int i = 0; future != null && i < future.length(); i++) {
      JSONObject payload = future.optJSONObject(i);
      if (payload == null || payload.optString("reply", "").trim().isEmpty()) continue;
      String stepId = payload.optString("stepId", "");
      JSONObject requested = null;
      for (int j = 0; j < request.steps.length(); j++) {
        JSONObject step = request.steps.getJSONObject(j);
        if (stepId.equals(step.optString("turnId", ""))) requested = step;
      }
      if (requested == null) continue;
      for (int j = 0; j < slots.length(); j++) {
        JSONObject slot = slots.getJSONObject(j);
        if (!sameStep(slot, requested) || slot.optJSONObject("payload") != null) continue;
        JSONObject clean = copy(payload);
        clean.remove("stepId");
        clean.remove("future");
        slot.put("payload", clean);
        accepted++;
        break;
      }
    }
    return accepted;
  }

  public synchronized boolean finish(Request request) {
    if (active != request) return false;
    active = null;
    if (readyCount() >= TARGET) demand = false;
    return needsRefill();
  }

  /** Consume exactly one prepared slot. A bad current slot never invalidates the prepared tail. */
  public synchronized JSONObject poll(String hash, String turnId, Predicate<JSONObject> outcome) throws JSONException {
    if (slots.length() == 0) return null;
    JSONObject slot = slots.getJSONObject(0);
    boolean matched = matches(slot, hash, turnId, outcome);
    slots.remove(0);
    requestRefill();
    if (!matched || slot.optJSONObject("payload") == null) return null;
    return copy(slot);
  }

  private static boolean matches(JSONObject step, String hash, String turnId,
                                 Predicate<JSONObject> outcome) {
    if (turnId != null && !turnId.isEmpty()) {
      return turnId.equals(step.optString("turnId", "")) && outcome.test(step);
    }
    return hash != null && !hash.isEmpty() && hash.equals(step.optString("authorityHash", ""));
  }

  private static boolean sameStep(JSONObject left, JSONObject right) {
    if (left == null || right == null) return false;
    return !left.optString("turnId", "").isEmpty()
        && left.optString("turnId", "").equals(right.optString("turnId", ""));
  }

  private static JSONObject copy(JSONObject object) throws JSONException { return new JSONObject(object.toString()); }
}
