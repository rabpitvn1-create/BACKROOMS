const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const assets = path.join(__dirname, '../app/src/main/assets');
const source = path.join(assets, 'canon/TRAC_LAM_CODEX.md');
const overlay = path.join(assets, 'TracLam.png');
const avatar = path.join(assets, 'avatars/TracLam_avatar.jpg');

test('Trac Lam Canon and original Drive overlay/avatar are packaged', () => {
  const canon = fs.readFileSync(source, 'utf8');
  assert.match(canon, /SRU-03/);
  assert.match(canon, /2099/);
  assert.match(canon, /Level 0\.7/);
  assert.ok(fs.statSync(overlay).size > 100_000);
  assert.ok(fs.statSync(avatar).size > 10_000);
  assert.equal(fs.readFileSync(overlay).subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(fs.readFileSync(avatar).subarray(0, 2).toString('hex'), 'ffd8');
});

test('Trac Lam is registered in authoritative GM knowledge without a second NPC identity', () => {
  const db = JSON.parse(fs.readFileSync(path.join(assets, 'knowledge/knowledge_db.json'), 'utf8'));
  const card = db.records.filter(x => x.id === 'CHAR.TRAC_LAM.AUTHORED');
  assert.equal(card.length, 1);
  assert.match(card[0].text, /M-92 Gemini/);
  assert.match(card[0].text, /AEGIS-K4/);
});
