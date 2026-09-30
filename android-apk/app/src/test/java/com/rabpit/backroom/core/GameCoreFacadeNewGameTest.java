package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GameCoreFacadeNewGameTest {
  @Test public void newGameDiscardsWebViewStatsHpCoreStatusAndInventory() throws Exception {
    JSONObject forged = new JSONObject()
        .put("turn", 900).put("currentLevel", 6).put("saveVersion", 12)
        .put("story", new JSONObject().put("active", true).put("currentChapter", "legacy"))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 10000))
        .put("player", new JSONObject().put("hp", 999).put("attack", 999))
        .put("party", new JSONArray().put(new JSONObject().put("id", "syvial").put("joined", true)))
        .put("inventory", new JSONArray().put(new JSONObject().put("name", "First Aid Kit")
            .put("quantity", 999)))
        .put("characterProgression", new JSONObject()
            .put("coreResource", new JSONObject().put("quantity", 999))
            .put("characters", new JSONObject().put("cao_minh", new JSONObject()
                .put("schema", "core_stats_v1").put("currentHp", 999)
                .put("stats", new JSONObject().put("STR", 999))
                .put("statusEffects", new JSONArray().put(new JSONObject()
                    .put("type", "cheat").put("source", "webview"))))));

    JSONObject fresh = GameCoreFacade.newGameState(forged);
    new CharacterProgressionCore().normalizeState(fresh);
    assertEquals(1, fresh.getInt("turn"));
    assertEquals(0, fresh.getInt("currentLevel"));
    assertEquals(0, fresh.getJSONArray("party").length());
    assertEquals(3, fresh.getJSONArray("inventory").length());
    assertFalse(fresh.has("gameTime"));
    assertFalse(fresh.has("story"));
    assertFalse(fresh.getJSONObject("player").has("attack"));
    assertEquals(50, fresh.getJSONObject("player").getInt("hp"));
    JSONObject profile = new CharacterProgressionCore().profile(fresh, "cao_minh");
    assertEquals(5, profile.getJSONObject("stats").getInt("STR"));
    assertEquals(0, profile.getJSONArray("statusEffects").length());
    assertEquals(0, new CharacterProgressionCore().coreCount(fresh));
  }
}
