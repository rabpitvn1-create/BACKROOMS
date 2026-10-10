# Character Brain Rule Table V1

Version: 1.0.0. Author/owner: Ponytail. Date: 2026-10-09.
Status: DESIGN CONTENT READY FOR REVIEW; NOT IMPLEMENTED OR TESTED AS A REDUCER.
Authority: approved architecture baseline and delegated product decision in
[issue #476](https://github.com/rabpitvn1-create/BACKROOMS/issues/476#issuecomment-6072401470).
This specifies mechanics, not new canon. Runtime adoption requires the P2 gate.

## 1. Deterministic evaluation contract

Input is prior brain, native-approved actor observations, verified Core outcomes,
Core turn and pinned rule version. Never accept a model JSON brain or parse prose
for a mutation. Evaluate events by committed revision, turn ordinal and stable ID;
evaluate rules by rule ID. All affected actors read the same prior batch revision.

Each rule emits typed deltas and evidence links before the authoritative DB commit.
Missing/invalid mandatory evidence rejects the proposal; an unrecognized trigger
emits no delta. Failure to persist a required delta aborts the complete batch.
Duplicate evidence never applies a rule twice. Persist rule/version, evidence and
application identity with the projection/manifest for audit and replay.

A rule application identity is the tuple (slot, actor, rule version, rule ID,
source observation/event identity, target instance). No provider request count,
wall clock or user-text repetition enters that key.

CanonPersona, world facts, inventory, abilities, equipment, routes and RNG are
outside the writable set. An inferred idea outside a reviewed template can remain
in audited dialogue/proposal, but is not a durable goal or belief.

## 2. Typed claim and evidence contract

A Claim is a bounded native-validated record: claimId, subjectRef, predicateId,
objectRef/value, speakerRef if applicable, sourceObservationIds and epistemic stance.
Subject/object IDs must be in the actor's authorized evidence or scene; no arbitrary
writer-only references. Identity preserves the distinction between the quoted claim
and the event that someone made it. Canonical tuple encoding is versioned; a digest
alone is not semantic validation.

Stances: UNKNOWN, SUSPECTED, BELIEVED, DISPUTED, KNOWN. A TOLD claim starts UNKNOWN:
certainty that a speaker spoke does not prove the quoted proposition. No v1 rule
promotes hearsay to KNOWN. A conflicting later claim appends evidence and marks
DISPUTED; the prior account remains traceable. There is no last-writer-wins truth.

SEEN/HEARD require Core-approved exposure (scene, perceptual reach, consciousness,
visibility/audibility). TOLD requires an actual native-approved speaker-to-listener
communication event. INFERRED requires an actor-owned source chain and a reviewed
inference rule; v1 has no general-purpose inference rule. INFERRED cannot be CERTAIN.

## 3. Promise acceptance and completion

A Promise proposal contains actor/promisor, beneficiary, scope, targetRef,
completionPredicateId/arguments, optional Core-turn deadline and evidence refs.
V1 supports only predicates supplied by the validated gameplay contract; an unknown
predicate is rejected, not stored as free-text executable semantics.

A promise becomes accepted only after native actor/scene/capability checks plus
applicable semantic/canon audit approve the structured acceptance decision. The
Core-approved PROMISE_ACCEPTED interaction event records the full terms, party IDs
and decision/evidence IDs. UtteranceDraft or GM prose cannot serve as acceptance.
A player saying “you promised” or requesting a promise is not acceptance.

Completion requires a verified Core outcome satisfying exactly the pinned predicate
and actor/target constraints. Mere absence of a success event is not a breach.
Breach requires a defined deadline crossing in Core turns or an explicit validated
incompatible outcome under the accepted terms. Impossible/ambiguous terms reject
before acceptance; no inferred betrayal or invented ethics.

V1 goal status is ACTIVE, DONE or ABANDONED. Valid breach abandons the promise goal
with a reason; DONE requires verified fulfillment. A correction appends evidence and
recomputes the affected projection through reviewed correction policy. If replay
cannot resolve contradictory fulfillment/breach evidence, mark disputed and avoid
trust/mood side effects; never silently erase history.

## 4. Rule definitions

All evidence is slot- and actor-scoped. Rules apply only to actors exposed to the
trigger or explicitly party to a validated interaction. Bounds are stated below;
there is no numerical personality score or automatic canon change.

| Rule | Trigger and eligibility | Writable delta | Bounds and failure |
| --- | --- | --- | --- |
| BR01 CREATE_TOLD_BELIEF | Valid TOLD observation with typed claim and known speaker/listener | CREATE belief UNKNOWN, quoted proposition, speaker and source IDs | One instance per actor/claim/speaker identity; no truth upgrade, unsupported refs reject |
| BR02 DISPUTE_CLAIM | Later validated actor-owned evidence contradicts the same typed proposition through a reviewed predicate comparison | UPDATE stance DISPUTED and append evidence | No deletion, no KNOWN promotion; comparison outside predicate contract emits no delta |
| GR01 CREATE_PROMISE_GOAL | PROMISE_ACCEPTED with validated terms and completion predicate; actor is promisor | CREATE ACTIVE goal pointing to promise and predicate | One goal per actor/promise; no goal for an unaccepted request |
| GR02 FULFILL_PROMISE | Verified outcome satisfies the accepted completion predicate | RESOLVE goal DONE; record fulfilled promise evidence | At most once per promise; actor/target mismatch rejects |
| GR03 BREACH_PROMISE | Verified deadline crossing or incompatible outcome satisfying breach predicate | RESOLVE goal ABANDONED with breach reason/evidence | Missing success is insufficient; ambiguous/conflicting outcome keeps status and records dispute |
| RR01 PROMISE_APPRAISAL | Actor is accepted promise beneficiary and has eligible observation of fulfillment/breach | CREATE/UPDATE relationship promise appraisal FULFILLED or BREACHED plus supporting refs | One appraisal per promise/outcome; trustBand and Disposition stay unchanged in v1 |
| MR01 WITNESSED_DANGER | Eligible native observation of a verified immediate-threat event classified by a reviewed native salience map | UPDATE mood to WORRIED with cause and expiryTurn = Core turn + 1 | ORDINARY emits no mood change; IMPORTANT/PIVOTAL share one-turn lifetime, no stacking |
| MR02 MOOD_EXPIRE | Current Core turn reaches stored expiryTurn with no later eligible danger trigger | UPDATE mood to UNSET; retain prior cause in history | One-turn expiry only; retry/reload/provider wait cannot change Core turn |

MR01 is a mechanical presentation state, not a new character personality or belief
about a threat's cause. Do not invent immediate-threat event classifications from
model prose. Their event-to-salience map must be reviewed before implementing MR01.
If no approved map entry exists, MR01 emits no delta. Canon-specific persona/voice
still bounds how the state is narrated.

RR01 deliberately records relationship evidence without changing scalar trust or
Disposition. A numerical trust transition requires a separately reviewed rule with
limits and fixtures; fulfilling the same promise repeatedly cannot farm a score.
This is explicit v1 scope, not an implicit unimplemented trust increment.

Mood conflict order: a later eligible danger observation replaces the prior mood
cause/expiry; for equal turn/ordinal use event ID. Apply MR02 only after checking the
batch for a newer eligible trigger. Unknown events never reset expiry.

## 5. Fixtures required before reducer implementation

These are specified expected outputs, not claims of executed unit tests.

| ID | Input | Exact expected property |
| --- | --- | --- |
| RB01 | First valid TOLD observation: X claims target Y is safe | New belief UNKNOWN with X/source refs; no world-fact change |
| RB02 | Same typed observation delivered twice | Identical brain after first application; no duplicate instance |
| RB03 | Memory/evidence belongs to another actor or slot | Reject; no brain or authoritative batch mutation |
| RB04 | LLM suggests KNOWN without native supporting rule | Reject proposal; prior projection unchanged |
| RB05 | Conflicting approved accounts | DISPUTED; both evidence chains retained |
| RG01 | First approved PROMISE_ACCEPTED | One ACTIVE goal with accepted predicate/terms |
| RG02 | Player/GM says a promise was accepted; no validated event | No goal created |
| RG03 | Verified outcome satisfies accepted predicate | Goal DONE exactly once, fulfillment evidence linked |
| RG04 | Wrong actor/target, unknown predicate or mere missing success | No completion/breach delta |
| RG05 | Explicit breach condition proven | ABANDONED and beneficiary appraisal BREACHED; no scalar trust change |
| RM01 | Approved important immediate danger at turn 17 | WORRIED, expiry 18, eligible actor only |
| RM02 | Reload/retry at turn 17 | Same mood and expiry; no decay |
| RM03 | Accepted Core turn 18 without newer eligible trigger | UNSET, original cause preserved in history |
| RM04 | Simultaneous decay and new danger at turn 18 | New cause/expiry wins; deterministic event order |
| RP01 | Same prior + evidence + rule version through reload/replay | Byte-equivalent canonical projection; no model call |
| RP02 | Unsupported canon/ability/world delta | Reject regardless of evidence count |
| RP03 | Required brain write fails during authoritative commit | All-or-nothing prior/new revision, never partial brain/world |

## 6. Storage and review gates

Pin ruleVersion in actor_brain and batch metadata; source event/observation records
are durable. Load reads the committed projection, not a new LLM interpretation.
Replay uses the pinned rule implementation and persisted inputs. Unknown rule or
canon revision fails explicitly; no automatic migration of personality on app update.

Before P2: implementer maps these typed contracts to reviewed runtime event types,
validates concrete event salience and promise predicates, writes failing fixtures
before reducer code, and passes actor/slot/atomicity tests on generated source.
Native/Core reviewer checks provenance, determinism, bounds and authority. This
artifact is the rule content for review; it is not an executed reducer certificate.
