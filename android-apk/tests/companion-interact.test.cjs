const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const ui = fs.readFileSync(path.join(root, 'app/src/main/assets/companion-interact.js'), 'utf8');

function fixture(debug = true) {
  const elements = Object.create(null), slots = new Map(), calls = [];
  class Element {
    constructor(tag) {
      this.tag = tag; this.children = []; this.parentNode = null;
      this.textContent = ''; this.value = ''; this.style = {};
      this.listeners = {}; this.disabled = false;
      this.classList = { add: () => {} };
    }
    appendChild(node) { this.children.push(node); node.parentNode = this; return node; }
    insertBefore(node) { return this.appendChild(node); }
    setAttribute() {}
    addEventListener(name, handler) { this.listeners[name] = handler; }
    click() { this.listeners.click?.(); }
    set id(value) { this._id = value; elements[value] = this; }
    get id() { return this._id; }
  }
  const body = new Element('body');
  const head = new Element('head');
  const actionBar = new Element('div'); actionBar.id = 'playerActionBar'; body.appendChild(actionBar);
  const log = new Element('div'); log.id = 'log'; body.appendChild(log);
  const turn = new Element('span'); turn.id = 'turn'; body.appendChild(turn);
  const location = new Element('span'); location.id = 'location'; body.appendChild(location);
  const window = {
    Android: {
      companionPreviewAvailable: () => debug,
      companionNewGame: () => calls.push('newGame'),
      companionOpen: slot => calls.push(['open', slot]),
      companionSubmit: (...args) => calls.push(['submit', ...args])
    },
    confirm: () => true
  };
  const document = {
    head, body,
    getElementById: id => elements[id] || null,
    querySelector: () => null,
    createElement: tag => new Element(tag)
  };
  const localStorage = {
    getItem: key => slots.get(key) || null,
    setItem: (key, value) => slots.set(key, value),
    removeItem: key => slots.delete(key)
  };
  const context = { window, document, localStorage, confirm: window.confirm, Math, Date };
  vm.runInNewContext(ui, context);
  return { window, elements, slots, calls };
}
const slotId = 'a'.repeat(32);
const projection = JSON.stringify({
  slotId, revision: 0, turn: 1, location: 'Level 0 / The Lobby',
  stop: 'level-0', publicEvents: []
});
const receipt = JSON.stringify({
  slotId, revision: 1, committedRevision: 1, turn: 2,
  location: 'Level 0 / The Lobby', stop: 'level-0', action: 'WAIT',
  publicEvents: [{type: 'WAIT_COMPLETED',
    payload: {actor: 'cao_minh', minutes: 30, location: 'The Lobby'}},
    {type: 'EXIT_STREAK_RESOLVED', payload: {completed: false, source: 'level-0'}}]
});

test('release build never mounts unfinished actor UI', () => {
  const f = fixture(false);
  assert.equal(f.elements.companionDock, undefined);
  assert.equal(f.window.backroomCompanionTurn, undefined);
});

test('only verified new game projects; a submitted interaction never optimistically commits', () => {
  const f = fixture();
  f.elements.companionStart.click();
  assert.deepEqual(f.calls, ['newGame']);
  f.window.backroomCompanionTurn(projection);
  assert.equal(f.slots.get('backroom-companion-native-slot'), slotId);
  const input = f.elements.companionInput;
  input.value = 'Tôi khuyên anh quan sát căn phòng kỹ hơn.';
  f.elements.companionSend.click();
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1][0], 'submit');
  assert.equal(f.calls[1][1], slotId);
  assert.equal(f.calls[1][2], input.value);
  assert.equal(f.elements.turn.textContent, 1);
  assert.match(f.elements.companionStatus.textContent, /Chưa có receipt/);
  f.window.backroomCompanionError('audit_failed');
  assert.equal(input.value, 'Tôi khuyên anh quan sát căn phòng kỹ hơn.');
  assert.equal(f.elements.turn.textContent, 1);
  assert.ok(f.slots.get('backroom-companion-native-pending'));
  f.elements.companionSend.click();
  assert.equal(f.calls[1][3], f.calls[2][3], 'retry preserves request alias');
  f.window.backroomCompanionTurn(receipt);
  assert.equal(input.value, '');
  assert.equal(f.elements.turn.textContent, 2);
  assert.equal(f.slots.get('backroom-companion-native-pending'), undefined);
});

test('generated Android loads bridge only after release patch and gates production', () => {
  const java = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
  const html = fs.readFileSync(path.join(root, 'app/src/main/assets/index.html'), 'utf8');
  for (const mark of ['COMPANION_NATIVE_INTERACT_PREVIEW_R01',
    'CompanionAndroidBridge.createNewGame', 'CompanionAndroidBridge.open(',
    'CompanionAndroidBridge.submit(', 'return BuildConfig.DEBUG',
    'geminiAuditText(', 'GAME_RNG.nextInt(bound)'])
    assert.ok(java.includes(mark), mark);
  assert.ok(html.includes('src="companion-interact.js"'));
  assert.ok(!ui.includes('submitAction('), 'INTERACT must not call legacy player-control bridge');
});
