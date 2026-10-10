package com.rabpit.backroom.core.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * M1b.2 public projection fixtures (issue #499; contract #498 §2b).
 * Each native WAIT event type: exposable fields pass, GM-private/system fields
 * are redacted, unsupported types are denied (null).
 */
class PublicEventProjectionTest {
  @Test fun waitCompletedExposesAllFourFields() {
    val p = PublicEventProjection.project("WAIT_COMPLETED", JSONObject()
      .put("actor", "cao_minh").put("minutes", 30)
      .put("location", "node-7").put("elapsedMinutes", 90))!!
    assertEquals("cao_minh", p.getString("actor"))
    assertEquals(30, p.getInt("minutes"))
    assertEquals("node-7", p.getString("location"))
    assertEquals(90L, p.getLong("elapsedMinutes"))
  }

  @Test fun streakResolvedCompletedExposesTargetButRedactsCounters() {
    val p = PublicEventProjection.project("EXIT_STREAK_RESOLVED", JSONObject()
      .put("success", true).put("streak", 5)
      .put("source", "node-7").put("target", "node-8").put("completed", true))!!
    assertEquals(true, p.getBoolean("completed"))
    assertEquals("node-7", p.getString("source"))
    assertEquals("node-8", p.getString("target"))
    assertFalse(p.has("success"))
    assertFalse(p.has("streak"))
  }

  @Test fun streakResolvedIncompleteRedactsTarget() {
    val p = PublicEventProjection.project("EXIT_STREAK_RESOLVED", JSONObject()
      .put("success", false).put("streak", 3)
      .put("source", "node-7").put("target", "node-8").put("completed", false))!!
    assertEquals(false, p.getBoolean("completed"))
    assertEquals("node-7", p.getString("source"))
    assertFalse(p.has("target"))
    assertFalse(p.has("success"))
    assertFalse(p.has("streak"))
  }

  @Test fun worldTransitionRedactsNodeId() {
    val p = PublicEventProjection.project("WORLD_TRANSITION", JSONObject()
      .put("source", "node-7").put("target", "node-8").put("node", "sys-node-uuid"))!!
    assertEquals("node-7", p.getString("source"))
    assertEquals("node-8", p.getString("target"))
    assertFalse(p.has("node"))
  }

  @Test fun combatStartedRedactsEncounterAndEntityMetadata() {
    val p = PublicEventProjection.project("COMBAT_STARTED", JSONObject()
      .put("encounterId", "enc-1")
      .put("entities", JSONArray().put(JSONObject()
        .put("id", "e1").put("name", "Wraith").put("presence", "ACTIVE")
        .put("hp", 100).put("attack", 25))))!!
    assertFalse(p.has("encounterId"))
    val entities = p.getJSONArray("entities")
    assertEquals(1, entities.length())
    val e = entities.getJSONObject(0)
    assertEquals("e1", e.getString("id"))
    assertEquals("Wraith", e.getString("name"))
    assertEquals("ACTIVE", e.getString("presence"))
    assertFalse(e.has("hp"))
    assertFalse(e.has("attack"))
  }

  @Test fun unsupportedEventTypeDenied() {
    assertNull(PublicEventProjection.project("UNKNOWN_TYPE", JSONObject()))
    assertNull(PublicEventProjection.project("", JSONObject()))
  }
}
