'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const assets = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const controls = fs.readFileSync(path.join(assets, 'explorer-controls.js'), 'utf8');
const css = fs.readFileSync(path.join(assets, 'explorer-controls.css'), 'utf8');

test('quest-item tile sits next to Casebook in the same exploration menu', () => {
  assert.match(html, /class="explorer-dossier-tiles"[\s\S]*id="explorerCasebook"[\s\S]*id="explorerQuestItems"/);
  assert.match(css, /\.explorer-dossier-tiles\{display:grid;grid-template-columns:repeat\(2,/);
});
test('quest-item view reads only the separate GM inventory', () => {
  const start = controls.indexOf('function openQuestItems(){');
  const end = controls.indexOf('function openCasebook(){', start);
  assert.ok(start >= 0 && end > start);
  const code = controls.slice(start, end);
  assert.match(code, /state\.gmQuestItems\?\.items/);
  assert.match(code, /status==='consumed'/);
  assert.match(code, /actionInput\.value=/);
  assert.doesNotMatch(code, /Android\.processItemAction|state\.inventory|state\.equipment/);
  assert.doesNotMatch(code, /localStorage\.setItem/);
});
test('snapshot, GM panel and header remain outside the GM item controls', () => {
  assert.match(controls, /window\.backroomTurn|global\.backroomTurn/);
  assert.match(html, /id="gameMenuTitle"/);
  assert.match(html, /id="snapshot"/);
});
