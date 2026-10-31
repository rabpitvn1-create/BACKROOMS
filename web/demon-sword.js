/* ============================================================================
 * Huyết Ma Kiếm — Demon Sword controller cho game Backrooms (web).
 *
 * IIFE, không đụng vào code khác. Mọi hàm đều bọc try/catch — KHÔNG BAO GIỜ
 * throw ra ngoài, không được làm crash game.
 *
 * API: window.backroomDemonSword = { render(stage, fighting), attack(skillName),
 *                                   isAttacking() }
 * ========================================================================== */
(function () {
  'use strict';
  var S = { attacking: false };
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
  function attack(skillName) {
    return (async function () {
      try {
        if (S.attacking) return false;
        try {
          if (window.matchMedia &&
              window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false;
        } catch (e) {}
        var stage = document.querySelector('.snapshot-stage');
        if (!stage) return false;
        var sword = stage.querySelector('.demon-sword');
        if (!sword) return false;
        try {
          if (sword.style.display === 'none') return false;
          if (!sword.getAttribute('src')) return false;
          if (sword.dataset.swordState !== 'shoulder') return false;
        } catch (e) { return false; }
        S.attacking = true;
        cleanupFx(stage);
        ensureBaseStyle();
        // User-approved: demonic aura charge (1500ms) -> diagonal fly 71deg (210ms) -> impact -> return
        var CHARGE_MS = 1500, FLY_MS = 210;
        try {
          sword.dataset.swordState = 'attack';
          sword.style.zIndex = '5';
        } catch (e) {}
        // Phase 1: Demonic aura charge
        var aura = null;
        try {
          aura = document.createElement('div');
          aura.className = 'demon-aura';
          aura.innerHTML = '<div class="wrap"></div><div class="coil"></div><div class="core-glow"></div>';
          stage.appendChild(aura);
          // Force reflow
          void aura.offsetWidth;
          aura.classList.add('show');
          sword.classList.add('sword-charging');
        } catch (e) {}
        await sleep(CHARGE_MS);
        try {
          if (aura) aura.remove();
          sword.classList.remove('sword-charging');
        } catch (e) {}
        // Phase 2: Fly diagonally (CSS animation handles the path)
        try {
          void sword.offsetWidth;
          sword.classList.add('sword-flying');
        } catch (e) {}
        await sleep(Math.round(FLY_MS * 0.78));
        // Phase 3: Impact effects
        try {
          var er2 = null;
          try {
            var ent2 = stage.querySelector('.snapshot-entity');
            if (ent2) er2 = ent2.getBoundingClientRect();
          } catch (e2) {}
          var str2 = stage.getBoundingClientRect();
          var tcx2, tcy2;
          if (er2 && er2.width > 0) {
            tcx2 = er2.left - str2.left + er2.width / 2;
            tcy2 = er2.top - str2.top + er2.height * 0.45;
          } else {
            tcx2 = str2.width * 0.35;
            tcy2 = str2.height * 0.28;
          }
          impact(stage, null, er2, str2, tcx2, tcy2, true);
        } catch (e) {}
        await sleep(FLY_MS - Math.round(FLY_MS * 0.78) + 60);
        try {
          sword.classList.remove('sword-flying');
        } catch (e) {}
        // Cleanup and return
        try {
          sword.dataset.swordState = 'shoulder';
          sword.style.zIndex = '';
          sword.style.transform = '';
          sword.style.transition = '';
        } catch (e) {}
        S.attacking = false;
        return true;
      } catch (e) {
        try {
          var st = document.querySelector('.snapshot-stage');
          if (st) {
            cleanupFx(st);
            var sw = st.querySelector('.demon-sword');
            if (sw) {
              clearDrive(sw);
              try { sw.dataset.swordState = 'shoulder'; } catch (_) {}
            }
          }
        } catch (_) {}
        S.attacking = false;
        return false;
      }
    })();
  }
  function isAttacking() { return S.attacking; }
  window.backroomDemonSword = Object.freeze({
    render: render,
    attack: attack,
    isAttacking: isAttacking
  });
})();
(function () {
  'use strict';
  function isCaoMinhAttack(e) {
    if (!e || e.target !== 'entity') return false;
    if (String(e.actorId || 'cao_minh') !== 'cao_minh') return false;
    var o = e.outcome;
    if (o === 'miss' || o === 'tick' || o === 'buff') return false;
    return true;
  }
  function install() {
    if (typeof window.backroomPlayCombatFeedback !== 'function') {
      setTimeout(install, 200); return;
    }
    if (window.backroomPlayCombatFeedback.__swordWrapped) return;
    var orig = window.backroomPlayCombatFeedback;
    var wrapped = function (event, fallback) {
      if (isCaoMinhAttack(event) &&
          window.backroomDemonSword &&
          typeof window.backroomDemonSword.attack === 'function') {
        try {
          window.backroomDemonSword.attack(
            typeof event.skill === 'string' ? event.skill : ''
          );
        } catch (_) {}
      }
      return orig.call(this, event, fallback);
    };
    wrapped.__swordWrapped = true;
    window.backroomPlayCombatFeedback = wrapped;
  }
  install();
})();
