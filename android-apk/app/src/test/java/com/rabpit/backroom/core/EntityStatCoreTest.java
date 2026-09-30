package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EntityStatCoreTest {
  @Test public void additiveStageScalingMatchesContract() {
    assertEquals(100, EntityStatCore.stagePercent(0));
    assertEquals(110, EntityStatCore.stagePercent(1));
    assertEquals(120, EntityStatCore.stagePercent(2));
    assertEquals(200, EntityStatCore.stagePercent(10));
    assertEquals(240, EntityStatCore.scale(240, 0));
    assertEquals(264, EntityStatCore.scale(240, 1));
    assertEquals(288, EntityStatCore.scale(240, 2));
    assertEquals(480, EntityStatCore.scale(240, 10));
  }

  @Test public void profileScalesOnlyHpAndDamageWithoutCharacterStats() throws Exception {
    JSONObject profile = new EntityStatCore().profile("hound", 240, 15, 2);
    assertEquals(2, profile.getInt("stageIndex"));
    assertEquals(120, profile.getInt("stagePercent"));
    assertEquals(288, profile.getInt("maxHp"));
    assertEquals(18, profile.getInt("damage"));
    assertFalse(profile.has("STR"));
    assertFalse(profile.has("DEF"));
    assertFalse(profile.has("AGI"));
    assertFalse(profile.has("CRIT"));
    assertFalse(profile.has("VIT"));
    assertFalse(profile.has("exp"));
    assertFalse(profile.has("level"));
  }
}
