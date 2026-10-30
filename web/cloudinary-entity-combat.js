/* The Snapshot stage owns the single Entity image; this bridge only supplies wave identity. */
(function () {
  'use strict';
  var encounterId = '';
  var waveKey = '';
  function resetIfChanged() {
    var combat = typeof state !== 'undefined' && state && state.combat;
    var next = combat && combat.active === true ? String(combat.encounterId || '') : '';
    if (next !== encounterId) {
      encounterId = next;
      waveKey = '';
      window.backroomCombatEntityVisualKey = '';
    }
  }
  window.backroomCombatEntityVisualKey = '';
  window.backroomSwitchCombatEntityWave = function (key) {
    resetIfChanged();
    if (!encounterId || !window.backroomCloudinary || !window.backroomCloudinary.entity(key)) return;
    waveKey = String(key);
    window.backroomCombatEntityVisualKey = waveKey;
    if (window.backroomSnapshotStage) window.backroomSnapshotStage.render(state);
  };
  window.backroomRestoreEntityOverlay = function () {
    resetIfChanged();
    if (window.backroomSnapshotStage && typeof state !== 'undefined') window.backroomSnapshotStage.render(state);
  };
  var beforeTurn = window.backroomTurn;
  if (typeof beforeTurn === 'function') window.backroomTurn = function (json) {
    var result = beforeTurn.apply(this, arguments);
    resetIfChanged();
    if (window.backroomSnapshotStage && typeof state !== 'undefined') window.backroomSnapshotStage.render(state);
    return result;
  };
})();
