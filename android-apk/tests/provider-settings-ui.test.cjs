const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/provider-settings.js'), 'utf8');

function harness() {
  const handlers = new Map();
  const ids = ['providerCredentialForm', 'providerCredentialToggle',
    'providerCredentialClear', 'providerCredentialStatus'];
  const elements = new Map(ids.map(id => [id, {
    id, hidden: id === 'providerCredentialForm', textContent: '', attributes: {},
    addEventListener(name, callback) { handlers.set(id + ':' + name, callback); },
    setAttribute(name, value) { this.attributes[name] = value; },
  }]));
  const form = elements.get('providerCredentialForm');
  const inputs = ['GEHIHI_API_KEY', 'GEHIHI_MODEL', 'GEMINI_API_KEY_1'].map(name => ({name, value: ''}));
  form.querySelectorAll = () => inputs;
  form.reset = () => inputs.forEach(input => { input.value = ''; });
  const calls = [];
  const Android = {
    providerCredentialsStatus: () => JSON.stringify({gehihi: false, geminiKeys: 0}),
    saveProviderCredentials: value => {
      calls.push(JSON.parse(value));
      return JSON.stringify({gehihi: true, geminiKeys: 1});
    },
  };
  const window = {Android, confirm: () => true};
  vm.runInNewContext(script, {document: {getElementById: id => elements.get(id)},
    window, Android, JSON, Number, String});
  const invoke = (id, type) => {
    const handler = handlers.get(id + ':' + type);
    assert.ok(handler, id + ':' + type);
    handler({preventDefault() {}});
  };
  return {elements, inputs, calls, invoke};
}

test('provider keys are sent to native storage, cleared from inputs and never echoed', () => {
  const ui = harness();
  ui.invoke('providerCredentialToggle', 'click');
  assert.equal(ui.elements.get('providerCredentialForm').hidden, false);
  ui.inputs[0].value = 'super-secret-ge';
  ui.inputs[1].value = 'model';
  ui.inputs[2].value = 'super-secret-gem';
  ui.invoke('providerCredentialForm', 'submit');
  assert.equal(ui.calls.length, 1);
  assert.equal(ui.calls[0].GEHIHI_API_KEY, 'super-secret-ge');
  assert.equal(ui.calls[0].GEMINI_API_KEY_1, 'super-secret-gem');
  assert.ok(ui.inputs.every(input => input.value === ''));
  const status = ui.elements.get('providerCredentialStatus').textContent;
  assert.match(status, /Đã lưu/);
  assert.ok(!status.includes('super-secret'));
});

test('clearing credentials uses an explicit native request', () => {
  const ui = harness();
  ui.invoke('providerCredentialClear', 'click');
  assert.equal(ui.calls.length, 1);
  assert.equal(ui.calls[0].clear, true);
});

test('missing native UI does not throw during startup', () => {
  vm.runInNewContext(script, {document: {getElementById: () => null}, window: {}});
});
