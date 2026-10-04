package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LevelCoreTest {
  @Test public void returnToEveryCurrentLevelStartOnlyMovesLocation() throws Exception {
    JSONObject graph = new JSONObject(readRepoAsset("level_graph.json"));
    org.json.JSONArray nodes = graph.getJSONArray("nodes");
    for (int i = 0; i < nodes.length(); i++) {
      JSONObject node = nodes.getJSONObject(i);
      String key = node.getString("key");
      JSONObject state = new JSONObject().put("currentLevelKey", key)
          .put("currentLevel", node.getInt("parentLevel"))
          .put("location", "away from the entrance")
          .put("levelRoute", new JSONObject().put("levelKey", key).put("streak", 7))
          .put("inventory", new org.json.JSONArray().put("saved item"));
      LevelCore.returnToCurrentLevelStart(state);
      assertEquals(key, state.getString("currentLevelKey"));
      assertEquals(node.getString("defaultLocation"), state.getString("location"));
      assertEquals(7, state.getJSONObject("levelRoute").getInt("streak"));
      assertEquals("saved item", state.getJSONArray("inventory").getString(0));
    }
  }

  private static final String ROUTE_ACTION = "Cao Minh đi tiếp theo hành lang";

  private static final class SequenceRng implements LevelCore.IntRng {
    private final int[] values;
    private int index;

    SequenceRng(int... values) {
      this.values = values;
    }

    @Override public int nextInt(int bound) {
      if (values.length == 0) throw new IllegalStateException("No RNG values configured");
      int value = values[Math.min(index, values.length - 1)];
      index++;
      if (value < 0 || value >= bound) throw new IllegalStateException("Test RNG out of range");
      return value;
    }
  }

  private static String readRepoAsset(String relativePath) throws Exception {
    Path[] candidates = new Path[] {
        Paths.get("src/main/assets", relativePath),
        Paths.get("app/src/main/assets", relativePath),
        Paths.get("android-apk/app/src/main/assets", relativePath)
    };
    for (Path path : candidates) {
      if (Files.isRegularFile(path)) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
    throw new IllegalStateException("Unable to locate test asset: " + relativePath);
  }

  private static JSONObject state(int turn, String location) throws Exception {
    return new JSONObject()
        .put("currentLevel", 0)
        .put("turn", turn)
        .put("location", location);
  }


  @Test public void graphAddsCurrentLevelOneToSixSublevelsWithoutTrimmedNodes() throws Exception {
    LevelGraph graph = LevelGraph.fromText(readRepoAsset("level_graph.json"));
    String[] progression = {
        "1","1.2","1.3","1.5","base_alpha","traders_vault",
        "2","2.1","3","3.5","4","office_market",
        "5","5.1","5.2","5.3","6"
    };
    for (int i = 0; i < progression.length; i++) {
      assertTrue("Missing graph node " + progression[i], graph.contains(progression[i]));
      if (i + 1 < progression.length) {
        assertTrue("Missing required edge " + progression[i] + " -> " + progression[i + 1],
            graph.allows(progression[i], progression[i + 1]));
      }
    }
    assertTrue(graph.contains("6.1"));
    assertTrue(graph.outgoing("6").isEmpty());
    assertFalse(graph.allows("6", "6.1"));
    assertTrue(graph.contains("6.31"));
    assertTrue(graph.outgoing("6.31").isEmpty());
    assertFalse(graph.allows("6.1", "6.31"));
    for (String trimmed : new String[]{"1.1","office_space_el3a","6.2","6.3"}) {
      assertFalse("Trimmed node must stay out of gameplay graph: " + trimmed, graph.contains(trimmed));
    }
  }

  @Test public void namedSublevelTargetsAreParsedExplicitly() {
    assertEquals("base_alpha", LevelCore.rawLevelKeyFromLocation("Cao Minh đi vào Base Alpha"));
    assertEquals("traders_vault", LevelCore.rawLevelKeyFromLocation("Cao Minh sang Traders Vault"));
    assertEquals("office_market", LevelCore.rawLevelKeyFromLocation("Cao Minh tiến vào The Office Market"));
    assertEquals("6.31", LevelCore.rawLevelKeyFromLocation("Cao Minh đi vào Level 6.31"));
  }

  @Test public void fallbackProgressionCannotSkipLevelOneSublevels() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = new JSONObject().put("currentLevel", 1).put("currentLevelKey", "1")
        .put("turn", 1).put("location", "Level 1 / Parking Zone");
    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }
    assertFalse(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 2"));
    assertTrue(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 1.2"));
    assertEquals("1.2", state.getString(LevelCore.LEVEL_KEY));
  }

  @Test public void structuredKnowledgeLoadsOnlyCurrentLevelBundle() throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray()
            .put("identity").put("architecture").put("gmConstraints").put("variationPool"))
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("name", "Level 0 — Test")
                .put("identity", new org.json.JSONArray().put("YELLOW_IDENTITY"))
                .put("architecture", new org.json.JSONArray().put("YELLOW_ARCH"))
                .put("gmConstraints", new org.json.JSONArray().put("YELLOW_RULE"))
                .put("variationPool", new org.json.JSONArray().put("YELLOW_VARIATION")))
            .put("0.1", new JSONObject()
                .put("name", "Level 0.1 — Test")
                .put("identity", new org.json.JSONArray().put("ZENITH_IDENTITY"))
                .put("architecture", new org.json.JSONArray().put("ZENITH_ARCH"))
                .put("gmConstraints", new org.json.JSONArray().put("ZENITH_RULE"))
                .put("variationPool", new org.json.JSONArray().put("ZENITH_VARIATION"))))
        .toString();

    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(5));
    JSONObject state = state(1, "Level 0.1 / Zenith Station").put(LevelCore.LEVEL_KEY, "0.1");
    String prompt = core.promptContext(state);

    assertTrue(prompt.contains("LEVEL KNOWLEDGE BUNDLE:"));
    assertTrue(prompt.contains("ZENITH_IDENTITY"));
    assertTrue(prompt.contains("ZENITH_ARCH"));
    assertTrue(prompt.contains("GM CONSTRAINTS"));
    assertTrue(prompt.contains("VARIATION POOL"));
    assertFalse(prompt.contains("YELLOW_IDENTITY"));
    assertFalse(prompt.contains("YELLOW_VARIATION"));
  }

  @Test public void structuredKnowledgeRotatesLargeScenePoolsByTurn() throws Exception {
    org.json.JSONArray seeds = new org.json.JSONArray();
    for (int i = 0; i < 10; i++) seeds.put("SEED_" + i);

    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray().put("identity").put("sceneSeeds"))
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("name", "Level 0")
                .put("identity", new org.json.JSONArray().put("STATIC_RULE"))
                .put("sceneSeeds", seeds)))
        .toString();

    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(5));
    JSONObject turnOne = state(1, "Level 0 / Start");
    JSONObject turnTwo = state(2, "Level 0 / Start");

    String first = core.promptContext(turnOne);
    String second = core.promptContext(turnTwo);

    assertTrue(first.contains("STATIC_RULE"));
    assertTrue(second.contains("STATIC_RULE"));
    assertTrue(first.contains("SCENESEEDS") || first.contains("SCENE SEEDS"));
    assertFalse(first.equals(second));

    int firstSeedCount = 0;
    int secondSeedCount = 0;
    for (int i = 0; i < 10; i++) {
      if (first.contains("SEED_" + i)) firstSeedCount++;
      if (second.contains("SEED_" + i)) secondSeedCount++;
    }
    assertEquals(6, firstSeedCount);
    assertEquals(6, secondSeedCount);
  }

  @Test public void levelZeroSceneFacingPoolsAreNaturalVietnamese() throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("knowledge/level_knowledge.json"));
    JSONObject levelZero = root.getJSONObject("levels").getJSONObject("0");
    String playerFacing = levelZero.getJSONArray("variationPool").toString() + "\n"
        + levelZero.getJSONArray("sensory").toString();
    String lower = playerFacing.toLowerCase(java.util.Locale.ROOT);

    for (String token : new String[] {
        "ankle-deep", "abrasive/soggy", "hallway", "chemical", "blackout",
        "pillar ", "staircase", "crawlspace", " chamber", " patch ", " creature",
        " baseline", " section ", " peripheral shift", " core "
    }) {
      assertFalse("Technical token leaked into Level 0 scene pool: " + token,
          lower.contains(token));
    }
    assertTrue(playerFacing.contains("chất lỏng sâu đến mắt cá chân"));
    assertTrue(playerFacing.contains("Thảm ẩm vừa thô ráp vừa sũng nước"));
  }

  @Test public void structuredKnowledgeRejectsLegacySchemaAsAuthoritativeBundle() throws Exception {
    String legacy = new JSONObject()
        .put("schemaVersion", 1)
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("identity", new org.json.JSONArray().put("SHOULD_NOT_LOAD"))))
        .toString();

    LevelCore core = LevelCore.withKnowledge(legacy, new SequenceRng(5));
    String prompt = core.promptContext(state(1, "Level 0 / Start"));
    assertTrue(prompt.contains("Canon for Level 0"));
    assertFalse(prompt.contains("SHOULD_NOT_LOAD"));
  }

  @Test public void sublevelSnapshotsRotateThroughManifestAssets() throws Exception {
    JSONObject manifest = new JSONObject()
        .put("root", "level_snapshots/drive")
        .put("sublevelRoot", "level_snapshots/sublevels/level_0")
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray().put("level_0/01.webp"))))
        .put("sublevels", new JSONObject()
            .put("0.1", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray()
                    .put("sublevel_0-1/03.webp")
                    .put("sublevel_0-1/04.webp"))));

    LevelCore core = new LevelCore(null, new SequenceRng(5));
    core.loadSnapshotManifestText(manifest.toString());

    JSONObject sublevel = state(1, "Level 0.1 / Zenith Station")
        .put(LevelCore.LEVEL_KEY, "0.1");

    JSONObject first = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(
        "file:///android_asset/level_snapshots/sublevels/level_0/sublevel_0-1/03.webp",
        first.getString("path"));

    sublevel.put("turn", 2);
    JSONObject second = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(
        "file:///android_asset/level_snapshots/sublevels/level_0/sublevel_0-1/04.webp",
        second.getString("path"));

    sublevel.put("turn", 3);
    JSONObject wrapped = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(first.getString("path"), wrapped.getString("path"));
  }

  @Test public void snapshotDescriptorExposesAuthoritativeRouteStreakForHud() throws Exception {
    assertEquals(5, LevelCore.ROUTE_REQUIRED_STREAK);
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start")
        .put(LevelCore.ROUTE_STATE, new JSONObject()
            .put("levelKey", "0")
            .put("streak", 3)
            .put("exitAvailable", false));

    JSONObject descriptor = new JSONObject(core.snapshotDescriptor(state));

    assertEquals(3, descriptor.getInt("routeStreak"));
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, descriptor.getInt("routeRequiredStreak"));
    assertFalse(descriptor.getBoolean("routeExitAvailable"));

    state.getJSONObject(LevelCore.ROUTE_STATE).put("streak", 99).put("exitAvailable", true);
    JSONObject capped = new JSONObject(core.snapshotDescriptor(state));
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, capped.getInt("routeStreak"));
    assertTrue(capped.getBoolean("routeExitAvailable"));
  }

  @Test public void mainLevelSnapshotRotationRemainsUnchanged() throws Exception {
    JSONObject manifest = new JSONObject()
        .put("root", "level_snapshots/drive")
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray()
                    .put("level_0/01.webp")
                    .put("level_0/02.webp"))));

    LevelCore core = new LevelCore(null, new SequenceRng(5));
    core.loadSnapshotManifestText(manifest.toString());

    JSONObject main = state(2, "Level 0 / Start");
    JSONObject descriptor = new JSONObject(core.snapshotDescriptor(main));
    assertEquals("file:///android_asset/level_snapshots/drive/level_0/02.webp", descriptor.getString("path"));
  }

  @Test public void routeRollUsesOneFortyNineFiftyDistributionBoundaries() throws Exception {
    LevelCore tripleCore = new LevelCore(null, new SequenceRng(0));
    JSONObject triple = state(1, "Level 0 / A");
    tripleCore.rollRouteForExplorerAction(triple, ROUTE_ACTION);
    assertEquals(3, triple.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("SUCCESS", triple.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));

    LevelCore normalCore = new LevelCore(null, new SequenceRng(1));
    JSONObject normal = state(1, "Level 0 / A");
    normalCore.rollRouteForExplorerAction(normal, ROUTE_ACTION);
    assertEquals(1, normal.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));

    LevelCore lastSuccessCore = new LevelCore(null, new SequenceRng(49));
    JSONObject lastSuccess = state(1, "Level 0 / A");
    lastSuccessCore.rollRouteForExplorerAction(lastSuccess, ROUTE_ACTION);
    assertEquals(1, lastSuccess.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));

    LevelCore failCore = new LevelCore(null, new SequenceRng(50));
    JSONObject failed = state(1, "Level 0 / A");
    failCore.rollRouteForExplorerAction(failed, ROUTE_ACTION);
    assertEquals(0, failed.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("RESET", failed.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));
  }

  @Test public void eightConsecutiveSuccessesUnlockExit() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, route.getInt("streak"));
    assertTrue(route.getBoolean("exitAvailable"));
    assertEquals("EXIT_AVAILABLE", route.getString("lastResult"));
  }

  @Test public void tripleSuccessCountsAsThreeAndCanCompleteChain() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(1,1,1,1,1,0));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= 6; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, route.getInt("streak"));
    assertTrue(route.getBoolean("exitAvailable"));
    assertEquals("EXIT_AVAILABLE", route.getString("lastResult"));
  }

  @Test public void levelZeroPointOneCannotExitBeforeFiveAttemptsAfterTripleBoost() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0, 1, 1, 1, 1));
    JSONObject state = state(1, "Level 0.1 / Zenith Station")
        .put(LevelCore.LEVEL_KEY, "0.1");

    for (int turn = 1; turn <= 4; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, route.getInt("streak"));
    assertEquals(4, route.getInt("attempts"));
    assertFalse(route.getBoolean("exitAvailable"));
    assertEquals("SUCCESS", route.getString("lastResult"));

    state.put("turn", 5);
    core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    assertEquals(5, route.getInt("attempts"));
    assertTrue(route.getBoolean("exitAvailable"));
    assertEquals("EXIT_AVAILABLE", route.getString("lastResult"));
  }

  @Test public void legacyReadyPointOneRouteWithoutAttemptCountStaysReady() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(9, "Level 0.1 / Zenith Station")
        .put(LevelCore.LEVEL_KEY, "0.1")
        .put(LevelCore.ROUTE_STATE, new JSONObject()
            .put("level", 0)
            .put("levelKey", "0.1")
            .put("streak", LevelCore.ROUTE_REQUIRED_STREAK)
            .put("exitAvailable", true)
            .put("lastRollTurn", 8)
            .put("lastResult", "EXIT_AVAILABLE"));

    core.normalizeState(state);

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, route.getInt("attempts"));
    assertTrue(route.getBoolean("exitAvailable"));
  }

  @Test public void oneFailureResetsFourSuccessesToZero() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,5,5,5,60));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("streak"));
    assertFalse(route.getBoolean("exitAvailable"));
    assertEquals("RESET", route.getString("lastResult"));
  }

  @Test public void sameTurnCannotReroll() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,60));
    JSONObject state = state(4, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    core.rollRouteForExplorerAction(state, ROUTE_ACTION);

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(1, route.getInt("streak"));
    assertEquals("SUCCESS", route.getString("lastResult"));
  }

  @Test public void nonRouteActionDoesNotRoll() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, "Cao Minh nghỉ tại đây");

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("streak"));
    assertEquals(-1, route.getInt("lastRollTurn"));
  }

  @Test public void lockedPlayerTransitionIsNotAppliedBeforeChainCompletes() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");
    core.normalizeState(state);

    assertFalse(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 0.1"));
    assertEquals("0", state.getString(LevelCore.LEVEL_KEY));
    assertEquals(0, state.getInt("currentLevel"));
  }

  @Test public void completedChainAppliesOnlyNextSublevelAndResetsRoute() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");
    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    assertTrue(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 0.1"));
    assertEquals("0.1", state.getString(LevelCore.LEVEL_KEY));
    assertEquals(0, state.getInt("currentLevel"));
    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals("0.1", route.getString("levelKey"));
    assertEquals(0, route.getInt("streak"));
    assertFalse(route.getBoolean("exitAvailable"));
  }

  @Test public void completedLevelZeroCannotSkipDirectlyToLevelOne() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");
    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    assertFalse(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 1"));
    assertEquals("0", state.getString(LevelCore.LEVEL_KEY));
  }

  @Test public void fullMandatorySublevelSequenceEndsAtLevelOneViaPlayerActions() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    String[] progression = LevelCore.levelZeroProgressionKeys();
    JSONObject state = state(1, LevelCore.defaultLocation(progression[0]))
        .put(LevelCore.LEVEL_KEY, progression[0]);
    int turn = 1;

    for (int i = 0; i < progression.length - 1; i++) {
      for (int step = 0; step < LevelCore.ROUTE_REQUIRED_STREAK; step++) {
        state.put("turn", turn++);
        core.rollRouteForExplorerAction(state, ROUTE_ACTION);
      }
      String next = progression[i + 1];
      assertTrue("Expected transition to " + next,
          core.applyPlayerTransitionIfRequested(state, "Cao Minh đi qua " + LevelCore.defaultLocation(next)));
      assertEquals(next, state.getString(LevelCore.LEVEL_KEY));
    }

    assertEquals("1", state.getString(LevelCore.LEVEL_KEY));
    assertEquals(1, state.getInt("currentLevel"));
  }

  @Test public void levelZeroPointThreeIsNotPartOfPlayerRoute() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0.2 / Remodeled Mess").put(LevelCore.LEVEL_KEY, "0.2");
    for (int turn = 1; turn <= LevelCore.ROUTE_REQUIRED_STREAK; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    assertFalse(core.applyPlayerTransitionIfRequested(state, "Cao Minh đi vào Level 0.3"));
    assertEquals("0.2", state.getString(LevelCore.LEVEL_KEY));
  }

  @Test public void failedRouteImmediatelyReturnsWorkingStateToChainOrigin() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5, 60));
    JSONObject state = state(1, "Level 0 / Origin").put(LevelCore.LEVEL_KEY, "0");

    core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    state.put("turn", 2);
    state.put("location", "Level 0 / Deeper corridor");
    core.rollRouteForExplorerAction(state, ROUTE_ACTION);

    assertEquals("Level 0 / Origin", state.getString("location"));
    assertEquals(0, state.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("RESET", state.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));
  }

  @Test public void stageIndexFollowsProgressionGraphNotDecimalNames() throws Exception {
    String[] keys = {
        "0","0.1","0.2","0.5","0.7","manila_room","the_torment","red_rooms",
        "1","1.2","1.3","1.5","base_alpha","traders_vault",
        "2","2.1","3","3.5","4","office_market","5","5.1","5.2","5.3","6","6.1","6.31"
    };
    for (int i = 0; i < keys.length; i++) {
      assertEquals(keys[i], i, LevelCore.stageIndexForKey(keys[i]));
    }
    assertEquals(4, LevelCore.stageIndexForKey("0.7"));
    assertEquals(8, LevelCore.stageIndexForKey("1"));
    assertEquals(24, LevelCore.stageIndexForKey("6"));
    assertEquals(26, LevelCore.stageIndexForKey("6.31"));
  }

  @Test public void actionAwareLevelContextSelectsRelevantSections() throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray()
            .put("identity").put("architecture").put("sensory").put("interactionRules").put("navigation"))
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("name", "Level 0")
                .put("identity", new org.json.JSONArray().put("IDENT_FACT"))
                .put("architecture", new org.json.JSONArray().put("ARCH_FACT"))
                .put("sensory", new org.json.JSONArray().put("SENSORY_FACT"))
                .put("interactionRules", new org.json.JSONArray().put("INTERACT_FACT"))
                .put("navigation", new org.json.JSONArray().put("NAV_FACT"))))
        .toString();

    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(0));

    String observePrompt = core.knowledgeContext("0", 1, "Cao Minh đứng yên lắng nghe xung quanh");
    assertTrue(observePrompt.contains("SENSORY_FACT"));
    assertFalse(observePrompt.contains("INTERACT_FACT"));
    assertFalse(observePrompt.contains("NAV_FACT"));

    String interactPrompt = core.knowledgeContext("0", 1, "Cao Minh mở tủ gỗ kiểm tra");
    assertTrue(interactPrompt.contains("INTERACT_FACT"));
    assertFalse(interactPrompt.contains("SENSORY_FACT"));
    assertFalse(interactPrompt.contains("NAV_FACT"));

    String explorePrompt = core.knowledgeContext("0", 1, "Cao Minh tiếp tục di chuyển");
    assertTrue(explorePrompt.contains("NAV_FACT"));
    assertFalse(explorePrompt.contains("SENSORY_FACT"));
    assertFalse(explorePrompt.contains("INTERACT_FACT"));
  }

  @Test public void sceneContextRoutesOnlyEnvironmentalDependencies() throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray()
            .put("identity").put("architecture").put("sensory").put("resources")
            .put("entities").put("gmConstraints").put("sceneSeeds"))
        .put("levels", new JSONObject().put("0", new JSONObject()
            .put("name", "Level 0")
            .put("identity", new org.json.JSONArray().put("IDENTITY_FACT"))
            .put("architecture", new org.json.JSONArray().put("ARCH_FACT"))
            .put("sensory", new org.json.JSONArray().put("SENSORY_FACT"))
            .put("resources", new org.json.JSONArray().put("ITEM_MARKER"))
            .put("entities", new org.json.JSONArray().put("ENTITY_MARKER"))
            .put("gmConstraints", new org.json.JSONArray().put("GLOBAL_CONSTRAINT_MARKER"))
            .put("sceneSeeds", new org.json.JSONArray().put("SCENE_SEED"))))
        .toString();
    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(0));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");

    String scene = core.scenePromptContext(state, "Cao Minh quan sát xung quanh");

    assertTrue(scene.contains("LEVEL: Level 0"));
    assertTrue(scene.contains("SENSORY_FACT"));
    assertFalse(scene.contains("ITEM_MARKER"));
    assertFalse(scene.contains("ENTITY_MARKER"));
    assertFalse(scene.contains("GLOBAL_CONSTRAINT_MARKER"));
    assertTrue(scene.length() <= LevelCore.MAX_SCENE_CONTEXT_CHARS);
  }

  @Test public void sceneLevelDescriptionIsCompactOrganizedAndKeepsDefiningTraits()
      throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray()
            .put("identity").put("architecture").put("zones").put("sensory")
            .put("hazards").put("navigation").put("variationPool"))
        .put("levels", new JSONObject().put("0", new JSONObject()
            .put("name", "Level 0")
            .put("identity", new org.json.JSONArray().put("IDENTITY_PRIMARY").put("IDENTITY_EXTRA"))
            .put("architecture", new org.json.JSONArray().put("STRUCTURE_PRIMARY").put("STRUCTURE_EXTRA"))
            .put("sensory", new org.json.JSONArray().put("SENSORY_PRIMARY").put("SENSORY_EXTRA"))
            .put("hazards", new org.json.JSONArray().put("HAZARD_PRIMARY").put("HAZARD_EXTRA"))
            .put("zones", new org.json.JSONArray().put("ZONE_ONE").put("ZONE_TWO"))
            .put("navigation", new org.json.JSONArray().put("NAV_ONE").put("NAV_TWO"))
            .put("variationPool", new org.json.JSONArray().put("VAR_ONE").put("VAR_TWO"))))
        .toString();
    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(0));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");

    String scene = core.scenePromptContext(state, "Cao Minh tiếp tục di chuyển");

    assertTrue(scene.contains("LEVEL ESSENTIALS:"));
    assertTrue(scene.contains("- IDENTITY: IDENTITY_PRIMARY"));
    assertTrue(scene.contains("- STRUCTURE: STRUCTURE_PRIMARY"));
    assertTrue(scene.contains("- SENSORY: SENSORY_PRIMARY"));
    assertTrue(scene.contains("- PRIMARY HAZARD: HAZARD_PRIMARY"));
    assertTrue(scene.contains("SCENE FOCUS:"));
    assertTrue(scene.contains("ZONES:"));
    assertTrue(scene.contains("NAVIGATION:"));
    assertFalse(scene.contains("IDENTITY_EXTRA"));
    assertFalse(scene.contains("STRUCTURE_EXTRA"));
    assertFalse(scene.contains("SENSORY_EXTRA"));
    assertFalse(scene.contains("HAZARD_EXTRA"));
  }

  @Test public void levelZeroKnowledgeContextBudgetUsesRealAsset() throws Exception {
    LevelCore core = LevelCore.withKnowledge(
        readRepoAsset("knowledge/level_knowledge.json"), new SequenceRng(0));
    JSONObject state = state(1, "Level 0 / Start").put(LevelCore.LEVEL_KEY, "0");

    String explorePrompt = core.promptContext(state, "Cao Minh đi tiếp theo hành lang");

    assertTrue("Real Level 0 context should stay below 6,000 chars, was: " + explorePrompt.length(),
        explorePrompt.length() < 6000);
    assertTrue(explorePrompt.contains("GM CONSTRAINTS"));
    assertTrue(explorePrompt.contains("CANONICALFACTS"));
    assertTrue(explorePrompt.contains("FORBIDDENINVENTIONS"));
  }

  @Test public void coreKnowledgeDoesNotRotateWithTurn() throws Exception {
    JSONObject level = new JSONObject().put("name", "Level 0")
        .put("identity", new org.json.JSONArray()
            .put("FIRST_IDENTITY").put("SECOND_IDENTITY").put("THIRD_IDENTITY"));
    String knowledge = new JSONObject().put("schemaVersion", 2)
        .put("levels", new JSONObject().put("0", level)).toString();
    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(0));
    assertEquals(core.knowledgeContext("0", 1), core.knowledgeContext("0", 9));
    assertTrue(core.knowledgeContext("0", 9).contains("FIRST_IDENTITY"));
  }


}
