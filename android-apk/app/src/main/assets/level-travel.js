/* Travel is selected by native Core once at New Game. Rendering never requests RNG. */
(function () {
  'use strict';
  if (window.__levelTravelUi) return;
  window.__levelTravelUi = true;
  const native = window.Android;
  if (!native || typeof native.initializeLevelGame !== 'function') return;
  const key = 'backroom-apk-state';
  const oldRender = window.render;
  function drawTravel() {
    if (typeof oldRender === 'function') oldRender();
    const t = state && state.levelTravel;
    if (!t) return;
    let panel = document.getElementById('level-travel-panel');
    if (!panel) {
      panel = document.createElement('section');
      panel.id = 'level-travel-panel';
      panel.style.cssText = 'margin:8px 10px;border:1px solid #343b41;background:#0b0e10';
      const image = document.createElement('img');
      image.id = 'level-travel-background';
      image.style.cssText = 'width:100%;max-height:260px;object-fit:cover;display:block';
      const label = document.createElement('div');
      label.id = 'level-travel-streak';
      label.style.cssText = 'padding:8px;color:#c8c8b0;font-size:12px';
      const button = document.createElement('button');
      button.type = 'button';
      button.id = 'level-travel-attempt';
      button.textContent = 'Thử thoát Level';
      button.style.cssText = 'width:100%';
      button.onclick = function () {
        if (busy) return;
        actionEl.value = 'Thử thoát Level';
        formEl.requestSubmit();
      };
      panel.append(image, label, button);
      const snapshot = document.getElementById('snapshot');
      if (snapshot) snapshot.before(panel); else document.body.prepend(panel);
    }
    document.getElementById('level-travel-attempt').disabled = busy || Boolean(state.combat && state.combat.active);
    const image = document.getElementById('level-travel-background');
    if (image.getAttribute('src') !== state.levelBackground) image.src = state.levelBackground;
    image.alt = state.title || 'Địa điểm hiện tại';
    document.getElementById('level-travel-streak').textContent = 'Chuỗi thoát: ' + t.exitStreak + '/3 · Mỗi lần thử: 50%';
  }
  window.render = drawTravel;
  window.save = function () {
    state.coreSnapshot = JSON.parse(native.levelCoreSnapshot());
    localStorage.setItem(key, JSON.stringify(state));
    statusEl.textContent = 'Đã lưu game.';
  };
  window.load = function () {
    if (busy) return;
    const raw = localStorage.getItem(key);
    if (!raw) { statusEl.textContent = 'Chưa có game đã lưu.'; return; }
    try {
      state = JSON.parse(native.initializeLevelGame(raw, false));
      drawTravel();
    } catch (error) { statusEl.textContent = 'Không thể tải game hiện hành: ' + error.message; }
  };
  function newGame() {
    const next = JSON.parse(native.initializeLevelGame(JSON.stringify(initial), true));
    state = next;
    submitEl.disabled = false;
    localStorage.removeItem('backroom-apk-snapshot');
    window.save();
    drawTravel();
  }
  window.resetGame = function () {
    if (!busy && confirm('Bắt đầu game mới?')) newGame();
  };
  window.clearSave = function () {
    if (!busy && confirm('Xóa save và bắt đầu game mới?')) {
      newGame();
    }
  };
  try {
    const raw = localStorage.getItem(key);
    state = JSON.parse(native.resumeLevelGame(raw || JSON.stringify(initial), Boolean(raw)));
    window.save();
    drawTravel();
  } catch (error) {
    statusEl.textContent = 'Không thể mở save. Dùng “Bắt đầu lại từ đầu” để tạo game mới.';
    submitEl.disabled = true;
  }
})();
