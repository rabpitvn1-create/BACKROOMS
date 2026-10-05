package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Narrative Architecture V2 state owner.
 *
 * FACTS are append-only committed history. PLAN is replaceable only at a loading/edit boundary.
 * AI proposals never write gameplay state directly; this class validates and projects them.
 */
final class NarrativeChapterCore {
  static final String ROOT_KEY = "narrativeV2";
  static final String CHOICE_PREFIX = "__narrative:choice:";
  private static final int MAX_BEATS = 4;
  private static final int MAX_MISSIONS = 6;
  private static final int MAX_FACTS = 64;
  private static final int MAX_RECENT = 8;

  boolean enabled(JSONObject state) {
    JSONObject root = state == null ? null : state.optJSONObject(ROOT_KEY);
    return root != null && root.optBoolean("enabled", false);
  }

  void startNewGame(JSONObject state) throws Exception {
    JSONObject root = new JSONObject()
        .put("enabled", true)
        .put("architecture", "NARRATIVE_V2")
        .put("levelKey", levelKey(state))
        .put("actIndex", 1)
        .put("editRevision", 0)
        .put("loadingRequired", true)
        .put("loadingState", "PENDING")
        .put("actLocked", false)
        .put("pendingLevelAdvance", false)
        .put("missionBoard", new JSONArray())
        .put("survivors", new JSONArray())
        .put("facts", new JSONArray())
        .put("threads", new JSONArray())
        .put("evidence", new JSONArray())
        .put("recentChoices", new JSONArray())
        .put("recentPatterns", new JSONArray())
        .put("currentChoices", new JSONArray())
        .put("endingTrajectory", "NEUTRAL")
        .put("lastChoiceResult", new JSONObject());
    state.put(ROOT_KEY, root);
    if (state.optJSONArray("narrativeMemory") == null) state.put("narrativeMemory", new JSONArray());
  }

  void normalizeState(JSONObject state) throws Exception {
    if (!enabled(state)) return;
    JSONObject root = state.getJSONObject(ROOT_KEY);
    if (root.optJSONArray("missionBoard") == null) root.put("missionBoard", new JSONArray());
    if (root.optJSONArray("survivors") == null) root.put("survivors", new JSONArray());
    if (root.optJSONArray("facts") == null) root.put("facts", new JSONArray());
    if (root.optJSONArray("threads") == null) root.put("threads", new JSONArray());
    if (root.optJSONArray("evidence") == null) root.put("evidence", new JSONArray());
    if (root.optJSONArray("recentChoices") == null) root.put("recentChoices", new JSONArray());
    if (root.optJSONArray("recentPatterns") == null) root.put("recentPatterns", new JSONArray());
    if (root.optJSONArray("currentChoices") == null) root.put("currentChoices", new JSONArray());
    if (root.optJSONObject("lastChoiceResult") == null) root.put("lastChoiceResult", new JSONObject());
    root.put("actIndex", Math.max(1, root.optInt("actIndex", 1)));
    root.put("editRevision", Math.max(0, root.optInt("editRevision", 0)));
    root.put("levelKey", root.optString("levelKey", levelKey(state)));
    if (state.optJSONArray("narrativeMemory") == null) state.put("narrativeMemory", new JSONArray());
  }

  boolean loadingRequired(JSONObject state) {
    JSONObject root = state == null ? null : state.optJSONObject(ROOT_KEY);
    return root != null && root.optBoolean("loadingRequired", false);
  }

  boolean pendingLevelAdvance(JSONObject state) {
    JSONObject root = state == null ? null : state.optJSONObject(ROOT_KEY);
    return root != null && root.optBoolean("pendingLevelAdvance", false);
  }

  void beginNextLevel(JSONObject state) throws Exception {
    JSONObject root = state.getJSONObject(ROOT_KEY);
    root.put("levelKey", levelKey(state))
        .put("actIndex", 1)
        .put("loadingRequired", true)
        .put("loadingState", "PENDING")
        .put("actLocked", false)
        .put("pendingLevelAdvance", false)
        .put("missionBoard", new JSONArray())
        .put("survivors", new JSONArray())
        .put("facts", new JSONArray())
        .put("threads", new JSONArray())
        .put("evidence", new JSONArray())
        .put("recentChoices", new JSONArray())
        .put("recentPatterns", new JSONArray())
        .put("currentChoices", new JSONArray())
        .put("endingTrajectory", "NEUTRAL")
        .put("lastChoiceResult", new JSONObject());
    root.remove("skeleton");
    root.remove("spawnBudget");
    root.remove("assetManifest");
    root.remove("levelResult");
  }

  JSONObject prepareLoading(JSONObject state, JSONArray entityCandidates) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    if (!root.optBoolean("loadingRequired", false)) {
      return new JSONObject()
          .put("needsMissionBoard", root.getJSONArray("missionBoard").length() == 0)
          .put("context", situationSnapshot(state));
    }

    int act = Math.max(1, root.optInt("actIndex", 1));
    int revision = Math.max(0, root.optInt("editRevision", 0));
    JSONObject budget = root.optJSONObject("spawnBudget");
    if (budget == null || budget.optInt("actIndex", -1) != act) {
      budget = rollBudget(state, entityCandidates, act, revision);
      root.put("spawnBudget", budget);
    }
    root.put("assetManifest", buildAssetManifest(state, budget))
        .put("loadingState", "PLANNING")
        .put("actLocked", true);
    return new JSONObject()
        .put("needsMissionBoard", root.getJSONArray("missionBoard").length() == 0)
        .put("context", situationSnapshot(state));
  }

  void commitEdit(JSONObject state, JSONObject missionProposal, JSONObject directorProposal) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    if (!root.optBoolean("loadingRequired", false)) return;

    if (root.getJSONArray("missionBoard").length() == 0) {
      JSONObject mission = validateMissionProposal(state, missionProposal);
      root.put("missionBoard", mission.getJSONArray("missions"));
      root.put("chapterActTarget", mission.getInt("chapterActTarget"));
    }

    JSONObject plan = validatePlan(state, directorProposal);
    JSONObject skeleton = new JSONObject()
        .put("facts", new JSONArray(root.getJSONArray("facts").toString()))
        .put("plan", plan)
        .put("currentBeat", 0)
        .put("scenePurpose", plan.optString("scenePurpose", ""))
        .put("missionLinks", collectLinks(plan, "missionLinks"))
        .put("threadLinks", collectLinks(plan, "threadLinks"))
        .put("choicePoint", new JSONObject())
        .put("constraints", new JSONArray()
            .put("FACTS_IMMUTABLE")
            .put("PLAN_REWRITABLE_ONLY_AT_LOADING")
            .put("CORE_AUTHORITY")
            .put("NO_ENTITY_OR_CHEST_OUTSIDE_BUDGET")
            .put("EXACTLY_THREE_CHOICES"))
        .put("climaxTarget", plan.optString("climaxTarget", ""));
    root.put("skeleton", skeleton)
        .put("loadingRequired", false)
        .put("loadingState", "READY")
        .put("actLocked", false)
        .put("editRevision", root.optInt("editRevision", 0) + 1)
        .put("lastChoiceResult", new JSONObject());
    root.put("currentChoices", choicesForBeat(plan.optJSONArray("beats"), 0));
    root.put("endingTrajectory", evaluateTrajectory(root));
  }

  JSONArray currentChoices(JSONObject state) throws Exception {
    if (!enabled(state)) return new JSONArray();
    normalizeState(state);
    return new JSONArray(state.getJSONObject(ROOT_KEY).getJSONArray("currentChoices").toString());
  }

  JSONObject currentBeatTrigger(JSONObject state) throws Exception {
    if (!enabled(state)) return new JSONObject();
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject skeleton = root.optJSONObject("skeleton");
    JSONObject plan = skeleton == null ? null : skeleton.optJSONObject("plan");
    JSONArray beats = plan == null ? null : plan.optJSONArray("beats");
    int index = skeleton == null ? -1 : skeleton.optInt("currentBeat", -1);
    if (beats == null || index < 0 || index >= beats.length()) return new JSONObject();
    JSONObject beat = beats.optJSONObject(index);
    if (beat == null) return new JSONObject();

    JSONObject trigger = new JSONObject().put("beatId", beat.optString("id", ""));
    String entityKey = beat.optString("entityKey", "").trim();
    if (!entityKey.isEmpty() && !resourceUsed(root, "entities", entityKey, -1)) {
      trigger.put("entityKey", entityKey)
          .put("entityMode", beat.optString("entityMode", "PRESSURE"));
    }
    if (beat.has("chestSlot")) {
      int slot = beat.optInt("chestSlot", -1);
      if (slot >= 0 && !resourceUsed(root, "chests", "", slot)) trigger.put("chestSlot", slot);
    }
    JSONObject survivor = beat.optJSONObject("survivor");
    if (survivor != null && !survivorKnown(root, survivor.optString("id", ""))) {
      trigger.put("survivor", new JSONObject(survivor.toString()));
    }
    return trigger;
  }

  void consumeBeatTrigger(JSONObject state, JSONObject trigger) throws Exception {
    if (trigger == null || trigger.length() == 0) return;
    JSONObject root = state.getJSONObject(ROOT_KEY);
    String entityKey = trigger.optString("entityKey", "").trim();
    if (!entityKey.isEmpty()) {
      markResourceUsed(root, "entities", entityKey, -1);
      JSONArray active = root.optJSONArray("activeEntities");
      if (active == null) active = new JSONArray();
      if (!containsString(active, entityKey)) active.put(entityKey);
      root.put("activeEntities", active);
    }
    int chestSlot = trigger.optInt("chestSlot", -1);
    if (chestSlot >= 0) markResourceUsed(root, "chests", "", chestSlot);
    JSONObject survivor = trigger.optJSONObject("survivor");
    if (survivor != null) {
      JSONArray survivors = root.getJSONArray("survivors");
      survivors.put(new JSONObject(survivor.toString()));
      root.put("survivors", survivors);
    }
  }

  JSONObject resolveChoice(JSONObject state, String action) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    if (root.optBoolean("loadingRequired", false)) {
      throw new IllegalStateException("Narrative edit is still loading");
    }
    String raw = action == null ? "" : action.trim();
    if (!raw.startsWith(CHOICE_PREFIX)) throw new IllegalArgumentException("Narrative V2 accepts only Core choices.");
    String id = raw.substring(CHOICE_PREFIX.length()).trim().toUpperCase(Locale.ROOT);

    JSONObject skeleton = root.optJSONObject("skeleton");
    JSONObject plan = skeleton == null ? null : skeleton.optJSONObject("plan");
    JSONArray beats = plan == null ? null : plan.optJSONArray("beats");
    int beatIndex = skeleton == null ? -1 : skeleton.optInt("currentBeat", -1);
    if (beats == null || beatIndex < 0 || beatIndex >= beats.length()) {
      throw new IllegalStateException("No active narrative beat.");
    }
    JSONObject beat = beats.getJSONObject(beatIndex);
    JSONArray choices = beat.getJSONArray("choices");
    JSONObject choice = null;
    for (int i = 0; i < choices.length(); i++) {
      JSONObject candidate = choices.optJSONObject(i);
      if (candidate != null && id.equals(candidate.optString("id", ""))) {
        choice = candidate;
        break;
      }
    }
    if (choice == null) throw new IllegalArgumentException("Choice is not valid for the current beat.");

    JSONObject result = new JSONObject()
        .put("choiceId", id)
        .put("text", choice.optString("text", ""))
        .put("beatId", beat.optString("id", ""))
        .put("actIndex", root.optInt("actIndex", 1));
    applyChoiceEffects(root, choice.optJSONArray("effects"), beat, result);

    appendBounded(root.getJSONArray("facts"), new JSONObject()
        .put("type", "CHOICE_COMMITTED")
        .put("actIndex", root.optInt("actIndex", 1))
        .put("beatId", beat.optString("id", ""))
        .put("choiceId", id)
        .put("text", choice.optString("text", "")), MAX_FACTS);
    appendBounded(root.getJSONArray("recentChoices"), new JSONObject()
        .put("actIndex", root.optInt("actIndex", 1))
        .put("beatId", beat.optString("id", ""))
        .put("choiceId", id)
        .put("text", choice.optString("text", "")), MAX_RECENT);
    appendBounded(root.getJSONArray("recentPatterns"), new JSONObject()
        .put("threatType", beat.optString("threatType", ""))
        .put("choiceShape", beat.optString("choiceShape", "")), MAX_RECENT);

    root.put("lastChoiceResult", new JSONObject(result.toString()));
    root.put("activeEntities", new JSONArray());

    int next = beatIndex + 1;
    if (next >= beats.length()) {
      closeAct(state, root, result);
    } else {
      skeleton.put("currentBeat", next);
      root.put("skeleton", skeleton);
      root.put("currentChoices", choicesForBeat(beats, next));
    }
    root.put("endingTrajectory", evaluateTrajectory(root));
    return result;
  }

  void noteCombatOutcome(JSONObject state, JSONArray entityKeys, String outcome) throws Exception {
    if (!enabled(state) || entityKeys == null || entityKeys.length() == 0) return;
    JSONObject root = state.getJSONObject(ROOT_KEY);
    appendBounded(root.getJSONArray("facts"), new JSONObject()
        .put("type", "COMBAT_OUTCOME")
        .put("entities", new JSONArray(entityKeys.toString()))
        .put("outcome", outcome == null ? "" : outcome), MAX_FACTS);
    if ("victory".equals(outcome)) {
      for (int i = 0; i < root.getJSONArray("missionBoard").length(); i++) {
        JSONObject mission = root.getJSONArray("missionBoard").optJSONObject(i);
        if (mission == null || !"ACTIVE".equals(mission.optString("status", ""))) continue;
        String title = mission.optString("title", "").toLowerCase(Locale.ROOT);
        if (title.contains("entity") || title.contains("thực thể")) advanceMission(mission, 1);
      }
    }
    root.put("endingTrajectory", evaluateTrajectory(root));
  }

  JSONObject decorateSceneFrame(JSONObject state, JSONObject frame) throws Exception {
    if (!enabled(state)) return frame;
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject narrative = new JSONObject()
        .put("levelKey", root.optString("levelKey", levelKey(state)))
        .put("actIndex", root.optInt("actIndex", 1))
        .put("missionBoard", publicMissionBoard(root.getJSONArray("missionBoard")))
        .put("endingTrajectoryHidden", root.optString("endingTrajectory", "NEUTRAL"))
        .put("lastChoiceResult", new JSONObject(root.optJSONObject("lastChoiceResult") == null
            ? "{}" : root.getJSONObject("lastChoiceResult").toString()));
    JSONObject skeleton = root.optJSONObject("skeleton");
    JSONObject plan = skeleton == null ? null : skeleton.optJSONObject("plan");
    JSONArray beats = plan == null ? null : plan.optJSONArray("beats");
    int index = skeleton == null ? -1 : skeleton.optInt("currentBeat", -1);
    if (beats != null && index >= 0 && index < beats.length()) {
      JSONObject beat = beats.optJSONObject(index);
      narrative.put("currentBeat", beat == null ? new JSONObject() : playerSafeBeat(root, beat));
    } else {
      narrative.put("currentBeat", new JSONObject());
    }
    narrative.put("actComplete", root.optBoolean("loadingRequired", false));
    frame.put("narrativeV2", narrative);
    return frame;
  }

  void redactClient(JSONObject state) throws Exception {
    if (!enabled(state)) return;
    JSONObject root = state.getJSONObject(ROOT_KEY);
    root.remove("skeleton");
    root.remove("endingTrajectory");
    root.remove("facts");
    root.remove("threads");
    root.remove("recentPatterns");
    root.put("missionBoard", publicMissionBoard(root.optJSONArray("missionBoard")));
    JSONArray survivors = root.optJSONArray("survivors");
    JSONArray safeSurvivors = new JSONArray();
    for (int i = 0; survivors != null && i < survivors.length(); i++) {
      JSONObject survivor = survivors.optJSONObject(i);
      if (survivor == null) continue;
      safeSurvivors.put(new JSONObject()
          .put("id", survivor.optString("id", ""))
          .put("name", survivor.optString("name", ""))
          .put("condition", survivor.optString("condition", ""))
          .put("relationship", survivor.optString("relationship", "UNKNOWN"))
          .put("status", survivor.optString("status", "ACTIVE")));
    }
    root.put("survivors", safeSurvivors);
    JSONObject budget = root.optJSONObject("spawnBudget");
    if (budget != null) {
      root.put("spawnBudget", new JSONObject()
          .put("actIndex", budget.optInt("actIndex", root.optInt("actIndex", 1)))
          .put("entityCount", budget.optInt("entityCount", 0))
          .put("chestCount", budget.optInt("chestCount", 0))
          .put("locked", true));
    }
  }

  JSONObject situationSnapshot(JSONObject state) throws Exception {
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject out = new JSONObject()
        .put("levelKey", levelKey(state))
        .put("actIndex", root.optInt("actIndex", 1))
        .put("player", copy(state.optJSONObject("player")))
        .put("party", copy(state.optJSONArray("party")))
        .put("inventory", copy(state.optJSONArray("inventory")))
        .put("missions", copy(root.optJSONArray("missionBoard")))
        .put("survivors", copy(root.optJSONArray("survivors")))
        .put("facts", tail(root.optJSONArray("facts"), 20))
        .put("threads", copy(root.optJSONArray("threads")))
        .put("evidence", copy(root.optJSONArray("evidence")))
        .put("recentChoices", copy(root.optJSONArray("recentChoices")))
        .put("recentPatterns", copy(root.optJSONArray("recentPatterns")))
        .put("spawnBudget", copy(root.optJSONObject("spawnBudget")))
        .put("longTermMemo", latestMemo(state))
        .put("endingTrajectory", root.optString("endingTrajectory", "NEUTRAL"));
    JSONObject flags = state.optJSONObject("flags");
    out.put("activeEntityKeys", flags == null ? new JSONArray()
        : copy(flags.optJSONArray("entityEncounterKeys")));
    return out;
  }

  private JSONObject rollBudget(JSONObject state, JSONArray candidates, int act, int revision) throws Exception {
    JSONObject emergent = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    int stateVersion = emergent == null ? 0 : Math.max(0, emergent.optInt("stateVersion", 0));
    TurnRng rng = new TurnRng(
        "narrative-act:" + levelKey(state) + ":" + act + ":" + revision,
        stateVersion, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);

    List<JSONObject> pool = new ArrayList<>();
    if (candidates != null) for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.optJSONObject(i);
      if (candidate != null && "ENTITY".equals(candidate.optString("kind", ""))) {
        pool.add(new JSONObject(candidate.toString()));
      }
    }
    int entityCount = pool.isEmpty() ? 0 : Math.min(pool.size(), rng.nextInt(TurnRng.Scope.WORLD_REACTION, 3));
    int chestCount = rng.nextInt(TurnRng.Scope.WORLD_REACTION, 2);
    JSONArray entities = new JSONArray();
    for (int i = 0; i < entityCount && !pool.isEmpty(); i++) {
      int pick = rng.nextInt(TurnRng.Scope.CANDIDATE_SELECTION, pool.size());
      JSONObject candidate = pool.remove(pick);
      entities.put(new JSONObject()
          .put("key", candidate.optString("payloadKey", ""))
          .put("summary", candidate.optString("publicSummary", ""))
          .put("capabilityContext", candidate.optString("capabilityContext", ""))
          .put("used", false));
    }
    JSONArray chests = new JSONArray();
    for (int i = 0; i < chestCount; i++) {
      chests.put(new JSONObject().put("slot", i).put("used", false));
    }
    return new JSONObject()
        .put("actIndex", act)
        .put("entityCount", entityCount)
        .put("chestCount", chestCount)
        .put("entities", entities)
        .put("chests", chests)
        .put("locked", true);
  }

  private JSONObject buildAssetManifest(JSONObject state, JSONObject budget) throws Exception {
    JSONArray entityKeys = new JSONArray();
    JSONArray entities = budget == null ? null : budget.optJSONArray("entities");
    for (int i = 0; entities != null && i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      String key = entity == null ? "" : entity.optString("key", "").trim();
      if (!key.isEmpty()) entityKeys.put(key);
    }

    JSONArray partyIds = new JSONArray().put("cao_minh");
    JSONArray party = state.optJSONArray("party");
    for (int i = 0; party != null && i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !member.optBoolean("joined", false)) continue;
      String id = normalizeId(member.optString("id", member.optString("name", "")));
      if (!id.isEmpty() && !containsString(partyIds, id)) partyIds.put(id);
    }
    return new JSONObject()
        .put("entityKeys", entityKeys)
        .put("partyMemberIds", partyIds)
        .put("chest", budget != null && budget.optInt("chestCount", 0) > 0);
  }

  private JSONObject validateMissionProposal(JSONObject state, JSONObject proposal) throws Exception {
    JSONArray source = proposal == null ? null : proposal.optJSONArray("missions");
    if (source == null && proposal != null) source = proposal.optJSONArray("missionBoard");
    JSONArray missions = new JSONArray();
    Set<String> ids = new LinkedHashSet<>();
    boolean hasMain = false;
    for (int i = 0; source != null && i < source.length() && missions.length() < MAX_MISSIONS; i++) {
      JSONObject raw = source.optJSONObject(i);
      if (raw == null) continue;
      String type = raw.optString("type", "MISSION").trim().toUpperCase(Locale.ROOT);
      if (!("MAIN".equals(type) || "MISSION".equals(type) || "OPTIONAL".equals(type) || "HIDDEN".equals(type))) continue;
      String id = sanitizeId(raw.optString("id", "mission_" + i));
      String title = sanitizeText(raw.optString("title", raw.optString("objective", "")), 160);
      if (id.isEmpty() || title.isEmpty() || !ids.add(id)) continue;
      if ("MAIN".equals(type)) {
        if (hasMain) type = "MISSION";
        else hasMain = true;
      }
      int target = Math.max(1, Math.min(3, raw.optInt("target", 1)));
      missions.put(new JSONObject()
          .put("id", id)
          .put("type", type)
          .put("title", title)
          .put("status", "ACTIVE")
          .put("progress", 0)
          .put("target", target)
          .put("revealed", !"HIDDEN".equals(type)));
    }
    if (!hasMain) {
      JSONArray fallback = fallbackMissionBoard(state);
      JSONObject main = fallback.getJSONObject(0);
      JSONArray merged = new JSONArray().put(main);
      for (int i = 0; i < missions.length() && merged.length() < MAX_MISSIONS; i++) merged.put(missions.get(i));
      missions = merged;
    }
    if (missions.length() < 2) missions = fallbackMissionBoard(state);
    int targetActs = proposal == null ? 4 : proposal.optInt("chapterActTarget", 4);
    targetActs = Math.max(3, Math.min(5, targetActs));
    return new JSONObject().put("missions", missions).put("chapterActTarget", targetActs);
  }

  private JSONArray fallbackMissionBoard(JSONObject state) throws Exception {
    JSONArray out = new JSONArray()
        .put(new JSONObject().put("id", "main_exit").put("type", "MAIN")
            .put("title", "Thoát khỏi " + levelKey(state)).put("status", "ACTIVE")
            .put("progress", 0).put("target", 1).put("revealed", true));
    JSONArray party = state.optJSONArray("party");
    boolean follower = false;
    for (int i = 0; party != null && i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member != null && member.optBoolean("joined", false)) { follower = true; break; }
    }
    out.put(new JSONObject().put("id", follower ? "party_survival" : "exit_evidence")
        .put("type", "MISSION")
        .put("title", follower ? "Giữ các đồng đội trong party sống sót" : "Thu thập bằng chứng về lối thoát")
        .put("status", "ACTIVE").put("progress", 0).put("target", 2).put("revealed", true));
    out.put(new JSONObject().put("id", "hidden_continuity").put("type", "HIDDEN")
        .put("title", "Điều kiện ẩn của Level").put("status", "ACTIVE")
        .put("progress", 0).put("target", 1).put("revealed", false));
    return out;
  }

  private JSONObject validatePlan(JSONObject state, JSONObject proposal) throws Exception {
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject rawPlan = proposal == null ? null : proposal.optJSONObject("plan");
    if (rawPlan == null) rawPlan = proposal;
    JSONArray source = rawPlan == null ? null : rawPlan.optJSONArray("beats");
    JSONObject budget = root.optJSONObject("spawnBudget");
    Set<String> allowedEntities = budgetEntityKeys(budget);
    Set<Integer> usedChestSlots = new LinkedHashSet<>();
    Set<String> usedEntities = new LinkedHashSet<>();
    JSONArray beats = new JSONArray();

    for (int i = 0; source != null && i < source.length() && beats.length() < MAX_BEATS; i++) {
      JSONObject raw = source.optJSONObject(i);
      if (raw == null) continue;
      JSONArray rawChoices = raw.optJSONArray("choices");
      if (rawChoices == null || rawChoices.length() != 3) continue;
      JSONObject beat = new JSONObject()
          .put("id", "A" + root.optInt("actIndex", 1) + "B" + (beats.length() + 1))
          .put("summary", sanitizeText(raw.optString("summary", raw.optString("purpose", "")), 360))
          .put("threatType", sanitizeId(raw.optString("threatType", "pressure")))
          .put("choiceShape", sanitizeId(raw.optString("choiceShape", "tradeoff")))
          .put("missionLinks", sanitizeLinks(raw.optJSONArray("missionLinks"), missionIds(root)))
          .put("threadLinks", sanitizeFreeLinks(raw.optJSONArray("threadLinks")));

      String entityKey = raw.optString("entityKey", "").trim().toLowerCase(Locale.ROOT);
      if (!entityKey.isEmpty() && allowedEntities.contains(entityKey) && usedEntities.add(entityKey)) {
        beat.put("entityKey", entityKey);
        beat.put("entityMode", sanitizeEntityMode(raw.optString("entityMode", "PRESSURE")));
      }
      if (raw.has("chestSlot")) {
        int slot = raw.optInt("chestSlot", -1);
        int count = budget == null ? 0 : budget.optInt("chestCount", 0);
        if (slot >= 0 && slot < count && usedChestSlots.add(slot)) beat.put("chestSlot", slot);
      }
      JSONObject survivor = sanitizeSurvivor(raw.optJSONObject("survivor"));
      if (survivor != null) beat.put("survivor", survivor);

      JSONArray choices = sanitizeChoices(root, beat, rawChoices);
      if (choices.length() != 3) continue;
      beat.put("choices", choices);
      beats.put(beat);
    }

    if (beats.length() < 2) return fallbackPlan(state);
    return new JSONObject()
        .put("scenePurpose", sanitizeText(rawPlan.optString("scenePurpose", "Gây áp lực hợp lý lên Cao Minh."), 240))
        .put("beats", beats)
        .put("climaxTarget", sanitizeText(rawPlan.optString("climaxTarget", "Buộc Cao Minh đánh đổi giữa các mục tiêu."), 240));
  }

  private JSONObject fallbackPlan(JSONObject state) throws Exception {
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject budget = root.optJSONObject("spawnBudget");
    JSONArray entities = budget == null ? null : budget.optJSONArray("entities");
    int chestCount = budget == null ? 0 : budget.optInt("chestCount", 0);
    String mission = firstNonMainMission(root);

    JSONArray beats = new JSONArray();
    for (int i = 0; i < 3; i++) {
      JSONObject beat = new JSONObject()
          .put("id", "A" + root.optInt("actIndex", 1) + "B" + (i + 1))
          .put("summary", i == 0 ? "Thiết lập áp lực của hồi."
              : i == 1 ? "Đẩy các mục tiêu vào thế xung đột." : "Khép hồi bằng một quyết định có hậu quả.")
          .put("threatType", i == 1 ? "pressure" : "continuity")
          .put("choiceShape", i == 2 ? "commitment" : "tradeoff")
          .put("missionLinks", mission.isEmpty() ? new JSONArray() : new JSONArray().put(mission))
          .put("threadLinks", new JSONArray());

      if (i == 1 && entities != null && entities.length() > 0) {
        beat.put("entityKey", entities.getJSONObject(0).optString("key", ""))
            .put("entityMode", "PRESSURE");
      } else if (i == 2 && chestCount > 0) {
        beat.put("chestSlot", 0);
      }

      JSONArray choices = new JSONArray();
      for (int c = 0; c < 3; c++) {
        JSONArray effects = new JSONArray();
        if (c == 0 && !mission.isEmpty()) {
          effects.put(new JSONObject().put("type", "MISSION_PROGRESS").put("missionId", mission).put("delta", 1));
        } else if (c == 1) {
          effects.put(new JSONObject().put("type", "EVIDENCE_ADD")
              .put("key", "act_" + root.optInt("actIndex", 1) + "_beat_" + (i + 1) + "_evidence"));
        } else {
          effects.put(new JSONObject().put("type", "THREAD_SET")
              .put("id", "act_" + root.optInt("actIndex", 1) + "_pressure").put("status", "OPEN"));
        }
        if (i == 2 && chestCount > 0 && c == 1) {
          effects.put(new JSONObject().put("type", "OPEN_CHEST"));
        }
        choices.put(new JSONObject()
            .put("id", String.valueOf((char)('A' + c)))
            .put("text", c == 0 ? "Ưu tiên mục tiêu đang có tiến triển rõ nhất."
                : c == 1 ? "Chấp nhận rủi ro để giữ thêm thông tin hoặc tài nguyên."
                : "Giữ thế chủ động và bảo toàn khả năng xoay chuyển ở cảnh sau.")
            .put("effects", effects));
      }
      beat.put("choices", choices);
      beats.put(beat);
    }
    return new JSONObject()
        .put("scenePurpose", "Gây khó công bằng bằng trade-off, không retcon và không auto-fail.")
        .put("beats", beats)
        .put("climaxTarget", "Đưa Cao Minh tới một quyết định có hậu quả trước loading tiếp theo.");
  }

  private JSONArray sanitizeChoices(JSONObject root, JSONObject beat, JSONArray rawChoices) throws Exception {
    JSONArray out = new JSONArray();
    Set<String> texts = new LinkedHashSet<>();
    for (int i = 0; i < 3; i++) {
      JSONObject raw = rawChoices.optJSONObject(i);
      if (raw == null) return new JSONArray();
      String text = sanitizeText(raw.optString("text", raw.optString("action", "")), 180);
      String key = text.toLowerCase(Locale.ROOT);
      if (text.isEmpty() || !texts.add(key)) return new JSONArray();
      JSONArray effects = sanitizeEffects(root, beat, raw.optJSONArray("effects"));
      out.put(new JSONObject()
          .put("id", String.valueOf((char)('A' + i)))
          .put("text", text)
          .put("effects", effects));
    }
    return out;
  }

  private JSONArray sanitizeEffects(JSONObject root, JSONObject beat, JSONArray effects) throws Exception {
    JSONArray out = new JSONArray();
    Set<String> missions = missionIds(root);
    for (int i = 0; effects != null && i < effects.length() && out.length() < 4; i++) {
      JSONObject raw = effects.optJSONObject(i);
      if (raw == null) continue;
      String type = raw.optString("type", "").trim().toUpperCase(Locale.ROOT);
      if ("MISSION_PROGRESS".equals(type)) {
        String id = sanitizeId(raw.optString("missionId", ""));
        if (missions.contains(id)) out.put(new JSONObject().put("type", type).put("missionId", id).put("delta", 1));
      } else if ("THREAD_SET".equals(type)) {
        String id = sanitizeId(raw.optString("id", ""));
        String status = raw.optString("status", "OPEN").trim().toUpperCase(Locale.ROOT);
        if (!id.isEmpty() && ("OPEN".equals(status) || "RESOLVED".equals(status) || "FAILED".equals(status))) {
          out.put(new JSONObject().put("type", type).put("id", id).put("status", status));
        }
      } else if ("EVIDENCE_ADD".equals(type)) {
        String key = sanitizeId(raw.optString("key", ""));
        if (!key.isEmpty()) out.put(new JSONObject().put("type", type).put("key", key));
      } else if ("SURVIVOR_STATUS".equals(type)) {
        String id = sanitizeId(raw.optString("id", ""));
        String status = raw.optString("status", "").trim().toUpperCase(Locale.ROOT);
        if (!id.isEmpty() && ("SAFE".equals(status) || "ESCAPED".equals(status) || "BETRAYED".equals(status))) {
          out.put(new JSONObject().put("type", type).put("id", id).put("status", status));
        }
      } else if ("OPEN_CHEST".equals(type) && beat.has("chestSlot")) {
        out.put(new JSONObject().put("type", type));
      } else if ("ENGAGE_ENTITY".equals(type)) {
        String key = beat.optString("entityKey", "");
        if (!key.isEmpty()) out.put(new JSONObject().put("type", type).put("entityKey", key));
      }
    }
    return out;
  }

  private void applyChoiceEffects(JSONObject root, JSONArray effects, JSONObject beat, JSONObject result) throws Exception {
    for (int i = 0; effects != null && i < effects.length(); i++) {
      JSONObject effect = effects.optJSONObject(i);
      if (effect == null) continue;
      String type = effect.optString("type", "");
      if ("MISSION_PROGRESS".equals(type)) {
        JSONObject mission = missionById(root, effect.optString("missionId", ""));
        if (mission != null) advanceMission(mission, 1);
      } else if ("THREAD_SET".equals(type)) {
        upsertThread(root.getJSONArray("threads"), effect.optString("id", ""), effect.optString("status", "OPEN"));
      } else if ("EVIDENCE_ADD".equals(type)) {
        String key = effect.optString("key", "");
        if (!key.isEmpty() && !containsString(root.getJSONArray("evidence"), key)) root.getJSONArray("evidence").put(key);
      } else if ("SURVIVOR_STATUS".equals(type)) {
        setSurvivorStatus(root, effect.optString("id", ""), effect.optString("status", ""));
      } else if ("OPEN_CHEST".equals(type)) {
        result.put("openChest", true);
      } else if ("ENGAGE_ENTITY".equals(type)) {
        result.put("engageEntityKey", effect.optString("entityKey", beat.optString("entityKey", "")));
      }
    }
  }

  private void closeAct(JSONObject state, JSONObject root, JSONObject result) throws Exception {
    int act = root.optInt("actIndex", 1);
    appendBounded(root.getJSONArray("facts"), new JSONObject()
        .put("type", "ACT_LOCKED").put("actIndex", act), MAX_FACTS);
    root.put("currentChoices", new JSONArray())
        .put("loadingRequired", true)
        .put("loadingState", "PENDING")
        .put("actLocked", true);
    root.remove("spawnBudget");
    root.remove("assetManifest");

    int target = Math.max(3, root.optInt("chapterActTarget", 4));
    if (act >= target) {
      JSONObject main = mainMission(root);
      if (main != null) {
        main.put("progress", main.optInt("target", 1)).put("status", "COMPLETE");
      }
      String ending = evaluateEnding(root);
      String memo = buildMemo(state, root, ending);
      JSONObject levelResult = new JSONObject()
          .put("levelKey", root.optString("levelKey", levelKey(state)))
          .put("ending", ending)
          .put("memo", memo);
      root.put("levelResult", levelResult)
          .put("endingTrajectory", ending)
          .put("pendingLevelAdvance", true);
      JSONArray memory = state.optJSONArray("narrativeMemory");
      if (memory == null) memory = new JSONArray();
      appendBounded(memory, new JSONObject(levelResult.toString()), 12);
      state.put("narrativeMemory", memory);
      result.put("levelComplete", true).put("ending", ending);
    } else {
      root.put("actIndex", act + 1);
      result.put("actComplete", true);
    }
  }

  private String evaluateTrajectory(JSONObject root) {
    JSONArray missions = root.optJSONArray("missionBoard");
    if (missions == null || missions.length() == 0) return "NEUTRAL";
    int completed = 0, failed = 0, visible = 0;
    for (int i = 0; i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission == null || "HIDDEN".equals(mission.optString("type", ""))) continue;
      visible++;
      if ("COMPLETE".equals(mission.optString("status", ""))) completed++;
      if ("FAILED".equals(mission.optString("status", ""))) failed++;
    }
    if (failed > 0) return "BAD";
    if (visible > 1 && completed >= visible - 1) return "GOOD";
    return completed > 0 ? "NEUTRAL" : "BAD";
  }

  private String evaluateEnding(JSONObject root) {
    JSONObject main = mainMission(root);
    if (main == null || !"COMPLETE".equals(main.optString("status", ""))) return "BAD";
    JSONArray missions = root.optJSONArray("missionBoard");
    int extras = 0, complete = 0, failed = 0;
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission == null || "MAIN".equals(mission.optString("type", ""))) continue;
      if ("HIDDEN".equals(mission.optString("type", "")) && !mission.optBoolean("revealed", false)
          && !"COMPLETE".equals(mission.optString("status", ""))) continue;
      extras++;
      if ("COMPLETE".equals(mission.optString("status", ""))) complete++;
      if ("FAILED".equals(mission.optString("status", ""))) failed++;
    }
    if (failed == 0 && extras > 0 && complete == extras) return "GOOD";
    if (failed <= Math.max(1, extras / 2) && complete >= extras / 2) return "NEUTRAL";
    return "BAD";
  }

  private String buildMemo(JSONObject state, JSONObject root, String ending) {
    StringBuilder out = new StringBuilder();
    out.append("LEVEL ").append(root.optString("levelKey", levelKey(state)))
        .append(" — ").append(ending).append(". ");
    JSONArray missions = root.optJSONArray("missionBoard");
    List<String> done = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission == null) continue;
      if ("COMPLETE".equals(mission.optString("status", ""))) done.add(mission.optString("title", ""));
      if ("FAILED".equals(mission.optString("status", ""))) failed.add(mission.optString("title", ""));
    }
    if (!done.isEmpty()) out.append("Hoàn thành: ").append(String.join("; ", done)).append(". ");
    if (!failed.isEmpty()) out.append("Thất bại: ").append(String.join("; ", failed)).append(". ");
    JSONArray survivors = root.optJSONArray("survivors");
    if (survivors != null && survivors.length() > 0) {
      out.append("Survivor: ");
      for (int i = 0; i < survivors.length(); i++) {
        JSONObject survivor = survivors.optJSONObject(i);
        if (survivor == null) continue;
        if (i > 0) out.append("; ");
        out.append(survivor.optString("name", survivor.optString("id", "unknown")))
            .append("=").append(survivor.optString("status", "ACTIVE"));
      }
      out.append(". ");
    }
    JSONArray threads = root.optJSONArray("threads");
    if (threads != null && threads.length() > 0) out.append("Threads còn lại: ").append(threads.toString()).append(".");
    String memo = out.toString().trim();
    return memo.length() > 1200 ? memo.substring(0, 1200) : memo;
  }

  private JSONObject playerSafeBeat(JSONObject root, JSONObject beat) throws Exception {
    JSONObject safe = new JSONObject()
        .put("id", beat.optString("id", ""))
        .put("summary", beat.optString("summary", ""))
        .put("missionLinks", copy(beat.optJSONArray("missionLinks")))
        .put("threadLinks", copy(beat.optJSONArray("threadLinks")));
    String entityKey = beat.optString("entityKey", "");
    if (!entityKey.isEmpty()) {
      JSONObject entity = budgetEntity(root.optJSONObject("spawnBudget"), entityKey);
      safe.put("entity", entity == null ? new JSONObject().put("key", entityKey) : entity)
          .put("entityMode", beat.optString("entityMode", "PRESSURE"));
    }
    if (beat.has("chestSlot")) safe.put("chestPresentInBeat", true);
    JSONObject survivor = beat.optJSONObject("survivor");
    if (survivor != null) safe.put("survivor", new JSONObject(survivor.toString()));
    return safe;
  }

  private JSONArray choicesForBeat(JSONArray beats, int index) throws Exception {
    JSONArray out = new JSONArray();
    if (beats == null || index < 0 || index >= beats.length()) return out;
    JSONObject beat = beats.optJSONObject(index);
    JSONArray choices = beat == null ? null : beat.optJSONArray("choices");
    for (int i = 0; choices != null && i < choices.length() && i < 3; i++) {
      JSONObject choice = choices.optJSONObject(i);
      if (choice == null) continue;
      String id = choice.optString("id", String.valueOf((char)('A' + i)));
      out.put(new JSONObject()
          .put("id", id)
          .put("text", choice.optString("text", ""))
          .put("action", CHOICE_PREFIX + id));
    }
    return out;
  }

  private JSONArray publicMissionBoard(JSONArray missions) throws Exception {
    JSONArray out = new JSONArray();
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission == null) continue;
      JSONObject copy = new JSONObject(mission.toString());
      if ("HIDDEN".equals(copy.optString("type", "")) && !copy.optBoolean("revealed", false)
          && !"COMPLETE".equals(copy.optString("status", ""))) {
        copy.put("title", "???");
      }
      out.put(copy);
    }
    return out;
  }

  private JSONObject sanitizeSurvivor(JSONObject raw) throws Exception {
    if (raw == null) return null;
    String id = sanitizeId(raw.optString("id", ""));
    String name = sanitizeText(raw.optString("name", ""), 80);
    if (id.isEmpty() || name.isEmpty()) return null;
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("condition", sanitizeText(raw.optString("condition", "Ổn định"), 120))
        .put("dispositionHidden", sanitizeText(raw.optString("dispositionHidden", "UNKNOWN"), 80))
        .put("relationship", sanitizeText(raw.optString("relationship", "UNKNOWN"), 100))
        .put("resourceNeed", sanitizeText(raw.optString("resourceNeed", ""), 120))
        .put("knowledge", sanitizeText(raw.optString("knowledge", ""), 220))
        .put("status", "ACTIVE")
        .put("stolenItems", new JSONArray())
        .put("threadState", "OPEN");
  }

  private static String sanitizeEntityMode(String raw) {
    String mode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    switch (mode) {
      case "STALK":
      case "HINT":
      case "TRACE":
      case "AMBUSH":
      case "CHASE":
      case "PRESSURE":
      case "COMBAT":
      case "CLIMAX":
        return mode;
      default:
        return "PRESSURE";
    }
  }

  static boolean combatEntityMode(String mode) {
    String value = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
    return "AMBUSH".equals(value) || "COMBAT".equals(value) || "CLIMAX".equals(value);
  }

  private static JSONObject copy(JSONObject source) throws Exception {
    return source == null ? new JSONObject() : new JSONObject(source.toString());
  }

  private static JSONArray copy(JSONArray source) throws Exception {
    return source == null ? new JSONArray() : new JSONArray(source.toString());
  }

  private static JSONArray tail(JSONArray source, int count) throws Exception {
    JSONArray out = new JSONArray();
    if (source == null) return out;
    int start = Math.max(0, source.length() - Math.max(0, count));
    for (int i = start; i < source.length(); i++) out.put(source.get(i));
    return out;
  }

  private static void appendBounded(JSONArray target, Object value, int max) throws Exception {
    if (target == null) return;
    target.put(value);
    if (target.length() <= max) return;
    JSONArray trimmed = new JSONArray();
    int start = target.length() - max;
    for (int i = start; i < target.length(); i++) trimmed.put(target.get(i));
    for (int i = 0; i < trimmed.length(); i++) target.put(i, trimmed.get(i));
    while (target.length() > trimmed.length()) target.remove(target.length() - 1);
  }

  private static String sanitizeText(String value, int max) {
    String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    return text.length() > max ? text.substring(0, max).trim() : text;
  }

  private static String sanitizeId(String value) {
    String id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9_:-]+", "_").replaceAll("^_+|_+$", "");
    return id.length() > 64 ? id.substring(0, 64) : id;
  }

  private static String normalizeId(String value) {
    String id = sanitizeId(value);
    if (id.contains("cao_minh") || id.contains("cao:minh")) return "cao_minh";
    if (id.contains("lucia")) return "lucia";
    if (id.contains("luc_tram") || id.contains("luc:tram")) return "luc_tram";
    if (id.contains("syvial")) return "syvial";
    return id;
  }

  private static String levelKey(JSONObject state) {
    if (state == null) return "0";
    return state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0))).trim();
  }

  private static boolean containsString(JSONArray array, String value) {
    for (int i = 0; array != null && i < array.length(); i++) {
      if (value.equals(array.optString(i, ""))) return true;
    }
    return false;
  }

  private static Set<String> budgetEntityKeys(JSONObject budget) {
    Set<String> out = new LinkedHashSet<>();
    JSONArray entities = budget == null ? null : budget.optJSONArray("entities");
    for (int i = 0; entities != null && i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      String key = entity == null ? "" : entity.optString("key", "");
      if (!key.isEmpty()) out.add(key);
    }
    return out;
  }

  private static JSONObject budgetEntity(JSONObject budget, String key) throws Exception {
    JSONArray entities = budget == null ? null : budget.optJSONArray("entities");
    for (int i = 0; entities != null && i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      if (entity != null && key.equals(entity.optString("key", ""))) return new JSONObject(entity.toString());
    }
    return null;
  }

  private static boolean resourceUsed(JSONObject root, String kind, String key, int slot) {
    JSONObject budget = root.optJSONObject("spawnBudget");
    JSONArray list = budget == null ? null : budget.optJSONArray(kind);
    for (int i = 0; list != null && i < list.length(); i++) {
      JSONObject item = list.optJSONObject(i);
      if (item == null) continue;
      if ("entities".equals(kind) && key.equals(item.optString("key", ""))) return item.optBoolean("used", false);
      if ("chests".equals(kind) && slot == item.optInt("slot", -1)) return item.optBoolean("used", false);
    }
    return true;
  }

  private static void markResourceUsed(JSONObject root, String kind, String key, int slot) throws Exception {
    JSONObject budget = root.optJSONObject("spawnBudget");
    JSONArray list = budget == null ? null : budget.optJSONArray(kind);
    for (int i = 0; list != null && i < list.length(); i++) {
      JSONObject item = list.optJSONObject(i);
      if (item == null) continue;
      if (("entities".equals(kind) && key.equals(item.optString("key", "")))
          || ("chests".equals(kind) && slot == item.optInt("slot", -1))) {
        item.put("used", true);
        return;
      }
    }
  }

  private static Set<String> missionIds(JSONObject root) {
    Set<String> out = new LinkedHashSet<>();
    JSONArray missions = root.optJSONArray("missionBoard");
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission != null) out.add(mission.optString("id", ""));
    }
    return out;
  }

  private static JSONArray sanitizeLinks(JSONArray input, Set<String> allowed) {
    JSONArray out = new JSONArray();
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; input != null && i < input.length() && out.length() < 6; i++) {
      String id = sanitizeId(input.optString(i, ""));
      if (!id.isEmpty() && allowed.contains(id) && seen.add(id)) out.put(id);
    }
    return out;
  }

  private static JSONArray sanitizeFreeLinks(JSONArray input) {
    JSONArray out = new JSONArray();
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; input != null && i < input.length() && out.length() < 6; i++) {
      String id = sanitizeId(input.optString(i, ""));
      if (!id.isEmpty() && seen.add(id)) out.put(id);
    }
    return out;
  }

  private static JSONArray collectLinks(JSONObject plan, String field) {
    JSONArray out = new JSONArray();
    Set<String> seen = new LinkedHashSet<>();
    JSONArray beats = plan == null ? null : plan.optJSONArray("beats");
    for (int i = 0; beats != null && i < beats.length(); i++) {
      JSONObject beat = beats.optJSONObject(i);
      JSONArray links = beat == null ? null : beat.optJSONArray(field);
      for (int j = 0; links != null && j < links.length(); j++) {
        String value = links.optString(j, "");
        if (!value.isEmpty() && seen.add(value)) out.put(value);
      }
    }
    return out;
  }

  private static JSONObject missionById(JSONObject root, String id) {
    JSONArray missions = root.optJSONArray("missionBoard");
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission != null && id.equals(mission.optString("id", ""))) return mission;
    }
    return null;
  }

  private static JSONObject mainMission(JSONObject root) {
    JSONArray missions = root.optJSONArray("missionBoard");
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission != null && "MAIN".equals(mission.optString("type", ""))) return mission;
    }
    return null;
  }

  private static String firstNonMainMission(JSONObject root) {
    JSONArray missions = root.optJSONArray("missionBoard");
    for (int i = 0; missions != null && i < missions.length(); i++) {
      JSONObject mission = missions.optJSONObject(i);
      if (mission != null && !"MAIN".equals(mission.optString("type", ""))) return mission.optString("id", "");
    }
    return "";
  }

  private static void advanceMission(JSONObject mission, int delta) throws Exception {
    if (mission == null || "COMPLETE".equals(mission.optString("status", ""))
        || "FAILED".equals(mission.optString("status", ""))) return;
    int target = Math.max(1, mission.optInt("target", 1));
    int progress = Math.min(target, Math.max(0, mission.optInt("progress", 0)) + Math.max(0, delta));
    mission.put("progress", progress);
    mission.put("status", progress >= target ? "COMPLETE" : "ACTIVE");
    if ("HIDDEN".equals(mission.optString("type", "")) && progress > 0) mission.put("revealed", true);
  }

  private static void upsertThread(JSONArray threads, String id, String status) throws Exception {
    for (int i = 0; i < threads.length(); i++) {
      JSONObject thread = threads.optJSONObject(i);
      if (thread != null && id.equals(thread.optString("id", ""))) {
        thread.put("status", status);
        return;
      }
    }
    threads.put(new JSONObject().put("id", id).put("status", status));
  }

  private static boolean survivorKnown(JSONObject root, String id) {
    JSONArray survivors = root.optJSONArray("survivors");
    for (int i = 0; survivors != null && i < survivors.length(); i++) {
      JSONObject survivor = survivors.optJSONObject(i);
      if (survivor != null && id.equals(survivor.optString("id", ""))) return true;
    }
    return false;
  }

  private static void setSurvivorStatus(JSONObject root, String id, String status) throws Exception {
    JSONArray survivors = root.optJSONArray("survivors");
    for (int i = 0; survivors != null && i < survivors.length(); i++) {
      JSONObject survivor = survivors.optJSONObject(i);
      if (survivor != null && id.equals(survivor.optString("id", ""))) {
        survivor.put("status", status);
        survivor.put("threadState", "SAFE".equals(status) ? "RESOLVED" : "OPEN");
        return;
      }
    }
  }

  private static String latestMemo(JSONObject state) {
    JSONArray memory = state == null ? null : state.optJSONArray("narrativeMemory");
    if (memory == null || memory.length() == 0) return "";
    JSONObject last = memory.optJSONObject(memory.length() - 1);
    return last == null ? "" : last.optString("memo", "");
  }
}
