package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SceneContextCompilerTest {
  static MilestoneCore milestone() throws Exception {
    return MilestoneCore.fromText(Files.readString(Paths.get("src/main/assets/knowledge/milestone_runtime.json")));
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

  @Test public void committedFactsRetainOutcomeButDropEventBookkeeping() throws Exception {
    JSONObject evidence = evidence().put("turnId", "INTERNAL_ID").put("commitSeq", 999)
        .put("claims", new JSONArray().put(new JSONObject().put("eventId", "INTERNAL_ID")
            .put("kind", "COMBAT_RESULT").put("subject", "opponent").put("value", "ongoing")));
    String facts = SceneContextCompiler.committedFacts(state(), evidence);
    assertTrue(facts.contains("ongoing"));
    assertFalse(facts.contains("INTERNAL_ID"));
    assertFalse(facts.contains("commitSeq"));
  }
}
