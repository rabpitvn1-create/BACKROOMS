/* Travel is selected by native Core once at New Game. Rendering never requests RNG. */
(function () {
  'use strict';
  if (window.__levelTravelUi) return;
  window.__levelTravelUi = true;
  const native = window.Android;
  const recovery = document.getElementById('startupRecovery');
  const recoveryMessage = document.getElementById('startupRecoveryMessage');
  const recoveryButton = document.getElementById('startNewGameFromRecovery');
  function showRecovery(error) {
    const detail = String(error && error.message || error || '');
    const incompatible = /save_version_unsupported|coreSnapshot|level_game_not_initialized/.test(detail);
    const message = incompatible
      ? 'Dữ liệu lưu hiện có không thuộc định dạng game hiện hành. Game không tự chuyển đổi hay xóa save cũ.'
      : 'Game Core không thể khởi động. Dữ liệu hiện có chưa bị xóa.';
    if (recovery) recovery.hidden = false;
    if (recoveryMessage) recoveryMessage.textContent = message +
      ' Chỉ chọn “Bắt đầu game mới” nếu bạn muốn thay thế dữ liệu đang lưu.';
    statusEl.textContent = message;
    submitEl.disabled = true;
  }
  function hideRecovery() {
    if (recovery) recovery.hidden = true;
  }
  if (!native || typeof native.initializeLevelGame !== 'function' ||
      typeof native.resumeLevelGame !== 'function') {
    showRecovery('android_bridge_unavailable');
    if (recoveryButton) recoveryButton.disabled = true;
    return;
  }
  const key = 'backroom-apk-state';
  // Expire Gemini-era generated image cache; Core game/save state is untouched.
  try { localStorage.removeItem('backroom-apk-snapshot'); } catch (_) {}
  const oldRender = window.render;
  function drawTravel() {
    if (typeof oldRender === 'function') oldRender();
    if (window.backroomSnapshotStage) window.backroomSnapshotStage.render(state);
    if (window.backroomLuciaVisuals) window.backroomLuciaVisuals.renderOverlay(state);
    if (window.backroomNamedFollowers) window.backroomNamedFollowers.renderOverlay(state);
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
      hideRecovery();
    } catch (error) { showRecovery(error); }
  };
  function newGame() {
    const next = JSON.parse(native.initializeLevelGame(JSON.stringify(initial), true));
    state = next;
    submitEl.disabled = false;
    localStorage.removeItem('backroom-apk-snapshot');
    window.save();
    drawTravel();
    hideRecovery();
  }
  if (recoveryButton) recoveryButton.addEventListener('click', () => {
    if (!confirm('Bắt đầu game mới sẽ thay thế save Core và save trong máy. Tiếp tục?')) return;
    try { newGame(); } catch (error) { showRecovery(error); }
  });
  window.resetGame = function () {
    if (!busy && confirm('Bắt đầu game mới?')) {
      try { newGame(); } catch (error) { showRecovery(error); }
    }
  };
  window.clearSave = function () {
    if (!busy && confirm('Xóa save và bắt đầu game mới?')) {
      try { newGame(); } catch (error) { showRecovery(error); }
    }
  };
  try {
    const raw = localStorage.getItem(key);
    state = JSON.parse(native.resumeLevelGame(raw || JSON.stringify(initial), Boolean(raw)));
    window.save();
    drawTravel();
    hideRecovery();
  } catch (error) {
    showRecovery(error);
  }
})();
