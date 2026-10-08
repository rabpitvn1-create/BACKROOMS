package com.rabpit.backroom.core;

import org.junit.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class EntityEncounterPolicyTest {
  @Test public void everyRareEntityRetainsItsExactChanceAtEveryLevel() {
    assertEquals(9, EntityEncounterPolicy.rareWindows().size());
    for (int level = 0; level <= 6; level++) {
      Map<String, Integer> seen = counts(level);
      for (EntityEncounterPolicy.Window rare : EntityEncounterPolicy.rareWindows()) {
        assertEquals("Rare Entity " + rare.entityKey + " at level " + level,
            "diep_minh".equals(rare.entityKey) ? 300 : 400,
            seen.getOrDefault(rare.entityKey, 0).intValue());
      }
    }
  }

  @Test public void eachNormalEntityGetsExactlyEightPercentAtItsOwnLevel() {
    String[][] expected = {
        {},
        {"hound", "duller", "hostile_faceling", "false_puddle", "skin-stealer"},
        {"clump", "smiler", "biological_pipeline"},
        {"wretch", "cable_mimic"},
        {"predatory_window"},
        {"deathmoth", "paintings", "the_beast_of_level_5", "hotel_corpse_lure"},
        {}
    };
    Set<String> seenNormal = new HashSet<>();
    for (int level = 0; level <= 6; level++) {
      Map<String, Integer> counts = counts(level);
      Set<String> expectedAtLevel = new HashSet<>();
      for (String key : expected[level]) {
        assertTrue("Normal Entity duplicated across Levels", seenNormal.add(key));
        expectedAtLevel.add(key);
        assertEquals("Normal Entity at its assigned Level", 800,
            counts.getOrDefault(key, 0).intValue());
      }
      for (EntityEncounterPolicy.Window window : EntityEncounterPolicy.windowsForLevel(level)) {
        if (!window.rare) {
          assertTrue("Unassigned normal Entity at Level " + level,
              expectedAtLevel.contains(window.entityKey));
          assertEquals(800, window.chanceBasisPoints());
        }
      }
      assertEquals(3500 + 800 * expected[level].length,
          EntityEncounterPolicy.totalChanceBasisPoints(level));
      assertEquals(10000 - EntityEncounterPolicy.totalChanceBasisPoints(level),
          counts.getOrDefault("", 0).intValue());
    }
    assertEquals(15, seenNormal.size());
  }

  @Test public void emptyNormalLevelsStillSpawnAllRareEntities() {
    for (int level : new int[] {0, 6}) {
      assertEquals(3500, EntityEncounterPolicy.totalChanceBasisPoints(level));
      for (EntityEncounterPolicy.Window window : EntityEncounterPolicy.windowsForLevel(level)) {
        assertTrue(window.rare);
      }
    }
  }

  @Test public void windowsAreDisjointAndProduceAtMostOneEncounter() {
    for (int level = 0; level <= 6; level++) {
      int previous = 0;
      for (EntityEncounterPolicy.Window window : EntityEncounterPolicy.windowsForLevel(level)) {
        assertEquals(previous + 1, window.start);
        assertEquals(window.chanceBasisPoints(), window.end - window.start + 1);
        previous = window.end;
      }
      assertTrue(previous <= EntityEncounterPolicy.DIE);
    }
    try {
      EntityEncounterPolicy.pick(1, 10001);
      fail("Invalid dice result accepted");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private static Map<String, Integer> counts(int level) {
    Map<String, Integer> result = new HashMap<>();
    for (int roll = 1; roll <= EntityEncounterPolicy.DIE; roll++) {
      EntityEncounterPolicy.Window selected = EntityEncounterPolicy.pick(level, roll);
      String key = selected == null ? "" : selected.entityKey;
      result.put(key, result.getOrDefault(key, 0) + 1);
    }
    return result;
  }
}
