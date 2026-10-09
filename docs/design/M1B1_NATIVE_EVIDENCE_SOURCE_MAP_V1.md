# M1b.1: Native observation evidence — source map V1

Status: contract-preparation slice; **runtime observation production remains disabled**.
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base branch: `test/companion-routine-benchmark-300` @ `4ec2ae6f67ddf8b0dc333a8240ea529000a775c6`
(stacks #495 M1a @ `b95c167` → #496 P1a @ `fbfaccd` → #497 routine300 @ `4ec2ae6`).
Inspected main: `a1b6e2f1c12620dc4b8bb80529cbe58b15e81505`.

## Method and limits

- "Effective source" here means the post-patch-chain working tree. There are no
  checked-in files carrying a "generated" marker: `GameState.kt`, `GameStateCodec.kt`
  and all `core/companion/*` files are hand-written and git-tracked; the 46 patch
  scripts (`android-apk/patch-*.py`, ordered by `apply-release-patch-chain.py` from
  the `scripts=(...)` array in `build-backroom-apk.yml`) transform them in place.
- The 46-patch chain was **not reproduced** in this environment (feasibility only:
  scripts + ordered array present, stdlib-only in-place transformers). All file/line
  claims below are read from the pre-chain tree at the base head and marked
  [SOURCE-TRACED]; post-chain drift is a recorded UNKNOWN (row 11).
- Runtime CI (Core/APK/46-patch parity/SQLite/crash): **NOT APPLICABLE** to this
  docs-only slice. No green claim is made from absent checks.
- Claim tags: [SOURCE-TRACED] = read from the tree at the base head (file:line given).
  [UNKNOWN] = not represented in the effective source; must not be inferred.

## Evidence source table

| # | Evidence dimension | Source file (function / version) | What it proves [SOURCE-TRACED] | What is UNKNOWN |
| --- | --- | --- | --- | --- |
| 1 | Actor existence | `core/GameState.kt` — `CharacterState` (id, name, …), `GameState.characters` | That an actor record with a stable `id` exists in a decoded snapshot. `KAI_ID = "kai"` (`GameState.kt:4`). | Whether the actor is actually at the event's scene. Existence ≠ presence at scene. |
| 2 | Presence flag | `core/GameState.kt:41` — `CharacterPresence { ACTIVE, SEPARATED, MISSING, DEAD }`; `CharacterState.presence` | The stored presence enum value for an actor in a snapshot. Serialized by `GameStateCodec.character()` as the enum name. | What SEPARATED vs MISSING means for perception. No documented mapping from this flag to "can perceive". Per the issue: never use ACTIVE (or HP>0) as proof of consciousness or seeing/hearing. |
| 3 | Scene keys | `core/GameState.kt` — `GameState.world: Map<String,String>`; keys observed: `"location"`, `"journeyStopKey"`, `"worldNodeId"`, `"levelJson"`, `"flagsJson"` | That scene/location keys exist per snapshot and are codec-serialized (`GameStateCodec` top-level `world`). `CompanionWaitAuthorizer.preflight()` checks `journeyStopKey`/`worldNodeId`/`levelJson`/`flagsJson`/`exploration.exitStreak` consistency vs `FeaturedJourneyRoutes`. | **Actor↔scene membership**: there is no registry mapping which actors are in which scene. Same node/party is explicitly insufficient (M1a). |
| 4 | Reach | — | — | [UNKNOWN] No Core model of perceptual reach. Only `CompanionExposurePolicy.NativeFacts.inReach: Fact` (policy-shaped; no native adapter produces it). |
| 5 | Consciousness | `core/GameState.kt:68` — `PhysiologyState` (minutesSinceFood/Water/Awake, painState?, infectionState?, thermalState?); `PhysiologyEngine.execute()` | That physiology counters exist and are updated by `PhysiologyCommand`s. | [UNKNOWN] Consciousness itself. `minutesSinceAwake` is not a consciousness proof; no Core field asserts "actor is conscious". Only `NativeFacts.conscious: Fact` (policy-shaped, no adapter). |
| 6 | Visibility / audibility | — | — | [UNKNOWN] No Core model. Only `NativeFacts.visible` / `audible: Fact` (policy-shaped, no adapter). |
| 7 | Event position & timing | `core/GameState.kt` — `TurnState` (currentTurnId, completedTurnIds); `GameTimeState` (elapsedSubjectiveMinutes); WAIT envelope `version="companion_wait_capture.v2"` (revision, turn) — `CompanionWaitCapture.kt` | Turn ordering and subjective elapsed time per snapshot; WAIT capture binds revision+turn in its envelope. | Wall-clock vs Core-turn reconciliation across sessions; event-to-turn binding for non-WAIT event kinds (no native event ledger type yet beyond WAIT batch `events[]`). |
| 8 | Public projection | `CompanionWaitCapture.project()` — exposes turn/level/flags/party(id, name, presence **excluding KAI_ID**) | Exactly which fields the current native projection exposes, and that `KAI_ID` ("kai") is deliberately excluded while the companion contract names the actor `"cao_minh"` (`CompanionDecisionBinding`: `lock.actorId=="cao_minh"` + KAI_ID ACTIVE-and-in-party check). | Projection ≠ observation. `project()` output grants no SEEN/HEARD and no payload read right. |
| 9 | Actual speaker / listener | — | — | [UNKNOWN] No native communication-event type exists. TOLD-per-Rule-Table requires "an actual native-approved speaker-to-listener communication event" — there is nothing to bind it to yet. |
| 10 | Payload & outcome binding | `CompanionWaitCapture` envelope (policy, policyDigest, revision, turn, afterSnapshotDigest, snapshotDigest, decisionDigest, tape, rolls, success, nextStreak, completed); `CompanionWaitBatch.verify()` rebuilds natively, exact match required; `CompanionRollTape` canonical lines + `verifyRoute()` vs `FeaturedJourneyRoutes` | Byte-level binding of outcome to snapshot bytes + ordered tape; `replay()` requires exact envelope equality; valid graph edge alone never authorizes completion (`CompanionWaitAuthorizer.replay()` re-derives via `ExitStreakEngine`). | Payload **read rights**: eligibility (`Eligible`) grants neither GM payload access nor objective truth (policy docstring). The payload whitelist boundary is defined in the producer contract, not in code. |
| 11 | Post-chain drift | `android-apk/apply-release-patch-chain.py` (46 scripts) | That the chain definition exists and is ordered. | [UNKNOWN] Whether any patch script alters the fields cited above. Chain not reproduced here; generated-source parity must be re-verified before any runtime PR (per #498 acceptance). |

## Load-bearing identity note [SOURCE-TRACED]

Core actor id is `KAI_ID = "kai"`; the companion contract actor is `"cao_minh"`
(`CompanionDecisionBinding.verifyWait`: `lock.actorId=="cao_minh"` **and** KAI_ID
presence ACTIVE and in party; `CompanionWaitCapture.project()` excludes KAI_ID from
party listing). `CompanionCanonPersonaRegistry` (P1a) pins `cao_minh → CHAR.KAI`
(R17, sha256-pinned) and rejects `kai` as the runtime actor id for the new API.
Any producer contract must carry **both** ids with this mapping explicit; collapsing
them is a contract break, not a simplification.
