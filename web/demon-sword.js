/* ============================================================================
 * Huyết Ma Kiếm — Demon Sword controller cho game Backrooms (web).
 *
 * IIFE, không đụng vào code khác. Mọi hàm đều bọc try/catch — KHÔNG BAO GIỜ
 * throw ra ngoài, không được làm crash game.
 *
 * API: render(stage, fighting), attack(skillName, onContact, miss), cancel(), isAttacking().
 * ========================================================================== */
(function () {
  'use strict';
  var S = { attacking: false, epoch: 0 };
  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }
  function nextFrame() {
    return new Promise(function (resolve) {
      try {
        requestAnimationFrame(function () { requestAnimationFrame(resolve); });
      } catch (e) { setTimeout(resolve, 32); }
    });
  }
  function ensureBaseStyle() {
    try {
      if (document.getElementById('demon-sword-base-style')) return;
      var st = document.createElement('style');
      st.id = 'demon-sword-base-style';
      st.textContent =
        '.snapshot-stage .demon-sword{position:absolute;width:auto;' +
        'pointer-events:none;}' +
        '.snapshot-stage .demon-sword[data-sword-state="attack"]{animation:none;}';
      (document.head || document.documentElement).appendChild(st);
    } catch (e) {}
  }
  function getSwordUrl() {
    try {
      var api = window.backroomCloudinary;
      if (api && typeof api.characterSword === 'function') {
        var u = api.characterSword('cao-minh');
        return u ? String(u) : '';
      }
    } catch (e) {}
    return '';
  }
  function clearDrive(sword) {
    try {
      sword.style.transform = '';
      sword.style.transition = '';
      sword.style.zIndex = '';
    } catch (e) {}
  }
  function cleanupFx(stage) {
    try {
      var nodes = stage.querySelectorAll('.sword-slash,.sword-spark');
      Array.prototype.forEach.call(nodes, function (n) {
        try { n.remove(); } catch (e) {}
      });
      stage.classList.remove('shake', 'final', 'sword-hitstop');
      var ent = stage.querySelector('.snapshot-entity');
      if (ent) {
        try { ent.classList.remove('sword-flash', 'final'); } catch (e) {}
      }
    } catch (e) {}
  }
  function render(stage, fighting) {
    try {
      if (!stage || typeof stage.querySelector !== 'function') return;
      ensureBaseStyle();
      var sword = stage.querySelector('.demon-sword');
      if (!sword) {
        sword = document.createElement('img');
        sword.className = 'demon-sword';
        sword.alt = 'Huyết Ma Kiếm';
        try { sword.draggable = false; } catch (e) {}
        stage.appendChild(sword);
      }
      var url = getSwordUrl();
      if (!url) {
        try { sword.style.display = 'none'; } catch (e) {}
        return;
      }
      try {
        if (sword.getAttribute('src') !== url) sword.setAttribute('src', url);
        sword.style.display = '';
      } catch (e) {}
      if (S.attacking) return;
      clearDrive(sword);
      try { sword.dataset.swordState = fighting ? 'shoulder' : 'orbit'; } catch (e) {}
    } catch (e) {}
  }
  function hitstop(stage, ms) {
    try { stage.classList.add('sword-hitstop'); } catch (e) {}
    return sleep(ms).then(function () {
      try { stage.classList.remove('sword-hitstop'); } catch (e) {}
    });
  }
  function spawnSparks(stage, x, y, isFinal) {
    try {
      var count = isFinal ? 7 : 3 + Math.floor(Math.random() * 3);
      for (var i = 0; i < count; i++) {
        var sp = document.createElement('div');
        sp.className = 'sword-spark' + (isFinal ? ' final' : '');
        var ang = Math.random() * Math.PI * 2;
        var dist = 36 + Math.random() * (isFinal ? 110 : 60);
        sp.style.left = x + 'px';
        sp.style.top = y + 'px';
        sp.style.setProperty('--tx', (Math.cos(ang) * dist).toFixed(1) + 'px');
        sp.style.setProperty('--ty', (Math.sin(ang) * dist).toFixed(1) + 'px');
        stage.appendChild(sp);
        (function (node) {
          setTimeout(function () { try { node.remove(); } catch (e) {} }, 600);
        })(sp);
      }
    } catch (e) {}
  }
  function impact(stage, entity, er, str, tcx, tcy, isFinal) {
    try {
      var slash = document.createElement('div');
      slash.className = 'sword-slash' + (isFinal ? ' final' : '');
      if (er && er.width > 0) {
        slash.style.left = (er.left - str.left) + 'px';
        slash.style.top = (er.top - str.top) + 'px';
        slash.style.width = er.width + 'px';
        slash.style.height = er.height + 'px';
      } else {
        var s = Math.min(str.width, str.height) * 0.4;
        slash.style.left = (tcx - s / 2) + 'px';
        slash.style.top = (tcy - s / 2) + 'px';
        slash.style.width = s + 'px';
        slash.style.height = s + 'px';
      }
      stage.appendChild(slash);
      setTimeout(function () { try { slash.remove(); } catch (e) {} }, isFinal ? 650 : 400);
      if (entity) {
        try {
          entity.classList.add('sword-flash');
          if (isFinal) entity.classList.add('final');
          setTimeout(function () {
            try { entity.classList.remove('sword-flash', 'final'); } catch (e) {}
          }, 200);
        } catch (e) {}
      }
      try {
        stage.classList.add('shake');
        if (isFinal) stage.classList.add('final');
        setTimeout(function () {
          try { stage.classList.remove('shake', 'final'); } catch (e) {}
        }, isFinal ? 500 : 320);
      } catch (e) {}
      spawnSparks(stage, tcx, tcy, isFinal);
    } catch (e) {}
  }
  function cancel() {
    S.epoch++;
    S.attacking = false;
    var stage = document.querySelector('.snapshot-stage');
    if (!stage) return;
    cleanupFx(stage);
    Array.prototype.forEach.call(stage.querySelectorAll('.demon-aura'), function (node) { node.remove(); });
    var sword = stage.querySelector('.demon-sword');
    if (sword) {
      sword.classList.remove('sword-charging', 'sword-flying');
      clearDrive(sword);
      sword.dataset.swordState = typeof state !== 'undefined' && state.combat && state.combat.active ? 'shoulder' : 'orbit';
    }
  }
  function cssAnimation(sword, name) {
    return typeof sword.getAnimations === 'function' &&
      sword.getAnimations().find(function (animation) { return animation.animationName === name; });
  }
  function attack(skillName, onContact, miss) {
    return (async function () {
      var token, stage, sword, aura;
      try {
        if (S.attacking || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)) return false;
        stage = document.querySelector('.snapshot-stage');
        sword = stage && stage.querySelector('.demon-sword');
        if (!sword || sword.style.display === 'none' || !sword.getAttribute('src')) return false;
        token = ++S.epoch;
        S.attacking = true;
        cleanupFx(stage);
        ensureBaseStyle();
        sword.dataset.swordState = 'attack';
        sword.style.zIndex = '5';
        aura = document.createElement('div');
        aura.className = 'demon-aura show';
        aura.innerHTML = '<div class="wrap"></div><div class="coil"></div><div class="core-glow"></div>';
        stage.appendChild(aura);
        sword.classList.add('sword-charging');
        await nextFrame();
        var charge = cssAnimation(sword, 'sword-charge-anim');
        if (charge) await charge.finished;
        else await sleep(1500);
        if (token !== S.epoch || !sword.isConnected) return false;
        aura.remove();
        sword.classList.remove('sword-charging');
        sword.classList.add('sword-flying');
        await nextFrame();
        var fly = cssAnimation(sword, 'sword-fly-anim');
        if (fly) {
          await new Promise(function (resolve) {
            function check() {
              if (token !== S.epoch || fly.playState === 'idle' ||
                  fly.playState === 'finished' || Number(fly.currentTime) >= 210 * 0.78) resolve();
              else requestAnimationFrame(check);
            }
            check();
          });
        } else await sleep(Math.round(210 * 0.78));
        if (token !== S.epoch || !sword.isConnected || (fly && fly.playState === 'idle')) return false;
        var entity = stage.querySelector('.snapshot-entity');
        var er = entity && entity.getBoundingClientRect(), rect = stage.getBoundingClientRect();
        var x = er && er.width ? er.left - rect.left + er.width / 2 : rect.width * 0.35;
        var y = er && er.width ? er.top - rect.top + er.height * 0.45 : rect.height * 0.28;
        if (!miss) impact(stage, null, er, rect, x, y, true);
        if (typeof onContact === 'function') onContact();
        if (fly) await fly.finished;
        else await sleep(210 - Math.round(210 * 0.78));
        await sleep(60);
        return token === S.epoch;
      } catch (_) {
        return false;
      } finally {
        if (token === S.epoch) {
          S.attacking = false;
          if (aura) aura.remove();
          sword.classList.remove('sword-charging', 'sword-flying');
          clearDrive(sword);
          sword.dataset.swordState = typeof state !== 'undefined' && state.combat && state.combat.active ? 'shoulder' : 'orbit';
        }
      }
    })();
  }
  function isAttacking() { return S.attacking; }
  window.backroomDemonSword = Object.freeze({
    render: render,
    attack: attack,
    isAttacking: isAttacking,
    cancel: cancel
  });
})();

