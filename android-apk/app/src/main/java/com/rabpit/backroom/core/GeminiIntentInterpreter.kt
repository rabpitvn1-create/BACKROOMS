package com.rabpit.backroom.core

import org.json.JSONObject

fun interface StructuredIntentClient {
  suspend fun classify(prompt: String): String
}

class GeminiIntentInterpreter(
  private val client: StructuredIntentClient,
  private val minimumScore: Float = .75f
) : IntentInterpreter {
  override suspend fun interpret(input: String, context: GameContext): IntentResult {
    val prompt = """
      Classify exactly one Vietnamese BACKROOMS player clause. Return JSON only:
      {"intent":"ONE_ENUM","confidence":0.0,"isRequestedAction":true}
      Allowed intent enum: ${GameIntent.entries.joinToString(",") { it.name }}.
      Do not infer item ownership, party consent, status consequence, HP, canon, quantity or mutate state.
      Description, memory, hypothetical, negation, observation and quoted speech are NO_ACTION.
      If unclear use UNKNOWN. Clause: ${JSONObject.quote(input)}
    """.trimIndent()
    return try {
      val json = strictObject(client.classify(prompt))
      val requested = json.optBoolean("isRequestedAction", false)
      val parsed = runCatching { GameIntent.valueOf(json.optString("intent")) }.getOrDefault(GameIntent.UNKNOWN)
      val intent = if (requested) parsed else GameIntent.NO_ACTION
      val score = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
      val confidence = when {
        intent == GameIntent.NO_ACTION && score >= minimumScore -> IntentConfidence.HIGH
        score >= .88f -> IntentConfidence.HIGH
        score >= minimumScore -> IntentConfidence.MEDIUM
        else -> IntentConfidence.LOW
      }
      IntentResult(listOf(IntentCandidate(input, intent, confidence, score, CommandSource.GEMINI)), confidence != IntentConfidence.HIGH)
    } catch (error: Exception) {
      IntentResult(listOf(IntentCandidate(input, GameIntent.UNKNOWN, IntentConfidence.LOW, 0f, CommandSource.GEMINI, error.message ?: "gemini_intent_error")), true)
    }
  }

  internal fun strictObject(raw: String): JSONObject {
    val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    require(text.startsWith('{') && text.endsWith('}')) { "gemini_intent_invalid_json" }
    val json = JSONObject(text)
    require(json.has("intent") && json.has("confidence") && json.has("isRequestedAction")) { "gemini_intent_schema_missing" }
    return json
  }
}
