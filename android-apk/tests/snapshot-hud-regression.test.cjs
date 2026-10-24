const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const assets = path.join(__dirname, '../app/src/main/assets');
const snapshotSource = fs.readFileSync(path.join(assets, 'snapshot-stage.js'), 'utf8');
const menuSource = fs.readFileSync(path.join(assets, 'mobile-hud.js'), 'utf8');

function element() {
  const node = {
    nodeType: 1, className: '', attrs: {}, children: [], style: {}, hidden: false,
    textContent: '', listeners: {}, parentNode: null,
    get childNodes() { return this.children; },
    get firstChild() { return this.children[0] || null; },
    appendChild(child) {
      if (child.parentNode) child.remove();
      this.children.push(child); child.parentNode = this; return child;
    },
    insertBefore(child, ref) {
      if (child.parentNode) child.remove();
      const i = ref ? this.children.indexOf(ref) : -1;
      this.children.splice(i < 0 ? this.children.length : i, 0, child);
      child.parentNode = this; return child;
    },
    remove() {
      if (this.parentNode) this.parentNode.children =
        this.parentNode.children.filter(value => value !== this);
      this.parentNode = null;
    },
    setAttribute(k, v) { this.attrs[k] = String(v); },
    getAttribute(k) { return this.attrs[k] ?? null; },
    removeAttribute(k) { delete this.attrs[k]; },
    addEventListener(event, callback) { this.listeners[event] = callback; },
    focus() { this.focused = true; },
    querySelector(selector) {
      const cls = selector.startsWith('.') ? selector.slice(1) : '';
      for (const child of this.children) {
        if (cls && child.className?.split(' ').includes(cls)) return child;
        const nested = child.querySelector?.(selector);
        if (nested) return nested;
      }
      return null;
    }
  };
  return node;
}

function snapshotBoot(initial) {
  const box = element();
  const originalText = {nodeType: 3, textContent: 'Original placeholder',
    parentNode: null, remove() {
      this.parentNode.children = this.parentNode.children.filter(x => x !== this);
      this.parentNode = null;
    }};
  box.appendChild(originalText);
  const state = initial || {turn: 1, levelTravel: {nodeId: 'LEVEL.NODE.2'}, level: {number: 2}};
  const scope = {window: {}, state, document: {
    getElementById: id => id === 'snapshot' ? box : null,
    createElement: element
  }};
  vm.runInNewContext(snapshotSource, scope);
  return {box, state, render: scope.window.backroomSnapshotStage.render};
}

test('Snapshot opens with bundled Level background and Cao Minh overlay without Gemini', () => {
  const {box} = snapshotBoot();
  const stage = box.querySelector('.snapshot-stage');
  assert.ok(stage);
  assert.equal(stage.querySelector('.snapshot-background-img').getAttribute('src'),
    'level_snapshots/level_2.webp');
  assert.equal(stage.querySelector('.snapshot-player-overlay').getAttribute('src'),
    'CAO_MINH_OVERLAY_EXPLORER_IDLE.png');
  assert.equal(stage.querySelector('.snapshot-scene-label').hidden, true,
    'a bundled background must not display a missing-image label');
  assert.equal(box.childNodes.some(x => x.nodeType === 3), false);
});

test('Repeated Snapshot renders preserve follower layers and combat effect DOM', () => {
  const {box, state, render} = snapshotBoot();
  const follower = element(); follower.className = 'snapshot-named-follower';
  const hit = element(); hit.className = 'combat-float';
  box.appendChild(follower); box.appendChild(hit);
  const stage = box.querySelector('.snapshot-stage');
  for (let i = 0; i < 8; i++) render(state, null);
  assert.equal(box.querySelector('.snapshot-stage'), stage);
  assert.equal(box.querySelector('.snapshot-named-follower'), follower);
  assert.equal(box.querySelector('.combat-float'), hit);
  assert.equal(box.children.filter(x => x.className === 'snapshot-stage').length, 1);
});

test('Combat swaps Cao Minh overlay, displays confirmed Entity, then cleans it up', () => {
  const {box, state, render} = snapshotBoot();
  state.combat = {active: true, entityKey: 'smiler', entityName: 'Smiler'};
  render(state, null);
  const stage = box.querySelector('.snapshot-stage');
  assert.equal(stage.querySelector('.snapshot-player-overlay').getAttribute('src'),
    'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png');
  assert.equal(stage.querySelector('.snapshot-entity').getAttribute('src'), 'entity/smiler.webp');
  state.combat = null;
  render(state, null);
  assert.equal(stage.querySelector('.snapshot-entity'), null);
  assert.equal(stage.querySelector('.snapshot-player-overlay').getAttribute('src'),
    'CAO_MINH_OVERLAY_EXPLORER_IDLE.png');
});

test('Generated image payloads are ignored; only curated fixed backgrounds are rendered', () => {
  const {box, state, render} = snapshotBoot();
  const stage = box.querySelector('.snapshot-stage');
  render(state, {turn: 1, dataUri: 'data:image/png;base64,aGVsbG8='});
  assert.equal(stage.querySelector('.snapshot-background-img').getAttribute('src'),
    'level_snapshots/level_2.webp');
  assert.equal(stage.querySelector('.snapshot-scene-label').hidden, true);
  const other = {...state, levelTravel: {nodeId: 'LEVEL.NODE.9'},
    level: {number: 9}, levelBackground: 'data:image/png;base64,Zm9yZ2Vk'};
  render(other, {turn: 1, dataUri: 'data:image/png;base64,aGVsbG8='});
  assert.equal(stage.querySelector('.snapshot-background-img'), null,
    'an injected data URI must never become the backdrop');
  assert.equal(stage.querySelector('.snapshot-scene-label').hidden, false);
  other.levelBackground = 'https://res.cloudinary.com/csz2me5b/image/upload/curated/level_9.webp';
  render(other);
  assert.equal(stage.querySelector('.snapshot-background-img').getAttribute('src'),
    other.levelBackground, 'curated static art remains available for unbundled Levels');
});

test('Mobile HUD remains open for Party detail and closes with Escape or explicit close', () => {
  const nodes = Object.fromEntries(['mobileMenuButton', 'sidePanel', 'mobileMenuClose',
    'mobileMenuBackdrop'].map(id => [id, element()]));
  nodes.mobileMenuBackdrop.hidden = true;
  const set = new Set();
  const body = {classList: {
    add: key => set.add(key), remove: key => set.delete(key), contains: key => set.has(key)
  }};
  const document = {body, listeners: {},
    getElementById: id => nodes[id],
    addEventListener(name, cb) {this.listeners[name] = cb;}};
  const window = {};
  vm.runInNewContext(menuSource, {window, document});
  nodes.mobileMenuButton.listeners.click();
  assert.equal(nodes.mobileMenuBackdrop.hidden, false);
  assert.equal(nodes.mobileMenuButton.getAttribute('aria-expanded'), 'true');
  assert.equal(set.has('mobile-menu-open'), true);
  document.listeners.keydown({key: 'Escape'});
  assert.equal(set.has('mobile-menu-open'), false);
  assert.equal(nodes.mobileMenuBackdrop.hidden, true);
  nodes.mobileMenuButton.listeners.click();
  assert.equal(set.has('mobile-menu-open'), true,
    'Party detail is a child of the sidebar and must remain visible');
  nodes.mobileMenuClose.listeners.click();
  assert.equal(set.has('mobile-menu-open'), false);
});

test('WebView and HTML use the Snapshot stage and visible recovery/menu controls', () => {
  const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
  const java = fs.readFileSync(path.join(__dirname,
    '../app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
  assert.match(html, /id="mobileMenuButton"/);
  assert.match(html, /id="startupRecovery"/);
  assert.match(html, /src="snapshot-stage\.js"/);
  assert.match(html, /src="mobile-hud\.js"/);
  assert.match(java, /window\.backroomSnapshotStage\.render\(state\)/);
  assert.doesNotMatch(java, /geminiImage\(|requestSnapshotInternal\(|@JavascriptInterface public void requestSnapshot\(/);
  assert.doesNotMatch(java, /backroom-apk-snapshot|Gemini đang tạo snapshot/);
  assert.doesNotMatch(snapshotSource, /dataUri|Snapshot Gemini/);
  assert.doesNotMatch(java, /box\.textContent=''/);
});
