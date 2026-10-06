package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LuciaSeparationContractTest {
  @Test public void luciaAndLucTramRemainDistinctAcrossRuntimeAndCanon() throws Exception {
    CharacterEncounterCore encounters = new CharacterEncounterCore();

    JSONObject partyState = state(0, "0").put("party", new JSONArray()
        .put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục"))
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm"))
        .put(new JSONObject().put("id", "syvial").put("name", "Syvial")));
    encounters.normalizeState(partyState);

    JSONArray party = partyState.getJSONArray("party");
    assertEquals(3, party.length());
    assertEquals("lucia", party.getJSONObject(0).getString("id"));
    assertEquals("luc_tram", party.getJSONObject(1).getString("id"));
    assertEquals("syvial", party.getJSONObject(2).getString("id"));

    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(partyState);
    assertTrue(partyState.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject(CharacterProgressionCore.CHARACTERS_KEY).has("lucia"));
    assertTrue(partyState.getJSONObject(CharacterProgressionCore.ROOT_KEY)
        .getJSONObject(CharacterProgressionCore.CHARACTERS_KEY).has("luc_tram"));
    assertEquals("lucia", CharacterProgressionCore.normalizeCharacterId("Hứa Thuý Mai"));
    assertEquals("luc_tram", CharacterProgressionCore.normalizeCharacterId("Lục Trầm"));

    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("lucia"));
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("luc_tram"));
    assertEquals(5, CombatChoiceEngine.characterProcCount("lucia"));
    assertEquals(5, CombatChoiceEngine.characterProcCount("luc_tram"));

    JSONArray levelZero = encounters.situationCandidates(state(0, "0"));
    assertEquals(10.0d, chance(levelZero, "character:lucia"), 0.0000001d);
    assertFalse(levelZero.toString().contains("character:luc_tram"));

    JSONArray levelOne = encounters.situationCandidates(state(1, "1"));
    assertEquals(1.0d, chance(levelOne, "character:luc_tram"), 0.0000001d);
    assertFalse(levelOne.toString().contains("character:lucia"));

    Path luciaStructured = Paths.get("src/main/assets/content/characters/lucia.md");
    Path luciaLegacy = Paths.get("src/main/assets/canon/Lucia_Codex.md");
    if (!Files.isRegularFile(luciaStructured)) {
      luciaStructured = Paths.get("app/src/main/assets/content/characters/lucia.md");
      luciaLegacy = Paths.get("app/src/main/assets/canon/Lucia_Codex.md");
    }
    assertTrue(Files.isRegularFile(luciaStructured));
    assertFalse(Files.exists(luciaLegacy));

    Path lucTramStructured = Paths.get("src/main/assets/content/characters/luc-tram.md");
    Path lucTramLegacy = Paths.get("src/main/assets/canon/Lục_Trầm_Codex.md");
    if (!Files.isRegularFile(lucTramStructured)) {
      lucTramStructured = Paths.get("app/src/main/assets/content/characters/luc-tram.md");
      lucTramLegacy = Paths.get("app/src/main/assets/canon/Lục_Trầm_Codex.md");
    }
    assertTrue(Files.isRegularFile(lucTramStructured));
    assertFalse(Files.exists(lucTramLegacy));

    String luciaCanon = readCanon("Lucia_Codex.md");
    String lucTramCanon = readCanon("Lục_Trầm_Codex.md");
    assertTrue(luciaCanon.contains("Lucia Lục / Hứa Thuý Mai và Lục Trầm là hai nhân vật khác nhau"));
    assertTrue(luciaCanon.contains("Lucia đến từ một thế giới khác với Cao Minh/Lục Trầm"));
    assertTrue(luciaCanon.contains("tương lai xa hơn về công nghệ và xã hội"));
    assertTrue(luciaCanon.contains("không viết cô như radio chiến thuật hoặc commando caricature"));
    assertTrue(lucTramCanon.contains("Lucia Lục / Hứa Thuý Mai là nhân vật riêng"));
    assertFalse(lucTramCanon.contains(
        "Lucia Lục / Hứa Thuý Mai và loadout quân nhân cũ là LEGACY / RETIRED"));

    Map<String, String> canon = new LinkedHashMap<>();
    canon.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nYellow walls.\n");
    canon.put("Cao_Minh_Codex.md", "# Cao Minh\n## Định danh\nPlayer.\n");
    canon.put("Lucia_Codex.md", luciaCanon);
    canon.put("Lục_Trầm_Codex.md", lucTramCanon);
    JSONObject retrievalState = state(0, "0").put("party", new JSONArray()
        .put(new JSONObject().put("id", "lucia").put("present", true)));
    CanonRetriever.CanonPacket packet =
        new CanonRetriever(canon).retrieve(retrievalState, "Tôi nhìn Lucia.", 12000, true);
    assertTrue(packet.missingMandatoryRefs.toString(), packet.missingMandatoryRefs.isEmpty());
    assertTrue(packet.promptText().contains("Runtime id của Lucia là `lucia`"));
    assertFalse(packet.promptText().contains("Lucia Lục / Hứa Thuý Mai và loadout quân nhân cũ"));
  }

  private static JSONObject state(int level, String levelKey) throws Exception {
    return new JSONObject()
        .put("currentLevel", level)
        .put(LevelCore.LEVEL_KEY, levelKey)
        .put("turn", 1)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject())
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Test")));
  }

  private static double chance(JSONArray candidates, String key) throws Exception {
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.getJSONObject(i);
      if (key.equals(candidate.optString("situationKey"))) return candidate.getDouble("chancePercent");
    }
    throw new AssertionError("Missing candidate " + key);
  }

  private static String readCanon(String name) throws Exception {
    if ("Lucia_Codex.md".equals(name) || "Lục_Trầm_Codex.md".equals(name)) {
      String structuredName = "Lucia_Codex.md".equals(name) ? "lucia.md" : "luc-tram.md";
      Path structured = Paths.get("src/main/assets/content/characters", structuredName);
      if (!Files.isRegularFile(structured)) {
        structured = Paths.get("app/src/main/assets/content/characters", structuredName);
      }
      if (Files.isRegularFile(structured)) {
        return new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
      }
    }
    Path path = Paths.get("src/main/assets/canon", name);
    if (!Files.isRegularFile(path)) path = Paths.get("app/src/main/assets/canon", name);
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
