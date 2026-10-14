package com.rabpit.backroom.core

import org.json.JSONArray

/**
 * Validates that Gehihi acknowledged every due Milestone event before a turn is committed.
 *
 * Canon is the authority on HOW characters speak (see GmNarrativeVoicePolicy: dialogue
 * belongs to the speaker's origin, era, and relationship — never a fixed format).
 * This validator therefore checks only the EVENT contract, never dialogue form:
 *
 * - No requirement for quotation marks (" " " "), em/en dashes (— –), or any
 *   specific dialogue layout. Valid Canon-compliant narration takes many forms.
 * - No requirement that a character's name appear in the prose. A character may be
 *   portrayed via pronouns, titles, roles, or indirect reference per Canon.
 *
 * What this validator guarantees:
 * - Gehihi declared [completedEventIds] matching every due event, in order, with no
 *   duplicates. A missing, extra, duplicated, or mismatched ID is rejected.
 *
 * What this validator does NOT guarantee (documented limitation):
 * - A declared event ID is Gehihi's assertion that the event was narrated; it is not
 *   independent proof that the event's content appears in the prose. Content-level
 *   proof would require a second AI verifier, which this project deliberately does
 *   not use (see StoryMilestoneRuntime: "Milestone events are committed by Core,
 *   with no AI evidence verifier"). The declaration is the contract; Core commits
 *   the event and milestone history records what was claimed.
 *
 * Milestone decides the event. Canon decides character consistency. Gehihi decides
 * how to tell it. This validator enforces only the first.
 */
object MilestoneNarrationValidator {

  /** Thrown when Gehihi's event acknowledgment is structurally invalid. */
  class MilestoneNarrationException(message: String) : Exception(message)

  /**
   * Require Gehihi to acknowledge every scheduled event before a turn is committed.
   *
   * @param completedEventIds the "completedEventIds" array from Gehihi's JSON reply,
   *   or null if absent.
   * @param dueIds the IDs of due Milestone events, in schedule order.
   * @throws MilestoneNarrationException if the acknowledgment is missing, has the wrong
   *   length, contains duplicates, or does not match the due IDs in order.
   */
  @JvmStatic
  @Throws(MilestoneNarrationException::class)
  fun requireAcknowledgment(completedEventIds: JSONArray?, dueIds: List<String>) {
    if (completedEventIds == null || completedEventIds.length() != dueIds.size)
      throw MilestoneNarrationException("milestone_narrative_events_missing")
    val seen = HashSet<String>()
    for (i in dueIds.indices) {
      val acknowledged = completedEventIds.optString(i, "")
      // Duplicate or out-of-order/mismatched IDs are rejected: the declaration must
      // mirror the due schedule exactly. An ID claimed for a skipped event is a mismatch.
      if (!seen.add(acknowledged) || acknowledged != dueIds[i])
        throw MilestoneNarrationException("milestone_narrative_event_mismatch")
    }
  }

  /**
   * Extract due event IDs from the due-events JSONArray (as produced by
   * StoryMilestoneRuntime.dueJson / GameCore.milestoneDueEvents).
   */
  @JvmStatic
  fun dueIds(due: JSONArray): List<String> =
    (0 until due.length()).map { due.getJSONObject(it).getString("id") }
}
