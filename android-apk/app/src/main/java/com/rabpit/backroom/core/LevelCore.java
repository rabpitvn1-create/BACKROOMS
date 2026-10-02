package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LevelCore {
  interface IntRng {
    int nextInt(int bound);
  }

  static final String ROUTE_STATE = "levelRoute";
  static final String LEVEL_KEY = "currentLevelKey";
  static final int ROUTE_SUCCESS_PERCENT = 50;
  static final int ROUTE_TRIPLE_SUCCESS_PERCENT = 1;
  static final int ROUTE_TRIPLE_SUCCESS_INCREMENT = 3;
  static final int ROUTE_REQUIRED_STREAK = 6;
  static final String LEVEL_ZERO_START_LOCATION =
      "Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng không gian";

  private static final String[] LEVEL_ZERO_PROGRESSION = {
      "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms", "1"
  };
  private static final String[] GAMEPLAY_PROGRESSION = {
      "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms",
      "1", "1.2", "1.3", "1.5", "base_alpha", "traders_vault",
      "2", "2.1", "3", "3.5", "4", "office_market",
      "5", "5.1", "5.2", "5.3", "6", "6.1"
  };
  private static final String[] ALL_LEVEL_NODE_ORDER = {
      "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms",
      "1", "1.2", "1.3", "1.5", "base_alpha", "traders_vault",
      "2", "2.1", "3", "3.5", "4", "office_market",
      "5", "5.1", "5.2", "5.3", "6", "6.1", "6.31"
  };
  private static final Pattern LEVEL_TOKEN =
      Pattern.compile("(?i)(?:^|\\b)level\\s*([0-6](?:\\.[0-9]+)?)");
  private static final String LEVEL_KNOWLEDGE_ASSET = "knowledge/level_knowledge.json";
  private static final String LEGACY_KNOWLEDGE_ASSET = "knowledge/knowledge_db.json";
  private static final String SNAPSHOT_MANIFEST_ASSET = "level_snapshots/drive/manifest.json";
  private static final int LEVEL_MISMATCH = -2;
  private static final int ROUTE_ROLL_BOUND = 100;
  static final int MAX_KNOWLEDGE_CONTEXT_CHARS = 3200;
  static final int MAX_SCENE_CONTEXT_CHARS = 2200;
  private static final int MAX_SCENE_PALETTE_CHARS = 1500;

  private final Map<String, JSONObject> knowledgeByLevelKey = new LinkedHashMap<>();
  private final Map<String, String> legacyCanonByLevelKey = new LinkedHashMap<>();
  private final JSONArray knowledgeSectionOrder = new JSONArray();
  private final Map<Integer, JSONArray> snapshotsByLevel = new LinkedHashMap<>();
  private final Map<Integer, String> visualTypeByLevel = new LinkedHashMap<>();
  private final Map<String, JSONArray> snapshotsByLevelKey = new LinkedHashMap<>();
  private final Map<String, String> visualTypeByLevelKey = new LinkedHashMap<>();
  private final IntRng rng;
  private final LevelGraph levelGraph;
  private String snapshotRoot = "level_snapshots/wiki";
  private String sublevelSnapshotRoot = "level_snapshots/sublevels/level_0";

  LevelCore(Context context) {
    this(context, bound -> {
      throw new IllegalStateException("Unscoped Level RNG is disabled; use the TurnRng-backed overload.");
    });
  }

  LevelCore(Context context, IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
    this.levelGraph = LevelGraph.load(context);
    if (context != null) {
      loadLevelKnowledge(context);
      if (knowledgeByLevelKey.isEmpty()) loadLegacyKnowledge(context);
      loadSnapshotManifest(context);
    }
  }

  static LevelCore withKnowledge(String levelKnowledgeJson, IntRng rng) {
    LevelCore core = new LevelCore((Context)null, rng);
    core.loadLevelKnowledgeText(levelKnowledgeJson);
    return core;
  }

  void normalizeState(JSONObject state) throws Exception {
    String levelKey = resolveLevelKey(state);
    int level = parentLevelForKey(levelKey);
    state.put("currentLevel", level);
    state.put(LEVEL_KEY, levelKey);
    normalizeRouteState(state, levelKey);
  }

  int stageIndexForState(JSONObject state) {
    String key = resolveLevelKey(state);
    int legacy = stageIndexForKey(key);
    return levelGraph.available() ? levelGraph.stageIndex(key, legacy) : legacy;
  }

  void rollRouteForExplorerAction(JSONObject state, String action) throws Exception {
    rollRouteForExplorerAction(state, action, rng);
  }

  void rollRouteForExplorerAction(JSONObject state, String action, IntRng turnRng) throws Exception {
    if (turnRng == null) throw new IllegalArgumentException("turn RNG is required");
    normalizeState(state);
    if (!GameCoreRules.isRouteExplorationAction(action)) return;

    String levelKey = resolveLevelKey(state);
    JSONObject route = normalizeRouteState(state, levelKey);
    if (route.optBoolean("exitAvailable", false)) return;

    int turn = Math.max(1, state.optInt("turn", 1));
    if (route.optInt("lastRollTurn", -1) == turn) return;

    int streak = route.optInt("streak", 0);
    if (streak == 0 && route.optString("originLocation", "").trim().isEmpty()) {
      route.put("originLocation", state.optString("location", ""));
    }

    int roll = nextRoll(turnRng, ROUTE_ROLL_BOUND);
    route.put("lastRollTurn", turn);

    if (roll < ROUTE_SUCCESS_PERCENT) {
      int increment = roll < ROUTE_TRIPLE_SUCCESS_PERCENT
          ? ROUTE_TRIPLE_SUCCESS_INCREMENT
          : 1;
      streak = Math.min(ROUTE_REQUIRED_STREAK, streak + increment);
      route.put("streak", streak);
      route.remove("returnLocation");
      if (streak >= ROUTE_REQUIRED_STREAK) {
        route.put("exitAvailable", true);
        route.put("lastResult", "EXIT_AVAILABLE");
      } else {
        route.put("exitAvailable", false);
        route.put("lastResult", "SUCCESS");
      }
    } else {
      String origin = route.optString("originLocation", state.optString("location", ""));
      route.put("streak", 0);
      route.put("exitAvailable", false);
      route.put("lastResult", "RESET");
      route.put("returnLocation", origin);
      route.remove("originLocation");
      // Route reset is an engine-owned mechanical outcome; apply it immediately to WorkingState.
      if (!origin.trim().isEmpty()) state.put("location", origin);
    }

    state.put(ROUTE_STATE, route);
  }

  boolean applyPlayerTransitionIfRequested(JSONObject state, String action) throws Exception {
    normalizeState(state);
    String fromKey = resolveLevelKey(state);
    JSONObject route = normalizeRouteState(state, fromKey);
    if (!route.optBoolean("exitAvailable", false)) return false;

    String requested = rawLevelKeyFromLocation(action);
    if (requested.isEmpty() && hasTransitionIntent(action)) {
      if (levelGraph.available()) {
        java.util.List<String> outgoing = levelGraph.outgoing(fromKey);
        if (outgoing.size() == 1) requested = outgoing.get(0);
      } else {
        requested = nextForKey(fromKey);
      }
    }
    requested = normalizeKey(requested);
    if (requested.isEmpty() || requested.equals(fromKey)) return false;
    if (!knownKey(requested) || !transitionAllowedFor(fromKey, requested)) return false;

    state.put("currentLevel", parentLevelForKey(requested));
    state.put(LEVEL_KEY, requested);
    state.put(ROUTE_STATE, newRouteStateForKey(requested));
    state.put("location", defaultLocationForKey(requested));
    return true;
  }

  private static boolean hasTransitionIntent(String action) {
    String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    return value.contains("đi qua") || value.contains("bước qua") || value.contains("tiến vào")
        || value.contains("đi vào") || value.contains("sang ") || value.contains("vào level")
        || value.contains("enter") || value.contains("go through") || value.contains("cross");
  }

  static void returnToCurrentLevelStart(JSONObject state) throws Exception {
    if (state == null) return;
    String key = new LevelCore((Context)null, bound -> 0).resolveLevelKey(state);
    state.put(LEVEL_KEY, key);
    state.put("location", defaultLocation(key));
  }

  String promptContext(JSONObject state) {
    return promptContext(state, "");
  }

  String promptContext(JSONObject state, String action) {
    String levelKey = resolveLevelKey(state);
    int level = parentLevelForKey(levelKey);
    int turn = Math.max(1, state.optInt("turn", 1));
    String canon = knowledgeContext(levelKey, turn, action);
    if (canon.trim().isEmpty()) {
      canon = legacyCanonByLevelKey.get(levelKey);
    }
    if (canon == null || canon.trim().isEmpty()) {
      canon = "Canon for " + displayNameForKey(levelKey) + " is unavailable.";
    }

    String next = nextForKey(levelKey);
    String allowed;
    if (levelGraph.available()) {
      StringBuilder values = new StringBuilder();
      for (String target : levelGraph.outgoing(levelKey)) {
        if (values.length() > 0) values.append(", ");
        values.append(displayNameForKey(target));
      }
      allowed = values.length() == 0 ? "none" : values.toString();
    } else if (next != null) {
      allowed = displayName(next);
    } else {
      StringBuilder values = new StringBuilder();
      for (int target = 0; target <= 6; target++) {
        if (target == level || !GameCoreRules.levelTransitionAllowed(level, target)) continue;
        if (values.length() > 0) values.append(", ");
        values.append(displayName(String.valueOf(target)));
      }
      allowed = values.length() == 0 ? "none" : values.toString();
    }

    JSONObject route;
    try {
      route = normalizeRouteState(state, levelKey);
    } catch (Exception e) {
      route = new JSONObject();
    }

    boolean rolledThisTurn = route.optInt("lastRollTurn", -1) == turn;
    String result = rolledThisTurn ? route.optString("lastResult", "") : "";
    boolean exitAvailable = route.optBoolean("exitAvailable", false);

    String routeInstruction;
    if ("RESET".equals(result)) {
      String returnLocation = route.optString("returnLocation", state.optString("location", ""));
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: RESET. Narrate naturally that the attempted route folds, loops, or returns Cao Minh toward familiar ground"
              + (returnLocation.trim().isEmpty() ? "." : " near: " + returnLocation + ".")
              + " Never mention a roll, streak, probability, reset counter, or hidden system.";
    } else if ("EXIT_AVAILABLE".equals(result) || exitAvailable) {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME: EXIT AVAILABLE. You may now establish a real boundary/route to "
              + allowed
              + " if the player's action naturally reaches or crosses it. Never reveal the hidden route system.";
    } else if ("SUCCESS".equals(result)) {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: SUCCESS. Narrate meaningful continued exploration inside "
              + displayNameForKey(levelKey)
              + ", but do not reveal an exit yet. Never mention a roll, streak, probability, or hidden system.";
    } else {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: NO ROUTE ROLL. Keep route progression implicit and never reveal the hidden system.";
    }

    String transitionInstruction = exitAvailable
        ? "LEVEL TRANSITION: AVAILABLE. The only allowed next destination is " + allowed
            + ". Do not skip any required Level graph node."
        : "LEVEL TRANSITION: LOCKED. Keep currentLevelKey unchanged and keep the environment inside "
            + displayNameForKey(levelKey) + ".";

    return "CURRENT LEVEL NODE: " + displayNameForKey(levelKey) + "\n"
        + "CURRENT LEVEL KEY: " + levelKey + "\n"
        + "PARENT LEVEL NUMBER: " + level + "\n"
        + "LEVEL KNOWLEDGE BUNDLE:\n" + canon + "\n"
        + "KNOWLEDGE RULE: environment canon is backstage truth, not automatic character knowledge. "
        + "variationPool contains optional scene motifs, never guaranteed persistent objects. "
        + "Entity spawning, item spawning, combat, stats and hidden route progress remain Core-owned.\n"
        + "VALID NEXT LEVEL TRANSITION: " + allowed + "\n"
        + transitionInstruction + "\n"
        + routeInstruction;
  }

  String scenePromptContext(JSONObject state, String action) {
    String levelKey = resolveLevelKey(state);
    int turn = Math.max(1, state == null ? 1 : state.optInt("turn", 1));
    StringBuilder out = new StringBuilder();
    out.append("LEVEL: ").append(displayNameForKey(levelKey)).append('\n');
    String location = state == null ? "" : state.optString("location", "").trim();
    if (!location.isEmpty()) out.append("LOCATION: ").append(location).append('\n');

    String palette = sceneKnowledgeContext(levelKey, turn, action);
    if (!palette.isEmpty()) {
      out.append("ENVIRONMENT PALETTE — descriptive possibilities for this Level, not spawned items/entities:\n")
          .append(palette).append('\n');
    }

    try {
      JSONObject route = normalizeRouteState(state, levelKey);
      boolean rolledThisTurn = route.optInt("lastRollTurn", -1) == turn;
      String result = rolledThisTurn ? route.optString("lastResult", "") : "";
      if ("RESET".equals(result)) {
        out.append("ROUTE FACT: this movement folded back toward familiar ground.\n");
      } else if ("SUCCESS".equals(result)) {
        out.append("ROUTE FACT: exploration progressed inside this Level; no exit was reached.\n");
      } else if ("EXIT_AVAILABLE".equals(result) || route.optBoolean("exitAvailable", false)) {
        String next = nextForKey(levelKey);
        out.append("ROUTE FACT: a real boundary may now be encountered")
            .append(next == null ? "" : " toward " + displayNameForKey(next))
            .append("; crossing still requires the player's action.\n");
      }
    } catch (Exception ignored) {}

    if (out.length() > MAX_SCENE_CONTEXT_CHARS) {
      return out.substring(0, MAX_SCENE_CONTEXT_CHARS);
    }
    return out.toString().trim();
  }

  String sceneKnowledgeContext(String levelKey, int turn, String action) {
    JSONObject bundle = knowledgeByLevelKey.get(normalizeKey(levelKey));
    if (bundle == null) return "";
    StringBuilder out = new StringBuilder();
    String category = categorizeAction(action);
    JSONArray order = knowledgeSectionOrder.length() == 0
        ? new JSONArray().put("identity").put("architecture").put("zones").put("sensory")
            .put("anomalies").put("hazards").put("navigation").put("microLocations")
            .put("environmentStates").put("environmentEvents").put("interactionRules")
            .put("actionConsequences").put("navigationPatterns").put("quietTurnPatterns")
            .put("variationPool").put("sceneSeeds")
        : knowledgeSectionOrder;

    for (int i = 0; i < order.length() && out.length() < MAX_SCENE_PALETTE_CHARS; i++) {
      String section = order.optString(i, "").trim();
      if (!sceneSectionAllowed(section) || !isSectionRelevantForCategory(section, category)) continue;
      JSONArray values = bundle.optJSONArray(section);
      if (values == null || values.length() == 0) continue;
      appendSceneSection(out, values, section, turn);
    }
    return out.toString().trim();
  }

  private static boolean sceneSectionAllowed(String section) {
    return "identity".equals(section) || "architecture".equals(section) || "zones".equals(section)
        || "sensory".equals(section) || "anomalies".equals(section) || "hazards".equals(section)
        || "navigation".equals(section) || "microLocations".equals(section)
        || "environmentStates".equals(section) || "environmentEvents".equals(section)
        || "interactionRules".equals(section) || "actionConsequences".equals(section)
        || "navigationPatterns".equals(section) || "quietTurnPatterns".equals(section)
        || "narrativeGrammar".equals(section) || "antiRepetition".equals(section)
        || "variationPool".equals(section) || "sceneSeeds".equals(section);
  }

  private static void appendSceneSection(
      StringBuilder out, JSONArray values, String section, int turn) {
    if (values == null || values.length() == 0 || out.length() >= MAX_SCENE_PALETTE_CHARS) return;
    int configured = rotatingSectionLimit(section);
    int count = Math.min(values.length(), configured > 0 ? Math.min(configured, 2) : 1);
    int start = values.length() > count
        ? Math.floorMod((Math.max(1, turn) - 1) * count + section.hashCode(), values.length())
        : 0;
    StringBuilder block = new StringBuilder();
    block.append(out.length() == 0 ? "" : "\n").append(sectionLabel(section)).append(":\n");
    int added = 0;
    for (int i = 0; i < count; i++) {
      String value = values.optString((start + i) % values.length(), "").trim();
      if (value.isEmpty()) continue;
      String line = "- " + value + "\n";
      if (out.length() + block.length() + line.length() > MAX_SCENE_PALETTE_CHARS) break;
      block.append(line);
      added++;
    }
    if (added > 0) out.append(block);
  }

  String snapshotDescriptor(JSONObject state) {
    String levelKey = resolveLevelKey(state);
    int level = parentLevelForKey(levelKey);
    JSONObject output = new JSONObject();
    try {
      output.put("level", level);
      output.put("levelKey", levelKey);
      output.put("label", displayNameForKey(levelKey));

      JSONArray assets;
      String root;
      String visualType;
      if (isMainLevelKey(levelKey)) {
        assets = snapshotsByLevel.get(level);
        root = snapshotRoot;
        visualType = visualTypeByLevel.containsKey(level) ? visualTypeByLevel.get(level) : "scene";
      } else {
        assets = snapshotsByLevelKey.get(levelKey);
        root = sublevelSnapshotRoot;
        visualType = visualTypeByLevelKey.containsKey(levelKey) ? visualTypeByLevelKey.get(levelKey) : "scene";
      }
      output.put("visualType", visualType);

      if (assets != null && assets.length() > 0) {
        int turn = Math.max(1, state.optInt("turn", 1));
        int index = Math.floorMod(turn - 1, assets.length());
        String relative = assets.optString(index, "");
        if (!relative.isEmpty()) output.put("path", "file:///android_asset/" + root + "/" + relative);
      }
    } catch (Exception ignored) {}
    return output.toString();
  }

  static int requestedLevel(int explicit, String location, int fallback) {
    int inferred = GameCoreRules.levelFromLocation(location);
    if (explicit >= 0 && explicit <= 6) {
      if (inferred >= 0 && inferred != explicit) return LEVEL_MISMATCH;
      return explicit;
    }
    return inferred >= 0 ? inferred : fallback;
  }

  static String[] levelZeroProgressionKeys() {
    return LEVEL_ZERO_PROGRESSION.clone();
  }

  /**
   * Authoritative gameplay Stage order. Sub-level decimals are graph node names, never numeric
   * stage values.
   */
  static int stageIndex(JSONObject state) {
    if (state == null) return 0;
    return stageIndexForKey(new LevelCore((Context)null, bound -> 0).resolveLevelKey(state));
  }

  static int stageIndexForKey(String levelKey) {
    String key = normalizeKey(levelKey);
    for (int i = 0; i < ALL_LEVEL_NODE_ORDER.length; i++) {
      if (ALL_LEVEL_NODE_ORDER[i].equals(key)) return i;
    }
    return 0;
  }

  static String displayName(String levelKey) {
    String key = normalizeKey(levelKey);
    switch (key) {
      case "0": return "Level 0 — The Lobby";
      case "0.1": return "Level 0.1 — Zenith Station";
      case "0.2": return "Level 0.2 — Remodeled Mess";
      case "0.5": return "Level 0.5 — Aquaclaustrophobic Infirmary";
      case "0.7": return "Level 0.7 — The Reminiscence District";
      case "manila_room": return "Manila Room";
      case "the_torment": return "The Torment";
      case "red_rooms": return "Red Rooms";
      case "1": return "Level 1 — Parking Zone";
      case "1.2": return "Level 1.2 — Concrete Garden";
      case "1.3": return "Level 1.3 — Malignance";
      case "1.5": return "Level 1.5 — Inverted";
      case "base_alpha": return "Base Alpha";
      case "traders_vault": return "Traders Vault";
      case "2": return "Level 2 — Pipe Dreams";
      case "2.1": return "Level 2.1 — Locked";
      case "3": return "Level 3 — The Electrical Station";
      case "3.5": return "Level 3.5 — Electropolis";
      case "4": return "Level 4 — The Abandoned Office";
      case "office_market": return "The Office Market";
      case "5": return "Level 5 — Terror Hotel";
      case "5.1": return "Level 5.1 — GRAND OPENING OF THE TERROR HOTEL CASINO";
      case "5.2": return "Level 5.2 — Scenic Views";
      case "5.3": return "Level 5.3 — Promethei Bibliotheca";
      case "6": return "Level 6 — Lights Out";
      case "6.1": return "Level 6.1 — The Snackrooms";
      case "6.31": return "Level 6.31 — Pierce the Veil";
      default: return levelKey == null || levelKey.trim().isEmpty() ? "Unknown Level" : levelKey;
    }
  }

  static String defaultLocation(String levelKey) {
    String key = normalizeKey(levelKey);
    switch (key) {
      case "0": return LEVEL_ZERO_START_LOCATION;
      case "0.1": return "Level 0.1 / Zenith Station";
      case "0.2": return "Level 0.2 / Remodeled Mess";
      case "0.5": return "Level 0.5 / Aquaclaustrophobic Infirmary";
      case "0.7": return "Level 0.7 / The Reminiscence District";
      case "manila_room": return "Manila Room / Level 0";
      case "the_torment": return "The Torment / Level 0";
      case "red_rooms": return "Red Rooms / Level 0";
      case "1": return "Level 1 / Parking Zone";
      case "1.2": return "Level 1.2 / Concrete Garden";
      case "1.3": return "Level 1.3 / Malignance";
      case "1.5": return "Level 1.5 / Inverted";
      case "base_alpha": return "Base Alpha / Level 1";
      case "traders_vault": return "Traders Vault / Level 1";
      case "2": return "Level 2 / Pipe Dreams";
      case "2.1": return "Level 2.1 / Locked";
      case "3": return "Level 3 / The Electrical Station";
      case "3.5": return "Level 3.5 / Electropolis";
      case "4": return "Level 4 / The Abandoned Office";
      case "office_market": return "The Office Market / Level 4";
      case "5": return "Level 5 / Terror Hotel";
      case "5.1": return "Level 5.1 / Terror Hotel Casino";
      case "5.2": return "Level 5.2 / Scenic Views";
      case "5.3": return "Level 5.3 / Promethei Bibliotheca";
      case "6": return "Level 6 / Lights Out";
      case "6.1": return "Level 6.1 / The Snackrooms";
      case "6.31": return "Level 6.31 / Pierce the Veil";
      default: return displayName(key);
    }
  }

  private JSONObject normalizeRouteState(JSONObject state, String levelKey) throws Exception {
    JSONObject route = state.optJSONObject(ROUTE_STATE);
    if (route == null || !levelKey.equals(normalizeKey(route.optString("levelKey", "")))) {
      route = newRouteStateForKey(levelKey);
      state.put(ROUTE_STATE, route);
      return route;
    }

    int streak = Math.max(0, Math.min(ROUTE_REQUIRED_STREAK, route.optInt("streak", 0)));
    boolean routeReady = streak >= ROUTE_REQUIRED_STREAK;
    route.put("level", parentLevelForKey(levelKey));
    route.put("levelKey", levelKey);
    route.put("streak", routeReady ? ROUTE_REQUIRED_STREAK : streak);
    route.put("exitAvailable", routeReady);
    state.put(ROUTE_STATE, route);
    return route;
  }

  private JSONObject newRouteStateForKey(String levelKey) throws Exception {
    return new JSONObject()
        .put("level", parentLevelForKey(levelKey))
        .put("levelKey", normalizeKey(levelKey))
        .put("streak", 0)
        .put("readinessReason", "")
        .put("exitAvailable", false)
        .put("lastRollTurn", -1)
        .put("lastResult", "");
  }

  private String resolveLevelKey(JSONObject state) {
    if (state == null) return "0";

    String explicit = normalizeKey(state.optString(LEVEL_KEY, ""));
    if (knownKey(explicit)) return explicit;

    String inferred = levelKeyFromLocation(state.optString("location", ""));
    if (knownKey(inferred)) return inferred;

    int level = state.optInt("currentLevel", -1);
    if (level >= 0 && level <= 6) return String.valueOf(level);
    return "0";
  }

  private static boolean legacyNodeTransitionAllowed(String fromKey, String toKey) {
    String from = normalizeKey(fromKey);
    String to = normalizeKey(toKey);
    if (!isKnownLevelKey(from) || !isKnownLevelKey(to)) return false;
    if (from.equals(to)) return true;

    String required = legacyNextRequiredLevelKey(from);
    if (required != null) return required.equals(to);

    int fromLevel = parentLevel(from);
    int toLevel = parentLevel(to);
    return isMainLevelKey(from) && isMainLevelKey(to)
        && GameCoreRules.levelTransitionAllowed(fromLevel, toLevel);
  }

  private static String legacyNextRequiredLevelKey(String levelKey) {
    String key = normalizeKey(levelKey);
    for (int i = 0; i < GAMEPLAY_PROGRESSION.length - 1; i++) {
      if (GAMEPLAY_PROGRESSION[i].equals(key)) return GAMEPLAY_PROGRESSION[i + 1];
    }
    return null;
  }

  private boolean knownKey(String key) {
    String normalized = normalizeKey(key);
    return levelGraph.available() ? levelGraph.contains(normalized) : isKnownLevelKey(normalized);
  }

  private int parentLevelForKey(String key) {
    String normalized = normalizeKey(key);
    int legacy = parentLevel(normalized);
    return levelGraph.available() ? levelGraph.parentLevel(normalized, legacy) : legacy;
  }

  private String displayNameForKey(String key) {
    String normalized = normalizeKey(key);
    String legacy = displayName(normalized);
    return levelGraph.available() ? levelGraph.displayName(normalized, legacy) : legacy;
  }

  private String defaultLocationForKey(String key) {
    String normalized = normalizeKey(key);
    String legacy = defaultLocation(normalized);
    return levelGraph.available() ? levelGraph.defaultLocation(normalized, legacy) : legacy;
  }

  private String nextForKey(String key) {
    String normalized = normalizeKey(key);
    if (levelGraph.available()) return levelGraph.singleNext(normalized);
    return legacyNextRequiredLevelKey(normalized);
  }

  private boolean transitionAllowedFor(String fromKey, String toKey) {
    String from = normalizeKey(fromKey);
    String to = normalizeKey(toKey);
    if (levelGraph.available()) return levelGraph.allows(from, to);
    return legacyNodeTransitionAllowed(from, to);
  }

  private static boolean isKnownLevelKey(String key) {
    String normalized = normalizeKey(key);
    for (String known : ALL_LEVEL_NODE_ORDER) {
      if (known.equals(normalized)) return true;
    }
    return false;
  }

  private static boolean isMainLevelKey(String key) {
    return normalizeKey(key).matches("[0-6]");
  }

  private static int parentLevel(String levelKey) {
    String key = normalizeKey(levelKey);
    if (key.matches("[1-6](?:\\.[0-9]+)?")) return Integer.parseInt(key.substring(0, 1));
    if ("base_alpha".equals(key) || "traders_vault".equals(key)) return 1;
    if ("office_market".equals(key)) return 4;
    return 0;
  }

  static String rawLevelKeyFromLocation(String location) {
    String text = location == null ? "" : location.trim();
    if (text.isEmpty()) return "";

    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("manila room")) return "manila_room";
    if (lower.contains("the torment") || lower.matches(".*\\btorment\\b.*")) return "the_torment";
    if (lower.contains("red rooms") || lower.contains("red room")) return "red_rooms";
    if (lower.contains("base alpha")) return "base_alpha";
    if (lower.contains("traders vault") || lower.contains("trader's vault")) return "traders_vault";
    if (lower.contains("office market")) return "office_market";

    Matcher matcher = LEVEL_TOKEN.matcher(text);
    if (!matcher.find()) return "";
    return normalizeNumericKey(matcher.group(1));
  }

  private static String levelKeyFromLocation(String location) {
    String key = rawLevelKeyFromLocation(location);
    return isKnownLevelKey(key) ? key : "";
  }

  private static String normalizeNumericKey(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.matches("[0-6]")) return String.valueOf(Integer.parseInt(value));
    if (value.matches("0\\.[0-9]+")) return value;
    return value;
  }

  private static String normalizeKey(String raw) {
    String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    key = key.replace('-', '_').replace(' ', '_');
    if ("manila".equals(key) || "manila_room".equals(key)) return "manila_room";
    if ("torment".equals(key) || "the_torment".equals(key)) return "the_torment";
    if ("red_room".equals(key) || "red_rooms".equals(key)) return "red_rooms";
    if ("basealpha".equals(key) || "base_alpha".equals(key)) return "base_alpha";
    if ("tradersvault".equals(key) || "traders_vault".equals(key) || "trader's_vault".equals(key))
      return "traders_vault";
    if ("officemarket".equals(key) || "office_market".equals(key) || "the_office_market".equals(key))
      return "office_market";
    if (key.matches("0+[1-6]")) return String.valueOf(Integer.parseInt(key));
    if ("00".equals(key)) return "0";
    return normalizeNumericKey(key);
  }

  private int nextRoll(int bound) {
    return nextRoll(rng, bound);
  }

  private static int nextRoll(IntRng source, int bound) {
    int value = source.nextInt(bound);
    if (value < 0 || value >= bound) {
      throw new IllegalStateException("RNG returned an out-of-range value");
    }
    return value;
  }

  private void loadLevelKnowledge(Context context) {
    try {
      loadLevelKnowledgeText(readAsset(context, LEVEL_KNOWLEDGE_ASSET));
    } catch (Exception ignored) {}
  }

  private void loadLevelKnowledgeText(String raw) {
    if (raw == null || raw.trim().isEmpty()) return;
    try {
      JSONObject root = new JSONObject(raw);
      if (root.optInt("schemaVersion", 0) < 2) return;

      JSONArray order = root.optJSONArray("sectionOrder");
      if (order != null) {
        for (int i = 0; i < order.length(); i++) {
          String section = order.optString(i, "").trim();
          if (!section.isEmpty()) knowledgeSectionOrder.put(section);
        }
      }

      JSONObject levels = root.optJSONObject("levels");
      if (levels == null) return;
      java.util.Iterator<String> levelKeys = levels.keys();
      while (levelKeys.hasNext()) {
        String key = levelKeys.next();
        JSONObject bundle = levels.optJSONObject(key);
        if (bundle != null) knowledgeByLevelKey.put(key, new JSONObject(bundle.toString()));
      }
    } catch (Exception ignored) {}
  }

  static String categorizeAction(String action) {
    if (action == null) return "EXPLORATION";
    String lower = action.toLowerCase(Locale.ROOT).trim();
    if (lower.contains("nghe") || lower.contains("nhìn") || lower.contains("quan sát") ||
        lower.contains("soi") || lower.contains("lắng nghe") || lower.contains("look") ||
        lower.contains("listen") || lower.contains("observe") || lower.contains("examine")) {
      return "OBSERVE";
    }
    if (lower.contains("kiểm tra") || lower.contains("tìm") || lower.contains("dùng") ||
        lower.contains("mở") || lower.contains("chạm") || lower.contains("sờ") ||
        lower.contains("inspect") || lower.contains("search") || lower.contains("use")) {
      return "INSPECT";
    }
    if (lower.contains("nghỉ") || lower.contains("ngủ") || lower.contains("tu luyện") ||
        lower.contains("điều hòa") || lower.contains("nghỉ ngơi") || lower.contains("rest") ||
        lower.contains("sleep") || lower.contains("meditate") || lower.contains("camp")) {
      return "REST";
    }
    if (lower.contains("quái") || lower.contains("đối đầu") || lower.contains("sinh thể") ||
        lower.contains("gặp") || lower.contains("encounter") || lower.contains("combat") ||
        lower.contains("entity")) {
      return "ENCOUNTER";
    }
    return "EXPLORATION";
  }

  private static boolean isSectionRelevantForCategory(String section, String category) {
    if ("identity".equals(section) || "canonicalFacts".equals(section)
        || "gmConstraints".equals(section) || "gameplayOverride".equals(section)
        || "forbiddenInventions".equals(section)) {
      return true;
    }

    switch (category) {
      case "OBSERVE":
        return "architecture".equals(section) || "sensory".equals(section)
            || "anomalies".equals(section) || "evidenceRules".equals(section)
            || "microLocations".equals(section) || "environmentEvents".equals(section)
            || "quietTurnPatterns".equals(section);
      case "INSPECT":
        return "interactionRules".equals(section) || "actionConsequences".equals(section)
            || "hazards".equals(section) || "resources".equals(section)
            || "evidenceRules".equals(section) || "persistenceRules".equals(section);
      case "REST":
        return "hazards".equals(section) || "persistenceRules".equals(section)
            || "environmentStates".equals(section) || "quietTurnPatterns".equals(section)
            || "hazardEscalation".equals(section);
      case "ENCOUNTER":
        return "encounterStaging".equals(section) || "hazardEscalation".equals(section)
            || "entities".equals(section) || "hazards".equals(section)
            || "zones".equals(section) || "evidenceRules".equals(section);
      case "EXPLORATION":
      default:
        return "architecture".equals(section) || "navigation".equals(section)
            || "entrancesExits".equals(section) || "navigationPatterns".equals(section)
            || "routeProgressionCues".equals(section) || "zones".equals(section)
            || "microLocations".equals(section) || "sceneSeeds".equals(section)
            || "variationPool".equals(section);
    }
  }

  String knowledgeContext(String levelKey, int turn) {
    return knowledgeContext(levelKey, turn, "");
  }

  String knowledgeContext(String levelKey, int turn, String action) {
    JSONObject bundle = knowledgeByLevelKey.get(normalizeKey(levelKey));
    if (bundle == null) return "";

    StringBuilder out = new StringBuilder();
    String name = bundle.optString("name", displayNameForKey(levelKey)).trim();
    if (!name.isEmpty()) out.append("NAME: ").append(name).append('\n');

    String category = categorizeAction(action);
    JSONArray order = knowledgeSectionOrder;
    if (order.length() == 0) {
      order = new JSONArray()
          .put("identity").put("architecture").put("zones").put("sensory")
          .put("anomalies").put("hazards").put("resources").put("entities")
          .put("navigation").put("entrancesExits").put("gameplayOverride")
          .put("gmConstraints").put("variationPool").put("canonicalFacts")
          .put("forbiddenInventions");
    }

    String[] coreSections = {
        "identity", "canonicalFacts", "gmConstraints", "gameplayOverride", "forbiddenInventions"
    };
    for (String section : coreSections) {
      JSONArray values = bundle.optJSONArray(section);
      if (values == null || values.length() == 0) continue;
      appendSectionWithinBudget(out, values, section, turn);
    }

    for (int i = 0; i < order.length() && out.length() < MAX_KNOWLEDGE_CONTEXT_CHARS; i++) {
      String section = order.optString(i, "").trim();
      if (isCoreNarrativeSection(section) || !isSectionRelevantForCategory(section, category)) continue;
      JSONArray values = bundle.optJSONArray(section);
      if (values == null || values.length() == 0) continue;
      appendSectionWithinBudget(out, values, section, turn);
    }
    return out.toString().trim();
  }

  private static boolean isCoreNarrativeSection(String section) {
    return "identity".equals(section) || "canonicalFacts".equals(section)
        || "gmConstraints".equals(section) || "gameplayOverride".equals(section)
        || "forbiddenInventions".equals(section);
  }

  private static void appendSectionWithinBudget(
      StringBuilder out, JSONArray values, String section, int turn) {
    int size = values.length();
    if (size == 0 || out.length() >= MAX_KNOWLEDGE_CONTEXT_CHARS) return;
    int configuredLimit = rotatingSectionLimit(section);
    int count = configuredLimit > 0 ? Math.min(configuredLimit, size) : size;
    int start = !isCoreNarrativeSection(section) && configuredLimit > 0 && size > count
        ? Math.floorMod((Math.max(1, turn) - 1) * count + section.hashCode(), size)
        : 0;

    String header = (out.length() > 0 ? "\n" : "") + sectionLabel(section) + ":\n";
    StringBuilder block = new StringBuilder(header);
    int added = 0;
    for (int i = 0; i < count; i++) {
      String value = values.optString((start + i) % size, "").trim();
      if (value.isEmpty()) continue;
      String line = "- " + value + "\n";
      if (out.length() + block.length() + line.length() > MAX_KNOWLEDGE_CONTEXT_CHARS) break;
      block.append(line);
      added++;
    }
    if (added > 0) out.append(block);
  }

  private static int rotatingSectionLimit(String section) {
    if ("identity".equals(section)) return 2;
    if ("canonicalFacts".equals(section)) return 2;
    if ("gmConstraints".equals(section)) return 3;
    if ("gameplayOverride".equals(section)) return 2;
    if ("forbiddenInventions".equals(section)) return 2;
    if ("architecture".equals(section)) return 3;
    if ("zones".equals(section)) return 2;
    if ("sensory".equals(section)) return 3;
    if ("anomalies".equals(section)) return 2;
    if ("hazards".equals(section)) return 3;
    if ("resources".equals(section)) return 2;
    if ("entities".equals(section)) return 2;
    if ("navigation".equals(section)) return 3;
    if ("entrancesExits".equals(section)) return 2;
    if ("variationPool".equals(section)) return 4;
    if ("microLocations".equals(section)) return 2;
    if ("environmentStates".equals(section)) return 2;
    if ("environmentEvents".equals(section)) return 2;
    if ("interactionRules".equals(section)) return 3;
    if ("actionConsequences".equals(section)) return 2;
    if ("persistenceRules".equals(section)) return 2;
    if ("evidenceRules".equals(section)) return 3;
    if ("navigationPatterns".equals(section)) return 2;
    if ("routeProgressionCues".equals(section)) return 2;
    if ("encounterStaging".equals(section)) return 3;
    if ("hazardEscalation".equals(section)) return 2;
    if ("quietTurnPatterns".equals(section)) return 2;
    if ("sceneSeeds".equals(section)) return 6;
    return 0;
  }

  private static String sectionLabel(String section) {
    if ("entrancesExits".equals(section)) return "ENTRANCES / EXITS";
    if ("gameplayOverride".equals(section)) return "BACKROOMSV2 OVERRIDES";
    if ("gmConstraints".equals(section)) return "GM CONSTRAINTS";
    if ("variationPool".equals(section)) return "VARIATION POOL";
    return section == null ? "" : section.replace('_', ' ').toUpperCase(Locale.ROOT);
  }

  private void loadLegacyKnowledge(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, LEGACY_KNOWLEDGE_ASSET));
      JSONArray records = root.optJSONArray("records");
      if (records == null) return;
      for (int i = 0; i < records.length(); i++) {
        JSONObject record = records.optJSONObject(i);
        if (record == null || !"LEVEL".equals(record.optString("domain"))) continue;
        String id = record.optString("id", "");
        if (!id.startsWith("LEVEL.")) continue;
        String levelKey = normalizeKnowledgeKey(id.substring("LEVEL.".length()));
        if (isKnownLevelKey(levelKey)) legacyCanonByLevelKey.put(levelKey, record.optString("text", ""));
      }
    } catch (Exception ignored) {}
  }

  private static String normalizeKnowledgeKey(String raw) {
    String key = normalizeKey(raw);
    if ("manila_room".equals(key) || "the_torment".equals(key) || "red_rooms".equals(key)) return key;
    if (raw != null && raw.matches("0[0-6]")) return String.valueOf(Integer.parseInt(raw));
    return key;
  }

  private void loadSnapshotManifest(Context context) {
    try {
      loadSnapshotManifestText(readAsset(context, SNAPSHOT_MANIFEST_ASSET));
    } catch (Exception ignored) {}
  }

  void loadSnapshotManifestText(String manifestText) {
    try {
      JSONObject manifest = new JSONObject(manifestText);
      snapshotRoot = manifest.optString("root", snapshotRoot);
      sublevelSnapshotRoot = manifest.optString("sublevelRoot", sublevelSnapshotRoot);

      snapshotsByLevel.clear();
      visualTypeByLevel.clear();
      JSONObject levels = manifest.optJSONObject("levels");
      if (levels != null) {
        for (int level = 0; level <= 6; level++) {
          JSONObject entry = levels.optJSONObject(String.valueOf(level));
          if (entry == null) continue;
          JSONArray assets = entry.optJSONArray("assets");
          if (assets != null && assets.length() > 0) snapshotsByLevel.put(level, assets);
          visualTypeByLevel.put(level, entry.optString("visualType", "scene"));
        }
      }

      snapshotsByLevelKey.clear();
      visualTypeByLevelKey.clear();
      JSONObject sublevels = manifest.optJSONObject("sublevels");
      if (sublevels != null) {
        for (String levelKey : LEVEL_ZERO_PROGRESSION) {
          if (isMainLevelKey(levelKey)) continue;
          JSONObject entry = sublevels.optJSONObject(levelKey);
          if (entry == null) continue;
          JSONArray assets = entry.optJSONArray("assets");
          if (assets != null && assets.length() > 0) snapshotsByLevelKey.put(levelKey, assets);
          visualTypeByLevelKey.put(levelKey, entry.optString("visualType", "scene"));
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
}
