/* GM_TURN_FEEDBACK_R01: UI-only feedback. Never modifies authoritative game state. */
(function () {
  'use strict';
  if (window.__gmTurnFeedbackInstalled) return;
  window.__gmTurnFeedbackInstalled = true;

  var notices = [];
  var active = false;
  var startedTurn = null;

  function status(message) {
    var node = document.getElementById('status');
    if (node) node.textContent = message;
  }

  function clearNotices() {
    for (var i = 0; i < notices.length; i++) clearTimeout(notices[i]);
    notices = [];
    active = false;
    startedTurn = null;
  }

  function waiting(turn) {
    return active && startedTurn === turn && typeof busy !== 'undefined' && busy;
  }

  function beginWaiting() {
    var turn = typeof state === 'object' && state ? Number(state.turn) : null;
    if (active && startedTurn === turn) return; // Failover is the same request.
    clearNotices();
    active = true;
    startedTurn = turn;
    notices.push(setTimeout(function () {
      if (waiting(turn)) status('GM vẫn đang chờ phản hồi AI. Lượt chưa được lưu, vui lòng không gửi lặp.');
    }, 25000));
    notices.push(setTimeout(function () {
      if (waiting(turn)) status('GM vẫn đang xử lý hoặc chuyển nhà cung cấp AI. Lượt chưa được xác nhận.');
    }, 75000));
  }

  var previousProvider;
  function providerFeedback(provider) {
    if (typeof previousProvider === 'function') previousProvider.apply(this, arguments);
    beginWaiting();
  }
  function wireProvider() {
    if (window.backroomProvider === providerFeedback) return;
    previousProvider = window.backroomProvider;
    window.backroomProvider = providerFeedback;
  }
  // Android installs native WebView enhancements after onPageFinished. That
  // script replaces backroomProvider, so it explicitly calls this hook again.
  window.backroomWireGMFeedbackProvider = wireProvider;
  wireProvider();

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function (json) {
    clearNotices();
    if (typeof previousTurn === 'function') return previousTurn.apply(this, arguments);
  };

  var previousError = window.backroomError;
  window.backroomError = function (message) {
    clearNotices();
    try {
      if (typeof previousError === 'function') previousError.apply(this, arguments);
    } finally {
      // A failed provider/Core request must not leave an optimistic pending message.
      document.querySelectorAll('[data-pending="1"]').forEach(function (node) { node.remove(); });
      var log = document.getElementById('log');
      if (log) {
        var previous = log.querySelector('.gm-transport-error');
        if (previous) previous.remove();
        var article = document.createElement('article');
        article.className = 'message gm-transport-error';
        article.setAttribute('role', 'status');
        var role = document.createElement('div');
        role.className = 'role';
        role.textContent = 'LỖI XỬ LÝ LƯỢT';
        var detail = document.createElement('div');
        detail.className = 'text';
        detail.textContent = String(message || 'Không thể xử lý lượt.');
        article.appendChild(role);
        article.appendChild(detail);
        log.appendChild(article);
        log.scrollTop = log.scrollHeight;
      }
      status('Không thể hoàn thành lượt: ' + String(message || 'lỗi không xác định') + '. Hành động chưa được xác nhận.');
    }
  };
})();
