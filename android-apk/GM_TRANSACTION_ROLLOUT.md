# GM Transaction Rollout

Status: PHASE 6C.6 — CAO FAMILY HISTORY MIGRATION COMPLETE; PHASE 6C.7 NEXT

This rollout preserves the current V2 Core/save/runtime while reintroducing GM planning authority in controlled stages. No phase may skip the transaction boundary.

## Non-negotiable invariants

1. GM never writes GameState directly.
2. Every gameplay mutation is a typed command owned by a deterministic Core.
3. Every causal group is atomic: if one command is rejected, the group commits nothing.
4. Narration may state a gameplay mutation as fact only when the same fact has committed-event evidence.
5. Canon truth, dynamic state, character knowledge, and GM proposals are separate authorities.
6. OPEN/UNKNOWN cannot be promoted into canon by gameplay.
7. A retry of the same turn must reuse the same authoritative base revision and deterministic RNG outcome.
8. A provider failure before commit means the turn did not happen; a narration failure after commit must not reroll or mutate the turn.

## Phase 1 — Contract and regression corpus

Live behavior: unchanged.

Deliverables:
- `GmTransactionContract` proposal envelope.
- Atomic causal-group semantics.
- Validation ledger and committed-event evidence.
- Regression test for the historical Almond Water contradiction.
- No call from `MainActivity` or `GameCoreFacade` into the new contract yet.

Historical regression that must remain impossible:

```
GM: player picked up Almond Water
Core: pickup rejected
Narration: player owns Almond Water
```

Required outcome when pickup is rejected:

```
ITEM_DISCOVERED may commit
INVENTORY_ITEM_ADDED must not commit
Narration must not claim possession
```

Discovery and pickup therefore belong in separate causal groups when discovery is allowed to survive a pickup failure.

## Phase 2 — Shadow GM Planner

Live behavior: still V2 authoritative.

The GM Planner receives:
- authoritative state snapshot/hash;
- player action;
- deterministic turn RNG context;
- bounded canon/character/knowledge context.

It emits a transaction proposal only. The proposal is validated and logged but never committed. CI and debug builds compare planner proposals against the V2 outcome.

Implemented in Phase 2:
- `GameCoreFacade.shadowPlannerContext()` exposes a copied, read-only pre-commit state snapshot/hash plus deterministic RNG identity and draw counters.
- `GmShadowPlanner` builds a bounded prompt from Core contexts, canon retrieval and the actor knowledge projection.
- Debug builds run the planner asynchronously only after the normal V2 turn has committed and narrated, so provider failure or invalid planner output cannot delay, reroll or mutate the live turn.
- Accepted shadow proposals are cached by a deterministic planner key; the same turn context reuses the same accepted proposal rather than asking the provider to re-plan.
- Structural schema is strict and fail-closed; raw state/json-patch fields are rejected.
- Shadow telemetry compares proposed command types with the V2 top-level state delta and selected situation using hashes/keys only, not raw canon or raw state logs.
- Current scope is prepared explorer turns. Query-only and combat-runtime paths remain fully deterministic and are not shadow-planned in this phase.

Exit gate:
- zero direct-state mutation by planner;
- deterministic replay for the same turn context through proposal reuse;
- proposal schema violations fail closed;
- existing V2 Android tests/build remain green.

## Phase 3 — Typed command authority adapters

Live behavior: still V2 authoritative by default.

Introduce a command registry with one deterministic owner per mutation family, for example:
- inventory -> ItemCore;
- level/route -> LevelCore;
- entity lifecycle -> EntityCore;
- character encounter/party -> CharacterEncounterCore;
- progression -> CharacterProgressionCore;
- survival -> SurvivalCore;
- combat -> CombatChoiceEngine / combat authority.

Unknown commands are rejected. Raw JSON patch commands are forbidden.

Implemented in Phase 3:
- `GmCommandAuthority` is the single typed-command registry; each registered command has exactly one Core owner.
- Active dry-run adapters cover item use/share/discard, legal Level transition, stat upgrade and combat start. They invoke existing Core code against a private copied state.
- Entity encounter, character encounter and chest discovery are registered to their deterministic owners but marked `PHASE4_SELECTION_GATE`; they are deliberately rejected in Phase 3 so the GM cannot bypass V2 candidate/RNG selection.
- Unknown command types and unknown payload fields fail closed.
- Causal groups are simulated atomically. If any command in a group fails, all state changes and committed-event evidence from that group are discarded.
- Every accepted command emits a Core-owned typed event. `GmTransactionContract` exposes evidence only for fully accepted groups.
- `GameCoreFacade.validateShadowTransaction()` operates only on the copied Phase-2 snapshot and has no persistence path.
- Debug telemetry records authority validity, accepted/rejected group counts and evidence count alongside the V2 comparison. Gameplay remains V2 authoritative.

Exit gate:
- every accepted planner command maps to exactly one Core owner;
- every accepted command produces committed-event evidence;
- causal-group rollback tests cover partial failures;
- selection/RNG-owned mutations remain fail-closed until Phase 4;
- existing V2 Android tests/build remain green.

## Phase 4 — Transaction commit behind a feature flag

Planner transactions can become authoritative only when the feature flag is enabled.

### Phase 4A — Commit gate only

Status: implemented, intentionally NOT armed.

- Build flag `GM_TRANSACTION_COMMIT_ENABLED` exists and defaults to `false` when the environment variable is absent.
- `GmTransactionCommitGate` is fail-closed.
- With the flag OFF it returns `gm_transaction_commit_disabled`.
- Even with the flag ON, Phase 4A returns `phase4_commit_not_armed`; no code path can persist a planner transaction yet.
- `MainActivity` passes the build flag into `GameCoreFacade`; the existing asynchronous shadow telemetry probes the gate but never invokes a commit or persistence method.
- Turn ID and base-state hash mismatches are rejected before any future commit can be armed.
- Phase 4A changes no live gameplay behavior. V2 remains the only authoritative turn path.

Phase 4 will advance in small sub-phases: 4B atomic execution state, 4C selection/RNG gates, then 4D live commit wiring behind the same flag.

### Phase 4B.1 — Pure transaction executor

Status: implemented, NO PERSIST.

- `GmTransactionExecutor` executes only against deep-copied state and has no Android `Context`, `SharedPreferences`, `GameCoreFacade` or persistence dependency.
- Phase 3 validation delegates atomic execution to this same executor, so shadow validation and 4B.1 cannot drift into two different rollback rules.
- Accepted causal groups advance the private working copy; if any command in a group is rejected, every mutation from that group is discarded.
- A later rejected group cannot erase an earlier accepted group.
- The caller's `beforeState` is checked for mutation and remains unchanged.
- The executor exposes an in-memory `afterState` plus before/after hashes only as an execution draft. It is not a commit candidate and is not persisted.
- Same input + same proposal produces the same `afterState` and hash for the currently enabled deterministic command adapters.
- Commands behind `PHASE4_SELECTION_GATE` remain rejected.

### Phase 4B.2 — Replay-verified commit candidate

Status: implemented, NO PERSIST.

- `GmCommitCandidateBuilder` accepts only a valid 4B.1 execution draft.
- It recomputes the evidence ledger from `proposal + commandResults`; a tampered execution ledger is rejected.
- It builds `stateDelta` with the existing `AuthoritativeStatePatch` format used by V2 commit records.
- The delta is replayed against the original `beforeState`. Replay must match the full `afterState`, not only the authoritative roots. Any accidental mutation to excluded/derived roots such as `log` is therefore rejected.
- The candidate carries `beforeStateHash`, `afterStateHash`, `proposalFingerprint`, accepted/rejected group ledgers, committed-event evidence and `transactionHash`.
- `transactionHash` is computed over the canonical candidate payload before the hash field is inserted. Same input + same proposal + same deterministic execution yields the same transaction hash.
- `verify()` replays the state delta and validates proposal fingerprint, hashes and transaction hash.
- The candidate deliberately does not contain the full `afterState`; 4B.2 remains an in-memory verification artifact and has no persistence path.
- Selection/RNG-gated commands remain closed until 4C. The Phase-4A commit gate remains unarmed.

### Phase 4C.1 — Core selection authorization

Status: implemented as a pure gate; gated adapters are not opened yet.

- `GmSelectionGate` binds an authorization packet to the prepared turn ID, base-state hash, the exact Core-selected `SituationCandidate`, and the exact `CANDIDATE_SELECTION` RNG draw trace.
- `START_ENTITY_ENCOUNTER`, `START_CHARACTER_ENCOUNTER` and `DISCOVER_CHEST` are eligible only when their payload matches the exact selected candidate. A `NONE` selection can never authorize one of these commands.
- The packet records `rngDrawSeq`, `rngDrawsUsed`, `rngDrawKey` and an authorization hash. Counter drift, stale turn/base state, candidate mismatch or packet tampering fail closed.
- This step does not yet change `GmCommandAuthority`; the three commands remain blocked until 4C.2 wires the gate into the pure executor.
- No persistence or feature-gate behavior changes.

### Phase 4C.2 — Selection-gated Core adapters

Status: implemented in shadow execution, NO PERSIST.

- `GameCoreFacade.shadowPlannerContext()` issues selection authorization from the retained `PreparedTurn`, selected candidate, working-state trace and the same scoped `TurnRng`.
- `GmTransactionExecutor` passes that Core-issued authorization to `GmCommandAuthority`.
- `START_ENTITY_ENCOUNTER`, `START_CHARACTER_ENCOUNTER` and `DISCOVER_CHEST` invoke their existing Core owners only when the command exactly matches the authorized candidate.
- Missing authorization, `NONE`, stale turn/base state or candidate redirection remains fail-closed.
- Accepted gated events carry `selectionEvidence`; the execution draft exposes it only when the containing causal group is fully accepted.
- Shadow planner sees the authorization in TURN_CONTEXT, but cannot choose a different Entity/Character/Chest than Core selected.
- No selection-gated result is persisted; V2 remains the live authority.

### Phase 4C.3 — Selection evidence pinned to CommitCandidate

Status: implemented, NO PERSIST.

- `GmSelectionGate.evidence()` now carries the complete verifiable authorization identity, including turn/base-state binding, RNG counter identity and authorization hash.
- `GmCommitCandidateBuilder` recomputes selection evidence from committed events and requires it to match the 4B.1 execution draft.
- A candidate containing selection-gated state must pass `GmSelectionGate.validationReason()` again before it is created.
- `selectionEvidence` is part of the canonical `CommitCandidate`, so `transactionHash` binds state delta, committed events and the exact Core RNG selection together.
- `verify()` checks that candidate-level evidence equals event-level evidence and remains cryptographically intact before replay validation succeeds.
- Evidence conflicts, tampering or detachment from the original turn/base state fail closed.
- The Phase-4A commit gate remains unarmed and there is still no persistence path for planner transactions.

Phase 4C is complete.

### Phase 4D.1 — Authoritative turn resolver

Status: implemented and gated; not wired to MainActivity yet.

- The commit gate is now logically armed only when `GM_TRANSACTION_COMMIT_ENABLED=true`; the build default remains `false`.
- `GmAuthoritativeTurnResolver` composes the already-prepared deterministic turn base with the replay-verified GM state delta. It has no persistence dependency.
- A Core-selected ENTITY/CHEST/CHARACTER situation must appear as matching committed selection evidence or the entire authoritative resolution fails.
- `GmCommittedEventAdapter` converts every accepted typed event into a canon-registered DomainEvent. Unmapped event types fail closed.
- Prepared player-action DomainEvents are preserved first; GM DomainEvents are appended with continuous event sequence, then thread-resolution/dormancy checks run.
- `COMBAT_STARTED` is now a registered canon DomainEvent so a GM-started combat mutation cannot exist without event evidence.
- No runtime caller invokes authoritative GM persistence yet. V2 remains live authority until 4D.2 wiring passes CI.

### Phase 4D.2 — Core authoritative commit API

Status: implemented behind the existing build flag; MainActivity still uses V2.

- `GameCoreFacade.completePreparedTurnWithGmTransaction()` is the only new persistence entry point.
- The caller supplies only `turnId + proposal`. Core reconstructs selection authorization, execution base, execution draft and CommitCandidate from its retained `PreparedTurn`; UI/model cannot submit an authoritative after-state.
- Live `stateVersion` and base-state hash are checked immediately before resolution.
- The GM delta is applied on top of the deterministic prepared-turn state, preserving player-action time/route/recovery and Core selection trace.
- Prepared player-action events and mapped GM events are committed together through one `EmergentTurnEngine.commitAuthoritative()`, producing exactly one state revision.
- Commit log records immutable `gmTransaction` metadata: transaction hash, proposal fingerprint, before/after execution hashes and selection evidence.
- Failed gate/validation/candidate verification performs no persist and keeps the prepared turn available for a bounded retry. Stale turns are discarded.
- Successful commit persists once, removes the prepared turn and returns transaction hash plus committed GM evidence.
- This API is not yet invoked by `MainActivity`; default/live behavior is still V2 until 4D.3.

### Phase 4D.3 — Runtime authority switch

Status: implemented behind `GM_TRANSACTION_COMMIT_ENABLED`.

- Default builds keep `GM_TRANSACTION_COMMIT_ENABLED=false` and continue through the existing V2 `completePreparedTurn()` path.
- Enabled builds synchronously run the GM transaction planner before commit and call only `completePreparedTurnWithGmTransaction()`.
- There is no same-turn fallback to V2 when GM authority is enabled. Provider/schema/Core rejection gets one bounded repair attempt; a second failure leaves the turn uncommitted.
- Planner context now uses `executionBaseState`: the deterministic post-player-action/pre-GM state retained by Core. `baseStateHash` still binds the transaction to the pre-turn live revision.
- A Core-selected situation must be represented by the exact matching selection-gated command; the planner cannot substitute another Entity/Character/Chest.
- V2 speculative narration prefetch is disabled in GM-authoritative builds because its preview outcome is not the GM transaction outcome.
- Debug shadow comparison continues only when V2 is live authority.
- If a GM transaction already starts combat, the post-narration bridge does not start combat a second time.
- Successful GM commit is persisted before narration; narration failure therefore regenerates/falls back from the same committed state without rerunning gameplay.
- Scheduler candidates selected in `MANDATORY` mode are authorized without inventing a fake RNG draw; their trace is bound by `selectionMode=MANDATORY` and the authorization hash. Weighted selections still require the exact `CANDIDATE_SELECTION` draw evidence.

Phase 4D is complete at the code level. The feature flag remains OFF by default; enabling it is an explicit rollout decision.

Commit protocol:
1. verify turnId and baseStateHash;
2. validate all commands;
3. reject/repair invalid groups;
4. commit accepted groups through Core owners;
5. persist one authoritative state revision;
6. emit immutable committed-event ledger.

V2 remains the default path while the feature flag is OFF. An enabled GM-authority turn never silently falls back to V2 after planning begins.

## Phase 5 — Narrator consumes committed reality only

Status: COMPLETE / FROZEN.

Narrator input is built after transaction commit from:
- committed-turn evidence;
- post-commit authoritative state;
- canon packet;
- POV/knowledge view.

Implemented:
- `CommittedTurnNarrationEvidence` projects only player-observable evidence from the latest matching commit.
- `GmNarrativePacket` exposes committed evidence separately from post-state and requires narrator `claims[]`.
- `NarrationGuard` validates declared claims against committed `eventId + kind + subject` evidence and rejects unsupported current-turn mutation claims.
- The Almond Water regression is covered: inventory that existed before the turn is not evidence that Almond Water was acquired during the current turn.
- Chest/combat loot evidence is exposed explicitly to narration when Core actually committed the reward.
- Narration retry uses the same committed turn and cannot reroll or mutate gameplay.
- Pending character-intro acknowledgement is Core-owned and derived from committed encounter evidence rather than model output.
- Narration may persist presentation/log data only; it is not a gameplay authority.

Exit gate:
- committed state exists before narration;
- every current-turn mutation claim must be supported by committed evidence;
- narration failure cannot rerun gameplay;
- historical Almond Water contradiction remains impossible;
- Android verification and release workflows are green.


## Phase 6 — Canon/content platform

### Phase 6A — Canon inventory and authority map

Status: COMPLETE / AUDIT ONLY.

- `CANON_INVENTORY_PHASE6A.md` inventories every direct Markdown source currently shipped under `assets/canon`.
- Current `CanonRetriever` behavior, mandatory subjects, supplemental selection and supported section metadata are recorded before any registry changes.
- Sources with explicit CURRENT/CHARACTER/WORLD authority are distinguished from unclassified history/visual/character sources without silently promoting the latter.
- Verified coverage/authority gaps are recorded, including Syvial's missing Markdown mirror, the out-of-pool scoped `DIEP_MINH_CANON.md`, and the Cao Minh R17 local declaration versus R15 source-map statements.
- No source was moved, rewritten or reconciled and runtime retrieval behavior is unchanged.

### Phase 6B — Canon registry schema and validation

Status: COMPLETE / NOT YET USED BY RETRIEVAL.

- `canon-registry.json` explicitly registers the twelve Markdown sources inventoried in 6A.
- Each source records stable ID, file path, type, authority, status, version/owner, dependencies, mandatory subjects, supersedes and an audit note.
- `CanonRegistry` validates schema version, unknown fields, source IDs/paths, authority/type/status enums, character ownership, duplicate IDs/paths, dependency/supersedes references and dependency cycles.
- The shipped registry deliberately preserves 6A uncertainty: unclassified/candidate sources are not silently promoted to CURRENT canon.
- `CanonRegistryTest` validates the shipped registry and fail-closed behavior for duplicate IDs, missing references, cycles, unknown authority/fields and missing character owner.
- `CanonRetriever` does not consume the registry yet. Runtime retrieval and gameplay behavior are unchanged.

### Phase 6C.1 — Structured content layout declaration

Status: COMPLETE / NO SOURCE MOVES.

- Registry entries now carry both legacy `path` and planned structured `contentPath`.
- `CanonRegistry` validates structured paths against the allowed content roots and rejects traversal, backslashes and duplicate destinations.
- The asset tree now reserves the content namespaces: world, levels, sublevels, entities, items, factions, phenomena, characters, history, wiki, codex and continuity.
- Existing Markdown remains under `assets/canon`; `CanonRetriever` still reads the legacy direct paths, so runtime behavior is unchanged.
- No canon source has two authoritative copies. The new tree is only a migration destination declaration.

### Phase 6C.2 — Compatibility loader + first migration

Status: COMPLETE.

- `CanonRetriever.fromAssets()` now resolves registered sources from `content/<contentPath>` first and falls back to the legacy `canon/<path>` during migration.
- Logical source identity remains the legacy registry `path`, so section IDs and prompt SOURCE labels remain stable when bytes move.
- Unregistered legacy Markdown remains discoverable during the transition.
- Missing registered sources fail closed if neither structured nor legacy copy exists.
- `ASYNC_BackroomsV2.md` was migrated unchanged to `content/history/async-backroomsv2.md` and the legacy copy was removed.
- Tests cover structured precedence, legacy fallback, missing-source failure and the shipped ASYNC migration.
- No gameplay authority or canon classification changed.

### Phase 6C.3 — Entity visual-reference migration

Status: COMPLETE.

- `Entity.md` was migrated unchanged to `content/entities/entity-visual-reference.md`.
- The legacy `assets/canon/Entity.md` copy was removed; registry compatibility preserves the logical source identity `Entity.md`.
- Registry classification remains `ENTITY_REFERENCE / REFERENCE`; this migration does not promote visual prose into Entity mechanics, spawn, damage, loot or route authority.
- Tests verify that only the structured copy ships and that expected visual-reference content remains present.
- Compatibility loading and retrieval behavior remain unchanged.

### Phase 6C.4 — Backrooms aura phenomenon migration

Status: COMPLETE.

- `Backrooms_Linh_Khi.md` was migrated unchanged to `content/phenomena/backrooms-linh-khi.md`.
- The legacy `assets/canon/Backrooms_Linh_Khi.md` copy was removed; registry compatibility preserves the logical source identity.
- Registry classification remains `ENVIRONMENT / UNCLASSIFIED / CANDIDATE`; migration does not promote the source to CURRENT authority.
- The source's HARD LOCK remains unchanged: Backrooms aura is unusually dense, while its origin remains `UNKNOWN`.
- Tests verify that only the structured copy ships and that the UNKNOWN-origin lock remains present.
- Compatibility loading and retrieval behavior remain unchanged.

### Phase 6C.5 — Cultivator aura-effects migration

Status: COMPLETE.

- `Backrooms_Linh_Khi_Anh_Huong_Tu_Si.md` was migrated unchanged to `content/phenomena/backrooms-linh-khi-anh-huong-tu-si.md`.
- The legacy `assets/canon/Backrooms_Linh_Khi_Anh_Huong_Tu_Si.md` copy was removed; registry compatibility preserves the logical source identity.
- Registry classification remains `ENVIRONMENT / UNCLASSIFIED / CANDIDATE`; this migration does not promote cross-character statements above their owning character canon.
- Existing hard locks remain unchanged: dense aura improves available resources but does not imply automatic breakthrough, infinite energy, immunity to Backrooms rules, or universal cultivation amplification.
- Tests verify that only the structured copy ships and that the key anti-overreach locks remain present.
- Compatibility loading and retrieval behavior remain unchanged.

### Phase 6C.6 — Cao-family history migration

Status: COMPLETE.

- `Huyet_Tay_Cao_Gia.md` was migrated unchanged to `content/history/huyet-tay-cao-gia.md`.
- The legacy `assets/canon/Huyet_Tay_Cao_Gia.md` copy was removed; registry compatibility preserves the logical source identity.
- Registry classification remains `HISTORY / UNCLASSIFIED / UNCLASSIFIED`; the move does not resolve or elevate overlapping Cao Minh / Diệp Minh character material.
- Tests verify that only the structured copy ships and that the core history markers remain present.
- Compatibility loading and retrieval behavior remain unchanged.

Phase 6C.7 will migrate the Táng Kiếm Cốc history source separately, still without reconciling POV/backstage conflicts.

Expand content without changing transaction semantics:

```
content/
  world/
  levels/
  sublevels/
  entities/
  items/
  factions/
  phenomena/
characters/
history/
wiki/
codex/
continuity/
```

Canon Registry and retrieval provide bounded context to both Planner and validators. Content growth must not create new direct state-mutation paths.

## Rollout rule

Only one phase becomes authoritative at a time. A phase moves forward only after its regression tests and current Android CI are green. The previous authoritative path remains available until the next phase has demonstrated parity and save integrity.
