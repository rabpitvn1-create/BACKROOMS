package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Deterministic guard for the non-authoritative narration payload. */
public final class NarrationGuard {
  private static final Set<String> FORBIDDEN_ROOT_KEYS = new HashSet<>(Arrays.asList(
      "transitionTarget", "sceneLabel", "state", "stateDelta", "currentLevel",
      "currentLevelKey", "location", "flags", "inventory", "party", "combat",
      "facts", "historicalFacts", "beliefs", "threads", "threadRegistry", "narrativeSkeleton", "emergent"));

  private NarrationGuard() {}

  /** Returns an empty string when valid, otherwise a deterministic rejection reason. */
  public static String validate(JSONObject generated, JSONObject committedState) {
    if (generated == null) return "Narration payload is missing.";
    String reply = generated.optString("reply", "").trim();
    if (reply.isEmpty()) return "reply is required.";

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

    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
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
