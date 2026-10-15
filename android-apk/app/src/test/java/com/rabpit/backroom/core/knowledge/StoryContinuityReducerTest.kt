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
      .put("party", JSONArray().put(JSONObject()
        .put("id", "iris").put("name", "Iris").put("presence", "ACTIVE")))
    after.getJSONObject("flags")
      .put("iris", JSONObject().put("continuity", "reunited"))
      .put("communication", JSONObject().put("blackBlood", "partial"))
      .put("entityRegistry", JSONObject().put("Hound", JSONObject().put("confirmed", true)))

    val rawAction = "Cao Minh says a very long private line that must not become the long-term memory store."
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
    assertTrue(knownBy.contains("cao_minh"))
    // Joining in the same turn does not prove Iris witnessed the discovery.
    assertFalse(knownBy.contains("iris"))
  }

  @Test fun activePartyCompanionDoesNotAutomaticallyWitnessDiscovery() {
    val party = JSONArray().put(JSONObject()
      .put("id", "iris").put("name", "Iris").put("presence", "ACTIVE"))
    val before = JSONObject().put("turn", 4)
      .put("party", JSONArray(party.toString()))
      .put("flags", JSONObject().put("entityRegistry", JSONObject()))
    val after = JSONObject(before.toString())
      .put("turn", 5)
    after.getJSONObject("flags").put("entityRegistry",
      JSONObject().put("Hound", JSONObject().put("confirmed", true)))

    val continuity = JSONObject(StoryContinuityReducer.apply(
      before.toString(), after.toString(), "khám phá"))
      .getJSONObject("flags").getJSONObject("storyContinuity")
    val known = continuity.getJSONArray("knowledge").getJSONObject(0)
      .getJSONArray("knownBy").toString()
    assertTrue(known.contains("cao_minh"))
    // Core has not supplied an explicit witness for this discovery.
    assertFalse(known.contains("iris"))
  }

  @Test fun absentOrUnverifiedPartyMembersNeverGainKnowledgeAutomatically() {
    val cases = listOf("SEPARATED", "MISSING", "DEAD", "")
    for (presence in cases) {
      val member = JSONObject().put("id", "iris").put("name", "Iris")
      if (presence.isNotBlank()) member.put("presence", presence)
      val before = JSONObject().put("turn", 4)
        .put("party", JSONArray().put(JSONObject(member.toString())))
        .put("flags", JSONObject().put("entityRegistry", JSONObject()))
      val after = JSONObject(before.toString()).put("turn", 5)
      after.getJSONObject("flags").put("entityRegistry",
        JSONObject().put("Hound", JSONObject().put("confirmed", true)))
      val result = JSONObject(StoryContinuityReducer.apply(
        before.toString(), after.toString(), "khám phá"))
      val knowledge = result.getJSONObject("flags")
        .getJSONObject("storyContinuity").getJSONArray("knowledge")
        .getJSONObject(0).getJSONArray("knownBy").toString()
      assertTrue(knowledge, knowledge.contains("cao_minh"))
      assertFalse("Unconfirmed companion presence=$presence must not know",
        knowledge.contains("iris"))
    }
  }

  @Test fun newlyArrivedOrDepartedPartyMemberDoesNotLearnPastEvent() {
    fun reduce(partyBefore: JSONArray, partyAfter: JSONArray): String {
      val before = JSONObject().put("turn", 2).put("party", partyBefore)
        .put("flags", JSONObject().put("entityRegistry", JSONObject()))
      val after = JSONObject(before.toString()).put("turn", 3)
        .put("party", partyAfter)
      after.getJSONObject("flags").put("entityRegistry",
        JSONObject().put("Hound", JSONObject().put("confirmed", true)))
      val result = JSONObject(StoryContinuityReducer.apply(before.toString(),
        after.toString(), "khám phá"))
      return result.getJSONObject("flags").getJSONObject("storyContinuity")
        .getJSONArray("knowledge").getJSONObject(0).getJSONArray("knownBy").toString()
    }
    val iris = JSONObject().put("id", "iris").put("presence", "ACTIVE")
    val party = JSONArray().put(iris)
    assertFalse(reduce(JSONArray(), party).contains("iris"))
    assertFalse(reduce(party, JSONArray()).contains("iris"))
    // Bare string Party entries provide no Core presence evidence.
    assertFalse(reduce(JSONArray().put("iris"), JSONArray().put("iris")).contains("iris"))
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

  @Test fun recordsNamedAreaTransitionsInsideOneParentLevel() {
    val before = JSONObject()
      .put("turn", 4)
      .put("level", JSONObject().put("id", "0.7").put("number", 0))
      .put("party", JSONArray())
      .put("flags", JSONObject())
    val after = JSONObject(before.toString())
      .put("turn", 5)
      .put("level", JSONObject().put("id", "manila_room").put("number", 0))
    val result = JSONObject(StoryContinuityReducer.apply(before.toString(), after.toString(), "bước vào phòng"))
    val continuity = result.getJSONObject("flags").getJSONObject("storyContinuity")
    assertTrue(allIds(continuity).contains("EVT.5.LEVEL.0.7.manila_room"))
    assertTrue(allIds(continuity).contains("DISC.LEVEL.manila_room"))
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
