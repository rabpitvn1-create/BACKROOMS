package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SolMilestoneResponseTest {
  private fun response(content: Any?, finishReason: String = "stop"): String =
    JSONObject().put("choices", JSONArray().put(JSONObject()
      .put("finish_reason", finishReason)
      .put("message", JSONObject().put("content", content)))).toString()

  @Test fun acceptsPlainAndFencedMilestoneJsonAndPreservesFields() {
    val plan = """{"schemaVersion":2,"checkpointId":"CP-L00-01","events":[],"completion":{}}"""
    val variants = listOf(response(plan), response(" ```json\n$plan\n``` "),
      response(JSONArray().put(JSONObject().put("type", "text").put("text", plan))))
    variants.forEach { payload ->
      val parsed = JSONObject(SolMilestoneResponse.normalize(payload))
      assertEquals(2, parsed.getInt("schemaVersion"))
      assertEquals("CP-L00-01", parsed.getString("checkpointId"))
      assertTrue(parsed.has("events"))
      assertTrue(parsed.has("completion"))
    }
  }

  @Test fun refusesTruncatedOrNonJsonResponsesBeforeWritingToCore() {
    val plan = """{"schemaVersion":2}"""
    listOf("length", "max_tokens").forEach { end ->
      val error = assertThrows(IllegalArgumentException::class.java) {
        SolMilestoneResponse.normalize(response(plan, end))
      }
      assertEquals("milestone_provider_truncated", error.message)
    }
    assertEquals("milestone_provider_json_invalid",
      assertThrows(IllegalArgumentException::class.java) {
        SolMilestoneResponse.normalize(response("Chưa có kế hoạch."))
      }.message)
    assertEquals("milestone_provider_choices_missing",
      assertThrows(IllegalArgumentException::class.java) {
        SolMilestoneResponse.normalize("""{"choices":[]}""")
      }.message)
  }
}
