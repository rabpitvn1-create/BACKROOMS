> BẢN SAO TÀI LIỆU THIẾT KẾ GỐC — BACKROOMS CANON RUNTIME 2.0
> Nguồn: tài liệu thiết kế gốc do tác giả cung cấp ngày 2026-10-24 (file gốc ngoài repo: BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE_0_nor4.md).
> File này là bản sao nguyên văn của tài liệu đó trong repo, để bất kỳ mô hình/developer nào làm sau cũng tra cứu được cùng một nguồn thiết kế.
> Trạng thái triển khai thực tế theo từng Gate: xem `android-apk/CANON_RUNTIME_2_README.md`.
> Nếu nội dung ở đây xung đột với code đang chạy: code + `android-apk/CANON_BASELINE_REPORT.md` là sự thật vận hành, file này là sự thật thiết kế.

# BACKROOMS THE GAME — CANON RUNTIME 2.0
## Kiến trúc Canon quy mô lớn, local-first, bảo vệ Game Master và tương thích API quota thấp

> **Trạng thái:** Bản thiết kế kỹ thuật có thể giao triển khai; **chưa phải code đã triển khai, chưa có kết quả benchmark Android/AI thực nghiệm**.  
> **Ngày khảo sát:** 24/10/2026.  
> **Repository tham chiếu:** [`rabpitvn1-create/BACKROOMS`](https://github.com/rabpitvn1-create/BACKROOMS), nhánh `main` tại thời điểm đọc; xem thêm [PR #668](https://github.com/rabpitvn1-create/BACKROOMS/pull/668).  
> **Đối tượng:** Android APK độc lập, giao diện WebView, Game State Core Kotlin/Java, AI Game Master sử dụng provider từ API Key người chơi hoặc cấu hình ứng dụng.  
> **Giả định đầu vào từ chủ dự án:** Tài sản Canon trong Drive `Novel / Asset` hiện chỉ đại diện khoảng **1%** nội dung có thể tồn tại trong tương lai. **Chưa kiểm kê được toàn bộ 99% còn lại**, vì vậy không tuyên bố đã biết số tài liệu, số thẻ hoặc dung lượng thật.

---

# 0. Quyết định kiến trúc, giới hạn và tiêu chí thành công

## 0.1. Quyết định

**Giữ Game Master hiện có làm một bộ phận sinh nội dung; chuyển toàn bộ phần xác định sự thật, luật, truy xuất, bộ nhớ dài hạn, quota và commit trạng thái sang máy cục bộ.**

Kiến trúc đích gọi là **Canon Runtime 2.0 (CR2)**. Luồng mỗi lượt:

1. Core khóa snapshot trạng thái, hành động và kết quả luật/xúc xắc.
2. `SceneResolver` tính đúng scene, actor hiện diện, Entity đã xác nhận, vật phẩm và năng lực có thể liên quan.
3. `CanonResolver` đọc **chỉ các record liên quan** từ kho cục bộ đã lập chỉ mục; kiểm tra điều kiện xem/biết và quyền áp dụng.
4. `ContextCompiler` tạo gói ngữ cảnh ngắn, ưu tiên mệnh đề bắt buộc, có giới hạn riêng cho từng provider.
5. `QuotaGovernor` chọn **tối đa một lần gọi sinh nội dung theo đường đi bình thường**. Không thêm một LLM chỉ để chọn TAG.
6. Bộ sinh trả lời; `StateReducer` và các validator xác định những thao tác nào thực sự được phép commit.
7. Ghi sự kiện đã commit và cập nhật bộ nhớ; lưu trạng thái hiện hành an toàn, không phụ thuộc khả năng AI “nhớ”.

Sơ đồ logic:

```text
                              BUILD / IMPORT — KHÔNG NẰM TRONG MỖI TURN
Google Drive / docs / assets / explicit overrides
             |
    Snapshot source + SHA-256 + provenance
             |
   Parse / extract / classify / propose records
             |
  Validate schema, contradictions, unknowns, human review
             |
   Canon Pack Builder ---> Signed versioned pack + global manifest
                                      |
                     DOWNLOAD/SHIP/PIN ON DEVICE
                                      v
  +------------------------- ANDROID DEVICE -------------------------+
  |                                                                  |
  | Player action --> IntentPipeline / GameCoreFacade / State Core   |
  |                        |                                         |
  |           Frozen scene snapshot + locked rolls                   |
  |                        v                                         |
  |           SceneResolver (deterministic)                          |
  |                        |                                         |
  |  SQLite Canon Index --> CanonResolver --> ContextCompiler        |
  |  [ID, alias, FTS, tags, edges, scope, secrets, authority]        |
  |                        |                                         |
  |          QuotaGovernor + provider capabilities                   |
  |                   /         |           \                        |
  |               API GM    local LLM    deterministic-only          |
  |                   \         |           /                        |
  |                  Parsed candidate response                       |
  |                           |                                      |
  |             Local validator / command authority                  |
  |                           |                                      |
  |              Atomic commit + story event ledger                 |
  |                           |                                      |
  |               UI + checkpoint + next turn                        |
  +------------------------------------------------------------------+
```

**Nguyên lý quan trọng:** Dung lượng **Canon đang lưu** có thể tăng rất lớn mà lượng **Canon gửi GM trong một lượt** vẫn giới hạn. Đây là hai đại lượng độc lập.

## 0.2. Không cam kết điều bất khả thi

- **Kho mới có 1% dữ liệu thì không thể bảo đảm 90% tính đúng đắn đối với 99% Canon chưa nhập.** Với thông tin chưa có nguồn, Core phải giữ `UNKNOWN` và GM không được khẳng định thành canon.
- **Gemini Free Tier không có quota thì thiết bị không thể tự tạo thêm quota.** Google áp dụng giới hạn theo project, model và nhiều trục RPM/TPM/RPD; năm key thuộc cùng project **không** đồng nghĩa năm lần quota [G1].
- **Không có mô hình sinh ngôn ngữ khả dụng** (cloud bị chặn và điện thoại không hỗ trợ model local) thì vẫn có thể cho thao tác có cấu trúc và cập nhật state, nhưng không thể hứa cung cấp trải nghiệm văn xuôi tự do ngang AI có kết nối.
- Mục tiêu “~90% công suất” chỉ có giá trị nếu định nghĩa **công suất** bằng bài đo: mức đúng luật, khả năng hoàn thành tác vụ, tính mạch lạc và độ hay so với baseline. Không dùng con số 90% làm lời quảng cáo không kiểm chứng.

## 0.3. Bất biến bắt buộc

- GM **không phải nơi lưu sự thật**; lời kể chưa được commit không thành state.
- Quy tắc gameplay và điều kiện Canon cứng được Core thực thi khi có thể, không chỉ trông cậy prompt.
- Không bao giờ đưa toàn bộ codex nhân vật vào GM chỉ vì nhân vật hiện diện.
- Không gọi model bổ sung để tìm kiếm, tóm tắt hoặc kiểm tra một lượt thông thường.
- Cảnh chưa xác nhận không được nâng thành sự thật; **không tiết lộ spoiler, tri thức hậu trường hoặc thông tin ngoài POV**.
- Không hy sinh độ đúng Canon cứng để đạt ngân sách token: nếu không chứa nổi phần tối thiểu, giảm nội dung tùy chọn hoặc chuyển sang chế độ hạn chế an toàn.
- Giữ hoạt động save/load **phiên bản hiện tại**; tuyệt đối **không tạo migration/adapter/fallback cho save game cũ**.
- Thất bại quota, mạng, tiến trình Android chết, hỏng pack hoặc thiếu bộ nhớ không được làm commit nửa lượt, nhân đôi lượt hay xóa tiến trình hiện tại.

---

# 1. Kết quả khảo sát repository: phải tái sử dụng gì?

## 1.1. Đã xác minh từ code hiện hành

| Thành phần | Hiện trạng quan sát được | Quyết định |
|---|---|---|
| `README.md` | APK độc lập; `MainActivity.java` là cầu nối Android/WebView và AI; Game State Core Kotlin; assets cục bộ. | Không bổ sung backend bắt buộc. |
| `assets/knowledge/knowledge_db.json` | Có **54 record** trong 9 domain (`GAME_RULE`, `WRITING`, `WORLD`, `STORY`, `CHARACTER`, `RELATIONSHIP`, `ENTITY`, `ITEM`, `LEVEL`), có `source`, `priority`, `tags`, `references`, `affordances`. | Dùng làm seed và hợp đồng dữ liệu ban đầu; không vứt đi. |
| `KnowledgeContextEngine.kt` | Đọc toàn `knowledge_db.json` vào map nhớ ở lần đầu; chọn mandatory/core, Level, Iris/Syvial, relationship, direct lookup, affordances và references; giới hạn mục tiêu **2200**, soft **2800**, hard **3400** token **ước lượng**. | Giữ semantics và test; tách `RecordStore`, `Resolver`, `BudgetCompiler`. |
| `KnowledgeLocalValidator.kt` | Kiểm tra một số chuỗi văn bản phản Canon về Sparda, Lucifer, ARGUS, Omnivault, Entity và Level 0. | Giữ kiểm tra hiện có nhưng thêm kiểm tra theo rule ID / thao tác có cấu trúc; substring chỉ là lớp phụ. |
| `StoryContinuityReducer.kt` | Dựa trên before/after state; ghi level, party, communication, discovery, relationship; giới hạn 12 events, 12 discoveries, 8 objectives, 8 threads, 16 knowledge, 8 relationships. | Giữ cách ghi bằng state hợp lệ, nhưng tách event ledger bền vững khỏi bản tóm tắt ngắn. |
| `TurnCoordinator.kt` | Có pending turn, chống hoàn tất trùng turn ID, commit qua `StateReducer`, recover pending. | Sử dụng làm hạt nhân transaction; không tự chế vòng đời lượt khác. |
| `patch-knowledge-context-builder.py` | Build-time patch nối `KnowledgeContextEngine.build()` vào writer và auditor, `KnowledgeLocalValidator` vào lượt sinh, `StoryContinuityReducer` trước commit. | Bảo vệ điểm tích hợp; nếu chuyển thành code nguồn trực tiếp phải làm **từng bước** và xác minh cùng luồng build thực. |
| `patch-gemini-model-matrix-final.py` và `patch-provider-deadline-final.py` | Có matrix nhiều Gemini key/model, cooldown, circuit breaker và provider khác; 429 đang có nhánh cooldown khoảng 60 giây trong patch matrix; các patch có thể thay đổi luồng cuối cùng. | Giữ provider routing, thêm project-aware quotas, nhật ký 429/reset và admission control. Không tin nhiều key sẽ giải quyết hết quota. |
| `benchmark-knowledge-context.py` | Corpus kiểm tra tổng hợp 8 kịch bản, mô phỏng context bằng `ceil(chars/4)`, kiểm tra coverage record và một số hợp đồng; **không phải benchmark thực với model**. | Giữ bài test hồi quy nhanh; bổ sung eval thực và benchmark trên điện thoại. |
| `app/build.gradle` | API keys có thể được nhúng vào `BuildConfig` từ môi trường build. | Nếu phân phối APK, **coi mọi key trong APK đều có thể bị trích xuất**; chuyển BYOK sang kho bí mật riêng của người dùng, không phát hành shared secret. |

**Các giới hạn hiện có cần sửa trước khi phình dữ liệu:**

1. `tokenizeTags()` là danh sách tên Entity/Item gắn cứng (khoảng 17 cụm từ); `addPresentRuntimeCards()` và `addRelationships()` gắn cứng Kai/Iris/Syvial. Đến 1.000 NPC, cách này không mở rộng được.
2. Đọc nguyên JSON thành hàng loạt `JSONObject`/map là chấp nhận được cho 54 record nhưng kém phù hợp với kho có hàng chục hoặc hàng trăm nghìn record.
3. `expandReferences()` đi theo references tới các record priority <= 55: có `visited`, nhưng **chưa có giới hạn hop/nút/tổng kích cỡ độc lập**. Một đồ thị liên hệ dày đặc vẫn có thể làm quá tải bước chọn.
4. `budgetedRecords()` cộng mandatory rồi mới chọn optional; `hardClip()` cắt **chuỗi văn bản theo ký tự** (hardTokens * 4), có khả năng cắt mất nội dung ở cuối hoặc phá ngữ nghĩa record. `ceil(chars/4)` không phải tokenizer thật của Gemini và đặc biệt không chính xác ổn định với tiếng Việt.
5. `compactState()` cho toàn bộ `storyContinuity` vào chuỗi đã cắt tối đa 5200 ký tự, trong khi `recentDialogue()` lấy bốn log gần nhất. Có thể mất các facts quan trọng khi state phình to và bị cắt theo ký tự.
6. Bộ nhớ dài hạn hiện cắt các mảng theo số lượng: sự kiện quan trọng có thể biến mất khỏi bản nhớ sau đủ nhiều lượt. Cần lưu ledger bền vững, rồi truy xuất tóm tắt theo mức quan trọng.
7. `KnowledgeLocalValidator` dựa vào tìm cụm từ trong lời kể; có nguy cơ **false positive** với phủ định, trích dẫn, lời nói của kẻ lừa đảo; và **false negative** với paraphrase.
8. Các builder/runtime được chỉnh bằng **chuỗi patch Python lúc build**. Khả năng patch lệch anchor là rủi ro bảo trì; cần đo code thực sự tạo sau build, không chỉ đọc file Java cơ sở.

**Lưu ý về căn cứ:** Đây là đánh giá tĩnh từ `main` và các script/PR đã đọc; chưa checkout/assemble APK, chưa xác minh thứ tự tất cả patch cuối cùng hoặc runtime trên máy thật. Không được đánh đồng nó với phát hiện lỗi tái hiện được trên bản phát hành.

Nguồn repo trực tiếp: [Engine](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt), [Validator](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeLocalValidator.kt), [Reducer](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/StoryContinuityReducer.kt), [Source Map](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/KNOWLEDGE_SOURCE_MAP.md), [Benchmark](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/benchmark-knowledge-context.py), [Build config](https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/build.gradle).

## 1.2. Không thay thế sự thật gốc bằng trí tưởng tượng của pipeline

`KNOWLEDGE_SOURCE_MAP.md` đã có chính sách authority và ghi rõ `UNKNOWN`, `OPEN`, `CHƯA KHÓA` vẫn là chưa biết. Phải bảo vệ thuộc tính này ngay từ ingest. Một extractor không được tự đặt tên, cấp bậc, quan hệ, sức mạnh hoặc kết quả sự kiện chỉ để tạo đủ field.

**Ví dụ phân biệt world:** tài liệu Seth được cấp trong dự án nói Seth/Jay Sephiroth và Omnivault có giới hạn không tác động sinh vật sống; Source Map của game hiện nêu Kai/Omnivault với các ràng buộc riêng. **Không tự suy ra Seth = Kai** hoặc dùng quy định năng lực của người này áp cho người kia. Mỗi record buộc có `universe_id`, `actor_id`, `source`, `scope`, `authority`.

---

# 2. Điều rút ra từ Reddit và GitHub, cùng biện pháp khắc phục điểm yếu

| Kinh nghiệm nghiên cứu | Giá trị sử dụng | Điểm yếu thực tế | Giải pháp CR2 |
|---|---|---|---|
| SillyTavern World Info: kích hoạt theo keyword, giới hạn token, entry scope theo nhân vật/chat; recursion có kiểm soát [ST1]. | Phần lớn lore dormant, GM chỉ đọc phần liên quan. | Keyword mơ hồ; recursion làm bung hàng loạt thẻ; model có thể bỏ qua lore dù đã nạp. Reddit mô tả cascade City -> Bar -> Bartender [R1]. | Mặc định **không recursive-by-text**. Dùng references có kiểu, filter scene và giới hạn 1–2 hops, số nút, byte, token; validator độc lập. |
| Một người dùng Reddit tự báo cáo kho >50.000 token, trung bình ~138 token/entry, khoảng 24–28 entry hoạt động mỗi lượt [R2]. | Kiến trúc nhiều thẻ nhỏ là thực tế; có thể dormant >90%. | Trường hợp đó có lượt tiêu thụ 4k–11k token, quá tốn với người dùng Free và model yếu. | Giới hạn theo **tổng prompt và năng lực provider**, không sao chép mức context của người dùng model mạnh. |
| AI Dungeon Story Cards [R3]. | On-demand memory dễ tạo. | Trigger xuất hiện trong lời sinh ra thì thẻ chỉ dùng từ **lượt sau**; NPC có thể giới thiệu sai ngay lượt đầu. | `SceneResolver` quyết định actor/Entity **trước** lời kể; nếu event được core xác nhận giữa turn, cập nhật eligibility **trước** lời GM. |
| Reddit về cache [R4]. | Nội dung động có thể làm mất prefix cache của một số provider. | Cố định mọi lore để cache lại phá mục tiêu tiết kiệm token. | Giữ prefix quy tắc thật ổn định; phần TAG động đi sau; đo trên provider thật. Không dựa vào tính năng caching trả phí. |
| SillyTavern MemoryBooks [ST2]. | Tách scene memory, arc summary, topical memory; lưu lời thoại gần đây riêng. | Tóm tắt AI có thể làm mất nhân quả hoặc bịa lore, tốn request phụ. | Ledger từ **validated state/event** làm nguồn; summary cấu trúc incremental không gọi AI. |
| GitHub Mem0 graph memory [GH1]. | Liên kết entity/record giúp multi-hop recall. | Đồng xuất hiện trong văn bản không đồng nghĩa quan hệ thật. | Chỉ tạo **typed edge có nguồn** (`knows`, `at`, `owns`, `can_do`, `requires`, `address_as`) và trạng thái xác nhận. |
| GitHub LocalGPT [GH2]. | Chia preprocessing, indexing, retrieval, generation, evaluation; không nhập cả file thô vào prompt. | Pipeline vector + rerank + nhiều LLM có độ trễ và phụ thuộc lớn, không hợp Android Free Tier. | Học cách tách giai đoạn và benchmark; **không** chép stack model/vector/agent nặng. |
| Reddit r/Rag: có bài báo cáo hybrid+rerank làm hit@3 thấp hơn vector, bài khác cho kết quả ngược [R5, R6]. | Không có thuật toán truy xuất tối ưu cho mọi loại corpus. | Thêm BM25/vector/reranker vô điều kiện có thể tệ hơn. | **Direct ID -> exact alias -> structured edge -> FTS** là chuẩn; chỉ bật embedding/rerank nếu corpus riêng chứng minh cải thiện. |
| Android/llama.cpp [GH3] và Gemini Nano [A5]. | Có thể suy luận on-device trên thiết bị phù hợp. | RAM/thermal/pin/băng thông bộ nhớ khác nhau; không phải máy nào cũng hỗ trợ. | Local text model là capability *optional* với benchmark thiết bị; Core/search chạy được kể cả khi không tải LLM. |

**Phương pháp chắt lọc:** Giữ các nguyên tắc làm giảm dữ liệu gửi model; loại bỏ những phần yêu cầu gọi thêm model, phụ thuộc server hoặc lãng phí context; đưa các ràng buộc không thể thương lượng xuống Core.

---

# 3. Mô hình dữ liệu: Canon Record + typed edge + điều kiện áp dụng

## 3.1. Không dùng “một thẻ = một đoạn văn bất kỳ”

Dùng **Record v2** gồm phần nhận diện, sự thật, điều kiện kích hoạt, tác dụng luật và chứng cứ. Record nhỏ, độc lập và có thể hợp nhất với các record anh em khi cần; không chia tùy tiện từng câu làm mất ngữ cảnh.

```json
{
  "id": "CHAR.SETH.OMNIVAULT.LIVING_TARGET_BLOCK",
  "schema_version": 2,
  "universe_id": "BACKROOMS_MAIN",
  "domain": "CHARACTER",
  "entity_id": "CHAR.SETH",
  "kind": "HARD_RULE",
  "authority": "CHARACTER_CODEX",
  "mutability": "IMMUTABLE",
  "truth_status": "CONFIRMED",
  "knowledge_visibility": "ACTOR_AWARE",
  "spoiler_policy": "SHOW_IF_RELEVANT",
  "text": "Omnivault Ring không thể tác động lên sinh vật đang sống.",
  "aliases": ["omnivault", "nhẫn vạn tàng"],
  "tags": ["equipment", "storage", "living_target"],
  "affordances": ["store_item", "scan_item", "restore_item"],
  "activation": {
    "any_scene_intent": ["use_omnivault", "ask_omnivault_rules"],
    "require_actor_present_or_subject": "CHAR.SETH",
    "level_scope": "ANY",
    "min_evidence": "CONFIRMED",
    "deny_if_unauthorized_pov": true
  },
  "references": [],
  "hard_rule": {"predicate": "target.is_living", "effect": "BLOCK_OMNIVAULT"},
  "source": {
    "document_id": "CHAR_SETH_CODEX",
    "document_path": "Novel/Characters/Seth",
    "anchor": "X.8",
    "content_sha256": "<computed-at-import>",
    "approved_revision": "<reviewed-source-revision>"
  },
  "priority": 10
}
```

Dấu `<...>` chỉ là placeholder minh họa, **không phải SHA đã tính**. Đường dẫn ví dụ cần xác nhận bằng nguồn Drive khi nhập thật. Không được chuyển bản ví dụ vào runtime như dữ liệu đã duyệt.

### Các field tối thiểu bắt buộc

- `id`: tên ổn định có namespace, không phụ thuộc thứ tự file; `universe_id` tách world/story khác nhau.
- `entity_id`: ID chuẩn, aliases chỉ hỗ trợ matching, không đóng vai trò khóa.
- `domain`, `kind`, `authority`, `mutability`: phân biệt hard rule, personality, perception, relationship, level, lore flavor, event.
- `truth_status`: `CONFIRMED`, `RUMORED`, `INFERRED`, `UNKNOWN`, `CONTRADICTED`, `DEPRECATED_SOURCE` (trạng thái nguồn, **không** là save compatibility).
- `knowledge_visibility` và `known_by`: **sự thật khách quan** khác với **kiến thức chủ thể**.
- `activation`: điều kiện dương/âm; entity có mặt, actor đang nói, hành động, Level, scene, state, disclosure và thời điểm.
- `references`: typed edges, không trộn mọi quan hệ thành từ khóa.
- `source`: bản gốc, anchor, hash, revision, authority; khi claim quan trọng phải truy vết được ngược.
- `priority`: chỉ xếp hạng record cùng lớp, **không được để priority tùy ý vượt qua hard lock**.

## 3.2. Các loại cạnh và quy tắc truy xuất

```text
CHAR.SETH --OWNS------------> ITEM.OMNIVAULT
CHAR.SETH --MEMBER_OF-------> ORG.BLACK_BLOOD
CHAR.SETH --ADDRESS_AS-------> CHAR.HUA_TIEU_LAN  [xưng tôi / cô]
CHAR.SETH --ABILITY_RULE-----> RULE.OMNIVAULT.LIVING_TARGET_BLOCK
LEVEL.05 --MAY_CONTAIN-----> ENTITY.BEAST_L5  [điều kiện; chưa xác nhận hiện diện]
EVENT.1024 --WITNESSED_BY--> CHAR.KAI
```

- `MAY_CONTAIN` **không** suy ra `IS_PRESENT`.
- `MENTIONED` **không** suy ra `CONFIRMED`.
- `KNOWS_OF` **không** suy ra `HAS_SEEN`.
- `CAN_DO` **không** suy ra `WILL_DO`; follower vẫn có tự chủ.
- `RELATION_BASELINE` khác `RELATION_RUNTIME_DELTA`; lời kể không âm thầm sửa quan hệ gốc.
- `STORY_POSSIBILITY` không tự biến thành `STORY_FACT`.

## 3.3. Kiểm soát xung đột Canon

So sánh theo **scope và authority cụ thể**: explicit override hiện hành > Text Game rules/current state cho dữ kiện vận hành > Character Codex cho đặc điểm bất biến > World canon > historical continuity > writing guidance > reference. Mục này phải lấy trực tiếp Source Map hiện hành, không tự sáng tạo precedence mới.

Nếu hai nguồn cùng thẩm quyền nói trái nhau: `CONFLICT_PENDING_REVIEW`, **không** dùng LLM để hợp nhất hoặc chọn ngẫu nhiên. Giữ bản active đang được chấp thuận; ghi cảnh báo cho tác giả, không crash lượt chơi. Nếu một câu chuyện cố tình có nhân vật nói dối hoặc hiểu sai, lưu sự thật/niềm tin như hai thực thể khác nhau.

---

# 4. Quy trình ingest 1% -> 100% không đốt quota người chơi

**Tuyệt đối không parse DOCX/PDF, tải Drive, gọi embedding API hay hỏi LLM để trích Canon vào lúc người chơi bấm hành động.** Những tác vụ đó thuộc pipeline authoring/build hoặc cập nhật pack nền.

## Bước ingest A: nguồn và snapshot

- Kết nối tới các thư mục được chỉ định; lấy file ID, path, MIME, modified revision và quyền nguồn.
- Snapshot chỉ các file mới/sửa/xóa; **content-hash SHA-256** thay vì quét/so sánh văn bản toàn kho mỗi lần.
- Lưu manifest `source_id -> revision_hash -> parser_version -> record_ids -> review_status`.
- Tài liệu chưa có quyền đọc/thiếu nội dung báo rõ `SOURCE_UNAVAILABLE`, không tự nhảy sang tài liệu tương tự.

## Bước ingest B: parse và chia có cấu trúc

- MD/TXT: giữ tiêu đề/mã mục.
- DOCX: giữ heading, bảng, đoạn; neo vào tên section, bookmark hoặc ordinal ổn định; hình kèm cờ `VISUAL_REVIEW_REQUIRED` nếu chứa dữ liệu quan trọng.
- PDF: phân biệt text layer và file ảnh; OCR **chỉ** khi thật cần, phải có kiểm tra chất lượng; giữ page/region làm chứng cứ.
- Image asset: metadata, asset ID, chú giải chính thức; **không suy đoán sức mạnh/quan hệ từ hình minh họa**.
- Split theo **ý nghĩa và phạm vi ràng buộc**, không theo chunk 500 ký tự cứng. Ưu tiên 1–5 mệnh đề liên quan có thể áp dụng độc lập.

## Bước ingest C: extractor + reviewer

- Heuristic/regex/sơ đồ tài liệu trích ID/aliases/type trước.
- LLM mạnh ở **thời điểm làm nội dung**, nếu có, chỉ là công cụ *đề xuất* record và liên kết, không tự phê duyệt canon; **không dùng API key của người chơi**.
- Mỗi record quan trọng bắt buộc có source anchor; nếu không chứng minh được -> `DRAFT_UNVERIFIED`.
- Human/author review cho hard rule, relationship lock, powers, story spoilers và mọi conflict.
- Auto-approve chỉ nên áp dụng phần metadata an toàn và bản re-index không thay đổi ngữ nghĩa.

## Bước ingest D: compile và xuất bản

1. Schema-check -> ID uniqueness -> alias collision report -> broken-reference check -> conflict report -> forbidden unknown promotion check.
2. Tạo `Manifest`, catalog ID, indexes, rule tables, localized display text, level/character shards.
3. Sinh golden cases cho record bắt buộc từ source contract; cập nhật trace `record -> source`.
4. Ký pack và xuất `pack_version`, `schema`, `checksum`, `source_snapshot_hash`, `min_app_version`, `dependencies`, `size`, `review_status`.
5. Chỉ phân phối pack đã duyệt, có khả năng rollback. Không để release do thay font/avatar tự động làm thay đổi authority của Canon ngoài phạm vi.

### Xử lý 99% chưa có

- Không dựng giả toàn bộ thế giới bằng AI.
- Tạo `coverage manifest` phân loại: `IMPORTED`, `PENDING_REVIEW`, `KNOWN_MISSING`, `NOT_IN_SCOPE`, `UNKNOWN_TOTAL`.
- Cho phép core khám phá mức/chủ thể mới theo quy tắc game đã có, nhưng **không tuyên bố những thông tin chưa có nguồn là official canon**.
- Khi nguồn mới tới, thêm pack/record; không phải chỉnh `MainActivity` cho mỗi nhân vật/Level.

---

# 5. Lưu trữ trên điện thoại: ưu tiên SQLite có chỉ mục và pack phân vùng

## 5.1. Hai giai đoạn, tránh kiến trúc hóa quá sớm

**Giai đoạn đầu (54 record):** giữ JSON assets hiện hành và `KnowledgeContextEngine` hoạt động qua interface `CanonStore` để không làm hỏng PR #668.

**Giai đoạn kho tăng đáng kể:** chuyển backend store của cùng interface sang **SQLite indexed pack** trước khi JSON toàn kho gây RAM/khởi động tệ. Đặt cổng chuyển bằng benchmark/cỡ data, không cố đưa Room/FTS/vector lib vào APK khi còn chưa có dữ liệu thật.

SQLite nằm trong app-private storage; `record_payload` chứa text ngắn, index và source; media lớn nằm ở file riêng và được trỏ bằng `asset_id`. Không nhét ảnh hoặc toàn bộ tài liệu Office vào blob RAM.

### Lược đồ tham khảo

```sql
CREATE TABLE canon_record (
    id TEXT PRIMARY KEY,
    universe_id TEXT NOT NULL,
    entity_id TEXT,
    domain TEXT NOT NULL,
    kind TEXT NOT NULL,
    authority TEXT NOT NULL,
    truth_status TEXT NOT NULL,
    priority INTEGER NOT NULL,
    shard_id TEXT NOT NULL,
    text TEXT NOT NULL,
    source_id TEXT NOT NULL,
    source_anchor TEXT NOT NULL,
    activation_json TEXT NOT NULL,
    rule_json TEXT
);
CREATE INDEX idx_canon_scene ON canon_record(universe_id, shard_id, domain);
CREATE INDEX idx_canon_entity ON canon_record(entity_id, kind);
CREATE TABLE canon_alias(alias_normalized TEXT NOT NULL, record_id TEXT NOT NULL);
CREATE INDEX idx_canon_alias ON canon_alias(alias_normalized);
CREATE TABLE canon_tag(tag TEXT NOT NULL, record_id TEXT NOT NULL);
CREATE INDEX idx_canon_tag ON canon_tag(tag);
CREATE TABLE canon_edge(src_id TEXT NOT NULL, edge_type TEXT NOT NULL,
                        dst_id TEXT NOT NULL, constraints_json TEXT);
CREATE INDEX idx_canon_edge_src ON canon_edge(src_id, edge_type);
CREATE TABLE canon_source(source_id TEXT PRIMARY KEY, document TEXT NOT NULL,
                          revision TEXT, sha256 TEXT, review_status TEXT NOT NULL);
```

Với full text: Android SQLite có nhiều biến thể theo API/vendor. **Ưu tiên FTS4 cho nền minSdk 24** và kiểm tra tính năng thực trên thiết bị; FTS5 chỉ bật nếu runtime thực sự hỗ trợ. Có thể dùng SQLite trực tiếp trước; Room là tùy chọn nếu chi phí migration/code có lợi. [A1]

## 5.2. Tổ chức pack/shard

```text
canon/
  manifest.json
  core.db                  # global rules, ID catalog, source/pack table
  world-level-000.db
  world-level-001.db
  characters-blackblood.db
  characters-other.db
  story-arcs-main.db
  language-vi.db
  images/                   # asset files; không nằm trong vector DB
```

Đây là **sơ đồ logic**, chưa nhất thiết mỗi phần là một SQLite database vật lý: ban đầu 1 DB với `shard_id` đủ; chỉ tách file khi profiling cho thấy lợi ích. Quá nhiều database gây bất tiện transaction/join; tránh tách chỉ để trông có vẻ “enterprise”.

- `core manifest` chứa ID/range/pack -> file; cold pack không cần parse.
- Truy vấn exact ID và indexed edges; giữ bộ nhớ cache LRU nhỏ theo cảnh.
- Cache nóng chỉ chứa record metadata/compact cards, không giữ toàn bộ nguồn lớn.
- Khi Level/party đổi, prefetch các shard hợp lệ theo transition xác nhận, có deadline và hủy được.
- Thiết bị thiếu dung lượng: core pack và active scene pack luôn ưu tiên; optional flavor/media/old cached packs có thể xóa và tải lại an toàn, **không xóa nguồn của save hiện hành nếu còn phụ thuộc**.
- Bản cập nhật pack đi qua temp + hash/signature + dependency validation + atomic pointer swap; crash trong khi cài không làm đọc nửa pack.

## 5.3. Bộ nhớ RAM và pin

- SQLite page cache/OS file cache xử lý index; không đọc 100.000 record thành đối tượng Kotlin tại startup.
- Không chạy embedding model hoặc local LLM liên tục ở background.
- Đặt byte-size và entry-count cho LRU; giải phóng cache khi chuyển cảnh, app background hoặc Android báo áp lực bộ nhớ, theo API hỗ trợ trên OS hiện hành [A2].
- WorkManager, nếu đã chứng minh cần, dùng cho tải/verify pack có thể phục hồi sau restart; **không** dùng WorkManager cho truy xuất của mỗi lượt vì tăng độ trễ và phức tạp. [A3]
- Cân nhắc dung lượng cache, battery level, nhiệt độ, memory class trước khi tải local model; không dựa trên tổng RAM quảng cáo của điện thoại.

---

# 6. Bộ truy xuất thực tế: Scene-first, direct-first, graph-bounded

## 6.1. Đầu vào `SceneSnapshot` phải chuẩn hóa từ Core

```json
{
  "turn_id": "turn-1024",
  "game_state_revision": 1024,
  "universe_id": "BACKROOMS_MAIN",
  "level_id": "LEVEL.01",
  "zone_id": "ZONE.01.B3",
  "pov_actor_id": "CHAR.KAI",
  "actors_present": ["CHAR.KAI", "CHAR.IRIS"],
  "entity_encounters": [{"id": "ENTITY.HOUND", "status": "CONFIRMED_PRESENT"}],
  "mentioned_unconfirmed": ["ENTITY.SMILER"],
  "action_intents": ["inspect_tracks", "ask_companion"],
  "objects_in_focus": ["ITEM.UNKNOWN_127"],
  "known_facts": ["DISC.LEVEL.01", "DISC.TRACKS.18"],
  "story_threads_active": ["MAIN.REUNITE.SYVIAL"],
  "rolls_locked": true
}
```

Đây là schema thiết kế, **không phải JSON hiện tồn tại**. Adapters lấy từ `GameState`/`flags` phải được xác minh qua `GameCoreFacade` và `TurnCoordinator` trước khi viết. Entity chỉ `RUMORED`/`MENTIONED` không được xuất hiện như xác nhận.

## 6.2. Thuật toán tìm theo nhiều cổng

**Cổng 0 — Quyền và scope:** universe, version, active Level/area, player POV/known_by, story disclosure gate, pack đã duyệt.

**Cổng 1 — ID chính xác:** actor hiện diện, action target, current Level, confirmed Entity/item, thread ID, immutable rules liên quan thao tác. O(lookup) theo index.

**Cổng 2 — Alias có kiểm soát:** normalize Unicode/viết hoa/dấu cách/dấu nối; alias ngắn/mơ hồ phải có scope hoặc nhiều điều kiện cùng đúng. `Rose` là tên người và từ thường không được khớp bừa.

**Cổng 3 — Typed edge:** `ADDRESS_AS`, `REQUIRES`, `HAS_ABILITY`, `LOCATED_IN`, `KNOWN_BY`. **Không** quét từ khóa bên trong text card để nạp thêm card.

**Cổng 4 — FTS nhẹ:** BM25/FTS chỉ trên tập đã lọc scope cho truy vấn mơ hồ, ưu tiên lexical match có provenance. Nếu không đủ bằng chứng, trả `NO_CANON_MATCH`, không ép record gần giống nhất.

**Cổng 5 — Semantic optional:** chỉ dùng embedding cục bộ hoặc build-time khi recall@K thực sự tốt hơn test. Không được đưa semantic result vào hard-rule tự động khi không đúng ID/scope.

### Hàng rào chống bùng nổ

- `max_graph_hops = 1` mặc định; hard dependencies bắt buộc được giải riêng, không tính như flavor recursion.
- `max_expanded_nodes`, `max_candidate_bytes`, `max_packet_records`, `max_per_entity`, `deadline_ms`: cấu hình đo được theo thiết bị.
- Cycle detection; distinct ID; deterministic tie-break; logging lý do từng record được chọn.
- Đồ thị `city -> bar -> bartender -> city` không kéo tất cả địa điểm/nhân vật vào prompt.
- Với nhiều nhân vật cùng scene: mọi actor **có core identity tối thiểu**, nhưng chỉ actor đang nói/hành động nhận expanded behavior. Những nhân vật còn lại có summary ngắn, Core vẫn biết toàn bộ state.

## 6.3. Điểm mù quan trọng: không chỉ load theo tên mà phải load theo **affordance**

Ví dụ Iris đang có mặt, người chơi chỉ viết “kiểm tra dấu giày”. Nếu đợi từ `ARGUS`, hệ thống bỏ lỡ năng lực. Resolver phải ánh xạ `inspect_tracks -> trace_analysis -> CHAR.IRIS.ARGUS` nhưng vẫn không được ép Iris tự thực hiện hành động nếu cô chưa quyết định. Phân biệt **có thể sử dụng** với **đã sử dụng**.

Các trigger này là data trong registry, không hardcode tên từng nhân vật trong Kotlin.

---

# 7. ContextCompiler: làm AI yếu có cơ hội làm đúng

## 7.1. Bốn lớp trách nhiệm

1. **Core-enforced rules**: dice, item ownership, invalid actions, entity spawn, quest gates, relationship flag commit. Chủ yếu **không gửi nguyên bài luật cho GM**; chỉ gửi kết quả/ràng buộc của hành động khi cần viết.
2. **Must-know narrative constraints**: identity, speech/address locks, POV, action-result boundary, capability limits hiện liên quan. Nhỏ và có thứ tự.
3. **Scene facts & durable memory**: current Level, nearby known entities, những sự kiện còn tác động tới lượt hiện tại.
4. **Optional prose/flavor**: cảm giác, mô tả phong cách, chi tiết lịch sử không cần thiết. Đây là lớp bị cắt đầu tiên.

Sự khác nhau: **Core luôn thực thi luật**, còn model chỉ cần thấy **hệ quả đã quyết định**. Không phải nạp `GAME.TEXT.CORE` hàng nghìn token mỗi lượt để nhờ AI tự xử đúng luật.

## 7.2. Adaptive profile, không một ngưỡng cho mọi AI

| Chế độ | Khi dùng | Quy tắc |
|---|---|---|
| `COMPACT` | Model yếu, quota thấp, TPM/RPD gần ngưỡng, pin/RAM thấp | Chỉ facts bắt buộc + scene focus + 1–2 sự kiện gần nhất có tác động; bớt flavor, output ngắn. |
| `STANDARD` | Provider hoạt động ổn, hội thoại thường | Facts và context vừa đủ, mở rộng các actor đang tương tác. |
| `RICH` | Model và quota đủ, cảnh đông/điểm nút truyện | Thêm quan hệ/thời gian/phong cách có liên quan, nhưng không dump canon. |
| `OFFLINE` | Cloud không có; có local model phù hợp | Cũng dùng `COMPACT`; tùy thiết bị có thêm local generator hoặc chỉ xử lý thao tác có cấu trúc. |

Ngưỡng gợi ý để **đem đo**, không phải tham số đã được chứng minh: `COMPACT` hướng tới tổng prompt input khoảng 1.5k–3k token với output reserve riêng; `STANDARD` có thể cao hơn theo provider và benchmark. Engine hiện hành dùng 2200/2800/3400 **ước lượng cho knowledge packet**, không bảo đảm tổng prompt nằm trong giới hạn. Phải tính **toàn bộ** system rules + context + action + schema + output reserve + TPM budget.

**Bất biến compiler:**

```text
usable_input = min(provider_window - output_reserve - safety_margin,
                   provider_tpm_available,
                   configured_max_input)

mandatory_facts = resolve_core_required_constraints(scene)
if cannot_fit(mandatory_facts, usable_input):
    compact_presentation_without_dropping_constraints()
if still_cannot_fit:
    return NEEDS_SAFE_LOCAL_RESOLUTION  # KHÔNG cắt cụt hard rule

rank_optional_by(relevance, recency, narrative_consequence, source_confidence)
append_only_whole_records_while_budget_allows()
```

- Dùng tokenizer phù hợp model nếu có; nếu không, estimator phải **bảo thủ**, có safety margin riêng cho tiếng Việt và ghi observed provider token usage để hiệu chỉnh.
- Không `hardClip` tự do trên chuỗi ghép. Chuỗi context được xây từ record nguyên vẹn rồi validate độ dài.
- Với hard rule dài: phải có `compact_form` được **author-approved** và bản đầy đủ ngoài prompt; không để LLM tự rút mất ngoại lệ.
- Tránh lặp source/provenance dài trong text gửi GM; giữ trace ở metadata cục bộ. Model chỉ cần nội dung và ID gọn khi cần dẫn chiếu.
- Tách **fact statement** khỏi **creative instruction**; tránh khiến AI yếu hiểu metadata là một phần đối thoại.

## 7.3. Prompt có cấu trúc ít việc

```text
[ROLE]
Bạn là GM. Viết tiếp ngắn gọn, tự nhiên bằng tiếng Việt.
Không quyết định hành động có chủ ý thay người chơi.
Chỉ mô tả diễn biến được Core cho phép; không tự thêm loot/spawn/chuyển Level.

[CURRENT_SCENE]
Turn=1024 | Level=1 | POV=Kai
Actors=Kai,Iris | Confirmed threat=Hound | Unconfirmed rumor=Smiler
Player action=Hỏi Iris nhận xét về dấu chân.
Locked result=Chưa xác nhận dấu chân thuộc Entity nào.

[CANON_REQUIRED]
CHAR.IRIS.CORE: ...
CHAR.IRIS.ARGUS: ...
ADDR.IRIS.KAI: ...

[CONTINUITY_RELEVANT]
Kai và Iris từng phát hiện ... [đã được core xác nhận]

[OUTPUT]
Trả JSON theo schema hiện hành; chỉ đề xuất ops, không tự commit.
```

Với model yếu, nhiều instruction phủ định lặp lại gây loãng mục tiêu; compiler dùng **ít luật biểu đạt chính xác, ví dụ ràng buộc trực tiếp**. Câu văn vẫn được GM tự sáng tạo; không ép một đoạn story cố định.

## 7.4. Prompt caching có điều kiện

Prefix gồm rules chung, output schema, các khóa tĩnh; phần state/tags/history thay đổi đưa về sau. Chỉ sử dụng cache API nếu tier/model thật sự hỗ trợ, có lợi khi đo và không làm rò nội dung riêng tư. Reddit đã nêu trường hợp DeepSeek bị miss cache khi dynamic lore chèn đầu prompt [R4], nhưng **không mặc định** tất cả Gemini model/free accounts có cùng cơ chế hoặc lợi ích.

---

# 8. Bộ nhớ dài hạn: không mất tình tiết ở turn 5000

## 8.1. Ba kho riêng

**(A) Immutable Canon**: được author duyệt, cập nhật qua pack; không sửa bởi GM.

**(B) Runtime truth ledger**: nguồn sự thật thay đổi theo ván, chỉ ghi từ state transition/command commit đã duyệt. Event append-only với ID và revision, hỗ trợ checkpoint/compaction **không làm mất các facts còn tác động**.

**(C) Narrative recap/read model**: các bản tóm tắt có cấu trúc, có thể tái tạo từ ledger. Đây chỉ là view ngắn để đưa GM, không phải nguồn quyết định đúng/sai.

Sử dụng `StoryContinuityReducer` làm cơ sở, nhưng `MAX_EVENTS = 12` chỉ nên áp dụng **recent view**, không xóa nguồn event dài hạn. Chuyển quan hệ, lời hứa, vật chứng, thương tích, NPC đã chết, lộ bí mật thành `durable_fact` có lifecycle.

## 8.2. Event và knowledge visibility

```json
{
  "event_id": "EVT.1024.TRACKS_DISCOVERED",
  "turn_id": "turn-1024",
  "state_revision_before": 1023,
  "state_revision_after": 1024,
  "kind": "DISCOVERY",
  "subject_ids": ["CHAR.KAI", "CHAR.IRIS"],
  "fact_id": "DISC.TRACKS.18",
  "truth_status": "CONFIRMED_OBSERVATION",
  "known_by": ["CHAR.KAI", "CHAR.IRIS"],
  "valid_from_turn": 1024,
  "supersedes_event_id": null
}
```

- Event log ghi **trước khi báo thành công cho UI**, theo transaction với current state (hoặc journal/checkpoint đảm bảo recovery).
- `pending_turn_id` và `applied_command_id` là idempotency keys; retry HTTP không được chạy lại dice/commit.
- Khi crash sau AI reply nhưng trước commit, phục hồi từ pending snapshot và xác định đã commit hay chưa, không đoán theo đoạn text.
- Không đưa toàn bộ transcript vào context; lưu riêng transcript nếu cần UI/history và có retention policy; durable facts không phụ thuộc vào việc chat bị cắt.

## 8.3. Story cứng mà không phá tự do

Dùng `Story Contract` thay vì script cố định:

- `HARD_LOCK`: sự thật không được đổi, mốc bắt buộc **khi điều kiện hợp lệ đã kích hoạt**.
- `GATED_BEAT`: sự kiện có điều kiện tiền đề, có nhánh/thất bại hợp lý, không tự teleport hay ép kết quả.
- `SOFT_ARC`: xu hướng, mục tiêu, sức ép tương lai để GM dùng khi phù hợp.
- `OPEN_PLAY`: GM tự chọn diễn biến không xung đột với luật và state.

Một sự kiện không xảy ra chỉ vì tác giả đã viết nó trong future chapter. Quest/story engine quyết định *eligibility*, GM quyết định cách kể trong giới hạn hậu quả được core xác nhận. Điều này giữ sáng tạo và không biến GM thành máy đọc lịch sử.

---

# 9. QuotaGovernor và chiến lược Free Tier

## 9.1. Điều phải hiểu về Gemini quota

Theo tài liệu Google: quota có thể giới hạn **RPM, TPM, RPD**, tùy model/tier và theo **project**, không phải mỗi key; số hiển thị của tài khoản phải xem AI Studio [G1]. HTTP `429` có thể do giới hạn tạm thời hoặc hết quota ngày; hướng retry khác nhau [G2].

Do đó `QuotaGovernor` quản lý theo **provider/project/account/model nếu metadata xác định được** và tracking riêng key/credential cho lỗi xác thực. Nếu nhiều key trỏ cùng project thì chung ngân sách. Không cố quay vòng key để né quota.

## 9.2. Chỉ gửi request khi đáng gửi

**Admission control trước gọi GM:**

- Nếu user action là `save`, `load`, xem trạng thái, kiểm tra inventory, menu, setting, xem hồ sơ hay query có thể giải chính xác từ Core: **0 lần gọi GM**.
- Nếu hành động làm thay đổi state nhưng không cần prose tự do: có thể dùng local renderer (có flag rõ trong UI), **0 request**.
- Nếu cần prose: chọn 1 writer; local validator trước/sau; chỉ yêu cầu 1 repair nếu thật sự có **hard violation** và vẫn còn quota/deadline, còn không trả kết quả hạn chế an toàn.
- Không gọi AI auditor cho các lượt bình thường. High-risk semantic audit là optional, chỉ bật nếu tiền/quota/độ rủi ro cho phép và đo chất lượng thật.

## 9.3. Máy trạng thái quota

```text
READY
  -> request OK ---------------------> READY (update observed usage)
  -> HTTP 429 transient ------------> COOLDOWN (respect Retry-After/backoff)
  -> HTTP 429 daily / no remaining --> EXHAUSTED_UNTIL_RESET
  -> 401/403 -----------------------> AUTH_BLOCKED (no blind retry)
  -> 400 invalid model/schema ------> MODEL_BLOCKED (requires config/capability change)
  -> repeated transport/5xx -------> CIRCUIT_OPEN

Any unavailable provider -> next genuinely independent, configured provider
No usable cloud provider -> LOCAL_MODEL if capable -> DETERMINISTIC_ONLY
```

- `429` **không nên luôn cooldown 60s**: phân tích response, `Retry-After`, error reason, model/project counter, header khi có; nếu không phân biệt được, backoff tăng dần có jitter và retry có ngân sách; không spam.
- Daily reset áp dụng mốc do provider công bố; Gemini RPD reset theo midnight Pacific time [G1]. Dùng thời gian thực/offset chính xác khi cần; không hardcode UTC+7 như quota reset.
- Cả **request count** và **input tokens** phải được reserve trước gọi; sau phản hồi reconcile theo usage nếu có. Hủy/retry/timeout cần release reservation đúng và tránh duplicate logical request.
- Dùng deadline tổng cho mỗi lượt. Không lặp qua 5 key x 3 model nếu đã biết tất cả chia chung quota hoặc đang circuit-open.
- Không hạ bớt hard canon để một prompt fit TPM; giảm optional và output trước.

## 9.4. Bốn mức dịch vụ có thể tuyên bố trung thực

| Mức | Nguồn sinh | Chức năng đáng tin |
|---|---|---|
| `S0 — FULL` | API phù hợp, quota ổn | GM viết tự do; core/Canon bảo vệ luật và sự thật. |
| `S1 — COMPACT` | API yếu hoặc hạn mức thấp | GM viết tự do với context nhỏ, hành động được pre-resolve; giảm độ dài/văn hoa nhưng giữ các ràng buộc thiết yếu. |
| `S2 — LOCAL_GENERATIVE` | Local model có sẵn và thiết bị đạt gate | Sinh prose đơn giản/đủ ngữ nghĩa hơn template, tất cả vẫn qua validator/Core. Chất lượng và độ trễ cần benchmark thiết bị. |
| `S3 — LOCAL_DETERMINISTIC` | Không cloud, không local model | UI/engine trả kết quả luật, state, lựa chọn theo world data hiện có; **không giả vờ có GM sinh tự do**. Các yêu cầu sáng tạo không xử lý được được thông báo rõ và không tự commit sai. |

Nếu chơi với model yếu nhưng còn quota, CR2 có thể **giảm gánh nặng kiến thức rất mạnh**. Nếu quota bằng 0 và điện thoại không có local LLM, không thể bảo đảm 90% *trải nghiệm văn xuôi sinh mới*. Vẫn giữ được gần như toàn bộ tính đúng đắn **của các tác vụ Core đã hỗ trợ**, nhưng phạm vi đó phải được công bố riêng.

## 9.5. Quyền riêng tư API Key

BuildConfig được biên dịch vào APK **không phải kho bí mật**. Không đóng gói shared provider keys không được phép phát lộ. Nếu hỗ trợ BYOK, lưu credential bằng Android Keystore-backed encryption/secure app-private configuration theo khả năng platform, không ghi vào log, save export, prompt trace hay crash report; hỗ trợ người dùng xóa/đổi key. Không tự thử nhiều endpoint ngoài các provider được cấu hình và cho phép.

---

# 10. On-device optional AI, không ép Android nào cũng chạy LLM

## 10.1. Tier thiết bị

- **Tier L (low-RAM/entry-level):** SQLite FTS + deterministic Core + remote compact writer khi có; **không tải LLM lớn**.
- **Tier M:** như Tier L, có thể dùng intent model nhỏ **đã tồn tại trong dự án** nếu chi phí RAM ổn; không thêm embedding mặc định.
- **Tier H:** cho phép tải **tùy chọn** GGUF/định dạng on-device được thử nghiệm; user biết dung lượng tải, RAM, nhiệt và chất lượng; không đóng mọi model vào base APK.

LiteRT trong dự án hiện phục vụ intent routing, **không đồng nghĩa đã có local LLM có khả năng viết truyện**. Muốn sinh offline thật, cần tích hợp engine generative riêng và benchmark theo model/ABI/thiết bị. `llama.cpp` có ví dụ Android chính thức [GH3]; Gemini Nano có giới hạn thiết bị/capability [A5]. Không đưa yêu cầu on-device LLM thành phụ thuộc tối thiểu.

## 10.2. Điều kiện bật local model

- App có đủ storage **và** free heap/memory budget thực; thiết bị không đang low-memory.
- Model được benchmark về tokens/s, first-token latency, RAM peak, battery và nhiệt trong ít nhất vài lượt liên tục.
- User cho phép tải/khởi chạy; tiến trình inference có thể hủy, không cản UI.
- Prompt + KV cache nằm trong giới hạn; Android có OOM/LMK với heap và memory pressure [A2].
- Nếu local model chất lượng dưới ngưỡng: **không chuyển sang tự tạo facts sai**. Chỉ cho viết prose trong kết quả đã khóa hoặc quay về deterministic renderer.

## 10.3. Không lạm dụng RAM điện thoại

Cài nhiều pack trên storage không có nghĩa nạp tất cả vào RAM. Mục tiêu RAM là metadata/index cache theo working set; giới hạn cache qua chỉ số quan sát được thay vì “thiết bị có 8 GB thì sử dụng 6 GB”. Cần tính cả WebView, ảnh, Kotlin/Java, native buffers và hệ điều hành.

---

# 11. Xử lý các tình huống khắc nghiệt nhất

| Tình huống | Hành vi bắt buộc | Không được làm |
|---|---|---|
| Corpus 100.000+ record | Exact ID/index lookup + shard lazy + bounded graph + compact packet; đo p95 trên máy thật. | Parse toàn JSON, thử tất cả record mỗi lượt. |
| 15 actor cùng cảnh | Mỗi actor core nhỏ; mở rộng chỉ actor focus/action; Core giữ presence đầy đủ. | Nạp 15 codex đầy đủ hoặc xóa actor khỏi state vì hết token. |
| 100+ quan hệ chéo/cycle | Typed edge + visited + hop/nodes/tokens bounds. | Recursive keyword không giới hạn. |
| 2 NPC tên trùng | ID đã xác nhận + level/organization/scene disambiguation; nếu còn mơ hồ giữ unresolved. | Gộp nhân vật hoặc chọn ngẫu nhiên. |
| Player cố nói một Entity đã xuất hiện | Intent chưa đủ; Core kiểm tra event/roll/scene trước khi confirmed. | Để keyword tự spawn Entity. |
| Mention sự kiện story tương lai | Gating bằng `story_thread`, reveal state và POV; không đưa secret vào packet. | Lấy card vì tên có trong user input rồi tiết lộ bí mật. |
| Canon nguồn xung đột | Quarantine record mới; giữ approved previous pack; emit diagnostic. | Auto-merge bằng LLM hoặc phá active run. |
| Thiếu 99% Canon | UNKNOWN/source gap; GM sáng tạo chỉ trong vùng không canon hóa, không khẳng định official fact. | Tự viết thành “canon vĩnh viễn”. |
| Model yếu bỏ qua ràng buộc | Parser + local validator + authoritative ops; nếu hard error có policy safe resolution. | Tin reply là state. |
| Gemini RPD hết | Không thử lặp vô hạn; thông báo hạn mức; chọn provider độc lập/local tier. | Xoay nhiều key cùng project để lách quota. |
| Mạng chập chờn, response trễ | Deadline/cancel; idempotent turn; không commit trước parse + validation. | Re-roll/double-save khi retry. |
| Điện thoại low-memory | Đóng local model, hạ cache và dừng prefetch; Core/SQLite vẫn dùng được. | Load hết file/ảnh vào heap. |
| Cập nhật pack giữa lượt | Pin version theo turn/snapshot; áp pack mới từ lượt kế, transaction swap. | Cho nửa response dùng Canon v1, nửa còn lại v2. |
| Update pack thiếu dependency | Giữ pack cũ, báo lỗi và rollback; không bỏ hard rule cũ khi active state cần. | Bật pack mới chưa đủ. |
| Tắt app giữa commit | WAL/transaction + pending journal + recovery; event/state cùng revision. | Mất vật phẩm, hoàn tất 2 lần, lệch narrative và state. |
| Prompt injection trong tài liệu | Record là dữ liệu; sanitize/encode; chỉ author-reviewed rule mới được nâng quyền; không làm theo lệnh từ source prose. | Cho đoạn DOCX chứa “ignore previous instructions” thành system prompt. |
| Người chơi/nguồn không đáng tin | Validate JSON, caps kích cỡ, danh sách thao tác cho phép, no arbitrary file/network execution. | Deserialization tùy ý hay chạy lệnh nhúng trong canon. |
| Không có AI sinh | Deterministic gameplay cho chức năng Core; UI nêu rõ khả năng không có sinh văn xuôi. | Hứa 90% truyện sinh như online. |

**Mục tiêu suy giảm an toàn:** lỗi retrieval/generator không được làm lỗi state. Nếu không có facts đủ để adjudicate hành động quan trọng, game phải giữ action pending/rejected đúng nghĩa, không bịa một kết quả thành công.

---

# 12. Đánh giá “90%” có phương pháp, không chơi với số liệu

## 12.1. Ba thước đo độc lập

1. **Canon Safety (bắt buộc):** hard invariant violations sau commit = **0** trên bộ test; sai spawn/loot/level, leakage kiến thức, sai quan hệ khóa được đo theo loại. Mục tiêu 100% cho các hành vi có thể xác định bằng Core, không lấy trung bình 90% để cho phép 10% phá luật.
2. **Retrieval Effectiveness:** Recall@K của **required record** trên scene gold set; false-positive activation, secret leakage và `UNKNOWN` promotion. Đề xuất acceptance *>=99% required hard records* và *0 confirmed-leak cases* trên corpus nhãn hóa, sau đó mở rộng nhiều domain.
3. **Narrative Task Quality:** blind rating các lượt truyện từ **cùng state và action** giữa model yếu với CR2 và model mạnh baseline. Định nghĩa tỷ lệ `weighted_score_weak_CR2 / weighted_score_strong_baseline >= 0.90` trên corpus công bố trước, đồng thời không giảm các safety metrics. Đây là **mục tiêu nghiên cứu**, **không bảo đảm đạt được**.

## 12.2. Bộ đo bắt buộc

- Ít nhất các nhóm: khám phá yên tĩnh, điều tra, Entity được xác nhận/chưa xác nhận, nhân vật mới, nhiều actor, năng lực có ngoại lệ, quan hệ/xưng hô, quest hard gate, loot/Omnivault, chuyển Level, player action tự do, timeline dài, mâu thuẫn nguồn, quota 429, offline/no key, low-memory/restart.
- Kịch bản có adversarial variants: đổi dấu/không dấu, biệt danh, đồng âm, phủ định, nói dối, transcript dẫn dụ, nhiều entity cùng tên.
- Dataset không chỉ lấy từ 54 record hiện tại: tạo thêm **synthetic stress corpus** rõ là dữ liệu giả để đo độ phức tạp, cộng **real canon corpus** để đo độ đúng nội dung.
- Long-run: 1.000–10.000 lượt mô phỏng Core/event; kill/restart ở thời điểm transaction; xác minh lời hứa/discovery vẫn truy xuất khi liên quan.
- Thiết bị: low-RAM thực hoặc emulator được cấu hình, máy trung bình và máy mạnh; Android API 24–mới; đo cold/warm, CPU, RSS/PSS, ANR, battery, p50/p95/p99.
- Provider: dùng key Free có quyền sử dụng thật và quota hợp lệ; không lạm dụng hoặc cố vượt hạn mức. Test quota cạn bằng **fault injection** thay vì đốt request thật.

## 12.3. Ngưỡng nghiệm thu **đề xuất để thử**, chưa đo

| Chỉ số | Gate ban đầu |
|---|---|
| Hard-rule illegal commits | 0 trên golden/integration suite |
| Confirmed Entity/spoiler từ mention không đủ bằng chứng | 0 trên adversarial cases |
| Required hard-record recall | >=99% trên corpus đã gắn nhãn |
| Context token budget | Không vượt profile budget tính **trọn prompt**; không cắt mất hard record |
| Local resolver p95 warm trên điện thoại trung bình | Mục tiêu <100 ms sau khi có index; đánh giá lại dựa trên baseline |
| RAM peak tăng do Canon khi tăng 10x số record không active | Không tăng tuyến tính với tổng corpus; ghi số MB thực theo thiết bị |
| Số request AI mỗi lượt thường | 0 đối với thao tác Core-only; 1 đối với lượt cần writer, không audit mặc định |
| So với version trước | p50/p95 thời gian **đến phản hồi đầu tiên** không thoái hóa quá ngưỡng product chấp nhận; đo mạng/provider riêng |
| Narrative quality weak/strong | Hướng tới >=0.90 theo thước đo được chốt trước; **chưa xác minh** |

Không chấp nhận báo cáo chất lượng bằng cách chỉ kiểm tra rằng record xuất hiện trong packet. Benchmark hiện có trong repo vẫn hữu ích như smoke/contract gate, nhưng không chứng minh model *hiểu* hoặc *sử dụng* record.

---

# 13. Kế hoạch triển khai theo 7 cổng, có sản phẩm và khả năng rollback

## Gate 0 — Audit build/runtime có thật, baseline

**Đầu việc:** xác định thứ tự patch thực sự trong GitHub Actions, artifact Java/Kotlin sau patch, provider chain cuối cùng, luồng input -> Core -> writer -> audit -> state commit -> save, dữ liệu Drive nào đã duyệt. Instrument local resolve time, prompt length và số request. Đo trước khi đổi.

**Chỉ ra file:** `MainActivity.java`, `patch-knowledge-context-builder.py`, patch AI/provider liên quan, `KnowledgeContextEngine.kt`, `TurnCoordinator.kt`, `benchmark-knowledge-context.py`, workflow build thực tế.

**Output:** `CANON_BASELINE_REPORT.md`, execution-path map, test fixtures và danh sách không được phá. **Không chạy full CI thường xuyên**; targeted static/smoke trước, full release gate ở cổng tích hợp.

## Gate 1 — Ingest nguồn thật và Canon Manifest

**Đầu việc:** dùng dữ liệu Drive hiện có (kể cả tài liệu nhân vật được duyệt) để sinh index bản đầu tiên, không tạo TAG rỗng; lập source coverage, source hash, approval/conflict report. 99% chưa có được đánh dấu thiếu.

**Output:** `canon_source_manifest`, một gói records hợp lệ từ **nguồn thật**, report số docs/records/unknown/conflict và sample trace nguồn. Chấp nhận khi **100% record ship có provenance** và không có unreviewed hard rule.

## Gate 2 — CanonStore v2 + indexed lookup

**Đầu việc:** xây interface `CanonStore`, giữ JSON adapter tạm trong build hiện tại; thử SQLite exact IDs, aliases, tags, affordances, typed edges; gắn active pack/version. Bổ sung data-driven ID thay cho danh sách cứng.

**Output:** kiểm thử cùng scene trên JSON và SQLite phải trả cùng required IDs; pack thiếu/hỏng không crash hoặc ảnh hưởng active state. Không cần thêm embedding.

## Gate 3 — SceneResolver + CanonResolver + ContextCompiler

**Đầu việc:** hình thành `SceneSnapshot` từ state được khóa; pre-GM confirmed actors/entities; kiểm soát disclosure; required/optional; graph bounds; prompt hard budget, tokenizer/estimator profile; trace lý do chọn.

**Output:** fixture đo recall/false positive, cảnh đông, action tự do, alias ambiguity và fallback safe. So OLD/NEW trên cùng corpora.

## Gate 4 — Durable Story Memory + deterministic guards

**Đầu việc:** mở rộng `StoryContinuityReducer` bằng event ledger/snapshot/relevant memory view; làm validator theo structured ops/hard rule data; định nghĩa lifecycle của rumors/secret/relationship. Không cố biến validator thành AI hiểu toàn bộ văn xuôi.

**Output:** 1.000+ turns mô phỏng, duplicate/restart fault injection, kiểm tra relation/knowledge POV. **Giữ save/load current version**, không migration save cũ.

## Gate 5 — QuotaGovernor + offline tiers + Android resource policy

**Đầu việc:** admission control, quota project/model, 429 distinctions, provider cooldown & deadline, no-key path, optional local LLM gate, lazy DB caches, pack update/pin/rollback, BYOK key storage.

**Output:** fault-injection matrix 401/403/429/5xx/no internet/low-memory; đảm bảo không gọi AI thừa và state không double commit.

## Gate 6 — Benchmark, rollout và chăm sóc dài hạn

**Đầu việc:** corpus real + synthetic, AI weak vs baseline strong, đo chất lượng bằng rubric mù, hiệu năng trên thiết bị thật, security audit, pack tampering, prompt injection. Rollout tính năng qua feature flag, canary, rollback pack và code gate.

**Output:** `CANON_RUNTIME_2_EVALUATION.md`, số liệu thật, decision log theo từng thiết bị/provider; bật CR2 rộng hơn chỉ sau khi đạt hard gates. Không tuyên bố 90% nếu thiếu dữ liệu.

### Phạm vi PR #668

PR #668 hiện đề cập font Nunito/avatar/Cao Minh và đã chứa một số file knowledge/core theo danh sách diff. **Không biến PR này thành cuộc đại tu âm thầm**. Nếu triển khai trên cùng PR theo chỉ đạo tiếp theo, tạo nhóm commit/feature flag rõ ràng, tránh phá thay đổi UI. Công việc trước mắt là tài liệu kiến trúc và source-backed ingestion plan; code thay đổi chỉ thực hiện sau khi kiểm tra đường build/patch và lựa chọn phạm vi commit.

---

# 14. Khung API nội bộ đề xuất để agent lập trình không hiểu nhầm

```kotlin
interface CanonStore {
    fun loadRecordsByIds(ids: Set<String>, snapshot: CanonSnapshot): List<CanonRecord>
    fun resolveAliases(terms: Set<String>, scope: CanonScope): Set<String>
    fun queryAffordances(affordances: Set<String>, scope: CanonScope): Set<String>
    fun traverseTypedEdges(startIds: Set<String>, types: Set<EdgeType>, bounds: GraphBounds): Set<String>
    fun findOptionalText(query: String, scope: CanonScope, limit: Int): List<CanonCandidate>
}

interface SceneResolver {
    fun resolve(state: GameState, action: PlayerAction, rolls: LockedRolls): SceneSnapshot
}

interface CanonResolver {
    fun resolve(scene: SceneSnapshot, snapshot: CanonSnapshot): CanonSelection
}

interface ContextCompiler {
    fun compile(scene: SceneSnapshot, selection: CanonSelection,
                profile: ProviderProfile, budget: ContextBudget): ContextPacketResult
}

interface QuotaGovernor {
    fun admit(request: GenerationRequest): AdmissionDecision
    fun recordResult(result: ProviderAttemptResult)
}
```

**Các điểm phải quyết định khi implement:** Kiểu thật của `GameState`/`PlayerAction`/`LockedRolls` hiện có; không tạo adapter nếu có sẵn contract phù hợp. `CanonSnapshot` pin pack version cho cả lượt. `ContextPacketResult` có `READY`, `MISSING_REQUIRED_CANON`, `BUDGET_UNSATISFIABLE`, `PACK_ERROR`, không trả chuỗi rỗng rồi bảo GM tự đoán.

**Pseudo-code một lượt:**

```text
input -> classify meta/core-only vs needs narration
pending = TurnCoordinator.createPending(turn_id, input)
state_snapshot, roll_snapshot, canon_pack_version = freeze(pending)
scene = SceneResolver.resolve(state_snapshot, input, roll_snapshot)
required, optional, retrieval_trace = CanonResolver.resolve(scene, canon_pack_version)
packet = ContextCompiler.compile(scene, required, optional, provider_profile)
if required missing/unsafe -> safe local resolution (no invented canon)
else if core-only -> deterministic response without API
else:
    route = QuotaGovernor.admit(packet_cost, deadline, provider_capabilities)
    candidate = route.generate(packet) or safe local outcome
    validated_ops = GameCoreFacade.validate(candidate.ops, frozen state/rolls)
    local_issues = CanonRuleValidator.check(scene, candidate, validated_ops)
    if severe issue -> bounded repair if affordable else reject/limit narration
commit via authoritative turn coordination, write ledger + revision atomically
publish reply and new state; observe metrics without leaking secrets
```

**Quan trọng:** `freeze` không nhất thiết phải là lớp mới. Có thể dùng `GameState` snapshot/revision và pending transaction hiện có; đây là contract ngữ nghĩa, không phải yêu cầu thêm framework.

---

# 15. Những quyết định cố ý KHÔNG chọn

1. **Không fine-tune một model riêng cho toàn Canon.** Canon thay đổi liên tục, fine-tune không làm nguồn sự thật tuyệt đối và có chi phí lớn.
2. **Không tạo agent đọc Drive mỗi lượt.** Phụ thuộc mạng, quota, độ trễ, quyền truy cập và tình trạng tài liệu.
3. **Không dùng full vector RAG làm tuyến chính.** Vector giống ngữ nghĩa nhưng dễ sai exact names, scope, negation, aliases và hard locks.
4. **Không dùng Neo4j/graph server bắt buộc.** Typed edges trong SQLite đủ cho nhu cầu lookup, giảm vận hành.
5. **Không chèn toàn bộ source/document vào prompt.** Gửi record cần thiết và giữ metadata nguồn phía local.
6. **Không thêm local LLM nặng vào mọi APK.** Chỉ khả dụng trên thiết bị đủ tài nguyên và đã benchmark; không đánh đổi việc mở game được trên máy yếu.
7. **Không cho AI tự tóm tắt và ghi đè Canon/trạng thái.** Model chỉ tạo candidate prose/ops; durable memory do Core xác nhận.
8. **Không tạo schema compatibility cho save cũ.** Chỉ bảo vệ save hiện hành và hợp đồng module liên quan.

---

# 16. Vận hành, quan sát và an toàn

**Trace mỗi turn (local, có thể opt-in export):** `turn_id`, `pack_version`, `state_revision`, `present_entity_ids`, `retrieved_record_ids + reasons`, `required_missing`, `total_input_estimated/observed_tokens`, `provider_model`, `quota_decision`, `latency_local`, `latency_provider`, `validator_codes`, `commit_revision`. Không ghi API key, raw canon bí mật, personal data hoặc full prompt theo mặc định.

**Integrity & failure:** Hash/signature cho pack; schema version; staged install; verify trước commit; atomic activate; rollback. Giới hạn dung lượng import và path traversal; không thực thi code từ asset. Khi không có nguồn hoặc pack chưa duyệt, không tự lấy nội dung bất kỳ từ web làm canon.

**Cảnh báo bắt buộc cho developer:** “Canon not found” khác “Canon permits anything”. “Source says unknown” khác “source absent”. “Entity mentioned” khác “confirmed encounter”. “NPC has ability” khác “NPC uses ability”. “GM narrated outcome” khác “Core committed outcome”. Mọi lỗi thuộc bốn cặp này được ưu tiên hơn thẩm mỹ văn phong.

**Feature flag:** Cho phép so OLD vs CR2 qua một cấu hình có thể bật/tắt ở bản phát hành; canary dữ liệu thực trước khi xóa code cũ không liên quan. Flag không tồn tại để duy trì đọc save cũ.

---

# 17. Danh mục nguồn kiểm chứng

Các ý kiến Reddit là **kinh nghiệm tự báo cáo, không phải kết quả chuẩn hoá**. Tài liệu chính thức và mã nguồn repo được ưu tiên cho claims kỹ thuật. Đã tra cứu nhiều nguồn độc lập; không trích lại long quote.

### Reddit

- **[R1]** r/SillyTavernAI, *Lorebook Recursion*, 12/12/2025 — lợi ích và nổ chuỗi City/Bar/Bartender: https://www.reddit.com/r/SillyTavernAI/comments/1pkzfzc/lorebook_recursion/
- **[R2]** r/SillyTavernAI, *Anyone running chonky lorebooks?*, 17/05/2026 — trường hợp >50k token, ~138 token/entry, 24–28 active: https://www.reddit.com/r/SillyTavernAI/comments/1tfeq7l/anyone_running_chonky_lorebooks_30_entries_hows/
- **[R3]** r/AIDungeon, *Story Cards Guide*, 11/01/2026 — keyword triggers có thể áp dụng muộn một lượt: https://www.reddit.com/r/AIDungeon/comments/1qadbzg/story_cards_guide_core_mechanics_trigger_design/
- **[R4]** r/SillyTavernAI, *Lorebooks, Caching, & You*, 07/10/2025 — cache miss khi dynamic content chen đầu prompt DeepSeek: https://www.reddit.com/r/SillyTavernAI/comments/1o0bwzf/
- **[R5]** r/Rag, *Hybrid search and reranking made my RAG worse*, 26/07/2026 — 1 trường hợp hybrid/rerank không cải thiện: https://www.reddit.com/r/Rag/comments/1v7g3oe/hybrid_search_and_reranking_made_my_rag_worse/
- **[R6]** r/Rag, *What’s your experience with hybrid retrieval?*, 26/02/2026 — 1 trường hợp báo cáo cải thiện 60% -> 85% trên 50 câu hỏi; **không suy rộng**: https://www.reddit.com/r/Rag/comments/1rf7xf6/whats_your_experience_with_hybrid_retrieval/
- **[R7]** r/SillyTavernAI, *How do lorebooks work?*, 16/03/2026 — khó debug điều kiện kích hoạt/entry: https://www.reddit.com/r/SillyTavernAI/comments/1rv6914/how_do_lorebooks_work/

### GitHub / tài liệu dự án khác

- **[ST1]** SillyTavern World Info docs — activation, scope, recursion, context budget, vector retrieval trade-offs: https://github.com/SillyTavern/SillyTavern-Docs/blob/main/Usage/worldinfo.md
- **[ST2]** SillyTavern MemoryBooks docs — memory tầng scene/arc, continuity và nguồn fact: https://github.com/aikohanasaki/SillyTavern-MemoryBooks/blob/main/userguides/1%20Memory_Books_AI_Reference_Manual.md
- **[GH1]** Mem0 graph memory — entity graph, giới hạn quan hệ suy ra từ đồng xuất hiện: https://github.com/mem0ai/mem0/blob/main/docs/platform/features/graph-memory.mdx
- **[GH2]** LocalGPT — kiến trúc index/retrieval/benchmarks và chi phí phụ thuộc mô hình: https://github.com/PromtEngineer/localGPT
- **[GH3]** llama.cpp Android guide — GUI/native Android và giới hạn context RAM: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- **[P1]** Backrooms README: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/README.md
- **[P2]** Backrooms KnowledgeContextEngine: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt
- **[P3]** Backrooms Knowledge Source Map: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/KNOWLEDGE_SOURCE_MAP.md
- **[P4]** Backrooms StoryContinuityReducer: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/StoryContinuityReducer.kt
- **[P5]** Backrooms KnowledgeLocalValidator: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeLocalValidator.kt
- **[P6]** Backrooms build-time knowledge patch: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/patch-knowledge-context-builder.py
- **[P7]** Backrooms benchmark: https://github.com/rabpitvn1-create/BACKROOMS/blob/main/android-apk/benchmark-knowledge-context.py
- **[P8]** Backrooms PR #668: https://github.com/rabpitvn1-create/BACKROOMS/pull/668

### Official platform / provider

- **[G1]** Gemini rate limits (RPM/TPM/RPD, project-level, midnight Pacific): https://ai.google.dev/gemini-api/docs/rate-limits
- **[G2]** Gemini API errors & quota exhausted: https://ai.google.dev/gemini-api/docs/api-errors
- **[G3]** Gemini troubleshooting / exponential backoff: https://ai.google.dev/gemini-api/docs/troubleshooting
- **[A1]** Android Room/SQLite FTS options: https://developer.android.com/training/data-storage/room/defining-data
- **[A2]** Android low-memory guidelines: https://developer.android.com/topic/performance/memory-overview và https://developer.android.com/topic/performance/issues/lmk
- **[A3]** Android WorkManager persistent background tasks: https://developer.android.com/develop/background-work/background-tasks/persistent
- **[A5]** Android Gemini Nano / AICore on-device: https://developer.android.com/ai/gemini-nano

---

# 18. Kết luận cuối cùng và thứ tự ưu tiên

**Lựa chọn dài hạn:** `Source-backed Canon Pack -> indexed CanonStore -> SceneSnapshot -> rule-gated CanonResolver -> adaptive ContextCompiler -> quota-aware single GM -> authoritative commit -> durable event ledger`.

**Bước triển khai ưu tiên số 1 không phải viết lại GM và cũng không phải tạo vector DB.** Phải **nhập Canon thực sự đang có, gắn nguồn, kiểm chứng schema, xác định luồng patch hiện hành**, rồi mới dần loại bỏ hardcoded character/Entity routing. Sau đó mới mở rộng SQLite, durable memory, quota policy và optional on-device generation theo benchmark.

**Mục tiêu cốt lõi:** Số tài liệu Canon tăng lên bao nhiêu cũng không ép số token GM nhận tăng tương ứng. Máy cục bộ nhớ và xác định điều kiện; Game Master chỉ làm việc sáng tạo trong scene đã được chuẩn bị. Khi AI yếu/quota thấp, Core vẫn giữ sự thật. Khi AI không tồn tại, game suy giảm chức năng **trung thực, an toàn, không phá save/state** thay vì bịa truyện để che lỗi.

**Tình trạng xác minh:** Đã đọc mã nguồn và tài liệu repo qua GitHub, đối chiếu Reddit/GitHub bên ngoài và tài liệu nhà cung cấp/Android. **Chưa thay đổi code, chưa build APK, chưa chạy test Kotlin, chưa chạy benchmark trên máy hay gọi API thật.** Tất cả target chất lượng/latency trong tài liệu là acceptance proposals, không phải kết quả đo.
