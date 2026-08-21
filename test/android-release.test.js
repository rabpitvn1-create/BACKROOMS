import test from "node:test";
import assert from "node:assert/strict";
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();

test("Android 1.1.38 release chain matches current authoritative inventory contract", (t) => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "backroom-android-test-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  const source = path.join(root, "android-apk");
  const target = path.join(temporaryRoot, "android-apk");
  cpSync(source, target, { recursive: true });

  const buildGradle = readFileSync(path.join(source, "app/build.gradle"), "utf8");
  const workflow = readFileSync(path.join(root, ".github/workflows/build-backroom-apk.yml"), "utf8");
  assert.match(buildGradle, /versionCode 40/);
  assert.match(buildGradle, /versionName '1\.1\.38'/);
  assert.match(workflow, /Backroom-1\.1\.38\.apk/);
  assert.match(workflow, /RELEASE_NOTES_1\.1\.38\.txt/);
  assert.match(workflow, /patch-save-controls-final\.py/);
  assert.match(workflow, /patch-gemini-model-matrix-final\.py/);
  assert.match(workflow, /patch-game-state-core-bridge\.py/);
  assert.match(workflow, /patch-character-inventory-ui\.py/);
  assert.doesNotMatch(workflow, /patch-inventory-pickup-reconcile-final\.py/);

  for (let slot = 1; slot <= 5; slot += 1) {
    assert.match(buildGradle, new RegExp(`GEMINI_API_KEY_${slot}`));
    assert.match(workflow, new RegExp(`secrets\\.GEMINI_API_KEY_${slot}`));
  }
  assert.doesNotMatch(buildGradle + workflow, /SNAPSHOT_API_KEY/);

  for (let level = 0; level <= 6; level += 1) {
    const snapshotAsset = readFileSync(path.join(source, `app/src/main/assets/level_snapshots/level_${level}.webp`));
    assert.equal(snapshotAsset.subarray(0, 4).toString("ascii"), "RIFF");
    assert.equal(snapshotAsset.subarray(8, 12).toString("ascii"), "WEBP");
  }

  const kaiAvatar = path.join(source, "app/src/main/assets/avatars/kai_avatar.png");
  assert.ok(existsSync(kaiAvatar), "Kai avatar must be packaged as a real PNG asset");
  const avatarBytes = readFileSync(kaiAvatar);
  assert.deepEqual([...avatarBytes.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);

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
    "patch-kai-resource-policy-final.py",
    "patch-provider-deadline-final.py",
    "patch-gemini-model-matrix-final.py",
    "patch-java-compile-hardening.py",
    "patch-save-controls-final.py",
    "patch-hard-mode-label.py",
    "patch-snapshot-unconfigured.py",
    "patch-game-state-core-bridge.py",
    "patch-character-inventory-ui.py",
  ];

  for (const script of scripts) {
    const result = spawnSync("python3", [path.join(target, script)], { cwd: target, encoding: "utf8" });
    assert.equal(result.status, 0, `${script} failed:\n${result.stdout}\n${result.stderr}`);
  }

  const main = readFileSync(path.join(target, "app/src/main/java/com/rabpit/backroom/MainActivity.java"), "utf8");
  const index = readFileSync(path.join(target, "app/src/main/assets/index.html"), "utf8");
  const facade = readFileSync(path.join(target, "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"), "utf8");
  const reducer = readFileSync(path.join(target, "app/src/main/java/com/rabpit/backroom/core/StateReducer.kt"), "utf8");
  const profiles = readFileSync(path.join(target, "app/src/main/java/com/rabpit/backroom/core/InventoryPolicy.kt"), "utf8");

  assert.match(main, /GameCoreFacade/);
  assert.match(main, /gameCore\.processRule\(stateJson, action\)/);
  assert.match(main, /gameCore\.processValidatedCandidate\(/);
  assert.match(main, /loadUrl\("file:\/\/\/android_asset\/index\.html"\)/);
  assert.doesNotMatch(main, /loadUrl\("https?:/);

  assert.match(reducer, /player_pickup_unavailable/);
  assert.match(reducer, /restore_narrative_only/);
  assert.match(facade, /\[Warning\]/);
  assert.match(facade, /There is no object available for scanning or multiplying\./);

  assert.match(profiles, /KAI_ID.*9.*999/s);
  assert.match(profiles, /iris.*4.*20/s);
  assert.match(profiles, /syvial.*4.*20/s);
  assert.match(profiles, /2.*2/s);

  assert.doesNotMatch(index, /<div class="card"><h2>Inventory<\/h2>/);
  assert.match(index, /avatars\/kai_avatar\.png/);
  assert.match(index, /character-inventory/);
  assert.match(index, /Inventory/);
  assert.doesNotMatch(index, /data:image\/[^;]+;base64/);
  assert.doesNotMatch(index, /data:font\/woff2;base64/);

  assert.match(index, /id="saveButton"/);
  assert.match(index, /id="loadButton"/);
  assert.match(index, /id="newGameButton"/);
  assert.match(index, /id="deleteSaveButton"/);
});
