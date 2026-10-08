# Design: Core-owned world progression & deterministic Entity scaling

Issue: #453 · Phase: **design / skeleton only** · Status: for review, no behavior change.

This branch is additive: it introduces the Core model, the pure scaling API, the
migration shape and the tests that lock the invariants. It does **not** rewire
`CombatChoiceEngine`, `Combat93Runtime`, `GameCoreFacade` or any gameplay flow —
that is implementation phase, after this design is approved.

## 1. Locked contract

- **Balance semantics: linear over base.** One full Level step adds **10 percentage
  points over base**. Not compounding. Documented honestly — never described as
  "+10% versus the previous node".
- **Fixed-point rank:** `RANK_PER_FULL_LEVEL = 1_000_000L`.
  `percentOfBase(rank) = 100 + rank * 10 / RANK_PER_FULL_LEVEL` (integer math).
- **Single base stats.** Entities keep exactly one set of base HP / damage.
  Effective stats derive only from `(base stat, Core progressionRank)`.
- **No ordinal scaling.** Difficulty never comes from list position, `indexOf`,
  display names, canon text or location strings.
- **Explicit immutable rank.** A new node's rank is balance data written
  explicitly in code. A midpoint between neighbours is only a designer
  suggestion, never Core authority.
- **Core owns transition authority.** AI / Gemini output is a narrative claim,
  never progression authority.
- **Fail closed.** Unknown node ids: reject the transition and keep the
  authoritative node (transition-time), or raise a clear migration error
  (load-time). No silent fallback to Level 0 / rank 0.
- **Legacy Levels 0–6 migrate bit-identical.** Nothing in the existing game
  changes by a single stat point.

## 2. File / class / API map

New package `com.rabpit.backroom.core.progression` (all new, all additive):

| File | Contents |
|---|---|
| `WorldProgressionCore.kt` | `RANK_PER_FULL_LEVEL`, `WorldNodeId`, `WorldNodeKind`, `WorldNode`, `WorldEdge`, `TransitionResult`, `RankLookup`, `WorldProgressionCore` (registry, explicit `EDGES`, `rankOf`, `validateTransition`, legacy id helper) |
| `EntityScaling.kt` | `EntityScaling.percentOfBase(rank)` (debug/UI only, truncated), `EntityScaling.scale(base, rank)` — pure, deterministic, full fixed-point precision |
| `ProgressionMigration.kt` | `PROGRESSION_SCHEMA_VERSION_1/2`, `MigrationResult`, `EncounterRankResolution`, `ProgressionMigration` (boundary-level v1→v2, per-entity step, world-state step, `resolveEncounterRank` — v2 load never trusts persisted rank) |
| `ProgressionConflict.kt` | `ProgressionConflict`, `ProgressionConflictPolicy.detect/normalizeClaim` — detection shape only |

Tests in `app/src/test/.../core/progression/`:

| File | Locks |
|---|---|
| `WorldProgressionCoreTest.kt` | constant pinned to literal, unique ids, strictly increasing ranks, valid edges, explicit traversal graph, Levels 0–6 pinned, fail-closed lookup & transitions, literal golden registry snapshot, sub-level insertion property |
| `EntityScalingTest.kt` | percent table from the contract, fractional-precision proof (scale bypasses truncated percent), bit-identical legacy scaling, determinism/monotonicity, invalid-rank rejection, base+rank-only derivation |
| `ProgressionMigrationTest.kt` | real `combat93.state` boundary fixture, single root version, whole `entities[]` migration, verbatim materialized stats, idempotency, v2-load rank distrust, fail-closed unknowns, world state stores only the node id |
| `ProgressionConflictTest.kt` | claim normalization, mismatch detection without mutating progression |

Deliberately untouched in this phase: `EntityStatCore.java` (legacy),
`CombatChoiceEngine.java`, `Combat93Runtime.kt`, `GameCoreFacade.kt`,
`StateReducer.kt`, all `patch-*.py` scripts.

## 3. Registry & rank design

```kotlin
WorldNode(id = "level-1.sub-a", kind = SUB_LEVEL, progressionRank = 1_500_000L, levelNumber = 1)
```

- `NODES` is the canonical order (traversal / UI / routing). `EDGES` is an
  EXPLICIT edge list — deliberately not derived from `NODES` order, so adding
  a Sub-level to canonical order can never silently rewire traversal.
  Today it is the linear chain 0→1→…→6, written out edge by edge; new
  gameplay edges are added explicitly.
- Level N is pinned to `N * RANK_PER_FULL_LEVEL` forever. Inserting any number
  of Sub-levels cannot move a Level — by construction, not by discipline.
- A Sub-level takes an explicit rank strictly between its neighbours
  (e.g. `1_500_000L` between Level 1 and Level 2 → 115%). The designer chooses
  the number; CI enforces strict increase along canonical order.
- The golden snapshot test pins every existing `(id, rank)` pair. Changing a
  rank is a deliberate balance commit that must update the test in the same
  diff — silent rebalancing becomes impossible.

Why not scale from the ordinal: an ordinal fuses "position in the list" with
"difficulty", so inserting a node renumbers everything after it. Separating
`order` (the list) from `difficulty` (the immutable rank) is the entire point.

## 4. Scaling math

True percent (rational): `100 + rank * 10 / RANK_PER_FULL_LEVEL`.

`scale(base, rank)` computes with the FULL fixed-point rational and never
truncates through an integer percent:

```
scale = base * (100 + rank * 10 / R) / 100
      = base + roundHalfUp(base * rank / (10 * R))      [R = RANK_PER_FULL_LEVEL]
```

implemented as `base + (2 * base * rank + 10_000_000) / 20_000_000`
(Long-safe inside the supported domain `rank in 0..1_000_000_000`;
invalid ranks fail closed via `require`, never clamp).

`percentOfBase(rank)` returns the TRUNCATED integer percent and is
debug/UI display only — it is not authoritative for computation, and
`scale()` does not call it. The `scaleKeepsFractionalPrecision` test proves
the separation: rank 1_050_000 → integer percent 110, but `scale(100, …)`
→ 111 (true 110.5%).

Contract table (true percents):

| rank | percent |
|---|---|
| 0 | 100% |
| 500,000 | 105% |
| 1,000,000 | 110% |
| 1,050,000 | 110.5% |
| 1,500,000 | 115% |
| 2,000,000 | 120% |

For legacy Levels (`rank = N * RANK_PER_FULL_LEVEL`) the formula reduces to
exactly `(base * (100 + 10*N) + 50) / 100` with identical rounding to the old
`EntityStatCore`, which `legacyLevelsMigrateBitIdentical` proves across
sample bases. The golden test also pins `RANK_PER_FULL_LEVEL == 1_000_000L`
as a literal and the registry snapshot uses literal ranks, so changing the
constant cannot silently rebalance every level while keeping tests green.

## 5. Migration v1 → v2

v1 semantics: `stageIndex` = level number, stored in world `levelJson.number`
and in the `combat93.state` boundary (`combatStageIndex` at root,
`combat.stageIndex`, `combat.entities[].stageIndex/stagePercent`).
v2 semantics: `worldNodeId` + `progressionRank` snapshot metadata;
`stageIndex` is never reused with a new meaning (old fields stay frozen).

```
legacy Level N  →  node id "level-N"  →  rank N * RANK_PER_FULL_LEVEL
```

- **Boundary level** (`migrateCombatBoundaryV1ToV2`): the version lives in
  exactly ONE place — the boundary root (`progressionSchemaVersion: 2`).
  Root `combatStageIndex` resolves once to `(nodeId, rank)`; the snapshot is
  written at root, on `combat`, and on every entry of `combat.entities[]`.
  Every materialized field (hp/maxHp/attack/status/dice/encounterId/…)
  stays verbatim — no mid-fight rescale. Unknown `combatStageIndex`
  records `progressionMigrationError` and writes nothing else.
- **World state**: `migrateWorldStateV1ToV2` writes **only** `worldNodeId`.
  Rank is always derived via `rankOf`, never persisted as an override.
- **v2 load rule** (`resolveEncounterRank`): NEVER trust a persisted
  `progressionRank` as authority. The authoritative rank always derives from
  the world's authoritative `worldNodeId` via `rankOf`; the persisted value
  is snapshot metadata, a mismatch is audit info, and the authoritative
  rank wins. Unknown authoritative node → null → fail closed.

Example (root + one entity; dice/participants abbreviated):

```jsonc
// v1 boundary (real shape)
{ "turn": 7, "combatStageIndex": 2, "location": "Level 2",
  "combat": { "active": true, "round": 3, "stageIndex": 2,
    "entities": [{ "key": "smiler", "hp": 37, "maxHp": 110, "attack": 12,
                    "baseHp": 100, "baseDamage": 11,
                    "stageIndex": 2, "stagePercent": 120, "status": "alive" }],
    "diceState": { "values": [1,2,3,4,5], "rerollsUsed": 1 },
    "encounterId": "turn-9:0:smiler" } }
// v2: same JSON + at root:
{ "progressionSchemaVersion": 2, "worldNodeId": "level-2", "progressionRank": 2000000 }
// ...and on "combat" and on every entity:
{ "worldNodeId": "level-2", "progressionRank": 2000000 }
// legacy stageIndex/stagePercent/combatStageIndex fields untouched.
```

## 6. Trust-boundary changes (implementation phase)

Current hole: `GameCoreFacade.kt:145` takes `levelJson` from the Gemini
`candidate` and forwards it through `ValidatedLegacyStateCommand`
(`StateReducer.kt:51` writes it to `state.world["levelJson"]`), which
`Combat93Runtime.stageIndex()` then reads for scaling. The AI therefore
indirectly supplies the scaling input today.

Required changes (not in this branch — listed for the implementation PR):

1. `GameCoreFacade`: treat `candidate.level` as a **narrative claim only**.
   Never let it become progression authority.
2. Introduce a Core-internal transition path: gameplay/Core events call
   `WorldProgressionCore.validateTransition(from, to)`; only a `Committed`
   result writes the authoritative `worldNodeId` into `GameState.world`
   (via `StateReducer`, not via `ValidatedLegacyStateCommand`).
3. `EntityStatCore` call sites (`CombatChoiceEngine.startInternal`,
   `Combat93Runtime.stageIndex`) resolve `worldNodeId → rankOf → scale`,
   ignoring any rank/scale/percent fields arriving from outside Core.
4. Narrative repair: when `ProgressionConflictPolicy.detect(claim, authoritative)`
   returns a conflict, keep the authoritative node, attach
   `progressionConflict { claimed, actual }` to the next GM context
   (`GameCoreFacade.contextFor`), and emit an audit event.
5. Recommended CI guard (note: the GitHub App cannot edit workflows, so this
   needs a manual workflow edit): grep that `progressionRank` / `worldNodeId`
   are never read from `candidate` in `GameCoreFacade`.

## 7. Source of truth vs build-time mutation

- **Source of truth:** committed files under `android-apk/app/src/main/**`.
  The new `core/progression/` package lives here as ordinary committed source —
  it is Core authority and must not be generated.
- **Build-time mutation:** `android-apk/patch-*.py`, applied by CI in the fixed
  order listed in `.github/workflows/build-backroom-apk.yml`
  ("Apply Android runtime patch chain"). They mutate the tree before Gradle
  builds; they are not the place for authority logic.
- Verified: no patch script generates or references `EntityStatCore`; it is
  committed source. The same applies to the new files in this branch.

### Patch-chain map for the implementation phase

Implementation will edit exactly the files the patch chain also mutates.
Before implementation starts, each touching patch needs a disposition:
**leave** (orthogonal — touches other methods), **update anchor** (textual
anchor sits on a signature being changed), or **retire** (purpose superseded).
The map below is generated from the current tree (`grep` over `patch-*.py`):

| Implementation target | Touching patches | Risk notes |
|---|---|---|
| `GameCoreFacade.kt` — trust boundary #1 (`candidate.level` → narrative claim) | 27 scripts, incl. `patch-game-state-core-bridge.py`, `patch-combat-93-runtime.py`, `patch-final-authority-hardening.py`, `patch-inventory-authority-finalize.py`, `patch-item-source-authority-final.py` | `patch-game-state-core-bridge.py` only wires facade construction (low risk). `patch-inventory-authority-finalize.py` also rewrites `ValidatedLegacyStateCommand`/`GameCommand` — anchor-risk where the trust-boundary change lands. Each of the 27 needs the leave/update/retire triage before the implementation PR. |
| `CombatChoiceEngine.java` / `Combat93Runtime.kt` — trust boundary #3 (`stageIndex` → `rankOf`) | `patch-combat-93-runtime.py` | **Known break:** line 46 anchors on `Combat93Runtime.stageIndex(current)`. Renaming/replacing that API *will* break the patch → disposition **update anchor** in the same implementation PR. |
| `StateReducer.kt` — authoritative `worldNodeId` commit path | `patch-item-source-authority-final.py`, `patch-kai-resource-policy-final.py`, `patch-omnivault-instance-authority-finalize.py`, `patch-rest-physiology-state-finalize.py`, `patch-search-action-false-warning.py` | Triage per patch; the new Core-internal transition command must not collide with existing command anchors. |
| `GameStateCodec.kt` — save versioning | 18 scripts (incl. `patch-character-stat-schema.py`, `patch-combat-hp-metadata-cleanup.py`, `patch-poker-dice-core-backport.py`) | The v1→v2 save changes must be reconciled with `patch-character-stat-schema.py` and `patch-combat-hp-metadata-cleanup.py` anchors specifically. |
| `EntityStatCore.java` — untouched | none | No patch references it; safe. |

Rule for the implementation PR: after the source edits, run the **full**
patch chain plus `:app:testDebugUnitTest` in CI on the branch before asking
for review. A source edit that a patch silently overwrites, or a patch whose
anchor no longer matches, must fail loudly there — not in a release build.

(Note: the GitHub App cannot edit `.github/workflows/*`; any workflow change
this plan needs, e.g. a CI grep guard, must be applied manually.)

## 8. Test plan → invariant mapping

| Invariant (issue #453 §8) | Covered by |
|---|---|
| WorldNode ID unique | `nodeIdsAreUnique` |
| Canonical order / traversal valid | `canonicalOrderHasStrictlyIncreasingRanks`, `everyEdgeReferencesExistingNodes`, `traversalGraphIsExplicit` |
| Every edge → existing node | `everyEdgeReferencesExistingNodes` |
| Rank explicit & valid; constant pinned as literal | `rankPerFullLevelIsPinned`, `goldenRegistrySnapshotUsesLiteralRanks`, strict-increase test |
| Existing Levels pin rank | `legacyLevelsZeroToSixArePinned`, `goldenRegistrySnapshotUsesLiteralRanks` |
| Adding Sub-level doesn't change old ranks | `addingSubLevelDoesNotChangeExistingRanks` |
| New node doesn't change existing encounter stats | `addingSubLevelDoesNotChangeExistingRanks` + `legacyLevelsMigrateBitIdentical` |
| Unknown node fail closed | `unknownNodeRankLookupFailsClosed`, rejection tests, migration failure tests, `invalidRankFailsClosed`, `resolveEncounterRankFailsClosedForUnknownNode` |
| AI/Gemini cannot authoritative-write progression | Architectural: §6 + recommended CI grep guard (not unit-testable; enforced by code review) |
| Legacy 0–6 v1→v2 bit-identical | `legacyLevelsMigrateBitIdentical`, `legacyLevelNumberMigratesToExpectedNodeAndRank` |
| Old active combat keeps HP | `boundaryMigrationVersionsRootOnceAndMigratesAllEntities` (real boundary fixture), idempotency test |
| Effective stats derive only from base + Core rank | `scaleDerivesOnlyFromBaseAndRank`, API shape (no other inputs exist) |
| Fractional ranks keep full precision | `scaleKeepsFractionalPrecision` (scale bypasses truncated int percent) |
| v2 load never trusts persisted rank | `v2LoadNeverTrustsPersistedRank` (tampered snapshot → authority wins) |

## 9. Non-goals (explicitly out of scope)

- Rewiring combat start / facade / state reducer to the new API.
- Real Sub-level content or traversal edges beyond the linear chain.
- Prompt / GM-context changes for conflict repair.
- Any balance change to Levels 0–6.
- Deleting or altering the legacy `EntityStatCore`.

## 10. Review checklist

- [ ] Rank semantics honest and linear as specified (no compounding).
- [ ] `RANK_PER_FULL_LEVEL = 1_000_000L` granularity is sufficient; the
      literal is pinned by test.
- [ ] Fractional-rank precision: `scale()` uses the full rational, integer
      percent is debug-only.
- [ ] Fail-closed behavior acceptable in both cases (transition-time, load-time),
      including out-of-domain ranks.
- [ ] v1→v2 migration shape covers the real `combat93.state` boundary
      (root + `combat` + all `entities[]`); version lives in one place.
- [ ] v2 load never trusts persisted `progressionRank` (authority always
      re-derived from the authoritative node).
- [ ] Traversal graph explicit, not derived from canonical order.
- [ ] Patch-chain map (§7) is complete enough to start implementation triage.
- [ ] Trust-boundary change list (§6) is complete before implementation starts.
- [ ] Nothing in this branch changes runtime behavior (additive only).
