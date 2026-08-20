import test from "node:test";
import assert from "node:assert/strict";
import { cpSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();

test("Android release patch chain injects routed canon, authoritative ops and five Gemini keys", (t) => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "backroom-android-test-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  const source = path.join(root, "android-apk");
  const target = path.join(temporaryRoot, "android-apk");
  cpSync(source, target, { recursive: true });

  const buildGradle = readFileSync(path.join(source, "app/build.gradle"), "utf8");
  const workflow = readFileSync(path.join(root, ".github/workflows/build-backroom-apk.yml"), "utf8");
  for (let slot = 1; slot <= 5; slot += 1) {
    assert.match(buildGradle, new RegExp(`GEMINI_API_KEY_${slot}`));
    assert.match(workflow, new RegExp(`secrets\\.GEMINI_API_KEY_${slot}`));
  }
  assert.match(workflow, /patch-ai-orchestrator\.py/);
  assert.match(workflow, /patch-gemini-health-pool\.py/);
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
    "patch-drive-canon-gameplay.py",
    "patch-inventory-persistence.py",
    "patch-ai-orchestrator.py",
    "patch-gemini-health-pool.py",
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
  assert.match(main, /SecureRandom GAME_RNG/);
  assert.match(main, /makeGameplayRolls/);
  assert.match(main, /compactDriveCanon/);
  assert.match(main, /compactKaiCanon/);
  assert.match(main, /compactStateForPrompt/);
  assert.match(main, /applyModelOperations/);
  assert.match(main, /Bạn KHÔNG được trả state hoàn chỉnh/);
  assert.match(main, /RECENT LOG ONLY/);
  assert.match(main, /geminiCooldownUntil/);
  assert.match(main, /geminiLatencyEma/);
  assert.match(main, /chooseGeminiWorker/);
  assert.match(main, /noteGeminiFailure/);
  assert.match(main, /code == 429/);
  assert.match(main, /code == 401 \|\| code == 403/);
  assert.match(main, /loadUrl\("file:\/\/\/android_asset\/index\.html"\)/);
  assert.match(main, /requestSnapshot/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_0\.webp/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_6\.webp/);
  assert.doesNotMatch(main, /backrooms-wiki\.(?:wikidot|wdfiles)\.com|upload\.wikimedia\.org/);

  for (let slot = 1; slot <= 5; slot += 1) {
    const position = main.indexOf(`BuildConfig.GEMINI_API_KEY_${slot}`);
    assert.ok(position >= 0, `Gemini key ${slot} must be wired into the APK`);
  }

  const generateStart = main.indexOf("private String generateText(String prompt)");
  const generateEnd = main.indexOf("private JSONObject parseModelJson", generateStart);
  const generateBody = main.slice(generateStart, generateEnd);
  const geminiProvider = generateBody.indexOf('emit("backroomProvider", "Gemini")');
  const lunaProvider = generateBody.indexOf('emit("backroomProvider", "Luna")');
  assert.ok(geminiProvider >= 0 && lunaProvider > geminiProvider, "Gemini pool must run before Luna");

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
