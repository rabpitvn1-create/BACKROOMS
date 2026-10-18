# Level 0 V2 — foundation milestone

Scope: phase 1 and the first runnable portion of phase 2. This is **not** completion of the six-phase redesign, puzzle content, or a validated ten-hour experience.

## Sources and authority

Inspected PR #654 at `d31af6e9693aa643c90c349f64fc208753e890f9`, branch `feat/653-mini-map-explorer`, Draft. Read `MUST-READ-ME.md` before implementation.

- Confirmed environment Canon: `LEVEL_0_EIGHT_ZONES_CANON.md`, `LEVEL_0_TWENTY_NINE_SUBZONES_CANON.md`, `Level0Environments.kt` and the three packaged knowledge databases. Eight parent environments and 29 child environments all belong to exact node `0`. The author approved their use in a persistent two-layer world in the implementation request of 18 October 2026.
- Drive read: `/Novel/Asset/02_Level/BACKROOMS_WORLD.md` (modified 28 September 2026) and `/Novel/wolrd/wolrd.md` (modified 22 September 2026). These establish environmental/knowledge boundaries and the existing exact-node route; they do not specify the new eight-zone topology. Drive was not modified.
- Supplied Seth document returned connector `404 NOT_FOUND`. Its contents have **not** been verified.
- The world graph is an explicitly designed gameplay layout, **not a new Canon geographical claim**. Numbered Canon references are not traversal order.
- No evidence establishes a specific Level 0 exit room. Registry currently authorizes `0 -> 0.1`, not `0 -> 1`. No exit readiness, ending, level transition, or Fracture exit is added here.

## Actual runtime trace before this change

| Boundary | Runtime contract and dependency |
| --- | --- |
| New run / save | `GameCoreFacade.newStoryRun` initializes `CoreClientProjection`, then scene progress. `GameStateCodec` serializes `world`, metadata, actors, inventories, Party, turn and combat state. `SaveRepository` commits synchronously. |
| Old scenario generation | `MainActivity.prepareScene` calls `sceneGenerationRequest`; `ScenarioGenerator` calls bounded Gemini lanes and Gehihi fallback. `MiniMapScenario` validates schema 5, rooms, predicates, mechanisms and proof. |
| Old navigation | `MiniMapRuntime.actions/stage` uses one frozen script and an eight-entry Level 0 outline. `next` discards the script, accepted/attempted evidence, room visits and events. This cannot support persistent cross-area exploration. |
| Transaction / GM | `beginSceneAction` validates exact command fields, action ID, version and location; stages progress, starts `ActionRuntime`, reserves spawn results, persists `scene.pending`. `MainActivity.sceneWriterPrompt` sends public event and Canon to Gehihi. `commitSceneAction` commits time, staged progress, spawn and narration receipt atomically. Retry reuses pending; GM prose does not supply state deltas. |
| Player Action / UI | `explorer-controls.js` shows MAP, Area Log, communication and free text. Android planner currently maps free text to at most 12 current MOVE/SEARCH targets and rechecks each against Core. This remains a phase-4 limitation, not the intended final action model. |
| Canon / snapshot | `KnowledgeContextEngine` reads three APK JSON databases, selects exact `scene.zoneId/subzoneId`; it does not fetch Drive during play. Snapshot resolver uses exact `scene.environment.id` and fixed local assets. |
| RPG / encounter | `SpawnMechanism.rollSceneSpawns` only rolls eligible MOVE/SEARCH, reserves combat/NPCs through Core. `CombatRuntime`, Poker Dice, `EntityExpRewards`, `OfflineEntityLoot`, Party and inventories use the same GameState. |
| Level routes | `LevelRouteRegistry` owns exact Level/sublevel transitions. Its acyclic mandatory route is separate from reversible navigation **inside** Level 0. |

Previously packaged environmental identities were active in generated rooms and snapshot retrieval, but there was no persistent world layout connecting all 37 environments. An indexed Canon entry is not a runtime puzzle or event. The old temporary-inference function also offered automatic next-SEARCH hints; the new world projection does not call it.

RPG balance inspected at the source HEAD: total Entity gate `3500/10000` per eligible physical scene turn; NPC first-meeting table separate. Normal Entity HP is scaled after base +30 then multiplied by 2; Diệp Minh keeps base 2999 and its special profile. Normal defeat reward is 2 times rank EXP (4 at Level 0); Diệp Minh is 24 times (48 at Level 0). Reward marker is per Entity instance, loot uses persistent encounter identity/seed, escape has its own progress. These profiles, loot tables, escape rules, NPC rates and Cao Minh abilities are not changed in this milestone.

## Implemented end-to-end slice

`WorldGraphRuntime` is the shared exploration engine; `WorldDefinition` supplies content/layout policy, `WorldDefinitions` registers supported nodes, and `Level0WorldDefinition` supplies only the Level 0 catalog adapter/generator/validation. The runtime owns a versioned graph inside `scene.progress.worldGraph`. This reuses the proven pending transaction rather than introducing a parallel persistence or narration engine.

- Persist decimal-string 64-bit seed, exact node ID, definition version, actual adjacency, stable opaque gameplay-room IDs, Canon environment and parent-zone IDs. Stored graph is loaded as saved, never regenerated from a provider response.
- Initial allocation: one gameplay room per Canon environment, 37 total. The schema separates room identity from environment identity; additional rooms per environment and conditional edges are later work.
- Generation uses a seeded permutation of a cyclic zone backbone, a local cycle for each zone's child rooms, and cross-zone chords. Before acceptance, validate environment membership, unique IDs, bidirectional edges, full reachability and reachability after **every single edge removal**. Every area has alternative approaches and return routes. This proof covers the ungated foundation only, not future conditional puzzle edges.
- New production runs at exact node `0` use this world immediately. No outline/script provider request is needed; generation request returns `ready`, public `generationNeeded=false`, and scenario replacement is rejected.
- Movement changes exact room within Level 0, increments scene version and preserves discoveries, observations, markers and Area Log. There is no NEXT and no level transition.
- Observation records the current environment's approved sensory description once. It is an observation, not a verified Entity sighting, explanation of an anomaly or newly established puzzle rule. Repeating it neither duplicates evidence nor invents new information.
- Personal map markers persist and are rendered with a diamond on MAP. These are **map annotations**, not physical chalk or wall damage; no imaginary inventory item is consumed.
- MAP shows only visited rooms and immediate visible frontier. Frontier names/environments and edges between unvisited rooms are withheld. Room IDs do not contain environment names. Seed/private graph do not enter planner or GM packets.
- Ordinary MOVE keeps the existing physical encounter gate and Canon first-meeting rates. Passive observation and personal map annotation use **zero spawn rolls**, avoiding 35% encounter spam while reading or annotating; regression asserts zero RNG calls. Full environmental/action risk tuning remains phase 5.
- Core-confirmed non-Party NPCs bind to the room of their first world encounter; leaving the room removes TALK, public presence, active GM memory and recruitment eligibility. Party members remain available under existing presence rules. No authored NPC is created by the world generator.
- Staged events carry destination `zoneId/subzoneId`; Android Canon retrieval now uses the destination instead of stale pre-MOVE scene data. GM prompt follows the environment Canon, including Fracture architecture, without treating it as a transition.
- Save schema is now **9**, manual slot `manual_checkpoint_v9`; no migration/old-save adapter. Codec checks graph/position/visited markers/observations and rejects a pending graph replacement. Autosave contains pending progress and already reserved encounters; manual checkpoints retain their existing no-active-action restriction.

## Validation and limits

Regression targets: `Level0WorldExplorationTest`, existing `MiniMapRuntimeTest`, `GameStateCodecTest`, `GameCheckpointCodecTest`, `EntityCombatExpTest`, `EntitySpawnPoolTest`, `CharacterMemoryBagsTest`; Node `explorer-controls.test.cjs`, `level-snapshot-routing.test.cjs`.

The new tests exercise 128 seeded layouts, single-edge-removal reachability, an independent BFS traversal visiting every location and returning, a deterministic random explorer, backtracking and record survival, exact environment projection, private/frontier data boundaries, forged/stale commands, pending save/reload/retry, RNG idempotency, repeated commits, passive-action RNG exclusion, encounter blocking, NPC locality, manual checkpoint and provider-independent production initialization. Random exploration cannot unlock an unimplemented exit. These are architectural checks; they do not establish puzzle difficulty or play duration.

Validation results are recorded in the milestone commit/PR report. The local JVM harness uses the project's Kotlin 2.3.0 compiler and JUnit 4.13.2 with real org.json; it is not an Android Gradle build or emulator. Node DOM tests are not Android WebView acceptance.

## Outstanding work / next dependencies

1. Finish phase 2: richer room allocations, conditional/one-way/hidden passage model, persistent environmental changes and shortcut discovery, UI review at full map size. Do not call all visible chords “discovered shortcuts”; discovery/gate state is not implemented yet.
2. Phase 3: implement at least two independently verified cross-zone law families, immutable parameters, observation/action/consequence transitions, proof, deliberate wrong experiments, solver and random-guess tests. No puzzle has been invented merely from a room name here.
3. Phase 4: replace limited MOVE/SEARCH planning with typed Core affordances (inspect/compare/manipulate/ability/return/TALK) and progressive hints only on request. Current passive observation/mark targets are an initial adapter, not the final creative-action system. Plan and narration still have provider latency.
4. Phase 5: per-environment/action risk model with explicit balance tests, local NPC lifecycle beyond first binding, gameplay roles for all 29 child areas. Existing combat/loot integration needs scenario tests for death, defeat, retreat and rewards while experimenting, not just encounter reservation.
5. Exit decision: determine the exact authored access and verification rule for registry destination `0.1`. Keep transition locked until the author confirms the location/phenomenon and acceptable alternate approaches; never assign it to Fracture by default.
6. Remove remaining old mini-map scenario pipeline/UI assumptions after new puzzle/action contracts replace their uses. Legacy source remains available for its existing tests; there is no old-save compatibility path or production toggle offered to the player.
7. Build APK and inspect Android WebView plus live providers at the integration gate. Measure API tokens/latency, real-device save interruption and human exploration/puzzle duration. Ten-hour balancing is **unverified**.

Reproduce targeted checks in an Android SDK/Gradle environment:

```sh
node --test android-apk/tests/explorer-controls.test.cjs android-apk/tests/level-snapshot-routing.test.cjs
cd android-apk
gradle :app:testDebugUnitTest --tests 'com.rabpit.backroom.core.WorldGraphRuntimeTest' --tests 'com.rabpit.backroom.core.Level0WorldExplorationTest' --tests 'com.rabpit.backroom.core.MiniMapRuntimeTest' --tests 'com.rabpit.backroom.core.GameStateCodecTest' --tests 'com.rabpit.backroom.core.GameCheckpointCodecTest' --tests 'com.rabpit.backroom.core.EntityCombatExpTest' --tests 'com.rabpit.backroom.core.EntitySpawnPoolTest' --tests 'com.rabpit.backroom.core.CharacterMemoryBagsTest' --no-daemon
```

### Verified milestone results — 18 October 2026

- Kotlin 2.3.0 compilation of Core production sources and new world tests: PASS. Java Core compilation using ECJ 3.39.0 targeting Java 17: PASS. Existing unrelated `CombatRuntime.kt:511` unnecessary-`!!` compiler warning remains.
- JUnit: **104 tests PASS**, zero failures, across the seven target classes above. Eleven new world tests include 128 seeded graph layouts and corruption/retry/locality checks. JUnit run reported 13.106 seconds; this is test execution time, not gameplay or API latency.
- Node: **34 tests PASS**, zero failures, in the two target files above, including the new marker UI test and all 37 snapshot identities.
- `git diff --check`: PASS.
- The host has no Gradle/Android SDK or javac executable. A partial-source incremental compiler attempt was rejected because of missing Java class outputs and Kotlin cross-module smart casts; no repository workaround was applied. Rebuilt the complete Core Kotlin source module with Java sources, compiled Java outputs with ECJ, then executed the above JUnit targets successfully.
- APK build, Android WebView/device playtest, live Gemini/Gehihi, CI for this new commit, human puzzle difficulty and ten-hour pacing: **not verified in this local milestone**.


## Architecture correction — shared world engine

The first milestone coupled reusable exploration to Level 0. That coupling has been removed rather than replicated for future Levels:

- `WorldGraphRuntime.kt`: shared initialization, graph acceptance, actions, observation/markers, exploration history, MAP, public events, save validation and node-scoped NPC presence. No Level 0 environment, room prefix, fixed room count, reference-number parsing or generator is referenced here.
- `WorldDefinition.kt`: small content contract and immutable supported-definition registry. Adding a supported Level requires a definition/registration, not another movement/save runtime. Registration does not add an edge to `LevelRouteRegistry` or enable a transition.
- `Level0WorldDefinition.kt`: existing seed algorithm, Canon catalog adapter, objective, scene zone/subzone tags, exact 37-location constraint and Level 0 room-ID/entry policy. This is a content definition, not an independent runtime.
- Generic validation allows multiple gameplay rooms to share an environment and makes single-edge-removal redundancy a definition policy. Current shared edge implementation remains bidirectional/ungated; conditional/one-way mechanics remain explicitly outstanding.
- Graph version 2 binds `nodeId` and `definitionVersion`; save schema 9 and checkpoint slot v9 reject earlier schemas without migration. NPC location keys are `world.nodeId` + `world.roomId`, so identical room IDs in different Levels cannot imply presence.
- Core initialization chooses a registered definition without a Level 0 branch. Codec, projections, Party recruitment and GM memory refer only to the shared runtime. Android staged Canon context uses the actual event node.
- `WorldGraphRuntimeTest` supplies an unregistered, test-only definition at node 1 with three differently named rooms, two environments and a tree layout. The same runtime explores, backtracks, observes, marks and round-trips its state payload. Tests also cover policy-specific redundancy, unknown environment, disconnection, node/version/seed mismatch, NPC node isolation and refusal to load an unregistered definition in production. This fixture is not Level 1 content or Canon.

The 104-JUnit result above belongs to the first milestone. Refactor validation is recorded separately in the follow-up commit/PR; do not treat the earlier result as validation of new code.


Refactor verification: Core Kotlin 2.3.0 and Java 17 compilation PASS; **108 JUnit tests PASS** across the eight targeted classes (including four new generic-world tests); **34 Node tests PASS** for Explorer and snapshot routing; diff check PASS. JUnit execution reported 8.789 seconds. No APK/device/live-provider acceptance is claimed. The existing unrelated CombatRuntime unnecessary-`!!` warning remains.


## Phase 2 — conditional graph follow-up (implementation commit)

- Graph schema 3, definition version 2, save version 10, checkpoint slot v10; old save formats are intentionally unsupported.
- Level 0 generator uses seeded, opaque room IDs and 45 physical rooms across exactly 37 Canon environment identities (two rooms per parent zone, one per subzone). All 45 rooms remain reachable through ordinary bidirectional corridors even if any one corridor fails.
- Optional concealed passages are frozen in the persisted graph: Wetfloor ↔ Hollow Ceiling and a one-way Loop → Afterimage approach. Each passage has an independently persisted discovery/unlock state and authored condition identifier; these are gameplay shortcuts, **not** new Canon or exits.
- Public map/planner show neither the secret endpoint nor unavailable movement. Inspection discovers a structural seam but does not unlock it. A verified gameplay rule fact will be needed to expose a Core-validated opening interaction. At this checkpoint rule facts have not yet been awarded by a puzzle.
- Target tests cover hidden passage discovery, forged traversal rejection, condition opening and save/load; outcomes require CI verification against this commit.


## Follow-up integration checkpoint — provider, UI and current save

- Current save schema 11, manual slot `manual_checkpoint_v11`; previous saves are deliberately unsupported. World graph version 3 and Level 0 definition version 3 stay frozen with 45 rooms.
- Free-text intent planning now uses Gemini's bounded JSON response (max 850 output tokens per invocation), **not Gehihi**. A proposed step is never trusted: the current Core list, room, actionId, version and target still authorize it immediately before staging. Gehihi remains solely the storyteller of confirmed public events.
- Physical traces and personal map annotations have distinct symbols on MAP. Repeat physical marking is idempotent, repeated incorrect verification counts are bounded without dropping existing evidence.
- Offline, provider-independent seeded puzzle solver and a public-action random walker are regression aids, not a human difficulty or 10-hour timing study. APK CI proves compilation/build only, not WebView layout or live-provider behavior.
