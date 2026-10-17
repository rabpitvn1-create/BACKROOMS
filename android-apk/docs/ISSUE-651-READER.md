# #651 — Interactive novel reader

## P0: effective runtime / authority

Inspected main `4bffdd09` (no AGENTS.md). Runtime is packaged `assets/index.html`,
`MainActivity.GameBridge`, `GameCoreFacade`, `ActionRuntime`/`TurnCoordinator`,
`SpawnMechanism`, `HiddenExitStreak`, `CombatRuntime`/`PokerDiceCore`,
`CoreClientProjection`, `GameStateCodec`, `SaveRepository` and `GameCheckpointCodec`.
The two GmNarrativeVoicePolicy classes serve different prompt sections; both had
player-input restrictions and both now allow ordinary automatic narration.

UI → `nextReaderPage` → `beginReaderPage` → durable Core plan/tickets → existing
`beginAction` → reserved Entity/deferred Canon NPC → writer + Canon/memory →
`commitReaderPage` → existing validated turn/time/spawn commit → atomic journal,
branch, continuity and ticket consumption → projected reading page.

Battle Start → existing encounter confirmation → Poker Dice roll/hold/reroll/finalize
→ existing Combat resolution/skills/Ultimate/Party/loot → durable closing receipt
→ next reader page. Reader controls never execute Dice or resolve Combat.

The old free-text Explorer bridge lane is removed. `submitAction` remains only
for the existing Poker Dice direct combat action. Menu/item/recruitment endpoints
retain their own Core validation.

## P1: plans, RNG, recovery

A page token is `PAGE:<Core view turn>`. A page is one Core-approved scene-facing
EXECUTE plan, with EXPLORE/INVESTIGATE/AVOID intents or a validated route traversal.
The prose itself and selecting/retrying an option do not add rolls.

Core keeps at most two ticket bundles: independent uniform Entity d10000, streak
d100, and independent d10000 NPC/ambient tickets. Existing SpawnMechanism/NpcSpawnTable evaluate their
actual eligibility against those tickets (no seeded PRNG approximation). Entity threshold remains 3500/10000;
streak remains three successive rolls below 50/100, with failure resetting to zero.
An Entity reservation makes that step ineligible for streak. NPC once-only,
reunion, Party/Level and ambient eligibility still go through existing code.

Forecasts are derived on demand, not stored future events. They are conditional on
the fixed current plan and the next EXPLORE plan, and stop at Entity/Canon NPC
boundaries. The next step can be foreshadowed on the current page. No future
Entity name/Combat result or premature Exit is projected to the UI. Branch,
Level/node, Party/profile and pending changes recompute eligibility from current
Core facts; unused ticket values survive instead of being discarded and rerolled.
An active plan locks mutation through existing action-session gates.

Provider failure/process death leaves the reservation and selected branch intact.
Retry re-enters the same action session; abortAction cannot discard an active reader
reservation. Completion consumes one bundle and writes gameplay, journal, branch
and replacement tickets in the same SaveRepository save. A duplicate completed
page returns the current projection; older stale tokens cannot advance gameplay.
Manual checkpoints use the existing current-version codec, including reader data;
loading a checkpoint deliberately rewinds the run. No old-save migration was added.

## P2: narration and choices

Provider normalization preserves only reply, decision and narrative continuity;
model state/ops do not get mutation authority. Choices contain display labels plus
whitelisted semantic intents, not arbitrary commands. They promise an intention,
not a clue/NPC/reward. Invalid options, switching a reserved option, malformed
choices, and choices during an Entity encounter are rejected before commit.

Core alone generates TRAVERSE options from unlocked LevelRouteRegistry edges.
Unlock does not traverse. An Entity can interrupt the chosen traversal; Battle
Start must still be resolved. Exact structured destinations avoid ambiguous
text matching on numbered routes. After a successful transition, the next Level
requires settings, inheriting prior selections and preserving memory.

Confirmed decisions and the complete reading journal persist. Recent pages and
prior decisions enter the prompt. Optional quoted narrative memories are stored
in the existing storyContinuity namespace, tagged NARRATIVE_ONLY and owned only
by Cao Minh. They cannot grant NPC knowledge or overwrite gameplay facts. A note
must quote the visible page. Invalid optional notes are omitted with a diagnostic
count instead of rejecting an otherwise valid page or trapping a selected branch.
Quote matching normalizes Unicode, whitespace and typographic quotes; uncertainty
words promote a grounded note to SUSPICION. Paraphrases are never stored as facts. Prompt retrieval
uses recent and relevant older notes; the Canon compact-state packet excludes
these narrative notes to avoid upgrading them into Core facts.

## P3: mobile reading surface

No textarea, player-action form or player chat rows. One Next Page button;
structured options are attached to their originating story page and lock Next
Page until selection. Reload of a pending page exposes Retry with the exact
stored request and locked options. Status/error/loading have recovery messaging.
Existing menu/status/item/Party, snapshots, Battle Start and Poker Dice remain.

## P4: proposed balance table (for review)

| Difficulty | Entity HP | Entity counterattack damage |
| --- | ---: | ---: |
| Normal | 100% | 100% |
| Hard | 125% | 125% |
| Cao Minh Must Die | 150% | 175% |

Applied in Core after existing Level scaling, including wave creation and decode.
Normal is an identity transformation. Special skills, equipment, hit/evade rules,
player input, loot, spawn and streak are unchanged. Damage scaling includes the
special percentage attack path; periodic status rules retain their Canon values.
No forced death or forced loss is introduced. These are explicit initial balance
proposals, not an assertion that extensive gameplay balance has been proven.
Narrative styles are a separate presentation policy: six checkboxes allow one or
more simultaneous choices. Core persists the selected set, validates it and blends
the styles in the narration prompt independently of difficulty. Settings are locked
inside a Level and never open during Combat.

## P5: evidence and remaining limits

Executed locally: JavaScript regression suite **144/144 PASS**; the semantic,
MadGod, Lucia overlay and Combat-layout source verifiers PASS; `assembleDebug`
PASS; packaged reader verification against the extracted debug APK PASS (no
retired composer assets). Headless Chromium at 412×915 with a mocked Android
bridge: no page errors, no horizontal overflow, inline choices lock Next Page.
The PR records the final targeted Kotlin/JUnit counts after completion.

The continuous reading area retains one ornate frame. Semantic emphasis rejects
pronouns/common words and prefers exact Core names over contextual guesses; cached
rendering annotations are recomputed with the corrected recognizer. Phone smoke
checks confirm three checked styles, the loaded frame asset and no overflow.

Targeted coverage:
thresholds, projected-vs-committed unlock/reset, Entity blocking streak, provider
failure/restart/replay, invalid and switched choices, branch persistence,
checkpoint restore, route transition/settings, Normal identity, difficulty HP,
provider output, mobile reader gates, Battle Start and existing Combat/Dice.

Live provider storytelling quality, physical Android/WebView and difficulty
playtesting require device/provider review; browser bridge mocks cannot establish
those properties. No merge, release, force-push or issue closure is part of this PR.
