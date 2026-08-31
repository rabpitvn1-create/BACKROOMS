package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

enum class LevelKind { MAIN, SUBLEVEL, SPECIAL }

data class LevelTransition(
  val targetId: String,
  val tags: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap()
)

data class LevelCatalogEntry(
  val id: String,
  val parentId: String? = null,
  val name: String,
  val kind: LevelKind,
  val parentMainLevel: Int? = null,
  val campaignId: String? = null,
  val campaignOrder: Long? = null,
  val metadata: Map<String, String> = emptyMap(),
  val schemaVersion: Int = LevelCatalogJson.CURRENT_SCHEMA_VERSION,
  val outgoingTransitions: List<LevelTransition> = emptyList()
)

data class LevelCatalogDocument(val path: String, val content: String)

data class LevelCatalogValidation(val valid: Boolean, val errors: List<String>)

object LevelCatalogJson {
  const val CURRENT_SCHEMA_VERSION = 1

  fun decodeDocument(document: LevelCatalogDocument): List<LevelCatalogEntry> {
    val raw = document.content.trim()
    require(raw.isNotEmpty()) { "empty_level_catalog_document:${document.path}" }
    return when {
      raw.startsWith("[") -> decodeArray(JSONArray(raw), null, CURRENT_SCHEMA_VERSION)
      else -> {
        val root = JSONObject(raw)
        val entries = root.optJSONArray("entries")
        if (entries != null) {
          val campaignId = root.optString("campaignId").takeIf(String::isNotBlank)
          val schemaVersion = root.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
          decodeArray(entries, campaignId, schemaVersion)
        } else {
          listOf(decodeEntry(root, null, CURRENT_SCHEMA_VERSION))
        }
      }
    }
  }

  private fun decodeArray(array: JSONArray, inheritedCampaignId: String?, inheritedSchemaVersion: Int): List<LevelCatalogEntry> =
    (0 until array.length()).map { index ->
      val json = array.optJSONObject(index)
        ?: throw IllegalArgumentException("level_catalog_entry_not_object:$index")
      decodeEntry(json, inheritedCampaignId, inheritedSchemaVersion)
    }

  private fun decodeEntry(json: JSONObject, inheritedCampaignId: String?, inheritedSchemaVersion: Int): LevelCatalogEntry {
    val rawKind = json.optString("kind").trim().uppercase()
    val kind = runCatching { LevelKind.valueOf(rawKind) }
      .getOrElse { throw IllegalArgumentException("unknown_level_kind:$rawKind") }
    val parentMainLevel = if (json.has("parentMainLevel") && !json.isNull("parentMainLevel")) {
      json.getInt("parentMainLevel")
    } else null
    val campaignOrder = if (json.has("campaignOrder") && !json.isNull("campaignOrder")) {
      json.getLong("campaignOrder")
    } else null
    return LevelCatalogEntry(
      id = json.optString("id"),
      parentId = json.optString("parentId").takeIf(String::isNotBlank),
      name = json.optString("name"),
      kind = kind,
      parentMainLevel = parentMainLevel,
      campaignId = json.optString("campaignId").takeIf(String::isNotBlank) ?: inheritedCampaignId,
      campaignOrder = campaignOrder,
      metadata = json.optJSONObject("metadata").stringsMap(),
      outgoingTransitions = json.optJSONArray("outgoingTransitions").transitions(),
      schemaVersion = json.optInt("schemaVersion", inheritedSchemaVersion)
    )
  }

  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }


  private fun JSONArray?.transitions(): List<LevelTransition> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
      when (val value = get(index)) {
        is String -> LevelTransition(value)
        is JSONObject -> LevelTransition(
          targetId = value.optString("targetId"),
          tags = value.optJSONArray("tags").stringsSet(),
          metadata = value.optJSONObject("metadata").stringsMap()
        )
        else -> throw IllegalArgumentException("level_transition_not_string_or_object:$index")
      }
    }
  }

  private fun JSONArray?.stringsSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length()).map { getString(it) }.toCollection(linkedSetOf())
  }
}

object LevelCatalogValidator {
  fun validate(entry: LevelCatalogEntry): LevelCatalogValidation {
    val errors = mutableListOf<String>()
    if (entry.id.isBlank()) errors += "level_id_missing"
    if (entry.id.length > 128) errors += "level_id_too_long"
    if (entry.id.any { it == '/' || it == '\\' || it.isISOControl() }) errors += "level_id_invalid_character"
    if (entry.parentId == entry.id) errors += "level_parent_self_reference"
    if (entry.parentId?.any { it == '/' || it == '\\' || it.isISOControl() } == true) errors += "level_parent_invalid_character"
    if (entry.name.isBlank()) errors += "level_name_missing"
    if (entry.parentMainLevel != null && entry.parentMainLevel < 0) errors += "parent_main_level_invalid"
    if (entry.campaignOrder != null && entry.campaignOrder < 0L) errors += "campaign_order_invalid"
    if (entry.campaignOrder != null && entry.campaignId.isNullOrBlank()) errors += "campaign_id_missing_for_order"
    if (entry.schemaVersion != LevelCatalogJson.CURRENT_SCHEMA_VERSION) errors += "unsupported_schema_version:${entry.schemaVersion}"
    entry.outgoingTransitions.forEach { transition ->
      if (transition.targetId.isBlank()) errors += "transition_target_missing"
      if (transition.targetId == entry.id) errors += "transition_self_loop:${entry.id}"
      if (transition.targetId.any { it == '/' || it == '\\' || it.isISOControl() }) errors += "transition_target_invalid_character:${transition.targetId}"
    }
    val duplicateTargets = entry.outgoingTransitions.groupBy { it.targetId }.filterValues { it.size > 1 }.keys
    duplicateTargets.sorted().forEach { errors += "duplicate_transition:${entry.id}:$it" }
    return LevelCatalogValidation(errors.isEmpty(), errors)
  }
}

class LevelCatalog private constructor(private val entries: Map<String, LevelCatalogEntry>) {
  fun get(id: String): LevelCatalogEntry? = entries[id]
  fun require(id: String): LevelCatalogEntry = entries[id] ?: throw IllegalArgumentException("unknown_level_catalog_entry:$id")
  fun contains(id: String): Boolean = id in entries
  fun ids(): List<String> = entries.keys.sorted()
  fun childrenOf(parentId: String): List<LevelCatalogEntry> = entries.values.filter { it.parentId == parentId }.sortedBy { it.id }
  fun unresolvedParents(): Set<String> = entries.values.mapNotNull { it.parentId }.filterNot(entries::containsKey).toSet()
  fun campaign(campaignId: String): List<LevelCatalogEntry> = entries.values
    .filter { it.campaignId == campaignId && it.campaignOrder != null }
    .sortedWith(compareBy<LevelCatalogEntry> { it.campaignOrder }.thenBy { it.id })
  fun allowedTransitionsFrom(levelId: String): List<LevelTransition> =
    entries[levelId]?.outgoingTransitions.orEmpty()
  fun canTransition(fromId: String, toId: String): Boolean =
    allowedTransitionsFrom(fromId).any { it.targetId == toId }
  val size: Int get() = entries.size

  companion object {
    fun from(values: Iterable<LevelCatalogEntry>): LevelCatalog {
      val map = linkedMapOf<String, LevelCatalogEntry>()
      values.forEach { entry ->
        require(entry.id !in map) { "duplicate_level_catalog_entry:${entry.id}" }
        val validation = LevelCatalogValidator.validate(entry)
        require(validation.valid) { "invalid_level_catalog_entry:${entry.id}:${validation.errors.joinToString(",")}" }
        map[entry.id] = entry
      }
      map.values.groupBy { it.campaignId }.forEach { (campaignId, group) ->
        if (campaignId.isNullOrBlank()) return@forEach
        val ordered = group.filter { it.campaignOrder != null }
        val duplicateOrders = ordered.groupBy { it.campaignOrder }.filterValues { it.size > 1 }.keys
        require(duplicateOrders.isEmpty()) {
          "duplicate_campaign_order:$campaignId:${duplicateOrders.filterNotNull().sorted().joinToString(",")}" 
        }
      }
      map.values.forEach { source ->
        source.outgoingTransitions.forEach { transition ->
          val target = map[transition.targetId]
            ?: throw IllegalArgumentException("dangling_transition:${source.id}:${transition.targetId}")
          require(source.id != target.id) { "transition_self_loop:${source.id}" }
          val sourceCampaign = source.campaignId?.takeIf(String::isNotBlank)
          val targetCampaign = target.campaignId?.takeIf(String::isNotBlank)
          require(sourceCampaign != null && sourceCampaign == targetCampaign) {
            "transition_campaign_mismatch:${source.id}:${target.id}"
          }
          val sourceOrder = source.campaignOrder
          val targetOrder = target.campaignOrder
          require(sourceOrder != null && targetOrder != null && targetOrder > sourceOrder) {
            "transition_not_forward:${source.id}:${target.id}"
          }
        }
      }
      return LevelCatalog(map)
    }

    fun empty(): LevelCatalog = LevelCatalog(emptyMap())
  }
}

object LevelCatalogLoader {
  fun load(documents: Iterable<LevelCatalogDocument>): LevelCatalog {
    val entries = mutableListOf<LevelCatalogEntry>()
    val failures = mutableListOf<String>()
    documents.forEach { document ->
      runCatching { LevelCatalogJson.decodeDocument(document) }
        .onSuccess(entries::addAll)
        .onFailure { failures += "${document.path}:${it.message ?: it::class.java.simpleName}" }
    }
    require(failures.isEmpty()) { "level_catalog_load_failed:${failures.joinToString("|")}" }
    return LevelCatalog.from(entries)
  }
}
