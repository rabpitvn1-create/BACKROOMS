const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.join(__dirname, '..', 'app', 'src', 'main');
const html = fs.readFileSync(path.join(root, 'assets', 'index.html'), 'utf8');
const core = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/GameCoreFacade.java'), 'utf8');
const bridge = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');

test('Save and Load address an explicit Core checkpoint without automatic WebView saves', () => {
  const fragment = html.slice(html.indexOf('function save()'), html.indexOf('function freshCoreGame()'));
  const calls = [];
  const context = {
    state: {turn: 5}, busy: false, statusEl: {textContent: ''},
    window: {__combatBusy: false}, CURRENT_CHARACTER_CANON: {},
    Android: {
      saveCheckpoint: () => { calls.push('save'); return '{}'; },
      loadCheckpoint: () => { calls.push('load'); return JSON.stringify({turn: 2}); }
    },
    ensureCurrentLevel: x => x, render: () => calls.push('render')
  };
  context.window.Android = context.Android;
  vm.createContext(context);
  vm.runInContext(fragment, context);
  assert.deepEqual(calls, []);
  context.save();
  assert.deepEqual(calls, ['save']);
  context.load();
  assert.deepEqual(calls, ['save', 'load', 'render']);
  assert.equal(context.state.turn, 2);
  assert.doesNotMatch(fragment, /localStorage\.setItem/);
});

test('Core persists only explicit checkpoints and restores them on launch', () => {
  const persist = core.slice(core.indexOf('private void persist(JSONObject state)'),
    core.indexOf('private void projectBeforePersist(JSONObject state)'));
  assert.match(persist, /liveStateJson =/);
  assert.doesNotMatch(persist, /preferences\.edit\(/);
  assert.match(core, /putString\(MANUAL_SAVE_KEY, live\)\.remove\(STATE_KEY\)\.commit\(\)/);
  assert.match(core, /this\.liveStateJson = checkpoint != null && !checkpoint\.isEmpty\(\)/);
  for (const file of ['index.html', 'gm-choice-ui.js', 'inventory-ui.js', 'party-ui.js']) {
    const source = fs.readFileSync(path.join(root, 'assets', file), 'utf8');
    assert.doesNotMatch(source, /localStorage\.setItem\(['"]backroom-apk-state['"]/);
  }
});

test('preview shares turn resolution without persisting or retaining attempts', () => {
  const preview = core.slice(core.indexOf('public synchronized String previewTurn('),
    core.indexOf('public synchronized String currentStateHash()'));
  assert.match(preview, /prepareExplorerTurnData\(normalized, text\)/);
  assert.match(preview, /finishWorkingTurn\(normalized, prepared, new JSONObject\(\)\)/);
  assert.doesNotMatch(preview, /\bpersist\(|preparedTurns\.(?:put|clear)/);
  const batch = bridge.slice(bridge.indexOf('private JSONObject geminiBranchBatch('),
    bridge.indexOf('private boolean haikuConfigured()'));
  assert.equal((batch.match(/postJson\(/g) || []).length, 1);
  assert.doesNotMatch(batch, /generateText\(|haikuText\(|for\s*\(int attempt/);
});

test('Explorer prefetch bridge is a no-op and owns no speculative cache', () => {
  const prefetch = bridge.slice(bridge.indexOf('private void prefetchChoices('),
    bridge.indexOf('private String worldProposalPrompt(', bridge.indexOf('private void prefetchChoices(')));
  assert.match(prefetch, /Intentionally no-op/);
  assert.doesNotMatch(prefetch, /geminiBranchBatch\(|generateText\(|postJson\(|previewTurn\(/);
  assert.doesNotMatch(bridge, /prefetchIo|prefetchGeneration|prefetchCache|PrefetchBranch|PrefetchCache/);
});

test('bridge contains no retired shadow-planner orchestration', () => {
  assert.doesNotMatch(bridge, /GmShadowPlanner|shadowPlannerIo|shadowPlannerCache/);
  assert.doesNotMatch(bridge, /shadowPlannerPrompt\(|authoritativeGmProposal\(|scheduleShadowPlanner\(/);
});

test('Core facade exposes no retired shadow-planner adapter surface', () => {
  assert.doesNotMatch(core, /shadowPlannerContext\(|plannerCommitGate\(|validateShadowTransaction\(|shadowCommandRegistry\(/);
});

test('retired GM transaction commit gate is absent from runtime wiring', () => {
  const gradle = fs.readFileSync(path.join(__dirname, '..', 'app', 'build.gradle'), 'utf8');
  assert.doesNotMatch(bridge, /GM_TRANSACTION_COMMIT_ENABLED/);
  assert.doesNotMatch(core, /completePreparedTurnWithGmTransaction\(|gmTransactionCommitEnabled|gmCommandAuthority/);
  assert.doesNotMatch(gradle, /GM_TRANSACTION_COMMIT_ENABLED|featureFlag/);
});

test('player turn commits Core before bounded presentation and never schedules planner calls', () => {
  const turn = bridge.slice(bridge.indexOf('public void submitTurn('), bridge.indexOf('public void combatRoll('));
  assert.match(turn, /completePreparedTurn\(turnId, "\{\}"\)/);
  assert.match(turn, /NarrationProviderPolicy\.present\(safeEvents/);
  assert.match(turn, /generateNarrationText\(prompt, providerCalls/);
  assert.match(turn, /commitPresentation\(turnId/);
  assert.doesNotMatch(turn, /generateText\(|authoritativeGmProposal\(|scheduleShadowPlanner\(|completePreparedTurnWithGmTransaction\(/);
  assert.ok(turn.indexOf('completePreparedTurn(') < turn.indexOf('NarrationProviderPolicy.present('));
  const provider = bridge.slice(bridge.indexOf('private String generateNarrationText('),
    bridge.indexOf('private String geminiResponseText('));
  const compiler = fs.readFileSync(path.join(root,
    'java/com/rabpit/backroom/core/SceneContextCompiler.java'), 'utf8');
  assert.match(compiler, /SafePresentationView\.narrativeText/);
  assert.match(bridge, /SceneContextCompiler\.compile\(gameCore, milestoneCore, state, action, turnId\)/);
  assert.doesNotMatch(provider, /currentCoreState/);
  assert.match(provider, /calls\[retry \? 1 : 0\]\+\+/);
  assert.doesNotMatch(provider, /catch \(|geminiText\(|haikuText\(|haikuTextOnce\(|sleep|attempt/);
});

test('oracle narration cache reuses only Core-matching future capsules', () => {
  assert.match(bridge, /private JSONArray narrationFutureCache = new JSONArray\(\)/);
  assert.match(bridge, /pollNarrationFuture\(String action, JSONObject committedState\)/);
  assert.match(bridge, /GameCoreFacade\.oracleAuthorityHash\(committedState\)/);
  assert.match(bridge, /expectedAction\.equals\(actualAction\)/);
  assert.match(bridge, /expectedHash\.equals\(actualHash\)/);
  assert.match(bridge, /"CHARACTER"\.equals\(slot\.optString\("worldKind", ""\)\)/);
  assert.match(bridge, /captureNarrationFuture\(freshFuture\[0\], freshOracleSteps\[0\]\)/);
  assert.match(bridge, /gameCore\.oracleWindow\(narrationState\.toString\(\)\)/);
  assert.match(bridge, /BATCH OUTPUT/);
  assert.match(bridge, /parsed\.remove\("future"\)/);
  assert.match(bridge, /if \(cachedForProvider != null\) return cachedForProvider;/);
  assert.match(bridge, /private final ExecutorService narrationFutureIo = Executors\.newSingleThreadExecutor\(\)/);
  assert.match(bridge, /scheduleNarrationFutureRefill\(JSONObject committedState, String action, String turnId\)/);
  assert.match(bridge, /narrationFutureEpoch\+\+/);
  assert.match(bridge, /captureNarrationFutureIfEpoch\(new JSONArray\(future\.toString\(\)\), oracleSteps, expectedEpoch\)/);
  assert.match(bridge, /if \(cachedForProvider != null && generated == cachedForProvider\) \{[\s\S]*scheduleNarrationFutureRefill\(narrationState, action, turnId\);/);
});


 test('turn-one current save survives a changed baseline prologue', () => {
  const fragment = html.slice(html.indexOf('ensureCurrentLevel(state);'), html.indexOf('function esc('));
  const saved = {turn: 1, title: 'current', currentLevel: 2, currentLevelKey: '2',
    location: 'live location', player: {name: 'Cao Minh', hp: 7, depletion: 4},
    party: [{id: 'lucia', present: false}], inventory: [], flags: {consequence: 'live'},
    knowledge: {seen: 'live'}, relationship: 'live', promises: ['live'], debts: ['live'],
    log: [{text: 'saved opening'}]};
  const sandbox = {state: structuredClone(saved), CURRENT_CHARACTER_CANON: {version: 'new'},
    initial: {log: [{text: 'new baseline prologue'}], player: {name: 'Cao Minh', hp: 50},
      inventory: [{name: 'baseline'}], location: 'baseline', currentLevel: 0, currentLevelKey: '0'},
    ensureCurrentLevel: value => value};
  vm.runInNewContext(fragment, sandbox);
  delete sandbox.state.characterCanon;
  assert.deepEqual(sandbox.state, saved);
});


test('packaged Backrooms background music loops only while the Activity is resumed', () => {
  const music = path.join(root, 'assets', 'BackroomsBM.mp3');
  assert.ok(fs.existsSync(music));
  assert.ok(fs.statSync(music).size > 1024);
  assert.match(bridge, /BACKGROUND_MUSIC_ASSET = "BackroomsBM\.mp3"/);
  assert.match(bridge, /BACKGROUND_MUSIC_VOLUME = 0\.18f/);
  assert.match(bridge, /initializeBackgroundMusic\(\)/);
  assert.match(bridge, /player\.setLooping\(true\)/);
  assert.match(bridge, /player\.prepareAsync\(\)/);
  assert.match(bridge, /activityResumed = true;[\s\S]*resumeBackgroundMusic\(\);/);
  assert.match(bridge, /activityResumed = false;[\s\S]*pauseBackgroundMusic\(\);[\s\S]*super\.onPause\(\);/);
  assert.match(bridge, /releaseBackgroundMusic\(\);[\s\S]*if \(gameCore != null\) gameCore\.close\(\);/);
});
