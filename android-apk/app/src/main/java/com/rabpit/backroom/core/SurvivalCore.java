package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Core-owned hunger, thirst and rest state based on subjective game time. */
final class SurvivalCore {
  static final String ROOT_KEY = "survival";
  static final String CHARACTERS_KEY = "characters";

  static final long FOOD_CRITICAL_MINUTES = 72L * 60L;
  static final long WATER_CRITICAL_MINUTES = 48L * 60L;
  static final long REST_CRITICAL_MINUTES = 36L * 60L;

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject root = state.optJSONObject(ROOT_KEY);
    if (root == null) root = new JSONObject();
    JSONObject characters = root.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();

    ensureProfileObject(state, characters, "cao_minh", 0L);

    JSONArray party = state.optJSONArray("party");
    if (party != null) {
      long elapsed = elapsedMinutes(state);
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
        String id = CharacterProgressionCore.normalizeCharacterId(
            member.optString("id", member.optString("name", "")));
        if (id.isEmpty() || "cao_minh".equals(id)) continue;
        ensureProfileObject(state, characters, id, elapsed);
      }
    }

    root.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, root);
  }

  JSONObject projectPhysiology(JSONObject state, String rawId) throws Exception {
    JSONObject profile = ensureProfile(state, rawId);
    long elapsed = elapsedMinutes(state);
    long sinceFood = Math.max(0L, elapsed - profile.optLong("lastFoodMinute", elapsed));
    long sinceWater = Math.max(0L, elapsed - profile.optLong("lastWaterMinute", elapsed));
    long sinceRest = Math.max(0L, elapsed - profile.optLong("lastRestMinute", elapsed));
    return derivePhysiology(sinceFood, sinceWater, sinceRest);
  }

  int statPenalty(JSONObject state, String rawId, String stat) throws Exception {
    JSONObject physiology = projectPhysiology(state, rawId);
    if ("STR".equals(stat)) return bandPenalty(physiology.optString("hunger"));
    if ("VIT".equals(stat)) return bandPenalty(physiology.optString("thirst"));
    if ("DEF".equals(stat) || "SKL".equals(stat)) {
      return bandPenalty(physiology.optString("sleepDeprivation"));
    }
    return 0;
  }

  private static int bandPenalty(String band) {
    if ("CRITICAL".equals(band)) return -3;
    if ("SEVERE".equals(band)) return -2;
    if ("MODERATE".equals(band)) return -1;
    return 0;
  }

  int restoreFood(JSONObject state, String rawId, int percentPoints) throws Exception {
    return restore(state, rawId, "lastFoodMinute", FOOD_CRITICAL_MINUTES, percentPoints, "foodPercent");
  }

  int restoreWater(JSONObject state, String rawId, int percentPoints) throws Exception {
    return restore(state, rawId, "lastWaterMinute", WATER_CRITICAL_MINUTES, percentPoints, "waterPercent");
  }

  int restoreRest(JSONObject state, String rawId, int percentPoints) throws Exception {
    return restore(state, rawId, "lastRestMinute", REST_CRITICAL_MINUTES, percentPoints, "restPercent");
  }

  JSONObject ensureProfile(JSONObject state, String rawId) throws Exception {
    normalizeState(state);
    String id = CharacterProgressionCore.normalizeCharacterId(rawId);
    if (id.isEmpty()) throw new IllegalArgumentException("character id is required");
    JSONObject characters = state.getJSONObject(ROOT_KEY).getJSONObject(CHARACTERS_KEY);
    long initial = "cao_minh".equals(id) ? 0L : elapsedMinutes(state);
    return ensureProfileObject(state, characters, id, initial);
  }

  static JSONObject derivePhysiology(long minutesSinceFood, long minutesSinceWater, long minutesAwake)
      throws Exception {
    JSONObject result = new JSONObject();
    putPhysiology(result, "hunger", minutesSinceFood, 12L * 60L, 24L * 60L, 48L * 60L,
        FOOD_CRITICAL_MINUTES, "foodPercent");
    putPhysiology(result, "thirst", minutesSinceWater, 6L * 60L, 12L * 60L, 24L * 60L,
        WATER_CRITICAL_MINUTES, "waterPercent");
    putPhysiology(result, "sleepDeprivation", minutesAwake, 16L * 60L, 20L * 60L, 24L * 60L,
        REST_CRITICAL_MINUTES, "restPercent");
    result.put("minutesSinceFood", Math.max(0L, minutesSinceFood));
    result.put("minutesSinceWater", Math.max(0L, minutesSinceWater));
    result.put("minutesAwake", Math.max(0L, minutesAwake));
    return result;
  }

  static long elapsedMinutes(JSONObject state) {
    JSONObject time = state == null ? null : state.optJSONObject("gameTime");
    return time == null ? 0L : Math.max(0L, time.optLong("elapsedSubjectiveMinutes", 0L));
  }

  private int restore(JSONObject state, String rawId, String key, long criticalMinutes,
                      int percentPoints, String percentKey) throws Exception {
    if (percentPoints <= 0) return 0;
    JSONObject before = projectPhysiology(state, rawId);
    JSONObject profile = ensureProfile(state, rawId);
    long elapsed = elapsedMinutes(state);
    long current = Math.min(elapsed, profile.optLong(key, elapsed));
    long restoreMinutes = criticalMinutes * Math.max(0, percentPoints) / 100L;
    profile.put(key, Math.min(elapsed, current + restoreMinutes));
    JSONObject after = projectPhysiology(state, rawId);
    return Math.max(0, after.optInt(percentKey, 0) - before.optInt(percentKey, 0));
  }

  private JSONObject ensureProfileObject(JSONObject state, JSONObject characters, String id, long initialMinute)
      throws Exception {
    JSONObject profile = characters.optJSONObject(id);
    long elapsed = elapsedMinutes(state);
    long initial = Math.max(0L, Math.min(elapsed, initialMinute));
    if (profile == null) {
      profile = new JSONObject();
      JSONObject legacy = legacyPhysiology(state, id);
      if (legacy != null) {
        profile.put("lastFoodMinute",
            minuteForRemainingPercent(elapsed, FOOD_CRITICAL_MINUTES, legacy.optInt("foodPercent", -1), initial));
        profile.put("lastWaterMinute",
            minuteForRemainingPercent(elapsed, WATER_CRITICAL_MINUTES, legacy.optInt("waterPercent", -1), initial));
        profile.put("lastRestMinute",
            minuteForRemainingPercent(elapsed, REST_CRITICAL_MINUTES, legacy.optInt("restPercent", -1), initial));
      }
    }
    profile.put("lastFoodMinute", clampMinute(profile.optLong("lastFoodMinute", initial), elapsed));
    profile.put("lastWaterMinute", clampMinute(profile.optLong("lastWaterMinute", initial), elapsed));
    profile.put("lastRestMinute", clampMinute(profile.optLong("lastRestMinute", initial), elapsed));
    characters.put(id, profile);
    return profile;
  }

  private static JSONObject legacyPhysiology(JSONObject state, String id) {
    JSONObject details = state == null ? null : state.optJSONObject("partyDetails");
    JSONArray members = details == null ? null : details.optJSONArray("members");
    if (members == null) return null;
    for (int i = 0; i < members.length(); i++) {
      JSONObject member = members.optJSONObject(i);
      if (member == null) continue;
      String memberId = CharacterProgressionCore.normalizeCharacterId(
          member.optString("id", member.optString("name", "")));
      if (!id.equals(memberId)) continue;
      JSONObject physiology = member.optJSONObject("physiology");
      if (physiology != null) return physiology;
    }
    return null;
  }

  private static long minuteForRemainingPercent(long elapsed, long criticalMinutes, int percent, long fallback) {
    if (percent < 0 || percent > 100 || criticalMinutes <= 0L) return fallback;
    long minutesSince = criticalMinutes * (100L - percent) / 100L;
    return elapsed - minutesSince;
  }

  private static long clampMinute(long value, long elapsed) {
    return Math.min(Math.max(0L, elapsed), value);
  }

  private static void putPhysiology(JSONObject result, String bandKey, long minutes,
                                    long mildAt, long moderateAt, long severeAt, long criticalAt,
                                    String percentKey) throws Exception {
    result.put(bandKey, band(minutes, mildAt, moderateAt, severeAt, criticalAt));
    result.put(percentKey, remainingPercent(minutes, criticalAt));
  }

  private static String band(long minutes, long mildAt, long moderateAt, long severeAt, long criticalAt) {
    if (minutes < 0L) return "UNKNOWN";
    if (minutes >= criticalAt) return "CRITICAL";
    if (minutes >= severeAt) return "SEVERE";
    if (minutes >= moderateAt) return "MODERATE";
    if (minutes >= mildAt) return "MILD";
    return "NORMAL";
  }

  private static int remainingPercent(long minutes, long emptyAt) {
    if (minutes < 0L || emptyAt <= 0L) return 0;
    long remaining = Math.max(0L, Math.min(emptyAt, emptyAt - minutes));
    return (int)Math.max(0L, Math.min(100L, remaining * 100L / emptyAt));
  }
}
