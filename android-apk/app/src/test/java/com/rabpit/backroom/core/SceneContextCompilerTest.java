package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SceneContextCompilerTest {
  static MilestoneCore milestone() throws Exception {
    return MilestoneCore.fromText(new String(Files.readAllBytes(Paths.get("src/main/assets/knowledge/milestone_runtime.json")),
        java.nio.charset.StandardCharsets.UTF_8));
  }

  static JSONObject state() throws Exception {
    return new JSONObject().put("currentLevel", 0).put("currentLevelKey", "0")
        .put("location", "Level 0 / yellow corridor").put("turn", 2)
        .put("player", new JSONObject().put("name", "Cao Minh").put("condition", "Stable"))
        .put("party", new JSONArray());
  }

  static JSONObject evidence() throws Exception {
    return new JSONObject().put("available", true).put("claims", new JSONArray());
  }

  static SceneContextCompiler.SceneContext compile(JSONObject state, String action) throws Exception {
    return SceneContextCompiler.compile(new LevelCore((android.content.Context) null, bound -> 0),
        new CharacterEncounterCore(), milestone(), state, action, evidence());
  }

  @Test public void contractHasExactlySevenSceneFieldsAndNeverCopiesUnrelatedState() throws Exception {
    JSONObject state = state().put("inventory", new JSONArray().put("UNRELATED_INVENTORY"))
        .put("characterCanon", "RAW_WORLD_CANON").put("scheduler", "HIDDEN_SCHEDULER")
        .put("entityRules", "ENTITY_RULES").put("itemRules", "ITEM_RULES")
        .put("epistemic", "FULL_EPISTEMIC_VIEW")
        .put("characterProgression", new JSONObject().put("secret", "PRIVATE_STAT"));
    String before = state.toString();
    SceneContextCompiler.SceneContext packet = compile(state, "Khám phá");
    JSONObject json = packet.asJson();
    assertEquals(7, json.length());
    for (String key : new String[] {"LevelScene", "CharacterScene", "StoryBoundary", "RelevantContinuity",
        "CommittedSceneFacts", "RecentContext", "PlayerAction"}) assertTrue(json.has(key));
    for (String marker : new String[] {"UNRELATED_INVENTORY", "RAW_WORLD_CANON", "HIDDEN_SCHEDULER",
        "ENTITY_RULES", "ITEM_RULES", "FULL_EPISTEMIC_VIEW", "PRIVATE_STAT"}) {
      assertFalse(json.toString().contains(marker));
    }
    assertEquals(before, state.toString());
    state.put("location", "MUTATED_AFTER_COMPILATION");
    json.put("LevelScene", "MUTATED_SERIALIZATION");
    assertFalse(packet.levelScene.contains("MUTATED"));
  }

  @Test public void runtimeWriterPromptOnlyRendersTheCompiledContract() throws Exception {
    JSONObject state = state().put("emergent", new JSONObject().put("lastSelection", new JSONObject()
        .put("situationKey", "INTERNAL_SITUATION").put("publicSummary", "UNCOMMITTED_SUMMARY")
        .put("candidateWeights", "SCHEDULER_WEIGHTS")))
        .put("narrativeSkeleton", "FULL_SKELETON").put("beliefs", "FULL_EPISTEMIC");
    String prompt = GmNarrativePacket.buildScene(compile(state, "Khám phá"));
    assertTrue(prompt.contains("CURRENT LEVEL"));
    for (String marker : new String[] {"INTERNAL_SITUATION", "UNCOMMITTED_SUMMARY", "SCHEDULER_WEIGHTS",
        "FULL_SKELETON", "FULL_EPISTEMIC", "READ-ONLY STATE", "COMMITTED TURN EVIDENCE", "eventId"}) {
      assertFalse(prompt.contains(marker));
    }
  }

  @Test public void dependencyRoutingDropsFutureAndAbsentThreadsEvenWithSharedProtagonist() throws Exception {
    JSONObject state = state();
    new EmergentTurnEngine().normalizeState(state);
    JSONObject skeleton = state.getJSONObject(EmergentTurnEngine.ROOT_KEY).getJSONObject(NarrativeSkeleton.ROOT_KEY);
    skeleton.getJSONArray("longTermTensions")
        .put(new JSONObject().put("keyRefs", new JSONArray().put("cao_minh").put("6"))
            .put("summary", "FUTURE_THREAD"))
        .put(new JSONObject().put("keyRefs", new JSONArray().put("0").put("syvial"))
            .put("summary", "ABSENT_CHARACTER_THREAD"))
        .put(new JSONObject().put("keyRefs", new JSONArray().put("0").put("cao_minh"))
            .put("summary", "CURRENT_THREAD"));
    String prompt = GmNarrativePacket.buildScene(compile(state, "Khám phá"));
    assertTrue(prompt.contains("CURRENT_THREAD"));
    assertFalse(prompt.contains("FUTURE_THREAD"));
    assertFalse(prompt.contains("ABSENT_CHARACTER_THREAD"));
    assertFalse(prompt.contains("first contact"));
    assertFalse(prompt.contains("Táng Kiếm Cốc"));
  }

  @Test public void mentionedKnownCharacterRetrievesContinuityWithoutMakingThemPresent() throws Exception {
    JSONObject state = state();
    CharacterKnowledge.mark(state, "cao_minh", "lucia", "knownName", "core:prior-meeting");
    new EmergentTurnEngine().normalizeState(state);
    state.getJSONObject(EmergentTurnEngine.ROOT_KEY).getJSONObject(NarrativeSkeleton.ROOT_KEY)
        .getJSONArray("importantRelationships").put(new JSONObject()
            .put("actorRefs", new JSONArray().put("cao_minh").put("lucia"))
            .put("summary", "KNOWN_MENTIONED_CONTINUITY"));
    SceneContextCompiler.SceneContext scene = compile(state, "Cao Minh nhớ lời Lucia");
    assertTrue(scene.relevantContinuity.contains("KNOWN_MENTIONED_CONTINUITY"));
    assertTrue(scene.characterScene.contains("MENTIONED ONLY"));
    assertTrue(scene.characterScene.contains("Lucia"));
    assertFalse(scene.characterScene.contains("PRESENT: Cao Minh, Lucia"));
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void recentDropsForeignSceneSubjectsAndOldLevelProse() throws Exception {
    JSONObject state = state().put("log", new JSONArray()
        .put(new JSONObject().put("role", "gm").put("text", "Level 6 OTHER_LEVEL_PROSE"))
        .put(new JSONObject().put("role", "gm").put("text", "Syvial ABSENT_CHARACTER_PROSE"))
        .put(new JSONObject().put("role", "gm").put("text", "Current corridor prose")));
    String recent = compile(state, "Khám phá").recentContext;
    assertTrue(recent.contains("Current corridor prose"));
    assertFalse(recent.contains("OTHER_LEVEL_PROSE"));
    assertFalse(recent.contains("ABSENT_CHARACTER_PROSE"));
  }

  @Test public void levelPaletteNeverProvidesInteractionOrOutcomeRules() throws Exception {
    String knowledge = new JSONObject().put("schemaVersion", 2)
        .put("sectionOrder", new JSONArray().put("identity").put("interactionRules").put("actionConsequences"))
        .put("levels", new JSONObject().put("0", new JSONObject()
            .put("identity", new JSONArray().put("CURRENT_LEVEL_MOTIF"))
            .put("interactionRules", new JSONArray().put("INTERACTION_RULE_MARKER"))
            .put("actionConsequences", new JSONArray().put("ACTION_OUTCOME_RULE_MARKER")))
            .put("6", new JSONObject().put("identity", new JSONArray().put("OTHER_LEVEL_MOTIF"))))
        .toString();
    SceneContextCompiler.SceneContext scene = SceneContextCompiler.compile(
        LevelCore.withKnowledge(knowledge, bound -> 0), new CharacterEncounterCore(), milestone(),
        state(), "kiểm tra", evidence());
    assertTrue(scene.levelScene.contains("CURRENT_LEVEL_MOTIF"));
    assertFalse(scene.levelScene.contains("OTHER_LEVEL_MOTIF"));
    assertFalse(scene.levelScene.contains("INTERACTION_RULE_MARKER"));
    assertFalse(scene.levelScene.contains("ACTION_OUTCOME_RULE_MARKER"));
  }

  @Test public void committedFactsRetainOutcomeButDropEventBookkeeping() throws Exception {
    JSONObject evidence = evidence().put("turnId", "INTERNAL_ID").put("commitSeq", 999)
        .put("claims", new JSONArray().put(new JSONObject().put("eventId", "INTERNAL_ID")
            .put("kind", "COMBAT_RESULT").put("subject", "opponent").put("value", "ongoing")));
    String facts = SceneContextCompiler.committedFacts(state(), evidence);
    assertTrue(facts.contains("ongoing"));
    assertFalse(facts.contains("INTERNAL_ID"));
    assertFalse(facts.contains("commitSeq"));
  }
  @Test public void levelZeroExploreRuntimePacketAndAttemptMetrics() throws Exception {
    LevelCore level = LevelCore.withKnowledge(new String(Files.readAllBytes(
        Paths.get("src/main/assets/knowledge/level_knowledge.json")), java.nio.charset.StandardCharsets.UTF_8), bound -> 0);
    MilestoneCore milestones = milestone();
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    String before = state.toString();
    JSONObject committed = evidence();
    String action = "Khám phá";
    CharacterEncounterCore characters = new CharacterEncounterCore();
    for (int i = 0; i < 5; i++) SceneContextCompiler.compile(level, characters, milestones, state, action, committed);
    long[] construction = new long[31], provider = new long[31], validation = new long[31];
    String prompt = "";
    for (int i = 0; i < construction.length; i++) {
      long start = System.nanoTime();
      SceneContextCompiler.SceneContext scene = SceneContextCompiler.compile(level, characters, milestones, state, action, committed);
      prompt = GmNarrativePacket.buildScene(scene);
      construction[i] = System.nanoTime() - start;
      assertTrue(scene.storyBoundary.contains("CURRENT NODE: 0 —"));
      for (String absent : new String[] {"Lucia", "Syvial", "Lục Trầm"}) assertFalse(prompt.contains(absent));
      assertFalse(scene.storyBoundary.contains("Lucia"));
      assertFalse(scene.storyBoundary.contains("Syvial"));
      assertFalse(scene.storyBoundary.contains("Lục Trầm"));
      assertFalse(scene.levelScene.matches("(?s).*Level [1-6](?:[^0-9]|$).*"));
      for (String excluded : new String[] {"CanonRetriever", "NarrativeSkeleton", "EntityCore", "ItemCore",
          "HIDDEN ROUTE OUTCOME", "candidateWeights", "eventId", "COMMITTED TURN EVIDENCE"}) {
        assertFalse(excluded, prompt.contains(excluded));
      }
      int[] attempts = {0, 0};
      long[] times = {0, 0};
      JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
        long requestStart = System.nanoTime();
        attempts[rejection.isEmpty() ? 0 : 1]++;
        JSONObject generated = new JSONObject().put("reply", "Ánh đèn run nhẹ trên mép thảm; Cao Minh lắng nghe tiếng ù.");
        times[0] += System.nanoTime() - requestStart;
        return generated;
      }, generated -> {
        long validationStart = System.nanoTime();
        String reason = NarrationGuard.validate(generated, state, committed);
        times[1] += System.nanoTime() - validationStart;
        return reason;
      });
      assertTrue(result.getString("reply").contains("Ánh đèn"));
      assertEquals(1, attempts[0]);
      assertEquals(0, attempts[1]);
      provider[i] = times[0];
      validation[i] = times[1];
    }
    assertEquals(before, state.toString());
    java.util.Arrays.sort(construction);
    java.util.Arrays.sort(provider);
    java.util.Arrays.sort(validation);
    System.out.println("SCENE VERIFY Level 0: promptChars=" + prompt.length()
        + " constructionMedianUs=" + construction[15] / 1000
        + " injectedProviderMedianUs=" + provider[15] / 1000
        + " validationMedianUs=" + validation[15] / 1000 + " attempts=1 repairCount=0");
  }

}
