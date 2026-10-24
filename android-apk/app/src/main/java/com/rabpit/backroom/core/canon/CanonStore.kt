package com.rabpit.backroom.core.canon

/**
 * Canon Runtime 2.0 — CanonStore: hợp đồng truy xuất kho Canon (ARCHITECTURE §14,
 * design_mapping §2.2). Chữ ký bám đúng §14; hàm [CanonStore.snapshot] là bổ sung
 * tối thiểu có ghi rõ lý do: §14 yêu cầu "CanonSnapshot pin pack version cho cả
 * lượt" nhưng interface mẫu chưa có cách lấy snapshot.
 *
 * Giai đoạn 1 (§5.1): backend là JSON assets qua [JsonCanonStore]; khi kho phình to,
 * backend SQLite thay vào sau chính interface này mà tầng resolver/compiler không đổi.
 *
 * Kỷ luật lỗi: lỗi hạ tầng (pack hỏng, snapshot lệch, I/O) ném [CanonStoreException]
 * để tầng trên fallback an toàn; tuyệt đối không nuốt lỗi thành danh sách rỗng —
 * "không tìm thấy" và "kho hỏng" là hai trạng thái khác nhau.
 */
class CanonStoreException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Ghim phiên bản pack cho cả một lượt: mọi truy vấn của lượt dùng đúng snapshot này. */
data class CanonSnapshot(
  val packId: String,
  val schemaVersion: Int,
  val recordCount: Int
)

/**
 * Phạm vi truy vấn của cổng 0 (§6.2): universe + Level hiện hành + POV.
 * [povActorId]/[knownByActorIds] là actor id lõi của game ("cao-minh", "luc-tram", …),
 * không phải entityId dạng "CHAR.LUC_TRAM" — tầng store chịu trách nhiệm ánh xạ.
 */
data class CanonScope(
  val universeId: String = CanonUniverse.BACKROOMS_MAIN,
  val levelId: String? = null,
  val povActorId: String,
  val knownByActorIds: Set<String>
)

/** Ứng viên từ cổng 4 (optional text): record + term đã khớp + điểm khớp xác định. */
data class CanonCandidate(
  val record: CanonRecord,
  val matchedTerm: String,
  val score: Int
)

/** Các loại typed edge (§3.2). Dữ liệu v1 hiện chỉ có REFERENCES có cạnh thật. */
enum class EdgeType { REFERENCES, ADDRESS_AS, REQUIRES, HAS_ABILITY, LOCATED_IN, KNOWN_BY }

/**
 * Hàng rào chống bùng nổ đồ thị (§6.2): hop mặc định 1, trần nút, trần byte ứng viên
 * và deadline. Mọi cài đặt traverseTypedEdges phải tôn trọng cả bốn bound.
 */
data class GraphBounds(
  val maxHops: Int = 1,
  val maxNodes: Int = 24,
  val maxCandidateBytes: Int = 24_000,
  val deadlineMs: Long = 25L
)

interface CanonStore {
  /** Snapshot hiện hành của pack đang nạp; dùng để pin cho cả lượt (§14). */
  fun snapshot(): CanonSnapshot

  /**
   * Nạp record theo ID chính xác (cổng 1). ID không tồn tại bị bỏ qua tại đây —
   * việc báo thiếu canon bắt buộc (MISSING_REQUIRED_CANON) là trách nhiệm của
   * CanonResolver đối chiếu với danh sách ID nó yêu cầu. Ném CanonStoreException
   * nếu [snapshot] không khớp pack đang nạp.
   */
  fun loadRecordsByIds(ids: Set<String>, snapshot: CanonSnapshot): List<CanonRecord>

  /** Cổng 2: term/alias đã chuẩn hoá -> ID record, đã lọc theo scope (cổng 0). */
  fun resolveAliases(terms: Set<String>, scope: CanonScope): Set<String>

  /** Cổng 3 (affordance): affordance key -> ID record, đã lọc theo scope. */
  fun queryAffordances(affordances: Set<String>, scope: CanonScope): Set<String>

  /**
   * Cổng 3 (typed edge): duyệt đồ thị từ [startIds] chỉ qua các loại cạnh [types],
   * tôn trọng [bounds] và chống cycle bằng visited. Trả các ID ĐẠT TỚI (không gồm
   * chính startIds) và chỉ gồm ID tồn tại trong store.
   */
  fun traverseTypedEdges(startIds: Set<String>, types: Set<EdgeType>, bounds: GraphBounds): Set<String>

  /**
   * Cổng 4: tìm optional theo text đã lọc scope. Không đủ bằng chứng -> danh sách
   * rỗng (tương đương NO_CANON_MATCH của §6.2); không bao giờ trả "record gần giống
   * nhất" để lấp chỗ. Giai đoạn JSON không có FTS/embedding — khớp exact-term trên
   * tag/alias index.
   */
  fun findOptionalText(query: String, scope: CanonScope, limit: Int): List<CanonCandidate>
}
