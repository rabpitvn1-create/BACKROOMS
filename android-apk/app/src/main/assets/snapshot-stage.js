/* Snapshot is a layered scene, not an element that may be cleared on every render. */
(function () {
  'use strict';

  const bundledEntities = new Set([
    'biological_pipeline', 'cable_mimic', 'clump', 'deathmoth', 'diep_minh',
    'duller', 'false_puddle', 'hostile_faceling', 'hotel_corpse_lure',
    'hound', 'jane_the_killer', 'jeff_the_killer', 'paintings',
    'predatory_window', 'skin-stealer', 'slenderman', 'smiler',
    'the_beast_of_level_5', 'wretch'
  ]);
  const LOCAL_LEVEL_MAX = 6;

  function levelNumber(gameState) {
    const node = String(gameState?.levelTravel?.nodeId || '');
    const match = /^LEVEL\.NODE\.(\d+)$/.exec(node);
    if (match) return Number(match[1]);
    const n = gameState?.level?.number;
    return Number.isInteger(n) ? n : null;
  }

  function background(gameState, cached) {
    const snapshot = cached && Number(cached.turn) === Number(gameState.turn) && typeof cached.dataUri === 'string' ? cached.dataUri : '';
    if (/^data:image\/(?:png|jpe?g|webp);base64,/i.test(snapshot)) {
      return {src: snapshot, generated: true};
    }
    const number = levelNumber(gameState);
    if (number !== null && number >= 0 && number <= LOCAL_LEVEL_MAX) {
      return {src: 'level_snapshots/level_' + number + '.webp', generated: false};
    }
    const source = String(gameState?.levelBackground || '');
    if (/^https:\/\/res\.cloudinary\.com\/[a-z0-9_-]+\/image\/upload\//i.test(source)) {
      return {src: source, generated: false};
    }
    return {src: '', generated: false};
  }

  function layer(box) {
    let stage = box.querySelector('.snapshot-stage');
    if (!stage) {
      stage = document.createElement('div');
      stage.className = 'snapshot-stage';
      box.insertBefore(stage, box.firstChild);
    }
    // Remove only the original static text. Other overlays and combat effects survive.
    Array.from(box.childNodes).forEach(node => {
      if (node.nodeType === 3 && node.textContent.trim()) node.remove();
    });
    return stage;
  }

  function image(parent, className, src, alt) {
    let node = parent.querySelector('.' + className);
    if (!src) {
      if (node) node.remove();
      return;
    }
    if (!node) {
      node = document.createElement('img');
      node.className = className;
      node.draggable = false;
      parent.appendChild(node);
    }
    if (node.getAttribute('src') !== src) node.setAttribute('src', src);
    node.alt = alt;
  }

  function render(gameState, cached) {
    const box = document.getElementById('snapshot');
    if (!box || !gameState) return;
    const stage = layer(box);
    const scene = background(gameState, cached);
    image(stage, 'snapshot-background-img', scene.src,
      scene.generated ? 'Snapshot Gemini Turn ' + gameState.turn : 'Bối cảnh Level hiện tại');

    const fighting = gameState.combat && gameState.combat.active === true;
    image(stage, 'snapshot-player-overlay',
      fighting ? 'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png' : 'CAO_MINH_OVERLAY_EXPLORER_IDLE.png',
      'Cao Minh');

    const entityKey = fighting ? String(gameState.combat.entityKey || '') : '';
    image(stage, 'snapshot-entity', bundledEntities.has(entityKey) ? 'entity/' + entityKey + '.webp' : '',
      String(gameState.combat?.entityName || entityKey));

    let label = stage.querySelector('.snapshot-scene-label');
    if (!label) {
      label = document.createElement('div');
      label.className = 'snapshot-scene-label';
      stage.appendChild(label);
    }
    label.textContent = scene.generated ? '' :
      scene.src ? 'BỐI CẢNH LEVEL · SNAPSHOT GEMINI CHƯA CÓ' :
        'SNAPSHOT GEMINI CHƯA CÓ · CHƯA XÁC ĐỊNH BỐI CẢNH';
    label.hidden = scene.generated;
  }

  window.backroomSnapshotStage = {render: render};
  if (typeof state !== 'undefined') render(state, null);
})();
