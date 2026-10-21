const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const router = require("../app/src/main/assets/level_snapshots/snapshot-mapping.js");
const assets = path.resolve(__dirname, "../app/src/main/assets/level_snapshots");
const source = fs.readFileSync(path.resolve(__dirname,
  "../app/src/main/java/com/rabpit/backroom/core/Level0AreaWorldDefinition.kt"), "utf8");
const nodes = {DU:"dullness", EP:"epsilon", LS:"ls_2"};
const groups = [...source.matchAll(/zone\("(DU|EP|LS)", ([1-4]), "([^"]+)", "([^"]+)", listOf\(([\s\S]*?)\n\s*\)\)/g)];
const catalog = groups.flatMap(([, tag, zone, name, canon, children]) => {
  const zoneId = `area_${tag}_z${zone}`;
  return [[name, canon], ...[...children.matchAll(/"([^"]+)" to "([^"]+)"/g)].map(m=>[m[1],m[2]])]
    .map(([environmentName, canon], sub)=>({nodeId:nodes[tag], zoneId,
      environmentId:zoneId+(sub?`_s${sub}`:""), environmentName, canon}));
});
const state = (node, id) => ({level:{id:node,number:0},
  scene:{nodeId:node,environment:{id,name:"Untrusted display name"}}});

test("all 60 Core AREA environments have their own verified Drive-sourced WebP", () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(assets,"area-environments.json")));
  assert.equal(groups.length,12);
  assert.equal(catalog.length,60);
  assert.equal(manifest.environments.length,60);
  const files = new Set(), hashes = new Set();
  for (const expected of catalog) {
    const row = manifest.environments.find(e=>e.environmentId===expected.environmentId);
    assert.ok(row,expected.environmentId);
    for (const key of Object.keys(expected)) assert.equal(row[key],expected[key],key);
    assert.ok(!files.has(row.file),"one file per environment"); files.add(row.file);
    const result = router.resolve(state(row.nodeId,row.environmentId));
    assert.equal(result.file,row.file);
    assert.equal(result.environmentId,row.environmentId);
    assert.equal(result.environmentName,row.environmentName);
    assert.equal(result.fallback,false);
    assert.equal(result.hasDedicatedImage,true);
    assert.equal(router.environmentFile(row.environmentId),row.file);
    assert.ok(!router.filesFor(row.nodeId).includes(row.file),"must not use old node-level frames");
    const bytes = fs.readFileSync(path.join(assets,row.file));
    assert.equal(bytes.subarray(0,4).toString(),"RIFF");
    assert.equal(bytes.subarray(8,12).toString(),"WEBP");
    assert.equal(bytes.length,row.bytes);
    assert.ok(bytes.length>0 && bytes.length<=275000,row.file);
    const hash = crypto.createHash("sha256").update(bytes).digest("hex");
    assert.equal(hash,row.sha256);
    assert.ok(!hashes.has(hash),"distinct actual image bytes per environment"); hashes.add(hash);
    assert.match(row.driveUrl,/^https:\/\/drive\.google\.com\/file\/d\//);
    assert.ok(row.width>=1280 && row.height>=720);
    for (const turn of [0,1,80]) {
      const changed = {...state(row.nodeId,row.environmentId),turn,location:"different label"};
      assert.equal(router.resolve(changed).file,row.file);
      assert.equal(router.sceneKey(changed),`${row.nodeId}|${row.environmentId}`);
      assert.equal(router.resolve(JSON.parse(JSON.stringify(changed))).file,row.file);
    }
  }
  for (const node of Object.values(nodes)) {
    assert.deepEqual(router.environmentIds(node).sort(),
      catalog.filter(e=>e.nodeId===node).map(e=>e.environmentId).sort());
  }
  const disk = fs.readdirSync(assets).filter(f=>/^area_00__(dullness|epsilon|ls_2)__zone_/.test(f));
  assert.deepEqual(disk.sort(),[...files].sort(),"no orphaned or missing AREA assets");
});

test("AREA identity cannot leak across nodes or select arbitrary paths", () => {
  for (const row of catalog) {
    for (const node of ["0",...Object.values(nodes)].filter(n=>n!==row.nodeId)) {
      assert.equal(router.resolve(state(node,row.environmentId)).environmentId,null);
    }
    for (const invalid of [row.environmentId+"/../../secret",row.environmentId+"_s9","area_DU_z0"]) {
      assert.equal(router.resolve(state(row.nodeId,invalid)).environmentId,null);
    }
    const mismatch = state(row.nodeId,row.environmentId);
    mismatch.scene.nodeId = "0";
    assert.equal(router.resolve(mismatch).environmentId,null);
  }
});

test("native renderer uses all 60 AREA images and never substitutes another room after load failure", () => {
  const vm = require("node:vm");
  const native = fs.readFileSync(path.resolve(__dirname,"../app/src/main/java/com/rabpit/backroom/MainActivity.java"),"utf8");
  const line = native.split("\n").find(s=>s.includes('"function visualSceneKey()'));
  const renderer = JSON.parse(line.trim().replace(/\s*\+$/,""));
  for (const row of catalog) {
    const children = [];
    const context = {window:{BackroomSnapshotAssets:router},state:state(row.nodeId,row.environmentId),
      document:{getElementById:()=>({appendChild:child=>children.push(child)}),createElement:()=>({})},
      caoMinhOverlaySource:()=>"character.png"};
    vm.runInNewContext(renderer,context);
    context.renderSnapshot();
    assert.equal(children.length,2);
    assert.equal(children[0].src,router.resolve(context.state).src);
    assert.equal(children[1].src,"character.png");
    children[0].removeAttribute = key=>delete children[0][key];
    children[0].onerror();
    assert.equal(children[0].src,undefined);
  }
});
