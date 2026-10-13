const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/java/com/rabpit/backroom');
const main=fs.readFileSync(path.join(root,'MainActivity.java'),'utf8');
const canon=fs.readFileSync(path.join(root,'core/LucTramCanon.kt'),'utf8');
const runtime=fs.readFileSync(path.join(root,'core/StoryMilestoneRuntime.kt'),'utf8');
const plan=fs.readFileSync(path.join(root,'core/StoryMilestonePlan.kt'),'utf8');
test('Luc Tram uses scheduled encounters and level compatibility is checked before plan acceptance',()=>{
 assert.doesNotMatch(main,/thresholdRoll\("lucTramEncounter"/);
 assert.doesNotMatch(canon,/ENCOUNTER_CHANCE|validEncounterRoll|"encounterChance"/);
 assert.match(canon,/"encounterLevels" to "4-6"/);
 assert.match(plan,/validateCharactersBeforeAcceptance\(state, plan, position\.level\)/);
});
test('meeting is committed by the shared dispatcher without automatic Party recruitment',()=>{
 assert.match(runtime,/for \(event in required\)/);
 assert.match(runtime,/"encountered" to "true"/);
 assert.match(runtime,/LUC_TRAM_ID -> "lucTram"/);
 assert.doesNotMatch(runtime,/PartyCommand|PartyEngine/);
});
