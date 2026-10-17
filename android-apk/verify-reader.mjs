import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

const source = fileURLToPath(new URL('./app/src/main/assets/', import.meta.url));
const assets = process.argv[2];
assert.ok(assets, 'Usage: node android-apk/verify-reader.mjs <extracted-assets-directory>');

// Verify the actual APK payload, including the reading surface and styles.
for (const file of ['index.html', 'reader.css', 'ui/ornate-frame-v1.css']) {
  assert.deepEqual(
    fs.readFileSync(path.join(assets, file)),
    fs.readFileSync(path.join(source, file)),
    `Packaged reader differs from source: ${file}`,
  );
}
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
assert.doesNotMatch(html, /<textarea\b|id="action"|id="form"|player-composer\.(js|css)/);
assert.match(html, /<button\b[^>]*id="nextPage"/);
assert.match(html, /id="readerSettings"/);
assert.match(html, /Android.nextReaderPage/);
assert.match(html, /reader.gate.options.forEach/);
for (const match of html.matchAll(/<script>([\s\S]*?)<\/script>/g)) new vm.Script(match[1]);
console.log('Packaged interactive reader verified');
