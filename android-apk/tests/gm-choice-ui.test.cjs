const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(root,'gm-choice-ui.js'),'utf8');

test('GM choices keep the normal turn pipeline with two fixed Explorer actions',()=>{
  assert.match(ui,/function submitExplorerChoice\(entry, choice\)/);
  assert.match(ui,/form\.requestSubmit\(\)/);
  assert.match(ui,/function fixedExplorerChoices\(\)/);
  assert.match(ui,/\{id:'A',text:'Khám phá',action:'Khám phá'\}/);
  assert.match(ui,/\{id:'B',text:'Tìm kiếm',action:'Tìm kiếm'\}/);
  assert.doesNotMatch(ui,/id:'C'/);
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
