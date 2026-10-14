package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryContinuityNotesTest {
  private fun state(streak: Int): JSONObject = JSONObject()
    .put("level", JSONObject().put("id", "0").put("number", 0))
    .put("flags", JSONObject().put("exploration", JSONObject()
      .put(HiddenExitStreak.STREAK_KEY, streak).put("exitReady", streak >= 5)))

  private fun rolls(): String = JSONObject().put(HiddenExitStreak.ROLL_KEY,
    JSONObject().put("eligible", true).put("success", true)).toString()

  @Test fun confirmedFirstMeetingIsNotReportedAsNoNpc() {
    val before = state(0).put("pendingNpcEncounters", org.json.JSONArray()
      .put(JSONObject().put("characterId", "lucia").put("name", "Lucia")))
    val notes = StoryContinuityReducer.continuityNotes(before.toString(), "{}")
    assertTrue(notes.contains("Lucia: Core xác nhận gặp LẦN ĐẦU"))
    assertTrue(notes.contains("inParty=false"))
    assertFalse(notes.contains("Chưa gặp NPC nào"))
  }

  @Test fun newlyReservedEntityIsNotReportedAsAnAlreadyNarratedEncounter() {
    val before = state(0)
    before.getJSONObject("flags").put("entityEncounterKey", "smiler")
    before.put("pendingEntityEncounter", JSONObject()
      .put("entityKey", "hound").put("entityName", "Hound").put("encounterId", "new"))
    val notes = StoryContinuityReducer.continuityNotes(before.toString(), "{}")
    assertTrue(notes.contains("Hound ĐÃ xuất hiện"))
    assertFalse(notes.contains("đã xảy ra và đã được kể"))
  }

  @Test fun fifthSuccessIsANewExitWhileAnExistingExitStaysKnown() {
    val discovery = StoryContinuityReducer.continuityNotes(state(4).toString(), rolls())
    assertTrue(discovery.contains("EXIT VỪA ĐỦ ĐIỀU KIỆN"))
    assertFalse(discovery.contains("Chưa phát hiện Exit"))
    assertFalse(discovery.contains("PHÁT HIỆN TỪ TRƯỚC"))
    val known = StoryContinuityReducer.continuityNotes(state(5).toString(), "{}")
    assertTrue(known.contains("PHÁT HIỆN TỪ TRƯỚC"))
    assertFalse(known.contains("EXIT VỪA"))
  }

  @Test fun unconfirmedExitRemainsLocked() {
    val notes = StoryContinuityReducer.continuityNotes(state(3).toString(), rolls())
    assertTrue(notes.contains("Chưa phát hiện Exit"))
    assertFalse(notes.contains("EXIT VỪA"))
  }
}
