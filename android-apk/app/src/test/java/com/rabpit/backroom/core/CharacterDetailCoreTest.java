package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CharacterDetailCoreTest {
  @Test public void projectionShowsOnlyNewCombatStatsAndSharedCore() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 0));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.grantCore(state, 3);

    new CharacterDetailCore().projectState(state);

    JSONObject details = state.getJSONObject("partyDetails");
    assertEquals("cao_minh", details.getString("leaderId"));
    assertEquals(4, details.getInt("maxMembers"));
    assertEquals(3, details.getInt("coreCount"));
    JSONObject cao = details.getJSONArray("members").getJSONObject(0);
    assertEquals(50, cao.getInt("currentHp"));
    assertEquals(545, cao.getInt("maxHp"));
    JSONObject stats = cao.getJSONObject("stats");
    assertEquals(5, stats.getJSONObject("STR").getInt("base"));
    assertEquals(99, stats.getJSONObject("STR").getInt("passiveBonus"));
    assertEquals(104, stats.getJSONObject("STR").getInt("effective"));
    assertEquals(104, stats.getJSONObject("DEF").getInt("effective"));
    assertEquals(104, stats.getJSONObject("SKL").getInt("effective"));
    assertEquals(104, stats.getJSONObject("VIT").getInt("effective"));
    assertFalse(cao.has("explorer"));
    assertFalse(cao.has("exp"));
    JSONArray passives = cao.getJSONArray("passives");
    assertEquals(2, passives.length());
    assertEquals("Đại Đạo Ma Tôn", passives.getJSONObject(0).getString("name"));
    assertEquals(10, passives.getJSONObject(0).getInt("healMaxHpPercent"));
    assertEquals(20, passives.getJSONObject(0).getInt("attackPerTurnPercent"));
    assertEquals(20, passives.getJSONObject(0).getInt("criticalPerTurnPercent"));
    assertEquals(50, passives.getJSONObject(0).getInt("allyCriticalBonusPercent"));
    assertEquals("Ma Tôn", passives.getJSONObject(1).getString("name"));
    assertEquals(99, passives.getJSONObject(1).getInt("allStatsBonus"));
    assertEquals(true, passives.getJSONObject(1).getBoolean("separateFromBaseStats"));
    assertEquals("NORMAL", cao.getJSONObject("physiology").getString("hunger"));
  }

  @Test public void joinedLucTramUsesCurrentRuntimeBaseHpAndOwnPhysiology() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "luc_tram").put("name", "Lục Trầm").put("joined", true)))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 5));

    new CharacterDetailCore().projectState(state);

    JSONArray members = state.getJSONObject("partyDetails").getJSONArray("members");
    assertEquals(2, members.length());
    JSONObject lucTram = members.getJSONObject(1);
    assertEquals("luc_tram", lucTram.getString("id"));
    assertEquals(50, lucTram.getInt("maxHp"));
    assertEquals(50, lucTram.getInt("baseMaxHp"));
    assertEquals(5, lucTram.getJSONObject("stats").getJSONObject("VIT").getInt("effective"));
    assertNotNull(lucTram.getJSONArray("inventory"));
  }

  @Test public void legacyThresholdsAndPercentagesRemainSurvivalOwned() throws Exception {
    JSONObject physiology = CharacterDetailCore.derivePhysiology(
        12L * 60L, 12L * 60L, 12L * 60L);
    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
    assertEquals(83, physiology.getInt("foodPercent"));
    assertEquals(75, physiology.getInt("waterPercent"));
    assertEquals(66, physiology.getInt("restPercent"));
  }

  @Test public void statusProjectionUsesCoreEffectsAndHpCondition() throws Exception {
    JSONObject state = new JSONObject().put("player", new JSONObject().put("name", "Cao Minh")
        .put("condition", "Ổn định")).put("party", new JSONArray());
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.applyStatusEffect(state, "cao_minh", "focus", "core:event",
        "explorer_turn", 2, "STR", 2);
    progression.setCurrentHp(state, "cao_minh", 0);
    new CharacterDetailCore().projectState(state);
    JSONObject member = state.getJSONObject("partyDetails").getJSONArray("members").getJSONObject(0);
    assertEquals("Bị hạ", member.getString("condition"));
    assertEquals(106, member.getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    assertEquals(2, member.getJSONArray("statusEffects").getJSONObject(0)
        .getInt("remainingTurns"));
  }
}
