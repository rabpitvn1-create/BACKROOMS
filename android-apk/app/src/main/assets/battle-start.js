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
      return entry.role !== 'player';
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
  window.backroomRenderEncounteredCharacters = function () {
    var box = document.getElementById('encounteredCharacters');
    if (!box) return;
    box.textContent = '';
    (state.encounteredCharacters || []).forEach(function (character) {
      var row = document.createElement('div');
      row.style.cssText = 'display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin-top:12px';
      var label = document.createElement('span');
      label.textContent = 'Đã gặp: ' + character.name + (character.inParty ? ' · Trong Party' : '');
      row.appendChild(label);
      if (character.present && character.joinEligible && !character.inParty) {
        var invite = document.createElement('button');
        invite.type = 'button';
        invite.textContent = 'Mời vào Party';
        invite.dataset.characterId = character.id;
        invite.disabled = busy || !!state.pendingEncounter || !!(state.combat && state.combat.active);
        invite.addEventListener('click', function () {
          if (invite.disabled || busy || !window.Android || typeof Android.recruitFollower !== 'function') return;
          busy = true;
          invite.disabled = true;
          syncPrimaryActions();
          statusEl.textContent = 'Đang mời ' + character.name + '…';
          try { Android.recruitFollower(JSON.stringify(state), character.id); }
          catch (error) { window.backroomRecruitment('{"handled":false,"error":"recruitment_failed"}'); }
        });
        row.appendChild(invite);
      }
      box.appendChild(row);
    });
  };

  window.backroomRecruitment = function (json) {
    busy = false;
    try {
      var result = JSON.parse(json);
      if (result.state) window.backroomTurn(JSON.stringify(result.state));
      else { syncPrimaryActions(); window.backroomRenderEncounteredCharacters(); }
      var messages = {party_full: 'Party đã đủ thành viên.', join_not_confirmed: 'Nhân vật chưa đồng ý gia nhập.',
        follower_not_encountered: 'Anh chưa gặp nhân vật này.', turn_in_progress: 'Hãy hoàn tất lượt hiện tại.',
        stale_client_state: 'Trạng thái đã thay đổi. Anh hãy thử lại.'};
      statusEl.textContent = result.handled ? (result.reply || 'Party đã cập nhật.') :
        (messages[result.error] || 'Chưa thể tuyển mộ. Anh hãy thử lại.');
    } catch (error) { syncPrimaryActions(); window.backroomRenderEncounteredCharacters(); statusEl.textContent = 'Chưa thể cập nhật Party. Anh hãy thử lại.'; }
  };
  window.backroomRenderBattleStart();
  window.backroomRenderEncounteredCharacters();
}());
