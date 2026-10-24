package com.rabpit.backroom.core.canon

import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil

/**
 * Canon Runtime 2.0 — Record v2 (ARCHITECTURE §3.1, design_mapping §1, §2.1).
 *
 * Model dữ liệu chuẩn của một thẻ Canon: nhận diện (id/universe/entity), phân loại
 * (domain/kind/authority/mutability giữ dạng String tự do như dữ liệu thật — KHÔNG ép
 * enum đóng, vì kind trong knowledge_db.json là chuỗi tự do), trạng thái sự thật
 * (truthStatus/knowledgeVisibility/spoilerPolicy là enum vì đó là trục điều khiển
 * cổng 0 của §6.2), điều kiện kích hoạt, typed edge, hard rule và provenance.
 *
 * File này chỉ chứa model + parser JSONObject -> CanonRecord. Parser fail-fast:
 * thiếu id, id rỗng hoặc trùng id đều ném lỗi rõ ràng (Gate 0 "không được phá" —
 * dữ liệu hỏng phải lộ ra ở bước nạp, không được nuốt thành record rỗng).
 * Việc suy field v2 từ dữ liệu v1 (knowledge_db.json) tuân theo bảng ánh xạ ở
 * design_mapping §1; mọi giá trị suy diễn đều ghi rõ nguồn gốc trong comment.
 */
enum class TruthStatus { CONFIRMED, RUMORED, INFERRED, UNKNOWN, CONTRADICTED, DEPRECATED_SOURCE }

enum class KnowledgeVisibility { PUBLIC, ACTOR_AWARE, RESTRICTED, WORLD, META }

enum class SpoilerPolicy { SHOW_IF_RELEVANT, HIDE_UNTIL_DISCLOSED, NEVER_SHOW }

/** Một typed edge (§3.2): quan hệ có kiểu từ record này tới [targetId]. */
data class CanonEdge(
  val type: EdgeType,
  val targetId: String
)

/** Hard rule dạng dữ liệu (§3.1). Đợt này dữ liệu v1 chưa có -> luôn null. */
data class CanonHardRule(
  val predicate: String,
  val effect: String
)

/**
 * Provenance của record (§3.1 source). [documentId]/[documentPath] trỏ tài liệu gốc;
 * [contentSha256]/[approvedRevision] chỉ có khi pack builder (Gate 1) đã chạy —
 * dữ liệu v1 chưa có hai field này nên parser để null thay vì bịa.
 */
data class CanonSourceRef(
  val documentId: String,
  val documentPath: String? = null,
  val anchor: String,
  val contentSha256: String? = null,
  val approvedRevision: String? = null
)

/**
 * Điều kiện kích hoạt (§3.1 activation).
 * [requireActorPresentOrSubject] là entityId (vd "CHAR.LUC_TRAM"); tầng store/resolver
 * ánh xạ entityId -> actor id lõi ("luc-tram") khi kiểm tra cổng 0.
 */
data class CanonActivation(
  val anySceneIntent: Set<String> = emptySet(),
  val requireActorPresentOrSubject: String? = null,
  val levelScope: String = LEVEL_SCOPE_ANY,
  val minEvidence: TruthStatus = TruthStatus.CONFIRMED,
  val denyIfUnauthorizedPov: Boolean = true
) {
  companion object { const val LEVEL_SCOPE_ANY = "ANY" }
}

data class CanonRecord(
  val id: String,
  val schemaVersion: Int = RECORD_SCHEMA_VERSION,
  val universeId: String = CanonUniverse.BACKROOMS_MAIN,
  val domain: String,
  val entityId: String?,
  val kind: String,
  val authority: String,
  val mutability: String,
  val truthStatus: TruthStatus = TruthStatus.CONFIRMED,
  val knowledgeVisibility: KnowledgeVisibility = KnowledgeVisibility.ACTOR_AWARE,
  val knownBy: Set<String> = emptySet(),
  val spoilerPolicy: SpoilerPolicy = SpoilerPolicy.SHOW_IF_RELEVANT,
  val text: String,
  val compactForm: String? = null,
  val aliases: List<String> = emptyList(),
  val tags: Set<String> = emptySet(),
  val affordances: Set<String> = emptySet(),
  val activation: CanonActivation = CanonActivation(),
  val references: List<CanonEdge> = emptyList(),
  val hardRule: CanonHardRule? = null,
  val source: CanonSourceRef,
  val priority: Int = 80
) {
  /**
   * Ước lượng token khớp semantics engine cũ (estimateTokens(text) + 8, mẫu số 4.0).
   * Ghi chú: design_mapping §2.5 đề xuất CanonTokens (mẫu số 3.5) thuộc file
   * ContextCompiler; file này giữ parity với engine hiện hành để độc lập biên dịch,
   * việc hợp nhất estimator do tầng compiler quyết định.
   */
  val estimatedTokens: Int get() = ceil(text.length / 4.0).toInt().coerceAtLeast(1) + 8

  companion object {
    const val RECORD_SCHEMA_VERSION = 2

    /**
     * Parse một record từ JSONObject. Chấp nhận cả khóa v2 snake_case (theo §3.1)
     * lẫn khóa v1 của knowledge_db.json hiện hành (source.document -> documentId).
     * Fail-fast: thiếu/rỗng id, domain, kind, text, authority, mutability hoặc source.
     */
    fun fromJson(json: JSONObject): CanonRecord {
      val id = json.optString("id").trim()
      require(id.isNotEmpty()) { "Canon record is missing required field: id" }
      val domain = required(json, "domain", id)
      val kind = required(json, "kind", id)
      val text = required(json, "text", id).trim()
      val authority = required(json, "authority", id)
      val mutability = required(json, "mutability", id)
      val sourceJson = json.optJSONObject("source")
        ?: throw IllegalArgumentException("Canon record $id is missing required field: source")

      val tags = stringSet(json.optJSONArray("tags"))
      val affordances = stringSet(json.optJSONArray("affordances"))
      val explicitEntityId = json.optString("entity_id").ifBlank { json.optString("entityId") }
      val entityId: String? = explicitEntityId.takeIf { it.isNotBlank() } ?: deriveEntityId(id)
      val truthStatus = parseEnum<TruthStatus>(json, id, "truth_status", "truthStatus") ?: TruthStatus.CONFIRMED
      val activation = parseActivation(json.optJSONObject("activation"), entityId, tags, affordances)
      val sourceDocumentId = sourceJson.optString("document_id")
        .ifBlank { sourceJson.optString("documentId") }
        .ifBlank { sourceJson.optString("document") }
      require(sourceDocumentId.isNotBlank()) { "Canon record $id source is missing document id" }

      return CanonRecord(
        id = id,
        schemaVersion = json.optInt("schema_version", json.optInt("schemaVersion", RECORD_SCHEMA_VERSION)),
        universeId = json.optString("universe_id")
          .ifBlank { json.optString("universeId") }
          .ifBlank { CanonUniverse.BACKROOMS_MAIN },
        domain = domain,
        entityId = entityId,
        kind = kind,
        authority = authority,
        mutability = mutability,
        truthStatus = truthStatus,
        knowledgeVisibility = parseEnum<KnowledgeVisibility>(json, id, "knowledge_visibility", "knowledgeVisibility")
          ?: KnowledgeVisibility.ACTOR_AWARE,
        knownBy = stringSet(json.optJSONArray("known_by") ?: json.optJSONArray("knownBy")),
        spoilerPolicy = parseEnum<SpoilerPolicy>(json, id, "spoiler_policy", "spoilerPolicy")
          ?: SpoilerPolicy.SHOW_IF_RELEVANT,
        text = text,
        compactForm = json.optString("compact_form").ifBlank { json.optString("compactForm") }
          .takeIf { it.isNotBlank() },
        aliases = stringList(json.optJSONArray("aliases")),
        tags = tags,
        affordances = affordances,
        activation = activation,
        references = parseReferences(json.optJSONArray("references")),
        hardRule = parseHardRule(json.optJSONObject("hard_rule") ?: json.optJSONObject("hardRule")),
        source = CanonSourceRef(
          documentId = sourceDocumentId,
          documentPath = sourceJson.optString("document_path")
            .ifBlank { sourceJson.optString("documentPath") }.takeIf { it.isNotBlank() },
          anchor = sourceJson.optString("anchor"),
          contentSha256 = sourceJson.optString("content_sha256")
            .ifBlank { sourceJson.optString("contentSha256") }.takeIf { it.isNotBlank() },
          approvedRevision = sourceJson.optString("approved_revision")
            .ifBlank { sourceJson.optString("approvedRevision") }.takeIf { it.isNotBlank() }
        ),
        priority = json.optInt("priority", 80)
      )
    }

    /**
     * Parse toàn bộ mảng "records" của một pack/DB. Fail-fast khi trùng id
     * (đúng require của KnowledgeContextEngine.load hiện hành).
     */
    fun parseAll(root: JSONObject): List<CanonRecord> {
      val array = root.optJSONArray("records")
        ?: throw IllegalArgumentException("Canon database is missing required array: records")
      val seen = linkedSetOf<String>()
      val out = ArrayList<CanonRecord>(array.length())
      for (i in 0 until array.length()) {
        val record = fromJson(array.getJSONObject(i))
        require(record.id !in seen) { "Duplicate canon record id: ${record.id}" }
        seen += record.id
        out += record
      }
      return out
    }

    /**
     * Heuristic ánh xạ entityId từ id khi dữ liệu chưa có field entity_id
     * (design_mapping §1): "CHAR.<X>. ..." -> "CHAR.<X>"; "LEVEL.<n>. ..." -> "LEVEL.<n>".
     * Không suy được -> null. Đây chỉ là ánh xạ ở adapter, không phải dữ liệu gốc.
     */
    fun deriveEntityId(id: String): String? {
      val parts = id.split('.')
      if (parts.size < 3) return null
      return when (parts[0]) {
        "CHAR", "LEVEL", "ENTITY", "ITEM", "ORG", "EVENT" -> parts[0] + "." + parts[1]
        else -> null
      }
    }

    private fun required(json: JSONObject, field: String, id: String): String {
      val value = json.optString(field)
      require(value.isNotBlank()) { "Canon record $id is missing required field: $field" }
      return value
    }

    private inline fun <reified T : Enum<T>> parseEnum(
      json: JSONObject, id: String, vararg keys: String
    ): T? {
      val raw = keys.firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } } ?: return null
      // Preserve WORLD/META as distinct visibility classes. Resolver gates may
      // authorize both, but serialization and Canon semantics must retain the label.
      val normalized = raw.trim().uppercase(Locale.ROOT)
      return runCatching { enumValueOf<T>(normalized) }
        .getOrElse { throw IllegalArgumentException("Canon record $id has unknown enum value: $raw") }
    }

    private fun parseActivation(
      json: JSONObject?,
      entityId: String?,
      tags: Set<String>,
      affordances: Set<String>
    ): CanonActivation {
      if (json == null) {
        // Dữ liệu v1 chưa có activation object -> suy tối thiểu theo design_mapping §1:
        // intent = affordances của chính record; record gắn entity thì yêu cầu entity đó
        // hiện diện/là chủ thể; record LEVEL.* thì scope theo chính Level đó.
        return CanonActivation(
          anySceneIntent = affordances,
          requireActorPresentOrSubject = entityId,
          levelScope = if (entityId != null && entityId.startsWith("LEVEL.")) entityId
            else CanonActivation.LEVEL_SCOPE_ANY
        )
      }
      return CanonActivation(
        anySceneIntent = stringSet(json.optJSONArray("any_scene_intent") ?: json.optJSONArray("anySceneIntent")),
        requireActorPresentOrSubject = json.optString("require_actor_present_or_subject")
          .ifBlank { json.optString("requireActorPresentOrSubject") }.takeIf { it.isNotBlank() },
        levelScope = json.optString("level_scope")
          .ifBlank { json.optString("levelScope") }.ifBlank { CanonActivation.LEVEL_SCOPE_ANY },
        minEvidence = json.optString("min_evidence").ifBlank { json.optString("minEvidence") }
          .let { raw ->
            if (raw.isBlank()) TruthStatus.CONFIRMED
            else runCatching { TruthStatus.valueOf(raw.trim().uppercase(Locale.ROOT)) }
              .getOrElse { throw IllegalArgumentException("Unknown activation min_evidence: $raw") }
          },
        denyIfUnauthorizedPov = json.optBoolean("deny_if_unauthorized_pov",
          json.optBoolean("denyIfUnauthorizedPov", true))
      )
    }

    private fun parseReferences(array: org.json.JSONArray?): List<CanonEdge> {
      if (array == null) return emptyList()
      val out = ArrayList<CanonEdge>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i)
        if (obj != null) {
          // Dạng v2: {"type": "ADDRESS_AS", "target_id": "..."} (camelCase cũng được chấp nhận).
          val rawType = obj.optString("type")
          val type = if (rawType.isBlank()) EdgeType.REFERENCES
            else runCatching { EdgeType.valueOf(rawType.trim().uppercase(Locale.ROOT)) }
              .getOrElse { throw IllegalArgumentException("Unknown canon edge type: $rawType") }
          val target = obj.optString("target_id").ifBlank { obj.optString("targetId") }
            .ifBlank { obj.optString("id") }
          require(target.isNotBlank()) { "Canon edge is missing target id" }
          out += CanonEdge(type, target)
        } else {
          // Dạng v1: references là mảng chuỗi chưa có type -> cạnh REFERENCES duy nhất
          // (design_mapping §1: không giả vờ có typed edge trong dữ liệu hiện tại).
          val target = array.optString(i, "").trim()
          if (target.isNotEmpty()) out += CanonEdge(EdgeType.REFERENCES, target)
        }
      }
      return out
    }

    private fun parseHardRule(json: JSONObject?): CanonHardRule? {
      if (json == null) return null
      val predicate = json.optString("predicate")
      val effect = json.optString("effect")
      if (predicate.isBlank() || effect.isBlank()) return null
      return CanonHardRule(predicate, effect)
    }

    /** Chuẩn hoá giống hệt KnowledgeContextEngine.strings()/normalize() hiện hành. */
    private fun stringSet(array: org.json.JSONArray?): Set<String> {
      if (array == null) return emptySet()
      val out = linkedSetOf<String>()
      for (i in 0 until array.length()) {
        val value = array.optString(i, "").trim()
        if (value.isNotEmpty()) out += normalizeTerm(value)
      }
      return out
    }

    private fun stringList(array: org.json.JSONArray?): List<String> {
      if (array == null) return emptyList()
      val out = ArrayList<String>(array.length())
      for (i in 0 until array.length()) {
        val value = array.optString(i, "").trim()
        if (value.isNotEmpty()) out += normalizeTerm(value)
      }
      return out
    }
  }
}

object CanonUniverse {
  const val BACKROOMS_MAIN = "BACKROOMS_MAIN"
}

/** Chỉ các kind cần cho logic cứng của compiler/resolver (design_mapping §2.1). */
object CanonKinds {
  const val HARD_LOCK = "hard-lock"
  const val HARD_CANON = "hard-canon"
  const val ADDRESS_LOCK = "address-lock"
  const val RUNTIME_CARD = "runtime-card"
}

/** Chuẩn hoá term dùng chung trong package canon — parity với KnowledgeContextEngine.normalize(). */
internal fun normalizeTerm(text: String): String = text.lowercase(Locale.ROOT)
  .replace('–', '-')
  .replace('—', '-')
  .replace(Regex("\\s+"), " ")
  .trim()
