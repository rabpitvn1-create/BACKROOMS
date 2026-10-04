const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.join(__dirname, '..', 'app', 'src', 'main');
const html = fs.readFileSync(path.join(root, 'assets', 'index.html'), 'utf8');
const core = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/GameCoreFacade.java'), 'utf8');
const bridge = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const choices = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/GmChoiceContract.java'), 'utf8');
const choiceUi = fs.readFileSync(path.join(root, 'assets', 'gm-choice-ui.js'), 'utf8');

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
});

test('free-form Explorer text shares the default Core trajectory', () => {
  const process = core.slice(core.indexOf('public synchronized String processRule('),
    core.indexOf('private PreparedTurn prepareExplorerTurnData('));
  assert.match(process, /String coreAction = GmChoiceContract\.defaultCoreAction\(legacy\);/);
  assert.match(process, /nextWorldTurnId\(legacy, coreAction\)/);
  assert.match(process, /prepareExplorerTurnData\(legacy, coreAction\)/);
});

test('GM path is one current-turn request with milestone, local facts and memorable events', () => {
  const turn = bridge.slice(bridge.indexOf('@JavascriptInterface public void submitTurn('),
    bridge.indexOf('@JavascriptInterface public void combatRoll('));
  const prompt = bridge.slice(bridge.indexOf('private String narrationPrompt('),
    bridge.indexOf('private void logDiagnostic(', bridge.indexOf('private String narrationPrompt(')));
  assert.ok(turn.indexOf('completePreparedTurn(') < turn.indexOf('generateNarrationText('));
  assert.ok(turn.indexOf('generateNarrationText(') < turn.indexOf('commitPresentation('));
  assert.match(prompt, /milestoneCore\.promptContext\(state\)/);
  assert.match(prompt, /memorableEvents/);
  assert.match(prompt, /CURRENT LOCAL EVENTS/);
  assert.match(prompt, /1-3 gợi ý hành động cụ thể/);
  assert.doesNotMatch(bridge, /NarrationFutureBuffer|NarrationGuard|NarrationProviderPolicy|SceneContextCompiler|oracleWindow\(|prefetchChoices\(/);
  assert.doesNotMatch(html, /narrationPrefetchStatus|backroomNarrationFutureStatus|backroomPrefetchChoices/);
});

test('GM suggestions keep up to three distinct player-facing actions', () => {
  assert.match(choices, /MAX_CHOICES = 3/);
  assert.match(choices, /\.put\("action", text\)/);
  assert.match(choiceUi, /generated\.slice\(0, 3\)/);
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
