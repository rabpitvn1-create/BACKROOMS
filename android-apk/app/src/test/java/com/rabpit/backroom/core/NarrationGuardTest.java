package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NarrationGuardTest {
  @Test public void rejectsAuthoritativeFields() throws Exception {
    JSONObject generated = new JSONObject()
        .put("reply", "Cao Minh đứng yên.")
        .put("transitionTarget", "1")
        .put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray());
    assertFalse(NarrationGuard.validate(generated, new JSONObject()).isEmpty());
  }

  @Test public void rejectsDialogueWithoutCommittedEncounter() throws Exception {
    JSONObject generated = new JSONObject()
        .put("reply", "Có tiếng nói.")
        .put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray().put("Xin chào.").put("Ai đó?"));
    assertFalse(NarrationGuard.validate(generated, new JSONObject()).isEmpty());
  }

  @Test public void acceptsBoundedDialogueForPendingEncounter() throws Exception {
    JSONObject state = new JSONObject()
        .put("characterEncounter", new JSONObject()
            .put("pendingIntro", new JSONArray().put("luc_tram")));
    JSONObject generated = new JSONObject()
        .put("reply", "Hai người đối mặt.")
        .put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray().put("Ma đầu.").put("Lục tiên tử."));
    assertTrue(NarrationGuard.validate(generated, state).isEmpty());
  }

  @Test public void rejectsChoicesDuringEntityEncounter() throws Exception {
    JSONObject state = new JSONObject()
        .put("flags", new JSONObject().put("entityEncounterKey", "hound"));
    JSONObject generated = new JSONObject()
        .put("reply", "Hound áp sát.")
        .put("choices", new JSONArray().put(new JSONObject().put("text", "Đi tiếp")))
        .put("encounterDialogue", new JSONArray());
    assertFalse(NarrationGuard.validate(generated, state).isEmpty());
  }
}
