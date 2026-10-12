package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** TEST_ONLY: a trusted validator response for synthetic Core integration fixtures. */
object TestMilestoneEvidence {
  fun narrative(state: GameState, facts: String = "[]"): String = "TEST_ONLY accepted narrative: " +
    proposalNarrative(facts) + " " + StoryMilestoneRuntime.due(state).joinToString(" ") { "${it.action}. ${it.event}. ${it.outcome}. " + it.storyFacts.joinToString(" ") { spec -> spec.fact } }

  private fun proposalNarrative(facts: String): String {
    val array = JSONArray(facts)
    return (0 until array.length()).joinToString(" ") { array.getJSONObject(it).getString("fact") }
  }

  fun proof(state: GameState, text: String = narrative(state), facts: String = "[]"): String = JSONObject()
    .put("verified", true)
    .put("requestToken", StoryMilestoneRuntime.evidenceRequest(state, text, facts).getString("requestToken"))
    .put("events", JSONArray().apply {
      StoryMilestoneRuntime.due(state).forEach { event -> put(JSONObject().put("id", event.id)
        .put("actionQuote", event.action).put("eventQuote", event.event).put("outcomeQuote", event.outcome)) }
    }).put("facts", JSONArray().apply {
      val request = StoryMilestoneRuntime.evidenceRequest(state, text, facts).getJSONArray("facts")
      for (i in 0 until request.length()) {
        val item = request.getJSONObject(i)
        put(JSONObject().put("sourceId", item.getString("sourceId"))
          .put("quote", item.getJSONObject("claim").getString("fact")))
      }
    }).toString()

  fun accept(state: GameState, receipt: String): GameState =
    StoryMilestoneRuntime.acceptTurn(state, receipt, narrative(state), proof(state))

  fun record(state: GameState, events: List<StoryMilestonePlan.Event>, facts: String): GameState =
    StoryFactLedger.recordTurn(state, events, facts, narrative(state, facts), proof(state, narrative(state, facts), facts))
}


/** TEST_ONLY: quota-compliant schedules and committed turns, never production fallback. */
object TestCheckpointQuota {
  fun events(original: JSONArray): JSONArray {
    val values = (0 until original.length()).map { JSONObject(original.getJSONObject(it).toString()) }.toMutableList()
    values.filter { it.getString("kind") == "FINAL_ENTITY" }.forEach { it.put("turn", 50) }
    for (index in 1..19) values += JSONObject().put("id", "encounter$index").put("turn", 9 + index)
      .put("kind", "ENTITY").put("entityKey", "hound").put("action", "TEST_ONLY Entity approaches $index")
      .put("event", "TEST_ONLY direct Entity encounter $index").put("outcome", "TEST_ONLY Entity moves away $index")
    return JSONArray().apply { values.sortedBy { it.getInt("turn") }.forEach { put(it) } }
  }

  fun reachBoss(initial: GameState): GameState {
    var state = initial
    val active = requireNotNull(StoryMilestonePlan.current(state))
    val target = active.startTurn + active.plan.finalEncounter.turn - 1
    while (CoreClientProjection.turnIndex(state) < target) {
      val ids = JSONArray().apply { StoryMilestoneRuntime.due(state).forEach { put(it.id) } }.toString()
      state = CoreClientProjection.advance(TestMilestoneEvidence.accept(state, ids))
    }
    return state
  }
}
