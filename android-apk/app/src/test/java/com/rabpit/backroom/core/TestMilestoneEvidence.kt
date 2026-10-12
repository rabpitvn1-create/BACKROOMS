package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** TEST_ONLY: a trusted validator response for synthetic Core integration fixtures. */
object TestMilestoneEvidence {
  fun narrative(state: GameState): String = "TEST_ONLY accepted narrative: " +
    StoryMilestoneRuntime.due(state).joinToString(" ") { "${it.action}. ${it.event}. ${it.outcome}." }

  fun proof(state: GameState, text: String = narrative(state)): String = JSONObject()
    .put("verified", true)
    .put("requestToken", StoryMilestoneRuntime.evidenceRequest(state, text).getString("requestToken"))
    .put("events", JSONArray().apply {
      StoryMilestoneRuntime.due(state).forEach { event -> put(JSONObject().put("id", event.id)
        .put("actionQuote", event.action).put("eventQuote", event.event).put("outcomeQuote", event.outcome)) }
    }).put("facts", JSONArray()).toString()

  fun accept(state: GameState, receipt: String): GameState =
    StoryMilestoneRuntime.acceptTurn(state, receipt, narrative(state), proof(state))
}
