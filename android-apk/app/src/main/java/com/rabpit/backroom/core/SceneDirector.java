package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Offline scene director. It does not own gameplay rules: it receives committed Core facts plus a
 * LevelCore environment draw and turns them into the single authoritative frame given to the GM.
 */
public final class SceneDirector {
  public static final int SCHEMA_VERSION = 1;

  private SceneDirector() {}

  public static JSONObject compose(JSONObject state, JSONObject facts, JSONObject environment)
      throws Exception {
    JSONObject frame = new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("authority", "LOCAL_SCENE_DIRECTOR")
        .put("playerIntent", copyObject(facts, "playerIntent"))
        .put("environment", environment == null ? new JSONObject() : new JSONObject(environment.toString()))
        .put("presentCharacters", copyArray(facts, "presentCharacters"))
        .put("pendingIntro", copyArray(facts, "pendingIntro"))
        .put("worldFacts", copyArray(facts, "worldFacts"))
        .put("entityEvents", copyArray(facts, "entityEvents"))
        .put("chestEvents", copyArray(facts, "chestEvents"))
        .put("characterEvents", copyArray(facts, "characterEvents"))
        .put("routeEvents", copyArray(facts, "routeEvents"))
        .put("specialEvents", copyArray(facts, "specialEvents"))
        .put("chestPresent", facts != null && facts.optBoolean("chestPresent", false));

    JSONObject voices = new JSONObject();
    JSONArray present = frame.getJSONArray("presentCharacters");
    for (int i = 0; i < present.length(); i++) {
      JSONObject member = present.optJSONObject(i);
      String id = member == null ? "" : member.optString("id", "").trim();
      if ("lucia".equals(id) || "luc_tram".equals(id) || "syvial".equals(id)) {
        voices.put(id, CharacterEncounterCore.sceneVoiceCard(id));
      }
    }
    JSONArray pending = frame.getJSONArray("pendingIntro");
    for (int i = 0; i < pending.length(); i++) {
      String id = pending.optString(i, "").trim();
      if (("lucia".equals(id) || "luc_tram".equals(id) || "syvial".equals(id)) && !voices.has(id)) {
        voices.put(id, CharacterEncounterCore.sceneVoiceCard(id));
      }
    }
    frame.put("characterVoice", voices);

    String focus = "ENVIRONMENT";
    if (frame.getJSONArray("entityEvents").length() > 0) focus = "ENTITY";
    else if (frame.getJSONArray("characterEvents").length() > 0) focus = "CHARACTER";
    else if (frame.getJSONArray("chestEvents").length() > 0) focus = "CHEST";
    else if (frame.getJSONArray("routeEvents").length() > 0) focus = "ROUTE";
    else if (frame.getJSONArray("specialEvents").length() > 0) focus = "SPECIAL";
    frame.put("focus", focus);

    frame.put("gmContract", new JSONObject()
        .put("role", "NARRATOR_ONLY")
        .put("mayAddGameplayFacts", false)
        .put("maySpawnEntity", false)
        .put("maySpawnChest", false)
        .put("maySpawnCharacter", false)
        .put("mayCreateSpecialEvent", false)
        .put("mayCreateChoices", false)
        .put("playerIntentIsWorldFact", false));

    frame.put("fallbackSummary", fallbackSummary(frame));
    return frame;
  }

  private static String fallbackSummary(JSONObject frame) {
    JSONArray world = frame.optJSONArray("worldFacts");
    if (world != null) {
      for (int i = 0; i < world.length(); i++) {
        JSONObject event = world.optJSONObject(i);
        if (event == null) continue;
        String type = event.optString("eventType", "");
        if ("ENTITY_ENCOUNTER_STARTED".equals(type)) {
          String subject = event.optString("entityAppearance", event.optString("subject", "một thực thể")).trim();
          String where = event.optString("entityLocation", "phía trước").trim();
          return capitalize(subject) + " xuất hiện " + where + ".";
        }
        if ("CHEST_SPAWNED".equals(type)) {
          return event.optString("actor", "Cao Minh") + " phát hiện một chiếc rương "
              + event.optString("chestLocation", "trong khu vực hiện tại") + ".";
        }
        if ("CHEST_OPENED".equals(type)) {
          return event.optString("actor", "Cao Minh") + " mở chiếc rương. Bên trong là "
              + event.optString("loot", "một vật phẩm") + ".";
        }
        if ("CHARACTER_ENCOUNTERED".equals(type) || "CHARACTER_REUNION".equals(type)) {
          return capitalize(event.optString("subject", "một người")) + " xuất hiện phía trước "
              + event.optString("actor", "Cao Minh") + ".";
        }
      }
    }
    JSONObject environment = frame.optJSONObject("environment");
    String motif = environment == null ? "" : environment.optString("motif", "").trim();
    if (!motif.isEmpty()) return capitalize(motif);
    String sensory = environment == null ? "" : environment.optString("sensoryCue", "").trim();
    if (!sensory.isEmpty()) return capitalize(sensory);
    return "Cao Minh quan sát khu vực hiện tại.";
  }

  private static JSONObject copyObject(JSONObject source, String key) throws Exception {
    JSONObject value = source == null ? null : source.optJSONObject(key);
    return value == null ? new JSONObject() : new JSONObject(value.toString());
  }

  private static JSONArray copyArray(JSONObject source, String key) throws Exception {
    JSONArray value = source == null ? null : source.optJSONArray(key);
    return value == null ? new JSONArray() : new JSONArray(value.toString());
  }

  private static String capitalize(String value) {
    String text = value == null ? "" : value.trim();
    if (text.isEmpty()) return text;
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
