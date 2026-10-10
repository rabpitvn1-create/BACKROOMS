const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const code = fs.readFileSync(path.join(root, 'app/src/main/assets/gm-turn-feedback.js'), 'utf8');

function fixture() {
  const timers = new Map();
  const removed = [];
  const log = {
    children: [],
    scrollTop: 0,
    scrollHeight: 9,
    appendChild(node) { this.children.push(node); },
    querySelector(selector) {
      return selector === '.gm-transport-error'
        ? this.children.find(x => x.className.includes('gm-transport-error')) || null
        : null;
    }
  };
  function node(tag) {
    return {
      tag,
      className: '',
      textContent: '',
      children: [],
      appendChild(child) { this.children.push(child); },
      setAttribute(key, value) { this[key] = value; },
      remove() {
        removed.push(this);
        const index = log.children.indexOf(this);
        if (index !== -1) log.children.splice(index, 1);
      }
    };
  }
  const status = node('div');
  const pending = node('article');
  let nextTimer = 0;
  const context = {
    busy: true,
    state: { turn: 4, player: { name: 'Cao Minh' } },
    setTimeout(fn, ms) {
      const id = ++nextTimer;
      timers.set(id, { fn, ms });
      return id;
    },
    clearTimeout(id) { timers.delete(id); },
    document: {
      getElementById(id) { return id === 'status' ? status : id === 'log' ? log : null; },
      querySelectorAll(selector) { return selector === '[data-pending="1"]' ? [pending] : []; },
      createElement: node
    }
  };
  let turns = 0;
  let errors = 0;
  context.window = context;
  context.backroomTurn = () => { turns++; context.busy = false; };
  context.backroomError = () => { errors++; context.busy = false; };
  context.backroomProvider = () => {};
  vm.runInNewContext(code, context);
  return { context, timers, log, removed, status, pending, get turns() { return turns; }, get errors() { return errors; } };
}

test('provider failover retains one wait clock and a committed callback clears notices', () => {
  const f = fixture();
  f.context.backroomProvider('Gehihi');
  assert.equal(f.timers.size, 2);
  f.context.backroomProvider('Gemini');
  assert.equal(f.timers.size, 2);
  const first = [...f.timers.values()].find(t => t.ms === 25000);
  first.fn();
  assert.match(f.status.textContent, /chưa được lưu/i);
  f.context.backroomTurn('{"turn":5}');
  assert.equal(f.turns, 1);
  assert.equal(f.timers.size, 0);
  assert.equal(f.context.state.turn, 4); // UI-only wrapper never forges Core state.
});

test('failure clears pending messages, preserves draft, and shows a visible non-state error', () => {
  const f = fixture();
  f.context.actionDraft = 'Tôi tìm kiếm quanh phòng vàng';
  f.context.backroomProvider('Haku');
  f.context.backroomError('Provider HTTP 503');
  assert.equal(f.errors, 1);
  assert.equal(f.timers.size, 0);
  assert.ok(f.removed.includes(f.pending));
  assert.equal(f.context.actionDraft, 'Tôi tìm kiếm quanh phòng vàng');
  assert.equal(f.log.children.length, 1);
  assert.equal(f.log.children[0].children[1].textContent, 'Provider HTTP 503');
  assert.match(f.status.textContent, /chưa được xác nhận/i);
  assert.equal(f.context.state.turn, 4);
});

test('native onPageFinished can rewire the replaced provider callback', () => {
  const f = fixture();
  let nativeCalls = 0;
  f.context.backroomProvider = () => { nativeCalls++; };
  f.context.backroomWireGMFeedbackProvider();
  f.context.backroomProvider('SOL');
  assert.equal(nativeCalls, 1);
  assert.equal(f.timers.size, 2);
  f.context.backroomWireGMFeedbackProvider(); // Idempotent, no recursion.
  f.context.backroomProvider('Gemini');
  assert.equal(nativeCalls, 2);
  assert.equal(f.timers.size, 2);
});

test('the final generated Android source enforces GM schema and loads feedback asset', () => {
  const java = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
  const html = fs.readFileSync(path.join(root, 'app/src/main/assets/index.html'), 'utf8');
  for (const marker of [
    'GM_TURN_FEEDBACK_R01',
    'checkedGmResponse(gehihiText(prompt))',
    'checkedGmResponse(geminiText(prompt))',
    'checkedGmResponse(hakuText(prompt))',
    'checkedGmResponse(solText(prompt))',
    'window.backroomWireGMFeedbackProvider',
    'geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 1800, true, 75_000L)'
  ]) assert.ok(java.includes(marker), marker);
  assert.match(html, /<script src="gm-turn-feedback\.js"><\/script>/);
  assert.ok(java.includes('postJsonFast(base + "/chat/completions", BuildConfig.GEHIHI_API_KEY'));
  assert.ok(java.includes('postJsonFast("https://api.vilao.ai/v1/chat/completions", BuildConfig.SOL_API_KEY'));
});
