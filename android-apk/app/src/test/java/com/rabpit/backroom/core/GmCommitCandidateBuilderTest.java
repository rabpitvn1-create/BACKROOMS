package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmCommitCandidateBuilderTest {
  private GmTransactionExecutor executor() {
    GmCommandAuthority authority = new GmCommandAuthority(
        new ItemCore(), null, null, new CharacterEncounterCore(), new CharacterProgressionCore());
    return new GmTransactionExecutor(authority);
  }

  @Test public void buildsReplayVerifiedCandidateWithoutAfterState() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));
    JSONObject draft = executor().execute(before, proposal, "turn-4b2", "base-hash");

    JSONObject envelope = GmCommitCandidateBuilder.build(before, proposal, draft);
    JSONObject candidate = envelope.getJSONObject("candidate");

    assertTrue(envelope.getBoolean("valid"));
    assertTrue(candidate.getBoolean("replayVerified"));
    assertFalse(candidate.has("afterState"));
    assertFalse(AuthoritativeStatePatch.isEmpty(candidate.getJSONObject("stateDelta")));
    assertNotEquals(candidate.getString("beforeStateHash"), candidate.getString("afterStateHash"));
    assertEquals(1, candidate.getJSONArray("committedGroups").length());
    assertEquals(0, candidate.getJSONArray("rejectedGroups").length());
    assertEquals(1, candidate.getJSONArray("committedEvents").length());
    assertTrue(GmCommitCandidateBuilder.verify(before, proposal, envelope));
  }

  @Test public void sameInputAndProposalProduceSameTransactionHash() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));

    JSONObject draftA = executor().execute(before, proposal, "turn-4b2", "base-hash");
    JSONObject draftB = executor().execute(before, proposal, "turn-4b2", "base-hash");
    JSONObject candidateA = GmCommitCandidateBuilder.build(before, proposal, draftA)
        .getJSONObject("candidate");
    JSONObject candidateB = GmCommitCandidateBuilder.build(before, proposal, draftB)
        .getJSONObject("candidate");

    assertEquals(candidateA.getString("afterStateHash"), candidateB.getString("afterStateHash"));
    assertEquals(candidateA.getString("transactionHash"), candidateB.getString("transactionHash"));
  }

  @Test public void rejectedGroupIsRecordedButCreatesNoStateDelta() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1",
            command("upgrade-str", "UPGRADE_STAT",
                new JSONObject().put("characterId", "cao_minh").put("stat", "STR")),
            command("unknown", "WRITE_RAW_STATE", new JSONObject())));
    JSONObject draft = executor().execute(before, proposal, "turn-4b2", "base-hash");

    JSONObject envelope = GmCommitCandidateBuilder.build(before, proposal, draft);
    JSONObject candidate = envelope.getJSONObject("candidate");

    assertTrue(envelope.getBoolean("valid"));
    assertTrue(AuthoritativeStatePatch.isEmpty(candidate.getJSONObject("stateDelta")));
    assertEquals(candidate.getString("beforeStateHash"), candidate.getString("afterStateHash"));
    assertEquals(0, candidate.getJSONArray("committedGroups").length());
    assertEquals(1, candidate.getJSONArray("rejectedGroups").length());
    assertEquals(0, candidate.getJSONArray("committedEvents").length());
  }

  @Test public void excludedRootMutationFailsStrictReplayVerification() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));
    JSONObject draft = executor().execute(before, proposal, "turn-4b2", "base-hash");

    JSONObject tamperedAfter = draft.getJSONObject("afterState");
    tamperedAfter.put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "tampered")));
    draft.put("simulatedAfterHash", stateHash(tamperedAfter));

    JSONObject envelope = GmCommitCandidateBuilder.build(before, proposal, draft);

    assertFalse(envelope.getBoolean("valid"));
    assertEquals("replay_state_mismatch", envelope.getString("reason"));
  }

  @Test public void tamperedExecutionLedgerIsRejected() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));
    JSONObject draft = executor().execute(before, proposal, "turn-4b2", "base-hash");
    draft.getJSONObject("resolvedTurn").getJSONArray("committedEvents")
        .getJSONObject(0).put("subjectKey", "tampered");

    JSONObject envelope = GmCommitCandidateBuilder.build(before, proposal, draft);

    assertFalse(envelope.getBoolean("valid"));
    assertEquals("execution_ledger_mismatch", envelope.getString("reason"));
  }

  @Test public void transactionHashDetectsCandidateTampering() throws Exception {
    JSONObject before = fundedState();
    JSONObject proposal = proposal(
        group("g1", command("upgrade-str", "UPGRADE_STAT",
            new JSONObject().put("characterId", "cao_minh").put("stat", "STR"))));
    JSONObject draft = executor().execute(before, proposal, "turn-4b2", "base-hash");
    JSONObject envelope = GmCommitCandidateBuilder.build(before, proposal, draft);

    assertTrue(GmCommitCandidateBuilder.verify(before, proposal, envelope));
    envelope.getJSONObject("candidate").put("afterStateHash", "tampered");
    assertFalse(GmCommitCandidateBuilder.verify(before, proposal, envelope));
  }

  private static String stateHash(JSONObject value) throws Exception {
    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
    byte[] bytes = digest.digest(
        GmShadowPlanner.canonicalJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
    return hex.toString();
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
        .put("turnId", "turn-4b2")
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
