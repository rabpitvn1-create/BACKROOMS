# CANON RUNTIME 2.0 — HƯỚNG DẪN TÍCH HỢP CANON

**File này dành cho bất kỳ ai (người hoặc mô hình AI) mở repo này ra làm tiếp.**
Nó trả lời ba câu hỏi: Canon được đưa vào game *như thế nào*, khi thêm Canon mới
phải làm *đúng quy trình nào*, và hiện tại đã làm *đến đâu*.

- Thiết kế đầy đủ: [`docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md`](../docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md)
  (bản sao nguyên văn của tài liệu thiết kế gốc do tác giả cung cấp ngày 2026-10-24).
- Sự thật vận hành hiện tại: [`CANON_BASELINE_REPORT.md`](CANON_BASELINE_REPORT.md)
  (Gate 0 + Gate 1: execution-path map, hardcode inventory, **12 mục không được phá**).
- Provenance của từng nguồn: [`canon_source_manifest.json`](canon_source_manifest.json).
- Bản đồ nguồn → record: [`KNOWLEDGE_SOURCE_MAP.md`](KNOWLEDGE_SOURCE_MAP.md).
- Kết quả đo: [`CANON_RUNTIME_2_EVALUATION.md`](CANON_RUNTIME_2_EVALUATION.md)
  + [`benchmark-canon-runtime-2.py`](benchmark-canon-runtime-2.py).

---

## 1. Kiến trúc CR2 trong một trang

Vấn đề: GM (model AI) yếu, quota thấp, nhưng Canon rất lớn và có hard lock.
CR2 giải bằng cách **không để GM tự nhớ hay tự tìm Canon**: một pipeline xác định
(deterministic) ở phía Android chọn đúng phần Canon tối thiểu cho từng lượt và
Core thực thi luật cứng; GM chỉ viết văn xuôi trong ranh giới đã quyết.

Năm thành phần:

1. **Canon Record v2** — đơn vị tri thức nhỏ, độc lập, có provenance. Không phải
   "một thẻ = một đoạn văn bất kỳ": mỗi record có nhận diện (`id`, `entity_id`),
   phân loại (`domain`, `kind`, `authority`, `mutability`), trạng thái sự thật
   (`truth_status`), phạm vi tri thức (`knowledge_visibility`), điều kiện kích hoạt,
   cạnh quan hệ (`references`) và nguồn (`source.document/anchor/hash`).
   Dữ liệu hiện hành: `app/src/main/assets/knowledge/knowledge_db.json`
   (schemaVersion 2, 66 record — 55 gốc + 11 bổ sung từ snapshot Drive 2026-10-24).
2. **Scene-first Resolver** — đầu vào là `SceneSnapshot` chuẩn hoá từ Game State
   (Level, actor hiện diện, entity đã xác nhận, intent, vật trong focus), **không**
   phải câu chữ tự do của người chơi. Truy xuất đi qua các cổng theo thứ tự:
   Cổng 0 scope/POV → Cổng 1 ID chính xác → Cổng 2 alias có kiểm soát → Cổng 3
   typed edge (maxHops = 1) → Cổng 4 affordance/FTS nhẹ. Không cổng nào được phép
   "đoán gần đúng": thiếu bằng chứng thì trả `NO_CANON_MATCH`.
3. **ContextCompiler** — đóng gói record thành context cho GM theo profile ngân
   sách (`COMPACT`/`STANDARD`/`RICH`), **chỉ gồm record nguyên vẹn**: thà bỏ cả
   record optional còn hơn cắt cụt một hard record. Required không vừa ngân sách
   thì chuyển `NEEDS_SAFE_LOCAL_RESOLUTION`, không lách bằng cách cắt chữ.
4. **Core + Validator + Reducer** — luật gameplay cứng (dice, sở hữu vật phẩm,
   spawn, quest gate, commit state) do Core thực thi và validate trước khi commit;
   lời kể của GM chưa commit thì không thành sự thật. Giữ nguyên pipeline hiện hữu
   (xem baseline report) — CR2 thay lớp *truy xuất/đóng gói*, không thay Core.
5. **QuotaGovernor + feature flag** — CR2 chạy sau flag `CANON_RUNTIME_2_ENABLED`
   (mặc định **OFF**) để so OLD/NEW và rollback được; không gọi thêm model phụ cho
   một lượt thường.

## 2. Luồng một lượt

```text
Người chơi nhập hành động
        │
        ▼
Game State Core khoá state ──► SceneSnapshot (Level, actor, entity CONFIRMED,
        │                        intent, focus) — mention trong câu chữ KHÔNG
        │                        được nâng thành sự thật ở bước này
        ▼
Resolver: Cổng 0 scope/POV ─► Cổng 1 ID chính xác ─► Cổng 2 alias (có scope)
        │              ─► Cổng 3 typed edge (maxHops=1) ─► Cổng 4 affordance
        │              + trace lý do chọn/từ chối từng record
        ▼
ContextCompiler: required trước → optional theo (priority, id)
        │          trọn record, trong ngân sách profile; required thiếu
        │          → báo missing_required tường minh / NEEDS_SAFE_LOCAL_RESOLUTION
        ▼
GM viết lượt (chỉ thấy fact đã chọn + hệ quả Core đã quyết, không thấy metadata)
        │
        ▼
Validator kiểm tra output theo hard rule ─► hợp lệ mới commit state
        │
        ▼
StoryContinuityReducer ghi sự kiện bền từ state ĐÃ validated (không phải AI tóm tắt)
```

## 3. Bất biến bắt buộc (phá vỡ bất biến = phá thiết kế)

Từ §0.3 của tài liệu thiết kế:

- GM **không phải nơi lưu sự thật**; lời kể chưa commit không thành state.
- Luật gameplay và Canon cứng do **Core thực thi**, không chỉ trông cậy prompt.
- Không bao giờ đưa toàn bộ codex của một nhân vật vào prompt chỉ vì nhân vật hiện diện.
- Không gọi model bổ sung để tìm kiếm/tóm tắt/kiểm tra một lượt thông thường.
- Cảnh chưa xác nhận không được nâng thành sự thật; không lộ spoiler, tri thức
  hậu trường, hay thông tin ngoài POV.
- Không hy sinh Canon cứng vì ngân sách token: không chứa nổi phần tối thiểu thì
  giảm nội dung tuỳ chọn hoặc vào chế độ hạn chế an toàn.
- Giữ save/load **phiên bản hiện tại**; tuyệt đối không tạo migration/adapter/
  fallback cho save cũ.
- Lỗi quota/mạng/process chết/pack hỏng/thiếu bộ nhớ không được gây commit nửa
  lượt, nhân đôi lượt, hay xoá tiến trình.

Bốn cặp lỗi phải phân biệt tuyệt đối (§16) — mọi lỗi thuộc bốn cặp này nghiêm
trọng hơn lỗi văn phong:

1. "Canon not found" **≠** "Canon permits anything".
2. "Source says unknown" **≠** "source absent".
3. "Entity mentioned" **≠** "confirmed encounter".
4. "NPC has ability" **≠** "NPC uses ability".

## 4. Thêm Canon mới — quy trình ingest 4 bước (bắt buộc theo thứ tự)

Không ai — kể cả mô hình AI hỗ trợ — được viết thẳng record vào
`knowledge_db.json` từ trí nhớ. Mọi Canon mới đi qua pipeline §4:

**Bước A — Nguồn và snapshot.** Xác định tài liệu nguồn (Drive/thư mục được chỉ
định), ghi `source_id`, revision, và **SHA-256 của nội dung**. Tài liệu không đọc
được thì ghi `SOURCE_UNAVAILABLE` — cấm nhảy sang tài liệu "tương tự" hay bịa nội dung.

**Bước B — Parse theo cấu trúc.** Chia tài liệu theo *ý nghĩa và phạm vi ràng buộc*
(giữ heading/mã mục/page làm neo), không chunk cứng theo số ký tự. Ưu tiên record
chứa 1–5 mệnh đề liên quan, áp dụng độc lập được.

**Bước C — Review.** Record quan trọng (hard rule, khoá quan hệ, năng lực, spoiler)
bắt buộc có source anchor và qua tác giả/reviewer duyệt. LLM ở bước này chỉ được
*đề xuất* record, không tự phê duyệt. Không chứng minh được nguồn →
`truth_status` giữ ở mức chưa xác nhận / `review_status` chờ duyệt.

**Bước D — Compile và manifest.** Schema-check → kiểm tra trùng ID → báo cáo
alias collision → kiểm tra broken reference → báo cáo xung đột → **kiểm tra cấm
UNKNOWN promotion**. Cập nhật `canon_source_manifest.json` (nguồn → hash →
record_ids → review_status) và coverage: phần chưa có nguồn phải nằm trong
manifest dưới dạng `KNOWN_MISSING`/`PENDING_REVIEW`, không được lấp bằng nội dung bịa.

### Field v2 bắt buộc của một record

`id` (ổn định, có namespace), `schema_version: 2`, `universe_id`, `entity_id`,
`domain`, `kind`, `authority`, `mutability`, `truth_status`
(`CONFIRMED`/`RUMORED`/`INFERRED`/`UNKNOWN`/`CONTRADICTED`/`DEPRECATED_SOURCE`),
`knowledge_visibility`, `text`, `tags`, `affordances`, `references`,
`priority` (chỉ xếp hạng trong cùng lớp — không bao giờ vượt hard lock),
và `source` gồm `document_id` + `anchor` + `content_sha256` (null chỉ khi nguồn
thật sự chưa snapshot được, kèm `review_status = "PENDING_SOURCE_SNAPSHOT"`).

### Ba điều cấm khi thêm Canon

- **Cấm bịa provenance**: không có hash/anchor giả. Chưa có snapshot thì để null +
  cờ chờ (sau snapshot 2026-10-24 chỉ còn 7/66 record chờ vì tài liệu gốc không còn trên Drive — xem `canon_source_manifest.json` mục `missing_sources`).
- **Cấm UNKNOWN promotion**: record `UNKNOWN` (hiện có đúng 1:
  `REL.LUC_TRAM.SYVIAL.BASELINE`) không bao giờ được viết lại thành khẳng định canon,
  không được dùng làm căn cứ suy ra record CONFIRMED khác.
- **Cấm suy diễn chéo**: `MAY_CONTAIN` ≠ có mặt; `MENTIONED` ≠ `CONFIRMED`;
  `KNOWS_OF` ≠ `HAS_SEEN`; `CAN_DO` ≠ `WILL_DO`; quan hệ baseline ≠ delta runtime.

## 5. Trạng thái triển khai theo Gate (cập nhật 2026-10-24)

| Gate | Trạng thái | Sản phẩm / nơi xem |
|---|---|---|
| 0 — Audit & baseline | ✅ Xong | `CANON_BASELINE_REPORT.md` (execution-path map, 12 mục không được phá) |
| 1 — Ingest nguồn thật + manifest | ✅ Xong + snapshot Drive đầy đủ (2026-10-24) | `knowledge_db.json` v2 (**66 record**, 65 CONFIRMED / 1 UNKNOWN), `canon_sources/` (24 tài liệu, SHA-256 khớp Drive), `canon_source_manifest.json` v2 (24 nguồn + 3 Google Doc chờ kết nối + 4 nguồn NOT_FOUND_ON_DRIVE giữ nguyên trạng thái chờ, không thay thế). 56/66 record có `content_sha256` từ tài liệu nguồn chính; 11 record mới tạo từ nguồn thật (CHAR.CAO_MINH.IDENTITY, CHAR.LUC_TRAM.COMBAT, CHAR.LUC_TRAM.UNKNOWN, STORY.MAIN.LUC_TRAM_EXISTENCE, WRITING.ADDRESS/PROSE_POV/VIVID_VERBS/HORROR/INTIMACY/DIALOGUE_BENCHMARK/GUIDE_SUPPLEMENT). 3 hàng Source Map còn thiếu vì MAIN_STORY.md không còn trên Drive: STORY.MAIN.PREMISE, STORY.MAIN.COMMS_INITIAL, STORY.MAIN.SYVIAL_EXISTENCE |
| 2 — CanonStore v2 + indexed lookup | ✅ Code đã có (chưa compile bằng toolchain thật) | Package `core/canon` (`CanonStore`, `JsonCanonStore`), sau feature flag `CANON_RUNTIME_2_ENABLED` (đã vào `app/build.gradle`, mặc định OFF) |
| 3 — SceneResolver + CanonResolver + ContextCompiler | ✅ Code đã có (chưa compile bằng toolchain thật) | `SceneResolver`/`LegacyJsonSceneResolver`, `CanonResolver`, `ContextCompiler` + facade `CanonRuntime2`/`CanonOutcome` trong package `core/canon`; wiring vào `submitTurn` qua `patch-canon-runtime-2.py` (đã kiểm chứng áp sạch + idempotent trên bản sao) |
| 4 — Durable Story Memory + deterministic guards | ⏳ Chưa làm | Mở rộng `StoryContinuityReducer`, giữ nguyên hợp đồng hiện hữu |
| 5 — QuotaGovernor + offline tiers + resource policy | 🔶 Một phần | `QuotaGovernor`/`DefaultQuotaGovernor` đã có code (admission + máy trạng thái 429, đồng hồ injectable) và được nối vào đường generate qua `patch-canon-runtime-2.py`; fault-injection matrix + offline tiers chưa làm, không đốt quota thật |
| 6 — Benchmark, rollout | 🔶 Một phần | `benchmark-canon-runtime-2.py` (mô phỏng trên DB thật: 8/8 PASS) + `CANON_RUNTIME_2_EVALUATION.md`; rollout chờ Gate 2–5 và đo trên thiết bị thật |

Đường build cho Gate 2–5: code CR2 là **file nguồn trực tiếp** trong package
`core/canon` (không sinh bằng patch — patch chỉ làm phần cắm vào luồng).
`patch-canon-runtime-2.py` đã tồn tại: nó thêm `buildConfigField
CANON_RUNTIME_2_ENABLED` (đã có sẵn trong `app/build.gradle`, mặc định OFF),
chèn nhánh CR2 vào `GameBridge.submitTurn` và adapter `QuotaGovernor` quanh
đường generate trong `MainActivity.java`. Patch đã được kiểm chứng trên bản
sao: áp sạch lên MainActivity/build.gradle sạch, chạy lần hai là no-op
(idempotent). Khi flag tắt, hành vi giống hệt bản hiện hành.

**Giới hạn kiểm chứng (ghi rõ để không ai hiểu nhầm):** toàn bộ code
Kotlin/Java của CR2 **chưa được biên dịch bằng toolchain thật** — môi trường
làm việc hiện tại không có JDK/Android SDK. Những gì đã kiểm là kiểm tĩnh:
đối chiếu chữ ký/anchor giữa patch và package `core/canon`, cân bằng ngoặc
của MainActivity sau patch, tính idempotent của patch. Việc compile bằng
Gradle và chạy unit test thuộc về máy build/CI của tác giả; chỉ sau bước đó
mới được coi Gate 2/3/5 là "xong" hẳn.

## 6. Những điều cố ý KHÔNG làm (§15) — đừng "sửa" chúng

1. **Không fine-tune model riêng cho Canon** — Canon thay đổi liên tục, fine-tune
   không phải nguồn sự thật và chi phí lớn.
2. **Không đọc Drive mỗi lượt chơi** — phụ thuộc mạng/quota/quyền truy cập.
3. **Không dùng full vector RAG làm tuyến chính** — dễ sai exact name, scope,
   phủ định, alias và hard lock; semantic chỉ là cổng tuỳ chọn có kiểm chứng.
4. **Không dùng graph server (Neo4j)** — typed edges trong store cục bộ là đủ.
5. **Không chèn toàn bộ tài liệu nguồn vào prompt** — chỉ gửi record cần thiết,
   metadata nguồn giữ ở local.
6. **Không nhét local LLM nặng vào mọi APK** — chỉ thiết bị đủ tài nguyên + đã
   benchmark mới được bật.
7. **Không cho AI tự tóm tắt rồi ghi đè Canon/state** — model chỉ tạo candidate;
   Core xác nhận mới thành dữ kiện bền.
8. **Không tạo schema tương thích cho save cũ** — chỉ bảo vệ save hiện hành.

## 7. Quy tắc khi sửa code liên quan

- **Không phá 12 mục** trong `CANON_BASELINE_REPORT.md` mục "không được phá"
  (chữ ký `KnowledgeContextEngine`, định dạng packet v1 + dòng metadata, thứ tự
  pipeline và ngữ nghĩa ngân sách của engine v1, validator luôn chạy, reducer chỉ
  ghi từ state đã validated, test hiện hữu giữ xanh, các anchor của
  `patch-knowledge-context-builder`, benchmark v1 giữ chạy được…).
- **Mọi thay đổi runtime phải qua patch script** như convention của repo: sửa
  patch (vd `patch-canon-runtime-2.py` cho CR2) rồi để chuỗi patch sinh artifact;
  không sửa tay file đích mà không sửa patch tương ứng, và ngược lại.
- Sau khi đổi dữ liệu Canon: chạy cả hai benchmark —
  `python3 benchmark-knowledge-context.py` (gate v1) và
  `python3 benchmark-canon-runtime-2.py` (gate CR2) — cả hai phải exit 0.
- Đổi `knowledge_db.json` thì cập nhật đồng thời `canon_source_manifest.json` và,
  nếu nguồn thay đổi, `KNOWLEDGE_SOURCE_MAP.md`.
- Không tuyên bố chỉ số chất lượng (đặc biệt là "90%") khi chưa có dữ liệu đo
  thật; ghi kết quả mới vào `CANON_RUNTIME_2_EVALUATION.md` theo đúng mẫu
  "đã đo / chưa đo" của tài liệu đó.
