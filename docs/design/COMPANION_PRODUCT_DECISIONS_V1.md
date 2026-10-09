# Companion Product Decisions V1

Date: 2026-10-09
Status: APPROVED DESIGN BASELINE UNDER EXPLICIT OWNER DELEGATION.
Implementation, verification and release: NOT APPROVED BY THIS RECORD.

## Decision authority and evidence

Decision-maker: Agent Ponytail under the project owner's explicit instruction,
"Comment cuối và thay tôi quyết định luôn đi", on 2026-10-09.

This records delegated approval. It does not claim that the owner personally
read or signed every line. The chat instruction has no public permalink.

Authoritative public records:
- [Architecture Baseline V1](https://github.com/rabpitvn1-create/BACKROOMS/issues/476#issuecomment-6072370176)
- [Orion technical verdict](https://github.com/rabpitvn1-create/BACKROOMS/issues/476#issuecomment-6072383041)
- [Delegated final decision](https://github.com/rabpitvn1-create/BACKROOMS/issues/476#issuecomment-6072401470)
- [Authoring work allocation](https://github.com/rabpitvn1-create/BACKROOMS/issues/476#issuecomment-6072430193)

All six baseline sections are approved as design decisions, including their
explicit unresolved verification gates. Approval does not establish that the
proposed schema, rule table, runtime or generated patch-chain has been tested.

Inspected repository baseline: main at
`5450284ddc2acded7cc1cc54d3c035faff4ba4be`.
Before this document, the design branch head was
`d7289975d3f4e9acca35c43fc507708e7ea650c7`, ahead 3 / behind 0 main.

## Approved product decisions

| ID | Decision | Status |
| --- | --- | --- |
| PD01 | Player always accompanies Cao Minh; no direct control or independent route. Cao Minh proposes autonomous decisions, subject to native/Core authorization. | APPROVED |
| PD02 | Companion V1 activates only for a fresh campaign. Import, conversion and migration of legacy saves are out of scope. | APPROVED |
| PD03 | Activation requires an explicit NEW GAME operation for Companion V1, after release gates. App update alone never switches an existing campaign; load failure never silently creates a campaign. | APPROVED |
| PD04 | Preserve legacy data in its existing storage/keys. Companion V1 uses a separate storage namespace/database and never overwrites or deletes legacy data. | APPROVED |

Retaining legacy data does not make it a second authority for a companion run.
The companion database is that run's single persistence authority.
WebView storage is only a projection/cache for that run.

No legacy loader is promised by these decisions. If the new build cannot load a
legacy save, its UI must explain incompatibility and must not pretend a successful
load. Do not add a legacy runtime mode, importer or new export feature solely to
satisfy this record. Bulk deletion or a retention-policy change requires a separate
explicit decision.

## Architecture constraints

AI MAY INTERPRET. CORE ALONE MAY AUTHORIZE.

- One existing native/Core mutation pipeline; one authoritative SaveStats commit.
- Core result, verified events, observations, memory/brain changes and committed
  receipt are atomic. UI receives success only after persistence succeeds.
- Brain changes use deterministic versioned evidence-backed rules with bounded
  CREATE/UPDATE/RESOLVE templates. CanonPersona is outside writable fields.
- Actor-private knowledge and memory never inherit GM-only secrets or another
  actor's observations.
- Locked decision and RNG reservation recover together; retries and request
  aliases do not grant independent rolls. Detailed recovery state-machine review
  remains required before A1/A2.
- Native actor/target/kind validation and existing audit/repair/fallback remain
  authoritative. Empty action kind does not automatically waive semantic audit.
- Provider failures never invent a character reaction or authoritative event.
- No old-data overwrite, canon mutation, extra dependency or production schema
  adoption is authorized merely by this document.

## Authoring responsibilities

| Scope | Owner / responsibility |
| --- | --- |
| Technical Design, RFC synchronization, Character Brain Rule Table V1, this Product Decisions document | Ponytail: author and architecture owner |
| Canon P0 characterization and Provider P0 observability specifications | Orion: author, consistent with prior acceptance in #476 |
| G0 evidence, schema/recovery/rule fixtures and QA plan | Ponytail: preparation and verification |
| Technical review | Orion: within the review role already exercised; no additional implementation commitment is assumed |
| Unsigned A-F review | Attribution remains unidentified; do not infer a person's identity |

File ownership prevents concurrent edits to the same document without coordination.
Writers inspect the latest branch and file SHA before each write, preserve ancestry,
and never force-push or merge without separate authorization.

## Acceptance and phase gates

Order:
G0 -> S1 -> S2 -> S2.5 -> M1 -> P1 -> M2 -> P2 -> C1 -> A1 -> A2 -> UI1 -> R1.

G0 must reproduce the recursive patch-chain, inspect generated output and provide
effective-source artifacts, a hooks manifest and behavior fixtures. Source tracing
and reviewer agreement are not G0 runtime proof.

Before their implementation phases, review concrete schema/state-machine details,
typed claim and promise-acceptance contracts, rule contents/bounds and evidence
fixtures. This document approves no unwritten numerical trust or mood rules.

S2.5 uses a fresh isolated test database/slot and test command adapter while real
Core validation and SaveStats commits run. No persisted legacy mode or dual-write
authority. The existing three buttons may act as test drivers.

### QA decisions

Preferred real device: the owner's moto g86 power 5G, subject to actual test access.
Record observed Android/API/build details, rather than assuming them.
Emulator automation does not establish real-device performance.

Use fixed seeds and validated command fixtures at 1k/5k/10k logical turns,
covering no-op/dialogue, event-producing actions, retrieval and retry/crash cases.
Run at least three independent trials; distinguish cold and warm measurements.
Report p50/p95, database size including WAL/sidecars, writes per turn and query
correctness.

Correctness acceptance: zero unauthorized mutations, cross-slot/actor leaks,
duplicate effects, incompatible decision/dice reuse and partial committed batches.
Require exact state/receipt parity, correct historical retrieval and contract-based
crash recovery.

Performance budgets are not measured or numerically approved yet. Obtain a
comparative storage/Core baseline on the same device/workload; Ponytail authors
budgets after the pilot, Orion reviews them before the qualification run.
Never relax thresholds retrospectively to label a qualification run GREEN.

## Closure, verification and rollback

Issue #476 remains open until its evidence and artifact acceptance criteria are met,
including G0, schema/rule review and small implementation-issue breakdown.
The architecture baseline and product policies above do not require another
identical approval round.

This is a Markdown-only record. No gameplay, canon, dependency or save schema is
changed by adding it. Documentation validation checks policy/provenance consistency;
CI results must be reported from actual runs.

Rollback this documentation change by reverting its commit. A document revert
does not silently revoke public decisions or change player data; changed policy must
have a new explicit decision record and linked review.

## Ownership handoff, 2026-10-09

Orion delivered the two observability specs in commits ac47c40 and bdea37c, then
left the task. The owner instructed Ponytail to take over all remaining work.
Ponytail now owns completion, artifact review preparation, G0 and QA planning.
Do not record future independent Orion reviews as completed or promise availability.
His existing review and authored content remain attributed to him.

The architecture decision's Exit v6 assumption was later disproved by the actual
recursive chain. See G0_EFFECTIVE_CHAIN_VERIFICATION_V1.md for the retirement to
EXIT_STREAK_V1. This is a factual design correction, not approval to restore v6 or
change production gameplay. Runtime readiness remains gated by reconciliation.
