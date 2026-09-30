package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmTransactionContractTest {
  @Test public void almondWaterPickupRejectedCannotBecomeCommittedClaim() {
    JSONObject proposal = proposal(
        group("discover-water",
            command("discover-water", "DISCOVER_ITEM", "almond_water")),
        group("pickup-water",
            command("pickup-water", "ADD_INVENTORY_ITEM", "almond_water")));

    JSONObject validation = new JSONObject()
        .put("turnId", "turn-42")
        .put("commandResults", new JSONArray()
            .put(accepted("discover-water", "ITEM_DISCOVERED", "almond_water"))
            .put(rejected("pickup-water", "INVENTORY_FULL")));

    JSONObject committed = GmTransactionContract.resolveValidatedTurn(proposal, validation);

    assertEquals(1, committed.getJSONArray("committedEvents").length());
    assertTrue(GmTransactionContract.supportsClaim(
        committed, "ITEM_DISCOVERED", "almond_water"));
    assertFalse(GmTransactionContract.supportsClaim(
        committed, "INVENTORY_ITEM_ADDED", "almond_water"));
    assertEquals("ACCEPTED",
        committed.getJSONArray("groups").getJSONObject(0).getString("status"));
    assertEquals("REJECTED",
        committed.getJSONArray("groups").getJSONObject(1).getString("status"));
  }

  @Test public void anyRejectedCommandRollsBackWholeCausalGroup() {
    JSONObject proposal = proposal(
        group("open-chest",
            command("open", "OPEN_CHEST", "chest-7"),
            command("grant", "ADD_INVENTORY_ITEM", "almond_water")));

    JSONObject validation = new JSONObject()
        .put("turnId", "turn-42")
        .put("commandResults", new JSONArray()
            .put(accepted("open", "CHEST_OPENED", "chest-7"))
            .put(rejected("grant", "INVENTORY_FULL")));

    JSONObject committed = GmTransactionContract.resolveValidatedTurn(proposal, validation);

    assertEquals(0, committed.getJSONArray("committedEvents").length());
    assertEquals("REJECTED",
        committed.getJSONArray("groups").getJSONObject(0).getString("status"));
    assertFalse(GmTransactionContract.supportsClaim(
        committed, "CHEST_OPENED", "chest-7"));
  }

  @Test public void proposalRejectsDuplicateCommandIds() {
    JSONObject proposal = proposal(
        group("g1", command("same", "DISCOVER_ITEM", "almond_water")),
        group("g2", command("same", "ADD_INVENTORY_ITEM", "almond_water")));

    assertEquals("command_id_duplicate", GmTransactionContract.validateProposal(proposal));
  }

  @Test public void proposalRequiresAtomicCausalGroups() {
    JSONObject group = group("g1", command("c1", "DISCOVER_ITEM", "almond_water"));
    group.put("atomic", false);

    assertEquals("causal_group_must_be_atomic",
        GmTransactionContract.validateProposal(proposal(group)));
  }

  private static JSONObject proposal(JSONObject... groups) {
    JSONArray array = new JSONArray();
    for (JSONObject group : groups) array.put(group);
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", "turn-42")
        .put("baseStateHash", "state-hash-before")
        .put("causalGroups", array);
  }

  private static JSONObject group(String groupId, JSONObject... commands) {
    JSONArray array = new JSONArray();
    for (JSONObject command : commands) array.put(command);
    return new JSONObject()
        .put("groupId", groupId)
        .put("atomic", true)
        .put("commands", array);
  }

  private static JSONObject command(String commandId, String type, String subjectKey) {
    return new JSONObject()
        .put("commandId", commandId)
        .put("type", type)
        .put("payload", new JSONObject().put("subjectKey", subjectKey));
  }

  private static JSONObject accepted(String commandId, String eventType, String subjectKey) {
    return new JSONObject()
        .put("commandId", commandId)
        .put("accepted", true)
        .put("event", new JSONObject()
            .put("eventType", eventType)
            .put("subjectKey", subjectKey));
  }

  private static JSONObject rejected(String commandId, String reason) {
    return new JSONObject()
        .put("commandId", commandId)
        .put("accepted", false)
        .put("reason", reason);
  }
}
