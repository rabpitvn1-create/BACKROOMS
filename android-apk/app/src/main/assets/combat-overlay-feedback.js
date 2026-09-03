/* Small, response-driven combat feedback. Never changes game state or delays a command. */
(function () {
  'use strict';
  var layer = document.createElement('div');
  layer.className = 'combat-fx-layer';
  var current = null, previous = null, cachedEntity = null, cachedEncounter = null, scheduled = false;
  var generation = 0, animations = [], seen = new Set(), initialized = false;
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

  function box() { return document.getElementById('snapshot'); }
  function attach() {
    var snap = box();
    if (snap && layer.parentNode !== snap) snap.appendChild(layer);
    return snap;
  }
  function asset(ref) {
    var value = String(ref || '');
    if (/^(file:|https?:|data:)/.test(value)) return value;
    return value.replace(/^\/+/, '');
  }
  function actor(id, name, avatar) {
    var kai = box() && box().querySelector('.snapshot-character');
    return { id: String(id), name: String(name || id), src: id === 'kai' ?
      (kai && kai.getAttribute('src') || 'SRU_AIM.png') : asset(avatar) };
  }
  function readView() {
    var s = typeof state !== 'undefined' && state || {};
    var combat = s.combat && s.combat.active === true ? s.combat : null;
    var turn = combat && combat.partyTurn;
    return {
      encounter: combat && String(combat.encounterId || ''),
      actor: turn && turn.actorId ? actor(turn.actorId, turn.actorName, turn.actorAvatar) : null,
      members: s.partyDetails && s.partyDetails.members || [],
      feedback: s.combatFeedback
    };
  }
  function imageFor(a) {
    var img = document.createElement('img');
    img.className = 'snapshot-party-actor';
    img.dataset.actorId = a.id;
    img.alt = a.name;
    if (a.src) img.src = a.src;
    img.onerror = function () { img.style.visibility = 'hidden'; };
    return img;
  }
  function remember(id) {
    if (!id) return;
    seen.add(id);
    if (seen.size > 64) seen.delete(seen.values().next().value);
  }
  function animate(node, frames, duration) {
    if (!node || reduced.matches || !node.animate || document.hidden) return Promise.resolve();
    var animation = node.animate(frames, { duration: duration, easing: 'ease-out' });
    animations.push(animation);
    // oncancel also settles interrupted transitions on rapid responses or combat exit.
    return new Promise(function (resolve) {
      function done() {
        animations = animations.filter(function (item) { return item !== animation; });
        resolve();
      }
      animation.onfinish = done;
      animation.oncancel = done;
    });
  }
  function hit(node, direction) {
    return animate(node, [
      { transform: 'translateX(0)', filter: 'brightness(1)' },
      { transform: 'translateX(' + (direction * 5) + 'px)', filter: 'brightness(1.7)', offset: .25 },
      { transform: 'translateX(' + (-direction * 2) + 'px)', filter: 'brightness(1)', offset: .65 },
      { transform: 'translateX(0)', filter: 'brightness(1)' }
    ], 160);
  }
  function settle(a) {
    layer.replaceChildren();
    current = a ? { actor: a, node: imageFor(a) } : null;
    if (current) layer.appendChild(current.node);
    var snap = attach();
    if (snap) {
      snap.classList.toggle('party-overlay-active', !!current);
      snap.classList.remove('entity-hit-active');
    }
  }
  async function swap(a, token) {
    if (current && a && current.actor.id === a.id && current.actor.src === a.src) return;
    var outgoing = current, incoming = a ? { actor: a, node: imageFor(a) } : null;
    current = incoming;
    if (incoming) layer.appendChild(incoming.node);
    var snap = attach();
    if (snap) snap.classList.toggle('party-overlay-active', !!incoming || !!outgoing);
    await Promise.all([
      outgoing && animate(outgoing.node, [{ opacity: 1, transform: 'translateX(0)' },
        { opacity: 0, transform: 'translateX(10px)' }], 200),
      incoming && animate(incoming.node, [{ opacity: 0, transform: 'translateX(10px)' },
        { opacity: 1, transform: 'translateX(0)' }], 200)
    ]);
    if (token !== generation) return;
    if (outgoing) outgoing.node.remove();
    if (snap) snap.classList.toggle('party-overlay-active', !!incoming);
  }
  async function present(view, feedback, token, entityImage) {
    var hits = feedback && Array.isArray(feedback.hits) ? feedback.hits : [];
    var entityHit = hits.some(function (h) { return h.targetId === 'entity' && h.damage > 0; });
    if (entityHit && entityImage) {
      var ghost = entityImage.cloneNode(true);
      ghost.className = 'combat-fx-entity';
      ghost.removeAttribute('id');
      layer.appendChild(ghost);
      var snap = attach();
      if (snap) snap.classList.add('entity-hit-active');
      await hit(ghost, -1);
      ghost.remove();
      if (token !== generation) return;
      if (snap) snap.classList.remove('entity-hit-active');
    }
    // Show the actual damaged character, never flash Lucia for damage applied to Kai.
    var partyHits = hits.filter(function (h) { return h.targetId !== 'entity' && h.damage > 0; });
    var target = partyHits.find(function (h) { return current && h.targetId === current.actor.id; }) ||
      partyHits.find(function (h) { return view.actor && h.targetId === view.actor.id; }) || partyHits[0];
    if (target) {
      var member = view.members.find(function (m) { return m.id === target.targetId; });
      var damaged = member && actor(member.id, member.name, member.avatar);
      if (damaged) {
        await swap(damaged, token);
        if (token !== generation) return;
        await hit(current && current.node, 1);
        if (token !== generation) return;
      }
    }
    await swap(view.actor, token);
  }
  function flush() {
    scheduled = false;
    var snap = attach();
    if (!snap) return;
    var view = readView(), event = view.feedback;
    var fresh = event && typeof event.id === 'string' && !seen.has(event.id);
    // Initial/load rendering establishes a baseline; old saved hits must not replay.
    var feedback = initialized && fresh && previous && previous.encounter === event.encounterId ? event : null;
    if (fresh) remember(event.id);
    if (feedback && (!Array.isArray(feedback.hits) || !feedback.hits.some(function (h) { return h.damage > 0; }))) feedback = null;
    var changed = !previous || previous.encounter !== view.encounter ||
      (previous.actor && previous.actor.id) !== (view.actor && view.actor.id) ||
      (previous.actor && previous.actor.src) !== (view.actor && view.actor.src);
    if (!changed && !feedback) return;
    generation += 1;
    animations.slice().forEach(function (a) { a.cancel(); });
    // Finish any interrupted presentation at its prior authoritative actor first.
    if (previous) settle(previous.actor);
    var entityImage = snap.querySelector('.snapshot-entity') ||
      (feedback && cachedEncounter === feedback.encounterId ? cachedEntity : null);
    if (!initialized || (previous && previous.encounter !== view.encounter && !feedback)) {
      settle(view.actor);
    } else {
      var token = generation;
      present(view, feedback, token, entityImage).catch(function () {
        if (token === generation) settle(view.actor);
      });
    }
    initialized = true;
    previous = view;
    if (!view.encounter) { cachedEntity = null; cachedEncounter = null; }
  }
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    window.requestAnimationFrame(flush);
  }
  window.CombatOverlayFeedback = {
    renderActor: schedule,
    beforeSnapshot: function () {
      var snap = box(), entity = snap && snap.querySelector('.snapshot-entity');
      if (entity) { cachedEntity = entity.cloneNode(true); cachedEncounter = previous && previous.encounter; }
      layer.remove();
    },
    afterSnapshot: function () { attach(); schedule(); }
  };
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      generation += 1;
      animations.slice().forEach(function (a) { a.cancel(); });
      if (previous) settle(previous.actor);
    }
  });
})();
