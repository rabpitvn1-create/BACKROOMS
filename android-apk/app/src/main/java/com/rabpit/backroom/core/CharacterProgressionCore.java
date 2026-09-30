package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core-owned party progression for the Poker Dice ruleset.
 *
 * There is no Character Level, Explorer rank or EXP in this system. The only mutable combat
 * progression is STR/DEF/SKL/VIT, paid from one party-shared Core resource.
 */
final class CharacterProgressionCore {
  static final String ROOT_KEY = "characterProgression";
  static final String CHARACTERS_KEY = "characters";
  static final String RESOURCE_KEY = "coreResource";
  static final String LEGACY_ROOT_KEY = "characterRpg";

  static final int BASE_STAT = 5;
  static final int MAX_STAT = 999;
  static final int DEFAULT_BASE_MAX_HP = 50;
  static final int COMPANION_REVIVE_TURNS = 10;
  static final int ENTITY_VICTORY_BASE_CORE = 2;

  private static final double CORE_STAGE_MULTIPLIER = 1.5d;
  private static final String TREASURE_STAGE_KILLS_KEY = "treasureEntityStageKills";
  private static final String[] STAT_KEYS = {"STR", "DEF", "SKL", "VIT"};

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;

    JSONObject root = state.optJSONObject(ROOT_KEY);
    if (root == null) root = new JSONObject();
    JSONObject characters = root.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();

    ensureProfileObject(state, characters, "cao_minh");
    ensureProfileObject(state, characters, "lucia");
    ensureProfileObject(state, characters, "luc_tram");
    ensureProfileObject(state, characters, "syvial");

    JSONObject resource = root.optJSONObject(RESOURCE_KEY);
    if (resource == null) {
      resource = new JSONObject()
          .put("quantity", 0)
          // Old saves must not receive retroactive Stage rewards merely by being loaded.
          .put("highestRewardedStageIndex", Math.max(0, LevelCore.stageIndex(state)));
    } else {
      resource.put("quantity", Math.max(0, resource.optInt("quantity", 0)));
      if (!resource.has("highestRewardedStageIndex")) {
        resource.put("highestRewardedStageIndex", Math.max(0, LevelCore.stageIndex(state)));
      } else {
        resource.put("highestRewardedStageIndex",
            Math.max(-1, resource.optInt("highestRewardedStageIndex", -1)));
      }
    }

    root.put(CHARACTERS_KEY, characters);
    root.put(RESOURCE_KEY, resource);
    root.put("schema", "core_stats_v1");
    state.put(ROOT_KEY, root);
    JSONObject player = state.optJSONObject("player");
    if (player != null) {
      JSONObject leader = characters.getJSONObject("cao_minh");
      player.put("hp", leader.getInt("currentHp")).put("maxHp", leader.getInt("maxHp"));
    }

    state.remove(LEGACY_ROOT_KEY);
    state.remove("equipment");
    stripLegacyProgression(state.optJSONObject("player"));

    JSONArray party = state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) stripLegacyProgression(party.optJSONObject(i));
    }

    JSONObject partyDetails = state.optJSONObject("partyDetails");
    JSONArray detailMembers = partyDetails == null ? null : partyDetails.optJSONArray("members");
    if (detailMembers != null) {
      for (int i = 0; i < detailMembers.length(); i++) stripLegacyProgression(detailMembers.optJSONObject(i));
    }
  }

  JSONObject ensureProfile(JSONObject state, String rawId) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    normalizeState(state);
    String id = normalizeCharacterId(rawId);
    if (id.isEmpty()) throw new IllegalArgumentException("character id is required");
    JSONObject characters = state.getJSONObject(ROOT_KEY).getJSONObject(CHARACTERS_KEY);
    return ensureProfileObject(state, characters, id);
  }

  JSONObject profile(JSONObject state, String rawId) throws Exception {
    return ensureProfile(state, rawId);
  }

  int coreCount(JSONObject state) throws Exception {
    normalizeState(state);
    return state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY).getInt("quantity");
  }

  int highestRewardedStageIndex(JSONObject state) throws Exception {
    normalizeState(state);
    return state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY)
        .optInt("highestRewardedStageIndex", -1);
  }

  static int upgradeCost(int currentStat) {
    int normalized = Math.max(BASE_STAT, currentStat);
    return 1 + (normalized - BASE_STAT) / 2;
  }

  static int bundleSize(int stageIndex) {
    return 1 + Math.max(0, stageIndex) / 10;
  }

  static int scaledCoreReward(int baseReward, int stageIndex) {
    int base = Math.max(0, baseReward);
    if (base == 0) return 0;
    int stage = Math.max(0, stageIndex);
    double scaled = base * Math.pow(CORE_STAGE_MULTIPLIER, stage);
    if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return Math.max(base, (int)Math.round(scaled));
  }

  static int statPercent(int stat) {
    return 100 + 10 * (Math.max(1, Math.min(MAX_STAT, stat)) - BASE_STAT);
  }

  static int scaledByStat(int baseValue, int stat) {
    long scaled = (long)Math.max(0, baseValue) * statPercent(stat);
    return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, (scaled + 50L) / 100L));
  }

  static int maxHpFor(int baseMaxHp, int vit) {
    return Math.max(1, scaledByStat(Math.max(1, baseMaxHp), vit));
  }

  JSONObject upgradeStat(JSONObject state, String rawId, String rawStat) throws Exception {
    String id = normalizeCharacterId(rawId);
    String stat = normalizeStat(rawStat);
    if (id.isEmpty()) throw new IllegalArgumentException("character id is required");
    if (stat.isEmpty()) throw new IllegalArgumentException("Chỉ có thể nâng STR, DEF, SKL hoặc VIT.");

    JSONObject profile = ensureProfile(state, id);
    JSONObject stats = profile.getJSONObject("stats");
    int current = Math.max(BASE_STAT, stats.optInt(stat, BASE_STAT));
    if (current >= MAX_STAT) throw new IllegalStateException("Chỉ số đã đạt giới hạn.");
    int cost = upgradeCost(current);
    JSONObject resource = state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY);
    int available = Math.max(0, resource.optInt("quantity", 0));
    if (available < cost) {
      throw new IllegalStateException("Không đủ Core. Cần " + cost + " Core.");
    }

    int previousMaxHp = profile.getInt("maxHp");
    int previousCurrentHp = profile.getInt("currentHp");
    resource.put("quantity", available - cost);
    stats.put(stat, current + 1);
    normalizeHp(state, id, profile);
    if ("VIT".equals(stat) && previousCurrentHp > 0) {
      int nextMaxHp = profile.getInt("maxHp");
      int maxHpGain = Math.max(0, nextMaxHp - previousMaxHp);
      profile.put("currentHp", Math.min(nextMaxHp, previousCurrentHp + maxHpGain));
    }
    syncShadowHp(state, id, profile.getInt("currentHp"), profile.getInt("maxHp"));

    return new JSONObject()
        .put("characterId", id)
        .put("stat", stat)
        .put("value", current + 1)
        .put("cost", cost)
        .put("coreRemaining", available - cost);
  }

  int grantCore(JSONObject state, int amount) throws Exception {
    if (amount <= 0) return 0;
    normalizeState(state);
    JSONObject resource = state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY);
    int current = Math.max(0, resource.optInt("quantity", 0));
    long next = (long)current + amount;
    int granted = (int)Math.min((long)Integer.MAX_VALUE - current, (long)amount);
    resource.put("quantity", (int)Math.min(Integer.MAX_VALUE, next));
    return granted;
  }

  int rewardTreasureEntityVictory(JSONObject state, String rawEntityKey, int stageIndex,
                                  int firstKillReward, int repeatKillReward) throws Exception {
    normalizeState(state);
    String entityKey = rawEntityKey == null ? "" : rawEntityKey.trim().toLowerCase(Locale.ROOT);
    if (entityKey.isEmpty()) throw new IllegalArgumentException("entity key is required");
    int normalizedStage = Math.max(0, stageIndex);
    String stageKey = String.valueOf(normalizedStage);

    JSONObject resource = state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY);
    JSONObject entityStages = resource.optJSONObject(TREASURE_STAGE_KILLS_KEY);
    if (entityStages == null) {
      entityStages = new JSONObject();
      resource.put(TREASURE_STAGE_KILLS_KEY, entityStages);
    }
    JSONObject clearedStages = entityStages.optJSONObject(entityKey);
    if (clearedStages == null) {
      clearedStages = new JSONObject();
      entityStages.put(entityKey, clearedStages);
    }

    boolean firstKillForStage = !clearedStages.optBoolean(stageKey, false);
    int reward = Math.max(0, firstKillForStage ? firstKillReward : repeatKillReward);
    if (firstKillForStage) clearedStages.put(stageKey, true);
    grantCore(state, reward);
    return reward;
  }

  int rewardStageCompletion(JSONObject state, int stageIndex) throws Exception {
    normalizeState(state);
    int normalizedStage = Math.max(0, stageIndex);
    JSONObject resource = state.getJSONObject(ROOT_KEY).getJSONObject(RESOURCE_KEY);
    int highest = resource.optInt("highestRewardedStageIndex", -1);
    if (normalizedStage <= highest) return 0;
    int reward = bundleSize(normalizedStage);
    int current = Math.max(0, resource.optInt("quantity", 0));
    int granted = (int)Math.min((long)reward, (long)Integer.MAX_VALUE - current);
    resource.put("quantity", current + granted);
    resource.put("highestRewardedStageIndex", normalizedStage);
    return granted;
  }

  void applyCaoMinhDeathPenalty(JSONObject state) throws Exception {
    // EXP/Explorer penalties no longer exist. Preserve the established respawn flow but do not
    // mutate Core or combat stats.
    JSONObject profile = ensureProfile(state, "cao_minh");
    int maxHp = profile.getInt("maxHp");
    profile.put("currentHp", maxHp);
    profile.remove("downedAtTurn");
    profile.remove("reviveAtTurn");
    syncShadowHp(state, "cao_minh", maxHp, maxHp);
    JSONObject player = state.optJSONObject("player");
    if (player != null) player.put("condition", "Ổn định");
  }

  void markCompanionDown(JSONObject state, String rawId) throws Exception {
    normalizeState(state);
    String id = normalizeCharacterId(rawId);
    if (id.isEmpty() || "cao_minh".equals(id)) return;

    JSONObject profile = profile(state, id);
    profile.put("currentHp", 0);
    int turn = Math.max(1, state.optInt("turn", 1));
    if (!profile.has("reviveAtTurn")) {
      profile.put("downedAtTurn", turn);
      profile.put("reviveAtTurn", turn + COMPANION_REVIVE_TURNS);
    }
    syncPartyRecoveryState(state, id, profile, turn);
  }

  void applyExplorerTurnRecovery(JSONObject state) throws Exception {
    // Method name retained to avoid a broad unrelated call-site rewrite. Recovery is turn based,
    // not Explorer-progression based.
    normalizeState(state);
    int turn = Math.max(1, state.optInt("turn", 1));
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;

    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      String id = normalizeCharacterId(member.optString("id", member.optString("name", "")));
      if (id.isEmpty() || "cao_minh".equals(id)) continue;

      JSONObject profile = profile(state, id);
      int hp = Math.max(0, profile.optInt("currentHp", 0));
      int reviveAtTurn = profile.optInt("reviveAtTurn", -1);
      if (hp <= 0 && reviveAtTurn > 0 && turn >= reviveAtTurn) {
        profile.put("currentHp", 1);
        profile.remove("downedAtTurn");
        profile.remove("reviveAtTurn");
      }
      syncPartyRecoveryState(state, id, profile, turn);
    }
  }

  void setCurrentHp(JSONObject state, String rawId, int currentHp) throws Exception {
    JSONObject profile = ensureProfile(state, rawId);
    normalizeHp(state, normalizeCharacterId(rawId), profile);
    int maxHp = profile.getInt("maxHp");
    profile.put("currentHp", Math.max(0, Math.min(currentHp, maxHp)));
  }

  int healCurrentHp(JSONObject state, String rawId, int amount) throws Exception {
    String id = normalizeCharacterId(rawId);
    JSONObject profile = ensureProfile(state, id);
    normalizeHp(state, id, profile);
    int current = Math.max(0, profile.optInt("currentHp", 0));
    int maxHp = profile.getInt("maxHp");
    if (!"cao_minh".equals(id) && current <= 0) {
      throw new IllegalStateException("Nhân vật đang bị hạ và phải chờ đủ 10 Explorer Turn để hồi sinh.");
    }
    int next = (int)Math.min((long)maxHp, (long)current + Math.max(0, amount));
    profile.put("currentHp", next);
    syncShadowHp(state, id, next, maxHp);
    return next - current;
  }

  void applyStatusEffect(JSONObject state, String rawId, String type, String source, String clock,
                         int turns, String stat, int modifier) throws Exception {
    if (type == null || type.trim().isEmpty() || source == null || source.trim().isEmpty()
        || (!"explorer_turn".equals(clock) && !"actor_turn".equals(clock))
        || normalizeStat(stat).isEmpty() || turns <= 0 || modifier == 0) {
      throw new IllegalArgumentException("Status effect không hợp lệ.");
    }
    JSONObject profile = ensureProfile(state, rawId);
    profile.getJSONArray("statusEffects").put(new JSONObject()
        .put("type", type.trim()).put("source", source.trim()).put("clock", clock)
        .put("remainingTurns", turns)
        .put("modifiers", new JSONObject().put(normalizeStat(stat), modifier)));
    normalizeStatusEffects(profile);
    String id = normalizeCharacterId(rawId);
    normalizeHp(state, id, profile);
    syncShadowHp(state, id, profile.getInt("currentHp"), profile.getInt("maxHp"));
  }

  void advanceStatusEffects(JSONObject state, String rawId, String clock) throws Exception {
    if (!"explorer_turn".equals(clock) && !"actor_turn".equals(clock)) {
      throw new IllegalArgumentException("Status clock không hợp lệ.");
    }
    String id = normalizeCharacterId(rawId);
    JSONObject profile = ensureProfile(state, id);
    JSONArray effects = profile.getJSONArray("statusEffects");
    JSONArray remaining = new JSONArray();
    for (int i = 0; i < effects.length(); i++) {
      JSONObject effect = effects.getJSONObject(i);
      if (clock.equals(effect.optString("clock"))) {
        int turns = effect.getInt("remainingTurns") - 1;
        if (turns <= 0) continue;
        effect.put("remainingTurns", turns);
      }
      remaining.put(effect);
    }
    profile.put("statusEffects", remaining);
    normalizeHp(state, id, profile);
    syncShadowHp(state, id, profile.getInt("currentHp"), profile.getInt("maxHp"));
  }

  private static void normalizeStatusEffects(JSONObject profile) throws Exception {
    JSONArray input = profile.optJSONArray("statusEffects");
    Map<String, JSONObject> unique = new LinkedHashMap<>();
    if (input != null) for (int i = 0; i < input.length() && i < 64; i++) {
      JSONObject effect = input.optJSONObject(i);
      if (effect == null) continue;
      String type = effect.optString("type", "").trim();
      String source = effect.optString("source", "").trim();
      String clock = effect.optString("clock", "");
      if (type.isEmpty() || type.length() > 64 || source.isEmpty() || source.length() > 80
          || (!"explorer_turn".equals(clock) && !"actor_turn".equals(clock))) continue;
      int turns = Math.min(20, Math.max(0, effect.optInt("remainingTurns", 0)));
      JSONObject inputModifiers = effect.optJSONObject("modifiers");
      if (turns == 0 || inputModifiers == null) continue;
      for (String stat : STAT_KEYS) {
        if (!inputModifiers.has(stat)) continue;
        int amount = Math.max(-5, Math.min(5, inputModifiers.optInt(stat, 0)));
        if (amount == 0) continue;
        String key = type + ":" + source + ":" + clock + ":" + stat;
        JSONObject previous = unique.get(key);
        int previousAmount = previous == null ? 0
            : previous.getJSONObject("modifiers").getInt(stat);
        int selected = Math.abs(amount) > Math.abs(previousAmount) ? amount : previousAmount;
        unique.put(key, new JSONObject()
            .put("type", type).put("source", source).put("clock", clock)
            .put("remainingTurns", Math.max(turns,
                previous == null ? 0 : previous.getInt("remainingTurns")))
            .put("modifiers", new JSONObject().put(stat, selected)));
      }
    }
    JSONArray output = new JSONArray();
    for (JSONObject effect : unique.values()) {
      if (output.length() >= 32) break;
      output.put(effect);
    }
    profile.put("statusEffects", output);
  }

  void protectFromCandidate(JSONObject before, JSONObject candidate) throws Exception {
    if (before == null || candidate == null) return;
    normalizeState(before);
    candidate.put(ROOT_KEY, new JSONObject(before.getJSONObject(ROOT_KEY).toString()));
    candidate.remove(LEGACY_ROOT_KEY);
    JSONObject sourcePlayer = before.optJSONObject("player");
    JSONObject targetPlayer = candidate.optJSONObject("player");
    if (sourcePlayer != null) {
      if (targetPlayer == null) {
        targetPlayer = new JSONObject(sourcePlayer.toString());
        candidate.put("player", targetPlayer);
      }
      for (String key : new String[]{"hp", "currentHp", "maxHp", "attackMax", "attack", "ATK"}) {
        if (sourcePlayer.has(key)) targetPlayer.put(key, sourcePlayer.get(key));
        else targetPlayer.remove(key);
      }
    }
    stripLegacyProgression(candidate.optJSONObject("player"));
    JSONArray party = candidate.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) stripLegacyProgression(party.optJSONObject(i));
    }
  }

  private JSONObject ensureProfileObject(JSONObject state, JSONObject characters, String id)
      throws Exception {
    JSONObject profile = characters.optJSONObject(id);
    if (profile == null) profile = new JSONObject();

    profile.put("id", id);
    boolean migrated = !"core_stats_v1".equals(profile.optString("schema", ""));
    JSONObject stats = profile.optJSONObject("stats");
    if (stats == null || migrated) {
      stats = freshStats();
    } else {
      for (String key : STAT_KEYS) {
        stats.put(key, Math.max(BASE_STAT, Math.min(MAX_STAT, stats.optInt(key, BASE_STAT))));
      }
      stats.remove("DF");
      stats.remove("AGI");
      stats.remove("CRIT");
      stats.remove("LUCK");
    }
    profile.put("stats", stats);

    int baseMaxHp = profile.has("baseMaxHp")
        ? Math.max(1, profile.optInt("baseMaxHp", defaultBaseMaxHp(id)))
        : defaultBaseMaxHp(id);
    profile.put("baseMaxHp", baseMaxHp);
    if (!profile.has("currentHp")) profile.put("currentHp", baseMaxHp);
    profile.put("schema", "core_stats_v1");
    normalizeStatusEffects(profile);

    profile.remove("baseStats");
    profile.remove("explorer");
    profile.remove("exp");
    profile.remove("level");
    profile.remove("equipment");
    normalizeHp(state, id, profile);

    characters.put(id, profile);
    return profile;
  }

  private static JSONObject freshStats() throws Exception {
    return new JSONObject()
        .put("STR", BASE_STAT)
        .put("DEF", BASE_STAT)
        .put("SKL", BASE_STAT)
        .put("VIT", BASE_STAT);
  }

  private static int defaultBaseMaxHp(String id) {
    return DEFAULT_BASE_MAX_HP;
  }

  private static void normalizeHp(JSONObject state, String id, JSONObject profile) throws Exception {
    int maxHp = maxHpFor(profile.optInt("baseMaxHp", DEFAULT_BASE_MAX_HP),
        CharacterStatCore.effectiveStat(state, profile, id, "VIT"));
    int current = profile.has("currentHp")
        ? profile.optInt("currentHp", maxHp)
        : maxHp;
    profile.put("maxHp", maxHp);
    profile.put("currentHp", Math.max(0, Math.min(current, maxHp)));
  }

  private void syncPartyRecoveryState(JSONObject state, String id, JSONObject profile, int turn)
      throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String memberId = normalizeCharacterId(member.optString("id", member.optString("name", "")));
      if (!id.equals(memberId)) continue;

      int hp = Math.max(0, profile.optInt("currentHp", 0));
      int maxHp = Math.max(1, profile.optInt("maxHp", DEFAULT_BASE_MAX_HP));
      member.put("hp", hp).put("maxHp", maxHp);
      if (hp <= 0) {
        int reviveAtTurn = profile.optInt("reviveAtTurn", turn + COMPANION_REVIVE_TURNS);
        member.put("condition", "Bị hạ");
        member.put("reviveTurnsRemaining", Math.max(0, reviveAtTurn - turn));
      } else {
        member.remove("reviveTurnsRemaining");
        if ("Bị hạ".equals(member.optString("condition", ""))) member.put("condition", "Ổn định");
      }
      return;
    }
  }

  private void syncShadowHp(JSONObject state, String id, int hp, int maxHp) throws Exception {
    if ("cao_minh".equals(id)) {
      JSONObject player = state.optJSONObject("player");
      if (player != null) player.put("hp", hp).put("maxHp", maxHp);
      return;
    }
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String memberId = normalizeCharacterId(member.optString("id", member.optString("name", "")));
      if (!id.equals(memberId)) continue;
      member.put("hp", hp).put("maxHp", maxHp);
      return;
    }
  }

  static String normalizeCharacterId(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (value.contains("cao_minh") || value.contains("cao minh")) return "cao_minh";
    if (value.contains("lucia") || value.contains("hứa thuý mai") || value.contains("hứa thúy mai")
        || value.contains("hua thuy mai")) return "lucia";
    if (value.contains("lục trầm") || value.contains("luc tram") || value.contains("luc_tram")) return "luc_tram";
    if (value.contains("syvial")) return "syvial";
    return value.replace(' ', '_');
  }

  private static String normalizeStat(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    for (String key : STAT_KEYS) if (key.equals(value)) return key;
    return "";
  }

  private static void stripLegacyProgression(JSONObject character) {
    if (character == null) return;
    character.remove("baseStats");
    character.remove("stats");
    character.remove("explorer");
    character.remove("exp");
    character.remove("level");
    character.remove("equipment");
  }
}
