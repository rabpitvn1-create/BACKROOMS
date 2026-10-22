'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const core=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
const combat=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt'),'utf8');
test('native turn pipeline has no parallel Entity/Follower/NPC random encounters',()=>{
 assert.doesNotMatch(main,/thresholdRoll\("(?:entityEncounter|survivor|anNhienEncounter|irisReunion|syvialReunion|luciaEncounter|lucTramEncounter)"/);
 assert.doesNotMatch(main,/EntityEncounterPool|selectedEntityKey|forceEntityEncounterFlag|applyModelOperations/);
 assert.ok(!fs.existsSync(path.join(root,'app/src/main/java/com/rabpit/backroom/EntityEncounterPool.java')));
 assert.doesNotMatch(core,/rolledCombatEntity|automaticFollowers|encounterSucceeded|validEncounterRoll/);
});
test('Core authoritatively reserves encounters via CombatRuntime without Milestone',()=>{
 // prepareEncounter reserves a Core encounter; confirmEncounter commits it.
 // Identity never comes from narrator/client Entity keys.
 assert.match(combat,/fun prepareEncounter\(state: GameState, entityKey: String, encounterId: String\): GameState/);
 assert.match(combat,/fun confirmEncounter\(state: GameState, encounterId: String\): GameState/);
 assert.match(combat,/A repeated click is a no-op; identity never comes from narrator\/client Entity keys/);
 // The encounter reservation API no longer accepts a Milestone-specific boss flag.
 assert.doesNotMatch(combat,/fun prepareEncounter\([^\n]*\bboss\s*:/);
 assert.doesNotMatch(combat,/StoryMilestonePlan/);
 // No Milestone system files may remain.
 assert.ok(!fs.existsSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/StoryMilestoneRuntime.kt')));
 assert.ok(!fs.existsSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/StoryMilestonePlan.kt')));
 // Facade has no milestone gates blocking encounters.
 assert.doesNotMatch(core,/"milestone_plan_required"/);
 assert.doesNotMatch(core,/StoryMilestoneRuntime/);
});
test('every packaged Entity is available to the authoritative Registry and image renderer',()=>{
 const rendererKeys=main.match(/var __entityKeys=\[([^\]]+)\]/)[1];
 const keys=[...rendererKeys.matchAll(/'([^']+)'/g)].map(x=>x[1]).sort();
 const assets=fs.readdirSync(path.join(root,'app/src/main/assets/entity')).filter(x=>x.endsWith('.webp')).map(x=>x.slice(0,-5)).sort();
 assert.deepEqual(keys,assets);
 assert.equal(new Set(keys).size,keys.length,'no duplicate renderer Entity keys');
 assert.ok(keys.length >= 22,'do not accidentally lose established Entity species');
 assert.ok(keys.includes('luc_tram_hac_hoa') && keys.includes('diep_minh'));
 for(const key of keys)assert.ok(combat.includes('"'+key+'"'),key+' Registry profile missing');
});
