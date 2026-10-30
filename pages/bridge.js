/* Browser host for the recovered GameCoreFacade. Gameplay rules run in game-core.js. */
(() => {
  'use strict';
  if (window.Android) return;
  const CORE_KEY = 'backrooms-web-core-v1';
  const assets = window.BACKROOMS_ASSETS || {};
  const config = window.BACKROOMS_CONFIG || {};
  const policy = assets['knowledge/level_travel_policy.json'];
  let chain = Promise.resolve(), bootError = null;
  const events = [];

  function check(value) {
    if (typeof value === 'string' && value.startsWith('{')) {
      let parsed;
      try { parsed = JSON.parse(value); } catch (_) {}
      if (parsed?.bridgeError) throw new Error(parsed.bridgeError);
    }
    return value;
  }
  function initialize(saved) {
    for (const [name, text] of Object.entries(assets)) window.coreAsset(name, text);
    check(window.coreInit(policy, saved || ''));
    bootError = null;
  }
  try { initialize(localStorage.getItem(CORE_KEY)); }
  catch (error) { bootError = error; }

  function persist() {
    const snapshot = check(window.coreCall('levelCoreSnapshot', '', '', '', ''));
    if (snapshot) localStorage.setItem(CORE_KEY, snapshot);
    else localStorage.removeItem(CORE_KEY);
  }
  function call(name, ...args) {
    if (bootError) {
      if (name === 'initializeLevelGame' && args[1] === true) initialize('');
      else throw bootError;
    }
    const result = check(window.coreCall(name, ...Array.from({ length: 4 }, (_, i) => String(args[i] ?? ''))));
    if (!['levelCoreSnapshot', 'coreStats'].includes(name)) persist();
    return result;
  }
  function object(name, ...args) { return JSON.parse(call(name, ...args)); }
  function emit(name, value) {
    if (typeof window[name] === 'function') window[name](typeof value === 'string' ? value : JSON.stringify(value));
  }
  function enqueue(job, errorCallback = 'backroomError') {
    chain = chain.then(job, job).catch(error => {
      const message = error?.message || 'Không thể xử lý hành động.';
      events.push({ kind: 'error', message, time: Date.now() });
      if (events.length > 40) events.shift();
      if (errorCallback === 'backroomCoreUpgrade' || errorCallback === 'backroomItemAction') {
        emit(errorCallback, { handled: false, error: message });
      } else emit(errorCallback, message);
    });
    return chain;
  }
  function record(event) { events.push(event); if (events.length > 40) events.shift(); }
  const generate = window.BackroomsProviders.create(config, { emit, record });
  async function gameplay(raw, kind, action) {
    if (!['SEARCH', 'EXECUTE', 'EXPLORE'].includes(kind)) throw new Error('action_kind_invalid');
    let result = object('processLevelAction', raw, action);
    if (result.handled) { emit('backroomTurn', result.state); return; }
    let before = result.state;
    result = object('processWorldPickup', JSON.stringify(before), action);
    if (result.handled) { emit('backroomTurn', result.state); return; }
    result = object('processNamedFollowerRule', JSON.stringify(before), action);
    if (result.handled) {
      const next = result.error ? result.state : object('finishLevelTurn', JSON.stringify(before), JSON.stringify(result.state), action);
      emit('backroomTurn', next); return;
    }
    const meta = window.coreMeta(action);
    if (!meta) {
      result = object('processRule', JSON.stringify(before), action);
      if (result.handled) {
        emit('backroomTurn', object('finishLevelTurn', JSON.stringify(before), JSON.stringify(result.state), action));
        return;
      }
      result = object('processExplorationLoot', JSON.stringify(before), action, kind);
      if (!result.handled || !result.state) throw new Error('core_loot_resolution_failed');
      before = result.state;
    }
    let rolls = JSON.parse(check(window.coreRolls(JSON.stringify(before), action, meta)));
    result = object('prepareNamedFollowerTurn', JSON.stringify(before), action, JSON.stringify(rolls), kind);
    before = result.state; rolls = result.rolls;
    if (meta) {
      const next = structuredClone(before);
      next.log = [...(next.log || []), { role: 'player', text: action }, { role: 'gm', text: 'Đây là lượt tra cứu. Trạng thái và lượt chơi không thay đổi.' }];
      emit('backroomTurn', next); return;
    }
    const prompt = check(window.corePrompt(JSON.stringify(before), action, JSON.stringify(rolls)));
    const generated = await generate(prompt);
    const next = JSON.parse(check(window.coreCandidate(JSON.stringify(before), JSON.stringify(generated), JSON.stringify(rolls), action)));
    persist();
    emit('backroomTurn', next);
  }
  function dice(method, ...args) {
    return enqueue(() => {
      const result = object(method, ...args);
      if (!result.handled) throw new Error(result.error || 'combat_dice_unavailable');
      emit('backroomCombatDiceState', result.state);
    }, 'backroomCombatDiceError');
  }
  window.Android = {
    initializeLevelGame(raw, fresh) { return call('initializeLevelGame', raw, fresh); },
    resumeLevelGame(raw, hasSave) { return call('resumeLevelGame', raw, hasSave); },
    levelCoreSnapshot() { return call('levelCoreSnapshot'); },
    resetCore() { call('clear'); },
    submitTurn(raw, action) { return this.submitGameplayAction(raw, 'EXECUTE', action); },
    submitGameplayAction(raw, kind, action) { return enqueue(() => gameplay(raw, kind, action)); },
    submitAction(raw, kind, action) {
      return enqueue(() => {
        const result = object('processCombat', raw, kind, action);
        if (!result.handled || !result.state) throw new Error(result.error || 'combat_action_unavailable');
        emit('backroomTurn', object('finishLevelTurn', raw, JSON.stringify(result.state), action));
      }, 'backroomCombatDiceError');
    },
    coreStats(raw, id) { return call('coreStats', raw, id); },
    coreUpgrade(raw, id, stat) { return enqueue(() => emit('backroomCoreUpgrade', call('processCoreUpgrade', raw, id, stat)), 'backroomCoreUpgrade'); },
    itemAction(raw, request) { return enqueue(() => emit('backroomItemAction', call('processItemAction', raw, request)), 'backroomItemAction'); },
    combatDicePrepare(raw, action) { return dice('prepareCombatDice', raw, action); },
    combatDiceHold(raw, index, held) { return dice('setCombatDiceHold', raw, index, held); },
    combatDiceRoll(raw) { return dice('rerollCombatDice', raw); },
    combatDiceFinish(raw) { return dice('finishCombatDice', raw); },
    continueAfterCombat(raw) {
      return enqueue(() => {
        const next = JSON.parse(raw);
        delete next.combatFeedback; delete next.postCombatContinuation;
        emit('backroomTurn', object('finishLevelTurn', raw, JSON.stringify(next), 'CONTINUE_AFTER_COMBAT'));
      });
    },
    exportDiagnosticLog(summary) {
      const data = JSON.stringify({ version: config.version, summary: JSON.parse(summary), events }, null, 2);
      const url = URL.createObjectURL(new Blob([data], { type: 'application/json' }));
      const a = document.createElement('a'); a.href = url; a.download = 'backrooms-web-diagnostics.json'; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
  };
  window.__webGame = { call, events, get idle() { return chain; }, get bootError() { return bootError?.message || null; } };
  document.documentElement.dataset.webMode = 'playable';
})();
