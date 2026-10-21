package com.rabpit.backroom;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** No Android context or Game Core objects are needed to prove the GM domain. */
public class GmQuestItemsTest {
  private JSONObject empty() {
    return new JSONObject().put("version", 1).put("items", new JSONArray())
      .put("appliedActions", new JSONArray());
  }
  private JSONArray ops(JSONObject... value) {
    JSONArray array = new JSONArray();
    for (JSONObject item : value) array.put(item);
    return array;
  }
  private JSONObject create(String id) {
    return new JSONObject().put("type", "create").put("id", id)
      .put("name", "Mảnh ký hiệu").put("description", "Dấu ghép dùng một lần.");
  }
  private JSONObject consume(String id) {
    return new JSONObject().put("type", "consume").put("id", id);
  }

  @Test public void contractRejectsBadGMCommandsAndCoreMutations() throws Exception {
    JSONObject p = GmQuestItems.parseEnvelope("{\"reply\":\"Đã nhặt vật quan sát được.\",\"questItems\":[{\"type\":\"create\",\"id\":\"mark_01\",\"name\":\"Mảnh ký hiệu\",\"description\":\"Dấu thử.\"}]}");
    assertEquals(1, p.getJSONArray("questItems").length());
    assertEquals(0, GmQuestItems.parseEnvelope("{\"reply\":\"Không phát hiện gì.\"}").getJSONArray("questItems").length());
    for (String bad : new String[]{
      "{\"reply\":\"abc\",\"inventory\":[{\"id\":\"ammo\"}]}",
      "{\"reply\":\"abc\",\"questItems\":[{\"type\":\"spawn\",\"id\":\"alien01\"}]}",
      "{\"reply\":\"abc\",\"questItems\":[{\"type\":\"create\",\"id\":\"aa\",\"name\":\"X\",\"description\":\"Y\"}]}",
      "{\"reply\":\"abc\",\"questItems\":[{\"type\":\"consume\",\"id\":\"mark_01\",\"quantity\":99}]}",
      "{\"reply\":\"abc\",\"questItems\":[{\"type\":\"create\",\"id\":\"mark_01\",\"name\":\"<script>\",\"description\":\"Y\"}]}",
      "{\"questItems\":[]}",
      "{\"reply\":\"\",\"questItems\":[]}"} ) {
      try { GmQuestItems.parseEnvelope(bad); fail("accepted invalid payload: "+bad); }
      catch (IllegalArgumentException | org.json.JSONException expected) {}
    }
  }

  @Test public void singleUseAndReplayNeverRestoreConsumedItems() throws Exception {
    JSONObject held = GmQuestItems.applySnapshot(empty(), "scene:001", "ls_2", ops(create("mark_01")));
    assertEquals("held", held.getJSONArray("items").getJSONObject(0).getString("status"));
    assertEquals("ls_2", held.getJSONArray("items").getJSONObject(0).getString("nodeId"));
    assertEquals(1, held.getJSONArray("items").length());
    JSONObject duplicateAck = GmQuestItems.applySnapshot(held, "scene:001", "ls_2", ops(create("mark_01")));
    assertEquals(held.toString(), duplicateAck.toString());
    JSONObject consumed = GmQuestItems.applySnapshot(held, "scene:002", "ls_2", ops(consume("mark_01")));
    assertEquals("consumed", consumed.getJSONArray("items").getJSONObject(0).getString("status"));
    JSONObject duplicateConsumeAck = GmQuestItems.applySnapshot(consumed, "scene:002", "ls_2", ops(consume("mark_01")));
    assertEquals(consumed.toString(), duplicateConsumeAck.toString());
    try { GmQuestItems.applySnapshot(consumed, "scene:003", "ls_2", ops(consume("mark_01"))); fail(); }
    catch (IllegalArgumentException expected) {}
    try { GmQuestItems.applySnapshot(consumed, "scene:004", "ls_2", ops(create("mark_01"))); fail(); }
    catch (IllegalArgumentException expected) {}
  }

  @Test public void failedTransactionNeverMutatesOldLedger() throws Exception {
    JSONObject initial = GmQuestItems.applySnapshot(empty(), "scene:101", "epsilon", ops(create("abc1")));
    String before = initial.toString();
    try {
      GmQuestItems.applySnapshot(initial, "scene:102", "epsilon",
        ops(consume("abc1"), consume("unknown")));
      fail();
    } catch (IllegalArgumentException expected) {}
    assertEquals(before, initial.toString());
    JSONObject success = GmQuestItems.applySnapshot(initial, "scene:103", "epsilon",
      ops(consume("abc1"), create("def2")));
    assertEquals("consumed", success.getJSONArray("items").getJSONObject(0).getString("status"));
    assertEquals("held", success.getJSONArray("items").getJSONObject(1).getString("status"));
  }

  @Test public void restoreCannotImportTamperedSchemaOrDuplicateIds() throws Exception {
    JSONObject forged = empty();
    forged.put("coreInventory", new JSONArray());
    try { GmQuestItems.applySnapshot(forged, "scene:01", "0", ops(create("abc"))); fail(); }
    catch (IllegalArgumentException expected) {}
    JSONObject duplicate = empty();
    JSONObject item = new JSONObject().put("id", "same_item").put("name", "Đồ thử")
      .put("description", "Nhãn").put("nodeId", "0").put("status", "held");
    duplicate.getJSONArray("items").put(item).put(new JSONObject(item.toString()));
    try { GmQuestItems.applySnapshot(duplicate, "scene:02", "0", new JSONArray()); fail(); }
    catch (IllegalArgumentException expected) {}
  }
}
