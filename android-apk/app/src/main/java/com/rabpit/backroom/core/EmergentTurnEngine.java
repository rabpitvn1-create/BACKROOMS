package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Authoritative event ledger plus deterministic post-commit projections for emergent gameplay.
 *
 * World state is still stored in the existing save JSON. This class owns only the transaction
 * metadata/projections that prevent AI narration from becoming a second source of truth.
 */
final class EmergentTurnEngine {
  static final String ROOT_KEY = "emergent";
  static final String CANON_VERSION = "emergent-canon-v1";
  static final String RNG_SCHEMA_VERSION = "scoped-rng-v1";
  static final String RESOLVER_VERSION = "emergent-resolver-v1";

  private static final int SCHEMA_VERSION = 3;
  private static final int TRACE_LIMIT = 20;
  private static final int DORMANT_AFTER_TURNS = 12;

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    JSONObject root = state.optJSONObject(ROOT_KEY);
    if (root == null) root = new JSONObject();

    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("canonVersion", CANON_VERSION);
    if (root.optString("saveId", "").trim().isEmpty()) {
      root.put("saveId", UUID.randomUUID().toString());
    }
    root.put("stateVersion", Math.max(0, root.optInt("stateVersion", 0)));
    root.put("commitSequence", Math.max(0, root.optInt("commitSequence", 0)));

    ensureArray(root, "historicalFacts");
    ensureArray(root, "beliefs");
    ensureArray(root, "threads");
    ensureArray(root, "threadArchive");
    ensureArray(root, "commitLog");
    ensureArray(root, "selectionTrace");
    ensureObject(root, "selectionIndex");
    ensureObject(root, "currentStateMeta");
    SchedulerProjection.normalize(root);
    boolean campaignSkeletonReset = CampaignSkeleton.normalize(root, state);
    boolean narrativeSkeletonReset = NarrativeSkeleton.normalize(root, state);
    normalizeDirector(root);

    JSONObject watermarks = ensureObject(root, "projectionWatermarks");
    watermarks.put("selectionCooldown", Math.max(0, watermarks.optInt("selectionCooldown", 0)));
    watermarks.put("scheduler", Math.max(0, watermarks.optInt("scheduler", 0)));
    int campaignSkeletonMark = Math.max(0, watermarks.optInt("campaignSkeleton", 0));
    if (campaignSkeletonReset) campaignSkeletonMark = 0;
    watermarks.put("campaignSkeleton",
        Math.min(root.getInt("commitSequence"), campaignSkeletonMark));
    int narrativeSkeletonMark = Math.max(0, watermarks.optInt("narrativeSkeleton", 0));
    if (narrativeSkeletonReset) narrativeSkeletonMark = 0;
    watermarks.put("narrativeSkeleton",
        Math.min(root.getInt("commitSequence"), narrativeSkeletonMark));
    watermarks.remove("skeleton");
    watermarks.put("director", campaignSkeletonReset || narrativeSkeletonReset
        ? 0 : Math.max(0, watermarks.optInt("director", 0)));

    state.put(ROOT_KEY, root);
  }

  int stateVersion(JSONObject state) throws Exception {
    normalizeState(state);
    return state.getJSONObject(ROOT_KEY).getInt("stateVersion");
  }

  String nextTurnId(JSONObject state, String action) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    int version = root.getInt("stateVersion");
    String saveId = root.getString("saveId");
    return "turn-" + (version + 1) + "-" + shortHash(saveId + "|" + version + "|" + safe(action));
  }

  JSONObject candidate(String kind, String situationKey, String category, double chancePercent,
                       String publicSummary, String payloadKey, boolean proposalRequired) throws Exception {
    if (situationKey == null || situationKey.trim().isEmpty()) {
      throw new IllegalArgumentException("situationKey is required");
    }
    return new JSONObject()
        .put("candidateId", situationKey)
        .put("situationKey", situationKey)
        .put("kind", safe(kind))
        .put("category", safe(category).isEmpty() ? "ENVIRONMENTAL" : category)
        .put("chancePercent", Math.max(0.0d, Math.min(99.999999d, chancePercent)))
        .put("publicSummary", safe(publicSummary))
        .put("payloadKey", safe(payloadKey))
        .put("proposalRequired", proposalRequired)
        .put("eligibilityRuleId", "canon:" + situationKey)
        .put("tags", new JSONArray().put(safe(category)).put(safe(kind)))
        .put("keyRefs", safe(payloadKey).isEmpty() ? new JSONArray() : new JSONArray().put(payloadKey));
  }

  JSONObject selectCandidate(JSONObject state, JSONArray rawCandidates, TurnRng rng, int selectionTurn)
      throws Exception {
    normalizeState(state);
    if (rng == null) throw new IllegalArgumentException("TurnRng is required");
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONObject mandatory = firstMandatoryCandidate(rawCandidates);
    if (mandatory != null) {
      JSONObject result = new JSONObject(mandatory.toString()).put("selectedNone", false);
      JSONArray traceCandidates = new JSONArray().put(new JSONObject()
          .put("situationKey", result.optString("situationKey", ""))
          .put("category", result.optString("category", ""))
          .put("finalWeight", 1.0d)
          .put("mandatory", true)
          .put("eligibilityRuleId", result.optString("eligibilityRuleId", "")));
      JSONObject trace = new JSONObject()
          .put("turn", selectionTurn)
          .put("candidates", traceCandidates)
          .put("selectedSituationKey", result.optString("situationKey", ""))
          .put("selectedNone", false)
          .put("selectionMode", "MANDATORY");
      JSONArray traces = root.getJSONArray("selectionTrace");
      traces.put(trace);
      while (traces.length() > TRACE_LIMIT) traces.remove(0);
      root.put("lastSelection", new JSONObject(result.toString()));
      state.put(ROOT_KEY, root);
      return result;
    }

    JSONObject index = root.getJSONObject("selectionIndex");
    JSONObject director = root.getJSONObject("director");
    JSONObject modifiers = director.optJSONObject("tagWeightModifiers");
    if (modifiers == null) modifiers = new JSONObject();

    List<WeightedCandidate> weighted = new ArrayList<>();
    double totalHazard = 0.0d;
    if (rawCandidates != null) {
      for (int i = 0; i < rawCandidates.length(); i++) {
        JSONObject source = rawCandidates.optJSONObject(i);
        if (source == null) continue;
        JSONObject candidate = new JSONObject(source.toString());
        String key = candidate.optString("situationKey", "").trim();
        if (key.isEmpty()) continue;

        String category = candidate.optString("category", "ENVIRONMENTAL").trim().toUpperCase(Locale.ROOT);
        JSONObject prior = index.optJSONObject(key);
        int lastTurn = prior == null ? Integer.MIN_VALUE / 4 : prior.optInt("lastSelectedTurn", Integer.MIN_VALUE / 4);
        int cooldown = Math.max(0, candidate.optInt("cooldownTurns", cooldownTurns(category)));
        boolean cooling = selectionTurn <= lastTurn + cooldown;
        double p = cooling ? 0.0d : Math.max(0.0d,
            Math.min(0.99999999d, candidate.optDouble("chancePercent", 0.0d) / 100.0d));
        double hazard = p <= 0.0d ? 0.0d : -Math.log(1.0d - p);
        double categoryModifier = clamp(modifiers.optDouble(category, 1.0d), 0.25d, 2.0d);
        double keyRefModifier = NarrativeSkeleton.keyRefWeightModifier(root, candidate);
        weighted.add(new WeightedCandidate(
            candidate, hazard, categoryModifier, keyRefModifier, cooling));
        totalHazard += hazard;
      }
    }

    double eventOdds = totalHazard <= 0.0d ? 0.0d : Math.expm1(totalHazard);
    double totalWeight = 1.0d; // NONE always participates through the same selector.
    JSONArray traceCandidates = new JSONArray();
    for (WeightedCandidate item : weighted) {
      double baseWeight = totalHazard <= 0.0d ? 0.0d : eventOdds * (item.hazard / totalHazard);
      item.finalWeight = baseWeight * item.categoryModifier * item.keyRefModifier;
      totalWeight += item.finalWeight;
      traceCandidates.put(new JSONObject()
          .put("situationKey", item.candidate.getString("situationKey"))
          .put("category", item.candidate.optString("category", ""))
          .put("finalWeight", item.finalWeight)
          .put("categoryWeightModifier", item.categoryModifier)
          .put("keyRefWeightModifier", item.keyRefModifier)
          .put("cooldownBlocked", item.cooling)
          .put("eligibilityRuleId", item.candidate.optString("eligibilityRuleId", "")));
    }
    traceCandidates.put(new JSONObject()
        .put("situationKey", "NONE")
        .put("category", "QUIET")
        .put("finalWeight", 1.0d)
        .put("eligibilityRuleId", "canon:none"));

    double draw = rng.nextUnit(TurnRng.Scope.CANDIDATE_SELECTION) * totalWeight;
    JSONObject selected = null;
    double cursor = 1.0d;
    if (draw >= 1.0d) {
      for (WeightedCandidate item : weighted) {
        cursor += item.finalWeight;
        if (draw < cursor && item.finalWeight > 0.0d) {
          selected = new JSONObject(item.candidate.toString());
          break;
        }
      }
    }

    JSONObject result;
    if (selected == null) {
      result = new JSONObject()
          .put("candidateId", "NONE")
          .put("situationKey", "NONE")
          .put("kind", "NONE")
          .put("category", "QUIET")
          .put("publicSummary", "Không có biến cố chủ động mới trong lượt này.")
          .put("proposalRequired", false)
          .put("selectedNone", true);
    } else {
      result = selected.put("selectedNone", false);
    }

    JSONObject trace = new JSONObject()
        .put("turn", selectionTurn)
        .put("candidates", traceCandidates)
        .put("selectedSituationKey", result.getString("situationKey"))
        .put("selectedNone", result.optBoolean("selectedNone", false))
        .put("rngScope", TurnRng.Scope.CANDIDATE_SELECTION.name())
        .put("rngDrawSeq", rng.drawsUsed(TurnRng.Scope.CANDIDATE_SELECTION) - 1);
    JSONArray traces = root.getJSONArray("selectionTrace");
    traces.put(trace);
    while (traces.length() > TRACE_LIMIT) traces.remove(0);
    root.put("lastSelection", new JSONObject(result.toString()));
    state.put(ROOT_KEY, root);
    return result;
  }

  JSONObject event(String turnId, JSONArray events, String eventType, String impactScope,
                   String subjectRef, JSONObject params, JSONArray threadEffects) throws Exception {
    int seq = events == null ? 0 : events.length();
    EventCanonRegistry.Spec spec = EventCanonRegistry.require(eventType);
    if (impactScope != null && !impactScope.trim().isEmpty()
        && !spec.impactScope.equals(impactScope.trim())) {
      throw new IllegalStateException(
          "Event impact_scope disagrees with canon: " + eventType + " expected " + spec.impactScope);
    }
    JSONObject normalizedParams = params == null ? new JSONObject() : new JSONObject(params.toString());
    if (!safe(subjectRef).isEmpty()) normalizedParams.put("subjectRef", subjectRef);
    JSONObject event = new JSONObject()
        .put("eventId", turnId + ":e" + seq)
        .put("eventSeq", seq)
        .put("eventType", eventType)
        .put("eventSemantics", spec.semanticsJson())
        .put("impactScope", spec.impactScope)
        .put("actorRefs", new JSONArray())
        .put("targetRefs", safe(subjectRef).isEmpty() ? new JSONArray() : new JSONArray().put(subjectRef))
        .put("params", normalizedParams);
    if (threadEffects != null && threadEffects.length() > 0) {
      event.put("threadEffects", new JSONArray(threadEffects.toString()));
    }
    return event;
  }

  JSONObject threadEffect(String threadType, JSONArray keyRefs, String effect,
                          String terminalOutcome) throws Exception {
    JSONArray refs = keyRefs == null ? new JSONArray() : new JSONArray(keyRefs.toString());
    JSONObject output = new JSONObject()
        .put("threadType", threadType)
        .put("keyRefs", refs)
        .put("effect", effect);
    String threadId = deterministicThreadId(threadType, refs);
    if ("ADVANCE".equals(effect) || "TERMINATE".equals(effect) || "DORMANCY".equals(effect)) {
      output.put("targetThreadId", threadId);
    }
    if ("TERMINATE".equals(effect)) {
      if (safe(terminalOutcome).isEmpty()) throw new IllegalArgumentException("terminalOutcome is required");
      output.put("terminalOutcome", terminalOutcome);
    }
    return output;
  }

  void appendThreadResolutionEvents(JSONObject state, JSONArray events, String turnId, int currentTurn)
      throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONArray threads = root.getJSONArray("threads");
    List<String> touched = touchedThreadIds(events);
    for (int i = 0; i < threads.length(); i++) {
      JSONObject thread = threads.optJSONObject(i);
      if (thread == null) continue;
      String status = thread.optString("status", "");
      if (!("ACTIVE".equals(status) || "DORMANT".equals(status))) continue;
      String id = thread.optString("threadId", "");
      if (id.isEmpty() || touched.contains(id)) continue;
      JSONArray conditions = thread.optJSONArray("resolutionConditions");
      ThreadPredicateEngine.validate(conditions);
      if (!ThreadPredicateEngine.allSatisfied(state, conditions)) continue;
      JSONArray effects = new JSONArray().put(new JSONObject()
          .put("threadType", thread.optString("threadType", ""))
          .put("keyRefs", thread.optJSONArray("keyRefs") == null
              ? new JSONArray() : new JSONArray(thread.getJSONArray("keyRefs").toString()))
          .put("effect", "TERMINATE")
          .put("terminalOutcome", "RESOLVED")
          .put("targetThreadId", id));
      events.put(event(turnId, events, "THREAD_RESOLUTION_CONDITION_MET", "LOCAL", id,
          new JSONObject().put("observedByPlayer", false).put("impactEligible", false), effects));
      touched.add(id);
    }
  }

  void appendDormancyEvents(JSONObject state, JSONArray events, String turnId, int currentTurn) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    JSONArray threads = root.getJSONArray("threads");
    List<String> touched = touchedThreadIds(events);

    for (int i = 0; i < threads.length(); i++) {
      JSONObject thread = threads.optJSONObject(i);
      if (thread == null || !"ACTIVE".equals(thread.optString("status"))) continue;
      String id = thread.optString("threadId", "");
      if (id.isEmpty() || touched.contains(id)) continue;
      int lastTouched = thread.optInt("lastTouchedTurn", currentTurn);
      if (currentTurn - lastTouched < DORMANT_AFTER_TURNS) continue;

      JSONArray effects = new JSONArray().put(new JSONObject()
          .put("threadType", thread.optString("threadType", ""))
          .put("keyRefs", thread.optJSONArray("keyRefs") == null
              ? new JSONArray() : new JSONArray(thread.getJSONArray("keyRefs").toString()))
          .put("effect", "DORMANCY")
          .put("targetThreadId", id));
      events.put(event(turnId, events, "THREAD_DORMANCY_REACHED", "LOCAL", id,
          new JSONObject().put("observedByPlayer", false), effects));
      touched.add(id);
    }
  }

  void validateBatch(String turnId, JSONArray events) {
    if (turnId == null || turnId.trim().isEmpty()) throw new IllegalArgumentException("turnId is required");
    if (events == null) throw new IllegalArgumentException("events are required");
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (event == null) throw new IllegalStateException("DomainEvent must be an object");
      if (event.optInt("eventSeq", -1) != i) throw new IllegalStateException("DomainEvent eventSeq drift");
      if (!event.optString("eventId", "").equals(turnId + ":e" + i)) {
        throw new IllegalStateException("DomainEvent eventId drift");
      }
      String eventType = event.optString("eventType", "").trim();
      if (eventType.isEmpty()) {
        throw new IllegalStateException("DomainEvent eventType is required");
      }
      EventCanonRegistry.Spec spec = EventCanonRegistry.require(eventType);
      if (!spec.impactScope.equals(event.optString("impactScope", ""))) {
        throw new IllegalStateException("DomainEvent impactScope disagrees with canon");
      }
      if (!EventCanonRegistry.matchesSemantics(eventType, event.optJSONArray("eventSemantics"))) {
        throw new IllegalStateException("DomainEvent semantics disagree with canon");
      }
      JSONArray effects = event.optJSONArray("threadEffects");
      if (effects == null) continue;
      for (int j = 0; j < effects.length(); j++) {
        JSONObject effect = effects.optJSONObject(j);
        if (effect == null) throw new IllegalStateException("ThreadEffect must be an object");
        String type = effect.optString("effect", "");
        if ("TERMINATE".equals(type)) {
          if (effect.optString("targetThreadId", "").trim().isEmpty()
              || effect.optString("terminalOutcome", "").trim().isEmpty()) {
            throw new IllegalStateException("TERMINATE requires targetThreadId and terminalOutcome");
          }
        }
        if (("ADVANCE".equals(type) || "DORMANCY".equals(type))
            && effect.optString("targetThreadId", "").trim().isEmpty()) {
          throw new IllegalStateException(type + " requires targetThreadId");
        }
      }
    }
  }

  boolean hasCommitted(JSONObject state, String turnId) throws Exception {
    normalizeState(state);
    JSONArray commits = state.getJSONObject(ROOT_KEY).getJSONArray("commitLog");
    for (int i = 0; i < commits.length(); i++) {
      JSONObject commit = commits.optJSONObject(i);
      if (commit != null && turnId.equals(commit.optString("turnId"))) return true;
    }
    return false;
  }

  void commitAuthoritative(JSONObject state, String turnId, JSONArray events, JSONObject selection)
      throws Exception {
    commitAuthoritative(null, state, turnId, events, selection, null);
  }

  void commitAuthoritative(JSONObject beforeState, JSONObject state, String turnId,
                           JSONArray events, JSONObject selection) throws Exception {
    commitAuthoritative(beforeState, state, turnId, events, selection, null);
  }

  void commitAuthoritative(JSONObject beforeState, JSONObject state, String turnId,
                           JSONArray events, JSONObject selection, JSONObject commitMetadata)
      throws Exception {
    normalizeState(state);
    validateBatch(turnId, events);
    if (hasCommitted(state, turnId)) return;

    JSONObject stateDelta = beforeState == null
        ? new JSONObject().put("set", new JSONObject()).put("remove", new JSONArray())
        : AuthoritativeStatePatch.diff(beforeState, state);
    if (beforeState != null && !AuthoritativeStatePatch.isEmpty(stateDelta) && events.length() == 0) {
      throw new IllegalStateException("Authoritative state changed without DomainEvent");
    }
    if (beforeState != null) {
      JSONObject replayed = AuthoritativeStatePatch.apply(beforeState, stateDelta);
      JSONObject residual = AuthoritativeStatePatch.diff(replayed, state);
      if (!AuthoritativeStatePatch.isEmpty(residual)) {
        throw new IllegalStateException("Authoritative state patch is not replay-complete");
      }
    }

    JSONObject root = state.getJSONObject(ROOT_KEY);
    int sequence = root.getInt("commitSequence") + 1;
    int nextStateVersion = root.getInt("stateVersion") + 1;
    int turn = Math.max(1, state.optInt("turn", 1));

    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.getJSONObject(i);
      projectHistoricalFact(root, event, turn);
      projectThreadEffects(root, event, turn);
    }

    String lastEventId = events.length() == 0 ? "" :
        events.getJSONObject(events.length() - 1).optString("eventId", "");
    JSONObject stateMeta = root.getJSONObject("currentStateMeta");
    JSONArray changedRoots = AuthoritativeStatePatch.changedRoots(stateDelta);
    for (int i = 0; i < changedRoots.length(); i++) {
      String path = changedRoots.optString(i, "");
      if (path.isEmpty()) continue;
      stateMeta.put(path, new JSONObject()
          .put("stateClass", "AUTHORITATIVE")
          .put("lastChangedEventId", lastEventId)
          .put("lastChangedTurn", turn));
    }

    JSONObject commit = new JSONObject()
        .put("commitSeq", sequence)
        .put("turnId", turnId)
        .put("turn", turn)
        .put("stateVersion", nextStateVersion)
        .put("canonVersion", CANON_VERSION)
        .put("rngSchemaVersion", RNG_SCHEMA_VERSION)
        .put("resolverVersion", RESOLVER_VERSION)
        .put("stateDelta", stateDelta)
        .put("events", new JSONArray(events.toString()));

    if (commitMetadata != null && commitMetadata.length() > 0) {
      commit.put("gmTransaction", new JSONObject(commitMetadata.toString()));
    }

    if (selection != null && !selection.optBoolean("selectedNone", false)) {
      commit.put("sourceSituationKey", selection.optString("situationKey", ""))
          .put("sourceSituationCategory", selection.optString("category", ""))
          .put("sourceSituationTags", selection.optJSONArray("tags") == null
              ? new JSONArray() : new JSONArray(selection.getJSONArray("tags").toString()))
          .put("sourceSituationSummary", selection.optString("publicSummary", ""));
    }

    root.getJSONArray("commitLog").put(commit);
    root.put("commitSequence", sequence);
    root.put("stateVersion", nextStateVersion);
    root.put("lastCommittedTurnId", turnId);
    state.put(ROOT_KEY, root);
  }

  boolean catchUpProjections(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    int latest = root.getInt("commitSequence");
    JSONObject marks = root.getJSONObject("projectionWatermarks");
    boolean changed = false;

    int selectionMark = marks.optInt("selectionCooldown", 0);
    if (selectionMark < latest) {
      foldSelectionIndex(root, selectionMark);
      marks.put("selectionCooldown", latest);
      changed = true;
    }

    int schedulerMark = marks.optInt("scheduler", 0);
    if (schedulerMark < latest) {
      SchedulerProjection.fold(root, schedulerMark);
      marks.put("scheduler", latest);
      changed = true;
    }

    int campaignSkeletonMark = marks.optInt("campaignSkeleton", 0);
    JSONObject campaignSkeleton = root.optJSONObject(CampaignSkeleton.ROOT_KEY);
    int campaignSkeletonDerived = campaignSkeleton == null
        ? -1 : campaignSkeleton.optInt("derivedFromCommitSeq", -1);
    if (campaignSkeletonMark < latest || campaignSkeletonDerived != latest) {
      CampaignSkeleton.rebuild(root, state);
      marks.put("campaignSkeleton", latest);
      changed = true;
    }

    int narrativeSkeletonMark = marks.optInt("narrativeSkeleton", 0);
    JSONObject narrativeSkeleton = root.optJSONObject(NarrativeSkeleton.ROOT_KEY);
    int narrativeSkeletonDerived = narrativeSkeleton == null
        ? -1 : narrativeSkeleton.optInt("derivedFromCommitSeq", -1);
    if (narrativeSkeletonMark < latest || narrativeSkeletonDerived != latest) {
      NarrativeSkeleton.rebuild(root, state);
      marks.put("narrativeSkeleton", latest);
      changed = true;
    }

    int directorMark = marks.optInt("director", 0);
    if (directorMark < latest || changed) {
      projectDirector(root, state);
      marks.put("director", latest);
      changed = true;
    }

    root.put("projectionWatermarks", marks);
    state.put(ROOT_KEY, root);
    return changed;
  }

  JSONArray schedulerCandidates(JSONObject state, int currentTurn) throws Exception {
    normalizeState(state);
    return SchedulerProjection.dueCandidates(state, currentTurn);
  }

  boolean selectionProjectionFresh(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject root = state.getJSONObject(ROOT_KEY);
    return root.getJSONObject("projectionWatermarks").optInt("selectionCooldown", -1)
        == root.optInt("commitSequence", 0);
  }

  String narrationSituation(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject root = state.getJSONObject(ROOT_KEY);
      JSONObject selection = root.optJSONObject("lastSelection");
      if (selection == null || selection.optBoolean("selectedNone", false)) {
        return "WORLD SITUATION: không có biến cố chủ động mới trong lượt này.";
      }
      String summary = selection.optString("publicSummary", "").trim();
      JSONObject proposal = selection.optJSONObject("worldProposal");
      String tactic = proposal == null ? "" : proposal.optString("actionType", "").trim();
      return "WORLD SITUATION ĐÃ COMMIT: " + (summary.isEmpty() ? selection.optString("situationKey") : summary)
          + (tactic.isEmpty() ? "" : "\nTACTIC ĐÃ COMMIT: " + tactic) + ".";
    } catch (Exception e) {
      return "WORLD SITUATION: chỉ kể những gì Core context xác nhận.";
    }
  }

  String worldProposalValidationReason(JSONObject selected, JSONObject raw) {
    if (selected == null || !selected.optBoolean("proposalRequired", false)) return "";
    JSONObject proposal = raw == null ? new JSONObject() : raw;
    String action = proposal.optString("actionType", "").trim().toUpperCase(Locale.ROOT);
    if (action.isEmpty()) return "actionType is required for the selected SituationCandidate.";
    if (!isAllowedWorldAction(action)) return "Unknown actionType: " + action;
    JSONArray allowed = selected.optJSONArray("allowedWorldActions");
    if (!containsAction(allowed, action)) {
      return "actionType " + action + " is outside selected candidate capabilities.";
    }
    String intent = proposal.optString("intentTag", "opportunistic").trim().toLowerCase(Locale.ROOT);
    if (!("aggressive".equals(intent) || "cautious".equals(intent) || "opportunistic".equals(intent))) {
      return "intentTag must be aggressive, cautious, or opportunistic.";
    }
    return "";
  }

  JSONObject sanitizeWorldProposal(JSONObject selected, JSONObject raw) throws Exception {
    JSONObject proposal = raw == null ? new JSONObject() : raw;
    if (selected == null || !selected.optBoolean("proposalRequired", false)) {
      return new JSONObject().put("actionType", "NONE").put("fallback", false);
    }
    String action = proposal.optString("actionType", "").trim().toUpperCase(Locale.ROOT);
    JSONArray allowed = selected.optJSONArray("allowedWorldActions");
    if (!worldProposalValidationReason(selected, proposal).isEmpty()) {
      action = selected.optString("fallbackAction", "INTERCEPT").trim().toUpperCase(Locale.ROOT);
      if (!isAllowedWorldAction(action) || !containsAction(allowed, action)) {
        action = firstAllowedAction(allowed);
      }
      return new JSONObject().put("actionType", action).put("intentTag", "opportunistic")
          .put("fallback", true);
    }
    String intent = proposal.optString("intentTag", "opportunistic").trim().toLowerCase(Locale.ROOT);
    if (!("aggressive".equals(intent) || "cautious".equals(intent) || "opportunistic".equals(intent))) {
      intent = "opportunistic";
    }
    return new JSONObject().put("actionType", action).put("intentTag", intent).put("fallback", false);
  }

  private static boolean containsAction(JSONArray allowed, String action) {
    if (allowed == null || allowed.length() == 0) return isAllowedWorldAction(action);
    for (int i = 0; i < allowed.length(); i++) {
      if (action.equals(allowed.optString(i, "").trim().toUpperCase(Locale.ROOT))) return true;
    }
    return false;
  }

  private static String firstAllowedAction(JSONArray allowed) {
    if (allowed != null) {
      for (int i = 0; i < allowed.length(); i++) {
        String action = allowed.optString(i, "").trim().toUpperCase(Locale.ROOT);
        if (isAllowedWorldAction(action)) return action;
      }
    }
    return "INTERCEPT";
  }

  private static boolean isAllowedWorldAction(String action) {
    return "STALK".equals(action) || "INTERCEPT".equals(action) || "AMBUSH".equals(action)
        || "DIRECT_ATTACK".equals(action) || "LURE".equals(action) || "OBSERVE".equals(action);
  }

  static String deterministicThreadId(String threadType, JSONArray keyRefs) {
    List<String> refs = new ArrayList<>();
    if (keyRefs != null) {
      for (int i = 0; i < keyRefs.length(); i++) {
        String value = keyRefs.optString(i, "").trim();
        if (!value.isEmpty()) refs.add(value);
      }
    }
    Collections.sort(refs);
    return "thread:" + shortHash(safe(threadType) + ":" + String.join(",", refs));
  }

  private static List<String> touchedThreadIds(JSONArray events) {
    List<String> ids = new ArrayList<>();
    if (events == null) return ids;
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      JSONArray effects = event == null ? null : event.optJSONArray("threadEffects");
      if (effects == null) continue;
      for (int j = 0; j < effects.length(); j++) {
        JSONObject effect = effects.optJSONObject(j);
        if (effect == null) continue;
        String id = effect.optString("targetThreadId", "");
        if (id.isEmpty()) {
          id = deterministicThreadId(effect.optString("threadType", ""), effect.optJSONArray("keyRefs"));
        }
        if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
      }
    }
    return ids;
  }

  private void projectHistoricalFact(JSONObject root, JSONObject event, int turn) throws Exception {
    JSONArray semantics = event.optJSONArray("eventSemantics");
    boolean stateChange = false;
    if (semantics != null) {
      for (int i = 0; i < semantics.length(); i++) {
        if ("STATE_CHANGE".equals(semantics.optString(i))) stateChange = true;
      }
    }
    if (!stateChange) return;

    JSONObject params = event.optJSONObject("params");
    if (params == null) params = new JSONObject();
    String subject = params.optString("subjectRef", "");
    String factId = "fact:" + event.getString("eventId");
    Object value = params.has("factValue") ? params.opt("factValue") : Boolean.TRUE;
    JSONObject fact = new JSONObject()
        .put("factId", factId)
        .put("eventId", event.getString("eventId"))
        .put("subjectRef", subject)
        .put("predicate", params.optString("factPredicate",
            event.getString("eventType").toLowerCase(Locale.ROOT)))
        .put("value", value == null ? JSONObject.NULL : value)
        .put("turn", turn)
        .put("impactScope", event.optString("impactScope", "LOCAL"))
        .put("causedBy", params.optString("causedBy", ""))
        .put("impactEligible", params.optBoolean("impactEligible", true));
    root.getJSONArray("historicalFacts").put(fact);
    BeliefResolver.project(root, event, fact, turn);
  }

  private void projectThreadEffects(JSONObject root, JSONObject event, int turn) throws Exception {
    JSONArray effects = event.optJSONArray("threadEffects");
    if (effects == null) return;
    JSONArray threads = root.getJSONArray("threads");
    for (int i = 0; i < effects.length(); i++) {
      JSONObject effect = effects.getJSONObject(i);
      String type = effect.optString("effect", "");
      String threadType = effect.optString("threadType", "");
      JSONArray refs = effect.optJSONArray("keyRefs");
      if (refs == null) refs = new JSONArray();
      String id = effect.optString("targetThreadId", deterministicThreadId(threadType, refs));
      JSONObject thread = findBy(threads, "threadId", id);

      if ("SEED_OR_ADVANCE".equals(type)) {
        if (thread == null) {
          thread = new JSONObject()
              .put("threadId", id)
              .put("threadType", threadType)
              .put("threadKey", threadType + ":" + sortedRefs(refs))
              .put("keyRefs", new JSONArray(refs.toString()))
              .put("objectiveState", new JSONObject())
              .put("knowledgeState", new JSONObject())
              .put("resolutionConditions", ThreadPredicateEngine.conditionsForThreadType(threadType, refs))
              .put("status", "ACTIVE");
          threads.put(thread);
        }
        thread.put("status", "ACTIVE").put("lastTouchedTurn", turn);
      } else if ("ADVANCE".equals(type)) {
        if (thread == null) throw new IllegalStateException("ADVANCE target thread does not exist: " + id);
        thread.put("status", "ACTIVE").put("lastTouchedTurn", turn);
      } else if ("DORMANCY".equals(type)) {
        if (thread == null) throw new IllegalStateException("DORMANCY target thread does not exist: " + id);
        thread.put("status", "DORMANT").put("lastTouchedTurn", turn);
      } else if ("TERMINATE".equals(type)) {
        if (thread == null) {
          thread = new JSONObject()
              .put("threadId", id)
              .put("threadType", threadType)
              .put("threadKey", threadType + ":" + sortedRefs(refs))
              .put("keyRefs", new JSONArray(refs.toString()))
              .put("objectiveState", new JSONObject())
              .put("knowledgeState", new JSONObject())
              .put("resolutionConditions", ThreadPredicateEngine.conditionsForThreadType(threadType, refs));
          threads.put(thread);
        }
        String outcome = effect.getString("terminalOutcome");
        thread.put("status", outcome).put("lastTouchedTurn", turn);
        archiveThread(root, thread, outcome, turn);
      }
    }
  }

  private void archiveThread(JSONObject root, JSONObject thread, String outcome, int turn) throws Exception {
    JSONArray archive = root.getJSONArray("threadArchive");
    String id = thread.getString("threadId");
    JSONObject existing = findBy(archive, "threadId", id);
    String policy = resurfacePolicy(thread.optString("threadType", ""), outcome);
    JSONObject residue = existing == null ? new JSONObject() : existing;
    residue.put("threadId", id)
        .put("resolvedTurn", turn)
        .put("terminalOutcome", outcome)
        .put("keyRefs", thread.optJSONArray("keyRefs") == null
            ? new JSONArray() : new JSONArray(thread.getJSONArray("keyRefs").toString()))
        .put("tags", new JSONArray().put(thread.optString("threadType", "")))
        .put("resurfacePolicy", policy);
    if ("DECAYING".equals(policy)) residue.put("decayWindowTurns", 40);
    if (existing == null) archive.put(residue);
  }

  private static String resurfacePolicy(String threadType, String outcome) {
    if ("ENTITY_ENCOUNTER".equals(threadType)) return "DECAYING";
    if ("FAILED".equals(outcome) && "SOCIAL_CONTACT".equals(threadType)) return "DECAYING";
    return "NEVER";
  }

  private void foldSelectionIndex(JSONObject root, int watermark) throws Exception {
    JSONArray commits = root.getJSONArray("commitLog");
    JSONObject index = root.getJSONObject("selectionIndex");
    for (int i = 0; i < commits.length(); i++) {
      JSONObject commit = commits.getJSONObject(i);
      if (commit.optInt("commitSeq", 0) <= watermark) continue;
      String key = commit.optString("sourceSituationKey", "").trim();
      if (key.isEmpty() || "NONE".equals(key)) continue;
      index.put(key, new JSONObject()
          .put("lastSelectedTurn", commit.optInt("turn", 1))
          .put("commitSeq", commit.optInt("commitSeq", 0))
          .put("category", commit.optString("sourceSituationCategory", "")));
    }
  }

  private void projectDirector(JSONObject root, JSONObject state) throws Exception {
    JSONObject director = root.getJSONObject("director");
    int currentTurn = Math.max(1, state.optInt("turn", 1));

    JSONObject metrics = recentEventMetrics(root, 6);
    int dangerCooldownUntil = dangerCooldownUntil(root);
    boolean dangerCooling = currentTurn <= dangerCooldownUntil;

    double dangerPressure = CampaignSkeleton.axisScore(root, "entity_attention");
    double resourcePressure = CampaignSkeleton.axisScore(root, "resource_dependency");
    double socialPressure = CampaignSkeleton.axisScore(root, "social_entanglement");
    double environmentalPressure = CampaignSkeleton.axisScore(root, "environmental_exposure");
    double consequencePressure = CampaignSkeleton.axisScore(root, "long_term_consequence");

    JSONObject modifiers = new JSONObject();
    double dangerModifier = saturatingModifier(dangerPressure, 0.60d, 1.30d);
    if (dangerCooling) dangerModifier = Math.min(dangerModifier, 0.65d);
    if (metrics.optInt("combat", 0) >= 2) dangerModifier = Math.min(dangerModifier, 0.75d);
    modifiers.put("DANGER", dangerModifier);
    modifiers.put("RESOURCE", saturatingModifier(resourcePressure, 0.70d, 1.40d));
    modifiers.put("SOCIAL", saturatingModifier(socialPressure, 0.72d, 1.22d));
    modifiers.put("ENVIRONMENTAL", saturatingModifier(environmentalPressure, 0.72d, 1.25d));
    modifiers.put("CONSEQUENCE", saturatingModifier(consequencePressure, 0.70d, 1.30d));

    // Cross-pressure relief: severe survival/resource pressure suppresses extra danger and boosts
    // resource opportunities instead of compounding threat indefinitely.
    if (resourcePressure >= 0.70d) {
      modifiers.put("DANGER", Math.min(modifiers.getDouble("DANGER"), 0.80d));
      modifiers.put("RESOURCE", Math.max(modifiers.getDouble("RESOURCE"), 1.25d));
    }

    director.put("tagWeightModifiers", modifiers)
        .put("authority", "WEIGHT_ONLY")
        .put("canCreateCandidates", false)
        .put("canUnlockEligibility", false)
        .put("derivedFromCommitSeq", root.optInt("commitSequence", 0))
        .put("activeCooldowns", new JSONObject().put("DANGER_UNTIL_TURN", dangerCooldownUntil))
        .put("recentEventMetrics", metrics)
        .put("pressures", new JSONObject()
            .put("danger", dangerPressure)
            .put("resource", resourcePressure)
            .put("social", socialPressure)
            .put("environmental", environmentalPressure)
            .put("consequence", consequencePressure));
  }

  private static double saturatingModifier(double score, double floor, double ceiling) {
    double normalized = clamp(score, 0.0d, 1.0d);
    double saturation = normalized / (0.35d + normalized);
    return clamp(floor + (ceiling - floor) * saturation, floor, ceiling);
  }

  private static JSONObject recentEventMetrics(JSONObject root, int commitWindow) throws Exception {
    JSONObject metrics = new JSONObject().put("combat", 0).put("danger", 0).put("resource", 0)
        .put("social", 0).put("environmental", 0);
    JSONArray commits = root.optJSONArray("commitLog");
    if (commits == null) return metrics;
    int start = Math.max(0, commits.length() - Math.max(1, commitWindow));
    for (int i = start; i < commits.length(); i++) {
      JSONArray events = commits.optJSONObject(i) == null ? null : commits.optJSONObject(i).optJSONArray("events");
      if (events == null) continue;
      for (int e = 0; e < events.length(); e++) {
        JSONObject event = events.optJSONObject(e);
        if (event == null) continue;
        String type = event.optString("eventType", "");
        if (type.startsWith("COMBAT_")) metrics.put("combat", metrics.getInt("combat") + 1);
        if (type.startsWith("ENTITY_") || type.startsWith("COMBAT_")) {
          metrics.put("danger", metrics.getInt("danger") + 1);
        } else if (type.startsWith("CHEST_") || type.startsWith("ITEM_")) {
          metrics.put("resource", metrics.getInt("resource") + 1);
        } else if (type.startsWith("CHARACTER_")) {
          metrics.put("social", metrics.getInt("social") + 1);
        } else if (type.startsWith("ROUTE_") || type.startsWith("LEVEL_")) {
          metrics.put("environmental", metrics.getInt("environmental") + 1);
        }
      }
    }
    return metrics;
  }

  private static int dangerCooldownUntil(JSONObject root) {
    JSONArray commits = root.optJSONArray("commitLog");
    if (commits == null) return 0;
    for (int i = commits.length() - 1; i >= 0; i--) {
      JSONObject commit = commits.optJSONObject(i);
      JSONArray events = commit == null ? null : commit.optJSONArray("events");
      if (events == null) continue;
      for (int e = events.length() - 1; e >= 0; e--) {
        JSONObject event = events.optJSONObject(e);
        if (event == null) continue;
        String type = event.optString("eventType", "");
        if ("COMBAT_VICTORY".equals(type) || "COMBAT_DEFEAT".equals(type)) {
          return commit.optInt("turn", 0) + 3;
        }
      }
    }
    return 0;
  }

  private static void normalizeDirector(JSONObject root) throws Exception {
    JSONObject director = root.optJSONObject("director");
    if (director == null) director = new JSONObject();
    if (!(director.opt("tagWeightModifiers") instanceof JSONObject)) {
      director.put("tagWeightModifiers", new JSONObject());
    }
    if (!(director.opt("pressures") instanceof JSONObject)) director.put("pressures", new JSONObject());
    director.put("authority", "WEIGHT_ONLY")
        .put("canCreateCandidates", false)
        .put("canUnlockEligibility", false)
        .put("derivedFromCommitSeq", Math.max(0, director.optInt("derivedFromCommitSeq", 0)));
    root.put("director", director);
  }

  private static JSONObject ensureObject(JSONObject parent, String key) throws Exception {
    JSONObject value = parent.optJSONObject(key);
    if (value == null) value = new JSONObject();
    parent.put(key, value);
    return value;
  }

  private static JSONArray ensureArray(JSONObject parent, String key) throws Exception {
    JSONArray value = parent.optJSONArray(key);
    if (value == null) value = new JSONArray();
    parent.put(key, value);
    return value;
  }

  private static JSONObject findBy(JSONArray values, String key, String expected) {
    if (values == null) return null;
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && expected.equals(value.optString(key, ""))) return value;
    }
    return null;
  }

  private static String sortedRefs(JSONArray refs) {
    List<String> values = new ArrayList<>();
    if (refs != null) {
      for (int i = 0; i < refs.length(); i++) {
        String value = refs.optString(i, "").trim();
        if (!value.isEmpty()) values.add(value);
      }
    }
    Collections.sort(values);
    return String.join(",", values);
  }

  private static int cooldownTurns(String category) {
    switch (safe(category).toUpperCase(Locale.ROOT)) {
      case "DANGER": return 3;
      case "RESOURCE": return 2;
      case "SOCIAL": return 12;
      case "CONSEQUENCE": return 5;
      case "ENVIRONMENTAL": return 2;
      default: return 0;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static String shortHash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(safe(value).getBytes(StandardCharsets.UTF_8));
      StringBuilder output = new StringBuilder();
      for (int i = 0; i < 8; i++) output.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
      return output.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static JSONObject firstMandatoryCandidate(JSONArray candidates) throws Exception {
    JSONObject selected = null;
    if (candidates == null) return null;
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.optJSONObject(i);
      if (candidate == null || !candidate.optBoolean("mandatory", false)) continue;
      if (selected == null) {
        selected = candidate;
        continue;
      }
      int dueA = candidate.optInt("dueTurn", candidate.optInt("schedulerDueTurn", Integer.MAX_VALUE));
      int dueB = selected.optInt("dueTurn", selected.optInt("schedulerDueTurn", Integer.MAX_VALUE));
      String keyA = candidate.optString("situationKey", "");
      String keyB = selected.optString("situationKey", "");
      if (dueA < dueB || (dueA == dueB && keyA.compareTo(keyB) < 0)) selected = candidate;
    }
    return selected == null ? null : new JSONObject(selected.toString());
  }

  private static final class WeightedCandidate {
    final JSONObject candidate;
    final double hazard;
    final double categoryModifier;
    final double keyRefModifier;
    final boolean cooling;
    double finalWeight;

    WeightedCandidate(JSONObject candidate, double hazard, double categoryModifier,
                      double keyRefModifier, boolean cooling) {
      this.candidate = candidate;
      this.hazard = hazard;
      this.categoryModifier = categoryModifier;
      this.keyRefModifier = keyRefModifier;
      this.cooling = cooling;
    }
  }
}
