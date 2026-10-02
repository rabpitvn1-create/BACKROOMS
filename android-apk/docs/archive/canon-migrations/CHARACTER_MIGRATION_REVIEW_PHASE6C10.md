# Phase 6C.10 — Character Migration Authority Review

Status: COMPLETE / REVIEW ONLY.

This review freezes the repository-observed authority state before character Markdown begins moving into `assets/content/characters/`. It does not decide unresolved canon conflicts and does not change retrieval behavior.

## Repository authority rule

`CHARACTER_CODEX_CURRENT.md` states that Google Drive `02_CHARACTERS` is the character authority when conflicts exist, except for newer scoped USER_RETCON locks explicitly frozen in the repository. UNKNOWN / OPEN / CHƯA KHÓA remain unknown, and KNOWLEDGE_LOCK material does not automatically become character knowledge.

## Remaining character sources

### Cao Minh — `Cao_Minh_Codex.md`

Registry:
- type: `CHARACTER`
- authority: `CHARACTER_CANON`
- status: `CURRENT`
- version: `R17`
- owner: `cao_minh`
- mandatoryFor: `character:cao_minh`

Repository source-map conflict:
- local Markdown declares R17 / MASTER CODEX;
- `CHARACTER_CODEX_CURRENT.md` still names Drive R15 as the direct current source.

6C.10 decision: do not use file migration to resolve this conflict. Moving Cao Minh is deferred until authority identity is made explicit.

### Lucia — `Lucia_Codex.md`

Registry:
- type: `CHARACTER`
- authority: `SCOPED_USER_RETCON`
- status: `CURRENT`
- owner: `lucia`
- mandatoryFor: `character:lucia`

Repository hard lock:
- Lucia / Hứa Thuý Mai and Lục Trầm are separate identities;
- runtime IDs `lucia` and `luc_tram` must not alias, rename, migrate into one another or merge.

6C.10 decision: migration is allowed later only as a byte-preserving move with the scoped USER_RETCON classification unchanged.

### Lục Trầm — `Lục_Trầm_Codex.md`

Registry:
- type: `CHARACTER`
- authority: `CHARACTER_CANON`
- status: `CURRENT`
- version: `R05`
- owner: `luc_tram`
- mandatoryFor: `character:luc_tram`

Repository source maps identify the current Lục Trầm Drive source and the local canon contains explicit SELF/CROSS/POV/WRITER-SECRET/WORLD/DYNAMIC/OPEN authority rules.

6C.10 decision: migration is deferred until after the unclassified character source. Moving the file must not alter dynamic-state ownership or knowledge-firewall semantics.

### Trác Lâm — `Trac_Lam_Codex.md`

Registry:
- type: `CHARACTER`
- authority: `UNCLASSIFIED`
- status: `UNCLASSIFIED`
- owner: `trac_lam`
- mandatoryFor: none

The source is detailed, but its local header does not declare CURRENT / CHARACTER CANON and the repository current-source maps do not elevate this Markdown to current character authority.

6C.10 decision: Trác Lâm is the lowest-risk first character migration. Phase 6C.11 may move the bytes to `content/characters/trac-lam.md`, but the registry must remain UNCLASSIFIED and the move must not create a new mandatory retrieval binding.

## Migration order frozen by this review

1. Trác Lâm — lowest risk, unclassified and non-mandatory.
2. Lucia — scoped USER_RETCON, preserve identity hard lock.
3. Lục Trầm — CURRENT CHARACTER_CANON, preserve knowledge/dynamic ownership.
4. Cao Minh — last, only after the local-R17 / source-map-R15 authority conflict is explicitly resolved or represented.

This ordering is a migration-safety decision only. It is not an ordering of narrative importance or canon quality.
