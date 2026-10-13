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

/** TEST_ONLY: on-device Core execution with synthetic evidence, not live AI traffic. */
@RunWith(AndroidJUnit4::class)
class FirstSixCheckpointsEmulatorTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val storage get() = SharedPreferencesSaveRepository(context)

  @After fun cleanup() { storage.clear() }

  private fun cpId(i: Int) = "CP-L00-" + i.toString().padStart(2, '0')

  private fun plan(id: String): String {
    val events = JSONArray().put(JSONObject()
      .put("id", "opening").put("turn", 1).put("kind", "STORY")
      .put("action", "TEST_ONLY enter").put("event", "TEST_ONLY investigate")
      .put("outcome", "TEST_ONLY area mapped"))
    for (i in 1..8) events.put(JSONObject()
      .put("id", "encounter$i").put("turn", i + 1).put("kind", "ENTITY")
      .put("entityKey", "hound")
      .put("action", "TEST_ONLY Hound approaches $i")
      .put("event", "TEST_ONLY direct Hound encounter $i")
      .put("outcome", "TEST_ONLY Hound leaves $i"))
    events.put(JSONObject().put("id", "boss").put("turn", 18)
      .put("kind", "FINAL_ENTITY").put("entityKey", "hound")
      .put("action", "TEST_ONLY Boss arrives").put("event", "TEST_ONLY Boss confrontation")
      .put("outcome", "TEST_ONLY Combat starts")
      .put("hpMultiplier", 9).put("expMultiplier", 9))
    return JSONObject().put("schemaVersion", 2).put("checkpointId", id)
      .put("events", events).put("completion", JSONObject()
        .put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY CP complete"))
      .toString()
  }

  private fun receipts(events: List<StoryMilestonePlan.Event>): String =
    JSONArray().apply { events.forEach { put(it.id) } }.toString()

  private fun narration(events: List<StoryMilestonePlan.Event>): String =
    "TEST_ONLY narrative " + events.joinToString(" ") {
      it.action + ". " + it.event + ". " + it.outcome + "."
    }

  private fun proof(state: GameState, events: List<StoryMilestonePlan.Event>, narrative: String): String =
    JSONObject().put("verified", true)
      .put("requestToken", StoryMilestoneRuntime.evidenceRequest(state, narrative, "[]")
        .getString("requestToken"))
      .put("events", JSONArray().apply {
        events.forEach { event -> put(JSONObject().put("id", event.id)
          .put("actionQuote", event.action).put("eventQuote", event.event)
          .put("outcomeQuote", event.outcome)) }
      }).put("facts", JSONArray()).toString()

  @Test fun firstSixCheckpointsCompleteOnAndroidCoreAndPersistAcrossTurns() {
    storage.clear()
    val core = GameCoreFacade.create(context)
    try {
      val first = JSONObject(core.milestonePlanRequest())
      assertTrue(first.toString(), first.getBoolean("needed"))
      assertTrue(core.installMilestonePlan(plan(cpId(1)), first.getString("runToken"), cpId(1), 1))
      for (cp in 1..6) {
        var state = storage.load()
        assertEquals(cpId(cp), StoryCheckpointCore.current(state)!!.id)
        val start = CoreClientProjection.turnIndex(state)
        assertEquals(8L, StoryMilestonePlan.minimumEntityEncounters(18))
        for (turn in 1..18) {
          state = storage.load()
          assertEquals(start + turn - 1, CoreClientProjection.turnIndex(state))
          val due = StoryMilestoneRuntime.due(state)
          if (turn == 18) {
            assertEquals(1, due.size)
            assertEquals(StoryMilestonePlan.Kind.FINAL_ENTITY, due.single().kind)
          }
          val words = narration(due)
          val before = core.currentClientState("{}")
          val result = JSONObject(core.processValidatedCandidate(before, before, "TEST_ONLY explore",
            receipts(due), words, proof(state, due, words), "[]"))
          assertTrue("CP $cp Turn $turn: $result", result.getBoolean("handled"))
          state = storage.load()
          assertEquals(start + turn, CoreClientProjection.turnIndex(state))
          assertEquals(cpId(cp), StoryCheckpointCore.current(state)!!.id)
          StoryMilestoneRuntime.validateStoredState(state)
          if (turn < 18) assertNull(CombatRuntime.active(state))
        }
        assertEquals(8, StoryMilestoneRuntime.entityEncounterCount(state))
        val fight = CombatRuntime.active(state)!!
        val explorerTurn = CoreClientProjection.turnIndex(state)
        val lease = JSONObject(core.milestoneNextPlanRequest())
        assertTrue(lease.toString(), lease.getBoolean("needed"))
        assertEquals(cpId(cp + 1), lease.getString("nextCheckpointId"))
        if (cp % 2 == 1) assertTrue(core.installNextMilestone(
          plan(cpId(cp + 1)), lease.getString("runToken"), cpId(cp), fight.encounterId))

        // Synthetic instant victory only for testing. Real Combat Core awards EXP and ends Boss.
        val dice = PokerDiceCore.finish(PokerDiceCore.prepare(storage.load(), "quan sát", fight.encounterId))
        storage.save(dice.copy(metadata = dice.metadata + ("combat.entityHp" to "0")))
        val battle = JSONObject(core.processCombat(JSONObject().put("turn", explorerTurn).toString(),
          "SEARCH", "quan sát"))
        assertTrue("CP $cp Boss: $battle", battle.getBoolean("handled"))
        assertEquals("combat_entity_destroyed", battle.getString("reason"))
        assertEquals(explorerTurn, CoreClientProjection.turnIndex(storage.load()))
        assertNull(CombatRuntime.active(storage.load()))

        if (cp % 2 == 0) {
          assertTrue(StoryMilestoneRuntime.awaitingPlan(storage.load()))
          assertTrue(core.installNextMilestone(
            plan(cpId(cp + 1)), lease.getString("runToken"), cpId(cp), fight.encounterId))
        }
        state = storage.load()
        assertEquals(cpId(cp + 1), StoryCheckpointCore.current(state)!!.id)
        assertEquals(explorerTurn, CoreClientProjection.turnIndex(state))
        assertEquals(cp.toLong(), StoryMilestoneRuntime.statsJson(state).getLong("completedCheckpoints"))
        val history = JSONArray(state.world.getValue("story.checkpointHistory"))
        assertEquals(cp, history.length())
        val completed = history.getJSONObject(cp - 1)
        assertEquals(18L, completed.getLong("turnCount"))
        assertEquals(8, completed.getInt("entityEncounterCount"))
        assertEquals(9, completed.getJSONArray("entityEncounterRefs").length())
        assertEquals(fight.encounterId, completed.getString("bossEncounterId"))
        assertEquals(start.toLong() + 17L, completed.getLong("endTurn"))
        assertEquals("DEFEATED", completed.getString("bossResult"))
        assertNotNull(state.metadata["exp.awarded." + fight.encounterId])
        val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
        StoryMilestoneRuntime.validateStoredState(decoded)
        storage.save(decoded)
        assertEquals(cp + 1, JSONObject(core.currentClientState("{}"))
          .getJSONObject("story").getInt("generatedMilestones"))
      }
    } finally { core.close() }
  }

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
