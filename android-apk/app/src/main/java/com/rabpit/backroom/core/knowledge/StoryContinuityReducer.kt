package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Deterministic long-term continuity derived from already validated state changes.
 *
 * This is deliberately not an AI summarizer. It records durable facts that can be
 * proven from before/after state: level transitions, party changes, communication
 * changes, registry discoveries, relationship-state changes and current story threads.
 * Raw dialogue remains a separate short recency buffer.
 */
object StoryContinuityReducer {
  private const val MAX_EVENTS = 12
  private const val MAX_DISCOVERIES = 12
  private const val MAX_OBJECTIVES = 8
  private const val MAX_THREADS = 8
  private const val MAX_KNOWLEDGE = 16
  private const val MAX_RELATIONSHIPS = 8

  @JvmStatic
  fun apply(beforeJson: String, afterJson: String, action: String): String {
    val before = runCatching { JSONObject(beforeJson) }.getOrElse { JSONObject() }
    val after = runCatching { JSONObject(afterJson) }.getOrElse { JSONObject() }
    val flags = after.optJSONObject("flags") ?: JSONObject().also { after.put("flags", it) }
    val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject().also { flags.put("storyContinuity", it) }

    val turn = after.optInt("turn", before.optInt("turn", 1)).coerceAtLeast(1)
    val witnesses = currentWitnesses(after)

    syncMainObjectives(after, continuity)
    recordLevelChange(before, after, continuity, turn, witnesses)
    recordPartyChanges(before, after, continuity, turn)
    recordCommunicationChange(before, after, continuity, turn, witnesses)
    recordRegistryDiscoveries(before, after, continuity, turn, witnesses, "entityRegistry", "entity")
    recordRegistryDiscoveries(before, after, continuity, turn, witnesses, "survivorRegistry", "survivor")
    recordCharacterContinuity(before, after, continuity, turn, "iris")
    recordCharacterContinuity(before, after, continuity, turn, "syvial")
    recordRelationshipState(before, after, continuity, turn, "iris")
    recordRelationshipState(before, after, continuity, turn, "syvial")
    updateReunionThreads(after, continuity, turn)

    continuity.put("lastTurn", turn)
    continuity.put("schemaVersion", 1)
    continuity.put("source", "deterministic-state-reducer")
    trim(continuity, "events", MAX_EVENTS)
    trim(continuity, "discoveries", MAX_DISCOVERIES)
    trim(continuity, "objectives", MAX_OBJECTIVES)
    trim(continuity, "unresolvedThreads", MAX_THREADS)
    trim(continuity, "knowledge", MAX_KNOWLEDGE)
    trim(continuity, "relationshipChanges", MAX_RELATIONSHIPS)
    return after.toString()
  }

  private fun syncMainObjectives(after: JSONObject, continuity: JSONObject) {
    val flags = after.optJSONObject("flags") ?: return
    val iris = lower(flags.optJSONObject("iris")?.optString("continuity", "").orEmpty())
    val syvial = lower(flags.optJSONObject("syvial")?.optString("continuity", "").orEmpty())
    if (!iris.contains("separated") && !syvial.contains("separated") && continuity.optJSONArray("objectives") == null) return
    val objectives = continuity.optJSONArray("objectives") ?: JSONArray().also { continuity.put("objectives", it) }
    upsertById(objectives, JSONObject().put("id", "MAIN.SURVIVE").put("status", "active").put("fact", "Survive and learn the current environment."))
    syncReunionObjective(objectives, after, "iris", iris)
    syncReunionObjective(objectives, after, "syvial", syvial)
    upsertById(objectives, JSONObject().put("id", "MAIN.EXIT").put("status", "active").put("fact", "Seek a way out only when gameplay evidence supports one."))
  }

  private fun syncReunionObjective(objectives: JSONArray, after: JSONObject, actor: String, continuityState: String) {
    val id = "MAIN.REUNITE.${actor.uppercase(Locale.ROOT)}"
    when {
      partyIds(after).contains(actor) -> upsertById(objectives, JSONObject()
        .put("id", id).put("status", "resolved").put("fact", "$actor is currently reunited with Kai's party."))
      continuityState.contains("separated") -> upsertById(objectives, JSONObject()
        .put("id", id).put("status", "active").put("fact", "Determine $actor's condition and re-establish a route if possible."))
    }
  }

  private fun recordLevelChange(before: JSONObject, after: JSONObject, continuity: JSONObject, turn: Int, witnesses: JSONArray) {
    val oldLevel = currentLevel(before)
    val newLevel = currentLevel(after)
    if (oldLevel == newLevel) return
    appendUnique(continuity, "events", JSONObject()
      .put("id", "EVT.$turn.LEVEL.$oldLevel.$newLevel")
      .put("turn", turn).put("type", "level-change")
      .put("fact", "Moved from Level $oldLevel to Level $newLevel."))
    val discoveryId = "DISC.LEVEL.$newLevel"
    appendUnique(continuity, "discoveries", JSONObject()
      .put("id", discoveryId).put("turn", turn).put("type", "level")
      .put("fact", "The party reached Level $newLevel."))
    recordKnowledge(continuity, discoveryId, turn, witnesses)
  }

  private fun recordPartyChanges(before: JSONObject, after: JSONObject, continuity: JSONObject, turn: Int) {
    val oldParty = partyIds(before)
    val newParty = partyIds(after)
    (newParty - oldParty).sorted().forEach { id ->
      appendUnique(continuity, "events", JSONObject()
        .put("id", "EVT.$turn.PARTY.JOIN.$id").put("turn", turn).put("type", "party-join")
        .put("actor", id).put("fact", "$id joined the current party."))
    }
    (oldParty - newParty).sorted().forEach { id ->
      appendUnique(continuity, "events", JSONObject()
        .put("id", "EVT.$turn.PARTY.LEAVE.$id").put("turn", turn).put("type", "party-leave")
        .put("actor", id).put("fact", "$id left the current party."))
    }
  }

  private fun recordCommunicationChange(before: JSONObject, after: JSONObject, continuity: JSONObject, turn: Int, witnesses: JSONArray) {
    val oldValue = before.optJSONObject("flags")?.opt("communication")
    val newValue = after.optJSONObject("flags")?.opt("communication")
    if (!changed(oldValue, newValue) || newValue == null || newValue == JSONObject.NULL) return
    val compact = clip(newValue.toString(), 260)
    val id = "DISC.COMMS.$turn"
    appendUnique(continuity, "discoveries", JSONObject()
      .put("id", id).put("turn", turn).put("type", "communication")
      .put("fact", "Communication state changed: $compact"))
    recordKnowledge(continuity, id, turn, witnesses)
  }

  private fun recordRegistryDiscoveries(
    before: JSONObject,
    after: JSONObject,
    continuity: JSONObject,
    turn: Int,
    witnesses: JSONArray,
    root: String,
    type: String
  ) {
    val old = registryIds(before.optJSONObject("flags")?.opt(root))
    val fresh = registryIds(after.optJSONObject("flags")?.opt(root)) - old
    fresh.sorted().take(8).forEach { idRaw ->
      val id = safeId(idRaw)
      val discoveryId = "DISC.${type.uppercase(Locale.ROOT)}.$id"
      appendUnique(continuity, "discoveries", JSONObject()
        .put("id", discoveryId).put("turn", turn).put("type", type)
        .put("fact", "Confirmed $type: $idRaw."))
      recordKnowledge(continuity, discoveryId, turn, witnesses)
    }
  }

  private fun recordCharacterContinuity(before: JSONObject, after: JSONObject, continuity: JSONObject, turn: Int, actor: String) {
    val oldValue = before.optJSONObject("flags")?.optJSONObject(actor)?.opt("continuity")
    val newValue = after.optJSONObject("flags")?.optJSONObject(actor)?.opt("continuity")
    if (!changed(oldValue, newValue) || newValue == null || newValue == JSONObject.NULL) return
    appendUnique(continuity, "events", JSONObject()
      .put("id", "EVT.$turn.${actor.uppercase(Locale.ROOT)}.CONTINUITY")
      .put("turn", turn).put("type", "character-continuity").put("actor", actor)
      .put("fact", "$actor continuity changed to ${clip(newValue.toString(), 180)}."))
  }

  private fun recordRelationshipState(before: JSONObject, after: JSONObject, continuity: JSONObject, turn: Int, actor: String) {
    val oldState = before.optJSONObject("flags")?.optJSONObject(actor)?.opt("relationship")
    val newState = after.optJSONObject("flags")?.optJSONObject(actor)?.opt("relationship")
    if (!changed(oldState, newState) || newState == null || newState == JSONObject.NULL) return
    appendUnique(continuity, "relationshipChanges", JSONObject()
      .put("id", "RELDELTA.$turn.${actor.uppercase(Locale.ROOT)}")
      .put("turn", turn).put("actor", actor)
      .put("fact", clip(newState.toString(), 220)))
  }

  private fun updateReunionThreads(after: JSONObject, continuity: JSONObject, turn: Int) {
    val flags = after.optJSONObject("flags") ?: return
    val threads = continuity.optJSONArray("unresolvedThreads") ?: JSONArray().also { continuity.put("unresolvedThreads", it) }
    listOf("iris", "syvial").forEach { actor ->
      val state = lower(flags.optJSONObject(actor)?.optString("continuity", "").orEmpty())
      val id = "THREAD.REUNITE.${actor.uppercase(Locale.ROOT)}"
      if (state.contains("separated")) {
        upsertById(threads, JSONObject().put("id", id).put("status", "open").put("turn", turn)
          .put("fact", "Re-establish contact/reunion with $actor; current separation remains unresolved."))
      } else if (partyIds(after).contains(actor)) {
        upsertById(threads, JSONObject().put("id", id).put("status", "resolved").put("turn", turn)
          .put("fact", "$actor is currently in the party."))
      }
    }
  }

  private fun recordKnowledge(continuity: JSONObject, factId: String, turn: Int, witnesses: JSONArray) {
    appendUnique(continuity, "knowledge", JSONObject()
      .put("id", "KNOW.$factId").put("factId", factId).put("turn", turn)
      .put("knownBy", witnesses))
  }

  private fun currentWitnesses(state: JSONObject): JSONArray {
    val out = JSONArray().put("kai")
    partyIds(state).sorted().filter { it != "kai" }.forEach { out.put(it) }
    return out
  }

  private fun partyIds(state: JSONObject): Set<String> {
    val out = linkedSetOf<String>()
    val party = state.optJSONArray("party") ?: return out
    for (i in 0 until party.length()) {
      val value = party.opt(i)
      val raw = if (value is JSONObject) value.optString("id", value.optString("name", "")) else value?.toString().orEmpty()
      val id = lower(raw).trim()
      if (id.isNotEmpty()) out += id
    }
    return out
  }

  private fun registryIds(value: Any?): Set<String> {
    val out = linkedSetOf<String>()
    when (value) {
      is JSONObject -> value.keys().forEach { out += it }
      is JSONArray -> for (i in 0 until value.length()) {
        val item = value.opt(i)
        val id = if (item is JSONObject) item.optString("id", item.optString("name", "")) else item?.toString().orEmpty()
        if (id.isNotBlank()) out += id
      }
    }
    return out
  }

  private fun appendUnique(root: JSONObject, key: String, item: JSONObject) {
    val array = root.optJSONArray(key) ?: JSONArray().also { root.put(key, it) }
    val id = item.optString("id", "")
    if (id.isNotEmpty()) {
      for (i in 0 until array.length()) if (array.optJSONObject(i)?.optString("id") == id) return
    }
    array.put(item)
  }

  private fun upsertById(array: JSONArray, item: JSONObject) {
    val id = item.optString("id", "")
    for (i in 0 until array.length()) {
      if (array.optJSONObject(i)?.optString("id") == id) {
        array.put(i, item)
        return
      }
    }
    array.put(item)
  }

  private fun trim(root: JSONObject, key: String, max: Int) {
    val source = root.optJSONArray(key) ?: return
    if (source.length() <= max) return
    val out = JSONArray()
    val start = source.length() - max
    for (i in start until source.length()) out.put(source.opt(i))
    root.put(key, out)
  }

  private fun currentLevel(state: JSONObject): Int {
    state.optJSONObject("level")?.let { return it.optInt("number", 0) }
    state.optJSONObject("flags")?.optJSONObject("currentLevel")?.let { return it.optInt("number", 0) }
    return 0
  }

  private fun changed(a: Any?, b: Any?): Boolean {
    val left = if (a == null || a == JSONObject.NULL) "null" else a.toString()
    val right = if (b == null || b == JSONObject.NULL) "null" else b.toString()
    return left != right
  }

  private fun safeId(value: String): String = value.uppercase(Locale.ROOT)
    .replace(Regex("[^A-Z0-9]+"), ".").trim('.')

  private fun clip(value: String, max: Int): String = if (value.length <= max) value else value.take(max) + "…"
  private fun lower(value: String): String = value.lowercase(Locale.ROOT)
}
