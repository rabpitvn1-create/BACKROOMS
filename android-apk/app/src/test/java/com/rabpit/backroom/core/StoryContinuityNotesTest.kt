package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryContinuityNotesTest {
  private fun state(streak: Int): JSONObject = JSONObject()
    .put("level", JSONObject().put("id", "0").put("number", 0))
    .put("flags", JSONObject().put("exploration", JSONObject()
      .put(HiddenExitStreak.STREAK_KEY, streak).put("exitReady", streak >= HiddenExitStreak.REQUIRED_STREAK)))

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

  @Test fun previouslyEncounteredAbsentCharactersDoNotExpandCurrentSceneNotes() {
    val encountered = org.json.JSONArray()
      .put(JSONObject().put("id", "lucia").put("name", "Lucia").put("present", false))
      .put(JSONObject().put("id", "iris").put("name", "Iris").put("present", true).put("inParty", false))
      .put(JSONObject().put("id", "syvial").put("name", "Syvial").put("present", false))
    val view = state(0)
      .put("encounteredCharacters", encountered)
      .put("party", org.json.JSONArray().put(JSONObject()
        .put("id", "syvial").put("name", "Syvial").put("presence", "ABSENT")))
    val notes = StoryContinuityReducer.continuityNotes(view.toString())
    assertTrue(notes.contains("Iris"))
    assertFalse(notes.contains("Lucia"))
    assertFalse(notes.contains("Syvial"))
    assertFalse(notes.contains("Chưa gặp NPC nào"))
  }

  @Test fun onlyAbsentEncounteredCharactersProduceOneGeneralAbsenceGuard() {
    val view = state(0).put("encounteredCharacters", org.json.JSONArray()
      .put(JSONObject().put("id", "lucia").put("name", "Lucia").put("present", false)))
    val notes = StoryContinuityReducer.continuityNotes(view.toString())
    assertTrue(notes.contains("không có NPC nào"))
    assertFalse(notes.contains("Lucia"))
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

  @Test fun thirdSuccessIsANewExitWhileAnExistingExitStaysKnown() {
    val discovery = StoryContinuityReducer.continuityNotes(state(2).toString(), rolls())
    assertTrue(discovery.contains("EXIT VỪA ĐỦ ĐIỀU KIỆN"))
    assertFalse(discovery.contains("Chưa phát hiện Exit"))
    assertFalse(discovery.contains("PHÁT HIỆN TỪ TRƯỚC"))
    val known = StoryContinuityReducer.continuityNotes(state(3).toString(), "{}")
    assertTrue(known.contains("PHÁT HIỆN TỪ TRƯỚC"))
    assertFalse(known.contains("EXIT VỪA"))
  }

  @Test fun unconfirmedExitRemainsLocked() {
    val notes = StoryContinuityReducer.continuityNotes(state(1).toString(), rolls())
    assertTrue(notes.contains("Chưa phát hiện Exit"))
    assertFalse(notes.contains("EXIT VỪA"))
  }
}
