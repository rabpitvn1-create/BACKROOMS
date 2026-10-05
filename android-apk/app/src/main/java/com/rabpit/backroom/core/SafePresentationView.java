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
      {"firearm", "súng", "một vật kim loại dài", "firearm", "rifle", "riflewoman", "rifleman", "gun", "gunblade", "pistol", "bullet", "bullets", "khẩu súng", "súng", "đạn"},
      {"laser", "laser", "một điểm sáng", "laser"},
      {"cao_minh_title", "Đại Đạo Ma Tôn", "người đàn ông", "Đại Đạo Ma Tôn"},
      {"cultivation", "tu tiên", "những khả năng khác thường", "tu tiên", "xianxia", "cultivation", "tu sĩ", "kiếm tu", "linh lực", "thần thức"},
      {"cao_minh", "Cao Minh", "người đàn ông", "Cao Minh"},
      {"luc_tram", "Lục Trầm", "người phụ nữ cầm kiếm", "Lục Trầm"},
      {"lucia", "Lucia Lục", "cô gái cầm một vật kim loại dài", "Lucia Lục", "Lucia", "Hứa Thuý Mai", "Hứa Thúy Mai"},
      {"syvial", "Syvial", "người phụ nữ mang thanh kiếm lớn", "Syvial"},
      {"hound", "Hound", "sinh vật bò bằng bốn chi", "Hound"},
      {"the_lifeform_bacteria_01", "Bacterial Stalker", "sinh vật hình người cao gầy bằng những sợi đen", "Bacterial Stalker", "The Lifeform Bacteria 01"},
      {"the_lifeform_bacteria_02", "Bacterial Strider", "sinh vật hình người chân dài bằng những sợi đen", "Bacterial Strider", "The Lifeform Bacteria 02"},
      {"the_lifeform_bacteria_03", "Bacterial Weaver", "sinh vật hình người phủ mạng sợi đen", "Bacterial Weaver", "The Lifeform Bacteria 03"},
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
      {"async_member_rifle_aim_right_01", "Research Async Member", "bóng người mặc đồ bảo hộ vàng đang ngắm một vật kim loại dài", "Research Async Member", "Research ASYNC Member"},
      {"copx", "CopX", "bóng người", "CopX"},
      {"tam_ma_cao_minh", "Evil Clown", "gã hề mặt trắng, mũi đỏ, mặc đồ đỏ–xanh và cầm lưỡi liềm cong", "Evil Clown", "Tâm Ma Cao Minh"},
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

  private static String canonicalLabel(String subject) {
    for (String[] row : SUBJECTS) if (row[0].equals(subject)) return row[1];
    return "Entity";
  }

  /** Redact all channels after composition, including keys/IDs and retrieved or recent text. */
  public static String text(JSONObject state, String actor, String raw) {
    if (raw == null) return "";
    List<String[]> replacements = new ArrayList<>();
    for (String[] row : SUBJECTS) {
      String visible = label(state, actor, row[0]);
      replacements.add(new String[] {row[0], visible, ""}); // Internal IDs are never narrator labels.
      if (!CharacterKnowledge.knows(state, actor, row[0], "knownName")) {
        for (int i = 3; i < row.length; i++) replacements.add(new String[] {row[i], visible, "(?iu)"});
      }
    }
    replacements.sort(Comparator.comparingInt((String[] row) -> row[0].length()).reversed());
    String result = raw;
    for (String[] pair : replacements) {
      result = Pattern.compile(pair[2] + "(?<![\\p{L}\\p{N}])" + Pattern.quote(pair[0])
          + "(?![\\p{L}\\p{N}])").matcher(result)
          .replaceAll(java.util.regex.Matcher.quoteReplacement(pair[1]));
    }
    return result;
  }

  /** Shared scene context cannot teach a foreign actor these managed lore terms. */
  public static String narrativeText(JSONObject state, String raw) {
    String result = text(state, "cao_minh", raw);
    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party != null) for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member != null && !member.optBoolean("present", true)) continue;
      String actor = member == null ? "" : member.optString("id", "");
      for (String subject : new String[] {"cao_minh_title", "cultivation"}) {
        if (CharacterKnowledge.knows(state, actor, subject, "knownName")) continue;
        for (String[] row : SUBJECTS) if (row[0].equals(subject)) {
          for (int j = 3; j < row.length; j++) result = Pattern.compile("(?iu)" + Pattern.quote(row[j]))
              .matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(row[2]));
        }
      }
    }
    return result;
  }

  public static JSONObject evidence(JSONObject state, JSONObject evidence) throws Exception {
    return (JSONObject) value(state, "cao_minh", evidence);
  }

  static boolean leaks(JSONObject state, String prose) {
    String text = prose == null ? "" : prose;
    // Vocabulary and imagery are not proof of authority violation. Only locked names and actual secrets are hard gates.
    for (String[] row : SUBJECTS) {
      if ("firearm".equals(row[0]) || "laser".equals(row[0]) || "cultivation".equals(row[0])) continue;
      if (CharacterKnowledge.knows(state, "cao_minh", row[0], "knownName")) continue;
      for (int i = 3; i < row.length; i++) {
        if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(row[i])
            + "(?![\\p{L}\\p{N}])").matcher(text).find()) return true;
      }
    }
    for (String sentence : text.toLowerCase(java.util.Locale.ROOT).split("(?<=[.!?;])\\s+|\\n+")) {
      if (sentence.contains("chưa biết") || sentence.contains("có thể") || sentence.contains("giả thuyết")
          || sentence.contains("không phải") || sentence.contains("không biết")) continue;
      if (sentence.matches("(?s).*linh khí.{0,30}(?:đầu độc|gây ra (?:điên|ảo giác|mất trí)|có nguồn gốc từ|bắt nguồn từ).*")) return true;
      if (sentence.matches("(?s).*humanoid.{0,30}(?:vốn là người|từng là người|đều là người).*")) return true;
      if (sentence.matches("(?s).*thuốc giải.{0,20}linh khí.*")) return true;
    }
    return false;
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

  /** Core permits only the present NPC's own managed name, never equipment or foreign lore. */
  static JSONArray identityDisclosureNames(JSONObject state) throws Exception {
    JSONArray allowed = new JSONArray();
    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party == null) return allowed;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !member.optBoolean("present", true)) continue;
      String id = member.optString("id", "");
      if (id.isEmpty() || "cao_minh".equals(id)) continue;
      for (String[] row : SUBJECTS) if (id.equals(row[0])) {
        JSONArray names = new JSONArray();
        for (int j = 3; j < row.length; j++) names.put(row[j]);
        allowed.put(new JSONObject().put("speaker", id).put("names", names));
      }
    }
    return allowed;
  }

  /** Preserve explicit self-disclosure only inside its spoken sentence; project everything else. */
  static JSONObject presentation(JSONObject state, JSONObject entry, String sourceEvent) throws Exception {
    JSONObject out = (JSONObject) value(state, "cao_minh", entry);
    String raw = entry.optString("text", "");
    java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
    JSONArray allowed = identityDisclosureNames(state);
    for (int i = 0; i < allowed.length(); i++) {
      JSONObject speaker = allowed.getJSONObject(i);
      JSONArray aliases = speaker.getJSONArray("names");
      for (int j = 0; j < aliases.length(); j++) {
        names.put(aliases.getString(j), speaker.getString("speaker"));
      }
    }
    if (names.isEmpty()) return out;
    StringBuilder alternatives = new StringBuilder();
    for (String name : names.keySet()) {
      if (alternatives.length() > 0) alternatives.append('|');
      alternatives.append(Pattern.quote(name));
    }
    // ponytail: finite quoted self-introduction forms over Core-managed aliases, not free prose inference.
    String sentence = "\\s*(?:Tôi là|Tôi tên là|Tên tôi là)\\s+(" + alternatives + ")[.!]\\s*";
    java.util.regex.Matcher spoken = Pattern.compile("(?:“" + sentence + "”|\"" + sentence + "\")")
        .matcher(raw);
    StringBuilder projected = new StringBuilder();
    java.util.Set<String> disclosed = new java.util.LinkedHashSet<>();
    int end = 0;
    while (spoken.find()) {
      projected.append(text(state, "cao_minh", raw.substring(end, spoken.start())));
      projected.append(spoken.group());
      String name = spoken.group(1) == null ? spoken.group(2) : spoken.group(1);
      disclosed.add(names.get(name));
      end = spoken.end();
    }
    projected.append(text(state, "cao_minh", raw.substring(end)));
    out.put("text", projected.toString());
    // The facade's validity gate and persistence lock make disclosure and log append one commit.
    for (String subject : disclosed) {
      CharacterKnowledge.mark(state, "cao_minh", subject, "knownName", sourceEvent);
    }
    return out;
  }

  public static JSONArray events(JSONObject state, String actor, JSONObject evidence) throws Exception {
    return events(state, actor, evidence, null);
  }

  static JSONArray events(JSONObject state, String actor, JSONObject evidence, EntityCore entities)
      throws Exception {
    JSONArray output = new JSONArray();
    JSONArray source = evidence == null ? null : evidence.optJSONArray("events");
    if (source != null) for (int i = 0; i < source.length(); i++) {
      JSONObject item = source.optJSONObject(i);
      if (item == null) continue;
      JSONObject view = event(state, actor, item);
      String type = item.optString("eventType", "");
      if (entities != null && ("ENTITY_ENCOUNTER_STARTED".equals(type) || "COMBAT_VICTORY".equals(type))) {
        JSONArray refs = item.optJSONArray("targetRefs");
        String subject = refs == null ? "" : refs.optString(0, "");
        JSONObject data = entities.presentation(subject);
        String appearance = text(state, actor, data.optString("appearance", "")).trim();
        if (!appearance.isEmpty()) {
          view.put("entityAppearance", appearance);
          if ("ENTITY_ENCOUNTER_STARTED".equals(type)
              && !CharacterKnowledge.knows(state, actor, subject, "knownName")) {
            view.put("subject", appearance);
          }
          view.put("heldObject", text(state, actor, data.optString("heldObject", "")));
          view.put("details", value(state, actor, data.optJSONArray("details") == null
              ? new JSONArray() : data.optJSONArray("details")));
          view.put("approachStyle", data.optString("approachStyle", "emerge"));
          String level = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
          view.put("entityLocation", "0".equals(level) ? "ở cuối dãy hành lang vàng"
              : "1".equals(level) ? "bên cạnh một cột bê tông" : "phía trước");
        }
      }
      output.put(view);
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
        .put("subject", "COMBAT_VICTORY".equals(type)
            ? canonicalLabel(subject) : label(state, actor, subject));
    if ("ENTITY_ENCOUNTER_STARTED".equals(type)) {
      view.put("approachStyle",
          ("async_rifleman".equals(subject) || "async_member_rifle_aim_right_01".equals(subject))
              ? "hold_distance"
              : "hound".equals(subject) ? "charge" : "emerge");
    }
    if ("PLAYER_ACTION_RESOLVED".equals(type)) {
      String action = event.optString("factValue", "").trim();
      if (action.isEmpty() && params != null) action = params.optString("factValue", "").trim();
      if (!action.isEmpty()) view.put("action", text(state, actor, action));
    }
    if ("CHARACTER_ENCOUNTERED".equals(type) || "CHARACTER_REUNION".equals(type)) {
      view.put("introDetail", "lucia".equals(subject) ? "Trong tay cô là một vật kim loại dài."
          : "syvial".equals(subject) ? "Cô mang theo một thanh kiếm lớn."
          : "Tay cô giữ một thanh kiếm.");
    }
    String level = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    view.put("chestLocation", "0".equals(level) ? "sát chân tường trên lớp thảm ẩm màu vàng"
        : "1".equals(level) ? "bên cạnh một cột bê tông" : "trong khu vực hiện tại");
    if ("CHEST_OPENED".equals(type)) {
      // Committed narration evidence flattens factValue; retain raw-event compatibility.
      String loot = event.optString("factValue", "").trim();
      if (loot.isEmpty() && params != null) loot = params.optString("factValue", "").trim();
      view.put("loot", text(state, actor, loot.isEmpty() ? "một vật phẩm" : loot));
    }
    return view;
  }
}
