package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable presentation for one player utterance and its confirmed public events.
 * It never changes commands, rewards, encounters, puzzle progress or receipts.
 */
object PlayerActionPresentation {
  const val JOURNAL = "player.action.presentation"

  /** The latest utterance is durable presentation data, not gameplay authority.
   * Receipts/events are captured in the same save as a Core commit. */
  fun journal(state: GameState): JSONObject? = state.world[JOURNAL]?.let(::JSONObject)
  fun begin(state: GameState, id: String, text: String): GameState {
    require(text.isNotBlank() && text.length <= 900) { "player_action_size" }
    val p = MiniMapRuntime.progress(state)
    val batch = JSONObject().put("id", id).put("playerText", text)
      .put("sceneMap", p.getString("nodeId") + ":" + p.getInt("index"))
      .put("status", "running").put("events", JSONArray()).put("receipts", JSONArray())
      .put("requested", 0).put("interrupted", true).put("message", "Đang phân tích yêu cầu…")
    return state.copy(world = state.world + (JOURNAL to batch.toString()))
  }
  fun update(state: GameState, change: (JSONObject) -> Unit): GameState {
    val batch = journal(state) ?: error("player_action_missing")
    change(batch)
    return state.copy(world = state.world + (JOURNAL to batch.toString()))
  }
  fun receipt(state: GameState, pending: JSONObject): GameState {
    val batch = journal(state) ?: return state
    if (pending.optString("playerActionId") != batch.optString("id") ||
      batch.optString("reply").isNotBlank()) return state
    return update(state) {
      val ids = it.getJSONArray("receipts")
      val id = pending.getString("actionId")
      if ((0 until ids.length()).none { n -> ids.optString(n) == id }) {
        ids.put(id)
        val public = pending.getJSONObject("public")
        val isFree = pending.getJSONObject("command").optString("kind") == "FREE"
        val event = JSONObject().put("kind", if (isFree) "FREE" else "CORE").put("event", public)
        if (isFree) event.put("text", public.getString("freeText"))
        it.getJSONArray("events").put(event)
      }
    }
  }
  @JvmStatic fun writerView(view: JSONObject): JSONObject {
    val compact = JSONObject()
    listOf("level", "location", "player", "party", "partyDetails", "equipment", "pendingEncounter").forEach {
      if (view.has(it)) compact.put(it, view.get(it))
    }
    view.optJSONObject("scene")?.let { scene ->
      val here = JSONObject()
      listOf("nodeId", "environment", "room", "npcs").forEach {
        if (scene.has(it)) here.put(it, scene.get(it))
      }
      compact.put("scene", here)
    }
    val recent = mutableListOf<JSONObject>()
    val log = view.optJSONArray("log") ?: JSONArray()
    val active = view.optJSONObject("playerAction")?.optString("id")
    var budget = 4500
    for (i in log.length() - 1 downTo 0) {
      if (recent.size == 4) break
      val entry = log.optJSONObject(i) ?: continue
      if (active != null && entry.optString("playerActionId") == active) continue
      val text = entry.optString("text")
      if (text.length > 4500) continue // Exclude prologue, rather than cutting a sentence.
      if (text.length > budget) break
      recent.add(JSONObject().put("role", entry.optString("role")).put("text", text)); budget -= text.length
    }
    return compact.put("recentContext", JSONArray(recent.asReversed()))
  }

  fun project(view: JSONObject, state: GameState) {
    view.remove("playerAction")
    val batch = journal(state) ?: return
    val scene = view.optJSONObject("scene") ?: return
    val sceneMap = scene.optString("nodeId") + ":" + scene.optInt("miniIndex", 1)
    if (batch.getString("sceneMap") != sceneMap) return
    val id = batch.getString("id")
    val receipts = batch.getJSONArray("receipts")
    val receiptIds = (0 until receipts.length()).map { receipts.getString(it) }.toSet()
    val log = view.optJSONArray("log") ?: JSONArray()
    val kept = JSONArray(); var insert = -1
    // Remove earlier projection of this batch and any individual Core receipts.
    for (i in 0 until log.length()) {
      val entry = log.optJSONObject(i) ?: continue
      val belongs = entry.optString("playerActionId") == id || entry.optString("sceneActionId") in receiptIds
      if (belongs) {
        if (insert < 0) insert = kept.length()
        if (entry.optString("playerActionId") != id && entry.optString("role") == "player") {
          val next = log.optJSONObject(i + 1)
          if (next?.optString("role") == "gm" && !next.has("playerActionId") && !next.has("sceneActionId"))
            next.put("playerActionId", id)
        }
      } else kept.put(entry)
    }
    if (insert < 0) insert = kept.length()
    val status = batch.getString("status")
    val events = batch.getJSONArray("events")
    val coreResults = (0 until events.length()).mapNotNull { n ->
      events.getJSONObject(n).optJSONObject("event")?.optString("result")
    }
    val freeCount = (0 until events.length()).count { events.getJSONObject(it).optString("kind") == "FREE" }
    val accepted = events.length()
    val requested = batch.optInt("requested")
    val note = when (status) {
      "complete" -> ""
      "running" -> "Đang xử lý · ${coreResults.size} lượt gameplay đã xác nhận."
      else -> "${coreResults.size} lượt gameplay đã xác nhận ($freeCount hành động tự do)" +
        (if (requested > 0) "; ${maxOf(0, requested - accepted)} bước chưa thực hiện." else ".") +
        " " + batch.optString("message")
    }
    val player = JSONObject().put("role", "player").put("text", batch.getString("playerText"))
      .put("playerActionId", id).put("sceneMap", sceneMap).put("sceneBatchActions", receipts)
      .put("actionStatus", note)
    val reply = batch.optString("reply")
    val result = if (reply.isNotBlank()) JSONObject().put("role", "gm").put("text", reply)
      else if (coreResults.isNotEmpty()) JSONObject().put("role", "core").put("text", coreResults.joinToString("\n\n"))
      else null
    result?.put("playerActionId", id)?.put("sceneMap", sceneMap)
    val merged = JSONArray()
    for (i in 0..kept.length()) {
      if (i == insert) { merged.put(player); if (result != null) merged.put(result) }
      if (i < kept.length()) merged.put(kept.get(i))
    }
    view.put("log", merged)
    view.put("playerAction", JSONObject().put("id", id).put("status", status)
      .put("recoverable", reply.isBlank() && events.length() > 0).put("message", note))
  }

}
