package com.rabpit.backroom.core

import org.json.JSONObject

/** Validates AI intent plans. FREE steps are prose only, never Core commands. */
object PlayerActionPlan {
  @JvmStatic fun parse(raw: String, expectedTotal: Int, completed: Int): JSONObject {
    require(raw.length <= 12000) { "player_plan_too_large" }
    val obj = JSONObject(raw.trim())
    require(obj.keys().asSequence().toSet() == setOf("totalActions", "steps", "unresolved")) { "player_plan_fields" }
    require(obj.opt("totalActions") is Int && obj.opt("unresolved") is Boolean) { "player_plan_types" }
    val total = obj.getInt("totalActions")
    require(total in 1..12 && (expectedTotal < 0 || total == expectedTotal)) { "player_plan_count_changed" }
    require(completed in 0..total) { "player_plan_progress" }
    val steps = obj.getJSONArray("steps")
    require(steps.length() <= total - completed) { "player_plan_steps" }
    for (i in 0 until steps.length()) {
      val step = steps.getJSONObject(i)
      val kind = step.getString("kind")
      if (kind == "FREE") {
        require(step.keys().asSequence().toSet() == setOf("kind", "text")) { "player_free_fields" }
        val text = step.getString("text")
        require(text.isNotBlank() && text.length <= 500 &&
          text.none { it.code < 32 && it != '\n' }) { "player_free_text" }
      } else {
        require(kind in setOf("MOVE", "SEARCH", "INTERACT", "TALK") &&
          step.keys().asSequence().toSet() == setOf("kind", "targetId")) { "player_core_fields" }
        val id = step.getString("targetId")
        require(id.isNotBlank() && id.length <= 160 && id.all { it.code >= 32 }) { "player_core_target" }
      }
    }
    return obj
  }
}
