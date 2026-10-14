package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MilestoneNarrationValidatorTest {
  private fun due(vararg ids: String) = JSONArray().apply {
    ids.forEach { put(JSONObject().put("id", it).put("kind", "STORY")) }
  }
  private fun character(kind: String = "FOLLOWER") = JSONArray().put(JSONObject()
    .put("id", "lucia_meet").put("kind", kind).put("characterId", LUCIA_ID))
  private fun scene(id: String) = JSONObject().put("eventId", id)
    .put("occurrence", "Cánh cửa khép lại ngay khi hắn bước qua.")
    .put("consequence", "Hắn đã bị ngăn cách với hành lang cũ.")
  private fun meeting() = scene("lucia_meet")
    .put("occurrence", "Người lính trẻ bước đến trước hắn, giữ khoảng cách cảnh giác.")
    .put("communication", JSONObject().put("speakerId", LUCIA_ID)
      .put("text", "Cô hạ giọng nhắc hắn đừng chạm vào dây điện, nó vẫn còn mang điện.") )
    .put("consequence", "Hắn đã nghe cảnh báo; cô vẫn đứng ngoài nhóm của hắn.")
  private fun response(vararg scenes: JSONObject) = JSONObject().put("reply", "Cao Minh dừng lại.")
    .put("completedEventIds", JSONArray(scenes.map { it.getString("eventId") }))
    .put("eventNarrations", JSONArray(scenes.toList()))
  private fun reject(code: String, value: JSONObject, events: JSONArray) {
    assertEquals(code, assertThrows(MilestoneNarrationValidator.MilestoneNarrationException::class.java) {
      MilestoneNarrationValidator.validate(value, events)
    }.message)
  }

  @Test fun correctIdWithUnrelatedReplyAndNoSceneIsRejected() {
    val value = response().put("reply", "Cao Minh nhìn những bức tường vàng.")
      .put("completedEventIds", JSONArray().put("door"))
    reject("milestone_narrative_content_missing", value, due("door"))
  }
  @Test fun mereMentionOrObservationWithoutCommunicationIsRejectedForNpcAndFollower() {
    for (kind in listOf("NPC", "FOLLOWER")) for (prose in listOf(
      "Cao Minh nghĩ tới Lucia.", "Lucia đứng quan sát ở cuối hành lang.")) {
      val value = response(meeting().apply { remove("communication"); put("occurrence", prose) })
      reject("milestone_character_communication_missing", value, character(kind))
    }
  }
  @Test fun naturalModernCommunicationWithoutNameQuotesOrDashesIsRendered() {
    val value = response(meeting())
    val result = MilestoneNarrationValidator.validate(value, character())
    assertFalse(result.reply.contains("Lucia"))
    assertFalse(result.reply.contains('"'))
    assertFalse(result.reply.contains('—'))
    assertTrue(result.reply.contains("Cô hạ giọng nhắc hắn đừng chạm vào dây điện"))
    assertEquals("lucia", result.events.getJSONObject(0).getJSONObject("communication").getString("speakerId"))
  }
  @Test fun emptyPreludeWithCompleteScenesIsValid() {
    val result = MilestoneNarrationValidator.validate(response(meeting()).put("reply", ""), character())
    assertTrue(result.reply.startsWith("Người lính trẻ"))
  }
  @Test fun multipleEventsRenderEachSceneOnceInScheduleOrder() {
    val a = scene("door"); val b = meeting(); val c = scene("warning").put("occurrence", "Đèn vụt tắt.")
    val result = MilestoneNarrationValidator.validate(response(a, b, c),
      JSONArray().put(due("door").getJSONObject(0)).put(character().getJSONObject(0)).put(due("warning").getJSONObject(0)))
    assertEquals(3, result.events.length())
    assertTrue(result.reply.indexOf("Cánh cửa") < result.reply.indexOf("Người lính"))
    assertTrue(result.reply.indexOf("Người lính") < result.reply.indexOf("Đèn vụt tắt"))
    assertEquals(1, Regex("Cô hạ giọng").findAll(result.reply).count())
  }
  @Test fun missingIdsAreRejected() {
    reject("milestone_narrative_events_missing", response(scene("door")).apply { remove("completedEventIds") }, due("door"))
    reject("milestone_narrative_events_missing", response(scene("door")), due("door", "warning"))
  }
  @Test fun duplicatedExtraWrongAndReorderedIdsAreRejected() {
    val events = due("door", "warning")
    for (ids in listOf(listOf("door", "door"), listOf("door", "invented"), listOf("warning", "door"))) {
      reject("milestone_narrative_event_mismatch", response(scene("door"), scene("warning"))
        .put("completedEventIds", JSONArray(ids)), events)
    }
    reject("milestone_narrative_events_missing", response(scene("door"), scene("warning"), scene("extra")), events)
  }
  @Test fun correctAcknowledgmentsCannotHideMismatchedScenes() {
    reject("milestone_narrative_event_mismatch", response(scene("invented"))
      .put("completedEventIds", JSONArray().put("door")), due("door"))
  }
  @Test fun missingOrBlankOccurrenceConsequenceAndCommunicationAreRejected() {
    for (key in listOf("occurrence", "consequence")) {
      reject("milestone_narrative_content_missing", response(scene("door").put(key, "  ")), due("door"))
      reject("milestone_narrative_content_missing", response(scene("door").apply { remove(key) }), due("door"))
    }
    reject("milestone_narrative_content_missing", response(meeting().apply {
      getJSONObject("communication").put("text", " ")
    }), character())
  }
  @Test fun anotherSpeakerCannotSatisfyRequiredCharacterCommunication() {
    reject("milestone_character_speaker_mismatch", response(meeting().apply {
      getJSONObject("communication").put("speakerId", CAO_MINH_ID)
    }), character())
  }
  @Test fun laterStoryDisclosureAlsoRequiresTheScheduledSpeaker() {
    val events = due("lucia_meet")
    events.getJSONObject(0).put("communicationCharacterId", LUCIA_ID)
    val value = response(meeting())
    assertTrue(MilestoneNarrationValidator.validate(value, events).reply.contains("dây điện"))
    value.getJSONArray("eventNarrations").getJSONObject(0).remove("communication")
    reject("milestone_character_communication_missing", value, events)
  }
  @Test fun invalidJsonTypesCannotBeCoercedIntoContent() {
    reject("milestone_narrative_content_missing", response(scene("door").put("occurrence", 17)), due("door"))
    reject("milestone_narrative_event_mismatch", response(scene("17")).put("completedEventIds", JSONArray().put(17)), due("17"))
    reject("milestone_narrative_content_missing", response(scene("door")).put("reply", JSONObject.NULL), due("door"))
    reject("milestone_narrative_content_missing", response(scene("door").put("occurrence", "x".repeat(6001))), due("door"))
    reject("milestone_narrative_content_missing", response(scene("door").put("consequence", "x\u0000y")), due("door"))
  }
  @Test fun noEventsNeedsNonemptyProseAndEmptyArrays() {
    assertEquals("Cao Minh dừng lại.", MilestoneNarrationValidator.validate(response(), due()).reply)
    reject("milestone_narrative_content_missing", response().put("reply", ""), due())
    reject("milestone_narrative_events_missing", response(scene("invented")), due())
  }
  @Test fun semanticMislabelingRemainsAnExplicitLimit() {
    // A deterministic renderer cannot infer that model-labelled speech is actually silence.
    // This test documents the boundary rather than claiming independent semantic proof.
    val value = response(meeting().apply { getJSONObject("communication").put("text", "Cô im lặng.") })
    assertTrue(MilestoneNarrationValidator.validate(value, character()).reply.contains("Cô im lặng."))
  }
}
