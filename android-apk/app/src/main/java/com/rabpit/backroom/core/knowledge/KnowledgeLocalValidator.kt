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
    if (mentionsAny(reply, "sparda core cạn", "sparda core hết", "hết quỷ lực", "cạn quỷ lực") && mentionsAny(reply, "kai", "twilight")) {
      issue("competence_suppression", "Kai hết/cạn quỷ lực", "Kai Codex locks Sparda Core as an infinite power source without intrinsic depletion.")
    }
    if (mentionsAny(reply, "lucifer core cạn", "lucifer core hết", "syvial hết quỷ lực", "syvial cạn quỷ lực")) {
      issue("competence_suppression", "Syvial hết/cạn quỷ lực", "Syvial Codex locks Lucifer Core as an infinite power source without intrinsic depletion.")
    }
    if (mentionsAny(reply, "devil trigger hết thời gian", "devil trigger cooldown", "devil trigger hồi chiêu", "devil trigger phản phệ")) {
      issue("ability_overreach", "Invented Devil Trigger limit", "Current Kai/Syvial codices do not permit an invented intrinsic duration cap/cooldown/backlash.")
    }
    if (mentionsAny(reply, "argus nhìn xuyên tường", "argus xuyên tường", "argus drone", "drone của iris", "iris nhìn xuyên tường")) {
      issue("ability_overreach", "ARGUS remote/omniscient sensing", "Iris Codex explicitly denies wall vision, remote cameras/drone mesh and omniscience for ARGUS Terrain Read.")
    }
    if (mentionsAny(reply, "thousandfold khiến cơ thể", "cơ thể iris nhanh gấp 1000", "iris nhanh gấp 1.000")) {
      issue("ability_overreach", "Thousandfold accelerates Iris's body", "Thousandfold accelerates information processing up to 1:1000, not her body 1000x.")
    }
    if (mentionsAny(reply, "godkiller gunblade", "godkiller là súng", "godkiller biến thành súng")) {
      issue("ability_overreach", "GodKiller as firearm/gunblade", "Syvial Codex locks GodKiller as a purely mechanical greatsword.")
    }
    if (mentionsAny(reply, "omnivault cất iris", "omnivault cất syvial", "omnivault cất người", "omnivault chứa người", "omnivault scan iris", "omnivault scan syvial")) {
      issue("ability_overreach", "Omnivault acts on a living being", "Kai Codex locks Omnivault to inanimate objects only.")
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
