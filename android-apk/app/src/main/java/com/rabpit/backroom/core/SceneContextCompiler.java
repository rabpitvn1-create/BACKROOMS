package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** The writer boundary: select scene dependencies, never resolve gameplay. */
public final class SceneContextCompiler {
  private SceneContextCompiler() {}

  /** Immutable text-only contract. It retains no Core state, rules or event metadata. */
  public static final class SceneContext {
    public final String levelScene;
    public final String characterScene;
    public final String storyBoundary;
    public final String relevantContinuity;
    public final String committedSceneFacts;
    public final String recentContext;
    public final String playerAction;

    private SceneContext(JSONObject state, String level, String characters, String story,
        String continuity, String facts, String recent, String action) {
      levelScene = visible(state, level);
      characterScene = visible(state, characters);
      storyBoundary = visible(state, story);
      relevantContinuity = visible(state, continuity);
      committedSceneFacts = visible(state, facts);
      recentContext = visible(state, recent);
      playerAction = visible(state, action);
    }

    public JSONObject asJson() throws Exception {
      return new JSONObject().put("LevelScene", levelScene).put("CharacterScene", characterScene)
          .put("StoryBoundary", storyBoundary).put("RelevantContinuity", relevantContinuity)
          .put("CommittedSceneFacts", committedSceneFacts).put("RecentContext", recentContext)
          .put("PlayerAction", playerAction);
    }
  }

  public static SceneContext compile(GameCoreFacade core, MilestoneCore milestone,
      JSONObject state, String action, String turnId) throws Exception {
    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(state, turnId);
    requireEvidence(evidence);
    String snapshot = state.toString();
    return assemble(state, action, evidence, core.levelSceneContext(snapshot, action),
        core.characterSceneContext(snapshot) + mentionedCharacters(state, action), milestone.promptContext(state, action),
        core.narrativeSceneContinuityContext(snapshot, action));
  }

  // Same dependency selectors with in-memory sources for observable packet regression tests.
  static SceneContext compile(LevelCore level, CharacterEncounterCore characters,
      MilestoneCore milestone, JSONObject state, String action, JSONObject evidence) throws Exception {
    requireEvidence(evidence);
    JSONObject snapshot = new JSONObject(state.toString());
    return assemble(snapshot, action, evidence, level.scenePromptContext(snapshot, action),
        characters.scenePromptContext(snapshot) + mentionedCharacters(snapshot, action), milestone.promptContext(snapshot, action),
        NarrativeSkeleton.sceneContext(snapshot.optJSONObject(EmergentTurnEngine.ROOT_KEY), snapshot, action));
  }

  private static SceneContext assemble(JSONObject state, String action, JSONObject evidence,
      String level, String characters, String story, String continuity) throws Exception {
    return new SceneContext(state, level, GmNarratorContract.caoMinhNarrativeCard() + "\n" + characters, story, continuity,
        committedFacts(state, evidence), recent(state), action);
  }

  private static void requireEvidence(JSONObject evidence) {
    if (evidence == null || !evidence.optBoolean("available", false)) {
      throw new IllegalStateException("Committed turn evidence unavailable for scene compilation");
    }
  }

  static String committedFacts(JSONObject state, JSONObject evidence) throws Exception {
    StringBuilder out = new StringBuilder();
    JSONObject player = state.optJSONObject("player");
    if (player != null) append(out, "Cao Minh condition: " + player.optString("condition", ""));
    JSONArray party = state.optJSONArray("party");
    for (int i = 0; party != null && i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !member.optBoolean("present", true)) continue;
      String injury = member.optString("injury", "").trim();
      if (!injury.isEmpty()) append(out, member.optString("name", "Companion") + " condition: " + injury);
    }
    JSONObject combat = state.optJSONObject("combat");
    if (combat != null && combat.optBoolean("active", false)) {
      JSONObject entity = combat.optJSONObject("entity");
      append(out, "Combat is ongoing" + (entity == null ? "." : " with " + entity.optString("name", "") + "."));
    }
    JSONArray claims = evidence.optJSONArray("claims");
    // ponytail: six prose outcomes per turn; expand only with a demonstrated scene need.
    int written = 0;
    for (int i = 0; claims != null && i < claims.length() && written < 6; i++) {
      JSONObject claim = claims.optJSONObject(i);
      if (claim == null) continue;
      String fact = sceneFact(claim.optString("kind", ""), claim.optString("subject", ""),
          claim.optString("value", ""));
      if (fact.isEmpty()) continue;
      append(out, fact);
      written++;
    }
    return out.toString().trim();
  }

  private static String sceneFact(String kind, String subject, String value) {
    switch (kind) {
      case "LEVEL_ENTERED": return "Level entered: " + subject + ".";
      case "ENTITY_ENCOUNTER_STARTED": return "Entity encounter committed: " + subject + ".";
      case "CHEST_DISCOVERED": return "A chest was discovered in the current scene.";
      case "ITEM_ACQUIRED": return "Item acquired: " + subject + ".";
      case "CORE_ACQUIRED": return "Core reward acquired: " + value + ".";
      case "ITEM_ACTION": return "Item action: " + value + ".";
      case "CHARACTER_ENCOUNTERED": return "Character first contact: " + subject + ".";
      case "CHARACTER_REUNION": return "Character reunion: " + subject + ".";
      case "COMBAT_STARTED": return "Combat started with: " + subject + ".";
      case "COMBAT_RESULT": return "Combat result for " + subject + ": " + value + ".";
      case "STAT_UPGRADED": return "Stat upgrade: " + value + ".";
      default: return "";
    }
  }

  static String recent(JSONObject state) {
    JSONArray log = state.optJSONArray("log");
    java.util.ArrayList<String> lines = new java.util.ArrayList<>();
    int length = 0;
    String levelKey = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    java.util.Set<String> refs = sceneCharacterRefs(state, "");
    for (int i = log == null ? -1 : log.length() - 1; i >= 0 && lines.size() < 4; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry == null || entry.has("battleLog")
          || entry.optString("presentationId", "").endsWith(":victory")) continue;
      String recordedLevel = entry.optString("sceneLevelKey", "");
      if (!recordedLevel.isEmpty() && !levelKey.equals(recordedLevel)) break;
      String text = entry.optString("text", "").trim();
      if (text.isEmpty() || hasForeignCharacter(text, refs) || hasForeignLevel(text, levelKey)) continue;
      if (text.length() > 600) text = text.substring(text.length() - 600);
      String line = ("player".equals(entry.optString("role", "")) ? "PLAYER: " : "GM: ") + text;
      if (length + line.length() + 1 > 1800) break;
      lines.add(0, line);
      length += line.length() + 1;
    }
    return String.join("\n", lines);
  }

  static java.util.Set<String> sceneCharacterRefs(JSONObject state, String action) {
    java.util.Set<String> refs = new java.util.LinkedHashSet<>();
    refs.add("cao_minh");
    JSONArray party = state == null ? null : state.optJSONArray("party");
    for (int i = 0; party != null && i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member != null && member.optBoolean("present", true)) refs.add(member.optString("id", ""));
    }
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    for (int i = 0; pending != null && i < pending.length(); i++) refs.add(pending.optString(i, ""));
    for (String id : new String[] {"lucia", "luc_tram", "syvial"}) {
      if (CharacterKnowledge.knows(state, "cao_minh", id, "knownName")
          && mentions(action, id, CharacterEncounterCore.displayName(id))) refs.add(id);
    }
    return refs;
  }

  private static String mentionedCharacters(JSONObject state, String action) {
    java.util.Set<String> present = sceneCharacterRefs(state, "");
    StringBuilder out = new StringBuilder();
    for (String id : sceneCharacterRefs(state, action)) {
      if (present.contains(id)) continue;
      out.append("\nMENTIONED ONLY — continuity, not current presence: ")
          .append(CharacterEncounterCore.sceneVoiceCard(id));
    }
    return out.toString();
  }

  static boolean hasForeignCharacter(String text, java.util.Set<String> refs) {
    for (String id : new String[] {"lucia", "luc_tram", "syvial"}) {
      if (!refs.contains(id) && mentions(text, id, CharacterEncounterCore.displayName(id))) return true;
    }
    return false;
  }

  static boolean mentions(String text, String id, String name) {
    String haystack = " " + mentionKey(text) + " ";
    for (String label : new String[] {id, name}) {
      String needle = mentionKey(label);
      if (!needle.isEmpty() && haystack.contains(" " + needle + " ")) return true;
    }
    return false;
  }

  private static String mentionKey(String text) {
    return (text == null ? "" : text).toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }

  private static boolean hasForeignLevel(String text, String current) {
    java.util.regex.Matcher levels = java.util.regex.Pattern.compile("(?i)level\\s+(-?\\d+(?:\\.\\d+)?)").matcher(text);
    while (levels.find()) if (!current.equals(levels.group(1))) return true;
    String named = LevelCore.rawLevelKeyFromLocation(text);
    return !named.isEmpty() && !current.equals(named);
  }

  private static void append(StringBuilder out, String text) {
    if (!text.trim().isEmpty() && !text.trim().endsWith(":")) out.append("- ").append(text).append('\n');
  }

  private static String visible(JSONObject state, String text) {
    return SafePresentationView.narrativeText(state, text == null ? "" : text);
  }
}
