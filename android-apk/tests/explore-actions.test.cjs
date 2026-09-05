const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'backroom-actions-'));
try {
  const assets = path.join(temp, 'app/src/main/assets');
  fs.mkdirSync(assets, { recursive: true });
  fs.copyFileSync(path.join(root, 'app/src/main/assets/index.html'), path.join(assets, 'index.html'));
  fs.copyFileSync(path.join(root, 'patch-two-page-ui.py'), path.join(temp, 'patch-two-page-ui.py'));
  execFileSync('python3', [path.join(temp, 'patch-two-page-ui.py')]);
  const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
  execFileSync('python3', [path.join(temp, 'patch-two-page-ui.py')]);
  assert.equal(fs.readFileSync(path.join(assets, 'index.html'), 'utf8'), html);
  assert.match(html, /type="button" id="explore"/);
  const handler = html.slice(html.indexOf('const exploreEl='), html.indexOf('\nrender();', html.indexOf('const exploreEl=')));
  assert.ok(handler.includes('window.backroomError='));
  const listeners = {};
  const explore = { disabled: false, addEventListener: (type, fn) => { listeners[type] = fn; } };
  const sent = [];
  const context = {
    byId: () => explore,
    busy: false,
    state: { turn: 1 },
    actionEl: { value: '' },
    submitEl: { disabled: false },
    statusEl: { textContent: '' },
    formEl: { addEventListener: (type, fn) => { listeners[type] = fn; } },
    window: { Android: { submitTurn: (state, action) => sent.push({ state, action }) } },
    save() {}, render() {}, focusLatestGmStart() {},
  };
  vm.createContext(context);
  vm.runInContext(handler, context);
  const submit = () => listeners.submit({ preventDefault() {} });
  const complete = () => context.window.backroomTurn('{"turn":2}');
  submit();
  assert.equal(sent.length, 0, 'Empty manual input must not submit');
  listeners.click();
  assert.equal(sent.length, 1, 'Explore works without typed input');
  assert.match(sent[0].action, /khám phá.*tìm kiếm/);
  assert.deepEqual(JSON.parse(sent[0].state), { turn: 1 });
  assert.ok(explore.disabled && context.submitEl.disabled);
  listeners.click(); submit();
  assert.equal(sent.length, 1, 'Repeated actions are blocked while busy');
  complete();
  assert.ok(!explore.disabled && !context.submitEl.disabled);
  context.actionEl.value = 'Bản nháp cần giữ';
  listeners.click(); complete();
  assert.equal(context.actionEl.value, 'Bản nháp cần giữ');
  context.actionEl.value = '  Kai mở cửa bên trái  ';
  submit();
  assert.equal(sent.at(-1).action, 'Kai mở cửa bên trái');
  complete();
  assert.equal(context.actionEl.value, '');
  context.actionEl.value = 'Giữ lại khi lỗi';
  listeners.click();
  context.window.backroomError('Network failure');
  assert.ok(!explore.disabled && !context.submitEl.disabled);
  assert.equal(context.actionEl.value, 'Giữ lại khi lỗi');
  context.window.Android.submitTurn = () => { throw new Error('Bridge failure'); };
  submit();
  assert.ok(!context.busy && !explore.disabled && !context.submitEl.disabled);
  assert.match(context.statusEl.textContent, /Bridge failure/);
  delete context.window.Android;
  listeners.click();
  assert.ok(!context.busy && !explore.disabled);
  console.log('PASS: exploration/manual actions, duplicate guard, draft preservation, success/error recovery, missing bridge, patch idempotence');
} finally {
  fs.rmSync(temp, { recursive: true, force: true });
}
