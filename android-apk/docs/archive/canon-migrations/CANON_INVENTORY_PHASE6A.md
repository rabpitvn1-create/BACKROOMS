# Phase 6A — Canon Inventory and Authority Map

Status: COMPLETE / AUDIT ONLY. Runtime behavior is unchanged.

This inventory records what the repository actually ships today before Phase 6 introduces a registry. It does not move sources, rewrite lore, resolve conflicts, or promote an unclassified source into canon.

## Current retrieval boundary

`CanonRetriever.fromAssets()` loads every direct UTF-8 `.md` child under `app/src/main/assets/canon/`, sorts filenames, splits each file by Markdown headings, and derives deterministic section IDs from filename + heading path.

The current section metadata supports only:
- `aliases`;
- `requires`;
- `refs`;
- `core=true`.

There is no file-level registry for authority class, CURRENT/HISTORICAL status, version, supersedes, character-knowledge scope, writer-secret scope, or conflict policy.

Mandatory retrieval is derived from live state:
- current Level;
- Cao Minh;
- present Party characters;
- active Entity.

Supplemental retrieval is lexical/action-based, limited to at most three sections and the character budget. Therefore any direct Markdown asset can currently become supplemental context even when its authority class is not explicit.

The retriever remains read-only; this audit concerns canon selection/authority, not GameState mutation.

## Shipped Markdown inventory

### `ASYNC_BackroomsV2.md`
Observed content: ASYNC Research Institute / Project KV31 / Threshold history.

Declared file-level status: none found.

6A treatment: `UNCLASSIFIED_WORLD_HISTORY`.

Registry requirement: do not auto-promote to CURRENT merely because it is present under `assets/canon`.

### `BACKROOMS_WORLD.md`
Observed content: project world baseline, Levels 0–10, project overrides and explicit HARD LOCK sections.

6A treatment: `WORLD_CANON / PROJECT_OVERRIDE`.

Important ownership rule already present in source: world description does not own gameplay route/spawn/damage when project Core rules override it.

### `BACKROOMS_WORLD_SUBLEVELS_1_6.md`
Declared status: `CURRENT / PROJECT CANON`.

6A treatment: `WORLD_CANON`.

The source itself explicitly keeps route authority in the level graph, Entity authority in EntityCore and item/resource authority in ItemCore.

### `Backrooms_Linh_Khi.md`
Observed content: Backrooms spiritual-energy environment plus a HARD LOCK that its origin remains UNKNOWN.

Declared file-level CURRENT status: none found.

6A treatment: `WORLD_CANON_CANDIDATE` with explicit hard-lock semantics preserved.

Registry requirement: UNKNOWN origin must remain UNKNOWN.

### `Backrooms_Linh_Khi_Anh_Huong_Tu_Si.md`
Observed content: environmental spiritual-energy effects on cultivators, including Cao Minh and Lục Trầm.

Declared file-level CURRENT status: none found.

6A treatment: world/environment rule with `CROSS_CANON` character references.

Registry requirement: character-specific statements must defer to the owning CURRENT character codex when they overlap.

### `Cao_Minh_Codex.md`
Declared status: `CHARACTER CANON • MASTER CODEX`, `CANON KIỂM SOÁT`, R17 dated 2026-09-28.

6A treatment: `CHARACTER_CANON`, owner `cao_minh`.

Audit conflict: repository source-map documents still describe the authoritative Drive source as Cao Minh R15. Phase 6A does not choose between the local R17 declaration and those R15 source-map statements because the repository explicitly says modification date alone is not authority.

### `Entity.md`
Observed content: visual descriptions for Entity types.

Declared file-level authority/status: none found.

6A treatment: `UNCLASSIFIED_VISUAL_REFERENCE`.

Registry requirement: this source must not silently become Entity mechanics, spawn probability, damage, loot or route authority.

### `Huyet_Tay_Cao_Gia.md`
Observed content: narrative/history account of the Cao-family massacre and Diệp Minh.

Declared file-level authority/status: none found.

6A treatment: `UNCLASSIFIED_HISTORY`.

Registry requirement: it overlaps current Cao Minh/Diệp Minh relationship material and must not override scoped USER_RETCON/current character sources until authority is explicitly registered.

### `Lucia_Codex.md`
Declared status: `CURRENT / SCOPED USER RETCON`.

6A treatment: `CHARACTER_CANON`, owner `lucia`, scoped to the Lucia restore/separation lock.

OPEN fields remain OPEN. It must never be merged with `luc_tram`.

### `Lục_Trầm_Codex.md`
Declared status: `CURRENT / CHARACTER CANON`, R05.

6A treatment: `CHARACTER_CANON`, owner `luc_tram`.

This source explicitly defines the authority classes SELF-CANON, CROSS-CANON, POV/BELIEF, WRITER-SECRET, WORLD-CANON, DYNAMIC and OPEN/UNKNOWN, and states that live continuity/save owns changed dynamic state.

### `Tang_Kiem_Coc_Huyet_Ma_Kiem_Tich_Quang.md`
Observed content: Táng Kiếm Cốc / Huyết Ma Kiếm history and backstage event material.

Declared file-level authority/status: none found.

6A treatment: `UNCLASSIFIED_HISTORY`.

Registry requirement: some material overlaps Lục Trầm's POV versus backstage truth. Phase 6 must distinguish objective history/writer-secret material from what a character knows instead of exposing the whole document as one authority class.

### `Trac_Lam_Codex.md`
Observed content: detailed Trác Lâm character profile.

Declared file-level CURRENT/CHARACTER CANON status: none found in the source header.

6A treatment: `UNCLASSIFIED_CHARACTER_SOURCE`.

Registry requirement: do not treat presence in `assets/canon` as sufficient proof of CURRENT character authority.

## Adjacent authority sources outside the Markdown pool

The repository also carries authority information outside `assets/canon`, including:
- `CHARACTER_CODEX_CURRENT.md`;
- `KNOWLEDGE_SOURCE_MAP.md`;
- `app/src/main/assets/knowledge/characters_current.json`;
- `app/src/main/assets/knowledge/knowledge_db.json`;
- scoped repository USER_RETCON sources such as `android-apk/DIEP_MINH_CANON.md`;
- Drive-backed character sources referenced by the source maps.

These are not loaded by `CanonRetriever.fromAssets()`.

## Verified coverage gaps

1. Current source maps reference a CURRENT Syvial Drive codex, but there is no `Syvial_Codex.md` direct child under `assets/canon`. The current filename-based mandatory character lookup therefore has no shipped Markdown owner for `character:syvial`.
2. Current source maps and Cao Minh material reference `DIEP_MINH_CANON.md`, but that scoped USER_RETCON is outside `assets/canon`. Prose references are not dependency edges; only explicit `<!-- canon: requires=... -->` metadata is enforced by the retriever.
3. Presence under `assets/canon` currently acts as eligibility for supplemental retrieval even for sources whose CURRENT/HISTORICAL/REFERENCE status is not declared.
4. The current retriever has no mechanism to represent supersedes, scoped authority, POV/BELIEF, WRITER-SECRET or DYNAMIC ownership at registry level.
5. The local Cao Minh R17 declaration and repository source maps naming Drive R15 require an explicit authority decision in Phase 6B; Phase 6A intentionally does not reconcile them.

## Phase 6A boundary

No source was moved.
No canon text was rewritten.
No authority conflict was resolved.
No runtime loader/retriever behavior changed.
No new source was promoted to canon.

Phase 6B may now introduce a registry schema that models these observed authority/status distinctions explicitly and validates them before retrieval.
