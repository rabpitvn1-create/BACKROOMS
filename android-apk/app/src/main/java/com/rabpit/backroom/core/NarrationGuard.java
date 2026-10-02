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
  private static final String[] ACQUISITION_PHRASES = {
      "nhặt được", "nhặt lấy", "nhặt ", "nhận được", "thu được", "lấy được",
      "có được", "có thêm", "sở hữu thêm"
  };

  private NarrationGuard() {}

  /** Legacy structural validation used outside the committed-turn narration path. */
  public static String validate(JSONObject generated, JSONObject committedState) {
    return validateBase(generated, committedState);
  }

  /** Runtime validation rejects authority contradictions, never prose style or claim bookkeeping. */
  public static String validate(
      JSONObject generated, JSONObject committedState, JSONObject evidence) {
    if (generated == null || generated.optString("reply", "").trim().isEmpty()) return "FORMAT: reply is required.";
    if (evidence == null || !evidence.optBoolean("available", false)) return "FORMAT: committed evidence is unavailable.";
    for (String key : FORBIDDEN_ROOT_KEYS) {
      if (generated.has(key)) return hard("Narration attempted authoritative field: " + key);
    }
    String prose = generated.optString("reply", "");
    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
    if (dialogue != null) for (int i = 0; i < dialogue.length(); i++) prose += "\n" + dialogue.optString(i, "");
    if (SafePresentationView.leaks(committedState, prose)) return hard("Locked canon reveal in prose.");
    JSONArray choices = generated.optJSONArray("choices");
    if (choices != null) for (int i = 0; i < choices.length(); i++) {
      JSONObject choice = choices.optJSONObject(i);
      if (choice != null && SafePresentationView.leaks(committedState, choice.optString("text", ""))) {
        return hard("Locked canon reveal in choice.");
      }
    }
    if (dialogue != null && dialogue.length() > 0 && pendingEncounterCount(committedState) == 0
        && SceneContextCompiler.sceneCharacterRefs(committedState, "").size() <= 1) {
      return hard("Dialogue invents a speaker outside the committed scene.");
    }
    String acquisition = acquisitionViolation(generated, committedState, evidence);
    if (!acquisition.isEmpty()) return hard(acquisition);
    String levelKey = committedState == null ? "0" : committedState.optString("currentLevelKey",
        String.valueOf(committedState.optInt("currentLevel", 0)));
    for (String sentence : prose.split("(?<=[.!?;])\\s+|\\n+")) {
      String lower = sentence.toLowerCase(Locale.ROOT);
      if (lower.contains("nhớ lại") || lower.contains("hồi tưởng") || lower.contains("trước kia")) continue;
      if (positive(lower, "(?:trận chiến kết thúc|chiến thắng trận|(?:entity|đối thủ|kẻ địch|quái vật|sinh vật).{0,20}(?:đã chết|bị giết|bị tiêu diệt)|(?:đã tiêu diệt|đã giết).{0,20}(?:entity|đối thủ|kẻ địch|quái vật|sinh vật))")) {
        JSONObject combat = committedState == null ? null : committedState.optJSONObject("combat");
        boolean existingTerminal = combat != null && !combat.optBoolean("active", false)
            && ("victory".equals(combat.optString("outcome", "")) || "defeat".equals(combat.optString("outcome", "")));
        if ((combat != null && combat.optBoolean("active", false))
            || (!terminalCombat(evidence) && !existingTerminal)) return hard("Combat closure contradicts Core outcome.");
      }
      if (positive(lower, "(?:mất|giảm|tăng|nhận thêm)\\s+\\d+\\s*(?:hp|str|def|skl|vit|stat|core)\\b")
          && !CommittedTurnNarrationEvidence.hasClaim(evidence, "STAT_UPGRADED", "")
          && !CommittedTurnNarrationEvidence.hasClaim(evidence, "CORE_ACQUIRED", "")) {
        return hard("Numeric stat mutation lacks committed evidence.");
      }
      if (positive(lower, "(?:gãy tay|gãy chân|bị thương mới|vừa bị thương)") && !terminalCombat(evidence)) {
        return hard("New injury lacks committed evidence.");
      }
      String target = LevelCore.rawLevelKeyFromLocation(sentence);
      if (!target.isEmpty() && !target.equals(levelKey)
          && positive(lower, "(?:bước vào|đi vào|tiến vào|chuyển sang|đã tới|đã đến|sang|vào)")) {
        return hard("Level transition contradicts the committed current node.");
      }
      for (String id : new String[] {"lucia", "luc_tram", "syvial"}) {
        if (!SceneContextCompiler.mentions(sentence, id, CharacterEncounterCore.displayName(id))) continue;
        if (!SceneContextCompiler.sceneCharacterRefs(committedState, "").contains(id)
            && positive(lower, "(?:xuất hiện|bước ra|đứng bên cạnh|đang đồng hành|gia nhập nhóm)")) {
          return hard("Character presence contradicts Core: " + id);
        }
        if (positive(lower, "(?:gia nhập nhóm|trở thành thành viên)")
            && !CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_ENCOUNTERED", id)
            && !CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_REUNION", id)) {
          return hard("Party addition lacks committed evidence.");
        }
      }
      if (positive(lower, "(?:rời khỏi nhóm|rời party|bị xóa khỏi nhóm|trở thành người yêu|chính thức yêu nhau)")) {
        return hard("Party or persistent relationship mutation lacks committed evidence.");
      }
    }
    return "";
  }

  private static boolean terminalCombat(JSONObject evidence) {
    JSONArray claims = evidence.optJSONArray("claims");
    for (int i = 0; claims != null && i < claims.length(); i++) {
      JSONObject claim = claims.optJSONObject(i);
      if (claim == null || !"COMBAT_RESULT".equals(claim.optString("kind", ""))) continue;
      String outcome = claim.optString("value", "");
      if ("victory".equals(outcome) || "defeat".equals(outcome)) return true;
    }
    return false;
  }

  private static boolean positive(String text, String pattern) {
    java.util.regex.Matcher match = java.util.regex.Pattern.compile(pattern).matcher(text);
    while (match.find()) {
      String prefix = text.substring(Math.max(0, match.start() - 24), match.start());
      if (!prefix.matches("(?s).*(?:không|chưa|chẳng|nếu|có thể|giả sử)\\s*$")) return true;
    }
    return false;
  }

  private static String hard(String reason) { return "AUTHORITY: " + reason; }

  private static String validateBase(JSONObject generated, JSONObject state) {
    if (generated == null || generated.optString("reply", "").trim().isEmpty()) return "FORMAT: reply is required.";
    for (String key : FORBIDDEN_ROOT_KEYS) if (generated.has(key)) return hard("Authoritative field: " + key);
    if (SafePresentationView.leaks(state, generated.optString("reply", ""))) return hard("Locked canon reveal in reply.");
    JSONArray choices = generated.optJSONArray("choices");
    for (int i = 0; choices != null && i < choices.length(); i++) {
      JSONObject choice = choices.optJSONObject(i);
      if (choice != null && SafePresentationView.leaks(state, choice.optString("text", ""))) return hard("Locked canon reveal in choice.");
    }
    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
    if (dialogue != null && dialogue.length() > 0 && pendingEncounterCount(state) == 0
        && SceneContextCompiler.sceneCharacterRefs(state, "").size() <= 1) return hard("Uncommitted dialogue speaker.");
    for (int i = 0; dialogue != null && i < dialogue.length(); i++) {
      if (SafePresentationView.leaks(state, dialogue.optString(i, ""))) return hard("Locked canon reveal in dialogue.");
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

  private static int pendingEncounterCount(JSONObject state) {
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    return pending == null ? 0 : pending.length();
  }

}
