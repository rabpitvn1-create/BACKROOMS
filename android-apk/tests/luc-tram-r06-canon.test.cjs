const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const assets=path.join(__dirname,'../app/src/main/assets');
const doc=fs.readFileSync(path.join(assets,'canon/LUC_TRAM_CODEX.md'),'utf8');
const db=JSON.parse(fs.readFileSync(path.join(assets,'knowledge/knowledge_db.json'),'utf8'));
const reader=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt'),'utf8');
const get=id=>db.records.find(r=>r.id===id);
test('current Drive R06 is mirrored and old R05 scene is not used as current truth',()=>{
  assert.match(doc,/PROLOGUE ADDENDUM R06/);
  assert.match(doc,/LUC-PRO-R06/);
  assert.match(doc,/A-20261011-CLARIFICATION/);
  assert.match(doc,/quên (?:\*\*)?tất cả về Cao Minh/i);
  assert.match(doc,/Kiếm Các.*Táng Kiếm Cốc/);
  assert.match(doc,/Lucia Lục.*riêng biệt/);
});
test('R06 runtime packet separates history from post-revival knowledge and unknown encounter',()=>{
  const keys=['CHAR.LUC_TRAM.RUNTIME_CORE','CHAR.LUC_TRAM.REVIVAL_R06','CHAR.LUC_TRAM.OPEN_R06','REL.CAO_MINH.LUC_TRAM.HISTORY_R06','CHAR.LUC_TRAM.POWERS_AND_VISUAL'];
  assert.equal(new Set(db.records.map(r=>r.id)).size,db.records.length);
  assert.ok(db.records.every(r=>!/(?:^|\.)(?:KAI|SETH)(?:\.|$)/i.test(r.id)), 'Legacy character IDs must not return');
  assert.ok(db.records.every(r=>r.references.every(id=>db.records.some(target=>target.id===id))), 'No dangling canon reference');
  for(const id of keys){let r=get(id);assert.ok(r,id);assert.equal(r.authority,'CHARACTER_CANON_R06');}
  assert.match(get('CHAR.LUC_TRAM.REVIVAL_R06').text,/QUÊN TẤT CẢ VỀ CAO MINH/);
  assert.match(get('CHAR.LUC_TRAM.OPEN_R06').text,/Không tự gặp trước Level 4/);
  assert.match(get('CHAR.LUC_TRAM.RUNTIME_CORE').text,/Lucia Lục/);
  assert.match(reader,/if \("luc_tram" in presentActors\)/);
  assert.match(reader,/actionText, "lục trầm"/);
  assert.match(reader,/REL\.CAO_MINH\.LUC_TRAM\.HISTORY_R06/);
});
