package com.rabpit.backroom.core

import org.json.JSONObject

data class ProceduralLevelProfile(
  val id: String,
  val canonProfile: LevelCanonProfile,
  val generationConstraints: ProceduralGenerationConstraints,
  val metadata: Map<String, String> = emptyMap(),
  val schemaVersion: Int = ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION
)

data class ProceduralLevelProfileDocument(val path: String, val content: String)
data class ProceduralLevelProfileValidation(val valid: Boolean, val errors: List<String>)

object ProceduralLevelProfileJson {
  const val CURRENT_SCHEMA_VERSION = 1

  fun decode(raw: String): ProceduralLevelProfile {
    val json = JSONObject(raw)
    return ProceduralLevelProfile(
      id = json.optString("id"),
      canonProfile = LevelCanonProfileJson.decode(json.optJSONObject("canonProfile")),
      generationConstraints = ProceduralGenerationConstraintsJson.decode(json.optJSONObject("generationConstraints")),
      metadata = json.optJSONObject("metadata").stringsMap(),
      schemaVersion = json.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
    )
  }

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
    if (profile.schemaVersion != ProceduralLevelProfileJson.CURRENT_SCHEMA_VERSION) {
      errors += "unsupported_profile_schema:${profile.schemaVersion}"
    }
    if (id.isNotBlank() && !catalog.contains(id)) errors += "profile_level_not_in_catalog:$id"

    val constraints = profile.generationConstraints
    if (constraints.minZones < 1) errors += "profile_generation_min_zones_invalid"
    if (constraints.maxZones < maxOf(2, constraints.minZones)) errors += "profile_generation_zone_range_invalid"
    if (constraints.minEvidencePerRequiredFact < 1) errors += "profile_generation_evidence_count_invalid"
    if (constraints.minEvidenceSourceTypesPerRequiredFact < 1) errors += "profile_generation_evidence_sources_invalid"
    if (constraints.maxRequiredActions < 1) errors += "profile_generation_max_actions_invalid"

    val availableSources = if (constraints.allowSurvivors) EvidenceSource.values().size else EvidenceSource.values().count { it != EvidenceSource.SURVIVOR }
    if (constraints.minEvidenceSourceTypesPerRequiredFact > availableSources) {
      errors += "profile_generation_source_diversity_unreachable"
    }

    return ProceduralLevelProfileValidation(errors.isEmpty(), errors.distinct())
  }
}

object ProceduralLevelProfileCompiler {
  private const val FALLBACK_FACT = "PROFILE_EXIT_PATTERN_CONFIRMED"
  private const val FALLBACK_ACTION = "follow_profile_transition"

  fun compile(profile: ProceduralLevelProfile, catalog: LevelCatalog): LevelDefinition {
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
      reply = "Kai không tạo ra lối thoát. Cậu chỉ tiếp tục theo mẫu chuyển vùng đã được quan sát đủ bằng chứng."
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
  fun load(documents: Iterable<ProceduralLevelProfileDocument>, catalog: LevelCatalog): List<LevelDefinition> {
    val definitions = mutableListOf<LevelDefinition>()
    val failures = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    documents.forEach { document ->
      runCatching {
        val profile = ProceduralLevelProfileJson.decode(document.content)
        require(seen.add(profile.id)) { "duplicate_procedural_level_profile:${profile.id}" }
        ProceduralLevelProfileCompiler.compile(profile, catalog)
      }.onSuccess(definitions::add)
        .onFailure { failures += "${document.path}:${it.message ?: it::class.java.simpleName}" }
    }
    require(failures.isEmpty()) { "invalid_procedural_level_profiles:${failures.joinToString("|")}" }
    return definitions
  }
}
