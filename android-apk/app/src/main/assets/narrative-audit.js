(function(){
  if (window.__narrativeAuditInstalled) return;
  window.__narrativeAuditInstalled = true;
  if (!window.Android || typeof Android.narrativeAuditRecord !== 'function') return;

  var MAX_ROUNDS = 50;
  var actions = [
    'Cao Minh thả thần thức dò xét không gian xung quanh.',
    'Cao Minh dừng lại điều tức trong chốc lát rồi quan sát phía trước.',
    'Cao Minh dùng thần thức tìm điểm bất thường trong không gian.',
    'Cao Minh thử quay lại đoạn hành lang vừa đi qua.',
    'Cao Minh để Huyết Ma Kiếm lơ lửng bên cạnh và thăm dò xung quanh.',
    'Cao Minh dùng ma khí thăm dò bức tường gần nhất.',
    'Cao Minh đứng yên lắng nghe động tĩnh phía trước.',
    'Cao Minh quan sát kỹ các lối đi trước mặt.'
  ];

  var completed = 0;
  var pendingExplorer = false;
  var pendingMode = '';
  var pendingInput = '';
  var stopped = false;
  var errorCount = 0;

  function record(value) {
    try { Android.narrativeAuditRecord(JSON.stringify(value)); } catch (_) {}
  }

  function latestGm() {
    if (!state || !Array.isArray(state.log)) return null;
    for (var i = state.log.length - 1; i >= 0; i--) {
      var entry = state.log[i];
      if (entry && entry.role !== 'player' && entry.scope !== 'environment') return entry;
    }
    return null;
  }

  function latestChoiceText() {
    var gm = latestGm();
    if (!gm || !Array.isArray(gm.choices) || !gm.choices.length) return '';
    return String(gm.choices[0].text || gm.choices[0].action || '');
  }

  function finish() {
    if (stopped) return;
    stopped = true;
    record({done:true, rounds:completed, errors:errorCount});
  }

  function maybeRecordExplorer() {
    if (!pendingExplorer) return;
    pendingExplorer = false;
    completed++;
    var gm = latestGm() || {};
    record({
      round: completed,
      mode: pendingMode,
      input: pendingInput,
      gm: String(gm.text || ''),
      nextChoice: latestChoiceText(),
      levelKey: state && state.currentLevelKey != null ? String(state.currentLevelKey) : '',
      location: state && state.location ? String(state.location) : ''
    });
    if (completed >= MAX_ROUNDS) finish();
  }

  function driveCombat() {
    if (!state || !state.combat || state.combat.active !== true) return false;
    if (window.__combatBusy || window.__combatFeedbackBusy) return true;
    var dice = state.combat.diceState || {};
    try {
      if (dice.hasRolled !== true) {
        Android.combatRoll(JSON.stringify(state));
      } else if (dice.finalized !== true) {
        Android.combatFinish(JSON.stringify(state));
      } else if (dice.resolved !== true) {
        Android.combatResolve(JSON.stringify(state));
      }
    } catch (error) {
      errorCount++;
      record({type:'combat_error', message:String(error && error.message || error)});
    }
    return true;
  }

  function submitNextExplorer() {
    if (stopped || completed >= MAX_ROUNDS) return finish();
    if (!state || (typeof busy !== 'undefined' && busy) || window.__combatBusy) {
      setTimeout(drive, 250);
      return;
    }

    var death = state.combat && state.combat.active !== true
      && state.combat.outcome === 'defeat'
      && state.combat.deathRestartPending === true;
    if (death) {
      try { Android.restartAfterDeath(); } catch (_) {}
      setTimeout(drive, 500);
      return;
    }

    if (driveCombat()) {
      setTimeout(drive, 500);
      return;
    }

    var nextRound = completed + 1;
    pendingExplorer = true;
    if (nextRound % 2 === 1) {
      pendingMode = 'PLAYER_ACTION';
      pendingInput = actions[Math.floor((nextRound - 1) / 2) % actions.length];
      try {
        Android.submitTurn(JSON.stringify(state), pendingInput);
      } catch (error) {
        pendingExplorer = false;
        errorCount++;
        record({round:nextRound, type:'submit_error', mode:pendingMode,
          input:pendingInput, message:String(error && error.message || error)});
        setTimeout(drive, 500);
      }
      return;
    }

    var button = document.querySelector('.explorer-choices .gm-choice:not(:disabled)');
    pendingMode = 'CHOICE';
    pendingInput = button ? String(button.textContent || '').trim() : 'Khám phá';
    try {
      if (button) button.click();
      else Android.submitTurn(JSON.stringify(state), 'Khám phá');
    } catch (error) {
      pendingExplorer = false;
      errorCount++;
      record({round:nextRound, type:'submit_error', mode:pendingMode,
        input:pendingInput, message:String(error && error.message || error)});
      setTimeout(drive, 500);
    }
  }

  function drive() {
    if (stopped) return;
    if (completed >= MAX_ROUNDS) return finish();
    if (pendingExplorer) return;
    if (driveCombat()) {
      setTimeout(drive, 500);
      return;
    }
    submitNextExplorer();
  }

  var oldTurn = window.backroomTurn;
  window.backroomTurn = function(json) {
    if (typeof oldTurn === 'function') oldTurn(json);
    maybeRecordExplorer();
    setTimeout(drive, 250);
  };

  var oldDice = window.backroomCombatDiceState;
  window.backroomCombatDiceState = function(json) {
    if (typeof oldDice === 'function') oldDice(json);
    setTimeout(drive, 850);
  };

  var oldCombat = window.backroomCombatTurn;
  window.backroomCombatTurn = function(json) {
    if (typeof oldCombat === 'function') oldCombat(json);
    setTimeout(drive, 1800);
  };

  var oldError = window.backroomError;
  window.backroomError = function(message) {
    if (typeof oldError === 'function') oldError(message);
    pendingExplorer = false;
    errorCount++;
    record({type:'runtime_error', round:completed + 1, mode:pendingMode,
      input:pendingInput, message:String(message || '')});
    setTimeout(drive, 1000);
  };

  record({start:true, targetRounds:MAX_ROUNDS});
  setTimeout(drive, 1000);
})();