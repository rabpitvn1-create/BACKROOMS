'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/java/com/rabpit/backroom');
const main=fs.readFileSync(path.join(root,'MainActivity.java'),'utf8');
const canon=fs.readFileSync(path.join(root,'core/LucTramCanon.kt'),'utf8');
const facade=fs.readFileSync(path.join(root,'core/GameCoreFacade.kt'),'utf8');
test('Luc Tram encounter uses Core-authoritative flags, not Milestone scheduling',()=>{
 assert.doesNotMatch(main,/thresholdRoll\("lucTramEncounter"/);
 assert.doesNotMatch(canon,/ENCOUNTER_CHANCE|validEncounterRoll|"encounterChance"/);
 assert.match(canon,/"encounterLevels" to "4-6"/);
 // No Milestone scheduling: the plan validator is gone.
 assert.ok(!fs.existsSync(path.join(root,'core/StoryMilestonePlan.kt')));
 assert.doesNotMatch(facade,/StoryMilestoneRuntime/);
});
test('meeting is committed by Core flag authority without automatic Party recruitment',()=>{
 // Core preserves encounter flags authoritatively; narrator cannot fake them.
 assert.match(facade,/"lucTram"/);
 assert.match(facade,/Narrator\/client JSON cannot spawn characters, reserve encounters or change Party/);
 assert.match(facade,/LUC_TRAM_ID -> "lucTram"/);
 // Encounter roll keys are stripped from narrator-supplied rolls.
 assert.match(facade,/"lucTramEncounter"/);
 // Party recruitment happens only via explicit recruitFollower, not flag commitment.
 const flagBlock=facade.split('encounterFields.forEach')[1].split('commands +=')[0];
 assert.doesNotMatch(flagBlock,/PartyCommand|PartyEngine/);
});
