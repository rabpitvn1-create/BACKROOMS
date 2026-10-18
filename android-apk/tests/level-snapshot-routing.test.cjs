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
test("snapshot remains fixed despite turns, MOVE steps, labels and reload",()=>{
  for(const id of ids){
    const first=router.resolve(s(id,"first",0));
    for(const step of [1,2,99,-1,1.5]){
      const state={...s(id,"different room",step),turn:900};
      assert.equal(router.resolve(state).file,first.file,id);
      assert.equal(router.sceneKey(state),router.sceneKey(s(id)),id);
      assert.equal(router.resolve(JSON.parse(JSON.stringify(state))).file,first.file,id);
    }
  }
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
  assert.ok(core.includes('SnapshotSceneProgress.apply(beforeView, continued, visualRolls,'));
  assert.ok(core.includes('if (deviceControl) "DEVICE" else if (sceneKind == "MOVE") "SCENE_MOVE" else "EXECUTE"'));
});

// Exercise the same router used by renderSnapshot, rather than checking prompt text.
test("sublevels retain their own fixed image",()=>{
  assert.equal(router.resolve(s("0.2","room",0)).file,router.resolve(s("0.2","other",99)).file);
  assert.ok(router.filesFor("0.2").includes(router.resolve(s("0.2")).file));
});

test("native WebView renderer attaches the resolved background beneath the character",()=>{
  const vm=require("node:vm");
  const native=fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/MainActivity.java"),"utf8");
  const line=native.split("\n").find(value=>value.includes('"function visualSceneKey()'));
  assert.ok(line,"native renderer source must be available");
  const source=JSON.parse(line.trim().replace(/\s*\+$/, ""));
  const states=ids.map(id=>s(id,"test room",1)).concat(router.environmentIds().map(id=>({
    ...s("0"),scene:{nodeId:"0",environment:{id}}
  })));
  for(const state of states){
    const id=state.scene?state.scene.environment.id:state.level.id;
    const children=[];
    const box={appendChild:child=>children.push(child)};
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
    if(state.scene){
      const background=children[0];
      background.removeAttribute=key=>{delete background[key];};
      background.onerror();
      assert.equal(background.src,undefined,"load failure must not substitute another area's image");
      assert.match(background.alt,/Không tải được/);
    }
  }
});

test("37 exact environment identities resolve only their matching local WebP",()=>{
  const manifest=JSON.parse(fs.readFileSync(path.join(assets,"level0-environments.json")));
  assert.equal(manifest.environments.length,37);
  const kotlin=fs.readFileSync(path.join(__dirname,"../app/src/main/java/com/rabpit/backroom/core/Level0Environments.kt"),"utf8");
  const coreRows=[...kotlin.matchAll(/Environment\("([^"]+)", "([^"]+)", (null|"[^"]+"), "([^"]+)"/g)];
  assert.equal(coreRows.length,37);
  for(const row of coreRows){
    const record=manifest.environments.find(e=>e.id===row[1]);
    assert.ok(record,row[1]);
    assert.equal(record.name,row[2]);
    assert.equal(record.parent,row[3]==="null"?null:JSON.parse(row[3]));
    assert.equal(record.number,row[4]);
  }

  assert.equal(manifest.environments.filter(e=>!e.parent).length,8);
  assert.equal(new Set(manifest.environments.map(e=>e.file)).size,37);
  assert.deepEqual(router.environmentIds().sort(),manifest.environments.map(e=>e.id).sort());
  for(const e of manifest.environments){
    const state={...s("0"),scene:{nodeId:"0",environment:{id:e.id}}};
    const resolved=router.resolve(state);
    assert.equal(resolved.file,e.file,e.id);
    assert.equal(resolved.environmentId,e.id);
    for(const step of [0,1,37,1000]){
      const changed={...state,turn:step,location:"misleading name",flags:{exploration:{snapshotStep:step}}};
      assert.equal(router.resolve(changed).file,e.file);
      assert.equal(router.sceneKey(changed),router.sceneKey(state));
    }
    assert.equal(router.resolve(JSON.parse(JSON.stringify(state))).file,e.file);
    const bytes=fs.readFileSync(path.join(assets,e.file));
    assert.equal(bytes.subarray(0,4).toString(),"RIFF");
    assert.equal(bytes.subarray(8,12).toString(),"WEBP");
    assert.ok(bytes.length>=220000 && bytes.length<=275000,e.file+" about 250kB");
  }
  const a={...s("0"),scene:{nodeId:"0",environment:{id:"phong_moc"}}};
  const b={...a,scene:{nodeId:"0",environment:{id:"phong_chop"}}};
  assert.notEqual(router.resolve(a).file,router.resolve(b).file);
  assert.notEqual(router.sceneKey(a),router.sceneKey(b));
  assert.equal(router.resolve({...s("1"),scene:a.scene}).environmentId,null);
  assert.equal(router.resolve({...s("0"),scene:{nodeId:"0",environment:{id:"../../evil"}}}).environmentId,null);
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
