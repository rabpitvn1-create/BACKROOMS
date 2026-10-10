# R1 Final Integration Qualification Report V1

**Issue:** #518. **Status:** QUALIFICATION INCOMPLETE — see §5.
**Date:** 2026-10-09. **Version:** `r1_report.v1`.

## 1. Scope

Final qualification of the #498→#517 companion ladder (20 draft PRs #519–#538).
This report covers what was actually run. Anything not listed here was not run.

## 2. Evidence — what actually passed

### 2.1 JVM unit tests: 104/104 PASS

All 9 companion test suites, compiled with kotlinc 2.0.21 and run on Temurin
JRE 17 (no Android SDK):

| Suite | Tests | Result |
|---|---|---|
| P2aFixturesTest (RB/RG/RM/RP02) | 18 | OK |
| ActorContextBuilderTest (C1) | 8 | OK |
| DecisionPreflightTest (A1a) | 12 | OK |
| CharacterDecisionOrchestratorTest (A1b) | 8 | OK |
| TalkExecutorTest (A2a) | 9 | OK |
| MoveSearchInspectExecutorTest (A2b) | 11 | OK |
| UseItemCombatNoneExecutorTest (A2c) | 14 | OK |
| CompanionSlotLifecycleTest (UI1a) | 13 | OK |
| InteractUiContractTest (UI1b) | 11 | OK |

### 2.2 Pipeline pilot: 3 independent trials, consistent

300 routine iterations each, end-to-end
(preflight → orchestration → TALK → belief):

| Trial | per-iter |
|---|---|
| 1 | 0.733 ms |
| 2 | 0.617 ms |
| 3 | 0.694 ms |

All within the frozen 5 ms/turn budget. 300/300 decided, 300/300 spoken per trial.

### 2.3 Storage pilot

300 turns, sqlite3 WAL: 5.5 rows/turn (budget ≤ 10), warm p95 0.213 ms
(budget 2 ms), cold p95 0.578 ms (budget 5 ms), retrieval spot-check pass.

### 2.4 Frozen budgets (#517)

All measured values within `qa_budgets.v1`. No relaxation applied.

## 3. Known issue — GitHub Actions regression (BLOCKER)

**"Verify Novel Asset Canon" fails on every muse/* branch (#520–#538), passes
on the base branch `test/companion-routine-benchmark-300` (4ec2ae6f).**

- Failing step: "Verify complete JVM suite and assemble debug APK"
  (`gradle :app:testDebugUnitTest :app:assembleDebug`).
- PR #519 (docs-only) did not trigger the workflow; all code branches fail.
- Investigation (without log access — API returns 403):
  - Not a duplicate class or test name.
  - Not org.json (project provides real org.json for tests).
  - Not a patch-chain conflict in depended-upon files (CompanionExposurePolicy
    unmodified by patches; no patch script references new files).
  - JVM standalone compilation and 104/104 tests pass, so the failure is
    specific to the Gradle environment (full source set, Kotlin 2.3.0,
    patched tree, or APK assembly).
- **Root cause undetermined. This blocks any claim of "CI green".**

## 4. Not verified (explicitly out of scope for this report)

- Physical-device measurements (deferred per project direction).
- 1k/5k/10k actual turns; 300 iterations is not turn10k proof.
- Real provider attempts, audits against live provider, fallback behavior.
- Human agency/voice playtest.
- Candidate APK (no APK was assembled here).
- Power-loss / kill-before-after-commit proofs.
- Android SQLite API 24/35 behavior.

## 5. Qualification verdict

**NOT QUALIFIED.** The ladder is functionally complete on JVM with frozen
budgets met, but the CI regression (§3) plus the unverified items (§4) mean
R1 cannot be signed off. No merge, no release.

## 6. Rollback plan

All #498→#517 work lives on **unmerged stacked draft branches**; `main` is
untouched. Rollback is abandonment, in reverse stack order:

```
muse/s25-pilot-budgets-517 (#538)
muse/ui1b-interact-projection-516 (#537)
muse/ui1a-slot-lifecycle-515 (#536)
muse/a2c-useitem-combat-none-514 (#535)
muse/a2b-move-search-inspect-513 (#534)
muse/a2a-talk-executor-512 (#533)
muse/a1b-decision-orchestration-511 (#532)
muse/a1a-decision-preflight-510 (#531)
muse/c1-actor-context-builder-509 (#530)
muse/p2d-mood-reducer-508 (#529)
muse/p2c-goal-reducer-507 (#528)
muse/p2b-belief-reducer-506 (#527)
muse/p2a-brain-contracts-505 (#526)
muse/m2b-memory-retrieval-504 (#525)
muse/m2a-episodic-memory-503 (#524)
muse/p1b-genesis-pins-502 (#523)
muse/m1b4-observation-retrieval-501 (#522)
muse/m1b3-observation-store-500 (#521)
muse/m1b2-perception-adapter-499 (#520)
muse/m1b1-native-evidence-contract-498 (#519)
```

- No legacy save data was touched; no migration was run.
- No workflow files were modified by this ladder.
- Each PR is independently closable; closing a child does not affect its parent.

## 7. Remaining risks

1. CI regression root cause (§3) — highest priority before any merge.
2. The full patch-chain + Gradle interaction was never reproduced locally
   (no Android SDK in this environment).
3. Provider seam is untested against a real provider.
4. ExitStreak authority preserved by contract, not by integration test.
