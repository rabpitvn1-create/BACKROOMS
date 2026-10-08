package com.rabpit.backroom.core.progression

/**
 * Reviewed internet provenance and game-safe factual context for world narrative.
 *
 * Scope phase 1: source pages directly examined for nine Level-0 journey stops;
 * all 67 stops are represented with a deliberately explicit pending/unreviewed
 * status. No live scraping, user save writes, exit edges, Entity/loot grants,
 * hazard rolls, or Google/Gemini authority are implemented by this registry.
 *
 * Wikidot prose is mutable, in-universe reports are not proof of mechanics,
 * and the user's project-specific locks override the external reference.
 */
enum class CanonWebReview {
  /** A page was directly read and adapted as contextual evidence. */
  PAGE_REVIEWED,
  /** Read, but conflicting with Project hard locks: Project prevails. */
  PROJECT_OVERRIDE,
  /** Direct page exists but is marked trimmed/outdated/rewrite. */
  SOURCE_TRIMMED,
  /** Project-local content exists; no verified direct Wikidot page in this pass. */
  PROJECT_ONLY,
  /** Listed in world inventory, but external article not individually reviewed. */
  INDEX_PENDING,
  /** OPEN wiki/Project entry; no promoted gameplay canon. */
  OPEN_PENDING,
}

/**
 * All textual hints are descriptive, non-executable references.
 * Never interpret [exitReports] as WorldProgressionCore.EDGES.
 *
 * For directly reviewed pages, authors and CC BY-SA 3.0 are page-level prose
 * attributions; image assets need independent per-image license review.
 */
data class InternetCanonSource(
  val wikiPageUrl: String?,
  val wikiTitle: String?,
  val creditedAuthors: String?,
  val review: CanonWebReview,
  val observedDate: String?,
  val environmentSignals: List<String> = emptyList(),
  val riskReports: List<String> = emptyList(),
  val exitReports: List<String> = emptyList(),
  val conflicts: List<String> = emptyList(),
) {
  val wikiTextLicense: String?
    get() = if (wikiPageUrl == null) null else "CC BY-SA 3.0"
}

data class GameCanonRecord(
  val stopKey: String,
  val parentLevel: Int,
  val projectTitle: String,
  val projectAuthority: WorldContentAuthority,
  val source: InternetCanonSource,
  /** Parent/identity from the reviewed Project registry; never from Wiki title parsing. */
  val worldNodeId: WorldNodeId?,
)

object WorldInternetCanon {
  const val WIKI_INDEX = "https://backrooms-wiki.wikidot.com/normal-levels-i"
  const val PROJECT_WORLD = "knowledge/novel_asset/BACKROOMS_WORLD.md"
  const val LICENSE_GUIDE = "https://backrooms-wiki.wikidot.com/licensing-guide"

  /**
   * Page evidence directly checked on 2026-10-08. No guessed links are stored.
   * Wiki reports and source summaries deliberately avoid detailed Entity,
   * difficulty, spawn, resource or exit assertions not committed by game Core.
   */
  private val reviewed: Map<String, InternetCanonSource> = mapOf(
    "level-0" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0",
      wikiTitle = "Level 0 - Threshold",
      creditedAuthors = "DivineAtlas, DrAkimoto, RobertGoerman",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Mê cung phòng vàng, giấy dán tường ẩm mốc, thảm ướt, đèn huỳnh quang rung ù.", "Không gian thay đổi và dễ mất định hướng."),
      riskReports = listOf("Thiếu nước và kiệt sức khi lang thang lâu."),
      exitReports = listOf("Wiki kể về tường nhấp nháy dẫn tới Level 1; Core chưa mở route từ dữ liệu này."),
      conflicts = emptyList(),
    ),
    "level-0.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0-1",
      wikiTitle = "Level 0.1 - Zenith Station",
      creditedAuthors = "CutTheBirch",
      review = CanonWebReview.PROJECT_OVERRIDE,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Project đã khóa 0.1 là Deep Emptiness; Zenith Station chỉ là bản Wikidot để đối chiếu, tuyệt đối không ghi đè tên và môi trường."),
    ),
    "level-0.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0-2",
      wikiTitle = "Level 0.2 - Remodeled Mess",
      creditedAuthors = "RowanLater",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Phiên bản Level 0 được cải tạo, tường trắng, thảm đỏ khô và thiết bị điện.", "Cấu trúc bắt đầu đổ vỡ sau khi có người bước vào."),
      riskReports = listOf("Nguy cơ sập trần, tường hoặc sàn và bụi từ đổ vỡ."),
      exitReports = listOf("Wiki báo cáo cửa quay lại Level 0; chỉ là dữ kiện nguồn, không cấp gameplay edge."),
      conflicts = listOf("Không được nhập nhằng Level 0.2 Remodeled Mess với Level 0.22 Fully Remodeled của Project."),
    ),
    "level-0.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0-3",
      wikiTitle = "Level 0.3 - The Icy Rooms",
      creditedAuthors = "CursedSliver",
      review = CanonWebReview.SOURCE_TRIMMED,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Wiki đánh dấu bài viết đã lỗi thời và bị trimmed/open for rewrite; giữ OPEN, không chuyển văn bản cũ thành cơ chế gameplay."),
    ),
    "level-0.5" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0-5",
      wikiTitle = "Level 0.5 - Aquaclaustrophobic Infirmary",
      creditedAuthors = "FuneralBouncer (Moose0)",
      review = CanonWebReview.PROJECT_OVERRIDE,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Hành lang chật ngập nước lạnh đục và khu bệnh viện xuống cấp; giữ mô tả chi tiết trong BACKROOMS_WORLD.md."),
      riskReports = listOf("Ngâm nước lâu, ô nhiễm và thiết bị điện hư hại là nguy cơ bối cảnh, chưa phải cơ chế damage tự chạy."),
      exitReports = listOf("Các lối ra được Wiki thuật lại khác với tuyến riêng mà Project đã ghi cho 0.5."),
      conflicts = listOf("Project đã định hướng continuity 0.2 → 0.5 → 0.7; bất kỳ route ngoài từ Wiki cần phê duyệt riêng."),
    ),
    "level-0.7" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-0-7",
      wikiTitle = "Level 0.7 - The Reminiscence District",
      creditedAuthors = "T-Dragon",
      review = CanonWebReview.PROJECT_OVERRIDE,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Project hard lock Level 0.7 = Claustrophobia, không phải The Reminiscence District; không trộn bối cảnh hoặc lore hai phiên bản."),
    ),
    "area:0:manila-room" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/manila-room",
      wikiTitle = "The Manila Room",
      creditedAuthors = "Br Miller & Neptunium",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Phòng nhỏ trong Level 0, giấy dán tường manila, ván gỗ và bàn bát giác với hai ghế.", "Tài liệu Wiki kể rằng hiệu ứng cô lập suy yếu gần phòng."),
      riskReports = emptyList(),
      exitReports = listOf("Wiki mô tả đây là một trong những cửa ngõ dẫn tới Level 1; trình tự game chưa kích hoạt lối đi này."),
      conflicts = listOf("Tài liệu và vật tư trong phòng không được tự động cấp cho nhân vật."),
    ),
    "area:0:red-rooms" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/red-rooms",
      wikiTitle = "Red Rooms",
      creditedAuthors = "scutoid studios",
      review = CanonWebReview.PROJECT_OVERRIDE,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Phòng đỏ thẫm kiểu Level 0, ánh sáng đỏ, thảm ráp dính và các mảng mốc đen."),
      riskReports = listOf("Wiki coi vùng này là ngõ cụt nguy hiểm, khả năng mất liên lạc và không thoát ra được."),
      exitReports = emptyList(),
      conflicts = listOf("Tuyến biên tập Red Rooms → Level 1 theo yêu cầu tác giả không đồng nghĩa Wiki xác nhận exit; cần thiết kế Core riêng trước khi cho đi qua."),
    ),
    "area:0:the-torment" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/the-torment",
      wikiTitle = "The Torment",
      creditedAuthors = "Sky3",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Nguồn mô tả dị thường u ám, mộ xếp vòng tròn và tượng đá giữa không gian xám; chỉ có lời kể nhân chứng."),
      riskReports = listOf("Thông tin nguồn xem nơi này là bí ẩn, không chứng minh có thể vào bằng lối thông thường."),
      exitReports = emptyList(),
      conflicts = listOf("Không suy ra một chặng có thể đi vào và ra thực sự chỉ từ mục tồn tại trên Wiki."),
    ),
  )

  /** Stable coverage includes all 67 journey stops, even if source is pending. */
  val RECORDS: List<GameCanonRecord> = WorldJourneyOrder.STOPS.map { stop ->
    val evidence = reviewed[stop.key] ?: InternetCanonSource(
      wikiPageUrl = null,
      wikiTitle = null,
      creditedAuthors = null,
      review = when (stop.authority) {
        WorldContentAuthority.PROJECT_CANON -> CanonWebReview.PROJECT_ONLY
        WorldContentAuthority.EXTERNAL_REFERENCE -> CanonWebReview.INDEX_PENDING
        WorldContentAuthority.OPEN -> CanonWebReview.OPEN_PENDING
      },
      observedDate = null,
    )
    GameCanonRecord(stop.key, stop.parentLevel, stop.title,
      stop.authority, evidence, stop.worldNodeId)
  }

  private val byKey = RECORDS.associateBy { it.stopKey }

  init {
    check(RECORDS.size == 67 && byKey.size == RECORDS.size)
    check(reviewed.keys.all { it in byKey }) { "Internet canon evidence for unknown journey stop" }
    check(RECORDS.map { it.stopKey } == WorldJourneyOrder.STOPS.map { it.key })
    check(RECORDS.all { it.source.wikiPageUrl == null ||
      (it.source.creditedAuthors?.isNotBlank() == true &&
        it.source.observedDate != null &&
        it.source.wikiPageUrl.startsWith("https://backrooms-wiki.wikidot.com/")) })
    check(RECORDS.all { it.source.review !in setOf(CanonWebReview.PROJECT_ONLY,
      CanonWebReview.INDEX_PENDING, CanonWebReview.OPEN_PENDING) ||
      (it.source.environmentSignals.isEmpty() && it.source.exitReports.isEmpty() &&
        it.source.riskReports.isEmpty()) })
  }

  /** Pure reference lookup: unknown stop does not become Level 0 by default. */
  fun record(key: String): GameCanonRecord? = byKey[key]

  fun recordsForLevel(parentLevel: Int): List<GameCanonRecord> =
    RECORDS.filter { it.parentLevel == parentLevel }

  /** Only reviewed source entries; do not count index-only placeholders as read. */
  fun directlyReviewed(): List<GameCanonRecord> =
    RECORDS.filter { it.source.wikiPageUrl != null }
}
