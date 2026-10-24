const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ui = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/level-travel.js'), 'utf8');
const storage = new Map();
let starts = 0;
let core;
let latest;
let submissions = 0;
const native = {
  initializeLevelGame(raw, fresh) {
    const input = JSON.parse(raw);
    if (fresh) {
      starts++;
      core = { world: { levelTravel: { runId: 'run-' + starts, exitStreak: 0 } } };
      latest = { ...input, levelTravel: core.world.levelTravel, coreSnapshot: core,
        title: 'Level ' + starts, levelBackground: 'https://res.cloudinary.com/test/' + starts };
      return JSON.stringify(latest);
    }
    if (!input.coreSnapshot) throw Error('unsupported save');
    core = input.coreSnapshot;
    return raw;
  },
  resumeLevelGame(raw, hasBrowserSave) {
    if (core) return JSON.stringify(latest);
    return this.initializeLevelGame(raw, !hasBrowserSave);
  },
  levelCoreSnapshot() { return JSON.stringify(core); }
};
function boot() {
  const elements = new Map();
  function element() {
    const e = { style: {}, textContent: '', disabled: false, hidden: false, listeners: {},
      append() {}, before() {}, prepend() {}, addEventListener(name, fn) {this.listeners[name] = fn;},
      getAttribute(name) { return this[name]; } };
    Object.defineProperty(e, 'id', { set(value) { elements.set(value, this); }, get() { return [...elements].find(([, v]) => v === this)?.[0]; } });
    return e;
  }
  elements.set('snapshot', element());
  elements.set('location', Object.assign(element(), {
    textContent: 'Level 0 / The Lobby — khu phòng vàng'
  }));
  elements.set('title', Object.assign(element(), {textContent: 'Level 0 / The Lobby'}));
  elements.set('startupRecovery', Object.assign(element(), {hidden: true}));
  elements.set('startupRecoveryMessage', element());
  elements.set('startNewGameFromRecovery', element());
  const context = { Android: native, confirm: () => true,
    document: { getElementById: id => elements.get(id), createElement: element, body: element() },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    submitEl: element(), statusEl: element(), actionEl: element(),
    formEl: { requestSubmit() { submissions++; } } };
  context.window = context;
  vm.createContext(context);
  vm.runInContext('const initial={turn:1}; let state={turn:1}; let busy=false; function render(){};', context);
  vm.runInContext(ui, context);
  return context;
}
const first = boot();
assert.equal(starts, 1, 'first game must be initialized once');
assert.equal(first.document.getElementById('level-travel-background'), undefined,
  'travel controls must not create a second Snapshot background');
for (let i = 0; i < 100; i++) first.render();
first.save();
assert.equal(starts, 1, 'render/save cannot initialize another game');
first.load();
assert.equal(starts, 1, 'load cannot reroll');
assert.equal(first.document.getElementById('level-travel-attempt'), undefined,
  'exit attempts must use the primary gameplay actions, without a dedicated exit button');
assert.match(first.document.getElementById('level-travel-streak').textContent, /0\/3/);
assert.equal(submissions, 0, 'travel rendering must never submit a gameplay action');
const reopened = boot();
assert.equal(starts, 1, 'reopen must load the current save');
reopened.resetGame();
assert.equal(starts, 2, 'explicit reset creates one new game');
assert.equal(reopened.submitEl.disabled, false);
core = null;
storage.set('backroom-apk-state', JSON.stringify({turn:1}));
const invalid = boot();
assert.equal(starts, 2, 'unsupported old save must not silently start or reroll');
assert.equal(invalid.submitEl.disabled, true);
assert.equal(invalid.document.getElementById('startupRecovery').hidden, false,
  'an incompatible save must display recovery at the top of the screen');
assert.equal(invalid.document.getElementById('location').textContent, 'Chưa xác định',
  'a failed boot must not display a stale or guessed true Level');
assert.equal(invalid.document.getElementById('title').textContent, 'MAIN_BACKROOMS — Cao Minh',
  'a failed boot must not reveal a canonical Level in the heading');
assert.match(invalid.document.getElementById('startupRecoveryMessage').textContent, /không tự chuyển đổi|không thể khởi tạo/i);
invalid.document.getElementById('startNewGameFromRecovery').listeners.click();
assert.equal(starts, 3, 'only explicit recovery creates another game');
assert.equal(invalid.submitEl.disabled, false, 'explicit new game must restore input');
assert.equal(invalid.document.getElementById('startupRecovery').hidden, true);
console.log('PASS: startup, 100 renders, save/load, reopen, read-only exit streak, reset and rejected-save recovery');

