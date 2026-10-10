# Companion implementation review (#498–#518)

Review baseline: Muse final stacked PR #539, commit `173fea2dc7cfcc8af7377396d2f9cb928ddacc0b`.
Fixes: draft PR #540, `fix/companion-muse-review`.

## Disposition

**NOT QUALIFIED.** The stack contains useful isolated contracts and fixtures, but does not implement the complete companion runtime. Passing helper tests, a synthetic decision benchmark, or standalone SQLite schema fixtures cannot close the integration acceptance criteria. Keep #498–#518 acceptance open until the corresponding native evidence exists. There is no automatic campaign activation, merge, or release in this review.

## Defects corrected in #540

| Boundary | Observed defect | Correction and regression evidence |
| --- | --- | --- |
| Generated build | Java invoked a non-static Kotlin digest; retired actor rejection was rewritten by the identity patch | Java-compatible shared digest; retired token survives all 46 release patches; exact-head Core/APK workflow enabled for the stacked base |
| Observation authority | Party membership/current scene granted positive sensory eligibility | No positive eligibility without a native sensory producer; scope and duplicate checks; unsupported events denied even with no eligible observers |
| Atomic publication specimens | Trigger bodies were split at internal SQL semicolons; ignored inserts concealed conflicts; retries did not compare complete records | Preserve complete triggers; use insertOrThrow; compare every observation and manifest identity/digest on retry; real Android SQLite fault/rollback/reopen fixture |
| Storage schema | Event/receipt identity and manifest digest were not jointly constrained; corrections lacked same-owner links | Composite foreign keys, digest-bound manifest, owner-scoped correction links and single successor; topic stored |
| Private context | Same actor from another slot and caller-labeled GM JSON could enter context; first oversized memory bypassed budget | Slot/owner filtering before context; immutable scene JSON and exact public projection; hard budget applies to every entry |
| Canon pins | Synthetic personas and a matching caller-provided revision were accepted | Compare full registry descriptor and context refs/SHA; verify genesis source path; tests use actual SHA-verified Codex assets (Cao Minh R17, Lục Trầm R05) |
| Decision orchestration | Unrelated RNG draw; missing output accepted; repair reason never reached provider; ledger race dereferenced missing winner | No decision RNG; required audit adapter; bounded one-repair re-audit; exact ledger binding and safe errors; missing adapter fails closed |
| Reducers | Unrelated events could breach a promise; contradictions lacked actual new evidence; only first matching belief updated; expired mood replay reset expiry | Match typed deadline/outcome; link new claim evidence; update all matching beliefs; preserve cause/trigger turn and reject overflow |
| Memory/load | Wrong slot/actor/turn and altered manifest digest could pass; arbitrary prose became a native correction; duplicate/branched correction chains were ambiguous | Exact scoped row/digest checks; corrections require newer same-owner observation and native template; reject invalid chains |
| Executors | Different actor/target facts were accepted; negative item cost gained charges; speech could change after audit and collide across turns; fabricated routes bypassed native rules | Bind actor/target; validate costs/dice scope; lock speech and scope event identity; reuse ExitStreakEngine and FeaturedJourneyRoutes with native input/combat eligibility |
| Lifecycle/UI | Lifecycle format differed from native store; malformed digest/wrong slot accepted; failed deletion reported success; pending events surfaced before durable receipt | Reference native format; validate digest/version/slot; typed deletion failure; publish events only after complete durable receipt |

## Remaining integration blockers

| Issues | Missing native work and required evidence |
| --- | --- |
| #498–#499 | A native sensory producer with scene/presence/consciousness/reach/audibility and per-entity visibility provenance. The current adapter intentionally yields UNKNOWN; membership is not evidence. Public projection is a field whitelist, not visibility authority. |
| #500–#504 | CompanionSlotStore does not install the new genesis/observation/memory schemas, publish those rows in its existing atomic transaction, or run the new reader on load. CompanionWaitBatch still emits empty observation/memory/brain manifests. Wire verified rows, reductions and receipt completeness into the one native commit and reopen path. |
| #505–#508 | Reducers are pure helpers. Persist their actor/slot state, feed only verified native events, and prove once-only replay and rollback with the receipt. Genesis InitialBrain is a separate scaffold, not the durable BrainState implementation. |
| #509–#511 | Trusted native context assembly, pinned-slot load, existing provider pool, existing semantic/canon audit adapter, and durable decision ledger remain unwired. Fake provider/auditor/ledger fixtures do not prove runtime behavior. Missing auditor currently rejects safely. |
| #512–#514 | TALK/MOVE/SEARCH/INSPECT/USE_ITEM/COMBAT_ACTION bundles are specimens. They do not stage/replay existing Core, bind native snapshots and scoped RNG, or enter its atomic observation/brain/receipt commit. Caller booleans and dice are not proof. Bind scene, inventory/combat revisions and typed commands using existing native authority. |
| #515–#516 | LifecycleStore has only a test fake; create must be atomic and non-overwriting. The input/projection contracts are not wired to MainActivity or the single interaction flow. Approved seed provenance still depends on the caller. |
| #517 | JVM fake-pipeline and raw SQLite pilot measurements are not integrated Core stage-cost/storage measurements. Preserve historical evidence but do not reuse it as qualification of this changed implementation; freeze budgets against the actual wired path. |
| #518 | Final integrated mixed actions and 1k/5k/10k qualification, receipt/replay/rollback, final long-run qualification, and deferred real-device evidence remain absent. API24/35 routine 300 runs are regression checks, not final acceptance. |

## Validation scope

Local companion helper tests: 283 passing after the code corrections. The lightweight host harness excludes CompanionWaitBatchTest and native Android SQLite execution; the exact-head GitHub workflow separately runs the complete Gradle Core suite, builds the APK, verifies all 46 generated patches/G0 provenance, and runs API24/API35 SQLite, process-kill recovery and routine 300-turn checks.

The new Android SQLite fixture verifies the actual schema DDL and ObservationPublisher against a CompanionSlotStore-created database, including failure rollback and reopen. It does **not** prove that production Core installs or calls these components. Final commit and workflow outcomes are recorded in PR #540 after CI completes; any failed or pending check prevents calling that head verified.
