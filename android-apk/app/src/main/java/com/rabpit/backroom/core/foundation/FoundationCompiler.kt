package com.rabpit.backroom.core.foundation

import org.json.JSONArray
import org.json.JSONObject

class FoundationCompiler {
  companion object {
    const val SCHEMA_VERSION = 1
    const val COMPILER_VERSION = "foundation-compiler-r01"

    private val KNOWLEDGE_DOMAINS = mapOf(
      "GAME" to FoundationSection.CORE_RULES,
      "WORLD" to FoundationSection.WORLD_LEVEL,
      "LEVEL" to FoundationSection.WORLD_LEVEL,
      "STORY" to FoundationSection.STORY,
      "CHARACTER" to FoundationSection.PARTY,
      "RELATIONSHIP" to FoundationSection.PARTY,
      "ADDRESS" to FoundationSection.PARTY,
      "ITEM" to FoundationSection.GAMEPLAY_CATALOG,
      "ENTITY" to FoundationSection.GAMEPLAY_CATALOG,
      "WRITING" to FoundationSection.NARRATIVE
    )
  }

  data class Build(
    val sourcePackHash: String,
    val objects: List<FoundationObject>
  )

  fun compile(sources: List<FoundationSource>, stateProjectionJson: String): Build {
    require(sources.map { it.path }.distinct().size == sources.size) { "Duplicate Foundation source path" }
    sources.forEach { source ->
      require(FoundationDigest.sha256(source.content) == source.sha256) { "Foundation source hash mismatch: ${source.path}" }
    }
    val sourcesSorted = sources.sortedBy { it.path }
    val projection = runCatching { JSONObject(stateProjectionJson) }.getOrElse { JSONObject() }
    val sourcePackHash = FoundationDigest.sha256(sourcesSorted.joinToString("\n") { "${it.path}:${it.sha256}" })
    val sectionRecords = FoundationSection.entries.associateWith { mutableListOf<JSONObject>() }
    val sectionDocuments = FoundationSection.entries.associateWith { mutableListOf<Pair<FoundationSource, Any>>() }

    sourcesSorted.forEach { source ->
      val parsed = runCatching { JSONObject(source.content) }.getOrNull()
      if (source.path.startsWith("knowledge/") && parsed?.optJSONArray("records") != null) {
        val records = parsed.getJSONArray("records")
        for (index in 0 until records.length()) {
          val record = records.optJSONObject(index) ?: continue
          val section = KNOWLEDGE_DOMAINS[record.optString("domain").uppercase()]
            ?: FoundationSection.CORE_RULES
          sectionRecords.getValue(section).add(JSONObject(record.toString()))
        }
      } else {
        val section = sectionForPath(source.path)
        val content: Any = parsed ?: runCatching { JSONArray(source.content) }.getOrNull() ?: source.content
        sectionDocuments.getValue(section).add(source to content)
      }
    }

    val objects = FoundationSection.entries.map { section ->
      val records = sectionRecords.getValue(section).sortedBy { it.optString("id") }
      val documents = sectionDocuments.getValue(section).sortedBy { it.first.path }
      val relevantProjection = projectionFor(section, projection)
      val contributions = JSONArray().apply {
        records.forEach { record ->
          put(JSONObject()
            .put("kind", "record")
            .put("id", record.optString("id"))
            .put("sha256", FoundationDigest.sha256(FoundationJson.canonical(record))))
        }
        documents.forEach { (source, content) ->
          put(JSONObject()
            .put("kind", "document")
            .put("path", source.path)
            .put("sha256", FoundationDigest.sha256(FoundationJson.canonical(content))))
        }
      }
      val inputKey = FoundationDigest.sha256(
        FoundationJson.canonical(JSONObject()
          .put("schemaVersion", SCHEMA_VERSION)
          .put("compilerVersion", COMPILER_VERSION)
          .put("section", section.wireName)
          .put("contributions", contributions)
          .put("stateProjection", relevantProjection))
      )
      val body = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("compilerVersion", COMPILER_VERSION)
        .put("section", section.wireName)
        .put("inputKey", inputKey)
        .put("stateProjection", relevantProjection)
        .put("records", JSONArray().apply { records.forEach(::put) })
        .put("documents", JSONArray().apply {
          documents.forEach { (source, content) ->
            put(JSONObject().put("path", source.path).put("sha256", source.sha256).put("content", content))
          }
        })
      val canonical = FoundationJson.canonical(body)
      FoundationObject(section, inputKey, FoundationDigest.sha256(canonical), canonical)
    }
    return Build(sourcePackHash, objects)
  }

  fun manifest(build: Build, createdAtEpochMs: Long = System.currentTimeMillis()): FoundationManifest {
    val objectMap = build.objects.associate { it.section to it.objectHash }
    require(objectMap.size == FoundationSection.entries.size) { "Foundation build is incomplete" }
    val identity = FoundationJson.canonical(JSONObject()
      .put("schemaVersion", SCHEMA_VERSION)
      .put("compilerVersion", COMPILER_VERSION)
      .put("sourcePackHash", build.sourcePackHash)
      .put("objects", JSONObject().apply {
        objectMap.entries.sortedBy { it.key.wireName }.forEach { (section, hash) -> put(section.wireName, hash) }
      }))
    return FoundationManifest(
      manifestId = FoundationDigest.sha256(identity),
      sourcePackHash = build.sourcePackHash,
      compilerVersion = COMPILER_VERSION,
      schemaVersion = SCHEMA_VERSION,
      createdAtEpochMs = createdAtEpochMs,
      objects = objectMap
    )
  }

  private fun sectionForPath(path: String): FoundationSection = when {
    path.startsWith("campaign_story/") -> FoundationSection.STORY
    path.startsWith("level_catalog/") || path.startsWith("level_profiles/") || path.startsWith("levels/") -> FoundationSection.WORLD_LEVEL
    path.contains("item", ignoreCase = true) || path.contains("entity", ignoreCase = true) -> FoundationSection.GAMEPLAY_CATALOG
    else -> FoundationSection.CORE_RULES
  }

  private fun projectionFor(section: FoundationSection, projection: JSONObject): JSONObject = when (section) {
    FoundationSection.CORE_RULES -> FoundationJson.copySelected(projection, "saveVersion", "projectionSchemaVersion")
    FoundationSection.WORLD_LEVEL -> FoundationJson.copySelected(projection, "level", "world")
    FoundationSection.STORY -> FoundationJson.copySelected(projection, "story")
    FoundationSection.PARTY -> FoundationJson.copySelected(projection, "party", "characters")
    FoundationSection.GAMEPLAY_CATALOG -> FoundationJson.copySelected(projection, "inventory", "equipment", "statuses")
    FoundationSection.NARRATIVE -> JSONObject()
  }
}
