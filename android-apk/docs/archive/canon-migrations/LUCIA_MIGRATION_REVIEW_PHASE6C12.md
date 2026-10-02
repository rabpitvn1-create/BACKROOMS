# Phase 6C.12 — Lucia Migration Authority and Path Review

Status: COMPLETE / REVIEW ONLY.

This review freezes the repository-observed requirements for moving Lucia's CURRENT scoped USER_RETCON source. It does not move the source and does not alter canon authority.

## Authority lock

Registry entry `lucia` currently remains:

- type: `CHARACTER`
- authority: `SCOPED_USER_RETCON`
- status: `CURRENT`
- owner: `lucia`
- mandatoryFor: `character:lucia`
- planned contentPath: `characters/lucia.md`

The migration must not change any of those fields.

## Identity hard lock

The source explicitly requires:

- Lucia Lục / Hứa Thuý Mai is a separate person from Lục Trầm.
- Runtime IDs remain `lucia` and `luc_tram`.
- The two IDs must not alias, rename, merge or migrate into each other.
- Lucia must not inherit Lục Trầm's Thiên Kiếm Môn identity, Tịch Quang, Kiếm Khải, Táng Kiếm Cốc relationship history or xưng hô.
- Lucia ↔ Cao Minh relationship/history/xưng hô remain OPEN unless live continuity establishes them.

## Physical-path references that must move with the source

The following current repository references point to the legacy physical path and must be updated atomically when the Markdown moves:

- `app/src/main/assets/knowledge/characters_current.json`
- `app/src/main/assets/knowledge/knowledge_db.json`
- `CHARACTER_CODEX_CURRENT.md`
- `KNOWLEDGE_SOURCE_MAP.md`
- `LuciaSeparationContractTest.readCanon()`

The Canon Registry `path` field remains `Lucia_Codex.md` because it is the logical compatibility identity used by CanonRetriever. Only the physical source moves to `content/characters/lucia.md`.

## 6C.13 migration gate

Phase 6C.13 may proceed only as a byte-preserving source move plus the physical-path reference updates above.

It must preserve:
- scoped USER_RETCON authority;
- CURRENT status;
- `character:lucia` mandatory binding;
- identity separation regression;
- runtime encounter/equipment/gameplay projection locks;
- OPEN relationship fields.

No authority promotion, demotion, merge or lore reconciliation is permitted by the migration.
