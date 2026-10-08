package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LucTramEntityTest {
  @Test public void actorPoisonAndStunSurviveReloadAndConsumeOneAction() throws Exception {
    JSONObject state = Combat93EngineTest.state().put("party", new JSONArray());
    CombatChoiceEngine.start(state, "luc_tram_hac_hoa", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 10000).put("maxHp", 10000);
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    actor.put("poisonTurns", 2).put("poisonPercent", 3).put("stunTurns", 1);
    state = new JSONObject(state.toString());
    Combat93EngineTest.finish(state, 1, 2, 3, 4, 6);
    combat = state.getJSONObject("combat");
    actor = combat.getJSONArray("participants").getJSONObject(0);
    assertEquals(10000, combat.getJSONObject("entity").getInt("hp"));
    assertTrue(combat.getJSONArray("feedbackEvents").toString().contains("-3000 HP"));
    // Cao Minh's existing post-turn passive may heal the poison damage back.
    assertTrue(state.getJSONArray("log").toString().contains("không thể hành động"));
    assertEquals(1, combat.getJSONArray("resolvedEntityTurns").length());
  }

  @Test public void darkEntityHasItsOwnIdentityAndStageScaledV2BaseStats() throws Exception {
    JSONObject state = Combat93EngineTest.state().put("combatStageIndex", 2);
    CombatChoiceEngine.start(state, "luc_tram_hac_hoa", 0);
    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    assertEquals("luc_tram_hac_hoa", entity.getString("key"));
    assertEquals(50, entity.getInt("baseHp"));
    assertEquals(24, entity.getInt("baseDamage"));
    assertEquals(60, entity.getInt("maxHp"));
    assertEquals(29, entity.getInt("attack"));
    assertEquals(7, CombatChoiceEngine.entitySkillCount("luc_tram_hac_hoa"));
    assertEquals(5, CombatChoiceEngine.characterProcCount("luc_tram"));
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("luc_tram"));
  }

  @Test public void entityCanApplySourceProcEffectsAndReloadKeepsThem() throws Exception {
    java.lang.reflect.Method response = CombatChoiceEngine.class.getDeclaredMethod(
      "resolveEntityResponse", JSONObject.class, JSONObject.class, JSONObject.class, boolean.class);
    response.setAccessible(true);
    boolean poison = false, stun = false, bleed = false, armor = false, ultimate = false;
    for (int seed = 1; seed < 20000 && !(poison && stun && bleed && armor && ultimate); seed++) {
      JSONObject combat = new JSONObject().put("seed", seed).put("round", 1).put("actorIndex", 0)
        .put("feedbackEvents", new JSONArray());
      JSONObject actor = new JSONObject().put("hp", 100000).put("maxHp", 100000).put("DEF", 5);
      JSONObject entity = new JSONObject().put("key", "luc_tram_hac_hoa").put("hp", 50).put("attack", 24);
      String summary = (String) response.invoke(null, combat, actor, entity, false);
      JSONObject reloaded = new JSONObject(actor.toString());
      poison |= reloaded.optInt("poisonTurns") == 2;
      stun |= reloaded.optInt("stunTurns") == 1;
      bleed |= reloaded.optInt("bleedTurns") == 2;
      armor |= reloaded.optInt("armorBreakPercent") == 10;
      ultimate |= summary.contains("Thiên Kiếm Định Giới");
    }
    assertTrue(poison && stun && bleed && armor && ultimate);
  }
}
