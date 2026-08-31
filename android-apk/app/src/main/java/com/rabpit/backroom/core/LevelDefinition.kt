package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class LevelDefinition(
  val id: String,
  val parentId: String? = null,
  val name: String,
  val initialZoneId: String,
  val zones: Map<String, ZoneState>,
  val landmarks: Map<String, String> = emptyMap(),
  val environment: Map<String, String> = emptyMap(),
  val escapeBlueprint: EscapeBlueprintState,
  val evidence: Map<String, EvidenceState>,
  val npcKnowledge: Map<String, Set<String>> = emptyMap(),
  val exploreRoute: List<String> = emptyList(),
  val actions: Map<String, LevelActionRule> = emptyMap(),
  val replies: Map<String, String> = emptyMap(),
  val canonProfile: LevelCanonProfile = LevelCanonProfile(),
  val generationConstraints: ProceduralGenerationConstraints = ProceduralGenerationConstraints(),
  val metadata: Map<String, String> = emptyMap(),
  val schemaVersion: Int = 1
)

data class LevelActionRule(
  val id: String,
  val matchGroups: List<Set<String>>,
  val conditions: Set<String> = emptySet(),
  val effects: List<LevelEffect> = emptyList(),
  val reply: String? = null
)

enum class LevelEffectType { SET_ENVIRONMENT, MOVE_TO_ZONE, COMPLETE_LEVEL }

data class LevelEffect(
  val type: LevelEffectType,
  val key: String? = null,
  val value: String? = null,
  val zoneId: String? = null
)

data class LevelDefinitionValidation(val valid: Boolean, val errors: List<String>)

data class LevelDefinitionDocument(val path: String, val content: String)

object LevelDefinitionJson {
  const val CURRENT_SCHEMA_VERSION = 1

  fun encode(value: LevelDefinition): JSONObject = JSONObject().apply {
    put("schemaVersion", value.schemaVersion)
    put("id", value.id)
    if (value.parentId != null) put("parentId", value.parentId)
    put("name", value.name)
    put("initialZoneId", value.initialZoneId)
    put("zones", JSONArray().apply { value.zones.values.forEach { put(encodeZone(it)) } })
    put("landmarks", stringMap(value.landmarks))
    put("environment", stringMap(value.environment))
    put("escapeBlueprint", encodeBlueprint(value.escapeBlueprint))
    put("evidence", JSONArray().apply { value.evidence.values.forEach { put(encodeEvidence(it)) } })
    put("npcKnowledge", JSONObject().apply { value.npcKnowledge.forEach { (id, facts) -> put(id, JSONArray(facts.sorted())) } })
    put("exploreRoute", JSONArray(value.exploreRoute))
    put("actions", JSONArray().apply { value.actions.values.forEach { put(encodeAction(it)) } })
    put("replies", stringMap(value.replies))
    put("canonProfile", LevelCanonProfileJson.encode(value.canonProfile))
    put("generationConstraints", ProceduralGenerationConstraintsJson.encode(value.generationConstraints))
    put("metadata", stringMap(value.metadata))
  }

  fun decode(raw: String): LevelDefinition = decode(JSONObject(raw))

  fun decode(json: JSONObject): LevelDefinition {
    val zones = linkedMapOf<String, ZoneState>()
    json.optJSONArray("zones").objects().forEach { zoneJson ->
      val zone = decodeZone(zoneJson)
      require(zone.id.isNotBlank()) { "zone_id_missing" }
      require(zone.id !in zones) { "duplicate_zone:${zone.id}" }
      zones[zone.id] = zone
    }

    val evidence = linkedMapOf<String, EvidenceState>()
    json.optJSONArray("evidence").objects().forEach { evidenceJson ->
      val item = decodeEvidence(evidenceJson)
      require(item.id.isNotBlank()) { "evidence_id_missing" }
      require(item.id !in evidence) { "duplicate_evidence:${item.id}" }
      evidence[item.id] = item
    }

    val actions = linkedMapOf<String, LevelActionRule>()
    json.optJSONArray("actions").objects().forEach { actionJson ->
      val action = decodeAction(actionJson)
      require(action.id.isNotBlank()) { "action_id_missing" }
      require(action.id !in actions) { "duplicate_action:${action.id}" }
      actions[action.id] = action
    }

    val knowledge = linkedMapOf<String, Set<String>>()
    json.optJSONObject("npcKnowledge")?.let { root ->
      root.keys().forEach { id -> knowledge[id] = root.optJSONArray(id).strings().toSet() }
    }

    return LevelDefinition(
      id = json.optString("id"),
      parentId = json.optString("parentId").takeIf(String::isNotBlank),
      name = json.optString("name"),
      initialZoneId = json.optString("initialZoneId"),
      zones = zones,
      landmarks = json.optJSONObject("landmarks").stringsMap(),
      environment = json.optJSONObject("environment").stringsMap(),
      escapeBlueprint = decodeBlueprint(json.optJSONObject("escapeBlueprint") ?: JSONObject()),
      evidence = evidence,
      npcKnowledge = knowledge,
      exploreRoute = json.optJSONArray("exploreRoute").strings(),
      actions = actions,
      replies = json.optJSONObject("replies").stringsMap(),
      canonProfile = LevelCanonProfileJson.decode(json.optJSONObject("canonProfile")),
      generationConstraints = ProceduralGenerationConstraintsJson.decode(json.optJSONObject("generationConstraints")),
      metadata = json.optJSONObject("metadata").stringsMap(),
      schemaVersion = json.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
    )
  }

  private fun encodeZone(value: ZoneState) = JSONObject().apply {
    put("id", value.id)
    put("name", value.name)
    put("connections", JSONArray(value.connections.sorted()))
    put("tags", JSONArray(value.tags.sorted()))
    put("properties", stringMap(value.properties))
  }

  private fun decodeZone(json: JSONObject) = ZoneState(
    id = json.optString("id"),
    name = json.optString("name"),
    connections = json.optJSONArray("connections").strings().toSet(),
    tags = json.optJSONArray("tags").strings().toSet(),
    properties = json.optJSONObject("properties").stringsMap()
  )

  private fun encodeBlueprint(value: EscapeBlueprintState) = JSONObject().apply {
    put("solutionId", value.solutionId)
    put("requiredFacts", JSONArray(value.requiredFacts.sorted()))
    put("requiredActions", JSONArray(value.requiredActions))
    put("locked", value.locked)
  }

  private fun decodeBlueprint(json: JSONObject) = EscapeBlueprintState(
    solutionId = json.optString("solutionId"),
    requiredFacts = json.optJSONArray("requiredFacts").strings().toSet(),
    requiredActions = json.optJSONArray("requiredActions").strings(),
    locked = json.optBoolean("locked", true)
  )

  private fun encodeEvidence(value: EvidenceState) = JSONObject().apply {
    put("id", value.id)
    put("supports", JSONArray(value.supports.sorted()))
    put("sources", JSONArray(value.sources.map { it.name }.sorted()))
    if (value.zoneId != null) put("zoneId", value.zoneId)
    put("discoverConditions", JSONArray(value.discoverConditions.sorted()))
  }

  private fun decodeEvidence(json: JSONObject) = EvidenceState(
    id = json.optString("id"),
    supports = json.optJSONArray("supports").strings().toSet(),
    sources = json.optJSONArray("sources").strings().map { raw ->
      runCatching { EvidenceSource.valueOf(raw) }.getOrElse { throw IllegalArgumentException("unknown_evidence_source:$raw") }
    }.toSet(),
    zoneId = json.optString("zoneId").takeIf(String::isNotBlank),
    discoverConditions = json.optJSONArray("discoverConditions").strings().toSet()
  )

  private fun encodeAction(value: LevelActionRule) = JSONObject().apply {
    put("id", value.id)
    put("matchGroups", JSONArray().apply { value.matchGroups.forEach { put(JSONArray(it.sorted())) } })
    put("conditions", JSONArray(value.conditions.sorted()))
    put("effects", JSONArray().apply { value.effects.forEach { effect ->
      put(JSONObject().apply {
        put("type", effect.type.name)
        effect.key?.let { put("key", it) }
        effect.value?.let { put("value", it) }
        effect.zoneId?.let { put("zoneId", it) }
      })
    } })
    value.reply?.let { put("reply", it) }
  }

  private fun decodeAction(json: JSONObject): LevelActionRule {
    val groups = json.optJSONArray("matchGroups").arrays().map { it.strings().toSet() }
    val effects = json.optJSONArray("effects").objects().map { effect ->
      val rawType = effect.optString("type")
      val type = runCatching { LevelEffectType.valueOf(rawType) }
        .getOrElse { throw IllegalArgumentException("unknown_level_effect:$rawType") }
      LevelEffect(
        type = type,
        key = effect.optString("key").takeIf(String::isNotBlank),
        value = effect.optString("value").takeIf(String::isNotBlank),
        zoneId = effect.optString("zoneId").takeIf(String::isNotBlank)
      )
    }
    return LevelActionRule(
      id = json.optString("id"),
      matchGroups = groups,
      conditions = json.optJSONArray("conditions").strings().toSet(),
      effects = effects,
      reply = json.optString("reply").takeIf(String::isNotBlank)
    )
  }

  private fun stringMap(values: Map<String, String>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }

  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }

  private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

  private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

  private fun JSONArray?.arrays(): List<JSONArray> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONArray)
}

object LevelDefinitionValidator {
  fun validate(definition: LevelDefinition): LevelDefinitionValidation {
    val errors = mutableListOf<String>()
    val id = definition.id
    if (id.isBlank()) errors += "level_id_missing"
    if (id.length > 128) errors += "level_id_too_long"
    if (id.any { it == '/' || it == '\\' || it.isISOControl() }) errors += "level_id_invalid_character"
    if (definition.parentId == id) errors += "level_parent_self_reference"
    if (definition.name.isBlank()) errors += "level_name_missing"
    if (definition.schemaVersion != LevelDefinitionJson.CURRENT_SCHEMA_VERSION) errors += "unsupported_schema_version:${definition.schemaVersion}"
    if (definition.initialZoneId !in definition.zones) errors += "initial_zone_missing"

    val constraints = definition.generationConstraints
    if (constraints.minZones < 1) errors += "generation_min_zones_invalid"
    if (constraints.maxZones < constraints.minZones) errors += "generation_zone_range_invalid"
    if (constraints.minEvidencePerRequiredFact < 1) errors += "generation_evidence_count_invalid"
    if (constraints.minEvidenceSourceTypesPerRequiredFact < 1) errors += "generation_evidence_sources_invalid"
    if (constraints.maxRequiredActions < 1) errors += "generation_max_actions_invalid"

    definition.zones.values.forEach { zone ->
      if (zone.name.isBlank()) errors += "zone_name_missing:${zone.id}"
      zone.connections.filterNot(definition.zones::containsKey).forEach { errors += "unknown_connection:${zone.id}:$it" }
    }

    definition.exploreRoute.filterNot(definition.zones::containsKey).forEach { errors += "unknown_explore_zone:$it" }
    validateRoute(definition, errors)

    definition.evidence.values.forEach { evidence ->
      if (evidence.sources.isEmpty()) errors += "evidence_source_missing:${evidence.id}"
      if (evidence.supports.isEmpty()) errors += "evidence_support_missing:${evidence.id}"
      if (evidence.zoneId != null && evidence.zoneId !in definition.zones) errors += "unknown_evidence_zone:${evidence.id}:${evidence.zoneId}"
      evidence.discoverConditions.forEach { validateCondition(definition, it, errors, "evidence:${evidence.id}") }
    }

    definition.escapeBlueprint.requiredActions.forEach { actionId ->
      if (actionId !in definition.actions) errors += "missing_action_rule:$actionId"
    }

    definition.actions.values.forEach { action ->
      if (action.matchGroups.isEmpty() || action.matchGroups.any { it.isEmpty() }) errors += "action_matcher_missing:${action.id}"
      action.conditions.forEach { validateCondition(definition, it, errors, "action:${action.id}") }
      action.effects.forEach { effect ->
        when (effect.type) {
          LevelEffectType.SET_ENVIRONMENT -> {
            if (effect.key.isNullOrBlank()) errors += "effect_key_missing:${action.id}"
            if (effect.value == null) errors += "effect_value_missing:${action.id}"
          }
          LevelEffectType.MOVE_TO_ZONE -> if (effect.zoneId !in definition.zones) errors += "effect_zone_unknown:${action.id}:${effect.zoneId.orEmpty()}"
          LevelEffectType.COMPLETE_LEVEL -> Unit
        }
      }
    }

    val instance = LevelInstanceState(
      runSeed = "definition-validation",
      levelId = definition.id,
      generationId = "definition-validation:${definition.id}",
      currentZoneId = definition.initialZoneId,
      zones = definition.zones,
      landmarks = definition.landmarks,
      environment = definition.environment,
      escapeBlueprint = definition.escapeBlueprint.copy(locked = true),
      evidence = definition.evidence,
      npcKnowledge = definition.npcKnowledge,
      exploreRoute = definition.exploreRoute,
      actions = definition.actions,
      replies = definition.replies
    )
    errors += BlueprintValidator.validate(instance, definition).errors
    return LevelDefinitionValidation(errors.isEmpty(), errors.distinct())
  }

  private fun validateRoute(definition: LevelDefinition, errors: MutableList<String>) {
    if (definition.exploreRoute.isEmpty() || definition.initialZoneId !in definition.zones) return
    var from = definition.initialZoneId
    definition.exploreRoute.forEachIndexed { index, to ->
      if (to in definition.zones && to !in definition.zones[from].orEmpty().connections) {
        errors += "explore_route_disconnected:$index:$from:$to"
      }
      from = to
    }
  }

  private fun validateCondition(definition: LevelDefinition, condition: String, errors: MutableList<String>, owner: String) {
    when {
      condition.startsWith("visit:") -> {
        val parts = condition.split(':')
        val zone = parts.getOrNull(1).orEmpty()
        val count = parts.getOrNull(2)?.toIntOrNull()
        if (zone !in definition.zones || count == null || count < 1) errors += "invalid_condition:$owner:$condition"
      }
      condition.startsWith("env:") -> {
        val body = condition.substringAfter("env:")
        if (!body.contains('=') || body.substringBefore('=').isBlank()) errors += "invalid_condition:$owner:$condition"
      }
      condition.startsWith("zone:") -> if (condition.substringAfter("zone:") !in definition.zones) errors += "invalid_condition:$owner:$condition"
      condition.startsWith("action:") -> if (condition.substringAfter("action:") !in definition.actions) errors += "invalid_condition:$owner:$condition"
      condition.startsWith("fact:") -> {
        val fact = condition.substringAfter("fact:")
        val knownFacts = definition.escapeBlueprint.requiredFacts + definition.evidence.values.flatMap { it.supports }
        if (fact !in knownFacts) errors += "invalid_condition:$owner:$condition"
      }
      else -> errors += "unsupported_condition:$owner:$condition"
    }
  }

  private fun ZoneState?.orEmpty(): ZoneState = this ?: ZoneState("", "")
}

class LevelRegistry private constructor(private val definitions: Map<String, LevelDefinition>) {
  fun get(id: String): LevelDefinition? = definitions[id]
  fun require(id: String): LevelDefinition = definitions[id] ?: throw IllegalArgumentException("unknown_level:$id")
  fun contains(id: String): Boolean = id in definitions
  fun ids(): List<String> = definitions.keys.sorted()
  fun childrenOf(parentId: String): List<LevelDefinition> = definitions.values.filter { it.parentId == parentId }.sortedBy { it.id }
  fun unresolvedParents(): Set<String> = definitions.values.mapNotNull { it.parentId }.filterNot(definitions::containsKey).toSet()
  val size: Int get() = definitions.size

  companion object {
    fun from(definitions: Iterable<LevelDefinition>): LevelRegistry {
      val map = linkedMapOf<String, LevelDefinition>()
      definitions.forEach { definition ->
        require(definition.id !in map) { "duplicate_level:${definition.id}" }
        val validation = LevelDefinitionValidator.validate(definition)
        require(validation.valid) { "invalid_level:${definition.id}:${validation.errors.joinToString(",")}" }
        map[definition.id] = definition
      }
      return LevelRegistry(map)
    }

    fun empty(): LevelRegistry = LevelRegistry(emptyMap())
  }
}

object LevelRegistryLoader {
  fun load(documents: Iterable<LevelDefinitionDocument>): LevelRegistry {
    val definitions = mutableListOf<LevelDefinition>()
    val failures = mutableListOf<String>()
    documents.forEach { document ->
      runCatching { LevelDefinitionJson.decode(document.content) }
        .onSuccess(definitions::add)
        .onFailure { failures += "${document.path}:${it.message ?: it::class.java.simpleName}" }
    }
    require(failures.isEmpty()) { "invalid_level_documents:${failures.joinToString("|")}" }
    return LevelRegistry.from(definitions)
  }
}

object GenericLevelGenerator {
  fun generate(definition: LevelDefinition, seed: String): LevelInstanceState =
    LevelInstanceGenerator.fromDefinition(definition, seed)
}
