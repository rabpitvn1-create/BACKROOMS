const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const html = readFileSync(join(__dirname, '../app/src/main/assets/index.html'), 'utf8');
function between(start, stop) {
  const a = html.indexOf(start);
  const b = html.indexOf(stop, a);
  assert.ok(a >= 0 && b > a, 'Required Character Info renderer is missing');
  return html.slice(a, b);
}
const helpers = between('  function finiteValue(value){', '  function iconFor(item)');
const renderer = between('  function render(member){', '  function statRows(item)');
const vitalRenderer = between('    const rawMaxHp=Number(member.maxHp)', '    renderSurvivalHud(member);');

function screen() {
  const status = { innerHTML: '' }, equipment = { innerHTML: '' }, inventory = { innerHTML: '' };
  const capacity = { textContent: '' };
  const scope = vm.createContext({
    status, equipment, inventory, nameEl: null,
    document: { getElementById: () => capacity },
    e: value => String(value ?? '').replace(/[&<>"']/g, ch => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch])),
    card: () => '', itemById: () => null
  });
  vm.runInContext(helpers + renderer, scope, { timeout: 1000 });
  return { scope, status };
}
const base = {
  STR: {base: 5, effective: 6, passive: 1},
  DEX: {base: 5, effective: 6, passive: 1},
  AGI: {base: 5, effective: 6, passive: 1},
  VIT: {base: 5, effective: 6, passive: 1},
  EVA: {base: 5, effective: 6, passive: 1}
};
const combat = {
  defense: 1, attackPercent: 104, skillDamagePercent: 104,
  criticalChancePercent: 5.45, criticalDamagePercent: 400,
  evasionPercent: 5.5, criticalResistancePercent: 0.25,
  evasionResistancePercent: 0.25
};

test('Character Info shows exactly five Base Stats and eight calculated Combat Stats', () => {
  const { scope, status } = screen();
  scope.render({
    id: 'cao_minh', stats: base, combatStatus: combat, presence: 'ACTIVE',
    condition: 'HEALTHY', energy: '∞', hpRegen: 0, statuses: []
  });
  for (const name of ['STR','DEX','AGI','VIT','EVA']) {
    assert.match(status.innerHTML, new RegExp('character-info-stat-name">' + name + '<'));
  }
  assert.equal((status.innerHTML.match(/class="character-info-stat-name"/g) || []).length, 5);
  assert.equal((status.innerHTML.match(/class="character-info-combat-item"/g) || []).length, 8);
  assert.match(status.innerHTML, /CHỈ SỐ CƠ BẢN/);
  assert.match(status.innerHTML, /CHỈ SỐ CHIẾN ĐẤU/);
  assert.match(status.innerHTML, /Sát thương đòn đánh/);
  assert.match(status.innerHTML, /×1\.04/);
  assert.match(status.innerHTML, /5\.45%/);
  assert.match(status.innerHTML, /5\.5%/);
  assert.match(status.innerHTML, /0\.25%/);
  assert.match(status.innerHTML, /400%/);
  assert.match(status.innerHTML, /Base 5 · \+1 bonus/);
  assert.doesNotMatch(status.innerHTML, /nextCoreCost|CORE STATS|\+1 Core/);
});

test('Switching Characters refreshes values without keeping previous stat cards', () => {
  const { scope, status } = screen();
  scope.render({id:'cao_minh',stats:base,combatStatus:combat,statuses:[]});
  scope.render({
    id:'iris',
    stats:Object.fromEntries(Object.keys(base).map(k=>[k,{base:5,effective:5,passive:0}])),
    combatStatus:{...combat, criticalChancePercent:5, defense:0},
    statuses:[{type:'<script>alert(1)</script>'}]
  });
  assert.equal((status.innerHTML.match(/character-info-combat-item/g) || []).length, 8);
  assert.doesNotMatch(status.innerHTML, /5\.45%|Base 5 · \+1 bonus/);
  assert.match(status.innerHTML, /Base 5/);
  assert.match(status.innerHTML, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
});

test('Missing core combat data never invents percentages', () => {
  const {scope,status}=screen();
  scope.render({id:'iris', stats:{}, combatStatus:null, statuses:[]});
  assert.equal((status.innerHTML.match(/character-info-stat-name/g)||[]).length,5);
  assert.doesNotMatch(status.innerHTML, /NaN|undefined|Infinity%/);
  assert.match(status.innerHTML, /character-info-combat-value">—</);
});

test('HP and EXP bars follow selected character, support MAX LEVEL and unknown data', () => {
  const nodes = Object.fromEntries(['characterLevelValue','characterExpValue','characterExpFill','characterExpProgress']
    .map(id => [id,{textContent:'',style:{},setAttribute(k,v){this[k]=v}}]));
  const hpFill={style:{}},hpValue={textContent:''};
  const ctx=vm.createContext({hpFill,hpValue,document:{getElementById:id=>nodes[id]}});
  vm.runInContext('function renderVitals(member){'+vitalRenderer+'}',ctx,{timeout:1000});
  ctx.renderVitals({maxHp:50,currentHp:50,level:0,exp:0,expToNextLevel:10});
  assert.equal(nodes.characterLevelValue.textContent,'LV. 0');
  assert.equal(nodes.characterExpValue.textContent,'EXP 0 / 10');
  assert.equal(nodes.characterExpFill.style.width,'0%');
  assert.equal(hpFill.style.width,'100%');
  ctx.renderVitals({maxHp:100,currentHp:25,level:1,exp:5,expToNextLevel:18});
  assert.equal(nodes.characterLevelValue.textContent,'LV. 1');
  assert.equal(hpFill.style.width,'25%');
  assert.equal(hpValue.textContent,'25/100');
  assert.ok(parseFloat(nodes.characterExpFill.style.width)>27);
  ctx.renderVitals({maxHp:250,currentHp:0,level:165,exp:0,expToNextLevel:null});
  assert.equal(nodes.characterExpValue.textContent,'MAX LEVEL');
  assert.equal(nodes.characterExpFill.style.width,'100%');
  ctx.renderVitals({maxHp:null,currentHp:null});
  assert.equal(nodes.characterLevelValue.textContent,'LV. —');
  assert.equal(nodes.characterExpValue.textContent,'EXP —');
  assert.equal(hpValue.textContent,'— / —');
});
