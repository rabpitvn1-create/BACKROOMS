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

test('foreground and background use health scheduling with bounded source attempts', () => {
  const compiler = fs.readFileSync(path.join(root,
    'android-apk/app/src/main/java/com/rabpit/backroom/core/SceneContextCompiler.java'), 'utf8');
  assert.match(method('narrationPrompt'), /SceneContextCompiler\.compile/);
  assert.match(compiler, /SafePresentationView\.narrativeText/);
  assert.match(method('generateNarrationText'), /return generateScheduledText\(prompt, false, deadlineNanos\)/);
  assert.match(method('generateText'), /return generateScheduledText\(prompt, true, NarrationHttpTransport\.deadlineAfterMillis\(60_000L\)\)/);
  const flow = method('generateScheduledText');
  assert.match(flow, /providerScheduler\.acquire/);
  assert.match(flow, /int limit = background \? 4 : 3/);
  assert.match(flow, /geminiTextOnce\(prompt, keys\[source\]\)/);
  assert.match(flow, /haikuTextOnce\(prompt\)/);
  assert.match(flow, /lunaText\(prompt\)/);
  assert.match(flow, /providerScheduler\.failed/);
  assert.match(flow, /BuildConfig\.LUNA_ENABLED && configured\(BuildConfig\.LUNA_API_KEY\)/);
  assert.doesNotMatch(flow, /currentCoreState|sleepBeforeNextGeminiKey|geminiText\(prompt\)|haikuText\(prompt\)/);
});

test('LUNA skips absent keys and validates JSON using shared OpenAI transport and parser', () => {
  const luna = method('lunaText');
  assert.match(luna, /if \(BuildConfig\.LUNA_API_KEY == null \|\| BuildConfig\.LUNA_API_KEY\.trim\(\)\.isEmpty\(\)\) \{\s*throw new Exception\(/);
  assert.ok(luna.indexOf('LUNA_API_KEY.trim().isEmpty()') < luna.indexOf('postJson('));
  assert.match(luna, /BuildConfig\.LUNA_MODEL == null \? "" : BuildConfig\.LUNA_MODEL\.trim\(\)/);
  assert.match(luna, /model\.isEmpty\(\) \? "gpt-6-luna" : model/);
  assert.match(luna, /BuildConfig\.LUNA_BASE_URL == null \? "" : BuildConfig\.LUNA_BASE_URL\.trim\(\)/);
  assert.match(luna, /base\.isEmpty\(\) \? "https:\/\/api\.apiz\.vn\/v1" : base/);
  assert.match(luna, /openAiResponseText\(postJson\(base \+ "\/chat\/completions",\s*BuildConfig\.LUNA_API_KEY, "Authorization", body\)\)/);
  assert.match(luna, /parseModelJson\(output\);\s*return output;/);
  assert.doesNotMatch(luna, /reasoning_effort|for\s*\(/);
});

test('LUNA config uses shared secret helper and optional release env', () => {
  const release = fs.readFileSync(path.join(root, '.github/workflows/release-version.yml'), 'utf8');
  const gradle = fs.readFileSync(path.join(root, 'android-apk/app/build.gradle'), 'utf8');
  for (const name of ['LUNA_API_KEY', 'LUNA_BASE_URL', 'LUNA_MODEL']) {
    assert.ok(gradle.includes('buildConfigField "String", "' + name + '", "\\"" + secret("' + name + '") + "\\""'));
  }
  assert.ok(release.includes('LUNA_API_KEY: $' + '{{ secrets.LUNA_API_KEY }}'));
  for (const name of ['LUNA_BASE_URL', 'LUNA_MODEL']) {
    assert.ok(release.includes(name + ': $' + '{{ secrets.' + name + ' || vars.' + name + ' }}'));
  }
  assert.doesNotMatch(release, /for name in [^\n]*LUNA/);
  assert.ok(gradle.includes('String.valueOf(System.getenv("LUNA_ENABLED") == "true")'));
  assert.ok(release.includes("LUNA_ENABLED: $" + "{{ vars.LUNA_ENABLED || 'false' }}"));
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

test('Gemini branch helper stays single-request while Explorer prefetch uses the shared background provider chain', () => {
  const batch = source.slice(source.indexOf('  private JSONObject geminiBranchBatch('),
    source.indexOf('  private boolean haikuConfigured('));
  assert.equal((batch.match(/postJson\(/g) || []).length, 1);
  assert.match(batch, /key = configured;\s*break;/);
  assert.doesNotMatch(batch, /solText\(|generateText\(|haikuText\(|geminiText\(/);

  const prefetch = source.slice(source.indexOf('  private void prefetchChoices('),
    source.indexOf('  private String worldProposalPrompt('));
  assert.match(prefetch, /scheduleNarrationFutureRefill\(current\)/);
  assert.doesNotMatch(prefetch, /postJson\(|geminiBranchBatch\(/);

  const refill = source.slice(source.indexOf('  private void scheduleNarrationFutureRefill('),
    source.indexOf('  private void prefetchChoices('));
  assert.match(refill, /narrationFutureIo\.execute/);
  assert.match(refill, /generateText\(prompt\)/);
});

test('debug telemetry separates core prompt provider validation repair and total latency', () => {
  const start = source.indexOf('@JavascriptInterface public void submitTurn(');
  const end = source.indexOf('@JavascriptInterface public void combatRoll(', start);
  const submit = source.slice(start, end);
  for (const marker of ['core=', 'prompt=', 'provider=', 'validation=', 'repair=', 'total=', 'promptCharsInitial=', 'promptCharsRepair=', 'repairCount=']) {
    assert.ok(submit.includes(marker), 'missing timing marker ' + marker);
  }
  assert.ok(submit.includes('providerInitial='));
  assert.ok(submit.includes('providerRepair='));
  assert.match(submit, /BuildConfig\.DEBUG/);
  assert.doesNotMatch(submit, /Log\.[dvwi]\([^\n]*(?:SOL_API_KEY|GEMINI_API_KEY|HAKU_API_KEY|PLAYER ACTION)/);
});


test('release PR checkout matches its merged workflow baseline', () => {
  const release = fs.readFileSync(path.join(root, '.github/workflows/release-version.yml'), 'utf8');
  const checkout = release.slice(release.indexOf('- uses: actions/checkout@v4'), release.indexOf('- name: Audit canon sources'));
  assert.ok(checkout.includes('ref: $' + '{{ github.sha }}'));
  assert.doesNotMatch(checkout, /pull_request\.head\.sha/);
});


test('scene content repair shares the foreground deadline and all providers use bounded transport', () => {
  const start = source.indexOf('@JavascriptInterface public void submitTurn(');
  const turn = source.slice(start, source.indexOf('@JavascriptInterface public void combatRoll(', start));
  assert.equal((turn.match(/final long narrationDeadline =/g) || []).length, 1);
  assert.ok(turn.indexOf('final long narrationDeadline =') < turn.indexOf('NarrationProviderPolicy.present('));
  assert.match(turn, /!rejection\.isEmpty\(\), narrationDeadline/);
  const scheduled = method('generateScheduledText');
  assert.match(scheduled, /deadlineNanos - System\.nanoTime\(\)/);
  assert.match(scheduled, /finally \{\s*providerRequestDeadline\.remove\(\)/);
  for (const name of ['postJson', 'postJsonHaiku']) {
    assert.match(method(name), /NarrationHttpTransport\.post/);
    assert.match(method(name), /requestDeadline\(\)/);
    assert.doesNotMatch(method(name), /setReadTimeout|readLine|disconnect/);
  }
});
