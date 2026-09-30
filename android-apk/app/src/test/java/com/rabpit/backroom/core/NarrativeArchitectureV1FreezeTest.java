package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Executable guard for the frozen Narrative Architecture v1 authority boundary. */
public class NarrativeArchitectureV1FreezeTest {
  @Test public void frozenAuthorityBoundaryRemainsExecutable() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject()
        .put("turn", 5)
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("location", "Level 0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("flags", new JSONObject().put("freezeSentinel", "unchanged"));

    engine.normalizeState(state);
    engine.catchUpProjections(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    assertTrue(root.has(CampaignSkeleton.ROOT_KEY));
    assertTrue(root.has(NarrativeSkeleton.ROOT_KEY));
    assertFalse(root.has("skeleton"));
    assertTrue(NarrativeSkeleton.contractValid(
        root.getJSONObject(NarrativeSkeleton.ROOT_KEY)));

    JSONObject director = root.getJSONObject("director");
    assertEquals("WEIGHT_ONLY", director.getString("authority"));
    assertFalse(director.getBoolean("canCreateCandidates"));
    assertFalse(director.getBoolean("canUnlockEligibility"));

    int factCount = root.getJSONArray("historicalFacts").length();
    int threadCount = root.getJSONArray("threads").length();
    JSONObject none = engine.selectCandidate(
        state, new JSONArray(), new TurnRng("freeze-empty-pool", engine.stateVersion(state),
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 5);

    assertTrue(none.getBoolean("selectedNone"));
    assertEquals(factCount, root.getJSONArray("historicalFacts").length());
    assertEquals(threadCount, root.getJSONArray("threads").length());
    assertEquals("unchanged", state.getJSONObject("flags").getString("freezeSentinel"));

    JSONObject narrationMutation = new JSONObject()
        .put("reply", "Không thay đổi authority.")
        .put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray())
        .put(NarrativeSkeleton.ROOT_KEY,
            new JSONObject().put("nextEvent", "forbidden"));
    assertFalse(NarrationGuard.validate(narrationMutation, state).isEmpty());
  }
}
