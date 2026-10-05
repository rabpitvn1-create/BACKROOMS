const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.join(__dirname, '..', 'app', 'src', 'main');
const html = fs.readFileSync(path.join(root, 'assets', 'index.html'), 'utf8');
const core = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/GameCoreFacade.java'), 'utf8');
const bridge = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
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

test('free-form Explorer text remains the Core action instead of being replaced by a default', () => {
  const process = core.slice(core.indexOf('public synchronized String processRule('),
    core.indexOf('private PreparedTurn prepareExplorerTurnData('));
  assert.doesNotMatch(process, /defaultCoreAction\(legacy\)/);
  assert.match(process, /nextWorldTurnId\(legacy, text\)/);
  assert.match(process, /prepareExplorerTurnData\(legacy, text\)/);
});

test('GM path consumes one offline SceneFrame and only narrates it', () => {
  const turn = bridge.slice(bridge.indexOf('@JavascriptInterface public void submitTurn('),
    bridge.indexOf('@JavascriptInterface public void combatRoll('));
  const prompt = bridge.slice(bridge.indexOf('private String narrationPrompt('),
    bridge.indexOf('private void logDiagnostic(', bridge.indexOf('private String narrationPrompt(')));
  assert.ok(turn.indexOf('completePreparedTurn(') < turn.indexOf('gameCore.sceneFrame('));
  assert.ok(turn.indexOf('gameCore.sceneFrame(') < turn.indexOf('generateNarrationText('));
  assert.ok(turn.indexOf('generateNarrationText(') < turn.indexOf('commitPresentation('));
  assert.match(turn, /SceneDirector\.fallbackNarration\(sceneFrame\)/);
  assert.doesNotMatch(turn, /OfflinePresenter\.fallback\(sceneFrame\)/);
  assert.doesNotMatch(turn, /OfflinePresenter\.present\(safeEvents/);
  assert.doesNotMatch(turn, /mergeEncounterDialogue/);
  assert.match(prompt, /SCENE FRAME — authoritative current-turn facts/);
  assert.match(prompt, /PLAYER INTENT/);
  assert.match(prompt, /Không tạo choices hay gợi ý hành động/);
  assert.match(prompt, /focus=ENTITY/);
  assert.match(prompt, /pendingIntro không rỗng/);
  assert.match(prompt, /2-5 câu thoại tự nhiên/);
  assert.match(prompt, /Vạn Giới Ma Tôn/);
  assert.match(prompt, /Ma Đạo Kiếm Tu/);
  assert.match(prompt, /tự rà chính tả và ngữ pháp/);
  assert.match(prompt, /không dùng fragment kỹ thuật/);
  assert.match(prompt, /không tự cho Cao Minh vận công, phóng thần thức, xuất kiếm/);
  assert.doesNotMatch(prompt, /CaoMinhVoiceContract/);
  assert.doesNotMatch(prompt, /milestoneCore|MEMORABLE EVENTS|CURRENT LOCAL EVENTS/);
  assert.doesNotMatch(bridge, /NarrationFutureBuffer|NarrationGuard|NarrationProviderPolicy|SceneContextCompiler|oracleWindow\(|prefetchChoices\(/);
  assert.doesNotMatch(html, /narrationPrefetchStatus|backroomNarrationFutureStatus|backroomPrefetchChoices/);
  assert.doesNotMatch(choiceUi, /backroomPrefetchChoices/);
});

test('Explorer suggestions are the two fixed local actions instead of GM-authored choices', () => {
  assert.match(choiceUi, /function fixedExplorerChoices\(\)/);
  assert.match(choiceUi, /text:'Khám phá',action:'Khám phá'/);
  assert.match(choiceUi, /text:'Tìm kiếm',action:'Tìm kiếm'/);
  assert.doesNotMatch(choiceUi, /Array\.isArray\(entry\.choices\)/);
  assert.doesNotMatch(choiceUi, /generated\.slice\(0, 3\)/);
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
