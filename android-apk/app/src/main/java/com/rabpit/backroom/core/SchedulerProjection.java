package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Replayable WHEN-only projection. It never mutates authoritative world state. */
final class SchedulerProjection {
  private SchedulerProjection() {}

  static JSONObject normalize(JSONObject root) throws Exception {
    JSONObject scheduler = root.optJSONObject("scheduler");
    if (scheduler == null) scheduler = new JSONObject();
    if (!(scheduler.opt("pending") instanceof JSONArray)) scheduler.put("pending", new JSONArray());
    scheduler.put("derivedFromCommitSeq", Math.max(0, scheduler.optInt("derivedFromCommitSeq", 0)));
    root.put("scheduler", scheduler);
    return scheduler;
  }

  static void fold(JSONObject root, int watermark) throws Exception {
    JSONObject scheduler = normalize(root);
    JSONArray pending = scheduler.getJSONArray("pending");
    JSONArray commits = root.getJSONArray("commitLog");

    for (int i = 0; i < commits.length(); i++) {
      JSONObject commit = commits.getJSONObject(i);
      if (commit.optInt("commitSeq", 0) <= watermark) continue;

      String selected = commit.optString("sourceSituationKey", "");
      if (!selected.isEmpty()) {
        for (int p = 0; p < pending.length(); p++) {
          JSONObject trigger = pending.optJSONObject(p);
          if (trigger != null && !trigger.optBoolean("fired", false)
              && selected.equals(trigger.optString("situationKey", ""))) {
            trigger.put("fired", true).put("firedCommitSeq", commit.optInt("commitSeq", 0));
          }
        }
      }

      JSONArray events = commit.optJSONArray("events");
      if (events == null) continue;
      for (int e = 0; e < events.length(); e++) {
        JSONObject event = events.optJSONObject(e);
        JSONObject params = event == null ? null : event.optJSONObject("params");
        JSONObject schedule = params == null ? null : params.optJSONObject("schedule");
        if (schedule == null) continue;
        String triggerId = schedule.optString("triggerId", "").trim();
        JSONObject candidate = schedule.optJSONObject("candidate");
        int dueTurn = schedule.optInt("dueTurn", -1);
        if (triggerId.isEmpty() || candidate == null || dueTurn < 0 || containsTrigger(pending, triggerId)) continue;
        pending.put(new JSONObject()
            .put("triggerId", triggerId)
            .put("sourceEventId", event.optString("eventId", ""))
            .put("dueTurn", dueTurn)
            .put("situationKey", candidate.optString("situationKey", ""))
            .put("candidate", new JSONObject(candidate.toString()))
            .put("fired", false));
      }
    }
    scheduler.put("derivedFromCommitSeq", root.optInt("commitSequence", 0));
  }

  static JSONArray dueCandidates(JSONObject state, int currentTurn) throws Exception {
    JSONArray output = new JSONArray();
    JSONObject root = state == null ? null : state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONObject scheduler = root == null ? null : root.optJSONObject("scheduler");
    JSONArray pending = scheduler == null ? null : scheduler.optJSONArray("pending");
    if (pending == null) return output;

    for (int i = 0; i < pending.length(); i++) {
      JSONObject trigger = pending.optJSONObject(i);
      if (trigger == null || trigger.optBoolean("fired", false)
          || trigger.optInt("dueTurn", Integer.MAX_VALUE) > currentTurn) continue;
      JSONObject candidate = trigger.optJSONObject("candidate");
      if (candidate == null) continue;
      JSONObject due = new JSONObject(candidate.toString());
      due.put("source", "SCHEDULER")
          .put("mandatory", true)
          .put("schedulerTriggerId", trigger.optString("triggerId", ""));
      output.put(due);
    }
    return output;
  }

  private static boolean containsTrigger(JSONArray pending, String triggerId) {
    for (int i = 0; i < pending.length(); i++) {
      JSONObject value = pending.optJSONObject(i);
      if (value != null && triggerId.equals(value.optString("triggerId", ""))) return true;
    }
    return false;
  }
}
