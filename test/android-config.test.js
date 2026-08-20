import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(path, "utf8");

test("APK delegates AI work to production without embedding provider credentials", () => {
  const gradle = read("android-apk/app/build.gradle");
  const activity = read("android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java");
  const manifest = read("android-apk/app/src/main/AndroidManifest.xml");
  const workflow = read(".github/workflows/build-backroom-apk.yml");
  const packagedSources = `${gradle}\n${activity}`;

  assert.doesNotMatch(packagedSources, /buildConfigField/);
  assert.doesNotMatch(packagedSources, /BuildConfig\.(?:LUNA|OPENAI|GEMINI).*KEY/);
  assert.doesNotMatch(workflow, /secrets\.(?:LUNA|OPENAI|GEMINI)/);
  assert.match(activity, /https:\/\/backroom-rose\.vercel\.app/);
  assert.match(manifest, /android:usesCleartextTraffic="false"/);
  assert.match(manifest, /android:allowBackup="false"/);
  assert.match(workflow, /Verify APK contains no provider credentials/);
  assert.match(workflow, /Backroom-1\.1\.25\.apk/);
});
