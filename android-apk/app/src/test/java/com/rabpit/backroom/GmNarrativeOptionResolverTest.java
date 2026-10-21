package com.rabpit.backroom;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Four GM suggestions remain ordinary, scene-scoped Core-validated intents. */
public class GmNarrativeOptionResolverTest {
  private static final String FOUR = "Bóng đèn vẫn kêu rè.\nGỢI Ý HÀNH ĐỘNG:\n"
    + "1. Thử kiểm tra vết bụi dưới cánh cửa bên trái.\n"
    + "2. Chú ý chiếc bóng trên vách dù chưa có dấu chứng.\n"
    + "3. Dừng lại nghe tiếng bóng đèn để đối chiếu âm thanh.\n"
    + "4. Thử chạm mặt thảm gần chân tường đang ẩm.";

  private JSONObject view(String map, String text) throws Exception {
    String[] parts = map.split(":");
    return new JSONObject().put("scene",
      new JSONObject().put("nodeId", parts[0]).put("miniIndex", Integer.parseInt(parts[1])))
      .put("log", new JSONArray().put(new JSONObject().put("role", "gm")
        .put("text", text).put("sceneMap", map)));
  }
  @Test public void parsesExactlyFourClickableChoices() throws Exception {
    JSONArray result = GmNarrativeOptionResolver.parse(FOUR);
    assertEquals(4, result.length());
    assertEquals("Thử kiểm tra vết bụi dưới cánh cửa bên trái.", result.getString(0));
    assertEquals(result.getString(3), GmNarrativeOptionResolver.resolve(4, view("0:1", FOUR)));
    assertEquals(0, GmNarrativeOptionResolver.parse("Không có lựa chọn.").length());
    assertEquals(0, GmNarrativeOptionResolver.parse(FOUR.replace("4. ", "3. ")).length());
    assertEquals(0, GmNarrativeOptionResolver.parse(FOUR + "\n5. Lối giả").length());
  }
  @Test public void rejectsStaleOrForgedSceneChoice() throws Exception {
    JSONObject stale = view("0:1", FOUR);
    stale.getJSONObject("scene").put("miniIndex", 2);
    try { GmNarrativeOptionResolver.resolve(2, stale); fail("stale accepted"); }
    catch (IllegalArgumentException expected) { }
    JSONObject blocked = view("0:1", FOUR);
    blocked.getJSONObject("scene").put("pendingActionId", "queued");
    try { GmNarrativeOptionResolver.resolve(1, blocked); fail("pending accepted"); }
    catch (IllegalStateException expected) { }
    JSONObject invalid = view("0:1", FOUR);
    invalid.put("pendingEncounter", new JSONObject());
    try { GmNarrativeOptionResolver.resolve(3, invalid); fail("encounter accepted"); }
    catch (IllegalStateException expected) { }
    try { GmNarrativeOptionResolver.resolve(5, view("0:1", FOUR)); fail("fifth accepted"); }
    catch (IllegalArgumentException expected) { }
  }
  @Test public void latestMessageOnlyCannotReuseOldOptions() throws Exception {
    JSONObject current = view("0:1", FOUR);
    current.getJSONArray("log").put(new JSONObject().put("role", "gm")
      .put("sceneMap", "0:1").put("text", "Kết quả mới không có thêm lựa chọn."));
    try { GmNarrativeOptionResolver.resolve(1, current); fail("old suggestion reused"); }
    catch (IllegalArgumentException expected) { }
  }
}
