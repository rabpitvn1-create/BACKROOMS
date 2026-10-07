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
| `WorldProgressionCore.kt` | `RANK_PER_FULL_LEVEL`, `WorldNodeId`, `WorldNodeKind`, `WorldNode`, `WorldEdge`, `TransitionResult`, `RankLookup`, `WorldProgressionCore` (registry, `rankOf`, `validateTransition`, legacy id helper) |
| `EntityScaling.kt` | `EntityScaling.percentOfBase(rank)`, `EntityScaling.scale(base, rank)` — pure, deterministic |
| `ProgressionMigration.kt` | `COMBAT_SCHEMA_VERSION_1/2`, `MigrationResult`, `ProgressionMigration` (level-number, world-state and combat-entity v1→v2) |
| `ProgressionConflict.kt` | `ProgressionConflict`, `ProgressionConflictPolicy.detect/normalizeClaim` — detection shape only |

Tests in `app/src/test/.../core/progression/`:

| File | Locks |
|---|---|
| `WorldProgressionCoreTest.kt` | unique ids, strictly increasing ranks, valid edges, Levels 0–6 pinned, fail-closed lookup & transitions, golden registry snapshot, sub-level insertion property |
| `EntityScalingTest.kt` | percent table from the contract, bit-identical legacy scaling, determinism/monotonicity, negative-rank rejection, base+rank-only derivation |
| `ProgressionMigrationTest.kt` | level-number migration, fail-closed unknowns, verbatim materialized stats, idempotency, world state stores only the node id |
| `ProgressionConflictTest.kt` | claim normalization, mismatch detection without mutating progression |

Deliberately untouched in this phase: `EntityStatCore.java` (legacy),
`CombatChoiceEngine.java`, `Combat93Runtime.kt`, `GameCoreFacade.kt`,
`StateReducer.kt`, all `patch-*.py` scripts.

## 3. Registry & rank design

```kotlin
WorldNode(id = "level-1.sub-a", kind = SUB_LEVEL, progressionRank = 1_500_000L, levelNumber = 1)
```

- `NODES` is the canonical order (traversal / UI / routing). `EDGES` is the
  traversal graph; today it is the linear chain 0→1→…→6 and can grow explicit
  edges later.
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

```
percentOfBase(rank) = 100 + rank * 10 / RANK_PER_FULL_LEVEL
scale(base, rank)   = max(0, ((max(0, base) * percentOfBase(rank) + 50) / 100))
```

Contract table:

| rank | percent |
|---|---|
| 0 | 100% |
| 500,000 | 105% |
| 1,000,000 | 110% |
| 1,500,000 | 115% |
| 2,000,000 | 120% |

Note: the formula is deliberately **not** the literal `100 + rank/100` — it
normalizes by `RANK_PER_FULL_LEVEL` so Sub-level fractional ranks keep full
precision. For legacy Levels (`rank = N * 1_000_000`) it reduces to exactly
`100 + 10*N` with identical rounding to the old `EntityStatCore`, which the
`legacyLevelsMigrateBitIdentical` test proves across sample bases.

## 5. Migration v1 → v2

v1 semantics: `stageIndex` = level number, stored in world `levelJson.number`
and in combat entity JSON. v2 semantics: `worldNodeId` + `progressionRank`;
`stageIndex` is never reused with a new meaning.

```
legacy Level N  →  node id "level-N"  →  rank N * RANK_PER_FULL_LEVEL
```

- World state: `migrateWorldStateV1ToV2` writes **only** `worldNodeId`. Rank is
  always derived via `rankOf`, never persisted as an override. Unknown level
  → `progressionMigrationError`, no `worldNodeId` written.
- Combat entity JSON: `migrateCombatEntityV1ToV2` is idempotent. It **adds**
  `combatSchemaVersion: 2`, `worldNodeId`, `progressionRank` and preserves
  `hp / maxHp / damage / baseHp / baseDamage` verbatim — no mid-fight rescale.
  The new rank applies to new encounters only. Unknown `stageIndex` records
  the error and keeps materialized stats untouched.

Example:

```jsonc
// v1
{ "key": "smiler", "hp": 37, "maxHp": 110, "attack": 12,
  "baseHp": 100, "baseDamage": 11, "stageIndex": 2, "stagePercent": 120 }
// v2 (after migration)
{ "key": "smiler", "hp": 37, "maxHp": 110, "attack": 12,
  "baseHp": 100, "baseDamage": 11, "stageIndex": 2, "stagePercent": 120,
  "combatSchemaVersion": 2, "worldNodeId": "level-2", "progressionRank": 2000000 }
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

## 8. Test plan → invariant mapping

| Invariant (issue #453 §8) | Covered by |
|---|---|
| WorldNode ID unique | `nodeIdsAreUnique` |
| Canonical order / traversal valid | `canonicalOrderHasStrictlyIncreasingRanks`, `everyEdgeReferencesExistingNodes` |
| Every edge → existing node | `everyEdgeReferencesExistingNodes` |
| Rank explicit & valid | `goldenRegistrySnapshot`, strict-increase test |
| Existing Levels pin rank | `legacyLevelsZeroToSixArePinned`, `goldenRegistrySnapshot` |
| Adding Sub-level doesn't change old ranks | `addingSubLevelDoesNotChangeExistingRanks` |
| New node doesn't change existing encounter stats | `addingSubLevelDoesNotChangeExistingRanks` + `legacyLevelsMigrateBitIdentical` |
| Unknown node fail closed | `unknownNodeRankLookupFailsClosed`, rejection tests, migration failure tests |
| AI/Gemini cannot authoritative-write progression | Architectural: §6 + recommended CI grep guard (not unit-testable; enforced by code review) |
| Legacy 0–6 v1→v2 bit-identical | `legacyLevelsMigrateBitIdentical`, `legacyLevelNumberMigratesToExpectedNodeAndRank` |
| Old active combat keeps HP | `combatEntityV1ToV2PreservesMaterializedStats`, idempotency test |
| Effective stats derive only from base + Core rank | `scaleDerivesOnlyFromBaseAndRank`, API shape (no other inputs exist) |

## 9. Non-goals (explicitly out of scope)

- Rewiring combat start / facade / state reducer to the new API.
- Real Sub-level content or traversal edges beyond the linear chain.
- Prompt / GM-context changes for conflict repair.
- Any balance change to Levels 0–6.
- Deleting or altering the legacy `EntityStatCore`.

## 10. Review checklist

- [ ] Rank semantics honest and linear as specified (no compounding).
- [ ] `RANK_PER_FULL_LEVEL = 1_000_000L` granularity is sufficient.
- [ ] Fail-closed behavior acceptable in both cases (transition-time, load-time).
- [ ] v1→v2 migration shape covers all save shapes you care about.
- [ ] Trust-boundary change list (§6) is complete before implementation starts.
- [ ] Nothing in this branch changes runtime behavior (additive only).
