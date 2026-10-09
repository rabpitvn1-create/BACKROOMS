# M1b.1: Fixture manifest — native observation evidence V1

Status: **specified expected outputs only — NOT executed tests.** Claiming these
as passing tests would be false; they are the fixture specs a future adapter
must satisfy.
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base: `test/companion-routine-benchmark-300` @ `4ec2ae6f`; main `a1b6e2f`.
Normative refs: `CompanionExposurePolicy` (`companion_exposure.v1`), Rule Table V1,
Action/Exit V1, Technical Design V1 §5, `M1B1_NATIVE_PRODUCER_CONTRACT_V1.md`.

Conventions: scope S = (slotId 32-hex, turnId, revision>0, eventId, sceneId);
actor A = `"cao_minh"` (Core `KAI_ID="kai"` carried alongside); actor B = `"luc_tram"`.
Fact ∈ {YES, NO, UNKNOWN}. Event publication ∈ {PERCEPTIBLE, PRIVATE_GM, UNCLASSIFIED}.

| ID | Input | Evidence | Expected result |
| --- | --- | --- | --- |
| F-01 | Event E1 PERCEPTIBLE, channels {SEEN}; actor B has no NativeFacts row | facts=[facts(A all-YES)] | eligible=[Eligible(S,A,SEEN)]; B absent → no row, no eligibility. Absence ≠ denial proof, just no grant. |
| F-02 | E1 PERCEPTIBLE {SEEN}; A and B share party and world node; B's facts all UNKNOWN | facts=[facts(A all-YES), facts(B all-UNKNOWN)] | eligible=[Eligible(S,A,SEEN)] only. Party/node membership grants nothing. |
| F-03 | E1 PERCEPTIBLE {SEEN,HEARD}; A conscious=NO (unconscious), visible=YES, audible=YES | facts=[NativeFacts(A, sceneMember=YES, inReach=YES, conscious=NO, visible=YES, audible=YES)] | eligible=[]. NO on any required sense denies the channel. |
| F-04 | E1 PERCEPTIBLE {SEEN,HEARD}; A sceneMember=YES, inReach=NO, conscious=YES, visible=YES, audible=YES | facts=[…inReach=NO…] | eligible=[]. Out-of-reach denies both channels. |
| F-05 | E1 PERCEPTIBLE {SEEN,HEARD}; A all-YES except audible=NO | facts=[…audible=NO…] | eligible=[Eligible(S,A,SEEN)]. Visible-only → SEEN only. |
| F-06 | E1 PERCEPTIBLE {SEEN,HEARD}; A all-YES except visible=NO | facts=[…visible=NO…] | eligible=[Eligible(S,A,HEARD)]. Audible-only → HEARD only. |
| F-07 | E1 PERCEPTIBLE {SEEN}; A visible=UNKNOWN (rest YES) | facts=[…visible=UNKNOWN…] | eligible=[]. UNKNOWN denies; never promoted to YES. |
| F-08 | E1 PERCEPTIBLE {SEEN}; facts row scope revision=7 vs event scope revision=8 | facts=[NativeFacts(scope rev 7, …)] | reject `evidence_scope_mismatch`. Cross-revision evidence invalid. |
| F-09 | E1 PERCEPTIBLE {SEEN}; facts row slotId=other slot | facts=[NativeFacts(other slot, …)] | reject `evidence_scope_mismatch`. Cross-slot evidence invalid. |
| F-10 | E1 PERCEPTIBLE {SEEN}; facts row sceneId=other scene | facts=[NativeFacts(other scene, …)] | reject `evidence_scope_mismatch`. Cross-scene evidence invalid. |
| F-11 | Two facts rows for actor A, same scope | facts=[facts(A…), facts(A…)] | reject `duplicate_actor_evidence`. Never merge/overwrite. |
| F-12 | 65 facts rows, same scope | facts=[65 rows] | reject `exposure_bound`. Bound is 64. |
| F-13 | E2 PRIVATE_GM, channels {SEEN}, A all-YES | event=E2 PRIVATE_GM | eligible=[]. PRIVATE_GM never emits eligibility. |
| F-14 | E3 UNCLASSIFIED, channels {SEEN}, A all-YES | event=E3 UNCLASSIFIED | eligible=[]. UNCLASSIFIED never emits eligibility. |
| F-15 | E4 PRIVATE_GM declaring channels {SEEN,HEARD} | event=E4 | constructor rejects `private_channels_invalid`. |
| F-16 | B in `CompanionCanonPersonaRegistry` (luc_tram → CHAR.LUC_TRAM, R05 pinned) but B never encountered in campaign; E1 PERCEPTIBLE {SEEN}; B sceneMember=NO | facts=[facts(B, sceneMember=NO, …)] | eligible for B: none. Registry presence proves canon source pinning, not scene presence or encounter. |
| F-17 | TOLD case: native-approved communication event C1 (speaker B → listener A, scene S, turn T, revision R) with claimRef K | evidence=C1 + claim K | TOLD observation permitted with sourceActorId=B, certainty=UNKNOWN, quoted proposition K. Certainty that B spoke does NOT promote K to KNOWN. |
| F-18 | TOLD claim K repeated by A in dialogue but no communication event C exists | evidence=dialogue prose only | No TOLD observation. Prose is not a native-approved communication event (contract P-C unimplemented). |
| F-19 | INFERRED: A saw B near the scene (SEEN eligible) and proposes "B caused the event" | evidence=SEEN observation only | No INFERRED observation. v1 has no general inference rule (Rule Table V1 §2); INFERRED cannot be CERTAIN. |
| F-20 | E1 PERCEPTIBLE {SEEN}; A eligible; payload read requested for GM-private field of the same world-event record | eligible=[Eligible(S,A,SEEN)] | Payload read DENIED for non-whitelisted fields. Eligibility ≠ payload access (contract §2/§6). |

## Explicit non-goals (must not appear as passing fixtures)

- No observation derived from prose/provider/UI narration.
- No `HP>0`, `ACTIVE`, or party membership used as consciousness/visibility proof.
- No reunion/history fabrication (Lục Trầm example, Tech Design V1 §5.1).
- No fixture asserts a reducer (BR01…RP03) outcome — reducer fixtures live in Rule Table V1 §5.
