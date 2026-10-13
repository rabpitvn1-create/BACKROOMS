const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.join(__dirname,'../app/src/main');
const main=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const planner=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/StoryMilestonePlannerPrompt.kt'),'utf8');
const runtime=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/StoryMilestoneRuntime.kt'),'utf8');
const facade=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
const diagnostics=fs.readFileSync(path.join(root,'assets/diagnostic-export.js'),'utf8');

test('SOL plans hostile face-to-face encounters as pending Battle Start, not silent avoidance',()=>{
  assert.match(planner,/combatRequired=true/);
  assert.match(planner,/Đối mặt trực tiếp Entity thù địch/);
  assert.match(planner,/không chỉ nhìn thấy/);
  assert.match(runtime,/if \(event\.combatRequired\)/);
  assert.match(runtime,/CombatRuntime\.prepareEncounter/);
  assert.doesNotMatch(runtime,/narrative\.contains|reply\.contains/);
});
test('Gehihi is explicitly barred from dismissing mandatory combat and must speak for new followers',()=>{
  assert.match(main,/NÓI ít nhất một câu thoại trực tiếp/);
  assert.match(main,/không được tự bỏ đi/);
  assert.match(main,/Không tự quyết định Combat, HP, EXP, Item, Party/);
  assert.match(main,/requireGameCore\(\)\.milestoneDueEvents\(\)/);
});
test('SOL initial and prefetch paths remain deterministic, bounded and instrumented',()=>{
  assert.match(main,/return solText\(prompt, 6144, 0\.2, true\)/);
  assert.match(main,/milestone\.provider_request/);
  assert.match(main,/milestone\.next_provider_request/);
  assert.match(main,/String rawPlan = milestonePlannerText\(prompt\)/);
  assert.match(main,/String raw = milestonePlannerText\(prompt\)/);
  assert.match(main,/milestone\.provider_response/);
});
test('diagnostic export exposes raw Core schedule with combatRequired, without changing gameplay',()=>{
  assert.match(facade,/fun milestoneDiagnosticSchedule\(\): String/);
  assert.match(facade,/active\?\.plan\?\.toJson\(\)/);
  assert.match(main,/@JavascriptInterface public String milestoneDiagnosticSchedule\(\)/);
  assert.match(diagnostics,/result\.milestoneSchedule/);
});
