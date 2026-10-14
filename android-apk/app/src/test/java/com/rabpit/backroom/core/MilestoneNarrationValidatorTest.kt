package com.rabpit.backroom.core

import com.rabpit.backroom.core.MilestoneNarrationValidator.MilestoneNarrationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for MilestoneNarrationValidator.
 *
 * The validator replaced the old form-based dialogue check in MainActivity
 * (which required quotation marks, em/en dashes, or a character-name mention in
 * the prose and threw "milestone_character_dialogue_missing"). Canon
 * (GmNarrativeVoicePolicy) governs HOW characters speak; this validator checks
 * only the EVENT contract via Gehihi's declared "completedEventIds".
 */
class MilestoneNarrationValidatorTest {

  private fun ids(vararg ids: String): JSONArray = JSONArray(ids.toList())

  private fun due(vararg ids: String): JSONArray = JSONArray().apply {
    ids.forEachIndexed { index, id ->
      put(JSONObject().put("id", id).put("turn", index + 1).put("kind", "STORY")
        .put("action", "TEST_ONLY").put("event", "TEST_ONLY").put("outcome", "TEST_ONLY"))
    }
  }

  private fun dueIds(vararg ids: String): List<String> =
    MilestoneNarrationValidator.dueIds(due(*ids))

  private fun expectMissing(block: () -> Unit) {
    val e = assertThrows(MilestoneNarrationException::class.java, block)
    assertEquals("milestone_narrative_events_missing", e.message)
  }

  private fun expectMismatch(block: () -> Unit) {
    val e = assertThrows(MilestoneNarrationException::class.java, block)
    assertEquals("milestone_narrative_event_mismatch", e.message)
  }

  @Test fun matchingIdsInOrderPass() {
    MilestoneNarrationValidator.requireAcknowledgment(
      ids("warning", "lucia_meet"), dueIds("warning", "lucia_meet"))
  }

  @Test fun nullCompletedEventIdsIsRejected() {
    expectMissing {
      MilestoneNarrationValidator.requireAcknowledgment(null, dueIds("warning"))
    }
  }

  @Test fun fewerIdsThanDueIsRejected() {
    expectMissing {
      MilestoneNarrationValidator.requireAcknowledgment(ids("warning"), dueIds("warning", "lucia_meet"))
    }
  }

  @Test fun moreIdsThanDueIsRejected() {
    expectMissing {
      MilestoneNarrationValidator.requireAcknowledgment(
        ids("warning", "lucia_meet", "extra"), dueIds("warning", "lucia_meet"))
    }
  }

  @Test fun duplicateIdsAreRejected() {
    expectMismatch {
      MilestoneNarrationValidator.requireAcknowledgment(
        ids("warning", "warning"), dueIds("warning", "lucia_meet"))
    }
  }

  @Test fun outOfOrderIdsAreRejected() {
    expectMismatch {
      MilestoneNarrationValidator.requireAcknowledgment(
        ids("lucia_meet", "warning"), dueIds("warning", "lucia_meet"))
    }
  }

  @Test fun idNotInDueScheduleIsRejected() {
    // Declaring an event that is not due (a skipped event claimed as completed)
    // must not pass, even when the count matches.
    expectMismatch {
      MilestoneNarrationValidator.requireAcknowledgment(
        ids("warning", "invented_event"), dueIds("warning", "lucia_meet"))
    }
  }

  @Test fun emptyDueWithEmptyAcknowledgmentPasses() {
    MilestoneNarrationValidator.requireAcknowledgment(ids(), dueIds())
  }

  @Test fun dueIdsExtractsIdsFromEventObjects() {
    val events = JSONArray().apply {
      put(JSONObject().put("id", "e1").put("kind", "FOLLOWER").put("characterId", "lucia"))
      put(JSONObject().put("id", "e2").put("kind", "ENTITY").put("entityKey", "hound"))
      put(JSONObject().put("id", "e3").put("kind", "STORY"))
    }
    assertEquals(listOf("e1", "e2", "e3"), MilestoneNarrationValidator.dueIds(events))
  }

  @Test fun followerEventPassesWithoutDialogueFormChecks() {
    // Regression for the "milestone_character_dialogue_missing" live blocker:
    // requireAcknowledgment takes NO reply text at all — the old check that
    // demanded quotation marks, em/en dashes, or a character-name mention in the
    // prose has been removed. Any of these Canon-valid narrations pass as long as
    // the declared IDs match the due schedule:
    //
    // - "Lucia gật đầu, ánh mắt đầy nghi ngờ." (no quotes, no dashes)
    // - "— Cô là ai? — giọng cô lạnh như thép." (dashes, but not required)
    // - "\"Đừng lại gần,\" cô nói." (quotes, but not required)
    // - "Người lính trẻ quan sát Cao Minh từ xa." (no name at all)
    //
    // The API itself is the proof: there is no reply parameter to inspect.
    val followerDue = JSONArray().apply {
      put(JSONObject().put("id", "lucia_meet").put("turn", 3).put("kind", "FOLLOWER")
        .put("characterId", "lucia").put("characterName", "Lucia")
        .put("action", "TEST_ONLY").put("event", "TEST_ONLY").put("outcome", "TEST_ONLY"))
    }
    MilestoneNarrationValidator.requireAcknowledgment(
      ids("lucia_meet"),
      MilestoneNarrationValidator.dueIds(followerDue))
  }
}
