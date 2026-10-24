package com.rabpit.backroom.core.canon

import android.content.Context
import org.json.JSONObject

/**
 * Canon Runtime 2.0 — JsonCanonStore: backend giai đoạn 1 của [CanonStore]
 * (ARCHITECTURE §5.1, design_mapping §2.2). Nạp assets `knowledge/knowledge_db.json`
 * — cùng asset với KnowledgeContextEngine hiện hành — và ánh xạ record v1 -> v2
 * qua [CanonRecord.fromJson]. Không SQLite, không FTS, không embedding ở giai đoạn
 * này; tìm optional bằng khớp exact-term trên index tag/alias dựng từ dữ liệu
 * ([CanonDirectTerms]).
 *
 * Kỷ luật lỗi: mọi lỗi hạ tầng (đọc asset, JSON hỏng, id trùng/thiếu, snapshot lệch)
 * đều ném [CanonStoreException] rõ ràng để tầng trên fallback an toàn; không bao giờ
 * nuốt lỗi thành kho rỗng.
 */
class JsonCanonStore private constructor(
  private val db: Database
) : CanonStore {

  data class Database(
    val records: Map<String, CanonRecord>,
    val tagIndex: Map<String, Set<String>>,
    val affordanceIndex: Map<String, Set<String>>,
    /** term (normalize) -> record id; dựng từ tags + aliases + tên entity trong DB. */
    val aliasIndex: Map<String, Set<String>>,
    /** src record id -> các typed edge ra của nó. */
    val edgeIndex: Map<String, List<CanonEdge>>,
    val snapshot: CanonSnapshot
  )

  override fun snapshot(): CanonSnapshot = db.snapshot

  override fun loadRecordsByIds(ids: Set<String>, snapshot: CanonSnapshot): List<CanonRecord> {
    if (snapshot != db.snapshot) {
      throw CanonStoreException(
        "Canon snapshot mismatch: turn pinned $snapshot but store holds ${db.snapshot}"
      )
    }
    return ids.mapNotNull { db.records[it] }
  }

  override fun resolveAliases(terms: Set<String>, scope: CanonScope): Set<String> {
    if (terms.isEmpty()) return emptySet()
    val out = linkedSetOf<String>()
    terms.forEach { term ->
      db.aliasIndex[normalizeTerm(term)].orEmpty().forEach { id ->
        val record = db.records[id] ?: return@forEach
        if (visibleInScope(record, scope)) out += id
      }
    }
    return out
  }

  override fun queryAffordances(affordances: Set<String>, scope: CanonScope): Set<String> {
    if (affordances.isEmpty()) return emptySet()
    val out = linkedSetOf<String>()
    affordances.forEach { affordance ->
      db.affordanceIndex[normalizeTerm(affordance)].orEmpty().forEach { id ->
        val record = db.records[id] ?: return@forEach
        if (visibleInScope(record, scope)) out += id
      }
    }
    return out
  }

  override fun traverseTypedEdges(
    startIds: Set<String>,
    types: Set<EdgeType>,
    bounds: GraphBounds
  ): Set<String> {
    if (startIds.isEmpty() || types.isEmpty()) return emptySet()
    val deadlineNanos = System.nanoTime() + bounds.deadlineMs.coerceAtLeast(0L) * NANOS_PER_MS
    val visited = linkedSetOf<String>()
    visited += startIds
    val reached = linkedSetOf<String>()
    var candidateBytes = 0L
    var frontier = startIds.filter { it in db.records }
    var hops = 0
    while (frontier.isNotEmpty() && hops < bounds.maxHops) {
      if (System.nanoTime() > deadlineNanos) break
      val next = ArrayList<String>()
      frontierLoop@ for (id in frontier) {
        for (edge in db.edgeIndex[id].orEmpty()) {
          if (edge.type !in types) continue
          val target = edge.targetId
          if (target in visited) continue
          val record = db.records[target] ?: continue
          if (reached.size >= bounds.maxNodes) break@frontierLoop
          candidateBytes += record.estimatedTokens * BYTES_PER_TOKEN_ESTIMATE
          if (candidateBytes > bounds.maxCandidateBytes) break@frontierLoop
          visited += target
          reached += target
          next += target
        }
        if (System.nanoTime() > deadlineNanos) break
      }
      frontier = next
      hops++
    }
    return reached
  }

  override fun findOptionalText(query: String, scope: CanonScope, limit: Int): List<CanonCandidate> {
    if (query.isBlank() || limit <= 0) return emptyList()
    // Cổng 4 giai đoạn JSON: exact-term trên aliasIndex (tags/aliases/tên entity).
    // Term trích từ chính dữ liệu — không quét text của record, không "gần giống nhất".
    val terms = CanonDirectTerms.extractTerms(query, db.aliasIndex).toMutableSet()
    val wholeQuery = normalizeTerm(query)
    if (wholeQuery in db.aliasIndex) terms += wholeQuery
    if (terms.isEmpty()) return emptyList()

    val best = linkedMapOf<String, CanonCandidate>()
    terms.forEach { term ->
      db.aliasIndex[term].orEmpty().forEach { id ->
        val record = db.records[id] ?: return@forEach
        if (!visibleInScope(record, scope)) return@forEach
        val score = when {
          term in record.tags -> SCORE_TAG_MATCH
          term in CanonDirectTerms.entityNameTerms(record) -> SCORE_NAME_MATCH
          term in record.aliases -> SCORE_ALIAS_MATCH
          else -> SCORE_TERM_MATCH
        }
        val current = best[id]
        if (current == null || score > current.score) {
          best[id] = CanonCandidate(record, term, score)
        }
      }
    }
    return best.values.sortedWith(
      compareByDescending<CanonCandidate> { it.score }
        .thenBy { it.record.priority }
        .thenBy { it.record.id }
    ).take(limit)
  }

  /**
   * Cổng 0 ở tầng store: universe, Level scope, known_by và quyền POV.
   * Record gắn entity (activation.requireActorPresentOrSubject) bị chặn khi entity đó
   * không phải POV và không nằm trong nhóm actor mà POV được biết — đây là guard
   * "CHAR.LUC_TRAM.* chỉ khi luc-tram hiện diện" của engine cũ, nay thành luật dữ liệu.
   */
  private fun visibleInScope(record: CanonRecord, scope: CanonScope): Boolean {
    if (record.universeId != scope.universeId) return false
    val levelScope = record.activation.levelScope
    if (scope.levelId != null &&
      levelScope != CanonActivation.LEVEL_SCOPE_ANY &&
      levelScope != scope.levelId
    ) return false
    if (record.knownBy.isNotEmpty()) {
      val knowers = scope.knownByActorIds + scope.povActorId
      if (record.knownBy.intersect(knowers).isEmpty()) return false
    }
    val requiredEntity = record.activation.requireActorPresentOrSubject
    if (record.activation.denyIfUnauthorizedPov && requiredEntity != null) {
      val actorId = actorIdForEntity(requiredEntity)
      if (actorId != null && actorId != scope.povActorId && actorId !in scope.knownByActorIds) {
        return false
      }
    }
    return true
  }

  companion object {
    private const val ASSET = "knowledge/knowledge_db.json"
    private const val NANOS_PER_MS = 1_000_000L
    private const val BYTES_PER_TOKEN_ESTIMATE = 4L
    private const val SCORE_TAG_MATCH = 3
    private const val SCORE_NAME_MATCH = 2
    private const val SCORE_ALIAS_MATCH = 2
    private const val SCORE_TERM_MATCH = 1

    @Volatile private var cached: JsonCanonStore? = null

    /** Nạp lazy + double-checked locking theo đúng mẫu KnowledgeContextEngine.database(). */
    @JvmStatic
    fun fromAssets(context: Context): JsonCanonStore {
      cached?.let { return it }
      return synchronized(this) {
        cached ?: loadFromAssets(context.applicationContext).also { cached = it }
      }
    }

    private fun loadFromAssets(context: Context): JsonCanonStore {
      val raw = try {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
      } catch (e: Exception) {
        throw CanonStoreException("Failed to read canon asset: $ASSET", e)
      }
      return parse(raw)
    }

    /**
     * Parse từ chuỗi JSON (tách khỏi Context để unit test thuần). Mọi lỗi dữ liệu/
     * hạ tầng được bọc thành [CanonStoreException]; id trùng/thiếu vẫn fail-fast
     * ở [CanonRecord.parseAll] trước khi bọc.
     */
    @JvmStatic
    fun parse(rawJson: String): JsonCanonStore {
      try {
        val root = JSONObject(rawJson)
        val recordList = CanonRecord.parseAll(root)
        val records = linkedMapOf<String, CanonRecord>()
        recordList.forEach { records[it.id] = it }
        fun index(selector: (CanonRecord) -> Set<String>): Map<String, Set<String>> {
          val out = linkedMapOf<String, MutableSet<String>>()
          records.values.forEach { record ->
            selector(record).forEach { key -> out.getOrPut(normalizeTerm(key)) { linkedSetOf() }.add(record.id) }
          }
          return out.mapValues { it.value.toSet() }
        }
        val edgeIndex = linkedMapOf<String, List<CanonEdge>>()
        records.values.forEach { record ->
          if (record.references.isNotEmpty()) edgeIndex[record.id] = record.references
        }
        val rootSchemaVersion = root.optInt("schemaVersion", 1)
        val snapshot = CanonSnapshot(
          packId = "knowledge_db@$rootSchemaVersion",
          schemaVersion = rootSchemaVersion,
          recordCount = records.size
        )
        return JsonCanonStore(
          Database(
            records = records.toMap(),
            tagIndex = index { it.tags },
            affordanceIndex = index { it.affordances },
            aliasIndex = CanonDirectTerms.buildTermIndex(records.values),
            edgeIndex = edgeIndex.toMap(),
            snapshot = snapshot
          )
        )
      } catch (e: CanonStoreException) {
        throw e
      } catch (e: Exception) {
        throw CanonStoreException("Failed to parse canon database", e)
      }
    }

    /**
     * Ánh xạ entityId -> actor id lõi của game: "CHAR.LUC_TRAM" -> "luc-tram".
     * Namespace không phải CHAR (LEVEL/ENTITY/ITEM...) không gắn với actor -> null.
     */
    internal fun actorIdForEntity(entityId: String): String? {
      val parts = entityId.split('.')
      if (parts.size < 2 || parts[0] != "CHAR") return null
      return when (parts[1]) {
        "NGUYEN_QUOC_NAM" -> "nguyen_quoc_nam"
        "HUA_TIEU_LAN" -> "hua_tieu_lan"
        else -> parts[1].lowercase().replace('_', '-')
      }
    }
  }
}
