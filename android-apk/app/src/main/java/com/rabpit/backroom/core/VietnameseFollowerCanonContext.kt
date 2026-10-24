package com.rabpit.backroom.core

import android.content.Context
import com.rabpit.backroom.core.canon.CanonRecord
import com.rabpit.backroom.core.canon.CanonTokens
import com.rabpit.backroom.core.canon.KnowledgeVisibility
import com.rabpit.backroom.core.canon.SpoilerPolicy
import org.json.JSONObject
import java.util.Locale

/** Same bounded, scene-selected source cards for both CR2 ON and OFF. No network/model calls. */
object VietnameseFollowerCanonContext {
  private const val MAX_PACKET_TOKENS = 1400
  private val actors = linkedMapOf(
    NGUYEN_QUOC_NAM_ID to "CHAR.NGUYEN_QUOC_NAM",
    HUA_TIEU_LAN_ID to "CHAR.HUA_TIEU_LAN"
  )
  private val rollKeys = mapOf(
    NGUYEN_QUOC_NAM_ID to "nguyenQuocNamEncounter",
    HUA_TIEU_LAN_ID to "huaTieuLanEncounter"
  )
  @Volatile private var cachedRecords: List<CanonRecord>? = null

  @JvmStatic fun build(context: Context, stateJson: String, action: String, rollsJson: String): String {
    val records = cachedRecords ?: synchronized(this) {
      cachedRecords ?: context.applicationContext.assets.open("knowledge/knowledge_db.json")
        .bufferedReader(Charsets.UTF_8).use { recordsFromJson(it.readText()) }
        .also { cachedRecords = it }
    }
    return compile(records, JSONObject(stateJson), action, JSONObject(rollsJson))
  }

  internal fun buildFromJson(database: String, stateJson: String, action: String, rollsJson: String): String =
    compile(recordsFromJson(database), JSONObject(stateJson), action, JSONObject(rollsJson))

  private fun recordsFromJson(database: String): List<CanonRecord> =
    CanonRecord.parseAll(JSONObject(database)).filter { it.entityId in actors.values }

  /** Only exact IDs in Core projections or the current locked preview confer presence. */
  @JvmStatic fun presentActors(state: JSONObject, rolls: JSONObject): Set<String> {
    val out = linkedSetOf<String>()
    fun visit(array: org.json.JSONArray?) {
      if (array == null) return
      for (index in 0 until array.length()) {
        val member = array.optJSONObject(index) ?: continue
        val id = member.optString("id")
        if (id in actors && member.optString("presence") == "ACTIVE") out += id
      }
    }
    val projection = state.optJSONObject("partyDetails")?.optJSONArray("members")
    if (projection != null) visit(projection) else visit(state.optJSONArray("party"))
    visit(state.optJSONArray("followerEncounters"))
    actors.keys.filter { firstEncounter(state, rolls, it) }.forEach { out += it }
    return out
  }

  private fun firstEncounter(state: JSONObject, rolls: JSONObject, id: String): Boolean {
    if (!rolls.has("turn") || rolls.optInt("turn", -1) != state.optInt("turn", 1)) return false
    val roll = rolls.optJSONObject(rollKeys.getValue(id)) ?: return false
    return roll.optBoolean("eligible", false) && roll.optBoolean("success", false)
  }

  /** Intent tags select categories, never prove that an actor exists or uses an ability. */
  @JvmStatic fun sceneIntents(state: JSONObject, action: String, rolls: JSONObject): Set<String> {
    val text = action.lowercase(Locale.ROOT)
    val tags = linkedSetOf<String>()
    fun tag(category: String, vararg terms: String) {
      if (terms.any(text::contains)) tags += "follower_$category"
    }
    tag("persona", "hỏi", "nói", "trò chuyện", "chào", "tâm sự", "xưng hô")
    tag("visual", "nhìn", "ngoại hình", "trang phục", "diện mạo", "tóc", "mặc")
    tag("history", "quá khứ", "trước khi", "hồi ức", "tâm sự", "anh tý", "kiếp", "chuyện cũ")
    tag("family", "gia đình", "cha", "mẹ", "vợ", "con gái", "anh trai", "hứa gia")
    tag("relationship", "tình cảm", "yêu", "lục trầm", "từ chối", "đồng hành", "đi cùng", "mời")
    tag("equipment", "vũ khí", "súng", "trang bị", "đạn", "dao", "đồng hồ", "vòng")
    tag("combat", "tấn công", "phòng thủ", "né tránh", "bắn", "giao chiến", "chiến đấu", "quan sát", "read", "attack", "guard", "evade")
    actors.keys.filter { firstEncounter(state, rolls, it) }.forEach {
      tags += "first_encounter_$it"
      tags += "follower_visual"
    }
    return tags
  }

  private fun compile(records: List<CanonRecord>, state: JSONObject, action: String, rolls: JSONObject): String {
    val present = presentActors(state, rolls)
    if (present.isEmpty()) return ""
    val entities = present.mapNotNull(actors::get).toSet()
    val tags = sceneIntents(state, action, rolls)
    val eligible = records.filter {
      it.entityId in entities && it.spoilerPolicy != SpoilerPolicy.NEVER_SHOW &&
        it.knowledgeVisibility != KnowledgeVisibility.RESTRICTED &&
        (it.activation.anySceneIntent.isEmpty() || it.activation.anySceneIntent.any(tags::contains))
    }
    val required = eligible.filter { record ->
      record.activation.anySceneIntent.isEmpty() ||
        record.activation.anySceneIntent.any { it.startsWith("first_encounter_") && it in tags }
    }.sortedBy { it.id }
    val expected = present.flatMap { actor ->
      val entity = actors.getValue(actor)
      listOf("$entity.RUNTIME_CORE", "$entity.LIMITS") +
        if (actor == HUA_TIEU_LAN_ID) listOf("ADDR.HUA_TIEU_LAN.CAO_MINH") else emptyList()
    }
    require(required.map { it.id }.containsAll(expected)) { "Required named follower canon is missing" }
    val header = "[NAMED_FOLLOWER_CANON]\nChỉ dùng thẻ đã chọn. Hồ sơ quá khứ không tự thành tri thức của Cao Minh; chỉ bộc lộ qua quan sát/lời kể hợp lệ. Core quyết định hiện diện, đồng thuận, kỹ năng và kết quả; không kể metadata hoặc tự bổ sung UNKNOWN.\n"
    val out = StringBuilder(header)
    fun line(record: CanonRecord) = "[${record.id}] [${record.truthStatus}] ${record.text}\n"
    required.forEach { out.append(line(it)) }
    val footer = "[END_NAMED_FOLLOWER_CANON]"
    require(CanonTokens.estimate(out.toString() + footer) <= MAX_PACKET_TOKENS) { "Named follower hard canon exceeds packet budget" }
    eligible.filter { it !in required }.sortedWith(compareBy<CanonRecord> { it.priority }.thenBy { it.id }).forEach {
      val whole = line(it)
      if (CanonTokens.estimate(out.toString() + whole + footer) <= MAX_PACKET_TOKENS) out.append(whole)
    }
    return out.append(footer).toString()
  }
}
