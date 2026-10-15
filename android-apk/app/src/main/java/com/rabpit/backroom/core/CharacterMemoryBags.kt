package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Core-owned, per-character factual memories. Stored with GameState metadata, so a
 * checkpoint/save/restore cannot move NPC memory independently of the campaign.
 *
 * The initial writer supports ONLY deterministic Core facts: first meeting and
 * Party membership changes. It never reads GM prose or infers promises/feelings.
 * Absence does not delete a bag; only Core-confirmed present actors activate it.
 */
object CharacterMemoryBags {
  private const val STORAGE_KEY = "core.characterMemoryBags.v1"
  private const val VERSION = 1
  private const val PROMPT_RECENT_LIMIT = 4

  /**
   * Called after a successful, validated Core transition, before its single save.
   * Rejected/aborted turns must not call this method.
   */
  fun recordCommitted(before: GameState, after: GameState): GameState {
    val meetings = after.characters.values
      .filter { it.id != CAO_MINH_ID && it.metadata["encountered"] == "true" &&
        before.characters[it.id]?.metadata?.get("encountered") != "true" }
      .sortedBy { it.id }
    val joined = (after.party.memberIds.toSet() - before.party.memberIds.toSet())
      .filter { it != CAO_MINH_ID }.sorted()
    val left = (before.party.memberIds.toSet() - after.party.memberIds.toSet())
      .filter { it != CAO_MINH_ID }.sorted()

    if (meetings.isEmpty() && joined.isEmpty() && left.isEmpty()) return after
    val root = readRoot(after)
    val bags = root.getJSONObject("bags")
    val turn = CoreClientProjection.turnIndex(after)

    meetings.forEach { actor ->
      val event = event("MEET.${actor.id}", "first_meeting", actor.id, turn)
      appendTo(bags, actor.id, event)
      appendTo(bags, CAO_MINH_ID, event)
    }
    joined.forEach { actor ->
      val event = event(nextEventId(root, "PARTY.JOIN", actor), "party_join", actor, turn)
      appendTo(bags, actor, event)
      appendTo(bags, CAO_MINH_ID, event)
    }
    left.forEach { actor ->
      val event = event(nextEventId(root, "PARTY.LEAVE", actor), "party_leave", actor, turn)
      appendTo(bags, actor, event)
      appendTo(bags, CAO_MINH_ID, event)
    }
    return after.copy(metadata = after.metadata + (STORAGE_KEY to root.toString()))
  }

  /** Return no memory text for absent NPCs, regardless of the size of the store. */
  fun sceneContext(state: GameState, pendingConfirmed: Set<String> = emptySet()): String {
    val visible = state.characters.values
      .filter { actor ->
        actor.id != CAO_MINH_ID &&
          (actor.presence == CharacterPresence.ACTIVE ||
            (actor.id in pendingConfirmed &&
              actor.presence in setOf(CharacterPresence.SEPARATED, CharacterPresence.MISSING))) &&
          (actor.metadata["encountered"] == "true" || actor.id in state.party.memberIds)
      }
      .sortedBy { it.id }
    if (visible.isEmpty()) return ""
    val bags = readRoot(state).getJSONObject("bags")
    val rendered = visible.mapNotNull { actor ->
      val entries = bags.optJSONArray(actor.id) ?: return@mapNotNull null
      val selected = (0 until entries.length()).map { entries.getJSONObject(it) }
      val first = selected.firstOrNull { it.optString("kind") == "first_meeting" }
      val recent = selected.takeLast(PROMPT_RECENT_LIMIT)
      val facts = (listOfNotNull(first) + recent).distinctBy { it.getString("id") }
      if (facts.isEmpty()) return@mapNotNull null
      buildString {
        append("Actor ").append(actor.id).append(" (Core-confirmed present):\n")
        append("Current Party status from Core: ")
          .append(if (actor.id in state.party.memberIds) "IN_PARTY" else "NOT_IN_PARTY")
          .append("\n")
        facts.forEach { item ->
          append("- Past event: ")
          when (item.getString("kind")) {
            "first_meeting" -> append("Has encountered Cao Minh before; no implied names, trust, or recruitment.")
            "party_join" -> append("Joined Cao Minh's Party.")
            "party_leave" -> append("Left Cao Minh's Party.")
            else -> error("Unsupported Core memory kind")
          }
          append(" [turn ").append(item.getInt("turn")).append("]\n")
        }
      }
    }
    return if (rendered.isEmpty()) "" else rendered.joinToString("")
  }

  private fun event(id: String, kind: String, actorId: String, turn: Int) = JSONObject()
    .put("id", id)
    .put("kind", kind)
    .put("actorId", actorId)
    .put("turn", turn)
    .put("authority", "CORE_CONFIRMED")

  /** Sequence is part of the same saved Core state; multiple UI actions in one turn
   * must not silently overwrite each other's memory events. */
  private fun nextEventId(root: JSONObject, kind: String, actorId: String): String {
    val next = root.optLong("nextSequence", 1L)
    require(next in 1 until Long.MAX_VALUE) { "character_memory_sequence_invalid" }
    root.put("nextSequence", next + 1)
    return "$kind.$actorId.$next"
  }

  /**
   * Validate memory together with GameStateCodec.decode, even when the NPC is absent.
   * A corrupt bag must be detected on load, not hundreds of turns later on reunion.
   */
  fun validate(state: GameState) {
    val root = readRoot(state)
    require(root.optLong("nextSequence", 1L) >= 1L) { "character_memory_sequence_invalid" }
    val bags = root.getJSONObject("bags")
    val owners = bags.keys()
    while (owners.hasNext()) {
      val owner = owners.next()
      require(owner.isNotBlank()) { "character_memory_owner_invalid" }
      val items = bags.optJSONArray(owner)
        ?: throw IllegalArgumentException("character_memory_entries_invalid")
      val seen = mutableSetOf<String>()
      for (i in 0 until items.length()) {
        val entry = items.optJSONObject(i)
          ?: throw IllegalArgumentException("character_memory_entry_invalid")
        val id = entry.optString("id", "")
        require(id.isNotBlank() && seen.add(id)) { "character_memory_event_id_invalid" }
        require(entry.optString("actorId").isNotBlank()) { "character_memory_actor_invalid" }
        require(entry.optString("kind") in setOf("first_meeting", "party_join", "party_leave")) {
          "character_memory_kind_invalid"
        }
        require(entry.optInt("turn", -1) >= 1) { "character_memory_turn_invalid" }
        require(entry.optString("authority") == "CORE_CONFIRMED") {
          "character_memory_authority_invalid"
        }
      }
    }
  }

  private fun appendTo(bags: JSONObject, owner: String, item: JSONObject) {
    val items = bags.optJSONArray(owner) ?: JSONArray().also { bags.put(owner, it) }
    for (i in 0 until items.length()) {
      if (items.getJSONObject(i).getString("id") == item.getString("id")) return
    }
    items.put(JSONObject(item.toString()))
  }

  private fun readRoot(state: GameState): JSONObject {
    val raw = state.metadata[STORAGE_KEY]
      ?: return JSONObject().put("schemaVersion", VERSION).put("bags", JSONObject())
    val root = JSONObject(raw)
    require(root.getInt("schemaVersion") == VERSION) { "unsupported_character_memory_schema" }
    require(root.optJSONObject("bags") != null) { "invalid_character_memory_bags" }
    return root
  }
}
