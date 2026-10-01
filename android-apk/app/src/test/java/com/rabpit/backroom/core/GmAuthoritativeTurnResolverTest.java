package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmAuthoritativeTurnResolverTest {
  private final CharacterEncounterCore characters = new CharacterEncounterCore();
  private final CharacterProgressionCore progression = new CharacterProgressionCore();
  private final EmergentTurnEngine engine = new EmergentTurnEngine();

  private GmAuthoritativeTurnResolver resolver() {
    GmCommandAuthority authority = new GmCommandAuthority(
        new ItemCore(), null, null, characters, progression);
    return new GmAuthoritativeTurnResolver(authority, engine);
  }

  @Test public void selectedCharacterBecomesStateAndCanonDomainEventTogether() throws Exception {
    JSONObject base = baseState(1);
    JSONObject selected = selected("CHARACTER", "character:syvial", "syvial");
    JSONObject auth = authorization(base, selected, "turn-4d", "base-hash");
    JSONArray prepared = preparedEvents("turn-4d");
    JSONObject proposal = proposal(
        command("character", "START_CHARACTER_ENCOUNTER",
            new JSONObject().put("characterId", "syvial")));

    JSONObject resolved = resolver().resolve(
        base, prepared, selected, auth, proposal, "turn-4d", "base-hash");

    assertTrue(resolved.getBoolean("valid"));
    assertEquals("syvial", resolved.getJSONObject("finalState")
        .getJSONArray("party").getJSONObject(0).getString("id"));
    JSONArray events = resolved.getJSONArray("events");
    assertEquals("PLAYER_ACTION_RESOLVED", events.getJSONObject(0).getString("eventType"));
    assertEquals("CHARACTER_ENCOUNTERED", events.getJSONObject(1).getString("eventType"));
    assertEquals(1, events.getJSONObject(1).getInt("eventSeq"));
    assertEquals("character:syvial", resolved.getJSONObject("candidateEnvelope")
        .getJSONObject("candidate").getJSONObject("selectionEvidence")
        .getString("selectedSituationKey"));
  }

  @Test public void selectedSituationCannotDisappearFromAuthoritativeTurn() throws Exception {
    JSONObject base = baseState(1);
    JSONObject selected = selected("CHARACTER", "character:syvial", "syvial");
    JSONObject auth = authorization(base, selected, "turn-4d", "base-hash");
    JSONObject proposal = new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-4d")
        .put("baseStateHash", "base-hash")
        .put("causalGroups", new JSONArray());

    JSONObject resolved = resolver().resolve(
        base, preparedEvents("turn-4d"), selected, auth, proposal, "turn-4d", "base-hash");

    assertFalse(resolved.getBoolean("valid"));
    assertEquals("selected_situation_not_committed", resolved.getString("reason"));
  }

  @Test public void noneSelectionMayCommitNonSelectionCommand() throws Exception {
    JSONObject base = baseState(1);
    progression.grantCore(base, 1000);
    JSONObject selected = new JSONObject()
        .put("situationKey", "NONE").put("kind", "NONE").put("selectedNone", true);
    JSONObject proposal = proposal(
        command("upgrade", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR")));

    JSONObject resolved = resolver().resolve(
        base, preparedEvents("turn-4d"), selected, null, proposal, "turn-4d", "base-hash");

    assertTrue(resolved.getBoolean("valid"));
    assertEquals("CHARACTER_STAT_UPGRADED",
        resolved.getJSONArray("events").getJSONObject(1).getString("eventType"));
    assertEquals(0, resolved.getJSONObject("candidateEnvelope").getJSONObject("candidate")
        .getJSONObject("selectionEvidence").length());
  }

  private JSONObject baseState(int level) throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    state.put("currentLevel", level).put(LevelCore.LEVEL_KEY, String.valueOf(level)).put("turn", 4);
    characters.normalizeState(state);
    progression.normalizeState(state);
    engine.normalizeState(state);
    return state;
  }

  private JSONArray preparedEvents(String turnId) throws Exception {
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
        new JSONObject().put("factPredicate", "player_action").put("factValue", "đi tiếp")
            .put("causedBy", "player").put("impactEligible", false)
            .put("observedByPlayer", true), null));
    return events;
  }

  private JSONObject authorization(
      JSONObject working, JSONObject selected, String turnId, String baseHash) throws Exception {
    TurnRng rng = new TurnRng(
        turnId, 0, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
    rng.resume(TurnRng.Scope.CANDIDATE_SELECTION, 1);
    JSONObject root = working.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    root.getJSONArray("selectionTrace").put(new JSONObject()
        .put("turn", working.optInt("turn", 1))
        .put("selectedSituationKey", selected.getString("situationKey"))
        .put("selectedNone", false)
        .put("rngScope", TurnRng.Scope.CANDIDATE_SELECTION.name())
        .put("rngDrawSeq", 0));
    return GmSelectionGate.issue(turnId, baseHash, selected, working, rng);
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

  private static JSONObject proposal(JSONObject command) throws Exception {
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-4d")
        .put("baseStateHash", "base-hash")
        .put("causalGroups", new JSONArray().put(new JSONObject()
            .put("groupId", "g1").put("atomic", true)
            .put("commands", new JSONArray().put(command))));
  }

  private static JSONObject command(String id, String type, JSONObject payload) throws Exception {
    return new JSONObject().put("commandId", id).put("type", type).put("payload", payload);
  }
}
