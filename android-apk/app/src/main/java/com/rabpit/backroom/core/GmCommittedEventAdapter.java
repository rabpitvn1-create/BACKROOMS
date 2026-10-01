package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Converts accepted GM typed-event evidence into canon-valid DomainEvents. */
final class GmCommittedEventAdapter {
  private GmCommittedEventAdapter() {}

  static void append(
      EmergentTurnEngine engine, String turnId, JSONArray target, JSONArray committedEvents)
      throws Exception {
    if (engine == null || target == null || committedEvents == null) return;
    for (int i = 0; i < committedEvents.length(); i++) {
      JSONObject evidence = committedEvents.optJSONObject(i);
      if (evidence == null) throw new IllegalStateException("gm_committed_event_missing");
      appendOne(engine, turnId, target, evidence);
    }
  }

  private static void appendOne(
      EmergentTurnEngine engine, String turnId, JSONArray target, JSONObject evidence)
      throws Exception {
    String typed = evidence.optString("eventType", "");
    String subject = evidence.optString("subjectKey", "");
    JSONObject params = new JSONObject()
        .put("gmCommandType", evidence.optString("commandType", ""))
        .put("gmOwner", evidence.optString("owner", ""))
        .put("causedBy", selectionEvidence(evidence) == null ? "player" : "world")
        .put("observedByPlayer", true);
    JSONObject selection = selectionEvidence(evidence);
    if (selection != null) params.put("selectionEvidence", new JSONObject(selection.toString()));

    if ("ITEM_USED".equals(typed) || "ITEM_SHARED".equals(typed) || "ITEM_DISCARDED".equals(typed)) {
      String operation = "ITEM_USED".equals(typed) ? "use"
          : "ITEM_SHARED".equals(typed) ? "share" : "discard";
      String actor = evidence.optString("actorId", "cao_minh");
      String targetId = evidence.optString("targetId", "");
      int quantity = Math.max(1, evidence.optInt("quantity", 1));
      params.put("factPredicate", "item_action")
          .put("factValue", subject + ":" + operation + ":" + quantity);
      target.put(engine.event(turnId, target, "ITEM_ACTION_RESOLVED", "LOCAL",
          targetId.isEmpty() ? actor : targetId, params, null));
      return;
    }

    if ("LEVEL_TRANSITIONED".equals(typed)) {
      String from = evidence.optString("fromLevelKey", "");
      params.put("factPredicate", "entered_level").put("factValue", subject);
      JSONArray effects = from.isEmpty() ? null : new JSONArray().put(engine.threadEffect(
          "LEVEL_ROUTE_SEARCH", new JSONArray().put(from), "TERMINATE", "RESOLVED"));
      target.put(engine.event(
          turnId, target, "LEVEL_TRANSITIONED", "REGIONAL", subject, params, effects));
      return;
    }

    if ("CHARACTER_STAT_UPGRADED".equals(typed)) {
      params.put("factPredicate", "stat_upgraded")
          .put("factValue", evidence.optString("stat", "") + ":" + evidence.optInt("value", 0));
      target.put(engine.event(
          turnId, target, "CHARACTER_STAT_UPGRADED", "LOCAL", subject, params, null));
      return;
    }

    if ("COMBAT_STARTED".equals(typed)) {
      params.put("factPredicate", "combat_started").put("factValue", subject);
      JSONArray effects = subject.isEmpty() ? null : new JSONArray().put(engine.threadEffect(
          "ENTITY_ENCOUNTER", new JSONArray().put(subject), "SEED_OR_ADVANCE", null));
      target.put(engine.event(turnId, target, "COMBAT_STARTED", "LOCAL", subject, params, effects));
      return;
    }

    if ("ENTITY_ENCOUNTER_STARTED".equals(typed)) {
      params.put("factPredicate", "entity_encounter_started").put("factValue", subject);
      JSONArray effects = new JSONArray().put(engine.threadEffect(
          "ENTITY_ENCOUNTER", new JSONArray().put(subject), "SEED_OR_ADVANCE", null));
      target.put(engine.event(
          turnId, target, "ENTITY_ENCOUNTER_STARTED", "LOCAL", subject, params, effects));
      return;
    }

    if ("CHEST_SPAWNED".equals(typed)) {
      params.put("factPredicate", "chest_discovered").put("factValue", true);
      JSONArray effects = new JSONArray().put(engine.threadEffect(
          "CHEST_AVAILABLE", new JSONArray().put(subject), "SEED_OR_ADVANCE", null));
      target.put(engine.event(turnId, target, "CHEST_SPAWNED", "LOCAL", subject, params, effects));
      return;
    }

    if ("CHARACTER_ENCOUNTERED".equals(typed) || "CHARACTER_REUNION".equals(typed)) {
      boolean reunion = "CHARACTER_REUNION".equals(typed);
      params.put("factPredicate", reunion ? "character_reunion" : "character_encountered")
          .put("factValue", subject);
      JSONArray effects = new JSONArray().put(engine.threadEffect(
          reunion ? "LUC_TRAM_RELATIONSHIP" : "SOCIAL_CONTACT",
          new JSONArray().put(subject), "SEED_OR_ADVANCE", null));
      target.put(engine.event(
          turnId, target, typed, "SOCIAL", subject, params, effects));
      return;
    }

    throw new IllegalStateException("gm_event_unmapped:" + typed);
  }

  private static JSONObject selectionEvidence(JSONObject evidence) {
    JSONObject selected = evidence == null ? null : evidence.optJSONObject("selectionEvidence");
    return selected == null || selected.length() == 0 ? null : selected;
  }
}
