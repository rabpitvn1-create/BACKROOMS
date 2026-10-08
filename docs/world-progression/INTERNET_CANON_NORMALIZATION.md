# Chuẩn hóa Internet canon → Backrooms game (giai đoạn đầu)

**Ngày đối chiếu:** 2026-10-08. **Phạm vi:** 67 điểm trong `WorldJourneyOrder` được đăng ký, **chỉ 9 trang Wikidot thuộc nhóm Level 0 đã mở và đọc trực tiếp**. 58 mục còn lại không được báo cáo là đã kiểm chứng từng trang.

## Nguồn và ưu tiên

- Nguồn web chính: [Backrooms Wikidot — Levels List](https://backrooms-wiki.wikidot.com/normal-levels-i), các trang Level/subsection tương ứng.
- Nguồn canon Project ưu tiên cao hơn: `android-apk/app/src/main/assets/knowledge/novel_asset/BACKROOMS_WORLD.md`, `WorldContentCatalog.kt` và các thiết kế do tác giả trực tiếp khóa.
- Chính sách: `latest user retcon > active Project/novel canon > reviewed online reference > OPEN`. **Không dùng wiki như một server quyết định hành động của game.**
- Ngày đối chiếu được ghi trên từng hồ sơ đã duyệt; Wiki có thể sửa nội dung, đổi tên, xóa hoặc archive theo thời gian.
- Văn bản mô tả chuyển thành bản tóm tắt Việt ngữ ngắn, tránh copy nguyên bài. Dữ kiện mang tính truyền miệng, nhật ký, M.E.G. hoặc báo cáo nhân chứng vẫn là **lời thuật nguồn**.
- Danh sách online có các trang `trimmed`, `to-rewrite`, `under rewrite`. Nếu vậy: **SOURCE_TRIMMED hoặc OPEN_PENDING**, không dùng bài cũ tự mở cơ chế.

## 9 trang nguồn đã duyệt trực tiếp

| Điểm hành trình Project | Nguồn Wikidot, tác giả theo trang | Trạng thái chuẩn hóa và quyết định |
| --- | --- | --- |
| `level-0` — Threshold / The Lobby | [Level 0 - Threshold](https://backrooms-wiki.wikidot.com/level-0), DivineAtlas, DrAkimoto, RobertGoerman | PAGE_REVIEWED; giữ phòng vàng, đèn huỳnh quang, thảm ẩm và rối định hướng. Exit Wiki chỉ là báo cáo. |
| `level-0.1` — **Deep Emptiness** | [0.1 - Zenith Station](https://backrooms-wiki.wikidot.com/level-0-1), CutTheBirch | **PROJECT_OVERRIDE:** xung đột danh tính; không nhập môi trường Zenith Station vào Deep Emptiness. |
| `level-0.2` — Remodeled Mess | [0.2 - Remodeled Mess](https://backrooms-wiki.wikidot.com/level-0-2), RowanLater | PAGE_REVIEWED; sửa sang nhưng có nguy cơ công trình sập. Không gộp với Project 0.22. |
| `level-0.3` — The Icy Rooms | [0.3 - The Icy Rooms](https://backrooms-wiki.wikidot.com/level-0-3), CursedSliver | **SOURCE_TRIMMED:** bài bị ghi là lỗi thời/open for rewrite; không nhập hazard/Entity từ bài cũ. |
| `level-0.5` — Aquaclaustrophobic Infirmary | [0.5 - Aquaclaustrophobic Infirmary](https://backrooms-wiki.wikidot.com/level-0-5), FuneralBouncer (Moose0) | PROJECT_OVERRIDE cho tuyến thoát; giữ mô tả nước bẩn lạnh và bệnh viện, nhưng Core route theo Project cần duyệt riêng. |
| `level-0.7` — **Claustrophobia** | [0.7 - The Reminiscence District](https://backrooms-wiki.wikidot.com/level-0-7), T-Dragon | **PROJECT_OVERRIDE:** tuyệt đối không viết đè bối cảnh Claustrophobia bằng Reminiscence District. |
| `area:0:manila-room` — Manila Room | [The Manila Room](https://backrooms-wiki.wikidot.com/manila-room), Br Miller & Neptunium | PAGE_REVIEWED; mô tả phòng nhỏ, tường màu manila, bàn tám cạnh, người sống sót có thể gặp nhau. Tài liệu và vật tư không tự cấp vào inventory. |
| `area:0:red-rooms` — Red Rooms | [Red Rooms](https://backrooms-wiki.wikidot.com/red-rooms), scutoid studios | PROJECT_OVERRIDE cho lộ trình: nguồn nói ngõ cụt/nguy hiểm, tác giả đặt điểm này ngay trước Level 1. **Không được tự biến thành exit hợp lệ**. |
| `area:0:the-torment` — The Torment | [The Torment](https://backrooms-wiki.wikidot.com/the-torment), Sky3 | PAGE_REVIEWED; mô tả dị thường xám, vòng mộ, tượng đá dưới dạng báo cáo. **Không** cho rằng có thể tiếp cận bằng đường thông thường. |

11 điểm Level 0 còn lại chỉ dùng canon Project đã tồn tại hoặc OPEN, **không giả định có URL bài Wiki tương ứng**. 47 điểm ngoài Level 0 được giữ `INDEX_PENDING`, `PROJECT_ONLY`, hoặc `OPEN_PENDING` đến khi nghiên cứu trang riêng và xác minh nguồn.

## Schema của game và phạm vi quyền hạn

`WorldInternetCanon.kt` là sổ chuẩn hóa đọc-only, đối chiếu theo **chính xác** `WorldJourneyOrder.STOPS.key`:

- `GameCanonRecord`: `stopKey`, `parentLevel`, `projectTitle`, `projectAuthority`, `worldNodeId` (null với khu phụ chưa có rank), `source`.
- `InternetCanonSource`: `wikiPageUrl`, `wikiTitle`, `creditedAuthors`, `review`, `observedDate`, `environmentSignals`, `riskReports`, `exitReports`, `conflicts`, `wikiTextLicense`.
- `CanonWebReview`: `PAGE_REVIEWED`, `PROJECT_OVERRIDE`, `SOURCE_TRIMMED`, `PROJECT_ONLY`, `INDEX_PENDING`, `OPEN_PENDING`.
- API thuần `record(key)`, `recordsForLevel(level)`, `directlyReviewed()`. Chỉ `WorldProgressionCore`, `EntityCore`, exit resolver và state máy chơi mới có thể quyết định hiện thực hóa đường đi, vật phẩm hay sự kiện.
- Lớp này **không tự cập nhật khi Wiki sửa**. Mỗi lần ingest phải đọc trang, xác minh tác giả/license, kiểm tra xung đột, mở PR có review, chạy full CI.
- Đã có các unit test khóa 67 stop, 9 bài web thật, hard locks, dữ liệu OPEN, sự tách biệt của exit và rank; không tuyên bố runtime đã tiêu thụ nguồn mới.

## Pháp lý và nguồn ảnh: gate bắt buộc trước khi phát hành phái sinh

Wikidot [Licensing Guide](https://backrooms-wiki.wikidot.com/licensing-guide) công bố nội dung chữ dưới **CC BY-SA 3.0**, yêu cầu ghi công và chia sẻ tương thích các tác phẩm phái sinh. Hướng dẫn cho game đưa ra yêu cầu bổ sung về giấy phép/phân phối. **Không được tự hiểu việc ghi URL là đủ để phát hành game đóng nguồn hoặc game có DRM.** Cần quyết định cách tuân thủ giấy phép ở cấp sản phẩm trước khi dùng bài Wiki làm nền game phát hành; tham vấn pháp lý nếu cần.

Mỗi trang đã duyệt lưu tên người viết và URL để hỗ trợ ghi công. **Không copy hình từ Wiki**: ảnh có thể theo giấy phép riêng và cần một danh mục provenance riêng.

## Bước tiếp theo

1. Duyệt trực tiếp các trang Level 1–13, sublevel và named area được Wiki index liệt kê; mỗi phần phải được viết thành dữ liệu nguồn có trạng thái và ghi công riêng.
2. Đối chiếu `PROJECT_OVERRIDE` và xác nhận tình trạng rewrite; đặc biệt Level 6 tundra tối vĩnh viễn không bị wiki cũ ghi đè.
3. Chỉ sau khi chuẩn hóa xong và được review mới cân nhắc dùng nội dung đã kiểm chứng cho cảnh, truyện, encounter design. **Không** biến source exit thành Core edge chỉ vì nó tồn tại trong văn bản.
