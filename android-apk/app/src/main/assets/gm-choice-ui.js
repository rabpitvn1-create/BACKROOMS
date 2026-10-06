(function(){
  'use strict';
  if (window.__gmChoiceUiInstalled) return;
  window.__gmChoiceUiInstalled = true;

  // Google Sans v14.000 (googlefonts/googlesans), packaged Latin/Vietnamese subset under OFL.
  var style = document.createElement('style');
  style.textContent = [
    "@font-face{font-family:'Google Sans';font-style:normal;font-weight:400;src:url('file:///android_asset/fonts/GoogleSans-Regular.woff') format('woff');font-display:swap}",
    "@font-face{font-family:'Google Sans';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/GoogleSans-Bold.woff') format('woff');font-display:swap}",
    "@font-face{font-family:'Play';font-style:normal;font-weight:400;src:url('file:///android_asset/fonts/Play-Regular.ttf') format('truetype');font-display:swap}",
    "@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/Play-Bold.ttf') format('truetype');font-display:swap}",
    ".message.gm .role{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700}",
    ".message.gm .gm-main-text{font-family:'Google Sans','Pretendard Std',system-ui,sans-serif;font-weight:400}",
    ".semantic{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700;text-decoration:none}",
    ".message.gm .gm-main-text .semantic{font-family:inherit;font-weight:700}",
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
    ".combat-dice-panel[hidden]{display:none}.combat-dice-panel{width:100%;box-sizing:border-box;margin-top:12px;position:relative;overflow:hidden;isolation:isolate;background:linear-gradient(145deg,rgba(26,28,24,.98),rgba(10,13,14,.99) 48%,rgba(17,20,18,.98));border:1px solid #6f6748;padding:14px;display:grid;gap:12px;touch-action:manipulation;border-radius:10px;box-shadow:inset 0 0 0 1px rgba(190,177,104,.08),inset 0 -24px 50px rgba(0,0,0,.28),0 10px 24px rgba(0,0,0,.25)}.combat-dice-panel:before{content:\"\";position:absolute;inset:0;z-index:0;pointer-events:none;opacity:.32;background:repeating-linear-gradient(90deg,transparent 0 47px,rgba(204,196,124,.035) 48px,transparent 49px),repeating-linear-gradient(0deg,transparent 0 31px,rgba(124,157,134,.03) 32px,transparent 33px)}.combat-dice-panel>*{position:relative;z-index:1}",
    ".combat-dice-title{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-size:14px;font-weight:700;letter-spacing:.09em;color:#efe8cf;text-shadow:0 1px 0 #000,0 0 10px rgba(194,178,103,.16)}.combat-dice-meta{font-size:11px;color:#9ea99f;letter-spacing:.02em}",
    ".combat-targets{display:flex;gap:6px;overflow-x:auto;padding-bottom:2px}.combat-target{flex:0 0 auto;max-width:180px;padding:7px 9px;border:1px solid #46483d;background:linear-gradient(180deg,rgba(27,31,28,.92),rgba(16,19,18,.92));color:#c8cec7;border-radius:7px;font-size:11px;text-align:left;box-shadow:inset 0 0 0 1px rgba(255,255,255,.015)}.combat-target.active{border-color:#b9a85d;color:#fff7d7;box-shadow:inset 0 0 0 1px rgba(185,168,93,.22),0 0 10px rgba(185,168,93,.12)}.combat-target:disabled{opacity:.42;text-decoration:line-through}",
    ".combat-dice-row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:3px;padding:8px 0 0}",
    ".combat-die{--die-delay:0ms;position:relative;min-width:44px;min-height:76px;padding:4px 0 24px;border:1px solid transparent;border-radius:12px;background:transparent;display:grid;place-items:center;overflow:visible;box-shadow:none;transition:background .18s,border-color .18s}",
    ".combat-die-shadow{position:absolute;left:16%;right:12%;bottom:24px;height:9px;border-radius:50%;background:#0008;filter:blur(4px);pointer-events:none}",
    ".combat-die-object{position:relative;width:100%;max-width:84px;aspect-ratio:1;display:grid;place-items:center;transform-origin:50% 65%;z-index:1}",
    ".combat-die-skin{display:block;width:100%;height:100%;object-fit:contain;pointer-events:none;filter:drop-shadow(0 3px 2px #0005)}",
    ".combat-die-unknown{width:78%;aspect-ratio:1;display:grid;place-items:center;border:1px solid #645b49;border-radius:10px;background:linear-gradient(145deg,#25261f,#151816);color:#ad9e79;font:700 24px/1 'Play','Pretendard Std',system-ui,sans-serif}",
    ".combat-die-hold-seal{position:absolute;bottom:5px;left:50%;transform:translateX(-50%);display:block;visibility:hidden;min-width:0;padding:3px 0;border:0;border-radius:0;background:transparent;color:#9be1bc;font:700 9px/1 'Pretendard Std',system-ui,sans-serif;letter-spacing:.04em;pointer-events:none}",
    ".combat-die.held{border-color:transparent;background:transparent;box-shadow:none}.combat-die.held .combat-die-hold-seal:before{content:\"\";display:inline-block;width:4px;height:4px;margin-right:4px;border-radius:50%;background:#82d9a9;vertical-align:middle}",
    ".combat-die.held .combat-die-hold-seal{visibility:visible}",
    ".combat-die:focus-visible{outline:2px solid #e8ca87;outline-offset:2px}",
    ".combat-die:active:not(:disabled) .combat-die-object{transform:translateY(2px)}",
    ".combat-die:disabled{opacity:.84}",
    ".combat-die.rolling .combat-die-shadow{animation:combat-die-shadow-roll .68s ease-in-out infinite;animation-delay:var(--die-delay)}",
    ".combat-die.settling .combat-die-object{animation:combat-die-settle .17s ease-out both}",
    "@keyframes combat-die-shadow-roll{0%,100%{transform:scaleX(1);opacity:.8}22%,74%{transform:scaleX(.65);opacity:.35}48%{transform:scaleX(.8);opacity:.55}}",
    "@keyframes combat-die-settle{0%{transform:translateY(-4px) scale(1.02)}60%{transform:translateY(1px) scale(.98)}100%{transform:translateY(0) scale(1)}}",
    "@media(prefers-reduced-motion:reduce){.combat-die,.combat-die-object{transition:none}.combat-die.rolling .combat-die-object,.combat-die.rolling .combat-die-shadow,.combat-die.settling .combat-die-object{animation:none}}",
    ".combat-dice-result{min-height:22px;text-align:center;font-family:'Play','Pretendard Std',system-ui,sans-serif;font-size:16px;font-weight:700;letter-spacing:.055em;color:#d8c477;text-shadow:0 1px 0 #000,0 0 11px rgba(216,196,119,.2)}",
    ".combat-dice-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.combat-roll,.combat-finish{width:100%;padding:12px 8px;color:#f2f4ef;font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700;letter-spacing:.12em;border-radius:8px}.combat-roll{background:linear-gradient(180deg,#1b3029,#13221e);border:1px solid #4f9479;box-shadow:inset 0 0 0 1px rgba(103,190,153,.09),0 4px 12px rgba(0,0,0,.16);color:#dff7ec}.combat-finish{background:linear-gradient(180deg,#30231d,#231815);border:1px solid #8b6047;box-shadow:inset 0 0 0 1px rgba(180,122,84,.08),0 4px 12px rgba(0,0,0,.16);color:#f2ddd0}.combat-roll:disabled,.combat-finish:disabled{opacity:.45}"
  ].join('');
  style.textContent += ".combat-die.rolling .combat-die-skin{visibility:hidden}\n.combat-die.rolling .combat-die-object:before{content:\"\";position:absolute;inset:0;background-image:url('file:///android_asset/dice/roll-3d.png');background-size:2400% 100%;background-repeat:no-repeat;animation:combat-die-3d .68s steps(23,end) infinite;animation-delay:var(--die-delay)}\n.combat-die.rolling .combat-die-object{animation:combat-die-toss .68s ease-in-out infinite;animation-delay:var(--die-delay)}\n@keyframes combat-die-3d{from{background-position:0% 0}to{background-position:100% 0}}\n@keyframes combat-die-toss{0%,100%{transform:translateY(0)}35%{transform:translateY(-7px)}70%{transform:translateY(-3px)}}\n@media(prefers-reduced-motion:reduce){.combat-die.rolling .combat-die-object{animation:none}.combat-die.rolling .combat-die-object:before{display:none;animation:none}.combat-die.rolling .combat-die-skin{visibility:visible}}\n";
  style.textContent += ".combat-die.held{--held-glow:#9be1bc}\n.combat-die.held .combat-die-skin{filter:drop-shadow(0 0 2px var(--held-glow)) drop-shadow(0 0 6px #9be1bc99) drop-shadow(0 0 11px #9be1bc55) drop-shadow(0 3px 2px #0005)}\n.combat-die.held .combat-die-hold-seal{color:var(--held-glow);text-shadow:0 0 7px #9be1bc88}\n.combat-die.held .combat-die-hold-seal:before{background:var(--held-glow);box-shadow:0 0 5px #9be1bcaa}\n";
  style.textContent += ".combat-dice-panel{background-color:#39341e;background-image:linear-gradient(145deg,rgba(15,17,13,.55),rgba(12,15,13,.74) 55%,rgba(15,17,13,.6)),url('file:///android_asset/dice/level0-wallpaper.svg');background-size:auto,64px 96px;border-color:#8b8052;box-shadow:inset 0 0 0 1px #c5b87818,inset 0 -24px 50px #0004,0 10px 24px #0004}\n.combat-dice-panel:before{opacity:.14;background:repeating-linear-gradient(0deg,transparent 0 2px,#e8dca21a 3px,transparent 4px)}\n.combat-dice-meta{color:#c5c3a8;text-shadow:0 1px 2px #000}\n.combat-roll,.combat-finish{transition:filter .1s,box-shadow .1s,transform .1s}\n.combat-roll:active:not(:disabled){transform:translateY(-2px);filter:brightness(1.3);box-shadow:inset 0 0 0 1px #a0ffd655,0 0 10px #69d8a888,0 0 22px #69d8a844}\n.combat-finish:active:not(:disabled){transform:translateY(-2px);filter:brightness(1.3);box-shadow:inset 0 0 0 1px #ffc39855,0 0 10px #cb926988,0 0 22px #cb926944}\n@media(prefers-reduced-motion:reduce){.combat-roll,.combat-finish{transition:none}}\n";
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
  var COMBAT_PHASE_MS = 1600;
  var COMBAT_SWAP_MS = 480;
  var COMBAT_EVENT_GAP_MS = 450;

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
        if (Array.isArray(state.combat.entities)) state.combat.entities.forEach(function(entity){ if(entity)addTerm(map,entity.name,'entity'); });
        else if (state.combat.entity) addTerm(map, state.combat.entity.name, 'entity');
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
    var displayText = String(choice.text || choice.action || '').trim();
    var coreAction = String(choice.action || displayText).trim();
    if (!displayText || !coreAction || !form || !action) return;
    explorerChoiceBusy = true;
    if (typeof window.render === 'function') window.render();
    action.value = coreAction;
    try {
      state.__uiDisplayAction = displayText;
      if (typeof form.requestSubmit === 'function') form.requestSubmit();
      else form.dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
    } finally {
      try { delete state.__uiDisplayAction; } catch (_) { state.__uiDisplayAction = ''; }
    }
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

  function contextualExplorerChoice() {
    if (chestPresent()) {
      return {id:'CTX',text:'Mở chiếc rương vừa phát hiện',action:'__loot:open_chest'};
    }
    try {
      if (state && state.levelRoute && state.levelRoute.exitAvailable === true) {
        return {id:'CTX',text:'Tiến qua ranh giới vừa được tìm thấy',action:'Đi qua ranh giới'};
      }
    } catch (_) {}
    return null;
  }

  function fixedExplorerChoices() {
    return [
      {id:'A',text:'Khám phá',action:'Khám phá'},
      {id:'B',text:'Tìm kiếm',action:'Tìm kiếm'}
    ];
  }

  function displayedExplorerChoices() {
    var choices = [];
    var contextual = contextualExplorerChoice();
    if (contextual) choices.push(contextual);
    fixedExplorerChoices().forEach(function(choice){
      choices.push(Object.assign({}, choice));
    });
    return choices.slice(0, 3);
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

    choices.slice(0, 3).forEach(function(choice){
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
  dicePanel.innerHTML='<div class="combat-dice-title" id="combatDiceTitle"></div><div class="combat-targets" id="combatTargets" aria-label="Entity targets"></div><div class="combat-dice-meta" id="combatDiceMeta"></div><div class="combat-dice-row" id="combatDiceRow"></div><div class="combat-dice-result" id="combatDiceResult"></div><div class="combat-dice-actions"><button type="button" class="combat-roll" id="combatDiceRoll">ROLL</button><button type="button" class="combat-finish" id="combatDiceFinish">FINISH</button></div>';
  var diceTitle=dicePanel.querySelector('#combatDiceTitle');
  var combatTargets=dicePanel.querySelector('#combatTargets');
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
  var diceSettleTimer=0;
  var diceSettleUntil=0;
  var diceSettleMask=[false,false,false,false,false];
  var DICE_ROLL_ANIMATION_MS=680;
  var DICE_SETTLE_ANIMATION_MS=170;

  function diceAsset(value){
    return 'file:///android_asset/dice/die-'+String(value)+'.png';
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

  function combatEntities(combat){
    if(!combat)return[];
    if(Array.isArray(combat.entities)&&combat.entities.length)return combat.entities;
    return combat.entity?[combat.entity]:[];
  }
  function combatEntityAt(combat,index){
    var entities=combatEntities(combat),i=Number(index);
    if(!Number.isInteger(i)||i<0||i>=entities.length)i=0;
    return entities[i]||combat&&combat.entity||null;
  }
  function activeCombatEntity(combat){
    return combatEntityAt(combat,combat&&combat.activeEntityIndex);
  }
  function sendCombatTarget(index){
    if(window.__combatBusy||!window.Android||typeof Android.combatTarget!=='function')return;
    window.__combatBusy=true;
    renderCombatPanel();
    Android.combatTarget(Number(index));
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
    if(combatTargets){
      combatTargets.textContent='';
      var targets=combatEntities(combat);
      combatTargets.hidden=targets.length<=1;
      var selectedTarget=Number(combat.targetEntityIndex);
      if(!Number.isInteger(selectedTarget))selectedTarget=Number(combat.activeEntityIndex)||0;
      targets.forEach(function(entity,index){
        if(!entity)return;
        var button=document.createElement('button');
        button.type='button';
        button.className='combat-target'+(index===selectedTarget?' active':'');
        var alive=entity.alive!==false&&Number(entity.hp)>0;
        button.disabled=!alive||window.__combatBusy||dice.finalized===true;
        button.textContent=String(entity.name||entity.key||'Entity')+' · '+String(Math.max(0,Number(entity.hp)||0))+'/'+String(Math.max(1,Number(entity.maxHp)||1))+' HP';
        button.setAttribute('aria-pressed',index===selectedTarget?'true':'false');
        button.addEventListener('click',function(){sendCombatTarget(index);});
        combatTargets.appendChild(button);
      });
    }
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
        var settling=!rolling&&held[index]!==true&&diceSettleUntil>Date.now()&&diceSettleMask[index]===true;
        button.className='combat-die'+(held[index]===true?' held':'')+(rolling?' rolling':'')+(settling?' settling':'');
        button.style.setProperty('--die-delay',String(index*-55)+'ms');
        button.disabled=!hasRolled||dice.finalized===true||window.__combatBusy;
        button.setAttribute('aria-pressed',held[index]===true?'true':'false');
        var shadow=document.createElement('span');shadow.className='combat-die-shadow';shadow.setAttribute('aria-hidden','true');button.appendChild(shadow);
        var seal=document.createElement('span');seal.className='combat-die-hold-seal';seal.textContent='GIỮ';seal.setAttribute('aria-hidden','true');button.appendChild(seal);
        var object=document.createElement('span');object.className='combat-die-object';button.appendChild(object);
        var value=Number(values[index])||0;
        var visualValue=value>=1&&value<=6?value:(rolling?(index%6)+1:0);
        button.setAttribute('aria-label','Xúc xắc '+String(index+1)+(value>=1&&value<=6?' · '+String(value):'')+(held[index]===true?' · Đang giữ':''));
        if(visualValue>=1&&visualValue<=6){
          var img=document.createElement('img');
          img.className='combat-die-skin';
          img.src=diceAsset(visualValue);
          img.alt='';
          img.setAttribute('aria-hidden','true');
          object.appendChild(img);
        }else{
          var blank=document.createElement('span');blank.className='combat-die-unknown';blank.textContent='?';object.appendChild(blank);
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

  function syncCombatSnapshotActor(combat){
    if(!combat||combat.active!==true){
      if(typeof window.backroomClearCombatVisualActor==='function')window.backroomClearCombatVisualActor();
      return;
    }
    var actorIndex=Number(combat.actorIndex);
    if(!Number.isInteger(actorIndex))actorIndex=0;
    var entity=activeCombatEntity(combat);
    var entityKey=entity&&entity.key?entity.key:'';
    if(typeof window.backroomSetCombatVisualActor==='function'){
      window.backroomSetCombatVisualActor(actorIndex,entityKey);
    }
  }

  window.backroomCombatDiceState=function(json){
    try{
      var nextState=JSON.parse(json);
      var applyDiceState=function(){
        var wasRolling=diceRollAnimating;
        if(wasRolling){
          var beforeDice=combatDiceState();
          var beforeHeld=beforeDice&&Array.isArray(beforeDice.held)?beforeDice.held:[false,false,false,false,false];
          diceSettleMask=beforeHeld.map(function(v){return v!==true;});
          diceSettleUntil=Date.now()+DICE_SETTLE_ANIMATION_MS;
          if(diceSettleTimer)clearTimeout(diceSettleTimer);
          diceSettleTimer=setTimeout(function(){diceSettleTimer=0;renderCombatPanel();},DICE_SETTLE_ANIMATION_MS+24);
        }
        diceRollAnimating=false;
        state=nextState;
        if(typeof CURRENT_CHARACTER_CANON!=='undefined')state.characterCanon=CURRENT_CHARACTER_CANON;
        syncCombatSnapshotActor(state.combat||{});
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
      diceSettleUntil=0;
      diceSettleMask=[false,false,false,false,false];
      if(diceSettleTimer){clearTimeout(diceSettleTimer);diceSettleTimer=0;}
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

  function combatPhaseEvents(events,phase,entityIndex){
    return (Array.isArray(events)?events:[]).filter(function(event){
      return event&&event.phase===phase&&(entityIndex===undefined||entityIndex===null||Number(event.entityIndex)===Number(entityIndex));
    });
  }
  function combatPhaseSchedule(events,phase,entityIndex){
    var previous='',at=COMBAT_SWAP_MS;
    return combatPhaseEvents(events,phase,entityIndex).map(function(event,index){
      var key=String(event.entityKey||''),swap=phase==='actor'&&index>0&&key&&key!==previous;
      if(index>0)at+=swap?COMBAT_PHASE_MS+COMBAT_SWAP_MS:COMBAT_EVENT_GAP_MS;
      previous=key;return {event:event,at:at,swap:swap};
    });
  }
  function combatPhaseDuration(events,phase,entityIndex){
    var schedule=combatPhaseSchedule(events,phase,entityIndex);
    return (schedule.length?schedule[schedule.length-1].at:COMBAT_SWAP_MS)+COMBAT_PHASE_MS;
  }
  function playCombatPhase(events,phase,entityIndex){
    var token=window.__combatAnimationToken;
    combatPhaseSchedule(events,phase,entityIndex).forEach(function(item){
      if(item.swap)setTimeout(function(){
        if(token===window.__combatAnimationToken&&typeof window.backroomSetCombatVisualActor==='function')
          window.backroomSetCombatVisualActor(item.event.actorIndex,item.event.entityKey);
      },item.at-COMBAT_SWAP_MS);
      setTimeout(function(){
        if(token!==window.__combatAnimationToken)return;
        if(typeof window.backroomPlayCombatFeedback==='function')window.backroomPlayCombatFeedback(item.event);
      },item.at);
    });
  }

  function finishCombatAnimation(token) {
    if (token !== window.__combatAnimationToken) return;
    syncCombatSnapshotActor(state && state.combat ? state.combat : {});
    setTimeout(function(){
      if(token!==window.__combatAnimationToken)return;
      window.__combatFeedbackBusy = false;
      window.__combatBusy = false;
      if (typeof busy !== 'undefined') busy = false;
      if (typeof window.render === 'function') window.render();
      scrollForCurrentMode();
      if (status) {
        status.textContent = state.combat && state.combat.active
          ? 'Lượt chiến đấu ' + state.combat.round + ' · ' + state.combat.currentActor
          : (state.combat && state.combat.outcome === 'victory'
              ? 'Entity bị tiêu diệt. Bắt đầu Turn ' + state.turn + '.'
              : 'Chiến đấu kết thúc. Turn ' + state.turn + '.');
      }
    },COMBAT_SWAP_MS);
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
      var actorEvents=events.filter(function(event){return event&&event.phase==='actor';});
      var actorEntityKey=actorEvents.length&&actorEvents[0].entityKey?String(actorEvents[0].entityKey):'';
      if(!actorEntityKey){
        var active=activeCombatEntity(combat);
        actorEntityKey=active&&active.key?String(active.key):'';
      }
      if (typeof window.backroomSetCombatVisualActor === 'function') {
        window.backroomSetCombatVisualActor(combat.resolvedActorIndex, actorEntityKey);
      }
      if (typeof window.render === 'function') window.render();
      syncComposer();
      renderCombatPanel();
      scrollCombatToBottom();
      if (status) status.textContent = 'Đang xử lý lượt của ' + (combat.resolvedActorName || 'nhân vật') + '…';

      playCombatPhase(events, 'actor');
      var delay = combatPhaseDuration(events,'actor');
      var deaths=Array.isArray(combat.entityDeathsThisTurn)?combat.entityDeathsThisTurn:[];
      deaths.forEach(function(death){
        var deathKey=death&&death.key?String(death.key):'';
        var deathIndex=death?Number(death.entityIndex):0;
        setTimeout(function(){
          if(token!==window.__combatAnimationToken)return;
          if(typeof window.backroomSetCombatVisualActor==='function'){
            window.backroomSetCombatVisualActor(combat.resolvedActorIndex,deathKey);
          }
          if(status)status.textContent=String(death&&death.name||'Entity')+' bị tiêu diệt…';
          setTimeout(function(){if(token===window.__combatAnimationToken&&typeof window.backroomShatterEntity==='function')window.backroomShatterEntity(deathKey);},COMBAT_SWAP_MS);
        },delay);
        delay+=COMBAT_SWAP_MS+1600;
      });

      var entityTurns=Array.isArray(combat.resolvedEntityTurns)?combat.resolvedEntityTurns:[];
      entityTurns.forEach(function(turn){
        var entityIndex=Number(turn&&turn.entityIndex);
        var entityKey=turn&&turn.entityKey?String(turn.entityKey):'';
        setTimeout(function(){
          if(token!==window.__combatAnimationToken)return;
          if(typeof window.backroomSetCombatVisualActor==='function'){
            window.backroomSetCombatVisualActor(combat.resolvedActorIndex,entityKey);
          }
          if(status)status.textContent=String(turn&&turn.entityName||'Entity')+' đang phản hồi…';
          playCombatPhase(events,'entity',entityIndex);
        },delay);
        delay+=combatPhaseDuration(events,'entity',entityIndex);
      });

      setTimeout(function(){
        if(token!==window.__combatAnimationToken)return;
        finishCombatAnimation(token);
      }, delay);
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



  window.render();
  scrollForCurrentMode();
})();
