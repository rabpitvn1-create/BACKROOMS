package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Small deterministic campaign-path smoke test.
 *
 * This intentionally runs pure Core components rather than mocking WebView/AI. It covers the
 * gameplay authority path that content/features depend on: exploration -> transition -> social
 * continuity -> resource interaction -> combat -> JSON reload.
 */
public class GameplaySmokeTest {
  @Test public void explorationSocialResourceCombatAndReloadStayPlayable() throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject()
        .put("location", LevelCore.LEVEL_ZERO_START_LOCATION));

    LevelCore level = new LevelCore((Context) null, bound -> 10);
    ItemCore items = new ItemCore(bound -> 0);
    CharacterEncounterCore characters = new CharacterEncounterCore();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    SurvivalCore survival = new SurvivalCore();
    EmergentTurnEngine emergent = new EmergentTurnEngine();

    level.normalizeState(state);
    characters.normalizeState(state);
    progression.normalizeState(state);
    survival.normalizeState(state);
    items.normalizeInventory(state);
    emergent.normalizeState(state);
    emergent.catchUpProjections(state);

    // Exploration: eight deterministic successes expose the next Level 0 node.
    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      level.rollRouteForExplorerAction(state, "đi tiếp", bound -> 10);
    }
    assertTrue(state.getJSONObject(LevelCore.ROUTE_STATE).getBoolean("exitAvailable"));

    state.put("turn", LevelCore.ROUTE_REQUIRED_STREAK + 1);
    assertTrue(level.applyPlayerTransitionIfRequested(state, "đi qua"));
    assertEquals("0.1", state.getString(LevelCore.LEVEL_KEY));
    assertFalse(state.getJSONObject(LevelCore.ROUTE_STATE).getBoolean("exitAvailable"));

    // Social: an already-eligible Syvial candidate becomes committed continuity.
    JSONObject syvialCandidate = findCandidate(
        characters.situationCandidates(state), "character:syvial");
    assertNotNull(syvialCandidate);
    characters.activateEncounterCandidate(state, "syvial");
    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("syvial", state.getJSONArray("party").getJSONObject(0).getString("id"));

    String socialTurnId = emergent.nextTurnId(state, "smoke-social");
    JSONArray socialEvents = new JSONArray();
    socialEvents.put(emergent.event(
        socialTurnId, socialEvents, "CHARACTER_ENCOUNTERED", "SOCIAL", "syvial",
        new JSONObject()
            .put("factPredicate", "character_encountered")
            .put("factValue", "syvial")
            .put("causedBy", "world")
            .put("observedByPlayer", true),
        new JSONArray().put(emergent.threadEffect(
            "SOCIAL_CONTACT", new JSONArray().put("syvial"), "SEED_OR_ADVANCE", null))));
    emergent.commitAuthoritative(
        state, socialTurnId, socialEvents,
        new JSONObject(syvialCandidate.toString()).put("selectedNone", false));
    emergent.catchUpProjections(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    assertTrue(root.has(CampaignSkeleton.ROOT_KEY));
    assertTrue(root.has(NarrativeSkeleton.ROOT_KEY));
    assertTrue(root.getJSONObject(NarrativeSkeleton.ROOT_KEY)
        .getJSONArray("importantRelationships").toString().contains("syvial"));
    assertEquals("WEIGHT_ONLY", root.getJSONObject("director").getString("authority"));

    // Resource: an eligible chest can be activated/opened and grants Core-owned loot.
    JSONObject chestCandidate = items.explorationChestCandidate(state);
    assertNotNull(chestCandidate);
    assertEquals("0.1", chestCandidate.getJSONArray("keyRefs").getString(0));
    int inventoryBefore = state.getJSONArray("inventory").length();
    items.activateExplorationChest(state);
    String lootName = items.openChest(state, bound -> 0);
    assertFalse(lootName.trim().isEmpty());
    assertFalse(state.getJSONObject("flags").getBoolean("chestPresent"));
    assertTrue(state.getJSONArray("inventory").length() > inventoryBefore);
    assertTrue(state.getJSONObject("flags").getInt("lastChestCoreReward") > 0);

    // Combat + reload: serialized state retains the active fight and can resolve a finalized hand.
    state.getJSONObject("flags").put("entityEncounterKey", "hound");
    state.put("log", new JSONArray().put(
        new JSONObject().put("role", "gm").put("text", "Smoke combat.")));
    CombatChoiceEngine.start(
        state, "hound", 0, "smoke-combat", emergent.stateVersion(state));
    assertTrue(CombatChoiceEngine.isActive(state));
    assertTrue(state.getJSONObject("combat")
        .getJSONObject("diceState").getBoolean("hasRolled"));

    JSONObject reloaded = new JSONObject(state.toString());
    CombatChoiceEngine.normalizeTerminalEncounter(reloaded);
    assertTrue(CombatChoiceEngine.isActive(reloaded));
    CombatChoiceEngine.finishHand(reloaded);
    CombatChoiceEngine.resolveFinalized(reloaded);

    JSONObject combat = reloaded.getJSONObject("combat");
    assertFalse(combat.optString("resolvedActorName", "").trim().isEmpty());
    assertTrue(combat.optBoolean("resolvedEntityTurn", false)
        || !combat.optBoolean("active", false));

    JSONObject afterReload = new JSONObject(reloaded.toString());
    characters.normalizeState(afterReload);
    progression.normalizeState(afterReload);
    survival.normalizeState(afterReload);
    items.normalizeInventory(afterReload);
    emergent.normalizeState(afterReload);
    emergent.catchUpProjections(afterReload);

    assertEquals("0.1", afterReload.getString(LevelCore.LEVEL_KEY));
    assertEquals("syvial", afterReload.getJSONArray("party").getJSONObject(0).getString("id"));
    assertTrue(afterReload.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .has(NarrativeSkeleton.ROOT_KEY));
  }

  private static JSONObject findCandidate(JSONArray candidates, String situationKey) {
    if (candidates == null) return null;
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.optJSONObject(i);
      if (candidate != null && situationKey.equals(candidate.optString("situationKey", ""))) {
        return candidate;
      }
    }
    return null;
  }
}
