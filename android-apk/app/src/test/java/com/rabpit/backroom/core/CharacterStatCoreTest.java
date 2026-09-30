package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CharacterStatCoreTest {
  @Test public void maTonProjectsSeparateNinetyNineBonusIntoCombatStats() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    JSONObject stats = projected.getJSONObject("stats");
    JSONObject status = projected.getJSONObject("combatStatus");

    for (String key : new String[]{"STR","DEF","SKL","VIT"}) {
      assertEquals(5, stats.getJSONObject(key).getInt("base"));
      assertEquals(99, stats.getJSONObject(key).getInt("passiveBonus"));
      assertEquals(104, stats.getJSONObject(key).getInt("effective"));
      assertEquals(0, stats.getJSONObject(key).getInt("temporaryModifier"));
    }
    assertEquals(327, status.getInt("damage"));
    assertEquals(90.8d, status.getDouble("defendPercent"), 0.001d);
    assertEquals(50, status.getInt("criticalChancePercent"));
    assertEquals(35, status.getInt("evasionPercent"));
    assertEquals(50, status.getInt("resCriticalPercent"));
    assertEquals(50, status.getInt("resEvasionPercent"));
  }

  @Test public void maTonBonusStaysSeparateFromBaseAndCoreUpgrade() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject before = new CharacterStatCore().project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR");
    assertEquals(5, before.getInt("base"));
    assertEquals(99, before.getInt("passiveBonus"));
    assertEquals(104, before.getInt("effective"));
    assertEquals(1, before.getInt("nextCoreCost"));

    progression.grantCore(state, 1);
    progression.upgradeStat(state, "cao_minh", "STR");

    assertEquals(6, progression.profile(state, "cao_minh").getJSONObject("stats").getInt("STR"));
    JSONObject after = new CharacterStatCore().project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR");
    assertEquals(6, after.getInt("base"));
    assertEquals(99, after.getInt("passiveBonus"));
    assertEquals(105, after.getInt("effective"));
    assertEquals(1, after.getInt("nextCoreCost"));
  }

  @Test public void derivedStatusTracksCoreStatsAndRuntimeBaseAttack() throws Exception {
    JSONObject state = baseState();
    state.getJSONObject("player").put("attack", 40);
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject stats = progression.profile(state, "cao_minh").getJSONObject("stats");
    stats.put("STR", 10).put("DEF", 10).put("SKL", 10).put("VIT", 10);

    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    JSONObject status = projected.getJSONObject("combatStatus");

    assertEquals(456, status.getInt("damage"));
    assertEquals(91.2d, status.getDouble("defendPercent"), 0.001d);
    assertEquals(50, status.getInt("criticalChancePercent"));
    assertEquals(35, status.getInt("evasionPercent"));
    assertEquals(50, status.getInt("resCriticalPercent"));
    assertEquals(50, status.getInt("resEvasionPercent"));
  }

  @Test public void companionsDoNotReceiveMaTonBonus() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject syvial = new CharacterStatCore().project(state, "syvial", progression);
    JSONObject str = syvial.getJSONObject("stats").getJSONObject("STR");
    assertEquals(5, str.getInt("base"));
    assertEquals(0, str.getInt("passiveBonus"));
    assertEquals(5, str.getInt("effective"));
  }

  @Test public void derivedChanceCapsRemainBoundedAtExtremeStats() {
    assertEquals(50, CharacterStatCore.criticalChancePercent(999));
    assertEquals(35, CharacterStatCore.evasionPercent(999));
    assertEquals(50, CharacterStatCore.criticalResistancePercent(999));
    assertEquals(50, CharacterStatCore.evasionResistancePercent(999));
  }

  @Test public void survivalPenaltyChangesProjectionWithoutMutatingPassiveBonus() throws Exception {
    JSONObject state = baseState().put("gameTime", new JSONObject()
        .put("elapsedSubjectiveMinutes", 12L * 60L));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.setCurrentHp(state, "cao_minh", 30);
    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    JSONObject vit = projected.getJSONObject("stats").getJSONObject("VIT");
    assertEquals(5, vit.getInt("base"));
    assertEquals(99, vit.getInt("passiveBonus"));
    assertEquals(103, vit.getInt("effective"));
    assertEquals(-1, vit.getInt("temporaryModifier"));
    assertEquals(540, projected.getInt("maxHp"));
    assertEquals(30, projected.getInt("currentHp"));
    new SurvivalCore().restoreWater(state, "cao_minh", 100);
    assertEquals(545, new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression).getInt("maxHp"));
    assertEquals(30, progression.profile(state, "cao_minh").getInt("currentHp"));
  }

  @Test public void activeStatusRefreshesOnceStacksBySourceAndExpiresByClock() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:a", "explorer_turn",
        2, "STR", 2);
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:a", "explorer_turn",
        2, "STR", 2);
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:b", "explorer_turn",
        1, "STR", -1);
    CharacterStatCore stats = new CharacterStatCore();
    assertEquals(105, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    String saved = state.toString();
    state = new JSONObject(saved);
    assertEquals(105, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    progression.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    assertEquals(106, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    progression.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    assertEquals(104, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
  }

  private static JSONObject baseState() throws Exception {
    return new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
  }
}
