import test from "node:test";
import assert from "node:assert/strict";
import { cpSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();

test("Android release patch chain keeps system font, hardens save controls and restores Gemini lanes", (t) => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "backroom-android-test-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  const source = path.join(root, "android-apk");
  const target = path.join(temporaryRoot, "android-apk");
  cpSync(source, target, { recursive: true });

  const buildGradle = readFileSync(path.join(source, "app/build.gradle"), "utf8");
  const workflow = readFileSync(path.join(root, ".github/workflows/build-backroom-apk.yml"), "utf8");
  assert.match(buildGradle, /versionCode 34/);
  assert.match(buildGradle, /versionName '1\.1\.32'/);
  assert.match(workflow, /Backroom-1\.1\.32\.apk/);
  assert.match(workflow, /RELEASE_NOTES_1\.1\.32\.txt/);
  assert.match(workflow, /patch-save-controls-final\.py/);
  assert.doesNotMatch(workflow, /patch-space-habitat-font\.py/);

  for (let slot = 1; slot <= 5; slot += 1) {
    assert.match(buildGradle, new RegExp(`GEMINI_API_KEY_${slot}`));
    assert.match(workflow, new RegExp(`secrets\\.GEMINI_API_KEY_${slot}`));
  }
  assert.match(workflow, /patch-gameplay-parity-final\.py/);
  assert.match(workflow, /patch-final-authority-hardening\.py/);
  assert.match(workflow, /patch-rejected-op-repair-final\.py/);
  assert.match(workflow, /patch-provider-deadline-final\.py/);
  assert.match(workflow, /if: github\.event_name == 'push' && github\.ref == 'refs\/heads\/main'/);
  assert.match(workflow, /git push origin HEAD:main/);
  assert.match(workflow, /if: github\.ref != 'refs\/heads\/main'/);
  assert.doesNotMatch(buildGradle + workflow, /SNAPSHOT_API_KEY/);

  for (let level = 0; level <= 6; level += 1) {
    const snapshotAsset = readFileSync(path.join(source, `app/src/main/assets/level_snapshots/level_${level}.webp`));
    assert.equal(snapshotAsset.subarray(0, 4).toString("ascii"), "RIFF");
    assert.equal(snapshotAsset.subarray(8, 12).toString("ascii"), "WEBP");
  }

  const scripts = [
    "patch-provider-status.py",
    "patch-luna-text.py",
    "patch-level-snapshot-backgrounds.py",
    "patch-snapshot-fallback.py",
    "patch-kai-hd-continuous.py",
    "patch-kai-png-preferred.py",
    "patch-kai-codex.py",
    "patch-r06-source-marker.py",
    "patch-drive-canon-gameplay.py",
    "patch-inventory-persistence.py",
    "patch-ai-orchestrator.py",
    "patch-state-op-hardening.py",
    "patch-gemini-health-pool.py",
    "patch-conditional-audit.py",
    "patch-audit-validated-risk.py",
    "patch-gameplay-parity-final.py",
    "patch-final-authority-hardening.py",
    "patch-rejected-op-repair-final.py",
    "patch-provider-deadline-final.py",
    "patch-java-compile-hardening.py",
    "patch-save-controls-final.py",
    "patch-hard-mode-label.py",
    "patch-snapshot-unconfigured.py",
  ];

  for (const script of scripts) {
    const result = spawnSync("python3", [path.join(target, script)], { cwd: target, encoding: "utf8" });
    assert.equal(result.status, 0, `${script} failed:\n${result.stdout}\n${result.stderr}`);
  }

  const main = readFileSync(path.join(target, "app/src/main/java/com/rabpit/backroom/MainActivity.java"), "utf8");
  const index = readFileSync(path.join(target, "app/src/main/assets/index.html"), "utf8");

  assert.match(main, /DRIVE_CANON_VERSION/);
  assert.match(main, /import java\.security\.SecureRandom;/);
  assert.match(main, /SecureRandom GAME_RNG/);
  assert.match(main, /private String lower\(String value\)/);
  assert.match(main, /private boolean networkFailure\(Exception error\)/);
  assert.match(main, /private String networkFailureMessage\(\)/);
  assert.equal((main.match(/private void mergeObject\(JSONObject target, JSONObject patch\)/g) || []).length, 1);
  assert.equal((main.match(/private void mergeObjectDeep\(JSONObject target, JSONObject patch\)/g) || []).length, 1);
  assert.doesNotMatch(main, /JSONObject\.getNames\(/);

  assert.match(main, /thresholdRoll\("survivor", 10000, 200/);
  assert.match(main, /thresholdRoll\("irisReunion", 1000000, 25/);
  assert.match(main, /thresholdRoll\("syvialReunion", 1000000, 25/);
  assert.match(main, /int\[\] entityThresholds = \{5, 200, 350, 350, 10, 400, 5\}/);
  assert.match(main, /int\[\] lootThresholds = \{35, 120, 100, 150, 180, 100, 45\}/);
  assert.match(main, /rolls\.put\("hazard"/);
  assert.match(main, /rolls\.put\("exitProbe", exitProbe\)/);
  assert.match(main, /rolls\.put\("levelExit", new JSONObject\(exitProbe\.toString\(\)\)/);

  assert.match(main, /compactDriveCanon/);
  assert.match(main, /compactKaiCanon/);
  assert.match(main, /compactStateForPrompt/);
  assert.match(main, /applyModelOperations/);
  assert.match(main, /establishedStructured/);
  assert.match(main, /worldConsequence/);
  assert.match(main, /exitMutation/);
  assert.match(main, /JSONArray proposed/);
  assert.match(main, /rejectedOperationIssuesAndroid/);
  assert.match(main, /state_narrative_mismatch/);
  assert.match(main, /appendIssues\(hardIssues, rejectedOperationIssuesAndroid/);

  assert.match(main, /private String postJsonFast\(/);
  assert.match(main, /setConnectTimeout\(5000\)/);
  assert.match(main, /setReadTimeout\(18000\)/);
  assert.match(main, /thinkingConfig/);
  assert.match(main, /thinkingLevel", "low"/);
  const policyStart = main.indexOf("private String geminiTextPolicy(");
  const policyEnd = main.indexOf("private String geminiText(String prompt)", policyStart);
  const policyBody = main.slice(policyStart, policyEnd);
  assert.doesNotMatch(policyBody, /\.put\("temperature", temperature\)/);
  assert.match(policyBody, /for \(int attempt = 0; attempt < 1; attempt\+\+\)/);
  assert.match(main, /emit\("backroomProvider", "Gemini K" \+ \(lastGeminiWorker \+ 1\)\)/);
  assert.match(main, /emit\("backroomProvider", "Luna fallback"\)/);

  assert.match(main, /private String postJsonLunaFast\(/);
  assert.match(main, /setConnectTimeout\(12000\)/);
  assert.match(main, /setReadTimeout\(12000\)/);
  assert.match(main, /RECENT LOG ONLY/);

  assert.match(index, /body\{margin:0;background:#080a0c;color:#eef1f3;font:15px system-ui,sans-serif\}/);
  assert.doesNotMatch(index, /MBF Space Habitat|data:font\/woff2;base64/);

  assert.match(index, /id="saveButton"/);
  assert.match(index, /id="loadButton"/);
  assert.match(index, /id="newGameButton"/);
  assert.match(index, /id="deleteSaveButton"/);
  assert.match(index, /const SAVE_KEY="backroom-apk-state"/);
  assert.match(index, /const SNAPSHOT_KEY="backroom-apk-snapshot"/);
  assert.match(index, /function savedState\(\)/);
  assert.match(index, /function armDestructive\(/);
  assert.match(index, /không đọc lại được save vừa ghi/);
  assert.match(index, /Đã tải save Turn/);
  assert.match(index, /localStorage\.removeItem\(SAVE_KEY\)/);
  assert.match(index, /localStorage\.removeItem\(SNAPSHOT_KEY\)/);
  assert.doesNotMatch(index, /confirm\(/);

  assert.match(main, /loadUrl\("file:\/\/\/android_asset\/index\.html"\)/);
  assert.match(main, /requestSnapshot/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_0\.webp/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_6\.webp/);
  assert.doesNotMatch(main, /backrooms-wiki\.(?:wikidot|wdfiles)\.com|upload\.wikimedia\.org/);

  for (let slot = 1; slot <= 5; slot += 1) {
    assert.ok(main.indexOf(`BuildConfig.GEMINI_API_KEY_${slot}`) >= 0, `Gemini key ${slot} must be wired into the APK`);
  }

  const generateStart = main.indexOf("private String generateText(String prompt)");
  const generateEnd = main.indexOf("private JSONObject parseModelJson", generateStart);
  const generateBody = main.slice(generateStart, generateEnd);
  assert.ok(generateBody.indexOf('emit("backroomProvider", "Gemini")') >= 0);
  assert.ok(generateBody.indexOf('emit("backroomProvider", "Gemini K"') > generateBody.indexOf('emit("backroomProvider", "Gemini")'));
  assert.ok(generateBody.indexOf('emit("backroomProvider", "Luna fallback")') > generateBody.indexOf('emit("backroomProvider", "Gemini K"'));

  const snapshotStart = main.indexOf("private void requestSnapshotInternal(String stateJson)");
  const snapshotEnd = main.indexOf("private void emit", snapshotStart);
  const snapshotBody = main.slice(snapshotStart, snapshotEnd);
  assert.match(snapshotBody, /Snapshot chưa được cấu hình/);
  assert.doesNotMatch(snapshotBody, /snapshotImage\(|geminiImageModel\(|openAiImageModel\(/);
  assert.match(main, /function requestSnapshot\(\)\{var s=.*Snapshot chưa được cấu hình/);
  assert.match(main, /b\.textContent='Snapshot chưa cấu hình';b\.disabled=true/);
  assert.doesNotMatch(main, /loadUrl\("https?:/);
  assert.match(index, /DRIVE-INTEGRATION-R06/);
  assert.match(index, /continuity:"SEPARATED"/);
  assert.match(index, /function save\(\)/);
  assert.match(index, /function load\(\)/);
});
