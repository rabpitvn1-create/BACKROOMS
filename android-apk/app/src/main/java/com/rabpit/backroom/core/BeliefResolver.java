package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/** Sole engine-owned writer for actor epistemic records created by committed events. */
final class BeliefResolver {
  private BeliefResolver() {}

  static void project(JSONObject root, JSONObject event, JSONObject fact, int turn) throws Exception {
    if (root == null || event == null || fact == null) return;
    JSONObject params = event.optJSONObject("params");
    if (params == null) return;

    Set<String> observers = new LinkedHashSet<>();
    if (params.optBoolean("observedByPlayer", false)) observers.add("cao_minh");
    JSONArray explicit = params.optJSONArray("observerRefs");
    if (explicit != null) {
      for (int i = 0; i < explicit.length(); i++) {
        String actor = explicit.optString(i, "").trim();
        if (!actor.isEmpty()) observers.add(actor);
      }
    }

    JSONArray beliefs = root.getJSONArray("beliefs");
    for (String actor : observers) {
      beliefs.put(new JSONObject()
          .put("claimId", fact.getString("factId") + ":" + actor)
          .put("actorId", actor)
          .put("beliefValue", copy(fact.opt("value")))
          .put("confidence", "CONFIRMED")
          .put("learnedViaEventId", event.getString("eventId"))
          .put("learnedTurn", turn)
          .put("confirmedFactId", fact.getString("factId")));
    }

    JSONArray updates = params.optJSONArray("beliefUpdates");
    if (updates == null) return;
    for (int i = 0; i < updates.length(); i++) {
      JSONObject update = updates.optJSONObject(i);
      if (update == null) continue;
      String actor = update.optString("actorId", "").trim();
      if (actor.isEmpty()) throw new IllegalStateException("beliefUpdate requires actorId");
      String confidence = update.optString("confidence", "SUSPECTED").trim();
      if (!("SUSPECTED".equals(confidence) || "CONFIRMED".equals(confidence))) {
        throw new IllegalStateException("Unsupported belief confidence: " + confidence);
      }
      JSONObject record = new JSONObject()
          .put("claimId", update.optString("claimId",
              event.getString("eventId") + ":belief:" + i + ":" + actor))
          .put("actorId", actor)
          .put("beliefValue", copy(update.opt("beliefValue")))
          .put("confidence", confidence)
          .put("learnedViaEventId", event.getString("eventId"))
          .put("learnedTurn", turn);
      if (update.has("confirmedFactId")) record.put("confirmedFactId", update.opt("confirmedFactId"));
      beliefs.put(record);
    }
  }

  private static Object copy(Object value) throws Exception {
    if (value instanceof JSONObject) return new JSONObject(value.toString());
    if (value instanceof JSONArray) return new JSONArray(value.toString());
    return value == null ? JSONObject.NULL : value;
  }
}
