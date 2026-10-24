/* The sidebar remains present, but on phones it opens as a reachable HUD. */
(function () {
  'use strict';
  const button = document.getElementById('mobileMenuButton');
  const panel = document.getElementById('sidePanel');
  const closeButton = document.getElementById('mobileMenuClose');
  const backdrop = document.getElementById('mobileMenuBackdrop');
  if (!button || !panel || !closeButton || !backdrop) return;

  function closeMenu() {
    document.body.classList.remove('mobile-menu-open');
    backdrop.hidden = true;
    button.setAttribute('aria-expanded', 'false');
    panel.removeAttribute('aria-modal');
  }
  function openMenu() {
    backdrop.hidden = false;
    document.body.classList.add('mobile-menu-open');
    button.setAttribute('aria-expanded', 'true');
    panel.setAttribute('aria-modal', 'true');
    closeButton.focus();
  }
  button.addEventListener('click', () => {
    if (document.body.classList.contains('mobile-menu-open')) closeMenu();
    else openMenu();
  });
  closeButton.addEventListener('click', () => {closeMenu(); button.focus();});
  backdrop.addEventListener('click', () => {closeMenu(); button.focus();});
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && document.body.classList.contains('mobile-menu-open')) {
      closeMenu(); button.focus();
    }
  });
  // Character detail is nested inside this sidebar; keep it visible when
  // a Party card opens its full-screen sheet, then return to the same HUD.
  window.backroomMobileHud = {open: openMenu, close: closeMenu};
})();
