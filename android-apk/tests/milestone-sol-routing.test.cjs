const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const main=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const prompt=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/StoryMilestonePlannerPrompt.kt'),'utf8');
const runtime=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/StoryMilestoneRuntime.kt'),'utf8');
const facade=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');

test('Milestone initial and next checkpoint use the saved Gemini-or-SOL planner choice',()=>{
  const planner=main.split('private void ensureMilestoneForCurrentCheckpoint(String trustedClientView)')[1]
    .split('private class GameBridge')[0];
  assert.ok(planner,'Milestone bridge must exist');
  assert.equal((planner.match(/milestonePlannerText\(prompt\)/g)||[]).length,2);
  assert.match(main,/if \(PLANNER_SOL\.equals\(provider\)\) return solMilestoneText\(prompt\)/);
  assert.match(main,/if \(PLANNER_GEMINI\.equals\(provider\)\) return geminiMilestoneText\(prompt\)/);
  assert.doesNotMatch(planner,/geminiTextPolicy\(/);
  assert.match(prompt,/Vai trò: SOL biên kịch Checkpoint/);
});

test('SOL Milestone has a separate 6144 token budget without changing ordinary writer',()=>{
  assert.match(main,/private String solText\(String prompt\) throws Exception \{\s*return solText\(prompt, 2048, 0\.6, false\);/);
  assert.match(main,/private String solMilestoneText\(String prompt\) throws Exception \{\s*return solText\(prompt, 6144, 0\.2, true\);/);
  assert.match(main,/openAiProviderBody\("vgpt\/gpt-6\.1-sol", prompt\)[\s\S]*?\.put\("max_tokens", maxOutputTokens\)[\s\S]*?\.put\("temperature", temperature\)/);
});

test('SOL Milestone parses response envelope, exposes categorized errors, and preserves actionable retries',()=>{
  assert.match(main,/SolMilestoneResponse\.normalize\(response\)/);
  assert.match(main,/milestoneFailureMessage\(error\)/);
  assert.match(main,/\[PLAN_INVALID\]/);
  assert.doesNotMatch(main,/Không thể nạp Milestone\. Kiểm tra kết nối và nhấn Thử lại/);
});

test('SOL authored events are applied by Core without GM receipts or second AI verifier',()=>{
  assert.match(main,/private String generateText\(String prompt\) throws Exception \{\s*emit\("backroomProvider", "Gehihi"\);\s*return verifiedWriterReply\(gehihiText\(prompt\)\);/);
  assert.match(main,/JSONObject generated = parseModelJson\(generateText\(writerPrompt\(before, action, rolls\)\)\)/);
  assert.match(main,/JSONObject coreCommit = new JSONObject\(requireGameCore\(\)\.processValidatedCandidate\(\s*before\.toString\(\), state\.toString\(\), action, reply\)\)/);
  assert.doesNotMatch(main,/verifyMilestoneNarrative\(|auditsForRisk\(|milestoneEventIds|generated\.getJSONArray\("storyFacts"\)/);
  assert.match(runtime,/val required = due\(state\)/);
  assert.match(runtime,/val updated = working\.copy\(/);
  assert.match(runtime,/fun continuityContext\(state: GameState\)/);
  assert.doesNotMatch(facade,/StoryFactLedger|storyFactContext/);
  assert.doesNotMatch(runtime,/validateEvidence\(|evidenceRequest\(|milestone_receipt_mismatch/);
  assert.match(facade,/StoryMilestoneRuntime\.acceptTurn\(protectedState\)/);
  assert.match(main,/if \(requireGameCore\(\)\.isSpecialRuleAction\(action\)\)/);
  assert.match(main,/JSONObject generated = parseModelJson\(generateText\(writerPrompt\(before, action, rolls\)\)\)/);
  assert.doesNotMatch(main,/if \(dueMilestones\.length\(\) == 0\)/);
  assert.doesNotMatch(facade,/milestoneNarrativeRequest\(/);
});
