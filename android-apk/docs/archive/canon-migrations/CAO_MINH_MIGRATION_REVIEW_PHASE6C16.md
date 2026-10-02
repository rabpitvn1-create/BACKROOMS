# Phase 6C.16 — Cao Minh Migration Authority Review

Status: COMPLETE / REVIEW ONLY. No Cao Minh authority decision is made here.

## Verified repository state

Local Markdown:
- logical path: `Cao_Minh_Codex.md`;
- planned structured destination: `characters/cao-minh.md`;
- declared title/version: Master Codex R17;
- registry authority: `CHARACTER_CANON`;
- registry status: `CURRENT`;
- registry version: `R17`;
- owner: `cao_minh`;
- mandatory binding: `character:cao_minh`.

Repository source maps:
- `CHARACTER_CODEX_CURRENT.md` names the Drive Cao Minh source as R15;
- `KNOWLEDGE_SOURCE_MAP.md` also describes Cao Minh machine-readable/current mapping as R15.

## Migration rule

The R17-local / R15-source-map disagreement is an authority conflict, not a filesystem problem.

Phase 6C migration therefore MUST NOT:
- declare R17 to supersede R15 merely because it is newer;
- downgrade the local registry entry to R15;
- rewrite Drive provenance into a local-file provenance claim;
- silently merge the two versions;
- alter gameplay/save state to match either document.

The move may only change the physical asset location. The registry logical `path` remains `Cao_Minh_Codex.md`, so CanonRetriever section IDs and SOURCE labels stay stable.

## Canon invariants preserved

- OPEN / UNKNOWN remain unresolved.
- KNOWLEDGE_LOCK remains writer/GM knowledge, not automatic character knowledge.
- Dynamic continuity/save remains authoritative for state changed during play.
- Diệp Minh scoped USER_RETCON remains a separate higher-priority scoped override where applicable.
- File movement does not create any new gameplay mutation path.

Phase 6C.17 may move the local R17 bytes to `content/characters/cao-minh.md` while leaving the authority conflict recorded exactly as above.
