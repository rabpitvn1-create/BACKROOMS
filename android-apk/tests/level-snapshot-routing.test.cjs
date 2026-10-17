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
  assert.equal(new Set(all).size,all.length,"each background belongs to exactly one canonical node");
  for(const file of all){
    assert.match(file,/\.webp$/);
    assert.ok(fs.statSync(path.join(assets,file)).size>0,file);
  }
});
test("sublevels and named areas resolve dedicated images from their own node",()=>{
  for(const id of ["0.2","1.2","1.5","5.1","6.1","manila_room","the_torment","red_rooms","office_market"]){
    const image=router.resolve(s(id));
    assert.ok(router.filesFor(id).includes(image.file),id);
    assert.equal(image.fallback,false,id);
    assert.equal(image.nodeId,id);
  }
  assert.equal(router.resolve(s("traders_vault")).parent,1);
});
test("every canonical route uses a dedicated background instead of its parent fallback",()=>{
  for(const id of ids){
    const image=router.resolve(s(id));
    assert.equal(image.fallback,false,id);
    assert.ok(router.filesFor(id).includes(image.file),id);
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
    router.resolve(s("manila_room","Manila",router.filesFor("manila_room").length)).file,"the full cycle restores the canonical frame");
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
  const core=fs.readFileSync(path.join(__dirname,"../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"),"utf8");
  assert.ok(core.includes('SnapshotSceneProgress.apply(beforeView, continued, visualRolls, "EXECUTE", actionText)'));
});

// Exercise the same router used by renderSnapshot, rather than checking prompt text.
test("Sublevel 0.2 displays its new background and rotates only on confirmed progress",()=>{
  const files=router.filesFor("0.2");
  assert.ok(files.includes("level_00p2__snapshot_001.webp"));
  assert.ok(files.includes("level_00p2__snapshot_002.webp"));
  const first=router.resolve(s("0.2","renovated room",0));
  const next=router.resolve(s("0.2","renovated room",1));
  assert.notEqual(first.file,next.file);
  assert.equal(first.fallback,false);
  assert.equal(next.src,"file:///android_asset/level_snapshots/"+next.file);
  assert.equal(router.resolve(s("0.2","different prose",0)).file,first.file);
  assert.equal(router.resolve(JSON.parse(JSON.stringify(s("0.2","renovated room",1)))).file,next.file);
  assert.equal(router.resolve(s("0.2","renovated room",files.length)).file,first.file);
  const added=fs.readFileSync(path.join(assets,"level_00p2__snapshot_002.webp"));
  assert.equal(added.subarray(0,4).toString(),"RIFF");
  assert.equal(added.subarray(8,12).toString(),"WEBP");
  assert.ok(added.length<=300000,"new background must fit 300 kB");
});

test("native WebView renderer attaches the resolved background beneath the character",()=>{
  const vm=require("node:vm");
  const native=fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/MainActivity.java"),"utf8");
  const line=native.split("\n").find(value=>value.includes('"function visualSceneKey()'));
  assert.ok(line,"native renderer source must be available");
  const source=JSON.parse(line.trim().replace(/\s*\+$/, ""));
  for(const id of ids){
    const children=[];
    const box={appendChild:child=>children.push(child)};
    const state=s(id,"test room",1);
    const context={window:{BackroomSnapshotAssets:router},state,
      document:{getElementById:key=>key==="snapshot"?box:null,createElement:tag=>({tag})},
      caoMinhOverlaySource:()=>"test-character.png"};
    vm.runInNewContext(source,context);
    context.renderSnapshot();
    assert.equal(children.length,2,id);
    assert.equal(children[0].className,"snapshot-bg");
    assert.equal(children[0].src,router.resolve(state).src,id);
    assert.equal(children[1].className,"snapshot-character");
    assert.equal(children[1].src,"test-character.png");
  }
});

test("every canonical node visits all distinct backgrounds before repeating and survives state restore",()=>{
  for(const id of ids){
    const files=router.filesFor(id);
    const seen=new Set();
    for(let step=0;step<files.length;step++){
      const state=s(id,"scene",step);
      const image=router.resolve(state);
      assert.equal(image.variantCount,files.length,id);
      assert.ok(files.includes(image.file),id);
      assert.equal(seen.has(image.file),false,id+" step "+step);
      seen.add(image.file);
      assert.equal(router.resolve(JSON.parse(JSON.stringify(state))).file,image.file,id);
      assert.equal(router.resolve({...state,turn:100+step}).file,image.file,id);
    }
    assert.equal(seen.size,files.length,id);
    assert.equal(router.resolve(s(id,"scene",files.length)).file,router.resolve(s(id,"scene",0)).file,id);
  }
});

test("completed scenery batches contain three WebP frames with compact new assets",()=>{
  const completed=["0.1","0.2","0.5","0.7","manila_room","the_torment","red_rooms","dullness","epsilon","ls_2","1.2"];
  for(const id of completed){
    const files=router.filesFor(id);
    assert.ok(files.length>=3,id+" requires three dedicated frames");
    for(const file of files){
      const bytes=fs.readFileSync(path.join(assets,file));
      assert.equal(bytes.subarray(0,4).toString(),"RIFF",file);
      assert.equal(bytes.subarray(8,12).toString(),"WEBP",file);
      const newlyAdded=["0.1","0.5","0.7"].includes(id)||!file.endsWith("_001.webp");
      if(newlyAdded)assert.ok(bytes.length<=300000,file+" exceeds 300 kB");
    }
  }
});
