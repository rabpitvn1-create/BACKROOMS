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
