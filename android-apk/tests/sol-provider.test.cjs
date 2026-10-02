const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../..');
const source = fs.readFileSync(path.join(root,
  'android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
function method(name) {
  const start = source.indexOf('  private String ' + name + '(');
  assert.ok(start >= 0, name + ' must exist');
  return source.slice(start, source.indexOf('\n  private ', start + 1));
}

test('shared flow tries SOL before Gemini rotation and later Haiku', () => {
  const flow = method('generateText');
  assert.match(flow, /SafePresentationView\.narrativeText/);
  assert.match(flow, /try\s*\{\s*return solText\(prompt\);\s*\} catch \(Exception/);
  assert.ok(flow.indexOf('solText(prompt)') < flow.indexOf('geminiText(prompt)'));
  assert.ok(flow.indexOf('geminiText(prompt)') < flow.indexOf('haikuText(prompt)'));
  assert.match(method('geminiText'), /ProviderRetryPolicy\.shouldRotateGeminiKey/);
  assert.match(method('haikuText'), /ProviderRetryPolicy\.shouldRetrySameProvider/);
  assert.match(method('generateNarrationText'), /return generateText\(prompt\)/);
});

test('SOL uses low reasoning with shared OpenAI payload, bearer transport and parser', () => {
  const sol = method('solText');
  assert.ok(sol.includes('BuildConfig.SOL_API_KEY.trim().isEmpty()'));
  assert.ok(sol.includes('openAiBody("vgpt/gpt-6.1-sol", prompt)'));
  assert.ok(sol.includes('.put("reasoning_effort", "low")'));
  assert.ok(sol.includes('postJson("https://api.vilao.ai/v1/chat/completions",'));
  assert.ok(sol.includes('BuildConfig.SOL_API_KEY, "Authorization", body)'));
  assert.ok(sol.includes('openAiResponseText('));
  assert.ok(sol.includes('parseModelJson(output)'));
  assert.doesNotMatch(sol, /for\s*\(|while\s*\(/);
  const payload = source.slice(source.indexOf('  private JSONObject openAiBody('),
    source.indexOf('  private String haikuOpenAiText('));
  assert.ok(payload.includes('.put("model", model)'));
  assert.ok(payload.includes('.put("role", "user").put("content", prompt)'));
  assert.ok(method('haikuOpenAiText').includes('openAiBody(haikuModel(), prompt)'));
  assert.ok(method('haikuOpenAiText').includes('openAiResponseText('));
  assert.doesNotMatch(method('haikuOpenAiText'), /reasoning_effort/);
  assert.ok(method('postJson').includes('authHeader.equals("Authorization") ? "Bearer " + key : key'));
  assert.ok(method('openAiResponseText').includes('rawContent instanceof String'));
  assert.ok(method('openAiResponseText').includes('rawContent instanceof JSONArray'));
});

test('SOL key uses release secret env and Gradle BuildConfig; both CI commands run this check', () => {
  const release = fs.readFileSync(path.join(root, '.github/workflows/release-version.yml'), 'utf8');
  const gradle = fs.readFileSync(path.join(root, 'android-apk/app/build.gradle'), 'utf8');
  assert.ok(release.includes('SOL_API_KEY: $' + '{{ secrets.SOL_API_KEY }}'));
  assert.match(gradle, /buildConfigField "String", "SOL_API_KEY", .*secret\("SOL_API_KEY"\)/);
  for (const workflow of ['release-version.yml', 'gm-core-verify.yml']) {
    assert.match(fs.readFileSync(path.join(root, '.github/workflows', workflow), 'utf8'),
      /node --test .*android-apk\/tests\/sol-provider\.test\.cjs/);
  }
});

test('Gemini batch keeps one physical request and prefetch dispatches none', () => {
  const batch = source.slice(source.indexOf('  private JSONObject geminiBranchBatch('),
    source.indexOf('  private boolean haikuConfigured('));
  assert.equal((batch.match(/postJson\(/g) || []).length, 1);
  assert.match(batch, /key = configured;\s*break;/);
  assert.doesNotMatch(batch, /solText\(|generateText\(|haikuText\(|geminiText\(/);
  const prefetch = source.slice(source.indexOf('  private void prefetchChoices('),
    source.indexOf('  private String worldProposalPrompt('));
  assert.match(prefetch, /invalidatePrefetch\(\)/);
  assert.doesNotMatch(prefetch, /solText\(|generateText\(|geminiBranchBatch\(|postJson\(/);
});
