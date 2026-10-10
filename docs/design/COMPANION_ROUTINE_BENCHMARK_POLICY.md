# Routine companion benchmark policy

Owner instruction, 2026-10-09: measure a few hundred turns instead of several
thousand on each iteration. Ponytail implements the routine default as 300 turns
on both Android API24 and API35.

This supersedes the routine 1k/5k benchmark requirements in earlier slice notes.
The full Core unit suite, APK build, authoritative 46-patch chain, generated-source
parity, real SQLite cases and fresh-process crash recovery stay required.
No runtime Core rules, storage format, RNG, scene or persona is changed.

The 300-turn run must emit actual p50/p95/p99, snapshot/DB/WAL and sampled memory
measurements, check exact committed revision/time/draw counts, retrieve event and
receipt17 without redraw, and reopen/verify the committed chain. The host runner
requires a real 300-turn result plus a matching verified reload and PASS.
Reducing loop length must not create an empty or unverified report.

The runner still accepts explicit 1k/5k/10k runs for final qualification. They are
reserved for that qualification or a concrete scalability diagnosis, rather than
automatically repeated on every small PR. Final mixed-workload/three-trial/cold-warm
and writes-per-turn requirements remain outstanding; a 300-turn regression cannot
certify turn10k retrieval, long-run scalability or physical-device performance.

Existing completed long-run evidence remains valid for its exact head/workload.
Already-running earlier-head jobs are not relabeled as 300-turn jobs. No long-run
performance acceptance or full S2.5 completion is claimed by this configuration.

Validation is the exact-head CI run using the updated instrumentation and runner
on API24/API35. No local test or green result is claimed before CI completes.
No merge/release/activation is performed.
