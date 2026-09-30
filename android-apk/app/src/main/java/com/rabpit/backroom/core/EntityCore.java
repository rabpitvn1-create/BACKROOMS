package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EntityCore {
  static final double MIN_AUTO_SPAWN_RATE_PERCENT = 3.0d;
  static final double MAX_AUTO_SPAWN_RATE_PERCENT = 3.5d;
  static final double MAX_TREASURE_AUTO_SPAWN_RATE_PERCENT = 4.0d;

  private static final String REGISTRY_ASSET = "knowledge/entity_encounters.json";
  private static final String ENCOUNTER_KEY = "entityEncounterKey";
  private static final String RESOLVED_KEY = "entityEncounterResolved";
  private static final String SOURCE = "core_independent_roll";
  private static final String TREASURE_SOURCE = "core_treasure_priority_roll";

  private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();
  private final Map<String, LegacyEntityDefinition> legacyEntities = new LinkedHashMap<>();

  EntityCore(Context context) {
    loadRegistry(context);
  }

  @Deprecated
  void prepareEncounter(JSONObject state) {
    throw new IllegalStateException(
        "Legacy unscoped Entity RNG is disabled; use SituationCandidate selection through TurnRng.");
  }

  JSONArray situationCandidates(JSONObject state) throws Exception {
    JSONArray output = new JSONArray();
    JSONObject currentFlags = flags(state);
    if (!currentFlags.optString(ENCOUNTER_KEY, "").trim().isEmpty()) return output;
    int level = state.optInt("currentLevel", 0);
    for (EntityDefinition entity : entities.values()) {
      if (!roamingAllowedOn(level)) continue;
      output.put(new JSONObject()
          .put("candidateId", "entity:" + entity.key)
          .put("situationKey", "entity:" + entity.key)
          .put("kind", "ENTITY")
          .put("category", "DANGER")
          .put("chancePercent", entity.ratePercent)
          .put("payloadKey", entity.key)
          .put("source", "CANON")
          .put("publicSummary", entity.name + " đã tiến vào phạm vi tương tác với Cao Minh.")
          .put("capabilityContext", entity.canon)
          .put("allowedWorldActions", allowedWorldActions(entity.canon))
          .put("fallbackAction", "INTERCEPT")
          .put("proposalRequired", true)
          .put("eligibilityRuleId", "canon:entity:" + entity.key)
          .put("tags", new JSONArray().put("DANGER").put("ENTITY").put(entity.key))
          .put("keyRefs", new JSONArray().put(entity.key)));
    }
    return output;
  }

  private static JSONArray allowedWorldActions(String canon) {
    JSONArray actions = new JSONArray()
        .put("INTERCEPT")
        .put("DIRECT_ATTACK")
        .put("OBSERVE");
    String text = canon == null ? "" : canon.toLowerCase(java.util.Locale.ROOT);
    if (text.contains("ambush") || text.contains("blind spot") || text.contains("recess")) {
      actions.put("AMBUSH");
    }
    if (text.contains("watch") || text.contains("stalk") || text.contains("hunt")) {
      actions.put("STALK");
    }
    if (text.contains("lure") || text.contains("mimic") || text.contains("voice")
        || text.contains("imitat")) {
      actions.put("LURE");
    }
    return actions;
  }

  void activateEncounterCandidate(JSONObject state, String key) throws Exception {
    String normalized = key == null ? "" : key.trim();
    EntityDefinition entity = entities.get(normalized);
    if (entity == null) throw new IllegalArgumentException("Unknown Entity candidate: " + normalized);
    JSONObject currentFlags = flags(state);
    if (!currentFlags.optString(ENCOUNTER_KEY, "").trim().isEmpty()) {
      throw new IllegalStateException("An Entity encounter is already active");
    }
    activateEncounter(state, currentFlags, entity, state.optInt("currentLevel", 0), "candidate_selector");
  }

  String promptContext(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    String activeKey = flags == null ? "" : flags.optString(ENCOUNTER_KEY, "").trim();
    if (activeKey.isEmpty()) {
      return "ENTITY CORE: no active Entity encounter this turn. Do not invent, summon or select an Entity. " +
        "Encounter selection is owned exclusively by the deterministic SituationCandidate selector. " +
        "All registered auto-spawn Entities are roaming and may roll on every valid Backrooms Level regardless of their original canon habitat.";
    }

    EntityDefinition entity = entities.get(activeKey);
    if (entity == null) {
      LegacyEntityDefinition legacy = legacyEntities.get(activeKey);
      if (legacy != null) return legacyPromptContext(activeKey, legacy.name, legacy.canon);
      return "ENTITY CORE: active legacy/boss encounter key=" + activeKey + ". Preserve the Core-owned encounter identity. " +
        "Narration must not replace, resolve, spawn, despawn or mutate this Entity.";
    }

    return "ENTITY CORE ACTIVE ENCOUNTER: " + entity.name + " (key=" + entity.key + ").\n" +
      (entity.treasure
          ? "TREASURE PRIORITY SPAWN RATE: " + entity.ratePercent + "% per eligible world-advancing turn.\n"
          : "FIXED INDEPENDENT SPAWN RATE: " + entity.ratePercent + "% per eligible world-advancing turn.\n") +
      "ROAMING POLICY: this registered Entity is valid on every Backrooms Level. Original canon habitat/location restrictions do not block its presence.\n" +
      "ENTITY CANON (behavior/capabilities only): " + entity.canon + "\n" +
      "Do not replace this Entity with another one. Narrate only the committed encounter state and behavioral canon. " +
      "Narration has no authority to resolve, spawn, despawn or mutate the Entity.";
  }

  static String legacyPromptContext(String activeKey, String name, String canon) {
    String safeKey = activeKey == null ? "" : activeKey.trim();
    String safeName = name == null || name.trim().isEmpty() ? safeKey : name.trim();
    String safeCanon = canon == null ? "" : canon.trim();
    return "ENTITY CORE ACTIVE LEGACY/BOSS ENCOUNTER: " + safeName + " (key=" + safeKey + ").\n" +
      "LEGACY ENTITY CANON: " + safeCanon + "\n" +
      "RUNTIME LOCK: this encounter is legacy/boss-only and must not be treated as an auto-spawn Entity. " +
      "Preserve the Core-owned encounter identity; narration must not replace, resolve or mutate it.";
  }

  private JSONObject flags(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    state.put("flags", flags);
    return flags;
  }

  private void activateEncounter(JSONObject state, JSONObject flags, EntityDefinition selected,
                                 int level, String source) throws Exception {
    flags.remove(RESOLVED_KEY);
    flags.put(ENCOUNTER_KEY, selected.key);
    flags.put("entityEncounterSource", source);
    flags.put("entityEncounterRatePercent", selected.ratePercent);
    flags.put("entityEncounterLevel", level);
    flags.put("entityEncounterStartedTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", flags);
  }

  private void loadRegistry(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, REGISTRY_ASSET));
      if (!"independent_per_entity".equals(root.optString("rollMode"))) return;
      JSONArray records = root.optJSONArray("entities");
      if (records == null) return;
      for (int i = 0; i < records.length(); i++) {
        JSONObject record = records.optJSONObject(i);
        if (record == null) continue;
        String key = record.optString("key", "").trim();
        String name = record.optString("name", key).trim();
        double rate = record.optDouble("ratePercent", 0.0);
        String canon = record.optString("canon", "").trim();
        boolean treasure = "treasure".equalsIgnoreCase(
            record.optString("spawnClass", "standard").trim());
        boolean validRate = treasure
            ? validTreasureAutoSpawnRatePercent(rate)
            : validAutoSpawnRatePercent(rate);
        if (key.isEmpty() || !validRate) continue;
        entities.put(key, new EntityDefinition(key, name, rate, canon, treasure));
      }

      JSONArray legacyRecords = root.optJSONArray("legacyEntities");
      if (legacyRecords != null) {
        for (int i = 0; i < legacyRecords.length(); i++) {
          JSONObject record = legacyRecords.optJSONObject(i);
          if (record == null) continue;
          String key = record.optString("key", "").trim();
          String name = record.optString("name", key).trim();
          String canon = record.optString("canon", "").trim();
          if (key.isEmpty() || canon.isEmpty()) continue;
          legacyEntities.put(key, new LegacyEntityDefinition(key, name, canon));
        }
      }
    } catch (Exception ignored) {}
  }

  private String readAsset(Context context, String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = context.getAssets().open(path);
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }

  static boolean roamingAllowedOn(int level) {
    return level >= 0;
  }

  static boolean validAutoSpawnRatePercent(double ratePercent) {
    return ratePercent >= MIN_AUTO_SPAWN_RATE_PERCENT
        && ratePercent <= MAX_AUTO_SPAWN_RATE_PERCENT;
  }

  static boolean validTreasureAutoSpawnRatePercent(double ratePercent) {
    return ratePercent > 0.0d && ratePercent <= MAX_TREASURE_AUTO_SPAWN_RATE_PERCENT;
  }

  private static final class LegacyEntityDefinition {
    final String key;
    final String name;
    final String canon;

    LegacyEntityDefinition(String key, String name, String canon) {
      this.key = key;
      this.name = name;
      this.canon = canon;
    }
  }

  private static final class EntityDefinition {
    final String key;
    final String name;
    final double ratePercent;
    final String canon;
    final boolean treasure;

    EntityDefinition(String key, String name, double ratePercent, String canon, boolean treasure) {
      this.key = key;
      this.name = name;
      this.ratePercent = ratePercent;
      this.canon = canon;
      this.treasure = treasure;
    }
  }
}
