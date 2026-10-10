# Native audit #517: measured budgets, pending qualification

The historical S25_QA_BUDGETS_V1.md describes fake-provider/JVM/Python SQLite
measurements. Its numbers are not Android Core budgets and do not establish
acceptance for this candidate. Existing 1,000/5,000-turn runs belong to different
commits and emulator hosts; they are not same-device comparative evidence.

The new runner installs and hashes the candidate debug APK and instrumentation
APK, records the checked-out SHA and emulator fingerprint, and runs one batch
on each API24/API35 emulator. Production Context slot creation verifies packaged
genesis pins. The workload is existing native Core WAIT capture, prepare and
atomic SQLite commit, using the existing deterministic test callback. It adds no
production RNG or reducer. There is no provider, dialogue or positive perception
fixture in this workload. Scope is BACKEND_WAIT_ONLY.

Each emulator runs a fresh-slot 300-turn pilot. Before any qualification trial,
the runner writes an immutable budget artifact from this predetermined rule:

* Each stage's warm p95 ceiling is max(5ms, twice pilot warm p95).
* Each stage's first-turn ceiling is max(10ms, twice pilot first-turn latency).
* Logical row-change ceiling is twice the maximum pilot logical row changes.
* Total warm-turn p95 ceiling is max(10ms, twice pilot total warm-turn p95).
* Warm p95 excludes the first 20 turns; all raw samples remain in the artifact.

Three independent fresh-slot, fresh-app-process trials follow, each 1,000 actual
commits, with checkpoints at 500 and 1,000. The freeze artifact is hashed before
trials and checked after each. Failed trials never alter thresholds. Cold means
an app process restarted by force-stop, not a cleared OS/page cache. Reopen is
a same-process close/open measurement, not a reboot or power-loss claim.

Raw per-turn records contain elapsed nanoseconds for native snapshot context
decode (not provider context or retrieval), admit, lock, reserve,
prepare, commit and receipt retry; writer-connection SQLite total_changes delta;
RNG draws; and observed DB/WAL/SHM/journal byte sizes. SQLite total_changes is
logical rows affected (including rolled-back changes), not SQL statement counts,
physical writes, fsyncs or wear. Sidecar sizes are footprints, not I/O counts.
Physical I/O and SQL statement counts remain NOT_MEASURED.

Revision17's native events and original receipt must survive 500/1,000 commits
and reopen without additional RNG, snapshot mutation or duplicate revision.
Empty actor observations/memory are labelled EMPTY_EVIDENCE_ONLY. This cannot
prove positive private-promise retrieval, actor leakage prevention with real
observations, integrated decision/action/UI performance, or voice/agency quality.

Run only after all intended source changes are complete:

```
python3 android-apk/run-companion-native-audit.py --api 24 --apk PATH --test-apk PATH --output NEW_DIRECTORY
```

The dedicated exact-head workflow also runs full Core tests, generated46-patch
and G0 parity, then real SQLite/crash smoke and this batch. It publishes artifacts
even on failure. No release, merge, or old-slot import is performed. The R1 gate
is separate and rejects these backend artifacts as integrated qualification.

Status at authoring: NOT_RUN. No measured budgets, trial PASS, candidate APK
qualification, physical-device measurement or human playtest is claimed here.
Keep #517/#518 open until their complete requested evidence exists.
