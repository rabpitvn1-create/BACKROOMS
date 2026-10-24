/* Typed gameplay actions have their own bridge. submitAction is combat-only. */
(function () {
  'use strict';
  const search = document.getElementById('searchActionButton');
  const explore = document.getElementById('exploreActionButton');
  const recovery = document.getElementById('startupRecovery');
  const modal = document.getElementById('playerActionModal');
  const confirm = document.getElementById('playerActionConfirm');
  const activity = document.getElementById('providerActivity');
  window.backroomProvider = name => {
    if (!/^(Gehihi|Gemini K[1-5])$/.test(String(name))) return;
    if (activity) { activity.textContent = name + ' đang xử lý lượt…'; activity.hidden = false; }
  };
  function fitModal() {
    if (!modal || modal.hidden) return;
    const viewport = window.visualViewport;
    modal.style.top = (viewport?.offsetTop || 0) + 'px';
    modal.style.height = (viewport?.height || window.innerHeight) + 'px';
  }
  function closeModal() {
    if (!modal) return;
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    actionEl.blur();
  }
  function locked() {
    return busy || (recovery && !recovery.hidden) || Boolean(state?.combat?.active);
  }
  function syncPrimaryActions() {
    const blocked = locked();
    submitEl.disabled = blocked;
    if (confirm) confirm.disabled = blocked || !actionEl.value.trim();
    if (search) search.disabled = blocked;
    if (explore) explore.disabled = blocked;
    if (activity) activity.hidden = !busy;
  }
  function submitMacro(kind, label) {
    if (locked()) return;
    if (!window.Android || typeof Android.submitGameplayAction !== 'function') {
      statusEl.textContent = 'Không tìm thấy kết nối hành động.';
      return;
    }
    busy = true;
    if (activity) activity.textContent = 'Đang xử lý lượt…';
    syncPrimaryActions();
    statusEl.textContent = 'Đang xử lý: ' + label + '…';
    try { Android.submitGameplayAction(JSON.stringify(state), kind, label); }
    catch (error) { window.backroomError(String(error)); }
  }
  search?.addEventListener('click', () => submitMacro('SEARCH', 'Tìm kiếm'));
  explore?.addEventListener('click', () => submitMacro('EXPLORE', 'Khám phá'));
  submitEl.addEventListener('click', () => {
    if (locked() || !modal) return;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    fitModal();
    actionEl.focus();
    syncPrimaryActions();
  });
  ['playerActionClose', 'playerActionCancel', 'playerActionBackdrop'].forEach(id => {
    document.getElementById(id)?.addEventListener('click', closeModal);
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && modal && !modal.hidden) closeModal();
  });
  window.visualViewport?.addEventListener('resize', fitModal);
  window.visualViewport?.addEventListener('scroll', fitModal);
  actionEl.addEventListener('input', syncPrimaryActions);
  formEl.addEventListener('submit', () => {
    if (activity) activity.textContent = 'Đang xử lý lượt…';
    if (busy) closeModal();
    syncPrimaryActions();
  });
  window.syncPrimaryActions = syncPrimaryActions;
  ['render', 'backroomTurn', 'backroomError'].forEach(name => {
    const previous = window[name];
    if (typeof previous !== 'function') return;
    window[name] = function () {
      const result = previous.apply(this, arguments);
      syncPrimaryActions();
      return result;
    };
  });
  syncPrimaryActions();
})();
