# Parallel review contribution for #509–#516

Owner requested parallel work on 2026-10-09. The other GPT owns #500–#508;
this contribution stays on `fix/companion-decision-review`. No merge, release,
legacy save conversion or production activation is included.

## Implemented changes

- Private packets snapshot nested collections, serialize through one versioned
  canonical representation, and hash the entire payload. Voice references are
  explicitly style-only; memories/beliefs remain subjective with provenance.
  Whole-packet UTF-8 byte budgeting removes selected memories only, never history.
- Decision binding includes exact native input digest, pending turn, packet digest,
  source snapshot digest and scene. The authoritative protocol requires a native
  tuple verifier before provider dispatch and durable reservation before exposing
  an outcome. Hash equality alone is not authority.
- Typed semantic/canon audit verdicts distinguish PASS, REPAIRABLE and HARD. One
  repair is shared across local/audit failures, with native revalidation and full
  re-audit. Legacy string-audit failures remain fail-closed. Reported HTTP attempts
  remain distinct from logical proposal calls; unavailable measurements stay null.
- Specimen executors reject mismatched locked intent/target/item, turn and scene;
  targeted SEARCH requires target presence/reach. TALK bounds UTF-8 speech.
- `NativeItemCombatStage` applies original StateReducer item semantics and typed
  Combat93 operations. Its reservation envelope captures all original scoped RNG
  bounds/values, including resolution/proc/reward/next-actor draws, and validates
  exact deterministic replay without a new RNG producer.
- Debug-only non-exported preview Activity/session provides explicit lifecycle and
  pending status. It requires a qualified factory; there is no v2 fallback. Slot
  generation guards stale callbacks; receipt durability is read from the native DB.

## Validation status

NOT RUN. The owner requested one consolidated final test, after integration.
New regression fixtures are source specifications until that run completes.
No green CI, native runtime qualification or issue closure is claimed here.
Final audit scope is 500–1,000 actual runs, with Android API24/API35,
generated-source parity, rollback, crash/reopen, retry and isolation retained.

## Integration dependencies and limits

The current production-native batch is WAIT30. TALK has no positive native
communication/physical exposure producer; SEARCH/INSPECT cannot invent findings.
The new item/combat stage is detached: it still needs an audited typed operation
lock, ordinary item time/exit capture, durable reservation and atomic publication
through the one authoritative store transaction. It is not a receipt.

No production decision provider, semantic/canon audit, native context verifier or
decision ledger/reservation adapter is supplied by the existing pure protocol.
The generated legacy provider/audit methods are private and use the GM path;
calling them as actor-private context would violate isolation. Their transport
pool/fallback must be reused through a dedicated audited runtime adapter.

Required upstream #500–#508 contract: fresh native seed/persona/brain creation;
verified reads of actor-owned records at one prior revision; source/pin validation
on reopen; pending tuple + snapshot digest read inside the writer; one atomic
publication boundary. Native verifier and durable lock must revalidate there,
not merely before provider calls. Debug factory installation awaits this contract.

#516 also requires staged Android interaction evidence and human voice/agency
playtest. Helper tests and a disabled debug surface cannot replace those criteria.
Keep #509–#516 open until their individual acceptance gates are actually met.
