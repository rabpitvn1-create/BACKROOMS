package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CharacterProgressionCoreTest {
  @Test public void allCharactersStartWithFourStatsAtFive() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    for (String id : new String[]{"cao_minh","luc_tram","syvial"}) {
      JSONObject stats = core.profile(state, id).getJSONObject("stats");
      assertEquals(5, stats.getInt("STR"));
      assertEquals(5, stats.getInt("DEF"));
      assertEquals(5, stats.getInt("SKL"));
      assertEquals(5, stats.getInt("VIT"));
      assertFalse(stats.has("AGI"));
      assertFalse(stats.has("CRIT"));
      assertFalse(stats.has("LUCK"));
      assertEquals(50, core.profile(state, id).getInt("currentHp"));
      assertEquals("cao_minh".equals(id) ? 55 : 50, core.profile(state, id).getInt("maxHp"));
      assertEquals(0, core.profile(state, id).getJSONArray("statusEffects").length());
    }
  }

  @Test public void legacyExpAndRandomStatsMigrateOnceToBaseline() throws Exception {
    JSONObject legacyProfile = new JSONObject()
        .put("baseStats", new JSONObject().put("STR", 99).put("DF", 88).put("AGI", 77).put("CRIT", 66))
        .put("explorer", 12).put("exp", 345).put("currentHp", 31).put("maxHp", 230);
    JSONObject state = baseState().put(CharacterProgressionCore.ROOT_KEY,
        new JSONObject().put("characters", new JSONObject().put("cao_minh", legacyProfile)));
    CharacterProgressionCore core = new CharacterProgressionCore();

    core.normalizeState(state);
    JSONObject migrated = core.profile(state, "cao_minh");
    assertEquals(5, migrated.getJSONObject("stats").getInt("STR"));
    assertEquals(5, migrated.getJSONObject("stats").getInt("DEF"));
    assertEquals(5, migrated.getJSONObject("stats").getInt("SKL"));
    assertEquals(5, migrated.getJSONObject("stats").getInt("VIT"));
    assertEquals(31, migrated.getInt("currentHp"));
    assertEquals(55, migrated.getInt("maxHp"));
    assertFalse(migrated.has("explorer"));
    assertFalse(migrated.has("exp"));
    assertFalse(migrated.has("baseStats"));

    String once = state.getJSONObject(CharacterProgressionCore.ROOT_KEY).toString();
    core.normalizeState(state);
    assertEquals(once, state.getJSONObject(CharacterProgressionCore.ROOT_KEY).toString());
  }

  @Test public void vitScalesFromCurrentRuntimeBaseHp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    assertEquals(50, core.profile(state, "cao_minh").getInt("baseMaxHp"));
    assertEquals(55, core.profile(state, "cao_minh").getInt("maxHp"));
    assertEquals(50, core.profile(state, "luc_tram").getInt("baseMaxHp"));
    assertEquals(50, core.profile(state, "luc_tram").getInt("maxHp"));

    core.grantCore(state, 1);
    core.upgradeStat(state, "luc_tram", "VIT");
    assertEquals(55, core.profile(state, "luc_tram").getInt("maxHp"));
  }

  @Test public void vitUpgradeAddsItsMaxHpGainToCurrentHp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    core.setCurrentHp(state, "cao_minh", 30);
    core.grantCore(state, 1);

    core.upgradeStat(state, "cao_minh", "VIT");

    assertEquals(60, core.profile(state, "cao_minh").getInt("maxHp"));
    assertEquals(35, core.profile(state, "cao_minh").getInt("currentHp"));
    assertEquals(60, state.getJSONObject("player").getInt("maxHp"));
    assertEquals(35, state.getJSONObject("player").getInt("hp"));
  }

  @Test public void maxHpChangesClampWithoutFreeHealingAndHealDoesNotOverflow() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    core.setCurrentHp(state, "cao_minh", 50);
    core.applyStatusEffect(state, "cao_minh", "frail", "core:injury", "explorer_turn",
        1, "VIT", -2);
    assertEquals(45, core.profile(state, "cao_minh").getInt("maxHp"));
    assertEquals(45, core.profile(state, "cao_minh").getInt("currentHp"));
    core.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    assertEquals(55, core.profile(state, "cao_minh").getInt("maxHp"));
    assertEquals(45, core.profile(state, "cao_minh").getInt("currentHp"));
    assertEquals(10, core.healCurrentHp(state, "cao_minh", Integer.MAX_VALUE));
    assertEquals(55, core.profile(state, "cao_minh").getInt("currentHp"));
  }

  @Test public void aiCandidateCannotChangePlayerNumericState() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject before = baseState().put("player", new JSONObject()
        .put("name", "Cao Minh").put("attack", 24));
    core.normalizeState(before);
    core.setCurrentHp(before, "cao_minh", 27);
    JSONObject candidate = new JSONObject(before.toString());
    candidate.getJSONObject("player").put("hp", 999).put("maxHp", 999)
        .put("attack", 999).put("ATK", 999);
    candidate.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject("characters").getJSONObject("cao_minh")
        .getJSONObject("stats").put("STR", 999);
    core.protectFromCandidate(before, candidate);
    assertEquals(27, candidate.getJSONObject("player").getInt("hp"));
    assertEquals(55, candidate.getJSONObject("player").getInt("maxHp"));
    assertEquals(24, candidate.getJSONObject("player").getInt("attack"));
    assertFalse(candidate.getJSONObject("player").has("ATK"));
    assertEquals(5, core.profile(candidate, "cao_minh").getJSONObject("stats").getInt("STR"));
  }

  @Test public void statMultiplierUsesFiveAsOneHundredPercent() {
    assertEquals(100, CharacterProgressionCore.statPercent(5));
    assertEquals(110, CharacterProgressionCore.statPercent(6));
    assertEquals(150, CharacterProgressionCore.statPercent(10));
  }

  @Test public void upgradeCostFollowsTwoLevelsPerCoreStep() {
    assertEquals(1, CharacterProgressionCore.upgradeCost(5));
    assertEquals(1, CharacterProgressionCore.upgradeCost(6));
    assertEquals(2, CharacterProgressionCore.upgradeCost(7));
    assertEquals(2, CharacterProgressionCore.upgradeCost(8));
    assertEquals(3, CharacterProgressionCore.upgradeCost(9));
    assertEquals(3, CharacterProgressionCore.upgradeCost(10));
  }

  @Test public void coreIsPartySharedButStatCostsArePerCharacter() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    core.grantCore(state, 3);

    core.upgradeStat(state, "cao_minh", "STR");
    core.upgradeStat(state, "cao_minh", "STR");
    assertEquals(7, core.profile(state, "cao_minh").getJSONObject("stats").getInt("STR"));
    assertEquals(5, core.profile(state, "luc_tram").getJSONObject("stats").getInt("STR"));
    assertEquals(1, core.coreCount(state));
    assertEquals(1, CharacterProgressionCore.upgradeCost(
        core.profile(state, "luc_tram").getJSONObject("stats").getInt("STR")));
  }

  @Test public void insufficientCoreRejectsUpgradeWithoutMutation() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    try {
      core.upgradeStat(state, "cao_minh", "DEF");
      fail("Expected insufficient Core");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("Không đủ Core"));
    }
    assertEquals(5, core.profile(state, "cao_minh").getJSONObject("stats").getInt("DEF"));
    assertEquals(0, core.coreCount(state));
  }

  @Test public void treasureEntityJackpotPersistsPerStageAcrossReloads() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);

    assertEquals(100, core.rewardTreasureEntityVictory(
        state, "tam_ma_cao_minh", 0, 100, 10));
    assertEquals(100, core.coreCount(state));

    JSONObject reloaded = new JSONObject(state.toString());
    CharacterProgressionCore reloadedCore = new CharacterProgressionCore();
    assertEquals(10, reloadedCore.rewardTreasureEntityVictory(
        reloaded, "tam_ma_cao_minh", 0, 100, 10));
    assertEquals(110, reloadedCore.coreCount(reloaded));

    assertEquals(110, reloadedCore.rewardTreasureEntityVictory(
        reloaded, "tam_ma_cao_minh", 1, 110, 11));
    assertEquals(220, reloadedCore.coreCount(reloaded));
    assertEquals(11, reloadedCore.rewardTreasureEntityVictory(
        reloaded, "tam_ma_cao_minh", 1, 110, 11));
    assertEquals(231, reloadedCore.coreCount(reloaded));
  }

  @Test public void stageCompletionRewardsOnlyNewHighestStageOnce() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject state = baseState();
    core.normalizeState(state);
    assertEquals(0, core.coreCount(state));

    assertEquals(1, core.rewardStageCompletion(state, 1));
    assertEquals(0, core.rewardStageCompletion(state, 1));
    assertEquals(0, core.rewardStageCompletion(state, 0));
    assertEquals(2, core.rewardStageCompletion(state, 10));
    assertEquals(3, core.coreCount(state));
    assertEquals(10, core.highestRewardedStageIndex(state));
  }

  @Test public void bundleSizeUsesStageIndexBuckets() {
    assertEquals(1, CharacterProgressionCore.bundleSize(0));
    assertEquals(1, CharacterProgressionCore.bundleSize(9));
    assertEquals(2, CharacterProgressionCore.bundleSize(10));
    assertEquals(6, CharacterProgressionCore.bundleSize(50));
    assertEquals(21, CharacterProgressionCore.bundleSize(200));
  }

  @Test public void candidateCannotForgeStatsOrCore() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore();
    JSONObject before = baseState();
    core.normalizeState(before);
    core.grantCore(before, 4);
    JSONObject candidate = new JSONObject(before.toString());
    candidate.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject("characters").getJSONObject("cao_minh")
        .getJSONObject("stats").put("STR", 999);
    candidate.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject(CharacterProgressionCore.RESOURCE_KEY).put("quantity", 999);

    core.protectFromCandidate(before, candidate);

    assertEquals(5, candidate.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject("characters").getJSONObject("cao_minh")
        .getJSONObject("stats").getInt("STR"));
    assertEquals(4, candidate.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject(CharacterProgressionCore.RESOURCE_KEY).getInt("quantity"));
  }

  private static JSONObject baseState() throws Exception {
    return new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray());
  }
}
