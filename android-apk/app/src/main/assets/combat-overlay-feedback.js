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
    var value = String(ref || '').replace(/^file:\/\/\/android_asset\//, '');
    if (/^(file:|https?:|data:)/.test(value)) return value;
    return value.replace(/^\/+/, '');
  }
  function actor(id, name, avatar) {
    var selector = id === 'kai' ? '.snapshot-character' : id === 'lucia' ? '.snapshot-lucia-entity' : null;
    var native = selector && box() && box().querySelector(selector);
    var fallback = id === 'kai' ? 'SRU_AIM.png' : id === 'lucia' ? 'file_000000000dbc8209b74585555f5786dc.png' : id === 'syvial' ? 'Syvial.png' : asset(avatar);
    return { id: String(id), name: String(name || id), src: asset(native && native.getAttribute('src') || fallback) };
  }
  function readView() {
    var s = typeof state !== 'undefined' && state || {};
    var combat = s.combat && s.combat.active === true ? s.combat : null;
    var turn = combat && combat.partyTurn;
    return {
      encounter: combat && String(combat.encounterId || ''),
      combat: combat,
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
  function compactName(id, name) {
    if (String(id) === 'kai') return 'KAI';
    return String(name || id || '').trim().toUpperCase();
  }
  function hpText(currentHp, maxHp) {
    var now = Number(currentHp), max = Number(maxHp);
    if (!Number.isFinite(now) || !Number.isFinite(max) || max <= 0) return '';
    return '[' + Math.max(0, Math.round(now)) + '/' + Math.max(1, Math.round(max)) + ']';
  }
  function removeNameplates(snap) {
    if (!snap) return;
    var party = snap.querySelector('.combat-nameplate-party');
    var entity = snap.querySelector('.combat-nameplate-entity');
    if (party) party.remove();
    if (entity) entity.remove();
  }
  function appendNameplate(snap, side, name, hp) {
    if (!snap || !name || !hp) return null;
    var label = document.createElement('div');
    label.className = 'combat-nameplate combat-nameplate-' + side;
    var nameNode = document.createElement('span');
    nameNode.className = 'combat-nameplate-name';
    nameNode.textContent = name;
    var hpNode = document.createElement('span');
    hpNode.className = 'combat-nameplate-hp';
    hpNode.textContent = hp;
    label.appendChild(nameNode);
    label.appendChild(hpNode);
    snap.appendChild(label);
    var measuredOverflow = Number(label.scrollWidth || 0) > Number(label.clientWidth || 0) && Number(label.clientWidth || 0) > 0;
    if (name.length > 13 || measuredOverflow) label.classList.add('combat-nameplate-stacked');
    return label;
  }
  function renderNameplates(view) {
    var snap = attach();
    if (!snap) return;
    removeNameplates(snap);
    var combat = view && view.combat;
    if (!combat || !view.actor) { delete snap.dataset.combatActive; return; }
    snap.dataset.combatActive = 'true';

    var member = view.members.find(function (item) { return String(item.id) === String(view.actor.id); });
    var partyHp = member && hpText(member.currentHp, member.maxHp);
    if (!partyHp && view.actor.id === 'kai') partyHp = hpText(combat.playerHp, combat.playerMaxHp);
    if (partyHp) appendNameplate(
      snap,
      'party',
      compactName(view.actor.id, member && member.name || view.actor.name),
      partyHp
    );

    var entityHp = hpText(combat.entityHp, combat.entityMaxHp);
    if (entityHp) appendNameplate(
      snap,
      'entity',
      compactName(combat.entityKey, combat.entityName || combat.entityKey || 'Entity'),
      entityHp
    );
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
    return new Promise(function (resolve) {
      function done() {
        animations = animations.filter(function (item) { return item !== animation; });
        resolve();
      }
      animation.onfinish = done;
      animation.oncancel = done;
    });
  }
  function impactMagnitude(damage) {
    var value = Math.max(1, Number(damage) || 1);
    return Math.max(12, Math.min(22, Math.round(9 + Math.log2(value + 1) * 2.2)));
  }
  function hit(node, direction, damage) {
    var magnitude = impactMagnitude(damage);
    return animate(node, [
      { transform: 'translateX(0) scale(1)', filter: 'brightness(1) contrast(1)' },
      { transform: 'translateX(' + (direction * magnitude) + 'px) scale(.985,1.015)', filter: 'brightness(2.15) contrast(1.25)', offset: .18 },
      { transform: 'translateX(' + (-direction * Math.round(magnitude * .42)) + 'px) scale(1.01,.99)', filter: 'brightness(1.25) contrast(1.08)', offset: .48 },
      { transform: 'translateX(' + (direction * Math.round(magnitude * .18)) + 'px) scale(1)', filter: 'brightness(1.05) contrast(1)', offset: .72 },
      { transform: 'translateX(0) scale(1)', filter: 'brightness(1) contrast(1)' }
    ], 245);
  }
  function cameraKick(direction, damage) {
    var snap = box();
    if (!snap) return Promise.resolve();
    var px = Math.max(2, Math.min(5, Math.round(2 + Math.log2(Math.max(1, Number(damage) || 1) + 1) * .42)));
    return animate(snap, [
      { transform: 'translate(0,0)' },
      { transform: 'translate(' + (direction * px) + 'px,' + (-Math.max(1, px - 2)) + 'px)', offset: .24 },
      { transform: 'translate(' + (-direction * Math.max(1, px - 2)) + 'px,1px)', offset: .58 },
      { transform: 'translate(0,0)' }
    ], 150);
  }
  function settle(a) {
    layer.replaceChildren();
    current = a ? { actor: a, node: imageFor(a) } : null;
    if (current) layer.appendChild(current.node);
    var snap = attach();
    if (snap) {
      snap.classList.toggle('party-overlay-active', !!current);
      snap.classList.toggle('party-overlay-lucia', !!current && current.actor.id === 'lucia');
      snap.classList.remove('entity-hit-active');
    }
  }
  async function swap(a, token) {
    if (current && a && current.actor.id === a.id && current.actor.src === a.src) return;
    var outgoing = current, incoming = a ? { actor: a, node: imageFor(a) } : null;
    current = incoming;
    if (incoming) layer.appendChild(incoming.node);
    var snap = attach();
    if (snap) {
      snap.classList.toggle('party-overlay-active', !!incoming || !!outgoing);
      snap.classList.toggle('party-overlay-lucia', !!((incoming && incoming.actor.id === 'lucia') || (outgoing && outgoing.actor.id === 'lucia')));
    }
    await Promise.all([
      outgoing && animate(outgoing.node, [{ opacity: 1, transform: 'translateX(0)' },
        { opacity: 0, transform: 'translateX(10px)' }], 200),
      incoming && animate(incoming.node, [{ opacity: 0, transform: 'translateX(10px)' },
        { opacity: 1, transform: 'translateX(0)' }], 200)
    ]);
    if (token !== generation) return;
    if (outgoing) outgoing.node.remove();
    if (snap) {
      snap.classList.toggle('party-overlay-active', !!incoming);
      snap.classList.toggle('party-overlay-lucia', !!incoming && incoming.actor.id === 'lucia');
    }
  }
  async function present(view, feedback, token, entityImage) {
    var hits = feedback && Array.isArray(feedback.hits) ? feedback.hits : [];
    var entityHit = hits.find(function (h) { return h.targetId === 'entity' && h.damage > 0; });
    if (entityHit && entityImage) {
      var ghost = entityImage.cloneNode(true);
      ghost.className = 'combat-fx-entity';
      ghost.removeAttribute('id');
      layer.appendChild(ghost);
      var snap = attach();
      if (snap) snap.classList.add('entity-hit-active');
      await Promise.all([hit(ghost, -1, entityHit.damage), cameraKick(-1, entityHit.damage)]);
      ghost.remove();
      if (token !== generation) return;
      if (snap) snap.classList.remove('entity-hit-active');
    }
    var partyHits = hits.filter(function (h) { return h.targetId !== 'entity' && h.damage > 0; });
    var target = partyHits.find(function (h) { return current && h.targetId === current.actor.id; }) ||
      partyHits.find(function (h) { return view.actor && h.targetId === view.actor.id; }) || partyHits[0];
    if (target) {
      var member = view.members.find(function (m) { return m.id === target.targetId; });
      var damaged = member && actor(member.id, member.name, member.avatar);
      if (damaged) {
        await swap(damaged, token);
        if (token !== generation) return;
        await Promise.all([hit(current && current.node, 1, target.damage), cameraKick(1, target.damage)]);
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
    renderNameplates(view);
    var fresh = event && typeof event.id === 'string' && !seen.has(event.id);
    var feedback = initialized && fresh && previous && previous.encounter === event.encounterId ? event : null;
    if (fresh) remember(event.id);
    if (feedback && (!Array.isArray(feedback.hits) || !feedback.hits.some(function (h) { return h.damage > 0; }))) feedback = null;
    var changed = !previous || previous.encounter !== view.encounter ||
      (previous.actor && previous.actor.id) !== (view.actor && view.actor.id) ||
      (previous.actor && previous.actor.src) !== (view.actor && view.actor.src);
    if (!changed && !feedback) return;
    generation += 1;
    animations.slice().forEach(function (a) { a.cancel(); });
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
