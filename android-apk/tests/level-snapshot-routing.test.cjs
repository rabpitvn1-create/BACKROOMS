const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const router=require("../app/src/main/assets/level_snapshots/snapshot-mapping.js");
const assets=path.resolve(__dirname,"../app/src/main/assets/level_snapshots");
const registry=fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/core/LevelRouteRegistry.java"),"utf8");
const ids=Array.from(registry.matchAll(/^\s*add\("([^"]+)"/gm),match=>match[1]);
const s=(id,location="",step=0)=>({level:{id:id,number:0},location:location,flags:{exploration:{snapshotStep:step}}});
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
test("Core-confirmed visual steps cycle images without repetition or turn-based drift",()=>{
  const first=router.resolve({...s("0","Level 0 / Phòng phía đông"),turn:1});
  const again=router.resolve({...s("0","Level 0 / Phòng phía đông"),turn:41});
  assert.equal(first.file,again.file);
  assert.equal(first.variantCount,8);
  const seen=new Set([first.file]);
  let previous=first;
  for(let step=1;step<8;step++){
    const current=router.resolve(s("0","Level 0 / Hall "+step,step));
    assert.notEqual(current.file,previous.file,"each new step must change the image");
    seen.add(current.file);
    previous=current;
  }
  assert.equal(seen.size,8,"all variants should be visited before repeating");
  assert.equal(router.resolve(s("0","Level 0 / Phòng phía đông",8)).file,first.file);
  assert.equal(router.resolve(s("0","Level 0 / Other Room",0)).file,first.file,
    "location prose alone must not change the frame without authoritative progress");
  assert.equal(router.resolve(s("0","Level 0 / Phòng phía đông",-1)).file,first.file);
  assert.equal(router.resolve(s("0","Level 0 / Phòng phía đông",1.5)).file,first.file);
  assert.notEqual(router.sceneKey(s("0","Level 0 / Phòng phía đông",0)),
    router.sceneKey(s("0","Level 0 / Phòng phía đông",1)));
  assert.notEqual(router.resolve(s("1","Parking",0)).file,router.resolve(s("1","Parking",1)).file);
  assert.equal(router.resolve(s("manila_room","Manila",0)).file,
    router.resolve(s("manila_room","Manila",3)).file,"single-image nodes retain their canonical asset");
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

test("WebView actually loads the manifest and renders images behind unchanged overlays",()=>{
  const html=fs.readFileSync(path.resolve(__dirname,"../app/src/main/assets/index.html"),"utf8");
  const native=fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/MainActivity.java"),"utf8");
  assert.ok(html.includes('<script src="level_snapshots/snapshot-mapping.js"></script>'));
  assert.ok(html.includes("s.level.id!=null?s.level.id:s.level.number"));
  assert.ok(native.includes("var scene=a?a.resolve(state):null"));
  assert.ok(native.includes("bg.src=scene?scene.src:"));
  assert.ok(native.includes("box.appendChild(caoMinh)"));
  assert.ok(native.includes("appendEntityOverlay()"));
  assert.ok(native.includes("appendLuciaCombatOverlay()"));
  assert.ok(native.includes("SnapshotSceneProgress.apply(before, state, rolls, actionKind)"));
});
