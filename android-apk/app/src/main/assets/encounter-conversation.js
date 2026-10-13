/* Meeting conversation is a normal player action, separate from Combat confirmation. */
(function () {
  'use strict';
  var previous = window.backroomRenderEncounteredCharacters;
  if (typeof previous !== 'function') return;

  window.backroomRenderEncounteredCharacters = function () {
    previous.apply(this, arguments);
    var box = document.getElementById('encounteredCharacters');
    if (!box || !state || !Array.isArray(state.encounteredCharacters)) return;

    state.encounteredCharacters.forEach(function (character, index) {
      if (!character.present) return;
      var row = box.children[index];
      if (!row) return;
      var talk = document.createElement('button');
      talk.type = 'button';
      talk.textContent = 'Trò chuyện';
      talk.dataset.characterId = character.id;
      talk.disabled = busy || !!window.__milestoneBlocked || !!state.pendingEncounter ||
        !!(state.combat && state.combat.active);
      talk.addEventListener('click', function () {
        if (talk.disabled || busy || !window.Android ||
            typeof Android.submitAction !== 'function') return;
        busy = true;
        syncPrimaryActions();
        talk.disabled = true;
        statusEl.textContent = 'Đang trò chuyện với ' + character.name + '…';
        try {
          Android.submitAction(JSON.stringify(state), 'EXECUTE',
            'Nói chuyện trực tiếp với ' + character.name);
        } catch (error) {
          busy = false;
          syncPrimaryActions();
          window.backroomRenderEncounteredCharacters();
          statusEl.textContent = 'Không thể bắt đầu hội thoại. Anh hãy thử lại.';
        }
      });
      row.appendChild(talk);
    });
  };
  window.backroomRenderEncounteredCharacters();
}());
