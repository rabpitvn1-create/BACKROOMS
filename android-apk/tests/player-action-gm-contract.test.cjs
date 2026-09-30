const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(assets,'player-action-ui.js'),'utf8');

test('PLAYER ACTION submits through the same GM turn bridge',()=>{
  assert.match(ui,/Android\.submitTurn\(JSON\.stringify\(state\), text\)/);
  assert.doesNotMatch(ui,/submitEnvironmentAction|backroomEnvironment|state\.story/);
});

test('PLAYER ACTION keeps combat and death locks without scripted-flow gates',()=>{
  assert.match(ui,/function actionLocked\(\)/);
  assert.match(ui,/combatActive\(\) \|\| deathRestartPending\(\) \|\| processing\(\)/);
  assert.doesNotMatch(ui,/storyBootstrap|storyCutaway|storyDecision|returnJourney/i);
});
