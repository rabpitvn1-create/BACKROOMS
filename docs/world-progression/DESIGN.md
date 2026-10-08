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
  (load-time). No silent fallback to Level 0 / rank 0. Version handling is
  exact: v1 migrates, v2 validates, future versions reject.
- **Legacy Levels 0–6 migrate bit-identical.** Nothing in the existing game
  changes by a single stat point.
- **Rewards stay discrete per full Level.** Sub-level fractional ranks never
  mint fractional rewards: `discreteLevelOf(rank) = rank / RANK_PER_FULL_LEVEL`.
  The core victory reward keeps its legacy compounding `1.5^level` semantics
  (exact rational, no floats); treasure amounts stay linear; first-kill
  bucket keys stay discrete-level strings, so the persisted
  `treasureEntityStageKills` shape is unchanged.
- **Transition graph preserves the legacy rule.** Any Level 0..6 is reachable
  from any other once the Core exit gate passes — deliberately NOT a new
  adjacent-only restriction. Sub-levels get no automatic edges.

## 2. File / class / API map

New package `com.rabpit.backroom.core.progression` (all new, all additive):

| File | Contents |
|---|---|
| `WorldProgressionCore.kt` | `RANK_PER_FULL_LEVEL`, `WorldNodeId`, `WorldNodeKind`, `WorldNode`, `WorldEdge`, `TransitionResult`, `RankLookup`, `WorldProgressionCore` (registry, explicit complete-graph `EDGES` preserving the legacy any-target rule, rank-domain `init` check, `rankOf`, `validateTransition`, legacy id helper) |
| `EntityScaling.kt` | `EntityScaling.percentOfBase(rank)` (debug/UI only, truncated), `EntityScaling.scale(base, rank)` — pure, deterministic, full fixed-point precision, `MAX_PROGRESSION_RANK` domain |
| `RewardScaling.kt` | `RewardScaling.discreteLevelOf`, `scaledCoreReward` (legacy 1.5^level as exact rational), `treasureBucketKey`, `treasureKillReward` — rewards discrete per full Level |
| `ProgressionMigration.kt` | `PROGRESSION_SCHEMA_VERSION_1/2`, `MigrationResult`, `BoundaryLoadOutcome`, `EncounterRankResolution`, `ProgressionMigration` (`loadCombatBoundary` exact dispatch, boundary migration, v2 validation, `resyncEntityAlias`, `resolveEncounterRank` — v2 load never trusts persisted rank) |
| `WorldSaveMigration.kt` | `WORLD_NODE_SAVE_VERSION = 4`, `needsMigration`, `migrateV3World` — seam + spec for the `GameStateCodec` 3→4 wiring |
| `ProgressionConflict.kt` | `ProgressionConflict`, `ProgressionConflictPolicy.detect/normalizeClaim` — detection shape only |

Tests in `app/src/test/.../core/progression/`:

| File | Locks |
|---|---|
| `WorldProgressionCoreTest.kt` | constant pinned to literal, unique ids, strictly increasing ranks, valid edges, complete legacy graph, no automatic sub-level edges, self-transition idempotent, Levels 0–6 pinned, fail-closed lookup & transitions, literal golden registry snapshot, rank-domain enforcement, sub-level insertion property |
| `EntityScalingTest.kt` | percent table from the contract, fractional-precision proof (scale bypasses truncated percent), bit-identical legacy scaling, determinism/monotonicity, invalid-rank rejection, base+rank-only derivation |
| `RewardScalingTest.kt` | discrete-level mapping, bit-identical legacy 1.5^ rewards, discrete rewards for sub-levels, legacy treasure bucket keys, discrete linear treasure amounts |
| `ProgressionMigrationTest.kt` | real `combat93.state` boundary fixture, exact version dispatch (v1/v2/future), single root version, whole `entities[]` migration, verbatim materialized stats, alias resync, idempotency, v2 validation + rank correction, v2-load rank distrust, fail-closed unknowns, world state stores only the node id |
| `WorldSaveMigrationTest.kt` | realistic v3 world map through the 3→4 step, idempotent passthrough, no-level worlds untouched, unknown level fail-closed, target version pinned |
| `ProgressionConflictTest.kt` | claim normalization, mismatch detection without mutating progression |

Deliberately untouched in this phase: `EntityStatCore.java` (legacy),
`CombatChoiceEngine.java`, `Combat93Runtime.kt`, `GameCoreFacade.kt`,
`StateReducer.kt`, all `patch-*.py` scripts.

## 3. Registry & rank design

```kotlin
WorldNode(id = "level-1.sub-a", kind = SUB_LEVEL, progressionRank = 1_500_000L, levelNumber = 1)
```

- `NODES` is the canonical order (traversal / UI / routing). `EDGES` is an
  EXPLICIT graph — never derived from `NODES` order, so adding a Sub-level
  to canonical order cannot silently rewire traversal.
- **Transition semantics (deliberate decision):** the legacy `set_level` rule
  is preserved exactly — any Level 0..6 is reachable from any other once the
  Core exit gate passes (the old code clamped the AI-proposed number to 0..6
  and no-op'd on same-level). This is NOT a new adjacent-only restriction;
  tightening it is a separate gameplay decision kept out of this refactor.
  The exit gate (`canTransition`: `confirmedExit` flag or `levelExit` roll)
  stays a gameplay precondition evaluated by the caller BEFORE
  `validateTransition`, which enforces graph authority only. Self-transitions
  commit idempotently, matching the legacy no-op.
- Sub-levels get NO automatic edges: each one is added here explicitly when
  its gameplay route is designed.
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

## 4b. Reward scaling (discrete per full Level)

Entity HP/damage was not the only progression consumer. `resolveEntityDeath`
(`CombatChoiceEngine.java` ~L1960–1990) also scales rewards off the legacy
`stageIndex`:

| Consumer | Legacy semantics | New semantics |
|---|---|---|
| Core victory reward | `base * 1.5^stage` (`scaledCoreReward`, compounding, float `Math.pow`) | `RewardScaling.scaledCoreReward(base, rank)`: same 1.5^level, exact rational `roundHalfUp(base * 3^L / 2^L)`, no floats |
| Treasure first/repeat amounts | `EntityStatCore.scale(base, stage)` (linear) | `RewardScaling.treasureKillReward(base, rank)`: linear, discrete level |
| First-kill bucket `treasureEntityStageKills[key][stage]` | stage int string | `RewardScaling.treasureBucketKey(rank)`: discrete-level string — persisted shape UNCHANGED (`"2"` stays `"2"`) |

Locked decisions:

- **Rewards are discrete per full Level** (`discreteLevelOf(rank) =
  rank / RANK_PER_FULL_LEVEL`). A sub-level never mints fractional rewards
  and never inflates the economy: inserting content cannot silently change
  reward economics. Considered and rejected: proportional-to-difficulty
  rewards (couples economy to content insertion).
- **Honest naming:** the victory reward IS compounding (1.5^level) while
  entity stats are linear (+10pp) — the two progressions deliberately differ
  and are documented as such.
- **No legacy `stageIndex` authority:** none of these paths may keep reading
  the authoritative stage from JSON; they take `progressionRank` (entity
  death context) and derive the discrete level internally.
- Sub-levels share their discrete level's treasure bucket, so no new
  first-kill bonuses appear when content is inserted.

Bit-identical proofs: `RewardScalingTest` checks `scaledCoreReward` against
`Math.round(base * 1.5^stage)` for stages 0..10 and `treasureKillReward`
against the legacy linear formula for levels 0..6.

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

**Exact version dispatch** (`loadCombatBoundary` — fail closed):

| `progressionSchemaVersion` | Action |
|---|---|
| missing / 1 | migrate (v1 → v2) |
| 2 | validate: `worldNodeId` present + known, else reject; rank snapshot mismatch → correct to authoritative + flag (metadata, not fatal) |
| anything else (e.g. 99) | explicit reject — never bypass |

**`combat.entity` alias:** projection-only. The runtime rebuilds it from
`entities[activeEntityIndex]` before every read (`syncActiveEntityAlias`);
migration re-syncs the persisted copy the same way (`resyncEntityAlias`)
and never treats it as an independent record.

## 5b. GameState save-version wiring (3 → 4)

`CURRENT_SAVE_VERSION = 3` (`GameState.kt`) routes `version >= 3` straight
into `decodeCurrent()`, so v3 saves would never reach any progression
migration. The implementation PR must:

1. Bump `CURRENT_SAVE_VERSION` 3 → 4.
2. Add the routing branch `version == 3 -> migrateV3ToV4(root)` (before the
   `>=` branch), which runs `WorldSaveMigration.migrateV3World` over the
   decoded world map, stamps `saveVersion = 4`, then delegates to
   `decodeCurrent` (untouched), marking `migratedFromVersion = "3"`.
3. Fail closed: unresolvable levels keep the original world with
   `progressionMigrationError` recorded — no guessed `worldNodeId`.

The exact `decode()` diff is specified in `WorldSaveMigration`'s KDoc;
`WorldSaveMigrationTest` runs a realistic v3 world map through the step.
(`GameStateCodec` is touched by 18 patch scripts — see §7 — so the
implementation PR must reconcile those anchors.)

## 6. Trust-boundary changes (implementation phase)

Current holes (all verified on latest main):

1. `GameCoreFacade.kt:145` takes `levelJson` from the Gemini `candidate`
   and forwards it through `ValidatedLegacyStateCommand`
   (`StateReducer.kt:51` writes it to `state.world["levelJson"]`), which
   `Combat93Runtime.stageIndex()` then reads for scaling. The AI therefore
   indirectly supplies the scaling input today.
2. **Transition authority lives in patch-generated code.** `set_level{level}`
   handling and `canTransition(before, rolls)` (confirmedExit flag or
   `levelExit` roll) are injected by `patch-ai-orchestrator.py` /
   `patch-drive-canon-gameplay.py` / `patch-an-nhien-follower.py` — i.e.
   build-time generated code, not committed Core source. Moving transition
   authority into Core means superseding these patch-injected versions
   (disposition: retire/supersede in the implementation PR).
3. **Reward paths read authoritative `stageIndex` from JSON**
   (`CombatChoiceEngine.resolveEntityDeath` ~L1960–1990):
   `scaledCoreReward` (1.5^stage), treasure first/repeat amounts
   (`EntityStatCore.scale`), and the `treasureEntityStageKills` bucket key.
   All three must move to `RewardScaling` (§4b); none may keep the legacy
   stage as authority.

Required changes (not in this branch — listed for the implementation PR):

1. `GameCoreFacade`: treat `candidate.level` as a **narrative claim only**.
   Never let it become progression authority.
2. Introduce a Core-internal transition path: gameplay/Core events check the
   exit gate, then call `WorldProgressionCore.validateTransition(from, to)`;
   only a `Committed` result writes the authoritative `worldNodeId` into
   `GameState.world` (via `StateReducer`, not via `ValidatedLegacyStateCommand`).
   The patch-injected `set_level`/`canTransition` versions are superseded.
3. `EntityStatCore` call sites (`CombatChoiceEngine.startInternal`,
   `Combat93Runtime.stageIndex`) resolve `worldNodeId → rankOf → scale`,
   ignoring any rank/scale/percent fields arriving from outside Core.
4. Reward call sites (`resolveEntityDeath`) resolve
   `worldNodeId → rankOf → RewardScaling`; the `1.5^stage` float path and
   the stage-keyed treasure bucket become the discrete-level versions.
5. Narrative repair: when `ProgressionConflictPolicy.detect(claim, authoritative)`
   returns a conflict, keep the authoritative node, attach
   `progressionConflict { claimed, actual }` to the next GM context
   (`GameCoreFacade.contextFor`), and emit an audit event.
6. Recommended CI guard (note: the GitHub App cannot edit workflows, so this
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

### Patch-chain map for the implementation phase (refreshed 2026-10-08 on current main)

Implementation will edit exactly the files the patch chain also mutates.
Before implementation starts, each touching patch needs a disposition:
**leave** (orthogonal — touches other methods), **update anchor** (textual
anchor sits on a signature being changed), **retire/supersede** (purpose
superseded by the new Core authority). The map below is generated from
current main (`git grep` over all `patch-*.py`; chain = 42 scripts):

| Implementation target | Touching patches (current main) | Risk notes |
|---|---|---|
| `GameCoreFacade.kt` — trust boundary #1 (`candidate.level` → narrative claim) | 29 scripts, incl. `patch-game-state-core-bridge.py`, `patch-combat-93-runtime.py`, `patch-final-authority-hardening.py`, `patch-inventory-authority-finalize.py`, `patch-item-source-authority-final.py`, **`patch-item-registry-core.py` (new)** | `patch-item-registry-core.py` edits with hard `RuntimeError` anchors on facade/reducer/combat files — any signature change nearby must keep those anchors matching. `patch-inventory-authority-finalize.py` also rewrites `ValidatedLegacyStateCommand`/`GameCommand`. Triage all 29 before the implementation PR. |
| `CombatChoiceEngine.java` / `Combat93Runtime.kt` — trust boundary #3 (`stageIndex` → `rankOf`) and reward paths (#4) | `patch-combat-93-runtime.py`, **`patch-item-registry-core.py` (new)** | **Known break:** `patch-combat-93-runtime.py:46` anchors on `Combat93Runtime.stageIndex(current)`. Renaming/replacing that API *will* break the patch → disposition **update anchor** in the same implementation PR. |
| `set_level` / `canTransition` (transition authority) | `patch-ai-orchestrator.py`, `patch-conditional-audit.py`, `patch-final-authority-hardening.py`, `patch-knowledge-context-builder.py`, `patch-rejected-op-repair-final.py` (+ `canTransition` in `patch-drive-canon-gameplay.py`, `patch-an-nhien-follower.py`, `patch-progression-snapshot-equipment.py`, `patch-special-followers-025.py`, `patch-visual-state-sync-final.py`) | Transition authority currently lives in **patch-generated** code. Disposition: **retire/supersede** — move the exit-gate + op handling into committed Core source; the patch-injected versions must not survive alongside. |
| `StateReducer.kt` — authoritative `worldNodeId` commit path | `patch-item-registry-core.py`, `patch-item-source-authority-final.py`, `patch-kai-resource-policy-final.py`, `patch-omnivault-instance-authority-finalize.py`, `patch-rest-physiology-state-finalize.py`, `patch-search-action-false-warning.py` | The new Core-internal transition command must not collide with existing command anchors. |
| `GameStateCodec.kt` — save versioning (3→4) | 17 scripts, incl. `patch-character-stat-schema.py`, `patch-combat-hp-metadata-cleanup.py`, `patch-poker-dice-core-backport.py`, `patch-item-source-authority-final.py` | The `decode()` routing change must be reconciled with `patch-character-stat-schema.py` and `patch-combat-hp-metadata-cleanup.py` anchors specifically. |
| Reward paths (`scaledCoreReward`, `treasureEntityStageKills`) | none — committed source only | No patch risk; safe to refactor in Core. |
| `EntityStatCore.java` — untouched | none | No patch references it; safe. |

Rule for the implementation PR: after the source edits, run the **full**
patch chain plus `:app:testDebugUnitTest` in CI on the branch before asking
for review. A source edit that a patch silently overwrites, or a patch whose
anchor no longer matches, must fail loudly there — not in a release build.

(Note: the GitHub App cannot edit `.github/workflows/*`; any workflow change
this plan needs, e.g. a CI grep guard, must be applied manually.)

## 8. Test plan → invariant mapping

| Invariant (issue #453 §8 + re-review) | Covered by |
|---|---|
| WorldNode ID unique | `nodeIdsAreUnique` |
| Canonical order / traversal valid | `canonicalOrderHasStrictlyIncreasingRanks`, `everyEdgeReferencesExistingNodes`, `legacyLevelsAreFullyConnected`, `subLevelsGetNoAutomaticEdges` |
| Every edge → existing node | `everyEdgeReferencesExistingNodes` |
| Rank explicit & valid; constant pinned as literal; ranks within scaler domain | `rankPerFullLevelIsPinned`, `goldenRegistrySnapshotUsesLiteralRanks`, strict-increase test, `allNodeRanksWithinScalerDomain` (+ `init` fail-fast) |
| Existing Levels pin rank | `legacyLevelsZeroToSixArePinned`, `goldenRegistrySnapshotUsesLiteralRanks` |
| Adding Sub-level doesn't change old ranks | `addingSubLevelDoesNotChangeExistingRanks` |
| New node doesn't change existing encounter stats | `addingSubLevelDoesNotChangeExistingRanks` + `legacyLevelsMigrateBitIdentical` |
| Transition semantics = legacy any-target rule (deliberate, not adjacent-only) | `legacyLevelsAreFullyConnected`, `distantTransitionIsAllowedPreservingLegacyRule`, `selfTransitionIsIdempotent` |
| Unknown node fail closed | `unknownNodeRankLookupFailsClosed`, rejection tests, migration failure tests, `invalidRankFailsClosed`, `resolveEncounterRankFailsClosedForUnknownNode` |
| AI/Gemini cannot authoritative-write progression | Architectural: §6 + recommended CI grep guard (not unit-testable; enforced by code review). Patch-injected `set_level`/`canTransition` superseded per §7 map |
| Legacy 0–6 v1→v2 bit-identical | `legacyLevelsMigrateBitIdentical`, `legacyLevelNumberMigratesToExpectedNodeAndRank` |
| Old active combat keeps HP | `v1BoundaryMigratesWithSingleRootVersion` (real boundary fixture), idempotency test |
| Effective stats derive only from base + Core rank | `scaleDerivesOnlyFromBaseAndRank`, API shape (no other inputs exist) |
| Fractional ranks keep full precision | `scaleKeepsFractionalPrecision` (scale bypasses truncated int percent) |
| v2 load never trusts persisted rank; exact version dispatch | `v2LoadNeverTrustsPersistedRank`, `v2RankMismatchIsCorrectedToAuthoritative`, `futureVersionIsRejectedNotBypassed`, `v2WithUnknownNodeIsRejected`, `v2WithMissingNodeIdIsRejected` |
| `combat.entity` alias is projection-only | `entityAliasIsResyncedProjectionOnly` |
| Rewards discrete per full Level; economy stable on content insert | `discreteLevelOfIgnoresFractionalRanks`, `scaledCoreRewardIsDiscretePerFullLevel`, `treasureBucketKeyKeepsLegacyShape`, `treasureKillRewardIsDiscreteAndLinear` |
| Reward 1.5^level bit-identical without floats | `scaledCoreRewardKeepsLegacyCompoundingBitIdentical` |
| v3 saves actually reach the migration (3→4 wiring) | `v3WorldMigratesToAuthoritativeNodeId`, `alreadyMigratedWorldPassesThrough`, `worldWithoutLevelJsonNeedsNoMigration`, `unknownLevelFailsClosedWithoutGuessing`, `targetSaveVersionIsPinned` |

## 9. Non-goals (explicitly out of scope)

- Rewiring combat start / facade / state reducer / reward paths to the new API.
- Real Sub-level content or new traversal edges.
- Prompt / GM-context changes for conflict repair.
- The `GameStateCodec` 3→4 edit itself (specified in §5b, applied in implementation).
- Any balance change to Levels 0–6.
- Deleting or altering the legacy `EntityStatCore`.

## 10. Review checklist

- [ ] Rank semantics honest and linear as specified (no compounding for entity
      stats; the 1.5^ victory reward is honestly documented as compounding).
- [ ] `RANK_PER_FULL_LEVEL = 1_000_000L` granularity is sufficient; the
      literal is pinned by test.
- [ ] Fractional-rank precision: `scale()` uses the full rational, integer
      percent is debug-only.
- [ ] Rewards discrete per full Level (decision locked; alternative
      proportional-to-difficulty considered and rejected — confirm).
- [ ] Transition graph = legacy any-target rule preserved (deliberate, not
      adjacent-only — confirm; tightening is a separate gameplay decision).
- [ ] Fail-closed behavior acceptable in all cases (transition-time,
      load-time, out-of-domain ranks, future versions).
- [ ] v1→v2 migration shape covers the real `combat93.state` boundary
      (root + `combat` + all `entities[]`); version lives in one place;
      `combat.entity` documented as projection-only.
- [ ] GameState 3→4 wiring spec complete (§5b); `decodeCurrent` untouched.
- [ ] Patch-chain map (§7, refreshed on current main) is complete enough to
      start implementation triage — incl. superseding patch-injected
      `set_level`/`canTransition`.
- [ ] Trust-boundary change list (§6) is complete before implementation starts.
- [ ] Nothing in this branch changes runtime behavior (additive only).
