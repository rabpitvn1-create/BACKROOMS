# M1a: actor exposure eligibility boundary

Status: implementation slice; runtime observation production and persistence remain disabled.
Parent: #476. Owner: Ponytail under the owner's continuation instruction.
Inspected main: a1b6e2f1c12620dc4b8bb80529cbe58b15e81505.
Runtime base: PR #494 at 79a1b569327dd6f8f310fdea3194218a41bce548.

## Scope

CompanionExposurePolicy evaluates synthetic or reviewed native evidence without a
provider, JSON parser, UI bridge, RNG, database write or Core mutation. It returns
actor/channel eligibility references, not observations, world facts, a validated
batch or access to an event payload. This is M1a, not complete M1.

The owner's request to proceed while the long benchmark runs permits this isolated
foundation slice. PR #494's 5k benchmark remains pending; neither S2.5 qualification
nor its numerical performance budgets are marked complete.

## Binding and eligibility

Every evidence row must match the event's slot, turn, committed revision, event ID
and exact scene ID. Revision is positive because genesis produces no gameplay event.
Actor evidence is unique and bounded to 64 rows; duplicate rows reject instead of
combining one actor's senses or silently overwriting conflicting evidence.

SEEN requires all of scene membership, perceptual reach, consciousness and visibility
to be explicitly YES. HEARD substitutes audibility for visibility. NO and UNKNOWN
both deny that channel. Channel declaration cannot establish the required facts.
No party, node, input word, provider identity, canon excerpt or quoted story is
accepted as a substitute. Actor order has deterministic actor-ID/channel ordering.

PRIVATE_GM and UNCLASSIFIED never emit eligibility. A private/unclassified event
declaring perceptible channels rejects. Only native-classified perceptible events
may declare SEEN/HEARD; the policy does not infer classification from prose.
Payload projection remains a separate mandatory boundary: eligibility cannot
expose writer secrets contained elsewhere in the same world-event record.

TOLD requires a future native-approved speaker/listener communication contract;
it is not interchangeable with hearing a sound. INFERRED remains unavailable
until an actor-owned source chain and a reviewed inference rule exist. Certainty
that a speaker spoke never proves the quoted claim.

## Native adapter gate

The facts constructor is an internal Kotlin boundary for reviewed native callers,
not proof that the facts are true. There is currently no runtime adapter or
Javascript/provider path invoking this policy.

Before enabling observation creation, the adapter must:
- Derive all facts from authoritative native/Core outcomes at the event's actual
  scene and temporal position, bound to the same proposed committed revision.
- Prove location, reach, consciousness and each applicable sensory condition.
  Existing party membership and a shared world node are insufficient.
- Fail closed for any field not represented by an approved native contract.
  Never manufacture booleans from narration, remembered tags or codex capabilities.
- Exclude private payloads and bind each observation to its validated event and
  actor. Preserve the distinction between a communication and its proposition.
- Construct required observations before the single SQLite commit, bind their
  complete ordered identities/digests in the receipt manifest, and reject the
  entire batch on observation-write failure.

WAIT publication currently has an empty observation set. This slice does not
change it, adopt a new storage format, initialize a fabricated reunion, or mark
Cao Minh/Luc Tram as having witnessed unrepresented facts.

## Evidence and remaining work

Ten deterministic tests cover channels, missing/unknown senses, absent actors,
actor isolation, private/unclassified events, every scope component, duplicate
evidence, ordering, immutable collections and bounds. Their native evidence is
synthetic; passing them does not certify a real perception producer.

CI must reproduce the 46-patch chain, verify unchanged generated Main/facade/HTML,
run the complete Core suite and APK build, then run existing real SQLite and
fresh-process crash regressions. A 1k WAIT backend regression is sufficient for
this unused pure-policy slice; PR #494 separately retains the 5k pilot.
No local compile is claimed while the terminal environment is offline.

M1b still needs native evidence mapping, persisted immutable observations,
actor-filtered retrieval, receipt completeness and atomic rollback/reload proof.
M2 memory, P1 persona, P2 brain, C1 private context and later runtime/UI gates
remain separate. No merge, release or companion activation is performed here.
