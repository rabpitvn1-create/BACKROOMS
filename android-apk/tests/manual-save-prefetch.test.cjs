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
  assert.match(bridge, /geminiBranchBatch\(prompt\.toString\(\)\)/);
  const batch = bridge.slice(bridge.indexOf('private JSONObject geminiBranchBatch('),
    bridge.indexOf('private boolean haikuConfigured()'));
  assert.equal((batch.match(/postJson\(/g) || []).length, 1);
  assert.doesNotMatch(batch, /generateText\(|haikuText\(|for\s*\(int attempt/);
});

test('Explorer prefetch prepares exactly the two fixed actions', () => {
  const prefetch = bridge.slice(bridge.indexOf('private void prefetchChoices('),
    bridge.indexOf('private String worldProposalPrompt(', bridge.indexOf('private void prefetchChoices(')));
  assert.match(prefetch, /choices\.length\(\) != 2/);
  assert.match(prefetch, /branch A\/B/);
  assert.doesNotMatch(prefetch, /A\/B\/C|branches A, B and C|i < 3/);
});
