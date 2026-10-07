package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** JSON boundary for the 1.1.93a mechanics; persistent values come from the Kotlin Core. */
final class Combat93Support {
  static final String LEVEL_KEY = "currentLevelKey";

  static int stageIndex(JSONObject state) {
    return Math.max(0, state.optInt("combatStageIndex", state.optInt("currentLevel", 0)));
  }

  static boolean isJoinedMember(JSONObject member) {
    return "ACTIVE".equals(member.optString("presence", "ACTIVE"));
  }

  static void returnToCurrentLevelStart(JSONObject state) throws Exception {
    JSONObject combat = state.getJSONObject("combat");
    state.put("location", combat.optString("deathRestartAnchorLocation", state.optString("location")));
    state.put("combatLevelRestart", true);
  }

  static int entityDropRatePercent(String key) { return 100; }
  static boolean shouldDropEntityLoot(int roll, int rate) { return roll >= 0 && roll < rate; }

  static String grantEntityLootItem(JSONObject state, int selector) throws Exception {
    // The two-item Entity pool from 1.1.93a. Actual inventory insertion belongs to the adapter.
    boolean water = Math.floorMod(selector, 2) == 0;
    String name = water ? "Almond Water" : "Băng Gạc Y Tế";
    JSONArray drops = state.optJSONArray("combat93Loot");
    if (drops == null) drops = new JSONArray();
    drops.put(new JSONObject().put("id", water ? "almond-water" : "bandage").put("name", name));
    state.put("combat93Loot", drops);
    return name;
  }

  static JSONArray semanticHighlights(String text, JSONObject state) throws Exception {
    JSONArray result = new JSONArray();
    for (java.util.Map.Entry<String, String> term : CombatChoiceEngine.semanticCatalog().entrySet()) {
      int from = 0, at;
      while (!term.getKey().isEmpty() && (at = text.indexOf(term.getKey(), from)) >= 0) {
        result.put(new JSONObject().put("start", at).put("end", at + term.getKey().length())
            .put("type", term.getValue()));
        from = at + term.getKey().length();
      }
    }
    return result;
  }

  static final class Progression {
    static final int BASE_STAT = 5;
    static final int ENTITY_VICTORY_BASE_CORE = 2;
    static int statPercent(int stat) { return 100 + 10 * (Math.max(1, Math.min(999, stat)) - 5); }
    static int scaledCoreReward(int base, int stage) {
      double value = Math.max(0, base) * Math.pow(1.5, Math.max(0, stage));
      return (int)Math.min(Integer.MAX_VALUE, Math.round(value));
    }
    static String normalizeCharacterId(String value) {
      String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (key.equals("cao minh") || key.equals("cao_minh")) return "cao_minh";
      if (key.equals("lục trầm") || key.equals("luc tram")) return "luc_tram";
      return key.replace(' ', '_');
    }
    void normalizeState(JSONObject state) throws Exception {
      JSONObject root = state.optJSONObject("characterProgression");
      if (root == null || root.optJSONObject("characters") == null)
        throw new IllegalStateException("Authoritative combat profiles are missing.");
    }
    JSONObject profile(JSONObject state, String id) throws Exception {
      return state.getJSONObject("characterProgression").getJSONObject("characters")
          .getJSONObject(normalizeCharacterId(id));
    }
    void setCurrentHp(JSONObject state, String id, int hp) throws Exception {
      JSONObject profile = profile(state, id);
      profile.put("currentHp", Math.max(0, Math.min(hp, profile.getInt("maxHp"))));
    }
    void grantCore(JSONObject state, int reward) throws Exception {
      JSONObject resource = state.getJSONObject("characterProgression").getJSONObject("coreResource");
      resource.put("quantity", (int)Math.min(Integer.MAX_VALUE,
          (long)Math.max(0, resource.optInt("quantity")) + Math.max(0, reward)));
    }
    int rewardTreasureEntityVictory(JSONObject state, String key, int stage, int first, int repeat)
        throws Exception {
      JSONObject resource = state.getJSONObject("characterProgression").getJSONObject("coreResource");
      JSONObject kills = resource.optJSONObject("treasureEntityStageKills");
      if (kills == null) kills = new JSONObject();
      JSONObject stages = kills.optJSONObject(key);
      if (stages == null) stages = new JSONObject();
      String index = String.valueOf(Math.max(0, stage));
      int reward = stages.optBoolean(index) ? repeat : first;
      stages.put(index, true); kills.put(key, stages); resource.put("treasureEntityStageKills", kills);
      grantCore(state, reward);
      return reward;
    }
    void markCompanionDown(JSONObject state, String id) throws Exception {
      JSONObject profile = profile(state, id);
      profile.put("currentHp", 0);
      if (!profile.has("reviveAtTurn")) profile.put("reviveAtTurn", state.optInt("turn", 1) + 10);
    }
    void applyCaoMinhDeathPenalty(JSONObject state) throws Exception {
      JSONObject profile = profile(state, "cao_minh");
      profile.put("currentHp", profile.getInt("maxHp"));
      profile.remove("reviveAtTurn");
      JSONObject player = state.optJSONObject("player");
      if (player != null) player.put("hp", profile.getInt("maxHp")).put("condition", "Ổn định");
    }
    void advanceStatusEffects(JSONObject state, String id, String clock) throws Exception {
      JSONObject profile = profile(state, id);
      JSONArray input = profile.optJSONArray("statusEffects"), remaining = new JSONArray();
      if (input != null) for (int i = 0; i < input.length(); i++) {
        JSONObject effect = input.getJSONObject(i);
        if (clock.equals(effect.optString("clock"))) {
          int turns = effect.optInt("remainingTurns") - 1;
          if (turns <= 0) continue;
          effect.put("remainingTurns", turns);
        }
        remaining.put(effect);
      }
      profile.put("statusEffects", remaining);
    }
  }

  static final class Stats {
    JSONObject project(JSONObject state, JSONObject source, String id, Progression progression)
        throws Exception {
      JSONObject profile = progression.profile(state, id);
      JSONObject values = profile.getJSONObject("stats"), stats = new JSONObject();
      for (String key : new String[]{"STR", "DEF", "SKL", "VIT"}) {
        int value = values.optInt(key, 5);
        stats.put(key, new JSONObject().put("effective", value));
      }
      int skl = values.optInt("SKL", 5), def = values.optInt("DEF", 5), vit = values.optInt("VIT", 5);
      return new JSONObject().put("currentHp", profile.getInt("currentHp"))
          .put("maxHp", profile.getInt("maxHp")).put("stats", stats)
          .put("combatStatus", new JSONObject()
              .put("criticalChancePercent", Math.max(0, Math.min(50, 5 + (skl - 5) * 2)))
              .put("evasionPercent", Math.max(0, Math.min(35, (vit - 5) * 2)))
              .put("resCriticalPercent", Math.max(0, Math.min(50, (def - 5) * 2)))
              .put("resEvasionPercent", Math.max(0, Math.min(50, (skl - 5) * 2))));
    }
  }
}
