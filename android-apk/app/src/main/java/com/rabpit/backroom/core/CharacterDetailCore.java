package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only character detail projection for the WebView Party UI.
 *
 * Restores the legacy partyDetails contract without giving Gemini ownership of character state.
 * Existing legacy detail data is preserved when present. Cao Minh's survival counters are derived from
 * authoritative subjective game time when no explicit physiology data exists.
 */
final class CharacterDetailCore {
  static final int MAX_MEMBERS = 4;
  private final CharacterProgressionCore characterProgressionCore = new CharacterProgressionCore();
  private final SurvivalCore survivalCore = new SurvivalCore();
  private final CharacterStatCore characterStatCore = new CharacterStatCore();

  void projectState(JSONObject state) throws Exception {
    if (state == null) return;
    characterProgressionCore.normalizeState(state);
    survivalCore.normalizeState(state);

    JSONObject previousDetails = state.optJSONObject("partyDetails");
    Map<String, JSONObject> previousById = detailMembersById(previousDetails);
    long elapsed = elapsedMinutes(state);

    JSONObject details = new JSONObject()
        .put("leaderId", "cao_minh")
        .put("maxMembers", MAX_MEMBERS)
        .put("coreCount", characterProgressionCore.coreCount(state))
        .put("elapsedSubjectiveMinutes", elapsed);

    JSONArray members = new JSONArray();
    JSONObject player = state.optJSONObject("player");
    if (player == null) player = new JSONObject().put("name", "Cao Minh");
    members.put(projectMember(state, player, previousById.get("cao_minh"), "cao_minh", "Cao Minh", true, elapsed));

    JSONArray party = state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length() && members.length() < MAX_MEMBERS; i++) {
        JSONObject source = party.optJSONObject(i);
        if (source == null || !CharacterEncounterCore.isJoinedMember(source)) continue;
        String id = characterId(source);
        if (id.isEmpty() || "cao_minh".equals(id)) continue;
        members.put(projectMember(
            state, source, previousById.get(id), id,
            source.optString("name", displayName(id)), false, elapsed));
      }
    }

    details.put("members", members);
    state.put("partyDetails", details);
  }

  static JSONObject derivePhysiology(long minutesSinceFood, long minutesSinceWater, long minutesAwake)
      throws Exception {
    return SurvivalCore.derivePhysiology(minutesSinceFood, minutesSinceWater, minutesAwake);
  }

  private JSONObject projectMember(JSONObject state, JSONObject source, JSONObject previous,
                                   String id, String fallbackName, boolean leader, long elapsed)
      throws Exception {
    JSONObject member = new JSONObject()
        .put("id", id)
        .put("name", nonEmpty(source.optString("name", ""), fallbackName))
        .put("presence", nonEmpty(source.optString("presence", ""), "ACTIVE"))
        .put("isLeader", leader);

    JSONObject progression =
        characterStatCore.project(state, source, id, characterProgressionCore);

    String avatar = firstString(source, previous, "avatar", "avatarRef");
    if (!avatar.isEmpty()) member.put("avatar", avatar);

    String healthState = firstString(source, previous, "healthState", "condition");
    if (!healthState.isEmpty()) member.put("healthState", healthState);
    String condition = firstString(source, previous, "condition", "healthState");
    if (!condition.isEmpty()) member.put("condition", condition);

    int hp = progression.getInt("currentHp");
    int maxHp = progression.getInt("maxHp");
    if (hp <= 0) {
      member.put("condition", "Bị hạ").put("healthState", "Bị hạ");
    } else if ("Bị hạ".equals(member.optString("condition"))) {
      member.put("condition", "Ổn định").put("healthState", "Ổn định");
    }
    member.put("currentHp", hp).put("hp", hp).put("maxHp", maxHp);
    member.put("baseMaxHp", progression.getInt("baseMaxHp"));
    member.put("stats", new JSONObject(progression.getJSONObject("stats").toString()));
    member.put("combatStatus",
        new JSONObject(progression.getJSONObject("combatStatus").toString()));
    member.put("progressionSource", progression.getString("source"));
    member.put("statusEffects", new JSONArray(characterProgressionCore.profile(state, id)
        .getJSONArray("statusEffects").toString()));

    if ("cao_minh".equals(id)) {
      JSONArray passives = new JSONArray()
          .put(new JSONObject()
              .put("name", CharacterStatCore.DAI_DAO_MA_TON_PASSIVE)
              .put("description",
                  "Tất cả Stats +10% theo stat gốc. Sau mỗi lượt combat của Cao Minh: hồi 10% Max HP, cộng dồn +20% Attack và +20% Critical trong trận. Đồng đội nhận +50% Critical.")
              .put("allStatsBonusPercent", CharacterStatCore.DAI_DAO_MA_TON_STAT_BONUS_PERCENT)
              .put("separateFromBaseStats", true)
              .put("healMaxHpPercent", 10)
              .put("attackPerTurnPercent", 20)
              .put("criticalPerTurnPercent", 20)
              .put("allyCriticalBonusPercent", 50));
      member.put("passives", passives);
    }

    copyStringIfPresent(source, previous, member, "role");
    copyArrayIfPresent(source, previous, member, "injuries");
    copyArrayIfPresent(source, previous, member, "statuses");
    copyArrayIfPresent(source, previous, member, "effects");

    member.put("physiology", survivalCore.projectPhysiology(state, id));

    JSONArray inventory = source.optJSONArray("inventory");
    if (inventory == null && leader) inventory = state.optJSONArray("inventory");
    if (inventory == null && previous != null) inventory = previous.optJSONArray("inventory");
    member.put("inventory", inventory == null ? new JSONArray() : new JSONArray(inventory.toString()));

    return member;
  }

  private static Map<String, JSONObject> detailMembersById(JSONObject details) {
    Map<String, JSONObject> output = new LinkedHashMap<>();
    if (details == null) return output;
    JSONArray members = details.optJSONArray("members");
    if (members == null) return output;
    for (int i = 0; i < members.length(); i++) {
      JSONObject member = members.optJSONObject(i);
      String id = characterId(member);
      if (!id.isEmpty() && !output.containsKey(id)) output.put(id, member);
    }
    return output;
  }

  private static long elapsedMinutes(JSONObject state) {
    JSONObject time = state == null ? null : state.optJSONObject("gameTime");
    return time == null ? 0L : Math.max(0L, time.optLong("elapsedSubjectiveMinutes", 0L));
  }

  private static String characterId(JSONObject member) {
    if (member == null) return "";
    String raw = (member.optString("id", "") + " " + member.optString("name", ""))
        .trim().toLowerCase(Locale.ROOT);
    if (raw.contains("cao_minh") ) return "cao_minh";
    if (raw.contains("lucia") || raw.contains("hứa thuý mai") || raw.contains("hứa thúy mai")
        || raw.contains("hua thuy mai")) return "lucia";
    if (raw.contains("lục trầm") || raw.contains("luc tram") || raw.contains("luc_tram")) return "luc_tram";
    if (raw.contains("syvial")) return "syvial";
    return member.optString("id", "").trim().toLowerCase(Locale.ROOT);
  }

  private static String displayName(String id) {
    if ("lucia".equals(id)) return "Lucia Lục";
    if ("luc_tram".equals(id)) return "Lục Trầm";
    if ("syvial".equals(id)) return "Syvial";
    return "Cao Minh";
  }

  private static String nonEmpty(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  private static String firstString(JSONObject source, JSONObject previous, String... keys) {
    for (String key : keys) {
      String value = source == null ? "" : source.optString(key, "").trim();
      if (!value.isEmpty()) return value;
    }
    for (String key : keys) {
      String value = previous == null ? "" : previous.optString(key, "").trim();
      if (!value.isEmpty()) return value;
    }
    return "";
  }

  private static void copyStringIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                          String key) throws Exception {
    String value = firstString(source, previous, key);
    if (!value.isEmpty()) target.put(key, value);
  }

  private static void copyArrayIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                         String key) throws Exception {
    JSONArray value = source == null ? null : source.optJSONArray(key);
    if (value == null && previous != null) value = previous.optJSONArray(key);
    target.put(key, value == null ? new JSONArray() : new JSONArray(value.toString()));
  }
}
