package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrative memory, independent of Combat HP, equipment, inventory and statuses.
 * Important unresolved story facts survive across Levels; a bounded history holds
 * completed events. No narrative text is interpreted as a gameplay command.
 */
object StoryFactLedger {
  private const val KEY = "story.factLedger"
  private const val MAX_HISTORY = 96
  private const val MAX_PER_TURN = 6
  private val subjectPattern = Regex("[a-z0-9_.:-]{2,64}")

  enum class Kind { EVENT, ENTITY, CLUE }

  data class FactSpec(val kind: Kind, val subject: String, val fact: String) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind.name)
      .put("subject", subject).put("fact", fact)
  }

  /** Syntax for SOL-authored Milestone facts. */
  fun parseSpecs(raw: JSONArray, limit: Int = MAX_PER_TURN): List<FactSpec> {
    require(raw.length() <= limit) { "story_facts_count_invalid" }
    return (0 until raw.length()).map { index ->
      val item = raw.getJSONObject(index)
      require(item.keys().asSequence().toSet() == setOf("kind", "subject", "fact")) {
        "story_fact_fields_invalid"
      }
      val kind = Kind.valueOf(readString(item, "kind", 16))
      val subject = readString(item, "subject", 64)
      require(subjectPattern.matches(subject)) { "story_subject_invalid" }
      FactSpec(kind, subject, readString(item, "fact", 220))
    }
  }

  /** Core records only SOL-authored events and facts, never GM claims. */
  fun recordTurn(state: GameState, milestoneEvents: List<StoryMilestonePlan.Event>): GameState {
    require(milestoneEvents == StoryMilestoneRuntime.due(state)) { "story_milestone_source_mismatch" }
    if (milestoneEvents.isEmpty()) return state
    val open = readArray(state, "open")
    val history = readArray(state, "history")
    val position = StoryCheckpointCore.current(state)?.id ?: "FREE"
    val turn = CoreClientProjection.turnIndex(state)
    var sequence = state.world[KEY]?.let { JSONObject(it).getLong("nextSequence") } ?: 0L
    val levelNode = JSONObject(state.world["levelJson"] ?: "{}").let {
      it.optString("id", it.optInt("number", 0).toString())
    }

    fun add(spec: FactSpec, event: StoryMilestonePlan.Event, source: String) {
      val instance = if (spec.kind == Kind.ENTITY && event.entityKey == spec.subject)
        StoryMilestoneRuntime.entityEncounterId(state, event) else null
      if (spec.kind == Kind.ENTITY)
        require(CombatRuntime.supportsEntityKey(spec.subject)) { "milestone_fact_entity_unknown" }
      val existingIndex = (0 until open.length()).firstOrNull { index ->
        val item = open.optJSONObject(index)
        item != null && item.optString("kind") == spec.kind.name &&
          item.optString("subject") == spec.subject &&
          (spec.kind != Kind.ENTITY || item.optString("entityEncounterId", "") == instance.orEmpty())
      }
      if (existingIndex != null) {
        val existing = open.getJSONObject(existingIndex)
        if (existing.getString("fact") == spec.fact) return
        // A newer SOL event supersedes the earlier scripted narrative fact.
        open.remove(existingIndex)
        history.put(JSONObject(existing.toString()).put("state", "SUPERSEDED")
          .put("resolutionState", "SUPERSEDED").put("resolutionSource", source)
          .put("resolvedTurn", turn).put("resolution", spec.fact))
      }
      val id = "FACT:$position:$turn:$sequence"
      sequence = Math.addExact(sequence, 1L)
      open.put(spec.toJson().put("id", id).put("turn", turn)
        .put("checkpointId", position).put("levelNode", levelNode)
        .put("source", source).put("state", "COMMITTED")
        .put("resolutionState", "OPEN").put("knowledgeScope", "NARRATIVE_ONLY")
        .apply { instance?.let { put("entityEncounterId", it) } })
    }

    for (event in milestoneEvents) {
      val key = "MS:$position:$turn:${event.id}"
      history.put(JSONObject().put("id", key).put("turn", turn)
        .put("kind", if (event.isEntityEncounter) "ENTITY" else "EVENT")
        .put("action", event.action).put("event", event.event).put("outcome", event.outcome)
        .put("fact", "${event.action} → ${event.event} → ${event.outcome}")
        .put("checkpointId", position).put("levelNode", levelNode)
        .put("source", "MILESTONE").put("state", "COMMITTED")
        .apply {
          if (event.isEntityEncounter) put("entityKey", event.entityKey)
            .put("entityEncounterId", StoryMilestoneRuntime.entityEncounterId(state, event))
        })
      event.storyFacts.forEach { add(it, event, "MILESTONE:$key") }
    }

    val retained = JSONArray()
    val archive = readArray(state, "archive")
    val start = (history.length() - MAX_HISTORY).coerceAtLeast(0)
    for (i in 0 until start) archive.put(history.get(i))
    for (i in start until history.length()) retained.put(history.get(i))
    val packed = JSONObject().put("version", 3).put("open", open)
      .put("history", retained).put("archive", archive).put("nextSequence", sequence)
    return state.copy(world = state.world + (KEY to packed.toString()))
  }

  /** Bounded retrieval context; omitted OPEN facts remain in the saved ledger. */
  fun context(state: GameState, query: String = ""): String {
    val open = readArray(state, "open")
    val history = readArray(state, "history")
    val all = (0 until open.length()).map { open.getJSONObject(it) }
    val ordered = linkedMapOf<String, JSONObject>()
    fun include(fact: JSONObject) { ordered.putIfAbsent(fact.getString("id"), fact) }
    val normalizedQuery = query.lowercase(java.util.Locale.ROOT)
    val queryWords = normalizedQuery.split(Regex("[^\\p{L}\\p{N}_.:-]+"))
      .filter { it.length >= 4 }.toSet()
    all.filter { fact ->
      normalizedQuery.contains(fact.getString("id").lowercase(java.util.Locale.ROOT)) ||
        normalizedQuery.contains(fact.getString("subject").lowercase(java.util.Locale.ROOT)) ||
        queryWords.any { fact.getString("fact").lowercase(java.util.Locale.ROOT).contains(it) }
    }.forEach(::include)
    val actors = state.party.memberIds.toSet() + listOfNotNull(CombatRuntime.active(state)?.entityKey)
    all.filter { it.getString("subject") in actors }.forEach(::include)
    all.take(4).forEach(::include)
    all.takeLast(4).forEach(::include)
    val checkpoint = StoryCheckpointCore.current(state)?.id
    all.filter { it.getString("checkpointId") == checkpoint }.takeLast(8).forEach(::include)
    if (all.isNotEmpty()) {
      val offset = CoreClientProjection.turnIndex(state) % all.size
      for (i in all.indices) include(all[(offset + i) % all.size])
    }
    val selected = JSONArray()
    var openChars = 0
    ordered.values.forEach { fact ->
      val cost = fact.toString().length + 1
      if (openChars + cost <= 18000) { selected.put(fact); openChars += cost }
    }
    val recent = JSONArray()
    val relevantHistory = JSONArray()
    var recentChars = 0
    val historyIds = hashSetOf<String>()
    if (normalizedQuery.isNotBlank()) {
      val archive = readArray(state, "archive")
      for (entries in listOf(history, archive)) {
        for (i in entries.length() - 1 downTo 0) {
          val entry = entries.getJSONObject(i)
          val text = (entry.optString("subject") + " " + entry.optString("fact") + " " +
            entry.optString("resolution")).lowercase(java.util.Locale.ROOT)
          val matches = normalizedQuery.contains(entry.getString("id").lowercase(java.util.Locale.ROOT)) ||
            queryWords.any(text::contains)
          val cost = entry.toString().length + 1
          if (matches && recentChars + cost <= 3750 && historyIds.add(entry.getString("id"))) {
            relevantHistory.put(entry)
            recentChars += cost
          }
        }
      }
    }
    val chosen = mutableListOf<Any>()
    for (i in history.length() - 1 downTo (history.length() - 8).coerceAtLeast(0)) {
      val entry = history.get(i)
      val cost = entry.toString().length + 1
      val id = (entry as JSONObject).getString("id")
      if (recentChars + cost <= 7500 && historyIds.add(id)) { chosen += entry; recentChars += cost }
    }
    chosen.asReversed().forEach(recent::put)
    return JSONObject().put("open", selected).put("recent", recent).put("relevantHistory", relevantHistory)
      .put("openTotal", all.size).put("omittedOpen", all.size - selected.length())
      .put("retention", "Omitted OPEN facts remain saved and unresolved. Retrieve by subject/id; this packet is not the whole ledger.")
      .apply { state.world["story.milestone.lastCompletion"]?.let { put("lastCompletion", JSONObject(it)) } }
      .toString()
  }

  private fun readArray(state: GameState, name: String): JSONArray {
    val raw = state.world[KEY] ?: return JSONArray()
    val root = JSONObject(raw)
    require(root.getInt("version") == 3) { "story_memory_version_invalid" }
    return JSONArray(root.getJSONArray(name).toString())
  }

  fun validateStoredState(state: GameState) {
    val raw = state.world[KEY] ?: return
    val root = JSONObject(raw)
    require(root.getInt("version") == 3 && root.getLong("nextSequence") >= 0 &&
      root.getJSONArray("history").length() <= MAX_HISTORY) { "story_memory_invalid" }
    val ids = hashSetOf<String>()
    for (name in listOf("open", "history", "archive")) {
      val array = root.getJSONArray(name)
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        require(ids.add(item.getString("id")) && item.getInt("turn") >= 1 &&
          item.getString("checkpointId").isNotBlank() && item.getString("levelNode").isNotBlank() &&
          item.getString("source").startsWith("MILESTONE")) { "story_fact_provenance_invalid" }
        if (name == "open") require(item.getString("state") == "COMMITTED" &&
          item.getString("resolutionState") == "OPEN") { "story_open_fact_invalid" }
        if (item.optString("state") == "SUPERSEDED") require(
          item.getString("resolutionSource").startsWith("MILESTONE") &&
            item.getString("resolution").isNotBlank()) { "story_resolution_provenance_invalid" }
      }
    }
  }

  fun statsJson(state: GameState): JSONObject {
    val raw = state.world[KEY] ?: return JSONObject()
      .put("confirmedFacts", 0).put("resolvedFacts", 0).put("openFacts", 0)
    val root = JSONObject(raw)
    var resolved = 0
    for (name in listOf("history", "archive")) {
      val entries = root.getJSONArray(name)
      for (i in 0 until entries.length()) if (entries.getJSONObject(i).optString("state") == "RESOLVED") resolved++
    }
    return JSONObject().put("confirmedFacts", root.getLong("nextSequence"))
      .put("resolvedFacts", resolved).put("openFacts", root.getJSONArray("open").length())
  }

  private fun readString(source: JSONObject, key: String, maxLength: Int): String {
    val raw = source.opt(key)
    require(raw is String) { "story_string_type_invalid:$key" }
    val value = raw.trim()
    require(value.isNotEmpty() && value.length <= maxLength &&
      value.none { Character.isISOControl(it) }) { "story_string_length_invalid:$key" }
    return value
  }
}

