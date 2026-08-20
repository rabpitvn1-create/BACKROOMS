import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

const root = process.cwd();
const retiredName = ["ver", "cel"].join("");
const textExtensions = new Set([".gradle", ".html", ".java", ".js", ".json", ".md", ".py", ".txt", ".yaml", ".yml"]);

function collect(target, output = []) {
  const absolute = path.join(root, target);
  if (!existsSync(absolute)) return output;
  if (statSync(absolute).isFile()) {
    if (textExtensions.has(path.extname(absolute))) output.push(absolute);
    return output;
  }
  for (const entry of readdirSync(absolute)) {
    if (["node_modules", ".next", ".git"].includes(entry)) continue;
    collect(path.join(target, entry), output);
  }
  return output;
}

test("retired hosting integration is absent and APK remains standalone", () => {
  const files = [".github", "android-apk", "app", "lib", "test", "README.md", "package.json", "package-lock.json"]
    .flatMap((target) => collect(target));
  const offenders = files.filter((file) => readFileSync(file, "utf8").toLowerCase().includes(retiredName));

  assert.deepEqual(offenders, []);
  assert.equal(existsSync(path.join(root, `${retiredName}.json`)), false);

  const activity = readFileSync(
    path.join(root, "android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java"),
    "utf8",
  );
  assert.match(activity, /file:\/\/\/android_asset\/index\.html/);
});
