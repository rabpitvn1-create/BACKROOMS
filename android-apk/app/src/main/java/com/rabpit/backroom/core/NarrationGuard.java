package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic guard for the non-authoritative narration payload. */
public final class NarrationGuard {
  private static final Set<String> FORBIDDEN_ROOT_KEYS = new HashSet<>(Arrays.asList(
      "transitionTarget", "sceneLabel", "state", "stateDelta", "currentLevel",
      "currentLevelKey", "location", "flags", "inventory", "party", "combat",
      "characterKnowledge", "facts", "historicalFacts", "beliefs", "threads", "threadRegistry", "narrativeSkeleton", "emergent"));
  private static final Set<String> CLAIM_KEYS = new HashSet<>(Arrays.asList(
      "eventId", "kind", "subject"));
  private static final String[] ACQUISITION_PHRASES = {
      "nhặt được", "nhặt lấy", "nhặt ", "nhận được", "thu được", "lấy được",
      "có được", "có thêm", "sở hữu thêm"
  };

  private NarrationGuard() {}

  /** Legacy structural validation used outside the committed-turn narration path. */
  public static String validate(JSONObject generated, JSONObject committedState) {
    return validateBase(generated, committedState);
  }

  /** Strict Phase-5 validation against the immutable evidence of the committed turn. */
  public static String validate(
      JSONObject generated, JSONObject committedState, JSONObject committedTurnEvidence) {
    String base = validateBase(generated, committedState);
    if (!base.isEmpty()) return base;
    if (committedTurnEvidence == null
        || !committedTurnEvidence.optBoolean("available", false)) {
      return "Committed turn evidence is unavailable.";
    }

    if (!CommittedTurnNarrationEvidence.hasClaim(committedTurnEvidence, "COMBAT_RESULT", "")) {
      String text = generated.optString("reply", "").toLowerCase(Locale.ROOT);
      if (text.contains("bị tiêu diệt") || text.contains("đã tiêu diệt") || text.contains("trận chiến kết thúc")) {
        return "Combat closure lacks current-turn evidence.";
      }
    }

    JSONArray declared = generated.optJSONArray("claims");
    if (declared != null) {
      if (declared.length() > 16) return "claims[] exceeds the maximum of 16.";
      JSONArray allowed = committedTurnEvidence.optJSONArray("claims");
      if (allowed == null) allowed = new JSONArray();
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < declared.length(); i++) {
        JSONObject claim = declared.optJSONObject(i);
        if (claim == null) return "Each narration claim must be an object.";
        if (!hasOnlyKeys(claim, CLAIM_KEYS)) return "Narration claim contains an unknown field.";
        String eventId = claim.optString("eventId", "").trim();
        String kind = claim.optString("kind", "").trim();
        String subject = claim.optString("subject", "").trim();
        if (eventId.isEmpty() || kind.isEmpty() || subject.isEmpty()) {
          return "Narration claim requires eventId, kind and subject.";
        }
        String key = eventId + "|" + kind + "|" + subject.toLowerCase(Locale.ROOT);
        if (!seen.add(key)) return "Narration claim is duplicated: " + eventId;
        if (!matchesAllowedClaim(allowed, eventId, kind, subject)) {
          return "Narration claim lacks committed evidence: " + kind + ":" + subject;
        }
      }
    }

    String acquisitionViolation = acquisitionViolation(
        generated, committedState, committedTurnEvidence);
    if (!acquisitionViolation.isEmpty()) return acquisitionViolation;
    return "";
  }

  private static String validateBase(JSONObject generated, JSONObject committedState) {
    if (generated == null) return "Narration payload is missing.";
    String reply = generated.optString("reply", "").trim();
    if (reply.isEmpty()) return "reply is required.";
    if (SafePresentationView.leaks(committedState, reply)) return "Managed knowledge term in reply.";

    for (String key : FORBIDDEN_ROOT_KEYS) {
      if (generated.has(key)) return "Narration attempted authoritative field: " + key;
    }

    JSONArray choices = generated.optJSONArray("choices");
    if (choices != null) {
      if (choices.length() > 3) return "choices must contain at most 3 suggestions.";
      for (int i = 0; i < choices.length(); i++) {
        JSONObject choice = choices.optJSONObject(i);
        if (choice == null || choice.optString("text", "").trim().isEmpty()) {
          return "Each choice must contain non-empty text.";
        }
      }
    }

    if (choices != null) for (int i = 0; i < choices.length(); i++) {
      if (SafePresentationView.leaks(committedState, choices.optJSONObject(i).optString("text", ""))) {
        return "Managed knowledge term in choice.";
      }
    }
    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
    if (dialogue != null) for (int i = 0; i < dialogue.length(); i++) {
      if (SafePresentationView.leaks(committedState, dialogue.optString(i, ""))) {
        return "Managed knowledge term in dialogue.";
      }
    }
    int dialogueCount = dialogue == null ? 0 : dialogue.length();
    int pendingCount = pendingEncounterCount(committedState);
    if (pendingCount == 0 && dialogueCount != 0) {
      return "encounterDialogue is forbidden without a committed pending encounter.";
    }
    if (pendingCount > 0 && (dialogueCount < 2 || dialogueCount > 5)) {
      return "Committed character encounter requires 2-5 dialogue lines.";
    }
    if (dialogue != null) {
      for (int i = 0; i < dialogue.length(); i++) {
        if (dialogue.optString(i, "").trim().isEmpty()) {
          return "encounterDialogue cannot contain empty lines.";
        }
      }
    }

    if (hasActiveEntityEncounter(committedState) && choices != null && choices.length() > 0) {
      return "choices are forbidden while a committed Entity encounter is active.";
    }
    return "";
  }

  private static String acquisitionViolation(
      JSONObject generated, JSONObject state, JSONObject evidence) {
    StringBuilder prose = new StringBuilder(generated.optString("reply", ""));
    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
    if (dialogue != null) {
      for (int i = 0; i < dialogue.length(); i++) {
        prose.append("\n").append(dialogue.optString(i, ""));
      }
    }
    String text = prose.toString();
    Set<String> items = new HashSet<>();
    for (Map.Entry<String, String> entry : ItemCore.semanticCatalog().entrySet()) {
      if ("item".equals(entry.getValue())) items.add(entry.getKey());
    }
    JSONArray inventory = state == null ? null : state.optJSONArray("inventory");
    if (inventory != null) {
      for (int i = 0; i < inventory.length(); i++) {
        JSONObject item = inventory.optJSONObject(i);
        if (item != null && !item.optString("name", "").trim().isEmpty()) {
          items.add(item.optString("name", "").trim());
        }
      }
    }

    for (String item : items) {
      if (!statesPositiveAcquisition(text, item)) continue;
      if (!CommittedTurnNarrationEvidence.hasClaim(evidence, "ITEM_ACQUIRED", item)) {
        return "Narration claims item acquisition without committed evidence: " + item;
      }
    }
    return "";
  }

  private static boolean statesPositiveAcquisition(String prose, String item) {
    if (prose == null || item == null || item.trim().isEmpty()) return false;
    String lower = prose.toLowerCase(Locale.ROOT);
    String needle = item.toLowerCase(Locale.ROOT);
    int itemAt = lower.indexOf(needle);
    while (itemAt >= 0) {
      int start = Math.max(0, itemAt - 120);
      String before = lower.substring(start, itemAt);
      for (String phrase : ACQUISITION_PHRASES) {
        int verbAt = before.lastIndexOf(phrase);
        if (verbAt < 0) continue;
        String prefix = before.substring(Math.max(0, verbAt - 12), verbAt);
        if (!prefix.matches("(?s).*(?:không|chưa|chẳng)\\s*$")) return true;
      }
      int end = Math.min(lower.length(), itemAt + needle.length() + 100);
      String after = lower.substring(itemAt + needle.length(), end);
      if (after.contains("được thêm vào inventory") || after.contains("được đưa vào inventory")) {
        return true;
      }
      itemAt = lower.indexOf(needle, itemAt + needle.length());
    }
    return false;
  }

  private static boolean matchesAllowedClaim(
      JSONArray allowed, String eventId, String kind, String subject) {
    for (int i = 0; i < allowed.length(); i++) {
      JSONObject candidate = allowed.optJSONObject(i);
      if (candidate == null) continue;
      if (eventId.equals(candidate.optString("eventId", ""))
          && kind.equals(candidate.optString("kind", ""))
          && subject.equalsIgnoreCase(candidate.optString("subject", ""))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasOnlyKeys(JSONObject object, Set<String> allowed) {
    java.util.Iterator<String> keys = object.keys();
    while (keys.hasNext()) if (!allowed.contains(keys.next())) return false;
    return true;
  }

  private static int pendingEncounterCount(JSONObject state) {
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    return pending == null ? 0 : pending.length();
  }

  private static boolean hasActiveEntityEncounter(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags != null && !flags.optString("entityEncounterKey", "").trim().isEmpty();
  }
}
