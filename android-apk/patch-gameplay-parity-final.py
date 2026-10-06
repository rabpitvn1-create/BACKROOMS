from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

start = text.index("  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {\n")
end = text.index("\n  private boolean rollSuccess(JSONObject rolls, String key) {", start)

replacement = r'''  private JSONObject thresholdRoll(String label, int max, int threshold, boolean eligible, String suffix) throws Exception {
    JSONObject result = new JSONObject()
      .put("label", label)
      .put("dice", threshold >= max ? "none" : "d" + max)
      .put("max", max)
      .put("threshold", threshold)
      .put("eligible", eligible && threshold > 0);
    double percent = max > 0 ? (threshold * 100.0 / max) : 0.0;
    result.put("chancePercent", percent).put("chance", String.format(java.util.Locale.ROOT, "%.4f%%%s", percent, suffix == null ? "" : suffix));
    if (!eligible || threshold <= 0) return result.put("roll", JSONObject.NULL).put("success", false);
    if (threshold >= max) return result.put("roll", JSONObject.NULL).put("success", true).put("guaranteedByState", true);
    int roll = GAME_RNG.nextInt(max) + 1;
    return result.put("roll", roll).put("success", roll <= threshold);
  }

  private int exitThresholdAndroid(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) return 10;
    int explicit = flags.optInt("exitChanceThreshold", -1);
    if (explicit >= 0 && explicit <= 10000) return explicit;
    String progress = flags.optString("exitProgress", "");
    JSONObject exploration = flags.optJSONObject("exploration");
    if (progress.isEmpty() && exploration != null) progress = exploration.optString("exitProgress", "");
    String upper = progress.toUpperCase(java.util.Locale.ROOT);
    if (containsAny(upper, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE")) return 10000;
    if (containsAny(upper, "NEAR", "ALMOST", "VERY STRONG")) return 150;
    if (containsAny(upper, "STRONG", "CORRECT ROUTE")) return 100;
    if (containsAny(upper, "CLUE", "CANDIDATE", "OPENED", "OBSERVED", "TRACKED")) return 50;
    return 10;
  }

  private boolean reunionEligibleAndroid(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    if (record == null || !record.optBoolean("exists", true)) return false;
    if (partyHas(state, key) || flagSpawned(state, key)) return false;
    if (record.has("reunionEligible") && !record.optBoolean("reunionEligible", true)) return false;
    String continuity = record.optString("continuity", "").toUpperCase(java.util.Locale.ROOT);
    return continuity.isEmpty() || containsAny(continuity, "SEPARATED", "LOST", "UNKNOWN");
  }

  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] entityThresholds = {5, 200, 350, 350, 10, 400, 5};
    int[] lootThresholds = {35, 120, 100, 150, 180, 100, 45};
    int[] waterThresholds = {20, 70, 35, 20, 120, 60, 35};

    String a = lower(action);
    boolean physical = containsAny(a, "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "cúi", "nhìn vào", "bò", "nhảy", "đào", "tháo", "đập", "vượt", "đi qua");
    boolean search = containsAny(a, "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo", "quan sát kỹ", "rà");
    boolean water = containsAny(a, "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain");
    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");

    JSONObject flags = state.optJSONObject("flags");
    boolean survivorAllowed = flags == null || flags.optBoolean("survivorEncountersAllowed", true);
    boolean entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true);
    JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
    boolean madGodEligible = search && (madGod == null || !madGod.optBoolean("spawned", false)) && (flags == null || flags.optBoolean("madGodDiscoveryAllowed", true));

    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 1000000, 25, reunionEligibleAndroid(state, "syvial"), ""));
    rolls.put("hazard", thresholdRoll("hazard", 10000, hazardThresholds[level], physical, ""));
    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";
    rolls.put("entityEncounter", thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix));
    rolls.put("loot", thresholdRoll("loot", 10000, lootThresholds[level], search, ""));
    rolls.put("madGodSet", thresholdRoll("madGodSet", 10000, 1, madGodEligible, " UR+ UNIQUE discovery"));
    rolls.put("almondWater", thresholdRoll("almondWater", 10000, waterThresholds[level], search && water, ""));

    int exitThreshold = exitThresholdAndroid(state);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search), " discovery clue");
    rolls.put("exitProbe", exitProbe);
    // Compatibility alias for the older Android reducer. Both keys point to the exact same locked result; no reroll occurs.
    rolls.put("levelExit", new JSONObject(exitProbe.toString()).put("label", "levelExit"));
    return rolls;
  }
'''

text = text[:start] + replacement + text[end:]

for marker in [
    'thresholdRoll("survivor", 10000, 200',
    'thresholdRoll("irisReunion", 1000000, 25',
    'int[] entityThresholds = {5, 200, 350, 350, 10, 400, 5}',
    'int[] lootThresholds = {35, 120, 100, 150, 180, 100, 45}',
    'rolls.put("hazard"',
    'rolls.put("exitProbe", exitProbe)',
    'rolls.put("levelExit", new JSONObject(exitProbe.toString())',
]:
    if marker not in text:
        raise RuntimeError(f"Android gameplay parity marker missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Android gameplay dice aligned with server canon: Exit probe progression chances halved; follower bonuses unchanged.")
