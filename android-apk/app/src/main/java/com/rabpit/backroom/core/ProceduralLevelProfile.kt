package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class LevelCanonProfilePatch(
  val environmentTagsAdd: Set<String> = emptySet(),
  val environmentTagsRemove: Set<String> = emptySet(),
  val requiredZoneTagsAdd: Set<String> = emptySet(),
  val requiredZoneTagsRemove: Set<String> = emptySet(),
  val allowedPhenomenaAdd: Set<String> = emptySet(),
  val allowedPhenomenaRemove: Set<String> = emptySet(),
  val forbiddenClaimsAdd: Set<String> = emptySet(),
  val transitionTagsAdd: Set<String> = emptySet(),
  val transitionTagsRemove: Set<String> = emptySet(),
  val metadataSet: Map<String, String> = emptyMap(),
  val metadataRemove: Set<String> = emptySet()
) {
  fun apply(base: LevelCanonProfile): LevelCanonProfile = base.copy(
    environmentTags = (base.environmentTags - environmentTagsRemove) + environmentTagsAdd,
    requiredZoneTags = (base.requiredZoneTags - requiredZoneTagsRemove) + requiredZoneTagsAdd,
    allowedPhenomena = (base.allowedPhenomena - allowedPhenomenaRemove) + allowedPhenomenaAdd,
    forbiddenClaims = base.forbiddenClaims + forbiddenClaimsAdd,
    transitionTags = (base.transitionTags - transitionTagsRemove) + transitionTagsAdd,
    metadata = (base.metadata - metadataRemove) + metadataSet
  )

  fun isEmpty(): Boolean =
    environmentTagsAdd.isEmpty() && environmentTagsRemove.isEmpty() &&
      requiredZoneTagsAdd.isEmpty() && requiredZoneTagsRemove.isEmpty() &&
      allowedPhenomenaAdd.isEmpty() && allowedPhenomenaRemove.isEmpty() &&
      forbiddenClaimsAdd.isEmpty() && transitionTagsAdd.isEmpty() &&
      transitionTagsRemove.isEmpty() && metadataSet.isEmpty() && metadataRemove.isEmpty()
}

data class ProceduralGenerationConstraintsPatch(
  val minZones: Int? = null,
  val maxZones: Int? = null,
  val minEvidencePerRequiredFact: Int? = null,
  val minEvidenceSourceTypesPerRequiredFact: Int? = null,
  val maxRequiredActions: Int? = null,
  val allowSurvivors: Boolean? = null,
  val allowEntities: Boolean? = null,
  val proceduralTopology: Boolean? = null,
  val proceduralLandmarks: Boolean? = null,
  val proceduralEvidencePlacement: Boolean? = null,
  val proceduralEscapeBlueprint: Boolean? = null
) {
  fun apply(base: ProceduralGenerationConstraints): ProceduralGenerationConstraints = base.copy(
    minZones = minZones ?: base.minZones,
    maxZones = maxZones ?: base.maxZones,
    minEvidencePerRequiredFact = minEvidencePerRequiredFact ?: base.minEvidencePerRequiredFact,
    minEvidenceSourceTypesPerRequiredFact = minEvidenceSourceTypesPerRequiredFact ?: base.minEvidenceSourceTypesPerRequiredFact,
    maxRequiredActions = maxRequiredActions ?: base.maxRequiredActions,
    allowSurvivors = allowSurvivors ?: base.allowSurvivors,
    allowEntities = allowEntities ?: base.allowEntities,
    proceduralTopology = proceduralTopology ?: base.proceduralTopology,
    proceduralLandmarks = proceduralLandmarks ?: base.proceduralLandmarks,
    proceduralEvidencePlacement = proceduralEvidencePlacement ?: base.proceduralEvidencePlacement,
    proceduralEscapeBlueprint = proceduralEscapeBlueprint ?: base.proceduralEscapeBlueprint
  )

  fun isEmpty(): Boolean =
    minZones == null && maxZones == null && minEvidencePerRequiredFact == null &&
      minEvidenceSourceTypesPerRequiredFact == null && maxRequiredActions == null &&
      allowSurvivors == null && allowEntities == null && proceduralTopology == null &&
      proceduralLandmarks == null && proceduralEvidencePlacement == null && proceduralEscapeBlueprint == null
}

data class ProceduralLevelProfile(
  val id: String,
  val canonProfile: LevelCanonProfile = LevelCanonProfile(),
  val generationConstraints: ProceduralGenerationConstraints = ProceduralGenerationConstraints(),
  val metadata: Map<String, String> = emptyMap(),
  val schemaVersion: Int = ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION,
  val inheritsFrom: String? = null,
  val canonPatch: LevelCanonProfilePatch = LevelCanonProfilePatch(),
  val generationConstraintsPatch: ProceduralGenerationConstraintsPatch = ProceduralGenerationConstraintsPatch()
)

data class ProceduralLevelProfileDocument(val path: String, val content: String)
data class ProceduralLevelProfileValidation(val valid: Boolean, val errors: List<String>)

object ProceduralLevelProfileJson {
  const val LEGACY_SCHEMA_VERSION = 1
  const val CURRENT_SCHEMA_VERSION = 2

  fun decode(raw: String): ProceduralLevelProfile {
    val json = JSONObject(raw)
    return ProceduralLevelProfile(
      id = json.optString("id"),
      canonProfile = LevelCanonProfileJson.decode(json.optJSONObject("canonProfile")),
      generationConstraints = ProceduralGenerationConstraintsJson.decode(json.optJSONObject("generationConstraints")),
      metadata = json.optJSONObject("metadata").stringsMap(),
      schemaVersion = json.optInt("schemaVersion", LEGACY_SCHEMA_VERSION),
      inheritsFrom = json.optString("inheritsFrom").takeIf(String::isNotBlank),
      canonPatch = decodeCanonPatch(json.optJSONObject("canonPatch")),
      generationConstraintsPatch = decodeGenerationConstraintsPatch(json.optJSONObject("generationConstraintsPatch"))
    )
  }

  private fun decodeCanonPatch(json: JSONObject?): LevelCanonProfilePatch {
    if (json == null) return LevelCanonProfilePatch()
    val allowed = setOf(
      "environmentTagsAdd", "environmentTagsRemove",
      "requiredZoneTagsAdd", "requiredZoneTagsRemove",
      "allowedPhenomenaAdd", "allowedPhenomenaRemove",
      "forbiddenClaimsAdd",
      "transitionTagsAdd", "transitionTagsRemove",
      "metadataSet", "metadataRemove"
    )
    json.keys().forEach { key ->
      require(key in allowed) { "unknown_canon_patch_field:$key" }
    }
    return LevelCanonProfilePatch(
      environmentTagsAdd = json.optJSONArray("environmentTagsAdd").strings().toSet(),
      environmentTagsRemove = json.optJSONArray("environmentTagsRemove").strings().toSet(),
      requiredZoneTagsAdd = json.optJSONArray("requiredZoneTagsAdd").strings().toSet(),
      requiredZoneTagsRemove = json.optJSONArray("requiredZoneTagsRemove").strings().toSet(),
      allowedPhenomenaAdd = json.optJSONArray("allowedPhenomenaAdd").strings().toSet(),
      allowedPhenomenaRemove = json.optJSONArray("allowedPhenomenaRemove").strings().toSet(),
      forbiddenClaimsAdd = json.optJSONArray("forbiddenClaimsAdd").strings().toSet(),
      transitionTagsAdd = json.optJSONArray("transitionTagsAdd").strings().toSet(),
      transitionTagsRemove = json.optJSONArray("transitionTagsRemove").strings().toSet(),
      metadataSet = json.optJSONObject("metadataSet").stringsMap(),
      metadataRemove = json.optJSONArray("metadataRemove").strings().toSet()
    )
  }

  private fun decodeGenerationConstraintsPatch(json: JSONObject?): ProceduralGenerationConstraintsPatch {
    if (json == null) return ProceduralGenerationConstraintsPatch()
    val allowed = setOf(
      "minZones", "maxZones", "minEvidencePerRequiredFact", "minEvidenceSourceTypesPerRequiredFact",
      "maxRequiredActions", "allowSurvivors", "allowEntities", "proceduralTopology",
      "proceduralLandmarks", "proceduralEvidencePlacement", "proceduralEscapeBlueprint"
    )
    json.keys().forEach { key ->
      require(key in allowed) { "unknown_generation_constraints_patch_field:$key" }
    }
    fun intOrNull(key: String): Int? = if (json.has(key) && !json.isNull(key)) json.getInt(key) else null
    fun booleanOrNull(key: String): Boolean? = if (json.has(key) && !json.isNull(key)) json.getBoolean(key) else null
    return ProceduralGenerationConstraintsPatch(
      minZones = intOrNull("minZones"),
      maxZones = intOrNull("maxZones"),
      minEvidencePerRequiredFact = intOrNull("minEvidencePerRequiredFact"),
      minEvidenceSourceTypesPerRequiredFact = intOrNull("minEvidenceSourceTypesPerRequiredFact"),
      maxRequiredActions = intOrNull("maxRequiredActions"),
      allowSurvivors = booleanOrNull("allowSurvivors"),
      allowEntities = booleanOrNull("allowEntities"),
      proceduralTopology = booleanOrNull("proceduralTopology"),
      proceduralLandmarks = booleanOrNull("proceduralLandmarks"),
      proceduralEvidencePlacement = booleanOrNull("proceduralEvidencePlacement"),
      proceduralEscapeBlueprint = booleanOrNull("proceduralEscapeBlueprint")
    )
  }

  private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }
}

object ProceduralLevelProfileValidator {
  fun validate(profile: ProceduralLevelProfile, catalog: LevelCatalog): ProceduralLevelProfileValidation {
    val errors = mutableListOf<String>()
    val id = profile.id
    if (id.isBlank()) errors += "profile_level_id_missing"
    if (id.length > 128) errors += "profile_level_id_too_long"
    if (id.any { it == '/' || it == '\\' || it.isISOControl() }) errors += "profile_level_id_invalid_character"
    if (profile.schemaVersion !in ProceduralLevelProfileJson.LEGACY_SCHEMA_VERSION..ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION) {
      errors += "unsupported_profile_schema:${profile.schemaVersion}"
    }
    val entry = if (id.isNotBlank()) catalog.get(id) else null
    if (id.isNotBlank() && entry == null) errors += "profile_level_not_in_catalog:$id"

    val inheritedFrom = profile.inheritsFrom
    if (inheritedFrom != null) {
      if (profile.schemaVersion < ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION) {
        errors += "profile_inheritance_requires_schema_2"
      }
      if (inheritedFrom == id) errors += "profile_inheritance_self_reference"
      if (entry != null && entry.parentId != inheritedFrom) {
        errors += "profile_inheritance_must_match_catalog_parent:$id:$inheritedFrom:${entry.parentId.orEmpty()}"
      }
      validateCanonPatch(profile.canonPatch, errors)
    } else {
      if (!profile.canonPatch.isEmpty() || !profile.generationConstraintsPatch.isEmpty()) {
        errors += "profile_patch_requires_inheritance"
      }
      validateConstraints(profile.generationConstraints, errors)
    }

    return ProceduralLevelProfileValidation(errors.isEmpty(), errors.distinct())
  }

  private fun validateCanonPatch(patch: LevelCanonProfilePatch, errors: MutableList<String>) {
    fun rejectOverlap(name: String, add: Set<String>, remove: Set<String>) {
      add.intersect(remove).sorted().forEach { errors += "profile_canon_patch_conflict:$name:$it" }
    }
    rejectOverlap("environmentTags", patch.environmentTagsAdd, patch.environmentTagsRemove)
    rejectOverlap("requiredZoneTags", patch.requiredZoneTagsAdd, patch.requiredZoneTagsRemove)
    rejectOverlap("allowedPhenomena", patch.allowedPhenomenaAdd, patch.allowedPhenomenaRemove)
    rejectOverlap("transitionTags", patch.transitionTagsAdd, patch.transitionTagsRemove)
    patch.metadataSet.keys.intersect(patch.metadataRemove).sorted().forEach {
      errors += "profile_canon_patch_conflict:metadata:$it"
    }
  }

  internal fun validateConstraints(constraints: ProceduralGenerationConstraints, errors: MutableList<String>) {
    if (constraints.minZones < 1) errors += "profile_generation_min_zones_invalid"
    if (constraints.maxZones < maxOf(2, constraints.minZones)) errors += "profile_generation_zone_range_invalid"
    if (constraints.minEvidencePerRequiredFact < 1) errors += "profile_generation_evidence_count_invalid"
    if (constraints.minEvidenceSourceTypesPerRequiredFact < 1) errors += "profile_generation_evidence_sources_invalid"
    if (constraints.maxRequiredActions < 1) errors += "profile_generation_max_actions_invalid"

    val availableSources = if (constraints.allowSurvivors) {
      EvidenceSource.values().size
    } else {
      EvidenceSource.values().count { it != EvidenceSource.SURVIVOR }
    }
    if (constraints.minEvidenceSourceTypesPerRequiredFact > availableSources) {
      errors += "profile_generation_source_diversity_unreachable"
    }
  }
}

object ProceduralLevelProfileResolver {
  /**
   * Resolve inheritance with an explicit stack so a valid content chain can be thousands of Levels
   * deep without consuming the JVM call stack. The resolved map is also the per-load cache: every
   * parent is merged at most once.
   */
  fun resolveAll(
    profiles: Iterable<ProceduralLevelProfile>,
    catalog: LevelCatalog,
    baseDefinitions: Map<String, LevelDefinition> = emptyMap()
  ): List<ProceduralLevelProfile> {
    val rawById = linkedMapOf<String, ProceduralLevelProfile>()
    profiles.forEach { profile ->
      require(rawById.put(profile.id, profile) == null) { "duplicate_procedural_level_profile:${profile.id}" }
    }
    val resolved = linkedMapOf<String, ProceduralLevelProfile>()

    fun fromDefinition(definition: LevelDefinition): ProceduralLevelProfile = ProceduralLevelProfile(
      id = definition.id,
      canonProfile = definition.canonProfile,
      generationConstraints = definition.generationConstraints,
      metadata = definition.metadata,
      schemaVersion = ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION
    )

    fun validateRaw(raw: ProceduralLevelProfile) {
      val validation = ProceduralLevelProfileValidator.validate(raw, catalog)
      require(validation.valid) {
        "invalid_procedural_level_profile:${raw.id}:${validation.errors.joinToString(",")}" 
      }
    }

    fun merge(raw: ProceduralLevelProfile, base: ProceduralLevelProfile): ProceduralLevelProfile {
      val mergedConstraints = raw.generationConstraintsPatch.apply(base.generationConstraints)
      val constraintErrors = mutableListOf<String>()
      ProceduralLevelProfileValidator.validateConstraints(mergedConstraints, constraintErrors)
      require(constraintErrors.isEmpty()) {
        "invalid_inherited_generation_constraints:${raw.id}:${constraintErrors.joinToString(",")}" 
      }
      return raw.copy(
        canonProfile = raw.canonPatch.apply(base.canonProfile),
        generationConstraints = mergedConstraints,
        metadata = raw.metadata + mapOf("profileInheritedFrom" to raw.inheritsFrom.orEmpty()),
        schemaVersion = ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION,
        inheritsFrom = null,
        canonPatch = LevelCanonProfilePatch(),
        generationConstraintsPatch = ProceduralGenerationConstraintsPatch()
      )
    }

    fun resolve(startId: String) {
      if (startId in resolved) return
      val chain = mutableListOf<ProceduralLevelProfile>()
      val positions = hashMapOf<String, Int>()
      var currentId = startId
      var base: ProceduralLevelProfile? = null

      while (true) {
        resolved[currentId]?.let {
          base = it
          break
        }
        val previous = positions[currentId]
        if (previous != null) {
          val cycle = (chain.subList(previous, chain.size).map { it.id } + currentId).joinToString("->")
          throw IllegalArgumentException("procedural_profile_inheritance_cycle:$cycle")
        }

        positions[currentId] = chain.size
        val raw = rawById[currentId]
          ?: throw IllegalArgumentException("procedural_profile_missing:$currentId")
        validateRaw(raw)
        chain += raw
        val parentId = raw.inheritsFrom
        if (parentId == null) {
          base = null
          break
        }

        val parentProfile = rawById[parentId]
        if (parentProfile != null) {
          currentId = parentId
          continue
        }
        base = baseDefinitions[parentId]?.let(::fromDefinition)
          ?: throw IllegalArgumentException("procedural_profile_inheritance_source_missing:${raw.id}:$parentId")
        break
      }

      var effectiveBase = base
      for (index in chain.indices.reversed()) {
        val raw = chain[index]
        val effective = if (raw.inheritsFrom == null) {
          raw
        } else {
          merge(raw, effectiveBase ?: throw IllegalArgumentException(
            "procedural_profile_inheritance_source_missing:${raw.id}:${raw.inheritsFrom}"
          ))
        }
        val effectiveValidation = ProceduralLevelProfileValidator.validate(effective, catalog)
        require(effectiveValidation.valid) {
          "invalid_resolved_procedural_level_profile:${effective.id}:${effectiveValidation.errors.joinToString(",")}" 
        }
        resolved[raw.id] = effective
        effectiveBase = effective
      }
    }

    rawById.keys.forEach(::resolve)
    return rawById.keys.map(resolved::getValue)
  }
}

object ProceduralLevelProfileCompiler {
  private const val FALLBACK_FACT = "PROFILE_EXIT_PATTERN_CONFIRMED"
  private const val FALLBACK_ACTION = "follow_profile_transition"

  fun compile(profile: ProceduralLevelProfile, catalog: LevelCatalog): LevelDefinition {
    require(profile.inheritsFrom == null) { "unresolved_procedural_level_profile:${profile.id}" }
    val validation = ProceduralLevelProfileValidator.validate(profile, catalog)
    require(validation.valid) {
      "invalid_procedural_level_profile:${profile.id}:${validation.errors.joinToString(",")}" 
    }

    val entry = catalog.require(profile.id)
    val constraints = profile.generationConstraints
    val canon = profile.canonProfile
    val zoneCount = maxOf(2, constraints.minZones)
    val zoneIds = (0 until zoneCount).map { index ->
      when (index) {
        0 -> "profile_entry"
        zoneCount - 1 -> "profile_transition"
        else -> "profile_zone_$index"
      }
    }

    val tags = MutableList(zoneCount) { linkedSetOf<String>() }
    tags.first() += "entry"
    tags.last() += "escape"
    canon.requiredZoneTags.sorted().forEachIndexed { index, tag ->
      val target = when (tag) {
        "entry" -> 0
        "escape" -> zoneCount - 1
        else -> index % zoneCount
      }
      tags[target] += tag
    }

    val zones = linkedMapOf<String, ZoneState>()
    zoneIds.forEachIndexed { index, id ->
      val connections = linkedSetOf<String>()
      if (index > 0) connections += zoneIds[index - 1]
      if (index + 1 < zoneCount) connections += zoneIds[index + 1]
      val role = when (index) {
        0 -> "entry"
        zoneCount - 1 -> "transition"
        else -> "survey"
      }
      zones[id] = ZoneState(
        id = id,
        name = when (role) {
          "entry" -> "${entry.name} Entry"
          "transition" -> "${entry.name} Transition"
          else -> "${entry.name} Survey $index"
        },
        connections = connections,
        tags = tags[index],
        properties = mapOf("profileFallbackRole" to role)
      )
    }

    val sources = EvidenceSource.values().filter { constraints.allowSurvivors || it != EvidenceSource.SURVIVOR }
    val evidenceCount = maxOf(
      constraints.minEvidencePerRequiredFact,
      constraints.minEvidenceSourceTypesPerRequiredFact
    )
    val evidence = linkedMapOf<String, EvidenceState>()
    repeat(evidenceCount) { index ->
      val source = sources[index % sources.size]
      val zoneId = zoneIds[1 + (index % (zoneCount - 1))]
      val id = "profile_evidence_${index + 1}"
      evidence[id] = EvidenceState(
        id = id,
        supports = setOf(FALLBACK_FACT),
        sources = setOf(source),
        zoneId = zoneId,
        discoverConditions = if (source == EvidenceSource.SEARCH) emptySet() else setOf("visit:$zoneId:1")
      )
    }

    val survivorEvidence = evidence.values.filter { EvidenceSource.SURVIVOR in it.sources }.map { it.id }.toSet()
    val npcKnowledge = if (survivorEvidence.isEmpty()) emptyMap() else mapOf("profile_survivor" to survivorEvidence)
    val transitionSummary = canon.transitionTags.sorted().joinToString(", ") { humanize(it) }.ifBlank { "một mẫu chuyển vùng nhất quán" }
    val environmentSummary = canon.environmentTags.sorted().take(6).joinToString(", ") { humanize(it) }.ifBlank { "môi trường đặc trưng của Level" }

    val action = LevelActionRule(
      id = FALLBACK_ACTION,
      matchGroups = listOf(
        setOf("đi tiếp", "tiếp tục", "theo lối", "bám theo"),
        setOf("chuyển vùng", "dấu hiệu", "lối chuyển", "hướng chuyển")
      ),
      conditions = setOf("zone:${zoneIds.last()}", "fact:$FALLBACK_FACT"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      reply = "Kai không tạo ra lối thoát. Cậu chỉ tiếp tục theo mẫu chuyển vùng đã được quan sát đủ bằng chứng.",
      semanticDescriptions = setOf("tiếp tục theo dấu hiệu chuyển vùng đã quan sát")
    )

    val replies = linkedMapOf<String, String>()
    replies["search:empty"] = "Không có thêm chi tiết mới đủ để thay đổi kết luận trong trạng thái hiện tại."
    replies["search:exhausted"] = "Khu vực đã được rà đủ trong trạng thái hiện tại; lặp lại không tạo thêm tiến triển."
    replies["explore:moved"] = "Kai đi sâu hơn vào {zone}."
    replies["execute:conditions_missing"] = "Giả thuyết chưa đủ bằng chứng hoặc Kai chưa ở đúng vùng chuyển tiếp."
    replies["execute:no_progress"] = "Hành động không khớp với chuỗi thoát đã khóa của Level này."
    evidence.keys.forEachIndexed { index, id ->
      replies["evidence:$id"] = if (index == 0) {
        "Dấu vết đầu tiên cho thấy $environmentSummary đang dần hội tụ về $transitionSummary."
      } else {
        "Một nguồn độc lập khác củng cố cùng mẫu chuyển vùng: $transitionSummary."
      }
    }

    return LevelDefinition(
      id = entry.id,
      parentId = entry.parentId,
      name = entry.name,
      initialZoneId = zoneIds.first(),
      zones = zones,
      landmarks = if (canon.transitionTags.isEmpty()) emptyMap() else mapOf("profile_transition_pattern" to transitionSummary),
      environment = mapOf("profileFallback" to "true"),
      escapeBlueprint = EscapeBlueprintState(
        solutionId = "profile-fallback:${entry.id}",
        requiredFacts = setOf(FALLBACK_FACT),
        requiredActions = listOf(FALLBACK_ACTION),
        locked = true
      ),
      evidence = evidence,
      npcKnowledge = npcKnowledge,
      exploreRoute = zoneIds.drop(1),
      actions = mapOf(FALLBACK_ACTION to action),
      replies = replies,
      canonProfile = canon,
      generationConstraints = constraints,
      metadata = profile.metadata + mapOf(
        "definitionSource" to "procedural-profile",
        "fallbackGenerator" to "generic-profile-v1"
      )
    ).also { definition ->
      val definitionValidation = LevelDefinitionValidator.validate(definition)
      require(definitionValidation.valid) {
        "invalid_compiled_profile_definition:${profile.id}:${definitionValidation.errors.joinToString(",")}" 
      }
    }
  }

  private fun humanize(value: String): String = value.replace('_', ' ').trim()
}

object ProceduralLevelProfileLoader {
  fun load(
    documents: Iterable<ProceduralLevelProfileDocument>,
    catalog: LevelCatalog,
    baseDefinitions: Map<String, LevelDefinition> = emptyMap()
  ): List<LevelDefinition> {
    val profiles = mutableListOf<ProceduralLevelProfile>()
    val failures = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    documents.forEach { document ->
      runCatching {
        val profile = ProceduralLevelProfileJson.decode(document.content)
        require(seen.add(profile.id)) { "duplicate_procedural_level_profile:${profile.id}" }
        profile
      }.onSuccess(profiles::add)
        .onFailure { failures += "${document.path}:${it.message ?: it::class.java.simpleName}" }
    }
    require(failures.isEmpty()) { "invalid_procedural_level_profiles:${failures.joinToString("|")}" }

    val resolved = runCatching {
      ProceduralLevelProfileResolver.resolveAll(profiles, catalog, baseDefinitions)
    }.getOrElse { error ->
      throw IllegalArgumentException("invalid_procedural_level_profiles:${error.message ?: error::class.java.simpleName}", error)
    }
    return resolved.map { ProceduralLevelProfileCompiler.compile(it, catalog) }
  }
}
