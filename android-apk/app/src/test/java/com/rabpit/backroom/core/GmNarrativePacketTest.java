package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONObject;
import org.junit.Test;

public class GmNarrativePacketTest {
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

  @Test public void declaredNestedSecretsAndForeignKnowledgeStayOutOfActorView() throws Exception {
    JSONObject binding = new JSONObject().put("schemaVersion", 1).put("canonClass", "WRITER-SECRET")
        .put("knowledgeState", "UNKNOWN").put("actorId", "cao_minh")
        .put("originLayer", "BASELINE_CANON").put("evidenceRef", "");
    JSONObject state = new JSONObject().put("player", new JSONObject().put("hp", 7)
        .put("knowledgeLock", "SECRET_1").put("writerSecret", "SECRET_2")
        .put("hidden", new JSONObject().put("knowledgeBinding", binding).put("value", "SECRET_3")));
    String before = state.toString();
    JSONObject view = GmNarrativePacket.projectState(state);
    assertFalse(view.toString().contains("SECRET_"));
    org.junit.Assert.assertEquals(7, view.getJSONObject("player").getInt("hp"));
    org.junit.Assert.assertEquals(before, state.toString());
    binding.put("canonClass", "POV/BELIEF").put("knowledgeState", "OBSERVED")
        .put("originLayer", "LIVE_STATE").put("evidenceRef", "turn:1").put("actorId", "lucia");
    assertFalse(GmNarrativePacket.projectState(state).toString().contains("SECRET_3"));
  }

  @Test public void projectionDropsHeavyCoreContextAndHistory() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("characterCanon", "FULL_CANON_MARKER".repeat(200))
        .put("levelRoute", new JSONObject().put("streak", 9))
        .put("log", new org.json.JSONArray().put(new JSONObject().put("text", "secret history")))
        .put("flags", new JSONObject().put("hiddenEntityIntent", "ambush"))
        .put("emergent", new JSONObject().put("historicalFacts", new org.json.JSONArray().put("hidden truth")));

    JSONObject projected = GmNarrativePacket.projectState(state);

    assertFalse(projected.has("characterCanon"));
    assertFalse(projected.has("levelRoute"));
    assertFalse(projected.has("log"));
    assertFalse(projected.has("flags"));
    assertFalse(projected.has("emergent"));
    assertTrue(state.has("characterCanon"));
  }

  @Test public void projectionDefaultsUnknownStateToEpistemicAndIncludesActorBeliefs() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 9)
        .put("location", "Hành lang vàng")
        .put("unknownHiddenMechanic", new JSONObject().put("truth", "secret"))
        .put("flags", new JSONObject().put("ambushReady", true))
        .put("combat", new JSONObject()
            .put("active", true)
            .put("seed", 123456)
            .put("entity", new JSONObject().put("key", "hound").put("name", "Hound")
                .put("hp", 40).put("maxHp", 150).put("hiddenIntent", "ambush")))
        .put(EmergentTurnEngine.ROOT_KEY, new JSONObject()
            .put("beliefs", new org.json.JSONArray()
                .put(new JSONObject()
                    .put("claimId", "c1")
                    .put("actorId", "cao_minh")
                    .put("beliefValue", "Có tiếng động phía trước")
                    .put("confidence", "SUSPECTED"))
                .put(new JSONObject()
                    .put("claimId", "c2")
                    .put("actorId", "syvial")
                    .put("beliefValue", "secret")
                    .put("confidence", "CONFIRMED"))));

    JSONObject projected = GmNarrativePacket.projectState(state);

    assertFalse(projected.has("unknownHiddenMechanic"));
    assertFalse(projected.has("flags"));
    assertFalse(projected.getJSONObject("combat").has("seed"));
    assertFalse(projected.getJSONObject("combat").getJSONObject("entity").has("hiddenIntent"));
    assertTrue(projected.has("beliefs"));
    assertTrue(projected.getJSONArray("beliefs").toString().contains("Có tiếng động phía trước"));
    assertFalse(projected.getJSONArray("beliefs").toString().contains("secret"));
  }

  @Test public void quietSceneDoesNotPrimeNoEventTemplate() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 3)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new org.json.JSONArray())
        .put(EmergentTurnEngine.ROOT_KEY, new JSONObject()
            .put("lastSelection", new JSONObject().put("selectedNone", true)));

    String packet = GmNarrativePacket.buildScene(
        "LEVEL_MARKER",
        "CHARACTER_MARKER",
        "",
        "RECENT_MARKER",
        state,
        "Khám phá",
        "MILESTONE_MARKER",
        new JSONObject().put("available", true).put("claims", new org.json.JSONArray()));

    assertTrue(packet.contains("QUIET TURN"));
    assertTrue(packet.contains("composition môi trường mới"));
    assertFalse(packet.contains("không có biến cố chủ động mới"));
  }

  @Test public void sceneStateProjectionDropsWorldScaleState() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 7)
        .put("currentLevelKey", "0")
        .put("location", "Hành lang vàng")
        .put("player", new JSONObject()
            .put("name", "Cao Minh").put("condition", "Ổn định")
            .put("privateProgression", "DROP_PLAYER_DETAIL"))
        .put("inventory", new org.json.JSONArray().put("DROP_INVENTORY"))
        .put("characterProgression", new JSONObject().put("secret", "DROP_PROGRESSION"))
        .put("party", new org.json.JSONArray()
            .put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục")
                .put("present", true).put("injury", "wounded").put("knowledge", "DROP_PARTY_DETAIL"))
            .put(new JSONObject().put("id", "syvial").put("name", "Syvial")
                .put("present", false)))
        .put("gameTime", new JSONObject()
            .put("elapsedSubjectiveMinutes", 45).put("internalClockSeed", "DROP_TIME_DETAIL"));
    CharacterKnowledge.mark(state, "cao_minh", "lucia", "knownName", "core:test");

    JSONObject scene = GmNarrativePacket.projectSceneState(state);
    String serialized = scene.toString();

    assertTrue(serialized.contains("Cao Minh"));
    assertTrue(serialized.contains("Lucia Lục"));
    assertTrue(serialized.contains("wounded"));
    assertTrue(serialized.contains("elapsedSubjectiveMinutes"));
    assertFalse(serialized.contains("DROP_INVENTORY"));
    assertFalse(serialized.contains("DROP_PROGRESSION"));
    assertFalse(serialized.contains("DROP_PLAYER_DETAIL"));
    assertFalse(serialized.contains("DROP_PARTY_DETAIL"));
    assertFalse(serialized.contains("DROP_TIME_DETAIL"));
    assertFalse(serialized.contains("Syvial"));
  }

  @Test public void runtimeScenePacketUsesOnlyCurrentSceneDependencies() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 2)
        .put("location", "Hành lang vàng nhạt")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new org.json.JSONArray());

    String packet = GmNarrativePacket.buildScene(
        "LEVEL_MARKER",
        "CHARACTER_MARKER",
        "CONTINUITY_MARKER",
        "RECENT_MARKER",
        state,
        "Khám phá",
        "MILESTONE_NODE_MARKER",
        new JSONObject().put("available", true).put("claims", new org.json.JSONArray()));

    assertTrue(packet.contains("sandbox text game Backrooms"));
    assertTrue(packet.contains("LEVEL_MARKER"));
    assertTrue(packet.contains("CHARACTER_MARKER"));
    assertTrue(packet.contains("RECENT_MARKER"));
    assertTrue(packet.contains("MILESTONE_NODE_MARKER"));
    assertTrue(packet.contains("CONTINUITY_MARKER"));
    assertFalse(packet.contains("MARKDOWN CANON"));
    assertFalse(packet.contains("LONG-HORIZON CONTINUITY MEMORY"));
    assertFalse(packet.contains("ENTITY CORE:"));
    assertFalse(packet.contains("ITEM CORE:"));
    assertFalse(packet.contains("GM STYLE FEW-SHOT EXAMPLES"));
    assertFalse(packet.contains("\"claims\":[]}. choices"));
    assertFalse(packet.contains("claims chỉ khai báo"));
  }

  @Test public void ordinaryLevelZeroPacketUsesRealKnowledgeAndStaysBudgeted() throws Exception {
    LevelCore core = LevelCore.withKnowledge(
        readRepoAsset("knowledge/level_knowledge.json"), bound -> 0);
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 2)
        .put("location", "Hành lang vàng nhạt")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("flags", new JSONObject())
        .put("characterCanon", "FULL_CANON_MARKER".repeat(250));

    String levelContext = core.promptContext(state, "Cao Minh đi tiếp theo hành lang");
    String packet = GmNarrativePacket.build(
        levelContext,
        "ENTITY CORE: no active Entity encounter this turn.",
        "ITEM CORE: không có loot rời trong scene.",
        "CHARACTER ENCOUNTER CORE: Joined: none. Pending intro: none.",
        "GM: Cao Minh vừa đi qua một đoạn hành lang.\nPLAYER: Cao Minh tiếp tục tiến lên.",
        state,
        "Cao Minh đi tiếp theo hành lang",
        readRepoAsset("knowledge/gm_style_examples.json"));

    assertTrue("Ordinary narrative packet should stay under 13k chars, was: " + packet.length(),
        packet.length() < 13000);
    assertFalse(packet.contains("FULL_CANON_MARKER"));
    assertFalse(levelContext.contains("NARRATIVE TRANSITION SIGNAL"));
    assertFalse(levelContext.contains("transitionTarget"));
    assertFalse(levelContext.contains("sceneLabel"));
    assertFalse(packet.contains("\"transitionTarget\""));
    assertTrue(packet.contains("world outcome"));
  }

  @Test public void exploreContractTargetsRicherProseWithoutInventingEvents() throws Exception {
    String contract = GmNarratorContract.promptContext();
    assertFalse(contract.contains("35–70"));
    assertTrue(contract.contains("70–120 từ"));
    assertTrue(contract.contains("soft target, not a hard minimum"));
    assertTrue(contract.contains("không tạo thêm event/clue/vật thể"));
    assertTrue(contract.contains("NEGATIVE REPETITION MEMORY"));
    assertTrue(contract.contains("không tự thêm việc Cao Minh"));
  }

  @Test public void styleExamplesTeachDistinctExploreShapesAndNegativeMemory() throws Exception {
    String examples = readRepoAsset("knowledge/gm_style_examples.json");
    org.json.JSONObject root = new org.json.JSONObject(examples);
    org.json.JSONArray good = root.getJSONArray("goodExamples");
    assertTrue(good.length() >= 2);
    String first = good.getJSONObject(0).getString("gm");
    String second = good.getJSONObject(1).getString("gm");
    assertFalse(first.substring(0, Math.min(24, first.length()))
        .equals(second.substring(0, Math.min(24, second.length()))));
    for (int i = 0; i < good.length(); i++) {
      String prose = good.getJSONObject(i).getString("gm").toLowerCase(java.util.Locale.ROOT);
      for (String meta : new String[] {"core", "commit", "event", "clue", "scene"}) {
        assertFalse("Good Explore prose must stay in-world: " + meta, prose.contains(meta));
      }
    }

    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("party", new org.json.JSONArray());
    String packet = GmNarrativePacket.build("", "", "", "",
        "GM: Cao Minh đi tiếp trong hành lang.\nPLAYER: Khám phá.", state, "Khám phá", examples);
    assertTrue(packet.contains("NEGATIVE REPETITION MEMORY"));
    assertTrue(packet.contains("không kể lại hay paraphrase"));
  }

  @Test public void packetMakesGmNarrativelyFreeWhileKeepingMechanicsCoreOwned() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 1)
        .put("party", new org.json.JSONArray());

    String packet = GmNarrativePacket.build(
        "LEVEL CORE: current Level 0.",
        "ENTITY CORE: no active Entity encounter this turn.",
        "ITEM CORE: no pending loot.",
        "CHARACTER ENCOUNTER CORE: no pending intro.",
        "(chưa có lượt trước)",
        state,
        "Cao Minh quan sát",
        "");

    assertTrue(packet.contains("ĐÃ ĐƯỢC JAVA CORE COMMIT"));
    assertFalse(packet.contains("Không có cốt truyện, chương hay diễn biến định sẵn"));
    assertTrue(packet.contains("story beat không phải bằng chứng rằng sự kiện đã xảy ra"));
    assertTrue(packet.contains("Java Core sở hữu toàn bộ world outcome"));
    assertTrue(packet.contains("COMMITTED-EVIDENCE RULE"));
    assertTrue(packet.contains("BACKGROUND CANON ≠ TURN EVIDENCE"));
    assertTrue(packet.contains("PLAYER AGENCY"));
    assertTrue(packet.contains("EXPLORER CHOICES"));
  }

  @Test public void milestoneContextIsWriterGuidanceWithoutStateAuthority() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 5)
        .put("currentLevelKey", "5.2")
        .put("turn", 10)
        .put("party", new org.json.JSONArray());

    String packet = GmNarrativePacket.build(
        "LEVEL CORE: current Level 5.2.",
        "ENTITY CORE: none.",
        "ITEM CORE: none.",
        "CHARACTER ENCOUNTER CORE: none.",
        "(recent context)",
        state,
        "Quan sát phía trước",
        "",
        "CANON_MARKER",
        "MILESTONE_MARKER\nWRITER SECRET: do not reveal as actor knowledge.",
        "CONTINUITY_MARKER\nCao Minh và Lục Trầm đã tái ngộ theo committed history.",
        new JSONObject());

    assertTrue(packet.contains("MILESTONE STORY BIBLE"));
    assertTrue(packet.contains("MILESTONE_MARKER"));
    assertTrue(packet.contains("not actor knowledge"));
    assertTrue(packet.contains("never overrides Core or committed evidence"));
    assertTrue(packet.contains("LONG-HORIZON CONTINUITY MEMORY"));
    assertTrue(packet.contains("CONTINUITY_MARKER"));
    assertTrue(packet.contains("not current-turn evidence"));
    assertTrue(packet.contains("CANON_MARKER"));
  }
}
