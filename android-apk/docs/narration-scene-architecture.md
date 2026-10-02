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

`NarrationProviderPolicy.present` makes one initial writer attempt, including committed special scenes. Only `AUTHORITY:` rejection permits one content repair. Invalid/missing output or transport failure uses the existing local fallback without an extra content repair. Transport fallback remains independently bounded by the existing provider policies.

Hard checks cover authoritative JSON fields, locked canon names/revelations, uncommitted loot, combat closure, Level transitions, character/party mutations and explicit injury/stat mutations. They are deterministic checks for supported prose patterns, not a general natural-language proof system. A terminal combat outcome already owned by Core does not require a fresh model claim. Creative imagery and unused model claim bookkeeping do not cause repair.

After validation, `GmChoiceContract.gmEntry` shapes presentation; `commitPresentation` checks turn/version/base hash and appends once. Narrator output does not change inventory, party, relationships, mechanics or save authority.

## Verification and measurements

`SceneContextCompilerTest` verifies unrelated state, Level, absent character, mystery/thread, interaction rules, future-node material and bookkeeping exclusion, plus continuity retrieval for a known mentioned character. `MilestoneCoreTest` checks every configured node and the source hash. Guard/provider tests cover creative Explore, fake loot, unfinished combat closure, fake transition and one initial attempt. `PresentationCoreTest` covers actual facade combat closure, duplicate/stale presentation and checkpoint save/load.

`levelZeroExploreRuntimePacketAndAttemptMetrics` compiles real Level/Milestone assets and reports prompt characters and median construction/validation time over 31 samples after warm-up. Its provider is explicitly injected: its timing is not live LUNA latency. It asserts one initial attempt, zero repairs and unchanged state. Device debug telemetry records `promptCharsInitial`, `promptCharsRepair`, `repairCount`, and existing core/prompt/provider/validation/repair/total times; prompt content and credentials are not logged.
