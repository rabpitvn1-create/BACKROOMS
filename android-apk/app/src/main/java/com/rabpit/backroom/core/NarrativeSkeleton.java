package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Read-only long-horizon narrative continuity projection.
 *
 * It derives only from committed Facts, active/dormant Threads, ArchivedThreadResidue
 * and authoritative CurrentState. It never owns next-event planning, eligibility,
 * scheduling, outcomes or state mutation.
 */
final class NarrativeSkeleton {
  static final String ROOT_KEY = "narrativeSkeleton";
  static final int SCHEMA_VERSION = 3;
  private static final double MAX_KEY_REF_WEIGHT_MODIFIER = 1.35d;

  private static final String[] TOP_LEVEL_FIELDS = {
      "schemaVersion", "campaignIdentity", "longTermTensions", "anchorMysteries",
      "importantRelationships", "unresolvedWorldQuestions", "convergenceConditions",
      "endingPossibilities", "attentionHints", "derivedFromCommitSeq", "derivedFromTurn"
  };

  private static final String[] FORBIDDEN_PLANNING_KEYS = {
      "nextEvent", "next_event", "nextTurnEvent", "turnSchedule", "scheduledTurn", "dueTurn",
      "phase", "phaseLabel", "phaseGate", "chapter", "scene", "beat", "plotCursor",
      "fixedEncounterOrder", "encounterOrder", "fixedEnding", "spawnSchedule", "spawnTurn",
      "candidate", "candidateId", "eligibility", "eligibilityRuleId", "unlockEligibility"
  };

  private NarrativeSkeleton() {}

  /** Returns true when the projection must be rebuilt from committed sources. */
  static boolean normalize(JSONObject root, JSONObject state) throws Exception {
    JSONObject current = root.optJSONObject(ROOT_KEY);
    if (current == null || !contractValid(current)) {
      root.put(ROOT_KEY, empty(state));
      return true;
    }
    return false;
  }

  static void rebuild(JSONObject root, JSONObject state) throws Exception {
    JSONObject skeleton = empty(state);
    JSONArray tensions = skeleton.getJSONArray("longTermTensions");
    JSONArray mysteries = skeleton.getJSONArray("anchorMysteries");
    JSONArray relationships = skeleton.getJSONArray("importantRelationships");
    JSONArray questions = skeleton.getJSONArray("unresolvedWorldQuestions");
    JSONArray convergence = skeleton.getJSONArray("convergenceConditions");
    JSONArray endings = skeleton.getJSONArray("endingPossibilities");
    JSONArray hints = skeleton.getJSONArray("attentionHints");
    int currentTurn = Math.max(1, state == null ? 1 : state.optInt("turn", 1));

    JSONArray threads = root.optJSONArray("threads");
    if (threads != null) {
      for (int i = 0; i < threads.length(); i++) {
        JSONObject thread = threads.optJSONObject(i);
        if (thread == null || !isUnresolved(thread.optString("status", ""))
            || !narrativeThread(thread.optString("threadType", ""))) continue;
        String threadId = thread.optString("threadId", "").trim();
        String threadType = thread.optString("threadType", "").trim();
        String status = thread.optString("status", "ACTIVE");
        JSONArray refs = copyArray(thread.optJSONArray("keyRefs"));
        if (!threadId.isEmpty()) {
          tensions.put(new JSONObject()
              .put("tensionKey", "thread:" + threadId)
              .put("sourceType", "THREAD")
              .put("sourceRef", threadId)
              .put("threadType", threadType)
              .put("keyRefs", copyArray(refs))
              .put("status", status)
              .put("summary", threadSummary(threadType, refs, state, status)));
          questions.put(new JSONObject()
              .put("questionKey", "thread:" + threadId)
              .put("sourceThreadId", threadId)
              .put("threadType", threadType)
              .put("keyRefs", copyArray(refs))
              .put("question", threadQuestion(threadType, refs, state)));
        }
        if (isAnchorMysteryType(threadType) && !threadId.isEmpty()) {
          mysteries.put(new JSONObject()
              .put("mysteryKey", "thread:" + threadId)
              .put("sourceThreadId", threadId)
              .put("threadType", threadType)
              .put("keyRefs", copyArray(refs))
              .put("summary", mysterySummary(threadType, refs, state)));
        }
        if (isRelationshipType(threadType) && !threadId.isEmpty()) {
          addRelationship(relationships, refs, "thread:" + threadId,
              relationshipKind(threadType), relationshipSummary(threadType, refs, state));
        }
        addHint(hints, attentionTag(threadType), refs,
            "DORMANT".equals(status) ? 0.35d : 0.65d,
            threadId.isEmpty() ? "" : "thread:" + threadId);
      }
    }

    JSONArray archive = root.optJSONArray("threadArchive");
    if (archive != null) {
      for (int i = 0; i < archive.length(); i++) {
        JSONObject residue = archive.optJSONObject(i);
        if (residue == null || !residueRelevant(residue, currentTurn)) continue;
        String threadId = residue.optString("threadId", "").trim();
        JSONArray tags = residue.optJSONArray("tags");
        String threadType = tags == null ? "" : tags.optString(0, "");
        if (!narrativeThread(threadType)) continue;
        JSONArray refs = copyArray(residue.optJSONArray("keyRefs"));
        tensions.put(new JSONObject()
            .put("tensionKey", "residue:" + threadId)
            .put("sourceType", "ARCHIVED_THREAD_RESIDUE")
            .put("sourceRef", threadId)
            .put("threadType", threadType)
            .put("keyRefs", copyArray(refs))
            .put("resurfacePolicy", residue.optString("resurfacePolicy", "NEVER"))
            .put("summary", residueSummary(threadType, refs, residue, state)));
        addHint(hints, attentionTag(threadType), refs, 0.30d,
            threadId.isEmpty() ? "" : "residue:" + threadId);
      }
    }

    JSONArray facts = root.optJSONArray("historicalFacts");
    if (facts != null) {
      for (int i = 0; i < facts.length(); i++) {
        JSONObject fact = facts.optJSONObject(i);
        if (fact == null) continue;
        String predicate = fact.optString("predicate", "");
        if (!narrativeFact(fact)) continue;
        String factId = fact.optString("factId", "");
        JSONArray refs = refsForFact(fact);

        if (isRelationshipPredicate(predicate)) {
          addRelationship(relationships, refs, "fact:" + factId,
              "COMMITTED_CONTACT", factRelationshipSummary(predicate, refs, state));
        } else if (fact.optBoolean("impactEligible", true)
            && isLongTermTensionFact(predicate, fact.optString("impactScope", "LOCAL"))) {
          tensions.put(new JSONObject()
              .put("tensionKey", "fact:" + factId)
              .put("sourceType", "FACT")
              .put("sourceRef", factId)
              .put("predicate", predicate)
              .put("keyRefs", copyArray(refs))
              .put("impactScope", fact.optString("impactScope", "LOCAL"))
              .put("summary", factSummary(fact, state)));
        }

        if (isEndingPossibility(predicate)) {
          endings.put(new JSONObject()
              .put("possibilityKey", "fact:" + factId)
              .put("sourceFactId", factId)
              .put("predicate", predicate)
              .put("keyRefs", copyArray(refs))
              .put("summary", factSummary(fact, state))
              .put("value", copyValue(fact.opt("value")))
              .put("fixed", false));
        }

        if (fact.optBoolean("impactEligible", true)) {
          addHint(hints, attentionTag(predicate), refs,
              factAttentionStrength(fact, currentTurn),
              factId.isEmpty() ? "" : "fact:" + factId);
        }
      }
    }

    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        String actorId = member == null ? "" : member.optString("id", "").trim();
        if (!actorId.isEmpty()) {
          JSONArray refs = new JSONArray().put("cao_minh").put(actorId);
          addRelationship(relationships, refs, "state:party:" + actorId,
              "PARTY_COMPANION",
              displayRef(state, actorId) + " đang đồng hành cùng Cao Minh trong state hiện tại.");
          addHint(hints, "SOCIAL", new JSONArray().put(actorId), 0.45d,
              "state:party:" + actorId);
        }
      }
    }

    deriveConvergence(questions, convergence);
    skeleton.put("derivedFromCommitSeq", Math.max(0, root.optInt("commitSequence", 0)));
    skeleton.put("derivedFromTurn", currentTurn);
    root.put(ROOT_KEY, skeleton);
  }

  static boolean contractValid(JSONObject skeleton) {
    if (skeleton == null || skeleton.optInt("schemaVersion", -1) != SCHEMA_VERSION) return false;
    if (!(skeleton.opt("campaignIdentity") instanceof JSONObject)
        || !skeleton.has("derivedFromCommitSeq") || !skeleton.has("derivedFromTurn")) return false;
    String[] arrays = {
        "longTermTensions", "anchorMysteries", "importantRelationships",
        "unresolvedWorldQuestions", "convergenceConditions", "endingPossibilities", "attentionHints"
    };
    for (String key : arrays) if (!(skeleton.opt(key) instanceof JSONArray)) return false;

    Iterator<String> keys = skeleton.keys();
    while (keys.hasNext()) if (!isTopLevelField(keys.next())) return false;
    return !containsForbiddenPlanningKey(skeleton);
  }

  static final int MAX_PROMPT_CONTEXT_CHARS = 4200;
  static final int MAX_SCENE_CONTEXT_CHARS = 900;

  static String sceneContext(JSONObject root, JSONObject state, String action) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    if (!contractValid(skeleton)) return "";

    JSONArray relevantRefs = new JSONArray().put("cao_minh");
    addUniqueString(relevantRefs, currentLevelRef(state));

    JSONArray party = state == null ? null : state.optJSONArray("party");
    for (int i = 0; party != null && i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !member.optBoolean("present", true)) continue;
      addUniqueString(relevantRefs, member.optString("id", ""));
    }
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    for (int i = 0; pending != null && i < pending.length(); i++) {
      addUniqueString(relevantRefs, pending.optString(i, ""));
    }

    StringBuilder out = new StringBuilder();
    appendSceneRelationships(out, skeleton.optJSONArray("importantRelationships"),
        relevantRefs, state, action, 3);
    appendSceneSummaries(out, "OPEN CONTINUITY", skeleton.optJSONArray("longTermTensions"),
        relevantRefs, state, action, 2);
    appendSceneSummaries(out, "UNRESOLVED MYSTERY", skeleton.optJSONArray("anchorMysteries"),
        relevantRefs, state, action, 1);

    if (out.length() > MAX_SCENE_CONTEXT_CHARS) {
      return out.substring(0, MAX_SCENE_CONTEXT_CHARS);
    }
    return out.toString().trim();
  }

  private static void appendSceneRelationships(
      StringBuilder out, JSONArray entries, JSONArray relevantRefs, JSONObject state,
      String action, int maxItems) {
    int written = 0;
    for (int i = 0; entries != null && i < entries.length() && written < maxItems; i++) {
      JSONObject entry = entries.optJSONObject(i);
      JSONArray actors = entry == null ? null : entry.optJSONArray("actorRefs");
      if (!relationshipRelevant(actors, relevantRefs, state, action)) continue;
      String summary = safe(entry.optString("summary", ""));
      if (summary.isEmpty()) continue;
      if (out.length() == 0) out.append("RELEVANT COMMITTED CONTINUITY:\n");
      out.append("- ").append(summary).append('\n');
      written++;
    }
  }

  private static void appendSceneSummaries(
      StringBuilder out, String label, JSONArray entries, JSONArray relevantRefs, JSONObject state,
      String action, int maxItems) {
    int written = 0;
    for (int i = 0; entries != null && i < entries.length() && written < maxItems; i++) {
      JSONObject entry = entries.optJSONObject(i);
      JSONArray refs = entry == null ? null : entry.optJSONArray("keyRefs");
      if (!refsRelevant(refs, relevantRefs, state, action)) continue;
      String summary = safe(entry.optString("summary", ""));
      if (summary.isEmpty()) continue;
      if (out.length() == 0) out.append("RELEVANT COMMITTED CONTINUITY:\n");
      out.append("- ").append(label).append(": ").append(summary).append('\n');
      written++;
    }
  }

  private static boolean relationshipRelevant(
      JSONArray actors, JSONArray relevantRefs, JSONObject state, String action) {
    if (actors == null || actors.length() < 2) return false;
    for (int i = 0; i < actors.length(); i++) {
      String ref = safe(actors.optString(i, ""));
      if (ref.isEmpty()) continue;
      if (!containsString(relevantRefs, ref) && !actionMentionsRef(action, state, ref)) return false;
    }
    return true;
  }

  private static boolean refsRelevant(
      JSONArray refs, JSONArray relevantRefs, JSONObject state, String action) {
    if (refs == null || refs.length() == 0) return false;
    boolean specificDependency = false;
    for (int i = 0; i < refs.length(); i++) {
      String ref = safe(refs.optString(i, ""));
      if (ref.isEmpty() || "cao_minh".equals(ref)) continue;
      if (!containsString(relevantRefs, ref) && !actionMentionsRef(action, state, ref)) return false;
      specificDependency = true;
    }
    return specificDependency;
  }

  private static boolean actionMentionsRef(String action, JSONObject state, String ref) {
    String key = safe(ref);
    if (key.isEmpty() || key.matches("-?\\d+(?:\\.\\d+)?")) return false;
    String name = ("lucia".equals(key) || "luc_tram".equals(key) || "syvial".equals(key))
        ? CharacterEncounterCore.displayName(key) : displayRef(state, key);
    String haystack = " " + mentionKey(action) + " ";
    for (String label : new String[] {key, name}) {
      String needle = mentionKey(label);
      if (!needle.isEmpty() && haystack.contains(" " + needle + " ")) return true;
    }
    return false;
  }

  private static String mentionKey(String value) {
    String text = safe(value).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    return text.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }

  static String promptContext(JSONObject root) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    if (!contractValid(skeleton)) {
      return "LONG-HORIZON CONTINUITY MEMORY: unavailable. Do not invent prior continuity.";
    }

    StringBuilder out = new StringBuilder();
    out.append("LONG-HORIZON CONTINUITY MEMORY — READ-ONLY, DERIVED FROM COMMITTED CORE HISTORY\n");
    out.append("This is continuity guidance, not current-turn evidence and not actor knowledge by itself. ")
        .append("Never turn an unresolved question, mystery or relationship summary into a new fact, event, confession, ")
        .append("decision or state change. Current Core state and COMMITTED TURN EVIDENCE remain authoritative.\n");
    out.append("DERIVED THROUGH: commitSeq=").append(skeleton.optInt("derivedFromCommitSeq", 0))
        .append(", turn=").append(skeleton.optInt("derivedFromTurn", 0)).append('\n');

    appendPromptSummaries(out, "IMPORTANT RELATIONSHIPS",
        skeleton.optJSONArray("importantRelationships"), 6);
    appendPromptSummaries(out, "LONG-TERM TENSIONS",
        skeleton.optJSONArray("longTermTensions"), 6);
    appendPromptSummaries(out, "ANCHOR MYSTERIES — unresolved, never reveal as solved",
        skeleton.optJSONArray("anchorMysteries"), 4);
    appendPromptQuestions(out, "UNRESOLVED WORLD QUESTIONS",
        skeleton.optJSONArray("unresolvedWorldQuestions"), 5);

    if (out.length() > MAX_PROMPT_CONTEXT_CHARS) {
      return out.substring(0, MAX_PROMPT_CONTEXT_CHARS)
          + "\n[continuity memory clipped; do not infer omitted history]";
    }
    return out.toString();
  }

  private static void appendPromptSummaries(
      StringBuilder out, String title, JSONArray entries, int maxItems) {
    out.append(title).append(":\n");
    int written = 0;
    for (int i = 0; entries != null && i < entries.length() && written < maxItems; i++) {
      JSONObject entry = entries.optJSONObject(i);
      if (entry == null) continue;
      String summary = safe(entry.optString("summary", ""));
      if (summary.isEmpty()) continue;
      out.append("- ").append(summary).append('\n');
      written++;
    }
    if (written == 0) out.append("- none committed\n");
  }

  private static void appendPromptQuestions(
      StringBuilder out, String title, JSONArray entries, int maxItems) {
    out.append(title).append(":\n");
    int written = 0;
    for (int i = 0; entries != null && i < entries.length() && written < maxItems; i++) {
      JSONObject entry = entries.optJSONObject(i);
      if (entry == null) continue;
      String question = safe(entry.optString("question", ""));
      if (question.isEmpty()) continue;
      out.append("- ").append(question).append('\n');
      written++;
    }
    if (written == 0) out.append("- none committed\n");
  }

  static double attentionStrength(JSONObject root, String tag) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    JSONArray hints = skeleton == null ? null : skeleton.optJSONArray("attentionHints");
    String expected = safe(tag).toUpperCase(Locale.ROOT);
    double best = 0.0d;
    if (hints == null || expected.isEmpty()) return best;
    for (int i = 0; i < hints.length(); i++) {
      JSONObject hint = hints.optJSONObject(i);
      if (hint != null && expected.equals(hint.optString("tag", "").toUpperCase(Locale.ROOT))) {
        best = Math.max(best, clamp(hint.optDouble("strength", 0.0d)));
      }
    }
    return best;
  }

  static double keyRefWeightModifier(JSONObject root, JSONObject candidate) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    JSONArray hints = skeleton == null ? null : skeleton.optJSONArray("attentionHints");
    JSONArray candidateRefs = candidate == null ? null : candidate.optJSONArray("keyRefs");
    String category = candidate == null ? "" :
        safe(candidate.optString("category", "")).toUpperCase(Locale.ROOT);
    if (hints == null || candidateRefs == null || candidateRefs.length() == 0 || category.isEmpty()) {
      return 1.0d;
    }

    double best = 0.0d;
    for (int i = 0; i < hints.length(); i++) {
      JSONObject hint = hints.optJSONObject(i);
      if (hint == null || !hint.optBoolean("advisoryOnly", false)) continue;
      String tag = safe(hint.optString("tag", "")).toUpperCase(Locale.ROOT);
      if (!category.equals(tag) && !"CONSEQUENCE".equals(tag)) continue;
      JSONArray hintRefs = hint.optJSONArray("keyRefs");
      if (!overlaps(candidateRefs, hintRefs)) continue;
      double strength = clamp(hint.optDouble("strength", 0.0d));
      if ("CONSEQUENCE".equals(tag)) strength *= 0.5d;
      best = Math.max(best, strength);
    }
    return Math.min(MAX_KEY_REF_WEIGHT_MODIFIER, 1.0d + (0.35d * best));
  }

  private static JSONObject empty(JSONObject state) throws Exception {
    JSONObject player = state == null ? null : state.optJSONObject("player");
    String playerName = player == null ? "Cao Minh" : player.optString("name", "Cao Minh");
    String level = currentLevelRef(state);
    String location = state == null ? "" : state.optString("location", "").trim();

    JSONObject identity = new JSONObject()
        .put("campaignKey", "BACKROOMS_V2")
        .put("playerRef", "cao_minh")
        .put("playerName", playerName)
        .put("premise", "Cao Minh đang khám phá Backrooms; continuity dài hạn chỉ phản ánh những gì Core đã commit.");
    if (!level.isEmpty()) identity.put("currentLevelKey", level);
    if (!location.isEmpty()) identity.put("currentLocation", location);

    return new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("campaignIdentity", identity)
        .put("longTermTensions", new JSONArray())
        .put("anchorMysteries", new JSONArray())
        .put("importantRelationships", new JSONArray())
        .put("unresolvedWorldQuestions", new JSONArray())
        .put("convergenceConditions", new JSONArray())
        .put("endingPossibilities", new JSONArray())
        .put("attentionHints", new JSONArray())
        .put("derivedFromCommitSeq", 0)
        .put("derivedFromTurn", Math.max(1, state == null ? 1 : state.optInt("turn", 1)));
  }

  private static boolean isTopLevelField(String key) {
    for (String allowed : TOP_LEVEL_FIELDS) if (allowed.equals(key)) return true;
    return false;
  }

  private static boolean containsForbiddenPlanningKey(Object value) {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (isForbiddenKey(key) || containsForbiddenPlanningKey(object.opt(key))) return true;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        if (containsForbiddenPlanningKey(array.opt(i))) return true;
      }
    }
    return false;
  }

  private static boolean isForbiddenKey(String key) {
    for (String forbidden : FORBIDDEN_PLANNING_KEYS) {
      if (forbidden.equalsIgnoreCase(safe(key))) return true;
    }
    return false;
  }

  private static boolean isUnresolved(String status) {
    return "ACTIVE".equals(status) || "DORMANT".equals(status);
  }

  private static boolean narrativeThread(String type) {
    String upper = safe(type).toUpperCase(Locale.ROOT);
    return isRelationshipType(upper) || isAnchorMysteryType(upper)
        || upper.contains("WORLD_CONSEQUENCE");
  }

  private static boolean narrativeFact(JSONObject fact) {
    if (!fact.optBoolean("impactEligible", true)) return false;
    String predicate = safe(fact.optString("predicate", "")).toLowerCase(Locale.ROOT);
    String scope = safe(fact.optString("impactScope", "LOCAL")).toUpperCase(Locale.ROOT);
    if (isRelationshipPredicate(predicate) || isEndingPossibility(predicate)) return true;
    if ("LOCAL".equals(scope) || predicate.startsWith("route_")
        || predicate.startsWith("level_") || "entered_level".equals(predicate)
        || predicate.startsWith("chest_") || predicate.startsWith("item_")
        || predicate.startsWith("resource_") || predicate.startsWith("survival_")
        || predicate.startsWith("combat_") || predicate.startsWith("entity_")) return false;
    return ("PERSISTENT_WORLD".equals(scope) || "REGIONAL".equals(scope))
        && (predicate.startsWith("world_") || predicate.contains("mystery")
            || predicate.contains("consequence"));
  }

  private static boolean isAnchorMysteryType(String type) {
    String upper = safe(type).toUpperCase(Locale.ROOT);
    return upper.contains("MYSTERY")
        || upper.contains("UNKNOWN") || upper.contains("SECRET")
        || upper.contains("ORIGIN") || upper.contains("IDENTITY");
  }

  private static boolean isRelationshipType(String type) {
    String upper = safe(type).toUpperCase(Locale.ROOT);
    return "SOCIAL_CONTACT".equals(upper) || "LUC_TRAM_RELATIONSHIP".equals(upper)
        || upper.contains("RELATIONSHIP");
  }

  private static boolean isRelationshipPredicate(String predicate) {
    String lower = safe(predicate).toLowerCase(Locale.ROOT);
    return "character_reunion".equals(lower) || "character_encountered".equals(lower);
  }

  private static boolean isLongTermTensionFact(String predicate, String scope) {
    if ("LOCAL".equals(safe(scope).toUpperCase(Locale.ROOT))) return false;
    String lower = safe(predicate).toLowerCase(Locale.ROOT);
    return !isRelationshipPredicate(lower) && !"entered_level".equals(lower);
  }

  private static boolean isEndingPossibility(String predicate) {
    String lower = safe(predicate).toLowerCase(Locale.ROOT);
    return lower.startsWith("ending_possibility") || lower.startsWith("campaign_ending_possibility");
  }

  private static boolean residueRelevant(JSONObject residue, int currentTurn) {
    String policy = residue.optString("resurfacePolicy", "NEVER");
    if ("PERSISTENT".equals(policy) || "CONDITIONAL".equals(policy)) return true;
    return "DECAYING".equals(policy)
        && currentTurn <= residue.optInt("resolvedTurn", 0)
            + Math.max(0, residue.optInt("decayWindowTurns", 0));
  }

  private static String attentionTag(String source) {
    String upper = safe(source).toUpperCase(Locale.ROOT);
    if (upper.contains("ENTITY") || upper.contains("COMBAT")) return "DANGER";
    if (upper.contains("CHEST") || upper.contains("ITEM") || upper.contains("RESOURCE")
        || upper.contains("SURVIVAL")) return "RESOURCE";
    if (upper.contains("SOCIAL") || upper.contains("CHARACTER") || upper.contains("PARTY")
        || upper.contains("RELATIONSHIP")) return "SOCIAL";
    if (upper.contains("LEVEL") || upper.contains("ROUTE") || upper.contains("ENVIRONMENT")
        || upper.contains("LOCATION") || upper.contains("ENTERED_LEVEL")) return "ENVIRONMENTAL";
    return upper.isEmpty() ? "" : "CONSEQUENCE";
  }

  private static double factAttentionStrength(JSONObject fact, int currentTurn) {
    double base = impactStrength(fact.optString("impactScope", "LOCAL"));
    int age = Math.max(0, currentTurn - Math.max(0, fact.optInt("turn", currentTurn)));
    double factor = Math.pow(0.5d, age / 48.0d);
    return clamp(base * factor);
  }

  private static double impactStrength(String scope) {
    switch (safe(scope).toUpperCase(Locale.ROOT)) {
      case "PERSISTENT_WORLD": return 0.60d;
      case "REGIONAL": return 0.50d;
      case "SOCIAL": return 0.45d;
      default: return 0.20d;
    }
  }

  private static String threadSummary(String type, JSONArray refs, JSONObject state, String status) {
    String subject = firstRefLabel(state, refs);
    switch (safe(type).toUpperCase(Locale.ROOT)) {
      case "ENTITY_ENCOUNTER":
        return subject + " vẫn là một mối đe dọa chưa khép lại đối với Cao Minh.";
      case "CHEST_AVAILABLE":
        return "Một chiếc rương tại " + subject + " vẫn là nguồn tài nguyên chưa được xử lý.";
      case "LEVEL_ROUTE_SEARCH":
        return "Việc tìm lối ra khỏi " + subject + " vẫn chưa được giải quyết.";
      case "LUC_TRAM_RELATIONSHIP":
        return "Cuộc tái ngộ giữa Cao Minh và Lục Trầm đã trở thành một quan hệ dài hạn đang mở.";
      case "SOCIAL_CONTACT":
        return "Mối liên hệ giữa Cao Minh và " + subject + " vẫn đang mở.";
      default:
        return readableType(type) + " liên quan đến " + subject + " vẫn chưa được giải quyết.";
    }
  }

  private static String threadQuestion(String type, JSONArray refs, JSONObject state) {
    String subject = firstRefLabel(state, refs);
    switch (safe(type).toUpperCase(Locale.ROOT)) {
      case "ENTITY_ENCOUNTER":
        return "Mối đe dọa từ " + subject + " đã thực sự kết thúc chưa?";
      case "CHEST_AVAILABLE":
        return "Nguồn tài nguyên tại " + subject + " đã được xử lý chưa?";
      case "LEVEL_ROUTE_SEARCH":
        return "Lối ra khỏi " + subject + " đã được xác định chưa?";
      case "LUC_TRAM_RELATIONSHIP":
        return "Quan hệ giữa Cao Minh và Lục Trầm hiện đang ở trạng thái nào?";
      case "SOCIAL_CONTACT":
        return "Mối liên hệ giữa Cao Minh và " + subject + " hiện đã ổn định ở trạng thái nào?";
      default:
        return readableType(type) + " liên quan đến " + subject + " còn điều gì chưa được giải quyết?";
    }
  }

  private static String mysterySummary(String type, JSONArray refs, JSONObject state) {
    if ("LEVEL_ROUTE_SEARCH".equals(safe(type).toUpperCase(Locale.ROOT))) {
      return "Cấu trúc lối ra của " + firstRefLabel(state, refs) + " vẫn chưa được xác nhận.";
    }
    return threadQuestion(type, refs, state);
  }

  private static String relationshipKind(String type) {
    return "LUC_TRAM_RELATIONSHIP".equals(safe(type).toUpperCase(Locale.ROOT))
        ? "REUNION_CONTINUITY" : "SOCIAL_CONTACT";
  }

  private static String relationshipSummary(String type, JSONArray refs, JSONObject state) {
    if ("LUC_TRAM_RELATIONSHIP".equals(safe(type).toUpperCase(Locale.ROOT))) {
      return "Cao Minh và Lục Trầm đã tái ngộ; mối quan hệ giữa họ tiếp tục phát triển từ state đã commit.";
    }
    return "Cao Minh đã gặp " + firstRefLabel(state, refs)
        + "; mối liên hệ này đang tồn tại trong continuity đã commit.";
  }

  private static String factRelationshipSummary(String predicate, JSONArray refs, JSONObject state) {
    if ("character_reunion".equals(safe(predicate).toLowerCase(Locale.ROOT))) {
      return "Cao Minh đã tái ngộ " + firstRefLabel(state, refs) + " theo một sự kiện đã commit.";
    }
    return "Cao Minh đã gặp " + firstRefLabel(state, refs) + " theo một sự kiện đã commit.";
  }

  private static String residueSummary(
      String threadType, JSONArray refs, JSONObject residue, JSONObject state) {
    String subject = firstRefLabel(state, refs);
    String outcome = residue.optString("terminalOutcome", "RESOLVED");
    if ("ENTITY_ENCOUNTER".equals(safe(threadType).toUpperCase(Locale.ROOT))) {
      return "Cuộc đối đầu với " + subject + " đã khép ở trạng thái " + outcome
          + ", nhưng residue của sự kiện vẫn còn hiệu lực.";
    }
    return readableType(threadType) + " liên quan đến " + subject + " đã khép ở trạng thái "
        + outcome + ", nhưng hậu quả dài hạn vẫn còn được giữ.";
  }

  private static String factSummary(JSONObject fact, JSONObject state) {
    JSONArray refs = refsForFact(fact);
    String subject = firstRefLabel(state, refs);
    String predicate = readableType(fact.optString("predicate", "fact"));
    Object value = fact.opt("value");
    String rendered = value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value);
    return predicate + " liên quan đến " + subject
        + (rendered.isEmpty() ? "." : ": " + rendered + ".");
  }

  private static void addRelationship(
      JSONArray relationships, JSONArray refs, String sourceRef, String kind, String summary)
      throws Exception {
    if (refs == null || refs.length() == 0) return;
    JSONArray actors = copyArray(refs);
    if (!containsString(actors, "cao_minh")) actors.put("cao_minh");
    if (actors.length() < 2) return;
    String key = "relationship:" + sortedRefs(actors);
    JSONObject existing = findObject(relationships, "relationshipKey", key);
    if (existing != null) return;
    relationships.put(new JSONObject()
        .put("relationshipKey", key)
        .put("actorRefs", actors)
        .put("keyRefs", copyArray(refs))
        .put("relationshipKind", kind)
        .put("status", "IMPORTANT")
        .put("summary", summary)
        .put("sourceRef", sourceRef));
  }

  private static void addHint(
      JSONArray hints, String tag, JSONArray keyRefs, double strength, String sourceRef)
      throws Exception {
    String normalized = safe(tag).toUpperCase(Locale.ROOT);
    JSONArray refs = uniqueRefs(keyRefs);
    if (normalized.isEmpty() || strength <= 0.0d) return;
    String hintKey = normalized + "|" + sortedRefs(refs);
    JSONObject target = findObject(hints, "hintKey", hintKey);
    if (target == null) {
      target = new JSONObject()
          .put("hintKey", hintKey)
          .put("tag", normalized)
          .put("keyRefs", refs)
          .put("strength", clamp(strength))
          .put("advisoryOnly", true)
          .put("sourceRefs", new JSONArray());
      hints.put(target);
    } else {
      target.put("strength", Math.max(target.optDouble("strength", 0.0d), clamp(strength)));
    }
    if (!safe(sourceRef).isEmpty()) {
      JSONArray sources = target.getJSONArray("sourceRefs");
      addUniqueString(sources, sourceRef);
      while (sources.length() > 8) sources.remove(0);
    }
  }

  private static void deriveConvergence(JSONArray questions, JSONArray output) throws Exception {
    for (int i = 0; i < questions.length(); i++) {
      JSONObject left = questions.optJSONObject(i);
      JSONArray leftRefs = left == null ? null : left.optJSONArray("keyRefs");
      if (leftRefs == null) continue;
      for (int r = 0; r < leftRefs.length(); r++) {
        String ref = leftRefs.optString(r, "").trim();
        if (ref.isEmpty()) continue;
        JSONArray threadIds = new JSONArray();
        for (int j = 0; j < questions.length(); j++) {
          JSONObject question = questions.optJSONObject(j);
          JSONArray refs = question == null ? null : question.optJSONArray("keyRefs");
          if (refs != null && containsString(refs, ref)) {
            addUniqueString(threadIds, question.optString("sourceThreadId", ""));
          }
        }
        if (threadIds.length() < 2) continue;
        String conditionKey = "shared_unresolved_ref:" + ref;
        if (findObject(output, "conditionKey", conditionKey) != null) continue;
        output.put(new JSONObject()
            .put("conditionKey", conditionKey)
            .put("conditionType", "SHARED_UNRESOLVED_KEY_REF")
            .put("keyRef", ref)
            .put("sourceThreadIds", threadIds)
            .put("summary", "Nhiều unresolved thread cùng hội tụ vào " + displayRef(null, ref) + ".")
            .put("met", true)
            .put("advisoryOnly", true));
      }
    }
  }

  private static JSONArray refsForFact(JSONObject fact) {
    JSONArray refs = new JSONArray();
    String subject = fact == null ? "" : fact.optString("subjectRef", "").trim();
    if (!subject.isEmpty()) refs.put(subject);
    return refs;
  }

  private static String firstRefLabel(JSONObject state, JSONArray refs) {
    String ref = refs == null ? "" : refs.optString(0, "").trim();
    return ref.isEmpty() ? "một yếu tố chưa định danh" : displayRef(state, ref);
  }

  private static String displayRef(JSONObject state, String ref) {
    String key = safe(ref);
    if (key.isEmpty()) return "một yếu tố chưa định danh";
    if ("cao_minh".equals(key)) return "Cao Minh";
    if ("luc_tram".equals(key)) return "Lục Trầm";
    if ("syvial".equals(key)) return "Syvial";

    JSONObject player = state == null ? null : state.optJSONObject("player");
    if (player != null && key.equals(player.optString("id", "cao_minh"))) {
      return player.optString("name", "Cao Minh");
    }
    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member != null && key.equals(member.optString("id", ""))) {
          return member.optString("name", humanize(key));
        }
      }
    }
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    JSONObject entity = combat == null ? null : combat.optJSONObject("entity");
    if (entity != null && key.equals(entity.optString("key", ""))) {
      return entity.optString("name", humanize(key));
    }
    if (key.matches("-?\\d+(?:\\.\\d+)?")) return "Level " + key;
    return humanize(key);
  }

  private static String currentLevelRef(JSONObject state) {
    if (state == null) return "";
    String key = state.optString("currentLevelKey", "").trim();
    if (!key.isEmpty()) return key;
    return state.has("currentLevel") ? String.valueOf(state.optInt("currentLevel", 0)) : "";
  }

  private static String readableType(String value) {
    return humanize(safe(value).toLowerCase(Locale.ROOT));
  }

  private static String humanize(String value) {
    String text = safe(value).replace('_', ' ').replace('-', ' ').trim();
    if (text.isEmpty()) return "một yếu tố chưa định danh";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        out.append(c);
        upper = true;
      } else {
        out.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return out.toString();
  }

  private static boolean overlaps(JSONArray left, JSONArray right) {
    if (left == null || right == null) return false;
    for (int i = 0; i < left.length(); i++) {
      String value = left.optString(i, "").trim();
      if (!value.isEmpty() && containsString(right, value)) return true;
    }
    return false;
  }

  private static JSONArray uniqueRefs(JSONArray source) {
    JSONArray output = new JSONArray();
    if (source == null) return output;
    for (int i = 0; i < source.length(); i++) addUniqueString(output, source.optString(i, ""));
    return output;
  }

  private static String sortedRefs(JSONArray refs) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    if (refs != null) {
      for (int i = 0; i < refs.length(); i++) {
        String value = refs.optString(i, "").trim();
        if (!value.isEmpty() && !values.contains(value)) values.add(value);
      }
    }
    java.util.Collections.sort(values);
    return String.join("|", values);
  }

  private static JSONObject findObject(JSONArray values, String key, String expected) {
    if (values == null) return null;
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && expected.equals(value.optString(key, ""))) return value;
    }
    return null;
  }

  private static boolean containsString(JSONArray values, String expected) {
    if (values == null) return false;
    for (int i = 0; i < values.length(); i++) {
      if (expected.equals(values.optString(i, ""))) return true;
    }
    return false;
  }

  private static void addUniqueString(JSONArray values, String value) {
    String normalized = safe(value);
    if (!normalized.isEmpty() && !containsString(values, normalized)) values.put(normalized);
  }

  private static JSONArray copyArray(JSONArray source) throws Exception {
    return source == null ? new JSONArray() : new JSONArray(source.toString());
  }

  private static Object copyValue(Object value) throws Exception {
    if (value instanceof JSONObject) return new JSONObject(value.toString());
    if (value instanceof JSONArray) return new JSONArray(value.toString());
    return value == null ? JSONObject.NULL : value;
  }

  private static double clamp(double value) {
    return Math.max(0.0d, Math.min(1.0d, value));
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
