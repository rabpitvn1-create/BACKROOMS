package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class Combat93EngineTest {
  static JSONObject state() throws Exception {
    JSONObject profiles = new JSONObject();
    JSONArray party = new JSONArray();
    for (String id : new String[]{"cao_minh", "lucia", "luc_tram"}) {
      profiles.put(id, new JSONObject().put("currentHp", 100000).put("maxHp", 100000)
          .put("stats", new JSONObject().put("STR", 5).put("DEF", 5).put("SKL", 5).put("VIT", 5))
          .put("statusEffects", new JSONArray()));
      if (!id.equals("cao_minh")) party.put(new JSONObject().put("id", id).put("name", id));
    }
    return new JSONObject().put("turn", 7).put("location", "Level 0").put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", party).put("flags", new JSONObject())
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Encounter")))
        .put("characterProgression", new JSONObject().put("characters", profiles)
            .put("coreResource", new JSONObject().put("quantity", 0)));
  }
  static void finish(JSONObject state, int... values) throws Exception {
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    dice.put("values", new JSONArray(values)).put("hasRolled", true);
    CombatChoiceEngine.finishHand(state);
    CombatChoiceEngine.resolveFinalized(state);
  }
  @Test public void actorsRotateAndEntitiesRespondWithoutAdvancingExplorerTurn() throws Exception {
    JSONObject state = state();
    CombatChoiceEngine.start(state, new JSONArray().put("slenderman").put("diep_minh"), 0, "TURN_7", 0);
    for (int index = 0; index < 3; index++) {
      assertEquals(index, state.getJSONObject("combat").getInt("actorIndex"));
      finish(state, 1, 2, 3, 4, 6);
      assertEquals(7, state.getInt("turn"));
      assertEquals(2, state.getJSONObject("combat").getJSONArray("resolvedEntityTurns").length());
    }
    assertEquals(2, state.getJSONObject("combat").getInt("round"));
    assertEquals(0, state.getJSONObject("combat").getInt("actorIndex"));
  }
  @Test public void reloadPreservesDiceAndCannotResetRerolls() throws Exception {
    JSONObject state = state();
    CombatChoiceEngine.start(state, "slenderman", 0);
    CombatChoiceEngine.setHold(state, 0, false);
    CombatChoiceEngine.roll(state);
    JSONObject loaded = new JSONObject(state.toString());
    String dice = loaded.getJSONObject("combat").getJSONObject("diceState").toString();
    CombatChoiceEngine.start(loaded, "hound", 0);
    assertEquals(dice, loaded.getJSONObject("combat").getJSONObject("diceState").toString());
    assertEquals("slenderman", loaded.getJSONObject("combat").getJSONObject("entity").getString("key"));
  }
  @Test public void victoryRewardsAndLootAreGrantedOnce() throws Exception {
    JSONObject state = state();
    state.getJSONObject("player").put("baseAttack", 10000);
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    combat.getJSONObject("entity").put("evasionPercent", 0);
    finish(state, 2, 2, 2, 2, 2);
    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertEquals(1, state.getJSONArray("combat93Loot").length());
    int core = state.getJSONObject("characterProgression").getJSONObject("coreResource").getInt("quantity");
    assertEquals(2, core);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals(core, state.getJSONObject("characterProgression").getJSONObject("coreResource").getInt("quantity"));
    assertEquals(1, state.getJSONArray("combat93Loot").length());
  }
  @Test public void stageScalingAndStatusStackingMatchSource() throws Exception {
    assertEquals(180, new EntityStatCore().profile("hound", 150, 15, 2).getInt("maxHp"));
    JSONObject entity = new JSONObject();
    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 2, 10);
    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 3, 15);
    assertEquals(2, entity.optInt("bleedTurns"));
    assertEquals(25, entity.optInt("bleedPercent"));
    CombatChoiceEngine.applyStackingEffect(entity, "Choáng", 2, 0);
    CombatChoiceEngine.applyStackingEffect(entity, "Choáng", 2, 0);
    assertEquals(3, entity.optInt("stunTurns"));
  }
  @Test public void feedbackCarriesResolvedActorAndEntityIdentity() throws Exception {
    JSONObject state = state();
    CombatChoiceEngine.start(state, "slenderman", 0);
    state.getJSONObject("combat").getJSONObject("entity").put("evasionPercent", 0);
    finish(state, 1, 1, 2, 3, 4);
    JSONArray events = state.getJSONObject("combat").getJSONArray("feedbackEvents");
    assertTrue(events.length() > 0);
    for (int i = 0; i < events.length(); i++) {
      assertEquals(0, events.getJSONObject(i).getInt("actorIndex"));
      assertEquals("slenderman", events.getJSONObject(i).getString("entityKey"));
    }
  }
}
