import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

const source = fileURLToPath(new URL('./app/src/main/assets/', import.meta.url));
const assets = process.argv[2];
assert.ok(assets, 'Usage: node android-apk/verify-player-composer.mjs <extracted-assets-directory>');

// Verify the actual APK payload, including the external controller and styles.
for (const file of ['index.html', 'player-composer.js', 'player-composer.css', 'ui/ornate-frame-v1.css']) {
  assert.deepEqual(
    fs.readFileSync(path.join(assets, file)),
    fs.readFileSync(path.join(source, file)),
    `Packaged player composer differs from source: ${file}`,
  );
}
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const form = html.match(/<form\b[^>]*id="form"[^>]*>([\s\S]*?)<\/form>/);
assert.ok(form, 'Inline action form is missing');
assert.match(form[1], /<textarea\b[^>]*id="action"/);
assert.match(form[1], /<button\b[^>]*type="submit"[^>]*id="submit"/);
assert.match(form[1], /class="send-plane-icon"/);
assert.match(form[1], /src="avatars\/CAO_MINH_AVATAR.jpg"/);
assert.match(html, /<link\b[^>]*href="player-composer.css"/);
assert.match(html, /<script\b[^>]*src="player-composer.js"/);
assert.doesNotMatch(html, /exploreActionButton|playerActionModal|playerActionConfirm/);
new vm.Script(fs.readFileSync(path.join(assets, 'player-composer.js'), 'utf8'));
console.log('Packaged inline player composer verified');
