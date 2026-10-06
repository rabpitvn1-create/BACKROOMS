package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CombatChoiceEngineTest {
  @Test public void participantUsesSameEffectiveStatsAsPartyProjection() throws Exception {
    JSONObject state = combatState(new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 12L * 60L));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:sample",
        "actor_turn", 1, "STR", 2);
    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject actor = state.getJSONObject("combat").getJSONArray("participants").getJSONObject(0);
    assertEquals(projected.getJSONObject("stats").getJSONObject("STR").getInt("effective"),
        actor.getInt("STR"));
    assertEquals(projected.getInt("maxHp"), actor.getInt("maxHp"));
    assertEquals(projected.getJSONObject("combatStatus").getInt("criticalChancePercent"),
        actor.getInt("criticalChancePercent"));
    CombatChoiceEngine.finishHand(state);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals(5, progression.profile(state, "cao_minh")
        .getJSONObject("stats").getInt("STR"));
    assertEquals(0, progression.profile(state, "cao_minh")
        .getJSONArray("statusEffects").length());
  }
  @Test public void daiDaoMaTonHealsAndStacksAttackAndCriticalAfterCaoMinhTurn() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 9999).put("maxHp", 9999).put("stunTurns", 1);
    actor.put("hp", 25);

    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals("Đại Đạo Ma Tôn", actor.getString("passiveSkill"));
    assertEquals(31, actor.getInt("hp"));
    assertEquals(1, actor.getInt("daiDaoMaTonStacks"));
    assertEquals(20, actor.getInt("daiDaoMaTonAttackBonusPercent"));
    assertEquals(20, actor.getInt("daiDaoMaTonCriticalBonusPercent"));
    assertEquals(36, actor.getInt("baseAttack"));
    assertEquals(27, actor.getInt("criticalChancePercent"));
    assertEquals(31, new CharacterProgressionCore().profile(state, "cao_minh").getInt("currentHp"));

    entity.put("stunTurns", 1);
    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(37, actor.getInt("hp"));
    assertEquals(2, actor.getInt("daiDaoMaTonStacks"));
    assertEquals(40, actor.getInt("daiDaoMaTonAttackBonusPercent"));
    assertEquals(40, actor.getInt("daiDaoMaTonCriticalBonusPercent"));
    assertEquals(42, actor.getInt("baseAttack"));
    assertEquals(47, actor.getInt("criticalChancePercent"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.toString().contains("Đại Đạo Ma Tôn"));
  }

  @Test public void daiDaoMaTonGrantsFiftyCriticalToAllCombatAlliesOnly() throws Exception {
    JSONObject state = combatState(new JSONArray()
        .put(member("luc_tram", "Lục Trầm"))
        .put(member("syvial", "Syvial")));
    CombatChoiceEngine.start(state, "hound", 0);

    JSONArray participants = state.getJSONObject("combat").getJSONArray("participants");
    JSONObject cao = participants.getJSONObject(0);
    JSONObject lucTram = participants.getJSONObject(1);
    JSONObject syvial = participants.getJSONObject(2);

    assertEquals(7, cao.getInt("criticalChancePercent"));
    assertEquals(55, lucTram.getInt("criticalChancePercent"));
    assertEquals(55, syvial.getInt("criticalChancePercent"));
    assertEquals(50, lucTram.getInt("daiDaoMaTonAllyCriticalBonusPercent"));
    assertEquals(50, syvial.getInt("daiDaoMaTonAllyCriticalBonusPercent"));
  }

  @Test public void defeatAtSublevelPreservesRouteAndQueuesLocalRestart() throws Exception {
    JSONObject state = combatState(new JSONArray())
        .put("currentLevel", 0).put("currentLevelKey", "0.1")
        .put("location", "Level 0.1 / hành lang sâu");
    JSONObject route = new JSONObject().put("levelKey", "0.1").put("streak", 4);
    state.put("levelRoute", route);
    JSONObject combat = new JSONObject().put("active", false).put("outcome", "defeat");
    state.put("combat", combat);

    CombatChoiceEngine.normalizeTerminalEncounter(state);
    assertEquals("0.1", state.getString("currentLevelKey"));
    assertEquals(LevelCore.defaultLocation("0.1"), state.getString("location"));
    assertEquals(4, state.getJSONObject("levelRoute").getInt("streak"));
    assertEquals("Backrooms nuốt chửng lấy bạn khi bạn ngã xuống.",
        state.getJSONArray("log").getJSONObject(state.getJSONArray("log").length() - 1).getString("text"));
    JSONObject resolvedCombat = state.getJSONObject("combat");
    assertEquals("Level 0.1 / hành lang sâu", resolvedCombat.getString("deathRestartAnchorLocation"));
    assertEquals("0.1", resolvedCombat.getString("deathRestartLevelKey"));
    assertTrue(resolvedCombat.getBoolean("deathRestartPending"));
    assertTrue(resolvedCombat.getBoolean("deathRecoveryApplied"));

    int logSize = state.getJSONArray("log").length();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.setCurrentHp(state, "cao_minh", 7);
    CombatChoiceEngine.normalizeTerminalEncounter(new JSONObject(state.toString()));
    CombatChoiceEngine.normalizeTerminalEncounter(state);
    assertEquals(logSize, state.getJSONArray("log").length());
    assertEquals(7, progression.profile(state, "cao_minh").getInt("currentHp"));
  }

  @Test public void terminalNormalizationPreservesNewerEncounterFlags() throws Exception {
    JSONObject state = combatState(new JSONArray()).put("turn", 7);
    state.put("flags", new JSONObject()
        .put("entityEncounterKey", "hound")
        .put("entityEncounterKeys", new JSONArray().put("hound"))
        .put("entityEncounterStartedTurn", 7));
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject terminal = state.getJSONObject("combat");
    assertEquals(7, terminal.getInt("encounterStartedTurn"));
    terminal.put("active", false).put("outcome", "victory");

    state.put("turn", 8);
    state.put("flags", new JSONObject()
        .put("entityEncounterKey", "the_lifeform_bacteria_01")
        .put("entityEncounterKeys", new JSONArray()
            .put("the_lifeform_bacteria_01")
            .put("async_member_rifle_aim_right_01"))
        .put("entityEncounterStartedTurn", 8));

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    JSONArray keys = state.getJSONObject("flags").getJSONArray("entityEncounterKeys");
    assertEquals(2, keys.length());
    assertEquals("the_lifeform_bacteria_01", keys.getString(0));
    assertEquals("async_member_rifle_aim_right_01", keys.getString(1));
  }

  @Test public void terminalNormalizationStillClearsFlagsOwnedByThatCombat() throws Exception {
    JSONObject state = combatState(new JSONArray()).put("turn", 7);
    state.put("flags", new JSONObject()
        .put("entityEncounterKey", "hound")
        .put("entityEncounterKeys", new JSONArray().put("hound"))
        .put("entityEncounterStartedTurn", 7));
    CombatChoiceEngine.start(state, "hound", 0);
    state.getJSONObject("combat").put("active", false).put("outcome", "victory");

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    assertEquals("", state.getJSONObject("flags").getString("entityEncounterKey"));
    assertEquals(0, state.getJSONObject("flags").getJSONArray("entityEncounterKeys").length());
  }

  @Test public void startProducesInitialFiveD6ValuesAndProjectsHand() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    JSONArray values = dice.getJSONArray("values");
    assertTrue(dice.getBoolean("hasRolled"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals(5, values.length());
    int[] rolled = new int[5];
    for (int i = 0; i < 5; i++) {
      rolled[i] = values.getInt(i);
      assertTrue(rolled[i] >= 1 && rolled[i] <= 6);
    }
    assertEquals(CombatChoiceEngine.classify(rolled), dice.getString("hand"));
  }

  @Test public void autoHoldMarksEveryDuplicateGroupAndPlayerCanReleaseAnyDie() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    dice.put("values", new JSONArray().put(2).put(2).put(3).put(3).put(1));
    JSONArray held = dice.getJSONArray("held");
    for (int i = 0; i < 5; i++) held.put(i, false);

    CombatChoiceEngine.autoHoldDuplicateGroups(dice);

    assertTrue(held.getBoolean(0));
    assertTrue(held.getBoolean(1));
    assertTrue(held.getBoolean(2));
    assertTrue(held.getBoolean(3));
    assertFalse(held.getBoolean(4));

    CombatChoiceEngine.setHold(state, 0, false);
    assertFalse(held.getBoolean(0));
  }

  @Test public void autoHoldAddsDuplicateGroupsWithoutClearingManualHolds() throws Exception {
    JSONObject dice = new JSONObject()
        .put("values", new JSONArray().put(4).put(4).put(2).put(3).put(6))
        .put("held", new JSONArray().put(false).put(false).put(false).put(false).put(true));

    CombatChoiceEngine.autoHoldDuplicateGroups(dice);

    JSONArray held = dice.getJSONArray("held");
    assertTrue(held.getBoolean(0));
    assertTrue(held.getBoolean(1));
    assertTrue(held.getBoolean(4));
    assertFalse(held.getBoolean(2));
    assertFalse(held.getBoolean(3));
  }

  @Test public void holdPersistsAndRerollTouchesOnlyUnheldDice() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = combat.getJSONObject("diceState");
    JSONArray before = new JSONArray(dice.getJSONArray("values").toString());
    for (int i = 0; i < 5; i++) CombatChoiceEngine.setHold(state, i, false);
    CombatChoiceEngine.setHold(state, 0, true);
    CombatChoiceEngine.setHold(state, 3, true);
    int sequenceBefore = combat.getInt("rngSequence");
    int seed = combat.getInt("seed");
    JSONArray heldBefore = new JSONArray(dice.getJSONArray("held").toString());

    CombatChoiceEngine.roll(state);

    JSONArray after = dice.getJSONArray("values");
    assertEquals(before.getInt(0), after.getInt(0));
    assertEquals(before.getInt(3), after.getInt(3));
    int sequence = sequenceBefore;
    for (int slot : new int[]{1,2,4}) {
      int[] weights = CombatChoiceEngine.rerollWeights(before, heldBefore, slot);
      int totalWeight = 0;
      for (int weight : weights) totalWeight += weight;
      int roll = CombatChoiceEngine.deterministicRoll(seed, sequence, slot, totalWeight);
      assertEquals(CombatChoiceEngine.weightedFace(roll, weights), after.getInt(slot));
      sequence++;
    }
    assertEquals(sequenceBefore + 3, combat.getInt("rngSequence"));
    assertEquals(1, dice.getInt("rerollsUsed"));
    assertEquals(CombatChoiceEngine.classify(
        after.getInt(0), after.getInt(1), after.getInt(2), after.getInt(3), after.getInt(4)),
        dice.getString("hand"));
  }

  @Test public void heldFacesIncreaseMatchingRerollWeightWithoutForcingTheOutcome() {
    JSONArray values = new JSONArray().put(4).put(4).put(2).put(3).put(6);
    JSONArray held = new JSONArray().put(true).put(true).put(false).put(false).put(false);

    int[] weights = CombatChoiceEngine.rerollWeights(values, held, 2);

    assertEquals(24, weights[3]);
    for (int face : new int[]{0,1,2,4,5}) assertEquals(12, weights[face]);
    assertTrue(weights[3] < java.util.Arrays.stream(weights).sum());
  }

  @Test public void eachHeldCopyStacksAdditionalSameFaceWeight() {
    JSONArray values = new JSONArray().put(5).put(5).put(5).put(2).put(3);
    JSONArray held = new JSONArray().put(true).put(true).put(true).put(false).put(false);

    int[] weights = CombatChoiceEngine.rerollWeights(values, held, 3);

    assertEquals(30, weights[4]);
    assertEquals(12, weights[0]);
    assertEquals(12, weights[5]);
  }

  @Test public void heldOrderedStraightPrefixBiasesTheMissingValueAtItsExactSlot() {
    JSONArray values = new JSONArray().put(1).put(2).put(6).put(6).put(6);
    JSONArray held = new JSONArray().put(true).put(true).put(false).put(false).put(false);

    int[] thirdSlot = CombatChoiceEngine.rerollWeights(values, held, 2);
    int[] fourthSlot = CombatChoiceEngine.rerollWeights(values, held, 3);
    int[] fifthSlot = CombatChoiceEngine.rerollWeights(values, held, 4);

    assertEquals(30, thirdSlot[2]);
    assertEquals(30, fourthSlot[3]);
    assertEquals(30, fifthSlot[4]);
    assertEquals(18, thirdSlot[0]);
    assertEquals(18, thirdSlot[1]);
  }

  @Test public void heldReverseStraightPrefixBiasesTheReverseContinuation() {
    JSONArray values = new JSONArray().put(6).put(5).put(1).put(1).put(1);
    JSONArray held = new JSONArray().put(true).put(true).put(false).put(false).put(false);

    int[] thirdSlot = CombatChoiceEngine.rerollWeights(values, held, 2);

    assertEquals(30, thirdSlot[3]);
    assertEquals(18, thirdSlot[4]);
    assertEquals(18, thirdSlot[5]);
  }

  @Test public void unrelatedHeldValuesDoNotCreateStraightBias() {
    JSONArray values = new JSONArray().put(1).put(4).put(2).put(5).put(6);
    JSONArray held = new JSONArray().put(true).put(true).put(false).put(false).put(false);

    int[] weights = CombatChoiceEngine.rerollWeights(values, held, 2);

    assertEquals(18, weights[0]);
    assertEquals(18, weights[3]);
    assertEquals(12, weights[1]);
    assertEquals(12, weights[2]);
    assertEquals(12, weights[4]);
    assertEquals(12, weights[5]);
  }

  @Test public void normalizeActiveLegacyTurnBackfillsInitialRoll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = combat.getJSONObject("diceState");
    dice.put("values", new JSONArray().put(0).put(0).put(0).put(0).put(0))
        .put("hasRolled", false)
        .put("rerollsUsed", 0)
        .put("hand", "");
    combat.put("rngSequence", 0);

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    JSONArray values = dice.getJSONArray("values");
    assertTrue(dice.getBoolean("hasRolled"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals(CombatChoiceEngine.classify(
        values.getInt(0), values.getInt(1), values.getInt(2), values.getInt(3), values.getInt(4)),
        dice.getString("hand"));
  }

  @Test public void exactlyThreeRerollsThenRollStopsUntilFinish() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    assertFalse(dice.getBoolean("finalized"));

    for (int reroll = 0; reroll < 3; reroll++) {
      for (int i = 0; i < 5; i++) CombatChoiceEngine.setHold(state, i, false);
      CombatChoiceEngine.roll(state);
    }

    assertEquals(3, dice.getInt("rerollsUsed"));
    assertFalse(dice.getBoolean("finalized"));
    String frozen = dice.toString();
    CombatChoiceEngine.roll(state);
    assertEquals(frozen, dice.toString());

    CombatChoiceEngine.finishHand(state);
    assertTrue(dice.getBoolean("finalized"));
  }

  @Test public void holdingAllFiveStopsRerollUntilFinishWithoutSpendingReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    for (int i = 0; i < 5; i++) CombatChoiceEngine.setHold(state, i, true);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    CombatChoiceEngine.roll(state);
    assertFalse(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));

    CombatChoiceEngine.finishHand(state);
    assertTrue(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));
  }

  @Test public void finishCanFinalizeCurrentHandBeforeAnyReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    dice.put("values", new JSONArray().put(2).put(2).put(1).put(4).put(6));

    CombatChoiceEngine.finishHand(state);

    assertTrue(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals("ONE PAIR", dice.getString("hand"));
  }

  @Test public void serializedReloadCannotProduceFreeDifferentReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    CombatChoiceEngine.setHold(state, 1, true);
    JSONObject reloaded = new JSONObject(state.toString());

    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(reloaded);

    assertEquals(
        state.getJSONObject("combat").getJSONObject("diceState").toString(),
        reloaded.getJSONObject("combat").getJSONObject("diceState").toString());
    assertEquals(state.getJSONObject("combat").getInt("rngSequence"),
        reloaded.getJSONObject("combat").getInt("rngSequence"));
  }

  @Test public void orderedStraightClassificationIsExact() {
    assertEquals("SSF", CombatChoiceEngine.classify(1,2,3,4,5));
    assertEquals("SSF", CombatChoiceEngine.classify(5,4,3,2,1));
    assertEquals("STRAIGHT", CombatChoiceEngine.classify(2,3,4,5,6));
    assertEquals("STRAIGHT", CombatChoiceEngine.classify(6,5,4,3,2));
    assertNotEquals("STRAIGHT", CombatChoiceEngine.classify(5,1,4,2,3));
    assertNotEquals("SSF", CombatChoiceEngine.classify(5,1,4,2,3));
  }

  @Test public void pairTwoPairTripleFullHouseFourAndFiveKindClassifyCorrectly() {
    assertEquals("ONE PAIR", CombatChoiceEngine.classify(2,2,1,4,6));
    assertEquals("TWO PAIR", CombatChoiceEngine.classify(2,2,4,4,6));
    assertEquals("THREE OF A KIND", CombatChoiceEngine.classify(3,3,3,1,6));
    assertEquals("FULL HOUSE", CombatChoiceEngine.classify(3,3,3,6,6));
    assertEquals("FOUR OF A KIND", CombatChoiceEngine.classify(4,4,4,4,2));
    assertEquals("FSF", CombatChoiceEngine.classify(6,6,6,6,6));
  }

  @Test public void fsfPriorityOverridesFourOfAKind() {
    assertEquals("FSF", CombatChoiceEngine.classify(1,1,1,1,1));
  }

  @Test public void handAndStatsApplyExactlyOnceToDamage() {
    assertEquals(30, CombatChoiceEngine.basicDamage(30, 5, 100));
    assertEquals(38, CombatChoiceEngine.basicDamage(30, 5, 125));
    assertEquals(33, CombatChoiceEngine.basicDamage(30, 6, 100));
    assertEquals(77, CombatChoiceEngine.skillDamage(30, 170, 5, 150));
    assertEquals(84, CombatChoiceEngine.skillDamage(30, 170, 6, 150));
    assertEquals(35, CombatChoiceEngine.ultimateDamage(30, 1, 15, 100));
    assertEquals(840, CombatChoiceEngine.ultimateDamage(30, 24, 15, 100));
    assertEquals(1680, CombatChoiceEngine.ultimateDamage(30, 24, 15, 200));
    assertEquals(2100, CombatChoiceEngine.ultimateDamage(30, 60, 15, 100));
  }

  @Test public void defenseUsesDiminishingDivisionAndNeverImmunity() {
    assertEquals(20, CombatChoiceEngine.defendedIncomingDamage(20, 5));
    assertEquals(18, CombatChoiceEngine.defendedIncomingDamage(20, 6));
    assertEquals(10, CombatChoiceEngine.defendedIncomingDamage(20, 15));
    assertEquals(1, CombatChoiceEngine.defendedIncomingDamage(1, 999));
  }

  @Test public void downedCharacterIsSkippedAndThreeSlotOrderWrapsRound() throws Exception {
    JSONArray party = new JSONArray()
        .put(member("luc_tram", "Lục Trầm"))
        .put(member("syvial", "Syvial"));
    JSONObject state = combatState(party);
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.setCurrentHp(state, "luc_tram", 0);

    CombatChoiceEngine.start(state, "diep_minh", 0);
    assertEquals(3, state.getJSONObject("combat").getJSONArray("participants").length());

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Syvial", state.getJSONObject("combat").getString("currentActor"));

    int round = state.getJSONObject("combat").getInt("round");
    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Cao Minh", state.getJSONObject("combat").getString("currentActor"));
    assertEquals(round + 1, state.getJSONObject("combat").getInt("round"));
  }

  @Test public void rerollsDoNotSpamGmLogAndResolveAddsOrderedActorAndEntityLines() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    state.getJSONObject("combat").getJSONObject("entity")
        .put("hp", 9999).put("maxHp", 9999);
    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);
    JSONArray gmLog = state.getJSONArray("log");
    assertFalse(gmLog.getJSONObject(0).has("battleLog"));

    CombatChoiceEngine.finishHand(state);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = gmLog.getJSONObject(0).getJSONArray("battleLog");
    assertEquals(3, battleLog.length());
    String actorLine = battleLog.getJSONObject(0).getString("text");
    String entityLine = battleLog.getJSONObject(1).getString("text");
    String passiveLine = battleLog.getJSONObject(2).getString("text");
    assertTrue(actorLine.startsWith("["));
    assertTrue(actorLine.contains("Cao Minh"));
    assertTrue(actorLine.contains("Hound -"));
    assertTrue(actorLine.matches(".*\\[\\d+/\\d+ HP\\].*"));
    assertTrue(entityLine.startsWith("Hound "));
    assertTrue(passiveLine.startsWith("Đại Đạo Ma Tôn:"));
    assertTrue(entityLine.contains("Cao Minh"));
    assertTrue(entityLine.contains("né được")
        || entityLine.contains("đánh trượt")
        || entityLine.contains("không thể tấn công")
        || entityLine.matches(".*\\[\\d+/\\d+ HP\\].*"));
    assertFalse(actorLine.toLowerCase().contains("reroll"));
    assertFalse(actorLine.toLowerCase().contains("dice"));
  }

  @Test public void onlyAsyncRiflemanRemainsRegistered() throws Exception {
    assertTrue(CombatChoiceEngine.isKnownEntity("async_rifleman"));
    assertFalse(CombatChoiceEngine.isKnownEntity("async_vanguard"));
    assertFalse(CombatChoiceEngine.isKnownEntity("async_tactical"));
    assertFalse(CombatChoiceEngine.isKnownEntity("async_decon"));

    JSONObject state = combatState(new JSONArray());
    state.getJSONObject("flags").put("entityEncounterKey", "async_rifleman");
    CombatChoiceEngine.start(state, "async_rifleman", 0);

    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    assertEquals("async_rifleman", entity.getString("key"));
    assertEquals("ASYNC Rifleman", entity.getString("name"));
    assertEquals(180, entity.getInt("baseHp"));
    assertEquals(20, entity.getInt("baseDamage"));
  }

  @Test public void caoMinhAndLucTramHaveAuthoritativeUltimateMappings() {
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("cao_minh"));
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("luc_tram"));
    assertFalse(CombatChoiceEngine.hasAuthoritativeUltimate("syvial"));
  }

  @Test public void lucTramSsfUsesDynamicThienKiemDinhGioiUltimate() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("luc_tram", "Lục Trầm")));
    CombatChoiceEngine.start(state, "diep_minh", 0);

    finalizeAs(state, 2,2,4,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Lục Trầm", state.getJSONObject("combat").getString("currentActor"));

    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    int before = entity.getInt("hp");
    int expected = CombatChoiceEngine.ultimateDamage(24, 60, 15, 100);

    finalizeAs(state, 1,2,3,4,5);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(Math.max(0, before - expected), entity.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    boolean foundUltimate = false;
    for (int i = 0; i < battleLog.length(); i++) {
      if (battleLog.getJSONObject(i).getString("text").contains("Thiên Kiếm Định Giới")) {
        foundUltimate = true;
        break;
      }
    }
    assertTrue(foundUltimate);
  }

  @Test public void everyCombatEntityHasExpectedValidAutoProcSkills() throws Exception {
    Field entitiesField = CombatChoiceEngine.class.getDeclaredField("ENTITIES");
    entitiesField.setAccessible(true);
    Field skillsField = CombatChoiceEngine.class.getDeclaredField("ENTITY_SKILLS");
    skillsField.setAccessible(true);
    Map<?, ?> entities = (Map<?, ?>) entitiesField.get(null);
    Map<?, ?> pools = (Map<?, ?>) skillsField.get(null);

    assertEquals(entities.keySet(), pools.keySet());
    for (Object key : entities.keySet()) {
      List<?> skills = (List<?>) pools.get(key);
      boolean bacterial = String.valueOf(key).startsWith("the_lifeform_bacteria_");
      assertEquals("Skill count for " + key, bacterial ? 4 : 3, skills.size());
      for (Object skill : skills) {
        Field damage = skill.getClass().getDeclaredField("damagePercent");
        Field proc = skill.getClass().getDeclaredField("procPercent");
        damage.setAccessible(true);
        proc.setAccessible(true);
        assertTrue("Damage for " + key, damage.getInt(skill) > 0);
        assertTrue("Proc for " + key, proc.getInt(skill) >= 20);
        int maxProc = "tam_ma_cao_minh".equals(key) ? 45 : 35;
        assertTrue("Proc for " + key, proc.getInt(skill) <= maxProc);
      }
    }
  }

  @Test public void bacterialVariantsAreFifteenPercentAboveHoundAndShareThreeProcs()
      throws Exception {
    String[] keys = {
        "the_lifeform_bacteria_01",
        "the_lifeform_bacteria_02",
        "the_lifeform_bacteria_03"
    };

    Field skillsField = CombatChoiceEngine.class.getDeclaredField("ENTITY_SKILLS");
    skillsField.setAccessible(true);
    Map<?, ?> pools = (Map<?, ?>) skillsField.get(null);

    String shared = null;
    int[][] expectedUniqueMechanics = {
        {2, 0, 0},
        {1, 60, 0},
        {1, 0, 50}
    };

    for (int k = 0; k < keys.length; k++) {
      String key = keys[k];
      assertTrue(CombatChoiceEngine.isKnownEntity(key));
      JSONObject state = combatState(new JSONArray());
      state.getJSONObject("flags").put("entityEncounterKey", key);
      CombatChoiceEngine.start(state, key, 0);
      JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
      assertEquals(173, entity.getInt("baseHp"));
      assertEquals(17, entity.getInt("baseDamage"));
      assertEquals(4, CombatChoiceEngine.entitySkillCount(key));

      List<?> pool = (List<?>) pools.get(key);
      StringBuilder signature = new StringBuilder();
      for (int i = 0; i < 3; i++) {
        Object skill = pool.get(i);
        Field name = skill.getClass().getDeclaredField("name");
        Field damage = skill.getClass().getDeclaredField("damagePercent");
        Field proc = skill.getClass().getDeclaredField("procPercent");
        name.setAccessible(true);
        damage.setAccessible(true);
        proc.setAccessible(true);
        signature.append(name.get(skill)).append(':')
            .append(damage.getInt(skill)).append(':')
            .append(proc.getInt(skill)).append('|');
      }
      if (shared == null) shared = signature.toString();
      else assertEquals(shared, signature.toString());

      Object unique = pool.get(3);
      Field hits = unique.getClass().getDeclaredField("hitCount");
      Field pierce = unique.getClass().getDeclaredField("defensePiercePercent");
      Field heal = unique.getClass().getDeclaredField("healPercentOfDamage");
      hits.setAccessible(true);
      pierce.setAccessible(true);
      heal.setAccessible(true);
      assertEquals(expectedUniqueMechanics[k][0], hits.getInt(unique));
      assertEquals(expectedUniqueMechanics[k][1], pierce.getInt(unique));
      assertEquals(expectedUniqueMechanics[k][2], heal.getInt(unique));
    }

    assertEquals(20, CombatChoiceEngine.piercedIncomingDamage(20, 10, 100));
    assertEquals(5, CombatChoiceEngine.drainHealAmount(10, 50));
  }

  @Test public void researchAsyncMemberHasDedicatedProfileAndThreeProcSkills() throws Exception {
    String key = "async_member_rifle_aim_right_01";
    assertTrue(CombatChoiceEngine.isKnownEntity(key));

    JSONObject state = combatState(new JSONArray());
    state.getJSONObject("flags").put("entityEncounterKey", key);
    CombatChoiceEngine.start(state, key, 0);
    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");

    assertEquals("Research Async Member", entity.getString("name"));
    assertEquals(190, entity.getInt("baseHp"));
    assertEquals(20, entity.getInt("baseDamage"));
    assertEquals(3, CombatChoiceEngine.entitySkillCount(key));

    Field skillsField = CombatChoiceEngine.class.getDeclaredField("ENTITY_SKILLS");
    skillsField.setAccessible(true);
    Map<?, ?> pools = (Map<?, ?>) skillsField.get(null);
    List<?> skills = (List<?>) pools.get(key);
    String[] names = {"Loạt Bắn Kiểm Soát", "Hai Phát Liên Tiếp", "Áp Chế Mẫu Vật"};
    int[] damage = {110, 115, 120};
    int[] proc = {33, 28, 23};
    for (int i = 0; i < skills.size(); i++) {
      Field name = skills.get(i).getClass().getDeclaredField("name");
      Field damagePercent = skills.get(i).getClass().getDeclaredField("damagePercent");
      Field procPercent = skills.get(i).getClass().getDeclaredField("procPercent");
      name.setAccessible(true);
      damagePercent.setAccessible(true);
      procPercent.setAccessible(true);
      assertEquals(names[i], name.get(skills.get(i)));
      assertEquals(damage[i], damagePercent.getInt(skills.get(i)));
      assertEquals(proc[i], procPercent.getInt(skills.get(i)));
    }
  }

  @Test public void evilClownKeepsDoubleHoundStatsAndTreasureProcRates() throws Exception {
    assertTrue(CombatChoiceEngine.isKnownEntity("tam_ma_cao_minh"));

    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "tam_ma_cao_minh", 0);
    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    assertEquals("Evil Clown", entity.getString("name"));
    assertEquals(300, entity.getInt("baseHp"));
    assertEquals(30, entity.getInt("baseDamage"));
    assertEquals(3, CombatChoiceEngine.entitySkillCount("tam_ma_cao_minh"));

    Field skillsField = CombatChoiceEngine.class.getDeclaredField("ENTITY_SKILLS");
    skillsField.setAccessible(true);
    Map<?, ?> pools = (Map<?, ?>) skillsField.get(null);
    List<?> skills = (List<?>) pools.get("tam_ma_cao_minh");
    int[] expected = {35, 40, 45};
    String[] expectedNames = {"Lưỡi Liềm Hề Ác", "Màn Diễn Phản Kích", "Cú Vồ Điên Loạn"};
    int[] expectedDamage = {120, 115, 110};
    for (int i = 0; i < expected.length; i++) {
      Field proc = skills.get(i).getClass().getDeclaredField("procPercent");
      proc.setAccessible(true);
      assertEquals(expected[i], proc.getInt(skills.get(i)));
      Field name = skills.get(i).getClass().getDeclaredField("name");
      name.setAccessible(true);
      assertEquals(expectedNames[i], name.get(skills.get(i)));
      Field damage = skills.get(i).getClass().getDeclaredField("damagePercent");
      damage.setAccessible(true);
      assertEquals(expectedDamage[i], damage.getInt(skills.get(i)));
    }

    JSONObject nextStage = combatState(new JSONArray());
    nextStage.put(LevelCore.LEVEL_KEY, "0.1");
    CombatChoiceEngine.start(nextStage, "tam_ma_cao_minh", 0);
    JSONObject scaled = nextStage.getJSONObject("combat").getJSONObject("entity");
    assertEquals(330, scaled.getInt("maxHp"));
    assertEquals(33, scaled.getInt("attack"));
    assertEquals(110, scaled.getInt("stagePercent"));
  }

  @Test public void entityFallsBackToBasicAttackWhenNoSkillProcs() throws Exception {
    int seed = 1;
    while (CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 0) < 35
        || CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 1) < 32
        || CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 2) < 34) seed++;

    JSONObject combat = new JSONObject().put("seed", seed).put("round", 1).put("actorIndex", 0);
    JSONObject actor = new JSONObject().put("name", "Cao Minh").put("hp", 100)
        .put("maxHp", 100).put("DEF", CharacterProgressionCore.BASE_STAT);
    JSONObject entity = new JSONObject().put("key", "hound").put("name", "Hound")
        .put("attack", 15);
    Method respond = CombatChoiceEngine.class.getDeclaredMethod("resolveEntityResponse",
        JSONObject.class, JSONObject.class, JSONObject.class, boolean.class);
    respond.setAccessible(true);

    String summary = (String) respond.invoke(null, combat, actor, entity, false);

    assertTrue(summary.startsWith("Hound tấn công, Cao Minh -"));
    assertEquals(100 - actor.getInt("hp"),
        CombatChoiceEngine.defendedIncomingDamage(15, CharacterProgressionCore.BASE_STAT));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
  }
  @Test public void entityResolvesOnlyOneSkillWhenMultipleProcRollsSucceed() throws Exception {
    int seed = 1;
    while (CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 0) >= 35
        || CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 1) >= 32) seed++;

    JSONObject combat = new JSONObject().put("seed", seed).put("round", 1).put("actorIndex", 0);
    JSONObject actor = new JSONObject().put("name", "Cao Minh").put("hp", 100)
        .put("maxHp", 100).put("DEF", CharacterProgressionCore.BASE_STAT);
    JSONObject entity = new JSONObject().put("key", "hound").put("name", "Hound")
        .put("attack", 15);
    Method respond = CombatChoiceEngine.class.getDeclaredMethod("resolveEntityResponse",
        JSONObject.class, JSONObject.class, JSONObject.class, boolean.class);
    respond.setAccessible(true);

    String summary = (String) respond.invoke(null, combat, actor, entity, false);

    int expectedDamage = CombatChoiceEngine.defendedIncomingDamage(
        CombatChoiceEngine.entitySkillDamage(15, 120), CharacterProgressionCore.BASE_STAT);
    assertTrue(summary.contains("dùng Dead Bite"));
    assertFalse(summary.contains(" + "));
    assertEquals(100 - expectedDamage, actor.getInt("hp"));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
  }

  @Test public void firstEntityRotationHasExactlyThreeSkillsEach() {
  assertEquals(3, CombatChoiceEngine.entitySkillCount("hound"));
  assertEquals(3, CombatChoiceEngine.entitySkillCount("clump"));
  assertEquals(3, CombatChoiceEngine.entitySkillCount("duller"));
}
@Test public void entitySkillDamageIsBounded() {
  assertEquals(18, CombatChoiceEngine.entitySkillDamage(15,120));
  assertEquals(17, CombatChoiceEngine.entitySkillDamage(15,115));
  assertEquals(21, CombatChoiceEngine.entitySkillDamage(17,125));
}
@Test public void entitySkillProcRollIsStableWithinTurn() {
  int r=CombatChoiceEngine.entitySkillProcRoll(12345,4,2,1); assertTrue(r>=0&&r<100);
  assertEquals(r,CombatChoiceEngine.entitySkillProcRoll(12345,4,2,1));
}

  @Test public void onePairUsesCompactPairTokenInBattleLog() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    String actorLine = battleLog.getJSONObject(0).getString("text");
    assertTrue(actorLine.startsWith("[PAIR] Cao Minh "));
    assertFalse(actorLine.contains("[ONE PAIR]"));
  }

  @Test public void fourOfAKindUsesCompactDetailedBattleLineAndNoProcTextFloater() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);

    finalizeAs(state, 4,4,4,4,2);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    String actorLine = battleLog.getJSONObject(0).getString("text");
    assertTrue(actorLine.startsWith("[F.O.A.K] "));
    assertTrue(actorLine.contains("Cao Minh "));
    assertFalse(actorLine.contains("FOUR OF A KIND"));
    assertTrue(actorLine.contains("Diệp Minh -"));
    assertTrue(actorLine.matches(".*\\[\\d+/\\d+ HP\\].*"));

    JSONArray feedback = state.getJSONObject("combat").getJSONArray("feedbackEvents");
    for (int i = 0; i < feedback.length(); i++) {
      String text = feedback.getJSONObject(i).optString("text", "");
      if (text.isEmpty()) continue;
      assertTrue("Unexpected combat floater: " + text, text.matches("[+-]\\d+ HP"));
      assertFalse(text.contains("PROC"));
    }
  }

  @Test public void caoMinhAndLucTramEachHaveFiveCharacterProcsAndKaiHasNone() {
    assertEquals(5, CombatChoiceEngine.characterProcCount("cao_minh"));
    assertEquals(5, CombatChoiceEngine.characterProcCount("luc_tram"));
    assertEquals(0, CombatChoiceEngine.characterProcCount("kai"));

    for (String id : new String[]{"cao_minh", "luc_tram"}) {
      for (int i = 0; i < 5; i++) {
        int chance = CombatChoiceEngine.characterProcPercent(id, i);
        assertTrue(chance >= 45 && chance <= 55);
      }
    }
  }

  @Test public void lethalBleedAtRoundStartEndsCombatBeforeActorAction() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("round", 2).put("actorIndex", 0);
    entity.put("hp", 1)
        .put("bleedTurns", 1).put("bleedPercent", 3)
        .put("poisonTurns", 0).put("poisonPercent", 0);
    finalizeAs(state, 1,2,3,4,6);

    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertTrue(combat.getJSONObject("diceState").getBoolean("resolved"));
    assertFalse(combat.getBoolean("resolvedEntityTurn"));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
    assertEquals("status", combat.getJSONArray("feedbackEvents").getJSONObject(0).getString("damageSource"));
    assertFalse(state.getJSONArray("log").getJSONObject(0).has("battleLog"));
  }

  @Test public void lethalPoisonAtRoundStartEndsCombatBeforeActorAction() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("round", 2).put("actorIndex", 0);
    entity.put("hp", 1)
        .put("bleedTurns", 0).put("bleedPercent", 0)
        .put("poisonTurns", 1).put("poisonPercent", 3);
    finalizeAs(state, 1,2,3,4,6);

    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertTrue(combat.getJSONObject("diceState").getBoolean("resolved"));
    assertFalse(combat.getBoolean("resolvedEntityTurn"));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
    assertEquals("status", combat.getJSONArray("feedbackEvents").getJSONObject(0).getString("damageSource"));
    assertFalse(state.getJSONArray("log").getJSONObject(0).has("battleLog"));
  }

  @Test public void stackingRulesIncreasePotencyButOnlyStunIncreasesDuration() throws Exception {
    JSONObject entity = new JSONObject()
        .put("bleedTurns", 2).put("bleedPercent", 3)
        .put("poisonTurns", 2).put("poisonPercent", 3)
        .put("armorBreakTurns", 2).put("armorBreakPercent", 10)
        .put("stunTurns", 1);

    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 3, 4);
    assertEquals(2, entity.getInt("bleedTurns"));
    assertEquals(7, entity.getInt("bleedPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Trúng độc", 3, 5);
    assertEquals(2, entity.getInt("poisonTurns"));
    assertEquals(8, entity.getInt("poisonPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Xuyên giáp", 3, 15);
    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(25, entity.getInt("armorBreakPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Choáng", 1, 0);
    assertEquals(2, entity.getInt("stunTurns"));
  }

  @Test public void expiredProcPotencyDoesNotCarryIntoAnewApplication() throws Exception {
    JSONObject entity = new JSONObject()
        .put("bleedTurns", 0).put("bleedPercent", 25)
        .put("poisonTurns", 0).put("poisonPercent", 25)
        .put("armorBreakTurns", 0).put("armorBreakPercent", 75)
        .put("stunTurns", 0);

    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 2, 4);
    assertEquals(2, entity.getInt("bleedTurns"));
    assertEquals(4, entity.getInt("bleedPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Trúng độc", 2, 5);
    assertEquals(2, entity.getInt("poisonTurns"));
    assertEquals(5, entity.getInt("poisonPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Xuyên giáp", 2, 10);
    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(10, entity.getInt("armorBreakPercent"));
  }

  @Test public void basicAttackCanTriggerCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertTrue(entity.getInt("bleedTurns") > 0);
    assertTrue(entity.getInt("bleedPercent") > 0);
    JSONArray feedback = combat.getJSONArray("feedbackEvents");
    boolean foundBleedFloater = false;
    for (int i = 0; i < feedback.length(); i++) {
      JSONObject event = feedback.getJSONObject(i);
      if ("entity".equals(event.optString("target", ""))
          && "damage".equals(event.optString("kind", ""))
          && "Chảy máu".equals(event.optString("status", ""))) {
        foundBleedFloater = true;
      }
    }
    assertTrue(foundBleedFloater);
  }

  @Test public void normalSkillCanTriggerCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 3,3,3,1,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertTrue(entity.getInt("bleedTurns") > 0);
    assertTrue(entity.getInt("bleedPercent") > 0);
  }

  @Test public void ultimateNeverTriggersCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 1,2,3,4,5);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("poisonTurns"));
    assertEquals(0, entity.getInt("armorBreakTurns"));
    assertEquals(0, entity.getInt("stunTurns"));
  }

  @Test public void lethalSkillDoesNotApplyStatusToDefeatedEntity() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 1);
    combat.put("currentSkill", new JSONObject()
        .put("name", "Lethal Bleed")
        .put("damagePercent", 100)
        .put("effect", "Chảy máu")
        .put("effectTurns", 3)
        .put("effectValue", 5));

    finalizeAs(state, 2,2,2,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("bleedPercent"));
  }

  @Test public void lethalCharacterProcDoesNotApplyStatusToDefeatedEntity() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 35);

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 1,2,3,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("bleedPercent"));
  }

  @Test public void legacyPhaGiapAliasUsesArmorBreakRules() throws Exception {
    JSONObject entity = new JSONObject()
        .put("armorBreakTurns", 0)
        .put("armorBreakPercent", 0);

    CombatChoiceEngine.applyStackingEffect(entity, "Phá giáp", 2, 20);

    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(20, entity.getInt("armorBreakPercent"));
  }

  @Test public void spatialDominionRestoresAccuracyPenaltyMissAndDuration() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("syvial", "Syvial")));
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(1);
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("actorIndex", 1).put("seed", 2).put("rngSequence", 0);
    combat.put("currentSkill", new JSONObject()
        .put("name", "Spatial Dominion")
        .put("damagePercent", 210)
        .put("effect", "Mất phương hướng")
        .put("effectTurns", 2)
        .put("effectValue", 25));
    int hpBefore = actor.getInt("hp");

    finalizeAs(state, 2,2,2,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(hpBefore, actor.getInt("hp"));
    assertEquals(1, entity.getInt("accuracyPenaltyTurns"));
    assertEquals(25, entity.getInt("accuracyPenalty"));
    assertTrue(combat.getBoolean("resolvedEntityTurn"));
  }

  @Test public void armorBreakPercentIncreasesDamageWithoutExtendingDuration() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "syvial");
    resetToBaselineNonCaoStats(actor);
    entity.put("armorBreakTurns", 2).put("armorBreakPercent", 20);
    int before = entity.getInt("hp");

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - 46, entity.getInt("hp"));
    assertEquals(2, entity.getInt("armorBreakTurns"));
  }

  @Test public void secondaryCombatMathUsesResistanceAndCriticalMultiplier() {
    assertEquals(150, CombatChoiceEngine.criticalDamage(100));
    assertEquals(5, CombatChoiceEngine.effectiveChance(15, 10));
    assertEquals(0, CombatChoiceEngine.effectiveChance(5, 10));
    assertEquals(100, CombatChoiceEngine.effectiveChance(200, 0));

    int roll = CombatChoiceEngine.secondaryStatRoll(12345, 3, 1, "critical");
    assertTrue(roll >= 0 && roll < 100);
    assertEquals(roll, CombatChoiceEngine.secondaryStatRoll(12345, 3, 1, "critical"));
  }

  @Test public void forcedCharacterCriticalUsesProjectedCriticalPath() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "syvial");
    resetToBaselineNonCaoStats(actor);
    actor.put("criticalChancePercent", 100);
    entity.put("evasionPercent", 0).put("resCriticalPercent", 0);
    int before = entity.getInt("hp");

    finalizeAs(state, 1,3,4,5,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - 45, entity.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.getJSONObject(0).getString("text").contains("[CRITICAL]"));
    JSONObject feedback = combat.getJSONArray("feedbackEvents").getJSONObject(0);
    assertEquals("entity", feedback.getString("target"));
    assertEquals("damage", feedback.getString("kind"));
    assertTrue(feedback.getBoolean("critical"));
  }

  @Test public void forcedPassiveEvasionSkipsEntityDamage() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "syvial");
    resetToBaselineNonCaoStats(actor);
    actor.put("evasionPercent", 100);
    entity.put("resEvasionPercent", 0).put("evasionPercent", 0);
    int hpBefore = actor.getInt("hp");

    finalizeAs(state, 1,3,4,5,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(hpBefore, actor.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.getJSONObject(1).getString("text").contains("Evasion"));
  }

  @Test public void derivedCombatFieldsDoNotChangeStableSeed() throws Exception {
    JSONObject state = combatState(new JSONArray());
    JSONObject legacy = new JSONObject()
        .put("id", "cao_minh")
        .put("name", "Cao Minh")
        .put("sourceIndex", -1)
        .put("hp", 50)
        .put("maxHp", 50)
        .put("baseAttack", 30)
        .put("STR", 5)
        .put("DEF", 5)
        .put("SKL", 5)
        .put("VIT", 5);
    JSONObject enriched = new JSONObject(legacy.toString())
        .put("criticalChancePercent", 5)
        .put("evasionPercent", 0)
        .put("resCriticalPercent", 0)
        .put("resEvasionPercent", 0);

    assertEquals(
        CombatChoiceEngine.stableSeed(state, "hound", new JSONArray().put(legacy)),
        CombatChoiceEngine.stableSeed(state, "hound", new JSONArray().put(enriched)));
  }

  private static void resetToBaselineNonCaoStats(JSONObject actor) throws Exception {
    actor.put("baseAttack", 30)
        .put("baseCriticalChancePercent", 5)
        .put("STR", 5)
        .put("DEF", 5)
        .put("SKL", 5)
        .put("VIT", 5)
        .put("criticalChancePercent", 5)
        .put("evasionPercent", 0)
        .put("resCriticalPercent", 0)
        .put("resEvasionPercent", 0);
  }

  @Test public void luciaJoinedPartyGetsOwnCombatTurnAfterCaoMinh() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("lucia", "Lucia Lục")));
    CombatChoiceEngine.start(state, "hound", 0);

    JSONObject combat = state.getJSONObject("combat");
    JSONArray participants = combat.getJSONArray("participants");
    assertEquals(2, participants.length());
    assertEquals("cao_minh", participants.getJSONObject(0).getString("id"));
    assertEquals("lucia", participants.getJSONObject(1).getString("id"));

    combat.getJSONObject("entity").put("hp", 9999).put("maxHp", 9999).put("attack", 1);
    finalizeAs(state, 1, 2, 3, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, combat.getInt("resolvedActorIndex"));
    assertEquals(1, combat.getInt("actorIndex"));
    assertEquals("Lucia Lục", combat.getString("currentActor"));

    finalizeAs(state, 1, 2, 3, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(1, combat.getInt("resolvedActorIndex"));
    assertEquals("Lucia Lục", combat.getString("resolvedActorName"));
    assertEquals("Cao Minh", combat.getString("currentActor"));
    assertTrue(state.getJSONArray("log").getJSONObject(0)
        .getJSONArray("battleLog").toString().contains("Lucia Lục"));
  }

  private static void finalizeAs(JSONObject state, int... values) throws Exception {
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    JSONArray array = new JSONArray();
    for (int value : values) array.put(value);
    dice.put("values", array)
        .put("hasRolled", true)
        .put("finalized", true)
        .put("resolved", false)
        .put("hand", CombatChoiceEngine.classify(values));
  }

  private static JSONObject member(String id, String name) throws Exception {
    return new JSONObject().put("id", id).put("name", name).put("joined", true);
  }

  private static JSONObject combatState(JSONArray party) throws Exception {
    return new JSONObject()
        .put("turn", 4)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("location", LevelCore.LEVEL_ZERO_START_LOCATION)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", party)
        .put("flags", new JSONObject().put("entityEncounterKey", "hound"))
        .put("log", new JSONArray().put(
            new JSONObject().put("role", "gm").put("text", "Hound xuất hiện")));
  }

  @Test public void scopedCombatRngReplaysFromTurnMetadata() throws Exception {
    JSONObject first = combatState(new JSONArray());
    JSONObject second = combatState(new JSONArray());

    CombatChoiceEngine.start(first, "hound", 0, "turn-combat-rng", 12);
    CombatChoiceEngine.start(second, "hound", 0, "turn-combat-rng", 12);

    JSONObject firstCombat = first.getJSONObject("combat");
    JSONObject secondCombat = second.getJSONObject("combat");
    assertEquals("turn-combat-rng", firstCombat.getString("rngTurnId"));
    assertEquals(12, firstCombat.getInt("rngPreTurnStateVersion"));
    assertTrue(firstCombat.getInt("rngSequence") > 0);
    assertEquals(firstCombat.getJSONObject("diceState").getJSONArray("values").toString(),
        secondCombat.getJSONObject("diceState").getJSONArray("values").toString());

    CombatChoiceEngine.roll(first);
    CombatChoiceEngine.roll(second);
    assertEquals(firstCombat.getJSONObject("diceState").getJSONArray("values").toString(),
        secondCombat.getJSONObject("diceState").getJSONArray("values").toString());
    assertEquals(firstCombat.getInt("rngSequence"), secondCombat.getInt("rngSequence"));
  }


  @Test public void multiEntityStateKeepsHpStatusIndependentAndTargetsSelectedEntity()
      throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state,
        new JSONArray().put("hound").put("clump").put("deathmoth"), 0, null, 0);

    JSONObject combat = state.getJSONObject("combat");
    JSONArray entities = combat.getJSONArray("entities");
    assertEquals(3, entities.length());
    assertEquals("hound", entities.getJSONObject(0).getString("key"));
    assertEquals("clump", entities.getJSONObject(1).getString("key"));
    assertEquals("deathmoth", entities.getJSONObject(2).getString("key"));

    JSONObject hound = entities.getJSONObject(0);
    JSONObject clump = entities.getJSONObject(1);
    JSONObject deathmoth = entities.getJSONObject(2);
    hound.put("bleedTurns", 2).put("bleedPercent", 5);
    assertEquals(0, clump.getInt("bleedTurns"));
    assertEquals(0, deathmoth.getInt("bleedTurns"));

    for (int i = 0; i < entities.length(); i++) {
      entities.getJSONObject(i).put("hp", 9999).put("maxHp", 9999).put("attack", 1).put("stunTurns", 1);
    }
    int houndBefore = hound.getInt("hp");
    int clumpBefore = clump.getInt("hp");
    int deathmothBefore = deathmoth.getInt("hp");

    CombatChoiceEngine.setTargetEntity(state, 1);
    assertEquals(1, combat.getInt("targetEntityIndex"));
    assertEquals(0, combat.getInt("activeEntityIndex"));
    assertEquals("hound", combat.getJSONObject("entity").getString("key"));

    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(houndBefore, hound.getInt("hp"));
    assertTrue(clump.getInt("hp") < clumpBefore);
    assertEquals(deathmothBefore, deathmoth.getInt("hp"));
  }

  @Test public void threeLivingEntitiesRespondInOrderAndUseTheirOwnProcPools() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state,
        new JSONArray().put("hound").put("clump").put("deathmoth"), 0, null, 0);

    JSONObject combat = state.getJSONObject("combat");
    JSONArray entities = combat.getJSONArray("entities");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    actor.put("id", "syvial").put("name", "Syvial");
    resetToBaselineNonCaoStats(actor);
    for (int i = 0; i < entities.length(); i++) {
      entities.getJSONObject(i).put("hp", 9999).put("maxHp", 9999).put("attack", 1)
          .put("evasionPercent", 0);
    }
    int seed = 1;
    while (CombatChoiceEngine.entitySkillProcRoll(seed, 1, 0, 0) >= 31) seed++;
    combat.put("seed", seed);

    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray turns = combat.getJSONArray("resolvedEntityTurns");
    assertEquals(3, turns.length());
    assertEquals("hound", turns.getJSONObject(0).getString("entityKey"));
    assertEquals("clump", turns.getJSONObject(1).getString("entityKey"));
    assertEquals("deathmoth", turns.getJSONObject(2).getString("entityKey"));
    assertTrue(turns.getJSONObject(0).getString("summary").contains("Dead Bite"));
    assertTrue(turns.getJSONObject(1).getString("summary").contains("Grasping Crush"));
    assertTrue(turns.getJSONObject(2).getString("summary").contains("Ceiling Dive"));
    assertEquals(0, combat.getInt("activeEntityIndex"));
  }

  @Test public void middleEntityDeathDoesNotEndEncounterAndOnlyLastDeathWins() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    CombatChoiceEngine.start(state,
        new JSONArray().put("hound").put("clump").put("deathmoth"), 0, null, 0);

    JSONObject combat = state.getJSONObject("combat");
    JSONArray entities = combat.getJSONArray("entities");
    for (int i = 0; i < entities.length(); i++) {
      entities.getJSONObject(i).put("hp", 9999).put("maxHp", 9999).put("attack", 1).put("stunTurns", 20);
    }

    CombatChoiceEngine.setTargetEntity(state, 1);
    entities.getJSONObject(1).put("hp", 1);
    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertTrue(combat.getBoolean("active"));
    assertFalse(entities.getJSONObject(1).getBoolean("alive"));
    assertEquals("dead", entities.getJSONObject(1).getString("status"));
    assertTrue(entities.getJSONObject(0).getBoolean("alive"));
    assertTrue(entities.getJSONObject(2).getBoolean("alive"));
    assertEquals(1, combat.getJSONArray("entityDeaths").length());
    assertEquals("Clump", combat.getJSONArray("entityDeaths").getJSONObject(0).getString("name"));
    assertNotEquals(1, combat.getInt("activeEntityIndex"));

    CombatChoiceEngine.setTargetEntity(state, 0);
    entities.getJSONObject(0).put("hp", 1);
    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);
    assertTrue(combat.getBoolean("active"));
    assertEquals(2, combat.getJSONArray("entityDeaths").length());

    CombatChoiceEngine.setTargetEntity(state, 2);
    entities.getJSONObject(2).put("hp", 1);
    finalizeAs(state, 1, 2, 4, 5, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertEquals(3, combat.getJSONArray("entityDeaths").length());
    assertEquals("Deathmoth", combat.getJSONArray("entityDeaths").getJSONObject(2).getString("name"));
    assertEquals(6, progression.coreCount(state));
    assertTrue(combat.getBoolean("coreDropResolved"));
    assertTrue(combat.getBoolean("lootResolved"));
  }

  @Test public void legacyCombatEntityMigratesToSingleElementEntitiesArray() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    combat.remove("entities");
    combat.remove("activeEntityIndex");
    combat.remove("targetEntityIndex");

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    assertEquals(1, combat.getJSONArray("entities").length());
    assertEquals("hound", combat.getJSONArray("entities").getJSONObject(0).getString("key"));
    assertEquals("hound", combat.getJSONObject("entity").getString("key"));
    assertEquals(0, combat.getInt("activeEntityIndex"));
    assertEquals(0, combat.getInt("targetEntityIndex"));
  }

}
