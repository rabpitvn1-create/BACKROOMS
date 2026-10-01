package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmSelectionGateTest {
  @Test public void exactEntitySelectionAuthorizesOnlyMatchingEntityCommand() throws Exception {
    TurnRng rng = resumedCandidateRng("turn-4c", 3, 1);
    JSONObject selected = selected("ENTITY", "entity:hound", "hound");
    JSONObject working = tracedState("entity:hound", false, 0);

    JSONObject auth = GmSelectionGate.issue(
        "turn-4c", "base-hash", selected, working, rng);
    JSONObject allowed = GmSelectionGate.authorize(
        auth, "turn-4c", "base-hash", "START_ENTITY_ENCOUNTER",
        new JSONObject().put("entityKey", "hound"));
    JSONObject wrong = GmSelectionGate.authorize(
        auth, "turn-4c", "base-hash", "START_ENTITY_ENCOUNTER",
        new JSONObject().put("entityKey", "smiler"));

    assertTrue(auth.getBoolean("valid"));
    assertTrue(allowed.getBoolean("allowed"));
    assertEquals("entity:hound",
        allowed.getJSONObject("selectionEvidence").getString("selectedSituationKey"));
    assertFalse(wrong.getBoolean("allowed"));
    assertEquals("selection_candidate_mismatch", wrong.getString("reason"));
  }

  @Test public void noneSelectionCannotAuthorizeWorldSituationCommand() throws Exception {
    TurnRng rng = resumedCandidateRng("turn-none", 2, 1);
    JSONObject selected = new JSONObject()
        .put("situationKey", "NONE")
        .put("kind", "NONE")
        .put("selectedNone", true);
    JSONObject auth = GmSelectionGate.issue(
        "turn-none", "base", selected, tracedState("NONE", true, 0), rng);

    JSONObject result = GmSelectionGate.authorize(
        auth, "turn-none", "base", "DISCOVER_CHEST", new JSONObject());

    assertFalse(result.getBoolean("allowed"));
    assertEquals("selection_none", result.getString("reason"));
  }

  @Test public void staleTurnOrBaseStateCannotReuseAuthorization() throws Exception {
    TurnRng rng = resumedCandidateRng("turn-4c", 3, 1);
    JSONObject auth = GmSelectionGate.issue(
        "turn-4c", "base-hash", selected("CHARACTER", "character:luc_tram", "luc_tram"),
        tracedState("character:luc_tram", false, 0), rng);

    assertEquals("selection_turn_mismatch",
        GmSelectionGate.validationReason(auth, "other-turn", "base-hash"));
    assertEquals("selection_base_state_mismatch",
        GmSelectionGate.validationReason(auth, "turn-4c", "other-base"));
  }

  @Test public void rngCounterMustMatchRecordedDrawSequence() throws Exception {
    TurnRng rng = resumedCandidateRng("turn-4c", 3, 2);
    JSONObject auth = GmSelectionGate.issue(
        "turn-4c", "base-hash", selected("CHEST", "resource:chest:1", "1"),
        tracedState("resource:chest:1", false, 0), rng);

    assertFalse(auth.getBoolean("valid"));
    assertEquals("selection_rng_counter_mismatch", auth.getString("reason"));
  }

  @Test public void tamperedAuthorizationHashFailsClosed() throws Exception {
    TurnRng rng = resumedCandidateRng("turn-4c", 3, 1);
    JSONObject auth = GmSelectionGate.issue(
        "turn-4c", "base-hash", selected("CHARACTER", "character:luc_tram", "luc_tram"),
        tracedState("character:luc_tram", false, 0), rng);
    auth.put("payloadKey", "syvial");

    assertEquals("selection_authorization_hash_mismatch",
        GmSelectionGate.validationReason(auth, "turn-4c", "base-hash"));
  }

  private static TurnRng resumedCandidateRng(
      String turnId, int stateVersion, int drawsUsed) {
    TurnRng rng = new TurnRng(
        turnId, stateVersion,
        EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
    rng.resume(TurnRng.Scope.CANDIDATE_SELECTION, drawsUsed);
    return rng;
  }

  private static JSONObject selected(String kind, String situationKey, String payloadKey)
      throws Exception {
    return new JSONObject()
        .put("situationKey", situationKey)
        .put("kind", kind)
        .put("payloadKey", payloadKey)
        .put("eligibilityRuleId", "canon:" + situationKey)
        .put("selectedNone", false);
  }

  private static JSONObject tracedState(
      String selectedSituationKey, boolean selectedNone, int drawSeq) throws Exception {
    JSONObject trace = new JSONObject()
        .put("turn", 4)
        .put("selectedSituationKey", selectedSituationKey)
        .put("selectedNone", selectedNone)
        .put("rngScope", TurnRng.Scope.CANDIDATE_SELECTION.name())
        .put("rngDrawSeq", drawSeq);
    return new JSONObject().put(EmergentTurnEngine.ROOT_KEY,
        new JSONObject().put("selectionTrace", new JSONArray().put(trace)));
  }
}
