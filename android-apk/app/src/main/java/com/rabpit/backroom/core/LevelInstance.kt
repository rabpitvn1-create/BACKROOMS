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
  val environmentTags: Set<String> = emptySet(),
  val phenomena: Set<String> = emptySet(),
  val canonClaims: Set<String> = emptySet(),
  val escapeBlueprint: EscapeBlueprintState,
  val evidence: Map<String, EvidenceState>,
  val npcKnowledge: Map<String, Set<String>> = emptyMap(),
  val exploreRoute: List<String> = emptyList(),
  val actions: Map<String, LevelActionRule> = emptyMap(),
  val replies: Map<String, String> = emptyMap(),
  val generatorVersion: String = "legacy",
  val generationSchemaVersion: Int = 1,
  val generationFingerprint: String? = null,
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
    put("environmentTags", JSONArray(value.environmentTags.sorted()))
    put("phenomena", JSONArray(value.phenomena.sorted()))
    put("canonClaims", JSONArray(value.canonClaims.sorted()))
    put("escapeBlueprint", encodeBlueprint(value.escapeBlueprint))
    put("evidence", JSONObject().apply { value.evidence.forEach { (id, evidence) -> put(id, encodeEvidence(evidence)) } })
    put("npcKnowledge", JSONObject().apply { value.npcKnowledge.forEach { (id, facts) -> put(id, JSONArray(facts.sorted())) } })
    put("exploreRoute", JSONArray(value.exploreRoute))
    put("actions", JSONObject().apply { value.actions.forEach { (id, action) -> put(id, encodeAction(action)) } })
    put("replies", encodeStringMap(value.replies))
    put("generatorVersion", value.generatorVersion)
    put("generationSchemaVersion", value.generationSchemaVersion)
    put("generationFingerprint", value.generationFingerprint ?: JSONObject.NULL)
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

    val actions = linkedMapOf<String, LevelActionRule>()
    json.optJSONObject("actions")?.let { root ->
      root.keys().forEach { id -> root.optJSONObject(id)?.let { actions[id] = decodeAction(it) } }
    }

    return LevelInstanceState(
      runSeed = json.optString("runSeed"),
      levelId = json.optString("levelId"),
      generationId = json.optString("generationId"),
      currentZoneId = json.optString("currentZoneId"),
      zones = zones,
      landmarks = json.optJSONObject("landmarks").stringsMap(),
      environment = json.optJSONObject("environment").stringsMap(),
      environmentTags = json.optJSONArray("environmentTags").strings().toSet(),
      phenomena = json.optJSONArray("phenomena").strings().toSet(),
      canonClaims = json.optJSONArray("canonClaims").strings().toSet(),
      escapeBlueprint = decodeBlueprint(json.optJSONObject("escapeBlueprint") ?: JSONObject()),
      evidence = evidence,
      npcKnowledge = knowledge,
      exploreRoute = json.optJSONArray("exploreRoute").strings(),
      actions = actions,
      replies = json.optJSONObject("replies").stringsMap(),
      generatorVersion = json.optString("generatorVersion").ifBlank { "legacy" },
      generationSchemaVersion = json.optInt("generationSchemaVersion", 1).coerceAtLeast(1),
      generationFingerprint = json.optString("generationFingerprint").takeIf(String::isNotBlank),
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

  internal fun encodeAction(value: LevelActionRule) = JSONObject().apply {
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

  internal fun decodeAction(json: JSONObject): LevelActionRule {
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

  internal fun JSONArray?.arrays(): List<JSONArray> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONArray)
}

data class BlueprintValidation(val valid: Boolean, val errors: List<String>)

object BlueprintValidator {
  fun validate(instance: LevelInstanceState): BlueprintValidation = validateInternal(
    instance = instance,
    definition = null,
    minEvidence = 2,
    minSources = 2,
    maxActions = Int.MAX_VALUE
  )

  fun validate(instance: LevelInstanceState, definition: LevelDefinition): BlueprintValidation {
    val constraints = definition.generationConstraints
    return validateInternal(
      instance = instance,
      definition = definition,
      minEvidence = constraints.minEvidencePerRequiredFact,
      minSources = constraints.minEvidenceSourceTypesPerRequiredFact,
      maxActions = constraints.maxRequiredActions
    )
  }

  private fun validateInternal(
    instance: LevelInstanceState,
    definition: LevelDefinition?,
    minEvidence: Int,
    minSources: Int,
    maxActions: Int
  ): BlueprintValidation {
    val errors = mutableListOf<String>()
    if (instance.levelId.isBlank()) errors += "level_id_missing"
    if (instance.currentZoneId !in instance.zones) errors += "current_zone_missing"
    if (!instance.escapeBlueprint.locked) errors += "escape_blueprint_must_be_locked"
    if (instance.escapeBlueprint.requiredFacts.isEmpty()) errors += "required_facts_missing"
    if (instance.escapeBlueprint.requiredActions.isEmpty()) errors += "required_actions_missing"
    if (instance.escapeBlueprint.requiredActions.size > maxActions) errors += "required_actions_exceed_limit"
    if (instance.generatorVersion.isBlank()) errors += "generator_version_missing"
    if (instance.generationSchemaVersion < 1) errors += "generation_schema_invalid"

    instance.zones.values.forEach { zone ->
      if (zone.id.isBlank()) errors += "zone_id_missing"
      if (zone.name.isBlank()) errors += "zone_name_missing:${zone.id}"
      zone.connections.filterNot(instance.zones::containsKey).forEach { errors += "unknown_connection:${zone.id}:$it" }
    }

    instance.evidence.values.forEach { evidence ->
      if (evidence.id.isBlank()) errors += "evidence_id_missing"
      if (evidence.sources.isEmpty()) errors += "evidence_source_missing:${evidence.id}"
      if (evidence.supports.isEmpty()) errors += "evidence_support_missing:${evidence.id}"
      if (evidence.zoneId != null && evidence.zoneId !in instance.zones) errors += "unknown_evidence_zone:${evidence.id}:${evidence.zoneId}"
    }

    instance.escapeBlueprint.requiredFacts.forEach { fact ->
      val supporting = instance.evidence.values.filter { fact in it.supports }
      if (supporting.size < minEvidence) errors += "insufficient_evidence:$fact"
      if (supporting.flatMap { it.sources }.toSet().size < minSources) errors += "insufficient_source_diversity:$fact"
    }

    val escapeZones = instance.zones.values.filter { "escape" in it.tags }.map { it.id }.toSet()
    if (escapeZones.isEmpty()) errors += "escape_zone_missing"
    else if (instance.currentZoneId in instance.zones && !reachable(instance, escapeZones)) errors += "escape_zone_unreachable"

    if (definition != null) validateAgainstDefinition(instance, definition, errors)
    return BlueprintValidation(errors.isEmpty(), errors.distinct())
  }

  private fun validateAgainstDefinition(instance: LevelInstanceState, definition: LevelDefinition, errors: MutableList<String>) {
    val constraints = definition.generationConstraints
    val canon = definition.canonProfile
    if (instance.levelId != definition.id) errors += "candidate_level_mismatch:${instance.levelId}:${definition.id}"
    if (instance.zones.size < constraints.minZones) errors += "generated_zone_count_below_min"
    if (instance.zones.size > constraints.maxZones) errors += "generated_zone_count_above_max"
    if (instance.generatorVersion != "legacy" && !instance.environmentTags.containsAll(canon.environmentTags)) {
      canon.environmentTags.filterNot(instance.environmentTags::contains).forEach { errors += "missing_canon_environment_tag:$it" }
    }
    val presentZoneTags = instance.zones.values.flatMap { it.tags }.toSet()
    canon.requiredZoneTags.filterNot(presentZoneTags::contains).forEach { errors += "missing_required_zone_tag:$it" }
    instance.phenomena.filterNot(canon.allowedPhenomena::contains).forEach { errors += "forbidden_phenomenon:$it" }
    instance.canonClaims.intersect(canon.forbiddenClaims).forEach { errors += "forbidden_canon_claim:$it" }
    if (!constraints.allowSurvivors) {
      if (instance.npcKnowledge.isNotEmpty()) errors += "survivor_knowledge_forbidden"
      if (instance.evidence.values.any { EvidenceSource.SURVIVOR in it.sources }) errors += "survivor_evidence_forbidden"
    }

    validateRuntimeRules(instance, errors)
    validateSolvability(instance, constraints.minEvidencePerRequiredFact, constraints.minEvidenceSourceTypesPerRequiredFact, errors)
  }

  private fun validateRuntimeRules(instance: LevelInstanceState, errors: MutableList<String>) {
    instance.exploreRoute.filterNot(instance.zones::containsKey).forEach { errors += "unknown_explore_zone:$it" }
    instance.escapeBlueprint.requiredActions.forEach { actionId ->
      if (actionId !in instance.actions) errors += "missing_action_rule:$actionId"
    }
    val knownFacts = instance.escapeBlueprint.requiredFacts + instance.evidence.values.flatMap { it.supports }
    instance.evidence.values.forEach { evidence ->
      evidence.discoverConditions.forEach { validateCondition(instance, knownFacts, it, errors, "evidence:${evidence.id}") }
    }
    instance.actions.values.forEach { action ->
      if (action.id.isBlank()) errors += "action_id_missing"
      if (action.matchGroups.isEmpty() || action.matchGroups.any { it.isEmpty() }) errors += "action_matcher_missing:${action.id}"
      action.conditions.forEach { validateCondition(instance, knownFacts, it, errors, "action:${action.id}") }
      action.effects.forEach { effect ->
        when (effect.type) {
          LevelEffectType.SET_ENVIRONMENT -> {
            if (effect.key.isNullOrBlank()) errors += "effect_key_missing:${action.id}"
            if (effect.value == null) errors += "effect_value_missing:${action.id}"
          }
          LevelEffectType.MOVE_TO_ZONE -> if (effect.zoneId !in instance.zones) errors += "effect_zone_unknown:${action.id}:${effect.zoneId.orEmpty()}"
          LevelEffectType.COMPLETE_LEVEL -> Unit
        }
      }
    }
  }

  private fun validateCondition(
    instance: LevelInstanceState,
    knownFacts: Set<String>,
    condition: String,
    errors: MutableList<String>,
    owner: String
  ) {
    when {
      condition.startsWith("visit:") -> {
        val parts = condition.split(':')
        val zone = parts.getOrNull(1).orEmpty()
        val count = parts.getOrNull(2)?.toIntOrNull()
        if (zone !in instance.zones || count == null || count < 1) errors += "invalid_condition:$owner:$condition"
      }
      condition.startsWith("env:") -> {
        val body = condition.substringAfter("env:")
        if (!body.contains('=') || body.substringBefore('=').isBlank()) errors += "invalid_condition:$owner:$condition"
      }
      condition.startsWith("zone:") -> if (condition.substringAfter("zone:") !in instance.zones) errors += "invalid_condition:$owner:$condition"
      condition.startsWith("action:") -> if (condition.substringAfter("action:") !in instance.actions) errors += "invalid_condition:$owner:$condition"
      condition.startsWith("fact:") -> if (condition.substringAfter("fact:") !in knownFacts) errors += "invalid_condition:$owner:$condition"
      else -> errors += "unsupported_condition:$owner:$condition"
    }
  }

  private fun validateSolvability(
    instance: LevelInstanceState,
    minEvidence: Int,
    minSources: Int,
    errors: MutableList<String>
  ) {
    if (instance.currentZoneId !in instance.zones) return
    val maxBudget = 5000
    var evaluationCount = 0

    val reachableZones = mutableSetOf<String>()
    fun expandReachableZones(startZoneId: String) {
      val queue = ArrayDeque<String>()
      queue.add(startZoneId)
      while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!reachableZones.add(id)) continue
        instance.zones[id]?.connections.orEmpty().filterNot(reachableZones::contains).forEach(queue::addLast)
      }
    }

    expandReachableZones(instance.currentZoneId)

    val environment = instance.environment.toMutableMap()
    val completedActions = linkedSetOf<String>()
    val discoveredEvidence = mutableSetOf<String>()
    val discoveredFacts = linkedSetOf<String>()

    val allKnownFacts = (instance.escapeBlueprint.requiredFacts + instance.evidence.values.flatMap { it.supports }).toSet()

    fun conditionMet(condition: String): Boolean = when {
      condition.startsWith("visit:") -> {
        val parts = condition.split(':')
        val zone = parts.getOrNull(1).orEmpty()
        val count = parts.getOrNull(2)?.toIntOrNull() ?: 1
        zone in reachableZones && count >= 1
      }
      condition.startsWith("zone:") -> condition.substringAfter("zone:") in reachableZones
      condition.startsWith("env:") -> {
        val body = condition.substringAfter("env:")
        val key = body.substringBefore('=')
        val value = body.substringAfter('=', missingDelimiterValue = "")
        environment[key] == value
      }
      condition.startsWith("action:") -> condition.substringAfter("action:") in completedActions
      condition.startsWith("fact:") -> condition.substringAfter("fact:") in discoveredFacts
      else -> false
    }

    fun revealFactsAndEvidence(): Boolean {
      var changed = false
      do {
        evaluationCount++
        if (evaluationCount > maxBudget) {
          if (!errors.contains("validation_budget_exceeded")) {
            errors += "validation_budget_exceeded"
          }
          return false
        }
        var innerChanged = false
        instance.evidence.values.forEach { evidence ->
          if (evidence.id in discoveredEvidence) return@forEach
          if (evidence.zoneId != null && evidence.zoneId !in reachableZones) return@forEach
          if (!evidence.discoverConditions.all(::conditionMet)) return@forEach
          discoveredEvidence += evidence.id
          innerChanged = true
        }

        allKnownFacts.forEach { fact ->
          if (fact in discoveredFacts) return@forEach
          val supporting = instance.evidence.values.filter { it.id in discoveredEvidence && fact in it.supports }
          val sources = supporting.flatMap { it.sources }.toSet()
          if (supporting.size >= minEvidence && sources.size >= minSources) {
            discoveredFacts += fact
            innerChanged = true
          }
        }

        if (innerChanged) changed = true
      } while (innerChanged)
      return changed
    }

    revealFactsAndEvidence()
    if (errors.contains("validation_budget_exceeded")) return

    var completionPossible = false
    val requiredActions = instance.escapeBlueprint.requiredActions

    for (i in requiredActions.indices) {
      val actionId = requiredActions[i]
      val rule = instance.actions[actionId]
      if (rule == null) {
        errors += "missing_action_rule:$actionId"
        break
      }

      var actionConditionsMet = true
      for (condition in rule.conditions) {
        if (condition.startsWith("action:")) {
          val reqAct = condition.substringAfter("action:")
          if (reqAct !in completedActions) {
            actionConditionsMet = false
            break
          }
        } else if (!conditionMet(condition)) {
          actionConditionsMet = false
          break
        }
      }

      if (!actionConditionsMet) {
        errors += "required_action_unreachable:$actionId"
        break
      }

      completedActions += actionId

      rule.effects.forEach { effect ->
        when (effect.type) {
          LevelEffectType.SET_ENVIRONMENT -> {
            if (!effect.key.isNullOrBlank() && effect.value != null) {
              environment[effect.key] = effect.value
            }
          }
          LevelEffectType.MOVE_TO_ZONE -> {
            effect.zoneId?.let { zoneId ->
              if (zoneId in instance.zones) {
                expandReachableZones(zoneId)
              }
            }
          }
          LevelEffectType.COMPLETE_LEVEL -> completionPossible = true
        }
      }

      revealFactsAndEvidence()
      if (errors.contains("validation_budget_exceeded")) return
    }

    revealFactsAndEvidence()
    if (errors.contains("validation_budget_exceeded")) return

    instance.escapeBlueprint.requiredFacts.filterNot(discoveredFacts::contains).forEach {
      errors += "required_fact_unreachable:$it"
    }

    if (!completionPossible) errors += "completion_effect_missing"
  }

  private fun reachable(instance: LevelInstanceState, targets: Set<String>): Boolean =
    reachableZones(instance).any(targets::contains)

  private fun reachableZones(instance: LevelInstanceState): Set<String> {
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(instance.currentZoneId)
    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()
      if (!seen.add(id)) continue
      instance.zones[id]?.connections.orEmpty().filterNot(seen::contains).forEach(queue::addLast)
    }
    return seen
  }
}
