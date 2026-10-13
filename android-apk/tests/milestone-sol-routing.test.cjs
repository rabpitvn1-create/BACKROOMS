const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const main=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const prompt=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/StoryMilestonePlannerPrompt.kt'),'utf8');

test('Milestone initial and next checkpoint use SOL instead of Gemini planner transport',()=>{
  const planner=main.split('private void ensureMilestoneForCurrentCheckpoint(String trustedClientView)')[1]
    .split('private class GameBridge')[0];
  assert.ok(planner,'Milestone bridge must exist');
  assert.equal((planner.match(/solMilestoneText\(prompt\)/g)||[]).length,2);
  assert.doesNotMatch(planner,/geminiTextPolicy\(/);
  assert.match(prompt,/Vai trò: SOL biên kịch Checkpoint/);
});

test('SOL Milestone has a separate 12288 token budget without changing ordinary writer',()=>{
  assert.match(main,/private String solText\(String prompt\) throws Exception \{\s*return solText\(prompt, 2048, 0\.6, false\);/);
  assert.match(main,/private String solMilestoneText\(String prompt\) throws Exception \{\s*return solText\(prompt, 12288, 0\.5, true\);/);
  assert.match(main,/openAiProviderBody\("vgpt\/gpt-6\.1-sol", prompt\)[\s\S]*?\.put\("max_tokens", maxOutputTokens\)[\s\S]*?\.put\("temperature", temperature\)/);
});

test('SOL Milestone parses response envelope, exposes categorized errors, and preserves actionable retries',()=>{
  assert.match(main,/SolMilestoneResponse\.normalize\(response\)/);
  assert.match(main,/milestoneFailureMessage\(error\)/);
  assert.match(main,/\[PLAN_INVALID\]/);
  assert.doesNotMatch(main,/Không thể nạp Milestone\. Kiểm tra kết nối và nhấn Thử lại/);
});
