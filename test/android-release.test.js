import test from "node:test";
import assert from "node:assert/strict";
import { cpSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();

test("Android release patch chain injects Drive R06 canon and authoritative gameplay", (t) => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "backroom-android-test-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  const source = path.join(root, "android-apk");
  const target = path.join(temporaryRoot, "android-apk");
  cpSync(source, target, { recursive: true });

  const buildGradle = readFileSync(path.join(source, "app/build.gradle"), "utf8");
  const workflow = readFileSync(path.join(root, ".github/workflows/build-backroom-apk.yml"), "utf8");
  assert.match(buildGradle, /GEMINI_API_KEY_1/);
  assert.match(buildGradle, /GEMINI_API_KEY_2/);
  assert.match(workflow, /secrets\.GEMINI_API_KEY_1/);
  assert.match(workflow, /secrets\.GEMINI_API_KEY_2/);
  assert.doesNotMatch(buildGradle + workflow, /SNAPSHOT_API_KEY/);
  for (let level = 0; level <= 6; level += 1) {
    const snapshotAsset = readFileSync(
      path.join(source, `app/src/main/assets/level_snapshots/level_${level}.webp`),
    );
    assert.equal(snapshotAsset.subarray(0, 4).toString("ascii"), "RIFF");
    assert.equal(snapshotAsset.subarray(8, 12).toString("ascii"), "WEBP");
  }

  const scripts = [
    "patch-provider-status.py",
    "patch-luna-text.py",
    "patch-level-snapshot-backgrounds.py",
    "patch-snapshot-fallback.py",
    "patch-kai-hd-continuous.py",
    "finalize-kai-overlay.py",
    "patch-kai-codex.py",
    "patch-drive-canon-gameplay.py",
    "patch-snapshot-unconfigured.py",
  ];

  for (const script of scripts) {
    const result = spawnSync("python3", [path.join(target, script)], {
      cwd: target,
      encoding: "utf8",
    });
    assert.equal(result.status, 0, `${script} failed:\n${result.stdout}\n${result.stderr}`);
  }

  const main = readFileSync(
    path.join(target, "app/src/main/java/com/rabpit/backroom/MainActivity.java"),
    "utf8",
  );
  const index = readFileSync(path.join(target, "app/src/main/assets/index.html"), "utf8");

  assert.match(main, /DRIVE_CANON_VERSION/);
  assert.match(main, /SecureRandom GAME_RNG/);
  assert.match(main, /makeGameplayRolls/);
  assert.match(main, /sanitizedParty/);
  assert.match(main, /if \(!meta\)/);
  assert.match(main, /MadGod success chỉ mở đường\/vị trí khám phá/);
  assert.match(main, /loadUrl\("file:\/\/\/android_asset\/index\.html"\)/);
  assert.match(main, /requestSnapshot/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_0\.webp/);
  assert.match(main, /file:\/\/\/android_asset\/level_snapshots\/level_6\.webp/);
  assert.doesNotMatch(main, /backrooms-wiki\.(?:wikidot|wdfiles)\.com|upload\.wikimedia\.org/);
  const keyOne = main.indexOf("BuildConfig.GEMINI_API_KEY_1");
  const keyTwo = main.indexOf("BuildConfig.GEMINI_API_KEY_2");
  assert.ok(keyOne >= 0 && keyTwo > keyOne, "Gemini keys must be tried in order");

  const generateStart = main.indexOf("private String generateText(String prompt)");
  const generateEnd = main.indexOf("private JSONObject parseModelJson", generateStart);
  const generateBody = main.slice(generateStart, generateEnd);
  const geminiProvider = generateBody.indexOf('emit("backroomProvider", "Gemini")');
  const lunaProvider = generateBody.indexOf('emit("backroomProvider", "Luna")');
  assert.ok(geminiProvider >= 0 && lunaProvider > geminiProvider, "Gemini must run before Luna");

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
