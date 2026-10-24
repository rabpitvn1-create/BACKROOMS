# CANON RUNTIME 2.0 — EVALUATION (Gate 6, đợt đầu)

Trạng thái: **bản đánh giá mô phỏng đầu tiên**, ngày 2026-10-24.
Công cụ: `benchmark-canon-runtime-2.py` (cùng thư mục), chạy trên `knowledge_db.json`
schemaVersion 2 thật (55 record, sha256 bản đo: `5527a117d44f1a7c…`).
Tài liệu thiết kế: `docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md` (§12 là khung đo).
Hướng dẫn tích hợp: `CANON_RUNTIME_2_README.md`.

> Nguyên tắc của tài liệu này (theo §12): tách rõ **đã đo** khỏi **chưa đo**.
> Không có con số "90%" nào được tuyên bố trong đợt này, vì thước đo Narrative Task
> Quality (§12.1 mục 3) chưa có dữ liệu — nó cần model thật chạy trên thiết bị thật.

---

## 1. ĐÃ ĐO — mô phỏng Python trên DB thật (8/8 kịch bản PASS)

Mô phỏng thực thi đúng semantics thiết kế: scene-first, Cổng 0 (scope/POV) → Cổng 1
(ID chính xác) → Cổng 2 (alias có scope) → Cổng 3 (typed edge, maxHops = 1) → Cổng 4
(affordance); ContextCompiler trọn record, estimator bảo thủ `ceil(chars/3.5)`,
ngân sách COMPACT 2600 / STANDARD 3200 / RICH 3900 token ước lượng.

| # | Kịch bản | Kết quả đo thật |
|---|---|---|
| a1 | Required recall — điều tra + đối thoại (Cao Minh + Lục Trầm, Level 1, intent `inspect_tracks` + `dialogue`), STANDARD | **Recall 12/12 required**; packet 14 record, ~3.096/3.200 tok; missing = 0 |
| a2 | Required recall — combat, `ENTITY.BEAST_LEVEL_5` ở trạng thái CONFIRMED_PRESENT (Level 5), RICH | **Recall 8/8 required** (gồm entity record + `ENTITY.GLOBAL_HARD_LOCK` qua cạnh hop 1); packet 17 record, ~3.314/3.900 tok |
| b | Adversarial — Smiler chỉ được *nhắc* trong action; Hound/False Puddle chỉ là MAY_CONTAIN của Level 1 | **0 record rò rỉ** vào packet; cả hai cạnh MAY_CONTAIN bị chặn có trace |
| c | Alias mơ hồ "kiếm" (3 record thuộc 2 entity) | Không scope → **AMBIGUOUS, chọn 0**; scope = 1 chủ thể hiện diện → chọn đúng record của entity đó |
| d | Record UNKNOWN (`REL.LUC_TRAM.SYVIAL.BASELINE`) trong scene đủ điều kiện | Được truy xuất và vào packet **với cờ `truth=UNKNOWN`**; **không** nằm trong tập affirmed canon (17/18 record của packet là CONFIRMED) |
| e | Ngân sách trọn record, cùng một scene ở COMPACT và RICH | COMPACT: 12 record, ~2.545/2.600 tok; RICH: 17 record, ~3.781/3.900 tok; **không packet nào vượt ngân sách, không record nào bị cắt cụt** (kiểm chứng bằng đối chiếu nguyên văn text) |
| f1 | Ngân sách ép xuống 100 tok | Required không vừa được **báo tường minh** trong `missing_required` (đầu danh sách: `GAME.GM.FAIRNESS`, `GAME.TEXT.CORE`, …), trạng thái packet = `NEEDS_SAFE_LOCAL_RESOLUTION`, packet vẫn ≤ 100 tok — không cắt cụt hard record để lách ngân sách |
| f2 | Scene đòi Level không tồn tại (`LEVEL.99`) | Báo tường minh `('LEVEL.99', 'NO_CANON_MATCH')`; phân biệt rõ "canon not found" với "canon permits anything" |

Số liệu nền của corpus:

- 55 record; chi phí trọn record: min 64 / trung vị 144 / max 458 token ước lượng.
- Nếu dump toàn bộ DB vào prompt: ~9.480 tok. Packet CR2 ở các scene trên dùng
  ~2.545–3.781 tok, tức khoảng **27–40%** dung lượng dump toàn bộ, trong khi vẫn đạt
  recall required 100% trên 2 scene mẫu.
- Benchmark cũ `benchmark-knowledge-context.py` vẫn chạy độc lập và PASS (exit 0);
  hai benchmark không thay thế nhau: bản cũ là smoke/contract gate của engine v1,
  bản này đo semantics CR2.

### Giới hạn của chính phép đo này

- Scene mẫu do người viết benchmark định nghĩa tay (2 scene recall, mỗi loại
  adversarial 1 mẫu) — đây là **smoke corpus**, chưa phải gold corpus gắn nhãn quy mô
  §12.2 (chưa có synthetic stress corpus, chưa có biến thể không dấu/đồng âm/phủ định
  hàng loạt, chưa chạy 1.000–10.000 lượt mô phỏng).
- DB v2 chưa có `activation`/`aliases`/typed edges thật (phạm vi Gate 2): benchmark
  suy luận cạnh LEVEL→ENTITY là MAY_CONTAIN và dựng alias index từ segment của
  record id. Khi Gate 2 bổ sung dữ liệu thật, benchmark phải được chạy lại và các
  suy luận này phải bị thay bằng dữ liệu.
- Estimator chars/3.5 là ước lượng bảo thủ, chưa hiệu chỉnh theo token usage thật
  của provider (§7.2 yêu cầu ghi observed usage để hiệu chỉnh — chưa có dữ liệu).

## 2. CHƯA ĐO — cần thiết bị / API thật (không suy đoán thay số liệu)

| Hạng mục (§12.2–12.3) | Vì sao chưa đo được ở đợt này |
|---|---|
| Latency resolver p50/p95/p99 trên điện thoại (mục tiêu đề xuất < 100 ms warm) | Chưa có runtime Kotlin CR2 trên thiết bị/emulator; mô phỏng Python không đo thay được |
| RAM/CPU/ANR/battery khi corpus tăng 10× | Cần thiết bị low-RAM/trung bình/mạnh thật và pack lớn hơn 55 record |
| Narrative Task Quality — tỷ lệ weak-model + CR2 so với strong baseline (hướng tới ≥ 0,90) | Cần chạy model thật + rubric chấm mù trên corpus công bố trước; **chưa có bất kỳ dữ liệu nào** |
| Quota thật (429, cooldown, RPD/TPM của provider) | Chỉ được phép đo bằng fault injection theo §12.2, không đốt request thật; harness fault injection chưa xây |
| Token usage thật của provider để hiệu chỉnh estimator | Cần log observed usage từ các lượt chạy thật qua feature flag |
| Long-run 1.000–10.000 lượt + kill/restart fault injection (Gate 4) | Cần durable story memory của Gate 4 hoàn thành trước |
| Cold/warm start, pack tampering/security audit (Gate 6) | Cần pack format + cơ chế ký của Gate 2/5 |

## 3. Quyết định rút ra cho đợt này

- Semantics truy xuất của CR2 **đạt các gate an toàn ở mức mô phỏng**: 0 rò rỉ
  confirmed từ mention/MAY_CONTAIN, 0 UNKNOWN promotion, 0 cắt cụt hard record,
  missing-required luôn tường minh. Đây là điều kiện cần, chưa phải điều kiện đủ
  để bật CR2 cho người chơi.
- Việc bật rộng CR2 vẫn phải chờ: runtime Kotlin sau feature flag
  `CANON_RUNTIME_2_ENABLED` (mặc định OFF), đo trên thiết bị thật, và corpus gắn nhãn
  lớn hơn. Xem lộ trình gate trong `CANON_RUNTIME_2_README.md`.

## Hiệu chuẩn lại sau Drive snapshot (2026-10-24)

Corpus tăng 55 → 66 record sau khi snapshot toàn bộ tài liệu Canon từ Google Drive (xem `canon_source_manifest.json` v2). Hai kịch bản benchmark đổi kết quả vì lý do corpus, đã xử lý như sau:

- **(a1) STANDARD 3200 → 3600**: tập required của scene đối thoại có Lục Trầm tăng thêm record gác OPEN `CHAR.LUC_TRAM.UNKNOWN` (affordance `dialogue`, ~163 token) và record UNKNOWN mà nó tham chiếu `REL.LUC_TRAM.SYVIAL.BASELINE` (~194 token). Đây là hành vi đúng theo thiết kế — canon "chưa thiết lập" phải hiện diện để GM không tự bịa — nên profile được hiệu chuẩn lại thay vì cắt record.
- **(e) COMPACT báo missing-required tường minh**: cùng scene vượt COMPACT 2600 một cách hợp lệ; kịch bản nay kiểm tra đúng hợp đồng thiết kế (COMPACT báo thiếu tường minh + trọn record, RICH 3900 chứa đủ, không missing).
- Kết quả sau hiệu chuẩn: **8/8 PASS** trên corpus 66 record. Lưu ý khi hiệu chuẩn profile Kotlin thật: ngân sách STANDARD phải tính cả các record gác OPEN của nhân vật hiện diện, không chỉ record nội dung.
