package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;

/** Real facade operations, with only the Android persistence adapter replaced. */
public class PresentationCoreTest {
  static GameCoreFacade core(JSONObject initial) {
    Map<String, String> saved = new HashMap<>();
    saved.put("state_json", initial.toString());
    SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
        SharedPreferences.Editor.class.getClassLoader(), new Class<?>[] {SharedPreferences.Editor.class},
        (proxy, method, args) -> {
          if ("putString".equals(method.getName())) { saved.put((String) args[0], (String) args[1]); return proxy; }
          if ("remove".equals(method.getName())) { saved.remove((String) args[0]); return proxy; }
          if ("commit".equals(method.getName())) return true;
          throw new UnsupportedOperationException(method.getName());
        });
    SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
        SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
        (proxy, method, args) -> {
          if ("getString".equals(method.getName())) return saved.getOrDefault((String) args[0], (String) args[1]);
          if ("edit".equals(method.getName())) return editor;
          throw new UnsupportedOperationException(method.getName());
        });
    Context context = new ContextWrapper(null) {
      @Override public Context getApplicationContext() { return this; }
      @Override public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
      @Override public AssetManager getAssets() { return null; }
    };
    GameCoreFacade facade = GameCoreFacade.create(context, false);
    facade.normalizeState(initial.toString()); // Same initialization as the Android bridge.
    return facade;
  }

  static JSONObject state() throws Exception {
    return GameCoreFacade.newGameState(new JSONObject());
  }

  static JSONObject committed(GameCoreFacade core, String action) throws Exception {
    JSONObject prepared = new JSONObject(core.processRule(core.currentCoreState(), action));
    assertEquals("turn_prepared", prepared.getString("reason"));
    JSONObject result = new JSONObject(core.completePreparedTurn(prepared.getString("turnId"), "{}"));
    assertTrue(result.toString(), result.getBoolean("handled"));
    return result;
  }

  @Test public void lateLegacyNarrationCannotOverwriteLogAfterCoreKnowledgeChanged() throws Exception {
    GameCoreFacade core = core(state());
    JSONObject commit = committed(core, "Cao Minh quan sát");
    JSONObject snapshot = commit.getJSONObject("state");
    String originalLog = snapshot.optJSONArray("log") == null ? "[]" : snapshot.getJSONArray("log").toString();
    JSONArray incoming = snapshot.optJSONArray("log");
    if (incoming == null) incoming = new JSONArray();
    incoming.put(new JSONObject().put("role", "player").put("text", "Cao Minh quan sát"));
    incoming.put(new JSONObject().put("role", "gm").put("text", "LATE_RESPONSE"));
    snapshot.put("log", incoming);
    core.markNameKnown("cao_minh", "lucia_m4a1", "core:new-fact-before-append");
    JSONObject result = append(core, commit, "LATE_RESPONSE").getJSONObject("state");
    assertEquals(originalLog, result.optJSONArray("log") == null ? "[]" : result.getJSONArray("log").toString());
    assertFalse(result.toString().contains("LATE_RESPONSE"));
  }

  static JSONObject append(GameCoreFacade core, JSONObject commit, String text) throws Exception {
    JSONObject snapshot = commit.getJSONObject("state");
    String turnId = commit.getString("turnId");
    return new JSONObject(core.commitPresentation(turnId,
        snapshot.getJSONObject(EmergentTurnEngine.ROOT_KEY).getInt("stateVersion"),
        GameCoreFacade.presentationBaseHash(snapshot), turnId + ":narration", "Cao Minh quan sát",
        new JSONObject().put("role", "gm").put("text", text).toString()));
  }

  @Test public void duplicateConcurrentPresentationsAppendExactlyOnceWithoutChangingMechanics() throws Exception {
    GameCoreFacade core = core(state());
    JSONObject commit = committed(core, "Cao Minh quan sát");
    JSONObject before = commit.getJSONObject("state");
    int size = before.optJSONArray("log") == null ? 0 : before.getJSONArray("log").length();
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<JSONObject> task = () -> { start.await(); return append(core, commit, "Cao Minh quan sát."); };
      java.util.concurrent.Future<JSONObject> first = pool.submit(task), second = pool.submit(task);
      start.countDown();
      JSONObject a = first.get(), b = second.get();
      assertTrue(a.getBoolean("handled") != b.getBoolean("handled"));
      assertEquals("duplicate_presentation", a.getBoolean("handled") ? b.getString("reason") : a.getString("reason"));
      JSONObject after = new JSONObject(core.currentCoreState());
      assertEquals(size + 2, after.getJSONArray("log").length());
      assertEquals(commit.getString("turnId") + ":narration",
          after.getJSONArray("log").getJSONObject(size + 1).getString("presentationId"));
      for (String key : new String[] {"player", "inventory", "party", "currentLevel", "emergent"}) {
        assertEquals(key, before.opt(key).toString(), after.opt(key).toString());
      }
    } finally { pool.shutdownNow(); }
  }

  @Test public void newerTurnAndLoadDropOldResponsesAtTheAppendOperation() throws Exception {
    GameCoreFacade core = core(state());
    core.saveCheckpoint();
    JSONObject first = committed(core, "Cao Minh quan sát");
    committed(core, "Cao Minh nhìn quanh");
    assertEquals("stale_presentation", append(core, first, "LATE_RESPONSE").getString("reason"));
    core.loadCheckpoint();
    assertEquals("stale_presentation", append(core, first, "LATE_RESPONSE").getString("reason"));
    assertFalse(core.currentCoreState().contains("LATE_RESPONSE"));
  }

  @Test public void victoryClosesImmediatelyOnceAndNextExploreHasNoCombatNarrationEvidence() throws Exception {
    JSONObject initial = state().put("flags", new JSONObject().put("entityEncounterKey", "hound"))
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Sinh vật xuất hiện.")));
    CombatChoiceEngine.start(initial, "hound", 0);
    JSONObject combat = initial.getJSONObject("combat");
    combat.getJSONObject("entity").put("hp", 1).put("maxHp", 1);
    combat.getJSONObject("diceState").put("values", new JSONArray("[6,6,6,6,6]"))
        .put("hasRolled", true).put("finalized", true).put("resolved", false).put("hand", "FSF");
    GameCoreFacade core = core(initial);
    JSONObject victory = new JSONObject(core.processCombatResolution("{}")).getJSONObject("state");
    assertFalse(victory.getJSONObject("combat").getBoolean("active"));
    assertEquals("victory", victory.getJSONObject("combat").getString("outcome"));
    assertEquals("", victory.getJSONObject("flags").getString("entityEncounterKey"));
    JSONArray log = victory.getJSONArray("log");
    assertEquals("Hound bị tiêu diệt.",
        log.getJSONObject(log.length() - 1).getString("text"));
    String closed = core.currentCoreState();
    assertEquals("duplicate_combat_resolution", new JSONObject(core.processCombatResolution("{}"))
        .getString("reason"));
    assertEquals(closed, core.currentCoreState());
    JSONObject next = committed(core, "Cao Minh quan sát");
    JSONObject nextState = next.getJSONObject("state");
    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(nextState, next.getString("turnId"));
    assertFalse(CommittedTurnNarrationEvidence.hasClaim(evidence, "COMBAT_RESULT", ""));
    assertFalse(nextState.toString().contains("pendingBattleNarration"));
  }


  @Test public void freeFormExplorerActionRemainsTheAuthoritativePlayerIntent() throws Exception {
    GameCoreFacade first = core(state());
    GameCoreFacade second = core(new JSONObject(first.currentCoreState()));
    JSONObject explore = committed(first, "Khám phá");
    JSONObject inspect = committed(second, "Cao Minh thả thần thức kiểm tra xung quanh");

    assertNotEquals(explore.getString("turnId"), inspect.getString("turnId"));
    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(
        inspect.getJSONObject("state"), inspect.getString("turnId"));
    JSONArray events = evidence.getJSONArray("events");
    boolean found = false;
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.getJSONObject(i);
      if ("PLAYER_ACTION_RESOLVED".equals(event.optString("eventType"))
          && "Cao Minh thả thần thức kiểm tra xung quanh".equals(event.optString("factValue"))) {
        found = true;
      }
    }
    assertTrue(found);
  }

  @Test public void worldTurnRngIgnoresCombatOnlyStateVersionChanges() throws Exception {
    JSONObject sample = state();
    EmergentTurnEngine emergent = new EmergentTurnEngine();
    emergent.normalizeState(sample);
    String action = "Cao Minh tiếp tục tiến lên";
    String first = emergent.nextWorldTurnId(sample, action);
    int firstRngVersion = emergent.worldRngVersion(sample);

    sample.getJSONObject(EmergentTurnEngine.ROOT_KEY).put("stateVersion", 99);
    assertEquals(first, emergent.nextWorldTurnId(sample, action));
    assertEquals(firstRngVersion, emergent.worldRngVersion(sample));

    sample.put("turn", sample.optInt("turn", 1) + 1);
    assertNotEquals(first, emergent.nextWorldTurnId(sample, action));
  }

  @Test public void explicitCoreUpdatesSurviveActualCheckpointSaveAndLoad() throws Exception {
    GameCoreFacade core = core(state());
    core.markEffectKnown("cao_minh", "lucia_m4a1", "core:observed-shot");
    core.markNameKnown("cao_minh", "lucia_m4a1", "core:confirmed-disclosure");
    core.saveCheckpoint();
    core.markNameKnown("cao_minh", "lucia", "core:later-introduction");
    JSONObject restored = new JSONObject(core.loadCheckpoint());
    assertTrue(CharacterKnowledge.knows(restored, "cao_minh", "lucia_m4a1", "knownName"));
    assertTrue(CharacterKnowledge.knows(restored, "cao_minh", "lucia_m4a1", "knownEffect"));
    assertFalse(CharacterKnowledge.knows(restored, "cao_minh", "lucia", "knownName"));
    assertEquals("core:confirmed-disclosure", restored.getJSONObject(CharacterKnowledge.ROOT)
        .getJSONObject("cao_minh").getJSONObject("lucia_m4a1").getString("sourceEventId"));
    assertFalse(CharacterKnowledge.knows(restored, "lucia", "cao_minh_title", "knownName"));
    assertTrue(CharacterKnowledge.knows(restored, "luc_tram", "cao_minh", "knownName"));
    assertFalse(CharacterKnowledge.knows(restored, "syvial", "cultivation", "knownName"));
  }

  @Test public void newerMultiEntityEncounterSurvivesStaleTerminalCombatNormalization()
      throws Exception {
    JSONObject initial = state().put("turn", 4);
    initial.put("flags", new JSONObject()
        .put("entityEncounterKey", "hound")
        .put("entityEncounterKeys", new JSONArray().put("hound"))
        .put("entityEncounterStartedTurn", 4));
    CombatChoiceEngine.start(initial, "hound", 0);
    initial.getJSONObject("combat").put("active", false).put("outcome", "victory");

    initial.put("turn", 5);
    initial.put("flags", new JSONObject()
        .put("entityEncounterKey", "the_lifeform_bacteria_01")
        .put("entityEncounterKeys", new JSONArray()
            .put("the_lifeform_bacteria_01")
            .put("async_member_rifle_aim_right_01"))
        .put("entityEncounterStartedTurn", 5));

    GameCoreFacade core = core(initial);
    JSONObject normalized = new JSONObject(core.currentCoreState());
    assertEquals(2, normalized.getJSONObject("flags").getJSONArray("entityEncounterKeys").length());

    JSONObject started = new JSONObject(
        core.startCombatRuntime("the_lifeform_bacteria_01", 0));
    JSONArray entities = started.getJSONObject("combat").getJSONArray("entities");
    assertEquals(2, entities.length());
    assertEquals("the_lifeform_bacteria_01", entities.getJSONObject(0).getString("key"));
    assertEquals("async_member_rifle_aim_right_01", entities.getJSONObject(1).getString("key"));
    assertEquals(2, started.getJSONObject("flags").getJSONArray("entityEncounterKeys").length());
  }

  @Test public void facadeEmitsVictoryOnlyAfterTheLastEntityDies() throws Exception {
    JSONObject initial = state();
    initial.put("location", LevelCore.LEVEL_ZERO_START_LOCATION);
    initial.put("log", new JSONArray().put(
        new JSONObject().put("role", "gm").put("text", "Nhiều Entity xuất hiện")));
    initial.put("flags", new JSONObject()
        .put("entityEncounterKey", "hound")
        .put("entityEncounterKeys", new JSONArray().put("hound").put("clump").put("deathmoth")));
    CombatChoiceEngine.start(initial,
        new JSONArray().put("hound").put("clump").put("deathmoth"), 0, null, 0);
    JSONArray entities = initial.getJSONObject("combat").getJSONArray("entities");
    for (int i = 0; i < entities.length(); i++) {
      entities.getJSONObject(i).put("hp", 1).put("attack", 1).put("stunTurns", 20)
          .put("evasionPercent", 0);
    }

    GameCoreFacade core = core(initial);
    for (int kill = 0; kill < 3; kill++) {
      core.combatFinishRuntime();
      JSONObject result = new JSONObject(core.processCombatResolution(core.currentCoreState()));
      assertTrue(result.toString(), result.getBoolean("handled"));
      JSONObject current = result.getJSONObject("state");
      JSONObject emergent = current.getJSONObject(EmergentTurnEngine.ROOT_KEY);
      JSONArray commits = emergent.getJSONArray("commitLog");
      JSONObject commit = commits.getJSONObject(commits.length() - 1);
      String eventType = commit.getJSONArray("events").getJSONObject(0).getString("eventType");

      if (kill < 2) {
        assertEquals("COMBAT_HAND_RESOLVED", eventType);
        assertTrue(current.getJSONObject("combat").getBoolean("active"));
        assertEquals(3, current.getJSONObject("flags").getJSONArray("entityEncounterKeys").length());
      } else {
        assertEquals("COMBAT_VICTORY", eventType);
        assertFalse(current.getJSONObject("combat").getBoolean("active"));
        assertEquals("victory", current.getJSONObject("combat").getString("outcome"));
        assertEquals(0, current.getJSONObject("flags").getJSONArray("entityEncounterKeys").length());
        assertEquals("", current.getJSONObject("flags").getString("entityEncounterKey"));
      }
    }
  }

  private static JSONObject encounterState(String id) throws Exception {
    JSONObject initial = state();
    new CharacterEncounterCore().activateEncounterCandidate(initial, id);
    EmergentTurnEngine engine = new EmergentTurnEngine();
    engine.normalizeState(initial);
    String turnId = engine.nextTurnId(initial, "encounter-test");
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "CHARACTER_ENCOUNTERED", "SOCIAL", id,
        new JSONObject().put("observedByPlayer", true).put("factValue", id), new JSONArray()));
    engine.commitAuthoritative(initial, turnId, events, null);
    engine.catchUpProjections(initial);
    return initial;
  }

  private static JSONObject presentationCommit(GameCoreFacade core) throws Exception {
    JSONObject snapshot = new JSONObject(core.currentCoreState());
    return new JSONObject().put("state", snapshot).put("turnId",
        snapshot.getJSONObject(EmergentTurnEngine.ROOT_KEY).getString("lastCommittedTurnId"));
  }

  private static String lastNarration(JSONObject state) throws Exception {
    JSONArray log = state.getJSONArray("log");
    return log.getJSONObject(log.length() - 1).getString("text");
  }

  @Test public void selfIntroductionSurvivesRealPresentationCommitAndTeachesOnlyTheName() throws Exception {
    GameCoreFacade core = core(encounterState("lucia"));
    JSONObject commit = presentationCommit(core);
    JSONObject before = commit.getJSONObject("state");
    assertFalse(CharacterKnowledge.knows(before, "cao_minh", "lucia", "knownName"));
    assertEquals("cô gái cầm một vật kim loại dài", SafePresentationView.text(before, "cao_minh", "Lucia"));
    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(before, commit.getString("turnId"));
    JSONObject frame = new JSONObject(core.sceneFrame(before, evidence, "Quan sát"));
    assertEquals("CHARACTER", frame.getString("focus"));
    assertTrue(frame.getJSONArray("pendingIntro").toString().contains("lucia"));
    JSONObject result = append(core, commit, "Lucia xuất hiện. “Tôi là Lucia.” M4A1, Hound.");
    assertTrue(result.toString(), result.getBoolean("handled"));
    JSONObject after = result.getJSONObject("state");
    String text = lastNarration(after);
    assertTrue(text, text.contains("“Tôi là Lucia.”"));
    assertFalse(text, text.contains("Lucia xuất hiện"));
    assertFalse(text, text.contains("Tôi là cô gái"));
    assertFalse(text, text.contains("M4A1"));
    assertFalse(text, text.contains("Hound"));
    assertTrue(CharacterKnowledge.knows(after, "cao_minh", "lucia", "knownName"));
    assertFalse(CharacterKnowledge.knows(after, "cao_minh", "lucia_m4a1", "knownName"));
    assertFalse(CharacterKnowledge.knows(after, "lucia", "cultivation", "knownName"));
    assertFalse(CharacterKnowledge.knows(after, "lucia", "cao_minh_title", "knownName"));
    assertEquals("Lucia", SafePresentationView.text(after, "cao_minh", "Lucia"));
    assertEquals(0, after.getJSONObject("characterEncounter").getJSONArray("pendingIntro").length());
    String saved = core.currentCoreState();
    assertEquals("duplicate_presentation", append(core, commit, "“Tôi là Syvial.”").getString("reason"));
    assertEquals(saved, core.currentCoreState());
    core.saveCheckpoint();
    assertTrue(CharacterKnowledge.knows(new JSONObject(core.loadCheckpoint()), "cao_minh", "lucia", "knownName"));
  }

  @Test public void encounterAndNarratorNameMentionsDoNotGrantKnowledge() throws Exception {
    GameCoreFacade core = core(encounterState("lucia"));
    JSONObject result = append(core, presentationCommit(core),
        "Lucia xuất hiện. Tôi là Lucia. “Tôi chưa muốn nói tên.” “Cô ấy là Lucia.” “Tôi là Syvial.”");
    assertTrue(result.getBoolean("handled"));
    JSONObject after = result.getJSONObject("state");
    assertFalse(CharacterKnowledge.knows(after, "cao_minh", "lucia", "knownName"));
    assertFalse(CharacterKnowledge.knows(after, "cao_minh", "syvial", "knownName"));
    assertFalse(lastNarration(after).contains("Lucia"));
    assertFalse(lastNarration(after).contains("Syvial"));
  }

  @Test public void disclosureIsGenericAndAbsentNpcsCannotIntroduceThemselves() throws Exception {
    GameCoreFacade core = core(encounterState("syvial"));
    JSONObject result = append(core, presentationCommit(core), "\"Tôi là Syvial.\"");
    assertTrue(result.getBoolean("handled"));
    assertEquals("\"Tôi là Syvial.\"", lastNarration(result.getJSONObject("state")));
    assertTrue(CharacterKnowledge.knows(result.getJSONObject("state"), "cao_minh", "syvial", "knownName"));

    JSONObject initial = encounterState("lucia");
    initial.getJSONArray("party").getJSONObject(0).put("present", false);
    GameCoreFacade absent = core(initial);
    JSONObject rejectedDisclosure = append(absent, presentationCommit(absent), "“Tôi là Lucia.”");
    assertTrue(rejectedDisclosure.getBoolean("handled"));
    assertFalse(CharacterKnowledge.knows(rejectedDisclosure.getJSONObject("state"), "cao_minh", "lucia", "knownName"));
    assertFalse(lastNarration(rejectedDisclosure.getJSONObject("state")).contains("Lucia"));
  }

  @Test public void staleDisclosureCannotTeachKnowledge() throws Exception {
    GameCoreFacade core = core(encounterState("lucia"));
    JSONObject commit = presentationCommit(core);
    core.markEffectKnown("cao_minh", "lucia_m4a1", "core:observed-effect");
    JSONObject result = append(core, commit, "“Tôi là Lucia.”");
    assertFalse(result.getBoolean("handled"));
    assertEquals("stale_presentation", result.getString("reason"));
    assertFalse(CharacterKnowledge.knows(new JSONObject(core.currentCoreState()),
        "cao_minh", "lucia", "knownName"));
  }

}
