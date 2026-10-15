const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.join(__dirname, '../app/src/main/assets');
const source = fs.readFileSync(path.join(assets, 'battle-start.js'), 'utf8');
const java = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');

function runtime(name = 'Duller') {
  let bridgeCalls = [], button, turnCalls = [];
  const last = {children: [{textContent: 'Đoạn cuối lời kể.'}], appendChild(child) { this.children.push(child); button = child; }};
  const ctx = {
    state: {turn: 3, log: [{role: 'gm', text: 'Lời kể trước.'},
      {role: 'gm', text: 'Đoạn một.\n\nĐoạn cuối lời kể.', encounterId: 'CORE-ID'}],
      combat: {active: false, entityKey: 'duller'},
      pendingEncounter: {entityName: name, entityKey: 'duller', encounterId: 'CORE-ID'}},
    busy: false, statusEl: {},
    logEl: {querySelectorAll() { return [{children: []}, last]; }},
    document: {
      getElementById(id) { return id === "battleStartButton" ? button || null : null; },
      createElement() { return {style: {}, addEventListener(type, handler) { this.click = handler; }, remove() {
        last.children = last.children.filter(x => x !== this); button = null;
      }}; }
    },
    Android: {battleStart(json, id) { bridgeCalls.push([JSON.parse(json), id]); }},
    semanticPrepareNextState(before, json) { return JSON.parse(json); },
    syncPrimaryActions() {},
    render() { ctx.backroomRenderBattleStart(); },
    backroomTurn(json) { ctx.state = JSON.parse(json); turnCalls.push(ctx.state); ctx.render(); }
  };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(source, ctx);
  return {ctx, last, button: () => button, bridgeCalls, turnCalls};
}

test('Battle Start follows all narrative paragraphs and uses the Core registry display name', () => {
  for (const name of ['Duller', 'Hostile Faceling', 'Diệp Minh']) {
    const r = runtime(name);
    assert.equal(r.last.children[0].textContent, 'Đoạn cuối lời kể.');
    assert.equal(r.last.children[1], r.button());
    assert.equal(r.button().textContent, `Thực thể ${name} đã xuất hiện ! Battle Start.`);
    assert.equal(r.bridgeCalls.length, 0, 'render must never start Combat');
    assert.equal(r.ctx.state.combat.active, false);
  }
  assert.doesNotMatch(html, /id="exploreActionButton"/);
  assert.match(html, /id="submit"/);
});

test('rapid clicks send one exact Core encounter and success removes the button', () => {
  const r = runtime();
  const click = r.button().click;
  for (let i = 0; i < 8; i++) click();
  assert.equal(r.bridgeCalls.length, 1);
  assert.equal(r.bridgeCalls[0][1], 'CORE-ID');
  assert.equal(r.bridgeCalls[0][0].turn, 3);
  assert.equal(r.button().disabled, true);
  r.ctx.backroomBattleStart(JSON.stringify({handled: true, state: {turn: 3, combat: {active: true, entityKey: 'duller', encounterId: 'CORE-ID'}}}));
  assert.equal(r.button(), null);
  assert.equal(r.turnCalls.length, 1);
  assert.equal(r.ctx.state.combat.entityKey, 'duller');
});

test('Core rejection or bridge failure keeps Battle Start retryable', () => {
  const r = runtime();
  r.button().click();
  r.ctx.backroomBattleStart('{"handled":false,"error":"save_failed"}');
  assert.equal(r.button().disabled, false);
  assert.equal(r.ctx.state.combat.active, false);
  r.ctx.Android.battleStart = () => { throw new Error('bridge failed'); };
  r.button().click();
  assert.equal(r.button().disabled, false);
});

test('a name in prose alone never creates Battle Start; pending button returns on reload', () => {
  const r = runtime();
  delete r.ctx.state.pendingEncounter;
  r.ctx.render();
  assert.equal(r.button(), null);
  r.ctx.state.pendingEncounter = {entityName: 'Duller', encounterId: 'CORE-ID'};
  r.ctx.render();
  assert.ok(r.button());
  assert.equal(r.bridgeCalls.length, 0);
});

function embeddedFunction(name) {
  const line = java.split('\n').find(row => row.includes('"function ' + name + '('));
  return JSON.parse(line.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/)[1]);
}

test('pending encounters show no Entity image; confirmed encounter clears a previous wave image', () => {
  const ctx = {state: {combat: {active: false, entityKey: 'duller', encounterId: 'NEW'}},
    backroomCombatEntityVisualKey: 'hound', backroomVisualEncounterId: 'OLD',
    normalizeEntityKey: key => key};
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(embeddedFunction('activeEntityKey'), ctx);
  assert.equal(ctx.activeEntityKey(), '');
  ctx.state.combat.active = true;
  assert.equal(ctx.activeEntityKey(), 'duller');
  assert.equal(ctx.backroomCombatEntityVisualKey, '');
  assert.ok(fs.existsSync(path.join(assets, 'entity', ctx.activeEntityKey() + '.webp')));
});

test('the native bridge passes narration before committing and confirmation never calls submitAction', () => {
  assert.match(java, /processValidatedCandidate\([\s\S]*?action, reply, generated\.toString\(\)\)/);
  assert.match(java, /@JavascriptInterface public void battleStart\(String stateJson, String encounterId\)/);
  assert.doesNotMatch(source, /submitAction|selectedEntityKey|startCombatState/);
  assert.doesNotMatch(java, /forceEntityEncounterFlag|requireGameCore\(\)\.startCombatState/);
});

test('the final action-button override blocks exploration while Core awaits confirmation', () => {
  const processing = html.match(/function processing\(\)\{[^}]+\}/)[0];
  const ctx = {state: {pendingEncounter: {encounterId: 'CORE-ID'}}, busy: false};
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(processing, ctx);
  assert.equal(ctx.processing(), true);
  delete ctx.state.pendingEncounter;
  assert.equal(ctx.processing(), false);
});
