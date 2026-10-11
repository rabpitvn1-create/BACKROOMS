package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class EntityEncounterPoolTest {
  @Test public void exactlyFortyFivePercentAcrossEveryEntity() {
    Map<String, Integer> counts = new HashMap<>();
    for (int roll = 1; roll <= EntityEncounterPool.ENCOUNTER_THRESHOLD; roll++) {
      String key = EntityEncounterPool.pickForSuccessfulRoll(roll);
      counts.put(key, counts.getOrDefault(key, 0) + 1);
    }
    assertEquals(22, counts.size());
    int total = 0;
    for (int count : counts.values()) total += count;
    assertEquals(4500, total);
    String[] common = {"hound", "clump", "duller", "deathmoth", "hostile_faceling",
      "false_puddle", "paintings", "smiler", "skin-stealer", "predatory_window",
      "biological_pipeline", "wretch", "cable_mimic", "hotel_corpse_lure"};
    String[] lifeform = {"blackroot_sentinel", "sinew_strider", "hollow_grasper"};
    String[] rare = {"the_beast_of_level_5", "slenderman", "diep_minh"};
    String[] killers = {"jeff_the_killer", "jane_the_killer"};
    for (String key : common) assertEquals(Integer.valueOf(250), counts.get(key));
    for (String key : lifeform) assertEquals(Integer.valueOf(200), counts.get(key));
    for (String key : rare) assertEquals(Integer.valueOf(100), counts.get(key));
    for (String key : killers) assertEquals(Integer.valueOf(50), counts.get(key));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unsuccessfulRollCannotPickEntity() {
    EntityEncounterPool.pickForSuccessfulRoll(4501);
  }

  @Test(expected = IllegalArgumentException.class)
  public void zeroCannotPickEntity() {
    EntityEncounterPool.pickForSuccessfulRoll(0);
  }
}
