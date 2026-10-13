(function () {
  'use strict';
  var inFlight = false;

  window.backroomRenderBattleStart = function () {
    var previous = document.getElementById('battleStartButton');
    if (previous) previous.remove();
    var encounter = state && state.pendingEncounter;
    if (!encounter || state.combat && state.combat.active === true) return;
    var rows = logEl.querySelectorAll('.message.gm');
    var narrativeIndex = (state.log || []).filter(function (entry) {
      return entry.role === 'gm';
    }).findIndex(function (entry) { return entry.encounterId === encounter.encounterId; });
    var last = rows[narrativeIndex];
    if (!last) return;
    var button = document.createElement('button');
    button.id = 'battleStartButton';
    button.type = 'button';
    button.textContent = 'Thực thể ' + encounter.entityName + ' đã xuất hiện ! Battle Start.';
    button.style.cssText = 'display:block;width:100%;margin-top:16px;padding:14px;white-space:normal';
    button.disabled = inFlight;
    button.addEventListener('click', function () {
      if (inFlight || busy || !state.pendingEncounter ||
          state.pendingEncounter.encounterId !== encounter.encounterId) return;
      if (!window.Android || typeof Android.battleStart !== 'function') {
        statusEl.textContent = 'Không tìm thấy Battle Start bridge.';
        return;
      }
      inFlight = true;
      busy = true;
      syncPrimaryActions();
      button.disabled = true;
      statusEl.textContent = 'Đang bắt đầu chiến đấu…';
      try {
        Android.battleStart(JSON.stringify(state), encounter.encounterId);
      } catch (error) {
        inFlight = false;
        busy = false;
        syncPrimaryActions();
        button.disabled = false;
        statusEl.textContent = 'Không thể bắt đầu chiến đấu. Anh hãy thử lại.';
      }
    });
    last.appendChild(button);
  };

  window.backroomBattleStart = function (json) {
    inFlight = false;
    busy = false;
    try {
      var result = JSON.parse(json);
      if (result.handled === true && result.state) {
        window.backroomTurn(JSON.stringify(result.state));
        return;
      }
      if (result.state) state = semanticPrepareNextState(state, JSON.stringify(result.state));
      syncPrimaryActions();
      render();
      statusEl.textContent = 'Chưa bắt đầu chiến đấu: ' + (result.error || 'Core chưa xác nhận.');
    } catch (error) {
      window.backroomRenderBattleStart();
      statusEl.textContent = 'Phản hồi Battle Start không hợp lệ. Anh hãy thử lại.';
    }
  };
  window.backroomRenderBattleStart();
}());
