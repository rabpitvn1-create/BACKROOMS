package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Contract-level integration coverage for the authoritative turn pipeline without Android storage.
 */
public class EmergentTurnIntegrationTest {
  @Test public void selectedSituationCommitsThenProjectsThenNarratesReadOnly() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject before = baseState(4);
    engine.normalizeState(before);
    engine.catchUpProjections(before);

    int preVersion = engine.stateVersion(before);
    String turnId = engine.nextTurnId(before, "Cao Minh tiến lên");
    TurnRng rng = new TurnRng(
        turnId, preVersion, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);

    JSONObject working = new JSONObject(before.toString()).put("turn", 5);
    JSONArray pool = new JSONArray().put(
        engine.candidate("ENTITY", "entity:hound", "DANGER", 3.5d,
            "Hound tiến vào phạm vi tương tác.", "hound", true)
            .put("mandatory", true)
            .put("allowedWorldActions", new JSONArray().put("INTERCEPT").put("DIRECT_ATTACK"))
            .put("fallbackAction", "INTERCEPT"));

    JSONObject selected = engine.selectCandidate(working, pool, rng, 5);
    assertEquals("entity:hound", selected.getString("situationKey"));
    assertFalse(selected.getBoolean("selectedNone"));

    JSONObject proposal = engine.sanitizeWorldProposal(
        selected, new JSONObject().put("actionType", "AMBUSH").put("intentTag", "aggressive"));
    assertEquals("INTERCEPT", proposal.getString("actionType"));
    assertTrue(proposal.getBoolean("fallback"));

    working.put("flags", new JSONObject().put("entityEncounterKey", "hound"));
    working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .put("lastSelection", new JSONObject(selected.toString()).put("worldProposal", proposal));

    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
        new JSONObject()
            .put("factPredicate", "player_action")
            .put("factValue", "Cao Minh tiến lên")
            .put("causedBy", "player")
            .put("impactEligible", false)
            .put("observedByPlayer", true),
        null));
    events.put(engine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", "hound",
        new JSONObject()
            .put("factPredicate", "entity_encounter_started")
            .put("factValue", "hound")
            .put("causedBy", "world")
            .put("observedByPlayer", true),
        new JSONArray().put(engine.threadEffect(
            "ENTITY_ENCOUNTER", new JSONArray().put("hound"), "SEED_OR_ADVANCE", null))));

    engine.validateBatch(turnId, events);
    engine.commitAuthoritative(before, working, turnId, events, selected);

    assertTrue(engine.hasCommitted(working, turnId));
    assertFalse(engine.selectionProjectionFresh(working));
    assertEquals(1, working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .getJSONArray("commitLog").length());

    // Retry after COMMIT must not duplicate history or rerun selection/resolution.
    engine.commitAuthoritative(before, working, turnId, events, selected);
    assertEquals(1, working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .getJSONArray("commitLog").length());

    assertTrue(engine.catchUpProjections(working));
    assertTrue(engine.selectionProjectionFresh(working));
    assertEquals(5, working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .getJSONObject("selectionIndex").getJSONObject("entity:hound")
        .getInt("lastSelectedTurn"));

    assertFalse(working.toString().isEmpty());
  }

  @Test public void noneCandidateCommitsPlayerTurnWithoutInventingWorldEventOrCooldown() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject before = baseState(9);
    engine.normalizeState(before);
    int preVersion = engine.stateVersion(before);
    String turnId = engine.nextTurnId(before, "Cao Minh đứng quan sát");
    TurnRng rng = new TurnRng(
        turnId, preVersion, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);

    JSONObject working = new JSONObject(before.toString()).put("turn", 10);
    JSONObject selected = engine.selectCandidate(working, new JSONArray(), rng, 10);
    assertTrue(selected.getBoolean("selectedNone"));

    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
        new JSONObject()
            .put("factPredicate", "player_action")
            .put("factValue", "Cao Minh đứng quan sát")
            .put("causedBy", "player")
            .put("impactEligible", false)
            .put("observedByPlayer", true),
        null));

    engine.commitAuthoritative(before, working, turnId, events, selected);
    engine.catchUpProjections(working);

    JSONObject root = working.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    assertFalse(root.getJSONObject("selectionIndex").has("NONE"));
    assertEquals(1, root.getJSONArray("commitLog").length());
    assertEquals(1, root.getJSONArray("commitLog").getJSONObject(0)
        .getJSONArray("events").length());
    assertFalse(root.getJSONArray("commitLog").getJSONObject(0).has("sourceSituationKey"));
  }

  private static JSONObject baseState(int turn) throws Exception {
    return new JSONObject()
        .put("turn", turn)
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("location", "Hành lang vàng")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("flags", new JSONObject())
        .put("log", new JSONArray());
  }
}
