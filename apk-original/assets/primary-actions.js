/* Typed gameplay actions have their own bridge. submitAction is combat-only. */
(function () {
  'use strict';
  const search = document.getElementById('searchActionButton');
  const explore = document.getElementById('exploreActionButton');
  const recovery = document.getElementById('startupRecovery');
  const modal = document.getElementById('playerActionModal');
  const confirm = document.getElementById('playerActionConfirm');
  const activity = document.getElementById('providerActivity');
  const inviteActions = document.getElementById('followerInviteActions');
  const namedFollowers = Object.freeze({
    hua_tieu_lan: 'Hứa Tiểu Lan',
    nguyen_quoc_nam: 'Nguyễn Quốc Nam'
  });
  let inviteSignature = '';
  let lastTurnFailed = false;
  let nativeImeBottom = 0;
  let nativeImeVisible = false;
  let baselineHeight = window.innerHeight || 0;
  // Native IME insets complement visualViewport on fullscreen Android WebView.
  window.backroomImeInsets = function(bottomCssPx, visible) {
    nativeImeBottom = Math.max(0, Number(bottomCssPx) || 0);
    nativeImeVisible = Boolean(visible);
    if (!nativeImeVisible) baselineHeight = window.innerHeight || baselineHeight;
    fitModal();
  };
  // Only model identity is visible. Provider key aliases and internal routing are hidden.
  function displayModel(raw) {
    let model = String(raw || '').trim();
    const routed = /^Gemini K[1-5] · (gemini-[a-z0-9.-]+)$/i.exec(model);
    if (routed) model = routed[1];
    if (model === 'Gehihi' || !/^[a-z][a-z0-9._:\/-]{1,119}$/i.test(model)) return null;
    model = model.replace(/^models\//i, '').split('/').pop();
    return model.replace(/^gemini-/i, 'GEMINI ').replace(/[-_]+/g, ' ').toUpperCase();
  }
  window.backroomProvider = name => {
    const model = displayModel(name);
    if (model && activity) { activity.textContent = model; activity.hidden = false; }
  };
  function fitModal() {
    if (!modal || modal.hidden) return;
    const viewport = window.visualViewport;
    const innerHeight = Number(window.innerHeight) || baselineHeight;
    if (!nativeImeVisible && (!viewport || viewport.height >= innerHeight * 0.95))
      baselineHeight = innerHeight;
    const visualHeight = Number(viewport?.height) || innerHeight;
    const imeLimited = nativeImeVisible ? baselineHeight - nativeImeBottom : visualHeight;
    const visibleHeight = Math.max(96, Math.min(visualHeight, imeLimited));
    modal.style.top = (viewport?.offsetTop || 0) + 'px';
    modal.style.height = visibleHeight + 'px';
    // Top-aligned composer is the fail-safe even if WebView fails to report IME.
    const keyboardOpen = nativeImeVisible || visibleHeight < baselineHeight * 0.78;
    modal.classList.toggle('keyboard-visible', Boolean(keyboardOpen));
  }
  function closeModal() {
    if (!modal) return;
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    actionEl.blur();
    modal.classList.remove('keyboard-visible');
    modal.style.top = '';
    modal.style.height = '';
  }
  function locked() {
    return busy || (recovery && !recovery.hidden) || Boolean(state?.combat?.active);
  }
  function syncInvites(blocked) {
    if (!inviteActions) return;
    const party = new Set((Array.isArray(state?.party) ? state.party : []).map(member => member?.id));
    const guests = (Array.isArray(state?.followerEncounters) ? state.followerEncounters : [])
      .filter(member => member?.presence === 'ACTIVE' &&
        Object.hasOwn(namedFollowers, member.id) && !party.has(member.id) &&
        state?.flags?.[member.id]?.joinPending !== false);
    const signature = guests.map(member => member.id).join('|');
    if (signature !== inviteSignature) {
      inviteActions.replaceChildren();
      guests.forEach(member => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = 'Mời ' + namedFollowers[member.id] + ' đồng hành';
        button.addEventListener('click', () => {
          if (!locked()) submitMacro('EXECUTE', 'Mời ' + namedFollowers[member.id] + ' gia nhập Party');
        });
        inviteActions.appendChild(button);
      });
      inviteSignature = signature;
    }
    inviteActions.hidden = guests.length === 0;
    Array.from(inviteActions.children).forEach(button => { button.disabled = blocked; });
  }
  function syncPrimaryActions() {
    const blocked = locked();
    syncInvites(blocked);
    submitEl.disabled = blocked;
    if (confirm) confirm.disabled = blocked || !actionEl.value.trim();
    if (search) search.disabled = blocked;
    if (explore) explore.disabled = blocked;
    if (activity) activity.hidden = ((!busy && !lastTurnFailed) || !activity.textContent);
  }
  function submitMacro(kind, label) {
    if (locked()) return;
    if (!window.Android || typeof Android.submitGameplayAction !== 'function') {
      statusEl.textContent = 'Không tìm thấy kết nối hành động.';
      return;
    }
    busy = true;
    lastTurnFailed = false;
    if (activity) { activity.textContent = ''; activity.hidden = true; }
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
    if (typeof requestAnimationFrame === 'function') requestAnimationFrame(fitModal);
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
  window.addEventListener?.('resize', fitModal);
  actionEl.addEventListener('input', syncPrimaryActions);
  formEl.addEventListener('submit', () => {
    lastTurnFailed = false;
    if (activity) { activity.textContent = ''; activity.hidden = true; }
    if (busy) closeModal();
    syncPrimaryActions();
  });
  window.syncPrimaryActions = syncPrimaryActions;
  ['render', 'backroomTurn', 'backroomError'].forEach(name => {
    const previous = window[name];
    if (typeof previous !== 'function') return;
    window[name] = function () {
      const result = previous.apply(this, arguments);
      if (name === 'backroomError') {
        lastTurnFailed = true;
        if (activity) activity.textContent = statusEl.textContent || 'Lỗi xử lý lượt.';
      } else if (name === 'backroomTurn') {
        lastTurnFailed = false;
      }
      syncPrimaryActions();
      return result;
    };
  });
  syncPrimaryActions();
})();
