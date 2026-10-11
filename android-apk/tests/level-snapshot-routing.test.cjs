const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const router=require("../app/src/main/assets/level_snapshots/snapshot-mapping.js");
const assets=path.resolve(__dirname,"../app/src/main/assets/level_snapshots");
const registry=fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/core/LevelRouteRegistry.java"),"utf8");
const ids=Array.from(registry.matchAll(/^\s*add\("([^"]+)"/gm),match=>match[1]);
const s=(id,location="")=>({level:{id:id,number:0},location:location,flags:{}});
test("every canonical Level 0–6 node is mapped to its original local WebP",()=>{
  assert.equal(ids.length,25);
  assert.deepEqual(router.knownIds().sort(),ids.sort());
  const all=ids.flatMap(id=>router.filesFor(id));
  assert.equal(all.length,41);
  assert.equal(new Set(all).size,41);
  for(const file of all){
    assert.match(file,/\.webp$/);
    assert.ok(fs.statSync(path.join(assets,file)).size>0,file);
  }
});
test("sublevels and named areas use their own images, never just parent level number",()=>{
  assert.equal(router.resolve(s("0.2")).file,"level_00p2__snapshot_001.webp");
  assert.equal(router.resolve(s("1.2")).file,"level_01p2__snapshot_001.webp");
  assert.equal(router.resolve(s("1.5")).file,"level_01p5__snapshot_001.webp");
  assert.equal(router.resolve(s("5.1")).file,"level_05p1__snapshot_001.webp");
  assert.equal(router.resolve(s("6.1")).file,"level_06p1__snapshot_001.webp");
  assert.equal(router.resolve(s("manila_room")).file,"area_00__manila_room__snapshot_001.webp");
  assert.equal(router.resolve(s("the_torment")).file,"area_00__the_torment__snapshot_001.webp");
  assert.equal(router.resolve(s("red_rooms")).file,"area_00__red_rooms__snapshot_001.webp");
  assert.equal(router.resolve(s("office_market")).file,"area_04__the_office_market__snapshot_001.webp");
  assert.equal(router.resolve(s("traders_vault")).parent,1);
});
test("three route nodes without bespoke images explicitly use bundled parent reference",()=>{
  for(const id of ["0.1","0.5","0.7"]){
    const image=router.resolve(s(id));
    assert.equal(image.fallback,true);
    assert.equal(image.file,"level_0.webp");
    assert.ok(fs.statSync(path.join(assets,image.file)).size>0);
  }
});
test("multi-image level is deterministic for a location and persists across turns",()=>{
  const first=router.resolve({...s("0","Level 0 / Phòng phía đông"),turn:1});
  const again=router.resolve({...s("0","Level 0 / Phòng phía đông"),turn:41});
  assert.equal(first.file,again.file);
  assert.equal(first.variantCount,8);
  const variants=new Set();
  for(let i=0;i<50;i++)variants.add(router.resolve(s("0","Level 0 / Hall "+i)).file);
  assert.ok(variants.size>1,"different locations can display different snapshots");
});
test("node ID is authoritative and malicious or invalid identifiers cannot choose arbitrary paths",()=>{
  assert.equal(router.resolve({level:{id:"6.1",number:6}}).nodeId,"6.1");
  assert.equal(router.resolve({level:{number:3}}).nodeId,"3");
  const invalid=router.resolve({level:{id:"../../etc/passwd",number:0}});
  assert.equal(invalid.nodeId,"0");
  assert.ok(invalid.src.startsWith("file:///android_asset/level_snapshots/"));
  assert.equal(router.sceneKey(s("1.2","Old Corridor")).startsWith("1.2|"),true);
  assert.notEqual(router.sceneKey(s("1.2","Old Corridor")),router.sceneKey(s("1","Old Corridor")));
});
