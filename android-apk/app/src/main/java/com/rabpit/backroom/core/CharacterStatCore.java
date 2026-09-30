package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.json.JSONArray;

/** Read-only projection of authoritative Poker Dice combat stats and derived combat status. */
final class CharacterStatCore {
  static final int BASE_CRITICAL_PERCENT = 5;
  static final int CRITICAL_PER_SKL = 2;
  static final int MAX_CRITICAL_PERCENT = 50;
  static final int EVASION_PER_VIT = 2;
  static final int MAX_EVASION_PERCENT = 35;
  static final int RESIST_PER_STAT = 2;
  static final int MAX_RESIST_PERCENT = 50;
  static final String DAI_DAO_MA_TON_PASSIVE = "Đại Đạo Ma Tôn";
  static final int DAI_DAO_MA_TON_STAT_BONUS_PERCENT = 10;

  JSONObject project(JSONObject state, String characterId, CharacterProgressionCore progressionCore)
      throws Exception {
    return project(state, null, characterId, progressionCore);
  }

  JSONObject project(JSONObject state, JSONObject runtimeSource, String characterId,
                     CharacterProgressionCore progressionCore) throws Exception {
    JSONObject profile = progressionCore.profile(state, characterId);
    JSONObject source = profile.getJSONObject("stats");

    int passiveBonusPercent = passiveStatBonusPercent(characterId);
    int strBase = source.optInt("STR", CharacterProgressionCore.BASE_STAT);
    int defBase = source.optInt("DEF", CharacterProgressionCore.BASE_STAT);
    int sklBase = source.optInt("SKL", CharacterProgressionCore.BASE_STAT);
    int vitBase = source.optInt("VIT", CharacterProgressionCore.BASE_STAT);
    int str = effectiveStat(state, profile, characterId, "STR");
    int def = effectiveStat(state, profile, characterId, "DEF");
    int skl = effectiveStat(state, profile, characterId, "SKL");
    int vit = effectiveStat(state, profile, characterId, "VIT");

    JSONObject stats = new JSONObject()
        .put("STR", line(strBase, passiveBonusPercent, passiveStatBonus(characterId, strBase), str))
        .put("DEF", line(defBase, passiveBonusPercent, passiveStatBonus(characterId, defBase), def))
        .put("SKL", line(sklBase, passiveBonusPercent, passiveStatBonus(characterId, sklBase), skl))
        .put("VIT", line(vitBase, passiveBonusPercent, passiveStatBonus(characterId, vitBase), vit));

    int baseAttack = CombatChoiceEngine.baseAttackFor(runtimeSource, characterId);
    JSONObject combatStatus = new JSONObject()
        .put("damage", CombatChoiceEngine.basicDamage(baseAttack, str, 100))
        .put("defendPercent", defendPercent(def))
        .put("criticalChancePercent", criticalChancePercent(skl))
        .put("evasionPercent", evasionPercent(vit))
        .put("resCriticalPercent", criticalResistancePercent(def))
        .put("resEvasionPercent", evasionResistancePercent(skl));

    return new JSONObject()
        .put("currentHp", profile.getInt("currentHp"))
        .put("maxHp", profile.getInt("maxHp"))
        .put("baseMaxHp", profile.getInt("baseMaxHp"))
        .put("stats", stats)
        .put("combatStatus", combatStatus)
        .put("source", CharacterProgressionCore.ROOT_KEY);
  }

  static int criticalChancePercent(int skl) {
    return Math.max(0, Math.min(MAX_CRITICAL_PERCENT,
        BASE_CRITICAL_PERCENT + (skl - CharacterProgressionCore.BASE_STAT) * CRITICAL_PER_SKL));
  }

  static int evasionPercent(int vit) {
    return Math.min(MAX_EVASION_PERCENT,
        Math.max(0, vit - CharacterProgressionCore.BASE_STAT) * EVASION_PER_VIT);
  }

  static int criticalResistancePercent(int def) {
    return Math.min(MAX_RESIST_PERCENT,
        Math.max(0, def - CharacterProgressionCore.BASE_STAT) * RESIST_PER_STAT);
  }

  static int evasionResistancePercent(int skl) {
    return Math.min(MAX_RESIST_PERCENT,
        Math.max(0, skl - CharacterProgressionCore.BASE_STAT) * RESIST_PER_STAT);
  }

  static double defendPercent(int def) {
    int multiplier = CharacterProgressionCore.statPercent(def);
    double percent = 100.0d - (10_000.0d / multiplier);
    return Math.round(percent * 10.0d) / 10.0d;
  }

  static int effectiveStat(JSONObject state, JSONObject profile, String characterId, String key)
      throws Exception {
    int base = profile.getJSONObject("stats").optInt(key, CharacterProgressionCore.BASE_STAT);
    long value = base + passiveStatBonus(characterId, base)
        + new SurvivalCore().statPenalty(state, characterId, key);
    JSONArray effects = profile.optJSONArray("statusEffects");
    if (effects != null) {
      for (int i = 0; i < effects.length(); i++) {
        JSONObject effect = effects.optJSONObject(i);
        if (effect != null && effect.optInt("remainingTurns", 0) > 0) {
          JSONObject modifiers = effect.optJSONObject("modifiers");
          if (modifiers != null) value += modifiers.optInt(key, 0);
        }
      }
    }
    return (int)Math.max(1L, Math.min(CharacterProgressionCore.MAX_STAT, value));
  }

  static int passiveStatBonusPercent(String characterId) {
    return "cao_minh".equals(CharacterProgressionCore.normalizeCharacterId(characterId))
        ? DAI_DAO_MA_TON_STAT_BONUS_PERCENT : 0;
  }

  static int passiveStatBonus(String characterId, int base) {
    int percent = passiveStatBonusPercent(characterId);
    if (percent <= 0) return 0;
    return Math.max(0, Math.round(Math.max(1, base) * percent / 100.0f));
  }

  private JSONObject line(int base, int passiveBonusPercent, int passiveBonus, int effective)
      throws Exception {
    int normalized = Math.max(CharacterProgressionCore.BASE_STAT, base);
    return new JSONObject()
        .put("base", normalized)
        .put("passiveBonusPercent", Math.max(0, passiveBonusPercent))
        .put("passiveBonus", Math.max(0, passiveBonus))
        .put("effective", effective)
        .put("temporaryModifier", effective - normalized - Math.max(0, passiveBonus))
        .put("nextCoreCost", CharacterProgressionCore.upgradeCost(normalized));
  }
}
