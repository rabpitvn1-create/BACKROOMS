package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sanitizes and deterministically enriches Explorer choice/highlight projection before WebView. */
public final class GmChoiceContract {
  private static final int MAX_CHOICES = 3;
  private static final int MAX_CHOICE_TEXT = 180;
  private static final int MAX_HIGHLIGHTS = 24;
  private static final int MAX_HIGHLIGHT_TEXT = 120;

  private static final Pattern LEVEL_PATTERN =
      Pattern.compile("(?iu)\\bLevel\\s+\\d+(?:\\s*[-–—/]\\s*[A-Za-zÀ-ỹ0-9 _]+)?");
  private static final Pattern HP_PAIR_PATTERN =
      Pattern.compile("(?iu)(?:\\bHP\\s*\\d+\\s*/\\s*\\d+\\b|\\b\\d+\\s*/\\s*\\d+\\s*HP\\b)");
  private static final Pattern SIGNED_STAT_PATTERN =
      Pattern.compile("(?iu)[+-]\\d+(?:\\.\\d+)?%?\\s*(?:HP|DEF)\\b");
  private static final Pattern SECOND_PERSON_NARRATION_PATTERN =
      Pattern.compile("(?iu)(?<!người )(?<![\\p{L}\\p{N}_])bạn(?!\\s+(?:bè|đồng hành)\\b)(?![\\p{L}\\p{N}_])");

  private static final String[][] FIXED_TERMS = {
      {"Cao Minh", "character"},
      {"Vạn Giới Ma Tôn", "character"},
      {"Syvial", "character"},
      {"Lục Trầm", "character"},
      {"Backrooms", "location"},
      {"Explorer", "stat"},
      {"EXP", "stat"},
      {"MaxHP", "stat"},
      {"HP", "stat"},
      {"STR", "stat"},
      {"DF", "stat"},
      {"DEF", "stat"},
      {"AGI", "stat"},
      {"CRIT", "stat"},
      {"Base Stats", "stat"},
      {"Effective Stats", "stat"}
  };

  // Backstage technical environment glossary. Serves as a last-resort safety net
  // preventing internal English environment keys from leaking into player UI.
  private static final String[][] VIETNAMESE_ENVIRONMENT_TERMS = {
      {"blackout zones", "các vùng mất sáng"},
      {"blackout zone", "vùng mất sáng"},
      {"pillar rooms", "các phòng cột"},
      {"pillar room", "phòng cột"},
      {"arch rooms", "các phòng vòm"},
      {"arch room", "phòng vòm"},
      {"hole fields", "các bãi hố"},
      {"hole field", "bãi hố"},
      {"transition chambers", "các khoang chuyển tiếp"},
      {"transition chamber", "khoang chuyển tiếp"},
      {"dead ends", "các ngõ cụt"},
      {"dead end", "ngõ cụt"},
      {"floor recesses", "các chỗ trũng trên sàn"},
      {"floor recess", "chỗ trũng trên sàn"},
      {"noise spikes", "các đợt tăng tiếng ồn"},
      {"noise spike", "đợt tăng tiếng ồn"},
      {"stable pockets", "các vùng ổn định"},
      {"stable pocket", "vùng ổn định"},
      {"dry patches", "các mảng khô"},
      {"dry patch", "mảng khô"},
      {"synthetic carpet", "thảm sợi tổng hợp"},
      {"supernatural voice", "giọng nói siêu nhiên"},
      {"layout shift", "biến đổi bố cục"},
      {"wallpaper", "giấy dán tường"},
      {"ceilings", "trần nhà"},
      {"ceiling", "trần nhà"},
      {"fixtures", "các bộ đèn"},
      {"fixture", "bộ đèn"},
      {"openings", "các lối mở"},
      {"opening", "lối mở"},
      {"corridors", "các hành lang"},
      {"corridor", "hành lang"},
      {"junctions", "các giao lộ"},
      {"junction", "giao lộ"},
      {"landmarks", "các mốc định hướng"},
      {"landmark", "mốc định hướng"},
      {"carpet", "thảm"},
      {"fluids", "các chất lỏng"},
      {"fluid", "chất lỏng"},
      {"layouts", "các bố cục"},
      {"layout", "bố cục"},
      {"grids", "các lưới"},
      {"grid", "lưới"},
      {"mildew", "nấm mốc"},
      {"buzz", "tiếng ù"},
      {"cues", "các dấu hiệu"},
      {"cue", "dấu hiệu"},
      {"topology", "cấu trúc không gian"},
      {"fluorescent", "huỳnh quang"},
      {"chemical", "hóa chất"},
      {"pillar hall", "sảnh cột"},
      {"pillar", "cột"},
      {"hall", "sảnh"},
      {"hallway", "hành lang"},
      {"chamber", "phòng"},
      {"patch", "mảng"},
      {"section", "đoạn"},
      {"blackout", "mất sáng"},
      {"baseline", "mức bình thường"},
      {"staircase", "cầu thang"},
      {"crawlspace", "lối bò thấp"},
      {"haze", "màn mờ"},
      {"pale", "nhạt màu"},
      {"tile", "tấm trần"},
      {"familiar", "quen thuộc"},
      {"risk", "nguy cơ"},
      {"outlet", "ổ điện"},
      {"flicker", "chớp tắt"}
  };

  // Choice-only cleanup for common English action/navigation leakage. Official names are left intact.
  private static final String[][] VIETNAMESE_CHOICE_TERMS = {
      {"look around", "quan sát xung quanh"},
      {"move forward", "tiến về phía trước"},
      {"go forward", "tiến về phía trước"},
      {"turn back", "quay lại"},
      {"keep going", "tiếp tục đi"},
      {"search the area", "tìm kiếm quanh khu vực"},
      {"through the", "qua"},
      {"toward the", "về phía"},
      {"towards the", "về phía"},
      {"into the", "vào"},
      {"near the", "gần"},
      {"investigate", "khảo sát"},
      {"inspect", "kiểm tra"},
      {"examine", "xem xét"},
      {"search", "tìm kiếm"},
      {"follow", "đi theo"},
      {"approach", "tiến lại gần"},
      {"enter", "đi vào"},
      {"open", "mở"},
      {"listen", "lắng nghe"},
      {"observe", "quan sát"},
      {"explore", "khám phá"},
      {"check", "kiểm tra"},
      {"wait", "chờ"},
      {"hide", "ẩn nấp"},
      {"run", "chạy"},
      {"walk", "đi"},
      {"move", "di chuyển"},
      {"doorway", "lối cửa"},
      {"hallway", "hành lang"},
      {"passage", "lối thông"},
      {"door", "cánh cửa"},
      {"room", "căn phòng"},
      {"path", "lối đi"},
      {"wall", "bức tường"},
      {"light", "ánh sáng"},
      {"nearby", "gần đó"},
      {"ahead", "phía trước"},
      {"behind", "phía sau"},
      {"forward", "phía trước"},
      {"through", "qua"},
      {"towards", "về phía"},
      {"toward", "về phía"},
      {"around", "xung quanh"},
      {"then", "rồi"},
      {"and", "và"},
      {"or", "hoặc"}
  };

  private GmChoiceContract() {}

  public static JSONObject gmEntry(String reply, JSONObject generated) throws Exception {
    return gmEntry(reply, generated, null);
  }

  public static JSONObject gmEntry(String reply, JSONObject generated, JSONObject state) throws Exception {
    String text = normalizeNarrationPerspective(normalizePlayerFacingVietnamese(reply));
    JSONObject entry = new JSONObject().put("role", "gm").put("text", text);
    JSONArray highlights = deterministicHighlights(
        text, state, generated == null ? null : generated.optJSONArray("highlights"));
    if (highlights.length() > 0) entry.put("highlights", highlights);
    JSONArray choices = sanitizeChoices(
        generated == null ? null : generated.optJSONArray("choices"), state);
    if (choices.length() > 0) entry.put("choices", choices);
    return entry;
  }


  public static String mergeEncounterDialogue(String reply, JSONArray dialogue) {
    String base = reply == null ? "" : reply.trim();
    if (dialogue == null || dialogue.length() == 0) return base;

    StringBuilder output = new StringBuilder(base);
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < dialogue.length(); i++) {
      String line = dialogue.optString(i, "").trim();
      String key = dialogueDedupKey(line);
      if (key.isEmpty() || !seen.add(key) || replyAlreadyContainsDialogue(base, line, key)) continue;
      if (output.length() > 0) output.append("\n\n");
      output.append(quoteDialogueLine(line));
    }
    return output.toString();
  }

  public static String normalizeNarrationPerspective(String input) {
    String text = input == null ? "" : input.trim();
    if (text.isEmpty()) return text;

    StringBuilder output = new StringBuilder();
    StringBuilder plain = new StringBuilder();
    boolean curlyQuote = false;
    boolean asciiQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (current == '“' && !asciiQuote && !curlyQuote) {
        output.append(rewriteNarrationSecondPerson(plain.toString()));
        plain.setLength(0);
        curlyQuote = true;
        output.append(current);
        continue;
      }
      if (current == '”' && curlyQuote) {
        output.append(current);
        curlyQuote = false;
        continue;
      }
      if (current == '"' && !curlyQuote) {
        if (asciiQuote) {
          output.append(current);
          asciiQuote = false;
        } else {
          output.append(rewriteNarrationSecondPerson(plain.toString()));
          plain.setLength(0);
          asciiQuote = true;
          output.append(current);
        }
        continue;
      }
      if (curlyQuote || asciiQuote) output.append(current);
      else plain.append(current);
    }
    if (plain.length() > 0) output.append(rewriteNarrationSecondPerson(plain.toString()));
    return output.toString();
  }

  private static String rewriteNarrationSecondPerson(String value) {
    Matcher matcher = SECOND_PERSON_NARRATION_PATTERN.matcher(value == null ? "" : value);
    StringBuffer normalized = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(normalized, Matcher.quoteReplacement("Cao Minh"));
    }
    matcher.appendTail(normalized);
    return normalized.toString();
  }

  private static boolean replyAlreadyContainsDialogue(String reply, String line, String key) {
    if (reply == null || reply.trim().isEmpty()) return false;
    String lowerReply = reply.toLowerCase(Locale.ROOT);
    String lowerLine = line.toLowerCase(Locale.ROOT);
    if (lowerReply.contains("\"" + lowerLine + "\"")
        || lowerReply.contains("“" + lowerLine + "”")
        || lowerReply.contains("‘" + lowerLine + "’")
        || lowerReply.contains(": " + lowerLine)) {
      return true;
    }
    for (String paragraph : reply.split("\\r?\\n+")) {
      if (key.equals(dialogueDedupKey(paragraph))) return true;
    }
    return false;
  }

  private static String quoteDialogueLine(String line) {
    if ((line.startsWith("“") && line.endsWith("”"))
        || (line.startsWith("\"") && line.endsWith("\""))) return line;
    return "“" + line + "”";
  }

  private static String dialogueDedupKey(String value) {
    return (value == null ? "" : value).toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }

  public static JSONArray sanitizeChoices(JSONArray input) throws Exception {
    return sanitizeChoices(input, null);
  }

  static JSONArray sanitizeChoices(JSONArray input, JSONObject state) throws Exception {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    for (int i = 0; i < input.length() && output.length() < MAX_CHOICES; i++) {
      Object raw = input.opt(i);
      String text = "";
      JSONArray rawHighlights = null;
      if (raw instanceof JSONObject) {
        JSONObject object = (JSONObject) raw;
        text = object.optString("text", object.optString("action", "")).trim();
        rawHighlights = object.optJSONArray("highlights");
      } else if (raw != null) {
        text = String.valueOf(raw).trim();
      }
      text = normalizeChoiceVietnamese(text);
      if (text.isEmpty()) continue;
      if (text.length() > MAX_CHOICE_TEXT) text = text.substring(0, MAX_CHOICE_TEXT).trim();
      JSONObject choice = new JSONObject()
          .put("id", String.valueOf((char)('A' + output.length())))
          .put("text", text)
          .put("action", text);
      JSONArray highlights = deterministicHighlights(text, state, rawHighlights);
      if (highlights.length() > 0) choice.put("highlights", highlights);
      output.put(choice);
    }
    return output;
  }

  public static String defaultCoreAction(JSONObject state) {
    if (state != null) {
      JSONObject flags = state.optJSONObject("flags");
      if (flags != null && flags.optBoolean("chestPresent", false)) {
        return ItemCore.OPEN_CHEST_ACTION;
      }
      JSONObject route = state.optJSONObject(LevelCore.ROUTE_STATE);
      if (route != null && route.optBoolean("exitAvailable", false)) {
        return "Đi qua ranh giới";
      }
    }
    return "Khám phá";
  }

  public static String normalizePlayerFacingVietnamese(String input) {
    String output = input == null ? "" : input.trim();
    if (output.isEmpty()) return output;
    for (String[] term : VIETNAMESE_ENVIRONMENT_TERMS) {
      Pattern pattern = Pattern.compile(
          "(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(term[0])
              + "(?![\\p{L}\\p{N}_])");
      Matcher matcher = pattern.matcher(output);
      StringBuffer normalized = new StringBuffer();
      while (matcher.find()) {
        String replacement = term[1];
        String matched = matcher.group();
        if (!matched.isEmpty() && Character.isUpperCase(matched.codePointAt(0)) && !replacement.isEmpty()) {
          replacement = replacement.substring(0, 1).toUpperCase(Locale.ROOT) + replacement.substring(1);
        }
        matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
      }
      matcher.appendTail(normalized);
      output = normalized.toString();
    }
    return output;
  }


  public static String normalizeChoiceVietnamese(String input) {
    String output = normalizePlayerFacingVietnamese(input);
    if (output.isEmpty()) return output;
    for (String[] term : VIETNAMESE_CHOICE_TERMS) {
      Pattern pattern = Pattern.compile(
          "(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(term[0])
              + "(?![\\p{L}\\p{N}_])");
      Matcher matcher = pattern.matcher(output);
      StringBuffer normalized = new StringBuffer();
      while (matcher.find()) {
        String replacement = term[1];
        String matched = matcher.group();
        if (!matched.isEmpty() && Character.isUpperCase(matched.codePointAt(0)) && !replacement.isEmpty()) {
          replacement = replacement.substring(0, 1).toUpperCase(Locale.ROOT) + replacement.substring(1);
        }
        matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
      }
      matcher.appendTail(normalized);
      output = normalized.toString();
    }
    return output.replaceAll("\\s{2,}", " ").trim();
  }

  public static JSONArray sanitizeHighlights(JSONArray input) throws Exception {
    LinkedHashMap<String, JSONObject> output = new LinkedHashMap<>();
    addHighlightList(output, input, null, false);
    return toArray(output);
  }

  public static JSONArray semanticHighlights(String text, JSONObject state) throws Exception {
    return deterministicHighlights(text, state, null);
  }

  private static JSONArray deterministicHighlights(String text, JSONObject state, JSONArray modelHighlights)
      throws Exception {
    LinkedHashMap<String, JSONObject> output = new LinkedHashMap<>();

    // Core-owned semantic typing always runs first. Model metadata can only supplement it.
    for (String[] term : FIXED_TERMS) addIfPresent(output, text, term[0], term[1], true);
    addCatalog(output, text, CombatChoiceEngine.semanticCatalog());
    addCatalog(output, text, ItemCore.semanticCatalog());
    addStateTerms(output, text, state);
    addPatternMatches(output, text, LEVEL_PATTERN, "location");
    addPatternMatches(output, text, HP_PAIR_PATTERN, "stat");
    addPatternMatches(output, text, SIGNED_STAT_PATTERN, "stat");
    addHighlightList(output, modelHighlights, text, false);

    return toArray(output);
  }

  private static void addCatalog(LinkedHashMap<String, JSONObject> output, String source,
                                 Map<String, String> catalog) throws Exception {
    for (Map.Entry<String, String> entry : catalog.entrySet()) {
      addIfPresent(output, source, entry.getKey(), entry.getValue(), true);
    }
  }

  private static void addStateTerms(LinkedHashMap<String, JSONObject> output, String source, JSONObject state)
      throws Exception {
    if (state == null) return;

    JSONObject player = state.optJSONObject("player");
    if (player != null) addIfPresent(output, source, player.optString("name", ""), "character", true);

    JSONArray party = state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member != null) {
          addIfPresent(output, source,
              member.optString("name", member.optString("id", "")), "character", true);
        }
      }
    }

    JSONArray inventory = state.optJSONArray("inventory");
    if (inventory != null) {
      for (int i = 0; i < inventory.length(); i++) {
        JSONObject stack = inventory.optJSONObject(i);
        if (stack != null) addIfPresent(output, source, stack.optString("name", ""), "item", true);
      }
    }

    String location = state.optString("location", "").trim();
    if (!location.isEmpty()) {
      String head = location.split("—", 2)[0].trim();
      if (!head.isEmpty()) addIfPresent(output, source, head, "location", true);
    }

    JSONObject combat = state.optJSONObject("combat");
    if (combat != null) {
      addIfPresent(output, source, combat.optString("currentActor", ""), "character", true);
      JSONArray entities = combat.optJSONArray("entities");
      if (entities != null) {
        for (int i = 0; i < entities.length(); i++) {
          JSONObject entity = entities.optJSONObject(i);
          if (entity != null) addIfPresent(output, source, entity.optString("name", ""), "entity", true);
        }
      } else {
        JSONObject entity = combat.optJSONObject("entity");
        if (entity != null) addIfPresent(output, source, entity.optString("name", ""), "entity", true);
      }
      JSONObject skill = combat.optJSONObject("currentSkill");
      if (skill != null) addIfPresent(output, source, skill.optString("name", ""), "skill", true);
    }
  }

  private static void addPatternMatches(LinkedHashMap<String, JSONObject> output, String source,
                                        Pattern pattern, String type) throws Exception {
    Matcher matcher = pattern.matcher(source == null ? "" : source);
    while (matcher.find() && output.size() < MAX_HIGHLIGHTS) {
      putHighlight(output, matcher.group(), type, true);
    }
  }

  private static void addIfPresent(LinkedHashMap<String, JSONObject> output, String source,
                                   String term, String type, boolean replaceGeneric) throws Exception {
    String value = term == null ? "" : term.trim();
    if (value.length() < 2 || !containsWholeTerm(source, value)) return;
    putHighlight(output, value, type, replaceGeneric);
  }

  private static boolean containsWholeTerm(String source, String term) {
    if (source == null || source.isEmpty() || term == null || term.isEmpty()) return false;
    Pattern pattern = Pattern.compile(
        "(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}_])");
    return pattern.matcher(source).find();
  }

  private static void addHighlightList(LinkedHashMap<String, JSONObject> output, JSONArray input,
                                       String source, boolean replaceGeneric) throws Exception {
    if (input == null) return;
    for (int i = 0; i < input.length() && output.size() < MAX_HIGHLIGHTS; i++) {
      Object raw = input.opt(i);
      String text;
      String type = "";
      if (raw instanceof JSONObject) {
        JSONObject object = (JSONObject) raw;
        text = object.optString("text", object.optString("name", "")).trim();
        type = sanitizeHighlightType(object.optString("type", object.optString("kind", "")));
      } else {
        text = raw == null ? "" : String.valueOf(raw).trim();
      }
      if (text.length() < 2) continue;
      if (source != null && !containsWholeTerm(source, text)) continue;
      putHighlight(output, text, type, replaceGeneric);
    }
  }

  private static void putHighlight(LinkedHashMap<String, JSONObject> output, String text,
                                   String type, boolean replaceGeneric) throws Exception {
    if (text == null) return;
    String value = text.trim();
    if (value.length() < 2) return;
    if (value.length() > MAX_HIGHLIGHT_TEXT) value = value.substring(0, MAX_HIGHLIGHT_TEXT).trim();

    String normalizedType = sanitizeHighlightType(type);
    String key = value.toLowerCase(Locale.ROOT);
    JSONObject previous = output.get(key);
    if (previous != null) {
      String previousType = previous.optString("type", "");
      if (!replaceGeneric || normalizedType.isEmpty() || !previousType.isEmpty()) return;
    }
    if (output.size() >= MAX_HIGHLIGHTS && previous == null) return;

    JSONObject highlight = new JSONObject().put("text", value);
    if (!normalizedType.isEmpty()) highlight.put("type", normalizedType);
    output.put(key, highlight);
  }

  private static JSONArray toArray(LinkedHashMap<String, JSONObject> map) {
    JSONArray output = new JSONArray();
    for (JSONObject value : map.values()) output.put(value);
    return output;
  }

  private static String sanitizeHighlightType(String raw) {
    String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if ("npc".equals(type) || "ally".equals(type) || "player".equals(type)) type = "character";
    if ("enemy".equals(type) || "monster".equals(type)) type = "entity";
    if ("level".equals(type) || "area".equals(type) || "zone".equals(type) || "place".equals(type)) type = "location";
    if ("status".equals(type) || "buff".equals(type) || "debuff".equals(type)) type = "effect";
    switch (type) {
      case "character":
      case "entity":
      case "item":
      case "skill":
      case "effect":
      case "location":
      case "stat":
        return type;
      default:
        return "";
    }
  }
}
