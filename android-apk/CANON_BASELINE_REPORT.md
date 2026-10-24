# CANON BASELINE REPORT — Gate 0 (baseline cây PR #668)

> Tài liệu baseline cho developer và mọi mô hình/agent làm việc trên repo này sau này.
> Tổng hợp từ hai audit đã kiểm chứng trực tiếp trên cây nguồn (2026-10-24):
> `~/workspace/cr2_work/gate0_audit.md` (CR2-A1 — execution path, hardcode, giới hạn) và
> `~/workspace/cr2_work/gate1_ingest_audit.md` (CR2-A2 — provenance & ingest).
> Đối tượng: cây `android-apk/` ở trạng thái **PR #668** (đã canon-swap Kai→Cao Minh,
> Iris→Lục Trầm; đã thêm passive Đại Ma Đầu; đã purge MadGod) — **không phải `main` thuần**.
> Tài liệu thiết kế đích: `~/workspace/cr2_work/ARCHITECTURE.md` (Canon Runtime 2.0).
>
> Sản phẩm đi kèm đợt baseline này: `knowledge_db.json` đã nâng lên **Record v2**
> (schemaVersion 2) và `canon_source_manifest.json` ở thư mục này.

---

## 1. Bản đồ execution-path (luồng một lượt chơi)

Có **ba đường** cùng tồn tại; hiểu sai đường nào đang chạy là lỗi phổ biến nhất.

### Đường A — lượt GM trong cây nguồn (chưa qua chuỗi patch)

| Bước | Vị trí | Ghi chú |
| --- | --- | --- |
| Vào lượt | `app/src/main/java/com/rabpit/backroom/MainActivity.java:957` `GameBridge.submitTurn(stateJson, action)` | `@JavascriptInterface`; WebView giữ state JSON và gọi vào; chạy trên `io` executor (:958). |
| Phân loại meta | :960 → `isMetaAction(action)` :691 | Bằng danh sách cụm từ cứng. |
| Khóa xúc xắc | :961 → `makeGameplayRolls(before, action, meta)` :781 | Rolls do Android sinh **trước** khi gọi model — model không được đổi kết quả. |
| Dựng prompt | :963–981 (inline) | Toàn văn hằng `DRIVE_CANON` (:35) + `state.toString()` đầy đủ + action + rolls + JSON schema cũ. **Cây nguồn KHÔNG gọi `KnowledgeContextEngine.build`.** |
| Gọi provider (cơ sở) | `generateText` :180 → `geminiText` :135 | 5 key `BuildConfig.GEMINI_API_KEY_1..5` (app/build.gradle:28-32 nhúng từ secret lúc build); model hằng `gemini-3.6-flash` :30; mỗi key ≤2 attempt; retry {408,429,500,502,503,504} hoặc code 0; read timeout 60s. |
| Parse + sanitize | `parseModelJson` :184; sanitize :985-1006 (`sanitizedPlayer/Party/Inventory/Flags`, `canTransition`, `sanitizedSnapshotEvent`) | Non-meta: `turn` +1, `flags.lastRolls = rolls`; gắn `canonVersion = DRIVE_CANON_VERSION` :34; `emit("backroomTurn", ...)`. Android cơ sở **không tự save** trong submitTurn. |

Các hàm `compactDriveCanon(state, action, rolls)` :371, `compactCaoMinhCanon` :401,
`compactStateForPrompt` :421 tồn tại nhưng **không có call site trong cây nguồn** (dead code
tương thích benchmark/audit — xem mục 6, điều 10).

### Đường B — knowledge path SAU chuỗi patch build

Luồng knowledge **không nằm trong cây nguồn**; nó được nối vào lúc build bởi
`patch-knowledge-context-builder.py`, và patch này phụ thuộc các patch sinh hàm đi trước:

1. `patch-ai-orchestrator.py` thay `GameBridge.submitTurn` để dùng `compactDriveCanon` + validated state operations.
2. `patch-three-action-bridge.py`, `patch-conditional-audit.py`, `patch-audit-validated-risk.py`, `patch-rejected-op-repair-final.py` sinh ra `writerPrompt(...)`, `runAudit(...)`, `auditScopeCanon(...)`, `candidateState`, `appendIssues()`, `rejectedOperationIssuesAndroid()`. **Các hàm này không tồn tại trong cây nguồn.**
3. `patch-knowledge-context-builder.py` (131 dòng) can thiệp đúng 5 điểm vào MainActivity đã patch:
   - Thay thân `auditScopeCanon(...)` → `KnowledgeContextEngine.build(MainActivity.this, before.toString(), action, rolls.toString())` (giữ signature).
   - Thay thân `runAudit(...)`: auditor dùng packet trên, reply cắt 7000 ký tự, `geminiAuditText` + `parseModelJson`.
   - Thay thân `writerPrompt(...)`: `packet = KnowledgeContextEngine.build(...)`; gắn `AUDIT FEEDBACK HARD` khi có auditFeedback; định nghĩa `localKnowledgeIssues(before, generated)` gọi `KnowledgeLocalValidator.validate(...)`.
   - Chèn validator tại 2 điểm (sau `rejectedOperationIssuesAndroid(...)` ở first pass — chỉ khi `!meta` — và ở repair pass): `appendIssues(hardIssues, localKnowledgeIssues(before, generated))`.
   - Chèn reducer **trước commit**: ngay sau `JSONObject state = candidateState;`, chỉ khi `!meta`: `state = new JSONObject(StoryContinuityReducer.apply(before.toString(), state.toString(), action))`. Continuity chỉ ghi từ candidate **đã validated**, không bao giờ từ candidate bị reject.
4. `patch-knowledge-engine-source.py` sửa trực tiếp `KnowledgeContextEngine.kt` — **đang lệch anchor**, xem mục 7.1.

Bên trong engine (`app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt`):

- API: `build(context, stateJson, action, rollsJson): String` :52 (@JvmStatic); `traceRecord` :59; `recordIds` :71.
- `load()` :80 đọc **toàn bộ** `assets/knowledge/knowledge_db.json`, cache singleton, dựng `records` map + `tagIndex` + `affordanceIndex`; trùng id → `require` fail ngay khi load (:101).
- Pipeline chọn record (`Builder.build()` :133, thứ tự cố định): `resolvePresence()` → `addMandatory()` → `addCurrentLevel()` → `addPresentRuntimeCards()` → `addRelationships()` → `addDirectStructuredLookups()` → `addSceneAffordances()` → `addStateDrivenRecords()` → `expandReferences()` → `budgetedRecords()` → `hardClip(..., HARD_CONTEXT_CEILING)`.
- Định dạng packet (hợp đồng đầu ra :140-160): header `[KNOWLEDGE_PACKET v1]`, dòng `Budget target=2200 soft=2800 hard=3400`, `Present actors:`, `Current state:`, `Recent dialogue buffer:`, `Retrieved records:` — mỗi record 2 dòng `<ID> text` + `  source=<doc>#<anchor>; authority=...; mutability=...; why=<reason>`, kết `[END_KNOWLEDGE_PACKET]`.

Validator (`KnowledgeLocalValidator.kt:14`): dòng đầu gọi `KnowledgeContextEngine.recordIds(context)` để **ép nạp DB — DB hỏng/thiếu sẽ fail trước khi commit** (fail-fast có chủ đích). Chỉ đọc `generated.reply`, normalize lowercase + gộp khoảng trắng, đối chiếu substring; trả `{"issues":[{rule, severity:"hard", claim, reason}]}`.

### Đường C — Core/Facade và commit (hạt nhân transaction đích)

- Khởi tạo: `MainActivity.java:42` `gameCore = GameCoreFacade.create(...)` (SharedPreferencesSaveRepository).
- `GameCoreFacade.processRule(legacyStateJson, action)` (`core/GameCoreFacade.kt:15`): đường deterministic, **không bao giờ gọi Gemini** — `loadOrMigrate` → `TurnCoordinator.createPending` → `RuleIntentInterpreter.interpretSync` → chặn PICKUP trực tiếp (`player_pickup_unavailable`) → `CommandResolver.resolve` → `TurnCoordinator.commit` → `repository.save` → `syncLegacy`.
- `GameCoreFacade.processValidatedCandidate(beforeJson, candidateJson, action)` :155: dựng `ItemCommand`/`PartyCommand`/`ValidatedLegacyStateCommand`/`TimeAdvanceCommand` từ diff đã validated → `TurnCoordinator.commit` → `CharacterProgressionCore.protectFromCandidate` → `repository.save`.
- `TurnCoordinator` (`core/TurnCoordinator.kt`): `createPending` :10 (chống `turn_already_completed`/`another_turn_pending`, idempotent cùng turnId+input), `commit` :26 chạy `StateReducer.executeAll`; batch fail → pending FAILED, **state gốc giữ nguyên**; thành công → `completedTurnIds += turnId` + `CharacterStatEngine.applyCompletedTurnRegen` :45. Có `recover` :49, `reject` :53.
- Hai chế độ save cùng tồn tại: (a) legacy JSON do WebView giữ (đường A/B — `StoryContinuityReducer` ghi vào `flags.storyContinuity` **trong legacy JSON**), (b) `SaveRepository` cho GameState Core (đường C).
- Lưu ý: trong cây nguồn **không có call site từ MainActivity tới `processRule`/`processValidatedCandidate`** (chỉ có comment tại `GameCoreFacade.kt:37`); MainActivity gọi facade qua `requireGameCore()` — mà **định nghĩa `requireGameCore()` cũng không có trong cây nguồn**, do patch chèn vào (xem mục 7.2).

---

## 2. Đối chiếu §1.1 của thiết kế với cây PR #668

| # | Khẳng định §1.1 | Kết quả trên cây PR #668 |
| --- | --- | --- |
| 1 | knowledge_db.json có 54 record / 9 domain | **LỆCH NHẸ**: thực tế **55 record** (PR #668 thêm `CHAR.CAO_MINH.DAI_MA_DAU`), vẫn 9 domain: CHARACTER 19, ENTITY 11, LEVEL 7, RELATIONSHIP 5, WRITING 4, ITEM 4, GAME_RULE 2, STORY 2, WORLD 1. Nay đã nâng Record v2 (schemaVersion 2). |
| 2 | Engine đọc toàn JSON vào map; chọn mandatory/core, Level, nhân vật hiện diện, relationship, direct lookup, affordances, references | **ĐÚNG** (đã swap tên Luc Trầm/Syvial). `load()` :80; pipeline :133. |
| 3 | Budget 2200 / soft 2800 / hard 3400 token ước lượng | **ĐÚNG**: hằng tại :17-19. |
| 4 | Validator kiểm chuỗi phản Canon về Sparda, Lucifer, ARGUS, Omnivault, Entity, Level 0 | **LỆCH (đã swap/purge)**: bản PR #668 không còn rule Sparda/ARGUS/Omnivault. Rule hiện hành: mục 3.3. |
| 5 | Reducer giới hạn 12 events, 12 discoveries, 8 objectives, 8 threads, 16 knowledge, 8 relationships | **ĐÚNG tuyệt đối**: `StoryContinuityReducer.kt:16-21`. |
| 6 | TurnCoordinator: pending, chống hoàn tất trùng, commit qua StateReducer, recover | **ĐÚNG**: :10, :26, :35, :49. |
| 7 | patch-knowledge-context-builder nối `build()` vào writer + auditor, validator vào lượt sinh, reducer trước commit | **ĐÚNG theo nội dung patch** (mục 1, đường B). Patch chưa áp vào cây nguồn; phụ thuộc anchor của patch đi trước. |
| 8 | patch-gemini-model-matrix-final + patch-provider-deadline-final: matrix key/model, cooldown, circuit breaker; 429 cooldown ~60s | **ĐÚNG**: matrix 3 model × 5 key; 429 → cooldown lane 60s; circuit model 5 phút; credential disable 30 phút; timeout 45/35/25s. Thứ tự provider cuối: **Gehihi → Gemini → Haku → SOL**; Gemini read timeout nâng lên 18s. |
| 9 | benchmark-knowledge-context.py: corpus 8 kịch bản, `ceil(chars/4)`, không phải benchmark model thật | **ĐÚNG**: benchmark là **mô phỏng Python độc lập** (tự cài lại selection), assert p95 ≤ 3400 (benchmark :332). Không chạy Kotlin thật. |
| 10 | app/build.gradle nhúng API key vào BuildConfig | **ĐÚNG**: app/build.gradle:28-32. Khớp cảnh báo §9.5 của thiết kế. |

Đối chiếu 8 "giới hạn cần sửa" của §1.1: **cả 8 đều đúng về bản chất trên cây PR #668**
(tokenizeTags cứng — thực tế **19 cụm**, không phải ~17; đọc nguyên JSON; expandReferences
không giới hạn hop/nút; budgetedRecords cộng mandatory trước + hardClip cắt theo ký tự;
compactState nhét toàn bộ storyContinuity cắt 5200 ký tự + recentDialogue 4 entry; bộ nhớ
dài hạn cắt theo số lượng; validator substring có FP/FN; rủi ro patch lệch anchor — đã có
ca thật, mục 7.1). Chỉ số liệu bị lệch là số record (55) và nội dung validator (hàng 1, 4).

---

## 3. Hardcode inventory (các điểm phải data-driven hoá)

### 3.1. Trong `KnowledgeContextEngine.kt`

| Điểm cứng | Vị trí | Nội dung |
| --- | --- | --- |
| Actor chính khởi tạo | :122 | `presentActors = linkedSetOf("cao-minh")` khóa cứng trong code. |
| Nhận diện actor hiện diện | `resolvePresence()` :303-330 | Chỉ biết `"luc-tram"`, `"syvial"` (khớp `contains` trên id/name từ `state.party`/`partyDetails.members`). Nhân vật khác (kể cả An Nhiên) vô hình với engine. |
| Runtime card theo actor | `addPresentRuntimeCards()` :184-187 | `CHAR.LUC_TRAM.RUNTIME_CORE`, `CHAR.SYVIAL.RUNTIME_CORE` viết tay. |
| Quan hệ/xưng hô theo cặp | `addRelationships()` :189-199 | 5 record viết tay: `REL.CAO_MINH.LUC_TRAM.BASELINE`, `ADDR.LUC_TRAM.CAO_MINH`, `REL.CAO_MINH.SYVIAL.BASELINE`, `ADDR.SYVIAL.CAO_MINH`, `REL.LUC_TRAM.SYVIAL.BASELINE` (cặp cuối chỉ khi cả hai cùng present). Đây là typed edge `ADDRESS_AS`/`RELATION_BASELINE` của thiết kế §3.2 nhưng đang nằm trong code thay vì dữ liệu. |
| Mandatory set | `addMandatory()` :167-177 | 7 id viết tay: `GAME.TEXT.CORE`, `GAME.GM.FAIRNESS`, `WORLD.CORE`, `WRITING.KNOWLEDGE_BOUNDARY`, `WRITING.COMPETENCE`, `WRITING.PLAYER_AGENCY`, `CHAR.CAO_MINH.RUNTIME_CORE`. |
| Direct lookup theo cụm từ | `addDirectStructuredLookups()` :201-225 | ~15 ánh xạ cụm từ → record id viết tay (Thiên Kiếm Linh Tâm, Tịch Quang, kiếm khải, GodKiller, Lucifer Core, Vạn Quỷ Ma Tâm, Nhị Thập Tứ Trảm, Huyết Ma Kiếm, Vạn Khố Giới Chỉ + alias "scan/restore", Vạn Quỷ Ma Thân, Devil Trigger có điều kiện present). |
| Tag Entity/Item | `tokenizeTags()` :409-422 | 19 cụm viết tay (16 Entity + 3 Item). Ý đồ thay bằng quét `tagIndex` đã nằm trong patch-knowledge-engine-source nhưng patch lệch anchor (mục 7.1). |
| Affordance theo từ khóa | `addSceneAffordances()` :227-245 | 4 affordance (`trace_analysis`, `direct_threat`, `field_medical`, `field_food`) kích hoạt bằng cụm từ cứng trên sceneText; lọc actor cứng theo tiền tố `CHAR.LUC_TRAM.`/`CHAR.SYVIAL.` (:237-238). Thiết kế §6.3: affordance phải là registry data. |
| Record theo state | `addStateDrivenRecords()` :247-261 | `ENTITY.GLOBAL_HARD_LOCK`/`ITEM.GLOBAL_HARD_LOCK` bằng cụm từ cứng + cờ `entitiesConfirmedLocal` + roll `entityEncounter`; story records qua `isMainCampaignSeparated()` :377-383 (đọc `flags["luc-tram"/"syvial"].continuity` chứa "separated" + heuristic `presentActors.size == 1 && turn <= 3`). |
| Whitelist compact state | `compactState()` :338-362 | Key player (`hp/condition/weapon/armor/needs`) và key continuity viết tay (:346, :356-360). |

### 3.2. Trong `StoryContinuityReducer.kt`

- Actor continuity/relationship/reunion viết tay đúng `"luc-tram"`, `"syvial"`: `apply()` :39-47; `updateReunionThreads` :162 lặp `listOf("luc-tram", "syvial")`; objectives đoàn tụ sinh từ 2 actor này (`syncMainObjectives` :57, id mẫu `MAIN.REUNITE.<ACTOR>`).
- `currentWitnesses()` :184 luôn bắt đầu bằng `"cao-minh"` viết cứng.
- Registry discoveries: tối đa 8 id mới/registry mỗi lượt (`take(8)`, :132); `trim()` :235 giữ N phần tử **cuối** (mới nhất), không có tiêu chí quan trọng/durable.

### 3.3. Trong `KnowledgeLocalValidator.kt` (toàn bộ là cụm từ cứng, :31-63; không đọc từ record/rule data)

| Rule id | Dòng | Điều kiện kích hoạt (substring trên reply đã normalize) |
| --- | --- | --- |
| `competence_suppression` | :31 | "vạn quỷ ma tâm cạn / ma tâm cạn / ma tâm hết / hết ma nguyên / cạn ma nguyên" + có "cao minh" |
| `competence_suppression` | :34 | "lucifer core cạn/hết", "syvial hết/cạn quỷ lực" |
| `ability_overreach` | :37 | Bịa thời lượng/cooldown/phản phệ cho "vạn quỷ ma thân" hoặc "devil trigger" (8 cụm) |
| `ability_overreach` | :40 | Linh Tâm "đọc suy nghĩ / biết thiện ác / tự nhận entity / biết trước luật" (6 cụm) |
| `ability_overreach` | :43 | "hai thanh tịch quang / tịch quang thứ hai / ..." (4 cụm) |
| `ability_overreach` | :46 | "godkiller gunblade / godkiller là súng / biến thành súng" |
| `ability_overreach` | :49 | Vạn Khố Giới Chỉ "cất/chứa/scan" người (8 cụm) |
| `canon_conflict` | :54 | "entity/thực thể thân thiện / trung lập" + guard phủ định thô (chỉ kiểm tra vắng mặt của "không có/không phải/không thể" trên toàn reply) |
| `canon_conflict` | :60 | Level hiện tại == 0 (đọc từ state) + "entity/thực thể cư trú", "quần thể entity ở level 0" |

### 3.4. Ngoài knowledge (liên quan trực tiếp execution-path)

- `MainActivity.isMetaAction` :691 — phân loại meta bằng cụm từ cứng.
- Phân loại hành động cho compact canon `actionDialogue/actionCombat/actionOmnivault/actionItem/actionEntity` :343-361 — còn chứa từ khóa legacy ("omnivault", "guilty crown", "white wraith", "magnum", "talon", "phantom" ở :347).
- `GameCoreFacade.contextFor` :253 — bản đồ tên → id viết tay ("cao minh", "lục trầm", "syvial", "an nhiên"...).
- Prompt writer sau patch (patch-knowledge-context-builder.py :66-84): danh sách flag root cho phép viết cứng trong chuỗi prompt; câu "Người chơi chỉ điều khiển hành động có chủ ý của **Kai**" — **residue tên cũ ngay trong patch knowledge hiện hành**, cần dọn khi triển khai CR2.
- Thứ tự/điều kiện audit (khi nào chạy auditor, mấy auditor, mấy repair) nằm trong các patch conditional-audit / rejected-op-repair, không nằm trong source.

---

## 4. Bảng giới hạn hiện tại (số liệu đã kiểm chứng)

| Giới hạn | Giá trị | Vị trí |
| --- | --- | --- |
| Knowledge packet target / soft / hard | 2200 / 2800 / 3400 token **ước lượng** | KnowledgeContextEngine.kt:17-19 |
| Ước lượng token | `ceil(chars / 4)`; +8 token/record; base +80 | :431; Record.estimatedTokens :40; :295 |
| Mandatory trong ngân sách | priority ≤ 30, cộng **không điều kiện** (bất kể tổng ngân sách) | budgetedRecords() :279-290 |
| Optional | Cộng nếu vừa trần động: TARGET khi đang dưới TARGET, ngược lại SOFT | :291-296 |
| expandReferences | BFS có `visited` nhưng **không** hop limit / node limit / byte limit; chỉ theo target priority ≤ 55 → record priority 56/58/60 không bao giờ tới được qua references | :263-277 |
| hardClip | Cắt chuỗi tại 3400×4 = **13.600 ký tự** + hậu tố `[PACKET_CLIPPED_AT_HARD_CEILING]` — có thể cắt giữa record | :386-390 |
| compactState | Cắt **5200 ký tự** toàn khối (gồm storyContinuity) | :338, :361 |
| Recent dialogue trong packet | **4** entry gần nhất, **700** ký tự/entry | :22-23, :364 |
| Reducer caps | events 12, discoveries 12, objectives 8, threads 8, knowledge 16, relationships 8; trim giữ mới nhất | StoryContinuityReducer.kt:16-21, :235 |
| Registry discovery mỗi lượt | Tối đa 8 id mới / registry | :132 |
| Validator | 9 rule substring, severity luôn "hard", không rule data | KnowledgeLocalValidator.kt:31-63 |
| Provider cơ sở | 5 key × 2 attempt, retryable {408,429,500,502,503,504}, sleep 350ms, read timeout 60s | MainActivity.java:32, :95, :105, :135 |
| Provider sau patch (matrix) | 3 model (writer: 3.6 Flash → 3.5 Flash → 3.5 Lite; auditor: Lite → 3.5) × 5 key; 429 → lane cooldown **60s**; circuit model 5 phút; credential disable 30 phút; timeout 45/35/25s | patch-gemini-model-matrix-final.py |
| Provider sau patch (deadline) | Thứ tự **Gehihi → Gemini → Haku → SOL**; Gemini read timeout 18s; Haku max_tokens 2048 | patch-provider-deadline-final.py:13, :66, :191-194 |
| DB seed | **55 record**, schemaVersion **2** (Record v2 — xem mục 8), priority 10–60 | assets/knowledge/knowledge_db.json |

---

## 5. Provenance & coverage (Gate 1 — tóm tắt)

- **Authority precedence chuẩn** nằm ở `KNOWLEDGE_SOURCE_MAP.md` (mục "Authority order") và là nguồn duy nhất được phép dùng: (1) chỉ đạo mới nhất của tác giả > (2) Text Game rules + campaign state cho runtime facts > (3) Character Codex cho đặc điểm bất biến > (4) World canon > (5) story/continuity > (6) writing rules > (7) reference/benchmark. `UNKNOWN`/`OPEN`/`CHƯA KHÓA` giữ nguyên là unknown — **cấm runtime tự hợp nhất bằng suy diễn**.
- 55/55 record có `source.document` + `source.anchor` (chuỗi truy vết: GM context line → KnowledgeRecord.id → source.document + source.anchor).
- Chỉ **2 tài liệu nguồn có bản local** để tính SHA-256 tại Gate 1: `CAO_MINH_CODEX.md` (R17 + Addendum R18, sha256 `c8b4172d…`) và `LUC_TRAM_CODEX.md` (R05 + Addendum R06, sha256 `58e85b5d…`) → **15/55 record** truy vết được tới hash. **40/55 record** trỏ tài liệu chỉ có trên Drive (18 tài liệu chưa có bản local) → `content_sha256 = null`, `review_status = PENDING_SOURCE_SNAPSHOT` cho tới khi có snapshot Drive (ingest A của thiết kế §4).
- Coverage: `IMPORTED` 54 record; `PENDING_REVIEW` toàn bộ 55 record + 14 hàng Source Map; `KNOWN_MISSING` = **14 hàng Source Map chưa có record** (`CHAR.CAO_MINH.IDENTITY`, `CHAR.LUC_TRAM.COMBAT`, `CHAR.LUC_TRAM.UNKNOWN`, `STORY.MAIN.PREMISE`, `STORY.MAIN.COMMS_INITIAL`, `STORY.MAIN.LUC_TRAM_EXISTENCE`, `STORY.MAIN.SYVIAL_EXISTENCE`, `WRITING.ADDRESS`, `WRITING.PROSE_POV`, `WRITING.VIVID_VERBS`, `WRITING.HORROR`, `WRITING.INTIMACY`, `WRITING.DIALOGUE_BENCHMARK`, `WRITING.GUIDE_SUPPLEMENT`) + thư mục Drive `05_TextGame/SAVE` trống (thiếu **có xác nhận**); `UNKNOWN_TOTAL`: tổng entity/item của wildcard `ENTITY.*`/`ITEM.*` **không đếm được** (chưa có `entity.md`/`items.md` local); Level 7+ và nhân vật ngoài 3 codex thuộc phần "99% chưa có" — không dựng giả.
- Record đặc biệt: `REL.LUC_TRAM.SYVIAL.BASELINE` tự khai báo **không có canon** (quan hệ Lục Trầm↔Syvial chưa từng được thiết lập) → `truth_status = UNKNOWN`, giữ nguyên trạng thái chưa thiết lập, cấm suy diễn quan hệ thay thế.
- Chi tiết đầy đủ: `canon_source_manifest.json` (thư mục này) và báo cáo Gate 1.

---

## 6. Danh sách KHÔNG ĐƯỢC PHÁ (12 hợp đồng hiện hữu)

### Semantics + API của KnowledgeContextEngine

1. **Chữ ký @JvmStatic** `build(Context, String, String, String): String`, `traceRecord(Context, String)`, `recordIds(Context)` (:52, :59, :71). `traceRecord` là hiện thân của hợp đồng truy vết trong `KNOWLEDGE_SOURCE_MAP.md`; `recordIds` được validator dùng làm fail-fast.
2. **Định dạng packet** `[KNOWLEDGE_PACKET v1]` và dòng metadata `source=...; authority=...; mutability=...; why=...` (:140-160) — writer/auditor prompt sau patch và benchmark đều dựa trên hợp đồng này. Đổi format = phải đổi đồng bộ patch + benchmark.
3. **Thứ tự pipeline chọn record** trong `Builder.build()` (:133-160) và ngữ nghĩa: mandatory (priority ≤ 30) không bao giờ bị cắt bởi ngân sách optional; references chỉ kéo record priority ≤ 55; trùng id khi load DB → `require` fail.
4. **Lý do chọn (`why`)** cho từng record — retrieval trace duy nhất hiện có; thiết kế §6.2 yêu cầu giữ và mở rộng, không bỏ.

### Validator + Reducer + commit

5. `KnowledgeLocalValidator.validate` **luôn chạy** trên mọi lượt non-meta sau patch (first pass + repair pass), mọi issue đều `severity = "hard"`; hành vi fail-fast khi DB lỗi (validator :16-17) phải giữ.
6. `StoryContinuityReducer.apply(before, after, action)` chỉ ghi từ **state đã validated**, chạy tại đúng điểm chèn sau `candidateState` và chỉ khi `!meta`; đầu ra mang `schemaVersion = 1`, `source = "deterministic-state-reducer"`, `lastTurn` (:45-47). Reducer **không phải AI summarizer** — cấm thay bằng tóm tắt model.
7. Test hiện hữu phải giữ xanh: `StoryContinuityReducerTest` (2 test), `TurnCoordinatorTest` (4 test: pending không commit 2 lần, batch fail nguyên tử, gameplay+time commit nguyên tử, roll fail rollback time), `GameCoreFacadeContractTest`.
8. `TurnCoordinator.commit` nguyên tử qua `StateReducer.executeAll` + chống trùng `completedTurnIds`; `processRule` **không gọi Gemini**; `processValidatedCandidate` chỉ commit delta đã qua validated (prose/JSON của model không bao giờ thành storage trực tiếp — nguyên tắc tại comment `GameCoreFacade.kt:151-154`).

### Điểm tích hợp của patch-knowledge-context-builder

9. 5 điểm can thiệp và anchor của chúng (mục 1, đường B). Đặc biệt: anchor first-pass yêu cầu đúng chuỗi `JSONArray hardIssues = hardAuditIssues(audits);` + dòng `appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));` (tức **patch-rejected-op-repair-final phải chạy trước** patch knowledge); anchor commit yêu cầu đúng dòng `JSONObject state = candidateState;`. Đổi tên/thứ tự các hàm `auditScopeCanon`/`runAudit`/`writerPrompt`/`GameBridge` trong bất kỳ patch nào trước đó đều làm patch knowledge raise.
10. `compactDriveCanon`/`compactCaoMinhCanon` (MainActivity :371/:401) được **giữ làm dead code tương thích benchmark/audit** theo comment tại patch-knowledge-context-builder.py:7-8 — không xoá khi dọn source.
11. Các patch provider dùng `replace_once` yêu cầu **đúng 1 match** (ví dụ anchor field `private volatile int lastGeminiWorker = -1;` của matrix patch). Sửa MainActivity.java phải giữ nguyên các anchor này hoặc sửa patch tương ứng trong cùng thay đổi.
12. Benchmark `benchmark-knowledge-context.py` là regression gate nhanh hiện có (assert p95 ≤ 3400, :332) — giữ chạy được; nhưng phải nhớ nó **mô phỏng**, không thực thi Kotlin (mục 2, hàng 9).

---

## 7. Phát hiện rủi ro (cần quyết trước các gate sau)

### 7.1. `patch-knowledge-engine-source.py` đã LỆCH ANCHOR trên cây PR #668 — đã kiểm chứng bằng máy

Patch này định: (a) đổi parser `references` sang `rawStrings`, (b) thay `tokenizeTags()` bằng quét registry `tagIndex` + thêm lookup `WRITING.DIALOGUE`, (c) xoá heuristic `presentActors.size == 1 && turn <= 3` khỏi `isMainCampaignSeparated`.

| Anchor | Kết quả |
| --- | --- |
| `references = strings(json.optJSONArray("references"))` (engine :97) | **MATCH** |
| Block devil-trigger chứa `CHAR.KAI.DEVIL_TRIGGER` + `tokenizeTags(actionText).forEach...` | **NO MATCH** — engine hiện tại đã là bản swap (devil trigger chỉ `CHAR.SYVIAL.DEVIL_TRIGGER` khi Syvial present, :215) → patch raise `RuntimeError("Structured registry lookup anchor not found")` |
| `return iris.contains("separated") || ...` | **NO MATCH** — engine hiện tại dùng biến `lucTram` (:381); heuristic turn ≤ 3 vẫn còn nguyên :382 |

Hệ quả: nếu chuỗi build chạy patch này sau khi engine đã được sửa tay ở PR #668, **build gãy tại patch này**; và cơ chế tag data-driven (b) — tiền lệ trực tiếp cho Gate 2 của CR2 — đang nằm chết trong patch. Cần quyết: cập nhật patch theo anchor Cao Minh, hay chuyển hẳn thay đổi của nó vào source.

### 7.2. Cây nguồn thiếu định nghĩa mà nó tham chiếu

- `requireGameCore()` có 6 call site (MainActivity :1023-1053) nhưng **không có định nghĩa** trong cây nguồn — do patch chèn. Facade còn được các patch gọi bằng API không tồn tại trong `GameCoreFacade.kt` hiện tại (`startCombatState`, `processItemAction`, `processCombat...`).
- Kết luận: **mọi thay đổi trên MainActivity/facade phải được kiểm chứng trên artifact SAU chuỗi patch**; không kết luận "đã chạy" chỉ từ source.

### 7.3. Thiếu workflow — thứ tự chuỗi patch toàn cục không có trong repo

Không có `.github/workflows/` trong cây; không có manifest thứ tự patch. Chỉ kiểm chứng được quan hệ phụ thuộc **từng cặp** patch qua anchor/comment (mục 1, 6.9); **không khẳng định được** vị trí của patch-knowledge-* trong toàn chuỗi ~99 patch. Cần lấy thứ tự build thật từ cấu hình của tác giả trước Gate 2.

### 7.4. Cờ provenance chờ reviewer/tác giả (từ Gate 1)

- `STORY.MAIN.OBJECTIVE`: Source Map trỏ `05_TextGame/STORY/MAIN_STORY.md §3`, DB trỏ `CAO_MINH_CODEX §19.3` → cờ `PROVENANCE_MISMATCH`.
- `STORY.MAIN.SEPARATION` và `CHAR.LUC_TRAM.THIEN_CO_BACH_KIM_KIEM_KHAI`: có record nhưng không có hàng riêng trong Source Map → cờ `NOT_IN_SOURCE_MAP` (riêng `DAI_MA_DAU` ngoài map là hợp lệ — quyết định tác giả 2026-10-24, cần bổ sung map khi đồng bộ).
- `CHAR.CAO_MINH.VAN_KHO_GIOI_CHI`: source trỏ `Kai_Codex.docx` đã deprecated; tính hợp lệ dựa vào quyết định tác giả 2026-10-24 (precedence #1). Cờ `OPEN_CẦN_TÁC_GIẢ`: hợp nhất Vạn Khố Giới Chỉ ↔ Vạn Tàng Giới (`CAO-EQP-VANTANG-01`) chưa chốt.
- 2 tên record lệch map↔DB nên chuẩn hoá khi lên v2: `CHAR.CAO_MINH.HUYET_MA_24_TRAM` (map) = `CHAR.CAO_MINH.HUYET_MA_NHI_THAP_TU_TRAM` (DB); `CHAR.CAO_MINH.ARMOR` (map) = `CHAR.CAO_MINH.HUYET_MA_CHIEN_KHAI` (DB).

### 7.5. Lệch dữ liệu phụ trợ

- `benchmark-knowledge-context.py` đọc `kai-codex.txt` làm CAO_MINH_CANON (:10) — tên file legacy còn giữ; nội dung đã là codex Cao Minh. Không ảnh hưởng kết quả nhưng dễ gây hiểu nhầm khi audit.
- API key Gemini nhúng vào BuildConfig lúc build (app/build.gradle:28-32) — khớp cảnh báo bảo mật §9.5 của thiết kế.

---

## 8. Record v2 — những gì đã thay đổi ở đợt baseline này

`knowledge_db.json` đã được nâng từ schemaVersion 1 lên **Record v2** theo đúng mapping
Gate 1 (script `~/workspace/cr2_work/upgrade_knowledge_db_v2.py`, có validate sau biến đổi):

- Root: `schemaVersion` 1 → 2.
- Mỗi record **thêm**: `schema_version = 2`, `universe_id = "BACKROOMS_MAIN"`, `entity_id`
  (theo mapping), `truth_status` (54 `CONFIRMED`; `REL.LUC_TRAM.SYVIAL.BASELINE` = `UNKNOWN`),
  `knowledge_visibility` (CHARACTER/RELATIONSHIP → `ACTOR_AWARE`; WORLD/LEVEL/ENTITY/ITEM →
  `WORLD`; GAME_RULE/WRITING → `META`; STORY → `WORLD` — điểm suy luận ngoài danh sách gốc,
  chờ reviewer xác nhận).
- `source` **thêm**: `document_id` (suy từ `source.document` hiện có; = `null` cho record
  khai báo không nguồn) và `content_sha256` — **chỉ** 15 record trỏ 2 codex local mới có hash;
  40 record còn lại `content_sha256 = null` + `review_status = "PENDING_SOURCE_SNAPSHOT"`.
- **Giữ nguyên 100%** `id, domain, kind, text, source.document, source.anchor, authority,
  mutability, priority, tags, references, affordances` của cả 55 record (đã assert bằng máy).
- Chưa làm ở đợt này (thuộc gate sau): `activation`, `spoiler_policy`, `aliases` tách riêng,
  `hard_rule` dạng cấu trúc, typed edges cho `references`, chuẩn hoá enum `authority`,
  `approved_revision` — không field nào trong số này được bịa ở Gate 1.
- Engine v1 hiện hành đọc DB theo từng key nên field v2 thêm vào không phá parser hiện tại;
  không có code nào trong cây khóa cứng `schemaVersion` của DB (đã grep kiểm chứng).

Chi tiết nguồn và độ phủ nằm ở `canon_source_manifest.json` (cùng thư mục):
22 nguồn (2 codex local + knowledge_db v2 + 18 tài liệu Source Map `SOURCE_UNAVAILABLE`
+ 1 nguồn deprecated `Kai_Codex.docx`) kèm coverage block
(IMPORTED 54 / PENDING_REVIEW 55 / KNOWN_MISSING 14 hàng map / UNKNOWN_TOTAL cho entity-item).

---

## 9. Phạm vi chưa kiểm chứng (nói rõ để không suy đoán)

- Chưa build APK, chưa chạy test Kotlin, chưa chạy benchmark Python trong các lượt audit này (chỉ đọc code của chúng).
- Chưa xác minh thứ tự toàn cục của chuỗi patch và artifact Java sau build (mục 7.3).
- Chưa đọc toàn bộ ~99 patch; chỉ đọc đầy đủ các patch knowledge/provider liên quan và trích quan hệ của ai-orchestrator / three-action-bridge / conditional-audit / audit-validated-risk / rejected-op-repair-final ở mức anchor/print.
- Nội dung `drive-canon.txt` / `kai-codex.txt` chỉ được đối chiếu gián tiếp qua benchmark và hằng trong MainActivity.
- 40/55 record chưa có `content_sha256`/`approved_revision` cho tới khi snapshot Drive được thực hiện (ingest A).
