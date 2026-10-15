const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');

test('primary action bar contains only Execute',()=>{
  const row=html.match(/<div class="primary-action-row" id="primaryActionRow">([\s\S]*?)<\/div>/);
  assert.ok(row);
  assert.deepEqual([...row[1].matchAll(/<button\b[^>]*\bid="([^"]+)"/g)].map(m=>m[1]),['submit']);
  assert.doesNotMatch(html,/searchActionButton/);
  assert.doesNotMatch(html,/exploreActionButton|submitMacroAction|footprint-icon/);
  assert.match(row[1],/id="submit" aria-label="Thực Hiện"/);
});

test('Execute preserves action submission, modal access and combat gating',()=>{
  assert.match(html,/window\.Android\.submitAction\(JSON\.stringify\(state\),"EXECUTE",a\)/);
  assert.match(html,/if\(execute\)execute\.addEventListener\("click"/);
  assert.match(html,/if\(combatActive\(\)\)\{ev\.preventDefault\(\);ev\.stopImmediatePropagation\(\);\}/);
  assert.match(html,/\.primary-action-row\{display:grid;grid-template-columns:minmax\(0,1fr\)/);
  assert.match(html,/\.player-action-bar \.primary-action-row\{grid-template-columns:minmax\(0,1fr\)/);
});

test('combat action bar renders with Execute as its only button',()=>{
  const script=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]).find(s=>s.includes('function renderCombatActionBar()'));
  const classes=new Set();
  let guard;
  const row={hidden:true,addEventListener(type,fn){guard=fn;},classList:{toggle(name,on){if(on)classes.add(name);else classes.delete(name);}}};
  const execute={disabled:true,type:'submit'};
  const elements={primaryActionRow:row,submit:execute};
  let synced=0;
  const ctx={state:{combat:{active:true}},document:{getElementById(id){return elements[id]||null;}},syncPrimaryActions(){synced++;}};
  ctx.window=ctx;
  vm.runInNewContext(script,ctx);
  assert.equal(row.hidden,false);
  assert.equal(classes.has('combat-direct'),true);
  assert.equal(execute.type,'button');
  let blocked=0;
  guard({preventDefault(){blocked++;},stopImmediatePropagation(){blocked++;}});
  assert.equal(blocked,2);
  ctx.state.combat.active=false;
  ctx.renderCombatActionBar();
  assert.equal(classes.has('combat-direct'),false);
  assert.equal(execute.type,'submit');
  assert.equal(synced,1);
});
