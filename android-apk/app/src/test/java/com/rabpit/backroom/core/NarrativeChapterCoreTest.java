package com.rabpit.backroom.core;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NarrativeChapterCoreTest {
  private static JSONObject baseState() throws Exception {
    return new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("location", "Level 0 / The Lobby")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)))
        .put("inventory", new JSONArray())
        .put("flags", new JSONObject());
  }

  private static JSONArray entityCandidates() throws Exception {
    return new JSONArray()
        .put(new JSONObject()
            .put("kind", "ENTITY")
            .put("payloadKey", "hound")
            .put("publicSummary", "Hound hiện diện.")
            .put("capabilityContext", "A hostile Hound."))
        .put(new JSONObject()
            .put("kind", "ENTITY")
            .put("payloadKey", "smiler")
            .put("publicSummary", "Smiler hiện diện.")
            .put("capabilityContext", "A Smiler."));
  }

  @Test public void loadingManifestAlwaysIncludesJoinedPartyMembers() throws Exception {
    NarrativeChapterCore core = new NarrativeChapterCore();
    JSONObject state = baseState();
    core.startNewGame(state);

    core.prepareLoading(state, entityCandidates());

    JSONObject root = state.getJSONObject(NarrativeChapterCore.ROOT_KEY);
    JSONObject manifest = root.getJSONObject("assetManifest");
    JSONArray party = manifest.getJSONArray("partyMemberIds");
    assertTrue(party.toString(), party.toString().contains("cao_minh"));
    assertTrue(party.toString(), party.toString().contains("lucia"));
    assertEquals(root.getJSONObject("spawnBudget").getInt("entityCount"),
        manifest.getJSONArray("entityKeys").length());
  }

  @Test public void validatorPublishesExactlyThreeCoreChoicesAndSeparatesFactsFromPlan() throws Exception {
    NarrativeChapterCore core = new NarrativeChapterCore();
    JSONObject state = baseState();
    core.startNewGame(state);
    core.prepareLoading(state, entityCandidates());

    JSONObject missions = new JSONObject()
        .put("chapterActTarget", 3)
        .put("missions", new JSONArray()
            .put(new JSONObject().put("id", "main_exit").put("type", "MAIN")
                .put("title", "Thoát Level 0").put("target", 1))
            .put(new JSONObject().put("id", "protect_party").put("type", "MISSION")
                .put("title", "Giữ party sống sót").put("target", 2)));

    JSONObject beat = new JSONObject()
        .put("summary", "Một tình huống buộc Cao Minh phải đánh đổi.")
        .put("missionLinks", new JSONArray().put("protect_party"))
        .put("choices", new JSONArray()
            .put(new JSONObject().put("text", "Ưu tiên bảo vệ đồng đội.")
                .put("effects", new JSONArray().put(new JSONObject()
                    .put("type", "MISSION_PROGRESS").put("missionId", "protect_party"))))
            .put(new JSONObject().put("text", "Ưu tiên giữ bằng chứng.")
                .put("effects", new JSONArray().put(new JSONObject()
                    .put("type", "EVIDENCE_ADD").put("key", "exit_trace"))))
            .put(new JSONObject().put("text", "Giữ thế chủ động cho cảnh sau.")
                .put("effects", new JSONArray().put(new JSONObject()
                    .put("type", "THREAD_SET").put("id", "pressure").put("status", "OPEN")))));

    JSONObject director = new JSONObject().put("plan", new JSONObject()
        .put("scenePurpose", "tradeoff")
        .put("climaxTarget", "commitment")
        .put("beats", new JSONArray().put(beat).put(new JSONObject(beat.toString()))));

    core.commitEdit(state, missions, director);

    JSONObject root = state.getJSONObject(NarrativeChapterCore.ROOT_KEY);
    JSONObject skeleton = root.getJSONObject("skeleton");
    assertTrue(skeleton.has("facts"));
    assertTrue(skeleton.has("plan"));
    assertEquals(3, core.currentChoices(state).length());
    for (int i = 0; i < 3; i++) {
      assertTrue(core.currentChoices(state).getJSONObject(i).getString("action")
          .startsWith(NarrativeChapterCore.CHOICE_PREFIX));
    }

    int factsBefore = skeleton.getJSONArray("facts").length();
    core.resolveChoice(state, NarrativeChapterCore.CHOICE_PREFIX + "A");
    assertTrue(state.getJSONObject(NarrativeChapterCore.ROOT_KEY)
        .getJSONArray("facts").length() > factsBefore);
  }
}
