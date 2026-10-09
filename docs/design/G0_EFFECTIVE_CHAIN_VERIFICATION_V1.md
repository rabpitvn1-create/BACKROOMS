# G0 Effective Chain Verification V1

Date: 2026-10-09. Executor: Ponytail. Status: PARTIAL G0, RUNTIME READINESS BLOCKED.
Pinned input: main 5450284ddc2acded7cc1cc54d3c035faff4ba4be.
No production runtime was edited; generation ran in an isolated detached worktree.

## Executed evidence

| Check | Actual result | Scope |
| --- | --- | --- |
| Workflow-ordered recursive Python patch generation | PASS, 46 top-level scripts, 104 traced runpy invocations | Local generated-source reproduction |
| Workflow final runtime contract assertions | PASS, exit 0 | Existing source-marker assertions, not compilation |
| verify-four-1193a-entities.py | PASS | Packaged assets/registry/spawn windows verifier |
| verify-player-action-ime.mjs | PASS | Existing simulated IME layout verifier |
| build-novel-asset-canon-index.py --check | PASS | 11 documents, 297 excerpts, 63 retained records; 39 world locations |
| Two existing Node test files | PASS, 15 tests, zero failures | GM highlighting and snapshot combat feedback |
| Proposed SQL specimen checks | PASS, 8 cases | Host SQLite only; not Android SaveStats tests |
| JVM/Kotlin/Android compilation and unit tests | NOT RUN | No Gradle/Kotlin compiler/Android SDK available in this environment |
| APK/emulator/process-kill/power-loss/real-device tests | NOT RUN | No APK build/device run |
| Live LLM calls/trace parity/production performance | NOT RUN | No provider calls or trace implementation |
| CI on Markdown-only design head | NOT APPLICABLE to current automatic workflow paths | No empty-check GREEN claim |

The old workflow names Verify GM Core Restoration / Release Version are not present
in current .github/workflows. Test Android Game Core is manual-only; APK and relevant
verification workflows use android-apk/workflow paths. Runtime changes still require
applicable real CI GREEN; this report does not waive that gate.

## Finding G0-01: Exit v6 is an overwritten intermediate output

Actual invocation order:
1. patch-exit-discovery-engine.py generates v6 engine/test and bridge code.
2. Later patch-player-action-ime.py calls patch-exit-streak-integration.py.
3. The nested script unlinks ExitDiscoveryEngine.kt and ExitDiscoveryEngineTest.kt,
   removes retired dispatch/imports and installs EXIT_STREAK_V1.
4. Final generated MainActivity invokes ExitStreakEngine.advance and
   GameCoreFacade.processValidatedCandidateWithStreak; facade guards route source/target.

The final engine documents a 15-Unicode-code-point floor, one nextInt(2) per accepted
ordinary non-combat turn, five consecutive wins, loss resets and combat exclusions.
The source assertions are not a JVM execution of ExitStreakEngineTest.

Consequences: the earlier approved design's SEARCH-only v6 exit bonus and
traverse_exit assumptions are factually superseded by effective output. Do not
restore retired gameplay. The exact companion actor-action mapping must be reviewed
against current streak progression before A2. This remains a runtime readiness gate.
Technical Design and RFC now state the mismatch explicitly.

## Finding G0-02: audit/continuity hooks survive final generation

Generated MainActivity has writer candidate -> risk -> auditsForRisk -> hard issues
-> rejected-op checks -> localKnowledgeIssues -> one repair/re-audit -> Core commit.
At risk below 4 the audit array is empty; risk 4..6 uses canon audit, risk >=7 uses
canon and character audits. A thrown semantic audit precedes the local validators;
there is no catch in that section that guarantees local validation after audit failure.
This confirms ordering by generated-source inspection, not by injected provider faults.

StoryContinuityReducer.apply remains present after processValidatedCandidateWithStreak.
Existing subsequent narration/visual/progression handling must be audited when moving
all required projections into one SaveStats transaction; hook presence alone does not
prove the proposed atomic database architecture already exists.

Provider generated-source inspection confirms writer Gemini modelOrder {0,1,2},
auditor {2,1}, per-model/key attempt tracking and excluded-key-only second phase.
The 18/10/76 attempt figures in Orion's spec are static upper-bound calculations,
not observed HTTP counts. Deadline/cooldown/circuit behavior can reduce attempts.
No live pressure/failure-path measurement or packet trace parity has been performed.

## Reproducibility artifacts

See [effective source evidence bundle](g0-v1/effective-source-evidence.zip),
[hooks manifest](g0-v1/hooks-manifest.json), [invocation trace](g0-v1/invocations.json),
[reproduction runner](g0-v1/reproduce-chain.py) and
[SQL specimen validator](g0-v1/validate-contract-specimens.py).
The archive contains:
- invocations.json: executed runpy order, parents, depth and success per invocation.
- patch-chain.log: complete local patch execution output.
- effective-hashes.json: SHA-256 for all final Java/Kotlin/HTML/Python/Gradle sources.
- hooks-manifest.json: exact final-file hash, marker and line for selected hooks.
- MainActivity.java.txt, GameCoreFacade.kt.txt, KnowledgeContextEngine.kt.txt,
  index.html.txt and ExitStreakEngine.kt.txt: final generated source snapshots.
- reproduce-chain.py: tracing runner; run only in a clean disposable checkout.
- runtime-contracts.log (empty stdout, exit 0) and schema-specimen.log: checked result records.
- verification-results.json and verification-1..4.log: command/exit/log records for executed verifiers and Node tests.
- validate-contract-specimens.py: reproducible host SQLite design checks.

Reproduce from a clean checkout of the pinned SHA. Derive the scripts array from
build-backroom-apk.yml, then invoke reproduce-chain.py with checkout root, JSON script
array and an output directory. The wrapper traces runpy calls; it is not a general
subprocess/network profiler. Retain the workflow ordering; do not manually apply only
an early exit patch and treat that output as final.

## Required next proof

G0 is not fully GREEN: compile/unit-test the generated tree, inspect complete authority
and persistence call paths, run behavior fixtures for action/route/retry gates and
complete Android/device checks at their required phase. Resolve G0-01 in reviewed A2
contract rather than silently weakening tests or resurrecting a retired subsystem.
No S1/runtime implementation approval is implied by this partial report.

Evidence archive SHA-256: `4ca324323eef693dcc58336d8305ff1c3610cacfbdb20f115f9b82c121995701`.
