const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(root,'gm-choice-ui.js'),'utf8');

test('GM choices expose one scene-driven default while keeping Core-routed mechanics',()=>{
  assert.match(ui,/function submitExplorerChoice\(entry, choice\)/);
  assert.match(ui,/form\.requestSubmit\(\)/);
  assert.match(ui,/var displayText = String\(choice\.text \|\| choice\.action \|\| ''\)\.trim\(\)/);
  assert.match(ui,/var coreAction = String\(choice\.action \|\| displayText\)\.trim\(\)/);
  assert.match(ui,/state\.__uiDisplayAction = displayText/);
  assert.match(ui,/action\.value = coreAction/);
  assert.match(ui,/delete state\.__uiDisplayAction/);
  assert.match(ui,/function displayedExplorerChoices\(entry\)/);
  assert.match(ui,/Array\.isArray\(entry\.choices\)/);
  assert.match(ui,/generated\.length \? generated\[0\] : fallbackExplorerChoice\(\)/);
  assert.match(ui,/choices\.slice\(0, 1\)/);
  assert.match(ui,/if \(choices\.length !== 1\) return;/);
  assert.doesNotMatch(ui,/function fixedExplorerChoices\(\)/);
  assert.doesNotMatch(ui,/text:'Tìm kiếm'/);
  assert.match(ui,/makeChoiceButton\('', choice\.text \|\| choice\.action \|\| ''/);
  assert.match(ui,/prefix \? prefix \+ '\. ' : '• '/);
  assert.doesNotMatch(ui,/state\.story|resolveStoryDecision|prepareStoryDecision|returnJourney|attackStoryEntity/);
});
test('each Entity death shatters the exact sprite without requiring terminal victory',()=>{const block=ui.slice(ui.indexOf('window.backroomCombatTurn = function'),ui.indexOf('var previousError'));assert.match(block,/entityDeathsThisTurn/);assert.match(block,/window\.backroomSetCombatVisualActor\(combat\.resolvedActorIndex,deathKey\)/);assert.match(block,/window\.backroomShatterEntity\(deathKey\)/);assert.doesNotMatch(block,/combat\.active!==true&&combat\.outcome==='victory'[\s\S]{0,200}backroomShatterEntity/);});
test('composer is locked only by active gameplay constraints',()=>{
  const body=ui.split('function syncComposer()')[1].split('var dicePanel=')[0];
  assert.match(body,/combat/);
  assert.match(body,/deathLocked/);
  assert.doesNotMatch(body,/story|cutaway|pendingStory/i);
});

test('Poker Dice renders six-face ivory cubes with physical roll, hold seal and settle states',()=>{
  assert.match(ui,/function dieRestRotation\(value\)/);
  assert.match(ui,/\.combat-die-cube\{[^}]*transform-style:preserve-3d/);
  assert.match(ui,/var faceDefs=\[\['front',1\],\['back',6\],\['right',3\],\['left',4\],\['top',2\],\['bottom',5\]\]/);
  assert.match(ui,/\.combat-die-face\.front\{transform:translateZ\(var\(--cube-half\)\)\}/);
  assert.match(ui,/\.combat-die\.rolling \.combat-die-cube\{animation:combat-die-cube-tumble \.68s/);
  assert.match(ui,/\.combat-die\.held \.combat-die-hold-ring\{[^}]*repeating-conic-gradient/);
  assert.match(ui,/seal\.textContent='鎖'/);
  assert.match(ui,/\.combat-die\.settling \.combat-die-object\{animation:combat-die-settle \.17s/);
  assert.match(ui,/@media\(prefers-reduced-motion:reduce\)/);
});

test('combat snapshot follows the active party actor on dice-state updates',()=>{
  assert.match(ui,/function syncCombatSnapshotActor\(combat\)/);
  assert.match(ui,/var actorIndex=Number\(combat\.actorIndex\)/);
  assert.match(ui,/window\.backroomSetCombatVisualActor\(actorIndex,entityKey\)/);
  const dice=ui.slice(ui.indexOf('window.backroomCombatDiceState=function'),ui.indexOf('var previousRender',ui.indexOf('window.backroomCombatDiceState=function')));
  assert.match(dice,/syncCombatSnapshotActor\(state\.combat\|\|\{\}\)/);
  const finish=ui.slice(ui.indexOf('function finishCombatAnimation'),ui.indexOf('window.backroomCombatTurn = function'));
  assert.match(finish,/syncCombatSnapshotActor\(state && state\.combat \? state\.combat : \{\}\)/);
});
