package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Core SaveState is the only source of committed Milestone events. */
class StoryMilestoneHistoryTest {
  private fun plan(checkpointId: String): String = JSONObject()
    .put("schemaVersion", 2).put("checkpointId", checkpointId)
    .put("events", TestCheckpointQuota.events(JSONArray()
      .put(JSONObject().put("id", "story").put("turn", 1).put("kind", "STORY")
        .put("action", "Nhân vật mở cửa")
        .put("event", "Cao Minh nhìn thấy căn phòng màu vàng")
        .put("outcome", "Mọi người đi tiếp"))
      .put(JSONObject().put("id", "boss").put("turn", 18).put("kind", "FINAL_ENTITY")
        .put("action", "Boss xuất hiện").put("event", "Hound chặn đường")
        .put("outcome", "Combat bắt đầu").put("entityKey", "hound")
        .put("hpMultiplier", 9).put("expMultiplier", 9))))
    .put("completion", JSONObject()
      .put("type", "NEXT_CHECKPOINT").put("outcome", "Qua Checkpoint"))
    .toString()

  private fun initial(): GameState = StoryMilestonePlan.installCurrent(
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())),
    plan("CP-L00-01"))

  @Test fun extraneousStoryFactsCannotVetoMilestoneOrBecomeSavedState() {
    val raw = JSONObject(plan("CP-L00-01"))
    raw.getJSONArray("events").getJSONObject(0).put("storyFacts",
      JSONArray().put(JSONObject().put("kind", "CLUE")
        .put("subject", "Cánh cửa màu vàng")))
    val parsed = StoryMilestonePlan.parse(raw.toString(), StoryCheckpointCore.Position(0, 1, 20))
    assertFalse(parsed.toJson().toString().contains("storyFacts"))
    val installed = StoryMilestonePlan.installCurrent(
      StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), raw.toString())
    assertFalse(installed.world.containsKey("story.factLedger"))
    assertFalse(StoryMilestonePlan.current(installed)!!.plan.toJson().toString().contains("storyFacts"))
  }

  @Test fun saveLoadPreservesCommittedContextWithoutStoryFactLedger() {
    val state = initial()
    assertEquals(0, JSONObject(StoryMilestoneRuntime.continuityContext(state))
      .getJSONArray("recentCommittedEvents").length())
    val committed = StoryMilestoneRuntime.acceptTurn(state)
    assertTrue(StoryMilestoneRuntime.continuityContext(committed).contains("Cao Minh"))
    assertFalse(committed.world.containsKey("story.factLedger"))
    val restored = GameStateCodec.decode(GameStateCodec.encode(committed))
    StoryMilestoneRuntime.validateStoredState(restored)
    assertTrue(StoryMilestoneRuntime.continuityContext(restored).contains("Cao Minh"))
    assertFalse(CoreClientProjection.project(JSONObject(), restored)
      .getJSONObject("story").has("factStats"))
  }

  @Test fun checkpointHistoryStoresEventsAndRejectsTampering() {
    val beforeBoss = TestCheckpointQuota.reachBoss(initial())
    val boss = TestMilestoneTurn.accept(beforeBoss, JSONArray().put("boss").toString())
    val encounterId = CombatRuntime.active(boss)!!.encounterId
    val ready = StoryMilestonePlan.prepareNextDuringCombat(boss, plan("CP-L00-02"))
    val defeated = CombatRuntime.resolve(ready.copy(metadata = ready.metadata +
      ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY observe")
    assertTrue(defeated.entityDestroyed)
    val completed = StoryMilestoneRuntime.afterCombat(
      CoreClientProjection.advance(defeated.state), encounterId, true, false)
    val history = JSONArray(completed.world.getValue("story.checkpointHistory"))
    val cp = history.getJSONObject(0)
    val events = cp.getJSONArray("events")
    assertEquals(cp.getJSONArray("eventRefs").length(), events.length())
    assertEquals("Cao Minh nhìn thấy căn phòng màu vàng",
      events.getJSONObject(0).getString("event"))
    assertTrue(StoryMilestoneRuntime.continuityContext(completed).contains("Cao Minh"))
    StoryMilestoneRuntime.validateStoredState(
      GameStateCodec.decode(GameStateCodec.encode(completed)))
    val forged = JSONArray(history.toString())
    forged.getJSONObject(0).getJSONArray("events").getJSONObject(0).put("id", "FAKE")
    assertThrows(Exception::class.java) {
      StoryMilestoneRuntime.validateStoredState(completed.copy(world = completed.world +
        ("story.checkpointHistory" to forged.toString())))
    }
  }
}
