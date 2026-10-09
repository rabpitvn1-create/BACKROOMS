# M1b.1: Fixture manifest — native observation evidence V1 (rev.2)

Status: **specified expected outputs only — NOT executed tests.**
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base: `test/companion-routine-benchmark-300` @ `4ec2ae6f`; main `a1b6e2f`.
Rev.2 addresses Ponytail review R1–R6 on PR #519 (comment 6078761454).
Normative refs: `CompanionExposurePolicy` (`companion_exposure.v1`), Rule Table V1,
Technical Design V1 §5 (SQL specimen), `M1B1_NATIVE_PRODUCER_CONTRACT_V1.md`.

Conventions: scope S = (slotId 32-hex, turnId, revision>0, eventId, sceneId) —
turnId/eventId/revision must come from the validated pending/accepted native batch
(R6), not from constructor syntax alone. Actor A = `"cao_minh"` — effective
runtime identity (R1: `KAI_ID="cao_minh"` post-chain; raw `"kai"` is
[PRE-CHAIN/RETIRED]). Actor B = `"luc_tram"`. Fact ∈ {YES, NO, UNKNOWN}.
Observation certainty ∈ {CERTAIN, PLAUSIBLE, UNCERTAIN} (R4 — never UNKNOWN);
claim stance ∈ {UNKNOWN, …, KNOWN} lives on the Claim, not the observation.

| ID | Input | Evidence | Expected result |
| --- | --- | --- | --- |
| F-01 | E1 PERCEPTIBLE {SEEN}; B has no NativeFacts row | facts=[facts(A all-YES)] | eligible=[Eligible(S,A,SEEN)]. B absent → no grant (absence ≠ denial proof). |
| F-02 | E1 PERCEPTIBLE {SEEN}; A,B share party+node; B all-UNKNOWN | facts=[facts(A all-YES), facts(B all-UNKNOWN)] | eligible=[Eligible(S,A,SEEN)]. Party/node grants nothing. |
| F-03 | E1 PERCEPTIBLE {SEEN,HEARD}; A conscious=NO, visible=YES, audible=YES | facts=[NativeFacts(A, YES,YES,NO,YES,YES)] | eligible=[]. NO on required sense denies. |
| F-04 | E1 PERCEPTIBLE {SEEN,HEARD}; A inReach=NO (rest YES) | facts=[…inReach=NO…] | eligible=[]. Out-of-reach denies both channels. |
| F-05 | E1 PERCEPTIBLE {SEEN,HEARD}; A audible=NO (rest YES) | facts=[…audible=NO…] | eligible=[Eligible(S,A,SEEN)]. Visible-only → SEEN. |
| F-06 | E1 PERCEPTIBLE {SEEN,HEARD}; A visible=NO (rest YES) | facts=[…visible=NO…] | eligible=[Eligible(S,A,HEARD)]. Audible-only → HEARD. |
| F-07 | E1 PERCEPTIBLE {SEEN}; A visible=UNKNOWN (rest YES) | facts=[…visible=UNKNOWN…] | eligible=[]. UNKNOWN denies; never promoted. |
| F-08 | E1 rev 8; facts row scope rev 7 | facts=[NativeFacts(scope rev 7…)] | reject `evidence_scope_mismatch`. |
| F-09 | facts row slotId = other slot | facts=[NativeFacts(other slot…)] | reject `evidence_scope_mismatch`. |
| F-10 | facts row sceneId = other scene | facts=[NativeFacts(other scene…)] | reject `evidence_scope_mismatch`. |
| F-11 | two facts rows for A, same scope | facts=[facts(A…), facts(A…)] | reject `duplicate_actor_evidence` (policy-call level). |
| F-12 | 65 facts rows, same scope | facts=[65 rows] | reject `exposure_bound` (64). |
| F-13 | E2 PRIVATE_GM, channels={} ; A all-YES | event=E2 PRIVATE_GM, no channels | eligible=[]. PRIVATE_GM never emits eligibility. |
| F-13b | E2 PRIVATE_GM declaring channels={SEEN} | event=E2 ctor | **constructor rejects** `private_channels_invalid` — never reaches eligible(). |
| F-14 | E3 UNCLASSIFIED, channels={} ; A all-YES | event=E3 UNCLASSIFIED, no channels | eligible=[]. UNCLASSIFIED never emits eligibility. |
| F-14b | E3 UNCLASSIFIED declaring channels={HEARD} | event=E3 ctor | **constructor rejects** `private_channels_invalid`. |
| F-15 | (retired — merged into F-13b/F-14b) | — | — |
| F-16 | B in `CompanionCanonPersonaRegistry` (R05 pinned) but never encountered; B sceneMember=NO | facts=[facts(B, sceneMember=NO…)] | B: no eligibility. Registry pinning ≠ scene presence/encounter. |
| F-17 | TOLD: native-approved communication C1 (speaker B → listener A, S/T/R, claimRef K) | evidence=C1 + claim K | TOLD observation permitted: sourceActorId=B, certainty per perception contract (e.g. CERTAIN that the telling was heard); **claim stance starts UNKNOWN** — certainty of speaking ≠ truth of K. |
| F-18 | A repeats claim K in dialogue; no communication event exists | prose only | No TOLD observation. Prose is not a native-approved communication event. |
| F-19 | INFERRED: A SEEN-eligible, proposes "B caused the event" | SEEN observation only | No INFERRED observation. v1 has no general inference rule; INFERRED never CERTAIN. |
| F-20 | E1 PERCEPTIBLE {SEEN}; A eligible; read requested for GM-private field of the same world-event record | eligible=[Eligible(S,A,SEEN)] | Payload read DENIED for non-whitelisted fields (contract §2b). Eligibility ≠ access. |
| F-21 | E1 turnId=T5 (accepted batch); facts row turnId=T4 | facts=[NativeFacts(turn T4…)] | reject: turnId must come from the validated batch (R6). |
| F-22 | E1 eventId=E1 (accepted); facts row eventId=E9 | facts=[NativeFacts(event E9…)] | reject: eventId must match the accepted event (R6). |
| F-23 | Observation write with ownerActorId=B for A's SEEN eligibility | eligible=[Eligible(S,A,SEEN)] + write(owner=B) | reject: owner must equal the eligible actor; cross-actor assignment invalid (R6). |
| F-24 | E1 PERCEPTIBLE {SEEN,HEARD}; A all-YES both channels | facts=[facts(A all-YES)] | **two** observation entries: (S,A,E1,SEEN) and (S,A,E1,HEARD) — uniqueness key is (slot,actor,event,access_kind) (R6). A generic (slot,actor,event) dedup dropping one channel is wrong. |

## Explicit non-goals

- No observation from prose/provider/UI; no HP>0/ACTIVE/party as consciousness or
  sense proof; no reunion/history fabrication.
- No fixture asserts reducer outcomes (Rule Table V1 §5 owns those).
- No dual runtime identity: effective `KAI_ID` = actorId = `"cao_minh"` (R1).
