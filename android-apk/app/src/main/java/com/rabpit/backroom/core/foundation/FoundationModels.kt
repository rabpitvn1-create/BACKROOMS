package com.rabpit.backroom.core.foundation

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class FoundationSection(val wireName: String) {
  CORE_RULES("core_rules"),
  WORLD_LEVEL("world_level"),
  STORY("story"),
  PARTY("party"),
  GAMEPLAY_CATALOG("gameplay_catalog"),
  NARRATIVE("narrative");

  companion object {
    fun fromWireName(value: String): FoundationSection = entries.first { it.wireName == value }
  }
}

enum class FoundationSliceRole(val wireName: String, val characterBudget: Int) {
  WRITER("writer", 10_400),
  CANON_AUDIT("canon_audit", 11_600),
  CHARACTER_AUDIT("character_audit", 10_400),
  REPAIR("repair", 7_200);

  companion object {
    @JvmStatic
    fun fromWireName(value: String): FoundationSliceRole = entries.firstOrNull {
      it.wireName.equals(value.trim(), ignoreCase = true)
    } ?: WRITER
  }
}

data class FoundationSource(
  val path: String,
  val sha256: String,
  val content: String
)

data class FoundationObject(
  val section: FoundationSection,
  val inputKey: String,
  val objectHash: String,
  val json: String
)

data class FoundationManifest(
  val manifestId: String,
  val sourcePackHash: String,
  val compilerVersion: String,
  val schemaVersion: Int,
  val createdAtEpochMs: Long,
  val objects: Map<FoundationSection, String>
) {
  fun toJson(): String = FoundationJson.canonical(JSONObject().apply {
    put("manifestId", manifestId)
    put("sourcePackHash", sourcePackHash)
    put("compilerVersion", compilerVersion)
    put("schemaVersion", schemaVersion)
    put("objects", JSONObject().apply {
      objects.entries.sortedBy { it.key.wireName }.forEach { (section, hash) -> put(section.wireName, hash) }
    })
  })

  companion object {
    fun fromJson(raw: String): FoundationManifest {
      val json = JSONObject(raw)
      val objectJson = json.getJSONObject("objects")
      val objects = linkedMapOf<FoundationSection, String>()
      objectJson.keys().asSequence().sorted().forEach { key ->
        objects[FoundationSection.fromWireName(key)] = objectJson.getString(key)
      }
      return FoundationManifest(
        manifestId = json.getString("manifestId"),
        sourcePackHash = json.getString("sourcePackHash"),
        compilerVersion = json.getString("compilerVersion"),
        schemaVersion = json.getInt("schemaVersion"),
        // Wall-clock diagnostics are intentionally outside identity-bearing content.
        createdAtEpochMs = json.optLong("createdAtEpochMs", 0L),
        objects = objects
      )
    }
  }
}

data class FoundationHandle(
  val manifest: FoundationManifest,
  val pinnedAtEpochMs: Long = System.currentTimeMillis()
)

object FoundationDigest {
  fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

  fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(value)
    .joinToString("") { "%02x".format(it) }
}

object FoundationJson {
  fun canonical(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") { key ->
      JSONObject.quote(key) + ":" + canonical(value.get(key))
    }
    is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
    is String -> JSONObject.quote(value)
    is Number, is Boolean -> value.toString()
    else -> JSONObject.quote(value.toString())
  }

  fun copySelected(source: JSONObject, vararg keys: String): JSONObject = JSONObject().apply {
    keys.sorted().forEach { key -> if (source.has(key)) put(key, source.get(key)) }
  }
}
