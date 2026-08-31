package com.rabpit.backroom.core

import java.text.Normalizer

data class SemanticActionDescriptor(val candidateToken: String, val descriptions: Set<String>)

data class SemanticActionMapping(
  val candidateToken: String? = null,
  val confidence: IntentConfidence = IntentConfidence.LOW,
  val score: Float = 0f,
  val outcome: String = "no_match"
)

/**
 * Deterministic gameplay-only mapper. Its input surface is deliberately limited to player text
 * and author-approved public descriptions: no blueprint, sequence, conditions, effects, evidence,
 * RNG, future Level data or raw model context crosses this boundary.
 */
object SemanticActionMapper {
  private val answerSeeking = Regex(
    "(?:dap an|loi giai|solution|required action|escape blueprint|lam gi de thoat|chi toi cach thoat)",
    RegexOption.IGNORE_CASE
  )
  private val stopWords = setOf("kai", "toi", "se", "hay", "vao", "o", "tai", "cai", "chiec", "mot", "thu", "do", "nay")
  private val canonical = mapOf(
    "tat" to "ngat", "cat" to "ngat", "ngung" to "ngat",
    "nguon" to "dien", "cau" to "dien", "dao" to "dien",
    "tro" to "quay", "lai" to "quay",
    "am" to "tieng", "thanh" to "tieng", "hum" to "tieng",
    "nguoc" to "nguoc",
    "buoc" to "vao", "di" to "vao", "chui" to "vao",
    "elevator" to "thangmay", "thang" to "thangmay", "may" to "thangmay"
  )

  fun resolve(input: String, candidates: Collection<SemanticActionDescriptor>): SemanticActionMapping {
    val normalizedInput = normalize(input)
    if (normalizedInput.isBlank() || answerSeeking.containsMatchIn(normalizedInput)) {
      return SemanticActionMapping(outcome = "unsafe_or_empty")
    }
    val inputTokens = tokens(normalizedInput)
    if (inputTokens.size < 2) return SemanticActionMapping(outcome = "insufficient_signal")

    val scored = candidates.flatMap { candidate ->
      candidate.descriptions.map { description ->
        val descriptorTokens = tokens(normalize(description))
        val overlap = inputTokens.intersect(descriptorTokens).size
        val coverage = if (descriptorTokens.isEmpty()) 0f else overlap.toFloat() / descriptorTokens.size
        Triple(candidate.candidateToken, coverage, overlap)
      }
    }.filter { it.third >= 2 && it.second >= .6f }
      .groupBy { it.first }
      .map { (id, scores) -> id to scores.maxOf { it.second } }
      .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })

    val best = scored.firstOrNull() ?: return SemanticActionMapping(outcome = "no_match")
    val runnerUp = scored.getOrNull(1)
    if (runnerUp != null && best.second - runnerUp.second < .15f) {
      return SemanticActionMapping(score = best.second, outcome = "ambiguous")
    }
    val confidence = if (best.second >= .8f) IntentConfidence.HIGH else IntentConfidence.MEDIUM
    return SemanticActionMapping(best.first, confidence, best.second, "matched")
  }

  private fun tokens(value: String): Set<String> = value.split(' ')
    .filter { it.length >= 2 && it !in stopWords }
    .map { canonical[it] ?: it }
    .toSet()

  private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace('đ', 'd')
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
}
