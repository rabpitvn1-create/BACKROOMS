'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const core=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
const runtime=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/StoryMilestoneRuntime.kt'),'utf8');
test('native turn pipeline has no parallel Entity/Follower/NPC random encounters',()=>{
 assert.doesNotMatch(main,/thresholdRoll\("(?:entityEncounter|survivor|anNhienEncounter|irisReunion|syvialReunion|luciaEncounter|lucTramEncounter)"/);
 assert.doesNotMatch(main,/EntityEncounterPool|selectedEntityKey|forceEntityEncounterFlag|applyModelOperations/);
 assert.ok(!fs.existsSync(path.join(root,'app/src/main/java/com/rabpit/backroom/EntityEncounterPool.java')));
 assert.doesNotMatch(core,/rolledCombatEntity|automaticFollowers|encounterSucceeded|validEncounterRoll/);
});
test('Core alone takes Entity identity and combat eligibility from accepted Milestone',()=>{
 assert.match(runtime,/val required = due\(state\)/);
 assert.match(runtime,/if \(event\.combatRequired\)/);
 assert.match(runtime,/CombatRuntime\.prepareEncounter\(working, requireNotNull\(event\.entityKey\)/);
 assert.doesNotMatch(main,/generated\.optJSONArray\("ops"\)|startCombatState\(/);
 assert.match(core,/"milestone_plan_required"/);
});
test('every packaged Entity is available to the authoritative Registry and image renderer',()=>{
 const rendererKeys=main.match(/var __entityKeys=\[([^\]]+)\]/)[1];
 const keys=[...rendererKeys.matchAll(/'([^']+)'/g)].map(x=>x[1]).sort();
 const assets=fs.readdirSync(path.join(root,'app/src/main/assets/entity')).filter(x=>x.endsWith('.webp')).map(x=>x.slice(0,-5)).sort();
 assert.deepEqual(keys,assets);assert.equal(keys.length,22);
 const combat=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt'),'utf8');
 for(const key of keys)assert.ok(combat.includes('"'+key+'"'),key+' Registry profile missing');
});
