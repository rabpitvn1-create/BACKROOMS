const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const router = require("../app/src/main/assets/level_snapshots/snapshot-mapping.js");
const core = fs.readFileSync(path.join(__dirname,
  "../app/src/main/java/com/rabpit/backroom/core/Level0SatelliteWorldDefinition.kt"), "utf8");
const registry = fs.readFileSync(path.join(__dirname,
  "../app/src/main/java/com/rabpit/backroom/core/WorldDefinition.kt"), "utf8");
const nodes = {
  "0.1":"01", "0.2":"02", "0.5":"05", "0.7":"07",
  "manila_room":"MN", "the_torment":"TM", "red_rooms":"RR"
};
const groups = [...core.matchAll(/^\s*group\("([^"]+)", "([^"]+)", ([1-4]), "([^"]+)", "([^"]+)", listOf\(\n([\s\S]*?)^\s*\)\)/gm)];
function state(node, id, turn=0) {
  return {level:{id:node,number:0},scene:{nodeId:node,environment:{id,name:"Tên phòng"}},
    location:"giả lập",turn,flags:{exploration:{snapshotStep:turn}}};
}
test("seven catalogs have 4 distinct zones and 4 unique subzones each", () => {
  assert.equal(groups.length, 28);
  assert.match(registry,/\.map\(::Level0SatelliteWorldDefinition\)/);
  const ids = new Set(), descriptions = new Set();
  for (const [node, tag] of Object.entries(nodes)) {
    const own = groups.filter(m=>m[1]===node);
    assert.equal(own.length,4,node);
    assert.deepEqual(own.map(m=>Number(m[3])).sort(),[1,2,3,4]);
    own.forEach(m=>{
      assert.equal(m[2],tag,node);
      assert.ok(m[4].length>4 && m[5].length>65,m[4]);
      assert.ok(!descriptions.has(m[5]),"duplicate main-zone description");
      descriptions.add(m[5]);
      const subzones=[...m[6].matchAll(/^\s*"([^"]+)" to "([^"]+)"/gm)];
      assert.equal(subzones.length,4,m[4]);
      const zoneId=`sat_${tag}_z${m[3]}`;
      assert.ok(!ids.has(zoneId));
      ids.add(zoneId);
      subzones.forEach((sub,i)=>{
        assert.ok(sub[1].length>3 && sub[2].length>65,sub[1]);
        assert.ok(!descriptions.has(sub[2]),"duplicate subzone description");
        descriptions.add(sub[2]);
        const id=zoneId+"_s"+(i+1);
        assert.ok(!ids.has(id));
        ids.add(id);
      });
    });
  }
  assert.equal(ids.size,140);
  assert.equal(descriptions.size,140);
});
test("all 140 satellite identities resolve to fixed previously packaged node-local WebPs", () => {
  let count=0;
  for (const [node, tag] of Object.entries(nodes)) {
    const existing=router.filesFor(node);
    assert.equal(existing.length,3,node);
    for(let zone=1;zone<=4;zone++){
      for(let sub=0;sub<=4;sub++){
        const id=`sat_${tag}_z${zone}`+(sub?"_s"+sub:"");
        const before=router.resolve(state(node,id));
        assert.ok(existing.includes(before.file),id);
        assert.equal(before.environmentId,id);
        assert.equal(before.fallback,false);
        for(const step of [1,5,800]){
          assert.equal(router.resolve(state(node,id,step)).file,before.file);
          assert.equal(router.sceneKey(state(node,id,step)),router.sceneKey(state(node,id)));
        }
        count++;
      }
    }
  }
  assert.equal(count,140);
});
test("node and scene identity cannot select a foreign or arbitrary file", () => {
  const invalid=[
    state("0.2","sat_01_z1"),
    state("0.2","sat_02_z5"),
    state("0.2","sat_02_z1_s5"),
    state("0.2","sat_02_z1_s1/../../secret"),
    {...state("0.2","sat_02_z1"),scene:{nodeId:"0.1",environment:{id:"sat_02_z1"}}}
  ];
  for(const input of invalid){
    const result=router.resolve(input);
    assert.equal(result.environmentId,null);
    assert.equal(result.file,router.filesFor("0.2")[0]);
  }
  assert.equal(router.resolve(state("0.2","sat_02_z1_s1")).nodeId,"0.2");
  assert.equal(router.resolve({level:{id:"0"},scene:{nodeId:"0",environment:{id:"sat_02_z1"}}}).environmentId,null);
});
