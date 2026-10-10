# M1b.1: Native observation producer — minimal contract V1 (rev.2)

Status: contract-preparation slice; **no runtime producer is implemented or enabled**.
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base: `test/companion-routine-benchmark-300` @ `4ec2ae6f`; main `a1b6e2f`.
Rev.2 addresses Ponytail review R1–R6 on PR #519 (comment 6078761454).
Normative refs: `CompanionExposurePolicy` (`companion_exposure.v1`), Rule Table V1,
Action/Exit V1, Technical Design V1 §5 (SQL specimen + Kotlin specimen).

## 0. Identity (R1 correction)

Rev.1 described a dual runtime identity. **Wrong for the effective runtime.**
[POST-CHAIN TRACED] `patch-cao-minh-identity-overlay.py:34` rewrites `"kai"`→`"cao_minh"`;
`:57` asserts `const val KAI_ID = "cao_minh"`; generated `CaoMinhIdentityTest`
asserts `KAI_ID="cao_minh"` and no `"kai"` character key. **Effective runtime:
`KAI_ID = "cao_minh"`, companion actorId = `"cao_minh"` — one identity.**
`KAI_ID` (symbol) and `CHAR.KAI.*` (knowledge namespace) are retained; no
alias/dual identity is constructed. Raw `"kai"` = [PRE-CHAIN/RETIRED].

## 1. Binding

Every native observation record MUST carry all of the following; a missing field
fails closed:

| Field | Constraint | Source |
| --- | --- | --- |
| slotId | 32 lowercase hex | `CompanionExposurePolicy.Scope` |
| turnId | `[A-Za-z0-9_.:-]{1,160}`, from the **validated pending/accepted native batch** + receipt/event provenance — constructor syntax or a prior snapshot's `TurnState.currentTurnId` does not authorize a new turn | Policy `Scope`; R6 |
| revision | Long > 0, the batch's committed revision | Policy `Scope` |
| eventId | `[A-Za-z0-9_.:-]{1,160}`, must match the **accepted** event | Policy `Scope`; R6 |
| sceneId | non-blank, ≤256 UTF-8 bytes, well-formed, no ISO controls | Policy `Scope` |
| actorId | `[A-Za-z0-9_.:-]{1,160}`; effective value `"cao_minh"` (§0) | Policy `NativeFacts` |
| sourceWorldEventId | stable identity of the accepted world event | Rule Table V1 §1 |
| access | one of SEEN / HEARD / TOLD / INFERRED | Tech Design SQL specimen |

**Dedup, two levels (R6):**
- *Policy-call level*: within one `eligible()` call, `NativeFacts` rows must have
  unique actorIds (`duplicate_actor_evidence`); scope must equal the event scope
  (`evidence_scope_mismatch`); ≤64 rows (`exposure_bound`).
- *Observation level*: uniqueness key is **(slot, actor, event, access_kind)**
  (Tech Design SQL specimen l.176 `UNIQUE (slot_id, actor_id, event_id, access_kind)`).
  An actor eligible for both SEEN and HEARD yields **two** legitimate entries;
  a generic (slot,actor,event) dedup that drops the second channel is wrong.

## 2. Observation record fields (R4 correction)

Rev.1 used `certainty=UNKNOWN` on observations. **Wrong enum.** Per Technical
Design V1:

- **Observation certainty** ∈ `{CERTAIN, PLAUSIBLE, UNCERTAIN}` (SQL specimen
  l.172 `CHECK(certainty IN ('CERTAIN','PLAUSIBLE','UNCERTAIN'))`; l.173
  `access_kind != 'INFERRED' OR certainty != 'CERTAIN'` — INFERRED is never CERTAIN).
  Certainty answers "how well was this perceived / was the telling heard",
  NOT "is the proposition true".
- **Claim stance** ∈ `{UNKNOWN, SUSPECTED, BELIEVED, DISPUTED, KNOWN}` (Rule Table
  V1 §2) lives on the *Claim*, not the observation. A TOLD claim **starts
  UNKNOWN**: certainty that the speaker spoke does not prove the quoted
  proposition. No v1 rule promotes hearsay to KNOWN. The SQL/Kotlin specimens are
  design specimens, not adopted production — noted here so the two fields are
  never merged into one meaning.

Record fields: observationId, ownerActorId, sourceWorldEventId, sourceObservationIds,
access, sourceActorId? (required for TOLD), certainty, scope tuple (§1),
`policyVersion = "companion_exposure.v1"`.

## 2b. Public content whitelist per native WAIT event (R5)

§2 whitelists observation *metadata*. Native WAIT batches today emit 4 event types
(`CompanionWaitBatch.build()` [SOURCE-TRACED raw]); per-event/field exposure:

| Event | Payload fields [SOURCE-TRACED raw] | Exposable (with what evidence) | Must redact / deny |
| --- | --- | --- | --- |
| WAIT_COMPLETED | actor=`"cao_minh"`, minutes=30, location=`world.location`, elapsedMinutes | actor+minutes: self-evident to the waiter. location/elapsedMinutes: exposable to the actor with SEEN-class eligibility at that scene (the actor knows where they are and how long they waited). | Nothing here is GM-private, but location must not leak *other* actors' positions. |
| EXIT_STREAK_RESOLVED | success, streak=nextStreak, source=sourceStop, target=route.targetStop, completed | **completed=true**: the transition happened — the actor experiences arrival; target stop becomes knowable *after* arrival with scene evidence. | success/streak counters, route.targetStop **before** completion, internal streak node: system mechanics, never character knowledge by default. Do not default RNG/streak internals as known. |
| WORLD_TRANSITION | source=sourceStop, target=targetStopKey, node=targetNodeId (only if transition completed) | source/target stop keys after arrival (experienced). | node IDs are system identifiers — expose stop keys, redact nodeIds unless a reviewed rule needs them. |
| COMBAT_STARTED | encounterId, entities[] (only if combat active) | entities the actor can perceive: expose **id/name/presence only**, and only with SEEN-class eligibility (conscious, present, in-reach). | encounterId (system id); full entity metadata (stats/HP/capabilities = GM-private). Never expose all-entities metadata as character-known. |

Unsupported/unclassified event types → deny exposure entirely. Eligibility
(`Eligible`) alone never authorizes payload reads; reads need §6's binding.

## 3. Valid perception-fact sources

Today: **none native**. `NativeFacts` is policy-shaped; no adapter produces its
facts; WAIT manifest hardcodes `"observations": []` (`CompanionWaitBatch.kt:73`).
The constructor is an internal boundary for reviewed native callers, not proof.

A future adapter qualifies ONLY if it derives each fact from authoritative
native/Core outcomes at the event's actual scene and temporal position, bound to
the same committed revision — and fails closed for any field without an approved
native contract (M1a "Native adapter gate"). NEVER valid substitutes: party
membership, shared node, input words, HP>0 / ACTIVE / status flags, provider
identity, canon excerpts, quoted story, narration, remembered tags, codex
capabilities, or channel declaration by the event itself.

## 4. Update / reload

Immutable once written; corrections append. Reload reproduces the identical set:
snapshot byte-equality + codec round-trip stability (`CompanionCoreStage` guards),
exact envelope equality on `replay()`. Rule Table application identity
(slot, actor, rule version, rule ID, source observation/event identity, target
instance) makes re-application idempotent.

## 6. Fail-closed rules

1. Missing binding field (§1) → no observation; batch rejected.
2. `Fact.UNKNOWN` on any required sense → channel denied. UNKNOWN never → YES.
3. `PRIVATE_GM` / `UNCLASSIFIED` → no eligibility ever; an event of those
   publications declaring perceptible channels fails at construction
   (`private_channels_invalid`) — see fixtures F-13b/F-14b.
4. Scope mismatch / >64 rows / duplicate actor rows in one call → reject
   (policy-level rules, §1).
5. TOLD without a native-approved speaker→listener communication event → no
   TOLD observation (no such event type exists yet; proposal P-C).
6. INFERRED → unavailable in v1; and never CERTAIN (specimen l.173).
7. Read-right grant (proposal P-D, **not approved**): if ever introduced, bind
   (slot, actor, turn, committed revision, event, scene, access, policy, exact
   public projection/schema) — not a bare (actor,observationId,revision).
   Simpler alternative kept open: native verified projection + owner-filtered
   repository, no new capability subsystem.

## 7. Proposed minimal typed additions — FOR REVIEW ONLY (not implemented)

- P-A: `SceneMembership` native record (slotId, sceneId, actorId, revision,
  joinedTurnId) from Core scene transitions. Fills source-map row 3.
- P-B: `NativePerceptionFact` producer interface — one reviewed native function
  per sense with explicit Core provenance. Fills rows 4/5/6. No narration booleans.
- P-C: `CommunicationEvent` native type (speakerId, listenerId, sceneId, turnId,
  revision, claimRef). Fills row 9; enables TOLD per Rule Table V1 §2.
- P-D: observation read-right grant — **proposal only, not approved**; see §6.7.
