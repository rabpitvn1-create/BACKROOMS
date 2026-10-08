package com.rabpit.backroom.core.progression

/**
 * Reviewed internet provenance and game-safe factual context for world narrative.
 *
 * Scope phases 1-3: detailed summaries for nine Level-0 stops and thirteen full Levels;
 * linked metadata for 34 more stops; remaining Project-only sources are clearly identified.
 * No live scraping, user save writes, exit edges, Entity/loot grants,
 * hazard rolls, or Google/Gemini authority are implemented by this registry.
 *
 * Wikidot prose is mutable, in-universe reports are not proof of mechanics,
 * and the user's project-specific locks override the external reference.
 */
enum class CanonWebReview {
  /** Direct source article URL and writing credit verified; its narrative NOT extracted yet. */
  PAGE_METADATA_ONLY,
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
    "level-1.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-1-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "kvn7",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("trimmed/outdated; OPEN trong catalog Project"),
    ),
    "level-1.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-1-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Praetor3005",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-1.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-1-3",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "DivineAtlas",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-1.5" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-1-5",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Stretchsterz",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:1:base-alpha" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/base-alpha",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Praetor3005",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:1:traders-vault" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/traders-vault",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Stretchsterz",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Trang mục đang được viết lại; OPEN trong catalog Project"),
    ),
    "level-2.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-2-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "penutbuteraples",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:2:office-space-el3a" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/office-space-el3a",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Noctilucian",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn trimmed; OPEN trong catalog Project"),
    ),
    "level-3.5" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-3-5",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "exotichive",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:4:the-office-market" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/the-office-market",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Praetor3005",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-5.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-5-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Natedagreat563",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Bài Wiki dùng tên dài GRAND OPENING..., title Project giữ nguyên"),
    ),
    "level-5.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-5-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "jan Jejasa",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-5.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-5-3",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Praetor3005",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-6.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-6-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Stretchsterz",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Project Level 6 là tundra tối vĩnh viễn; không mang cơ chế wiki Level 6 thay bối cảnh"),
    ),
    "level-6.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-6-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "VivamusLudio",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn trimmed; OPEN trong catalog Project; Level 6 tundra hard lock"),
    ),
    "level-6.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-6-3",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Eurasian_",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn trimmed; OPEN trong catalog Project; Level 6 tundra hard lock"),
    ),
    "level-6.31" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-6-31",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "r a t i f",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn liên quan đến sublevel 6.3; Project vẫn giữ mã 6.31 với parentLevel 6"),
    ),
    "level-7.6" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-7-6",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "ForestIsWatching",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-7.7" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-7-7",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Ericote",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-7.8" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-7-8",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Light_Nate",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:7:the-hadal-zone" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/the-hadal-zone",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Sky3",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-8.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-8-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "RiemannHypothesis",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn trimmed/open; giữ OPEN"),
    ),
    "area:8:the-sanctum-subterraneous" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/the-sanctum",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Kai4C",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Đường dẫn thật /the-sanctum, không suy đoán theo slug tên Project"),
    ),
    "level-9.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-9-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Noctilucian",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-9.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-9-3",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "ForestIsWatching",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-9.5" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-9-5",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "RoseMonsignor",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-10.1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-10-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Kitty Rika",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "level-10.2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-10-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "TheLiminalJester283",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:11:asset-11-1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-11-1",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Dr Bierre",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Wiki định danh Level 11.1; game giữ Asset 11.1 như khu phụ KHÔNG có WorldNodeId/rank"),
    ),
    "area:11:scene-01-2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-11-2",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Univ - Wise Explorer",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Wiki định danh Level 11.2; game giữ Scene-01.2 như khu phụ KHÔNG có WorldNodeId/rank"),
    ),
    "level-11.3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-11-3",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Noctilucian and VivamusLudio",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:11:after-hours" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/after-hours",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "Sky3",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
    ),
    "area:11:the-headquarters" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/the-headquarters",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "VivamusLudio",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn trimmed; OPEN trong catalog Project"),
    ),
    "area:11:radio-backrooms-studio" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/radio-backrooms-studio",
      wikiTitle = null, // Detailed page prose has not passed normalization review
      creditedAuthors = "VivamusLudio",
      review = CanonWebReview.PAGE_METADATA_ONLY,
      observedDate = "2026-10-08",
      conflicts = listOf("Nguồn under rewrite; OPEN trong catalog Project"),
    ),
    "level-1" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-1",
      wikiTitle = "Level 1 - Habitable Zone",
      creditedAuthors = "Praetor3005 and DivineAtlas",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Không gian bê tông rộng như gara và hành lang công nghiệp, với nhiều phòng và khu chức năng.", "Blackout có thể làm thay đổi khả năng nhận biết môi trường."),
      riskReports = listOf("Điều kiện nhìn và âm thanh thay đổi khi mất điện."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-2" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-2",
      wikiTitle = "Level 2 - Abandoned Utility Halls",
      creditedAuthors = "Greggita Mahayfaio",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Hành lang kỹ thuật bê tông hẹp, nhiều ống và thiết bị công nghiệp.", "Lối đi chằng chịt, có khúc ngoặt và khoảng không bị thiết bị chiếm."),
      riskReports = listOf("Không gian tù túng và thiết bị kỹ thuật gây rủi ro môi trường."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-3" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-3",
      wikiTitle = "Level 3 - Electrical Station",
      creditedAuthors = "Natedagreat563",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Cơ sở điện công nghiệp cũ với thiết bị và đường dẫn kỹ thuật."),
      riskReports = listOf("Wiki báo cáo vùng có tính nguy hiểm cao và có lời kể về các thực thể thù địch; không tự spawn Entity."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-4" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-4",
      wikiTitle = "Level 4 - Abandoned Office",
      creditedAuthors = "u/M654zy on Reddit",
      review = CanonWebReview.SOURCE_TRIMMED,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Mục Level 4 được index đánh dấu under rewrite; giữ baseline Project, chưa nâng thông tin mới thành hazard, loot hoặc exit."),
    ),
    "level-5" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-5",
      wikiTitle = "Level 5 - Terror Hotel",
      creditedAuthors = "Stretchsterz",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Khách sạn cũ rộng lớn với phòng ngủ, lối đi và các khu chức năng khác nhau."),
      riskReports = listOf("Môi trường thay đổi theo khu; Entity/hazard cần nguồn và hệ thống gameplay xác nhận riêng."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-6" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-6",
      wikiTitle = "Level 6 - Lights Out",
      creditedAuthors = "Bart0nius",
      review = CanonWebReview.PROJECT_OVERRIDE,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Project hard lock Level 6 là tundra tối vĩnh viễn; Wiki hiện đánh dấu Level 6 trimmed/open for rewrite, không thay thế bằng mê cung tối cổ điển."),
    ),
    "level-7" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-7",
      wikiTitle = "Level 7 - Thalassophobia",
      creditedAuthors = "Bart0nius",
      review = CanonWebReview.SOURCE_TRIMMED,
      observedDate = "2026-10-08",
      environmentSignals = emptyList(),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Trang Wiki hiện thuộc nhóm trimmed/open for rewrite; chỉ dùng baseline đại dương đã khóa trong Project khi dựng cảnh."),
    ),
    "level-8" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-8",
      wikiTitle = "Level 8 - Cave Systems",
      creditedAuthors = "C-Graph and kai4C",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Mạng hang động ngầm tự nhiên rộng, lối đi đá tối và chật."),
      riskReports = listOf("Nguy cơ lạc đường, đường đá sâu và các báo cáo có kẻ săn mồi; EntityCore phải quyết định có gặp hay không."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-9" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-9",
      wikiTitle = "Level 9 - The Suburbs",
      creditedAuthors = "Stretchsterz",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Dãy phố và khu nhà ngoại ô lặp lại trong đêm tối."),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
    "level-10" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-10",
      wikiTitle = "Level 10 - Bumper Crop",
      creditedAuthors = "scutoid studios & egglord",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Khu đồng ruộng, nông trại và công trình nông nghiệp trải dài."),
      riskReports = emptyList(),
      exitReports = listOf("Wiki ghi nhận việc theo đường bộ lâu có thể tới Level 11; chỉ là nguồn tham khảo."),
      conflicts = emptyList(),
    ),
    "level-11" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-11",
      wikiTitle = "Level 11 - The City That Never Sleeps",
      creditedAuthors = "Greggita Mahayfaio",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Mạng đường phố, khu cư trú và thương mại của một đô thị rất rộng; wiki mô tả như một đầu mối Backrooms."),
      riskReports = emptyList(),
      exitReports = emptyList(),
      conflicts = listOf("Đây là baseline ngoài Project: chưa được cấp route/spawn hoặc quyền nhân vật tự biết tổ chức trong thành phố."),
    ),
    "level-12" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-12",
      wikiTitle = "Level 12 - Matrix",
      creditedAuthors = "Stretchsterz and Liryn",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Không gian nội thất trắng bất thường và hiện tượng làm sai lệch/cắt trắng hình ảnh ghi lại."),
      riskReports = listOf("Nguồn mô tả khó ghi hình hoặc lưu thông tin vì hiệu ứng censor; chưa phải trạng thái UI tự kích hoạt."),
      exitReports = listOf("Wiki có nhật ký đội khảo sát thuật lại cách tìm lối ra; không dùng nó tự ghi Core edge."),
      conflicts = emptyList(),
    ),
    "level-13" to InternetCanonSource(
      wikiPageUrl = "https://backrooms-wiki.wikidot.com/level-13",
      wikiTitle = "Level 13 - The Boiling Frogs",
      creditedAuthors = "Greggita Mahayfaio",
      review = CanonWebReview.PAGE_REVIEWED,
      observedDate = "2026-10-08",
      environmentSignals = listOf("Khu căn hộ kiểu thế kỷ 20, hành lang màu nhạt dài, nhiều cầu thang và thang máy có trạng thái không nhất quán."),
      riskReports = listOf("Báo cáo về sức ì tâm lý là lore, chưa thành status effect nếu Core chưa phê duyệt."),
      exitReports = emptyList(),
      conflicts = emptyList(),
    ),
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

  /** Reviewed content ONLY; a URL/author alone is not a reviewed environment. */
  fun directlyReviewed(): List<GameCanonRecord> =
    RECORDS.filter {
      it.source.review in setOf(CanonWebReview.PAGE_REVIEWED,
        CanonWebReview.PROJECT_OVERRIDE, CanonWebReview.SOURCE_TRIMMED)
    }

  /** Verified on-wiki source URL (including metadata-only records). */
  fun sourceLinked(): List<GameCanonRecord> =
    RECORDS.filter { it.source.wikiPageUrl != null }
}
