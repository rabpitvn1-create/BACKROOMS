const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');

test('inline composer contains one send button and Cao Minh avatar',()=>{
  const row=html.match(/<div class="primary-action-row chat-glass" id="primaryActionRow">([\s\S]*?)<\/div>/);
  assert.ok(row);
  assert.deepEqual([...row[1].matchAll(/<button\b[^>]*\bid="([^"]+)"/g)].map(m=>m[1]),['submit']);
  assert.doesNotMatch(html,/searchActionButton/);
  assert.doesNotMatch(html,/exploreActionButton|submitMacroAction|footprint-icon/);
  assert.match(html,/class="chat-avatar" src="avatars\/CAO_MINH_AVATAR.jpg"/);
  assert.doesNotMatch(html,/playerActionModal|playerActionConfirm|PLAYER ACTION/);
  assert.match(row[1],/id="submit" aria-label="Gửi hành động"/);
});

test('composer submits EXECUTE directly and combat blocks form submissions',()=>{
  assert.match(html,/window\.Android\.submitAction\(JSON\.stringify\(state\),"EXECUTE",a\)/);
  assert.match(html,/if\(combatActive\(\)\)\{ev\.preventDefault\(\);ev\.stopImmediatePropagation\(\);\}/);
  assert.match(html,/window\.__combatBusy\|\|window\.__combatDiceBusy\|\|window\.__combatFeedbackBusy/);
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
  assert.equal(execute.disabled,true);
  let blocked=0;
  guard({preventDefault(){blocked++;},stopImmediatePropagation(){blocked++;}});
  assert.equal(blocked,2);
  ctx.state.combat.active=false;
  ctx.renderCombatActionBar();
  assert.equal(classes.has('combat-direct'),false);
  assert.equal(execute.type,'submit');
  assert.equal(synced,1);
});
