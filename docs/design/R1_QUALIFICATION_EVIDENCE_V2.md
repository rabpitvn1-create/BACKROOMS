# R1 evidence v2 — #518

This is an evidence collection contract, not a completed qualification report.
There are no trial measurements, successful checks, human approvals, or fabricated
results in this document. The owner reduced the audit to 500–1,000 actual turns;
real-device measurements remain deferred. Emulator SQLite/crash evidence does not
prove hardware power-loss safety. No merge or release is authorized by this gate.

`android-apk/companion-qualification-gate.py` reviews a completed manifest using
only the Python standard library. It does not build, test, contact a provider,
change saves, or query GitHub. Run it once on the collected evidence after the
integrated candidate is ready:

```sh
python android-apk/companion-qualification-gate.py evidence/manifest.json --output evidence/review-result.json
```

Exit 1 and `NOT_QUALIFIED` mean missing, inconsistent, out-of-budget, or invalid
evidence. Exit 0 means `EVIDENCE_COMPLETE`, requiring human authenticity and scope
review; it does not approve a release. Hashes pin bytes and cannot establish that
measurements were honestly collected. The reviewer must inspect raw traces,
source provenance, and linked CI runs. A backend WAIT-only pilot must declare
`runtime_scope: backend_wait_pilot` and can never qualify the integrated companion.

## Manifest skeleton

Replace every placeholder with real collected evidence. Empty fields intentionally
fail the validator. Save all artifacts relative to the manifest directory, list
the exact SHA-256 of every artifact's bytes, and retain the underlying logs. No
absolute paths, path escapes, or implicit current-head lookup are accepted.

```json
{
  "schema": "companion-r1-evidence-v2",
  "candidate_head": "",
  "runtime_scope": "integrated_companion",
  "apk_sha256": "",
  "apk_artifact": "candidate_apk",
  "budget_artifact": "frozen_budgets",
  "provider_artifact": "provider_cases",
  "lifecycle_artifact": "lifecycle_cases",
  "human_playtest_artifact": "human_playtest",
  "build_artifact": "build_cases",
  "ci_artifact": "exact_head_ci",
  "artifacts": {},
  "trials": []
}
```

An artifact declaration is `"name": {"path": "raw/name.json", "sha256": "..."}`.
The APK is a binary artifact, not an evidence JSON file. The candidate head is a
full 40-character Git SHA. All metrics/proofs/builds/CI/APK refer to that candidate.

## Frozen budgets and trials

Freeze the budget artifact before starting any trial. It contains
`candidate_head`, `frozen_at_unix_ms`, and nonempty numeric `limits`. Recognized
per-turn maximum keys are `admission_ms`, `context_ms`, `provider_ms`, `commit_ms`,
`reload_ms`, and `logical_sql_rows`. An unknown or missing measurement fails.
Budgets are an explicit owner/reviewer choice, never retrospectively copied from
results or inflated after a failure. Aggregate summaries can supplement raw
samples but cannot replace them. Warm-up turns must be marked and retained; do
not delete inconvenient samples to improve an average.

Collect at least three independently initialized native trials on **each** API24
and API35. Each trial runs at least 1,000 actual turns, retaining checkpoints at
500 and 1,000. Distinct trial IDs are necessary but insufficient to prove
independence; record fresh process/slot/reset provenance in the accompanying logs.
The validator compares workload hash, APK hash, and runtime between environments;
Android/device/SQLite versions must also be recorded. Workload must include
no-op, dialogue, event, retrieval, retry, and crash turns, with both cold and warm
samples. Cold means a new process/database open without reused prepared in-memory
state; warm means the same process with documented initialized caches. Do not
label a cleared JSON helper as a native cold database trial.

Each trial descriptor contains:

```json
{
  "trial_id": "",
  "api": 24,
  "candidate_head": "",
  "budget_artifact": "frozen_budgets",
  "started_at_unix_ms": null,
  "environment": {
    "device": "", "android_build": "", "sqlite_version": "",
    "runtime": "", "workload_sha256": "", "apk_sha256": ""
  },
  "samples_artifact": "",
  "checkpoints_artifact": "",
  "crash_artifact": ""
}
```

The samples artifact is an array numbered consecutively from 1. Each row records
`turn`, `head`, `source: native_runtime`, `kind`, `mode`, `slot_id`, `request_id`,
`receipt_sha256`, `rng_tape_sha256`, `logical_sql_rows`, and `latency_ms` containing
all five stages `admission`, `context`, `provider`, `commit`, `reload`.
Rows also record observed integer counters `unauthorized_mutations`,
`cross_actor_leaks`, `cross_slot_leaks`, `duplicate_commits`, `partial_batches`,
and `rng_rerolls`, all zero. An omitted counter is not evidence of zero.

`logical_sql_rows` measures logical database work. It does not measure flash writes,
fsyncs, energy, or disk traffic. `physical_io` must explicitly be either
`{"status":"unavailable"}` or a measured object containing `status: measured`,
`method`, `read_bytes`, and `write_bytes`. Record actual instrumentation limitations;
never multiply logical rows into an invented physical-I/O estimate.

## Checkpoints, crash, provider and lifecycle proof

A checkpoint artifact maps keys `"500"` and `"1000"` to proof artifact names. At
both milestones prove `event17_receipt17_no_reroll`, `actor_private_promise`, and
`correction_reload`. These refer to the retained original event17 and receipt17,
private actor memory/promise visibility, and corrected knowledge after native
reload, rather than similarly named fabricated JSON objects.

Every proof artifact contains `candidate_head` and `cases`. Each case has `name`,
`source`, `expected`, `actual`, and `trace_artifact`. Expected and actual must match,
and the actual outcome must include the observable fields required in the gate's
`OUTCOMES` table. Raw traces are nonempty arrays of operations with `head`, `source`,
`operation`, and `observed`. Source is `native_runtime`, except build proofs use
`build_pipeline`. Logs remain the primary evidence; matching summary booleans are
insufficient for reviewer acceptance.

Required case names and additional evidence:

| Proof | Required cases | Review focus |
|---|---|---|
| Each trial crash | before_commit, after_commit, ambiguous_readback, retry, cross_slot | Process kill/fault boundaries, durable readback, identical receipt/RNG tape, untouched foreign slot |
| Provider | provider_attempt, provider_audit, repair, fallback, no_extra_rng | Actual transport attempts, persisted audits/raw response/request digest, bounded repairs, fail-closed fallback |
| Lifecycle | rollback, new_game, cache_preservation, old_slot_rejected_without_mutation | Original slot byte/state evidence, fresh identity, incompatible saves preserved |
| Build | generated_46_patch_parity, g0_parity, source_provenance | Exactly46 authoritative patches, generated source parity, protected G0 source and dependency versions |

The `OUTCOMES` keys define a minimum machine-checkable observation contract.
Retain request/actor/slot IDs, before/after state, commit/readback traces, and native
source anchors so a reviewer can verify each observation. Explicit fault injection
and emulator process kill must be identified separately from real hardware power
loss. Current fail-closed unknown perception is not proof of working dialogue,
provider integration, positive sensory events, private promises, or corrections.

## Human review and applicable CI

Human playtest evidence contains `candidate_head`, `reviewer`, `played_at`, and an
`observations` array including both `voice` and `agency` categories. Every observation
needs `scenario`, `notes`, and explicit `accepted: true`. Human judgments cannot be
generated by a script or replaced with a persona fixture matching a string.

CI evidence contains `candidate_head`, a nonempty `applicable_workflows` inventory,
and actual `runs`. Each required workflow has a completed successful run with
`workflow`, `head_sha`, `run_id`, `url`, and successful linked `jobs`. Review the
entire recursive/main-target chain and provenance before choosing the inventory;
an incomplete inventory does not exempt a required check. Cite real GitHub run
and job links. A docs-only check marked not applicable is not a successful runtime
check. Do not reuse an older green head for a new candidate.

The final report must separately list remaining risks, deferred device measurements,
rollback instructions, and the tested candidate APK link/digest. Do not close #518
while an integrated-runtime requirement is missing. A report explaining
`NOT_QUALIFIED` is useful audit output, not acceptance of unfinished functionality.

## Relationship to #517 backend audit

The #517 `native_backend_audit.v2` format is deliberately different: it measures
native WAIT storage staging (`context_decode`, `admit`, `lock`, `reserve`, `prepare`, `commit`,
`receipt_retry`), SQLite `total_changes` deltas, sidecar file
sizes, and reopen time. Sidecar bytes are file sizes, not physical I/O traffic.
SQL statement counts and physical I/O are explicitly `NOT_MEASURED` when no
instrumentation exists. Its empty actor-observation/memory counts are
`EMPTY_EVIDENCE_ONLY`, not private promise or positive memory isolation evidence.

A pilot300 may establish the predetermined budget rule before the actual trials:
warm stage p95 ceiling `max(5ms, 2 × pilot p95)`, first-turn cold ceiling
`max(10ms, 2 × pilot first-turn)`, and logical-row ceiling `2 × pilot maximum`.
Exclude the first20 turns only from warm p95, retain all raw samples, force-stop
before every independent process, and record fresh slot IDs. Budget rules and
resulting thresholds are frozen before the500/1000 trials; any adjusted thresholds
require fresh trials. These aggregate pilot budgets are not interchangeable with
the integrated gate's per-turn limits. A backend manifest therefore requires an
explicit backend review and cannot be relabeled into the integrated gate schema.
