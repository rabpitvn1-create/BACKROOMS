package com.rabpit.backroom.core

import kotlin.math.roundToInt

object TimeCostPolicy {
  private val numberDuration = Regex(
    "(\\d+(?:[.,]\\d+)?)\\s*(phút|phut|minutes?|mins?|giờ|gio|tiếng|tieng|hours?|hrs?)\\b",
    RegexOption.IGNORE_CASE
  )
  private val wordNumbers = linkedMapOf(
    "một" to 1, "mot" to 1,
    "hai" to 2,
    "ba" to 3,
    "bốn" to 4, "bon" to 4,
    "năm" to 5, "nam" to 5,
    "sáu" to 6, "sau" to 6,
    "bảy" to 7, "bay" to 7,
    "tám" to 8, "tam" to 8,
    "chín" to 9, "chin" to 9,
    "mười" to 10, "muoi" to 10
  )
  private val wordDuration = Regex(
    "\\b(${wordNumbers.keys.joinToString("|") { Regex.escape(it) }})\\s*(phút|phut|giờ|gio|tiếng|tieng)\\b",
    RegexOption.IGNORE_CASE
  )

  fun estimateMinutes(action: String): Int {
    val text = action.trim()
    explicitMinutes(text)?.let { return it }
    if (text.isEmpty()) return 1

    return when {
      Regex("\\b(?:ngủ|ngu|sleep|nghỉ|nghi|rest|chờ|cho|đợi|doi|wait)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 30
      Regex("\\b(?:đi|di chuyển|di chuyen|tiến|tien|khám phá|kham pha|tìm đường|tim duong|walk|travel|move|explore)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 10
      Regex("\\b(?:tìm|tim|lục|luc|kiểm tra|kiem tra|quan sát|quan sat|search|inspect|examine)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 5
      Regex("\\b(?:bắn|ban|đánh|danh|né|ne|chạy|chay|attack|shoot|fight|dodge|run)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 1
      Regex("\\b(?:nói|noi|hỏi|hoi|trả lời|tra loi|talk|ask|answer)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 1
      else -> 2
    }
  }

  fun explicitMinutes(action: String): Int? {
    numberDuration.find(action)?.let { match ->
      val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
      val minutes = toMinutes(amount, match.groupValues[2])
      if (minutes > 0) return minutes
    }
    wordDuration.find(action)?.let { match ->
      val amount = wordNumbers[match.groupValues[1].lowercase()] ?: return@let
      val minutes = toMinutes(amount.toDouble(), match.groupValues[2])
      if (minutes > 0) return minutes
    }
    return null
  }

  private fun toMinutes(amount: Double, unit: String): Int {
    if (!amount.isFinite() || amount <= 0.0) return 0
    val isHour = unit.lowercase() in setOf("giờ", "gio", "tiếng", "tieng", "hour", "hours", "hr", "hrs")
    val minutes = if (isHour) amount * 60.0 else amount
    if (minutes > Int.MAX_VALUE.toDouble()) return Int.MAX_VALUE
    return minutes.roundToInt().coerceAtLeast(1)
  }
}
