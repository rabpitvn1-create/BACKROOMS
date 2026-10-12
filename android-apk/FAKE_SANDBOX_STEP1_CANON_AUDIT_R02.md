# Fake Sandbox · Bước 1/10 · Audit Canon R02

**Phạm vi:** chỉ kiểm toán ba miền CHARACTER / LEVEL / ENTITY, tổ chức và truy nguyên nguồn, phát hiện thiếu / trùng / xung đột và kiểm tra đường truy xuất GM. **Không sửa gameplay, Canon nguồn, prompt GM, kịch bản, Combat hay Inventory.**

**Mốc đối chiếu:** repository `main` tại `f91cbcf6e9200021730c5e2f76cea1afa3f8dae0`; PR #590 (nhánh Canon Step 2) là **đề xuất chưa merge**, không được nhầm với runtime `main`. Nguồn tác phẩm đối chiếu ngày 2026-10-12: Google Drive `/Novel/Asset` và [CANON_REGISTRY_R01](https://docs.google.com/document/d/1Na1enfT6KMMWvTenOS6jusnUZs7pN3RhBSvjo2jByUA/edit).

## 1. Luật nguồn và thẩm quyền

1. **Hai nhóm nguồn lore được phép:** tác giả tại Google Drive `/Novel/Asset`; Internet công khai dùng như tài liệu Backrooms tham khảo, phải giữ URL truy nguyên. **Không lấy repository làm nguồn sáng tác Canon thứ ba.** Repository/Game Core chỉ là sự thật về **runtime** hiện có.
2. Drive tác giả ưu tiên khi có tác giả chủ đích thay đổi quy luật Backrooms. Địa danh hoặc mô tả xung đột chưa rõ phải `SOURCE_CONFLICT`, không sửa âm thầm theo Wiki hoặc tên runtime.
3. Baseline nhân vật / Level / Entity không ghi đè dữ kiện **DYNAMIC** đã xảy ra. Phân biệt `SELF-CANON`, `CROSS-CANON`, `POV/BELIEF`, `WRITER-SECRET`, `DYNAMIC`, `OPEN/UNKNOWN`. Nhân vật được **nhắc tên** không có nghĩa **đang hiện diện**.
4. GM nhận thẻ Canon liên quan theo state, không tải toàn bộ tài liệu Drive hoặc Internet mỗi Turn. Gemini biên kịch và GM không được nhận tình tiết giả định từ bài kiểm thử hay ví dụ thảo luận như dữ kiện đã xảy ra.
5. **Phạm vi gameplay tách biệt:** Canon baseline không tự kích hoạt Entity/Combat hoặc sửa HP, Inventory; Story Continuity không phải cơ chế sửa Combat. Milestone và kết thúc Level thuộc kế hoạch gameplay, **không phải** dữ liệu Canon được tự thêm trong audit.

## 2. Kiểm kê nguồn Character

Nguồn đã đọc:

- `/Novel/Asset/01_Character/CAO_MINH_CODEX.md`: Canon CURRENT và addendum R18, có nhãn SECRET/DYNAMIC và giới hạn tri thức.
- `LUC_TRAM_CODEX.md`: CURRENT / R06, yêu cầu firewall tri thức và bản sắc riêng.
- `LUCIA_LUC_CODEX.md`: **DRAFT / AUTHOR-CONFIRMED PARTIAL**, runtime ID `lucia`; **không** được nâng mọi trường lên CURRENT.
- `TRAC_LAM_CODEX.md`: có thiết lập riêng; việc được ghi trong Asset không chứng minh đã được đăng ký làm nhân vật điều khiển được trong Core.
- `Syvial_Codex.docx`: bản sao hiện có trong Asset; không còn ghi nhầm rằng Syvial hoàn toàn thiếu tài liệu trong Asset.

**Thiếu/deferred theo tác giả:** Iris và An Nhiên chưa có Character Codex chuyên biệt tại `/Novel/Asset/01_Character`. Giữ `OPEN/UNKNOWN`; không bịa hồ sơ để khóa tiến độ. **Lucia và Lục Trầm là hai phiên bản riêng**, không dùng suy luận một người cho người kia.

**Rủi ro tri thức:** WRITER-SECRET trong các Codex không phải sự thật mà nhân vật tự biết. Cần test riêng việc GM retrieval không làm rò bí mật khi actor không có provenance; chỉ thấy hệ thống có budget/lookup không đủ chứng minh firewall ngữ nghĩa kín hoàn toàn.

## 3. Kiểm kê Level / node

Nguồn `/Novel/Asset/02_Level/BACKROOMS_WORLD.md`: baseline Level chính **0–10** và các khu vực/sublevel. Code `LevelRouteRegistry.java` trên `main` đăng ký **25 node trong Level 0–6**, không có chứng cứ hỗ trợ gameplay Level 7–10.

**Hai xung đột chưa được duyệt để hợp nhất:**
- Route `0.1`: Drive `Deep Emptiness`; runtime `Zenith Station`.
- Route `0.7`: Drive `Claustrophobia`; runtime `The Reminiscence District`.

Giữ cùng lúc cả hai phát biểu có provenance; không coi tên runtime là Canon tác giả và không đổi tên Game Core bằng phỏng đoán.

**Khác biệt cấu trúc bắt buộc:** `Level` (tầng chính), `LevelRouteRegistry` exact node (ví dụ `0.1`, `manila_room`, `red_rooms`) và **Story Checkpoint** là ba khái niệm khác nhau. Không ánh xạ 20 Story Checkpoint của Level 0 thành 20 node môi trường. Không được dùng số lượng Level được viết trong World file làm chứng cứ game đã mở Level đó.

Nguồn Internet được Canon Registry tham chiếu để đối chiếu nhưng **chưa kiểm chứng độc lập trong lượt audit này**: https://backrooms-wiki.wikidot.com/normal-levels-i và https://backrooms-wiki.wikidot.com/level-0. Không nhập phát biểu mới từ URL chưa đọc.

## 4. Kiểm kê Entity

`EntityEncounterPool.java` trên `main` có **22 runtime keys**, chia 14 common, 3 Lifeform, 3 rare, 2 killers (tổng 45% ở luồng RNG hiện tại). Đây là **thống kê code runtime**, không phải quy tắc viết kịch bản mới.

`/Novel/Asset/03_Entity/Entity.md` có mô tả ngoại hình cho **22/22 Entity trong pool**, và thêm 3 đề mục ngoài pool: **ASYNC Rifleman, CopX, Tâm Ma Cao Minh**. Không được tự đưa ba đề mục này vào Encounter Pool hoặc suy ra chúng là Boss.

**Thiếu/deferred:** mô tả hình dáng không tương đương hồ sơ hành vi, giới hạn năng lực, chiến thuật hoặc điều kiện sinh ra. Không tự suy kỹ năng/tỷ lệ xuất hiện từ hình ảnh hay Wikipedia/Wiki.

Tham khảo Internet đã được Registry ghi URL nhưng chưa xác minh độc lập trong lượt này: https://backrooms-wiki.wikidot.com/entities ; Hound: https://backrooms-wiki.wikidot.com/entity-8.

## 5. Tính đầy đủ của runtime knowledge và R0–R3

**Thực trạng đã đối chiếu:**
- `main`: `knowledge_db.json` chứa **48 records** (16 Character, 7 Level, 12 Entity, 4 Item, 2 Game Rule, 4 Writing, 1 World, 2 Relationship). Nhiều provenance vẫn là đường dẫn legacy repository, **không phải URL/ID trong hai nguồn lore được duyệt**.
- Nhánh Step 2 cũ tại PR #590: bổ sung 41 authored/visual records lên **89**, đổi provenance 6 record Syvial sang bản Asset; còn **42 legacy records** chưa chứng thực nguồn theo luật mới. **Chưa merge vào main.**
- `KnowledgeContextEngine` có lookup indexed, lọc state/present actors/exact Level node/Entity key và giới hạn packet; nhánh PR #590 đã sửa lookup theo rolls `selectedEntityKey` trước Combat và Lucia encounter.
- R0 Resolve: từ Core state và Milestone xác định exact actor/Level node/Entity đã được xác nhận.
- R1 Quick Cards: các facts ngắn với nguồn, scope, authority, visibility.
- R2 Detail: chỉ lấy dữ liệu cần cho tình huống trong ngân sách; không quét toàn Codex mỗi Turn.
- R3 Author-only: được dùng để bảo vệ tính nhất quán **nhưng phải có kiểm soát kiến thức theo POV**, không được xuất sang NPC hoặc câu trả lời GM khi không có căn cứ.

**Chưa xác minh:** bộ kiểm thử đã chứng minh từng lookup/gắn provenance cơ bản, nhưng chưa có bằng chứng bao phủ tất cả cross-canon reference, `WRITER-SECRET` leakage hay mọi trường hợp mâu thuẫn dữ liệu. Không đánh dấu R0–R3 hoàn tất toàn diện chỉ vì CI/build chạy thành công.

## 6. Sổ vấn đề và cách xử lý

| ID | Tình trạng | Quyết định tạm thời |
|---|---|---|
| C-01 | Iris / An Nhiên thiếu hồ sơ tác giả chuyên biệt | `OPEN/UNKNOWN`, bổ sung sau, **không block** runtime retrieval |
| C-02 | Lucia Codex DRAFT PARTIAL | Giữ nhãn DRAFT; dùng phần tác giả xác nhận, giữ field còn thiếu là UNKNOWN |
| C-03 | Drive/runtime bất đồng node 0.1 và 0.7 | `SOURCE_CONFLICT`; giữ 2 provenance, chưa auto sửa route |
| C-04 | Entity behavior chưa đủ | Chỉ dùng thẻ VISUAL được chứng thực; không tự suy gameplay |
| C-05 | 3 Entity.md headings nằm ngoài pool 22 | `REFERENCE_ONLY`, không tự mở Encounter |
| C-06 | 42 records legacy chưa có nguồn đối chiếu được duyệt | Ghi nợ truy nguyên; **không xóa làm mất gameplay** |
| C-07 | Level 7–10 có trong Drive nhưng chưa có route gameplay | `REFERENCE_ONLY`; không mở Level tự động |
| C-08 | R3 / knowledge firewall cần kiểm thử hành vi thêm | Giữ nguyên rule, thêm targeted tests ở giai đoạn code thích hợp |
| C-09 | 20 Checkpoint Level 0 và Level node dễ bị đánh đồng | Giữ identifier và transition owner tách biệt |
| C-10 | Chưa có bằng chứng Internet URL được đối chiếu phiên bản hiện hành | Để pending, không nhập hoặc trích khẳng định mới |

## 7. Điều kiện kết thúc Bước 1 và đường vào Bước 2

Audit có thể được coi là hoàn tất **trong phạm vi nguồn đã đọc** khi inventory, nguồn, gap, contradiction, current runtime mapping và retrieval risks đã được ghi rõ, **không phải khi mọi Codex đều đã đủ**. Các gap deferred được tác giả cho phép bổ sung sau, không tự đẩy Bước 1 thành công việc sáng tác.

Bước 2 chỉ được triển khai sau khi audit này được review; chọn lại các phần code hợp lệ từ PR #590 theo source attribution, kiểm tra quyền tri thức và thực thi các targeted tests. Không đưa một dòng giả định lore vào assets hoặc prompt. Cần ghép đúng chuỗi branch với Bước 3–7 trước khi tuyên bố runtime tích hợp.

**Phân tách nghiêm ngặt:** Bản audit chỉ là báo cáo, không thay Canon gốc, không seed Story Event, không thiết lập Milestone Outcome, không thay GameState/Save, Combat hoặc Inventory. Không duy trì khả năng đọc save cũ chỉ vì phiên bản trước.
