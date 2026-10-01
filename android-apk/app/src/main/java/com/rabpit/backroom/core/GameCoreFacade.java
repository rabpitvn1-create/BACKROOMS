package com.rabpit.backroom.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class GameCoreFacade implements AutoCloseable {
  private static final String TAG = "BackroomGameCore";
  private static final String PREFS = "backroom_game_core";
  private static final String STATE_KEY = "state_json";
  private static final String MANUAL_SAVE_KEY = "manual_save_json";
  private static final int CURRENT_SAVE_VERSION = 14;

  private final SharedPreferences preferences;
  private String liveStateJson;
  private final boolean debugLogging;
  private final boolean gmTransactionCommitEnabled;
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final ItemCore itemCore;
  private final CharacterEncounterCore characterEncounterCore;
  private final CharacterProgressionCore characterProgressionCore;
  private final SurvivalCore survivalCore;
  private final CharacterDetailCore characterDetailCore;
  private final EmergentTurnEngine emergentTurnEngine;
  private final GmCommandAuthority gmCommandAuthority;
  private final Map<String, PreparedTurn> preparedTurns = new LinkedHashMap<>();

  private GameCoreFacade(Context context, boolean debugLogging, boolean gmTransactionCommitEnabled) {
    Context appContext = context.getApplicationContext();
    this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String checkpoint = preferences.getString(MANUAL_SAVE_KEY, "");
    this.liveStateJson = checkpoint != null && !checkpoint.isEmpty()
        ? checkpoint : preferences.getString(STATE_KEY, "{}");
    this.debugLogging = debugLogging;
    this.gmTransactionCommitEnabled = gmTransactionCommitEnabled;
    this.levelCore = new LevelCore(appContext);
    this.entityCore = new EntityCore(appContext);
    this.itemCore = new ItemCore();
    this.characterEncounterCore = new CharacterEncounterCore();
    this.characterProgressionCore = new CharacterProgressionCore();
    this.survivalCore = new SurvivalCore();
    this.characterDetailCore = new CharacterDetailCore();
    this.emergentTurnEngine = new EmergentTurnEngine();
    this.gmCommandAuthority = new GmCommandAuthority(
        itemCore, levelCore, entityCore, characterEncounterCore, characterProgressionCore);
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging, false);
  }

  public static GameCoreFacade create(
      Context context, boolean debugLogging, boolean gmTransactionCommitEnabled) {
    return new GameCoreFacade(context, debugLogging, gmTransactionCommitEnabled);
  }

  public synchronized String processRule(String legacyStateJson, String action) {
    JSONObject legacy = parseState(legacyStateJson);
    JSONObject stored = parseState(liveStateJson);
    if (stored.length() > 0) legacy = stored;
    try {
      normalizeCoreState(legacy);
      emergentTurnEngine.normalizeState(legacy);
      if (emergentTurnEngine.catchUpProjections(legacy)) persist(legacy);
      if (!emergentTurnEngine.selectionProjectionFresh(legacy)) {
        throw new IllegalStateException("SelectionCooldown projection is stale");
      }

      String text = action == null ? "" : action.trim();
      if (text.isEmpty()) return response(false, legacy, "Hành động trống.", "validation_rejected", null);

      if (GameCoreRules.isDirectPlayerPickupAction(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = "Không thể nhặt vật phẩm tự do. Loot chỉ nhận từ Entity hoặc Rương.";
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
      }

      if (GameCoreRules.isInventoryQuery(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = inventoryReply(result.optJSONArray("inventory"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "query_handled", reply);
      }

      if (GameCoreRules.isPartyQuery(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = partyReply(result.optJSONArray("party"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "query_handled", reply);
      }

      String turnId = emergentTurnEngine.nextTurnId(legacy, text);
      PreparedTurn existing = preparedTurns.get(turnId);
      if (existing != null && existing.baseHash.equals(fingerprint(legacy))) {
        return preparedResponse(legacy, existing);
      }
      PreparedTurn prepared = prepareExplorerTurnData(legacy, text);
      preparedTurns.clear();
      preparedTurns.put(turnId, prepared);
      return preparedResponse(legacy, prepared);
    } catch (Exception e) {
      debug("processRule failed: " + e.getMessage());
      return response(false, legacy, safeMessage(e), "core_error", null);
    }
  }

  private PreparedTurn prepareExplorerTurnData(JSONObject legacy, String text) throws Exception {
      int preTurnStateVersion = emergentTurnEngine.stateVersion(legacy);
      String turnId = emergentTurnEngine.nextTurnId(legacy, text);

      TurnRng turnRng = new TurnRng(
          turnId, preTurnStateVersion,
          EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
      JSONObject working = deepCopy(legacy);
      JSONArray events = new JSONArray();
      String replyHint = "";
      boolean openedChest = false;
      String beforeLevelKey = working.optString("currentLevelKey", String.valueOf(working.optInt("currentLevel", 0)));

      if (itemCore.isOpenChestAction(text)) {
        String itemName = itemCore.openChest(
            working, bound -> turnRng.nextInt(TurnRng.Scope.PLAYER_ACTION, bound));
        openedChest = true;
        JSONObject flags = working.optJSONObject("flags");
        int coreReward = flags == null ? 0 : Math.max(0, flags.optInt("lastChestCoreReward", 0));
        replyHint = "Rương chứa " + itemName + " x1. Đã thêm vào Inventory."
            + (coreReward > 0 ? " Nhận +" + coreReward + " Core." : "");
        JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
            "CHEST_AVAILABLE", new JSONArray().put(beforeLevelKey), "TERMINATE", "RESOLVED"));
        events.put(emergentTurnEngine.event(turnId, events, "CHEST_OPENED", "LOCAL", beforeLevelKey,
            new JSONObject()
                .put("factPredicate", "chest_opened")
                .put("factValue", itemName)
                .put("causedBy", "player")
                .put("observedByPlayer", true),
            effects));
      } else {
        boolean transitioned = levelCore.applyPlayerTransitionIfRequested(working, text);
        if (transitioned) {
          String nextLevel = working.optString("currentLevelKey", "");
          JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
              "LEVEL_ROUTE_SEARCH", new JSONArray().put(beforeLevelKey), "TERMINATE", "RESOLVED"));
          events.put(emergentTurnEngine.event(turnId, events, "LEVEL_TRANSITIONED", "REGIONAL", nextLevel,
              new JSONObject()
                  .put("factPredicate", "entered_level")
                  .put("factValue", nextLevel)
                  .put("causedBy", "player")
                  .put("observedByPlayer", true),
              effects));
        } else {
          levelCore.rollRouteForExplorerAction(
              working, text, bound -> turnRng.nextInt(TurnRng.Scope.PLAYER_ACTION, bound));
          appendRouteEventIfAny(working, turnId, events);
        }
      }

      incrementTurn(working);
      advanceGameTime(working, text);
      characterProgressionCore.applyExplorerTurnRecovery(working);
      survivalCore.normalizeState(working);
      itemCore.normalizeInventory(working);
      characterEncounterCore.normalizeState(working);
      working.put("mode", "ai");
      working.put("saveVersion", CURRENT_SAVE_VERSION);

      // Turn/time are authoritative too, so every world-advancing player turn has an event source.
      events.put(emergentTurnEngine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
          new JSONObject()
              .put("factPredicate", "player_action")
              .put("factValue", text)
              .put("causedBy", "player")
              .put("impactEligible", false)
              .put("observedByPlayer", true),
          null));

      JSONArray candidates = new JSONArray();
      appendAll(candidates, emergentTurnEngine.schedulerCandidates(
          working, Math.max(1, working.optInt("turn", 1))));
      appendAll(candidates, entityCore.situationCandidates(working));
      if (!openedChest) {
        JSONObject chestCandidate = itemCore.explorationChestCandidate(working);
        if (chestCandidate != null) candidates.put(chestCandidate);
      }
      appendAll(candidates, characterEncounterCore.situationCandidates(working));

      JSONObject selected = emergentTurnEngine.selectCandidate(
          working, candidates, turnRng, Math.max(1, working.optInt("turn", 1)));
      PreparedTurn prepared = new PreparedTurn(
          turnId, preTurnStateVersion, fingerprint(legacy), text, working, events, selected, turnRng, replyHint);
      return prepared;
  }

  /** Returns a hypothetical post-turn state without writing preferences or retaining a turn attempt. */
  public synchronized String previewTurn(String action, String expectedBaseHash) {
    JSONObject base = parseState(liveStateJson);
    try {
      String text = action == null ? "" : action.trim();
      if (base.length() == 0 || text.isEmpty() || !fingerprint(base).equals(expectedBaseHash)
          || CombatChoiceEngine.isActive(base) || GameCoreRules.isDirectPlayerPickupAction(text)
          || GameCoreRules.isInventoryQuery(text) || GameCoreRules.isPartyQuery(text)) {
        return response(false, base, "Preview unavailable.", "preview_unavailable", null);
      }
      JSONObject normalized = deepCopy(base);
      normalizeCoreState(normalized);
      emergentTurnEngine.normalizeState(normalized);
      emergentTurnEngine.catchUpProjections(normalized);
      if (!emergentTurnEngine.selectionProjectionFresh(normalized)) {
        throw new IllegalStateException("SelectionCooldown projection is stale");
      }
      PreparedTurn prepared = prepareExplorerTurnData(normalized, text);
      JSONObject working = finishWorkingTurn(normalized, prepared, new JSONObject());
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      projectBeforePersist(working);
      return new JSONObject().put("handled", true).put("turnId", prepared.turnId)
          .put("state", clientSafeState(working)).put("replyHint", prepared.replyHint)
          .put("baseHash", expectedBaseHash).put("outcomeHash", fingerprint(working)).toString();
    } catch (Exception e) {
      return response(false, base, safeMessage(e), "preview_unavailable", null);
    }
  }

  public synchronized String currentStateHash() {
    return fingerprint(parseState(liveStateJson));
  }

  /** Only an explicit player action writes the live game to durable storage. */
  public synchronized String saveCheckpoint() {
    String live = liveStateJson;
    if (parseState(live).length() == 0) throw new IllegalStateException("Chưa có game để lưu.");
    if (!preferences.edit().putString(MANUAL_SAVE_KEY, live).remove(STATE_KEY).commit()) {
      throw new IllegalStateException("Không thể lưu game.");
    }
    return live;
  }

  public synchronized String loadCheckpoint() {
    String saved = preferences.getString(MANUAL_SAVE_KEY, "");
    if (saved == null || parseState(saved).length() == 0) {
      throw new IllegalStateException("Chưa có bản lưu thủ công.");
    }
    JSONObject restored = parseState(saved);
    preparedTurns.clear();
    persist(restored);
    return clientSafeState(restored).toString();
  }

  public synchronized void clearCheckpoint() {
    preparedTurns.clear();
    preferences.edit().remove(MANUAL_SAVE_KEY).remove(STATE_KEY).commit();
  }

  public synchronized String validateWorldProposal(String selectedJson, String proposalJson) {
    JSONObject output = new JSONObject();
    try {
      JSONObject selected = parseState(selectedJson);
      JSONObject raw = parseState(proposalJson);
      String reason = emergentTurnEngine.worldProposalValidationReason(selected, raw);
      output.put("valid", reason.isEmpty())
          .put("reason", reason)
          .put("proposal", emergentTurnEngine.sanitizeWorldProposal(selected, raw));
    } catch (Exception e) {
      try {
        output.put("valid", false)
            .put("reason", safeMessage(e))
            .put("proposal", new JSONObject());
      } catch (Exception ignored) {}
    }
    return output.toString();
  }

  /**
   * Phase-2 read-only envelope for the shadow GM planner. The returned snapshot is copied from
   * authoritative live state, omits the narrative log for prompt budget, and cannot commit.
   */
  public synchronized String shadowPlannerContext(String turnId) {
    JSONObject persisted = parseState(liveStateJson);
    JSONObject output = new JSONObject();
    try {
      PreparedTurn prepared = preparedTurns.get(turnId);
      if (prepared == null) {
        return output.put("available", false).put("reason", "turn_attempt_missing").toString();
      }
      String liveHash = fingerprint(persisted);
      if (!prepared.baseHash.equals(liveHash)
          || emergentTurnEngine.stateVersion(persisted) != prepared.preTurnStateVersion) {
        return output.put("available", false).put("reason", "stale_turn_attempt").toString();
      }

      JSONObject snapshot = clientSafeState(persisted);
      snapshot.remove("log");
      JSONObject draws = new JSONObject();
      for (TurnRng.Scope scope : TurnRng.Scope.values()) {
        draws.put(scope.name(), prepared.rng.drawsUsed(scope));
      }
      JSONObject selectionAuthorization = GmSelectionGate.issue(
          prepared.turnId, prepared.baseHash, prepared.selected, prepared.working, prepared.rng);
      JSONObject rngContext = new JSONObject()
          .put("turnId", prepared.turnId)
          .put("preTurnStateVersion", prepared.preTurnStateVersion)
          .put("canonVersion", EmergentTurnEngine.CANON_VERSION)
          .put("rngSchemaVersion", EmergentTurnEngine.RNG_SCHEMA_VERSION)
          .put("drawsUsed", draws);

      return output.put("available", true)
          .put("schemaVersion", GmTransactionContract.SCHEMA_VERSION)
          .put("turnId", prepared.turnId)
          .put("baseStateHash", prepared.baseHash)
          .put("action", prepared.action)
          .put("stateSnapshot", snapshot)
          .put("selectionAuthorization", selectionAuthorization)
          .put("rngContext", rngContext)
          .toString();
    } catch (Exception e) {
      try {
        output.put("available", false).put("reason", safeMessage(e));
      } catch (Exception ignored) {}
      return output.toString();
    }
  }

  /**
   * Phase-4A gate probe. This method cannot persist; it only reports whether the future commit
   * protocol is allowed to advance past the feature gate.
   */
  public synchronized String plannerCommitGate(String plannerContextJson, String proposalJson) {
    JSONObject output = new JSONObject();
    try {
      JSONObject context = new JSONObject(plannerContextJson == null ? "{}" : plannerContextJson);
      JSONObject proposal = new JSONObject(proposalJson == null ? "{}" : proposalJson);
      return GmTransactionCommitGate.preflight(
          gmTransactionCommitEnabled,
          context.optString("turnId", ""),
          context.optString("baseStateHash", ""),
          proposal).toString();
    } catch (Exception e) {
      try {
        output.put("allowed", false).put("reason", "commit_gate_error");
      } catch (Exception ignored) {}
      return output.toString();
    }
  }

  /** Phase-3 planner-visible command registry. Descriptive only; no state is touched. */
  public synchronized String shadowCommandRegistry() {
    return gmCommandAuthority.promptContext();
  }

  /**
   * Phase-3 dry-run authority validation. The adapter receives only the copied Phase-2 snapshot
   * and can never persist or mutate liveStateJson.
   */
  public synchronized String validateShadowTransaction(String plannerContextJson, String proposalJson) {
    JSONObject output = new JSONObject();
    try {
      JSONObject context = new JSONObject(plannerContextJson == null ? "{}" : plannerContextJson);
      JSONObject proposal = new JSONObject(proposalJson == null ? "{}" : proposalJson);
      if (!context.optBoolean("available", false)) {
        return output.put("valid", false)
            .put("reason", "planner_context_unavailable")
            .put("commandResults", new JSONArray())
            .put("resolvedTurn", new JSONObject())
            .toString();
      }
      JSONObject snapshot = context.optJSONObject("stateSnapshot");
      if (snapshot == null) {
        return output.put("valid", false)
            .put("reason", "planner_state_snapshot_missing")
            .put("commandResults", new JSONArray())
            .put("resolvedTurn", new JSONObject())
            .toString();
      }
      return gmCommandAuthority.validate(
          snapshot, proposal,
          context.optString("turnId", ""),
          context.optString("baseStateHash", ""),
          context.optJSONObject("selectionAuthorization")).toString();
    } catch (Exception e) {
      try {
        output.put("valid", false)
            .put("reason", safeMessage(e))
            .put("commandResults", new JSONArray())
            .put("resolvedTurn", new JSONObject());
      } catch (Exception ignored) {}
      return output.toString();
    }
  }

  public synchronized String completePreparedTurn(String turnId, String proposalJson) {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      if (emergentTurnEngine.hasCommitted(persisted, turnId)) {
        return response(true, persisted, null, "duplicate_commit", null);
      }

      PreparedTurn prepared = preparedTurns.get(turnId);
      if (prepared == null) {
        return response(false, persisted, "Không có turn attempt phù hợp.", "turn_attempt_missing", null);
      }
      if (emergentTurnEngine.stateVersion(persisted) != prepared.preTurnStateVersion
          || !fingerprint(persisted).equals(prepared.baseHash)) {
        preparedTurns.remove(turnId);
        return response(false, persisted, "State đã thay đổi trước COMMIT.", "stale_turn_attempt", null);
      }

      JSONObject working = finishWorkingTurn(persisted, prepared, parseState(proposalJson));
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      preparedTurns.remove(turnId);
      return preparedCommitResponse(working, prepared);
    } catch (Exception e) {
      preparedTurns.remove(turnId);
      debug("completePreparedTurn failed: " + e.getMessage());
      return response(false, persisted, safeMessage(e), "system_fault_precommit", null);
    }
  }

  private JSONObject finishWorkingTurn(JSONObject persisted, PreparedTurn prepared, JSONObject rawProposal)
      throws Exception {
      JSONObject working = prepared.working;
      JSONObject selected = prepared.selected;
      JSONObject proposal = emergentTurnEngine.sanitizeWorldProposal(selected, rawProposal);
      selected.put("worldProposal", proposal);
      working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
          .put("lastSelection", new JSONObject(selected.toString()));

      if (!selected.optBoolean("selectedNone", false)) {
        applySelectedSituation(working, prepared.events, prepared.turnId, selected, proposal);
      }

      emergentTurnEngine.appendThreadResolutionEvents(
          working, prepared.events, prepared.turnId, Math.max(1, working.optInt("turn", 1)));
      emergentTurnEngine.appendDormancyEvents(
          working, prepared.events, prepared.turnId, Math.max(1, working.optInt("turn", 1)));
      if (prepared.events.length() > 64) {
        throw new IllegalStateException("DomainEvent cascade exceeded max_batch_size=64");
      }
      emergentTurnEngine.validateBatch(prepared.turnId, prepared.events);
      emergentTurnEngine.commitAuthoritative(
          persisted, working, prepared.turnId, prepared.events, selected);

      return working;
  }

  public synchronized String commitNarration(String stateJson, boolean acknowledgePendingIntro) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(liveStateJson);
    try {
      normalizeCoreState(state);
      JSONArray log = submitted.optJSONArray("log");
      if (log != null) state.put("log", new JSONArray(log.toString()));
      if (acknowledgePendingIntro) characterEncounterCore.acknowledgePendingIntro(state);
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu narration.", e);
    }
  }

  private void applySelectedSituation(JSONObject working, JSONArray events, String turnId,
                                      JSONObject selected, JSONObject proposal) throws Exception {
    String kind = selected.optString("kind", "");
    String payload = selected.optString("payloadKey", "");
    JSONObject params = new JSONObject()
        .put("observedByPlayer", true)
        .put("causedBy", "world")
        .put("situationKey", selected.optString("situationKey", ""))
        .put("worldProposal", new JSONObject(proposal.toString()));

    if ("ENTITY".equals(kind)) {
      entityCore.activateEncounterCandidate(working, payload);
      params.put("factPredicate", "entity_encounter_started").put("factValue", payload);
      JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
          "ENTITY_ENCOUNTER", new JSONArray().put(payload), "SEED_OR_ADVANCE", null));
      events.put(emergentTurnEngine.event(
          turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", payload, params, effects));
      return;
    }

    if ("CHEST".equals(kind)) {
      itemCore.activateExplorationChest(working);
      String levelKey = working.optString("currentLevelKey", payload);
      params.put("factPredicate", "chest_discovered").put("factValue", true);
      JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
          "CHEST_AVAILABLE", new JSONArray().put(levelKey), "SEED_OR_ADVANCE", null));
      events.put(emergentTurnEngine.event(
          turnId, events, "CHEST_SPAWNED", "LOCAL", levelKey, params, effects));
      return;
    }

    if ("CHARACTER".equals(kind)) {
      characterEncounterCore.activateEncounterCandidate(working, payload);
      boolean reunion = "luc_tram".equals(payload);
      params.put("factPredicate", reunion ? "character_reunion" : "character_encountered")
          .put("factValue", payload);
      JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
          reunion ? "LUC_TRAM_RELATIONSHIP" : "SOCIAL_CONTACT",
          new JSONArray().put(payload), "SEED_OR_ADVANCE", null));
      events.put(emergentTurnEngine.event(
          turnId, events, reunion ? "CHARACTER_REUNION" : "CHARACTER_ENCOUNTERED",
          "SOCIAL", payload, params, effects));
      return;
    }

    throw new IllegalStateException("Unknown selected Situation kind: " + kind);
  }

  private void appendRouteEventIfAny(JSONObject state, String turnId, JSONArray events) throws Exception {
    JSONObject route = state.optJSONObject(LevelCore.ROUTE_STATE);
    if (route == null) return;
    int currentTurn = Math.max(1, state.optInt("turn", 1));
    if (route.optInt("lastRollTurn", -1) != currentTurn) return;
    String result = route.optString("lastResult", "");
    if (result.isEmpty()) return;

    String levelKey = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    String eventType = "RESET".equals(result) ? "ROUTE_SEARCH_RESET"
        : "EXIT_AVAILABLE".equals(result) ? "ROUTE_EXIT_AVAILABLE" : "ROUTE_SEARCH_PROGRESS";
    String effect = "EXIT_AVAILABLE".equals(result) ? "TERMINATE" : "SEED_OR_ADVANCE";
    JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
        "LEVEL_ROUTE_SEARCH", new JSONArray().put(levelKey), effect,
        "TERMINATE".equals(effect) ? "RESOLVED" : null));
    events.put(emergentTurnEngine.event(turnId, events, eventType, "LOCAL", levelKey,
        new JSONObject()
            .put("factPredicate", "route_search_result")
            .put("factValue", result)
            .put("causedBy", "player")
            .put("observedByPlayer", true),
        effects));
  }

  private static void appendAll(JSONArray target, JSONArray source) {
    if (target == null || source == null) return;
    for (int i = 0; i < source.length(); i++) target.put(source.opt(i));
  }

  private String preparedResponse(JSONObject committedState, PreparedTurn prepared) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", false)
          .put("reason", "turn_prepared")
          .put("state", clientSafeState(committedState))
          .put("turnId", prepared.turnId)
          .put("selectedCandidate", new JSONObject(prepared.selected.toString()))
          .put("proposalRequired", prepared.selected.optBoolean("proposalRequired", false));
      if (!prepared.replyHint.isEmpty()) output.put("replyHint", prepared.replyHint);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private String preparedCommitResponse(JSONObject state, PreparedTurn prepared) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", true)
          .put("reason", "turn_committed")
          .put("state", clientSafeState(state))
          .put("turnId", prepared.turnId)
          .put("selectedCandidate", new JSONObject(prepared.selected.toString()));
      if (!prepared.replyHint.isEmpty()) output.put("replyHint", prepared.replyHint);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private void normalizeCoreState(JSONObject state) throws Exception {
    levelCore.normalizeState(state);
    characterProgressionCore.normalizeState(state);
    survivalCore.normalizeState(state);
    itemCore.normalizeInventory(state);
    characterEncounterCore.normalizeState(state);
    CombatChoiceEngine.normalizeTerminalEncounter(state);
  }

  public synchronized String startCombatRuntime(String entityKey, int gmLogIndex) {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);
      JSONObject root = working.getJSONObject(EmergentTurnEngine.ROOT_KEY);
      String rngTurnId = root.optString("lastCommittedTurnId", "").trim();
      if (rngTurnId.isEmpty()) {
        rngTurnId = emergentTurnEngine.nextTurnId(persisted, "combat:start:" + entityKey);
      }
      CombatChoiceEngine.start(
          working, entityKey, gmLogIndex, rngTurnId, emergentTurnEngine.stateVersion(persisted));
      persist(working);
      return clientSafeState(working).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể khởi tạo combat runtime.", e);
    }
  }

  public synchronized String processCombatResolution(String stateJson) {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      emergentTurnEngine.catchUpProjections(persisted);

      int preVersion = emergentTurnEngine.stateVersion(persisted);
      JSONObject working = deepCopy(persisted);
      JSONObject beforeCombat = working.optJSONObject("combat");
      JSONObject beforeEntity = beforeCombat == null ? null : beforeCombat.optJSONObject("entity");
      String entityKey = beforeEntity == null ? "" : beforeEntity.optString("key", "");
      String turnId = emergentTurnEngine.nextTurnId(
          persisted, "combat:resolve:" + entityKey + ":" + (beforeCombat == null ? 0 : beforeCombat.optInt("round", 0))
              + ":" + (beforeCombat == null ? 0 : beforeCombat.optInt("actorIndex", 0)));

      boolean wasActive = CombatChoiceEngine.isActive(working);
      CombatChoiceEngine.resolveFinalized(working);
      CombatChoiceEngine.normalizeTerminalEncounter(working);
      boolean active = CombatChoiceEngine.isActive(working);
      JSONObject combat = working.optJSONObject("combat");
      String outcome = combat == null ? "" : combat.optString("outcome", "");
      String resolvedActor = combat == null ? "" : combat.optString("resolvedActorName", "");

      JSONArray events = new JSONArray();
      JSONArray threadEffects = new JSONArray();
      JSONArray entityRefs = entityKey.isEmpty() ? new JSONArray() : new JSONArray().put(entityKey);
      if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
        threadEffects.put(emergentTurnEngine.threadEffect(
            "ENTITY_ENCOUNTER", entityRefs, "TERMINATE",
            "victory".equals(outcome) ? "RESOLVED" : "FAILED"));
      } else if (wasActive && active && !entityKey.isEmpty()) {
        threadEffects.put(emergentTurnEngine.threadEffect(
            "ENTITY_ENCOUNTER", entityRefs, "SEED_OR_ADVANCE", null));
      }

      events.put(emergentTurnEngine.event(
          turnId, events,
          !active && "victory".equals(outcome) ? "COMBAT_VICTORY"
              : !active && "defeat".equals(outcome) ? "COMBAT_DEFEAT"
              : "COMBAT_HAND_RESOLVED",
          "LOCAL",
          entityKey.isEmpty() ? "combat" : entityKey,
          new JSONObject()
              .put("factPredicate", "combat_resolution")
              .put("factValue", outcome.isEmpty() ? "ongoing" : outcome)
              .put("resolvedActor", resolvedActor)
              .put("causedBy", "player")
              .put("observedByPlayer", true),
          threadEffects));

      if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
        incrementTurn(working);
      }

      emergentTurnEngine.validateBatch(turnId, events);
      emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      if (emergentTurnEngine.stateVersion(working) != preVersion + 1) {
        throw new IllegalStateException("Combat commit stateVersion drift");
      }
      working.put("saveVersion", CURRENT_SAVE_VERSION);
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      return response(true, working, null,
          active ? "combat_turn_resolved" : "combat_finished", null);
    } catch (Exception e) {
      return response(false, persisted, safeMessage(e), "combat_resolve_rejected", null);
    }
  }


  public synchronized String restartAfterDeath() {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject combat = persisted.optJSONObject("combat");
      if (combat == null || !"defeat".equals(combat.optString("outcome", ""))
          || !combat.optBoolean("deathRestartPending", false)) {
        return response(false, persisted, "Không có lượt hồi sinh đang chờ.",
            "death_restart_unavailable", null);
      }

      JSONObject working = deepCopy(persisted);
      JSONObject workingCombat = working.getJSONObject("combat");
      String turnId = emergentTurnEngine.nextTurnId(persisted, "death:restart");
      LevelCore.returnToCurrentLevelStart(working);
      workingCombat.put("deathRestartPending", false).put("outcome", "");
      working.put("combat", workingCombat);

      JSONArray events = new JSONArray();
      events.put(emergentTurnEngine.event(turnId, events, "PLAYER_RESPAWNED", "LOCAL", "cao_minh",
          new JSONObject()
              .put("factPredicate", "respawned_at_level_start")
              .put("factValue", working.optString("location", ""))
              .put("causedBy", "system")
              .put("observedByPlayer", true),
          null));
      emergentTurnEngine.validateBatch(turnId, events);
      emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      working.put("saveVersion", CURRENT_SAVE_VERSION);
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      return response(true, working, null, "death_restart_completed", null);
    } catch (Exception e) {
      return response(false, persisted, safeMessage(e), "death_restart_rejected", null);
    }
  }


  public synchronized String levelPromptContext(String stateJson) {
    return levelPromptContext(stateJson, "");
  }

  public synchronized String levelPromptContext(String stateJson, String action) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return levelCore.promptContext(state, action);
    } catch (Exception e) {
      return "CURRENT LEVEL: 0\nLEVEL CANON: unavailable";
    }
  }

  public synchronized String characterPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      characterEncounterCore.normalizeState(state);
      return characterEncounterCore.promptContext(state);
    } catch (Exception e) {
      return "CHARACTER ENCOUNTER CORE: unavailable. Do not spawn characters or mutate Party.";
    }
  }

  public synchronized String entityPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return entityCore.promptContext(state);
    } catch (Exception e) {
      return "ENTITY CORE: unavailable. Do not invent an Entity.";
    }
  }

  public synchronized String itemPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return itemCore.promptContext(state);
    } catch (Exception e) {
      return "ITEM CORE: unavailable. Do not invent or grant loot.";
    }
  }

  public synchronized String processItemAction(String stateJson, String itemId, String operation,
                                               String targetId, int quantity) {
    return processItemAction(stateJson, "cao_minh", itemId, operation, targetId, quantity);
  }

  public synchronized String processItemAction(String stateJson, String ownerId, String itemId,
                                               String operation, String targetId, int quantity) {
    JSONObject submitted = parseState(stateJson);
    JSONObject persisted = parseState(liveStateJson);
    if (persisted.length() == 0) persisted = submitted;
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);
      String turnId = emergentTurnEngine.nextTurnId(
          persisted, "item:" + ownerId + ":" + itemId + ":" + operation + ":" + targetId + ":" + quantity);
      String reply = itemCore.applyItemAction(working, ownerId, itemId, operation, targetId, quantity);

      JSONArray events = new JSONArray();
      events.put(emergentTurnEngine.event(turnId, events, "ITEM_ACTION_RESOLVED", "LOCAL",
          targetId == null || targetId.trim().isEmpty() ? ownerId : targetId,
          new JSONObject()
              .put("factPredicate", "item_action")
              .put("factValue", itemId + ":" + operation + ":" + Math.max(1, quantity))
              .put("causedBy", "player")
              .put("observedByPlayer", true),
          null));
      emergentTurnEngine.validateBatch(turnId, events);
      emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      working.put("saveVersion", CURRENT_SAVE_VERSION);
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      return response(true, working, null, "item_action_committed", reply);
    } catch (Exception e) {
      return response(false, persisted, safeMessage(e), "item_action_rejected", null);
    }
  }

  public synchronized String processCoreUpgrade(String stateJson, String characterId, String stat) {
    JSONObject submitted = parseState(stateJson);
    JSONObject persisted = parseState(liveStateJson);
    if (persisted.length() == 0) persisted = submitted;
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      if (CombatChoiceEngine.isActive(persisted)) {
        return response(false, persisted,
            "Battle đang hoạt động. Hãy hoàn tất Poker Dice trước khi nâng chỉ số.",
            "combat_locked", null);
      }

      JSONObject working = deepCopy(persisted);
      String turnId = emergentTurnEngine.nextTurnId(
          persisted, "upgrade:" + characterId + ":" + stat);
      JSONObject result = characterProgressionCore.upgradeStat(working, characterId, stat);
      characterDetailCore.projectState(working);

      JSONArray events = new JSONArray();
      events.put(emergentTurnEngine.event(turnId, events, "CHARACTER_STAT_UPGRADED", "LOCAL",
          result.getString("characterId"),
          new JSONObject()
              .put("factPredicate", "stat_upgraded")
              .put("factValue", result.getString("stat") + ":" + result.getInt("value"))
              .put("causedBy", "player")
              .put("observedByPlayer", true),
          null));
      emergentTurnEngine.validateBatch(turnId, events);
      emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      working.put("saveVersion", CURRENT_SAVE_VERSION);
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);

      String reply = result.getString("stat") + " của " + result.getString("characterId")
          + " tăng lên " + result.getInt("value") + ". -" + result.getInt("cost")
          + " Core.";
      return response(true, working, null, "core_upgrade_committed", reply);
    } catch (Exception e) {
      return response(false, persisted, safeMessage(e), "core_upgrade_rejected", null);
    }
  }

  public synchronized String levelSnapshotDescriptor(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return levelCore.snapshotDescriptor(state);
    } catch (Exception e) {
      return "{\"level\":0}";
    }
  }

  public synchronized String normalizeState(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      JSONObject persisted = parseState(liveStateJson);
      if (persisted.length() > 0) {
        state = persisted;
      } else {
        state = newGameState(state);
      }
      normalizeCoreState(state);
      characterProgressionCore.applyExplorerTurnRecovery(state);
      emergentTurnEngine.normalizeState(state);
      emergentTurnEngine.catchUpProjections(state);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
    } catch (Exception e) {
      debug("normalizeState failed: " + e.getMessage());
    }
    return clientSafeState(state).toString();
  }

  public synchronized String currentCoreState() {
    return clientSafeState(parseState(liveStateJson)).toString();
  }

  public synchronized String combatRollRuntime() {
    return mutateCombatRuntime("ROLL", -1, false);
  }

  public synchronized String combatHoldRuntime(int dieIndex, boolean held) {
    return mutateCombatRuntime("HOLD", dieIndex, held);
  }

  public synchronized String combatFinishRuntime() {
    return mutateCombatRuntime("FINISH", -1, false);
  }

  private String mutateCombatRuntime(String operation, int dieIndex, boolean held) {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);

      if ("ROLL".equals(operation)) {
        CombatChoiceEngine.roll(working);
      } else if ("HOLD".equals(operation)) {
        CombatChoiceEngine.setHold(working, dieIndex, held);
      } else if ("FINISH".equals(operation)) {
        CombatChoiceEngine.finishHand(working);
      } else {
        throw new IllegalArgumentException("Unknown combat runtime operation: " + operation);
      }

      // ROLL/HOLD/FINISH are transient combat-control state. World effects are committed only
      // by processCombatResolution() through DomainEventBatch.
      persisted.put("combat", new JSONObject(working.getJSONObject("combat").toString()));
      persist(persisted);
      return clientSafeState(persisted).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể cập nhật combat runtime.", e);
    }
  }

  public synchronized String startNewGame(String initialJson) {
    preparedTurns.clear();
    liveStateJson = "{}";
    return normalizeState(initialJson);
  }

  static JSONObject newGameState(JSONObject initial) throws Exception {
    if (initial == null) initial = new JSONObject();
    JSONObject fresh = new JSONObject()
        .put("title", initial.optString("title", "Level 0 : The Lobby"))
        .put("turn", 1).put("mode", "local APK")
        .put("currentLevel", 0).put("currentLevelKey", "0")
        .put("location", initial.optString("location", "Hành lang vàng nhạt — khu vực chưa xác định"))
        .put("player", new JSONObject().put("name", "Cao Minh").put("condition", "Ổn định"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray()
            .put(new JSONObject().put("name", "Huyết Ma Kiếm"))
            .put(new JSONObject().put("name", "Huyết Ma Chiến Khải"))
            .put(new JSONObject().put("name", "Vạn Tàng Giới")))
        .put("flags", new JSONObject());
    if (initial.has("characterCanon")) fresh.put("characterCanon", initial.get("characterCanon"));
    JSONArray log = initial.optJSONArray("log");
    if (log != null) fresh.put("log", new JSONArray(log.toString()));
    return fresh;
  }

  public synchronized void clear() {
    preparedTurns.clear();
    liveStateJson = "{}";
  }

  private static final class PreparedTurn {
    final String turnId;
    final int preTurnStateVersion;
    final String baseHash;
    final String action;
    final JSONObject working;
    final JSONArray events;
    final JSONObject selected;
    final TurnRng rng;
    final String replyHint;

    PreparedTurn(String turnId, int preTurnStateVersion, String baseHash, String action, JSONObject working,
                 JSONArray events, JSONObject selected, TurnRng rng, String replyHint) {
      this.turnId = turnId;
      this.preTurnStateVersion = preTurnStateVersion;
      this.baseHash = baseHash;
      this.action = action;
      this.working = working;
      this.events = events;
      this.selected = selected;
      this.rng = rng;
      this.replyHint = replyHint == null ? "" : replyHint;
    }
  }

  @Override public void close() {
    // Pure Java core has no native model/runtime resources to close.
  }

  private JSONObject parseState(String json) {
    try {
      if (json == null || json.trim().isEmpty()) return new JSONObject();
      return new JSONObject(json);
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private JSONArray parseArray(String json) {
    try {
      if (json == null || json.trim().isEmpty()) return new JSONArray();
      return new JSONArray(json);
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  private JSONObject deepCopy(JSONObject source) {
    try {
      return new JSONObject(source == null ? "{}" : source.toString());
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private static void copyField(JSONObject source, JSONObject target, String key) throws Exception {
    if (source != null && source.has(key)) target.put(key, source.get(key));
    else target.remove(key);
  }

  private JSONArray normalizeInventory(JSONArray input) {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    Map<String, JSONObject> deduplicated = new LinkedHashMap<>();
    for (int i = 0; i < input.length(); i++) {
      JSONObject item = input.optJSONObject(i);
      if (item == null) continue;
      String name = item.optString("name", "").trim();
      if (name.isEmpty()) continue;
      String id = item.optString("id", "").trim();
      if (id.isEmpty()) id = GameCoreRules.stableItemId(name);
      int quantity = Math.max(1, item.optInt("quantity", 1));
      JSONObject normalized;
      try {
        normalized = new JSONObject(item.toString());
      } catch (Exception e) {
        normalized = new JSONObject();
      }
      try {
        normalized.put("id", id);
        normalized.put("name", name);
        normalized.put("quantity", quantity);
      } catch (Exception ignored) {}
      deduplicated.put(id, normalized);
    }
    for (JSONObject value : deduplicated.values()) output.put(value);
    return output;
  }

  private void incrementTurn(JSONObject state) throws Exception {
    state.put("turn", Math.max(1, state.optInt("turn", 1)) + 1);
  }

  private void advanceGameTime(JSONObject state, String action) throws Exception {
    JSONObject gameTime = state.optJSONObject("gameTime");
    if (gameTime == null) gameTime = new JSONObject();
    int minutes = GameCoreRules.estimateMinutes(action);
    long elapsed = Math.max(0L, gameTime.optLong("elapsedSubjectiveMinutes", 0L));
    gameTime.put("elapsedSubjectiveMinutes", elapsed + minutes);
    gameTime.put("lastAdvanceMinutes", minutes);
    gameTime.put("lastAdvanceReason", "player_action");
    state.put("gameTime", gameTime);
    state.put("saveVersion", CURRENT_SAVE_VERSION);
    advanceExplorerStatusEffects(state);
  }

  private void advanceGameTimeFromBefore(JSONObject before, JSONObject candidate, String action) throws Exception {
    JSONObject prior = before.optJSONObject("gameTime");
    long elapsed = prior == null ? 0L : Math.max(0L, prior.optLong("elapsedSubjectiveMinutes", 0L));
    int minutes = GameCoreRules.estimateMinutes(action);
    JSONObject gameTime = new JSONObject();
    gameTime.put("elapsedSubjectiveMinutes", elapsed + minutes);
    gameTime.put("lastAdvanceMinutes", minutes);
    gameTime.put("lastAdvanceReason", "player_action");
    candidate.put("gameTime", gameTime);
    advanceExplorerStatusEffects(candidate);
  }

  private void advanceExplorerStatusEffects(JSONObject state) throws Exception {
    characterProgressionCore.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      characterProgressionCore.advanceStatusEffects(
          state, member.optString("id", ""), "explorer_turn");
    }
  }

  private String inventoryReply(JSONArray inventory) {
    if (inventory == null || inventory.length() == 0) return "Inventory hiện đang trống.";
    StringBuilder reply = new StringBuilder("Inventory hiện có: ");
    boolean first = true;
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null) continue;
      String name = item.optString("name", "").trim();
      if (name.isEmpty()) continue;
      if (!first) reply.append(", ");
      first = false;
      reply.append(name);
      int quantity = Math.max(1, item.optInt("quantity", 1));
      if (quantity > 1) reply.append(" x").append(quantity);
    }
    if (first) return "Inventory hiện đang trống.";
    reply.append('.');
    return reply.toString();
  }

  private String partyReply(JSONArray party) {
    if (party == null || party.length() == 0) return "Cao Minh hiện không có đồng đội trong party.";
    StringBuilder reply = new StringBuilder("Party hiện có Cao Minh");
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String name = member.optString("name", member.optString("id", "")).trim();
      if (!name.isEmpty()) reply.append(", ").append(name);
    }
    reply.append('.');
    return reply.toString();
  }

  private void appendLog(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    log.put(new JSONObject().put("role", "player").put("text", action));
    log.put(new JSONObject().put("role", "gm").put("text", reply));
    state.put("log", log);
  }

  private String encounterKey(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase(Locale.ROOT);
  }

  static boolean startRandomEntityEncounter(EntityCore entityCore, JSONObject state) throws Exception {
    if (CombatChoiceEngine.isActive(state)) return true;
    entityCore.prepareEncounter(state);
    String encounter = state.optJSONObject("flags").optString("entityEncounterKey", "")
        .trim().toLowerCase(Locale.ROOT);
    if (!CombatChoiceEngine.isKnownEntity(encounter)) return false;
    CombatChoiceEngine.start(state, encounter, lastGmLogIndex(state));
    return CombatChoiceEngine.isActive(state);
  }

  private static int lastGmLogIndex(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return 0;
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry != null && !"player".equals(entry.optString("role"))) return i;
    }
    return Math.max(0, log.length() - 1);
  }

  private void persist(JSONObject state) {
    projectBeforePersist(state);
    liveStateJson = state == null ? "{}" : state.toString();
  }

  private void projectBeforePersist(JSONObject state) {
    if (state == null) return;
    try {
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterDetailCore.projectState(state);
    } catch (Exception e) {
      debug("Character detail projection failed: " + e.getMessage());
    }
  }

  private static String fingerprint(JSONObject state) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical(state).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot fingerprint Core state", e);
    }
  }

  private static String canonical(Object value) throws Exception {
    if (value == null || value == JSONObject.NULL) return "null";
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      ArrayList<String> keys = new ArrayList<>();
      java.util.Iterator<String> iterator = object.keys();
      while (iterator.hasNext()) keys.add(iterator.next());
      Collections.sort(keys);
      StringBuilder out = new StringBuilder("{");
      for (String key : keys) {
        if (out.length() > 1) out.append(',');
        out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));
      }
      return out.append('}').toString();
    }
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < array.length(); i++) {
        if (i > 0) out.append(',');
        out.append(canonical(array.get(i)));
      }
      return out.append(']').toString();
    }
    return value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
  }

  private String response(boolean handled, JSONObject state, String error, String reason, String reply) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", handled);
      output.put("state", clientSafeState(state == null ? new JSONObject() : state));
      output.put("reason", reason == null ? "" : reason);
      if (error != null && !error.isEmpty()) output.put("error", error);
      if (reply != null && !reply.isEmpty()) output.put("reply", reply);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private JSONObject clientSafeState(JSONObject source) {
    return deepCopy(source);
  }

  private String safeMessage(Exception e) {
    if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) return "Game State Core error";
    return e.getMessage();
  }

  private void debug(String message) {
    if (debugLogging) Log.d(TAG, message);
  }
}
