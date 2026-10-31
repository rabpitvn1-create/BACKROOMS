/* Browser host for the recovered GameCoreFacade. Gameplay rules run in game-core.js. */
(() => {
  'use strict';
  if (window.Android) return;
  const CORE_KEY = 'backrooms-web-core-v1';
  const assets = window.BACKROOMS_ASSETS || {};
  const config = window.BACKROOMS_CONFIG || {};
  // Proxy Cloudflare Worker giấu API key: game gọi Worker, Worker tự gắn key từ secret.
  const PROXY_BASE_URL = 'https://backrooms-gemini-proxy.onrender.com';
  const policy = assets['knowledge/level_travel_policy.json'];
  let chain = Promise.resolve(), bootError = null;
  const events = [];
  const cooldowns = new Map();
  const resolvedDiceActions = new Set();

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
  // Không tự load save cũ khi mở game — mỗi lần mở là new game mới.
  // Save cũ vẫn nằm trong localStorage; người chơi bấm load trong UI
  // thì UI gọi resumeLevelGame / initializeLevelGame để quay lại vị trí cũ.
  try { initialize(''); }
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
  function parseAnswer(text) {
    let raw = String(text || '').trim().replace(/^```(?:json)?\s*|\s*```$/g, '');
    try { return JSON.parse(raw); }
    catch (_) {
      const first = raw.indexOf('{'), last = raw.lastIndexOf('}');
      if (first >= 0 && last > first) return JSON.parse(raw.slice(first, last + 1));
      throw new Error('Game Master chưa trả về JSON hợp lệ. Lượt chưa được thay đổi.');
    }
  }
  async function generate(prompt) {
    // API key nằm trên Cloudflare Worker (secret); client chỉ gọi proxy /gemini, không gửi key.
    const models = config.models || ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite'];
    const failures = [];
    // Worker tự xoay vòng 6 key Gemini khi gặp 429; client chỉ fallback qua các model.
    for (const model of models) {
      const slot = `proxy:${model}`;
      if ((cooldowns.get(slot) || 0) > Date.now()) continue;
      emit('backroomProvider', `Gemini · ${model}`);
      try {
        const response = await fetch(PROXY_BASE_URL + '/gemini', {
          method: 'POST', signal: AbortSignal.timeout(60000),
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model,
            contents: [{ role: 'user', parts: [{ text: prompt }] }],
            generationConfig: { responseMimeType: 'application/json', temperature: 0.8, maxOutputTokens: 4096 },
          }),
        });
        record({ kind: 'provider', alias: 'Gemini', model, status: response.status });
        const bodyText = await response.text().catch(()=> '');
        if (!response.ok) {
          failures.push(`Gemini ${model} HTTP ${response.status} ${bodyText.slice(0,300)}`);
          if ([403, 404, 429, 502].includes(response.status)) cooldowns.set(slot, Date.now() + (response.status === 429 ? 60000 : 300000));
          continue;
        }
        const payload = JSON.parse(bodyText);
        const text = (payload.candidates?.[0]?.content?.parts || []).filter(p => !p.thought).map(p => p.text || '').join('');
        const parsed = parseAnswer(text);
        if (typeof parsed.reply !== 'string' || !parsed.reply.trim()) throw new Error('Phản hồi chưa có lời kể.');
        return parsed;
      } catch (error) {
        failures.push(`Gemini ${model}: ${error.name === 'TimeoutError' ? 'hết thời gian chờ' : 'yêu cầu không hoàn tất'}`);
      }
    }
    throw new Error('Gemini hiện chưa xử lý được lượt: ' + [...new Set(failures)].slice(-6).join('; ') + '. Có thể thử lại.');
  }
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
        const snapshot = JSON.parse(raw);
        const combat = snapshot.combat;
        const diceState = combat?.diceState;
        const actionKey = combat?.active && diceState?.finalized
          ? String(combat.encounterId || '') + '|' + String(snapshot.turn || '') + '|' + String(diceState.action || '')
          : null;
        if (actionKey && resolvedDiceActions.has(actionKey)) {
          record({ kind: 'combat-repeat-blocked', time: Date.now() });
          return;
        }
        if (actionKey) {
          resolvedDiceActions.add(actionKey);
          if (resolvedDiceActions.size > 80)
            resolvedDiceActions.delete(resolvedDiceActions.values().next().value);
        }
        try {
          const result = object('processCombat', raw, kind, action);
          if (!result.handled || !result.state) throw new Error(result.error || 'combat_action_unavailable');
          emit('backroomTurn', object('finishLevelTurn', raw, JSON.stringify(result.state), action));
        } catch (error) {
          if (actionKey) resolvedDiceActions.delete(actionKey);
          throw error;
        }
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
