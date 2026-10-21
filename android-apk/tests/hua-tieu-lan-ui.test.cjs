const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '../app/src/main');
const assets = path.join(root, 'assets');
const java = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const canon = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/HuaTieuLanCanon.kt'), 'utf8');
const skills = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/CompanionSkillCatalog.kt'), 'utf8');
const spawn = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/EntitySpawnPool.kt'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(assets, 'item-icons/manifest.json'), 'utf8'));

test('Original full-body and cropped face portraits are separate transparent WebP assets', () => {
  const full = fs.readFileSync(path.join(assets, 'HuaTieuLan.webp'));
  const avatar = fs.readFileSync(path.join(assets, 'avatars/HuaTieuLan_avatar.webp'));
  assert.ok(full.length > 1000 && full.length <= 350000);
  assert.ok(avatar.length > 1000 && avatar.length <= 350000);
  assert.equal(full.toString('ascii', 0, 4), 'RIFF');
  assert.equal(avatar.toString('ascii', 0, 4), 'RIFF');
  assert.equal(full.toString('ascii', 12, 16), 'VP8X');
  assert.equal(avatar.toString('ascii', 12, 16), 'VP8X');
  assert.notEqual(crypto.createHash('sha256').update(full).digest('hex'),
    crypto.createHash('sha256').update(avatar).digest('hex'));
  assert.match(canon, /avatars\/HuaTieuLan_avatar\.webp/);
});

test('Hứa Tiểu Lan snapshot overlay respects follower and committed encounter visibility', () => {
  assert.match(java, /HUA_TIEU_LAN_FOLLOWER_OVERLAY_R01/);
  assert.match(java, /function huaTieuLanVisualPresent/);
  assert.match(java, /function appendHuaTieuLanVisual/);
  assert.match(java, /encounteredCharacters/);
  assert.match(java, /file:\/\/\/android_asset\/HuaTieuLan\.webp/);
  assert.match(java, /__tieuLanBaseRenderSnapshot\(\);appendHuaTieuLanVisual\(\);/);
  assert.match(canon, /ALTERNATE_HISTORY_FICTION/);
  assert.match(spawn, /HUA_TIEU_LAN_ID/);
});

test('Five normal skills and Ultimate registered with four unique verified WebP VP8 item icons', () => {
  for (const skill of ['Tĩnh Tâm Ngắm Bắn', 'Một Phát Định Đoạt', 'Bộ Pháp Tà Lụa',
    'Ngắm Qua Hoa Cúc', 'Tài Bắn Phản Xạ', 'Bách Hoa Nhất Tiễn']) {
    assert.ok(skills.includes(skill), 'Missing skill: ' + skill);
  }
  const items = manifest.items.filter(x => x.item_id && x.item_id.startsWith('tieu-lan:'));
  assert.equal(items.length, 4);
  assert.equal(new Set(items.map(x => x.key)).size, 4);
  for (const item of items) {
    const buf = fs.readFileSync(path.join(assets, 'item-icons', item.file));
    assert.equal(buf.length, item.bytes);
    assert.equal(buf.toString('ascii', 12, 16), 'VP8 ');
    assert.equal(crypto.createHash('sha256').update(buf).digest('hex'), item.sha256);
  }
});
