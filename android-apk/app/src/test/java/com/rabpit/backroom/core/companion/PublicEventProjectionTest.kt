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

  @Test fun combatDoesNotEnumerateEntitiesWithoutVisibilityProof() {
    assertNull(PublicEventProjection.project("COMBAT_STARTED",JSONObject().put("entities",JSONArray().put(
      JSONObject().put("id","hidden").put("name","writer secret").put("presence","ACTIVE")))))
    assertNull(PublicEventProjection.project("COMBAT_STARTED",JSONObject()))
    assertEquals(0,PublicEventProjection.project("COMBAT_STARTED",JSONObject().put("entities",JSONArray()))!!.getJSONArray("entities").length())
  }
  @Test fun malformedFieldsAreDeniedInsteadOfFabricated() {
    for(p in listOf(JSONObject(),JSONObject().put("actor","a").put("minutes","30").put("location","l").put("elapsedMinutes",30),
      JSONObject().put("actor","a").put("minutes",-1).put("location","l").put("elapsedMinutes",30))) {
      assertNull(PublicEventProjection.project("WAIT_COMPLETED",p))
    }
    assertNull(PublicEventProjection.project("EXIT_STREAK_RESOLVED",JSONObject().put("completed","true").put("source","s").put("target","t")))
    assertNull(PublicEventProjection.project("WORLD_TRANSITION",JSONObject().put("source","s")))
  }
  @Test fun sceneKeysAreBoundedAndNamedAreasAreDistinct() {
    assertNull(PublicEventProjection.project("WORLD_TRANSITION",JSONObject().put("source","x".repeat(257)).put("target","t")))
    val state=com.rabpit.backroom.core.GameState(characters=emptyMap(),world=mapOf("worldNodeId" to "level-0","journeyStopKey" to "area:0:red-rooms"))
    assertEquals("area:0:red-rooms",NativeObservationSource.sceneAt(state))
  }

  @Test fun unsupportedEventTypeDenied() {
    assertNull(PublicEventProjection.project("UNKNOWN_TYPE", JSONObject()))
    assertNull(PublicEventProjection.project("", JSONObject()))
  }
}
