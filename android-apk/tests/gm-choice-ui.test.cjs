const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(root,'gm-choice-ui.js'),'utf8');

test('GM choices use the normal turn pipeline',()=>{
  assert.match(ui,/function submitExplorerChoice\(entry, choice\)/);
  assert.match(ui,/form\.requestSubmit\(\)/);
  assert.match(ui,/function fallbackExplorerChoices\(\)/);
  assert.match(ui,/\{id:'A',text:'Quan sát kỹ khu vực xung quanh'/);
  assert.match(ui,/\{id:'B',text:'Kiểm tra các lối đi hoặc điểm bất thường gần nhất'/);
  assert.match(ui,/\{id:'C',text:'Tiếp tục khám phá '/);
  assert.match(ui,/choice\.id \|\| String\.fromCharCode\(65 \+ index\)/);
  assert.match(ui,/prefix \? prefix \+ '\. ' : '• '/);
  assert.doesNotMatch(ui,/state\.story|resolveStoryDecision|prepareStoryDecision|returnJourney|attackStoryEntity/);
});

test('composer is locked only by active gameplay constraints',()=>{
  const body=ui.split('function syncComposer()')[1].split('var dicePanel=')[0];
  assert.match(body,/combat/);
  assert.match(body,/deathLocked/);
  assert.doesNotMatch(body,/story|cutaway|pendingStory/i);
});
