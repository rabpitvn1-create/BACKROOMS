# Lục Trầm canon import — Drive CURRENT R06

Source: [LUC_TRAM_CODEX.md](https://drive.google.com/file/d/1_ivAnDOeKrUPzJRmlGFC-HQmeffcnPpF/view), modified 2026-10-11 (Drive metadata). V2 comparison: [Lục_Trầm_Codex.md](https://github.com/rabpitvn1-create/BACKROOMsV2/blob/main/android-apk/app/src/main/assets/canon/L%E1%BB%A5c_Tr%E1%BA%A7m_Codex.md), R05. The full current source is mirrored verbatim at `app/src/main/assets/canon/LUC_TRAM_CODEX.md`.

## R06 supersedes these V2 assumptions

- Pre-Backrooms: Cao Minh and Lục Trầm became trusted allies and had mutual feelings, despite public hostility.
- Their bond did not first develop after meeting in Backrooms. At Lôi Thiên Vực she died saving the situation and Cao Minh's transfusion restored her life.
- After revival she begins a gradual demonic transformation and specifically **forgets everything about Cao Minh**, not every memory. Past resentment, love and nicknames cannot leak into her post-revival POV.
- The place named Kiếm Các and Táng Kiếm Cốc in the historic event is the same incident. This does not identify the Thiên Kiếm Môn departments Kiếm Các / Khí Các with the scene.
- The old prologue's drinking-on-mountain/height scene was explicitly revoked. The canonical source must win over old embedded prologue text.
- The author-only secret about Vô Lượng and Diệp Minh must not become characters' knowledge.

## Integration boundary

- `KnowledgeContextEngine` now retrieves a compact R06-aware profile, memory firewall and relationship history when `luc_tram` is actually present or is explicitly mentioned. **An explicit mention is not encounter confirmation.**
- User-authorized override (2026-10-11): Lục Trầm is registered outside Party. Only physical actions at Level 4+ roll 25/10000 (0.25%); Core adds her to Party after confirmed success, or stores joinPending when full. Lucia stays independent.
- No encounter before Level 4, no guaranteed meeting, no pre-existing current hostility, no V2 Poker Dice formula. The Level 4+ 0.25% mechanic was explicitly requested after R06. The after-revival equipment status, memories and dialogue remain governed by continuity. Unique skill multipliers are not invented; the combat fallback is an ordinary companion attack.
- The file contains writer-only details; no automatic retcon of Cao Minh, world or existing continuity. The full document is for canon review, while compact GM retrieval obeys R06 secrecy and epistemic distinctions.

## Combat ability integration (author-approved implementation)

The six entries in `CompanionSkillCatalog.lucTram` are now real authoritative CombatRuntime actions, not narration-only labels. Tactical mapping intentionally reuses existing combat abstractions, not new canon percentages or magic powers:

- `Thiên Kiếm Linh Tâm`: during party READ, add 1 opening beyond the ordinary READ opening.
- `Nhất Tuyến Phá Vọng`: focused strike at CLOSE or with pre-existing opening; existing Accuracy/Evasion applies, armor ignored by 20% as a **gameplay balance parameter** (not a statement of canon).
- `Bạch Hồng Quán Nhật`: when outside CLOSE without an opening, move one existing range band and attack normally; no teleport across Level geometry.
- `Vạn Kiếm Quy Tâm`: party EVADE raises cover to HARD; GUARD builds one more opening. No omnipotent protection.
- `Tịch Quang Phản Kiếm`: one counter against a missed normal Entity response, only when the sword is explicitly equipped. Uses standard Accuracy/Evasion and damage, no guaranteed hit.
- `Thiên Kiếm Định Giới`: requires the sword, opening >= 2 and an unused slot per wave. Uses Lục Trầm's attack action to set HARD cover and opening=3 without dealing direct damage. Does not rewrite World/Level geometry.

The physical Tịch Quang and Kiếm Khải are defined in the equipment catalog but **not seeded**, because post-revival possession/condition remains OPEN in R06. Sword-dependent moves are intentionally gated; an authorized equip/continuity update is required for them to fire. All effects require Lục Trầm to be in Party, ACTIVE and conscious. Combat behavior is reproducible from authoritative state and covered by focused Kotlin tests.
