const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'..','app','src','main','assets');
const party=fs.readFileSync(path.join(assets,'party-ui.js'),'utf8');

test('Ma Ton stat UI renders base plus separate passive bonus',()=>{
  assert.match(party,/String\(projectedBase\)\+' \(\+'\+String\(passiveBonus\)\+'\)'/);
  assert.match(party,/projectedWithPassive=projectedBase\+\(passiveOnly\?passiveBonus:0\)/);
  assert.match(party,/if\(passiveOnly&&current!==projectedWithPassive\)statText\+=' → '\+String\(current\)/);
});

test('Core upgrade cost remains independent from displayed passive bonus',()=>{
  const block=party.split('function appendCoreStats')[1].split('function combatStatusSection')[0];
  assert.match(block,/cost=statCost\(member,key\)/);
  assert.match(block,/passiveBonus=Number\(statLine&&statLine\.passiveBonus\)/);
  assert.doesNotMatch(block,/statCost\([^)]*passiveBonus/);
});
