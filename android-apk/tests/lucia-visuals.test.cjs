const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/lucia-visuals.js'), 'utf8');

function fixture() {
  const box = {
    children: [], style: {},
    querySelector(selector) {
      if(selector === '.snapshot-lucia') return this.children.find(image => image.className === 'snapshot-lucia');
      const id = selector.match(/data-named-follower="([^"]+)"/);
      return id && this.children.find(image => image.attributes && image.attributes['data-named-follower'] === id[1]);
    },
    querySelectorAll() { return this.children.filter(image => ['snapshot-lucia','snapshot-named-follower'].includes(image.className)); },
    appendChild(image) { this.children.push(image); },
    set textContent(_) { this.children = []; }
  };
  const document = {
    getElementById(id) { return id === 'snapshot' ? box : null; },
    createElement() {
      const image = {style: {}, attributes: {}, setAttribute(k,v){this.attributes[k]=v}};
      image.remove = () => { box.children = box.children.filter(child => child !== image); };
      return image;
    }
  };
  const context = {window: {}, document, localStorage: {getItem() { return null; }}};
  vm.createContext(context);
  vm.runInContext(source, context);
  vm.runInContext(fs.readFileSync(path.join(root, 'app/src/main/assets/named-follower-visuals.js'), 'utf8'), context);
  return {box, context, api: context.window.backroomLuciaVisuals};
}

const active = {id: 'lucia', presence: 'ACTIVE'};
const party = member => ({partyDetails: {members: [member]}});

test('new Lucia portrait applies to party/detail views without mutating state or other portraits', () => {
  const {api} = fixture();
  const old = {...active, avatar: 'avatars/lucia_avatar.jpg'};
  const updated = api.withAvatar(old);
  assert.equal(updated.avatar, 'avatars/LUCIA_LUC_AVATAR.png');
  assert.equal(updated.avatarRef, updated.avatar);
  assert.equal(old.avatar, 'avatars/lucia_avatar.jpg');
  const leader = {id: 'cao-minh', avatar: 'avatars/CAO_MINH_AVATAR.jpg'};
  assert.equal(api.withAvatar(leader), leader);
});

test('overlay requires ACTIVE party membership and removes stale artwork', () => {
  const {box, api} = fixture();
  api.renderOverlay(party(active));
  api.renderOverlay(party(active));
  assert.equal(box.children.length, 1);
  assert.equal(box.children[0].src, 'LUCIA_LUC_OVERLAY.png');
  for (const presence of ['SEPARATED', 'MISSING', 'DEAD', undefined]) {
    api.renderOverlay(party({...active, presence}));
    assert.equal(box.children.length, 0);
    api.renderOverlay(party(active));
  }
  api.renderOverlay({partyDetails: {members: []}, party: [active], flags: {lucia: {present: true}}});
  assert.equal(box.children.length, 0);
  api.renderOverlay({flags: {lucia: {present: true}}});
  assert.equal(box.children.length, 0);
  api.renderOverlay({party: [active]});
  assert.equal(box.children.length, 1);
});

test('Android snapshot redraw restores Lucia after clearing the scene', () => {
  const {box, context} = fixture();
  const java = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
  const line = java.split('\n').find(line => line.includes('function equippedItem(s)'));
  const javascript = JSON.parse(line.trim().replace(/\s*\+$/, ''));
  context.state = party(active);
  vm.runInContext(javascript, context);
  vm.runInContext('renderSnapshot(); renderSnapshot();', context);
  assert.equal(box.children.filter(image => image.className === 'snapshot-lucia').length, 1);
  context.state = {partyDetails: {members: []}};
  vm.runInContext('renderSnapshot()', context);
  assert.equal(box.children.filter(image => image.className === 'snapshot-lucia').length, 0);
});
