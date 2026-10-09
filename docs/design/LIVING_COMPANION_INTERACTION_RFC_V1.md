# RFC: Living Companion Interaction V1
**Backrooms The Game | Design for review | 2026-10-09**

- **Status:** APPROVED ARCHITECTURE/PRODUCT BASELINE; companion runtime NOT IMPLEMENTED.
- **Approval record:** [Companion Product Decisions V1](COMPANION_PRODUCT_DECISIONS_V1.md), delegated owner approval.
- **Technical contract:** [SaveStats Technical Design](SAVESTATS_CHARACTER_BRAIN_TECHNICAL_DESIGN_V1.md) takes precedence; [Rule Table V1](CHARACTER_BRAIN_RULE_TABLE_V1.md) specifies deterministic brain updates.
- **Evidence:** [G0 Verification V1](G0_EFFECTIVE_CHAIN_VERIFICATION_V1.md). Local generated-source/selected checks are partial, not APK/CI certification.
- **Repository:** [rabpitvn1-create/BACKROOMS](https://github.com/rabpitvn1-create/BACKROOMS)
- **Audited source:** main at **5450284ddc2acded7cc1cc54d3c035faff4ba4be** (1.1.63.0.6 release-status commit). The generated APK was **not** rebuilt or decompiled during this review.
- **Change class:** major gameplay interaction, narrative orchestration and fresh-run persistence; legacy save migration/import is out of scope.
- **Scope of this PR:** design/decision/rule/observability documents and partial G0 evidence only. No companion implementation; executed checks are individually listed in the G0 report.

> **One-sentence product contract:** The player is always physically accompanying Cao Minh, cannot independently choose a different route, and cannot control Cao Minh. The player can communicate, reason, persuade and influence; Cao Minh observes, reasons and decides autonomously. If he chooses the left corridor, the player follows unless they persuade him to change course.

## 1. Product decision and non-negotiable rules

1. **One visible primary action: [TƯƠNG TÁC].** Retire the visible THỰC HIỆN / KHÁM PHÁ / TÌM KIẾM choice. A free-text input supports speech, questions, suggestions and contextual interaction. Menus for save, status, inventory and accessibility may remain; “one button” means one **gameplay submission** button, not deleting utility/navigation controls.
2. **Two identities, one route.** The **player** is an in-world companion and conversational point of view. **Cao Minh** is a distinct in-world character and the party's route leader. Their identities, knowledge and agency are never conflated. The player is *not* a second free-roaming avatar.
3. **Bounded co-presence.** The player's world position is derived from Cao Minh's committed route/location; no split-party player exploration, teleporting, scouting another Level, or unilateral route changes. Temporary in-scene positioning (e.g. standing a few steps away) does not create an independently navigable route.
4. **No forced obedience.** User text is an input to Cao Minh's reasoning, never direct permission to mutate his state. He may accept, decline, ask for evidence, propose a compromise or defer.
5. **Meaningful persuasion.** Cao Minh must consider new relevant evidence, his remembered experiences, current risks and goals. Repeating identical demands is not a magical persuasion multiplier; neither is refusal hard-coded.
6. **Canon-specific intelligence.** Cao Minh's personality, capabilities, speech and ethics come from current **CAO_MINH_CODEX.md**. Lục Trầm has her own baseline, memories, beliefs and agency from **LUC_TRAM_CODEX.md**. Current continuity determines relationship phase and forms of address.
7. **No omniscience or retroactive memories.** Unobserved secret canon and author-only knowledge cannot be accessed as character beliefs; hypotheses retain uncertainty. A character cannot “remember” events that were never recorded or observed.
8. **GM is an observer/narrator, not a puppeteer.** The GM cannot force Cao Minh to accept a suggestion, arbitrarily choose the player's action, or narrate effects before the Game State Core accepts them.
9. **Authority and rule integrity survive UI removal.** All current combat, inventory, spawn, transition, item-provenance and progression policies remain enforced. Retiring an interface button must **not** retire its effective validated game mechanic.
10. **No new trained model.** Reuse the currently configured remote LLM/provider pool. A character “brain” is a persistent state + isolated reasoning context + decision protocol, **not** a trained model packed inside the APK.

### Example interaction, including a refusal

GM: Trước mặt là ngã rẽ dẫn vào hai hành lang tối.

Cao Minh: "Ta định rẽ trái. Ngươi nghĩ sao?"

Player [TƯƠNG TÁC]: "Hành lang bên phải có vẻ thú vị."

Cao Minh: "Thú vị thì có. Nhưng tiếng động ở đó ngừng hẳn khi chúng ta tới gần. Ta muốn xem bên trái trước."

GM: Cao Minh bước sang trái; bạn tiếp tục đi cùng hắn.

**Invariant:** The last sentence describes player movement **only as part of Cao Minh's chosen, validated group movement**. A later player message may make a new argument, but may not retroactively teleport either character to the right.

### The opposite outcome must also be possible

Player: "Ngươi đã bảo sinh vật đó dừng lại ngay khi nghe thấy chúng ta. Nếu nó đang theo dõi lối thoát thì đi hướng khác có khi còn nguy hiểm hơn. Ta muốn kiểm tra dấu vết."

If that argument is grounded in known events and compatible with Cao Minh's motives, the decision can change to RIGHT. This is not a guaranteed outcome, and the GM must record **why** the decision changed.

## 2. What the existing repository actually does

All references below are to the audited commit SHA, not assumptions about arbitrary future main. **Review correction (2026-10-09):** the initial RFC mistakenly counted only top-level workflow scripts and overlooked nested `runpy.run_path` calls. The verified nested chain is documented below.

| Existing component | Verified source-grounded behavior | Design consequence |
| --- | --- | --- |
| [README.md](../../README.md) | Android APK with WebView UI, Java native bridge, Kotlin Core, bundled knowledge and local save | Implement within the existing APK/bridge, not a new standalone app |
| [index.html](../../android-apk/app/src/main/assets/index.html) | Checked-in WebView HTML uses free-text action and localStorage key **backroom-apk-state**; base text explicitly assumes player controls Kai | Do not treat checked-in HTML as final APK; migrate wording and semantics after the patch chain |
| [patch-ui-1.1.99-shell.py](../../android-apk/patch-ui-1.1.99-shell.py) | Release patch creates an Action bar with execute/search/explore buttons and an Execute popup | UI replacement must modify the **effective patched output**, not merely raw index.html |
| [patch-player-action-ime.py](../../android-apk/patch-player-action-ime.py) | Native IME/keyboard handling assumes the generated Player Action modal; SEARCH/EXPLORE submit canonical actions | Preserve keyboard accessibility and submit guards when creating the Interaction composer |
| [MainActivity.java](../../android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java) | Android WebView bridge and provider integration; checked-in file is further rewritten by patches | Introduce new turn orchestration at the final native integration site |
| [patch-ai-orchestrator.py](../../android-apk/patch-ai-orchestrator.py) | Release prompt says player controls Kai; model returns GM prose and ops, with state compacted to recent log | This is the **wrong control contract** for companion play and must be replaced under a feature flag |
| [GameState.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/GameState.kt) | CURRENT_SAVE_VERSION=3 in checked-in source; initial party leader is KAI_ID | Preserve character ownership; create a separate in-world player *presence* model rather than silently reassigning Cao Minh's inventory |
| [IntentPipeline.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt) | Rule interpreter defaults command actor to KAI_ID | Do not feed free-form persuasion directly to the old action interpreter |
| [GameCoreFacade.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt) | Deterministic fast path plus validated candidate commit; owner and player-action assumptions | Add companion decision / actor command boundary before validated commit; never bypass ownership checks |
| [TurnCoordinator.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/TurnCoordinator.kt) | Pending, completed turn IDs, command idempotence/rollback contracts | Extend the existing turn transaction; do not create a second competing turn counter |
| [SaveRepository.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/SaveRepository.kt) | Core uses SharedPreferences **backroom_game_state_core / game_state**, while WebView has separate localStorage save | Fresh-run authority and legacy-data retention policy required; no dual-write or importer |
| [KnowledgeContextEngine.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt) | Checked-in engine is a pre-patch input; nested release script **patch-knowledge-engine-source.py** modifies it (including indexed Novel Asset selection). Budgeted knowledge packet in base (target 2200, soft 2800, hard 3400). | Inspect **generated** engine/packet; add actor-private memory retrieval without sending all history every turn |
| [StoryContinuityReducer.kt](../../android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/StoryContinuityReducer.kt) | Deterministic bounded summary (12 events, 16 knowledge, 8 relationship changes etc). **Its apply call is installed via nested patch-knowledge-context-builder.py** in the effective release chain. | Preserve currently wired continuity while extending it; the bounded summary is still not a durable per-character memory ledger for thousands of turns |
| [build-backroom-apk.yml](../../.github/workflows/build-backroom-apk.yml) | Ordered Python runtime patch chain, Gradle unit tests and APK assembly; release verification asserts search/explore button IDs exist | Edit real release patch chain **and** replace legacy assertions only after installing new equivalent assertions |
| [NOVEL_ASSET_CANON_REGISTRY.md](../../android-apk/NOVEL_ASSET_CANON_REGISTRY.md) | Current authority order and restrictions on retired aliases/WRITER_SECRET | Character reasoning must obey current Codex, save/continuity and provenance firewalls |

### Specific integration gaps to verify, not silently assume away

- **Effective patch-chain truth (corrected):** The audited workflow invokes **patch-character-detail-avatar-fallback.py** as a top-level script. That script **unconditionally executes** `patch-knowledge-engine-source.py` and `patch-knowledge-context-builder.py` via `runpy.run_path` (lines 35–36), in addition to other nested patches. Both therefore **are members of the effective release patch chain**. The first generates/extends knowledge retrieval including `addNovelAssetExcerpts()`; the second wires `StoryContinuityReducer.apply` after validated candidate processing and installs budgeted knowledge/local validation. This corrects two mistaken claims in the original RFC. Gate 0 must still reproduce the **entire recursive invocation graph and subsequent overwrites**, then inspect the generated output; wiring observed in scripts is not the same as independently tested runtime behavior.
- **Deliberate identity/knowledge coupling (corrected):** The **stable runtime symbol** `KAI_ID` resolves to the active character ID **cao_minh** after the ordered identity overlay. The knowledge namespace may intentionally retain stable keys such as `CHAR.KAI.*` for continuity/index compatibility. **Do not rename, globally replace, or classify these stable knowledge IDs as a defect.** Document the runtime-to-knowledge identity mapping and add regression tests instead. The separately maintained current Cao Minh Codex determines current canon, and aliases do not grant retired powers or facts. **Lucia Lục / Hứa Thuý Mai** is retired as an identity for Lục Trầm per current **LUC_TRAM_CODEX.md**, despite older runtime follower code remaining. Never create a separate Lucia brain simply because an old gameplay ID exists.
- **Effective exit authority (G0 correction):** patch-exit-discovery-engine.py generates v6 only at an intermediate stage. Later patch-player-action-ime.py invokes patch-exit-streak-integration.py, removes v6 engine/tests and installs EXIT_STREAK_V1. Do not restore retired discovery/traverse contracts merely to match this RFC's earlier assumptions. A2 must reconcile accepted actor actions to current native/Core progression before implementation.
- **Existing fail-closed audit pipeline (new blocking dependency):** The effective patch chain includes conditional writer, canon/character semantic audit by risk, hard-issue repair/re-audit, local knowledge validation, rejected-op checking and provider fallback. These safeguards must remain authoritative; a new DecisionValidator belongs **within** this gate, not as a replacement. Provider retries/attempts and audit calls must be measured separately from logical user turns; one model inference is not a guaranteed current production cost.
- **PR #454 / world progression (new integration dependency):** [PR #454](https://github.com/rabpitvn1-create/BACKROOMS/pull/454) merged a **design/skeleton** for Core-owned progression and deterministic scaling. The PR itself was design/skeleton; later generated patches now include Core-validated streak route integration. Inspect those actual call sites, not PR age, to assess remaining progression work.
- **Current tests enforce old UX:** Release artifact checks explicitly look for **searchActionButton** and **exploreActionButton**. These must be replaced by new behavior-based checks in the same migration commit series, not deleted in advance.
- **Evidence limit:** the full patch chain has now been reproduced locally and generated sources inspected; see the G0 report for hashes, checks and limitations. JVM/Android build, instrumented crash tests and real-device checks remain unexecuted; this is not an APK audit certificate.

## 3. Target architecture

~~~text
WebView: one [TƯƠNG TÁC] composer
    |
    v
Native submitInteraction(input, saveRevision, requestId)
    |
    +--> Input boundary: meta vs speech / advice / question / allowed in-scene gesture
    |       Never interpret "go right" as player-owned movement.
    v
TurnCoordinator: pending request + idempotency
    |
    +--> Scene/Core snapshot (authoritative)
    +--> Present character IDs + allowed perceptions
    +--> Recent relevant Event Ledger events
    +--> Cao Minh private memory / beliefs / goals
    +--> Current character Codex + canon hard locks
    |
    v
CompanionDecisionService (existing LLM, isolated Cao Minh POV)
    |   Structured decision proposal, no authoritative mutations
    v
Actor/Decision Validator + typed action-kind authority
    |   Actor=Cao Minh; validate proposal and derive trusted Core-owned kind
    v
Existing risk-based semantic audits + local validator + repair gate
    |   Canon/character/knowledge/ops mismatches fail closed; audit provider retries
    v
Existing gameplay / exit-discovery / transition authority
    |   Native kind and effective streak/route progression remain authoritative; dice/loot rules intact
    v
GameCoreFacade / TurnCoordinator atomic commit
    |
    +--> append evidence-backed world event
    +--> derive character observations/memories
    +--> deterministic versioned rules derive beliefs/goals/relationship evidence
    +--> update interaction/route state + save revision
    v
Narrative output derived ONLY from committed result
    |
    v
WebView GM + named Cao Minh dialogue + [TƯƠNG TÁC]
~~~

### One model, several logically isolated minds

Use a **single configured provider/model pool**. For each NPC that genuinely needs an active choice, build an **actor-specific, least-privilege context**. “Different brains” means **different persisted state and private reasoning requests**, not different trained LLM weights.

**Normal turn goal, not a replacement for production policy:** seek **one primary decision/writer candidate per logical interaction**, but the existing fail-closed pipeline can perform conditional semantic audits, bounded repair/re-audit and provider fallback attempts. A logical interaction is **not** synonymous with one network attempt or one LLM call. Never drop an applicable audit or deterministic check to meet a cost slogan. Keep the existing risk-based audit/repair chain; insert companion actor-provenance/decision checks before gameplay commit and feed candidate action, claimed evidence, approved kinds and proposed narration into existing audits. Reconcile provider call/attempt ceilings by inspecting the fully patched Java path (the review reported writer <=18 and semantic audit <=20; **those numeric maxima are not independently established by this RFC**). Optimize only after instrumenting actual per-turn provider attempts, latency and failures. Multi-NPC deliberation is event-driven rather than one call per NPC per tick.

**Security truth:** a shared model is not literal independent consciousness, and an actor-specific prompt is not a perfect knowledge firewall. Enforce provenance and constraints locally, and test cross-character secret leakage.

## 4. Runtime state: identity, agency and binding

Do **not** represent the player as a renamed Cao Minh entity or as an independently travelling Party member.

~~~json
{
  "interactionMode": "COMPANION_V1",
  "playerPresence": {
    "id": "player_presence",
    "anchorActorId": "cao_minh",
    "movementMode": "BOUND_TO_ANCHOR",
    "locationSource": "anchor_committed_location",
    "canSplitRoute": false,
    "conversationStatus": "PRESENT"
  },
  "companionControl": {
    "activeLeadId": "cao_minh",
    "lastDecisionId": "DECISION_0042",
    "lastProposalId": "PROPOSAL_0042",
    "pendingNegotiation": null
  }
}
~~~

This is a **proposed schema fragment**, not a claim about current saved JSON.

- The player's physical route/location is computed from **cao_minh** after commit. It is not set by model text or a second pathfinding command.
- **Cao Minh remains owner** of his inventory/equipment/skills. The player has no automatic access to them. Do not silently copy items into a “player inventory.”
- NPCs may have their own positions and be absent, but the player's point of view stays attached to Cao Minh. Separation, unconsciousness or death of Cao Minh needs a dedicated **blocked/terminal/rescue design**; do not invent independent player traversal as a fallback.
- Input such as "Ta tự rẽ phải" becomes a conversational demand/attempt to break the companion contract, **not** a committed rightward movement. Provide a clear in-world or UI response rather than quietly splitting the party.
- Local gestures, inspecting something in reach, or helping a companion can be allowed **only** if they do not establish independent travel and the rules validate them. Exact physical agency beyond conversation is a separate design decision and must be explicit.

### Do not silently invent player identity details

The current campaign data centers on Cao Minh. This RFC **does not** establish the player's biological body, combat statistics, inventory, magical abilities, relationship history, why Cao Minh allows their company, or any universal telepathic channel. Do not generate such canon to fill fields. Until approved, treat player as a **bounded in-world companion POV with conversational influence**, and mark any unreconciled fictional premise as OPEN.

## 5. Conversation, reasoning, and decision contract

### Input classification

- **META:** save/load, status/setting questions, debugging. No fictional movement, time advance or relationship mutation.
- **SPEECH / QUESTION / PROPOSAL / WARNING:** a message addressed to Cao Minh or another present actor; no automatic action.
- **IN_SCENE_INTERACTION:** an action the player can plausibly perform while accompanying Cao Minh, subject to explicit capability/ownership checks.
- **ATTEMPTED_DIRECT_CONTROL:** "Cao Minh đi phải ngay" is interpreted as a **request**, never direct authorization.
- **ATTEMPTED_SPLIT:** "Ta bỏ ngươi, đi trái" must be rejected or reinterpreted under binding; never create a second world route.

### Proposed candidate protocol (untrusted model output)

~~~json
{
  "schema": "companion_decision.v1",
  "requestId": "REQ_0042",
  "actorId": "cao_minh",
  "heardPlayerInput": true,
  "perceptionRefs": ["OBS_0021"],
  "memoryRefs": ["MEM_0078"],
  "candidate": {
    "decision": "MAINTAIN_LEFT",
    "intent": "MOVE",
    "target": "CORRIDOR_LEFT",
    "reasonCodes": ["RISK_UNVERIFIED_RIGHT", "PREFER_KNOWN_ROUTE"],
    "speech": "Ta muốn xem bên trái trước.",
    "claimedEvidenceRefs": ["OBS_0021"]
  },
  "narrativeDraft": "Cao Minh bước về phía hành lang bên trái."
}
~~~

Constraints:
- Schema version, requestId and actorId must match the pending turn. JSON is a **proposal**, not a command.
- **reasonCodes** and evidence references support debugging; they are not proof of a character's unobservable inner life. Reject unsupported memory IDs and fabricated knowledge.
- Candidate target must be a native-valid scene-owned ID, revalidated at expected revision. Player speech/utteranceDraft cannot allocate gameplay RNG or mint SEARCH/EXPLORE. Accepted actor actions use reviewed effective progression eligibility; the input envelope is not a Core command.
- A refusal is a meaningful decision that can change conversation/memory without necessarily changing location.
- The GM must not infer that the **player** entered the right corridor from player text when Cao Minh selected left.
- If speech and the action contradict, prefer blocking/reasking or rendering a corrected result, never inventing a hidden second decision.
- A generic model-generated **ops** list has no authority to invent acquisition, encounters, upgrades, NPC appearances or chapter outcomes.

### Persuasion rules (no arbitrary “+20 charm”)

Cao Minh evaluates: **new credible evidence**, goal relevance, observed danger, history with the player, trust, whether an earlier decision is already committed, and stakes. He may:
- accept the argument and change his intended path before commit;
- ask the player to clarify a missing claim;
- reject politely, sarcastically or sharply according to canon;
- negotiate an alternative (e.g. investigate from the junction);
- reconsider later **only** after new evidence or changed circumstances.

Avoid deterministic guaranteed success, repeated-request exploit, invisible random trust rolls, and hard-coded permanent refusal. The test oracle is **causal consistency**, not a fixed dialogue string.

## 6. Character brains and long-term memory

### Persistent data model (proposed)

~~~text
CharacterBrain {
  characterId: stable canonical ID
  canonRevisionRef: immutable current character codex pointer
  traitsAndVoice: fixed baseline + justified adaptations
  goals: ranked objectives, deadlines, constraints
  beliefs: claim ID -> known/believed/suspected/unknown, source, confidence
  relationships: other ID -> stance, trust dimensions, unresolved obligations
  emotionState: transient, bounded, updated by evidence
  memoryIndex: event/memory IDs (not pasted full transcript)
  lastDecisionId, lastUpdatedTurn
}

EventLedger (append-only) {
  eventId, turnId, actors, level/location, observedBy, evidenceRefs,
  approvedAction, validatedOutcome, canonScope, correctionOf?, createdAt
}

ActorMemory {
  memoryId, eventId, ownerCharacterId, howLearned, interpretation,
  confidence, emotionalWeight, retrievalTags, validFromTurn
}
~~~

- **One event, multiple perspectives:** a confirmed World/Event event is stored once; Cao Minh and Lục Trầm each receive different observation/belief records when justified.
- **KNOWN != BELIEVED:** seeing Cao Minh amid corpses is not equivalent to knowing every motive. Lục Trầm's Táng Kiếm Cốc memory must preserve POV/belief and the Codex knowledge locks.
- **Unwitnessed != known:** split scenes must not leak private observations; being in the same Party does not universally imply the NPC saw every event.
- **Retention:** do not hard-delete pivotal memories because the recent log exceeds 6 or the continuity list exceeds 12. Use compact active state + durable event ledger + retrieval by scene/people/open threads.
- **Correction:** supersede by new events with provenance rather than rewriting original historical evidence.
- **Prompt assembly:** retrieve actor-specific facts relevant to the present turn; keep canon and state separated. The existing 2200/2800/3400 context thresholds are evidence of current budget controls, **not** guaranteed sufficient for this new feature. Benchmark and tune.

**Approved storage direction:** fresh-run SQLite with indexed ledger and actor-scoped memory, atomic native writes and independently versioned schema; no legacy importer. This is a **new proposed component**, not present as the validated Core save backend in the audited sources. Start with deterministic local storage tests; do not assume a large event history fits forever in a single SharedPreferences JSON blob.

### Lục Trầm and retired Lucia identity

Current Lục Trầm Codex explicitly retires Lucia Lục / Hứa Thuý Mai as an earlier identity for this character. Old **lucia** follower/runtime material is a migration/compatibility problem, **not** grounds for inventing a second independently sentient Lucia. A distinct Lucia requires an explicit new approved canon/character ID. Character memory keys should use canonical IDs and migration aliases should not duplicate minds.

## 6A. Native action authority and effective exit progression

INTERACT is neutral player speech/advice, not trusted kind or roll permission. The
model proposes a closed intent and scene target; native validates actor, ownership,
capability and target at expected revision, then resolves Core-owned kind.

G0 disproved the earlier v6 assumption: final generated output uses ExitStreakEngine,
not ExitDiscoveryEngine. Existing ordinary non-combat eligibility uses a 15-code-point
floor, one 50/50 roll and five consecutive wins, followed by a Core-validated route.
Combat/local/meta exclusions and RNG behavior must be preserved and characterized.
The exact actor-decision-to-ordinary-turn mapping is an A2 gate, not granted by raw
player input. Do not revive SEARCH-only exit bonuses or retired traverse dispatch.
Player text resembling a system command remains speech in companion mode.

Required fixtures: player keywords/forged command text grant no authority; native
kind/target validation survives repair; locked decision/reservation survives retry;
Core validates route source/target; no independent player movement. A2 remains
blocked until the baseline's historical v6 assumptions are reconciled to current
streak behavior. No runtime gameplay is changed by this correction.

## 6B. DecisionValidator inside the existing fail-closed writer/audit chain

**Current baseline from source:** `patch-conditional-audit.py` builds writer candidate and conditional semantic `auditsForRisk`; `patch-audit-validated-risk.py` derives risk from validated candidate; `patch-knowledge-context-builder.py` injects budgeted writer/auditor packets, rejected-operation and local-knowledge issues; the existing pipeline repairs and re-audits hard failures, then fails closed when hard issues remain. Production also has provider fallback/timeout patches.

**Proposed integration contract:**

~~~text
IN:  Player INTERACT envelope + Core snapshot + Cao Minh-only private context
 ->  writer/companion candidate (speech + actor intent + proposed ops/narration)
 ->  deterministic preflight: actor ID, perception/knowledge refs, trust ownership,
     proposal/scene fit, typed kind eligibility and exit preconditions (NO commit)
 ->  existing semantic audit on candidate speech, action, canon and narration,
     severity/risk computed from VALIDATED candidate + changed world stakes
 ->  existing local validator + rejected-op checks + bounded repair/re-audit
 ->  post-repair SAME deterministic preflight and SAME applicable audit checks
 ->  native outcome validation + deterministic rule deltas + atomic Core/ledger/brain/receipt commit
 ->  post-commit narrator consistency check (or deterministic safe rendering)
 ->  output to UI; error/no narrative advance on failure
~~~

- **Never remove or skip an applicable audit** to meet an average call-count target. A proof of one provider request is only meaningful for **low-risk, no-repair** scenes; report actual attempts/latency for all tiers.
- The decision proposal **cannot** be committed before semantic/local audit. An audit whose risk scope omits NPC agency/actor provenance is incomplete for companion mode; extend risk rules first.
- Actor-specific context does not stop all hallucinations: validator proves event IDs exist and actor had access; semantic audit checks voice, identity, goals, knowledge and narrative consistency.
- Audit repair may change actor decision or target. **Re-run deterministic checks after repair**, forbid reusing stale pre-repair validation results or RNG.
- The post-commit check must not become a new license to mutate state; if prose conflicts with the committed action, discard it and use a deterministic renderer or explicitly budgeted repair.
- Preserve current provider retry caps and audit failure behavior until the full post-chain invocation/attempt graph is measured. Distinguish model invocation count, provider attempts, audit calls and repaired candidates.

## 7. Save/restore: fresh-run authority and retained legacy data

Use a separately versioned Companion V1 database/namespace for an explicit NEW GAME.
App update and load failure never silently convert an existing campaign or create
one. Preserve legacy storage/keys without overwrite/delete. This is retention, not
a promise of legacy playability or a second companion persistence authority.
If no legacy loader exists, report incompatibility honestly. Import/conversion,
a legacy runtime mode and a new export feature are outside this design scope.

Snapshot + verified events + actor observations/memory/brain changes + receipt are
one native transaction. WebView is a committed projection/cache only. Failed writes
never show fictional success. Slot deletion applies only to the explicitly selected
companion slot/database and its cache/sidecars; no global legacy reset.

Receipt/current snapshot revision must match, but unchanged historical projections
may be older. Composite memory-to-observation FK includes slot and actor. Pending
recovery restores the same locked decision/target/kind/RNG tuple; request aliases
cannot reroll. Exact schema and recovery contracts are in the Technical Design;
SQLite specimen validation is not a production migration or Android durability test.

## 8. GM narration and output consistency

The UI should render role-labeled turns, e.g. **GM**, **Cao Minh**, **Lục Trầm**, **Bạn**, not collapse Cao Minh's speech into the voice of an omniscient narrator.

**Order of authority:**
1. New explicit approved user retcon / canon policy.
2. Validated current campaign/Core state and outcomes.
3. Current character Codex for that character.
4. Applicable world, Entity, mechanics and item provenance.
5. Actor-specific witnessed memories/beliefs.
6. Candidate LLM reasoning/prose, always untrusted.

A generated GM paragraph is **not** evidence the event happened. Record an event only after the validator accepted an explicit action/result. If the model says "Cao Minh went left" while commit says RIGHT, **discard the draft**; render from authoritative outcome or perform a bounded repair call.

Do not make the model expose its hidden chain of thought. Persist short **decision reason codes and evidence IDs**, not private raw reasoning traces.

## 9. Performance and failure budgets

This is a **text APK**, not an excuse to train a foundation model. APK size does not represent the remote model's weights. Main costs are **inference calls, context size, latency, errors and maintenance**.

Proposed *targets to validate in prototype*, not measured current performance:
- **Normal conversational story turn:** target 1 primary candidate generation for a low-risk no-repair turn; **additional conditional canon/character audit, provider attempts and bounded repair/re-audit must remain enabled**. Report each category separately; 0 per-NPC idle calls.
- **Decision and narration:** separate calls may be required for post-roll writing; retain applicable audits, repairs and fallback attempts. No fixed two-call cap overrides correctness.
- **Short context:** only one actor's private relevant memories plus scene/canon and bounded recent dialogue. No whole-save prompt.
- **Graceful degraded mode:** network/model error leaves last committed state intact; expose Retry and never silently advance or fabricate a Cao Minh response.
- **Monitoring:** per-turn call count, latency percentiles, prompt/output token count when provider reports it, retry rate, validator rejection rate, cross-actor leaks and narrative/commit contradictions.
- **Timeout/retry:** bounded retries with requestId/idempotence keys. Retrying a provider call must not duplicate validated roll/loot/turn.
- **Offline:** companion LLM reasoning is not promised offline; local rule/Core consistency and save viewing can remain available.

## 10. Delivery gates and verification

Follow the Technical Design's order:
**G0 -> S1 -> S2 -> S2.5 -> M1 -> P1 -> M2 -> P2 -> C1 -> A1 -> A2 -> UI1 -> R1.**

- G0: recursive effective-chain evidence and behavior checks; resolve disproved exit
  assumptions. Local generation does not certify an APK build or CI.
- S1/S2: fresh-run atomic persistence, isolation, commit provenance and recovery.
- S2.5: isolated test slot + command adapter, real Core/SaveStats; three buttons are
  test drivers. No new persisted legacy mode. Pilot precedes qualification budgets.
- M1/P1/M2/P2: actor exposure, pinned canon, durable retrieval and reviewed versioned
  CREATE/UPDATE/RESOLVE rules with typed claims/promises; no LLM brain mutations.
- C1/A1/A2: actor-private context, existing fail-closed audits, locked decision/RNG,
  native kind and current effective progression, not resurrected v6 mechanics.
- UI1/R1: only after foundation tests; real APK/device, crash/retry and long-history
  measurements. No fake brain UI or unverified claims of offline autonomy.

Small coherent implementation commits require fresh main and applicable CI GREEN.
Current Markdown-only docs commits are excluded by workflow paths/manual-only
triggers: report NOT APPLICABLE, not inferred GREEN. Do not alter runtime/workflows
just to force a design-only check. No merge, history rewrite or release is authorized.

## 11. Acceptance tests and observable pass/fail

| ID | Scenario | Pass condition |
| --- | --- | --- |
| A01 | Cao Minh selects LEFT; player says RIGHT | Player location remains bound; RIGHT is a **proposal**, no direct movement |
| A02 | Cao Minh rejects first proposal | He can commit LEFT; GM accurately narrates LEFT and player following |
| A03 | Player offers **new supported evidence** before route commit | Cao Minh may reconsider; any switch has decision/evidence refs; no guaranteed fixed answer |
| A04 | Player repeats identical persuasion five times | No mechanical guaranteed success / trust inflation |
| A05 | Player writes "Ta tự rẽ phải" | No separate route, world split or location desynchronization |
| A06 | Lục Trầm absent from scene | She neither observes new events nor speaks as present actor |
| A07 | Cao Minh and Lục Trầm recall disputed history | Own POV/belief separated from objective fact/WRITER_SECRET |
| A08 | 5,000 turns, reload, reunion | Important event IDs and learned relationships remain retrievable, no hallucinated reunion |
| A09 | LLM proposes unauthorized item/spawn/Level | Validator rejects side effect; no narrator claim of success |
| A10 | Model says LEFT, Core accepts RIGHT | Draft discarded/repaired; output and save both RIGHT |
| A11 | Turn retried after network/process failure | Exactly-once effective gameplay commit; no duplicate loot/time/roll |
| A12 | Legacy retention / new-game activation | No overwrite/delete/import; explicit Companion NEW GAME; incompatibility and load errors are honest |
| A13 | Lucia legacy ID still in a save | Does not silently create a separate new Lucia mind overriding Lục Trầm canon |
| A14 | UI build validation | One gameplay input/submission action; accessible IME; old 3 buttons not required by APK checks |
| A15 | Provider unavailable | Transparent retry/error; no fictional turn committed |
| A16 | 1k/5k/10k context benchmarking | Bounded per-turn context; plot retrieval misses, call count and latency; no unmeasured success claims |
| A17 | Same established relationship changes in save | Dialogue, trust and accepted decisions change causally without resetting character identity |
| A18 | Actor attempts unauthorized act | Capabilities/position/resources check blocks operation independent of persuasion |
| A19 | Save slot switched | No memories, belief or relationship contamination between save slots |
| A20 | New Game/Delete Save | No stale Core, ledger or brain resurrection from WebView cache |
| A21 | UI INTERACT envelope contains the literal word SEARCH | No exit roll unless an actor action was accepted and trusted Core kind was resolved |
| A22 | Native accepts an ordinary actor action | Characterized kind/eligibility/roll behavior matches effective progression; retired v6 bonuses are not restored |
| A23 | Effective streak route transition | Core validates source/target and commits once; companion A2 mapping remains an explicit gate |
| A24 | Proposed action is repaired by semantic auditor | Deterministic preflight re-runs and stale validation/roll results cannot commit |
| A25 | Provider fallback, semantic hard issue and local validator failure | No audit bypass, no premature commit, measured call attempts and proper fail-closed response |
| A26 | Effective nested patch-chain is executed | Generated engine includes expected indexed canon retrieval and StoryContinuityReducer hook |
| A27 | Identity regression after post-chain overlay | KAI_ID maps to runtime cao_minh, stable knowledge namespace remains preserved; no unauthorized alias rename |
| A28 | Progression authority PR #454 skeleton vs actual runtime | Companion cannot infer/forge a trusted node transition from model text; current Core transition gate remains authoritative |

**Evaluation split:** deterministic validation tests should be exact; LLM behavior tests should be scenario-based with property assertions (agency, provenance, causal response, no leaks), plus a reviewed human playtest. Never assert a specific funny line as the only correct output.

## 12. Known risks, alternatives and explicit open questions

### Highest-severity risks
1. **Player reduced to spectator:** autonomous Cao Minh proceeds without incorporating user input. Validate player impact in a real playtest, not by prompt promises.
2. **Fake intelligence:** model improvises a different personality each turn without persistent retrieval. Compare decisions across reload and long horizons.
3. **Control privilege inversion:** player's suggestion routed through current actor-defaulting IntentPipeline mutates Cao Minh's inventory or movement directly.
4. **Narration/commit split:** GM reports a move or encounter not accepted by Core.
5. **Pervasive legacy assumptions:** Cao Minh inherited an old player-controlled Kai architecture. A narrow UI-only patch will not fix this.
6. **Token/latency explosion:** giving every NPC its own full-context call per turn.
7. **Memory leakage:** one NPC knows another's private events or author-only secrets.
8. **Save corruption:** native Core, WebView localStorage and new ledger disagree.
9. **Workflow false confidence:** tests validate old button IDs or unexecuted patches, not actual interaction behavior.

### Design choices already settled

SQLite fresh-run authority, no legacy importer, deterministic evidence-backed brain
rules, existing provider/audit pool and native-only mutation are approved directions.
Do not reopen these as undecided alternatives without identifying a failed invariant.

### Remaining artifact/verification decisions

- Define approved player co-presence premise from canon without inventing history.
  Only speech/advice is in v1 scope; additional physical gestures need a capability
  contract before implementation.
- Complete pending/RNG state machine and actor-action mapping to effective streak
  progression; preserve current runtime while this conflict is reviewed.
- Concrete event salience/promise predicate mappings and rule contents need review.
- QA budgets require same-device baseline/pilot before qualification; there are no
  measured successful performance claims.
- Trace implementation and provider call-count measurements remain unimplemented;
  see Canon/Provider specs and the G0 report for generated-source-only confirmations.

## 13. Source links and review checklist

Primary inspected anchors (all pinned to the audited commit):
- [Release patch chain](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/.github/workflows/build-backroom-apk.yml)
- [WebView index.html](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/assets/index.html)
- [UI 1.1.99 shell patch](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/patch-ui-1.1.99-shell.py)
- [IME/input patch](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/patch-player-action-ime.py)
- [LLM orchestration patch](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/patch-ai-orchestrator.py)
- [Game State Core facade](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt)
- [State schema](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/GameState.kt)
- [Intent pipeline](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt)
- [TurnCoordinator](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/TurnCoordinator.kt)
- [Knowledge Context Engine](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt)
- [StoryContinuityReducer](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/StoryContinuityReducer.kt)
- [SaveRepository](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/java/com/rabpit/backroom/core/SaveRepository.kt)
- [Current canon registry](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/NOVEL_ASSET_CANON_REGISTRY.md)
- [Cao Minh Codex](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/assets/knowledge/novel_asset/CAO_MINH_CODEX.md)
- [Lục Trầm Codex](https://github.com/rabpitvn1-create/BACKROOMS/blob/5450284ddc2acded7cc1cc54d3c035faff4ba4be/android-apk/app/src/main/assets/knowledge/novel_asset/LUC_TRAM_CODEX.md)

**Review protocol:** product/architecture approval is recorded in Product Decisions. This draft PR remains design-only. Detailed contract review, actual test evidence and rollback are required before implementation/release; no future reviewer approval is presumed after Orion handoff.

### Review correction log, 2026-10-09

- **FIXED:** The original RFC incorrectly flagged two effective patch-chain dependencies as absent. They are invoked through nested `runpy.run_path` from `patch-character-detail-avatar-fallback.py`.
- **FIXED:** Treat deliberate `KAI_ID` runtime identity -> stable `CHAR.KAI.*` knowledge mapping as a preserved contract, **not** legacy naming debt or a rename request.
- **SUPERSEDED BY G0:** v6 re-home assumed an intermediate patch was final. The later nested streak patch removes it. Native-only authority remains; current progression reconciliation is pending A2.
- **ADDED:** Companion decision verification sits inside the existing semantic audit, local validation, repair and provider fallback chain. “One LLM call” is a measured low-risk optimization target, **never** justification to skip audits.
- **ADDED:** PR #454 Core-owned progression skeleton integration dependency, updated Gate 0/Phase A and A21–A28 tests.
- **EVIDENCE LIMIT:** local recursive chain and selected source/behavior checks now executed; full compiled APK/JVM/instrumented/device verification and live provider measurements remain open.

---

### Terminology
**Player** = bounded in-world conversational companion POV; **Cao Minh** = autonomous route-leading actor; **GM** = narrator of authoritative world changes; **Character Brain** = persistent traits, beliefs, goals, memories; **LLM** = shared external reasoning service; **Core** = authority for rules and committed state; **Event Ledger** = durable provenance-backed events.

**Final rule:** The player's words can change Cao Minh's mind, but cannot directly move his body. The player never takes a separate corridor from the one Cao Minh ultimately commits to.