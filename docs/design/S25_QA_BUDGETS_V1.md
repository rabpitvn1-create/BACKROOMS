# S2.5 QA Budgets V1 — FROZEN

**Status:** FROZEN (issue #517). Review: Ponytail, before R1 qualification (#518).
**Frozen:** 2026-10-09. **Version:** `qa_budgets.v1`.

Budgets below are set from actual pilot measurements (same environment, same
workload). They are not relaxed after a failed result — a miss triggers
targeted diagnosis, not a budget change.

## What was measured (and what was NOT)

Measured:
- **Pipeline pilot** (`CompanionPilotBench`, 300 routine iterations per #497):
  preflight → orchestration (fake provider) → TALK execution → belief reduce,
  on JVM (kotlinc 2.0.21, Temurin 17). Per-stage avg/p50/p95/max.
- **Storage pilot** (`s25_storage_pilot.py`, 300 turns, Python sqlite3, WAL mode):
  per-turn writes (2 observations + 2 manifest rows + 1 memory + FK stubs),
  warm vs cold connection, DB/WAL/page sizes, retrieval correctness spot-check.

NOT proven (explicitly out of scope):
- No physical-device measurement (deferred per project direction).
- 300 iterations is NOT turn10k proof.
- Different hosts are NOT same-device comparative proof.
- No Android SQLite (API 24/35) run; no emulator run.

## Frozen budgets

### Pipeline (JVM, per turn)

| Stage | Measured p95 | Budget p95 |
|---|---|---|
| preflight | 0.250 ms | 1 ms |
| orchestration | 0.374 ms | 1 ms |
| TALK execute | 0.243 ms | 1 ms |
| belief reduce | 0.258 ms | 1 ms |
| end-to-end per turn | ~0.7 ms avg | 5 ms |

### Storage (sqlite3, per turn)

| Metric | Measured | Budget |
|---|---|---|
| rows written per turn | 5.5 | ≤ 10 |
| warm per-turn p95 | 0.213 ms | 2 ms |
| cold per-turn p95 | 0.578 ms | 5 ms |
| retrieval correctness | spot-check pass | must pass |

### Physical I/O note

The 300-turn pilot left 4.1 MB in WAL (85 pages, 315 KB DB). Budgets above
cover logical writes; physical I/O (checkpoint timing, fsync) is a
device-measurement concern and stays open until real-device runs.

## Raw pilot artifacts

Pipeline (300 iters):
```
PILOT iterations=300 decided=300 spoken=300 beliefs=1
preflight_ms avg=0.292 p50=0.034 p95=0.250 max=59.661
orchestrate_ms avg=0.177 p50=0.070 p95=0.374 max=13.751
talk_ms avg=0.126 p50=0.031 p95=0.243 max=11.157
belief_ms avg=0.100 p50=0.029 p95=0.258 max=9.048
pipeline_total_ms=208.305 per_iter_ms=0.694
```

Storage (300 turns):
```
PILOT turns=300 observations=660 memories=330 manifest_rows=660
rows_per_turn=5.5 (2 obs + 2 manifest + 1 memory)
warm_per_turn_ms avg=0.145 p50=0.128 p95=0.213 max=0.651
cold_per_turn_ms avg=0.405 p50=0.386 p95=0.578 max=0.586
db_bytes=315392 wal_bytes=4140632 pages=85 page_size=4096
retrieval_spotcheck=mem-329 (expect mem-329)
```

Note: `beliefs=1` in the pipeline pilot is correct — all 300 claims shared one
proposition/speaker, so BR01 idempotency deduplicated them (the realistic
steady-state path).
