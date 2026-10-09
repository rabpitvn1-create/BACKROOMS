# SaveStats Pending Recovery Contract V1

Date: 2026-10-09. Owner: Ponytail under the owner's continuing implementation
instruction. Status: DECIDED CONTRACT; SQLite/native integration NOT IMPLEMENTED.
Parent #476; design gate #478; design PR #475.

This specifies the missing state machine in
[Technical Design](SAVESTATS_CHARACTER_BRAIN_TECHNICAL_DESIGN_V1.md).
Existing production TurnCoordinator does not implement this durable protocol.
Its current reject() clears a pending turn; do not copy that behavior after a
companion reservation. Main remains `5450284ddc2acded7cc1cc54d3c035faff4ba4be`.

## Identity and admission

One active pending record per slot at committed storage revision N. A request alias
is a native row pointing to the same turn, not another receipt/reservation. The
canonical input digest covers protocol/schema version, actor, exact UTF-8 player
input and expected revision with a length-delimited native encoding; no trimming,
Unicode normalization, case folding, model text or caller-supplied digest authority.
The storage layer can accept a native digest, but its bridge must compute it.

Admission checks an existing request/alias first: identical digest and original
expected revision return its pending state or committed receipt, even when current
slot revision has since advanced. A reused ID with changed content/revision rejects.
An unknown request at a stale revision rejects. A fresh ID with identical input and
revision aliases the one pending turn. Different input while pending returns BUSY;
it cannot reset the decision or draw entitlement. Completed receipts never rerun
providers, RNG, dialogue, commands or mood decay.

## Durable phases

Receipt status remains PENDING until COMMITTED or pre-lock REJECTED. Pending phase
is separately PREPARING, DECISION_LOCKED, RESERVED or SUSPENDED. SUSPENDED retains
its resume phase. Do not store COMMITTING as a durable state: a SQLite transaction
either leaves the previous pending record intact or publishes the complete receipt.

| Source | Operation and guards | Result |
| --- | --- | --- |
| No pending at N | Begin native request, validated slot/version/input | PREPARING; immutable turn identity and alias. |
| PREPARING | Validate/audit decision, scene evidence and typed kind/target/duration | DECISION_LOCKED with complete immutable decision envelope and digest. |
| PREPARING | Provider failure or user pause | SUSPENDED(PREPARING); no authoritative character reaction. |
| PREPARING or SUSPENDED(PREPARING) | Cancel/reject before lock and before any draw | REJECTED; N unchanged; close aliases; new admission allowed. |
| DECISION_LOCKED | Generate and durably capture the full native reservation atomically | RESERVED with reservation ID and roll/route tape; no exposure before commit. |
| DECISION_LOCKED or RESERVED | Pause, provider/audit failure or user cancel | SUSPENDED(same phase); identity/decision/reservation retained. |
| SUSPENDED | Resume | Retained phase, no new turn/decision/roll. |
| RESERVED | Native final validation + complete batch at N, matching locked envelope and reservation | Atomic COMMITTED at N+1; publish saved final result. |
| Any phase | Changed identity, stale Core, changed lock/tape, unsupported pinned version, invalid transition | Fail closed; no record/state advancement. |
| COMMITTED | Identical request/alias replay | Read saved receipt/result only. |

Only PREPARING can be cleared at unchanged N. After decision lock, cancellation means
pause, not reroll/reset. Changed action/target on repair is rejected; retry the same
locked decision. V1 has no post-reservation "discard and draw again" control. Persistent
failure is exposed as a recoverable error; abandoning the campaign requires explicit
NEW GAME into a different slot, preserving the failed slot and legacy data. Do not
hide a deadlock by inventing an aborted gameplay turn, no-op progression or reaction.

NONE/invalid decisions are rejected before lock. Accepted local/no-streak operations
use an explicit empty exit reservation while still capturing any native dice required
by their existing policies. No draw should occur merely to fill a reservation field.

## Reservation with current RNG

Current main uses native SecureRandom GAME_RNG. Keep that generator/policy; do not
introduce request-ID-seeded RNG, serialize a fake SecureRandom seed or replace it with
a new campaign PRNG. Extract/inject the current native roll-producing code through
one Core-authorized capture path, operating on Core@N copies. Every ordered draw is
tagged with native purpose, bound and value; include the resulting route/action data
and versioned policy digest. Persist the entire reservation and transition to RESERVED
inside a single serialized SQLite transaction before calling the outcome writer.

If a process dies during generation or persistence fails, the transaction rolls back
to DECISION_LOCKED. Uncommitted draws were never exposed to providers/UI and are not
outcomes; recovery may capture a new complete reservation. This is NOT persistence of
the SecureRandom generator state or bit-identical replay of invisible failed work.
After successful reservation commit, recovery must never invoke the generator again.
One DB writer lock prevents concurrent aliases from capturing multiple reservations.

Outcome writer/audit/repair consume the persisted tape. They cannot append new draws.
If Core requests an absent draw, the reservation contract is incomplete: fail closed
and fix the versioned capture policy; do not fall back to live RNG. A policy version
change cannot reinterpret an existing tape.

## Commit, manifest and recovery

In one transaction: verify current N and all locks, apply final Core-authorized result
to snapshot N+1, append immutable events, actor-approved observations/memories, rule
deltas, record manifest, mark receipt COMMITTED and advance save_slot. Compare-and-set
must affect exactly one slot row; aliases retain their original immutable binding.
No legacy SharedPreferences writes, WebView writes or facade eager-save calls are
part of this transaction. Snapshot must be pure validated staged output, not AI JSON.

Manifest has version; slot/turn/request IDs; N and N+1; input, decision and reservation
digests; Core snapshot digest; ordered event/observation/memory IDs with digests;
changed actor brain IDs/digests; pinned canon/schema/rule/policy versions; saved final
UI result digest. Native validation checks exact set equality, uniqueness, actor/slot
provenance and per-record hashes. No missing, extra or future record is accepted.
Hashing detects inconsistency; it is not a substitute for Core authorization or an
authentication scheme against a user editing local storage.

On load: check DB/version/foreign keys and current snapshot/receipt/manifest consistency.
At revision 0 validate genesis snapshot and initial brain projections without fabricated
events/receipt. Historical unchanged brains may have older revisions. Pending recovery
uses Core@N, never partially generated candidate state. Unknown versions/corruption are
explicit errors; never run an importer, silently repair canon or create a fresh slot.

Crash matrix: before admission => no request; after admission => PREPARING; after lock
=> DECISION_LOCKED; during capture => rollback/no exposure; after capture => same RESERVED;
during final commit => rollback to RESERVED; after commit/before callback => replay N+1.
An ambiguous DB-commit exception requires readback by request ID before any retry.
No assumption that an exception proves rollback.

Slot filenames are native-generated opaque IDs in a companion-only directory, with
strict validation and no user/model path input. Explicit deletion closes all handles
and removes only that slot DB and WAL/SHM plus its UI cache. New game creates a new
exclusive file; opening/loading never creates missing DBs. Preserve legacy namespaces.

## Implementation slices and checks

1. S1a: pure native pending transition/admission contract and adversarial tests; no
   connection to production bridge, persistence schema or RNG yet.
2. S1b: dedicated SQLite lifecycle, transactions, aliases and durable phase enforcement;
   actual Android storage/rollback/isolation tests before adoption.
3. S1c/S2: Core staging, roll capture, validated batch/manifest, recovery and ledger;
   test real outcome pipeline and fault boundaries, not a parallel test reducer.

Fixtures: cross-slot/request/actor confusion; same-ID changed input; stale unknown
request; identical alias; concurrent admissions; post-lock cancel; altered decision or
reservation; wrong digest/version; resume/reload; pre-lock rejection; double commit;
historical receipt replay; revision overflow; crash at every matrix boundary. Pure
transition tests prove only S1a semantics, not SQLite durability or Core integration.

Rollback: revert an unconnected implementation slice. Once data is adopted, rollback
must detect unsupported companion versions without overwriting/auto-converting files.
