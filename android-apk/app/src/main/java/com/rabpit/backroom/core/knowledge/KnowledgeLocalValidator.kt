package com.rabpit.backroom.core.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Deterministic checks for contradictions that code can know without an AI critic.
 * This deliberately stays narrow: ambiguity remains for the conditional semantic critic.
 */
object KnowledgeLocalValidator {
  @JvmStatic
  fun validate(context: Context, stateJson: String, generatedJson: String): String {
    // Force database load so malformed/missing knowledge assets fail before a turn can commit.
    KnowledgeContextEngine.recordIds(context)
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val generated = runCatching { JSONObject(generatedJson) }.getOrElse { JSONObject() }
    val reply = normalize(generated.optString("reply", ""))
    val issues = JSONArray()

    fun issue(rule: String, claim: String, reason: String) {
      issues.put(JSONObject()
        .put("rule", rule)
        .put("severity", "hard")
        .put("claim", claim)
        .put("reason", reason))
    }

    // Code-known immutable capability contradictions.
    if (mentionsAny(reply, "vạn quỷ ma tâm cạn", "ma tâm cạn", "ma tâm hết", "hết ma nguyên", "cạn ma nguyên") && mentionsAny(reply, "cao minh")) {
      issue("competence_suppression", "Cao Minh hết/cạn ma nguyên", "Cao Minh Codex locks Vạn Quỷ Ma Tâm as his Đạo Cơ, Ma Hải and central ma-origin source; do not invent intrinsic depletion of his ma nguyên.")
    }
    if (mentionsAny(reply, "lucifer core cạn", "lucifer core hết", "syvial hết quỷ lực", "syvial cạn quỷ lực")) {
      issue("competence_suppression", "Syvial hết/cạn quỷ lực", "Syvial Codex locks Lucifer Core as an infinite power source without intrinsic depletion.")
    }
    if (mentionsAny(reply, "vạn quỷ ma thân hết thời gian", "vạn quỷ ma thân cooldown", "vạn quỷ ma thân hồi chiêu", "vạn quỷ ma thân phản phệ", "devil trigger hết thời gian", "devil trigger cooldown", "devil trigger hồi chiêu", "devil trigger phản phệ")) {
      issue("ability_overreach", "Invented Vạn Quỷ Ma Thân/Devil Trigger limit", "Current Cao Minh/Syvial codices do not permit an invented intrinsic duration cap/cooldown/backlash.")
    }
    if (mentionsAny(reply, "linh tâm đọc suy nghĩ", "linh tâm đọc được suy nghĩ", "linh tâm biết thiện ác", "linh tâm phân biệt thiện ác", "linh tâm tự nhận entity", "linh tâm biết trước luật")) {
      issue("ability_overreach", "Thiên Kiếm Linh Tâm mind-reading/omniscience", "Lục Trầm Codex explicitly denies that Thiên Kiếm Linh Tâm reads minds, judges good/evil, auto-detects Entities or foreknows Level rules.")
    }
    if (mentionsAny(reply, "hai thanh tịch quang", "tịch quang thứ hai", "thêm một thanh tịch quang", "hai tịch quang")) {
      issue("ability_overreach", "Multiple Tịch Quang swords", "Lục Trầm Codex locks Tịch Quang as her single bản mệnh sword; she does not carry a second one.")
    }
    if (mentionsAny(reply, "godkiller gunblade", "godkiller là súng", "godkiller biến thành súng")) {
      issue("ability_overreach", "GodKiller as firearm/gunblade", "Syvial Codex locks GodKiller as a purely mechanical greatsword.")
    }
    if (mentionsAny(reply, "vạn khố giới chỉ cất lục trầm", "vạn khố giới chỉ cất syvial", "vạn khố giới chỉ cất người", "vạn khố giới chỉ chứa người", "giới chỉ cất người", "giới chỉ chứa người", "vạn khố giới chỉ scan lục trầm", "vạn khố giới chỉ scan syvial")) {
      issue("ability_overreach", "Vạn Khố Giới Chỉ acts on a living being", "Cao Minh Codex locks Vạn Khố Giới Chỉ to inanimate objects only.")
    }

    // Project Entity hard lock is code-known and unambiguous.
    if (mentionsAny(reply, "entity thân thiện", "entity trung lập", "thực thể thân thiện", "thực thể trung lập") && !mentionsAny(reply, "không có", "không phải", "không thể")) {
      issue("canon_conflict", "Friendly/neutral Entity", "Project Entity canon explicitly forbids friendly or neutral Entities toward humans.")
    }

    // Level 0 has no confirmed resident Entity. A confirmed incursion is allowed, so only reject
    // language that asserts a resident population as established fact.
    if (currentLevel(state) == 0 && mentionsAny(reply, "entity cư trú", "thực thể cư trú", "quần thể entity ở level 0")) {
      issue("canon_conflict", "Resident Entity population on Level 0", "Level 0 canon has no confirmed resident Entity population; a real Entity would be an abnormal incursion.")
    }

    return JSONObject().put("issues", issues).toString()
  }

  private fun currentLevel(state: JSONObject): Int {
    state.optJSONObject("level")?.let { return it.optInt("number", 0) }
    state.optJSONObject("flags")?.optJSONObject("currentLevel")?.let { return it.optInt("number", 0) }
    return 0
  }

  private fun mentionsAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(normalize(it)) }

  private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
    .replace('–', '-')
    .replace('—', '-')
    .replace(Regex("\\s+"), " ")
    .trim()
}
