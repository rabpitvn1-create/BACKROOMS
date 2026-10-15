package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LevelRouteRegistryTest {
  @Test public void allMainSublevelSpecialAndAreaNodesAreRegistered() {
    assertEquals(25, LevelRouteRegistry.size());
    for (int i = 0; i <= 6; i++) {
      LevelRouteRegistry.Node node = LevelRouteRegistry.node(Integer.toString(i));
      assertNotNull(node);
      assertEquals(i, node.parent);
      assertEquals("MAIN", node.kind);
    }
    for (String id : Arrays.asList("0.1", "0.2", "0.5", "0.7",
        "manila_room", "the_torment", "red_rooms", "dullness", "epsilon", "ls_2")) {
      assertEquals(0, LevelRouteRegistry.node(id).parent);
    }
    for (String id : Arrays.asList("1.2", "1.5", "traders_vault", "base_alpha"))
      assertEquals(1, LevelRouteRegistry.node(id).parent);
    assertEquals(2, LevelRouteRegistry.node("office_space_el3a").parent);
    assertEquals(4, LevelRouteRegistry.node("office_market").parent);
    assertEquals(5, LevelRouteRegistry.node("5.1").parent);
    assertEquals(6, LevelRouteRegistry.node("6.1").parent);
    assertNull(LevelRouteRegistry.node("7"));
  }

  @Test public void levelZeroMandatoryRouteCannotBeSkippedOrReversed() {
    List<String> chain = Arrays.asList("0", "0.1", "0.2", "0.5", "0.7",
        "manila_room", "the_torment", "red_rooms", "1");
    for (int i = 0; i < chain.size() - 1; i++) {
      assertTrue(chain.get(i) + " -> " + chain.get(i+1),
          LevelRouteRegistry.canMove(chain.get(i), chain.get(i + 1)));
      assertFalse(chain.get(i + 1) + " -> " + chain.get(i),
          LevelRouteRegistry.canMove(chain.get(i + 1), chain.get(i)));
    }
    assertFalse(LevelRouteRegistry.canMove("0", "1"));
    assertFalse(LevelRouteRegistry.canMove("0.2", "0.7"));
    assertFalse(LevelRouteRegistry.canMove("red_rooms", "the_torment"));
  }

  @Test public void optionalAreasHaveExplicitReturnPathsWithoutShortcuts() {
    String[][] branches = {
        {"0", "dullness"}, {"0", "epsilon"}, {"0", "ls_2"},
        {"1", "1.2"}, {"1", "1.5"}, {"1", "traders_vault"}, {"1", "base_alpha"},
        {"2", "office_space_el3a"}, {"4", "office_market"},
        {"5", "5.1"}, {"6", "6.1"}
    };
    for (String[] entry : branches) {
      assertTrue(LevelRouteRegistry.canMove(entry[0], entry[1]));
      assertTrue(LevelRouteRegistry.canMove(entry[1], entry[0]));
    }
    assertFalse(LevelRouteRegistry.canMove("1.2", "2"));
    assertFalse(LevelRouteRegistry.canMove("6.1", "7"));
    assertTrue(LevelRouteRegistry.canMove("3", "6"));
    assertFalse(LevelRouteRegistry.canMove("6", "3"));
  }

  @Test public void mainLevelProgressionIsOneWayWhileLocalDetoursAreReversible() {
    String[][] forwardRoutes = {
        {"1", "2"}, {"2", "3"}, {"3", "4"}, {"3", "6"}, {"4", "5"}, {"5", "6"}
    };
    for (int i = 0; i < forwardRoutes.length; i++) {
      String from = forwardRoutes[i][0], to = forwardRoutes[i][1];
      assertTrue(from + " -> " + to, LevelRouteRegistry.canMove(from, to));
      assertFalse(to + " -> " + from, LevelRouteRegistry.canMove(to, from));
    }
    assertTrue(LevelRouteRegistry.canMove("1", "traders_vault"));
    assertTrue(LevelRouteRegistry.canMove("traders_vault", "1"));
    assertFalse(LevelRouteRegistry.canMove("2", "1"));
    assertFalse(LevelRouteRegistry.canMove("5", "4"));
    assertFalse(LevelRouteRegistry.canMove("6", "5"));
  }

  @Test public void decimalIdentitySurvivesJsonAndParentScalingRemainsIntegral() throws Exception {
    LevelRouteRegistry.Node sublevel = LevelRouteRegistry.requested(new JSONObject().put("number", 1.2));
    assertEquals("1.2", sublevel.id);
    JSONObject stored = sublevel.asJson();
    assertEquals("1.2", LevelRouteRegistry.currentId(new JSONObject().put("level", stored)));
    assertEquals(1, stored.getInt("number"));
    assertEquals("1.2", LevelRouteRegistry.fromLocation("Level 1.2 / corridor").id);
    assertEquals("manila_room", LevelRouteRegistry.fromLocation("Manila Room / desk").id);
    assertEquals("0", LevelRouteRegistry.currentId(new JSONObject()
        .put("level", new JSONObject().put("number", 0))));
    assertNull(LevelRouteRegistry.requested(new JSONObject().put("id", "7")));
    assertNull(LevelRouteRegistry.fromLocation("Hallway mentioning Level 6"));
  }

  @Test public void nodeTransitionsResetExitStreakEvenInsideSameParentLevel() throws Exception {
    JSONObject before = new JSONObject().put("location", "Level 0 / Hall")
        .put("flags", new JSONObject().put("exploration", new JSONObject()
          .put(HiddenExitStreak.STREAK_KEY, 5).put("exitReady", true)));
    JSONObject after = new JSONObject(before.toString()).put("location", "Level 0.1 / Zenith Station");
    HiddenExitStreak.apply(before, after, new JSONObject(), "0", "0.1");
    assertEquals(0, HiddenExitStreak.current(after));
    assertFalse(after.getJSONObject("flags").getJSONObject("exploration").getBoolean("exitReady"));
  }
}
