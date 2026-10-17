import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
const source=fileURLToPath(new URL('./app/src/main/assets/',import.meta.url));
const assets=process.argv[2];
assert.ok(assets,'Usage: verify-explorer-controls.mjs <assets-directory>');
for(const file of ['index.html','explorer-controls.js','explorer-controls.css','ui/ornate-frame-v1.css','ui/backrooms-reference-frame-v2.png'])
  assert.deepEqual(fs.readFileSync(path.join(assets,file)),fs.readFileSync(path.join(source,file)),`Packaged Explorer differs from source: ${file}`);
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
assert.doesNotMatch(html,/<textarea|player-composer[.]/);
assert.match(html,/<input type="hidden" id="action"/);
assert.match(html,/src="explorer-controls.js"/);
assert.match(html,/href="explorer-controls.css"/);
new vm.Script(fs.readFileSync(path.join(assets,'explorer-controls.js'),'utf8'));
console.log('Packaged Explorer controls verified');
