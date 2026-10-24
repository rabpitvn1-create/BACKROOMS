# Random Level / Exit Streak 3 — PR #670

## Behavior and authority

Only `LevelTravelEngine` can choose the initial node or commit a different node.
`EXIT_REQUIRED_STREAK = 3`, success/failure are exactly 50%/50%, with no follower bonus.
The old `canTransition`, `exitProbe`/`levelExit` rolls, GM `set_level` operation and
`confirmedExit`/`transitionReady`/`exitReady` permissions have been removed from runtime.
Ordinary actions consume no exit RNG and preserve streak. Successful exit attempts
1/3 and 2/3 stay in place; failure resets to zero; 3/3 selects one different
configured destination and commits arrival, location, streak reset, receipt and
continuity together. No next-turn transition is queued.

The UI provides **Thử thoát Level**. Core also accepts a small complete-action grammar
(e.g. `thử thoát`, `thử thoát khỏi level hiện tại`, `đi qua lối thoát`, `exit level`).
Searching for an exit, mentioning one, hypothetical/negated requests, dialogue,
rest, combat and ordinary movement do not become exit attempts. Active combat
blocks a Level-exit attempt; escaping combat is a separate operation.

Explicit attempts are resolved and narrated locally before local interpreters,
ActionRuntime creation, normal event rolls or any GM request. This keeps GM retries,
provider failure and duplicate AI output out of the exit decision. Ordinary GM turns
may describe validated local movement, but cannot change node, title or streak.

## Data policy and limits

`knowledge/level_travel_policy.json` is gameplay configuration, **not newly asserted lore**.
It references the packaged CR2 node IDs and Cloudinary visuals. Entry IDs are stable
arrival anchors; generic “điểm vào” labels do not invent a room, maze or safe zone.

- Starting pool: main Levels **0–10**, chosen once for a fresh run.
- Destination pool: main Levels **0–10** plus all **29 standalone sublevels/named
  AREA nodes** in the packaged registry, including Red Rooms, Dullness, Level ε,
  Manila Room, LS-2 and the named areas of later Levels. All **40 nodes** may be
  entered and independently exited with the same 50% / streak 3/3 transaction.
  The current ID and targets forbidden by inbound hard locks are excluded.
- This explicit author approval supplies the gameplay entry/exit policy even when
  a special area's Canon does not previously define an independent exit. It does
  not promote `INFERRED`/`UNKNOWN` lore or invent environmental rules.
- Canon hard lock: **0.5 → 0.7 only**, and **0.5 may be entered only from 0.2**.
  Level 0.2 is now enabled, so 0.5 is reachable through its permitted source.
  Its exit still requires streak 3/3 and uses a one-element destination pool.
- New Game remains limited to main Levels **0–10**. Main Levels **11–13** themselves
  are still excluded; named sublevels/areas under them are approved destinations
  using their exact node cards, keeping missing lore `UNKNOWN`.
- Nested **ZONE/SUB** image nodes remain local scene subdivisions rather than
  independently randomized destinations. Registry parent/child relationships do
  not create travel edges or bypass streak 3/3.
- Unknown IDs, empty configured pools and conflicting hard constraints fail before
  exit RNG and preserve state. No automatic parent return is used.

Descriptive examples of gradual main-Level transitions in the old context are not
exclusive destination locks. The task authorizes the explicit gameplay random pool.
The actual exclusive 0.5 lock remains enforced. No old GM permission can bypass 3/3.

## Persistence and retry

Travel data lives in the current `GameState.world.levelTravel` JSON, alongside the
current `levelJson`, location, background and continuity flags. A receipt is pinned
to `(runId, turnId, exact input)`. Duplicate or concurrent callbacks reuse it; a
conflicting input or stale turn is rejected. The RNG is injected for tests and uses
private `SecureRandom` in production; no WebView RNG or seed override is exposed.

One SharedPreferences snapshot commits the exit. A failed save does not emit success.
Startup resumes the committed Core, even if the process died before the first browser
autosave or before delivery of an exit result. A delayed old callback cannot rewind
subsequent UI turns. Explicit current-format save/load keeps the same travel state.
No save migration, old-format adapter or old-save fallback was added.

Core snapshots, the receipt ledger and historical travel events are removed from
GM prompt state. CR2 selects the exact current node and its applicable current-Level
facts; sublevels do not inherit the parent's environmental description or other node
cards. Travel scenes use CR2 even when the older global rollout flag is off; missing
required facts or CR2 errors stop the narration request safely.

## Changed files

- `core/LevelTravelEngine.kt`: policy validation, start selection, streak, RNG,
  idempotency, arrival transaction and spatial authority.
- `core/GameCoreFacade.kt`, `SaveRepository.kt`, `StateReducer.kt`: startup/resume,
  native hooks, durable projection and GM mutation guards.
- `MainActivity.java`: Core-first bridge; removed old approval/RNG path; normal,
  local and combat reconciliation; scoped prompt data.
- `core/canon/{SceneSnapshot,SceneResolver,CanonResolver,ContextCompiler,CanonRuntime2}.kt`:
  stable node identity, exact scene selection and safe CR2 failure behavior.
- `assets/knowledge/level_travel_policy.json`, `assets/level-travel.js`, `assets/index.html`:
  configured pools, stable backgrounds, streak and explicit attempt UI.
- `patch-level-travel.py`, `patch-item-single-state-finalize.py`, CI workflow:
  repeatable final authority layer after generated patches, with drift checks.
- Tests: `LevelTravelEngineTest.kt`, `tests/level-travel-ui.test.cjs`; existing
  `CombatFeedback1193aTest.kt` injects the new engine dependency.
- `core/Engines.kt`: fixed a pre-existing Kotlin expression-body/early-return
  compile error in `PartyEngine`; party behavior is unchanged.

## Verification

Focused JVM tests compile the real Core and exercise new game, 50% boundary,
success/success/failure, three consecutive successes, self exclusion, hard locks,
sublevel and special-area fixtures, duplicates/concurrency, process-death recovery,
storage failure, 100 ordinary turns, local movement, save/load and actual CR2 packs.
Existing combat feedback and codec tests run alongside them. UI lifecycle tests
exercise startup, render, save/load, reopen, reset and the attempt button.

```sh
gradle :app:testDebugUnitTest --tests '*LevelTravelEngineTest' \
  --tests '*CombatFeedback1193aTest' --tests '*GameStateCodecTest'
node tests/level-travel-ui.test.cjs
python3 patch-level-travel.py
```

Local result: **39 focused JUnit tests passed**, the UI lifecycle test passed, and all
140 configured IDs/Canon references/Cloudinary URLs match the packaged registries.
Special-area regression additionally checks shipped entry/exit pools, failed-combo
reset, save/load, duplicate attempts and exact Canon packets for all 40 enabled nodes.
Fresh generation from the pre-feature PR snapshot matches the checked-in runtime;
reapplying the finalizer makes no changes.

Local verification uses the real Kotlin/JVM sources, JUnit and `org.json`; the Android
API jar supplies compile-time signatures and BuildConfig is stubbed without secrets.
The Java bridge is also compiled against SDK 35 API signatures. This is not an APK
build or a device/WebView instrumentation test. The existing Android CI remains the
APK packaging gate; no merge is performed by this change.
