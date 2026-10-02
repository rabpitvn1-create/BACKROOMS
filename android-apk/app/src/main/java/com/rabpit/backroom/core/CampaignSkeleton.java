package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Replayable campaign-pressure projection restored from PR #160.
 *
 * It owns coarse pacing pressure only. It cannot create candidates, unlock eligibility,
 * schedule encounters, mutate authoritative state or encode plot/chapter/scene progression.
 */
final class CampaignSkeleton {
  static final String ROOT_KEY = "campaignSkeleton";
  static final int SCHEMA_VERSION = 1;

  private static final String[] AXES = {
      "survival_stability", "resource_dependency", "entity_attention", "social_entanglement",
      "world_knowledge", "environmental_exposure", "long_term_consequence"
  };

  private CampaignSkeleton() {}

  /**
   * Normalizes the current projection and discards the retired pre-v1 "skeleton" field.
   * Legacy projection data is not migrated into current authority.
   */
  static boolean normalize(JSONObject root, JSONObject state) throws Exception {
    JSONObject current = root.optJSONObject(ROOT_KEY);
    boolean reset = false;

    if (current == null) {
      current = empty();
      reset = true;
    }

    if (!hasAllAxes(current)) {
      current = empty();
      reset = true;
    } else {
      normalizeShape(current);
    }

    root.put(ROOT_KEY, current);
    root.remove("skeleton");
    return reset;
  }

  static void rebuild(JSONObject root, JSONObject state) throws Exception {
    JSONObject skeleton = empty();
    JSONObject axes = skeleton.getJSONObject("axes");
    JSONArray commits = root.optJSONArray("commitLog");

    if (commits != null) {
      for (int i = 0; i < commits.length(); i++) {
        JSONObject commit = commits.optJSONObject(i);
        if (commit == null) continue;
        int commitTurn = Math.max(1, commit.optInt("turn", 1));
        decayResidueAxes(axes, commitTurn);

        double entityDelta = 0.0d;
        double socialDelta = 0.0d;
        double knowledgeDelta = 0.0d;
        double environmentDelta = 0.0d;
        JSONArray events = commit.optJSONArray("events");
        if (events != null) {
          for (int e = 0; e < events.length(); e++) {
            JSONObject event = events.optJSONObject(e);
            if (event == null) continue;
            String type = event.optString("eventType", "");
            if (type.startsWith("ENTITY_") || type.startsWith("COMBAT_")) entityDelta += 0.08d;
            if (type.startsWith("CHARACTER_")) socialDelta += 0.08d;
            if (type.startsWith("ROUTE_") || "LEVEL_TRANSITIONED".equals(type)) {
              knowledgeDelta += 0.04d;
              environmentDelta += 0.03d;
            }
          }
        }
        bump(axes, "entity_attention", Math.min(0.12d, entityDelta), commitTurn);
        bump(axes, "social_entanglement", Math.min(0.12d, socialDelta), commitTurn);
        bump(axes, "world_knowledge", Math.min(0.12d, knowledgeDelta), commitTurn);
        bump(axes, "environmental_exposure", Math.min(0.12d, environmentDelta), commitTurn);
      }
    }

    int currentTurn = Math.max(1, state == null ? 1 : state.optInt("turn", 1));
    double stability = survivalStability(state);
    setAxis(axes, "survival_stability", stability, currentTurn);
    setAxis(axes, "resource_dependency", 1.0d - stability, currentTurn);
    setAxis(axes, "long_term_consequence", longTermConsequence(root, currentTurn), currentTurn);
    skeleton.put("derivedFromCommitSeq", Math.max(0, root.optInt("commitSequence", 0)));
    skeleton.put("playerImpact", playerImpact(root));
    root.put(ROOT_KEY, skeleton);
  }

  static double axisScore(JSONObject root, String name) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    JSONObject axes = skeleton == null ? null : skeleton.optJSONObject("axes");
    JSONObject axis = axes == null ? null : axes.optJSONObject(name);
    return axis == null ? 0.0d : clamp(axis.optDouble("score", 0.0d), 0.0d, 1.0d);
  }

  private static JSONObject empty() throws Exception {
    JSONObject axes = new JSONObject();
    for (String name : AXES) {
      axes.put(name, new JSONObject()
          .put("score", 0.0d)
          .put("trend", "STABLE")
          .put("lastUpdatedTurn", 0));
    }
    return new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("axes", axes)
        .put("derivedFromCommitSeq", 0)
        .put("playerImpact", 0.0d);
  }

  private static boolean hasAllAxes(JSONObject skeleton) {
    if (skeleton == null) return false;
    JSONObject axes = skeleton.optJSONObject("axes");
    if (axes == null) return false;
    for (String name : AXES) if (!(axes.opt(name) instanceof JSONObject)) return false;
    return true;
  }

  private static void normalizeShape(JSONObject skeleton) throws Exception {
    JSONObject axes = skeleton.getJSONObject("axes");
    for (String name : AXES) {
      JSONObject axis = axes.getJSONObject(name);
      axis.put("score", clamp(axis.optDouble("score", 0.0d), 0.0d, 1.0d))
          .put("lastUpdatedTurn", Math.max(0, axis.optInt("lastUpdatedTurn", 0)));
      String trend = axis.optString("trend", "STABLE");
      if (!("RISING".equals(trend) || "FALLING".equals(trend) || "STABLE".equals(trend))) {
        axis.put("trend", "STABLE");
      }
    }
    skeleton.put("schemaVersion", SCHEMA_VERSION)
        .put("derivedFromCommitSeq", Math.max(0, skeleton.optInt("derivedFromCommitSeq", 0)))
        .put("playerImpact", clamp(skeleton.optDouble("playerImpact", 0.0d), 0.0d, 1.0d));
  }

  private static void decayResidueAxes(JSONObject axes, int turn) throws Exception {
    decayAxis(axes, "entity_attention", turn, 18.0d);
    decayAxis(axes, "social_entanglement", turn, 36.0d);
    decayAxis(axes, "world_knowledge", turn, 96.0d);
    decayAxis(axes, "environmental_exposure", turn, 24.0d);
  }

  private static void decayAxis(JSONObject axes, String name, int turn, double halfLifeTurns)
      throws Exception {
    JSONObject axis = axes.getJSONObject(name);
    int previousTurn = Math.max(0, axis.optInt("lastUpdatedTurn", 0));
    int deltaTurns = Math.max(0, turn - previousTurn);
    if (deltaTurns == 0) return;
    double previous = clamp(axis.optDouble("score", 0.0d), 0.0d, 1.0d);
    double factor = Math.pow(0.5d, deltaTurns / Math.max(1.0d, halfLifeTurns));
    setAxis(axes, name, previous * factor, turn);
  }

  private static void bump(JSONObject axes, String name, double delta, int turn) throws Exception {
    if (delta <= 0.0d) return;
    JSONObject axis = axes.getJSONObject(name);
    setAxis(axes, name,
        axis.optDouble("score", 0.0d) + Math.min(0.12d, Math.max(0.0d, delta)), turn);
  }

  private static void setAxis(JSONObject axes, String name, double score, int turn) throws Exception {
    JSONObject axis = axes.getJSONObject(name);
    double previous = clamp(axis.optDouble("score", 0.0d), 0.0d, 1.0d);
    double next = clamp(score, 0.0d, 1.0d);
    String trend = next > previous + 0.0001d ? "RISING"
        : next < previous - 0.0001d ? "FALLING" : "STABLE";
    axis.put("score", next)
        .put("trend", trend)
        .put("lastUpdatedTurn", Math.max(0, turn));
  }

  private static double survivalStability(JSONObject state) {
    try {
      JSONObject survival = state == null ? null : state.optJSONObject(SurvivalCore.ROOT_KEY);
      JSONObject chars = survival == null ? null : survival.optJSONObject(SurvivalCore.CHARACTERS_KEY);
      JSONObject player = chars == null ? null : chars.optJSONObject("cao_minh");
      if (player == null) return 1.0d;
      long elapsed = SurvivalCore.elapsedMinutes(state);
      long food = Math.max(0L, elapsed - player.optLong("lastFoodMinute", elapsed));
      long water = Math.max(0L, elapsed - player.optLong("lastWaterMinute", elapsed));
      long rest = Math.max(0L, elapsed - player.optLong("lastRestMinute", elapsed));
      double worst = Math.max(
          (double) food / SurvivalCore.FOOD_CRITICAL_MINUTES,
          Math.max((double) water / SurvivalCore.WATER_CRITICAL_MINUTES,
              (double) rest / SurvivalCore.REST_CRITICAL_MINUTES));
      return clamp(1.0d - worst, 0.0d, 1.0d);
    } catch (Exception ignored) {
      return 1.0d;
    }
  }

  private static double longTermConsequence(JSONObject root, int turn) {
    JSONArray archive = root == null ? null : root.optJSONArray("threadArchive");
    if (archive == null || archive.length() == 0) return 0.0d;
    int eligible = 0;
    for (int i = 0; i < archive.length(); i++) {
      JSONObject residue = archive.optJSONObject(i);
      if (residue == null) continue;
      String policy = residue.optString("resurfacePolicy", "NEVER");
      if ("PERSISTENT".equals(policy)) eligible++;
      else if ("DECAYING".equals(policy)
          && turn <= residue.optInt("resolvedTurn", 0)
              + residue.optInt("decayWindowTurns", 0)) eligible++;
      else if ("CONDITIONAL".equals(policy)) eligible++;
    }
    return clamp(eligible / 5.0d, 0.0d, 1.0d);
  }

  private static double playerImpact(JSONObject root) {
    JSONArray facts = root == null ? null : root.optJSONArray("historicalFacts");
    if (facts == null) return 0.0d;
    long score = 0L;
    for (int i = 0; i < facts.length(); i++) {
      JSONObject fact = facts.optJSONObject(i);
      if (fact == null || !"player".equals(fact.optString("causedBy"))
          || !fact.optBoolean("impactEligible", true)) continue;
      switch (fact.optString("impactScope", "LOCAL")) {
        case "PERSISTENT_WORLD": score += 27L; break;
        case "REGIONAL": score += 9L; break;
        case "SOCIAL": score += 3L; break;
        default: score += 1L; break;
      }
    }
    return clamp(score / (score + 27.0d), 0.0d, 1.0d);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
