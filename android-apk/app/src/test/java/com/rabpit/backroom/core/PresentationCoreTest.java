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
    return GameCoreFacade.create(context, false);
  }

  static JSONObject state() throws Exception {
    return GameCoreFacade.newGameState(new JSONObject());
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
