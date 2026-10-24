package com.rabpit.backroom.core.canon

/**
 * Canon Runtime 2.0 — CanonDirectTerms: registry term -> record được SUY TỪ DỮ LIỆU
 * (design_mapping §2.2/§2.7; ARCHITECTURE §1.1 điểm yếu 1, §6.2 cổng 2).
 *
 * Thay thế cơ chế cũ: KnowledgeContextEngine.tokenizeTags() gắn cứng ~19 cụm tên
 * Entity/Item trong code — thêm nhân vật/Entity mới là phải sửa Kotlin. Tại đây term
 * được trích xuất từ chính các record của store:
 *  - mọi tag của record (kể cả tag nhiều từ, vd "false puddle"),
 *  - mọi alias của record (dữ liệu v1 chưa có aliases -> nguồn này tự lớn lên khi
 *    pipeline ingest Gate 1 bổ sung alias, không cần sửa code),
 *  - tên entity suy từ entityId/id của record (vd CHAR.LUC_TRAM -> "luc tram";
 *    ITEM.ENTITY... không áp dụng — chỉ các segment tên riêng trong namespace).
 *
 * Registry này là bước đệm có chủ đích: đích cuối là aliases/typed edge nằm trong
 * dữ liệu record qua ingest; khi đó buildTermIndex tự hấp thụ mà tầng gọi không đổi.
 */
object CanonDirectTerms {

  /**
   * Dựng index term (đã normalize) -> tập record id từ một tập record.
   * Deterministic: duyệt record theo thứ tự đầu vào, term giữ nguyên dạng normalize
   * giống KnowledgeContextEngine.normalize().
   */
  fun buildTermIndex(records: Collection<CanonRecord>): Map<String, Set<String>> {
    val out = linkedMapOf<String, MutableSet<String>>()
    fun add(term: String, recordId: String) {
      val key = normalizeTerm(term)
      if (key.isNotEmpty()) out.getOrPut(key) { linkedSetOf() }.add(recordId)
    }
    records.forEach { record ->
      record.tags.forEach { add(it, record.id) }
      record.aliases.forEach { add(it, record.id) }
      entityNameTerms(record).forEach { add(it, record.id) }
    }
    return out.mapValues { it.value.toSet() }
  }

  /**
   * Các term tên riêng suy từ record: segment tên của entityId và của id.
   * Vd: CHAR.LUC_TRAM.TICH_QUANG -> "luc tram", "tich quang";
   * ENTITY.HOUND -> "hound"; LEVEL.01 -> không sinh term (số Level không phải tên).
   * Underscore được thay bằng khoảng trắng để khớp cách người chơi viết trong action.
   */
  fun entityNameTerms(record: CanonRecord): Set<String> {
    val out = linkedSetOf<String>()
    fun addSegment(segment: String) {
      val term = normalizeTerm(segment.replace('_', ' '))
      if (term.isNotEmpty() && term.any { it.isLetter() }) out += term
    }
    record.entityId?.let { entityId ->
      val parts = entityId.split('.')
      if (parts.size >= 2) addSegment(parts[1])
    }
    val idParts = record.id.split('.')
    if (idParts.size >= 3) addSegment(idParts[2])
    // Record thuộc domain ENTITY/ITEM: segment thứ hai của id chính là tên đối tượng
    // (vd ENTITY.BEAST_L5, ITEM.ALMOND_WATER nếu dữ liệu dùng namespace này).
    if ((record.domain == "ENTITY" || record.domain == "ITEM") && idParts.size >= 2) {
      addSegment(idParts[1])
    }
    return out
  }

  /**
   * Tìm các term đã biết xuất hiện trong [text] (đã normalize) — thay vai trò của
   * tokenizeTags() cũ nhưng nguồn term là [termIndex] dữ liệu. Khớp theo contains
   * trên chuỗi đã normalize, ưu tiên term dài trước để term nhiều từ thắng term con.
   */
  fun extractTerms(text: String, termIndex: Map<String, Set<String>>): Set<String> {
    if (text.isBlank() || termIndex.isEmpty()) return emptySet()
    val normalized = normalizeTerm(text)
    val matched = linkedSetOf<String>()
    termIndex.keys.sortedByDescending { it.length }.forEach { term ->
      if (term.length >= 3 && normalized.contains(term)) matched += term
    }
    return matched
  }
}
