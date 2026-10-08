/* GM story typography: visual-only; neither parses actions nor changes game state. */
(function (root) {
  'use strict';
  var CHARACTERS = ['Cao Minh','Kai Akechi','Lục Trầm','Diệp Minh','Trác Lâm','An Nhiên','Iris','Syvial','Lucia','Slenderman','Jeff the Killer','Jane the Killer'];
  // ItemRegistry display names and established equipment names. Extra items come from state.
  var ITEMS = ['Almond Water','Băng gạc','Thuốc sát trùng','Greek Fire','Liquid Pain','White Wraith Magnum','W.W Magnum','Blackblood Armor','Blackblood Armor & linked modules','Omnivault Ring','Nhẫn Vạn Tàng','MadGod Set'];
  var WORD = /[\p{L}\p{N}_]/u;
  var KINDS = {character:true,location:true,item:true};

  function add(map, name, kind) {
    if (!KINDS[kind] || typeof name !== 'string') return;
    var value = name.trim();
    if (value.length < 3 || value.length > 90 || /[\r\n]/.test(value)) return;
    var key = value.toLocaleLowerCase('vi');
    if (!map.has(key)) map.set(key, {name:value,kind:kind});
  }
  function addNames(map, items, kind) {
    if (!Array.isArray(items)) return;
    items.forEach(function (item) {
      if (typeof item === 'string') add(map,item,kind);
      else if (item && typeof item === 'object') add(map,item.displayName || item.name || item.label,kind);
    });
  }
  function addEquipment(map, items) {
    if (!items || typeof items !== 'object') return;
    if (Array.isArray(items)) {addNames(map,items,'item');return;}
    Object.keys(items).forEach(function (key) {
      var entry = items[key];
      if (!entry || typeof entry !== 'object') return;
      add(map,entry.displayName || entry.name || entry.label,'item');
      if (entry.slots) addEquipment(map,entry.slots);
    });
  }
  function currentState() {
    try {return typeof state !== 'undefined' && state ? state : null;}
    catch (_) {return null;}
  }
  function termsFromState(s) {
    var terms = new Map();
    CHARACTERS.forEach(function (n) {add(terms,n,'character');});
    add(terms,s && s.player && s.player.name,'character');
    addNames(terms,s && s.party,'character');
    var details = s && s.partyDetails;
    var members = Array.isArray(details) ? details : details && details.members;
    addNames(terms,members,'character');
    if (s && s.combat) {
      addNames(terms,s.combat.entities,'character');
      addNames(terms,s.combat.actors,'character');
      add(terms,s.combat.player && s.combat.player.name,'character');
    }
    add(terms,s && s.location,'location');
    var level = s && s.level;
    if (level && typeof level === 'object') {
      add(terms,level.displayName || level.name || level.title,'location');
      if (typeof level.number === 'number' && Number.isFinite(level.number) && level.number >= 0)
        add(terms,'Level ' + level.number,'location');
    }
    ITEMS.forEach(function (n) {add(terms,n,'item');});
    addNames(terms,s && s.inventory,'item');
    addEquipment(terms,s && s.equipment);
    if (Array.isArray(members)) members.forEach(function (member) {
      if (!member) return;
      addNames(terms,member.inventory,'item');
      addEquipment(terms,member.equipment);
    });
    return Array.from(terms.values());
  }
  function escaped(text) {
    return text.replace(/[\\^$.*+?()[\]{}|]/g,'\\$&');
  }
  function tokenize(text, entries) {
    var input = String(text == null ? '' : text);
    var names = (entries || []).filter(function (e) {
      return e && KINDS[e.kind] && typeof e.name === 'string' && e.name.length >= 3;
    }).slice(0,250);
    if (!input || !names.length) return [{text:input,kind:null}];
    names.sort(function (a,b) {return b.name.length-a.name.length;});
    var lookup = new Map();
    names.forEach(function (e) {
      var key = e.name.toLocaleLowerCase('vi');
      if (!lookup.has(key)) lookup.set(key,e.kind);
    });
    var rx = new RegExp('(?:'+names.map(function (e) {return escaped(e.name);}).join('|')+')','giu');
    var tokens = [],last = 0,match;
    while ((match = rx.exec(input)) !== null) {
      var start = match.index,end = start+match[0].length;
      if ((start > 0 && WORD.test(input[start-1])) ||
          (end < input.length && WORD.test(input[end]))) continue;
      if (start > last) tokens.push({text:input.slice(last,start),kind:null});
      tokens.push({text:input.slice(start,end),kind:lookup.get(match[0].toLocaleLowerCase('vi'))});
      last = end;
    }
    if (last < input.length) tokens.push({text:input.slice(last),kind:null});
    return tokens.length ? tokens : [{text:input,kind:null}];
  }
  function decorate() {
    var log = document.getElementById('log');
    if (!log) return;
    var entries = termsFromState(currentState());
    log.querySelectorAll('.message:not(.player):not(.pending) .text').forEach(function (node) {
      if (node.dataset.gmHighlightsDone === '1') return;
      node.dataset.gmHighlightsDone = '1';
      var tokens = tokenize(node.textContent,entries);
      if (!tokens.some(function (t) {return t.kind;})) return;
      var fragment = document.createDocumentFragment();
      tokens.forEach(function (t) {
        if (!t.kind) fragment.appendChild(document.createTextNode(t.text));
        else {
          var span = document.createElement('span');
          span.className = 'gm-highlight gm-highlight--'+t.kind;
          span.textContent = t.text;
          fragment.appendChild(span);
        }
      });
      node.replaceChildren(fragment);
    });
  }
  function start() {
    var log = document.getElementById('log');
    if (!log || typeof MutationObserver === 'undefined') return;
    decorate();
    // Renderer replaces log rows per turn. Watch direct children, not our spans.
    new MutationObserver(decorate).observe(log,{childList:true});
  }
  root.BackroomGMHighlights = {tokenize:tokenize,termsFromState:termsFromState};
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',start);
  else start();
})(window);
