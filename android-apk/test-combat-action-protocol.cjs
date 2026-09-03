const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Execute the actual generated WebView scripts, including capture-phase routing.
function fixture(partyTurn = true) {
  const html = fs.readFileSync(path.join(__dirname, 'app/src/main/assets/index.html'), 'utf8');
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
  const listeners = {}, nodes = {}, calls = [];
  class Element {
    constructor() { this.style = {}; this.dataset = {}; this.attrs = {}; this.buttons = {}; this.classList = { toggle() {}, add() {}, remove() {} }; }
    setAttribute(k, v) { this.attrs[k] = v; }
    addEventListener(k, cb) { this[k] = cb; }
    appendChild() {}
    querySelector(s) { return this.buttons[s] ||= new Element(); }
    querySelectorAll() { return Object.values(this.buttons); }
    set innerHTML(v) { this.html = v; this.buttons = {}; }
    get innerHTML() { return this.html; }
  }
  const parent = { insertBefore(el) { nodes[el.id] = el; } };
  for (const id of ['primaryActionRow', 'searchActionButton', 'submit', 'exploreActionButton', 'form', 'action', 'status', 'log', 'partySkillPopup', 'partySkillList']) {
    nodes[id] = new Element(); nodes[id].parentNode = parent;
  }
  const context = { console, busy: false, state: { combat: { active: true } },
    document: { getElementById: id => nodes[id], createElement: () => new Element(),
      addEventListener: (type, cb) => { listeners[type] = cb; } },
    setTimeout: cb => cb(), syncPrimaryActions() {}, appendMacroPending() {} };
  if (partyTurn) context.state.combat.partyTurn = { actorName: 'Kai', ap: 2, skills: [{name:'Test skill',cost:1}] };
  context.window = context;
  context.Android = { submitAction: (...args) => calls.push(args) };
  context.backroomTurn = json => { context.state = JSON.parse(json); context.busy = false; };
  context.backroomError = () => { context.busy = false; };
  for (const marker of ['const combatButtons=', '/* PARTY_TURN_BASED_AP_V1 */']) {
    const source = scripts.find(s => s.includes(marker));
    assert.ok(source, `Missing generated script ${marker}`);
    vm.runInNewContext(source, context);
  }
  function clickLegacy(id) {
    listeners.click({target:{closest:()=>nodes[id]},preventDefault(){},stopImmediatePropagation(){}});
  }
  return {context,nodes,calls,clickLegacy};
}

for (const [id, action, command] of [
  ['searchActionButton','atk','PARTY_TURN_ATK'],
  ['submit','def','PARTY_TURN_DEFEND'],
  ['exploreActionButton','run','PARTY_TURN_RUN'],
]) {
  test(`visible turn button sends ${command} once and unlocks on error`, () => {
    const {context,nodes,calls} = fixture();
    const button = () => nodes.partyTurnCombat.querySelector(`[data-party-action="${action}"]`);
    assert.equal(nodes.primaryActionRow.style.display, 'none');
    button().onclick(); button().onclick();
    assert.equal(calls.length, 1);
    assert.equal(calls[0][1], 'EXECUTE'); assert.equal(calls[0][2], command);
    assert.equal(JSON.parse(calls[0][0]).combat.partyTurn.actorName, 'Kai');
    assert.equal(button().disabled, true);
    context.backroomError('retry');
    assert.equal(button().disabled, false);
  });
  test(`legacy row sends ${command} for saves without partyTurn`, () => {
    const {nodes,calls,clickLegacy} = fixture(false);
    assert.equal(nodes.primaryActionRow.style.display, '');
    clickLegacy(id); clickLegacy(id);
    assert.equal(calls.length, 1); assert.equal(calls[0][2], command);
    if (id === 'submit') assert.match(nodes.submit.innerHTML, /PHÒNG THỦ/);
  });
}

test('response advances actor and exit restores normal action row', () => {
  const {context,nodes} = fixture();
  context.backroomTurn(JSON.stringify({combat:{active:true,partyTurn:{actorName:'Lucia',ap:3}}}));
  assert.match(nodes.partyTurnCombat.innerHTML, /Lucia/);
  context.backroomTurn(JSON.stringify({combat:{active:false}}));
  assert.equal(nodes.primaryActionRow.style.display, '');
  assert.equal(nodes.submit.type, 'submit');
  assert.match(nodes.submit.innerHTML, /Thực hiện/);
  assert.equal(nodes.partyTurnCombat.innerHTML, '');
});

test('skill uses the same bridge and cannot open while busy', () => {
  const {context,nodes,calls} = fixture();
  const skills = [];
  nodes.partySkillList.appendChild = b => skills.push(b);
  context.openPartySkillPopup(); skills[0].onclick();
  assert.equal(calls[0][2], 'PARTY_TURN_SKILL::Test skill');
  context.openPartySkillPopup(); assert.equal(skills.length, 1);
});
