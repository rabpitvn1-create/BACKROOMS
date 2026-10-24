/* Read-only EXP/Level display. Combat and Character Detail state are Core-owned. */
(function () {
  'use strict';
  function update() {
    if (typeof state === 'undefined' || !state) return;
    var members = state.partyDetails && state.partyDetails.members;
    var first = Array.isArray(members) && members.find(function (m) { return m.id === 'cao-minh'; });
    var exp = first || state.characterProgression;
    var box = document.getElementById('playerStats');
    if (!box || !exp || !Number.isInteger(exp.level)) return;
    var row = box.querySelector('[data-character-progression]');
    if (!row) {
      row = document.createElement('div');
      row.className = 'row';
      row.setAttribute('data-character-progression', 'true');
      row.innerHTML = '<b>Level / EXP</b><span></span>';
      box.insertBefore(row, box.firstChild);
    }
    var left = Math.max(0,Number(exp.exp)||0),needed = Math.max(0,Number(exp.requiredExp)||0);
    row.querySelector('span').textContent = String(exp.level)+' · '+left+'/'+needed+' EXP';
  }
  var old = window.render;
  if (typeof old === 'function') window.render = function () {
    var result = old.apply(this, arguments);
    update();
    return result;
  };
  var turn = window.backroomTurn;
  if (typeof turn === 'function') window.backroomTurn = function () {
    var result = turn.apply(this, arguments);
    update();
    return result;
  };
  update();
})();
