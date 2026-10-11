const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.join(__dirname,'../app/src/main');
const java=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const core=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
const canon=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/LucTramCanon.kt'),'utf8');
test('level four encounter chance and guard',()=>{
 assert.match(java,/thresholdRoll\("lucTramEncounter", 10000, 25,/);
 assert.match(java,/physical && level >= 4 && !lucTramAlreadyMet/);
 assert.match(java,/patch.remove\("lucTram"\)/);
 assert.match(java,/rollSuccess\(rolls, "lucTramEncounter"\)/);
 assert.match(canon,/FIRST_ELIGIBLE_LEVEL = 4/);
 assert.match(canon,/value in 1\.\.25/);
});
test('core holds Party authority',()=>{
 assert.match(core,/LucTramCanon\.validEncounterRoll\(pending\.state, authoritativeRolls\)/);
 assert.match(core,/desiredParty\.remove\(LUC_TRAM_ID\)/);
 assert.match(core,/Triple\(LUC_TRAM_ID, "lucTram", "lucTramEncounter"\)/);
});
