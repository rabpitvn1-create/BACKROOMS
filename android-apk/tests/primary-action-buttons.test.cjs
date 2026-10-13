const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');

test('primary action bar has Explore followed by Execute, without Search',()=>{
  const row=html.match(/<div class="primary-action-row" id="primaryActionRow">([\s\S]*?)<\/div>/);
  assert.ok(row);
  assert.deepEqual([...row[1].matchAll(/<button\b[^>]*\bid="([^"]+)"/g)].map(m=>m[1]),['exploreActionButton','submit']);
  assert.doesNotMatch(html,/searchActionButton/);
  assert.match(row[1],/id="exploreActionButton" aria-label="Khám Phá"/);
  assert.match(row[1],/id="submit" aria-label="Thực Hiện"/);
});

test('both actions preserve their behavior and combat gating',()=>{
  assert.match(html,/submitMacroAction\("EXPLORE","Khám Phá"\)/);
  assert.match(html,/window\.Android\.submitAction\(JSON\.stringify\(state\),kind,label\)/);
  assert.match(html,/window\.Android\.submitAction\(JSON\.stringify\(state\),"EXECUTE",a\)/);
  assert.match(html,/if\(execute\)execute\.addEventListener\("click"/);
  assert.match(html,/if\(combatActive\(\)\)\{ev\.preventDefault\(\);ev\.stopImmediatePropagation\(\);\}/);
  assert.match(html,/\.primary-action-row\{display:grid;grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
  assert.match(html,/\.player-action-bar \.primary-action-row\{grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
});