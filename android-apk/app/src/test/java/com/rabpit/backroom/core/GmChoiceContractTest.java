package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmChoiceContractTest {
  @Test public void gmEntryAddsCoreOwnedSemanticHighlightsWithoutModelMetadata() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("location", "Level 0 / The Lobby — hành lang phía đông")
        .put("party", new JSONArray().put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm")))
        .put("inventory", new JSONArray().put(new JSONObject().put("id", "almond-water").put("name", "Almond Water")));

    String reply = "Cao Minh gặp Clump tại Level 0. Lục Trầm dùng Tịch Quang Phản Kiếm trong khi "
        + "Almond Water vẫn còn. HP 50/50, EXP 10 và trạng thái Chảy máu xuất hiện.";
    JSONObject entry = GmChoiceContract.gmEntry(reply, new JSONObject(), state);
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Cao Minh", "character");
    assertHighlight(highlights, "Lục Trầm", "character");
    assertHighlight(highlights, "Clump", "entity");
    assertHighlight(highlights, "Tịch Quang Phản Kiếm", "skill");
    assertHighlight(highlights, "Chảy máu", "effect");
    assertHighlight(highlights, "Almond Water", "item");
    assertHighlight(highlights, "Level 0", "location");
    assertHighlight(highlights, "HP 50/50", "stat");
    assertHighlight(highlights, "EXP", "stat");
  }

  @Test public void combatSemanticsHighlightTitleCompactHandAndHpPair() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("combat", new JSONObject()
            .put("currentActor", "Cao Minh")
            .put("entity", new JSONObject().put("name", "Hound")));
    JSONArray highlights = GmChoiceContract.semanticHighlights(
        "[F.O.A.K] Vạn Giới Ma Tôn dùng Huyết Ảnh Ma Độn, Hound -20 HP [30/50 HP].",
        state);

    assertHighlight(highlights, "[F.O.A.K]", "stat");
    assertHighlight(highlights, "Vạn Giới Ma Tôn", "character");
    assertHighlight(highlights, "Huyết Ảnh Ma Độn", "skill");
    assertHighlight(highlights, "Hound", "entity");
    assertHighlight(highlights, "-20 HP", "stat");
    assertHighlight(highlights, "30/50 HP", "stat");
  }

  @Test public void gmEntryNormalizesLeakedEnglishEnvironmentTerms() throws Exception {
    JSONObject generated = new JSONObject().put("choices",
        new JSONArray().put(new JSONObject().put("text",
            "Quan sát opening bên corridor và nghe buzz từ fixture")));

    JSONObject entry = GmChoiceContract.gmEntry(
        "Wallpaper vàng bong khỏi wall. Ceiling thấp, fixture phát buzz đều. "
            + "Một opening dẫn sang corridor có junction ở cuối.",
        generated,
        new JSONObject());

    String text = entry.getString("text");
    assertTrue(text.contains("Giấy dán tường") || text.contains("giấy dán tường"));
    assertTrue(text.contains("Trần nhà") || text.contains("trần nhà"));
    assertTrue(text.contains("bộ đèn"));
    assertTrue(text.contains("tiếng ù"));
    assertTrue(text.contains("lối mở"));
    assertTrue(text.contains("hành lang"));
    assertTrue(text.contains("giao lộ"));
    assertTrue(text.contains("tường"));

    String lower = text.toLowerCase();
    assertFalse(lower.contains("wallpaper"));
    assertFalse(lower.contains("ceiling"));
    assertFalse(lower.contains("fixture"));
    assertFalse(lower.contains("buzz"));
    assertFalse(lower.contains("opening"));
    assertFalse(lower.contains("corridor"));
    assertFalse(lower.contains("junction"));

    String choice = entry.getJSONArray("choices").getJSONObject(0).getString("text");
    assertTrue(choice.contains("lối mở"));
    assertTrue(choice.contains("hành lang"));
    assertTrue(choice.contains("tiếng ù"));
    assertTrue(choice.contains("bộ đèn"));
  }

  @Test public void vietnameseNormalizerPreservesOfficialNamesAndStats() {
    String normalized = GmChoiceContract.normalizePlayerFacingVietnamese(
        "Cao Minh ở Level 0, còn Almond Water và Thiên Ma Bộ. corridor phía trước có chemical lạ.");
    assertTrue(normalized.contains("Cao Minh"));
    assertTrue(normalized.contains("Level 0"));
    assertTrue(normalized.contains("Almond Water"));
    assertTrue(normalized.contains("Thiên Ma Bộ"));
    assertTrue(normalized.contains("hành lang"));
    assertTrue(normalized.contains("hóa chất"));
    assertFalse(normalized.toLowerCase().contains("chemical"));
  }


  @Test public void gmEntryNormalizesSecondPersonNarrationButPreservesQuotedDialogue() throws Exception {
    JSONObject entry = GmChoiceContract.gmEntry(
        "Bạn bước qua lối mở. Ánh đèn trên đầu bạn chớp tắt. “Nếu bạn muốn, tôi sẽ đợi.”",
        new JSONObject(), new JSONObject());
    String text = entry.getString("text");

    assertFalse(text.startsWith("Bạn "));
    assertFalse(text.contains("đầu bạn"));
    assertTrue(text.contains("Cao Minh"));
    assertTrue(text.contains("“Nếu bạn muốn, tôi sẽ đợi.”"));
  }

  @Test public void encounterDialogueMergesOnceIntoLocalPresentation() {
    JSONArray dialogue = new JSONArray()
        .put("Đứng yên.")
        .put("Đứng yên.")
        .put("Nói rõ anh là ai.");
    assertEquals(
        "Cô gái giữ khoảng cách.\n\n“Đứng yên.”\n\n“Nói rõ anh là ai.”",
        GmChoiceContract.mergeEncounterDialogue("Cô gái giữ khoảng cách.", dialogue));
  }

  @Test public void choiceNormalizerRemovesCommonEnglishActionLeakage() throws Exception {
    JSONArray input = new JSONArray().put(new JSONObject().put("text",
        "Move forward through the corridor and inspect the door, then listen nearby"));
    JSONArray choices = GmChoiceContract.sanitizeChoices(input);
    String text = choices.getJSONObject(0).getString("text");
    String lower = text.toLowerCase();

    assertTrue(text.contains("Tiến về phía trước") || text.contains("tiến về phía trước"));
    assertTrue(text.contains("hành lang"));
    assertTrue(text.contains("kiểm tra"));
    assertTrue(text.contains("cánh cửa"));
    assertTrue(text.contains("lắng nghe"));
    assertFalse(lower.contains("move forward"));
    assertFalse(lower.contains("through"));
    assertFalse(lower.contains("corridor"));
    assertFalse(lower.contains("inspect"));
    assertFalse(lower.contains("door"));
    assertFalse(lower.contains("then"));
    assertFalse(lower.contains("nearby"));
  }

  @Test public void deterministicTypeReplacesUntypedModelDuplicate() throws Exception {
    JSONObject generated = new JSONObject().put("highlights",
        new JSONArray().put("Clump").put(new JSONObject().put("text", "vệt đen").put("type", "effect")));

    JSONObject entry = GmChoiceContract.gmEntry(
        "Clump đứng cạnh một vệt đen.", generated, new JSONObject());
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Clump", "entity");
    assertHighlight(highlights, "vệt đen", "effect");
  }

  @Test public void knownTermsNotPresentInReplyAreNotInjected() throws Exception {
    JSONObject entry = GmChoiceContract.gmEntry(
        "Cao Minh đứng yên.", new JSONObject(), new JSONObject());
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Cao Minh", "character");
    assertFalse(hasHighlight(highlights, "Clump"));
    assertFalse(hasHighlight(highlights, "Almond Water"));
  }

  @Test public void choicesAlsoReceiveDeterministicSemanticHighlights() throws Exception {
    JSONObject generated = new JSONObject().put("choices",
        new JSONArray().put(new JSONObject().put("text", "Đi về Level 0 và dùng Bandage")));
    JSONObject entry = GmChoiceContract.gmEntry("", generated, new JSONObject());
    JSONObject choice = entry.getJSONArray("choices").getJSONObject(0);

    assertHighlight(choice.getJSONArray("highlights"), "Level 0", "location");
    assertHighlight(choice.getJSONArray("highlights"), "Bandage", "item");
  }

  @Test public void choicesStayPlayerFacingWhileCoreKeepsItsOwnDefaultAction() throws Exception {
    JSONObject ordinary = new JSONObject();
    assertEquals("Khám phá", GmChoiceContract.defaultCoreAction(ordinary));

    JSONObject chest = new JSONObject().put("flags", new JSONObject().put("chestPresent", true));
    assertEquals(ItemCore.OPEN_CHEST_ACTION, GmChoiceContract.defaultCoreAction(chest));

    JSONObject exit = new JSONObject().put(LevelCore.ROUTE_STATE,
        new JSONObject().put("exitAvailable", true));
    assertEquals("Đi qua ranh giới", GmChoiceContract.defaultCoreAction(exit));

    JSONObject generated = new JSONObject().put("choices",
        new JSONArray().put(new JSONObject().put("text", "Quan sát khe sáng phía trước"))
            .put(new JSONObject().put("text", "Kiểm tra tiếng động sau bức tường")));
    JSONArray choices = GmChoiceContract.gmEntry("", generated, ordinary).getJSONArray("choices");
    assertEquals(2, choices.length());
    assertEquals(choices.getJSONObject(0).getString("text"), choices.getJSONObject(0).getString("action"));
    assertEquals(choices.getJSONObject(1).getString("text"), choices.getJSONObject(1).getString("action"));
  }

  private static void assertHighlight(JSONArray values, String text, String type) throws Exception {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value == null || !text.equals(value.optString("text", ""))) continue;
      assertEquals(type, value.optString("type", ""));
      return;
    }
    throw new AssertionError("Missing highlight: " + text + " / " + type + " in " + values);
  }

  private static boolean hasHighlight(JSONArray values, String text) {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && text.equals(value.optString("text", ""))) return true;
      if (text.equals(values.optString(i, ""))) return true;
    }
    return false;
  }

  @Test public void semanticHighlightsIncludeAllCombatEntities() throws Exception {
    JSONObject state = new JSONObject().put("combat", new JSONObject()
        .put("currentActor", "Cao Minh")
        .put("entities", new JSONArray()
            .put(new JSONObject().put("name", "Hound"))
            .put(new JSONObject().put("name", "Clump")))
        .put("entity", new JSONObject().put("name", "Hound")));
    JSONArray highlights = GmChoiceContract.semanticHighlights(
        "Hound lao tới trong khi Clump siết chặt khoảng cách.", state);
    assertTrue(highlights.toString().contains("Hound"));
    assertTrue(highlights.toString().contains("Clump"));
  }

}
