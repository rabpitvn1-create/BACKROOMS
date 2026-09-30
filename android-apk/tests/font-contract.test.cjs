const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'..','app','src','main','assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const gm=fs.readFileSync(path.join(assets,'gm-choice-ui.js'),'utf8');
const inventory=fs.readFileSync(path.join(assets,'inventory-ui.js'),'utf8');
const snapshot=fs.readFileSync(path.join(assets,'snapshot-ui.js'),'utf8');

test('Pretendard Std is the packaged default game font',()=>{
  assert.match(index,/font-family:'Pretendard Std';font-style:normal;font-weight:400/);
  assert.match(index,/PretendardStd-Regular\.woff2/);
  assert.match(index,/PretendardStd-Bold\.woff2/);
  assert.match(index,/PretendardStd-ExtraBold\.woff2/);
  assert.match(index,/body\{[^}]*font:15px 'Pretendard Std'/);
  assert.match(index,/player-action-modal\{[^}]*font-family:'Pretendard Std'/);
  assert.match(index,/game-menu-modal\{[^}]*font-family:'Pretendard Std'/);
  assert.match(gm,/\.message\.gm \.role\{font-family:'Play','Pretendard Std'/);
  assert.match(gm,/\.message\.gm \.gm-main-text\{font-family:'Play','Pretendard Std'/);
  assert.doesNotMatch(gm,/\.message\.player \.gm-main-text\{font-family:'Play'/);
});

test('Play remains primary for special presentation selectors',()=>{
  assert.match(index,/\.topbar,[^\n]*font-family:'Play','Pretendard Std'/);
  assert.match(index,/player-action-head h2\{[^}]*'Play','Pretendard Std'/);
  assert.match(gm,/\.semantic\{font-family:'Play','Pretendard Std'/);
  assert.match(gm,/\.gm-choice\{[^}]*font-family:'Play','Pretendard Std'/);
  assert.match(inventory,/\.inventory-item-name\{font-family:'Play','Pretendard Std'/);
  assert.match(snapshot,/\.combat-float\{[^}]*font-family:Play,"Pretendard Std"/);
});

test('Lucia Lục keeps the normal Pretendard font inside semantic narration',()=>{
  assert.match(gm,/\.semantic-lucia-name\{font-family:'Pretendard Std',system-ui,sans-serif\}/);
  assert.match(gm,/matched\.toLocaleLowerCase\('vi'\) === 'lucia lục'/);
});
