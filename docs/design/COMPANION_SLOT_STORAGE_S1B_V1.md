# Companion Slot Storage S1b V1

Date: 2026-10-09. Parent #476/#478. Owner: Ponytail under continuing owner delegation.
Status: IMPLEMENTED ISOLATED COMPONENT; Android evidence pending the PR run.
Depends on S1a PR #479 at df27b36. No UI activation/Core adoption/legacy importer.

## Scope and authority

CompanionSlotStore uses a native private companion directory. Creation generates an
opaque 32-hex slot ID and exclusively creates its lease/database identity; load accepts
only that ID grammar and opens without CREATE_IF_NECESSARY. Missing, incomplete, corrupt
or unsupported storage is an error, never a replacement fresh game. Genesis is a bounded
native-approved snapshot fixture/input: this storage layer does not authorize its Core
semantics and cannot take model JSON as a validated state.

The control database format is 1, application ID CPS1. It stores immutable genesis at
revision 0, policy pin, encoded turn control and request aliases. No method commits
gameplay, advances snapshot/revision or persists COMMITTED without Core/ledger/manifest
integration. This is not yet the final SaveStats gameplay/event/brain schema.

## Concrete schema

| Table/index | Contract |
| --- | --- |
| slot_meta | singleton=1, slot ID, format=1, revision=0, pinned policy, genesis BLOB and SHA-256 |
| turn_control | turn ID primary key, active_slot=1, expected_revision=0, legal control phase and bounded V1 codec blob |
| one_active_turn | UNIQUE active_slot WHERE phase != REJECTED; one pending turn for the whole dedicated slot DB |
| request_alias | immutable request ID primary key and turn ID foreign key; exact set equals aliases in the control blob |

PREPARING/DECISION_LOCKED/RESERVED/SUSPENDED/REJECTED are the legal S1b phases.
Codec and SQL phase/identity/revision must agree. Decision policy must match the slot
policy. Unknown DB/codec versions and future/COMMITTED records reject. S1c/S2 must supply
the full approved transactional gameplay schema before adoption; do not casually enable
COMMITTED here by relaxing a CHECK. Experimental files are not silently upgraded.

Admission first checks historical request IDs, then the active turn. Changed input/ID
rejects; identical new ID aliases; different pending input is BUSY; stale revision
rejects. Pre-lock cancel stores REJECTED and frees only the active entitlement. Post-lock
cancel persists SUSPENDED and keeps decision/tape. Existing RESERVED replay never calls
the capture factory again. Alias floods are bounded at 128 while existing IDs replay.

Turn blob and every new alias are persisted in one SQLite transaction; results return
only after endTransaction completes. Capture runs under its writer transaction on the
locked native decision, without provider calls or exposing uncommitted dice. Failed
capture/persistence rolls back; the caller must read back by request ID after an
ambiguous commit exception. Fault seams are package-private and used only by androidTest.

## Connection and lifecycle decisions

S1b chooses DELETE journal with synchronous=EXTRA and foreign keys enabled, verified on
every open/transaction. This replaces the previous candidate WAL/FULL choice for the
initial control slice: one connection avoids API28-only synchronous configuration and
reader-pool assumptions on API24. WAL/performance changes need their own measured review.
No claim that these settings alone qualify OEM power-loss behavior.

References reviewed: [Android SQLiteDatabase API](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase),
[OpenParams.Builder API28](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.OpenParams.Builder),
[SQLite synchronous](https://www.sqlite.org/pragma.html#pragma_synchronous), and
[default corruption handler source](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/DefaultDatabaseErrorHandler.java).
The corruption handler fails/closes and preserves files; no default destructive handler.

A native per-slot file lease prevents other processes from opening/deleting an active
slot. Same-process handles share the lease and SQLite serializes their writer transactions.
Explicit deletion excludes opens, closes all such handles and deletes only this DB and
SQLite sidecars. The small .lease identity file remains to avoid unlinking a lock inode
that another process could still reference. IDs are never reused. Failed/closed handles
are not resurrected. No operation touches legacy namespaces or unrelated slots.

## Actual test entrypoint and limits

PR Build workflow retains its existing build-apk job unchanged. New dependent matrix
jobs run API24 and35 after that build. They execute the exact authoritative release patch
array through apply-release-patch-chain.py, compile the test-only instrumentation runner
via -PcompanionStorageSmoke, then install app/test APKs on Android Emulator Runner.
The normal Canon P0 runner remains unchanged when that property is absent.

CompanionStorageInstrumentation calls production store/codec APIs: create/load/isolation,
alias/conflict/busy/stale admission, faults after turn/alias write and before commit,
reservation retry, pause/reopen, two concurrent handles, real FK/unique constraints,
version/policy/blob/physical corruption and explicit deletion. A separate invocation
kills its process after an uncommitted reservation write; a new process verifies rollback
to DECISION_LOCKED and exact persisted reservation replay after a successful retry.

Reports include actual API/source/APK hash, suite/crash/recovery output and runtime logs.
The kill is an application-process crash, not power failure. Genesis uses a native test
fixture, not the full game loop. No real-device performance, 10k turns, final Core/ledger
atomic commit, brain integration or production qualification is claimed by this slice.

Local verification before PR: Java compiler module with API stubs and -Xlint:all -Werror;
28 existing pending/codec JUnit tests; workflow build-job semantic parity; Python syntax;
full 46-script patch generation and runner/property retention. These are not local Android
SQLite execution. Actual emulator results must be read from the new PR CI before GREEN.

Rollback: revert this unconnected component/test commit. No companion files are created
by the ordinary game bridge. Unsupported experimental files must fail explicitly rather
than being converted/deleted by a downgrade.
