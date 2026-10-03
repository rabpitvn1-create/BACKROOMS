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
import java.util.Locale;
import java.util.Map;

final class EntityCore {
  static final double MIN_AUTO_SPAWN_RATE_PERCENT = 3.0d;
  static final double MAX_AUTO_SPAWN_RATE_PERCENT = 5.0d;
  static final double MAX_TREASURE_AUTO_SPAWN_RATE_PERCENT = 4.0d;
  static final double AUTO_SPAWN_RATE_MULTIPLIER = 3.0d;

  private static final String REGISTRY_ASSET = "knowledge/entity_encounters.json";
  private static final String ENCOUNTER_KEY = "entityEncounterKey";
  private static final String ENCOUNTER_KEYS = "entityEncounterKeys";
  private static final String RESOLVED_KEY = "entityEncounterResolved";
  private static final String SOURCE = "core_independent_roll";
  private static final String TREASURE_SOURCE = "core_treasure_priority_roll";

  private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();
  private final Map<String, LegacyEntityDefinition> legacyEntities = new LinkedHashMap<>();

  EntityCore(Context context) {
    loadRegistry(context);
  }

  JSONObject presentation(String key) throws Exception {
    EntityDefinition entity = entities.get(key);
    LegacyEntityDefinition legacy = legacyEntities.get(key);
    JSONObject data = entity != null ? entity.presentation : legacy == null ? null : legacy.presentation;
    return data == null ? new JSONObject() : new JSONObject(data.toString());
  }

  @Deprecated
  void prepareEncounter(JSONObject state) {
    throw new IllegalStateException(
        "Legacy unscoped Entity RNG is disabled; use SituationCandidate selection through TurnRng.");
  }

  JSONArray situationCandidates(JSONObject state) throws Exception {
    JSONArray output = new JSONArray();
    JSONObject currentFlags = flags(state);
    if (activeEncounterKeys(state).length() > 0) return output;
    int level = state.optInt("currentLevel", 0);
    String levelKey = state.optString(LevelCore.LEVEL_KEY, String.valueOf(level)).trim();
    for (EntityDefinition entity : entities.values()) {
      if (!entity.allowedOn(level, levelKey)) continue;
      output.put(new JSONObject()
          .put("candidateId", "entity:" + entity.key)
          .put("situationKey", "entity:" + entity.key)
          .put("kind", "ENTITY")
          .put("category", "DANGER")
          .put("chancePercent", effectiveAutoSpawnRatePercent(entity.ratePercent))
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
    activateEncounterCandidates(state, new JSONArray().put(key));
  }

  void activateEncounterCandidates(JSONObject state, JSONArray keys) throws Exception {
    JSONObject currentFlags = flags(state);
    if (activeEncounterKeys(state).length() > 0) {
      throw new IllegalStateException("An Entity encounter is already active");
    }

    JSONArray normalizedKeys = new JSONArray();
    JSONObject rates = new JSONObject();
    List<String> seen = new ArrayList<>();
    EntityDefinition first = null;
    if (keys != null) {
      for (int i = 0; i < keys.length(); i++) {
        String normalized = keys.optString(i, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || seen.contains(normalized)) continue;
        EntityDefinition entity = entities.get(normalized);
        if (entity == null) throw new IllegalArgumentException("Unknown Entity candidate: " + normalized);
        seen.add(normalized);
        normalizedKeys.put(normalized);
        rates.put(normalized, effectiveAutoSpawnRatePercent(entity.ratePercent));
        if (first == null) first = entity;
      }
    }
    if (first == null) throw new IllegalArgumentException("At least one Entity candidate is required");

    int level = state.optInt("currentLevel", 0);
    currentFlags.remove(RESOLVED_KEY);
    currentFlags.put(ENCOUNTER_KEYS, normalizedKeys);
    currentFlags.put(ENCOUNTER_KEY, first.key);
    currentFlags.put("entityEncounterSource", "candidate_selector");
    currentFlags.put("entityEncounterRatePercent", effectiveAutoSpawnRatePercent(first.ratePercent));
    currentFlags.put("entityEncounterRatePercents", rates);
    currentFlags.put("entityEncounterLevel", level);
    currentFlags.put("entityEncounterLevelKey",
        state.optString(LevelCore.LEVEL_KEY, String.valueOf(level)).trim());
    currentFlags.put("entityEncounterStartedTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", currentFlags);
  }

  JSONArray activeEncounterKeys(JSONObject state) {
    JSONArray output = new JSONArray();
    JSONObject currentFlags = state == null ? null : state.optJSONObject("flags");
    if (currentFlags == null) return output;
    List<String> seen = new ArrayList<>();
    JSONArray keys = currentFlags.optJSONArray(ENCOUNTER_KEYS);
    if (keys != null) {
      for (int i = 0; i < keys.length(); i++) {
        String key = keys.optString(i, "").trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || seen.contains(key)) continue;
        seen.add(key);
        output.put(key);
      }
    }
    String legacy = currentFlags.optString(ENCOUNTER_KEY, "").trim().toLowerCase(Locale.ROOT);
    if (!legacy.isEmpty() && !seen.contains(legacy)) output.put(legacy);
    return output;
  }

  String promptContext(JSONObject state) {
    JSONArray activeKeys = activeEncounterKeys(state);
    if (activeKeys.length() == 0) {
      return "ENTITY CORE: no active Entity encounter this turn. Do not invent, summon or select an Entity. " +
        "Encounter selection is owned exclusively by the deterministic SituationCandidate selector. " +
        "Only registered auto-spawn Entities whose registry Level/LevelKey eligibility includes the current location may roll this turn.";
    }

    String activeCombatKey = "";
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    if (combat != null && combat.optBoolean("active", false)) {
      JSONObject active = combat.optJSONObject("entity");
      if (active != null) activeCombatKey = active.optString("key", "").trim();
    }
    if (activeCombatKey.isEmpty()) activeCombatKey = activeKeys.optString(0, "");

    StringBuilder out = new StringBuilder();
    out.append("ENTITY CORE ACTIVE ENCOUNTER: ").append(activeKeys.length())
        .append(" Entity record(s) are present. ACTIVE COMBAT ENTITY key=")
        .append(activeCombatKey).append(".\n");
    out.append("PRESENT ENTITY KEYS: ").append(activeKeys.toString()).append(".\n");
    for (int i = 0; i < activeKeys.length(); i++) {
      String key = activeKeys.optString(i, "");
      EntityDefinition entity = entities.get(key);
      if (entity != null) {
        out.append("ENTITY[").append(i).append("] ")
            .append(entity.name).append(" (key=").append(entity.key).append("). ")
            .append("LEVEL POLICY: ").append(entity.locationPolicy()).append(". ")
            .append("CANON: ").append(entity.canon).append("\n");
      } else {
        LegacyEntityDefinition legacy = legacyEntities.get(key);
        out.append("ENTITY[").append(i).append("] ")
            .append(legacy == null ? key : legacy.name).append(" (key=").append(key).append("). ")
            .append(legacy == null ? "Preserve this Core-owned legacy encounter identity."
                : "CANON: " + legacy.canon)
            .append("\n");
      }
    }
    out.append("GM COMBAT RULE: know the full present list, but describe combat action only for the ACTIVE COMBAT ENTITY. ")
        .append("Never transfer appearance, held objects, skills or behavior from one Entity record to another. ")
        .append("Narration has no authority to resolve, spawn, despawn or mutate any Entity.");
    return out.toString();
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
    activateEncounterCandidates(state, new JSONArray().put(selected.key));
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
        JSONArray levels = record.optJSONArray("levels");
        JSONArray levelKeys = record.optJSONArray("levelKeys");
        if (key.isEmpty() || !validRate || levels == null || levels.length() == 0) continue;
        entities.put(key, new EntityDefinition(key, name, rate, canon, treasure,
            new JSONArray(levels.toString()),
            levelKeys == null ? new JSONArray() : new JSONArray(levelKeys.toString()),
            record.optJSONObject("presentation")));
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
          legacyEntities.put(key, new LegacyEntityDefinition(key, name, canon,
              record.optJSONObject("presentation")));
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

  static boolean locationAllowed(JSONArray levels, JSONArray levelKeys, int level, String levelKey) {
    if (levelKeys != null && levelKeys.length() > 0) {
      String normalized = levelKey == null ? "" : levelKey.trim();
      for (int i = 0; i < levelKeys.length(); i++) {
        if (normalized.equals(levelKeys.optString(i, "").trim())) return true;
      }
      return false;
    }
    if (levels == null) return false;
    for (int i = 0; i < levels.length(); i++) {
      if (levels.optInt(i, Integer.MIN_VALUE) == level) return true;
    }
    return false;
  }

  static double effectiveAutoSpawnRatePercent(double configuredRatePercent) {
    return configuredRatePercent * AUTO_SPAWN_RATE_MULTIPLIER;
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
    final JSONObject presentation;

    LegacyEntityDefinition(String key, String name, String canon, JSONObject presentation) {
      this.key = key;
      this.name = name;
      this.canon = canon;
      this.presentation = presentation;
    }
  }

  private static final class EntityDefinition {
    final String key;
    final String name;
    final double ratePercent;
    final String canon;
    final boolean treasure;
    final JSONArray levels;
    final JSONArray levelKeys;
    final JSONObject presentation;

    EntityDefinition(String key, String name, double ratePercent, String canon, boolean treasure,
                     JSONArray levels, JSONArray levelKeys, JSONObject presentation) {
      this.key = key;
      this.name = name;
      this.ratePercent = ratePercent;
      this.canon = canon;
      this.treasure = treasure;
      this.levels = levels == null ? new JSONArray() : levels;
      this.levelKeys = levelKeys == null ? new JSONArray() : levelKeys;
      this.presentation = presentation;
    }

    boolean allowedOn(int level, String levelKey) {
      return locationAllowed(levels, levelKeys, level, levelKey);
    }

    String locationPolicy() {
      return levelKeys.length() > 0
          ? "LevelKeys " + levelKeys.toString()
          : "Levels " + levels.toString();
    }
  }
}
