package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmShadowPlannerTest {
  @Test public void promptAndPlannerKeyAreDeterministicForEquivalentContext() throws Exception {
    JSONObject a = context();
    JSONObject b = new JSONObject()
        .put("action", "Tôi đi tiếp")
        .put("rngContext", new JSONObject()
            .put("rngSchemaVersion", "rng-v1")
            .put("canonVersion", "canon-v1")
            .put("preTurnStateVersion", 7)
            .put("turnId", "turn-7"))
        .put("baseStateHash", "hash-before")
        .put("turnId", "turn-7");

    String promptA = GmShadowPlanner.buildPrompt(
        a, new JSONObject().put("actor", "cao_minh"),
        "level", "entity", "item", "character", "canon");
    String promptB = GmShadowPlanner.buildPrompt(
        b, new JSONObject().put("actor", "cao_minh"),
        "level", "entity", "item", "character", "canon");

    assertEquals(promptA, promptB);
    assertEquals(GmShadowPlanner.plannerKey(a, promptA), GmShadowPlanner.plannerKey(b, promptB));
  }

  @Test public void proposalFailsClosedOnTurnOrStateMismatch() throws Exception {
    JSONObject proposal = proposal("turn-7", "hash-before");
    JSONObject wrongTurn = GmShadowPlanner.validateProposal(proposal, "turn-8", "hash-before");
    JSONObject wrongHash = GmShadowPlanner.validateProposal(proposal, "turn-7", "other-hash");

    assertFalse(wrongTurn.getBoolean("valid"));
    assertEquals("turn_id_mismatch", wrongTurn.getString("reason"));
    assertFalse(wrongHash.getBoolean("valid"));
    assertEquals("base_state_hash_mismatch", wrongHash.getString("reason"));
  }

  @Test public void unknownSchemaFieldsFailClosed() throws Exception {
    JSONObject proposal = proposal("turn-7", "hash-before").put("narration", "không được phép");

    JSONObject validation = GmShadowPlanner.validateProposal(proposal, "turn-7", "hash-before");

    assertFalse(validation.getBoolean("valid"));
    assertEquals("proposal_unknown_field", validation.getString("reason"));
  }

  @Test public void rawStatePatchIsRejectedEvenInsidePayload() throws Exception {
    JSONObject proposal = proposal("turn-7", "hash-before");
    proposal.getJSONArray("causalGroups").getJSONObject(0)
        .getJSONArray("commands").getJSONObject(0)
        .getJSONObject("payload").put("statePatch", new JSONObject().put("inventory", "almond_water"));

    JSONObject validation = GmShadowPlanner.validateProposal(proposal, "turn-7", "hash-before");

    assertFalse(validation.getBoolean("valid"));
    assertTrue(validation.getString("reason").startsWith("raw_state_mutation_forbidden"));
  }

  @Test public void comparisonIsReadOnlyAndReportsV2Delta() throws Exception {
    JSONObject before = new JSONObject()
        .put("currentLevelKey", "0")
        .put("inventory", new JSONArray())
        .put("log", new JSONArray());
    JSONObject after = new JSONObject(before.toString())
        .put("currentLevelKey", "1")
        .put("turn", 2)
        .put("log", new JSONArray().put("ignored"));
    JSONObject proposal = proposal("turn-7", "hash-before");
    String beforeCopy = before.toString();
    String afterCopy = after.toString();
    String proposalCopy = proposal.toString();

    JSONObject comparison = GmShadowPlanner.compareToV2(
        proposal, before, after, new JSONObject().put("situationKey", "route:test"));

    assertEquals(beforeCopy, before.toString());
    assertEquals(afterCopy, after.toString());
    assertEquals(proposalCopy, proposal.toString());
    assertTrue(comparison.getJSONArray("v2ChangedTopLevelKeys").toString().contains("currentLevelKey"));
    assertFalse(comparison.getJSONArray("v2ChangedTopLevelKeys").toString().contains("log"));
    assertEquals("route:test", comparison.getString("v2SelectedSituation"));
  }

  private static JSONObject context() throws Exception {
    return new JSONObject()
        .put("turnId", "turn-7")
        .put("baseStateHash", "hash-before")
        .put("action", "Tôi đi tiếp")
        .put("rngContext", new JSONObject()
            .put("turnId", "turn-7")
            .put("preTurnStateVersion", 7)
            .put("canonVersion", "canon-v1")
            .put("rngSchemaVersion", "rng-v1"));
  }

  private static JSONObject proposal(String turnId, String baseHash) throws Exception {
    JSONObject command = new JSONObject()
        .put("commandId", "c1")
        .put("type", "DISCOVER_ITEM")
        .put("payload", new JSONObject().put("subjectKey", "almond_water"));
    JSONObject group = new JSONObject()
        .put("groupId", "g1")
        .put("atomic", true)
        .put("commands", new JSONArray().put(command));
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("turnId", turnId)
        .put("baseStateHash", baseHash)
        .put("causalGroups", new JSONArray().put(group));
  }
}
