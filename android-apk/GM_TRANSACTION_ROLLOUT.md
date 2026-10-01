# GM Transaction Rollout

Status: PHASE 3 — TYPED COMMAND AUTHORITY ACTIVE IN SHADOW; V2 REMAINS AUTHORITATIVE

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

4C.3 will bind the same selection evidence into `CommitCandidate`.

Commit protocol:
1. verify turnId and baseStateHash;
2. validate all commands;
3. reject/repair invalid groups;
4. commit accepted groups through Core owners;
5. persist one authoritative state revision;
6. emit immutable committed-event ledger.

V2 remains the fallback path until parity and save-integrity gates pass.

## Phase 5 — Narrator consumes committed reality only

Narrator input is built after transaction commit from:
- CommittedTurn;
- post-commit authoritative state;
- canon packet;
- POV/knowledge view.

Narration cannot be used as the source of state. State claims require committed-event evidence. If narration fails validation, regenerate narration from the same CommittedTurn without rerunning gameplay.

The historical Almond Water bug is a release blocker in this phase.

## Phase 6 — Canon/content platform

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
