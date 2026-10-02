const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(root,'gm-choice-ui.js'),'utf8');

test('GM choices expose one scene-driven default while keeping Core-routed mechanics',()=>{
  assert.match(ui,/function submitExplorerChoice\(entry, choice\)/);
  assert.match(ui,/form\.requestSubmit\(\)/);
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
test('composer is locked only by active gameplay constraints',()=>{
  const body=ui.split('function syncComposer()')[1].split('var dicePanel=')[0];
  assert.match(body,/combat/);
  assert.match(body,/deathLocked/);
  assert.doesNotMatch(body,/story|cutaway|pendingStory/i);
});
