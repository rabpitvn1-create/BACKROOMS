package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EntityCoreTest {
  @Test public void onlyTamMaRemainsAutoSpawnAndFormerRoamingEntitiesStayDormantLegacy()
      throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("knowledge/entity_encounters.json"));
    JSONArray auto = root.getJSONArray("entities");
    assertEquals(1, auto.length());
    assertEquals("tam_ma_cao_minh", auto.getJSONObject(0).getString("key"));

    JSONArray legacy = root.getJSONArray("legacyEntities");
    Map<String, JSONObject> legacyByKey = new HashMap<>();
    for (int i = 0; i < legacy.length(); i++) {
      JSONObject record = legacy.getJSONObject(i);
      legacyByKey.put(record.getString("key"), record);
    }

    String[] dormant = {
        "hound",
        "clump",
        "duller",
        "deathmoth",
        "hostile_faceling",
        "false_puddle",
        "paintings",
        "smiler",
        "skin-stealer",
        "predatory_window",
        "biological_pipeline",
        "wretch",
        "cable_mimic",
        "the_beast_of_level_5",
        "hotel_corpse_lure",
        "jeff_the_killer",
        "async_rifleman",
        "copx"
    };
    for (String key : dormant) {
      JSONObject record = legacyByKey.get(key);
      assertNotNull("Dormant Entity must be preserved in legacy: " + key, record);
      assertFalse(record.optString("canon", "").trim().isEmpty());
      assertNotNull(record.optJSONObject("presentation"));
      assertEquals(3, record.getJSONArray("autoProcSkills").length());
    }
  }

  @Test public void roamingEntitiesAreEligibleOnEveryCurrentLevel() {
    for (int level = 0; level <= 6; level++) {
      assertTrue("Level " + level + " must allow roaming Entities", EntityCore.roamingAllowedOn(level));
    }
  }

  @Test public void roamingPolicyDoesNotHardCodeCurrentLevelRange() {
    assertTrue(EntityCore.roamingAllowedOn(7));
    assertTrue(EntityCore.roamingAllowedOn(99));
    assertFalse(EntityCore.roamingAllowedOn(-1));
  }

  @Test public void autoSpawnRateBoundsReflectPlusTwoPointIncrease() {
    assertTrue(EntityCore.validAutoSpawnRatePercent(3.0d));
    assertTrue(EntityCore.validAutoSpawnRatePercent(3.5d));
    assertFalse(EntityCore.validAutoSpawnRatePercent(2.99d));
    assertFalse(EntityCore.validAutoSpawnRatePercent(3.51d));
  }

  @Test public void treasureSpawnAllowsFourPercentWithoutChangingOrdinaryBounds() {
    assertFalse(EntityCore.validAutoSpawnRatePercent(4.0d));
    assertTrue(EntityCore.validTreasureAutoSpawnRatePercent(4.0d));
    assertFalse(EntityCore.validTreasureAutoSpawnRatePercent(0.0d));
    assertFalse(EntityCore.validTreasureAutoSpawnRatePercent(4.01d));
  }

  @Test public void legacyBossPromptCarriesCanonWithoutAutoSpawnSemantics() {
    String prompt = EntityCore.legacyPromptContext(
        "diep_minh", "Diệp Minh", "Huyết cừu Cao gia; ontology Backrooms vẫn OPEN.");

    assertTrue(prompt.contains("Diệp Minh"));
    assertTrue(prompt.contains("Huyết cừu Cao gia"));
    assertTrue(prompt.contains("legacy/boss-only"));
    assertTrue(prompt.contains("must not be treated as an auto-spawn Entity"));
  }

  private static String readRepoAsset(String relativePath) throws Exception {
    Path[] candidates = {
        Paths.get("src/main/assets", relativePath),
        Paths.get("app/src/main/assets", relativePath),
        Paths.get("android-apk/app/src/main/assets", relativePath)
    };
    for (Path path : candidates) {
      if (Files.isRegularFile(path)) {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalStateException("Asset not found: " + relativePath);
  }
}
