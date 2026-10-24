const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const assets = path.join(__dirname, '../app/src/main/assets');
const source = fs.readFileSync(path.join(assets, 'diagnostic-export.js'), 'utf8');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const java = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');

function harness({withBridge = true} = {}) {
  const store = new Map();
  const filename = 'very-private-token-do-not-leak';
  store.set('backroom-apk-state', JSON.stringify({
    coreSnapshot: {saveVersion: 3, metadata: {apiKey: filename}},
    log: [{text: 'A private player conversation including ' + filename}]
  }));
  const button = {disabled: false, callbacks: {},
    addEventListener(type, fn) {this.callbacks[type] = fn;}};
  const status = {textContent: ''};
  const recovered = {hidden: false};
  const nodes = {exportDiagnosticsJson: button, diagnosticExportStatus: status, startupRecovery: recovered};
  const emitted = {};
  const calls = [];
  const state = {
    turn: 7, displayLocation: 'Chưa xác định',
    levelTravel: {nodeId: 'LEVEL.NODE.2'},
    party: [{id: 'luc-tram'}], combat: {active: true},
    coreSnapshot: {saveVersion: 3, metadata: {apiKey: filename}},
    log: [{text: filename}]
  };
  const context = {
    state,
    localStorage: {
      getItem(k) {return store.get(k) ?? null;},
      setItem(k, v) {store.set(k, v);},
      removeItem(k) {store.delete(k);}
    },
    document: {getElementById(id) {return nodes[id] || null;}},
    addEventListener(name, callback) {emitted[name] = callback;},
    Android: withBridge ? {exportDiagnosticLog(json) {calls.push(json);}} : undefined
  };
  context.window = context;
  vm.runInNewContext(source, context, {filename: 'diagnostic-export.js'});
  return {context, store, button, status, emitted, calls, filename};
}

test('menu exposes export action and loads script before native Level Travel startup', () => {
  assert.match(html, /id="exportDiagnosticsJson"/);
  assert.match(html, /src="diagnostic-export\.js"/);
  assert.ok(html.indexOf('src="diagnostic-export.js"') <
    html.indexOf('src="level-travel.js"'));
  assert.match(java, /@JavascriptInterface public void exportDiagnosticLog\(String uiSummaryJson\)/);
  assert.match(java, /Intent\.ACTION_CREATE_DOCUMENT/);
  assert.match(java, /onActivityResult/);
  assert.doesNotMatch(java, /WRITE_EXTERNAL_STORAGE/);
});

test('JSON report sends technical fields and excludes secrets, narrative and raw save', () => {
  const h = harness();
  h.emitted.error({filename: 'file:///android_asset/index.html',
    lineno: 55, colno: 9, message: 'private token ' + h.filename});
  h.emitted.unhandledrejection({reason: 'private reason'});
  h.context.backroomDiagnosticRecord('startup', 'save_version_unsupported');
  h.button.callbacks.click();
  assert.equal(h.calls.length, 1);
  const data = JSON.parse(h.calls[0]);
  assert.equal(data.turn, 7);
  assert.equal(data.levelNodeId, 'LEVEL.NODE.2');
  assert.equal(data.combatActive, true);
  assert.equal(data.hasCoreSnapshot, true);
  assert.equal(data.startupRecoveryVisible, true);
  assert.equal(data.displayLocationKnown, false);
  assert.equal(data.browserSaveVersion, 3);
  assert.ok(data.events.some(e => e.code === 'uncaught_exception'
    && e.source === 'index.html' && e.line === 55));
  assert.ok(data.events.some(e => e.code === 'save_version_unsupported'));
  assert.doesNotMatch(h.calls[0], /very-private-token|private reason|private token|apiKey|conversation|logText/);
  assert.equal(h.button.disabled, true);
  h.context.backroomDiagnosticExportResult('saved');
  assert.equal(h.button.disabled, false);
  assert.match(h.status.textContent, /Đã xuất log JSON/);
});

test('log is bounded across events and strips arbitrary error payloads', () => {
  const h = harness();
  for (let i = 0; i < 150; i++) h.context.backroomDiagnosticRecord('combat', 'error with secret');
  assert.equal(h.context.backroomDiagnosticEvents().length, 80);
  assert.ok(h.context.backroomDiagnosticEvents().every(e => e.code === 'details_redacted'));
  const persisted = h.store.get('backroom-diagnostic-events-v1');
  assert.equal(JSON.parse(persisted).length, 80);
  h.button.callbacks.click();
  assert.equal(JSON.parse(h.calls[0]).events.length, 80);
});

test('missing native bridge never throws and leaves save unchanged', () => {
  const h = harness({withBridge: false});
  const before = h.store.get('backroom-apk-state');
  h.button.callbacks.click();
  assert.match(h.status.textContent, /chưa hỗ trợ/);
  assert.equal(h.button.disabled, false);
  assert.equal(h.store.get('backroom-apk-state'), before);
  assert.equal(h.calls.length, 0);
});
