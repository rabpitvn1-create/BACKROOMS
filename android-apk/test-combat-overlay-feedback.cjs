const { test } = require('node:test');
const assert=require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// A DOM/animation test double keeps these coordination tests dependency-free.
// Rendering quality is intentionally outside this test's scope.
function fixture() {
  const events = [];
  class Element {
    constructor() { this.children = []; this.dataset = {}; this.style = {}; this.attrs = {}; this.className = ''; this.textContent = ''; }
    get classList() { return {
      toggle: (name, on) => { const names = new Set(this.className.split(' ').filter(Boolean)); on ? names.add(name) : names.delete(name); this.className = [...names].join(' '); },
      add: name => this.classList.toggle(name, true), remove: name => this.classList.toggle(name, false)
    }; }
    appendChild(child) { child.remove(); this.children.push(child); child.parentNode = this; return child; }
    remove() { if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(x => x !== this); this.parentNode = null; }
    replaceChildren() { this.children.slice().forEach(child => child.remove()); }
    querySelector(selector) {
      for (const child of this.children) {
        if (child.className.split(' ').includes(selector.slice(1))) return child;
        const nested = child.querySelector(selector); if (nested) return nested;
      }
      return null;
    }
    getAttribute(key) { return this.attrs[key] || null; }
    removeAttribute(key) { delete this.attrs[key]; }
    cloneNode() { const node = new Element(); node.className = this.className; node.attrs = {...this.attrs}; node.style = {...this.style}; return node; }
    animate(frames, options) {
      events.push({ actor: this.dataset.actorId, kind: this.className, hit: frames.some(f => f.filter), duration: options.duration });
      const animation = { cancel() { clearTimeout(timer); if (animation.oncancel) animation.oncancel(); } };
      const timer = setTimeout(() => { if (animation.onfinish) animation.onfinish(); }, 3);
      return animation;
    }
  }
  const snap = new Element(), media = {matches:false}, listeners = {};
  const context = { console, Promise, Set, document: {
    hidden:false, createElement: () => new Element(), getElementById: id => id === 'snapshot' ? snap : null,
    addEventListener: (event, callback) => { listeners[event] = callback; }
  }, window: { matchMedia: () => media, requestAnimationFrame: callback => setTimeout(callback, 0) } };
  const members = [
    {id:'kai',name:'Kai',avatar:'SRU_AIM.png',currentHp:140,maxHp:140},
    {id:'syvial',name:'Syvial',avatar:'avatars/Syvial_avatar.jpg',currentHp:140,maxHp:140},
    {id:'lucia',name:'Lucia',avatar:'avatars/lucia_avatar.jpg',currentHp:120,maxHp:120}
  ];
  function view(id, packet, encounter = 'encounter-1') {
    context.state = { partyDetails: {members}, combatFeedback: packet,
      combat: id ? {
        active:true,encounterId:encounter,entityKey:'predatory_window',entityName:'Predatory Window',entityHp:476,entityMaxHp:476,
        playerHp:140,playerMaxHp:140,partyTurn:{actorId:id,actorName:id,actorAvatar:members.find(m=>m.id===id).avatar}
      } : null };
  }
  view('kai');
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'app/src/main/assets/combat-overlay-feedback.js'), 'utf8'), context);
  const fx = context.window.CombatOverlayFeedback;
  function redraw() {
    fx.beforeSnapshot(); snap.replaceChildren();
    const kai = new Element(); kai.className = 'snapshot-character'; kai.attrs.src = 'SRU_AIM.png'; snap.appendChild(kai);
    if (context.state.combat) {
      const entity = new Element(); entity.className = 'snapshot-entity'; entity.attrs.src = 'entity/hound.png'; snap.appendChild(entity);
      const lucia = new Element(); lucia.className = 'snapshot-lucia-entity'; lucia.attrs.src = 'file:///android_asset/file_000000000dbc8209b74585555f5786dc.png'; snap.appendChild(lucia);
    }
    fx.afterSnapshot();
  }
  const wait = () => new Promise(resolve => setTimeout(resolve, 45));
  function actor() { return snap.querySelector('.snapshot-party-actor'); }
  let seq = 0;
  function response(id, hits, packet) {
    const next = packet || {id:String(++seq),encounterId:'encounter-1',hits};
    view(id, next); fx.renderActor(); redraw(); return next;
  }
  redraw();
  return {fx, snap, events, media, context, listeners, view, redraw, wait, actor, response};
}

test('Syvial combat overlay uses the dedicated root asset without changing her avatar metadata', async () => {
  const f = fixture(); await f.wait();
  f.response('syvial', []); await f.wait();
  assert.equal(f.actor().dataset.actorId, 'syvial');
  assert.equal(f.actor().src, 'Syvial.png');
  assert.equal(f.context.state.combat.partyTurn.actorAvatar, 'avatars/Syvial_avatar.jpg');
});

test('Entity hit precedes Kai-to-Lucia handoff; native redraw never replays it', async () => {
  const f = fixture(); await f.wait(); assert.equal(f.events.length, 0);
  const packet = f.response('lucia', [{targetId:'entity',damage:10}]); await f.wait();
  assert.equal(f.actor().dataset.actorId, 'lucia');
  assert.equal(f.actor().src, 'file_000000000dbc8209b74585555f5786dc.png');
  assert.ok(f.snap.className.includes('party-overlay-lucia'));
  assert.equal(f.events.filter(e=>e.hit).length, 1);
  assert.equal(f.events[0].kind, 'combat-fx-entity');
  assert.ok(f.events.some(e=>e.actor==='kai'&&!e.hit));
  assert.ok(f.events.some(e=>e.actor==='lucia'&&!e.hit));
  const actor = f.actor(), count = f.events.length;
  f.response('lucia', [], packet); await f.wait();
  assert.equal(f.actor(), actor); assert.equal(f.events.length, count);
});

test('Counterattack flashes Kai, not the outgoing Lucia; empty hits do not flash', async () => {
  const f = fixture(); await f.wait(); f.response('lucia', []); await f.wait(); f.events.length = 0;
  f.response('kai', [{targetId:'kai',damage:4}]); await f.wait();
  assert.deepEqual(f.events.filter(e=>e.hit).map(e=>e.actor), ['kai']);
  assert.ok(!f.snap.className.includes('party-overlay-lucia'));
  f.events.length = 0; f.response('lucia', []); await f.wait();
  assert.equal(f.events.filter(e=>e.hit).length, 0);
});

test('Lethal hits use the previous Entity sprite and clean up after exit', async () => {
  const f = fixture(); await f.wait(); f.response(null, [{targetId:'entity',damage:100}]); await f.wait();
  assert.equal(f.events.filter(e=>e.hit&&e.kind==='combat-fx-entity').length, 1);
  assert.equal(f.actor(), null); assert.equal(f.snap.querySelector('.combat-fx-entity'), null);
  assert.ok(!f.snap.className.includes('party-overlay-active'));
});

test('Reduced motion, restored packets and new encounters never replay stale hits', async () => {
  const f = fixture();
  f.view('kai', {id:'saved',encounterId:'encounter-1',hits:[{targetId:'entity',damage:10}]}); await f.wait();
  assert.equal(f.events.length, 0);
  f.media.matches = true; f.response('lucia', [{targetId:'entity',damage:10}]); await f.wait();
  assert.equal(f.events.length, 0); assert.equal(f.actor().dataset.actorId, 'lucia');
  f.media.matches = false; f.view('kai', {id:'unseen-old',encounterId:'other',hits:[{targetId:'entity',damage:10}]}, 'new');
  f.redraw(); await f.wait(); assert.equal(f.events.length, 0);
});

test('Rapid responses and app backgrounding cancel old animation nodes', async () => {
  const f = fixture(); await f.wait(); f.response('lucia', [{targetId:'entity',damage:10}]);
  await new Promise(resolve=>setTimeout(resolve, 2)); f.response('kai', []); await f.wait();
  assert.equal(f.actor().dataset.actorId, 'kai'); assert.equal(f.snap.querySelector('.combat-fx-entity'), null);
  f.response('lucia', []); await new Promise(resolve=>setTimeout(resolve, 2));
  f.context.document.hidden = true; f.listeners.visibilitychange(); await f.wait();
  assert.equal(f.actor().dataset.actorId, 'lucia');
  assert.equal(f.snap.querySelector('.combat-fx-layer').children.length, 1);
});

test('Combat nameplates keep HP separate and stack long names instead of clipping HP', async () => {
  const f = fixture(); await f.wait();
  const party = f.snap.querySelector('.combat-nameplate-party');
  const entity = f.snap.querySelector('.combat-nameplate-entity');
  assert.ok(party); assert.ok(entity);
  assert.equal(party.querySelector('.combat-nameplate-name').textContent, 'KAI');
  assert.equal(party.querySelector('.combat-nameplate-hp').textContent, '[140/140]');
  assert.equal(entity.querySelector('.combat-nameplate-name').textContent, 'PREDATORY WINDOW');
  assert.equal(entity.querySelector('.combat-nameplate-hp').textContent, '[476/476]');
  assert.ok(entity.className.includes('combat-nameplate-stacked'));
  assert.ok(!party.className.includes('combat-nameplate-stacked'));
});
