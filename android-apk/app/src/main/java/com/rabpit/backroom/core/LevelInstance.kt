package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

enum class EvidenceSource { ENVIRONMENT, SEARCH, SURVIVOR, ANOMALY }

data class ZoneState(
  val id: String,
  val name: String,
  val connections: Set<String> = emptySet(),
  val tags: Set<String> = emptySet(),
  val properties: Map<String, String> = emptyMap()
)

data class WorldMutation(
  val id: String,
  val revision: Int,
  val kind: String,
  val targetId: String,
  val value: String
)

data class EvidenceState(
  val id: String,
  val supports: Set<String>,
  val sources: Set<EvidenceSource>,
  val zoneId: String? = null,
  val discoverConditions: Set<String> = emptySet(),
  val discovered: Boolean = false,
  val discoveredAtRevision: Int? = null
)

data class EscapeBlueprintState(
  val solutionId: String,
  val requiredFacts: Set<String>,
  val requiredActions: List<String>,
  val locked: Boolean = true
)

data class LevelInstanceState(
  val runSeed: String,
  val levelId: String,
  val generationId: String,
  val currentZoneId: String,
  val zones: Map<String, ZoneState>,
  val landmarks: Map<String, String> = emptyMap(),
  val environment: Map<String, String> = emptyMap(),
  val escapeBlueprint: EscapeBlueprintState,
  val evidence: Map<String, EvidenceState>,
  val npcKnowledge: Map<String, Set<String>> = emptyMap(),
  val discoveredFacts: Set<String> = emptySet(),
  val completedActions: List<String> = emptyList(),
  val mutations: List<WorldMutation> = emptyList(),
  val revision: Int = 1,
  val completed: Boolean = false
)

object LevelInstanceJson {
  fun encode(value: LevelInstanceState): JSONObject = JSONObject().apply {
    put("runSeed", value.runSeed)
    put("levelId", value.levelId)
    put("generationId", value.generationId)
    put("currentZoneId", value.currentZoneId)
    put("zones", JSONObject().apply { value.zones.forEach { (id, zone) -> put(id, encodeZone(zone)) } })
    put("landmarks", encodeStringMap(value.landmarks))
    put("environment", encodeStringMap(value.environment))
    put("escapeBlueprint", encodeBlueprint(value.escapeBlueprint))
    put("evidence", JSONObject().apply { value.evidence.forEach { (id, evidence) -> put(id, encodeEvidence(evidence)) } })
    put("npcKnowledge", JSONObject().apply { value.npcKnowledge.forEach { (id, facts) -> put(id, JSONArray(facts.sorted())) } })
    put("discoveredFacts", JSONArray(value.discoveredFacts.sorted()))
    put("completedActions", JSONArray(value.completedActions))
    put("mutations", JSONArray().apply { value.mutations.forEach { put(encodeMutation(it)) } })
    put("revision", value.revision)
    put("completed", value.completed)
  }

  fun decode(json: JSONObject): LevelInstanceState {
    val zonesJson = json.optJSONObject("zones") ?: JSONObject()
    val zones = linkedMapOf<String, ZoneState>()
    zonesJson.keys().forEach { id -> zonesJson.optJSONObject(id)?.let { zones[id] = decodeZone(it) } }

    val evidenceJson = json.optJSONObject("evidence") ?: JSONObject()
    val evidence = linkedMapOf<String, EvidenceState>()
    evidenceJson.keys().forEach { id -> evidenceJson.optJSONObject(id)?.let { evidence[id] = decodeEvidence(it) } }

    val knowledge = linkedMapOf<String, Set<String>>()
    json.optJSONObject("npcKnowledge")?.let { root ->
      root.keys().forEach { id -> knowledge[id] = root.optJSONArray(id).strings().toSet() }
    }

    return LevelInstanceState(
      runSeed = json.optString("runSeed"),
      levelId = json.optString("levelId"),
      generationId = json.optString("generationId"),
      currentZoneId = json.optString("currentZoneId"),
      zones = zones,
      landmarks = json.optJSONObject("landmarks").stringsMap(),
      environment = json.optJSONObject("environment").stringsMap(),
      escapeBlueprint = decodeBlueprint(json.optJSONObject("escapeBlueprint") ?: JSONObject()),
      evidence = evidence,
      npcKnowledge = knowledge,
      discoveredFacts = json.optJSONArray("discoveredFacts").strings().toSet(),
      completedActions = json.optJSONArray("completedActions").strings(),
      mutations = json.optJSONArray("mutations").objects().map(::decodeMutation),
      revision = json.optInt("revision", 1).coerceAtLeast(1),
      completed = json.optBoolean("completed", false)
    )
  }

  internal fun encodeZone(value: ZoneState) = JSONObject().apply {
    put("id", value.id)
    put("name", value.name)
    put("connections", JSONArray(value.connections.sorted()))
    put("tags", JSONArray(value.tags.sorted()))
    put("properties", encodeStringMap(value.properties))
  }

  internal fun decodeZone(json: JSONObject) = ZoneState(
    id = json.optString("id"),
    name = json.optString("name"),
    connections = json.optJSONArray("connections").strings().toSet(),
    tags = json.optJSONArray("tags").strings().toSet(),
    properties = json.optJSONObject("properties").stringsMap()
  )

  internal fun encodeBlueprint(value: EscapeBlueprintState) = JSONObject().apply {
    put("solutionId", value.solutionId)
    put("requiredFacts", JSONArray(value.requiredFacts.sorted()))
    put("requiredActions", JSONArray(value.requiredActions))
    put("locked", value.locked)
  }

  internal fun decodeBlueprint(json: JSONObject) = EscapeBlueprintState(
    solutionId = json.optString("solutionId"),
    requiredFacts = json.optJSONArray("requiredFacts").strings().toSet(),
    requiredActions = json.optJSONArray("requiredActions").strings(),
    locked = json.optBoolean("locked", true)
  )

  internal fun encodeEvidence(value: EvidenceState) = JSONObject().apply {
    put("id", value.id)
    put("supports", JSONArray(value.supports.sorted()))
    put("sources", JSONArray(value.sources.map { it.name }.sorted()))
    put("zoneId", value.zoneId ?: JSONObject.NULL)
    put("discoverConditions", JSONArray(value.discoverConditions.sorted()))
    put("discovered", value.discovered)
    put("discoveredAtRevision", value.discoveredAtRevision ?: JSONObject.NULL)
  }

  internal fun decodeEvidence(json: JSONObject) = EvidenceState(
    id = json.optString("id"),
    supports = json.optJSONArray("supports").strings().toSet(),
    sources = json.optJSONArray("sources").strings().mapNotNull { raw -> EvidenceSource.values().firstOrNull { it.name == raw } }.toSet(),
    zoneId = json.optString("zoneId").takeIf { it.isNotBlank() },
    discoverConditions = json.optJSONArray("discoverConditions").strings().toSet(),
    discovered = json.optBoolean("discovered", false),
    discoveredAtRevision = if (json.has("discoveredAtRevision") && !json.isNull("discoveredAtRevision")) json.optInt("discoveredAtRevision") else null
  )

  private fun encodeMutation(value: WorldMutation) = JSONObject().apply {
    put("id", value.id)
    put("revision", value.revision)
    put("kind", value.kind)
    put("targetId", value.targetId)
    put("value", value.value)
  }

  private fun decodeMutation(json: JSONObject) = WorldMutation(
    id = json.optString("id"),
    revision = json.optInt("revision", 1).coerceAtLeast(1),
    kind = json.optString("kind"),
    targetId = json.optString("targetId"),
    value = json.optString("value")
  )

  internal fun encodeStringMap(values: Map<String, String>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }

  internal fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }

  internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

  internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
}

data class BlueprintValidation(val valid: Boolean, val errors: List<String>)

object BlueprintValidator {
  fun validate(instance: LevelInstanceState): BlueprintValidation {
    val errors = mutableListOf<String>()
    if (instance.levelId.isBlank()) errors += "level_id_missing"
    if (instance.currentZoneId !in instance.zones) errors += "current_zone_missing"
    if (!instance.escapeBlueprint.locked) errors += "escape_blueprint_must_be_locked"
    if (instance.escapeBlueprint.requiredFacts.isEmpty()) errors += "required_facts_missing"
    if (instance.escapeBlueprint.requiredActions.isEmpty()) errors += "required_actions_missing"

    instance.zones.values.forEach { zone ->
      zone.connections.filterNot(instance.zones::containsKey).forEach { errors += "unknown_connection:${zone.id}:$it" }
    }

    instance.escapeBlueprint.requiredFacts.forEach { fact ->
      val supporting = instance.evidence.values.filter { fact in it.supports }
      if (supporting.size < 2) errors += "insufficient_evidence:$fact"
      if (supporting.flatMap { it.sources }.toSet().size < 2) errors += "insufficient_source_diversity:$fact"
    }

    val escapeZones = instance.zones.values.filter { "escape" in it.tags }.map { it.id }.toSet()
    if (escapeZones.isEmpty()) errors += "escape_zone_missing"
    else if (instance.currentZoneId in instance.zones && !reachable(instance, escapeZones)) errors += "escape_zone_unreachable"

    return BlueprintValidation(errors.isEmpty(), errors.distinct())
  }

  private fun reachable(instance: LevelInstanceState, targets: Set<String>): Boolean {
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(instance.currentZoneId)
    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()
      if (!seen.add(id)) continue
      if (id in targets) return true
      instance.zones[id]?.connections.orEmpty().filterNot(seen::contains).forEach(queue::addLast)
    }
    return false
  }
}
