package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryContinuityReducerTest {
  @Test fun derivesDurableFactsFromValidatedStateWithoutRawDialogueSummary() {
    val before = JSONObject()
      .put("turn", 7)
      .put("level", JSONObject().put("number", 0))
      .put("party", JSONArray())
      .put("flags", JSONObject()
        .put("iris", JSONObject().put("continuity", "separated"))
        .put("syvial", JSONObject().put("continuity", "separated"))
        .put("entityRegistry", JSONObject()))

    val after = JSONObject(before.toString())
      .put("turn", 8)
      .put("level", JSONObject().put("number", 1))
      .put("party", JSONArray().put(JSONObject().put("id", "iris").put("name", "Iris")))
    after.getJSONObject("flags")
      .put("iris", JSONObject().put("continuity", "reunited"))
      .put("communication", JSONObject().put("blackBlood", "partial"))
      .put("entityRegistry", JSONObject().put("Hound", JSONObject().put("confirmed", true)))

    val rawAction = "Kai says a very long private line that must not become the long-term memory store."
    val reduced = JSONObject(StoryContinuityReducer.apply(before.toString(), after.toString(), rawAction))
    val continuity = reduced.getJSONObject("flags").getJSONObject("storyContinuity")

    val ids = allIds(continuity)
    assertTrue(ids.contains("EVT.8.LEVEL.0.1"))
    assertTrue(ids.contains("EVT.8.PARTY.JOIN.iris"))
    assertTrue(ids.contains("DISC.ENTITY.HOUND"))
    assertTrue(ids.contains("MAIN.REUNITE.SYVIAL"))
    assertTrue(ids.contains("THREAD.REUNITE.SYVIAL"))
    assertFalse(continuity.toString().contains(rawAction))

    val knowledge = continuity.getJSONArray("knowledge")
    val houndKnowledge = (0 until knowledge.length())
      .mapNotNull { knowledge.optJSONObject(it) }
      .first { it.optString("factId") == "DISC.ENTITY.HOUND" }
    val knownBy = houndKnowledge.getJSONArray("knownBy").toString()
    assertTrue(knownBy.contains("kai"))
    assertTrue(knownBy.contains("iris"))
  }

  @Test fun boundedContinuityDoesNotGrowWithWholeCampaignLog() {
    var state = JSONObject()
      .put("turn", 1)
      .put("level", JSONObject().put("number", 0))
      .put("party", JSONArray())
      .put("flags", JSONObject())

    for (turn in 2..30) {
      val before = JSONObject(state.toString())
      val after = JSONObject(state.toString())
        .put("turn", turn)
        .put("level", JSONObject().put("number", turn % 7))
      state = JSONObject(StoryContinuityReducer.apply(before.toString(), after.toString(), "move"))
    }

    val continuity = state.getJSONObject("flags").getJSONObject("storyContinuity")
    assertTrue(continuity.getJSONArray("events").length() <= 12)
    assertTrue(continuity.getJSONArray("discoveries").length() <= 12)
    assertTrue(continuity.getJSONArray("knowledge").length() <= 16)
  }

  private fun allIds(continuity: JSONObject): Set<String> {
    val result = linkedSetOf<String>()
    listOf("events", "discoveries", "objectives", "unresolvedThreads", "knowledge", "relationshipChanges").forEach { key ->
      val array = continuity.optJSONArray(key) ?: return@forEach
      for (i in 0 until array.length()) {
        val id = array.optJSONObject(i)?.optString("id").orEmpty()
        if (id.isNotEmpty()) result += id
      }
    }
    return result
  }
}
