package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmTransactionExecutorTest {
  private GmTransactionExecutor executor() {
    GmCommandAuthority authority = new GmCommandAuthority(
        new ItemCore(), null, null, new CharacterEncounterCore(), new CharacterProgressionCore());
    return new GmTransactionExecutor(authority);
  }

  @Test public void executionIsPureAndDeterministicForSameInput() throws Exception {
    JSONObject state = fundedState();
    String callerStateBefore = GmShadowPlanner.canonicalJson(state);
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));

    JSONObject first = executor().execute(state, proposal, "turn-4b1", "base-hash");
    JSONObject second = executor().execute(state, proposal, "turn-4b1", "base-hash");

    assertEquals(callerStateBefore, GmShadowPlanner.canonicalJson(state));
    assertTrue(first.getBoolean("valid"));
    assertEquals(first.getString("simulatedAfterHash"), second.getString("simulatedAfterHash"));
    assertEquals(
        GmShadowPlanner.canonicalJson(first.getJSONObject("afterState")),
        GmShadowPlanner.canonicalJson(second.getJSONObject("afterState")));
    assertNotEquals(first.getString("simulatedBeforeHash"), first.getString("simulatedAfterHash"));
  }

  @Test public void rejectedCommandRollsBackEntireCausalGroup() throws Exception {
    JSONObject state = fundedState();
    JSONObject proposal = proposal(
        group("g1",
            command("upgrade-str", "UPGRADE_STAT",
                new JSONObject().put("characterId", "cao_minh").put("stat", "STR")),
            command("unknown", "WRITE_RAW_STATE", new JSONObject())));

    JSONObject draft = executor().execute(state, proposal, "turn-4b1", "base-hash");

    assertEquals(0, draft.getInt("acceptedGroups"));
    assertEquals(1, draft.getInt("rejectedGroups"));
    assertEquals(draft.getString("simulatedBeforeHash"), draft.getString("simulatedAfterHash"));
    assertEquals(0, draft.getJSONObject("resolvedTurn").getJSONArray("committedEvents").length());
  }

  @Test public void rejectedLaterGroupPreservesEarlierAcceptedGroupOnly() throws Exception {
    JSONObject state = fundedState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    int beforeStr = progression.profile(state, "cao_minh").getJSONObject("stats").getInt("STR");
    int beforeDef = progression.profile(state, "cao_minh").getJSONObject("stats").getInt("DEF");

    JSONObject proposal = proposal(
        group("g1",
            command("upgrade-str", "UPGRADE_STAT",
                new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))),
        group("g2",
            command("upgrade-def", "UPGRADE_STAT",
                new JSONObject().put("characterId", "cao_minh").put("stat", "DEF")),
            command("unknown", "WRITE_RAW_STATE", new JSONObject())));

    JSONObject draft = executor().execute(state, proposal, "turn-4b1", "base-hash");
    JSONObject after = draft.getJSONObject("afterState");
    JSONObject afterStats = progression.profile(after, "cao_minh").getJSONObject("stats");

    assertEquals(1, draft.getInt("acceptedGroups"));
    assertEquals(1, draft.getInt("rejectedGroups"));
    assertEquals(beforeStr + 1, afterStats.getInt("STR"));
    assertEquals(beforeDef, afterStats.getInt("DEF"));
    JSONArray evidence = draft.getJSONObject("resolvedTurn").getJSONArray("committedEvents");
    assertEquals(1, evidence.length());
    assertEquals("CHARACTER_STAT_UPGRADED", evidence.getJSONObject(0).getString("eventType"));
  }

  @Test public void selectionGateStillFailsClosedInPureExecutor() throws Exception {
    JSONObject state = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("entity", "START_ENTITY_ENCOUNTER",
            new JSONObject().put("entityKey", "hound"))));

    JSONObject draft = executor().execute(state, proposal, "turn-4b1", "base-hash");

    assertTrue(draft.getBoolean("valid"));
    assertEquals(0, draft.getInt("acceptedGroups"));
    assertEquals(draft.getString("simulatedBeforeHash"), draft.getString("simulatedAfterHash"));
    JSONObject result = draft.getJSONArray("commandResults").getJSONObject(0);
    assertFalse(result.getBoolean("accepted"));
    assertEquals("selection_authorization_missing", result.getString("reason"));
  }

  @Test public void selectedCharacterCommandExecutesOnlyWithMatchingCoreAuthorization() throws Exception {
    JSONObject state = fundedState().put("currentLevel", 1).put(LevelCore.LEVEL_KEY, "1");
    JSONObject proposal = proposal(
        group("g1", command("character", "START_CHARACTER_ENCOUNTER",
            new JSONObject().put("characterId", "syvial"))));
    JSONObject authorization = authorization("CHARACTER", "character:syvial", "syvial");

    JSONObject draft = executor().execute(
        state, proposal, "turn-4b1", "base-hash", authorization);

    assertEquals(1, draft.getInt("acceptedGroups"));
    assertEquals("syvial",
        draft.getJSONObject("afterState").getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("character:syvial",
        draft.getJSONObject("selectionEvidence").getString("selectedSituationKey"));
    JSONObject event = draft.getJSONObject("resolvedTurn")
        .getJSONArray("committedEvents").getJSONObject(0);
    assertEquals("CHARACTER_ENCOUNTERED", event.getString("eventType"));
    assertEquals("CANDIDATE_SELECTION",
        event.getJSONObject("selectionEvidence").getString("rngScope"));
  }

  @Test public void selectedChestCommandUsesCoreCandidateInsteadOfGmSpawnAuthority() throws Exception {
    JSONObject state = fundedState().put("currentLevel", 0).put(LevelCore.LEVEL_KEY, "0");
    JSONObject proposal = proposal(
        group("g1", command("chest", "DISCOVER_CHEST", new JSONObject())));
    JSONObject authorization = authorization("CHEST", "resource:chest:0", "0");

    JSONObject draft = executor().execute(
        state, proposal, "turn-4b1", "base-hash", authorization);

    assertEquals(1, draft.getInt("acceptedGroups"));
    assertTrue(draft.getJSONObject("afterState").getJSONObject("flags")
        .getBoolean("chestPresent"));
    assertEquals("resource:chest:0",
        draft.getJSONObject("selectionEvidence").getString("selectedSituationKey"));
  }

  @Test public void mismatchedSelectedCandidateCannotBeRedirectedByGm() throws Exception {
    JSONObject state = fundedState().put("currentLevel", 1).put(LevelCore.LEVEL_KEY, "1");
    JSONObject proposal = proposal(
        group("g1", command("character", "START_CHARACTER_ENCOUNTER",
            new JSONObject().put("characterId", "syvial"))));
    JSONObject authorization = authorization("CHARACTER", "character:luc_tram", "luc_tram");

    JSONObject draft = executor().execute(
        state, proposal, "turn-4b1", "base-hash", authorization);

    assertEquals(0, draft.getInt("acceptedGroups"));
    assertEquals("selection_candidate_mismatch",
        draft.getJSONArray("commandResults").getJSONObject(0).getString("reason"));
  }

  private static JSONObject authorization(String kind, String situationKey, String payloadKey)
      throws Exception {
    TurnRng rng = new TurnRng(
        "turn-4b1", 0, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
    rng.resume(TurnRng.Scope.CANDIDATE_SELECTION, 1);
    JSONObject selected = new JSONObject()
        .put("situationKey", situationKey)
        .put("kind", kind)
        .put("payloadKey", payloadKey)
        .put("eligibilityRuleId", "canon:" + situationKey)
        .put("selectedNone", false);
    JSONObject trace = new JSONObject()
        .put("selectedSituationKey", situationKey)
        .put("selectedNone", false)
        .put("rngScope", TurnRng.Scope.CANDIDATE_SELECTION.name())
        .put("rngDrawSeq", 0);
    JSONObject working = new JSONObject().put(EmergentTurnEngine.ROOT_KEY,
        new JSONObject().put("selectionTrace", new JSONArray().put(trace)));
    return GmSelectionGate.issue("turn-4b1", "base-hash", selected, working, rng);
  }

  private static JSONObject fundedState() throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.grantCore(state, 1_000);
    return state;
  }

  private static JSONObject proposal(JSONObject... groups) throws Exception {
    JSONArray array = new JSONArray();
    for (JSONObject group : groups) array.put(group);
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-4b1")
        .put("baseStateHash", "base-hash")
        .put("causalGroups", array);
  }

  private static JSONObject group(String groupId, JSONObject... commands) throws Exception {
    JSONArray array = new JSONArray();
    for (JSONObject command : commands) array.put(command);
    return new JSONObject()
        .put("groupId", groupId)
        .put("atomic", true)
        .put("commands", array);
  }

  private static JSONObject command(
      String commandId, String type, JSONObject payload) throws Exception {
    return new JSONObject()
        .put("commandId", commandId)
        .put("type", type)
        .put("payload", payload);
  }
}
