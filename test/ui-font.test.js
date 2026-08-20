import test from "node:test";
import assert from "node:assert/strict";
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();

test("Space Habitat is scoped outside chat on web and APK", (t) => {
  const sharedCss = readFileSync(path.join(root, "app/space-habitat.css"), "utf8");
  assert.match(sharedCss, /font-family: "MBF Space Habitat"/);
  assert.match(sharedCss, /body \{\s*font-family: var\(--backroom-ui-font\)/s);
  assert.match(sharedCss, /\.log,\s*\.log \*,\s*\.composer textarea \{\s*font-family: var\(--backroom-chat-font\)/s);

  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "backroom-font-test-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  mkdirSync(path.join(temporaryRoot, "app"), { recursive: true });
  cpSync(path.join(root, "app/space-habitat.css"), path.join(temporaryRoot, "app/space-habitat.css"));
  cpSync(path.join(root, "android-apk"), path.join(temporaryRoot, "android-apk"), { recursive: true });

  const result = spawnSync("python3", [path.join(temporaryRoot, "android-apk/patch-space-habitat-font.py")], {
    cwd: temporaryRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);

  const index = readFileSync(path.join(temporaryRoot, "android-apk/app/src/main/assets/index.html"), "utf8");
  assert.match(index, /data-backroom-space-habitat="1"/);
  assert.match(index, /font-family: "MBF Space Habitat"/);
  assert.match(index, /\.log,\s*\.log \*,\s*\.composer textarea/s);
});
