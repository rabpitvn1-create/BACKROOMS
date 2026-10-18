package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Presentation-only grouping for one player utterance with several Core
 * commands. This never changes a pending command, reward, encounter, or receipt.
 */
object PlayerActionPresentation {
  private fun paragraph(text: String) = text.replace(Regex("\\s+"), " ").trim()

  @JvmStatic fun fold(view: JSONObject, firstActionId: String, currentActionId: String,
    rawPlayerText: String): JSONObject {
    val log = view.optJSONArray("log") ?: return view
    fun playerIndex(actionId: String): Int =
      (0 until log.length()).firstOrNull { i ->
        log.optJSONObject(i)?.let {
          it.optString("role") == "player" && it.optString("sceneActionId") == actionId
        } == true
      } ?: -1
    fun gmIndex(playerIndex: Int): Int {
      if (playerIndex < 0 || playerIndex + 1 >= log.length()) return -1
      val player = log.optJSONObject(playerIndex) ?: return -1
      val next = log.optJSONObject(playerIndex + 1) ?: return -1
      return if (next.optString("role") == "gm" &&
        next.optString("sceneMap") == player.optString("sceneMap") &&
        !next.has("sceneActionId")) playerIndex + 1 else -1
    }
    val first = playerIndex(firstActionId)
    val current = playerIndex(currentActionId)
    if (first < 0 || current < 0) return view
    val firstPlayer = log.getJSONObject(first)
    firstPlayer.put("text", rawPlayerText) // No trim, rewriting or paraphrase.
    // Keep receipt IDs on the retained entry so projection after app restart,
    // retry or the final render cannot append the last receipt a second time.
    val seen = firstPlayer.optJSONArray("sceneBatchActions")
      ?: JSONArray().put(firstActionId).also { firstPlayer.put("sceneBatchActions", it) }
    if ((0 until seen.length()).none { seen.optString(it) == currentActionId })
      seen.put(currentActionId)

    val firstGm = gmIndex(first)
    if (first == current) {
      if (firstGm >= 0)
        log.getJSONObject(firstGm).put("text", paragraph(log.getJSONObject(firstGm).optString("text")))
      return view
    }
    val currentGm = gmIndex(current)
    val earlier = if (firstGm >= 0) paragraph(log.getJSONObject(firstGm).optString("text")) else ""
    val latest = if (currentGm >= 0) paragraph(log.getJSONObject(currentGm).optString("text")) else ""
    val singleParagraph = listOf(earlier, latest).filter { it.isNotBlank() }.joinToString(" ")
    val gm = if (firstGm >= 0) log.getJSONObject(firstGm)
      else JSONObject().put("role", "gm").put("sceneMap", firstPlayer.optString("sceneMap"))
    if (singleParagraph.isNotEmpty()) gm.put("text", singleParagraph)
    val combined = JSONArray()
    for (i in 0 until log.length()) {
      if (i == first) {
        combined.put(firstPlayer)
        if (singleParagraph.isNotEmpty()) combined.put(gm)
      } else if (i != firstGm && i != current && i != currentGm)
        combined.put(log.get(i))
    }
    view.put("log", combined)
    return view
  }
}
