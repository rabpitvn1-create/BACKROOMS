package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class HiddenExitStreakTest {
  private static JSONObject state(String location, int streak) throws Exception {
    JSONObject exploration = new JSONObject()
        .put(HiddenExitStreak.STREAK_KEY, streak)
        .put("exitReady", streak >= HiddenExitStreak.REQUIRED_STREAK)
        .put("transitionReady", streak >= HiddenExitStreak.REQUIRED_STREAK);
    return new JSONObject()
        .put("location", location)
        .put("flags", new JSONObject().put("exploration", exploration));
  }

  private static JSONObject roll(boolean success) throws Exception {
    return new JSONObject().put(HiddenExitStreak.ROLL_KEY, new JSONObject()
        .put("eligible", true).put("success", success));
  }

  @Test public void fiveConsecutiveSuccessesUnlockExit() throws Exception {
    JSONObject current = state("Level 0 / Hall A", 0);
    for (int i = 1; i <= HiddenExitStreak.REQUIRED_STREAK; i++) {
      JSONObject before = new JSONObject(current.toString());
      JSONObject candidate = new JSONObject(current.toString());
      JSONObject rolls = roll(true);
      assertEquals(i == HiddenExitStreak.REQUIRED_STREAK,
          HiddenExitStreak.projectedReady(before, rolls));
      HiddenExitStreak.apply(before, candidate, rolls, 0, 0);
      current = candidate;
      assertEquals(i, HiddenExitStreak.current(current));
    }
    assertTrue(HiddenExitStreak.ready(current));
    JSONObject exploration = current.getJSONObject("flags").getJSONObject("exploration");
    assertTrue(exploration.getBoolean("exitReady"));
    assertTrue(exploration.getBoolean("transitionReady"));
  }

  @Test public void failureResetsStreakAndKeepsPreviousLocation() throws Exception {
    JSONObject before = state("Level 2 / Pipe Junction", 4);
    JSONObject candidate = new JSONObject(before.toString()).put("location", "Level 2 / New Corridor");
    JSONObject exploration = candidate.getJSONObject("flags").getJSONObject("exploration");
    exploration.put("confirmedExit", "door").put("exitCandidate", "door").put("exitProgress", "READY");

    JSONObject rolls = roll(false);
    assertTrue(HiddenExitStreak.failedThisTurn(rolls));
    assertFalse(HiddenExitStreak.projectedReady(before, rolls));
    HiddenExitStreak.apply(before, candidate, rolls, 2, 2);

    assertEquals(0, HiddenExitStreak.current(candidate));
    assertEquals("Level 2 / Pipe Junction", candidate.getString("location"));
    assertFalse(exploration.getBoolean("exitReady"));
    assertFalse(exploration.getBoolean("transitionReady"));
    assertFalse(exploration.has("confirmedExit"));
    assertFalse(exploration.has("exitCandidate"));
    assertFalse(exploration.has("exitProgress"));
  }

  @Test public void ineligibleActionPreservesPartialStreak() throws Exception {
    JSONObject before = state("Level 3 / Transformer Row", 3);
    JSONObject candidate = new JSONObject(before.toString());
    JSONObject rolls = new JSONObject().put(HiddenExitStreak.ROLL_KEY, new JSONObject()
        .put("eligible", false).put("success", false));

    HiddenExitStreak.apply(before, candidate, rolls, 3, 3);

    assertEquals(3, HiddenExitStreak.current(candidate));
    assertEquals("Level 3 / Transformer Row", candidate.getString("location"));
    assertFalse(HiddenExitStreak.ready(candidate));
  }

  @Test public void realLevelTransitionResetsStreakForNextLevel() throws Exception {
    JSONObject before = state("Level 4 / Office", 5);
    JSONObject candidate = state("Level 5 / Hotel", 5);
    HiddenExitStreak.apply(before, candidate, new JSONObject(), 4, 5);
    assertEquals(0, HiddenExitStreak.current(candidate));
    assertFalse(HiddenExitStreak.ready(candidate));
    assertEquals("Level 5 / Hotel", candidate.getString("location"));
  }
}
