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
    if (typeof window.backroomDiagnosticRecord === 'function')
      window.backroomDiagnosticRecord('startup', detail || 'unknown_failure');
    const incompatible = /save_version_unsupported|unsupported save|coreSnapshot|level_game_not_initialized/.test(detail);
    const message = incompatible
      ? 'Dữ liệu lưu hiện có không thuộc định dạng game hiện hành. Game không tự chuyển đổi hay xóa save cũ.'
      : detail.includes('core_save_decode_failed')
        ? 'Save Core bị lỗi giải mã; dữ liệu gốc vẫn được giữ nguyên.'
        : 'Game Core không thể khởi động. Dữ liệu hiện có chưa bị xóa.';
    if (recovery) recovery.hidden = false;
    if (recoveryMessage) recoveryMessage.textContent = message +
      ' Mã lỗi: ' + (detail.replace(/[\\r\\n<>]/g, ' ').slice(0, 220) || 'unknown') +
      '. Chỉ chọn “Bắt đầu game mới” nếu bạn muốn thay thế dữ liệu đang lưu.';
    statusEl.textContent = message;
    submitEl.disabled = true;
    // A cached browser label is not proof of Core identity after boot failed.
    const place = document.getElementById('location');
    if (place) place.textContent = 'Chưa xác định';
    const heading = document.getElementById('title');
    if (heading) heading.textContent = 'MAIN_BACKROOMS — Cao Minh';
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
  function syncCombatDice() {
    if (typeof window.ensureDirectCombatDice === 'function') window.ensureDirectCombatDice();
  }
  function drawTravel() {
    if (typeof oldRender === 'function') oldRender();
    if (window.backroomSnapshotStage) window.backroomSnapshotStage.render(state);
    if (window.backroomLuciaVisuals) window.backroomLuciaVisuals.renderOverlay(state);
    if (window.backroomNamedFollowers) window.backroomNamedFollowers.renderOverlay(state);

  }
  window.render = drawTravel;
  window.save = function () {
    state.coreSnapshot = JSON.parse(native.levelCoreSnapshot());
    localStorage.setItem(key, JSON.stringify(state));
    statusEl.textContent = 'Đã lưu game.';
    if (window.backroomMenuFeedback) window.backroomMenuFeedback('Đã lưu tiến trình trên thiết bị.');
  };
  window.load = function () {
    if (busy) return;
    const raw = localStorage.getItem(key);
    if (!raw) {
      statusEl.textContent = 'Chưa có game đã lưu.';
      if (window.backroomMenuFeedback) window.backroomMenuFeedback('Không tìm thấy tiến trình đã lưu.');
      return;
    }
    try {
      state = JSON.parse(native.initializeLevelGame(raw, false));
      drawTravel();
      syncCombatDice();
      hideRecovery();
      if (window.backroomMenuFeedback) window.backroomMenuFeedback('Đã tải tiến trình đã lưu.');
    } catch (error) { showRecovery(error); }
  };
  function newGame() {
    const next = JSON.parse(native.initializeLevelGame(JSON.stringify(initial), true));
    state = next;
    submitEl.disabled = false;
    localStorage.removeItem('backroom-apk-snapshot');
    window.save();
    drawTravel();
    syncCombatDice();
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
    syncCombatDice();
    hideRecovery();
    if (typeof window.backroomDiagnosticRecord === 'function')
      window.backroomDiagnosticRecord('startup', 'resume_success');
  } catch (error) {
    showRecovery(error);
  }
})();

