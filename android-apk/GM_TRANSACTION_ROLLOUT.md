# GM Transaction Rollout

Status: PHASE 1 — CONTRACT FROZEN, NOT WIRED TO LIVE GAMEPLAY

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

Exit gate:
- zero direct-state mutation by planner;
- deterministic replay for the same turn;
- proposal schema violations fail closed.

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

Exit gate:
- every accepted planner command maps to exactly one Core owner;
- every accepted command produces committed-event evidence;
- causal-group rollback tests cover partial failures.

## Phase 4 — Transaction commit behind a feature flag

Planner transactions can become authoritative only when the feature flag is enabled.

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
