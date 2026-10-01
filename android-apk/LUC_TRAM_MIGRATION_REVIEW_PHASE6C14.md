# Phase 6C.14 — Lục Trầm Migration Review

Status: COMPLETE / REVIEW ONLY. No Lục Trầm source was moved in this phase.

## Verified authority

- Registry ID: `luc-tram`.
- Logical path: `Lục_Trầm_Codex.md`.
- Planned structured destination: `characters/luc-tram.md`.
- Type: `CHARACTER`.
- Authority: `CHARACTER_CANON`.
- Status: `CURRENT`.
- Version: `R05`.
- Owner: `luc_tram`.
- Mandatory binding: `character:luc_tram`.

The local source declares `CURRENT / CHARACTER CANON`. Repository knowledge/source maps separately preserve Drive provenance for Lục Trầm R05. A file move must not rewrite those provenance records into local-source claims.

## Knowledge firewall

The source's READ FIRST contract must survive migration unchanged:
- SELF-CANON, CROSS-CANON, POV/BELIEF, WRITER-SECRET, WORLD-CANON, DYNAMIC and OPEN/UNKNOWN remain distinct;
- writer knowledge does not become character knowledge;
- OBSERVED/TOLD/VERIFIED/INFERRED provenance remains required;
- seeing an ability or Entity once does not grant complete rule knowledge.

## Dynamic-state ownership

The codex defines baseline identity and capability. It must not reset live continuity/save fields such as:
- injury or depletion;
- inventory/equipment changes;
- current location;
- learned knowledge;
- current relationship/trust;
- promises, debts, consequences or previous choices.

Live continuity/save remains authoritative for changed DYNAMIC state.

## Character separation and cross-canon boundaries

- Lục Trầm remains `luc_tram`.
- Lucia remains a separate `lucia` character.
- Lục Trầm codex does not own Cao Minh facts; cross-canon statements must defer to Cao Minh's current owner.
- Táng Kiếm Cốc backstage truth must not be promoted into Lục Trầm's knowledge automatically.

## Physical migration impact

Unlike Lucia, the machine-readable knowledge records currently identify Lục Trầm's Drive source (`02_CHARACTERS/Lục_Trầm_Codex`) rather than the local physical Markdown path. Those provenance fields must remain unchanged during a local file move.

The direct local-file regression reader is `LuciaSeparationContractTest.readCanon("Lục_Trầm_Codex.md")`; 6C.15 must update that reader to the structured physical path while keeping the CanonRetriever logical identity `Lục_Trầm_Codex.md`.

## 6C.15 entry conditions

A byte-preserving move is allowed only if:
1. registry remains `CHARACTER_CANON / CURRENT / R05`;
2. owner remains `luc_tram`;
3. `mandatoryFor` remains `character:luc_tram`;
4. READ FIRST knowledge firewall and DYNAMIC-state rules remain present;
5. Lucia/Lục Trầm identity separation remains present;
6. Drive provenance records remain unchanged;
7. CanonRetriever logical source identity remains `Lục_Trầm_Codex.md`.

Phase 6C.14 changes no runtime behavior and resolves no lore conflict.
