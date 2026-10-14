package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structural acknowledgment is separate from rendered event content. Each due event
 * supplies an occurrence and a consequence; character encounters additionally need
 * communication by the scheduled character. These parts ARE the displayed prose,
 * not receipts claiming something exists in an unrelated reply.
 *
 * Canon remains the sole voice authority. No name/quotation/dash/phrase heuristics.
 * Deterministic checks cannot establish that arbitrary prose fulfills every semantic
 * requirement, or that communication is truthful/Canon-compliant. Mislabelled content
 * remains a model failure and needs inspection during live acceptance.
 */
object MilestoneNarrationValidator {
  class MilestoneNarrationException(message: String) : Exception(message)
  data class Validated(val reply: String, val events: JSONArray)

  private fun fail(code: String): Nothing = throw MilestoneNarrationException(code)
  private fun text(value: JSONObject, key: String): String {
    val raw = value.opt(key)
    if (raw !is String || raw.isBlank() || raw.length > 6000 || raw.contains('\u0000')) fail("milestone_narrative_content_missing")
    return raw.trim()
  }

  @JvmStatic
  @Throws(MilestoneNarrationException::class)
  fun requireAcknowledgment(completedEventIds: JSONArray?, dueIds: List<String>) {
    if (completedEventIds == null || completedEventIds.length() != dueIds.size)
      fail("milestone_narrative_events_missing")
    val seen = HashSet<String>()
    for (i in dueIds.indices) {
      val acknowledged = completedEventIds.opt(i)
      if (acknowledged !is String || !seen.add(acknowledged) || acknowledged != dueIds[i])
        fail("milestone_narrative_event_mismatch")
    }
  }

  @JvmStatic
  fun dueIds(due: JSONArray): List<String> =
    (0 until due.length()).map { due.getJSONObject(it).getString("id") }

  /** All scenes are checked before rendering/committing any part of the turn. */
  @JvmStatic
  @Throws(MilestoneNarrationException::class)
  fun validate(generated: JSONObject, due: JSONArray): Validated {
    requireAcknowledgment(generated.optJSONArray("completedEventIds"), dueIds(due))
    val scenes = generated.optJSONArray("eventNarrations")
      ?: fail("milestone_narrative_content_missing")
    if (scenes.length() != due.length()) fail("milestone_narrative_content_missing")
    val rendered = mutableListOf<String>()
    val prelude = generated.opt("reply")
    if (prelude !is String || prelude.length > 12000 || prelude.contains('\u0000')) fail("milestone_narrative_content_missing")
    if (prelude.isNotBlank()) rendered += prelude.trim()
    val accepted = JSONArray()
    for (i in 0 until due.length()) {
      val event = due.getJSONObject(i)
      val scene = scenes.optJSONObject(i) ?: fail("milestone_narrative_content_missing")
      if (scene.opt("eventId") != event.getString("id")) fail("milestone_narrative_event_mismatch")
      val occurrence = text(scene, "occurrence")
      val consequence = text(scene, "consequence")
      val canonical = JSONObject().put("eventId", event.getString("id"))
        .put("occurrence", occurrence).put("consequence", consequence)
      rendered += occurrence
      val speaker = if (event.optString("kind") in setOf("NPC", "FOLLOWER"))
        event.getString("characterId") else event.optString("communicationCharacterId").takeIf { it.isNotBlank() }
      if (speaker != null) {
        val communication = scene.optJSONObject("communication")
          ?: fail("milestone_character_communication_missing")
        if (communication.opt("speakerId") != speaker)
          fail("milestone_character_speaker_mismatch")
        val spoken = text(communication, "text")
        canonical.put("communication", JSONObject()
          .put("speakerId", speaker).put("text", spoken))
        rendered += spoken
      } else if (scene.has("communication")) {
        fail("milestone_character_speaker_mismatch")
      }
      rendered += consequence
      accepted.put(canonical)
    }
    if (rendered.isEmpty()) fail("milestone_narrative_content_missing")
    return Validated(rendered.joinToString("\n\n"), accepted)
  }

  @JvmStatic
  fun prompt(): String = """
    Chỉ trả JSON {"reply":"lời dẫn hoặc diễn biến hành động người chơi, có thể rỗng nếu cảnh event đã đủ","completedEventIds":[],"eventNarrations":[]}.
    completedEventIds và eventNarrations phải khớp TOÀN BỘ event đến hạn theo thứ tự, không thêm/thiếu/trùng ID; không có event thì cả hai mảng rỗng và reply là lời kể đầy đủ.
    Mỗi eventNarrations là {"eventId":"ID đến hạn","occurrence":"kể việc action/event đã thực sự diễn ra, không chỉ nhắc/quan sát nhân vật","consequence":"kể outcome bắt buộc đã được thể hiện"}.
    Với NPC/FOLLOWER hoặc STORY có communicationCharacterId phải thêm "communication":{"speakerId":"characterId hoặc communicationCharacterId của event","text":"phần giao tiếp thực sự của nhân vật, bao gồm thông tin/manh mối cần tiết lộ theo event/outcome"}.
    Ứng dụng HIỂN THỊ lần lượt reply rồi occurrence, communication.text (nếu có), consequence cho từng event. Viết các phần thành một cảnh tự nhiên, không lặp lại trong reply; đó là lời kể thật, không phải lời xác nhận hay tóm tắt chứng cứ.
    Canon trong KNOWLEDGE PACKET quyết định tính cách, xuất thân, xưng hô, quan hệ và trạng thái; tự do diễn đạt, không bắt buộc tên riêng, dấu ngoặc kép, gạch ngang hoặc mẫu thoại. Không tự đặt lời/ý định vào Cao Minh, không tự tuyển Follower vào Party.
    ENTITY/FINAL_ENTITY chỉ kể chạm trán và thế đối đầu bắt buộc Combat, chưa có đòn đánh/kết quả Combat. Không đưa ID hoặc cấu trúc kỹ thuật vào các phần hiển thị.
  """.trimIndent()
}
