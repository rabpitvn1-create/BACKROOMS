(function(){
  'use strict';
  if (window.__playerActionUiInstalled) return;
  window.__playerActionUiInstalled = true;

  var modal = document.getElementById('playerActionModal');
  var openButton = document.getElementById('playerActionOpen');
  var closeButton = document.getElementById('playerActionClose');
  var cancelButton = document.getElementById('playerActionCancel');
  var backdrop = document.getElementById('playerActionBackdrop');
  var form = document.getElementById('form');
  var action = document.getElementById('action');
  var submit = document.getElementById('submit');
  if (!modal || !openButton || !form || !action) return;

  function combatActive(){
    try { return !!(state && state.combat && state.combat.active); }
    catch (_) { return false; }
  }

  function deathRestartPending(){
    try {
      var combat = state && state.combat;
      return !!(combat && combat.active !== true && combat.outcome === 'defeat'
        && combat.deathRestartPending === true);
    } catch (_) { return false; }
  }

  function processing(){
    return !!window.__combatBusy || (typeof busy !== 'undefined' && !!busy);
  }

  function actionLocked(){
    return combatActive() || deathRestartPending() || processing();
  }

  function fitVisualViewport(){
    if (modal.hidden) return;
    var vv = window.visualViewport;
    if (!vv) {
      modal.style.top = '0px';
      modal.style.height = '100%';
      return;
    }
    modal.style.top = Math.max(0, vv.offsetTop || 0) + 'px';
    modal.style.height = Math.max(1, vv.height || window.innerHeight) + 'px';
  }

  function resetViewport(){
    modal.style.removeProperty('top');
    modal.style.removeProperty('height');
  }

  function closePlayerAction(keepDraft){
    if (modal.hidden) return;
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('player-action-open');
    resetViewport();
    try { action.blur(); } catch (_) {}
    if (!keepDraft) action.value = '';
  }

  function openPlayerAction(){
    if (actionLocked()) return;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('player-action-open');
    fitVisualViewport();
    setTimeout(function(){
      try {
        action.focus({preventScroll:true});
        var length = action.value.length;
        action.setSelectionRange(length, length);
      } catch (_) {
        try { action.focus(); } catch (_) {}
      }
    }, 40);
  }

  function syncPlayerAction(){
    var locked = actionLocked();
    openButton.disabled = locked;
    openButton.setAttribute('aria-disabled', String(locked));
    if ((combatActive() || deathRestartPending()) && !modal.hidden) closePlayerAction(true);
  }

  form.addEventListener('submit', function(event){
    if (modal.hidden) return;
    event.preventDefault();
    event.stopImmediatePropagation();

    var text = String(action.value || '').trim();
    if (!text || actionLocked()) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (typeof window.backroomError === 'function') window.backroomError('Không tìm thấy Android GM bridge.');
      return;
    }

    window.__gmErrorMessage = '';
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    closePlayerAction(true);
    if (typeof window.render === 'function') window.render();
    Android.submitTurn(JSON.stringify(state), text);
  }, true);

  openButton.addEventListener('click', openPlayerAction);
  if (closeButton) closeButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (cancelButton) cancelButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (backdrop) backdrop.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });

  document.addEventListener('keydown', function(event){
    if (event.key === 'Escape' && !modal.hidden && !processing()) closePlayerAction(true);
  });

  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', fitVisualViewport);
    window.visualViewport.addEventListener('scroll', fitVisualViewport);
  }
  window.addEventListener('resize', fitVisualViewport);

  window.backroomOpenPlayerAction = openPlayerAction;
  window.backroomClosePlayerAction = closePlayerAction;
  window.backroomSyncPlayerAction = syncPlayerAction;

  var previousRender = window.render;
  window.render = function(){
    if (typeof previousRender === 'function') previousRender();
    syncPlayerAction();
  };

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    if (typeof previousTurn === 'function') previousTurn(json);
    closePlayerAction(false);
    syncPlayerAction();
  };

  var previousError = window.backroomError;
  window.backroomError = function(message){
    if (typeof previousError === 'function') previousError(message);
    syncPlayerAction();
  };

  var previousCommittedError = window.backroomCommittedError;
  window.backroomCommittedError = function(json){
    if (typeof previousCommittedError === 'function') previousCommittedError(json);
    syncPlayerAction();
  };

  syncPlayerAction();
})();
