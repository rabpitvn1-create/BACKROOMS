(function(){
  'use strict';
  if (window.__gmChoiceUiInstalled) return;
  window.__gmChoiceUiInstalled = true;

  var style = document.createElement('style');
  style.textContent = [
    "@font-face{font-family:'Play';font-style:normal;font-weight:400;src:url('file:///android_asset/fonts/Play-Regular.ttf') format('truetype');font-display:swap}",
    "@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/Play-Bold.ttf') format('truetype');font-display:swap}",
    ".message.gm .role{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700}",
    ".message.gm .gm-main-text{font-family:'Play','Pretendard Std',system-ui,sans-serif}",
    ".semantic{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700;text-decoration:none}",
    ".semantic-character{color:#67d5ff}",
    ".semantic-lucia-name{font-family:'Pretendard Std',system-ui,sans-serif}",
    ".semantic-entity{color:#ff6b6b}",
    ".semantic-item{color:#f6c85f}",
    ".semantic-skill{color:#c792ea}",
    ".semantic-effect{color:#ff9f43}",
    ".semantic-location{color:#7bd88f}",
    ".semantic-stat{color:#ffd166}",
    ".semantic-damage{color:#ff5c5c}",
    ".semantic-buff{color:#73e6a2}",
    ".message.gm .semantic-effect,.message.gm .semantic-damage,.message.gm .semantic-buff{font-family:inherit}",
    ".semantic-generic{color:#e5e9ed}",
    ".gm-choice:disabled .semantic{opacity:.72}",
    ".gm-main-text{white-space:pre-wrap;line-height:1.55}",
    ".battle-log{display:grid;gap:5px;margin-top:10px}",
    ".battle-line{white-space:pre-wrap;line-height:1.45}",
    ".gm-choices{display:grid;gap:7px;margin-top:12px}",
    ".gm-choice{width:100%;text-align:left;padding:11px 12px;background:#171d22;border:1px solid #39424a;color:#f0f3f5;font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:400;letter-spacing:normal;text-transform:none;white-space:normal;line-height:1.4;border-radius:8px}",
    ".gm-choice:disabled{opacity:.62}",
    ".gm-system-loading{border-left-color:#65717a;background:#111519}",
    ".gm-system-error{border-left-color:#a95f5f;background:#181112}",
    ".gm-system-error .text{color:#e7c6c6}",
    ".gm-choice.selected{border-color:#7a858e;background:#20272d}",
    ".composer.battle-locked textarea{background:#101316;color:#697178;border-color:#262d33}",
    ".composer.battle-locked #submit{background:#24282c;color:#777e84;border-color:#30353a;opacity:.7}",
    ".message.gm{border-left-color:#59646d;border-radius:0}",
    ".battle-separator{height:1px;background:#262d33;margin-top:10px}",
    ".combat-dice-panel[hidden]{display:none}.combat-dice-panel{width:100%;box-sizing:border-box;margin-top:12px;background:#0e1114;border:1px solid #46515a;padding:14px;display:grid;gap:12px;touch-action:manipulation;border-radius:10px}",
    ".combat-dice-title{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-size:14px;font-weight:700;letter-spacing:.06em}.combat-dice-meta{font-size:11px;color:#9ba6af}",
    ".combat-dice-row{display:grid;grid-template-columns:repeat(5,1fr);gap:5px;perspective:720px}.combat-die{padding:1px;aspect-ratio:1/1;border:1px solid #343d45;background:#151a1f;display:grid;place-items:center;min-width:0;border-radius:9px;transform-style:preserve-3d;will-change:transform}.combat-die img{width:104%;height:104%;object-fit:contain;pointer-events:none}.combat-die.held{border-color:#f6c85f;background:#211e14;box-shadow:inset 0 0 0 1px #f6c85f55}.combat-die.rolling{border-color:#72808b;box-shadow:0 0 12px #91a1ad33;animation:combat-die-roll .46s cubic-bezier(.25,.7,.35,1) infinite}.combat-die:disabled{opacity:.85}@keyframes combat-die-roll{0%{transform:rotateX(0deg) rotateY(0deg) rotateZ(0deg) scale(.94)}25%{transform:rotateX(120deg) rotateY(70deg) rotateZ(45deg) scale(1.04)}50%{transform:rotateX(230deg) rotateY(160deg) rotateZ(120deg) scale(.96)}75%{transform:rotateX(320deg) rotateY(260deg) rotateZ(220deg) scale(1.04)}100%{transform:rotateX(360deg) rotateY(360deg) rotateZ(360deg) scale(.94)}}@media(prefers-reduced-motion:reduce){.combat-die.rolling{animation:none}}",
    ".combat-dice-result{min-height:22px;text-align:center;font-family:'Play','Pretendard Std',system-ui,sans-serif;font-size:16px;font-weight:700;color:#f6c85f}",
    ".combat-dice-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.combat-roll,.combat-finish{width:100%;padding:12px 8px;background:#1b2126;border:1px solid #46515a;color:#f0f3f5;font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700;letter-spacing:.12em;border-radius:8px}.combat-roll:disabled,.combat-finish:disabled{opacity:.45}"
  ].join('');
  document.head.appendChild(style);

  var log = document.getElementById('log');
  var form = document.getElementById('form');
  var action = document.getElementById('action');
  var submit = document.getElementById('submit');
  var status = document.getElementById('status');
  var defaultPlaceholder = action ? action.getAttribute('placeholder') : '';
  window.__combatBusy = false;
  var explorerChoiceBusy = false;
  window.__combatAnimationToken = 0;
  var COMBAT_PHASE_MS = 1000;

  var semanticPriority = {generic:0,location:1,item:2,effect:3,skill:4,character:5,entity:6};
  var knownSkills = [
    'Huyết Ma Tứ Liên','Ma Tâm Trấn Hồn','Huyết Ảnh Ma Độn','Thiên Ma Bộ','Huyết Ma Nhị Thập Tứ Trảm',
    'Twosome Time','Rain Storm','Honeycomb Fire','Charged Shot',
    'Rift Sever','Crimson Guillotine','Lucifer Breaker','Spatial Dominion',
    'M4A1 Joint Attack','Toxic Burst','Armor-Piercing Burst','Concussive Burst','Rending Burst','Corrosive Burst','Too Young To Die',
    'Tịch Quang Hợp Kích','Tịch Quang Phản Kiếm','Nhất Tuyến Phá Vọng','Thiên Kiếm Chấn','Bạch Hồng Quán Nhật','Vạn Kiếm Quy Tâm','Thiên Kiếm Định Giới'
  ];
  var knownEffects = ['Choáng','Chảy máu','Trúng độc','Xuyên giáp','Phá giáp','Né tránh','Mất phương hướng'];
  var knownHandTokens = ['[NO HAND]','[PAIR]','[TWO PAIR]','[TRIPLE]','[STRAIGHT]','[FULL HOUSE]','[F.O.A.K]','[SSF]','[FSF]'];

  function normalizeSemanticType(value) {
    var type = String(value || '').trim().toLowerCase();
    if (type === 'npc' || type === 'ally' || type === 'player') type = 'character';
    if (type === 'enemy' || type === 'monster' || type === 'quái vật') type = 'entity';
    if (type === 'level' || type === 'area' || type === 'zone' || type === 'place') type = 'location';
    if (type === 'status' || type === 'buff' || type === 'debuff') type = 'effect';
    return semanticPriority.hasOwnProperty(type) ? type : 'generic';
  }

  function addTerm(map, value, type) {
    if (value === null || value === undefined) return;
    var text = String(value).trim();
    if (text.length < 2) return;
    var normalizedType = normalizeSemanticType(type);
    var key = text.toLocaleLowerCase('vi');
    var previous = map.get(key);
    if (!previous || semanticPriority[normalizedType] > semanticPriority[previous.type]) {
      map.set(key, {text:text,type:normalizedType});
    }
  }

  function addHighlightList(map, list) {
    (Array.isArray(list) ? list : []).forEach(function(x){
      if (typeof x === 'string') addTerm(map, x, 'generic');
      else if (x) addTerm(map, x.text || x.name, x.type || x.kind || 'generic');
    });
  }

  function entryHighlights(entry) {
    var map = new Map();
    ['Cao Minh','Vạn Giới Ma Tôn','Lucia Lục','Hứa Thuý Mai','Syvial','Lục Trầm'].forEach(function(x){ addTerm(map,x,'character'); });
    knownEffects.forEach(function(x){ addTerm(map,x,'effect'); });
    knownSkills.forEach(function(x){ addTerm(map,x,'skill'); });
    knownHandTokens.forEach(function(x){ addTerm(map,x,'stat'); });

    try {
      if (state && state.player) addTerm(map, state.player.name, 'character');
      if (state && state.location) {
        var locationHead = String(state.location).split('—')[0];
        locationHead.split('/').forEach(function(x){ addTerm(map, x, 'location'); });
      }
      if (state && Array.isArray(state.party)) state.party.forEach(function(x){ addTerm(map, typeof x === 'string' ? x : x && (x.name || x.id), 'character'); });
      if (state && Array.isArray(state.inventory)) state.inventory.forEach(function(x){ addTerm(map, typeof x === 'string' ? x : x && x.name, 'item'); });
      if (state && state.combat) {
        addTerm(map, state.combat.currentActor, 'character');
        if (state.combat.entity) addTerm(map, state.combat.entity.name, 'entity');
        if (state.combat.currentSkill) addTerm(map, state.combat.currentSkill.name, 'skill');
      }
    } catch (_) {}

    addHighlightList(map, entry && entry.highlights);
    return Array.from(map.values()).sort(function(a,b){ return b.text.length-a.text.length; });
  }

  function escapeRegex(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function appendRichText(container, text, entry, extraHighlights) {
    var source = text === null || text === undefined ? '' : String(text);
    var baseTerms = entryHighlights(entry);
    var termMap = new Map();
    baseTerms.forEach(function(x){ addTerm(termMap, x.text, x.type); });
    addHighlightList(termMap, extraHighlights || []);
    var terms = Array.from(termMap.values()).sort(function(a,b){ return b.text.length-a.text.length; });
    var lookup = new Map();
    terms.forEach(function(x){ lookup.set(x.text.toLocaleLowerCase('vi'), x.type); });

    var patterns = terms.map(function(x){ return escapeRegex(x.text); });
    patterns.push('[+-]\\d+(?:\\.\\d+)?%?\\s*(?:HP|DEF)');
    patterns.push('(?:HP\\s*\\d+\\s*\\/\\s*\\d+|\\d+\\s*\\/\\s*\\d+\\s*HP)');
    patterns.push('Level\\s+\\d+(?:\\s*[-–—/]\\s*[A-Za-zÀ-ỹ0-9 _]+)?');
    var re = new RegExp('(' + patterns.join('|') + ')', 'gi');
    var cursor = 0;
    var match;
    while ((match = re.exec(source)) !== null) {
      if (match.index > cursor) container.appendChild(document.createTextNode(source.slice(cursor, match.index)));
      var matched = match[0];
      var type = lookup.get(matched.toLocaleLowerCase('vi')) || '';
      if (!type) {
        if (/^-/.test(matched) && /(?:HP|DEF)$/i.test(matched)) type = 'damage';
        else if (/^\+/.test(matched) && /(?:HP|DEF)$/i.test(matched)) type = 'buff';
        else if (/^(?:HP\s*\d+\s*\/\s*\d+|\d+\s*\/\s*\d+\s*HP)$/i.test(matched)) type = 'stat';
        else if (/^Level\s+\d+/i.test(matched)) type = 'location';
        else type = 'generic';
      }
      var span = document.createElement('span');
      span.className = 'semantic semantic-' + type;
      if (matched.toLocaleLowerCase('vi') === 'lucia lục') span.className += ' semantic-lucia-name';
      span.textContent = matched;
      container.appendChild(span);
      cursor = match.index + matched.length;
      if (matched.length === 0) re.lastIndex++;
    }
    if (cursor < source.length) container.appendChild(document.createTextNode(source.slice(cursor)));
  }

  function lastGmIndex() {
    if (!state || !Array.isArray(state.log)) return -1;
    for (var i = state.log.length - 1; i >= 0; i--) {
      var entry = state.log[i];
      if (entry && entry.role !== 'player' && entry.scope !== 'environment') return i;
    }
    return -1;
  }

  function submitExplorerChoice(entry, choice) {
    if (!choice || explorerChoiceBusy || window.__combatBusy || (state.combat && state.combat.active)) return;
    var text = String(choice.action || choice.text || '').trim();
    if (!text || !form || !action) return;
    explorerChoiceBusy = true;
    if (typeof window.render === 'function') window.render();
    action.value = text;
    if (typeof form.requestSubmit === 'function') form.requestSubmit();
    else form.dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
  }

  function chestPresent() {
    try { return !!(state && state.flags && state.flags.chestPresent === true); } catch (_) { return false; }
  }

  function deathRestartPending() {
    try {
      var combat = state && state.combat;
      return !!(combat && combat.active !== true && combat.outcome === 'defeat'
        && combat.deathRestartPending === true);
    } catch (_) { return false; }
  }

  function submitDeathRestart() {
    if (!deathRestartPending() || window.__combatBusy) return;
    if (!window.Android || typeof Android.restartAfterDeath !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android death restart bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (typeof window.render === 'function') window.render();
    Android.restartAfterDeath();
  }

  function fallbackExplorerChoice() {
    if (chestPresent()) {
      return {id:'A',text:'Mở chiếc rương vừa phát hiện',action:'__loot:open_chest'};
    }
    try {
      if (state && state.levelRoute && state.levelRoute.exitAvailable === true) {
        return {id:'A',text:'Tiến qua ranh giới vừa được tìm thấy',action:'Tiếp tục qua ranh giới'};
      }
    } catch (_) {}
    return {id:'A',text:'Tiếp tục thăm dò khu vực phía trước',action:'Khám phá'};
  }

  function displayedExplorerChoices(entry) {
    var generated = entry && Array.isArray(entry.choices) ? entry.choices : [];
    var choice = generated.length ? generated[0] : fallbackExplorerChoice();
    return choice ? [Object.assign({}, choice)] : [];
  }

  function submitChestChoice() {
    if (!chestPresent() || window.__combatBusy || (state.combat && state.combat.active)) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (status) status.textContent = 'Đang mở Rương…';
    if (typeof window.render === 'function') window.render();
    Android.submitTurn(JSON.stringify(state), '__loot:open_chest');
  }


  function makeChoiceButton(prefix, text, entry, extra, disabled, selected, onClick) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'gm-choice' + (selected ? ' selected' : '');
    button.disabled = !!disabled;
    button.appendChild(document.createTextNode(prefix ? prefix + '. ' : '• '));
    appendRichText(button, text, entry, extra);
    button.addEventListener('click', onClick);
    return button;
  }

  function appendBattleSection(article, entry, index) {
    if (Array.isArray(entry.battleLog) && entry.battleLog.length) {
      var separator = document.createElement('div');
      separator.className = 'battle-separator';
      article.appendChild(separator);
      var battleLog = document.createElement('div');
      battleLog.className = 'battle-log';
      entry.battleLog.forEach(function(line){
        var row = document.createElement('div');
        row.className = 'battle-line';
        if (typeof line === 'string') appendRichText(row, line, entry, []);
        else appendRichText(row, line && line.text, entry, line && line.highlights);
        battleLog.appendChild(row);
      });
      article.appendChild(battleLog);
    }
  }

  function appendExplorerChoices(article, entry, index) {
    if (!entry || (state.combat && state.combat.active)) return;
    var latest = index === lastGmIndex();
    var deathRestart = !!(state && state.combat && state.combat.active !== true
      && state.combat.outcome === 'defeat' && state.combat.deathRestartPending === true);

    if (latest && deathRestart) {
      var restartBox = document.createElement('div');
      restartBox.className = 'gm-choices death-restart';
      var restartButton = document.createElement('button');
      restartButton.type = 'button';
      restartButton.className = 'gm-choice';
      restartButton.textContent = 'BẮT ĐẦU LẠI TỪ ĐẦU LEVEL';
      restartButton.disabled = !!window.__combatBusy;
      restartButton.addEventListener('click', submitDeathRestart);
      restartBox.appendChild(restartButton);
      article.appendChild(restartBox);
      return;
    }

    var choices = latest && !(state.combat && state.combat.active)
        ? displayedExplorerChoices(entry) : [];
    if (!choices.length) return;

    var actionable = latest && !(state.combat && state.combat.active)
      && !explorerChoiceBusy && !window.__combatBusy && !(typeof busy !== 'undefined' && busy);
    var box = document.createElement('div');
    box.className = 'gm-choices explorer-choices';

    choices.slice(0, 1).forEach(function(choice){
      var disabled = !actionable || !!choice.disabled || !!choice.selected;
      box.appendChild(makeChoiceButton('', choice.text || choice.action || '', entry,
        choice.highlights || [], disabled, !!choice.selected,
        function(){ submitExplorerChoice(entry, choice); }));
    });
    article.appendChild(box);
  }

  function captureLogAnchor() {
    if (!log) return null;
    var logRect = log.getBoundingClientRect();
    var messages = log.querySelectorAll('.message[data-log-index]');
    for (var i = 0; i < messages.length; i++) {
      var rect = messages[i].getBoundingClientRect();
      var bottom = Number.isFinite(rect.bottom) ? rect.bottom : rect.top + (messages[i].offsetHeight || 0);
      if (bottom > logRect.top + 1) {
        return {
          logIndex: messages[i].dataset.logIndex,
          offset: rect.top - logRect.top,
          scrollTop: log.scrollTop
        };
      }
    }
    return {scrollTop: log.scrollTop};
  }

  function restoreLogAnchor(anchor) {
    if (!log || !anchor) return;
    requestAnimationFrame(function(){
      if (anchor.logIndex !== undefined) {
        var message = log.querySelector('.message[data-log-index="' + anchor.logIndex + '"]');
        if (message) {
          var logRect = log.getBoundingClientRect();
          var messageRect = message.getBoundingClientRect();
          log.scrollTop += (messageRect.top - logRect.top) - anchor.offset;
          return;
        }
      }
      log.scrollTop = Math.max(0, Math.min(anchor.scrollTop || 0, log.scrollHeight - log.clientHeight));
    });
  }

  function appendGmSystemMessage(className, message) {
    var article = document.createElement('article');
    article.className = 'message gm ' + className;
    var role = document.createElement('div');
    role.className = 'role';
    role.textContent = 'GAME MASTER';
    article.appendChild(role);
    var text = document.createElement('div');
    text.className = 'text gm-main-text';
    text.textContent = message;
    article.appendChild(text);
    log.appendChild(article);
  }

  function renderSemanticLog(anchor) {
    if (!log || !state || !Array.isArray(state.log)) return;
    log.textContent = '';
    state.log.forEach(function(entry, index){
      if (!entry) return;
      var article = document.createElement('article');
      var player = entry.role === 'player';
      article.className = 'message ' + (player ? 'player' : 'gm');
      article.dataset.logIndex = String(index);
      var role = document.createElement('div');
      role.className = 'role';
      role.textContent = player ? 'BẠN' : 'GAME MASTER';
      article.appendChild(role);
      var text = document.createElement('div');
      text.className = 'text gm-main-text';
      var displayText = player && entry.text === '__loot:open_chest' ? 'Mở rương' : entry.text;
      appendRichText(text, displayText || '', entry, []);
      article.appendChild(text);
      if (!player) {
        appendBattleSection(article, entry, index);
        appendCombatDiceSection(article, index);
        appendExplorerChoices(article, entry, index);
      }
      log.appendChild(article);
    });
    if (window.__gmEnvironmentLoading)
      appendGmSystemMessage('gm-system-loading', 'Đang xử lý hành động môi trường…');
    if (window.__gmErrorMessage)
      appendGmSystemMessage('gm-system-error', window.__gmErrorMessage);
    restoreLogAnchor(anchor);
  }

  function scrollLatestGmToStart() {
    if (!log) return;
    requestAnimationFrame(function(){
      var messages = log.querySelectorAll('.message.gm');
      if (!messages.length) return;
      var latest = messages[messages.length - 1];
      var logRect = log.getBoundingClientRect();
      var messageRect = latest.getBoundingClientRect();
      var target = log.scrollTop + (messageRect.top - logRect.top);
      log.scrollTop = Math.max(0, target);
    });
  }

  function scrollCombatToBottom() {
    if (!log) return;
    requestAnimationFrame(function(){ log.scrollTop = log.scrollHeight; });
  }

  function scrollGmSystemMessage() {
    if (!log) return;
    requestAnimationFrame(function(){
      var message = log.querySelector('.gm-system-error');
      if (!message) return;
      var logRect = log.getBoundingClientRect();
      var messageRect = message.getBoundingClientRect();
      log.scrollTop += messageRect.top - logRect.top;
    });
  }

  function scrollForCurrentMode() {
    if (state && state.combat && state.combat.active) scrollCombatToBottom();
    else scrollLatestGmToStart();
  }

  window.backroomScrollLatestGmToStart = scrollLatestGmToStart;
  window.backroomScrollCombatToBottom = scrollCombatToBottom;
  window.backroomScrollGmSystemMessage = scrollGmSystemMessage;
  window.backroomScrollForCurrentMode = scrollForCurrentMode;

  function syncComposer() {
    if (!form || !action || !submit) return;
    var combat = !!(state && state.combat && state.combat.active);
    var deathLocked = deathRestartPending();
    form.classList.toggle('battle-locked', combat || deathLocked);
    action.disabled = combat || deathLocked;
    action.readOnly = combat || deathLocked;
    if (combat) {
      action.value = '';
      action.placeholder = 'Đang chiến đấu — hoàn tất Poker Dice trong khung bên trên.';
      submit.disabled = true;
    } else if (deathLocked) {
      action.value = '';
      action.placeholder = 'Cao Minh đã gục ngã — bắt đầu lại từ đầu Level trong khung GAME MASTER.';
      submit.disabled = true;
    } else {
      action.placeholder = defaultPlaceholder || 'Cao Minh tương tác gì với môi trường hiện tại?';
      submit.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && !!busy);
    }
  }

  var dicePanel=document.createElement('section');
  dicePanel.className='combat-dice-panel';
  dicePanel.hidden=true;
  dicePanel.setAttribute('aria-label','Poker Dice Combat');
  dicePanel.innerHTML='<div class="combat-dice-title" id="combatDiceTitle"></div><div class="combat-dice-meta" id="combatDiceMeta"></div><div class="combat-dice-row" id="combatDiceRow"></div><div class="combat-dice-result" id="combatDiceResult"></div><div class="combat-dice-actions"><button type="button" class="combat-roll" id="combatDiceRoll">ROLL</button><button type="button" class="combat-finish" id="combatDiceFinish">FINISH</button></div>';
  var diceTitle=dicePanel.querySelector('#combatDiceTitle');
  var diceMeta=dicePanel.querySelector('#combatDiceMeta');
  var diceRow=dicePanel.querySelector('#combatDiceRow');
  var diceResult=dicePanel.querySelector('#combatDiceResult');
  var diceRoll=dicePanel.querySelector('#combatDiceRoll');
  var diceFinish=dicePanel.querySelector('#combatDiceFinish');
  var finalizeTimer=0;
  var finalizeKey='';
  var diceRollAnimating=false;
  var diceRollStartedAt=0;
  var diceRollToken=0;
  var DICE_ROLL_ANIMATION_MS=650;

  function diceAsset(value){
    return 'file:///android_asset/dice/die-'+String(value)+'.svg';
  }

  function allHeld(values){
    if(!Array.isArray(values)||values.length!==5)return false;
    for(var i=0;i<5;i++)if(values[i]!==true)return false;
    return true;
  }

  function handLabel(hand){
    var labels={
      'NO HAND':'No Hand',
      'ONE PAIR':'One Pair',
      'TWO PAIR':'Two Pair',
      'THREE OF A KIND':'Three of a Kind',
      'STRAIGHT':'Straight',
      'FULL HOUSE':'Full House',
      'FOUR OF A KIND':'Four of a Kind',
      'SSF':'SSF',
      'FSF':'FSF'
    };
    var key=String(hand||'NO HAND');
    return labels[key]||key;
  }

  function combatDiceState(){
    return state&&state.combat&&state.combat.active&&state.combat.diceState?state.combat.diceState:null;
  }

  function combatLogIndex(){
    if(!state||!Array.isArray(state.log))return -1;
    var combat=state.combat||{};
    var index=Number(combat.logIndex);
    if(Number.isInteger(index)&&index>=0&&index<state.log.length){
      var entry=state.log[index];
      if(entry&&entry.role!=='player')return index;
    }
    return lastGmIndex();
  }

  function appendCombatDiceSection(article,index){
    if(!state||!state.combat||!state.combat.active)return;
    if(index!==combatLogIndex())return;
    article.appendChild(dicePanel);
  }

  function sendCombatHold(index,held){
    if(window.__combatBusy||!window.Android||typeof Android.combatHold!=='function')return;
    window.__combatBusy=true;
    renderCombatPanel();
    Android.combatHold(JSON.stringify(state),index,!!held);
  }

  function sendCombatRoll(){
    if(window.__combatBusy||!window.Android||typeof Android.combatRoll!=='function')return;
    window.__combatBusy=true;
    diceRollAnimating=true;
    diceRollStartedAt=Date.now();
    ++diceRollToken;
    renderCombatPanel();
    Android.combatRoll(JSON.stringify(state));
  }

  function sendCombatFinish(){
    if(window.__combatBusy||!window.Android||typeof Android.combatFinish!=='function')return;
    window.__combatBusy=true;
    renderCombatPanel();
    Android.combatFinish(JSON.stringify(state));
  }

  function scheduleCombatResolve(combat,dice){
    var key=String(combat.round||1)+':'+String(combat.actorIndex||0)+':'+String(combat.rngSequence||0)+':'+String(dice.hand||'');
    if(finalizeKey===key&&finalizeTimer)return;
    if(finalizeTimer)clearTimeout(finalizeTimer);
    finalizeKey=key;
    finalizeTimer=setTimeout(function(){
      finalizeTimer=0;
      if(window.__combatBusy||!state||!state.combat||!state.combat.active)return;
      var current=state.combat.diceState;
      if(!current||current.finalized!==true||current.resolved===true)return;
      if(!window.Android||typeof Android.combatResolve!=='function')return;
      window.__combatBusy=true;
      renderCombatPanel();
      Android.combatResolve(JSON.stringify(state));
    },2000);
  }

  function renderCombatPanel(){
    var combat=state&&state.combat;
    var dice=combatDiceState();
    var visible=!!dice&&!window.__combatFeedbackBusy;
    dicePanel.hidden=!visible;
    if(!visible)return;

    diceTitle.textContent=String(combat.currentActor||'Nhân vật');
    var hasRolled=dice.hasRolled===true;
    var rerolls=Math.max(0,Number(dice.rerollsUsed)||0);
    var maxRerolls=Math.max(0,Number(dice.maxRerolls)||3);
    diceMeta.textContent='Lượt Quay '+String(rerolls)+'/'+String(maxRerolls)+' - Chạm Vào Xúc Xắc Để Giữ';

    diceRow.textContent='';
    var values=Array.isArray(dice.values)?dice.values:[0,0,0,0,0];
    var held=Array.isArray(dice.held)?dice.held:[false,false,false,false,false];
    for(var i=0;i<5;i++){
      (function(index){
        var button=document.createElement('button');
        button.type='button';
        var rolling=diceRollAnimating&&held[index]!==true;
        button.className='combat-die'+(held[index]===true?' held':'')+(rolling?' rolling':'');
        if(rolling)button.style.animationDelay=String(index*-55)+'ms';
        button.disabled=!hasRolled||dice.finalized===true||window.__combatBusy;
        button.setAttribute('aria-label','Die '+String(index+1)+(held[index]===true?' HOLD':''));
        var value=Number(values[index])||0;
        var visualValue=value>=1&&value<=6?value:(rolling?(index%6)+1:0);
        if(visualValue>=1&&visualValue<=6){
          var img=document.createElement('img');
          img.src=diceAsset(visualValue);
          img.alt=value>=1&&value<=6?String(value):'';
          button.appendChild(img);
        }else{
          var blank=document.createElement('span');blank.textContent='?';button.appendChild(blank);
        }
        button.addEventListener('click',function(){sendCombatHold(index,held[index]!==true);});
        diceRow.appendChild(button);
      })(i);
    }

    diceResult.textContent=hasRolled?handLabel(dice.hand):'';
    diceRoll.textContent='ROLL';
    diceFinish.textContent='FINISH';
    diceRoll.disabled=window.__combatBusy||dice.finalized===true||!hasRolled||rerolls>=maxRerolls||allHeld(held);
    diceFinish.disabled=window.__combatBusy||dice.finalized===true||!hasRolled;
    if(dice.finalized===true)scheduleCombatResolve(combat,dice);
  }

  diceRoll.addEventListener('click',sendCombatRoll);
  diceFinish.addEventListener('click',sendCombatFinish);

  window.backroomCombatDiceState=function(json){
    try{
      var nextState=JSON.parse(json);
      var applyDiceState=function(){
        diceRollAnimating=false;
        state=nextState;
        if(typeof CURRENT_CHARACTER_CANON!=='undefined')state.characterCanon=CURRENT_CHARACTER_CANON;
        window.__combatBusy=false;
        if(typeof busy!=='undefined')busy=false;
        if(typeof window.render==='function')window.render();
        syncComposer();
        renderCombatPanel();
        if(status){
          var dice=combatDiceState();
          status.textContent=dice&&dice.finalized===true
            ? 'Đã chốt '+String(dice.hand||'hand')+'.'
            : 'Poker Dice · '+String(state.combat&&state.combat.currentActor||'Nhân vật');
        }
      };
      if(diceRollAnimating){
        var token=diceRollToken;
        var wait=Math.max(0,DICE_ROLL_ANIMATION_MS-(Date.now()-diceRollStartedAt));
        if(wait>0){
          setTimeout(function(){if(token===diceRollToken)applyDiceState();},wait);
          return;
        }
      }
      applyDiceState();
    }catch(_){
      diceRollAnimating=false;
      ++diceRollToken;
      window.__combatBusy=false;
      if(status)status.textContent='Combat dice state không hợp lệ.';
    }
  };

  var previousRender = window.render;
  window.render = function(){
    var viewportAnchor = captureLogAnchor();
    if (typeof previousRender === 'function') previousRender();
    renderSemanticLog(viewportAnchor);
    syncComposer();
    if (state && state.combat && state.combat.active) scrollCombatToBottom();
    renderCombatPanel();
  };

  if (form) {
    form.addEventListener('submit', function(event){
      if (state && state.combat && state.combat.active) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (status) status.textContent = 'Đang chiến đấu. Hãy hoàn tất Poker Dice trong khung bên trên.';
        syncComposer();
      }
    }, true);
  }

  function latestGmScrollKey() {
    if (!state || !Array.isArray(state.log)) return '';
    var index = lastGmIndex();
    if (index < 0) return '';
    var entry = state.log[index] || {};
    return String(index) + '\\n' + String(entry.text || '');
  }

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    var previousGmScrollKey = latestGmScrollKey();
    explorerChoiceBusy = false;
    window.__combatBusy = false;
    if (typeof previousTurn === 'function') previousTurn(json);
    syncComposer();
    if ((state && state.combat && state.combat.active) || latestGmScrollKey() !== previousGmScrollKey)
      scrollForCurrentMode();
  };

  function playCombatPhase(events, phase) {
    var phaseEvents = (Array.isArray(events) ? events : []).filter(function(event){ return event && event.phase === phase; });
    phaseEvents.forEach(function(event, index){
      setTimeout(function(){
        if (typeof window.backroomPlayCombatFeedback === 'function') window.backroomPlayCombatFeedback(event);
      }, Math.min(index * 150, 450));
    });
  }

  function finishCombatAnimation(token) {
    if (token !== window.__combatAnimationToken) return;
    window.__combatFeedbackBusy = false;
    window.__combatBusy = false;
    if (typeof busy !== 'undefined') busy = false;
    if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
    if (typeof window.render === 'function') window.render();
    scrollForCurrentMode();
    if (status) {
      status.textContent = state.combat && state.combat.active
        ? 'Lượt chiến đấu ' + state.combat.round + ' · ' + state.combat.currentActor
        : (state.combat && state.combat.outcome === 'victory'
            ? 'Entity bị tiêu diệt. Bắt đầu Turn ' + state.turn + '.'
            : 'Chiến đấu kết thúc. Turn ' + state.turn + '.');
    }
  }

  window.backroomCombatTurn = function(json){
    try {
      var nextState = JSON.parse(json);
      state = nextState;
      if (typeof CURRENT_CHARACTER_CANON !== 'undefined') state.characterCanon = CURRENT_CHARACTER_CANON;
      if (action) action.value = '';

      var combat = state.combat || {};
      var events = Array.isArray(combat.feedbackEvents) ? combat.feedbackEvents : [];
      var hasResolvedActor = Number.isInteger(combat.resolvedActorIndex);
      if (!hasResolvedActor) {
        window.__combatBusy = false;
        if (typeof busy !== 'undefined') busy = false;
        if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
        if (typeof window.render === 'function') window.render();
        syncComposer();
        scrollForCurrentMode();
        return;
      }

      window.__combatFeedbackBusy = true;
      window.__combatBusy = true;
      if (typeof busy !== 'undefined') busy = true;
      var token = ++window.__combatAnimationToken;
      var entityKey = combat.entity && combat.entity.key ? combat.entity.key : '';
      if (typeof window.backroomSetCombatVisualActor === 'function') {
        window.backroomSetCombatVisualActor(combat.resolvedActorIndex, entityKey);
      }
      if (typeof window.render === 'function') window.render();
      syncComposer();
      renderCombatPanel();
      scrollCombatToBottom();
      if (status) status.textContent = 'Đang xử lý lượt của ' + (combat.resolvedActorName || 'nhân vật') + '…';

      playCombatPhase(events, 'actor');
      var delay = COMBAT_PHASE_MS;
      if (combat.resolvedEntityTurn === true) {
        setTimeout(function(){
          if (token !== window.__combatAnimationToken) return;
          if (status) status.textContent = 'Entity đang phản hồi…';
          playCombatPhase(events, 'entity');
        }, COMBAT_PHASE_MS);
        delay += COMBAT_PHASE_MS;
      }
      setTimeout(function(){ finishCombatAnimation(token); }, delay);
    } catch (error) {
      ++window.__combatAnimationToken;
      window.__combatFeedbackBusy = false;
      window.__combatBusy = false;
      if (typeof busy !== 'undefined') busy = false;
      if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
      if (status) status.textContent = 'Combat state không hợp lệ.';
      syncComposer();
    }
  };

  var previousError = window.backroomError;
  window.backroomError = function(message){
    explorerChoiceBusy = false;
    diceRollAnimating = false;
    ++diceRollToken;
    window.__combatFeedbackBusy = false;
    window.__combatBusy = false;
    if (typeof busy !== 'undefined') busy = false;
    if (typeof previousError === 'function') previousError(message);
    syncComposer();
    if (typeof window.render === 'function') window.render();
    scrollGmSystemMessage();
  };

  window.backroomPrefetchChoices = function(){
    if (!window.Android || typeof Android.prefetchChoices !== 'function' || !state
        || (state.combat && state.combat.active)
        || (state.flags && state.flags.entityEncounterKey)) return;
    var index = lastGmIndex();
    var entry = index < 0 ? null : state.log[index];
    if (!entry || index !== state.log.length - 1) return;
    var choices = displayedExplorerChoices(entry);
    if (choices.length !== 1) return;
    var actions = choices.map(function(choice, i){
      return {id:String.fromCharCode(65 + i),action:String(choice.action || choice.text || '').trim()};
    });
    if (actions.some(function(choice){return !choice.action;})) return;
    Android.prefetchChoices(JSON.stringify(actions));
  };

  window.render();
  scrollForCurrentMode();
  window.backroomPrefetchChoices();
})();
