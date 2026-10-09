# SaveStats & Character Brain: Technical Architecture V1

**Backrooms The Game | Technical Design | 2026-10-09**

**Status:** APPROVED ARCHITECTURE BASELINE; detailed contracts remain implementation-gated.
Product/authority approval: [Companion Product Decisions V1](COMPANION_PRODUCT_DECISIONS_V1.md).
No companion runtime, database or reducer is claimed implemented, compiled or shipped.
Local generated-source evidence: [G0 Verification V1](G0_EFFECTIVE_CHAIN_VERIFICATION_V1.md).
The G0 report is partial: patch execution and selected checks are not an APK/unit-test certificate.

**Decision:** Build the new experience **for a NEW GAME**. Compatibility, import and conversion of old save files are **out of scope**. Reuse reliable gameplay rules from the existing Core, **not** the old save architecture. This document supersedes the implementation order in [Living Companion Interaction RFC V1](LIVING_COMPANION_INTERACTION_RFC_V1.md).

**Product invariant:** The player always accompanies **Cao Minh**. They cannot control him, choose an independent route or split off. Cao Minh independently decides actions and the group's direction. The player interacts, questions, warns, persuades and influences his decisions. The GM narrates **accepted** outcomes only.

**Reviewed repository baseline:** main at 5450284ddc2acded7cc1cc54d3c035faff4ba4be, including nested patch invocations. This document derives designs from checked-in source; it does not claim to have reproduced the complete APK.

## 1. What actually exists, and what must be built

| System | Present in current repository | Needed for requested design |
| --- | --- | --- |
| Gameplay Core | GameState, GameCoreFacade, TurnCoordinator, command validation, items, combat, world state, time and physiology | Keep as authoritative rule execution; extend with native character decisions and one shared storage transaction |
| Save | Core v3 JSON via SharedPreferences; WebView projection via localStorage | **NEW** transactionally authoritative SaveStats database for **new runs**, independently versioned from UI |
| Story continuity | StoryContinuityReducer, wired into the effective nested release patch chain; trimmed arrays including 12 events, 16 knowledge and 8 relationship changes | **NEW** durable, append-only Event Ledger and retrieval; existing reducer is only a short working projection |
| Canon | Codex and budgeted KnowledgeContextEngine; nested generated knowledge/index patches | **NEW** actor-scoped, canon-aware personal memory/perspective retrieval |
| Character properties | CharacterState, skills/equipment, stats/physiology, current character presentation | **NEW** immutable Persona baseline + persistent individual CharacterMind state |
| LLM | Existing remote GM writer with risk-based semantic audits, local validation, repair and provider fallbacks | **NEW** Character Decision Engine using this same provider pool, with separate mind contexts, native decision validation and all existing safety gates preserved |
| UI | Three actions SEARCH/EXECUTE/EXPLORE with ActionRuntime and typed action authority | **LAST:** one visible [TƯƠNG TÁC] input after all foundations work |

**Do not conflate existing character stat data with personal intelligence.** A persisted hit point value is not a belief, memory, goal, personality or decision mechanism.

Source map:
- [GameState.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/GameState.kt)
- [GameCoreFacade.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt)
- [SaveRepository.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/SaveRepository.kt)
- [GameStateCodec.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/GameStateCodec.kt)
- [TurnCoordinator.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/TurnCoordinator.kt)
- [ActionRuntime.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/ActionRuntime.kt)
- [StoryContinuityReducer.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/StoryContinuityReducer.kt)
- [KnowledgeContextEngine.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt)
- [Exit Discovery v6 patch](../../android-apk/patch-exit-discovery-engine.py)
- [Effective nested patch invoker](../../android-apk/patch-character-detail-avatar-fallback.py)
- [Build workflow](../../.github/workflows/build-backroom-apk.yml)

## 2. Implementation-first dependency graph

~~~text
  Reviewed canon + existing validated Core mechanics
                      |
           [0] Baseline/gate audit
                      |
           [1] SaveStats NEW datastore
                      |
           [2] Immutable Event Ledger
                      |
           [3] Observation & Witness rules
                      |
      +---------------+----------------+
      |                                |
 [4] Persona baseline             [5] Episodic Memory
      |                                |
      +---------------+----------------+
                      |
        [6] CharacterMind state:
        beliefs, goals, moods, relationships
                      |
        [7] Actor-private Context Builder
                      |
        [8] Autonomous Decision Engine
        + existing writer/audit pipeline
                      |
        [9] Native ActionKind authority
        + existing Core and Exit Engine
                      |
        [10] One [TƯƠNG TÁC] UI
~~~

**Hard gate:** No step 8–10 until steps 1–7 persist/reload and pass actor-knowledge isolation tests. No UI-only mock that pretends the character brain exists.

**Release discipline:** Fresh main before each implementation phase, one small coherent change group,
explicit tests, commit, wait for applicable CI GREEN, then continue. Current workflows exclude
Markdown-only docs changes; report those checks as NOT APPLICABLE, never GREEN by absence.
Do not manufacture runtime changes to trigger CI. No merge/force-push/delete history without
review. Design-only PR does not authorize gameplay deployment.

## 3. SaveStats: design a new storage engine, not a patch to old saves

### 3.1 Authoritative transaction

**Choice:** A dedicated on-device SQLite DB for every new save slot, accessed through one native Kotlin repository boundary. Store structured Core snapshots **and** their causal history and actor minds **in the same database transaction**. SQLiteOpenHelper is one possible Android API; evaluate minSdk, journaling configuration and FTS availability before implementation. FTS5 is optional acceleration, never an assumed Android requirement.

WebView localStorage is **only UI cache**. Its text cannot update authoritative gameplay or NPC minds. Do not duplicate permanent truth across SharedPreferences and SQLite.

Existing Core's **rules** may be reused and adapted. Do **not** spend this project implementing an importer for legacy save v3.

### 3.2 SQLite schema V1 (design specimen; not adopted by production)

The following specimen addresses actor FK isolation, commit provenance and locked
recovery metadata. Native typed validation, batch completeness and the recovery
state machine remain mandatory; valid SQL alone does not establish authorization.
All connection handles enable foreign_keys before transactions. WAL/FULL is a
candidate target configuration, not a measured durability guarantee.

~~~sql
PRAGMA foreign_keys = ON;
CREATE TABLE save_slot (
  slot_id TEXT PRIMARY KEY,
  schema_version INTEGER NOT NULL,
  mode TEXT NOT NULL CHECK(mode = 'COMPANION_V1'),
  current_revision INTEGER NOT NULL CHECK(current_revision >= 0),
  current_turn INTEGER NOT NULL CHECK(current_turn >= 1),
  core_snapshot_json TEXT NOT NULL,
  core_snapshot_hash TEXT NOT NULL,
  created_at_ms INTEGER NOT NULL,
  updated_at_ms INTEGER NOT NULL
);
CREATE TABLE turn_receipt (
  slot_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  turn_id TEXT NOT NULL,
  input_hash TEXT NOT NULL,
  expected_revision INTEGER NOT NULL CHECK(expected_revision >= 0),
  committed_revision INTEGER,
  status TEXT NOT NULL CHECK(status IN ('PENDING','COMMITTED','REJECTED')),
  locked_decision_json TEXT,
  locked_decision_digest TEXT,
  resolved_action_kind TEXT,
  locked_target_ref TEXT,
  locked_rng_json TEXT,
  reservation_id TEXT,
  batch_manifest_json TEXT,
  final_result_json TEXT,
  PRIMARY KEY (slot_id, request_id),
  UNIQUE (slot_id, turn_id),
  UNIQUE (slot_id, committed_revision),
  UNIQUE (slot_id, turn_id, committed_revision),
  CHECK ((status = 'COMMITTED' AND committed_revision IS NOT NULL AND committed_revision = expected_revision + 1
          AND final_result_json IS NOT NULL AND batch_manifest_json IS NOT NULL)
      OR (status IN ('PENDING','REJECTED') AND committed_revision IS NULL)),
  FOREIGN KEY (slot_id) REFERENCES save_slot(slot_id)
);
CREATE TABLE world_event (
  slot_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL,
  ordinal INTEGER NOT NULL CHECK(ordinal >= 0),
  actor_id TEXT,
  event_type TEXT NOT NULL,
  world_node_id TEXT,
  location_key TEXT,
  verified_payload_json TEXT NOT NULL,
  evidence_refs_json TEXT NOT NULL,
  corrects_event_id TEXT,
  PRIMARY KEY (slot_id, event_id),
  UNIQUE (slot_id, turn_id, ordinal),
  FOREIGN KEY (slot_id, turn_id, committed_revision)
    REFERENCES turn_receipt(slot_id, turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED,
  FOREIGN KEY (slot_id, corrects_event_id) REFERENCES world_event(slot_id, event_id)
);
CREATE INDEX event_by_turn ON world_event(slot_id, turn_id, ordinal);
CREATE INDEX event_by_actor ON world_event(slot_id, actor_id, turn_id);
CREATE TABLE actor_observation (
  slot_id TEXT NOT NULL,
  observation_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  created_turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL,
  access_kind TEXT NOT NULL CHECK(access_kind IN ('SEEN','HEARD','TOLD','INFERRED')),
  source_actor_id TEXT,
  certainty TEXT NOT NULL CHECK(certainty IN ('CERTAIN','PLAUSIBLE','UNCERTAIN')),
  CHECK(access_kind != 'INFERRED' OR certainty != 'CERTAIN'),
  PRIMARY KEY (slot_id, observation_id),
  UNIQUE (slot_id, observation_id, actor_id),
  UNIQUE (slot_id, actor_id, event_id, access_kind),
  FOREIGN KEY (slot_id, event_id) REFERENCES world_event(slot_id, event_id),
  FOREIGN KEY (slot_id, created_turn_id, committed_revision)
    REFERENCES turn_receipt(slot_id, turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX observation_by_actor ON actor_observation(slot_id, actor_id);
CREATE TABLE actor_memory (
  slot_id TEXT NOT NULL,
  memory_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  observation_id TEXT NOT NULL,
  created_turn_id TEXT NOT NULL,
  committed_revision INTEGER NOT NULL,
  subjective_summary TEXT NOT NULL,
  interpretation_source TEXT NOT NULL CHECK(interpretation_source IN ('NATIVE','MODEL_AUDITED')),
  salience TEXT NOT NULL CHECK(salience IN ('ORDINARY','IMPORTANT','PIVOTAL')),
  status TEXT NOT NULL CHECK(status IN ('ACTIVE','SUPERSEDED')),
  corrected_by_memory_id TEXT,
  PRIMARY KEY (slot_id, memory_id),
  UNIQUE (slot_id, memory_id, actor_id),
  FOREIGN KEY (slot_id, observation_id, actor_id)
    REFERENCES actor_observation(slot_id, observation_id, actor_id),
  FOREIGN KEY (slot_id, created_turn_id, committed_revision)
    REFERENCES turn_receipt(slot_id, turn_id, committed_revision)
    DEFERRABLE INITIALLY DEFERRED,
  FOREIGN KEY (slot_id, corrected_by_memory_id, actor_id)
    REFERENCES actor_memory(slot_id, memory_id, actor_id)
);
CREATE INDEX memory_by_actor ON actor_memory(slot_id, actor_id, salience);
CREATE TABLE memory_topic (
  slot_id TEXT NOT NULL,
  memory_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  topic TEXT NOT NULL,
  PRIMARY KEY (slot_id, actor_id, topic, memory_id),
  FOREIGN KEY (slot_id, memory_id, actor_id)
    REFERENCES actor_memory(slot_id, memory_id, actor_id)
);
CREATE TABLE actor_brain (
  slot_id TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  brain_schema_version INTEGER NOT NULL,
  rule_version TEXT NOT NULL,
  canon_persona_ref TEXT NOT NULL,
  canon_revision TEXT NOT NULL,
  beliefs_json TEXT NOT NULL,
  goals_json TEXT NOT NULL,
  mood_json TEXT NOT NULL,
  relationships_json TEXT NOT NULL,
  disposition_json TEXT NOT NULL,
  committed_revision INTEGER NOT NULL CHECK(committed_revision >= 0),
  PRIMARY KEY (slot_id, actor_id),
  FOREIGN KEY (slot_id) REFERENCES save_slot(slot_id)
);
CREATE TRIGGER immutable_event_update BEFORE UPDATE ON world_event
BEGIN SELECT RAISE(ABORT, 'world_event_is_append_only'); END;
CREATE TRIGGER immutable_event_delete BEFORE DELETE ON world_event
BEGIN SELECT RAISE(ABORT, 'world_event_is_append_only'); END;
~~~

Specimen deletion lifecycle: each slot has its own database, as in §3.1. Explicit
Delete Slot closes handles and deletes that slot's DB plus sidecars/cache through
the native lifecycle; it does not issue row deletion against immutable history.
Never reuse this trigger policy in a shared-slot DB without designing slot deletion.
No automatic data deletion or resurrection after failure. Legacy data remains in its
existing namespace and is not touched by Companion V1 lifecycle operations.

Revision 0 initialization atomically creates the approved fresh Core snapshot and
initial persona/brain projections; no fabricated gameplay receipt or world event.
After genesis, native completeness checks compare the current snapshot with its
COMMITTED receipt/manifest and verify record provenance. Historical records/unchanged
brains may be older than current_revision; future/uncommitted records reject on load.
JSON bounds, typed claims, exposure and state-machine transitions are native contracts.
Queryable memory topics use ordinary indexes rather than requiring FTS. No separate
claim or event-participant table is mandated before concrete queries require it.

### 3.3 Transaction rules

1. Verify slot ID, request ID, expected revision and request hash.
2. Load Core snapshot at revision **N** and affected committed brain projections valid at N; their last-update revision may be older. Verify provenance and pinned versions.
3. Run authoritative validation; never accept LLM JSON as a new state.
4. Persist accepted Core snapshot **N+1**, event rows, per-actor observations, updated brains and COMMITTED receipt in **one** SQLite transaction.
5. Respond to UI **after** transaction success. If DB commit fails, the world has not moved.
6. Duplicate request + identical input replays the same committed receipt, not RNG, loot or dialogue side effects.
7. Duplicate request ID + different input or stale expected revision rejects.
8. Provider/model failure before commitment leaves previous revision intact.
9. Crash after commit but before UI callback replays committed output.
10. Save slot creation starts a **fresh campaign**. Missing or corrupt DB is an explicit error. Never overwrite it by silently pretending a fresh game loaded.

**One source of truth:** snapshot and current receipt agree on revision; historical records
and unchanged brains carry their own creation/update revisions and must not exceed it.
Completeness comes from the commit manifest, not max(revision) equality across all tables.
WebView/legacy storage never overwrites companion truth.

**Pending recovery:** a single active native pending turn/reservation at a slot revision
binds decision schema/digest, actor, scene/evidence revision, kind/target and dice. Request
aliases must not allocate a fresh roll. Before reserving RNG, intent can be validated;
after reservation it is immutable. Recover the same persisted tuple after crash.
Cancellation/rejection cannot reset entitlement to draw independently at the same pending
turn. The concrete state machine is decided in [Pending Recovery V1](SAVESTATS_PENDING_RECOVERY_CONTRACT_V1.md).
After decision lock, cancellation suspends instead of clearing the turn. Existing
SecureRandom outcomes are captured durably before exposure; no request-ID-seeded RNG.
SQLite/Core integration and crash proof remain required before runtime adoption.

### 3.4 Kotlin API contract (proposed)

~~~kotlin
interface SaveStatsRepository {
    fun newSlot(seed: ApprovedCampaignSeed): SlotResult
    fun load(slotId: String): LoadResult
    fun beginRequest(slotId: String, requestId: String, revision: Long,
                     inputDigest: String): PendingResult
    fun commit(batch: ValidatedTurnBatch): CommitReceipt
    fun committedReceipt(slotId: String, requestId: String): CommitReceipt?
    fun events(slotId: String, query: EventQuery): List<WorldEvent>
    fun memories(slotId: String, actorId: String, query: MemoryQuery): List<ActorMemory>
    fun brain(slotId: String, actorId: String): CharacterBrainState?
}
~~~

**ValidatedTurnBatch is native-only.** It carries accepted Core result, locked decision,
provenance, exposure-approved observations, rule-derived brain deltas, pinned rule version,
manifest and expected revision. Build all required deltas after outcome validation and
before DB commit. Do not commit Core and then call a model to patch brain state.
No new export feature is authorized by this API specimen.

## 4. Event Ledger: SaveStats remembers what actually happened

**Goal:** turn 5,000 can retrieve the verified result of turn 17 without quoting a truncated six-message log.

~~~json
{
  "eventId": "E_0017_02",
  "turnId": "TURN_17",
  "actorId": "cao_minh",
  "eventType": "EXIT_DISCOVERED",
  "nodeId": "level-0",
  "locationKey": "junction",
  "verifiedPayload": {
    "exitRecordId": "EXIT_17",
    "discoveredBy": "SEARCH"
  },
  "evidenceRefs": ["CORE_EXIT_RECORD_17", "DEC_17"]
}
~~~

The example is **illustrative**, not evidence that any named exit has actually been found in a user's run.

**Append-only categories:** location transitions, exit discovery/traversal, meetings, conversations actually held, promises, item acquisitions/losses, combat outcomes, alliance changes, injuries and time-consuming actions. A **spoken claim** is stored as “actor X said Y”, never promoted into objective world truth merely because it was said.

**Provenance:** `verifiedPayload` must be generated from accepted commands and native rule outcomes; candidate GM prose, secret canon excerpts or unvalidated player text cannot create a WorldEvent. Corrections append a linked correcting event. Stable IDs and indexes ensure history remains available while active prompt context stays small.

The existing StoryContinuityReducer remains a **short derived projection**, not a ledger. Keep its effective integration; derive future summaries from accepted events rather than trusting LLM-created timelines.

## 5. Individual memory: one event, different minds

### 5.1 Character observation is not Party membership

- Only actors in the native-approved exposure set can acquire **SEEN/HEARD**: scene membership, reach, consciousness and visibility/audibility all matter. Same node alone is insufficient.
- If another character tells them about an event, they acquire **TOLD** information whose speaker may be wrong.
- Persist **INFERRED** only through a reviewed inference rule with actor-owned source observations and uncertainty; evidence IDs alone do not validate an inference. No CERTAIN inference.
- Absent, unconscious or out-of-reach actors get no automatic knowledge.
- Sharing the same LLM provider gives no character a license to read other actors' memory tables.
- Historical Codex knowledge is separate from **in-campaign** observed memories.

**Lục Trầm example:** Seeing Cao Minh near dead bodies after Táng Kiếm Cốc may support her suspicion. It must **not** promote writer-only ritual secrets or an unverified causal accusation into her known facts. Do not hallucinate an already completed Backrooms reunion.

### 5.2 ActorMemory fields

~~~kotlin
data class ActorObservation(
    val observationId: String,
    val ownerActorId: String,
    val sourceWorldEventId: String,
    val access: AccessKind,
    val sourceActorId: String?,
    val certainty: Certainty
)
data class ActorMemory(
    val memoryId: String,
    val ownerActorId: String,
    val sourceObservationId: String,
    val interpretation: String,
    val importance: Salience,
    val topics: Set<String>,
    val state: MemoryState
)
~~~

**Storage does not require an LLM call.** Native observation/record creation is deterministic; model interpretation can be proposed separately, checked for consistency, and discarded if unsupported.

### 5.3 Retrieval protocol

~~~kotlin
fun retrieveForActor(
    slotId: String,
    actorId: String,
    scene: ValidatedScene,
    mentionedActorIds: Set<String>,
    activeGoals: Set<String>,
    tokenBudget: Int
): ActorMemoryPacket
~~~

Rank: **explicit episode references > current scene/actors > unresolved goals and promises > pivotal memories > recently relevant events**. Deterministic stable tie-breakers. Always filter **slot_id AND actor_id** before relevance ranking. Prompt-budget limits control **retrieval**, not permanent memory retention.

**Test:** At turn 10,000, Cao Minh recalls a relevant old promise; Lục Trầm does not know that private promise without a supported communication event.

## 6. Persona + Personality: not a string in a prompt

The system must implement **four separate, testable layers**:

| Layer | Source | Can change? | What it governs |
| --- | --- | --- | --- |
| **CanonPersona** | Approved Codex | Only approved canon revision | Identity, values, habitual voice, capability bounds, ethics |
| **Disposition** | Validated campaign experience | Slowly, with evidence | Accumulated trust, caution, risk appetite, social habits |
| **TransientMood** | Recent witnessed meaningful events | Quickly, bounded | Immediate anger, worry, fatigue, calm, attention |
| **Goals / Beliefs / Relationships** | Actor-specific experience | Through causal updates | What actor wants, thinks happened, expects of other actors |

These layers must not collapse into an untyped “personality score.” A moment of anger cannot rewrite Cao Minh's canon, unlock new combat skills or turn him into an obedient player puppet.

### 6.1 CanonPersona schema

~~~json
{
  "actorId": "cao_minh",
  "source": "CAO_MINH_CODEX.md",
  "sourceRevision": "approved-revision-id",
  "traitRefs": ["approved-trait-record-ids"],
  "ethicalBoundsRefs": ["approved-canon-rule-ids"],
  "voiceRefs": ["approved-dialogue-guidance-ids"],
  "knowledgeLocks": ["WRITER_SECRET_IS_NOT_CHARACTER_KNOWLEDGE"]
}
~~~

The IDs are examples; the full trait content must be authored **from approved canon**, not invented to fill empty fields. Stable runtime `KAI_ID` -> `cao_minh` mapping and knowledge keys `CHAR.KAI.*` are deliberate and **must not be renamed**.

### 6.2 CharacterBrain state

~~~json
{
  "actorId": "cao_minh",
  "brainVersion": 1,
  "canonPersonaRevision": "approved-revision-id",
  "currentGoals": [],
  "beliefs": [],
  "disposition": {},
  "mood": {"state": "UNSET", "causeEventIds": []},
  "relationships": [],
  "episodicMemoryRefs": [],
  "lastDecisionId": null
}
~~~

Empty values mean **unknown/unestablished**, not missing design. The game must not invent a relationship, backstory or physical attributes for the player simply to initialize a brain.

### 6.3 Belief, goal, mood, relationship contracts

~~~text
Belief:
  claimId, stance(KNOWN|BELIEVED|SUSPECTED|DISPUTED|UNKNOWN),
  sourceObservationIds, confidenceBand, updatedAtTurn

Goal:
  goalId, originatingCanonOrEventRef, priorityBand,
  status(ACTIVE|PAUSED|DONE|ABANDONED), constraints, updatedAtTurn

Mood:
  label, triggerEventIds, intensityBand, decayPolicy, updatedAtTurn

Relationship:
  targetActorId, perceptionOfTarget, trustBand, unresolvedPromises,
  supportingEventIds, lastChangedTurn
~~~

**Reducer:** deterministic versioned rules, not LLM brain proposals. See
[Character Brain Rule Table V1](CHARACTER_BRAIN_RULE_TABLE_V1.md) for CREATE/UPDATE/RESOLVE,
typed claim/promise acceptance, evidence, bounds, conflict policy and exact fixture outputs.
CanonPersona is outside writable fields. Unknown template emits no delta; a required
projection write failure aborts the whole batch. Mood decay uses Core turn, never wall
clock/retry/provider waiting. Rule/canon revision mismatch fails explicitly until a
separate reviewed conversion policy exists; a CANON_REVISION_CHANGED event is not a bypass.

**Avoid numerical manipulation:** repeated identical player suggestions do not farm trust. New evidence, genuinely fulfilled promises and actual shared danger can matter. Any numeric mechanic used later must be reviewable, bounded and tested; values are *mechanics*, not objectively measured personality.

## 7. Character Context Builder: only what this actor knows

~~~text
ACTOR_CANON_PERSONA
    reviewed character identity, approved traits/voice and ability limits
VALIDATED_SCENE
    node, location, party, physical capabilities, encountered threats
ACTOR_OBSERVATIONS
    direct observation + hearsay + uncertainty, ONLY this actor
ACTOR_RETRIEVED_MEMORY
    event references, significance, subjective interpretation
ACTOR_CURRENT_MIND
    goals, mood, beliefs, relationships
PLAYER_INPUT
    speech/advice/request only, never a direct authority token
DECISION_OUTPUT_SCHEMA
    structured untrusted proposal
~~~

The GM may receive additional **writer-only** canon to avoid continuity mistakes, but the Cao Minh-specific decision request may not treat the GM's writer-only reference material as Cao Minh's personal knowledge. Thus **GM-context != actor-context**.

Observability dependencies: [Canon P0](CANON_P0_CHARACTERIZATION_SPEC_V1.md) and
[Provider P0](PROVIDER_P0_OBSERVABILITY_SPEC_V1.md). Separate actor memory/canon/state
budgets and decision/writer/audit/repair/fallback roles; never log prompt/Canon text or keys.
Build packets using reviewed KnowledgeContextEngine mechanisms plus **new actor-scope retrieval**. Check full generated engine after nested Python patches. Existing 2200/2800/3400 context thresholds are current *source markers*, not a guarantee that a new brain packet fits. Allocate and measure separate memory/canon/state budgets before implementation.

## 8. Autonomous reasoning with the current remote LLM

**No LLM inside the APK, no new model training.** Use the existing remote model pool to reason only when a present character must decide. Actor-specific intelligence emerges from stable canon, durable mind, bounded memory and constrained decision/commit protocols. This simulates autonomy without assuming separate independently trained model weights.

### 8.1 The correct turn sequence

~~~text
 Player types an interaction
   |
   v
 Native receives INTERACT (NO gameplay kind)
   |
   v
 SaveStats loads Core@N and CaoMinhBrain@N
   |
   v
 Perception + personal memory + goals + canon packet
   |
   v
 LLM proposes Cao Minh's response/intent/target (UNTRUSTED)
   |
   v
 Deterministic actor/scene/evidence preflight
   |
   v
 Required semantic validation of actor's decision
   |
   v
 Native resolves and locks action kind/target
   |
   v
 Existing Core rolls / effective exit progression / ActionRuntime validation
   |
   v
 Existing writer + risk-based canon/character audit
 + local validator + bounded repair (NO audit bypass)
   |
   v
 Native final check: no changed locked decision or reused stale rolls
   |
   v
 GameCore validates outcome; native derives events/observations/rule deltas
 + SaveStats commits Core/events/observations/brains/receipt atomically
   |
   v
 GM speaks only about the committed outcome
 Player stays with Cao Minh at his validated location
~~~

**Critical sequencing issue:** Existing action buttons give action kind **before** the writer; companion mode does not know the kind until Cao Minh decides. Therefore a blanket “one inference call per turn” cannot be promised. A scene with dice consequences may require an actor decision request **and** a post-roll writer request, plus current audit/repair and provider retries. **Correctness beats minimizing LLM calls.**

**RNG policy:** [Pending Recovery V1](SAVESTATS_PENDING_RECOVERY_CONTRACT_V1.md) locks the accepted decision and captures existing native dice before provider/UI exposure. Repair cannot change action/target; failures suspend the same turn. No post-lock clear/restart permits another roll. Uncommitted, unexposed capture work may be retried after transaction rollback; persisted reservations replay without calling RNG. Core integration and crash tests remain required.

### 8.2 CharacterDecision proposal (never directly mutates world)

~~~json
{
  "schema": "companion_decision.v1",
  "actorId": "cao_minh",
  "intent": "MOVE",
  "targetRef": "corridor-left",
  "utteranceDraft": "Ta muốn kiểm tra bên trái trước.",
  "perceptionRefs": [],
  "memoryRefs": [],
  "reasonCodes": ["PERSONAL_PREFERENCE", "RISK_ASSESSMENT"]
}
~~~

The character may **accept, reject, question, negotiate or defer**. A direct statement by the player such as “rẽ phải ngay” becomes an argument/request; it is not a MOVE command. Every intent is validated against scene and Core rules.

### 8.3 Preserve effective authorities, not an overwritten intermediate patch

**G0 correction:** on the reviewed main, patch-player-action-ime.py calls
patch-exit-streak-integration.py after patch-exit-discovery-engine.py. It removes the
v6 engine/test and installs EXIT_STREAK_V1. See the pinned G0 report. SEARCH-only v6
exit rolls, An Nhiên exit read and traverse_exit dispatch are not active final-output
contracts. Do not resurrect them merely because an earlier patch generates them.

- **Action kind:** native validates actor/scene/target at expected revision and resolves
  SEARCH/EXPLORE/EXECUTE from closed intent, never utteranceDraft/player keywords.
- **Current progression:** final output uses ExitStreakEngine and Core-validated featured
  routes; existing 15-code-point input eligibility, one 50/50 roll per accepted ordinary
  non-combat turn and five-consecutive-win progression are the measured source contract.
  Actor INTERACT envelope alone does not grant a gameplay turn or random draw. The mapping
  of accepted actor decisions is now specified by [Action / Exit V1](COMPANION_ACTION_EXIT_CONTRACT_V1.md); its production adapter still requires A2 tests.
- **Migration gate:** the approved baseline's Exit v6 assumption is disproved. Preserve
  current runtime. [Action / Exit V1](COMPANION_ACTION_EXIT_CONTRACT_V1.md) reconciles the mapping; implementation evidence remains required.
  No restoration, extra exit engine, balance change or stale v6 test removal is a task
  authorized by this document. This conflict blocks runtime readiness, not doc authoring.
- **Audit:** risk/content drives applicable semantic audit, local validation, repair and
  fallback. No exemption merely because a decision contains only dialogue/no action kind.
- **Failure:** provider/quota failure keeps committed state/brain unchanged; no invented
  character reaction is persisted.
- **Narration:** output inconsistent with committed outcome is discarded/regenerated with
  no second mutation.

## 9. Player companionship and UI, only after the brain exists

**PlayerPresence** is an observation/conversation viewpoint permanently anchored to Cao Minh for route movement, **not** a second freely navigable Party actor. The system may allow tightly scoped in-scene gestures only after explicit capability design. It cannot move independently between Levels or pick up Cao Minh's inventory by text.

~~~json
{
  "playerPresence": {
    "id": "player_presence",
    "anchorActorId": "cao_minh",
    "movementRule": "FOLLOW_ACCEPTED_ANCHOR_ROUTE",
    "canSplit": false
  }
}
~~~

**Example expected behavior:**

GM: “Hai hành lang mở ra phía trước.”

Cao Minh: “Ta định rẽ trái.”

Player [TƯƠNG TÁC]: “Bên phải có vẻ đáng tìm hiểu.”

Cao Minh: “Ta vẫn muốn xem bên trái trước.”

GM: “Cao Minh đi về phía trái. Bạn tiếp tục đồng hành cùng hắn.”

That narrative is valid **only if native Core committed LEFT**. If Cao Minh is reasonably persuaded by additional evidence, the next validated action may differ; direct text cannot place the player on RIGHT in secret.

Only after SaveStats, memory, mind and decision engine work should the UI retire the three gameplay buttons and present one [TƯƠNG TÁC] submission button. Utility menus need not disappear.

## 10. Release phases with proof requirements

| Phase | Deliverable | Proof required before proceeding |
| --- | --- | --- |
| **G0** | Reproduce effective nested patch-chain and inspect generated Java/Kotlin/HTML, current audit, exit authority | Exact patch invocations, output artifacts, tests/CI baseline |
| **S1** | New SQLite SaveStats database for fresh runs and typed repository API | Create/save/load/rollback/slot-isolation/process-crash tests |
| **S2** | Native immutable event ledger linked to Core accepted outcomes | Unauthorized ops cannot write facts; old event retrieval at 5k turns |
| **S2.5** | Fresh isolated test DB/slot and command adapter; real Core/SaveStats, existing three buttons only as drivers | Core parity, crash/retry, 1k/5k/10k measurements; no persisted legacy mode/dual write |
| **M1** | Per-actor observation ledger and provenance | No actor knowledge leak, absent actor cannot witness |
| **P1** | Reviewed canon-persona registry for Cao Minh then Lục Trầm | Current Codex parity; stable identity/knowledge namespace |
| **M2** | Durable episodic memory and actor-specific retrieval | Separate minds, subjective beliefs and indexed old memories |
| **P2** | Goals, beliefs, mood, disposition and relationship reducer | Causal updates, deterministic reload, no unsupported trait changes |
| **C1** | Actor-specific private Context Builder | No writer secrets or other actor's private memories |
| **A1** | Character Decision proposal and validator inside audit chain | Accept/refuse/reconsider, Core authority, retry/fail-closed |
| **A2** | Native ActionKindResolver and effective exit progression binding | Resolve G0 v6/streak conflict; preserve current costs/eligibility/RNG; no action authority from text |
| **UI1** | One [TƯƠNG TÁC] input and role-separated transcript | End-to-end from input through brains/validation/commits, keyboard working |
| **R1** | Release candidate + real playtest | 1k/5k/10k turns, save stability, long memory, quality and latency reports |

**No old-save migration phase.** Preserve legacy data, require explicit Companion NEW GAME, and never silently convert/load-reset. A fresh new game is the only activation path for this architecture. Preserve game **rules** that work; do not allow legacy data-format concerns to dictate the new system.

## 11. Test contract

| ID | Test | Observable pass condition |
| --- | --- | --- |
| SS01 | New game persistence | Native DB restores same state/revision and linked histories |
| SS02 | Atomicity | Crash during commit yields either complete new revision or complete old revision |
| SS03 | Duplicate request | Same receipt, same effects, no second exit/loot roll |
| SS04 | Stale UI message | No authoritative mutation |
| SS05 | Slot isolation | No cross-save memory or inventory |
| SS06 | 10,000 turns | Early validated events still retrievable via event IDs |
| EV01 | Model fabricates an item | No ledger/world change without authorized Core result |
| EV02 | Corrected testimony | Correcting event linked; original evidence retained |
| ME01 | Separate witnessing | Cao Minh and Lục Trầm see only eligible observations |
| ME02 | Absent character | No observation learned merely because story narrated it |
| ME03 | Hearsay | “Was told” is not upgraded to “knows truth” |
| ME04 | Pivotal promise | Actor recalls it 5,000 turns later through indexed retrieval |
| PS01 | Persona invariance | Cao Minh retains current Canon identity after long run, reload and provider failover |
| PS02 | Mood vs persona | Temporary anger cannot rewrite ethics, skills or equipment |
| PS03 | Relationship progression | Trust changes only with supported meaningful events |
| PS04 | Retired identity | Lucia retired identity cannot produce a phantom second Lục Trầm brain |
| AI01 | Player urges RIGHT | No direct action; Cao Minh decides and player remains bound |
| AI02 | Cao Minh refuses | GM renders only actual accepted LEFT or no movement |
| AI03 | New evidence | Cao Minh can reconsider causally; not forced either way |
| AI04 | Fake evidence reference | Proposal rejected before action commit |
| AI05 | Wrong actor memory | Retrieval refuses cross-actor/private knowledge |
| AK01 | Non-authoritative input | INTERACT/player keywords alone never allocate gameplay RNG or exit progress |
| AK02 | Accepted ordinary action | Native eligibility and kind preserve effective progression/RNG; no resurrection of retired SEARCH-only v6 logic |
| AK03 | Effective route progression | Core validates streak source/target and commits transition once; A2 mapping must reconcile the retired v6 dispatch |
| AU01 | Semantic audit fails | No Core change, no fabricated success |
| AU02 | Repair changes action | Old approved action kind and its dice cannot be reused |
| CI01 | Full nested patch chain | Effective knowledge and continuity hooks persist; no fragile anchors |
| CI02 | Actual APK | UI and bridge execute new contract with real Core/DB, not fake mocks |

**Deterministic correctness tests** must be exact. Model behavior is judged by properties, source-grounded cases and human playtests rather than expecting identical strings on every inference.

## 12. Performance and bounded AI cost

Goals are **measurements to obtain**, not invented successful benchmarks:

- Durable storage per 1k/5k/10k turns: DB size, writes/turn, latency, replay duration.
- Actor memory retrieval: correct episode IDs under old-turn recall, precision/leak rate and query latency on target Android phone.
- Context budget: actual generated knowledge packet + actor memory, with hard canon never clipped unpredictably.
- AI: per interaction **decision calls, writer calls, risk-based audit calls, repairs, provider failover attempts**, p50/p95 time and token usage.
- Offline: reading valid saved stats and memory indices may work locally; autonomous new decisions may require network. Never claim a fully offline independent intelligence.
- No idle thinking loops for absent NPCs; actor reasoning triggered only when a relevant decision is needed.

**Design principle:** A small text APK can use a shared remote LLM as a reasoning service. Its real technical challenges are **correct long-term state, memory attribution, stable personality, restricted knowledge, authority-bound decisions and reliability**, not shipping several foundation models inside the APK.

## 13. Explicitly out of scope

- Migrating/repairing/importing old saves.
- Maintaining old player-controls-Cao behavior within the new save mode.
- Training or embedding a dedicated LLM for each character.
- Automatically inventing the player's physical abilities, inventory, relationship history or story origin.
- Bypassing existing canon, equipment, combat, dice, Entity spawn or world/exit rules.
- Replacing the existing provider fail-closed audit with a cheap, unchecked single call.
- Showing an attractive but fake character-brain UI before persistence and decisions work.

## 14. Reviewer checklist / approval gates

Approved product policy and authoring ownership are recorded in
[Product Decisions](COMPANION_PRODUCT_DECISIONS_V1.md). Ponytail now owns remaining
work following Orion handoff. Independent review is still outstanding where noted;
do not attribute future review approval to an agent who has left.


**Core/storage reviewer:** atomic transaction, native only write, idempotency, correct ownership and reroll prevention.

**Memory reviewer:** exact event provenance, actor exposure and false-belief representation, large-history retrieval.

**Canon reviewer:** current Cao Minh/Lục Trầm identity, stable KAI_ID/CHAR.KAI coupling, secrecy boundaries and no invented relationship.

**AI reviewer:** personalized actor context, candidate schema, semantic audit placement, repair/failure behavior and provider costs.

**Gameplay reviewer:** autonomous actor-to-kind mapping, effective streak/route semantics and the G0 reconciliation gate, multi-character interactions.

**QA/release reviewer:** full nested patch chain, compilation, behavioral fixtures, real-device latency and rollback.

**Requested implementation order:** Start with **SaveStats + Event Ledger**, then **personal memory**, then **personality/mind**, then **LLM-driven character decisions**, and **only last** the single-button interaction. A character is not intelligent merely because a prompt calls it intelligent.

---

**Bottom line:** Build the new brain and its memory first. The new UI is the consequence of that system, not the starting point.
