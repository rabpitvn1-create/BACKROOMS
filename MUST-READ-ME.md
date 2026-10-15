# MUST READ ME — Quy trình bổ sung Canon cho Backrooms The Game

> **BẮT BUỘC ĐỌC TRƯỚC KHI THÊM/SỬA CANON.** Tài liệu này mô tả **mã nguồn đang chạy**, không phải kế hoạch xây thêm engine. Khi code và tài liệu khác nhau, kiểm tra code hiện hành trước khi nhập.
>
> Mục tiêu: Canon **đúng nguồn, đủ dữ kiện, đúng lúc, ngắn gọn**, không làm Gehihi tải lâu hoặc vượt quyền Game Core.

## 1. Đường đi thực tế của Canon

1. **Nguồn tác giả đã duyệt** (ví dụ tài liệu trong Google Drive `/Novel/Asset`) → đối chiếu bản gốc, ghi rõ phiên bản/đề mục và trạng thái xác nhận.
2. **Biên dịch thành bản ghi JSON** trong `android-apk/app/src/main/assets/knowledge/`:
   - `knowledge_db.json`: quick cards, hard locks, Level, Entity, nhân vật, vật phẩm, các mối quan hệ cơ bản.
   - `canon_detail_db.json`: các đoạn chi tiết `authored-detail` của CHARACTER / WORLD / LEVEL.
   - `canon_support_db.json`: các đoạn bổ trợ `authored-detail` và `source-metadata`.
3. `KnowledgeContextEngine.kt` đọc **cả ba file khi chạy**, gộp theo ID, lập chỉ mục tag/affordance và **cache trong tiến trình**. Game **không đọc Google Drive và không gọi thêm API Canon ở mỗi lượt**. Sửa tài liệu Drive **không tự cập nhật APK**: phải cập nhật JSON, kiểm thử, build APK mới.
4. `MainActivity.writerPrompt()` ghép Canon packet, trạng thái Core, continuity và luật GM → **một request Gehihi**. Gehihi kể chuyện; **Core** quyết định spawn, combat, inventory, party, Level, thưởng và save.

**Không được** chỉ dán nguyên Codex dài vào prompt hoặc thêm toàn bộ thư viện vào một packet. Cũng không tự xây NPC/Scene/Memory engine mới chỉ vì có thêm tư liệu.

## 2. Trước khi nhập một tài liệu mới

- **Xác minh thẩm quyền**: đây là bản tác giả phê duyệt, bản nháp, báo cáo trong thế giới game, dữ kiện bất định hay bí mật người viết? Chép đúng mức chắc chắn. `UNKNOWN`, `OPEN`, `DYNAMIC` không được biến thành fact đã xác nhận.
- **So bản đang có**: tìm record trùng, ID đã tồn tại, cùng nhân vật/Level/Entity nhưng khác nội dung. Không ghi đè sự thật cũ bằng một đoạn mới mà chưa giải quyết xung đột nguồn.
- **Phân loại**:
  - Điều luật/nền tảng áp dụng mỗi lượt → quick/hard Canon **chỉ khi thật sự bắt buộc**.
  - Nhân vật/Level/Entity phải nhận diện nhanh → quick card ngắn.
  - Hồ sơ dài, mô tả, đối thoại, chiến đấu, môi trường → R2 detail có chủ đề/tag phù hợp.
  - Chỉ dẫn nguồn, bản nháp, spoiler, `WRITER-SECRET` → không đưa vào packet công khai. Không đóng gói bản thảo bí mật vào APK public chỉ vì retriever hiện lọc nó.
- **Giữ nguồn gốc**: đường dẫn/tên tài liệu và anchor đề mục chính xác, không tự nhận đã kiểm chứng Drive khi chưa đọc nguồn thật; tôn trọng quyền tác giả/giấy phép các tài liệu ngoài.

## 3. Schema record đang được parser hỗ trợ

Cả ba JSON có mảng `records`. `KnowledgeContextEngine.parseDatabase()` thực tế đọc các trường sau:

| Trường | Quy tắc |
| --- | --- |
| `id` | **Bắt buộc**; duy nhất trên **cả ba file**; có cấu trúc ổn định, không đổi ID để chữa lỗi truy xuất tạm thời. |
| `domain`, `kind`, `text` | **Bắt buộc**; đúng loại đối tượng; `text` là đoạn Canon ngắn, độc lập, không bỏ mất điều kiện hoặc mức bất định. |
| `authority`, `mutability` | **Bắt buộc**; dùng giá trị đang có và khớp thẩm quyền tài liệu, không tự nâng lên hard lock. |
| `source.document` | **Bắt buộc**; tên/path nguồn thực; `source.anchor` nên ghi chính xác đề mục/đoạn. |
| `priority` | Số nguyên; parser mặc định 80 nếu thiếu nhưng **nên luôn ghi rõ**. `priority <= 30` trở thành mandatory khi record được chọn, có thể làm vượt budget. |
| `tags`, `references`, `affordances` | Mảng chuỗi tùy chọn; dùng đúng ý nghĩa; references phải trỏ ID thật, không dùng để vượt giới hạn quyền hạn. |

Ví dụ *minh họa schema*, **không phải Canon mới để nhập ngay**:

```json
{
  "id": "CHAR.NOVA.DETAIL.PROFILE.001.01",
  "domain": "CHARACTER",
  "kind": "authored-detail",
  "text": "Mô tả nhân vật đã được tác giả xác nhận, có thể tự đứng độc lập.",
  "source": {
    "document": "Google Drive /Novel/Asset/01_Character/NOVA_CODEX.md",
    "anchor": "01 · PROFILE"
  },
  "authority": "CHARACTER_CANON",
  "mutability": "IMMUTABLE",
  "priority": 72,
  "tags": ["detail:profile"],
  "references": [],
  "affordances": []
}
```

`schemaVersion` và metadata ở cấp file đang phục vụ quản trị; **parser runtime chỉ gộp `records`**. Không tự giả định metadata cấp file sẽ trở thành quy tắc trong prompt.

## 4. Đặt ID và tag để retriever thực sự tìm thấy

| Loại | Quy ước hiện tại và điều kiện kích hoạt |
| --- | --- |
| Nhân vật quick card | `CHAR.<CORE_ID_IN_UPPERCASE>.AUTHORED` (+ `.RUNTIME_CORE` nếu cần và đã được Core cung cấp). **Tên được nhắc trong câu người chơi không đủ**; nhân vật phải có ID tương ứng và được Core xác nhận hiện diện/Party. |
| Nhân vật R2 | `CHAR.<CORE_ID>.DETAIL.<TOPIC>.<SECTION>.<SEGMENT>`, `kind=authored-detail` và tag `detail:<topic>`; chỉ xét nhân vật hiện diện. |
| Level quick card | `LEVEL.00`, `LEVEL.00.AUTHORED` cho Level 0; node chính xác: `LEVEL.NODE.<node-id>` theo state/Core. |
| Level/World R2 | `LEVEL.00.DETAIL.ENVIRONMENT.*`, `LEVEL.NODE.<node-id>.DETAIL.*`, `WORLD.DETAIL.*`; selection phụ thuộc Level/node hiện tại và chủ đề `environment`. |
| Entity visual | `ENTITY.<ENTITY_KEY_UPPERCASE_WITH_UNDERSCORES>.VISUAL`. Chỉ chọn khi **Core xác nhận** combat hoặc pending encounter theo entity key; không spawn vì người chơi nhắc tên. |
| Item quick card | `domain=ITEM`, tag tìm kiếm có ý nghĩa. Canon về item **không đồng nghĩa** tạo item trong Inventory. |
| Support R2 | Hiện code chỉ nhận các nhóm `SUPPORT.ASYNC.DETAIL.*`, `SUPPORT.LINH_KHI.DETAIL.*`, `SUPPORT.TU_SI.DETAIL.*`, `SUPPORT.WRITING.DETAIL.*` theo từ khóa/ngữ cảnh. **Thêm prefix SUPPORT mới chưa tự được truy xuất**; phải thay đổi nhỏ có kiểm thử ở `addAuthoredDetails()` nếu thực sự cần. |

Các chủ đề R2 đang được nhận diện: `profile`, `dialogue`, `combat`, `environment`, `visual`; dùng `detail:<topic>` tương ứng. Một chủ thể/chủ đề thường chỉ được chọn **một** đoạn R2 mỗi packet; cần chia đoạn sao cho mỗi đoạn tự đủ nghĩa, không phụ thuộc bắt buộc vào đoạn kế tiếp.

**Đừng lầm “đã được index” với “đã được chọn” hoặc “đã xuất hiện trong packet”.** Bộ lọc hiện tại có điều kiện ID, Core presence, Level, chủ đề và budget. Không phải mọi dạng ID/tag tùy ý đều được đọc lên cho Gehihi.

## 5. Những ranh giới không được phá

- **Core là nguồn sự thật hiện hành**, Canon không tạo Entity/NPC, tự kết nạp Party, chuyển Level, thêm loot, sửa HP hay diễn biến Combat.
- **Canon hậu trường không phải tri thức nhân vật.** Báo cáo, suy đoán, ký ức chưa phục hồi và nguồn tin không chắc chắn phải giữ nguyên ranh giới nhận thức.
- Record `authority=WRITER-SECRET` và `kind=reference-only-level` bị retriever chặn, nhưng **đừng đưa tài liệu bí mật vào asset phát hành public**. GitHub public/commit history có thể lưu vết lâu dài.
- **Level node khác Level cha**. Đối chiếu `LevelRouteRegistry` trước khi thêm lối ra hoặc node; không lấy đường đi từ Wiki biến thành route Core.
- `references` không bảo đảm được nạp: retriever chỉ mở rộng mục tiêu có priority đủ điều kiện, vẫn qua bộ lọc an toàn và budget.
- Các giới hạn trong `KnowledgeContextEngine`: target **2200**, soft **2800**, hard **3400 token ước lượng** (không phải token thực của provider); mandatory có thể khiến lỗi `knowledge_mandatory_context_over_budget`. **Không tăng ceiling tùy tiện.** Canon packet **không phải toàn bộ prompt** do Gehihi còn nhận hướng dẫn, continuity và memory section.
- Không hỗ trợ tương thích save game cũ. Nhưng phải bảo vệ save/load của phiên bản hiện hành và các hợp đồng Core/UI; thêm Canon không phải lý do sửa save schema.

## 6. Checklist nghiệm thu mỗi lần nhập Canon

1. **Review nguồn → mapping**: liệt kê tài liệu và anchor, xác minh ai phê duyệt, điểm `UNKNOWN`, bí mật và xung đột, các record bị thay thế/giữ nguyên.
2. **Review JSON**: parse được cả ba file; không trùng ID; đầy đủ nguồn và authority; `kind`/`domain`/`tags` theo đường chọn thật; không có bản thảo bí mật, credential, nội dung không được phép phát hành.
3. **Kiểm tra retrieval bằng tình huống**: record mới xuất hiện **khi phù hợp**, không xuất hiện nếu nhân vật vắng mặt, Level khác, Entity chưa được Core spawn; dữ kiện `WRITER-SECRET` không rò; các hard lock cũ vẫn còn; tổng packet trong ngân sách. Nếu một record “đã nhập mà không được lấy”, kiểm tra `eligible()`, `addAuthoredDetails()`, `addPresentRuntimeCards()`, `addCurrentLevel()`, `addStateDrivenRecords()`, `budgetedRecords()` trước khi đổ lỗi cho AI.
4. **Chỉ thêm/sửa test có mục tiêu** ở `CanonDetailRetrievalTrialTest`, `KnowledgeContextEngineTest`, `CanonGmIntegrationTest` khi liên quan. Tại `android-apk/`, có thể chạy:
   ```bash
   gradle :app:testDebugUnitTest --tests 'com.rabpit.backroom.core.knowledge.CanonDetailRetrievalTrialTest' --tests 'com.rabpit.backroom.core.knowledge.KnowledgeContextEngineTest' --tests 'com.rabpit.backroom.core.CanonGmIntegrationTest' --no-daemon
   ```
   Không cần chạy toàn bộ test/build liên tục cho từng đoạn nhỏ; dùng CI theo cổng PR của dự án.
5. **Chạy thử APK/diagnostic khi đáng làm**: kiểm tra lời kể đúng dữ kiện, Core không bị qua mặt, kích thước `canon.packet`/`gm.prompt`, thời gian `canon.build` và `gm.http`. CI xanh **không chứng minh** Gehihi luôn kể đúng hoặc API nhanh.
6. **PR gọn, diff rõ**: nhập Canon theo nguồn/nhóm nhỏ; để CI bắt buộc xanh trước khi merge. Không tự squash/merge/phát hành nếu chưa được yêu cầu.

## 7. Mẫu ghi chú cho một lần nhập Canon (đưa vào PR)

```text
Nguồn/phiên bản/anchor:
Ai xác nhận Canon:
Tình trạng FACT / UNKNOWN / OPEN / DYNAMIC / SECRET:
Các file JSON và record ID thêm/sửa:
Tác động Core (NPC presence, Entity, Level route, item): có/không, bằng chứng:
Điều kiện retriever chọn card:
Test có mục tiêu và kết quả thật:
Còn chưa nhập / mâu thuẫn / rủi ro / nội dung không phát hành:
```

## 8. Mã nguồn tham chiếu

- `android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt`: loader, schema thực, index, presence, eligibility, budgets và retrieval.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: `writerPrompt()` và ghép prompt Gehihi.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/LevelRouteRegistry.java`: tuyến Level có thẩm quyền.
- `android-apk/CANON_R2_TRIAL_20261015.md`: biên bản phạm vi/cảnh báo lúc nhập R2; **các trạng thái “trial/chưa merge” là lịch sử**, không thay thế code `main` hiện tại.

**Nguyên tắc chốt:** thêm Canon = thêm **dữ kiện đã xác nhận** + **đường chọn có bằng chứng** + **test đủ để chắc rằng Gehihi nhận đúng khi cần**. Không biến thư viện Canon thành một prompt khổng lồ hay một game engine thứ hai.
