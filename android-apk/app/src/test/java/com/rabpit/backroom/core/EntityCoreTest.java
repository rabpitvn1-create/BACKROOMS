package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EntityCoreTest {
  private static final String[] BACTERIAL_KEYS = {
      "the_lifeform_bacteria_01",
      "the_lifeform_bacteria_02",
      "the_lifeform_bacteria_03"
  };

  @Test public void tamMaAndThreeBacterialVariantsAreAutoSpawnWhileOldRosterStaysLegacy()
      throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("knowledge/entity_encounters.json"));
    JSONArray auto = root.getJSONArray("entities");
    Map<String, JSONObject> autoByKey = new HashMap<>();
    for (int i = 0; i < auto.length(); i++) {
      JSONObject record = auto.getJSONObject(i);
      autoByKey.put(record.getString("key"), record);
    }

    assertEquals(4, autoByKey.size());
    assertNotNull(autoByKey.get("tam_ma_cao_minh"));

    String sharedThree = null;
    Set<String> fourthSkills = new HashSet<>();
    for (String key : BACTERIAL_KEYS) {
      JSONObject record = autoByKey.get(key);
      assertNotNull("Missing active Bacterial variant: " + key, record);
      assertEquals(5.0d, record.getDouble("ratePercent"), 0.0001d);
      assertEquals(4, record.getJSONArray("autoProcSkills").length());
      assertEquals(
          new JSONArray().put("0").put("0.1").put("0.2").put("the_torment")
              .put("red_rooms").put("4").put("6").toString(),
          record.getJSONArray("levelKeys").toString());

      JSONArray skills = record.getJSONArray("autoProcSkills");
      String firstThree = new JSONArray()
          .put(skills.getJSONObject(0))
          .put(skills.getJSONObject(1))
          .put(skills.getJSONObject(2)).toString();
      if (sharedThree == null) sharedThree = firstThree;
      else assertEquals(sharedThree, firstThree);
      fourthSkills.add(skills.getJSONObject(3).getString("name"));
    }
    assertEquals(3, fourthSkills.size());

    JSONArray legacy = root.getJSONArray("legacyEntities");
    Map<String, JSONObject> legacyByKey = new HashMap<>();
    for (int i = 0; i < legacy.length(); i++) {
      JSONObject record = legacy.getJSONObject(i);
      legacyByKey.put(record.getString("key"), record);
    }

    String[] dormant = {
        "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle",
        "paintings", "smiler", "skin-stealer", "predatory_window", "biological_pipeline",
        "wretch", "cable_mimic", "the_beast_of_level_5", "hotel_corpse_lure",
        "jeff_the_killer", "async_rifleman", "copx"
    };
    for (String key : dormant) {
      JSONObject record = legacyByKey.get(key);
      assertNotNull("Dormant Entity must remain preserved in legacy: " + key, record);
      assertFalse(record.optString("canon", "").trim().isEmpty());
      assertNotNull(record.optJSONObject("presentation"));
      assertEquals(3, record.getJSONArray("autoProcSkills").length());
    }
  }

  @Test public void exactLevelKeysOverrideBroadParentLevelEligibility() {
    JSONArray parentLevels = new JSONArray().put(0).put(4).put(6);
    JSONArray exact = new JSONArray()
        .put("0").put("0.1").put("0.2").put("the_torment").put("red_rooms").put("4").put("6");

    assertTrue(EntityCore.locationAllowed(parentLevels, exact, 0, "0"));
    assertTrue(EntityCore.locationAllowed(parentLevels, exact, 0, "0.1"));
    assertTrue(EntityCore.locationAllowed(parentLevels, exact, 0, "red_rooms"));
    assertTrue(EntityCore.locationAllowed(parentLevels, exact, 4, "4"));
    assertTrue(EntityCore.locationAllowed(parentLevels, exact, 6, "6"));

    assertFalse(EntityCore.locationAllowed(parentLevels, exact, 0, "0.5"));
    assertFalse(EntityCore.locationAllowed(parentLevels, exact, 0, "0.7"));
    assertFalse(EntityCore.locationAllowed(parentLevels, exact, 0, "manila_room"));
    assertFalse(EntityCore.locationAllowed(parentLevels, exact, 5, "5.1"));
  }

  @Test public void bacterialConfiguredFivePercentBecomesFifteenPercentAtRuntime() {
    assertTrue(EntityCore.validAutoSpawnRatePercent(3.0d));
    assertTrue(EntityCore.validAutoSpawnRatePercent(5.0d));
    assertFalse(EntityCore.validAutoSpawnRatePercent(2.99d));
    assertFalse(EntityCore.validAutoSpawnRatePercent(5.01d));
    assertEquals(15.0d, EntityCore.effectiveAutoSpawnRatePercent(5.0d), 0.0001d);
  }

  @Test public void treasureConfiguredFourPercentStillBecomesTwelvePercent() {
    assertTrue(EntityCore.validAutoSpawnRatePercent(4.0d));
    assertTrue(EntityCore.validTreasureAutoSpawnRatePercent(4.0d));
    assertFalse(EntityCore.validTreasureAutoSpawnRatePercent(0.0d));
    assertFalse(EntityCore.validTreasureAutoSpawnRatePercent(4.01d));
    assertEquals(12.0d, EntityCore.effectiveAutoSpawnRatePercent(4.0d), 0.0001d);
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
