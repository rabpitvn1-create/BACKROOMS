package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MilestoneCoreTest {
  @Test public void runtimeProjectionIsBoundedAndLevelAware() throws Exception {
    MilestoneCore core = MilestoneCore.fromText(readAsset("knowledge/milestone_runtime.json"));

    String levelZero = core.promptContext(state("0", 0));
    assertTrue(levelZero.contains("MILESTONE — CURRENT LEVEL ONLY"));
    assertTrue(levelZero.contains("ACT I — NGƯỜI ĐẦU TIÊN"));
    assertTrue(levelZero.contains("Lucia first contact"));
    assertFalse(levelZero.contains("WRITER SECRETS"));
    assertFalse(levelZero.contains("slow-burn"));
    assertFalse(levelZero.contains("FORBIDDEN REVEALS"));
    assertFalse(levelZero.contains("ACT XIV — LUCIA VÀ LỤC TRẦM"));
    assertTrue(levelZero.length() < MilestoneCore.MAX_CONTEXT_CHARS);

    String levelFiveTwo = core.promptContext(state("5.2", 5));
    assertTrue(levelFiveTwo.contains("ACT XIV — LUCIA VÀ LỤC TRẦM"));
    assertTrue(levelFiveTwo.contains("không tự tạo cú trượt/rơi"));
    assertFalse(levelFiveTwo.contains("ACT I — NGƯỜI ĐẦU TIÊN"));

    String levelSix = core.promptContext(state("6", 6));
    assertTrue(levelSix.contains("FINALE — CHÚNG TA CHƯA HIỂU GÌ"));
    assertTrue(levelSix.contains("BOUNDARY: milestone ends at Level 6"));
    assertTrue(levelSix.contains("Level 6.1 is outside this milestone"));
  }

  @Test public void runtimeProjectionRejectsDifferentMilestoneSourceHash() throws Exception {
    String raw = readAsset("knowledge/milestone_runtime.json")
        .replace(MilestoneCore.SOURCE_SHA256, "deadbeef");
    try {
      MilestoneCore.fromText(raw);
      throw new AssertionError("Expected source hash validation failure");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("milestone_source_hash_invalid"));
    }
  }

  @Test public void v2SourceAndEveryNodeArePinnedWithoutFutureOrSecretDump() throws Exception {
    String raw = readAsset("knowledge/milestone_runtime.json");
    JSONObject runtime = new JSONObject(raw);
    Path sourcePath = Files.isRegularFile(Paths.get("docs/milestone-v2.md"))
        ? Paths.get("docs/milestone-v2.md") : Paths.get("../docs/milestone-v2.md");
    byte[] source = Files.readAllBytes(sourcePath);
    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(source);
    StringBuilder hex = new StringBuilder();
    for (byte value : hash) hex.append(String.format("%02x", value & 255));
    assertTrue(runtime.getString("sourceSha256").equals(hex.toString()));
    assertTrue(runtime.getString("sourceDocumentId").equals("MS-V2"));
    MilestoneCore core = MilestoneCore.fromText(raw);
    for (int i = 0; i < runtime.getJSONArray("route").length(); i++) {
      String key = runtime.getJSONArray("route").getString(i);
      String context = core.promptContext(state(key, 0));
      assertTrue(context.contains("CURRENT NODE: " + key + " —"));
      assertTrue(context.contains(runtime.getJSONObject("levels").getJSONObject(key).getString("milestoneId")));
      assertFalse(context.contains(runtime.getString("oneLineCore")));
      assertFalse(context.contains("writerSecrets"));
      assertTrue(context.length() <= MilestoneCore.MAX_CONTEXT_CHARS);
    }
  }

  private static JSONObject state(String levelKey, int parentLevel) throws Exception {
    return new JSONObject()
        .put(LevelCore.LEVEL_KEY, levelKey)
        .put("currentLevel", parentLevel)
        .put("turn", 1);
  }

  private static String readAsset(String relativePath) throws Exception {
    Path[] candidates = {
        Paths.get("src/main/assets", relativePath),
        Paths.get("app/src/main/assets", relativePath)
    };
    for (Path path : candidates) {
      if (Files.isRegularFile(path)) {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalStateException("Unable to locate test asset: " + relativePath);
  }
}
