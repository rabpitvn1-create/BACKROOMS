const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'..','app','src','main','assets');
const ui=fs.readFileSync(path.join(assets,'narrative-v2-ui.js'),'utf8');
const choices=fs.readFileSync(path.join(assets,'gm-choice-ui.js'),'utf8');
const main=fs.readFileSync(path.join(__dirname,'..','app','src','main','java','com','rabpit','backroom','MainActivity.java'),'utf8');

test('Act loading preloads reserved entities and every joined party member',()=>{
  assert.match(ui,/manifest\.entityKeys/);
  assert.match(ui,/manifest\.partyMemberIds/);
  assert.match(ui,/Array\.isArray\(nextState\.party\)/);
  assert.match(ui,/member\.joined!==true/);
  assert.match(ui,/member\.avatar\|\|member\.avatarRef/);
  assert.match(ui,/cao_minh_entity_overlay\.png/);
  assert.match(ui,/lucia_overlay\.png/);
  assert.match(ui,/luctram_overlay\.png/);
  assert.match(ui,/preloadAssets\(next\)\.then/);
  assert.ok(ui.indexOf('preloadAssets(next).then') < ui.indexOf('hideLoading();',ui.indexOf('preloadAssets(next).then')));
});

test('Narrative V2 removes free-form action from the active runtime and exposes Core choices',()=>{
  assert.match(ui,/bar\.hidden=active/);
  assert.match(choices,/state\.narrativeV2\.currentChoices/);
  assert.match(choices,/Android\.submitTurn\(JSON\.stringify\(state\), coreAction\)/);
  assert.match(main,/@JavascriptInterface public void prepareNarrativeAct\(String stateJson\)/);
  assert.match(main,/missionBoardPrompt\(context\)/);
  assert.match(main,/hostileDirectorPrompt\(context, missionProposal\)/);
  assert.match(main,/gameCore\.commitNarrativeEdit/);
});

test('loading edit waits for combat to finish',()=>{
  assert.match(ui,/state&&state\.combat&&state\.combat\.active===true/);
});


test('Act loading exposes real pipeline progress instead of a static technical sentence',()=>{
  assert.match(ui,/role="progressbar"/);
  assert.match(ui,/function setLoadingProgress\(percent,stage\)/);
  assert.match(ui,/window\.backroomNarrativeProgress=function\(json\)/);
  assert.match(ui,/preloadAssets\(next,function\(done,total\)/);
  assert.doesNotMatch(ui,/Đang khóa quá khứ, roll Spawn Budget/);
  for(const percent of [15,35,55,65,80]){
    assert.ok(main.includes('emitNarrativeProgress('+percent+','));
  }
});
