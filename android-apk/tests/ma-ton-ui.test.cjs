const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'..','app','src','main','assets');
const party=fs.readFileSync(path.join(assets,'party-ui.js'),'utf8');

test('Dai Dao Ma Ton stat UI renders base plus ten-percent passive bonus',()=>{
  assert.match(party,/passiveBonusPercent=Number\(statLine&&statLine\.passiveBonusPercent\)/);
  assert.match(party,/String\(projectedBase\)\+' \(\+'\+String\(passiveBonusPercent\)\+'%\)'/);
  assert.match(party,/projectedWithPassive=projectedBase\+\(passiveOnly\?passiveBonus:0\)/);
});

test('Core upgrade cost remains independent from displayed passive bonus',()=>{
  const block=party.split('function appendCoreStats')[1].split('function combatStatusSection')[0];
  assert.match(block,/cost=statCost\(member,key\)/);
  assert.match(block,/passiveBonusPercent=Number\(statLine&&statLine\.passiveBonusPercent\)/);
  assert.doesNotMatch(block,/statCost\([^)]*passiveBonus/);
});

test('Lucia Lục uses the packaged Drive avatar',()=>{
  assert.match(party,/lucia:'file:\/\/\/android_asset\/avatars\/lucia_avatar\.png'/);
});
