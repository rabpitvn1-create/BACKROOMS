/* GitHub Pages hosts the recovered UI. Native gameplay is available in the APK. */
(() => {
  if (window.Android) return;
  const game = document.querySelector('.game');
  const actions = document.getElementById('playerActionBar');
  if (!game || !actions) return;
  const notice = document.createElement('section');
  notice.id = 'webPreviewNotice';
  notice.className = 'web-preview-notice';
  notice.setAttribute('aria-label', 'Trạng thái bản web');
  notice.innerHTML = '<div><strong>Bản xem trước trên web</strong><p>Lượt chơi, chiến đấu và trang bị hiện hoạt động trong APK Android. Bản web này dùng để xem giao diện và nội dung đã phục hồi.</p></div><a href="https://github.com/rabpitvn1-create/Backrooms/releases/download/v1.0.0.0/Backrooms-1.0.0.0-debug.apk">Tải APK debug 1.0.0.0</a>';
  game.insertBefore(notice, actions);
  const style = document.createElement('style');
  style.textContent = 'html[data-web-mode="preview"] button:disabled{opacity:.5;cursor:not-allowed}.web-preview-notice{flex:none;display:flex;align-items:center;gap:12px;padding:10px max(12px,env(safe-area-inset-right)) 10px max(12px,env(safe-area-inset-left));border-top:1px solid #696348;background:#211f16;color:#eee9ce;font-size:12px;line-height:1.45}.web-preview-notice p{margin:3px 0 0;color:#c4bfa9}.web-preview-notice a{flex:none;display:block;border:1px solid #b6a977;border-radius:6px;padding:10px;color:#fff1b8;text-decoration:none;font-weight:700}.web-preview-notice a:focus-visible{outline:2px solid #fff;outline-offset:3px}@media(max-width:520px){.web-preview-notice{gap:8px;font-size:11px}.web-preview-notice a{max-width:125px;text-align:center;padding:8px}}';
  document.head.appendChild(style);
  // Keep inspection, menu and local save/load available. Native actions cannot run here.
  const nativeControls = '#playerActionBar button,#playerActionConfirm,#itemActionEquip,#itemActionUse,#itemActionTransfer,#itemActionDrop,#exportDiagnosticsJson';
  const disableNative = () => {
    document.querySelectorAll(nativeControls).forEach(button => {
      if (!button.disabled) button.disabled = true;
      button.title = 'Thao tác này hiện có trong APK Android.';
    });
  };
  const sync = window.syncPrimaryActions;
  if (typeof sync === 'function') window.syncPrimaryActions = function () {
    const result = sync.apply(this, arguments);
    disableNative();
    return result;
  };
  const observer = new MutationObserver(disableNative);
  observer.observe(actions, { subtree: true, childList: true, attributes: true, attributeFilter: ['disabled'] });
  disableNative();
  document.documentElement.dataset.webMode = 'preview';
})();
