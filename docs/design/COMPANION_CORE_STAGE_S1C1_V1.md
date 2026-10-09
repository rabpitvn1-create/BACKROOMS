# Companion Core Stage S1c.1 V1

Date: 2026-10-09. Parent #476. Owner: Ponytail under continued owner delegation.
Depends on isolated S1a/S1b #479/#480 at e3247b3. First S1c slice; not full S1c.

## Scope

CompanionCoreStage accepts an existing native GameState and native-resolved typed
commands. It detaches state through the production GameStateCodec and executes the
existing TurnCoordinator.createPending/commit. It does not call GameCoreFacade,
SharedPreferences, SQLite, UI, providers, action-kind resolution or any RNG. It builds
no second reducer. Candidate snapshots/events are staging outputs, not durable receipts
or a Core-authorized final companion batch. The ordinary game bridge never calls it.

The current codec normalizes on decode. Staging therefore requires exact GameState
round-trip equality before execution and after success; normalization is an explicit
error, never an additional unreviewed mutation. Unsupported save versions, live Core
pending records, completed turns, empty/oversized batches, wrong turn IDs, duplicate
command IDs and reused IDs reject. Production CommandValidator preflights every command before the coordinator query-only
exception can skip invalid later queries. Existing Core validators retain actor/source/target
and gameplay authority. A failure returns no partial candidate and leaves source state
unchanged. Commands with mutable metadata are copied; candidate outputs are immutable
strings and defensive unmodifiable ID/event lists. IDs include actual coordinator
injected commands, including deterministic rest commands, in Core execution order.

This seam expects a stable, natively loaded Core snapshot. It is not a loader or a
legacy importer. Codec equality does not replace canonical hashing or final manifest
validation. Candidate construction is not an authentication boundary. Caller must not
feed candidate JSON back as proof of Core approval.

## Verification

Full authoritative 46-script patch chain generated before local compilation. Production
Core Kotlin/Java plus the new staging tests compile locally using Kotlin 2.3.0, JVM17,
API16 compile stubs and the existing JUnit/JSON dependencies. One pre-existing warning
in generated CombatRuntime remains; no strict whole-tree warning claim.

16 JUnit cases pass against the real generated Core: exact coordinator parity,
gameplay/time atomicity, late batch failure, duplicate/reused commands, completed/pending
turns, cross-turn/actor confusion, player pickup authority, bounds/version rejection,
codec normalization rejection, immutable/deterministic output and injected rest commands.
Four existing TurnCoordinator tests also pass (20 total).
These are host tests, not Android execution. PR CI must compile/test/build the Android
APK and rerun the inherited API24/API35 storage/crash matrix before this group is green.

## Next S1c slices and qualification

Still required: bind native locked decision, current Core snapshot/revision and persisted
complete RNG tape; extract existing roll capture without fallback draws; build and verify
typed batch/manifest; atomically publish snapshot/ledger/receipt with SQLite compare-and-set;
prove fault and postcommit-callback recovery. S2 supplies immutable ledger/provenance.
Do not relax S1b phase checks to enable COMMITTED until these integrations exist.

Owner clarified that only physical-device measurements are deferred. CI, crash/rollback,
idempotency and 1k/5k/10k benchmark runs on available environments remain required.
S2.5 physical-device results must be recorded as deferred, not passed. No merge/release.
Rollback: revert this unconnected staging slice; no ordinary-game save data is affected.
