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
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final ItemCore itemCore;
  private final CharacterEncounterCore characterEncounterCore;
  private final CharacterProgressionCore characterProgressionCore;
  private final SurvivalCore survivalCore;
  private final CharacterDetailCore characterDetailCore;
  private final EmergentTurnEngine emergentTurnEngine;
  private final NarrativeChapterCore narrativeChapterCore;
  private final Map<String, PreparedTurn> preparedTurns = new LinkedHashMap<>();

  private GameCoreFacade(Context context, boolean debugLogging) {
    Context appContext = context.getApplicationContext();
    this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String checkpoint = preferences.getString(MANUAL_SAVE_KEY, "");
    this.liveStateJson = checkpoint != null && !checkpoint.isEmpty()
        ? checkpoint : preferences.getString(STATE_KEY, "{}");
    this.debugLogging = debugLogging;
    this.levelCore = new LevelCore(appContext);
    this.entityCore = new EntityCore(appContext);
    this.itemCore = new ItemCore();
    this.characterEncounterCore = new CharacterEncounterCore();
    this.characterProgressionCore = new CharacterProgressionCore();
    this.survivalCore = new SurvivalCore();
    this.characterDetailCore = new CharacterDetailCore();
    this.emergentTurnEngine = new EmergentTurnEngine();
    this.narrativeChapterCore = new NarrativeChapterCore();
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging);
  }

  public synchronized String processRule(String legacyStateJson, String action) {
    DiagnosticLog.record("core.processRule", "legacyStateJson", legacyStateJson, "action", action);
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

      if (narrativeChapterCore.enabled(legacy)) {
        if (!text.startsWith(NarrativeChapterCore.CHOICE_PREFIX)) {
          return response(false, legacy,
              "Narrative V2 chỉ chấp nhận một trong ba lựa chọn do Core công bố.",
              "narrative_choice_required", null);
        }
        String turnId = emergentTurnEngine.nextWorldTurnId(legacy, text);
        PreparedTurn existing = preparedTurns.get(turnId);
        if (existing != null && existing.baseHash.equals(fingerprint(legacy))) {
          return preparedResponse(legacy, existing);
        }
        PreparedTurn prepared = prepareNarrativeChoiceTurnData(legacy, text);
        preparedTurns.clear();
        preparedTurns.put(turnId, prepared);
        return preparedResponse(legacy, prepared);
      }

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

      String turnId = emergentTurnEngine.nextWorldTurnId(legacy, text);
      PreparedTurn existing = preparedTurns.get(turnId);
      if (existing != null && existing.baseHash.equals(fingerprint(legacy))) {
        return preparedResponse(legacy, existing);
      }
      PreparedTurn prepared = prepareExplorerTurnData(legacy, text);
      preparedTurns.clear();
      preparedTurns.put(turnId, prepared);
      return preparedResponse(legacy, prepared);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      debug("processRule failed: " + e.getMessage());
      return response(false, legacy, safeMessage(e), "core_error", null);
    }
  }

  private PreparedTurn prepareExplorerTurnData(JSONObject legacy, String text) throws Exception {
      int preTurnStateVersion = emergentTurnEngine.stateVersion(legacy);
      int worldRngVersion = emergentTurnEngine.worldRngVersion(legacy);
      String turnId = emergentTurnEngine.nextWorldTurnId(legacy, text);

      TurnRng turnRng = new TurnRng(
          turnId, worldRngVersion,
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

      JSONObject selected = emergentTurnEngine.selectLocalSituations(
          working, candidates, turnRng, Math.max(1, working.optInt("turn", 1)));
      PreparedTurn prepared = new PreparedTurn(
          turnId, preTurnStateVersion, fingerprint(legacy), text, working, events, selected, turnRng, replyHint);
      return prepared;
  }

  private PreparedTurn prepareNarrativeChoiceTurnData(JSONObject legacy, String text) throws Exception {
      int preTurnStateVersion = emergentTurnEngine.stateVersion(legacy);
      int worldRngVersion = emergentTurnEngine.worldRngVersion(legacy);
      String turnId = emergentTurnEngine.nextWorldTurnId(legacy, text);
      TurnRng turnRng = new TurnRng(
          turnId, worldRngVersion,
          EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
      JSONObject working = deepCopy(legacy);
      JSONArray events = new JSONArray();
      JSONObject resolution = narrativeChapterCore.resolveChoice(working, text);
      String replyHint = "";

      if (resolution.optBoolean("openChest", false)) {
        JSONObject flags = working.optJSONObject("flags");
        if (flags != null && flags.optBoolean("chestPresent", false)) {
          String itemName = itemCore.openChest(
              working, bound -> turnRng.nextInt(TurnRng.Scope.PLAYER_ACTION, bound));
          replyHint = "Rương chứa " + itemName + " x1. Đã thêm vào Inventory.";
          events.put(emergentTurnEngine.event(turnId, events, "CHEST_OPENED", "LOCAL",
              working.optString(LevelCore.LEVEL_KEY, "0"),
              new JSONObject()
                  .put("factPredicate", "chest_opened")
                  .put("factValue", itemName)
                  .put("causedBy", "player")
                  .put("observedByPlayer", true),
              null));
        }
      }

      String engageEntity = resolution.optString("engageEntityKey", "").trim();
      if (!engageEntity.isEmpty() && entityCore.activeEncounterKeys(working).length() == 0) {
        entityCore.activateEncounterCandidate(working, engageEntity);
        events.put(emergentTurnEngine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL",
            engageEntity,
            new JSONObject()
                .put("factPredicate", "entity_encounter_started")
                .put("factValue", engageEntity)
                .put("causedBy", "player")
                .put("observedByPlayer", true),
            null));
      }

      incrementTurn(working);
      advanceGameTime(working, resolution.optString("text", text));
      characterProgressionCore.applyExplorerTurnRecovery(working);
      survivalCore.normalizeState(working);
      itemCore.normalizeInventory(working);
      characterEncounterCore.normalizeState(working);
      working.put("mode", "ai");
      working.put("saveVersion", CURRENT_SAVE_VERSION);

      events.put(emergentTurnEngine.event(turnId, events, "NARRATIVE_CHOICE_RESOLVED", "LOCAL", "cao_minh",
          new JSONObject()
              .put("factPredicate", "narrative_choice")
              .put("factValue", resolution.optString("choiceId", ""))
              .put("choiceText", resolution.optString("text", ""))
              .put("actIndex", resolution.optInt("actIndex", 1))
              .put("beatId", resolution.optString("beatId", ""))
              .put("causedBy", "player")
              .put("observedByPlayer", true),
          null));

      if (entityCore.activeEncounterKeys(working).length() == 0 && !CombatChoiceEngine.isActive(working)) {
        applyNarrativeBeatTrigger(working, events, turnId);
      }

      JSONObject selected = new JSONObject()
          .put("selectedNone", true)
          .put("worldKind", "NARRATIVE")
          .put("situationKey", "narrative:choice:" + resolution.optString("choiceId", ""))
          .put("proposalRequired", false);
      return new PreparedTurn(
          turnId, preTurnStateVersion, fingerprint(legacy), text, working, events, selected, turnRng, replyHint);
  }

  private void applyNarrativeBeatTrigger(JSONObject working, JSONArray events, String turnId) throws Exception {
    if (!narrativeChapterCore.enabled(working) || narrativeChapterCore.loadingRequired(working)) return;
    JSONObject trigger = narrativeChapterCore.currentBeatTrigger(working);
    if (trigger.length() == 0) return;

    String entityKey = trigger.optString("entityKey", "").trim();
    String entityMode = trigger.optString("entityMode", "PRESSURE");
    if (!entityKey.isEmpty() && NarrativeChapterCore.combatEntityMode(entityMode)
        && entityCore.activeEncounterKeys(working).length() > 0) {
      return;
    }

    if (!entityKey.isEmpty() && NarrativeChapterCore.combatEntityMode(entityMode)) {
      entityCore.activateEncounterCandidate(working, entityKey);
    }

    int chestSlot = trigger.optInt("chestSlot", -1);
    JSONObject flags = working.optJSONObject("flags");
    boolean chestAlreadyPresent = flags != null && flags.optBoolean("chestPresent", false);
    if (chestSlot >= 0 && !chestAlreadyPresent) itemCore.activateExplorationChest(working);

    narrativeChapterCore.consumeBeatTrigger(working, trigger);

    if (!entityKey.isEmpty()) {
      events.put(emergentTurnEngine.event(turnId, events,
          NarrativeChapterCore.combatEntityMode(entityMode)
              ? "ENTITY_ENCOUNTER_STARTED" : "NARRATIVE_ENTITY_STAGED",
          "LOCAL", entityKey,
          new JSONObject()
              .put("factPredicate", "narrative_entity")
              .put("factValue", entityKey + ":" + entityMode)
              .put("entityMode", entityMode)
              .put("causedBy", "director_plan")
              .put("observedByPlayer", true),
          null));
    }
    if (chestSlot >= 0 && !chestAlreadyPresent) {
      events.put(emergentTurnEngine.event(turnId, events, "CHEST_SPAWNED", "LOCAL",
          working.optString(LevelCore.LEVEL_KEY, "0"),
          new JSONObject()
              .put("factPredicate", "chest_spawned")
              .put("factValue", "act_slot:" + chestSlot)
              .put("causedBy", "director_plan")
              .put("observedByPlayer", true),
          null));
    }
    JSONObject survivor = trigger.optJSONObject("survivor");
    if (survivor != null) {
      events.put(emergentTurnEngine.event(turnId, events, "SURVIVOR_INTRODUCED", "LOCAL",
          survivor.optString("id", "survivor"),
          new JSONObject()
              .put("factPredicate", "survivor_present")
              .put("factValue", survivor.optString("name", survivor.optString("id", "survivor")))
              .put("causedBy", "director_plan")
              .put("observedByPlayer", true),
          null));
    }
  }

  /** Returns a hypothetical post-turn state without writing preferences or retaining a turn attempt. */
  public synchronized String previewTurn(String action, String expectedBaseHash) {
    DiagnosticLog.record("core.previewTurn", "action", action, "expectedBaseHash", expectedBaseHash);
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
      String coreAction = GmChoiceContract.defaultCoreAction(normalized);
      PreparedTurn prepared = prepareExplorerTurnData(normalized, coreAction);
      JSONObject working = finishWorkingTurn(normalized, prepared, new JSONObject());
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      projectBeforePersist(working);
      return new JSONObject().put("handled", true).put("turnId", prepared.turnId)
          .put("state", clientSafeState(working)).put("replyHint", prepared.replyHint)
          .put("baseHash", expectedBaseHash).put("outcomeHash", fingerprint(working)).toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return response(false, base, safeMessage(e), "preview_unavailable", null);
    }
  }

  public synchronized String currentStateHash() {
    return fingerprint(parseState(liveStateJson));
  }

  /** Only an explicit player action writes the live game to durable storage. */
  public synchronized String saveCheckpoint() {
    DiagnosticLog.record("core.saveCheckpoint");
    String live = liveStateJson;
    if (parseState(live).length() == 0) throw new IllegalStateException("Chưa có game để lưu.");
    if (!preferences.edit().putString(MANUAL_SAVE_KEY, live).remove(STATE_KEY).commit()) {
      throw new IllegalStateException("Không thể lưu game.");
    }
    DiagnosticLog.record("checkpoint.saved", "state", live);
    return live;
  }

  public synchronized String loadCheckpoint() {
    DiagnosticLog.record("core.loadCheckpoint");
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
    DiagnosticLog.record("core.clearCheckpoint");
    preparedTurns.clear();
    preferences.edit().remove(MANUAL_SAVE_KEY).remove(STATE_KEY).commit();
  }

  public synchronized String completePreparedTurn(String turnId, String proposalJson) {
    DiagnosticLog.record("core.completePreparedTurn", "turnId", turnId, "proposalJson", proposalJson);
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
      DiagnosticLog.record("core.error", "error", e);
      preparedTurns.remove(turnId);
      debug("completePreparedTurn failed: " + e.getMessage());
      return response(false, persisted, safeMessage(e), "system_fault_precommit", null);
    }
  }

  private JSONObject finishWorkingTurn(JSONObject persisted, PreparedTurn prepared, JSONObject rawProposal)
      throws Exception {
      JSONObject working = prepared.working;
      JSONObject selected = prepared.selected;

      if (!selected.optBoolean("selectedNone", false)) {
        JSONArray situations = selected.optJSONArray("situations");
        if (situations != null) {
          for (int i = 0; i < situations.length(); i++) {
            JSONObject situation = situations.getJSONObject(i);
            JSONObject proposal = emergentTurnEngine.sanitizeWorldProposal(situation, rawProposal);
            situation.put("worldProposal", proposal);
            applySelectedSituation(working, prepared.events, prepared.turnId, situation, proposal);
          }
        } else {
          JSONObject proposal = emergentTurnEngine.sanitizeWorldProposal(selected, rawProposal);
          selected.put("worldProposal", proposal);
          applySelectedSituation(working, prepared.events, prepared.turnId, selected, proposal);
        }
      }
      working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
          .put("lastSelection", new JSONObject(selected.toString()));

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

  public static String presentationBaseHash(JSONObject snapshot) { return fingerprint(snapshot); }

  public JSONArray safePresentationEvents(JSONObject snapshot, JSONObject evidence) throws Exception {
    return SafePresentationView.events(snapshot, "cao_minh", evidence, entityCore);
  }

  /** Builds the local authoritative scene frame after Core commit and before GM narration. */
  public String sceneFrame(JSONObject snapshot, JSONObject evidence, String playerIntent) throws Exception {
    JSONObject state = deepCopy(snapshot);
    JSONArray safeEvents = SafePresentationView.events(state, "cao_minh", evidence, entityCore);
    JSONObject facts = OfflinePresenter.sceneFacts(state, playerIntent, safeEvents);
    String turnId = evidence == null ? "" : evidence.optString("turnId", "").trim();
    if (turnId.isEmpty()) {
      turnId = emergentTurnEngine.nextWorldTurnId(state, playerIntent == null ? "" : playerIntent);
    }
    int stateVersion = evidence == null ? 0 : Math.max(0, evidence.optInt("stateVersion", 0));
    TurnRng sceneRng = new TurnRng(
        turnId, stateVersion, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
    JSONObject environment = levelCore.sceneDirectorEnvironment(
        state, playerIntent, bound -> sceneRng.nextInt(TurnRng.Scope.WORLD_REACTION, bound));
    JSONObject frame = SceneDirector.compose(state, facts, environment);
    return narrativeChapterCore.decorateSceneFrame(state, frame).toString();
  }

  /** Validity check and append share the same lock as all authoritative writes. */
  public synchronized String commitPresentation(String turnId, int expectedStateVersion,
      String baseHash, String presentationId, String action, String gmEntryJson) {
    DiagnosticLog.record("core.commitPresentation", "turnId", turnId, "expectedStateVersion", expectedStateVersion, "baseHash", baseHash, "presentationId", presentationId, "action", action, "gmEntryJson", gmEntryJson);
    JSONObject state = parseState(liveStateJson);
    try {
      JSONArray log = state.optJSONArray("log");
      if (log == null) log = new JSONArray();
      for (int i = 0; i < log.length(); i++) {
        JSONObject entry = log.optJSONObject(i);
        if (entry != null && presentationId != null
            && presentationId.equals(entry.optString("presentationId", ""))) {
          return response(false, state, null, "duplicate_presentation", null);
        }
      }
      JSONObject root = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
      JSONObject evidence = CommittedTurnNarrationEvidence.fromState(state, turnId);
      if (root == null || root.optInt("stateVersion", -1) != expectedStateVersion
          || !fingerprint(state).equals(baseHash) || !evidence.optBoolean("available", false)
          || !(turnId + ":narration").equals(presentationId)) {
        return response(false, state, null, "stale_presentation", null);
      }
      JSONObject gmEntry = new JSONObject(gmEntryJson);
      String text = gmEntry.optString("text", "");
      if (!"gm".equals(gmEntry.optString("role", "")) || text.trim().isEmpty()) {
        return response(false, state, null, "unsafe_presentation", null);
      }
      gmEntry = SafePresentationView.presentation(state, gmEntry, presentationId);
      gmEntry.put("presentationId", presentationId);
      String displayAction = ItemCore.OPEN_CHEST_ACTION.equals(action) ? "Mở rương" : action;
      log.put(new JSONObject().put("role", "player").put("text", displayAction == null ? "" : displayAction));
      log.put(gmEntry);
      state.put("log", log);
      if (CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_ENCOUNTERED", "")
          || CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_REUNION", "")) {
        characterEncounterCore.acknowledgePendingIntro(state);
      }
      persist(state);
      return response(true, state, null, "presentation_committed", null);
    } catch (Exception error) {
      DiagnosticLog.record("core.error", "error", error);
      return response(false, parseState(liveStateJson), safeMessage(error), "presentation_rejected", null);
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
      JSONArray entityKeys = selectedEntityKeys(selected);
      entityCore.activateEncounterCandidates(working, entityKeys);
      for (int i = 0; i < entityKeys.length(); i++) {
        String key = entityKeys.optString(i, "");
        if ("tam_ma_cao_minh".equals(key)) remember(working, "Đã gặp Evil Clown.");
        if ("diep_minh".equals(key)) remember(working, "Đã gặp Diệp Minh.");
      }
      for (int i = 0; i < entityKeys.length(); i++) {
        String entityKey = entityKeys.optString(i, "");
        JSONObject entityParams = new JSONObject(params.toString())
            .put("factPredicate", "entity_encounter_started")
            .put("factValue", entityKey)
            .put("entityKeys", new JSONArray(entityKeys.toString()));
        JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
            "ENTITY_ENCOUNTER", new JSONArray().put(entityKey), "SEED_OR_ADVANCE", null));
        events.put(emergentTurnEngine.event(
            turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", entityKey, entityParams, effects));
      }
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
      String name = CharacterEncounterCore.displayName(payload);
      remember(working, "Đã gặp " + name + ".");
      remember(working, name + " đã tham gia nhóm.");
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

  private static void remember(JSONObject state, String event) throws Exception {
    JSONArray memory = state.optJSONArray("memorableEvents");
    if (memory == null) memory = new JSONArray();
    for (int i = 0; i < memory.length(); i++) {
      if (event.equals(memory.optString(i, ""))) return;
    }
    memory.put(event);
    state.put("memorableEvents", memory);
  }

  private static JSONArray selectedEntityKeys(JSONObject selected) throws Exception {
    JSONArray output = new JSONArray();
    if (selected == null) return output;
    JSONArray keys = selected.optJSONArray("payloadKeys");
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    if (keys != null) {
      for (int i = 0; i < keys.length(); i++) {
        String key = keys.optString(i, "").trim();
        if (!key.isEmpty() && seen.add(key)) output.put(key);
      }
    }
    String legacy = selected.optString("payloadKey", "").trim();
    if (!legacy.isEmpty() && seen.add(legacy)) output.put(legacy);
    return output;
  }

  private static JSONArray combatEntityKeys(JSONObject combat) throws Exception {
    JSONArray output = new JSONArray();
    if (combat == null) return output;
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    JSONArray entities = combat.optJSONArray("entities");
    if (entities != null) for (int i = 0; i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      String key = entity == null ? "" : entity.optString("key", "").trim();
      if (!key.isEmpty() && seen.add(key)) output.put(key);
    }
    JSONObject legacy = combat.optJSONObject("entity");
    String key = legacy == null ? "" : legacy.optString("key", "").trim();
    if (!key.isEmpty() && seen.add(key)) output.put(key);
    return output;
  }

  private static String activeCombatEntityKey(JSONObject combat) {
    JSONObject entity = combat == null ? null : combat.optJSONObject("entity");
    return entity == null ? "" : entity.optString("key", "").trim();
  }

  private static String stringArraySignature(JSONArray values) {
    java.util.List<String> parts = new java.util.ArrayList<>();
    if (values != null) for (int i = 0; i < values.length(); i++) {
      String value = values.optString(i, "").trim();
      if (!value.isEmpty()) parts.add(value);
    }
    return String.join("+", parts);
  }

  private static String payloadSignature(JSONObject source) throws Exception {
    if (source == null || source.optBoolean("selectedNone", false)
        || "QUIET".equals(source.optString("worldKind", ""))) return "";
    JSONArray keys = source.optJSONArray("payloadKeys");
    if (keys != null && keys.length() > 0) return stringArraySignature(keys);
    return source.optString("payloadKey", "").trim();
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
    CharacterKnowledge.normalize(state);
    levelCore.normalizeState(state);
    characterProgressionCore.normalizeState(state);
    survivalCore.normalizeState(state);
    itemCore.normalizeInventory(state);
    characterEncounterCore.normalizeState(state);
    CombatChoiceEngine.normalizeTerminalEncounter(state);
    narrativeChapterCore.normalizeState(state);
  }

  /** Core caller only: no JavaScript or model-output route exposes these updates. */
  public synchronized String markNameKnown(String actor, String subject, String sourceEvent) {
    DiagnosticLog.record("core.markNameKnown", "actor", actor, "subject", subject, "sourceEvent", sourceEvent);
    return markKnowledge(actor, subject, "knownName", sourceEvent);
  }

  public synchronized String markEffectKnown(String actor, String subject, String sourceEvent) {
    DiagnosticLog.record("core.markEffectKnown", "actor", actor, "subject", subject, "sourceEvent", sourceEvent);
    return markKnowledge(actor, subject, "knownEffect", sourceEvent);
  }

  private String markKnowledge(String actor, String subject, String field, String sourceEvent) {
    JSONObject state = parseState(liveStateJson);
    try {
      CharacterKnowledge.normalize(state);
      CharacterKnowledge.mark(state, actor, subject, field, sourceEvent);
      preparedTurns.clear();
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception error) {
      DiagnosticLog.record("core.error", "error", error);
      throw new IllegalArgumentException("Invalid Core knowledge update", error);
    }
  }

  public synchronized String startCombatRuntime(String entityKey, int gmLogIndex) {
    DiagnosticLog.record("core.startCombatRuntime", "entityKey", entityKey, "gmLogIndex", gmLogIndex);
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);
      JSONArray entityKeys = entityCore.activeEncounterKeys(working);
      String fallback = entityKey == null ? "" : entityKey.trim().toLowerCase();
      if (entityKeys.length() == 0 && CombatChoiceEngine.isKnownEntity(fallback)) {
        entityKeys.put(fallback);
      }
      if (entityKeys.length() == 0) throw new IllegalStateException("Không có Entity encounter để bắt đầu combat.");

      JSONObject root = working.getJSONObject(EmergentTurnEngine.ROOT_KEY);
      String rngTurnId = root.optString("lastCommittedTurnId", "").trim();
      if (rngTurnId.isEmpty()) {
        rngTurnId = emergentTurnEngine.nextTurnId(
            persisted, "combat:start:" + stringArraySignature(entityKeys));
      }
      CombatChoiceEngine.start(
          working, entityKeys, gmLogIndex, rngTurnId, emergentTurnEngine.stateVersion(persisted));
      persist(working);
      return clientSafeState(working).toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      throw new IllegalStateException("Không thể khởi tạo combat runtime.", e);
    }
  }

  public synchronized String processCombatResolution(String stateJson) {
    DiagnosticLog.record("core.processCombatResolution", "stateJson", stateJson);
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      emergentTurnEngine.catchUpProjections(persisted);

      int preVersion = emergentTurnEngine.stateVersion(persisted);
      JSONObject working = deepCopy(persisted);
      JSONObject beforeCombat = working.optJSONObject("combat");
      JSONArray entityRefs = combatEntityKeys(beforeCombat);
      String activeEntityKey = activeCombatEntityKey(beforeCombat);
      String turnId = emergentTurnEngine.nextTurnId(
          persisted, "combat:resolve:" + stringArraySignature(entityRefs)
              + ":" + (beforeCombat == null ? 0 : beforeCombat.optInt("round", 0))
              + ":" + (beforeCombat == null ? 0 : beforeCombat.optInt("actorIndex", 0))
              + ":" + (beforeCombat == null ? 0 : beforeCombat.optInt("activeEntityIndex", 0)));

      boolean wasActive = CombatChoiceEngine.isActive(working);
      if (!wasActive) return response(true, persisted, null, "duplicate_combat_resolution", null);
      CombatChoiceEngine.resolveFinalized(working);
      CombatChoiceEngine.normalizeTerminalEncounter(working);
      boolean active = CombatChoiceEngine.isActive(working);
      JSONObject combat = working.optJSONObject("combat");
      String outcome = combat == null ? "" : combat.optString("outcome", "");
      String resolvedActor = combat == null ? "" : combat.optString("resolvedActorName", "");
      JSONArray deaths = combat == null || combat.optJSONArray("entityDeathsThisTurn") == null
          ? new JSONArray() : new JSONArray(combat.getJSONArray("entityDeathsThisTurn").toString());

      JSONArray events = new JSONArray();
      JSONArray threadEffects = new JSONArray();
      for (int i = 0; i < entityRefs.length(); i++) {
        String key = entityRefs.optString(i, "");
        if (key.isEmpty()) continue;
        if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
          threadEffects.put(emergentTurnEngine.threadEffect(
              "ENTITY_ENCOUNTER", new JSONArray().put(key), "TERMINATE",
              "victory".equals(outcome) ? "RESOLVED" : "FAILED"));
        } else if (wasActive && active) {
          threadEffects.put(emergentTurnEngine.threadEffect(
              "ENTITY_ENCOUNTER", new JSONArray().put(key), "SEED_OR_ADVANCE", null));
        }
      }

      String eventSubject = activeEntityKey;
      if (!active && "victory".equals(outcome) && deaths.length() > 0) {
        JSONObject lastDeath = deaths.optJSONObject(deaths.length() - 1);
        if (lastDeath != null) eventSubject = lastDeath.optString("key", eventSubject);
      }
      if (eventSubject.isEmpty()) eventSubject = entityRefs.optString(0, "combat");

      events.put(emergentTurnEngine.event(
          turnId, events,
          !active && "victory".equals(outcome) ? "COMBAT_VICTORY"
              : !active && "defeat".equals(outcome) ? "COMBAT_DEFEAT"
              : "COMBAT_HAND_RESOLVED",
          "LOCAL",
          eventSubject,
          new JSONObject()
              .put("factPredicate", "combat_resolution")
              .put("factValue", outcome.isEmpty() ? "ongoing" : outcome)
              .put("resolvedActor", resolvedActor)
              .put("entityRefs", new JSONArray(entityRefs.toString()))
              .put("entityDeaths", deaths)
              .put("causedBy", "player")
              .put("observedByPlayer", true),
          threadEffects));

      if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
        incrementTurn(working);
      }

      if (wasActive && !active) {
        narrativeChapterCore.noteCombatOutcome(working, entityRefs, outcome);
        if ("victory".equals(outcome)) applyNarrativeBeatTrigger(working, events, turnId);
      }
      emergentTurnEngine.validateBatch(turnId, events);
      emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      if (emergentTurnEngine.stateVersion(working) != preVersion + 1) {
        throw new IllegalStateException("Combat commit stateVersion drift");
      }
      working.put("saveVersion", CURRENT_SAVE_VERSION);
      if (wasActive && !active && "victory".equals(outcome)) {
        JSONObject evidence = CommittedTurnNarrationEvidence.fromState(working, turnId);
        JSONObject closure = OfflinePresenter.present(
            safePresentationEvents(working, evidence),
            () -> { throw new IllegalStateException("Victory presentation must be offline"); });
        JSONArray log = working.optJSONArray("log");
        if (log == null) log = new JSONArray();
        log.put(new JSONObject().put("role", "gm").put("text", closure.getString("reply"))
            .put("presentationId", turnId + ":victory"));
        working.put("log", log);
      }
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      return response(true, working, null,
          active ? "combat_turn_resolved" : "combat_finished", null);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "combat_resolve_rejected", null);
    }
  }


  public synchronized String restartAfterDeath() {
    DiagnosticLog.record("core.restartAfterDeath");
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
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "death_restart_rejected", null);
    }
  }


  public synchronized String processItemAction(String stateJson, String itemId, String operation,
                                               String targetId, int quantity) {
    DiagnosticLog.record("core.processItemAction", "stateJson", stateJson, "itemId", itemId, "operation", operation, "targetId", targetId, "quantity", quantity);
    return processItemAction(stateJson, "cao_minh", itemId, operation, targetId, quantity);
  }

  public synchronized String processItemAction(String stateJson, String ownerId, String itemId,
                                               String operation, String targetId, int quantity) {
    DiagnosticLog.record("core.processItemAction", "stateJson", stateJson, "ownerId", ownerId, "itemId", itemId, "operation", operation, "targetId", targetId, "quantity", quantity);
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
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "item_action_rejected", null);
    }
  }

  public synchronized String processCoreUpgrade(String stateJson, String characterId, String stat) {
    DiagnosticLog.record("core.processCoreUpgrade", "stateJson", stateJson, "characterId", characterId, "stat", stat);
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
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "core_upgrade_rejected", null);
    }
  }

  public synchronized String levelSnapshotDescriptor(String stateJson) {
    DiagnosticLog.record("core.levelSnapshotDescriptor", "stateJson", stateJson);
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return levelCore.snapshotDescriptor(state);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return "{\"level\":0}";
    }
  }

  public synchronized String normalizeState(String stateJson) {
    DiagnosticLog.record("core.normalizeState", "stateJson", stateJson);
    JSONObject state = parseState(stateJson);
    try {
      JSONObject persisted = parseState(liveStateJson);
      if (persisted.length() > 0) {
        state = persisted;
      } else {
        state = newGameState(state);
        narrativeChapterCore.startNewGame(state);
      }
      normalizeCoreState(state);
      characterProgressionCore.applyExplorerTurnRecovery(state);
      emergentTurnEngine.normalizeState(state);
      emergentTurnEngine.catchUpProjections(state);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      debug("normalizeState failed: " + e.getMessage());
    }
    return clientSafeState(state).toString();
  }

  public synchronized String currentCoreState() {
    return clientSafeState(parseState(liveStateJson)).toString();
  }

  public synchronized String combatRollRuntime() {
    DiagnosticLog.record("core.combatRollRuntime");
    return mutateCombatRuntime("ROLL", -1, false);
  }

  public synchronized String combatHoldRuntime(int dieIndex, boolean held) {
    DiagnosticLog.record("core.combatHoldRuntime", "dieIndex", dieIndex, "held", held);
    return mutateCombatRuntime("HOLD", dieIndex, held);
  }

  public synchronized String combatFinishRuntime() {
    DiagnosticLog.record("core.combatFinishRuntime");
    return mutateCombatRuntime("FINISH", -1, false);
  }

  public synchronized String combatTargetRuntime(int entityIndex) {
    DiagnosticLog.record("core.combatTargetRuntime", "entityIndex", entityIndex);
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);
      CombatChoiceEngine.setTargetEntity(working, entityIndex);
      persisted.put("combat", new JSONObject(working.getJSONObject("combat").toString()));
      persist(persisted);
      return clientSafeState(persisted).toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      throw new IllegalStateException("Không thể đổi mục tiêu combat.", e);
    }
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
      DiagnosticLog.record("core.error", "error", e);
      throw new IllegalStateException("Không thể cập nhật combat runtime.", e);
    }
  }

  public synchronized String startNewGame(String initialJson) {
    DiagnosticLog.record("core.startNewGame", "initialJson", initialJson);
    preparedTurns.clear();
    liveStateJson = "{}";
    JSONObject state = new JSONObject();
    try {
      state = newGameState(parseState(initialJson));
      normalizeCoreState(state);
      narrativeChapterCore.startNewGame(state);
      emergentTurnEngine.normalizeState(state);
      emergentTurnEngine.catchUpProjections(state);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      throw new IllegalStateException("Không thể khởi tạo Narrative V2.", e);
    }
    return clientSafeState(state).toString();
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

  public synchronized String prepareNarrativeEdit() {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      emergentTurnEngine.catchUpProjections(persisted);
      if (!narrativeChapterCore.enabled(persisted)) {
        return response(false, persisted, "Narrative V2 không hoạt động.", "narrative_v2_disabled", null);
      }

      if (narrativeChapterCore.pendingLevelAdvance(persisted)) {
        String next = levelCore.advanceNarrativeLevel(persisted);
        if (next.isEmpty()) {
          JSONObject root = persisted.getJSONObject(NarrativeChapterCore.ROOT_KEY);
          root.put("pendingLevelAdvance", false)
              .put("loadingRequired", false)
              .put("loadingState", "COMPLETE")
              .put("gameComplete", true);
          persist(persisted);
          return new JSONObject()
              .put("handled", true)
              .put("gameComplete", true)
              .put("state", clientSafeState(persisted))
              .put("context", narrativeChapterCore.situationSnapshot(persisted))
              .toString();
        }
        narrativeChapterCore.beginNextLevel(persisted);
      }

      JSONObject prepared = narrativeChapterCore.prepareLoading(
          persisted, entityCore.situationCandidates(persisted));
      JSONObject narrativeContext = prepared.getJSONObject("context");
      String levelKey = persisted.optString(
          LevelCore.LEVEL_KEY, String.valueOf(persisted.optInt("currentLevel", 0))).trim();
      narrativeContext.put("levelSceneContext", levelCore.sceneKnowledgeContext(
          levelKey, Math.max(1, persisted.optInt("turn", 1)), ""));
      persist(persisted);
      return new JSONObject()
          .put("handled", true)
          .put("state", clientSafeState(persisted))
          .put("needsMissionBoard", prepared.optBoolean("needsMissionBoard", false))
          .put("context", prepared.getJSONObject("context"))
          .toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "narrative_edit_prepare_failed", null);
    }
  }

  public synchronized String commitNarrativeEdit(String missionJson, String directorJson) {
    JSONObject persisted = parseState(liveStateJson);
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      JSONObject working = deepCopy(persisted);
      narrativeChapterCore.commitEdit(working, parseState(missionJson), parseState(directorJson));

      String turnId = emergentTurnEngine.nextTurnId(
          persisted, "narrative:edit:"
              + working.optString(LevelCore.LEVEL_KEY, "0") + ":"
              + working.getJSONObject(NarrativeChapterCore.ROOT_KEY).optInt("actIndex", 1));
      JSONArray events = new JSONArray();
      applyNarrativeBeatTrigger(working, events, turnId);
      if (events.length() > 0) {
        emergentTurnEngine.validateBatch(turnId, events);
        emergentTurnEngine.commitAuthoritative(persisted, working, turnId, events, null);
      }

      working.put("saveVersion", CURRENT_SAVE_VERSION);
      projectBeforePersist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      return response(true, working, null, "narrative_edit_committed", null);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return response(false, persisted, safeMessage(e), "narrative_edit_rejected", null);
    }
  }

  public synchronized String narrativeOpeningFrame() {
    JSONObject state = parseState(liveStateJson);
    try {
      normalizeCoreState(state);
      JSONObject root = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
      int version = root == null ? 0 : Math.max(0, root.optInt("stateVersion", 0));
      JSONObject narrative = state.getJSONObject(NarrativeChapterCore.ROOT_KEY);
      String turnId = "narrative-opening:"
          + state.optString(LevelCore.LEVEL_KEY, "0") + ":" + narrative.optInt("actIndex", 1)
          + ":" + narrative.optInt("editRevision", 0);
      TurnRng rng = new TurnRng(
          turnId, version, EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
      JSONObject environment = levelCore.sceneDirectorEnvironment(
          state, "Mở đầu hồi", bound -> rng.nextInt(TurnRng.Scope.WORLD_REACTION, bound));

      JSONArray present = new JSONArray();
      JSONArray party = state.optJSONArray("party");
      for (int i = 0; party != null && i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member == null || !member.optBoolean("joined", false)) continue;
        present.put(new JSONObject()
            .put("id", member.optString("id", ""))
            .put("name", member.optString("name", member.optString("id", ""))));
      }
      JSONObject facts = new JSONObject()
          .put("playerIntent", new JSONObject().put("text", "Mở đầu hồi"))
          .put("presentCharacters", present);
      JSONObject frame = SceneDirector.compose(state, facts, environment);
      return narrativeChapterCore.decorateSceneFrame(state, frame).toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return "{}";
    }
  }

  public synchronized String commitNarrativeOpening(String gmEntryJson) {
    JSONObject state = parseState(liveStateJson);
    try {
      normalizeCoreState(state);
      if (!narrativeChapterCore.enabled(state) || narrativeChapterCore.loadingRequired(state)) {
        throw new IllegalStateException("Narrative act is not ready for presentation.");
      }
      JSONObject entry = new JSONObject(gmEntryJson);
      entry.put("role", "gm");
      JSONArray log = state.optJSONArray("log");
      if (log == null) log = new JSONArray();
      log.put(entry);
      state.put("log", log);
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      throw new IllegalStateException("Không thể commit mở đầu Narrative V2.", e);
    }
  }

  public synchronized void clear() {
    DiagnosticLog.record("core.clear");
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
      DiagnosticLog.record("core.error", "error", e);
      return new JSONObject();
    }
  }

  private JSONArray parseArray(String json) {
    try {
      if (json == null || json.trim().isEmpty()) return new JSONArray();
      return new JSONArray(json);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
      return new JSONArray();
    }
  }

  private JSONObject deepCopy(JSONObject source) {
    try {
      return new JSONObject(source == null ? "{}" : source.toString());
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
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
      DiagnosticLog.record("core.error", "error", e);
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
    JSONObject root = state == null ? null : state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    DiagnosticLog.record("state.commit",
        "turn", state == null ? -1 : state.optInt("turn", -1),
        "levelKey", state == null ? "" : state.optString(LevelCore.LEVEL_KEY, ""),
        "stateVersion", root == null ? -1 : root.optInt("stateVersion", -1));
  }

  private void projectBeforePersist(JSONObject state) {
    if (state == null) return;
    try {
      CharacterKnowledge.normalize(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterDetailCore.projectState(state);
      narrativeChapterCore.normalizeState(state);
    } catch (Exception e) {
      DiagnosticLog.record("core.error", "error", e);
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
      DiagnosticLog.record("core.error", "error", e);
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
    DiagnosticLog.record("core.diagnostic", "message", message);
    if (debugLogging) Log.d(TAG, message);
  }
}
