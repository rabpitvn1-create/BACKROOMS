# Core-owned world progression and deterministic Entity scaling

Issue #453. Design/skeleton only: existing gameplay call sites are unchanged.
The skeleton defines executable migration/scaling contracts. Runtime enforcement,
Core transitions, narrative repair and save-codec wiring require a separate
implementation review; skeleton tests do not prove those paths are secured today.

## Contract

- Stable `WorldNodeId`; explicit immutable `progressionRank`, never list position,
  display name, canon text, location, AI claims or an automatic midpoint.
- `RANK_PER_FULL_LEVEL = 1_000_000L`. Entity stats are linear over base:
  one full Level adds 10 percentage points, not 10% of the previous node.
- Supported rank is `0..1_000_000_000L` for every consumer, including rewards.
- Full fixed-point scaling: `base + roundHalfUp(base * rank / 10_000_000)`.
  `percentOfBase` truncates for display only; it is never a scaling input.
- Levels 0–6 retain literal ranks 0, 1M, 2M, 3M, 4M, 5M, 6M and legacy rounding.
- Rewards remain discrete per full Level. Victory rewards compound by `1.5^L`,
  computed exactly with standard-library `BigInteger` and saturated to
  `Int.MAX_VALUE`. Treasure amounts are linear and first-kill keys remain
  discrete Level strings. Fractional ranks share the parent Level reward bucket.
- Legacy Levels 0–6 retain their complete directed graph (42 edges).
  Both new full Levels and Sub-levels require deliberate edge declarations;
  adding registry content never creates routes automatically.
- Exit eligibility remains a Core gameplay precondition: confirmed exit or the
  existing levelExit roll. `validateTransition` validates graph membership;
  its `Committed` result is a value, not a state mutation or exit-gate bypass.
- Unknown transitions reject and retain the current node, with an audit event.
  Malformed/unknown saved nodes reject loading, with no Level 0/rank 0 fallback.

## File and API map

All files live in committed `core/progression/`, not generated patch output.

| File | Responsibility |
|---|---|
| `WorldProgressionCore.kt` | Stable IDs, ordered registry, pinned legacy graph, rank lookup, transition validation, legacy number lookup |
| `EntityScaling.kt` | Pure `scale(base, rank)` and display-only `percentOfBase(rank)` |
| `RewardScaling.kt` | Shared rank domain, exact saturated victory reward, discrete treasure amounts and keys |
| `ProgressionMigration.kt` | Version dispatch, trusted-node boundary validation, active-combat preservation, entity alias projection |
| `WorldSaveMigration.kt` | Explicit success/rejection for world migration and executable v3 save-root migration; explicit fresh-game seed |
| `ProgressionConflict.kt` | Narrative claim normalization/detection only; never authority |

The implementation will replace legacy `EntityStatCore` consumers with
`EntityScaling.scale(baseValue, progressionRank)`; the legacy class is unchanged
in this design PR. Existing base profiles remain the only balance inputs.

## Combat boundary migration and authority

The real persisted boundary is `combat93.state`: root `combatStageIndex`,
`combat.stageIndex`, `combat.entities[]` and the compatibility `combat.entity`.
The single schema field is `progressionSchemaVersion` at the boundary root.
Legacy stage fields stay frozen; they are never repurposed as fixed-point ranks.

The entry point is:

```kotlin
loadCombatBoundary(boundary, authoritativeNodeId)
```

The caller must obtain `authoritativeNodeId` from the validated Core world state,
never from the boundary or Gemini output. Its rank is resolved through `rankOf`.
Persisted root/combat/entity node IDs and ranks are snapshot metadata only.

| Schema | Action |
|---|---|
| Missing or integer 1 | Resolve the known legacy `combatStageIndex`; reject disagreement with trusted world; copy and add v2 metadata |
| Integer 2 | Validate root/combat/entity node IDs; missing or unknown IDs reject; known node/rank mismatches are corrected to trusted world and flagged |
| Malformed, fractional, string, null or unsupported version | Reject, never coerce or bypass |

Both versions require a combat object and entities array containing objects.
Active combat requires at least one entity. These are minimal structural checks,
not a replacement for the existing combat-stat/dice validator. The boundary
input is never modified on success or failure. Every entity's materialized
HP/maxHP/attack/status and all dice/encounter metadata remain unchanged.
`combat.entity` is projection-only and rebuilt from `entities[activeEntityIndex]`;
it never provides independent stats or progression authority.

A v2 correction returns `snapshotCorrected = true`; implementation must emit
an audit event before publishing the corrected state. Rejections surface a
load error. Active encounters never rescale in the middle of a fight; only
new encounters derive effective stats from the committed world rank.

## GameState save migration, v3 to v4

`WorldMigrationOutcome` and `WorldSaveLoadOutcome` have separate success and
rejection variants. An error map cannot be decoded accidentally as a v4 save.
`migrateV3Save(root)` validates the v3 world string-map, clones the root and
stamps version 4 only after successful world migration. Failed loads retain
input bytes/version for diagnostics and must not be passed to `decodeCurrent`.

World migration rules:

- Known existing `worldNodeId`: validate it and retain authority; narrative
  `levelJson` cannot override it. Remove persisted rank overrides/stale errors.
- Otherwise require valid JSON with a strict, known integer `levelJson.number`.
  Convert legacy Level N to `level-N`. Store only the node; derive rank.
- Missing, malformed or unknown state: return `Rejected`, including empty worlds.
- New game creation explicitly seeds `freshWorld()` (`level-0`). This is not a
  load fallback and must never run to conceal invalid saved state.

Implementation routing in `GameStateCodec`:

```kotlin
when (version) {
  4 -> validateWorldNodeThenDecodeCurrent(root)
  3 -> when (val result = WorldSaveMigration.migrateV3Save(root)) {
    is WorldSaveLoadOutcome.Migrated -> decodeCurrent(result.root)
    is WorldSaveLoadOutcome.Rejected -> surfaceLoadError(result.reason)
  }
  // v0–v2: existing legacy decode, then the same world validation/migration;
  // publish version 4 only on success, never stamp v4 in an intermediate step.
  // future/malformed versions: surfaceLoadError; no >= version shortcut.
}
```

The names for validation/error handling above describe required wiring; they
are not claimed to exist today. `CURRENT_SAVE_VERSION` becomes 4 only in the
implementation phase. Add `migratedFromVersion` metadata after successful load.
The codec's legacy branches must not bypass progression validation.

## Trust-boundary implementation plan

1. `GameCoreFacade` currently forwards Gemini `candidate.level` through
   `ValidatedLegacyStateCommand.levelJson`, and `StateReducer` writes it into
   world state. `Combat93Runtime.stageIndex` then reads it. Remove that authority
   chain: AI level data becomes a narrative claim only.
2. A Core-internal gameplay event evaluates the existing exit gate, calls
   `validateTransition`, and commits only the returned node through StateReducer.
   Unknown/illegal targets retain the current node and emit explicit audit.
   Do not expose a transition command accepting arbitrary Gemini IDs/ranks.
3. Combat start resolves validated world node to rank and scales base profiles.
   External JSON may not authoritative-write rank, stage, percent or stats.
   Boundary loading uses the trusted-node API above.
4. Victory and treasure paths use Core rank and `RewardScaling`, never persisted
   `stageIndex` as authority. Keep existing discrete reward keys and economy.
5. When `ProgressionConflictPolicy.detect` returns a conflict, retain authority,
   record `{ claimed, actual }`, emit audit and attach it to the next GM context
   so narrative repairs itself. Detection alone is not runtime enforcement.
6. Add integration tests after the full patch chain: forged Gemini Level 6 while
   Core is Level 2 cannot mutate authority/stats; conflict appears in next GM
   context; valid Core transition changes new encounters only; load errors
   prevent state publication; reward paths ignore forged JSON progression.

## Source of truth and patch reconciliation

Committed `app/src/main/**` is the intended source of truth. CI runs the scripts
from `.github/workflows/build-backroom-apk.yml` before compiling; text mutations
can overwrite source changes or invalidate anchors. Reconcile them in the same
implementation change and run the complete chain on a disposable checkout.

Inspection snapshot: `main` at `ed7292881ceef6f464c65875441e80c313dd451e`,
2026-10-08. The workflow lists **41** top-level scripts. This is a snapshot,
not a permanent repository policy. Direct literal file references:

| Target | Top-level chain scripts with direct references | Disposition |
|---|---|---|
| `GameCoreFacade.kt` | `patch-cao-minh-skills-equipment.py`, `patch-character-detail-avatar-fallback.py`, `patch-character-stats-1193a-restore.py`, `patch-combat-93-runtime.py`, `patch-combat-feedback-1193a.py`, `patch-game-state-core-bridge.py`, `patch-item-detail-actions.py`, `patch-item-registry-core.py`, `patch-item-source-authority-final.py`, `patch-item-whole-unit.py`, `patch-poker-dice-core-backport.py` | Preserve orthogonal edits; update command/authority anchors |
| `Combat93Runtime.kt` | `patch-item-registry-core.py` | Preserve item authority, reconcile progression anchors |
| `StateReducer.kt` | `patch-item-registry-core.py`, `patch-item-source-authority-final.py` | Preserve item handling, add Core-only transition path |
| `GameStateCodec.kt` | `patch-character-stats-1193a-restore.py` | Update routing/version anchors |
| `CombatChoiceEngine.java`, `EntityStatCore`, reward helpers | No direct references found | Keep committed source authoritative |

This direct-reference list is not the transitive mutation closure. Scripts
invoke other scripts and can use computed paths. Before runtime edits, trace
those invocations and classify each touching mutation. In particular:
`patch-combat-93-runtime.py` anchors on `Combat93Runtime.stageIndex(current)`;
update that anchor when replacing it. `set_level`/`canTransition` injected by
`patch-ai-orchestrator.py`, `patch-drive-canon-gameplay.py` and related authority
patches must be retired/superseded by committed Core handling, never retained
as a second authority path. The final generated source must be inspected and
integration-tested; checking committed source alone is insufficient.

## Verification and review gates

| Invariant | Test coverage / implementation gate |
|---|---|
| Unique IDs, explicit ranks, literal rank unit and Level pins | `WorldProgressionCoreTest` |
| Valid graph, all 42 legacy edges, no automatic future routes | `WorldProgressionCoreTest` |
| Fractional precision, deterministic linear scaling, legacy balance | `EntityScalingTest` |
| All reward ranks accepted, no overflow, discrete economy | `RewardScalingTest` |
| Exact schema dispatch, trusted world wins across every JSON layer | `ProgressionMigrationTest` |
| Missing/unknown IDs, malformed structures/version reject | `ProgressionMigrationTest` |
| All legacy Levels and multiple entities preserve materialized fields | `allLegacyLevelsPreserveEveryMaterializedEntityField` |
| Successful save migration alone stamps v4; malformed saves do not mutate | `WorldSaveMigrationTest` |
| Explicit fresh-game seed, empty saved world rejects | `WorldSaveMigrationTest` |
| Conflict detection cannot commit authority | `ProgressionConflictTest`; runtime integration remains required |
| AI cannot write progression; audit and next-turn repair enforced | Implementation integration tests above, after patch chain |

Review requires linear semantics, full shared rank domain, fail-closed loading,
trusted-node authority, explicit future routes, no mid-fight rescaling and honest
separation of skeleton coverage from runtime enforcement. Before runtime rollout,
run the full patch chain, targeted integration tests and Android unit/build CI.

Non-goals: new Sub-level content, runtime rewiring, prompt changes, balance
changes, legacy class removal, publishing a release or merging this PR.
