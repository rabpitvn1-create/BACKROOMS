package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SceneDirectorTest {
  @Test public void playerAssertionNeverBecomesWorldFact() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevelKey", "0")
        .put("currentLevel", 0)
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
    JSONArray views = new JSONArray().put(new JSONObject()
        .put("eventType", "PLAYER_ACTION_RESOLVED")
        .put("actor", "Cao Minh")
        .put("action", "Tiến lại gần chiếc rương"));
    JSONObject facts = OfflinePresenter.sceneFacts(state, "Tiến lại gần chiếc rương", views);
    JSONObject environment = new JSONObject()
        .put("levelKey", "0").put("levelName", "Level 0 — The Lobby")
        .put("motif", "hành lang vàng hẹp").put("sensoryCue", "tiếng ù huỳnh quang");
    JSONObject frame = SceneDirector.compose(state, facts, environment);

    assertEquals("INTENT_ONLY", frame.getJSONObject("playerIntent").getString("authority"));
    assertFalse(frame.getBoolean("chestPresent"));
    assertEquals(0, frame.getJSONArray("chestEvents").length());
    assertEquals("ENVIRONMENT", frame.getString("focus"));
    assertFalse(frame.getJSONObject("gmContract").getBoolean("playerIntentIsWorldFact"));
  }

  @Test public void committedEntityBecomesSceneFocusWithoutInventingChest() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevelKey", "0")
        .put("currentLevel", 0)
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
    JSONArray views = new JSONArray()
        .put(new JSONObject().put("eventType", "PLAYER_ACTION_RESOLVED")
            .put("actor", "Cao Minh").put("action", "Khám phá"))
        .put(new JSONObject().put("eventType", "ENTITY_ENCOUNTER_STARTED")
            .put("actor", "Cao Minh").put("subject", "một sinh vật hình người cao gầy")
            .put("entityAppearance", "một sinh vật hình người cao gầy")
            .put("entityLocation", "ở cuối dãy hành lang vàng"));
    JSONObject frame = SceneDirector.compose(state,
        OfflinePresenter.sceneFacts(state, "Khám phá", views),
        new JSONObject().put("levelKey", "0").put("motif", "giao lộ ba hướng"));

    assertEquals("ENTITY", frame.getString("focus"));
    assertEquals(1, frame.getJSONArray("entityEvents").length());
    assertEquals(0, frame.getJSONArray("chestEvents").length());
    assertEquals("ENTITY_ENCOUNTER", frame.getJSONObject("requiredBeat").getString("kind"));
    assertFalse(frame.has("fallbackSummary"));
    String fallback = SceneDirector.fallbackNarration(frame);
    assertTrue(fallback.toLowerCase(java.util.Locale.ROOT).contains("giao lộ ba hướng"));
    assertTrue(fallback.contains("sinh vật hình người cao gầy"));
  }

  @Test public void entityFallbackUsesCommittedAppearanceDetailsInsteadOfOneLineSummary() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevelKey", "0").put("currentLevel", 0)
        .put("party", new JSONArray()).put("flags", new JSONObject());
    JSONArray views = new JSONArray()
        .put(new JSONObject().put("eventType", "ENTITY_ENCOUNTER_STARTED")
            .put("actor", "Cao Minh")
            .put("subject", "một hình người cực cao và gầy")
            .put("entityAppearance", "một hình người cực cao và gầy được kết từ các sợi mô đen")
            .put("entityLocation", "ở cuối dãy hành lang vàng")
            .put("approachStyle", "stalk")
            .put("details", new JSONArray()
                .put("Lồng ngực rỗng được đan từ các bó sợi đen.")
                .put("Hai cánh tay dài quá đầu gối.")));
    JSONObject frame = SceneDirector.compose(state,
        OfflinePresenter.sceneFacts(state, "Tiếp tục thăm dò", views),
        new JSONObject().put("levelKey", "0")
            .put("motif", "Một hallway vàng hẹp.")
            .put("sensoryCue", "Tiếng buzz huỳnh quang kéo dài."));
    String fallback = SceneDirector.fallbackNarration(frame);

    assertTrue(fallback.contains("hành lang vàng hẹp"));
    assertTrue(fallback.contains("tiếng ù"));
    assertTrue(fallback.contains("Lồng ngực rỗng"));
    assertTrue(fallback.contains("Hai cánh tay dài"));
    assertTrue(fallback.contains("thu hẹp khoảng cách"));
  }

  @Test public void characterEncounterRequiresMeetingBeatAndFallbackDialogue() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevelKey", "0").put("currentLevel", 0)
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("present", true).put("joined", true)))
        .put("characterEncounter", new JSONObject()
            .put("pendingIntro", new JSONArray().put("lucia")))
        .put("flags", new JSONObject());
    JSONArray views = new JSONArray()
        .put(new JSONObject().put("eventType", "CHARACTER_ENCOUNTERED")
            .put("actor", "Cao Minh")
            .put("subject", "một cô gái cầm một vật kim loại dài")
            .put("introDetail", "Trong tay cô là một vật kim loại dài."));
    JSONObject frame = SceneDirector.compose(state,
        OfflinePresenter.sceneFacts(state, "Tiếp tục thăm dò", views),
        new JSONObject().put("levelKey", "0").put("motif", "giao lộ ba hướng"));

    JSONObject beat = frame.getJSONObject("requiredBeat");
    assertEquals("CHARACTER_ENCOUNTER", beat.getString("kind"));
    assertEquals(2, beat.getInt("dialogueLinesMin"));
    String fallback = SceneDirector.fallbackNarration(frame);
    assertTrue(fallback.contains("xuất hiện phía trước Cao Minh"));
    assertTrue(fallback.contains("“"));
    assertTrue(fallback.contains("Trước tiên"));
  }

  @Test public void levelDirectorUsesOnlyPlayerFacingEnvironmentFacts() throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new JSONArray().put("variationPool").put("quietTurnPatterns").put("sensory"))
        .put("levels", new JSONObject().put("0", new JSONObject()
            .put("name", "Level 0")
            .put("variationPool", new JSONArray().put("Blackout hallway với ankle-deep fluid."))
            .put("quietTurnPatterns", new JSONArray().put("Verification turn: backstage only."))
            .put("sensory", new JSONArray().put("abrasive/soggy carpet."))))
        .toString();
    LevelCore core = LevelCore.withKnowledge(knowledge, bound -> 0);
    JSONObject state = new JSONObject().put("currentLevel", 0).put("currentLevelKey", "0")
        .put("location", "Level 0 / Test");

    JSONObject moving = core.sceneDirectorEnvironment(state, "Cao Minh đi tiếp", bound -> 0);
    JSONObject observing = core.sceneDirectorEnvironment(state, "Cao Minh đứng quan sát", bound -> 0);
    assertEquals("variationPool", moving.getString("motifSource"));
    assertEquals("variationPool", observing.getString("motifSource"));
    assertEquals("Hành lang chìm trong bóng tối với chất lỏng sâu đến mắt cá chân.",
        moving.getString("motif"));
    assertEquals("thảm thô ráp và sũng nước.", moving.getString("sensoryCue"));
    assertFalse(observing.toString().contains("Verification turn"));
    assertFalse(observing.toString().contains("ankle-deep"));
    assertFalse(observing.toString().contains("abrasive/soggy"));
  }

  @Test public void localCoreCandidatesCanCoexistInOneTurn() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject()
        .put("turn", 4).put("party", new JSONArray())
        .put("currentLevel", 0).put("currentLevelKey", "0");
    engine.normalizeState(state);
    JSONArray candidates = new JSONArray()
        .put(new JSONObject().put("situationKey", "resource:test").put("kind", "CHEST")
            .put("category", "RESOURCE").put("chancePercent", 100.0d).put("payloadKey", "0")
            .put("publicSummary", "Rương xuất hiện.").put("proposalRequired", false))
        .put(new JSONObject().put("situationKey", "character:test").put("kind", "CHARACTER")
            .put("category", "SOCIAL").put("chancePercent", 100.0d).put("payloadKey", "lucia")
            .put("publicSummary", "Nhân vật xuất hiện.").put("proposalRequired", false));
    JSONObject selected = engine.selectLocalSituations(
        state, candidates, new TurnRng("scene-test", 4,
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 4);

    assertEquals("SCENE_BATCH", selected.getString("kind"));
    assertEquals(2, selected.getJSONArray("situations").length());
    assertEquals("LOCAL_INDEPENDENT",
        state.getJSONObject(EmergentTurnEngine.ROOT_KEY).getJSONArray("selectionTrace")
            .getJSONObject(0).getString("selectionMode"));
  }
}
