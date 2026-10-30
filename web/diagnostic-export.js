/* Privacy-preserving, user-initiated diagnostic export.
 * Never include save contents, chat text, prompts, API keys or provider payloads. */
(function () {
  'use strict';
  var button = document.getElementById('exportDiagnosticsJson');
  var status = document.getElementById('diagnosticExportStatus');
  var storageKey = 'backroom-diagnostic-events-v1';
  var MAX_EVENTS = 80;
  var events = [];
  var exporting = false;

  // Identifier-shaped secrets (including common API key formats) must not
  // become diagnostic codes merely because they happen to match a regex.
  var SAFE_CODES = new Set([
    'unknown', 'unknown_failure', 'details_redacted',
    'app', 'startup', 'javascript', 'turn', 'combat', 'storage', 'export',
    'diagnostics_ready', 'response_received', 'uncaught_exception',
    'unhandled_rejection', 'browser_save_parse_failed',
    'save_version_unsupported', 'level_game_not_initialized',
    'core_save_decode_failed', 'core_save_failed', 'level_travel_not_configured',
    'stale_game_run', 'stale_level_turn', 'stale_exit_turn',
    'exit_action_conflict', 'invalid_save', 'android_bridge_unavailable',
    'requested', 'file_saved', 'cancelled', 'failed', 'bridge_unavailable',
    'resume_success'
  ]);
  function safeCode(value) {
    var code = String(value == null ? '' : value).trim();
    return SAFE_CODES.has(code) ? code : 'details_redacted';
  }
  function safeScript(source) {
    var basename = String(source || '').split(/[\\/]/).pop();
    return /^[a-z0-9_.-]+\.(?:js|html)$/i.test(basename) ? basename : 'unknown';
  }
  function limitedNumber(value, limit) {
    var n = Number(value);
    return Number.isFinite(n) ? Math.max(0, Math.min(limit, Math.trunc(n))) : 0;
  }
  try {
    var saved = JSON.parse(localStorage.getItem(storageKey) || '[]');
    if (Array.isArray(saved)) {
      saved.slice(-MAX_EVENTS).forEach(function (e) {
        if (!e || typeof e !== 'object') return;
        events.push({
          timestampMs: limitedNumber(e.timestampMs, 9000000000000000),
          stage: safeCode(e.stage), code: safeCode(e.code)
        });
      });
    }
  } catch (_) { events = []; }

  function record(stage, code, detail) {
    var event = {timestampMs: Date.now(), stage: safeCode(stage), code: safeCode(code)};
    if (detail && typeof detail === 'object') {
      if (detail.line != null) event.line = limitedNumber(detail.line, 1000000);
      if (detail.column != null) event.column = limitedNumber(detail.column, 1000000);
      if (detail.source != null) event.source = safeScript(detail.source);
    }
    events.push(event);
    if (events.length > MAX_EVENTS) events.splice(0, events.length - MAX_EVENTS);
    try { localStorage.setItem(storageKey, JSON.stringify(events)); }
    catch (_) { /* Diagnostic logging cannot block the game. */ }
  }
  window.backroomDiagnosticRecord = record;
  window.backroomDiagnosticEvents = function () { return events.slice(); };

  if (typeof window.addEventListener === 'function') {
    window.addEventListener('error', function (event) {
      record('javascript', 'uncaught_exception', {
        line: event.lineno, column: event.colno, source: event.filename
      });
    });
    window.addEventListener('unhandledrejection', function () {
      record('javascript', 'unhandled_rejection');
    });
  }
  var oldTurn = window.backroomTurn;
  if (typeof oldTurn === 'function') window.backroomTurn = function () {
    record('turn', 'response_received');
    return oldTurn.apply(this, arguments);
  };
  var oldError = window.backroomError;
  if (typeof oldError === 'function') window.backroomError = function (message) {
    record('turn', safeCode(message));
    return oldError.apply(this, arguments);
  };
  var oldDiceError = window.backroomCombatDiceError;
  if (typeof oldDiceError === 'function') window.backroomCombatDiceError = function (message) {
    record('combat', safeCode(message));
    return oldDiceError.apply(this, arguments);
  };

  function uiSummary() {
    var game = typeof state !== 'undefined' && state && typeof state === 'object' ? state : null;
    var save = null;
    var hasBrowserSave = false;
    try {
      var text = localStorage.getItem('backroom-apk-state');
      hasBrowserSave = Boolean(text);
      if (text) save = JSON.parse(text);
    } catch (_) { record('storage', 'browser_save_parse_failed'); }
    var recovery = document.getElementById('startupRecovery');
    var level = game && game.levelTravel;
    var party = game && game.party;
    var count = game && game.partyDetails && game.partyDetails.members;
    return {
      hasState: Boolean(game),
      hasBrowserSave: hasBrowserSave,
      hasCoreSnapshot: Boolean((game && game.coreSnapshot) || (save && save.coreSnapshot)),
      startupRecoveryVisible: Boolean(recovery && !recovery.hidden),
      combatActive: Boolean(game && game.combat && game.combat.active),
      displayLocationKnown: Boolean(game && game.displayLocation &&
        game.displayLocation !== 'Chưa xác định'),
      turn: limitedNumber(game && game.turn, 1000000),
      partySize: limitedNumber(Array.isArray(count) ? count.length :
        Array.isArray(party) ? party.length + 1 : 0, 1000),
      browserSaveVersion: limitedNumber(save && save.coreSnapshot &&
        save.coreSnapshot.saveVersion, 100000),
      levelNodeId: level && typeof level.nodeId === 'string' ? level.nodeId : '',
      events: events.slice(-MAX_EVENTS)
    };
  }

  function show(message) { if (status) status.textContent = message; }
  window.backroomDiagnosticExportResult = function (outcome) {
    exporting = false;
    if (button) button.disabled = false;
    if (outcome === 'saved') {
      record('export', 'file_saved');
      show('Đã xuất log JSON. Có thể gửi tệp này để kiểm tra lỗi.');
    } else if (outcome === 'cancelled') {
      record('export', 'cancelled');
      show('Đã hủy xuất log. Dữ liệu game không thay đổi.');
    } else {
      record('export', 'failed');
      show('Không thể lưu log JSON. Vui lòng thử lại.');
    }
  };

  if (button) button.addEventListener('click', function () {
    if (exporting) return;
    if (!window.Android || typeof window.Android.exportDiagnosticLog !== 'function') {
      show('Phiên bản này chưa hỗ trợ xuất log JSON.');
      record('export', 'bridge_unavailable');
      return;
    }
    exporting = true;
    button.disabled = true;
    record('export', 'requested');
    show('Chọn nơi lưu tệp chẩn đoán trong Android…');
    try { window.Android.exportDiagnosticLog(JSON.stringify(uiSummary())); }
    catch (_) { window.backroomDiagnosticExportResult('failed'); }
  });
  record('app', 'diagnostics_ready');
})();
