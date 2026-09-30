package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Stage-only Entity scaling. Entities do not own Character-style stats or progression.
 */
final class EntityStatCore {
  static int stagePercent(int stageIndex) {
    return 100 + 10 * Math.max(0, stageIndex);
  }

  static int scale(int baseValue, int stageIndex) {
    long scaled = (long)Math.max(0, baseValue) * stagePercent(stageIndex);
    return Math.max(0, (int)((scaled + 50L) / 100L));
  }

  JSONObject profile(String entityKey, int baseMaxHp, int baseDamage, int stageIndex)
      throws Exception {
    int normalizedStage = Math.max(0, stageIndex);
    return new JSONObject()
        .put("entityKey", entityKey == null ? "" : entityKey)
        .put("stageIndex", normalizedStage)
        .put("stagePercent", stagePercent(normalizedStage))
        .put("baseMaxHp", Math.max(1, baseMaxHp))
        .put("baseDamage", Math.max(1, baseDamage))
        .put("maxHp", Math.max(1, scale(Math.max(1, baseMaxHp), normalizedStage)))
        .put("damage", Math.max(1, scale(Math.max(1, baseDamage), normalizedStage)));
  }
}
