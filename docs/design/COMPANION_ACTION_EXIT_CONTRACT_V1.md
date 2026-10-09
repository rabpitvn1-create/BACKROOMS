# Companion Action / Effective Exit Contract V1

Date: 2026-10-09. Owner: Ponytail under the owner's continuing implementation
instruction. Status: DECIDED CONTRACT; runtime adapter NOT IMPLEMENTED.
Parent #476; design gate #477; design PR #475.

Baseline: main `5450284ddc2acded7cc1cc54d3c035faff4ba4be` and the
[G0 effective-source evidence](G0_EFFECTIVE_CHAIN_VERIFICATION_V1.md).
This resolves the disproved ExitDiscovery v6 assumption. It adds no canon,
SEARCH bonus, direct traverse command, independent player route or second RNG.

## Closed actor proposal

`companion_decision.v1` has one intent from the table below, actor `cao_minh`,
scene-bound target references and an optional positive duration. Response disposition
is separate: ACCEPT, REJECT, QUESTION, NEGOTIATE or DEFER describes the conversation;
it never authorizes a command. Rejecting advice can still produce an ordinary TALK.
Unknown intent/disposition, unknown actor, inaccessible target, unsupported capability,
stale scene/evidence revision and invalid duration fail before reservation.

Player INTERACT remains untrusted conversation. Eligibility measures its exact
persisted input after the same `String.trim()`/Unicode code-point rule used by
ExitStreakEngine, not the model's utterance or a padded native template. Ordinary
non-combat input below 15 code points is rejected before the decision provider.
Do not synthesize filler to turn a short input into a valid turn.

## Mapping and effects

| Intent | Native kind/path | Target and time contract | Exit evaluation |
| --- | --- | --- | --- |
| MOVE | EXPLORE | Present reachable scene location; native movement capability required. Does not choose a Level/featured-stop transition. Core movement default 10 minutes, validated explicit duration overrides. | One bound-2 draw for an accepted ordinary non-combat turn. |
| SEARCH | SEARCH, NORMAL depth | Current accessible search scope; use existing coverage rules. Core search default 5 minutes, validated explicit duration overrides. | Same ordinary rule; no bonus. |
| INSPECT | SEARCH, QUICK depth | Present inspectable object/scope; no pickup permission. Core inspect default 5 minutes, validated explicit duration overrides. | Same ordinary rule. |
| TALK | EXECUTE | Present interlocutor or self; audited speech does not create a world fact merely by assertion. Core talk default 1 minute, validated explicit duration overrides. | Same ordinary rule, including refusal/question/negotiation spoken in scene. |
| WAIT | EXECUTE | Current scene; waiting does not imply sleep. Core wait default 30 minutes, validated explicit duration overrides. | Same ordinary rule. |
| USE_ITEM | Existing typed item-command path | Inventory ownership, quantity and capability checked by existing Core item validators. Time/cost comes from that command, not a guessed new number. Text-only item commands remain blocked. | Local typed item operation: none. |
| COMBAT_ACTION | Existing CombatChoice/Combat93 path | Only active combat and a current legal choice. Existing costs, turn and dice policies apply. | None; preserve prior streak. |
| NONE | No gameplay path | Only technical no-action/invalid proposal; no invented dialogue, time, receipt of gameplay completion or brain mutation. A spoken refusal uses TALK instead. | None. |

Duration and command input are derived natively from the validated actor proposal.
Use existing TimeCostPolicy/ActionRuntime defaults through canonical native verbs;
never feed raw player or model prose to time/rest/command interpreters. Explicit
duration must be representable by the existing Core policy; overflow/nonpositive
values reject. Model wording such as "sleep", "pickup", "traverse_exit", "search"
or "explore" cannot alter typed intent, target, cost or sleep effects.

UI meta actions and local informational queries have separate native entrypoints.
They are not actor intents and cannot be selected by a model or inferred from raw
INTERACT text. They consume no gameplay time/streak RNG and do not fabricate a
character observation. TALK/WAIT are ordinary gameplay, not an audit exemption.

Time/physiology/action-session changes are staged on a copy of Core@N. All accepted
ordinary effects, including the completion of the action session, commit in the
same SaveStats batch. Do not reuse current facade methods that eagerly save
beginAction/checkpoints against the legacy repository in this new atomic path.
These existing policies are reused; their persistence wrapper must be adapted.

## Exit and route authority

Preserve ExitStreakEngine's exact 0=win / 1=loss, bound=2, five wins, loss reset and
invalid-prior-streak handling. A fifth win authorizes route evaluation, not an
arbitrary model target. Core verifies current source Level/featured stop and its
canonical outbound target. If no outbound route exists, reset streak as current
integration does; never persist impossible 5/5 progress.

Capture the full ordered native roll/result tape before any outcome-bearing provider
response. Include source Level/stop, native route result, target Level/stop and whether
completion happened. A completed transition follows current behavior: suppress the
ordinary destination loot/encounter roll generation on that turn. Otherwise invoke
the same existing gameplay-roll policies in the same order. No extra streak roll
for the decision call, repair, provider fallback, alias, reload or UI callback.

Locked kind/target/duration, input digest, evidence revision and route source are
immutable once reservation begins. Outcome repair may replace prose/proposed ops;
it cannot change the action to use already reserved dice for another decision.
Mismatch fails closed and leaves the recoverable pending turn intact. See
[Pending Recovery V1](SAVESTATS_PENDING_RECOVERY_CONTRACT_V1.md).

## Required fixtures and evidence boundary

| Fixture | Expected outcome |
| --- | --- |
| Four wins then win; loss after four wins | One authorized transition; respectively streak reset on loss. |
| Five wins at a source with no outbound route | No transition; reset, no impossible 5/5 persisted. |
| TALK/WAIT with 14 versus 15 code points, including surrogate pairs | Short rejects without RNG; valid ordinary input evaluates exactly once. |
| Active combat, typed item/local query, UI meta | Zero exit draws; existing combat/item validation still required. |
| Player/model text contains `traverse_exit`, `search`, `sleep`, arbitrary Level | No typed authority, sleep effect or route granted from text. |
| Forged source/target, stale scene, inaccessible actor/item | Core rejects; no committed route or unauthorized effects. |
| Provider/audit failure, changed decision on repair, crash/alias/reload | Core@N unchanged; same reservation, no independent retry draw. |
| Accepted ordinary turn | Existing time/physiology policies and one exit evaluation; one atomic receipt, then UI. |

These are contract fixtures, not claims of adapter tests passing. Existing
ExitStreakEngineTest characterizes engine behavior, but does not test the new actor
adapter. New integration tests must call production native resolution and Core;
no test-only resolver, no bypass of applicable semantic/local audit.

Rollback: revert this contract commit before adoption. After runtime adoption,
policy changes require a new version and corresponding Core/receipt tests.
