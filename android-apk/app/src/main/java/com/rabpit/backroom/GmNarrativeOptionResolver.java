package com.rabpit.backroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a player's numbered reference to the LAST GM suggestion in the
 * current public scene. This is language expansion only, never Core authority.
 * The existing planner and the Core offered-action validator remain mandatory.
 */
public final class GmNarrativeOptionResolver {
  private static final Pattern SELECTION = Pattern.compile(
    "^(?:(?:tôi\\s+)?(?:chọn|lựa\\s+chọn)\\s+)?(?:phương\\s+án|pa)\\s*(?:số\\s*)?([1-9])\\s*[.!]?\\s*$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern NUMBERED = Pattern.compile(
    "(?m)^\\s*([1-9])[.)]\\s+([^\\r\\n]+?)\\s*$");

  private GmNarrativeOptionResolver() { }

  public static String resolve(String action, JSONObject publicView) throws Exception {
    if (action == null) return null;
    Matcher pick = SELECTION.matcher(action.trim());
    if (!pick.matches()) return action;
    int selected = Integer.parseInt(pick.group(1));
    JSONArray log = publicView.optJSONArray("log");
    JSONObject scene = publicView.optJSONObject("scene");
    if (log == null || scene == null)
      throw new IllegalArgumentException("gm_option_reference_missing");
    String sceneMap = scene.optString("nodeId") + ":" + scene.optInt("miniIndex", 1);
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry == null || !"gm".equals(entry.optString("role"))) continue;
      String taggedMap = entry.optString("sceneMap");
      // Tagged scene narration must never be resolved from a previous mini-map.
      if (!taggedMap.isEmpty() && !sceneMap.equals(taggedMap))
        throw new IllegalArgumentException("gm_option_reference_missing");
      String text = entry.optString("text");
      if (text.length() > 12000)
        throw new IllegalArgumentException("gm_option_reference_missing");
      Map<Integer, String> choices = new TreeMap<>();
      Matcher lines = NUMBERED.matcher(text);
      while (lines.find()) {
        int index = Integer.parseInt(lines.group(1));
        String description = lines.group(2).trim();
        if (description.length() < 5 || description.length() > 500 || choices.put(index, description) != null)
          throw new IllegalArgumentException("gm_option_reference_ambiguous");
      }
      if (choices.size() < 2 || !choices.containsKey(1) || !choices.containsKey(selected))
        throw new IllegalArgumentException("gm_option_reference_missing");
      for (int n = 1; n <= choices.size(); n++)
        if (!choices.containsKey(n))
          throw new IllegalArgumentException("gm_option_reference_ambiguous");
      return choices.get(selected);
    }
    throw new IllegalArgumentException("gm_option_reference_missing");
  }
}
