# M1b.1: Native observation producer — minimal contract V1

Status: contract-preparation slice; **no runtime producer is implemented or enabled**.
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base branch: `test/companion-routine-benchmark-300` @ `4ec2ae6f67ddf8b0dc333a8240ea529000a775c6`.
Inspected main: `a1b6e2f1c12620dc4b8bb80529cbe58b15e81505`.
Builds on: M1a exposure eligibility (`CompanionExposurePolicy`, `companion_exposure.v1`),
P1a persona pins, Action/Exit V1, Rule Table V1, Technical Design V1 §5.

## 1. Binding (what an observation must be bound to)

Every native observation record MUST carry all of the following; a missing field
fails closed (no observation is produced):

| Field | Type / constraint | Source [SOURCE-TRACED] |
| --- | --- | --- |
| slotId | 32 lowercase hex chars | `CompanionExposurePolicy.Scope` init |
| turnId | `[A-Za-z0-9_.:-]{1,160}` | `CompanionExposurePolicy.Scope` init; `TurnState.currentTurnId` |
| revision | Long, `> 0` (genesis produces no gameplay event) | `CompanionExposurePolicy.Scope` init |
| eventId | `[A-Za-z0-9_.:-]{1,160}` | `CompanionExposurePolicy.Scope` init |
| sceneId | non-blank, ≤256 UTF-8 bytes, well-formed, no ISO controls | `CompanionExposurePolicy.Scope` init |
| actorId | `[A-Za-z0-9_.:-]{1,160}`; companion actor `"cao_minh"` with Core identity `KAI_ID="kai"` carried alongside (see §5) | `CompanionExposurePolicy.NativeFacts`; `CompanionDecisionBinding` |
| sourceWorldEventId | stable event identity from the authoritative batch | Rule Table V1 §1 (application identity tuple) |

Duplicate (slot, actor, event) evidence MUST reject, never merge or overwrite
(`CompanionExposurePolicy.eligible`: `duplicate_actor_evidence`; Rule Table V1:
"Duplicate evidence never applies a rule twice").

## 2. Public payload whitelist

An observation's storable payload is limited to this whitelist. Anything else —
writer secrets, GM-private content, another actor's private information — MUST NOT
be persisted in an observation record:

- `observationId`, `ownerActorId`, `sourceWorldEventId` (Technical Design V1 §5.2
  `ActorObservation`)
- `access: AccessKind` — one of SEEN / HEARD / TOLD / INFERRED (never a fifth kind)
- `sourceActorId?` — required for TOLD (the speaker); absent otherwise
- `certainty: Certainty` — TOLD starts UNKNOWN and v1 has no rule promoting
  hearsay to KNOWN (Rule Table V1 §2)
- Scope tuple (§1) and `policyVersion = "companion_exposure.v1"`

Eligibility (`Eligible`) is a **reference only**: it grants neither access to a GM
payload nor objective truth (`CompanionExposurePolicy` docstring). Reading a
payload requires a separate, explicitly granted read right (see §6 UNKNOWN).

## 3. Valid perception-fact sources (what may fill NativeFacts)

Today: **none**. `CompanionExposurePolicy.NativeFacts` is policy-shaped;
no native adapter produces `sceneMember / inReach / conscious / visible / audible`
facts, and the WAIT batch manifest hardcodes `"observations": []`
(`CompanionWaitBatch.kt:73` [SOURCE-TRACED]). The constructor is an internal
Kotlin boundary for reviewed native callers — not proof the facts are true (M1a doc).

A future adapter qualifies as a valid source ONLY if, per event, it derives each
fact from authoritative native/Core outcomes at the event's actual scene and
temporal position, bound to the same committed revision — and fails closed for any
field without an approved native contract (M1a "Native adapter gate"). In
particular the following are NEVER valid substitutes (issue #498, M1a):

- party membership, shared world node, or input words;
- `HP > 0`, `CharacterPresence.ACTIVE`, or any status flag as proof of
  consciousness or of seeing/hearing;
- provider identity, canon excerpts, quoted story, narration, remembered tags,
  or codex capabilities;
- channel declaration by the event itself (`private_channels_invalid`).

## 4. Update / reload semantics

- Observations are immutable once written. Corrections append new records; no
  in-place mutation, no last-writer-wins (Rule Table V1 §1–§2).
- Reload MUST reproduce the identical observation set: snapshot byte-equality +
  codec round-trip stability (`CompanionCoreStage` guards) and exact envelope
  equality on replay (`CompanionWaitCapture.replay()`).
- Rule Table application identity `(slot, actor, rule version, rule ID, source
  observation/event identity, target instance)` makes re-application idempotent;
  no provider call count, wall clock, or user-text repetition enters the key.

## 5. Identity mapping (load-bearing)

Core actor id `KAI_ID = "kai"` (`GameState.kt:4`); companion contract actor
`"cao_minh"`. The producer MUST carry both, with the mapping explicit, per
`CompanionDecisionBinding.verifyWait` (`lock.actorId=="cao_minh"` AND KAI_ID
presence ACTIVE and in party) and `CompanionCanonPersonaRegistry`
(`cao_minh → CHAR.KAI`, R17 sha256-pinned; `kai` rejected as the new-API actor id).
Collapsing the two ids is a contract break.

## 6. Fail-closed rules

1. Any missing binding field (§1) → no observation; the batch is rejected.
2. `Fact.UNKNOWN` on any required sense → channel denied (M1a: NO and UNKNOWN
   both deny). UNKNOWN is never promoted to YES.
3. `Publication.PRIVATE_GM` / `UNCLASSIFIED` → no eligibility, ever; a
   private/unclassified event declaring perceptible channels rejects
   (`private_channels_invalid`).
4. Scope mismatch between evidence rows → reject (`evidence_scope_mismatch`).
5. More than 64 evidence rows → reject (`exposure_bound`).
6. TOLD without a native-approved speaker→listener communication event →
   no TOLD observation (no such event type exists yet — §7 UNKNOWN).
7. INFERRED → unavailable in v1 (no reviewed inference rule exists).

## 7. Proposed minimal typed additions — FOR REVIEW ONLY (not implemented)

If Core cannot supply a needed field, the producer needs (proposals; each needs
its own reviewed diff before any integration — issue #498 §3):

- P-A: `SceneMembership` native record — (slotId, sceneId, actorId, revision,
  joinedTurnId) produced by Core scene transitions. Fills the row-3 UNKNOWN
  (actor↔scene membership).
- P-B: `NativePerceptionFact` producer interface — one reviewed native function
  per sense (reach/conscious/visible/audible) with explicit Core provenance per
  call. Fills the rows-4/5/6 UNKNOWNs. No booleans manufactured from narration.
- P-C: `CommunicationEvent` native type — (speakerId, listenerId, sceneId,
  turnId, revision, claimRef) for validated in-scene speech. Fills the row-9
  UNKNOWN and enables TOLD per Rule Table V1 §2.
- P-D: observation read-right grant — typed capability binding
  (actorId, observationId, revision) issued only from `Eligible` + payload
  whitelist (§2). Fills the row-10 UNKNOWN.

None of P-A…P-D is implemented here. Each is a separate reviewable slice.
