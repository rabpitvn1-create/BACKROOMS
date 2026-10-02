# Narration scene boundary

Core decides what happened. LUNA decides how it feels. No narration proposal is passed into gameplay resolution: `MainActivity.GameBridge.submitTurn` calls `GameCoreFacade.processRule`, then `completePreparedTurn(turnId, "{}")` before requesting prose.

| Role | Runtime symbols | Responsibility |
| --- | --- | --- |
| Core | `GameCoreFacade.processRule`, `completePreparedTurn`, `commitPresentation`; EntityCore and ItemCore | Deterministic rules, committed state/outcomes, persistence; presentation append is versioned and idempotent. |
| Milestone | `MilestoneCore.promptContext(state, action)` | Current-node direction, filtered for scene characters. `docs/milestone-v2.md` is the pinned complete source, retained backstage. |
| Scene compiler | `SceneContextCompiler.compile` | The single text-only narrator contract; selects dependencies and applies visibility before rendering. |
| LUNA | `MainActivity.narrationPrompt`, `generateNarrationText`, `generateText` | Prose, atmosphere, harmless details and dialogue within committed presence/continuity. LUNA remains first in the existing transport fallback chain. |
| Guard | `NarrationGuard.validate(generated, state, evidence)` | Reject hard authority violations. It does not require internal claim IDs, matching templates, dialogue quotas or prose style. |

## Packet

`GmNarrativePacket.buildScene(SceneContext)` renders exactly these immutable fields:

| Field | Selected input |
| --- | --- |
| LevelScene | Current LevelCore node, location, compact action-relevant environment palette, committed route facts. |
| CharacterScene | Cao Minh card; present/pending characters; known directly-mentioned characters marked mentioned-only. |
| StoryBoundary | Current Milestone node only, with absent-character guidance removed. |
| RelevantContinuity | NarrativeSkeleton summaries whose concrete dependencies match the scene or a directly mentioned known subject; relationship actors must match. |
| CommittedSceneFacts | Compact observable turn facts and condition/combat context, without event bookkeeping. |
| RecentContext | Up to four short recent entries; foreign-Level/absent-character prose and combat recaps excluded. |
| PlayerAction | Current input under the same visibility boundary. |

The runtime compiler calls `levelSceneContext`, `characterSceneContext` and `narrativeSceneContinuityContext` on the facade. Their selective views are inputs to compilation, not additional writer packets. No raw CanonRetriever result, full NarrativeSkeleton, full Epistemic/state object, EntityCore/ItemCore rules, scheduler, weights, eligibility or future milestone document is rendered. Broader legacy helpers can still serve backstage or compatibility callers; `MainActivity.narrationPrompt` does not call them.

## Validation and presentation

`NarrationProviderPolicy.present` makes one initial writer content attempt and permits at most one validation-guided repair. A guard rejection is passed back to the writer once so a fresh complete payload can correct the rejected condition; a second rejection falls back locally. Transport failure does not start another content attempt because provider transport failover is already independently bounded. Core-owned Entity lifecycle scenes bypass the writer entirely.

Hard checks cover authoritative JSON fields, locked canon names/revelations, uncommitted loot, combat closure, Level transitions, character/party mutations and explicit injury/stat mutations. They are deterministic checks for supported prose patterns, not a general natural-language proof system. A terminal combat outcome already owned by Core does not require a fresh model claim. Creative imagery and unused model claim bookkeeping do not trigger another content request.

After validation, `GmChoiceContract.gmEntry` shapes presentation; `commitPresentation` checks turn/version/base hash and appends once. Narrator output does not change inventory, party, relationships, mechanics or save authority.

### Rolling oracle narration cache

A successful writer response may carry six compact future presentation capsules aligned to the six deterministic `oracleWindow` steps. The cache is process-local and presentation-only: it is never written into Core state or save data. Before reuse, runtime requires the exact Core-routed action and `oracleAuthorityHash` of the newly committed state to match the forecast, then runs the normal `NarrationGuard` against current committed evidence. Any free-form divergence, stale hash, malformed capsule or guard rejection discards the cache. Character encounter steps deliberately force a fresh writer call so newly committed character voice/context is available; Core-owned Entity lifecycle presentation still bypasses the writer. This keeps the normal path light while allowing several default world advances to share one content request.

## Verification and measurements

`SceneContextCompilerTest` verifies unrelated state, Level, absent character, mystery/thread, interaction rules, future-node material and bookkeeping exclusion, plus continuity retrieval for a known mentioned character. `MilestoneCoreTest` checks every configured node and the source hash. Guard/provider tests cover creative Explore, fake loot, unfinished combat closure, fake transition and one initial attempt. `PresentationCoreTest` covers actual facade combat closure, duplicate/stale presentation and checkpoint save/load.

`levelZeroExploreRuntimePacketAndAttemptMetrics` compiles real Level/Milestone assets and reports prompt characters and median construction/validation time over 31 samples after warm-up. Its provider is explicitly injected: its timing is not live LUNA latency. A valid first payload still asserts one initial attempt, zero repairs and unchanged state. Device debug telemetry records the bounded repair separately in `promptCharsRepair` and `repairCount`; the repair count is capped at one content attempt. Prompt content and credentials are not logged.
