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
    assertFalse(packet.contains("\"transitionTarget\""));
    assertTrue(packet.contains("world outcome"));
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
    assertTrue(packet.contains("Không có cốt truyện, chương hay diễn biến định sẵn"));
    assertTrue(packet.contains("Java Core sở hữu toàn bộ world outcome"));
    assertTrue(packet.contains("EXPLORER CHOICES"));
  }
}
