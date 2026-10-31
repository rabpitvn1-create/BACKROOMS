/* Snapshot is a layered scene, not an element that may be cleared on every render. */
(function () {
  'use strict';

  const LOCAL_LEVEL_MAX = 6;

  function levelNumber(gameState) {
    const node = String(gameState?.levelTravel?.nodeId || '');
    const match = /^LEVEL\.NODE\.(\d+)$/.exec(node);
    if (match) return Number(match[1]);
    const n = gameState?.level?.number;
    return Number.isInteger(n) ? n : null;
  }

  function background(gameState) {
    // Core selects the precise main Level/area/sublevel artwork.
    const source = String(gameState?.levelBackground || '');
    if (/^https:\/\/res\.cloudinary\.com\/csz2me5b\/image\/upload\//i.test(source)) {
      // The Core source identifies the exact scene, including areas inside a Level.
      // Turn indexing is stable across renders, menu reads and save/load.
      return window.backroomSceneBackgrounds
        ? window.backroomSceneBackgrounds.select(source, gameState.turn) : source;
    }
    const number = levelNumber(gameState);
    if (number !== null && number >= 0 && number <= LOCAL_LEVEL_MAX) {
      return 'level_snapshots/level_' + number + '.webp';
    }
    return '';
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
      node.className = className === 'snapshot-player-overlay'
        ? className + ' snapshot-character' : className;
      node.draggable = false;
      parent.appendChild(node);
    }
    if (node.getAttribute('src') !== src) node.setAttribute('src', src);
    node.alt = alt;
    if (className !== 'snapshot-background-img' && className !== 'snapshot-sword' && window.backroomSpriteLayout) {
      const box = document.getElementById('snapshot');
      const side = className === 'snapshot-entity' ? 'left' : 'right';
      window.backroomSpriteLayout.fit(node, box, side);
      if (!node.dataset.groundingBound) {
        node.dataset.groundingBound = 'true';
        node.addEventListener('load', () => window.backroomSpriteLayout.fit(node, box, side));
      }
    }
  }

  function render(gameState) {
    const box = document.getElementById('snapshot');
    if (!box || !gameState) return;
    const stage = layer(box);
    const scene = background(gameState);
    image(stage, 'snapshot-background-img', scene, 'Bối cảnh Level hiện tại');

    const fighting = gameState.combat && gameState.combat.active === true;
    // Character and Entity artwork is keyed by immutable gameplay IDs, not old APK files.
    const art = window.backroomCloudinary;
    image(stage, 'snapshot-player-overlay',
      art ? art.characterOverlay('cao-minh', fighting) || '' : '',
      'Cao Minh');
    // Demon Sword floats beside Cao Minh; attack stance handled by its controller.
    if (window.backroomDemonSword) window.backroomDemonSword.render(stage, fighting);

    const combatKey = fighting ? String(gameState.combat.entityKey || '') : '';
    const visualKey = fighting ? String(window.backroomCombatEntityVisualKey || '') : '';
    const entityKey = visualKey && art && art.entity(visualKey) ? visualKey : combatKey;
    image(stage, 'snapshot-entity', fighting && art ? art.entity(entityKey) || '' : '',
      String(gameState.combat?.entityName || entityKey));

    let label = stage.querySelector('.snapshot-scene-label');
    if (!label) {
      label = document.createElement('div');
      label.className = 'snapshot-scene-label';
      stage.appendChild(label);
    }
    label.textContent = 'CHƯA CÓ ẢNH NỀN CHO LEVEL NÀY';
    label.hidden = Boolean(scene);
  }

  window.backroomSnapshotStage = {render: render};
  if (typeof state !== 'undefined') render(state);
})();

