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

  const scripts = [
    "patch-provider-status.py",
    "patch-luna-text.py",
    "patch-level-snapshot-backgrounds.py",
    "patch-snapshot-fallback.py",
    "patch-kai-hd-continuous.py",
    "finalize-kai-overlay.py",
    "patch-kai-codex.py",
    "patch-drive-canon-gameplay.py",
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
  assert.doesNotMatch(main, /loadUrl\("https?:/);
  assert.match(index, /DRIVE-INTEGRATION-R06/);
  assert.match(index, /continuity:"SEPARATED"/);
  assert.match(index, /function save\(\)/);
  assert.match(index, /function load\(\)/);
});
