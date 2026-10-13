package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** Normalize a SOL chat-completions response without weakening the Core Milestone schema. */
object SolMilestoneResponse {
  @JvmStatic
  fun normalize(envelope: String): String {
    val packet = try {
      JSONObject(envelope)
    } catch (_: Exception) {
      throw IllegalArgumentException("milestone_provider_envelope_invalid")
    }
    val choice = packet.optJSONArray("choices")?.optJSONObject(0)
      ?: throw IllegalArgumentException("milestone_provider_choices_missing")
    when (choice.optString("finish_reason", "").lowercase()) {
      "length", "max_tokens", "max_output_tokens" ->
        throw IllegalArgumentException("milestone_provider_truncated")
      "content_filter" -> throw IllegalArgumentException("milestone_provider_filtered")
    }
    val content = choice.optJSONObject("message")?.opt("content")
      ?: throw IllegalArgumentException("milestone_provider_content_missing")
    val output = when (content) {
      is String -> content
      is JSONArray -> (0 until content.length()).mapNotNull { index ->
        content.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
      }.joinToString("\n")
      else -> throw IllegalArgumentException("milestone_provider_content_invalid")
    }.trim()
    val open = output.indexOf('{')
    val close = output.lastIndexOf('}')
    if (open < 0 || close <= open)
      throw IllegalArgumentException("milestone_provider_json_invalid")
    return try {
      JSONObject(output.substring(open, close + 1)).toString()
    } catch (_: Exception) {
      throw IllegalArgumentException("milestone_provider_json_invalid")
    }
  }
}
