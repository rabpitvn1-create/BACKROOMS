package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class OfflinePresenterTest {
  private static final OfflinePresenter.Provider POISON = () -> { throw new AssertionError("Offline path called provider"); };

  private JSONArray views(JSONObject state, String type, String subject) throws Exception {
    return new JSONArray().put(SafePresentationView.event(state, "cao_minh", new JSONObject()
        .put("eventType", type).put("eventId", "t:e1")
        .put("targetRefs", new JSONArray().put(subject))));
  }

  @Test public void entitySpawnNeverCallsProviderAndKeepsRiflemanAtRange() throws Exception {
    JSONObject state = new JSONObject();
    String reply = OfflinePresenter.present(views(state, "ENTITY_ENCOUNTER_STARTED", "async_rifleman"), POISON).getString("reply");
    assertFalse(reply.contains("ASYNC"));
    assertTrue(reply.contains("giữ khoảng cách"));
    assertFalse(reply.contains("áp sát"));
    assertFalse(reply.contains("chưa có thái độ thù địch"));
    assertTrue(OfflinePresenter.present(views(state, "ENTITY_ENCOUNTER_STARTED", "hound"), POISON)
        .getString("reply").contains("áp sát Cao Minh"));
  }

  @Test public void entityDeathNeverCallsProviderAndUsesKnownIdentityOnly() throws Exception {
    JSONObject state = new JSONObject();
    String before = state.toString();
    assertEquals("Bóng người mặc trang bị kín người bị tiêu diệt.",
        OfflinePresenter.present(views(state, "COMBAT_VICTORY", "async_rifleman"), POISON).getString("reply"));
    assertEquals(before, state.toString());
    CharacterKnowledge.mark(state, "cao_minh", "async_rifleman", "knownName", "core:known");
    assertEquals("ASYNC Rifleman bị tiêu diệt.",
        OfflinePresenter.present(views(state, "COMBAT_VICTORY", "async_rifleman"), POISON).getString("reply"));
  }

  @Test public void chestDiscoveryAndOpeningAreOfflineAndDoNotGrantLootAgain() throws Exception {
    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("inventory", new JSONArray().put(new JSONObject().put("name", "Almond Water").put("quantity", 1)))
        .put("player", new JSONObject().put("core", 9));
    String before = state.toString();
    assertTrue(OfflinePresenter.present(views(state, "CHEST_SPAWNED", "0"), POISON)
        .getString("reply").contains("thảm ẩm màu vàng"));
    JSONObject opened = new JSONObject().put("eventType", "CHEST_OPENED")
        .put("targetRefs", new JSONArray().put("0"))
        .put("params", new JSONObject().put("factValue", "Almond Water"));
    JSONArray projected = new JSONArray().put(SafePresentationView.event(state, "cao_minh", opened));
    assertEquals("Cao Minh mở chiếc rương. Bên trong là Almond Water.",
        OfflinePresenter.present(projected, POISON).getString("reply"));
    assertEquals(before, state.toString());
    state.put("currentLevelKey", "1");
    String levelOne = OfflinePresenter.present(views(state, "CHEST_SPAWNED", "1"), POISON).getString("reply");
    assertTrue(levelOne.contains("cột bê tông"));
    assertFalse(levelOne.contains("thảm"));
    state.put("currentLevelKey", "6");
    assertFalse(OfflinePresenter.present(views(state, "CHEST_SPAWNED", "6"), POISON)
        .getString("reply").contains("thảm"));
  }

  @Test public void normalExploreStillCallsProviderExactlyOnce() throws Exception {
    int[] calls = {0};
    JSONObject result = OfflinePresenter.present(views(new JSONObject(), "PLAYER_ACTION_RESOLVED", "cao_minh"),
        () -> { calls[0]++; return new JSONObject().put("reply", "Cao Minh đi tiếp."); });
    assertEquals(1, calls[0]);
    assertEquals("Cao Minh đi tiếp.", result.getString("reply"));
  }
}
