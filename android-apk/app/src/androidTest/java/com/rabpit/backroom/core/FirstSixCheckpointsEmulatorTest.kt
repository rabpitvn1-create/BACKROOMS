package com.rabpit.backroom.core

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rabpit.backroom.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** TEST_ONLY: six Checkpoints, mandatory real Combat transitions and persisted history; no live AI. */
@RunWith(AndroidJUnit4::class)
class FirstSixCheckpointsEmulatorTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val storage get() = SharedPreferencesSaveRepository(context)

  @After fun cleanup() { storage.clear() }

  private fun cpId(i: Int) = "CP-L00-" + i.toString().padStart(2, '0')

  private fun storyArc(variant: Int): String = if (variant == 1)
    ("Cao Minh lần theo ký ức ở Lôi Thiên Sơn và dấu vết của Lục Trầm giữa Backrooms. " +
      "Hắn gặp Lucia trong một căn phòng tối; cô cảnh giác trước sức mạnh của hắn. " +
      "Những cuộc đối thoại và chạm trán hé lộ một lối đi bị che giấu. ").repeat(3).take(500).trim()
    else
    ("Cao Minh khám phá hành lang có ánh sáng kỳ lạ và tìm kiếm lời giải cho tiếng động từ các cửa phòng. " +
      "Iris xuất hiện với một thông điệp bí ẩn về những người mất tích. " +
      "Những cuộc đụng độ mới dẫn hắn tới một manh mối khác. ").repeat(3).take(500).trim()

  private fun plan(id: String, variant: Int = 1): String {
    val events = JSONArray().put(JSONObject()
      .put("id", "opening").put("turn", 1).put("kind", "STORY")
      .put("action", "TEST_ONLY arrive").put("event", "TEST_ONLY chapter opens")
      .put("outcome", "TEST_ONLY investigation begins"))
    for (i in 1..20) events.put(JSONObject()
      .put("id", "encounter$i").put("turn", i + 1).put("kind", "ENTITY")
      .put("entityKey", (if (variant == 1) listOf("hound", "duller", "clump", "smiler")
        else listOf("smiler", "clump", "duller", "hound"))[(i - 1) % 4])
      .put("action", "TEST_ONLY Entity approaches $i")
      .put("event", "TEST_ONLY mandatory Hound Combat $i")
      .put("outcome", "TEST_ONLY await Battle Start $i"))
    events.put(JSONObject().put("id", "meeting").put("turn", 23)
      .put("kind", "FOLLOWER").put("characterId", if (variant == 1) LUCIA_ID else IRIS_ID)
      .put("action", "Người lạ tiếp cận Cao Minh")
      .put("event", "Cuộc gặp Follower của chương")
      .put("outcome", "Giao tiếp nhưng chưa gia nhập Party"))
    events.put(JSONObject().put("id", "conversation").put("turn", 24)
      .put("kind", "STORY").put("action", "Trao đổi manh mối")
      .put("event", "Biến chuyển quan hệ dựa trên cuộc gặp")
      .put("outcome", "Thông tin mới được xác nhận"))
    events.put(JSONObject().put("id", "boss").put("turn", 50)
      .put("kind", "FINAL_ENTITY").put("entityKey", if (variant == 1) "hound" else "smiler")
      .put("action", "TEST_ONLY Boss arrives").put("event", "TEST_ONLY Boss confrontation")
      .put("outcome", "TEST_ONLY mandatory Boss Combat")
      .put("hpMultiplier", 9).put("expMultiplier", 9))

    return JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION)
      .put("checkpointId", id).put("storyArc", storyArc(variant))
      .put("events", events).put("completion", JSONObject()
        .put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY CP complete"))
      .toString()
  }

  private fun narration(events: List<StoryMilestonePlan.Event>): String =
    "TEST_ONLY narrative " + events.joinToString(" ") {
      it.action + ". " + it.event + ". " + it.outcome + "."
    }

  /** An actual CombatRuntime battle, with test-only instant HP depletion to keep six chapters fast. */
  private fun resolveEncounter(core: GameCoreFacade, boss: Boolean): String {
    val pending = requireNotNull(CombatRuntime.pending(storage.load()))
    val view = core.currentClientState("{}")
    assertTrue(JSONObject(core.confirmEncounter(view, pending.encounterId)).getBoolean("handled"))
    val active = requireNotNull(CombatRuntime.active(storage.load()))
    assertEquals(pending.encounterId, active.encounterId)
    val outcome = CombatRuntime.resolve(storage.load().copy(metadata = storage.load().metadata +
      ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY defeat")
    assertTrue(outcome.entityDestroyed)
    val resolved = StoryMilestoneRuntime.afterCombat(outcome.state, active.encounterId,
      outcome.entityDestroyed, outcome.escaped)
    storage.save(resolved)
    assertNull(CombatRuntime.active(storage.load()))
    if (!boss) assertNull(CombatRuntime.pending(storage.load()))
    return active.encounterId
  }

  private fun runOneCheckpoint(variant: Int) {
    storage.clear()
    val core = GameCoreFacade.create(context)
    try {
      val first = JSONObject(core.milestonePlanRequest())
      assertTrue(first.getBoolean("needed"))
      assertTrue(core.installMilestonePlan(plan(cpId(1), variant), first.getString("runToken"), cpId(1), 1))
      for (cp in 1..1) {
        var state = storage.load()
        assertEquals(cpId(cp), StoryCheckpointCore.current(state)!!.id)
        val start = CoreClientProjection.turnIndex(state)
        assertEquals(20L, StoryMilestonePlan.minimumEntityEncounters(50))
        for (turn in 1..50) {
          state = storage.load()
          assertEquals(start + turn - 1, CoreClientProjection.turnIndex(state))
          val due = StoryMilestoneRuntime.due(state)
          if (turn == 50) assertEquals(StoryMilestonePlan.Kind.FINAL_ENTITY, due.single().kind)
          val before = core.currentClientState("{}")
          val result = JSONObject(core.processValidatedCandidate(before, before, "Khám Phá", narration(due)))
          assertTrue("CP $cp Turn $turn: $result", result.getBoolean("handled"))
          state = storage.load()
          assertEquals(start + turn, CoreClientProjection.turnIndex(state))
          StoryMilestoneRuntime.validateStoredState(state)
          if (turn in 2..21) {
            val encounter = requireNotNull(CombatRuntime.pending(state))
            val event = StoryMilestonePlan.current(state)!!.plan.events.single {
              it.kind == StoryMilestonePlan.Kind.ENTITY && it.turn == turn
            }
            assertEquals(event.entityKey, encounter.entityKey)
            resolveEncounter(core, boss = false)
            assertEquals(start + turn, CoreClientProjection.turnIndex(storage.load()))
          } else if (turn == 23) {
            val follower = if (variant == 1) LUCIA_ID else IRIS_ID
            assertEquals("true", state.characters.getValue(follower).metadata["encountered"])
            assertFalse(follower in state.party.memberIds)
          } else if (turn < 50) {
            assertNull(CombatRuntime.pending(state))
            assertNull(CombatRuntime.active(state))
          }
        }
        assertEquals(20, StoryMilestoneRuntime.entityEncounterCount(storage.load()))
        val pendingBoss = requireNotNull(CombatRuntime.pending(storage.load()))
        val currentView = core.currentClientState("{}")
        assertTrue(JSONObject(core.confirmEncounter(currentView, pendingBoss.encounterId)).getBoolean("handled"))
        val lease = JSONObject(core.milestoneNextPlanRequest())
        assertTrue(lease.getBoolean("needed"))
        assertEquals(cpId(cp + 1), lease.getString("nextCheckpointId"))
        if (cp % 2 == 1) assertTrue(core.installNextMilestone(
          plan(cpId(cp + 1), variant), lease.getString("runToken"), cpId(cp), pendingBoss.encounterId))
        val active = requireNotNull(CombatRuntime.active(storage.load()))
        val outcome = CombatRuntime.resolve(storage.load().copy(metadata = storage.load().metadata +
          ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY defeat Boss")
        assertTrue(outcome.entityDestroyed)
        storage.save(StoryMilestoneRuntime.afterCombat(outcome.state, active.encounterId,
          outcome.entityDestroyed, outcome.escaped))
        if (cp % 2 == 0) {
          assertTrue(StoryMilestoneRuntime.awaitingPlan(storage.load()))
          assertTrue(core.installNextMilestone(
            plan(cpId(cp + 1), variant), lease.getString("runToken"), cpId(cp), pendingBoss.encounterId))
        }
        state = storage.load()
        assertEquals(cpId(cp + 1), StoryCheckpointCore.current(state)!!.id)
        val history = JSONArray(state.world.getValue("story.checkpointHistory"))
        assertEquals(cp, history.length())
        val completed = history.getJSONObject(cp - 1)
        assertEquals(50L, completed.getLong("turnCount"))
        assertEquals(20, completed.getInt("entityEncounterCount"))
        assertEquals(21, completed.getJSONArray("entityEncounterRefs").length())
        assertEquals(pendingBoss.encounterId, completed.getString("bossEncounterId"))
        assertEquals("DEFEATED", completed.getString("bossResult"))
        assertEquals(storyArc(variant), completed.getString("storyArc"))
        assertEquals("true", state.characters.getValue(if (variant == 1) LUCIA_ID else IRIS_ID).metadata["encountered"])
        assertEquals(4, StoryMilestonePlan.parse(plan(cpId(1), variant), StoryCheckpointCore.Position(0, 1, 20))
          .events.filter { it.kind == StoryMilestonePlan.Kind.ENTITY }.map { it.entityKey }.toSet().size)
        StoryMilestoneRuntime.validateStoredState(GameStateCodec.decode(GameStateCodec.encode(state)))
      }
    } finally { core.close() }
  }

  @Test fun firstCheckpointWithLuciaAndMixedEntityCombats() = runOneCheckpoint(1)
  @Test fun repeatCheckpointWithIrisAndDifferentStoryArc() = runOneCheckpoint(2)

  @Test fun actualMainActivityLaunchesOnEmulator() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val activity = instrumentation.startActivitySync(
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    try {
      assertNotNull(activity)
      assertEquals(MainActivity::class.java, activity.javaClass)
    } finally { instrumentation.runOnMainSync { activity.finish() } }
  }
}
