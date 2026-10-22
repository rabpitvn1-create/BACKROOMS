package com.rabpit.backroom.core

/** Parse simple counted forward steps without AI or invented graph transitions. */
object PlayerFootstepIntent {
  private val pattern = Regex(
    """^\s*(?:(?:cao\s+minh|tôi)\s+)?(?:tiến|đi)\s+(?:(?:lên|thêm|về\s+phía\s+trước)\s+)?(\d{1,2}|mười\s+hai|mười\s+một|mười|một|hai|ba|bốn|năm|sáu|bảy|tám|chín)\s+bước(?:\s+chân)?\s*[.!?]?\s*$""",
    RegexOption.IGNORE_CASE
  )
  private val words = mapOf(
    "một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4,
    "năm" to 5, "sáu" to 6, "bảy" to 7, "tám" to 8,
    "chín" to 9, "mười" to 10, "mười một" to 11, "mười hai" to 12
  )

  @JvmStatic fun count(raw: String): Int {
    val token = pattern.matchEntire(raw)?.groupValues?.get(1)?.lowercase()
      ?.replace(Regex("""\s+"""), " ") ?: return 0
    val value = token.toIntOrNull() ?: words[token] ?: return 0
    require(value in 1..12) { "player_footstep_count" }
    return value
  }
}
