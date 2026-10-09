# Provider P0 Observability — Spec V1

**Backrooms The Game | Design document | 2026-10-09**

| Field | Value |
|---|---|
| Status | **DESIGN ONLY — NOT IMPLEMENTED, NOT EXECUTED** |
| Version | 1.0.0 |
| Author | Orion (technical reviewer) |
| Review thread | BACKROOMS issue #476 |
| Design basis | Provider P0 final spec locked in technical review with Eric, 2026-10-07/08 |
| Architecture approval | Architecture Baseline V1 per #476 decision record (delegated approval, 2026-10-09) |
| Scope | Observe-only provider instrumentation. Does NOT authorize implementation, behavior change, or merge |

## Verification-basis legend

- **[SOURCE-TRACED]** — traced from patch scripts and the final generated patch composition on main
  (including nested `runpy` chains). Accurate as of the traced source, not yet confirmed on
  reproduced generated output.
- **[PENDING-G0]** — requires Gate 0: reproduce the full recursive patch chain and inspect the
  effective generated Java/Kotlin before treating the claim as verified runtime behavior.
- **[LOCKED]** — a design decision locked with Eric; not a claim about current code.

## 1. Principle: observe only

**[LOCKED]** No change to provider order, retry policy, thresholds, parallelism, attempt caps,
pressure modes, or fail-closed semantics. No change to audit or validation behavior.
Instrumentation must not alter the number, order, timing, or outcome of any provider call.

## 2. Corrected execution order (replaces earlier draft)

**[SOURCE-TRACED]** (from `patch-knowledge-context-builder.py` `submitTurn` composition):

1. Writer candidate is built (`generateText`).
2. `validatedTurnRisk()` derives risk from the validated candidate.
3. At risk ≥ 4, semantic `auditsForRisk()` runs **before** the deterministic validators.
4. `hardAuditIssues(audits)` is computed from the semantic audits.
5. `rejectedOperationIssuesAndroid()` and `KnowledgeLocalValidator` append their issues.
6. At most one repair attempt; failing audits are re-run after repair.

**Critical consequence [SOURCE-TRACED]:** if the semantic auditor throws (429/503/network
exception), `submitTurn` aborts **before** the two deterministic validators execute.
At risk < 4 (no semantic audit), the deterministic validators run directly.
The ProviderTrace must record this exact order via `semanticAuditRequired/Completed` and
`deterministicValidationCompleted` — never imply the validators always run.

## 3. Corrected attempt caps

**[SOURCE-TRACED]** (from `patch-gemini-model-matrix-final.py`, `patch-gemini-health-pool.py`,
`patch-provider-deadline-final.py`):

- **Writer** `generateText()`: Gehihi 1 + Gemini matrix ≤15 (3 models × 5 keys, 120s budget)
  + Haku 1 + SOL 1 = **≤18 attempts per call**.
- **Auditor** `geminiAuditText()`: models {Lite, 3.5}, 60s budget, Gemini-only (no provider fallback).
  - Phase 0 (writer's key excluded): **4 keys × 2 models = 8 attempts**.
  - Phase 1 (excluded key only): **1 key × 2 models = 2 attempts**.
  - **Maximum 10 attempts per audit.** (If the writer did not use Gemini, phase 0 alone tries
    all 5 keys × 2 models = 10 with no phase 1 — still ≤10.)
- **Repair:** at most 1.
- **Worst case** (high-risk turn + repair): 18 (writer) + 20 (2 audits) + 18 (repair writer)
  + 20 (re-audit) = **≈76 HTTP attempts**, before deadline/circuit-breaker cuts it short.

**[PENDING-G0]** Confirm attempt caps, the phase-0 exclusion wiring, and the fail-closed abort
path on the effective generated code — the figures above are source-traced, not G0-verified.

## 4. ProviderTrace schema (minimum fields)

```json
{
  "turnId": "TURN_0042",
  "callId": "CALL_0042_W1",
  "parentCallId": null,
  "role": "writer|audit|repair",
  "provider": "gemini|gehihi|haku|sol",
  "model": "gemini-2.0-flash-lite|...",
  "keyLabel": "K1|K2|K3|K4|K5",
  "bucketLabel": "writer|audit",
  "attempt": 3,
  "latencyMs": 1842,
  "httpStatus": 200,
  "failureClass": "none|http_401|http_403|http_429|http_5xx|timeout|network|empty_response|invalid_json|deadline_exceeded|no_healthy_worker",
  "inTokensEst": 2310,
  "outTokensEst": 480,
  "retryReason": "http_429|...|null",
  "packetDigest": "sha256(exact post-hardClip packet)",
  "promptDigest": "sha256(exact sent prompt)",
  "semanticAuditRequired": true,
  "semanticAuditCompleted": true,
  "deterministicValidationCompleted": true,
  "deterministicIssueCount": 0,
  "repairTriggered": false,
  "finalOutcome": "committed|repaired_committed|aborted_hard_issues|aborted_audit_unavailable|error"
}
```

**[LOCKED]** Field rules:
- `keyLabel` is a non-secret label (`K1`–`K5`); **never log key material**.
- `packetDigest` is the SHA-256 of the **exact post-hardClip packet**; `promptDigest` is the
  SHA-256 of the **exact sent prompt**. Digests only — **never log Canon text or prompt text
  in production**.
- `parentCallId` links repair attempts and re-audits to the original call.
- Every LLM call in a turn must produce events; a scan must confirm no prompt/Canon text leakage.

## 5. Subsystem boundaries

**[LOCKED]**
- The Knowledge subsystem and the Provider subsystem must not import each other.
- The orchestrator passes only an opaque `turnId` and the packet/prompt digests between them.
- Trace consumers (e.g. C1 actor-context budgeting in the companion architecture) read
  digests and counts — never raw text.

## 6. Acceptance

- [ ] Every LLM call in a turn has trace events with the fields above.
- [ ] A scan confirms zero prompt/Canon text in production traces.
- [ ] Behavior before/after instrumentation is identical (same outcomes on the same fixtures).
- [ ] Failure classes cover real 429/503/timeout/`no_healthy_worker` cases observed in practice.
- [ ] The recorded execution order matches §2 on the generated chain (**[PENDING-G0]**).

## Explicitly forbidden (until separately approved)

**[LOCKED]** No provider-order, retry, threshold, parallelism, attempt-cap, pressure-mode, or
fail-closed changes. No new model/provider onboarding via this spec. No bypassing the
semantic audit to meet a call-count target — a logical turn is not synonymous with one
network attempt. This spec exists to *measure* the current pipeline, not to shrink it.
