package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Presentation only: consumes projected events; it cannot inspect or mutate mechanics. */
public final class OfflinePresenter {
  @FunctionalInterface public interface Provider { JSONObject generate() throws Exception; }

  private OfflinePresenter() {}

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
    return new JSONObject().put("reply", Character.toUpperCase(actor.charAt(0)) + actor.substring(1)
        + " quan sát khu vực trước mặt.").put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray()).put("claims", new JSONArray());
  }

  public static JSONObject present(JSONArray views, Provider provider) throws Exception {
    if (!isOffline(views)) return provider.generate();
    StringBuilder reply = new StringBuilder();
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
          sentence = "charge".equals(style)
              ? subject + " xuất hiện " + location + " rồi lập tức lao về phía " + actor + "."
              : "approach".equals(style)
                  ? subject + " xuất hiện " + location + " và bắt đầu tiến về phía " + actor + "."
                  : "hold_distance".equals(style)
                      ? subject + " xuất hiện " + location + ", giữ khoảng cách"
                          + (held.isEmpty() ? " với " : " và hướng " + held + " về phía ") + actor + "."
                      : subject + " hiện ra " + location + ".";
          if (!subject.equals(view.optString("entityAppearance"))) {
            sentence += " Trước mắt " + actor + " là " + view.optString("entityAppearance") + ".";
          }
          JSONArray details = view.optJSONArray("details");
          if (details != null) for (int j = 0; j < details.length(); j++) {
            String detail = details.optString(j, "").trim();
            if (!detail.isEmpty()) sentence += " " + detail;
          }
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
      }
      if (!sentence.isEmpty()) {
        if (reply.length() > 0) reply.append(' ');
        reply.append(Character.toUpperCase(sentence.charAt(0))).append(sentence.substring(1));
      }
    }
    if (reply.length() == 0) return fallback(views);
    return new JSONObject().put("reply", reply.toString()).put("choices", new JSONArray())
        .put("encounterDialogue", new JSONArray()).put("claims", new JSONArray());
  }
}
