package com.rabpit.backroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI-only GM recommendations: a numbered block at the END of the latest GM
 * narration. The selected text is never a privileged gameplay command.
 */
public final class GmNarrativeOptionResolver {
  private static final Pattern BLOCK = Pattern.compile(
    "(?s)(?:^|\\n)GỢI Ý HÀNH ĐỘNG:\\s*\\n((?:[1-3]\\. [^\\r\\n]+(?:\\r?\\n|$)){2,3})\\s*$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LINE = Pattern.compile("(?m)^([1-3])\\. ([^\\r\\n]+)\\s*$");

  private GmNarrativeOptionResolver() {}

  public static JSONArray parse(String narration) throws Exception {
    JSONArray suggestions = new JSONArray();
    if (narration == null || narration.length() > 12000) return suggestions;
    Matcher marker = BLOCK.matcher(narration.trim());
    if (!marker.find()) return suggestions;
    Matcher rows = LINE.matcher(marker.group(1));
    int n = 1;
    while (rows.find()) {
      if (Integer.parseInt(rows.group(1)) != n++) return new JSONArray();
      String action = rows.group(2).trim();
      if (action.length() < 5 || action.length() > 400 ||
        action.indexOf('<') >= 0 || action.indexOf('>') >= 0) return new JSONArray();
      suggestions.put(action);
    }
    return suggestions.length() >= 2 && suggestions.length() <= 3 ? suggestions : new JSONArray();
  }

  public static String resolve(int selected, JSONObject view) throws Exception {
    if (selected < 1 || selected > 3) throw new IllegalArgumentException("gm_suggestion_index");
    JSONObject scene = view.optJSONObject("scene");
    JSONArray log = view.optJSONArray("log");
    if (scene == null || log == null || scene.optBoolean("generationNeeded", false) ||
      scene.has("pendingActionId") || view.has("pendingEncounter"))
      throw new IllegalStateException("gm_suggestion_unavailable");
    String map = scene.optString("nodeId") + ":" + scene.optInt("miniIndex", 1);
    // Only the most recent GM message in the CURRENT scene is eligible.
    for (int i = log.length()-1; i>=0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry == null || !"gm".equals(entry.optString("role"))) continue;
      if (!map.equals(entry.optString("sceneMap"))) break;
      JSONArray options = parse(entry.optString("text"));
      if (selected <= options.length()) return options.getString(selected-1);
      break;
    }
    throw new IllegalArgumentException("gm_suggestion_missing");
  }
}
