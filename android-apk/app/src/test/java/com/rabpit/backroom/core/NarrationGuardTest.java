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

  @Test public void committedTurnEvidenceDoesNotTreatOldInventoryAsNewLoot() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    state.put("inventory", new JSONArray().put(
        new JSONObject().put("id", "almond-water").put("name", "Almond Water").put("quantity", 1)));
    engine.normalizeState(state);
    JSONObject before = new JSONObject(state.toString());
    String turnId = engine.nextTurnId(state, "đi tiếp");
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
        new JSONObject().put("factPredicate", "player_action").put("factValue", "đi tiếp")
            .put("causedBy", "player").put("impactEligible", false)
            .put("observedByPlayer", true), null));
    engine.commitAuthoritative(before, state, turnId, events, null);

    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(state, turnId);

    assertTrue(evidence.getBoolean("available"));
    assertFalse(CommittedTurnNarrationEvidence.hasClaim(
        evidence, "ITEM_ACQUIRED", "Almond Water"));
  }
}
