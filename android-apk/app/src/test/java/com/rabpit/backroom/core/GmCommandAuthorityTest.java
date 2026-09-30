package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmCommandAuthorityTest {
  private GmCommandAuthority authority() {
    return new GmCommandAuthority(
        new ItemCore(), null, null, new CharacterEncounterCore(), new CharacterProgressionCore());
  }

  @Test public void registryHasExactlyOneOwnerPerCommandType() throws Exception {
    JSONArray registry = authority().registryDescriptor();
    Set<String> types = new HashSet<>();
    for (int i = 0; i < registry.length(); i++) {
      JSONObject entry = registry.getJSONObject(i);
      assertTrue(types.add(entry.getString("type")));
      assertFalse(entry.getString("owner").trim().isEmpty());
      assertFalse(entry.getString("status").trim().isEmpty());
    }
    assertTrue(types.contains("USE_ITEM"));
    assertTrue(types.contains("MOVE_LEVEL"));
    assertTrue(types.contains("START_ENTITY_ENCOUNTER"));
    assertTrue(types.contains("START_COMBAT"));
  }

  @Test public void acceptedCommandProducesCoreOwnedEvidenceWithoutMutatingInput() throws Exception {
    JSONObject state = fundedState();
    String before = state.toString();
    JSONObject proposal = proposal(group(
        command("upgrade", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));

    JSONObject validation = authority().validate(state, proposal, "turn-3", "base-hash");

    assertEquals(before, state.toString());
    assertTrue(validation.getBoolean("valid"));
    assertEquals(1, validation.getInt("acceptedGroups"));
    JSONArray events = validation.getJSONObject("resolvedTurn").getJSONArray("committedEvents");
    assertEquals(1, events.length());
    assertEquals("CHARACTER_STAT_UPGRADED", events.getJSONObject(0).getString("eventType"));
    assertEquals("CharacterProgressionCore", events.getJSONObject(0).getString("owner"));
    assertNotEquals(
        validation.getString("simulatedBeforeHash"), validation.getString("simulatedAfterHash"));
  }

  @Test public void rejectedCommandRollsBackEarlierCommandInSameCausalGroup() throws Exception {
    JSONObject state = fundedState();
    JSONObject proposal = proposal(group(
        command("upgrade", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR")),
        command("unknown", "WRITE_RAW_STATE", new JSONObject())));

    JSONObject validation = authority().validate(state, proposal, "turn-3", "base-hash");

    assertEquals(0, validation.getInt("acceptedGroups"));
    assertEquals(1, validation.getInt("rejectedGroups"));
    assertEquals(0,
        validation.getJSONObject("resolvedTurn").getJSONArray("committedEvents").length());
    assertEquals(
        validation.getString("simulatedBeforeHash"), validation.getString("simulatedAfterHash"));
    JSONArray results = validation.getJSONArray("commandResults");
    assertTrue(results.getJSONObject(0).getBoolean("accepted"));
    assertFalse(results.getJSONObject(1).getBoolean("accepted"));
    assertEquals("unknown_command_type", results.getJSONObject(1).getString("reason"));
  }

  @Test public void selectionOwnedMutationIsRegisteredButCannotBypassPhase4Gate() throws Exception {
    JSONObject state = fundedState();
    JSONObject proposal = proposal(group(
        command("entity", "START_ENTITY_ENCOUNTER",
            new JSONObject().put("entityKey", "hound"))));

    JSONObject validation = authority().validate(state, proposal, "turn-3", "base-hash");

    assertEquals(0, validation.getInt("acceptedGroups"));
    JSONObject result = validation.getJSONArray("commandResults").getJSONObject(0);
    assertEquals("EntityCore", result.getString("owner"));
    assertEquals("phase4_selection_gate_required", result.getString("reason"));
  }

  private static JSONObject fundedState() throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.grantCore(state, 1_000);
    return state;
  }

  private static JSONObject proposal(JSONObject group) throws Exception {
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-3")
        .put("baseStateHash", "base-hash")
        .put("causalGroups", new JSONArray().put(group));
  }

  private static JSONObject group(JSONObject... commands) throws Exception {
    JSONArray array = new JSONArray();
    for (JSONObject command : commands) array.put(command);
    return new JSONObject()
        .put("groupId", "group-1")
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
