# The BACKROOMS — Drive Source Map for Runtime Knowledge

Status: SOURCE MAP / implementation prerequisite

This file maps runtime facts to their authoritative Google Drive source. It is intentionally independent from `drive-canon.txt`, current prompts, legacy compact canon, and old patches. Those implementation artifacts are audit inputs only.

## Canon Runtime 2.0 (CR2)

Bản đồ nguồn này là đầu vào provenance của **Canon Runtime 2.0** — kiến trúc truy xuất Canon theo scene (scene-first, qua các cổng scope/ID/alias/edge/affordance) thay cho việc nạp tri thức theo từ khoá. Mọi record trong `knowledge_db.json` (schemaVersion 2) đều truy vết về tài liệu trong bản đồ này qua `source.document`/`source.anchor`, đúng thứ tự authority ở mục dưới. Người hoặc mô hình làm sau cần đọc thêm, theo thứ tự: [`CANON_RUNTIME_2_README.md`](CANON_RUNTIME_2_README.md) (hướng dẫn tích hợp Canon + quy trình ingest 4 bước + các bất biến bắt buộc), [`docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md`](../docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md) (tài liệu thiết kế gốc đầy đủ), và [`canon_source_manifest.json`](canon_source_manifest.json) (manifest nguồn: hash, trạng thái duyệt, record_ids của từng nguồn). Trạng thái triển khai theo Gate nằm trong README trên; kết quả đo nằm ở `CANON_RUNTIME_2_EVALUATION.md`.

## Authority order

1. Latest explicit user instruction.
2. Current Text Game rules and current campaign state for runtime facts.
3. Current Character Codex for immutable character identity, personality core, abilities, equipment, knowledge limits, relationship baseline, address rules, and hard locks.
4. Current World canon for world/level/entity/item facts.
5. Current story/continuity for mutable campaign state.
6. Writing rules for prose/dialogue/POV/knowledge boundaries.
7. Reference/benchmark material only where its own source says it applies.

`UNKNOWN`, `OPEN`, `CHƯA KHÓA`, or equivalent remains unknown. The runtime database must not reconcile it by inference.

## Drive audit notes

Directly opened from Google Drive before implementation:

- `00_RULES/00_RULES_BOOT.md`
- `05_TextGame/RULES/TEXT_GAME_RULES.md`
- `05_TextGame/RULES/GAME_MASTER_RULES.md`
- `05_TextGame/STORY/MAIN_STORY.md`
- `Asset/01_Character/CAO_MINH_CODEX.md` (replaces `02_CHARACTERS/Kai_Codex.docx`; character id `cao-minh`)
- `Asset/01_Character/LUC_TRAM_CODEX.md` (replaces `02_CHARACTERS/Iris_Codex.docx`; character id `luc-tram`)
- `02_CHARACTERS/Syvial_Codex.docx`
- `01_WORLD/world.md`
- `01_WORLD/level.md`
- `01_WORLD/entity.md`
- `01_WORLD/items.md`
- `00_RULES/worldcodex.md`
- `00_RULES/CONVERSATION.MD`
- `00_RULES/WayOfWriting.md`
- `00_RULES/Vivid_Verbs_Guide.md`
- `00_RULES/Supernatural_Horror_Craft_Guide.md`
- `00_RULES/Intimacy_Writing_Guide.md`
- `00_RULES/Dialogue_Benchmarks.md`
- `00_RULES/Những điều bắt buộc không được làm khi viết tiểu thuyết.md`
- `00_RULES/HuongDan.txt`, because the boot/writing router delegates supplemental prose behavior to it.

The Drive folder `05_TextGame/SAVE` was opened directly and currently returned no files. Searches for the exact three required save names also returned no Drive result. Therefore no campaign state is imported from a guessed or legacy source. Runtime migration may preserve existing local APK save data, but the database seed treats the Drive save state as absent rather than inventing it.

## Record contract

Every important runtime record must expose:

- `id`: stable, namespaced identifier.
- `domain`: CHARACTER / RELATIONSHIP / WORLD / LEVEL / ENTITY / ITEM / WRITING / STORY / GAME_RULE.
- `kind`: runtime-card, identity, ability, equipment, hard-lock, level, entity, item, writing-rule, objective, event, discovery, promise, thread, knowledge-edge, etc.
- `text`: compact authoritative payload used in a context packet.
- `source.document`: exact Drive source path/title.
- `source.anchor`: source section/code when available.
- `authority`: authority class.
- `mutability`: IMMUTABLE / BASELINE / RUNTIME_MUTABLE.
- `priority`: hard-canon ordering for budget selection.
- `tags`: structured lookup tags.
- `references`: direct record IDs.
- `affordances`: scene-driven capability lookup tags.

Runtime traceability is therefore:

`GM context line -> KnowledgeRecord.id -> source.document + source.anchor`.

## Character source map

| Runtime ID | Source | Anchor | Mutability | Notes |
| --- | --- | --- | --- | --- |
| `CHAR.CAO_MINH.RUNTIME_CORE` | `CAO_MINH_CODEX.md` (R17+R18) | `CAO-QUICK-01`, `CAO-PER-01`, `CAO-DLG-01`, `CAO-WEAK-01`, `CAO-ACTION-LOCK-01` | IMMUTABLE | Minimal present-character card, not full ability dump. Character id `cao-minh` (replaces `kai`). |
| `CHAR.CAO_MINH.IDENTITY` | `CAO_MINH_CODEX.md` | `CAO-ID-01` | IMMUTABLE | Cao Minh / Vạn Giới Ma Tôn; no sect or faction; origin world and true age OPEN; already the strongest of his origin world before the Backrooms. |
| `CHAR.CAO_MINH.VAN_QUY_MA_TAM` | `CAO_MINH_CODEX.md` | `CAO-CORE-VQM-01` | IMMUTABLE | Replaces SPARDA_CORE. Đạo Cơ / Ma Hải; no established intrinsic cap; do not add invented depletion/cooldown/backlash. Origin (Vạn Quỷ Ma Tôn transmission) is KNOWLEDGE LOCK. |
| `CHAR.CAO_MINH.VAN_QUY_MA_THAN` | `CAO_MINH_CODEX.md` | `CAO-VQMS-01` | IMMUTABLE | Replaces DEVIL_TRIGGER. Full release of bản nguyên; keeps consciousness/personality; no invented berserk state, intrinsic duration cap, cooldown, or backlash. |
| `CHAR.CAO_MINH.HUYET_MA_24_TRAM` | `CAO_MINH_CODEX.md` | `CAO-ULT-HUYETMA24-01` | IMMUTABLE | Huyết Ma Nhị Thập Tứ Trảm: exact 24 sword strikes while external time is fully stopped, under the codex conditions. |
| `CHAR.CAO_MINH.HUYET_MA_KIEM` | `CAO_MINH_CODEX.md` | `CAO-EQP-HUYET-MA-KIEM-01` | IMMUTABLE | Replaces WHITE_WRAITH. Bản mệnh greatsword; kiếm thể / ngự kiếm / Huyết Sát Ma Khí; self-restores via Vạn Quỷ Ma Tâm. |
| `CHAR.CAO_MINH.ARMOR` | `CAO_MINH_CODEX.md` | `CAO-EQP-HUYET-MA-KHAI-01` | IMMUTABLE | Huyết Ma Chiến Khải (bản mệnh Ma Khải) and linked parts: Ma Trảo / Huyết Ấn, Đạp Hư Ma Văn. |
| `CHAR.CAO_MINH.VAN_KHO_GIOI_CHI` | author rename decision (2026-10-24) + `CAO_MINH_CODEX.md` | (old `KAI-EQP-OMNIVAULT-01` mechanism); cf. `CAO-EQP-VANTANG-01` | IMMUTABLE | Vạn Khố Giới Chỉ is the renamed Omnivault, kept for Cao Minh with its runtime feature set intact: inanimate-only storage; 3 scan/copy slots and codex restore constraints. NOTE: the Cao Minh codex also locks a separate ring, Vạn Tàng Giới (`CAO-EQP-VANTANG-01`), with a narrower feature set (storage + Phản Bổn only). Whether the two rings merge is an OPEN author decision; until then the runtime keeps the Vạn Khố Giới Chỉ feature set. |
| `CHAR.LUC_TRAM.RUNTIME_CORE` | `LUC_TRAM_CODEX.md` (R05+R06) | section `00 · HỒ SƠ TRUY XUẤT NHANH`, `LUC-READ-FIRST-R04` | IMMUTABLE | Present-character card. Character id `luc-tram` (replaces `iris`). Lục Trầm is a righteous-path sword cultivator (Thiên Kiếm Môn), not a modern soldier or scout station. |
| `CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM` | `LUC_TRAM_CODEX.md` | section `07 · THIÊN KIẾM LINH TÂM` | IMMUTABLE | Combat perception root: reads trajectories, balance shifts, linh lực fluctuation and instability points. Hard limits: no mind-reading, no good/evil detection, no Entity identification, no Backrooms-rule knowledge. |
| `CHAR.LUC_TRAM.TICH_QUANG` | `LUC_TRAM_CODEX.md` | section `08 · TỊCH QUANG KIẾM` | IMMUTABLE | Bản mệnh spirit sword; clean line, fast angle changes, joint/balance cuts, phá pháp, force redirection, rear-guard protection. No infinite self-regeneration; serious damage needs real repair. |
| `CHAR.LUC_TRAM.THIEN_KIEM_THAT_THUC` | `LUC_TRAM_CODEX.md` | section `11 · THIÊN KIẾM THẤT THỨC`, `12 · THIÊN KIẾM ĐỊNH GIỚI` | IMMUTABLE | Sword system incl. Nhất Tuyến Phá Vọng, Bạch Hồng Quán Nhật, Tịch Quang Phản Kiếm, Vạn Kiếm Quy Tâm; ultimate Thiên Kiếm Định Giới per codex. |
| `CHAR.LUC_TRAM.COMBAT` | `LUC_TRAM_CODEX.md` | section `10 · PHONG CÁCH CHIẾN ĐẤU` | IMMUTABLE | Strongest once the opponent commits: reads attack lines, breaks rhythm, redirects force, counter-strikes; natural formation anchor / rear guard. Power scale hard lock: below Cao Minh overall; do not competence-suppress or power-equalize. |
| `CHAR.LUC_TRAM.UNKNOWN` | `LUC_TRAM_CODEX.md` | sections `15 · TÁI NGỘ`, `23 · CÁC TRƯỜNG OPEN`, `LUC-READ-FIRST-R04` | IMMUTABLE | Preserve OPEN fields: true age, detailed cảnh giới, reunion place/time/conditions, memory-recovery possibility, Lục Trầm↔Syvial address (no shared canon yet). |
| `CHAR.SYVIAL.RUNTIME_CORE` | `Syvial_Codex.docx` | `SYVIAL-QUICK-01`, `SYVIAL-OVERVIEW-01`, `SYVIAL-YANDERE-01`, `SYVIAL-REL-KAI-01`, `SYVIAL-ACTION-LOCK-01` | IMMUTABLE | UR+, lucid/socially capable, heavy yandere toward Cao Minh without random murder or loss of tactical intelligence. |
| `CHAR.SYVIAL.LUCIFER_CORE` | `Syvial_Codex.docx` | `SYVIAL-CORE-LUCIFER-01` | IMMUTABLE | Infinite demon power; no invented intrinsic energy meter/cooldown. |
| `CHAR.SYVIAL.DEVIL_TRIGGER` | `Syvial_Codex.docx` | `SYVIAL-DT-01` | IMMUTABLE | Does not remove control or add an intrinsic duration/cooldown absent from codex. |
| `CHAR.SYVIAL.GODKILLER` | `Syvial_Codex.docx` | `SYVIAL-EQP-GODKILLER-01` | IMMUTABLE | Purely mechanical greatsword; do not turn into gun/gunblade. |
| `CHAR.SYVIAL.GODKILLER_OVERRIDE` | `Syvial_Codex.docx` | `SYVIAL-ULT-GKO-01` | IMMUTABLE | Exact 24 cuts in fully stopped external time under codex conditions. |
| `CHAR.SYVIAL.COMBAT` | `Syvial_Codex.docx` | `SYVIAL-COMBAT-01`, `SYVIAL-STYLE-COMBAT-01`, `SYVIAL-WEAK-01` | IMMUTABLE | High-tier sword combat, assault/control/counter/finish; do not competence-suppress. |

## Relationship and address source map

| Runtime ID | Source | Anchor | Mutability | Canon baseline |
| --- | --- | --- | --- | --- |
| `REL.CAO_MINH.LUC_TRAM.BASELINE` | `LUC_TRAM_CODEX.md` + `CAO_MINH_CODEX.md` | LUC sections `03`, `13`, `16`, `17`, `19`; CAO `15.4` | BASELINE | History only: from public enemies to trusted allies with mutual feelings before the Backrooms. After Lôi Thiên Vực she was revived by his đạo hạnh, began nhập ma, and FORGOT everything about him. This baseline is history, NOT her current knowledge; current state is set by continuity. Finding her is his priority #1. |
| `ADDR.LUC_TRAM.CAO_MINH` | `LUC_TRAM_CODEX.md` + author ruling | LUC section `20 · XƯNG HÔ` + post-revival state | IMMUTABLE | Post-revival default: Lục Trầm addresses Cao Minh as `các hạ` (righteous-path courtesy to an unidentified man; she does not remember him). Cao Minh -> Lục Trầm: calls her `nàng`, self-addresses `Bổn Tôn`. Historical registers (`ma đầu`, `Lục tiên tử`, bare names) belong to pre-amnesia phases and must not be applied post-revival without continuity. |
| `REL.LUC_TRAM.SYVIAL.BASELINE` | — | — | OPEN | No shared canon: Lục Trầm and Syvial have never met in locked canon. Do NOT carry over the old Iris↔Syvial friendship/rivalry baseline. Establish only via continuity. |
| `REL.CAO_MINH.SYVIAL.BASELINE` | `Syvial_Codex.docx` | `SYVIAL-REL-KAI-01` (partner renamed) | BASELINE | Syvial's feelings are immutable; how far Cao Minh reciprocates is continuity-controlled. |
| `ADDR.SYVIAL.CAO_MINH` | `Syvial_Codex.docx` | `SYVIAL-REL-KAI-01` (partner renamed) | IMMUTABLE | Syvial -> Cao Minh: `em`, calls `anh` or `Cao Minh`; `Anh Cao Minh` may be emphatic/teasing. Cao Minh -> Syvial: per Cao Minh register lock (see `CHAR.CAO_MINH.RUNTIME_CORE` / `CAO-DLG-01`). `cục cưng` only if continuity has locked a long-term romantic relationship, not the default. |
| `REL.CAMPAIGN.*` | runtime continuity | relationship-change events | RUNTIME_MUTABLE | Campaign delta overlays baseline without mutating codex records. |

## World source map

| Runtime ID | Source | Anchor | Mutability |
| --- | --- | --- | --- |
| `WORLD.CORE` | `01_WORLD/world.md` | `BACKROOMS-WORLD-CORE-R2` / 0.1–0.5 | IMMUTABLE |
| `LEVEL.00` … `LEVEL.06` | `01_WORLD/level.md` | corresponding Level section | IMMUTABLE |
| `ENTITY.GLOBAL_HARD_LOCK` | `01_WORLD/entity.md` | 0.6 | IMMUTABLE |
| `ENTITY.*` | `01_WORLD/entity.md` | stable entity codes such as `ENT-1A`, `ENT-2C`, `ENT-R01` | IMMUTABLE |
| `ITEM.GLOBAL_HARD_LOCK` | `01_WORLD/items.md` | 0.7–0.8 | IMMUTABLE |
| `ITEM.ALMOND_WATER` | `01_WORLD/items.md` | 8.1 | IMMUTABLE |
| `ITEM.GREEK_FIRE` | `01_WORLD/items.md` | 8.2 | IMMUTABLE |
| `ITEM.LIQUID_PAIN` | `01_WORLD/items.md` | 8.3 | IMMUTABLE |
| `ITEM.*` | `01_WORLD/items.md` | stable item/resource section | IMMUTABLE |

Adding future levels/entities is append-only: add records and references/tags; the GM prompt must not require a code branch for each new Level/Entity.

## Game rules and story source map

| Runtime ID | Source | Anchor | Mutability |
| --- | --- | --- | --- |
| `GAME.TEXT.CORE` | `05_TextGame/RULES/TEXT_GAME_RULES.md` | 1–12 | IMMUTABLE |
| `GAME.GM.FAIRNESS` | `05_TextGame/RULES/GAME_MASTER_RULES.md` | 1–15 | IMMUTABLE |
| `STORY.MAIN.PREMISE` | `05_TextGame/STORY/MAIN_STORY.md` | 2 | IMMUTABLE |
| `STORY.MAIN.OBJECTIVE` | `05_TextGame/STORY/MAIN_STORY.md` | 3 | BASELINE |
| `STORY.MAIN.COMMS_INITIAL` | `05_TextGame/STORY/MAIN_STORY.md` | 4 | BASELINE |
| `STORY.MAIN.LUC_TRAM_EXISTENCE` | `CAO_MINH_CODEX.md` + `LUC_TRAM_CODEX.md` | CAO `19`; LUC `14`, `15` | BASELINE |
| `STORY.MAIN.SYVIAL_EXISTENCE` | `05_TextGame/STORY/MAIN_STORY.md` | 5 | BASELINE |
| `STORY.CONTINUITY.EVENT.*` | runtime reducer | committed gameplay events | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.OBJECTIVE.*` | runtime continuity | objective state | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.DISCOVERY.*` | runtime continuity | deterministic discovery event | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.PROMISE.*` | runtime continuity | explicit validated social event | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.THREAD.*` | runtime continuity | unresolved-thread state | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.KNOWLEDGE.*` | runtime continuity | character knowledge ownership edge | RUNTIME_MUTABLE |

The required Drive save files are currently absent from the Drive folder, so there is no source-authorized row that claims a current `ACTIVE_RUN` value or current campaign facts. Local saves are migration input only, not Drive canon.

## Writing source map

| Runtime ID | Source | Anchor/use |
| --- | --- | --- |
| `WRITING.DIALOGUE` | `00_RULES/CONVERSATION.MD` | `RULES.DIALOGUE`; sole authority for dialogue generation/audit. |
| `WRITING.ADDRESS` | `00_RULES/CONVERSATION.MD` + character relationship sections | Address is relationship canon, never guessed. |
| `WRITING.KNOWLEDGE_BOUNDARY` | `00_RULES/worldcodex.md`, forbidden rules, `CONVERSATION.MD` | No backstage/other-character knowledge leak. |
| `WRITING.COMPETENCE` | forbidden rules + `worldcodex.md` + GM rules | No sudden stupidity or hidden competence. |
| `WRITING.PROSE_POV` | `WayOfWriting.md` + relevant hard bans | POV/information discipline. |
| `WRITING.VIVID_VERBS` | `Vivid_Verbs_Guide.md` | prose-level verb selection only, not dialogue generation. |
| `WRITING.HORROR` | `Supernatural_Horror_Craft_Guide.md` | horror/suspense distribution; never withhold competence. |
| `WRITING.INTIMACY` | `Intimacy_Writing_Guide.md` | relationship/intimacy reference, cannot override canon/address/consent. |
| `WRITING.DIALOGUE_BENCHMARK` | `Dialogue_Benchmarks.md` | post-draft reference only, never a generation template. |
| `WRITING.GUIDE_SUPPLEMENT` | `HuongDan.txt` | supplemental emotion/action around dialogue; delegated dialogue authority remains `CONVERSATION.MD`. |

## Retrieval rules derived from sources

1. Direct stable IDs first.
2. Explicit relationship edges second.
3. Current state, party presence, level and structured tags third.
4. Scene affordance -> relevant present-character capability next.
5. Semantic retrieval is a last resort only when structured lookup is insufficient.
6. Present characters always receive a compact runtime core. They do not need to be named by player input.
7. Scene-driven capability examples:
   - attack-line reading/formation anchor/counter-opening problem + Lục Trầm present -> `CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM` and `CHAR.LUC_TRAM.TICH_QUANG`.
   - direct combat/threat/control/frontline problem + Syvial present -> `CHAR.SYVIAL.COMBAT` and, only when relevant, weapon/override modules.
8. Capability retrieval never implies omniscience, invented limits, or automatic action. Followers retain autonomy.
9. Raw dialogue log is a small recency buffer only. Long-term memory is structured continuity.
10. Context budget priority: hard canon -> current state/scene -> active story -> present runtime cards -> relationship/address -> relevant ability/knowledge limits -> relevant Level/Entity/Item -> flavor.

## Baseline implementation audit link

The current build-time runtime is patch-generated. `patch-ai-orchestrator.py` extracts broad string sections from `drive-canon.txt` and `kai-codex.txt` (file names unchanged in this pass; `kai-codex.txt` content is now the Cao Minh operational codex and the former gun-skills addendum file now carries Cao Minh's automatic sword skills — file renames are a separate follow-up); `compactStateForPrompt` copies most legacy state and retains six recent log entries; routing uses keyword helpers such as dialogue/combat/item/entity plus limited presence checks. `patch-conditional-audit.py` already skips the AI critic below a risk threshold, but critic calls duplicate a broad canon/state slice.

Those files are not a canon source for the new database. They are retained only as OLD-system input for benchmark comparison.

---

## Addendum 2026-10-24 — Drive snapshot (Gate 1 completion, PR #668)

Toàn bộ tài liệu nguồn còn tồn tại trên Google Drive đã được snapshot vào `android-apk/canon_sources/` kèm SHA-256 trong `canon_source_manifest.json` (manifest v2). Các thay đổi so với bản đồ gốc:

- Thư mục nguồn trên Drive hiện tại là `Novel/Rules` (tương ứng `00_RULES/*` trong bản đồ) và `Novel/Asset/01_Character | 02_Level | 03_Entity`. `01_WORLD/entity.md` nay là `Asset/03_Entity/Entity.md` (kiểm chứng bằng nội dung); `01_WORLD/world.md`/`level.md` được thay bằng `Asset/02_Level/BACKROOMS_WORLD.md` ở cấp thư viện (một số anchor level cũ — Level 4, Level 6, Parking Zone — không còn xuất hiện trong tài liệu hiện hành, đã đánh dấu `ANCHOR_UNVERIFIED`).
- **Không còn trên Drive**: `05_TextGame/RULES/TEXT_GAME_RULES.md`, `05_TextGame/RULES/GAME_MASTER_RULES.md`, `05_TextGame/STORY/MAIN_STORY.md`, `01_WORLD/items.md` (thư mục TextGame/RULES và TextGame/STORY trống). Các record phụ thuộc giữ `PENDING_SOURCE_SNAPSHOT` với lý do tường minh; 3 hàng `STORY.MAIN.PREMISE`, `STORY.MAIN.COMMS_INITIAL`, `STORY.MAIN.SYVIAL_EXISTENCE` chưa tạo được record.
- Đã tạo 11 record còn thiếu từ nguồn thật; DB hiện 66 record (65 CONFIRMED / 1 UNKNOWN).
- 3 Google Doc chưa snapshot được vì connector Google Docs chưa kết nối: `DIEP_MINH_CODEX`, `HE_THONG_CANH_GIOI_TU_LUYEN_CANON`, `CANON_REGISTRY_R01`.
- Canon mới chưa ingest thành record: `Asset/03_Entity/NEW_ENTITIES_CODEX_R01.md`, codex Lucia/Trác Lâm/Diệp Minh — chờ chỉ đạo của tác giả.
