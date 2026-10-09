# Canon P0/P1 Observability — Characterization Spec V1

**Backrooms The Game | Design document | 2026-10-09**

| Field | Value |
|---|---|
| Status | **DESIGN ONLY — NOT IMPLEMENTED, NOT EXECUTED** |
| Version | 1.0.0 |
| Author | Orion (technical reviewer) |
| Review thread | BACKROOMS issue #476 |
| Design basis | Canon P0/P1 final spec locked in technical review with Eric, 2026-10-07/08 |
| Architecture approval | Architecture Baseline V1 per #476 decision record (delegated approval, 2026-10-09) |
| Scope | Canon characterization (P0) + shadow design (P1). Does NOT authorize implementation, behavior change, or merge |

## Verification-basis legend

Every behavioral claim in this document is tagged:

- **[SOURCE-TRACED]** — traced from patch scripts and the final generated patch composition on main
  (including nested `runpy` chains, e.g. `patch-character-detail-avatar-fallback.py` lines 35–36).
  Accurate as of the traced source, but not yet confirmed on reproduced generated output.
- **[PENDING-G0]** — requires Gate 0: reproduce the full recursive patch chain and inspect the
  effective generated Java/Kotlin/HTML before treating the claim as verified runtime behavior.
- **[LOCKED]** — a design decision locked with Eric; not a claim about current code.

Where a claim is marked [SOURCE-TRACED], Gate 0 must still confirm it on the generated chain
before implementation relies on it.

## Part A — Canon P0 final spec (observe-only characterization)

**Goal [LOCKED]:** a characterization harness for the FINAL GENERATED RUNTIME
(source tree + full patch chain). Observe only. Absolutely no change to selection,
budgeting, packet, prompt, audit, retry, or provider behavior.

### A.1 Test seam (proven approach with current test infrastructure)

**[LOCKED]** The repository has plain JUnit + org.json only; there is no Robolectric, and
`KnowledgeContextEngine.build()` requires an Android `Context` for assets. Therefore P0 uses a
**minimal test seam**, not instrumentation tests and not a second selector:

- Extract `load(context)` → `parseDatabase(raw: String): Database` (parsing logic moved verbatim).
- Add `@JvmStatic fun buildForTest(dbJson: String, stateJson: String, action: String, rollsJson: String): String`
  which reuses the **exact production `Builder`** (the same selection path, not a simulation).
- Production `build()` is not modified by a single line.

**[PENDING-G0]** Confirm on the generated chain that `parseDatabase` extraction preserves
byte-identical parsing (golden test: packet from `build()` vs `buildForTest()` with the same
shipped `knowledge_db.json` must be byte-identical).

### A.2 Trace originates from the real selection path

**[LOCKED]** Add `trace: MutableList<KnowledgeTraceEvent>? = null` to `Builder`
(default null → when null, zero behavior change). Instrument the four existing points:
`add()`, `expandReferences()`, `budgetedRecords()`, `hardClip()`. No new synchronization
(`Builder` is a per-call object).

**Parity invariant [LOCKED]:** the packet sent to the GM with trace enabled must be
byte-identical to the packet with trace disabled — asserted via SHA-256 on every fixture.

**Trace must never log Canon text or prompt text in production** — record IDs, digests,
decisions, and metrics only.

### A.3 KnowledgeTrace schema (final)

```json
{
  "fixture": "quiet_exploration_L0", "traceVersion": 1,
  "events": [
    {"seq":0,"type":"candidate_added","recordId":"CHAR.KAI.RUNTIME_CORE","reason":"mandatory hard context","priority":10,"estTokens":120},
    {"seq":1,"type":"candidate_reason_appended","recordId":"...","reason":"direct structured lookup"},
    {"seq":2,"type":"reference_followed","from":"LEVEL.01","to":"ENTITY.HOUND","targetPriority":58,"decision":"skipped","rule":"priority_gate_55"},
    {"seq":3,"type":"affordance_heuristic_fired","heuristic":"direct_threat","matchedKeywords":["hound"],"candidateIds":[...]},
    {"seq":4,"type":"budget_decision","recordId":"...","decision":"kept|dropped","tokensBefore":2100,"recordTokens":150,"ceiling":"target|soft","band":"mandatory|optional"},
    {"seq":5,"type":"hard_clip","occurred":true,"cutCharIndex":13300,"cutRecordId":"ENTITY.SMILER","cutField":"text|source_line"},
    {"seq":6,"type":"packet_finalized","sha256":"...","recordCount":17}
  ],
  "metrics": {
    "budgetEstimatedTokens": 2345,
    "serializedCharsBeforeClip": 11800,
    "serializedCharsAfterClip": 11800,
    "hardClipOccurred": false,
    "hardClipCutOffset": null, "hardClipCutRecordId": null, "hardClipCutField": null,
    "candidatesAdded": 22, "candidatesKept": 17, "candidatesDroppedByBudget": 5,
    "referencesFollowed": 8, "referencesSkipped": 13,
    "fanOut": {"direct":12,"reference":8,"affordance":5,"state":3},
    "selectionPressure": 0.23
  }
}
```

**Split budget metrics [LOCKED]:** do NOT collapse into a single `tokenEstimate`.
`budgetEstimatedTokens` (the `ceil(len/4)+8` estimator that `budgetedRecords()` decides on)
and `serializedCharsBeforeClip`/`serializedCharsAfterClip` (what `hardClip()` measures,
threshold `tokens*4` chars) are two different measures — the trace records both so any
mismatch is visible. **[SOURCE-TRACED]** for the two-measure fact; **[PENDING-G0]** for exact
threshold wiring on generated output.

**Multiple reasons [LOCKED]:** `add(id, reason)` keeps `putIfAbsent` for the packet;
the trace additionally emits `candidate_reason_appended` for **every** `add()` call,
including when `putIfAbsent` does not change the stored value.

### A.4 Fixture and snapshot schema

- Fixture: `{name, state (full JSON as submitTurn receives), action, rolls (locked)}`.
- Golden snapshot: `{fixture, packetSha256, recordIds[], budgets:{2200,2800,3400}, metrics{...}}`
  plus the full packet text stored separately.

### A.5 Corpus: 20–30 scenarios

**[LOCKED]** Coverage: quiet exploration L0/L5; iris/syvial present (alone/together); absent-character
queries; REL/ADDR + WRITING.DIALOGUE; direct entity lookup ("smiler"); direct item lookup
("almond water"); accented/unaccented names; runtime-event encounters; references on both sides
of the priority-55 gate (followed vs skipped); soft ceiling; hard ceiling; hardClip; separation
true/false; devil trigger; omnivault scan; long log; medical/food affordance.

**Identity coupling observation [LOCKED]:** include a scenario where Cao Minh's action triggers
`CHAR.KAI.*` records. Record the **runtime identity ↔ stable knowledge identity coupling**
(runtime player ID `cao_minh` via `KAI_ID`; knowledge namespace intentionally `CHAR.KAI.*`)
as an observed/documented coupling. **Do not propose renaming these IDs in P0/P1.**

### A.6 Acceptance

- [ ] Runs on the final generated runtime via the test seam; 20–30 scenarios with goldens.
- [ ] Trace ON/OFF parity: packet SHA-256 identical on 100% of the corpus.
- [ ] Every record in the packet has ≥1 reason event; multi-cause fully captured.
- [ ] Reference followed/skipped recorded with the exact rule; hardClip recorded with cut
      offset/record/field.
- [ ] All thresholds taken from constants in code; no invented numbers.

## Part B — Canon P1 final spec (shadow mode, no cutover)

**[LOCKED]** All P1 work is shadow-only. Nothing below changes selection, packet, or runtime
behavior. No cutover without P0 evidence reviewed and approved.

1. **Reference-edge classification (shadow):** record every followed/skipped edge with its rule.
   Do not change the priority-55 gate. Never infer `references = requires`.
2. **Alias index (shadow):** run alongside the current tag lookup; report parity
   (what aliasing adds/misses vs tags). Do not remove tags until parity is proven.
3. **Derived tier:** a proposed mapping table derived from priority semantics + P0 observations.
   Proposal only; never applied.
4. **Schema/lint (warn-only, CI):** duplicate IDs, dangling references, out-of-range priorities,
   ENTITY/ITEM records missing tags, empty text. Warn only; never block the build.
5. **Compiler round-trip:**
   - **DATA PARITY:** same records and runtime-significant fields
     (`id, domain, kind, text, authority, mutability, priority, tags, references, affordances, source`).
     Generated JSON may differ in non-semantic formatting/whitespace.
   - **RUNTIME PARITY:** run the P0 corpus on the compiled DB → **final packet byte-for-byte
     identical. No trailing-whitespace normalization or any other normalization on the packet.
     [LOCKED]**

## Appendix — Smallest first vertical slice (proven runnable with current test infra)

**[LOCKED]** The slice that proves the harness approach before expanding to the full corpus:

1. **Seam (~15 lines)** in `KnowledgeContextEngine.kt`: extract `parseDatabase(raw)`;
   add `buildForTest` overload reusing the exact production `Builder`. `build()` untouched.
2. **Minimal TraceSink (~30 lines):** `trace` param on `Builder` (default null);
   instrument `add()`, `expandReferences()`, `budgetedRecords()`.
3. **Test `KnowledgeContextEngineP0Test.kt` (plain JUnit):** copy the real `knowledge_db.json`
   into `src/test/resources`; 3 fixtures (`quiet_exploration_L0`, `direct_entity_lookup` for
   Smiler, `reference_above_gate` for LEVEL.01→ENTITY.HOUND@58); assert
   (a) packet SHA-256 with trace ON == OFF,
   (b) trace contains multi-reason `candidate_reason_appended`,
   (c) the reference skip records `rule: priority_gate_55`.
4. **Run:** `./gradlew :app:testDebugUnitTest --tests "*KnowledgeContextEngineP0Test*"`.
   Uses only JUnit + org.json (already in the test infrastructure); no new dependencies.

Pass criteria: the Gradle test is green and one sample KnowledgeTrace JSON is produced and
manually inspected. Only then expand to 20–30 scenarios + goldens + Provider P0.

## Explicitly forbidden (until separately approved)

**[LOCKED]** No GM rewrite or behavior change. No selection/budget/packet changes. No
provider-order, retry, threshold, parallelism, attempt-cap, pressure-mode, or fail-closed
changes. No LLM retrieval, vector DB, activation DSL, universal router, or Drive fetch at
runtime. No converting references into hard dependencies. No tag removal, Core-affordance
migration, record-boundary budgeting, or speculative abstraction. Drive remains the authoring
source of truth; the repo remains the reviewed release-pinned snapshot; the APK runs only the
built snapshot.
