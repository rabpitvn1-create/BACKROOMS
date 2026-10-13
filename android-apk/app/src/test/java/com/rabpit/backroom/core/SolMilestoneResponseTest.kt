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
    assertEquals(plan, SolMilestoneResponse.normalize(response(plan)))
    assertEquals(plan, SolMilestoneResponse.normalize(response(" ```json\n$plan\n``` ")))
    assertEquals(plan, SolMilestoneResponse.normalize(response(JSONArray().put(
      JSONObject().put("type", "text").put("text", plan)))))
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
