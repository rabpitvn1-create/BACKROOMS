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
    frame.put("identityDisclosure", SafePresentationView.identityDisclosureNames(state));

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
        .put("playerIntentIsWorldFact", false)
        .put("identityDisclosureRule", "Optional: a present NPC may say their own listed name in a quoted sentence, "
            + "for example “Tôi là <name>.”. This does not reveal equipment or foreign lore. "
            + "Narrator uses the POV label until the disclosure is committed; never write Cao Minh dialogue."));

    JSONObject requiredBeat = new JSONObject().put("kind", "NONE");
    if ("ENTITY".equals(focus)) {
      requiredBeat.put("kind", "ENTITY_ENCOUNTER")
          .put("mustNarrateBeforeCombat", true)
          .put("mustUseAppearanceAndApproach", true)
          .put("compactOneLineSummaryIsInsufficient", true);
    } else if ("CHARACTER".equals(focus) || frame.getJSONArray("pendingIntro").length() > 0) {
      requiredBeat.put("kind", "CHARACTER_ENCOUNTER")
          .put("mustNarrateMeetingBeforeCompanionContinuity", true)
          .put("dialogueLinesMin", 2)
          .put("dialogueLinesMax", 5)
          .put("caoMinhDialogueAllowed", false);
    }
    frame.put("requiredBeat", requiredBeat);
    return frame;
  }

  public static String fallbackNarration(JSONObject frame) {
    if (frame == null) return "Cao Minh quan sát khu vực hiện tại.";
    String narration = environmentOpening(frame.optJSONObject("environment"));
    JSONArray world = frame.optJSONArray("worldFacts");
    if (world != null) {
      for (int i = 0; i < world.length(); i++) {
        JSONObject event = world.optJSONObject(i);
        if (event == null) continue;
        String type = event.optString("eventType", "");
        String paragraph = "";
        if ("ENTITY_ENCOUNTER_STARTED".equals(type)) {
          paragraph = entityFallback(event);
        } else if ("CHARACTER_ENCOUNTERED".equals(type) || "CHARACTER_REUNION".equals(type)) {
          paragraph = characterFallback(event, "CHARACTER_REUNION".equals(type));
        } else if ("CHEST_SPAWNED".equals(type)) {
          paragraph = event.optString("actor", "Cao Minh") + " phát hiện một chiếc rương "
              + event.optString("chestLocation", "trong khu vực hiện tại") + ".";
        } else if ("CHEST_OPENED".equals(type)) {
          paragraph = event.optString("actor", "Cao Minh") + " mở chiếc rương. Bên trong là "
              + event.optString("loot", "một vật phẩm") + ".";
        }
        narration = joinParagraphs(narration, paragraph);
      }
    }
    return narration.isEmpty() ? "Cao Minh quan sát khu vực hiện tại." : narration;
  }

  private static String environmentOpening(JSONObject environment) {
    if (environment == null) return "";
    String motif = GmChoiceContract.normalizePlayerFacingVietnamese(
        environment.optString("motif", "").trim());
    String sensory = GmChoiceContract.normalizePlayerFacingVietnamese(
        environment.optString("sensoryCue", "").trim());
    if (!motif.isEmpty() && !sensory.isEmpty()) return sentence(motif) + " " + sentence(sensory);
    if (!motif.isEmpty()) return sentence(motif);
    if (!sensory.isEmpty()) return sentence(sensory);
    return "";
  }

  private static String entityFallback(JSONObject event) {
    String appearance = GmChoiceContract.normalizePlayerFacingVietnamese(
        event.optString("entityAppearance", event.optString("subject", "một thực thể")).trim());
    String where = GmChoiceContract.normalizePlayerFacingVietnamese(
        event.optString("entityLocation", "phía trước").trim());
    String actor = event.optString("actor", "Cao Minh").trim();
    String style = event.optString("approachStyle", "emerge").trim();
    String held = GmChoiceContract.normalizePlayerFacingVietnamese(
        event.optString("heldObject", "").trim());

    StringBuilder out = new StringBuilder();
    if (!appearance.isEmpty()) {
      out.append(capitalize(appearance)).append(" xuất hiện ").append(where).append(".");
    }
    JSONArray details = event.optJSONArray("details");
    if (details != null) {
      int used = 0;
      for (int i = 0; i < details.length() && used < 2; i++) {
        String detail = GmChoiceContract.normalizePlayerFacingVietnamese(details.optString(i, "").trim());
        if (detail.isEmpty()) continue;
        out.append(' ').append(sentence(detail));
        used++;
      }
    }
    if ("hold_distance".equals(style)) {
      out.append(" Nó giữ khoảng cách");
      if (!held.isEmpty()) out.append(" và hướng ").append(held).append(" về phía ").append(actor);
      else out.append(" với ").append(actor);
      out.append(".");
    } else if ("charge".equals(style)) {
      out.append(" Nó lập tức lao về phía ").append(actor).append(".");
    } else if ("approach".equals(style) || "pursue".equals(style) || "stalk".equals(style)) {
      out.append(" Nó bắt đầu thu hẹp khoảng cách với ").append(actor).append(".");
    } else {
      out.append(" Sự hiện diện của nó chặn ngay tuyến đường phía trước.");
    }
    return out.toString().trim();
  }

  private static String characterFallback(JSONObject event, boolean reunion) {
    String subject = event.optString("subject", "một người").trim();
    String actor = event.optString("actor", "Cao Minh").trim();
    String detail = GmChoiceContract.normalizePlayerFacingVietnamese(
        event.optString("introDetail", "Người đó đứng trong khu vực trước mặt.").trim());
    StringBuilder out = new StringBuilder();
    out.append(capitalize(subject)).append(" xuất hiện trước mặt ").append(actor).append(". ");
    if (!detail.isEmpty()) out.append(sentence(detail)).append("\n\n");
    if (reunion) {
      out.append("“Lại là anh.”\n\n");
      out.append("“Tôi không ngờ chúng ta lại gặp nhau ở đây.”");
    } else {
      out.append("“Đứng yên. Tôi không muốn gây thêm rắc rối.”\n\n");
      out.append("“Trước tiên, chúng ta nên xác định nơi này là đâu.”");
    }
    return out.toString().trim();
  }

  private static String joinParagraphs(String first, String second) {
    String a = first == null ? "" : first.trim();
    String b = second == null ? "" : second.trim();
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    return a + "\n\n" + b;
  }

  private static String sentence(String value) {
    String text = value == null ? "" : value.trim();
    if (text.isEmpty()) return "";
    String normalized = capitalize(text);
    char last = normalized.charAt(normalized.length() - 1);
    return last == '.' || last == '!' || last == '?' ? normalized : normalized + ".";
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
