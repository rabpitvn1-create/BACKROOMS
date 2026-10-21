const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const main = path.resolve(__dirname, '../app/src/main');
const assets = path.join(main, 'assets');
const java = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const canon = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/NguyenQuocNamCanon.kt'), 'utf8');
const skills = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/CompanionSkillCatalog.kt'), 'utf8');
const spawn = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/EntitySpawnPool.kt'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(assets, 'item-icons/manifest.json'), 'utf8'));

test('Nam alpha original and pale green 512px portrait are distinct packaged WebP assets', () => {
  const whole = fs.readFileSync(path.join(assets, 'NguyenQuocNam.webp'));
  const face = fs.readFileSync(path.join(assets, 'avatars/NguyenQuocNam_avatar.webp'));
  assert.ok(whole.length <= 350000);
  assert.ok(face.length > 500 && face.length < 350000);
  assert.equal(whole.toString('ascii', 0, 4), 'RIFF');
  assert.equal(face.toString('ascii', 0, 4), 'RIFF');
  assert.equal(whole.toString('ascii', 12, 16), 'VP8X');
  assert.equal(face.toString('ascii', 12, 16), 'VP8 ');
  assert.equal(face.readUInt16LE(26) & 0x3fff, 512);
  assert.equal(face.readUInt16LE(28) & 0x3fff, 512);
  assert.notEqual(crypto.createHash('sha256').update(whole).digest('hex'),
    crypto.createHash('sha256').update(face).digest('hex'));
  assert.match(canon, /avatars\/NguyenQuocNam_avatar\.webp/);
});

test('Nam overlay is connected to existing snapshot state and family is not instantiated as NPC', () => {
  assert.match(java, /NGUYEN_QUOC_NAM_FOLLOWER_OVERLAY_R01/);
  assert.match(java, /function appendNguyenQuocNamVisual/);
  assert.match(java, /encounteredCharacters/);
  assert.match(java, /file:\/\/\/android_asset\/NguyenQuocNam\.webp/);
  assert.match(java, /__namBaseRenderSnapshot\(\);appendNguyenQuocNamVisual\(\);/);
  assert.match(canon, /OUTSIDE_BACKROOMS/);
  assert.doesNotMatch(spawn, /NpcEncounter\(.*(?:nguyen_hong_lan|nguyen_thuy_duong)/);
});

test('Nam has five normal skills, one Ultimate and four distinct correctly hashed VP8 icons', () => {
  for (const skill of ['Quan Sát Lâm Sàng', 'Phát Bắn Kiềm Chế',
    'Sơ Cứu Tại Chỗ', 'Giữ Khoảng Cách', 'Đánh Giá Nguy Cơ', 'Lời Hứa Trở Về']) {
    assert.ok(skills.includes(skill), 'Missing skill: ' + skill);
  }
  const list = manifest.items.filter(x => x.item_id && x.item_id.startsWith('nam:'));
  assert.equal(list.length, 4);
  assert.equal(new Set(list.map(x => x.key)).size, 4);
  for (const item of list) {
    const buf = fs.readFileSync(path.join(assets, 'item-icons', item.file));
    assert.equal(buf.length, item.bytes);
    assert.equal(buf.toString('ascii', 12, 16), 'VP8 ');
    assert.equal(crypto.createHash('sha256').update(buf).digest('hex'), item.sha256);
  }
});
