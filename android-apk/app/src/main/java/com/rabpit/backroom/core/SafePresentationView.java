package com.rabpit.backroom.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small content table and projector shared by local prose, provider packets and fallback. */
public final class SafePresentationView {
  // ponytail: finite managed vocabulary, not a general lore/knowledge classifier.
  private static final String[][] SUBJECTS = {
      {"lucia_m4a1", "M4A1", "một vật kim loại dài", "M4A1"},
      {"firearm", "súng", "một vật kim loại dài", "firearm", "rifle", "khẩu súng"},
      {"laser", "laser", "một điểm sáng", "laser"},
      {"cao_minh_title", "Đại Đạo Ma Tôn", "người đàn ông", "Đại Đạo Ma Tôn"},
      {"cultivation", "tu tiên", "những khả năng khác thường", "tu tiên", "xianxia"},
      {"cao_minh", "Cao Minh", "người đàn ông", "Cao Minh"},
      {"luc_tram", "Lục Trầm", "người phụ nữ cầm kiếm", "Lục Trầm"},
      {"lucia", "Lucia Lục", "cô gái cầm một vật kim loại dài", "Lucia Lục", "Lucia", "Hứa Thuý Mai", "Hứa Thúy Mai"},
      {"syvial", "Syvial", "người phụ nữ mang thanh kiếm lớn", "Syvial"},
      {"hound", "Hound", "sinh vật bò bằng bốn chi", "Hound"},
      {"clump", "Clump", "khối thịt với nhiều chi", "Clump"},
      {"duller", "Duller", "bóng người gầy gò", "Duller"},
      {"deathmoth", "Deathmoth", "con bướm lớn", "Deathmoth"},
      {"hostile_faceling", "Hostile Faceling", "bóng người không có khuôn mặt", "Hostile Faceling", "Faceling"},
      {"false_puddle", "False Puddle", "vũng chất lỏng", "False Puddle"},
      {"paintings", "Paintings", "hình dạng trong bức tranh", "Paintings"},
      {"smiler", "Smiler", "nụ cười sáng trong bóng tối", "Smiler"},
      {"skin-stealer", "Skin-Stealer", "bóng người với lớp da bất thường", "Skin-Stealer"},
      {"predatory_window", "Predatory Window", "hình dạng bên cửa sổ", "Predatory Window"},
      {"biological_pipeline", "Biological Pipeline", "đường ống phủ mô thịt", "Biological Pipeline"},
      {"wretch", "Wretch", "bóng người méo mó", "Wretch"},
      {"cable_mimic", "Cable Mimic", "khối dây cáp chuyển động", "Cable Mimic"},
      {"the_beast_of_level_5", "The Beast of Level 5", "sinh vật lớn", "The Beast of Level 5"},
      {"hotel_corpse_lure", "Hotel Corpse Lure", "hình người bất động", "Hotel Corpse Lure"},
      {"jeff_the_killer", "Jeff the Killer", "bóng người cầm dao", "Jeff the Killer"},
      {"async_rifleman", "ASYNC Rifleman", "bóng người mặc trang bị kín người", "ASYNC Rifleman", "ASYNC"},
      {"copx", "CopX", "bóng người", "CopX"},
      {"tam_ma_cao_minh", "Tâm Ma Cao Minh", "bóng người có diện mạo giống Cao Minh", "Tâm Ma Cao Minh"},
      {"diep_minh", "Diệp Minh", "bóng người", "Diệp Minh"},
      {"jane_the_killer", "Jane", "bóng người", "Jane the Killer", "Jane"},
      {"slenderman", "Slenderman", "bóng người cao gầy", "Slenderman"}
  };

  private SafePresentationView() {}

  public static String label(JSONObject state, String actor, String subject) {
    for (String[] row : SUBJECTS) {
      if (!row[0].equals(subject)) continue;
      if (CharacterKnowledge.knows(state, actor, subject, "knownName")) return row[1];
      if ("lucia_m4a1".equals(subject)
          && CharacterKnowledge.knows(state, actor, subject, "knownEffect")) {
        return "một loại vũ khí phóng vật nhỏ với tốc độ rất cao";
      }
      return row[2];
    }
    return "hình dạng phía trước";
  }

  /** Redact all channels after composition, including keys/IDs and retrieved or recent text. */
  public static String text(JSONObject state, String actor, String raw) {
    if (raw == null) return "";
    List<String[]> replacements = new ArrayList<>();
    for (String[] row : SUBJECTS) {
      String visible = label(state, actor, row[0]);
      replacements.add(new String[] {row[0], visible}); // Internal IDs are never narrator labels.
      if (!CharacterKnowledge.knows(state, actor, row[0], "knownName")) {
        for (int i = 3; i < row.length; i++) replacements.add(new String[] {row[i], visible});
      }
    }
    replacements.sort(Comparator.comparingInt((String[] row) -> row[0].length()).reversed());
    String result = raw;
    for (String[] pair : replacements) {
      result = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(pair[0])
          + "(?![\\p{L}\\p{N}])").matcher(result)
          .replaceAll(java.util.regex.Matcher.quoteReplacement(pair[1]));
    }
    return result;
  }

  static Object value(JSONObject state, String actor, Object raw) throws Exception {
    if (raw instanceof JSONObject) {
      JSONObject source = (JSONObject) raw, out = new JSONObject();
      Iterator<String> keys = source.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        out.put(text(state, actor, key), value(state, actor, source.get(key)));
      }
      return out;
    }
    if (raw instanceof JSONArray) {
      JSONArray source = (JSONArray) raw, out = new JSONArray();
      for (int i = 0; i < source.length(); i++) out.put(value(state, actor, source.get(i)));
      return out;
    }
    return raw instanceof String ? text(state, actor, (String) raw) : raw;
  }

  public static JSONArray events(JSONObject state, String actor, JSONObject evidence) throws Exception {
    JSONArray output = new JSONArray();
    JSONArray source = evidence == null ? null : evidence.optJSONArray("events");
    if (source != null) for (int i = 0; i < source.length(); i++) {
      JSONObject item = source.optJSONObject(i);
      if (item != null) output.put(event(state, actor, item));
    }
    return output;
  }

  /** No raw identity, inventory internals, objective HUD or lore enters the offline presenter. */
  public static JSONObject event(JSONObject state, String actor, JSONObject event) throws Exception {
    String type = event.optString("eventType", "");
    JSONArray refs = event.optJSONArray("targetRefs");
    JSONObject params = event.optJSONObject("params");
    String subject = refs == null ? "" : refs.optString(0, "");
    JSONObject view = new JSONObject().put("eventType", type)
        .put("eventId", event.optString("eventId", ""))
        .put("actor", label(state, actor, actor))
        .put("subject", label(state, actor, subject));
    if ("ENTITY_ENCOUNTER_STARTED".equals(type)) {
      view.put("approachStyle", "async_rifleman".equals(subject) ? "hold_distance"
          : "hound".equals(subject) ? "charge" : "emerge");
    }
    String level = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    view.put("chestLocation", "0".equals(level) ? "sát chân tường trên lớp thảm ẩm màu vàng"
        : "1".equals(level) ? "bên cạnh một cột bê tông" : "trong khu vực hiện tại");
    if ("CHEST_OPENED".equals(type) && params != null) {
      view.put("loot", text(state, actor, params.optString("factValue", "vật phẩm")));
    }
    return view;
  }
}
