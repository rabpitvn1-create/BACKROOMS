package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Local scene fact adapter plus deterministic emergency rendering. It never owns mechanics. */
public final class OfflinePresenter {
  @FunctionalInterface public interface Provider { JSONObject generate() throws Exception; }

  private OfflinePresenter() {}

  public static JSONObject sceneFacts(JSONObject state, String playerIntent, JSONArray views) throws Exception {
    JSONObject facts = new JSONObject();
    facts.put("playerIntent", new JSONObject()
        .put("text", playerIntent == null ? "" : playerIntent.trim())
        .put("authority", "INTENT_ONLY"));

    JSONArray present = new JSONArray().put(new JSONObject()
        .put("id", "cao_minh").put("label", "Cao Minh").put("role", "PLAYER"));
    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member == null || !member.optBoolean("present", true)) continue;
        String id = member.optString("id", "").trim();
        if (id.isEmpty()) continue;
        present.put(new JSONObject()
            .put("id", id)
            .put("label", SafePresentationView.label(state, "cao_minh", id))
            .put("role", "PARTY"));
      }
    }
    facts.put("presentCharacters", present);

    JSONArray pendingIntro = new JSONArray();
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    if (pending != null) for (int i = 0; i < pending.length(); i++) {
      String id = pending.optString(i, "").trim();
      if (!id.isEmpty()) pendingIntro.put(id);
    }
    facts.put("pendingIntro", pendingIntro);

    JSONArray world = new JSONArray();
    JSONArray entities = new JSONArray();
    JSONArray chests = new JSONArray();
    JSONArray characters = new JSONArray();
    JSONArray routes = new JSONArray();
    JSONArray specials = new JSONArray();
    String coreAction = "";

    if (views != null) for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view == null) continue;
      JSONObject copy = new JSONObject(view.toString());
      String type = copy.optString("eventType", "");
      if ("PLAYER_ACTION_RESOLVED".equals(type)) {
        coreAction = copy.optString("action", "").trim();
        continue;
      }
      world.put(copy);
      if ("ENTITY_ENCOUNTER_STARTED".equals(type) || type.startsWith("COMBAT_")) entities.put(copy);
      else if (type.startsWith("CHEST_")) chests.put(copy);
      else if (type.startsWith("CHARACTER_")) characters.put(copy);
      else if (type.startsWith("ROUTE_") || "LEVEL_TRANSITIONED".equals(type)) routes.put(copy);
      else specials.put(copy);
    }

    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    facts.put("coreAction", coreAction)
        .put("worldFacts", world)
        .put("entityEvents", entities)
        .put("chestEvents", chests)
        .put("characterEvents", characters)
        .put("routeEvents", routes)
        .put("specialEvents", specials)
        .put("chestPresent", flags != null && flags.optBoolean("chestPresent", false));
    return facts;
  }

  public static JSONObject fallback(JSONObject sceneFrame) throws Exception {
    String reply = sceneFrame == null ? "" : sceneFrame.optString("fallbackSummary", "").trim();
    if (reply.isEmpty()) reply = "Cao Minh quan sát khu vực hiện tại.";
    return new JSONObject().put("reply", reply);
  }

  public static boolean isCoreOwnedEntityLifecycle(JSONArray views) {
    if (views == null) return false;
    for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view == null) continue;
      String type = view.optString("eventType", "");
      if ("ENTITY_ENCOUNTER_STARTED".equals(type) || "COMBAT_VICTORY".equals(type)
          || "COMBAT_DEFEAT".equals(type)) return true;
    }
    return false;
  }

  public static boolean isOffline(JSONArray views) {
    if (views == null) return false;
    for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view == null) continue;
      String type = view.optString("eventType", "");
      if ("ENTITY_ENCOUNTER_STARTED".equals(type) || "COMBAT_VICTORY".equals(type)
          || "CHEST_SPAWNED".equals(type) || "CHEST_OPENED".equals(type)
          || "CHARACTER_ENCOUNTERED".equals(type) || "CHARACTER_REUNION".equals(type)) return true;
    }
    return false;
  }

  /** Provider is deliberately injectable: a poison provider proves zero calls on offline paths. */
  public static JSONObject fallback(JSONArray views) throws Exception {
    String actor = "người lữ hành";
    if (views != null) for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view != null && !view.optString("actor", "").trim().isEmpty()) {
        actor = view.optString("actor").trim();
        break;
      }
    }
    String action = "";
    if (views != null) for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view != null && !view.optString("action", "").trim().isEmpty()) {
        action = view.optString("action").trim();
        break;
      }
    }
    String subject = Character.toUpperCase(actor.charAt(0)) + actor.substring(1);
    String reply = "Khám phá".equalsIgnoreCase(action)
        ? subject + " tiếp tục khám phá khu vực hiện tại."
        : subject + " quan sát khu vực trước mặt.";
    return new JSONObject().put("reply", reply).put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray()).put("claims", new JSONArray());
  }

  public static JSONObject present(JSONArray views, Provider provider) throws Exception {
    if (!isOffline(views)) return provider.generate();
    StringBuilder reply = new StringBuilder();
    JSONArray encounterDialogue = new JSONArray();
    for (int i = 0; i < views.length(); i++) {
      JSONObject view = views.optJSONObject(i);
      if (view == null) continue;
      String type = view.optString("eventType", "");
      String subject = view.optString("subject", "hình dạng phía trước");
      String sentence = "";
      if ("COMBAT_VICTORY".equals(type)) sentence = subject + " bị tiêu diệt.";
      if ("ENTITY_ENCOUNTER_STARTED".equals(type)) {
        String actor = view.optString("actor", "Cao Minh");
        String style = view.optString("approachStyle", "emerge");
        if (view.has("entityAppearance")) {
          String location = view.optString("entityLocation", "phía trước");
          String held = view.optString("heldObject", "").trim();
          String appearance = view.optString("entityAppearance");
          String reference = subject.equals(appearance) ? subject : subject + ", " + appearance + ",";
          sentence = "charge".equals(style)
              ? reference + " xuất hiện " + location + " rồi lập tức lao về phía " + actor + "."
              : "approach".equals(style)
                  ? reference + " xuất hiện " + location + " và bắt đầu tiến về phía " + actor + "."
                  : "hold_distance".equals(style)
                      ? reference + " xuất hiện " + location + ", giữ khoảng cách"
                          + (held.isEmpty() ? " với " : " và hướng " + held + " về phía ") + actor + "."
                      : reference + " hiện ra " + location + ".";
          // Keep deterministic Entity intros compact. Registry details remain available as
          // presentation/canon data, but the encounter line only needs silhouette + behavior.
        } else {
          // Keep compatibility with legacy projected views that have no registry presentation block.
          sentence = "hold_distance".equals(style)
              ? subject + " xuất hiện phía trước, giữ khoảng cách và nâng một vật kim loại dài về phía " + actor + "."
              : "charge".equals(style)
                  ? subject + " lao ra và áp sát " + actor + "."
                  : subject + " xuất hiện và chắn đường " + actor + ".";
        }
      }
      if ("CHEST_SPAWNED".equals(type)) {
        sentence = view.optString("actor", "Cao Minh") + " phát hiện một chiếc rương "
            + view.optString("chestLocation", "trong khu vực hiện tại") + ".";
      }
      if ("CHEST_OPENED".equals(type)) {
        sentence = view.optString("actor", "Cao Minh") + " mở chiếc rương. Bên trong là "
            + view.optString("loot", "một vật phẩm") + ".";
      }
      if ("CHARACTER_ENCOUNTERED".equals(type) || "CHARACTER_REUNION".equals(type)) {
        sentence = subject + " xuất hiện phía trước " + view.optString("actor", "Cao Minh") + "."
            + " " + view.optString("introDetail", "Người đó đứng trong khu vực trước mặt.");
        if (encounterDialogue.length() == 0) {
          if ("CHARACTER_REUNION".equals(type)) {
            encounterDialogue.put("Lại là anh.");
            encounterDialogue.put("Tôi không ngờ chúng ta lại gặp nhau ở đây.");
          } else {
            encounterDialogue.put("Tôi không muốn gây thêm rắc rối.");
            encounterDialogue.put("Trước tiên, chúng ta nên xác định nơi này là đâu.");
          }
        }
      }
      if (!sentence.isEmpty()) {
        if (reply.length() > 0) reply.append(' ');
        reply.append(Character.toUpperCase(sentence.charAt(0))).append(sentence.substring(1));
      }
    }
    if (reply.length() == 0) return fallback(views);
    return new JSONObject().put("reply", reply.toString()).put("choices", new JSONArray())
        .put("encounterDialogue", encounterDialogue).put("claims", new JSONArray());
  }
}
