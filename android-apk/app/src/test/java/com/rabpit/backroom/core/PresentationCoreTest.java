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
    JSONObject result = new JSONObject(core.commitNarration(snapshot.toString(), commit.getString("turnId")));
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
    assertTrue(log.getJSONObject(log.length() - 1).getString("text").endsWith("bị tiêu diệt."));
    assertFalse(log.getJSONObject(log.length() - 1).getString("text").contains("Hound"));
    String closed = core.currentCoreState();
    assertEquals("duplicate_combat_resolution", new JSONObject(core.processCombatResolution("{}"))
        .getString("reason"));
    assertEquals(closed, core.currentCoreState());
    JSONObject next = committed(core, "Cao Minh quan sát");
    JSONObject nextState = next.getJSONObject("state");
    assertFalse(GmNarrativePacket.projectState(nextState).has("combat"));
    JSONObject evidence = CommittedTurnNarrationEvidence.fromState(nextState, next.getString("turnId"));
    assertFalse(CommittedTurnNarrationEvidence.hasClaim(evidence, "COMBAT_RESULT", ""));
    JSONObject generated = new JSONObject().put("reply", "Sinh vật bị tiêu diệt.")
        .put("choices", new JSONArray()).put("encounterDialogue", new JSONArray()).put("claims", new JSONArray());
    // Core already owns the terminal outcome; the writer need not re-prove it with a new event claim.
    String beforeValidation = nextState.toString();
    assertTrue(NarrationGuard.validate(generated, nextState, evidence).isEmpty());
    assertEquals(beforeValidation, nextState.toString());
    assertFalse(nextState.toString().contains("pendingBattleNarration"));
  }

  @Test public void oracleWindowIsDeterministicReadOnlyAndMatchesNextDefaultCommit() throws Exception {
    GameCoreFacade core = core(state());
    String before = core.currentCoreState();

    JSONObject first = new JSONObject(core.oracleWindow(before));
    JSONObject second = new JSONObject(core.oracleWindow(before));
    assertEquals(first.toString(), second.toString());
    assertEquals(6, first.getJSONArray("steps").length());
    assertEquals(before, core.currentCoreState());

    JSONObject step = first.getJSONArray("steps").getJSONObject(0);
    assertFalse(step.getString("action").trim().isEmpty());
    assertFalse(step.getString("authorityHash").trim().isEmpty());

    JSONObject committed = committed(core, step.getString("action")).getJSONObject("state");
    assertEquals(step.getString("authorityHash"), GameCoreFacade.oracleAuthorityHash(committed));
  }


  @Test public void oracleShowsExactCoreChestLootInsteadOfLeavingWriterBlind() throws Exception {
    JSONObject initial = state();
    initial.put("flags", new JSONObject().put("chestPresent", true));
    GameCoreFacade core = core(initial);
    String before = core.currentCoreState();

    JSONObject oracle = new JSONObject(core.oracleWindow(before));
    JSONObject first = oracle.getJSONArray("steps").getJSONObject(0);
    assertEquals(ItemCore.OPEN_CHEST_ACTION, first.getString("action"));
    JSONArray events = first.getJSONArray("presentationEvents");
    boolean sawOpened = false;
    boolean sawLoot = false;
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (event == null || !"CHEST_OPENED".equals(event.optString("eventType", ""))) continue;
      sawOpened = true;
      sawLoot = !event.optString("loot", "").trim().isEmpty();
    }
    assertTrue(sawOpened);
    assertTrue(sawLoot);
    assertEquals(before, core.currentCoreState());
    assertTrue(oracle.getString("context").contains("PRESENTATION EVENTS"));
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
}
