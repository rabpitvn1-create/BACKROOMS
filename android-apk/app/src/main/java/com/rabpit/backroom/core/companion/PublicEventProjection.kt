package com.rabpit.backroom.core.companion

import org.json.JSONArray
import org.json.JSONObject

/**
 * M1b.2 public event projection (issue #499; contract #498 §2b).
 *
 * Pure function: (native WAIT event type, native payload) -> character-visible
 * JSONObject, or null to deny exposure. Implements the per-event/field whitelist:
 * exposable fields pass through; GM-private / system / unclassified content is
 * redacted or denied. Unsupported event types -> null (deny).
 *
 * This is the "native verified projection": eligibility (who may perceive) still
 * comes from CompanionExposurePolicy; this decides WHAT may be perceived.
 */
internal object PublicEventProjection {
  fun project(eventType: String, payload: JSONObject): JSONObject? = when (eventType) {
    "WAIT_COMPLETED" -> JSONObject()
      .put("actor", payload.optString("actor"))
      .put("minutes", payload.optInt("minutes"))
      .put("location", payload.optString("location"))
      .put("elapsedMinutes", payload.optLong("elapsedMinutes"))
    "EXIT_STREAK_RESOLVED" -> {
      // success/streak counters are system mechanics: never character knowledge.
      // target is only knowable after arrival; before completion it stays redacted.
      val out = JSONObject().put("completed", payload.optBoolean("completed"))
        .put("source", payload.optString("source"))
      if (payload.optBoolean("completed")) out.put("target", payload.optString("target"))
      out
    }
    "WORLD_TRANSITION" -> JSONObject()
      .put("source", payload.optString("source"))
      .put("target", payload.optString("target"))
    // redacted: node (system identifier)
    "COMBAT_STARTED" -> {
      // redact: encounterId (system id); full entity metadata (GM-private).
      // expose per-entity id/name/presence only.
      val entities = payload.optJSONArray("entities") ?: JSONArray()
      val visible = JSONArray()
      for (i in 0 until entities.length()) {
        val e = entities.optJSONObject(i) ?: continue
        visible.put(JSONObject()
          .put("id", e.optString("id"))
          .put("name", e.optString("name"))
          .put("presence", e.optString("presence")))
      }
      JSONObject().put("entities", visible)
    }
    else -> null
  }
}
