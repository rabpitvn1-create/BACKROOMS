/* Deterministic presentation-only narrative formatter. No Game Master or Core writes. */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.backroomNarrativeFormat = api;
})(typeof window === 'undefined' ? null : window, function () {
  'use strict';
  var VERSION = 1, LIMIT = 140;
  var CATEGORIES = ['character','entity','equipment','consumable','location','skill-demonic','skill-righteous','passive'];
  var CHARACTER_ALIASES = [
    ['cao-minh', 'Cao Minh'], ['luc-tram','Lục Trầm'], ['syvial','Syvial'],
    ['lucia','Lucia'], ['an-nhien','An Nhiên'], ['quoc-nam','Quốc Nam'],
    ['tieu-lan','Tiểu Lan']
  ];
  var ENTITY_NAMES = [
    ['hound','Hound'], ['smiler','Smiler'], ['false-puddle','False Puddle'],
    ['skin-stealer','Skin-Stealer'], ['skin-stealer','Skin Stealer'],
    ['wretch','Wretch'], ['deathmoth','Deathmoth'], ['faceling','Faceling'],
    ['cable-mimic','Cable Mimic'], ['jeff-the-killer','Jeff the Killer']
  ];
  function safeString(value) { return typeof value === 'string' ? value : ''; }
  function normalize(value) { return safeString(value).normalize('NFC').toLocaleLowerCase('vi-VN').trim(); }
  function hash(value) {
    var out = 2166136261, input = safeString(value);
    for (var i = 0; i < input.length; i++) { out ^= input.charCodeAt(i); out = Math.imul(out, 16777619); }
    return (out >>> 0).toString(16);
  }
  function logSignature(log) {
    var h = 'narrative-v1';
    (Array.isArray(log) ? log : []).forEach(function (entry) {
      h = hash(h + '\u0000' + safeString(entry && entry.role) + '\u0000' + safeString(entry && entry.text));
    });
    return h;
  }
  function runId(state) { return safeString(state && state.levelTravel && state.levelTravel.runId); }
  function add(records, text, kind, id) {
    text = safeString(text).trim();
    if (!text || text.length < 3 || CATEGORIES.indexOf(kind) < 0) return;
    var key = normalize(text);
    if (!key || key === 'unknown' || key === 'chưa xác định') return;
    if (!records.some(function (r) { return r.key === key && r.kind === kind; }))
      records.push({ key:key, text:text, kind:kind, id:safeString(id) });
  }
  function list(state) {
    var records = [], party = Array.isArray(state && state.party) ? state.party : [];
    var members = state && state.partyDetails && Array.isArray(state.partyDetails.members)
      ? state.partyDetails.members : [];
    var actorIds = new Set(['cao-minh']);
    party.concat(members).forEach(function (actor) {
      if (!actor || typeof actor !== 'object') return;
      var id = safeString(actor.id).toLowerCase();
      if (id && actor.presence !== 'SEPARATED' && actor.presence !== 'MISSING' && actor.presence !== 'ABSENT')
        actorIds.add(id);
      if (id === 'cao-minh' || (id && actorIds.has(id))) add(records, actor.name, 'character', id);
    });
    CHARACTER_ALIASES.forEach(function (pair) {
      if (actorIds.has(pair[0])) add(records, pair[1], 'character', pair[0]);
    });
    var inventory = Array.isArray(state && state.inventory) ? state.inventory : [];
    var world = state && state.flags && Array.isArray(state.flags.worldItems) ? state.flags.worldItems : [];
    function items(rows) {
      rows.forEach(function (item) {
        if (!item || (typeof item === 'object' && (item.available === false || item.quantity === 0))) return;
        var name = typeof item === 'string' ? item : item.name;
        var type = normalize(item && item.type || item && item.classification || item && item.rarity);
        add(records, name, /weapon|armor|equipment|legend|signature|rare|ring|kiếm|khải|pháp bảo/.test(type) ?
          'equipment' : 'consumable', item && item.id);
      });
    }
    items(inventory);
    items(world);
    members.forEach(function (member) {
      if (!member || !actorIds.has(safeString(member.id).toLowerCase())) return;
      items(Array.isArray(member.inventory) ? member.inventory : []);
      items(Array.isArray(member.equipmentItems) ? member.equipmentItems : []);
      (Array.isArray(member.skills) ? member.skills : []).forEach(function (skill) {
        if (!skill || !skill.name) return;
        var kind = normalize(skill.kind) === 'passive' ? 'passive' :
          safeString(member.id).toLowerCase() === 'luc-tram' ? 'skill-righteous' : 'skill-demonic';
        add(records, skill.name, kind, member.id);
      });
    });
    // Only the player-visible identification is authority for a level name.
    var level = state && state.level, display = safeString(state && state.displayLocation);
    if (display && display !== 'Chưa xác định') {
      add(records, display, 'location', level && level.id);
      if (level && display === safeString(state.location)) add(records, level.name, 'location', level.id);
    }
    // A recorded encounter is not proof of identity. Require a registry record
    // plus a known discovery for this actor; never use the complete canon database.
    var flags = state && state.flags || {}, registry = flags.entityRegistry || {};
    var continuity = flags.storyContinuity || {}, knowledge = Array.isArray(continuity.knowledge) ? continuity.knowledge : [];
    var discoveries = Array.isArray(continuity.discoveries) ? continuity.discoveries : [];
    function known(id) {
      var key = normalize(id).replace(/[-_ ]/g, '');
      return discoveries.some(function (entry) {
        var raw = normalize(entry && entry.id).replace(/[-_ ]/g, '');
        if (raw !== 'disc.entity.' + key && raw !== 'disc.entity' + key) return false;
        return knowledge.some(function (k) {
          return k && normalize(k.factId) === normalize(entry.id) && Array.isArray(k.knownBy) &&
            k.knownBy.some(function (who) { return normalize(who).replace(/[-_]/g,'') === 'caominh'; });
        });
      });
    }
    ENTITY_NAMES.forEach(function (entry) {
      var id = entry[0], reg = Object.keys(registry).some(function (k) {
        return normalize(k).replace(/^entity[._-]/,'').replace(/[^a-z0-9]/g,'') === id.replace(/[^a-z0-9]/g,'');
      });
      if (reg && known(id)) add(records, entry[1], 'entity', id);
    });
    return records.sort(function (a,b) { return b.text.length - a.text.length || a.key.localeCompare(b.key); });
  }
  function boundary(ch) { return !ch || !/[\p{L}\p{N}_]/u.test(ch); }
  function dialogues(text) {
    var out = [], start = -1;
    for (var i=0;i<text.length;i++) {
      var ch=text[i];
      if (ch === '“' && start < 0) start=i;
      else if (ch === '”' && start >= 0) { out.push({start:start,end:i+1,kind:'dialogue'}); start=-1; }
    }
    return out;
  }
  function annotate(text, records) {
    text = safeString(text);
    if (!text) return [];
    var spans = dialogues(text), busy = spans.slice();
    var candidates = [];
    (Array.isArray(records) ? records : []).forEach(function (record) {
      if (!record || CATEGORIES.indexOf(record.kind) < 0) return;
      var name = safeString(record.text); if (!name || name.length < 3) return;
      // Match indices must refer to the original UTF-16 narrative text.
      // Trimming/normalizing the whole narrative corrupts persisted offsets.
      var metaChars = '\\^$.*+?()[]{}|';
      var escaped = Array.from(name, function (ch) {
        return metaChars.indexOf(ch) < 0 ? ch : '\\' + ch;
      }).join('');
      var matcher = new RegExp(escaped, 'giu'), match;
      while ((match = matcher.exec(text)) !== null) {
        var at = match.index, end = at + match[0].length;
        if (boundary(text[at-1]) && boundary(text[end])) {
          candidates.push({start:at,end:end,kind:record.kind,id:safeString(record.id),length:match[0].length});
        }
      }
    });
    candidates.sort(function(a,b) {return a.start-b.start || b.length-a.length || a.kind.localeCompare(b.kind);});
    // Every verified name stays styled on every occurrence. A per-turn/per-paragraph
    // quota made long narration and repeated names lose their emphasis at random.
    candidates.forEach(function (candidate) {
      if (busy.some(function(r){return candidate.start < r.end && r.start < candidate.end;})) return;
      spans.push({start:candidate.start,end:candidate.end,kind:candidate.kind});
      busy.push(candidate);
    });
    return spans.sort(function(a,b){return a.start-b.start || a.end-b.end;});
  }
  function valid(spans,text) {
    if (!Array.isArray(spans) || spans.length > 300) return false;
    var end=0;
    return spans.every(function(s) {
      if (!s || !Number.isInteger(s.start) || !Number.isInteger(s.end) || s.start<end || s.end>text.length || s.start>=s.end) return false;
      if (s.kind !== 'dialogue' && CATEGORIES.indexOf(s.kind)<0) return false;
      end=s.end; return true;
    });
  }
  function decorate(element, text, spans) {
    if (!element || typeof document === 'undefined') return false;
    text=safeString(text);
    if (!valid(spans,text)) { element.textContent=text; return false; }
    var fragment=document.createDocumentFragment(), cursor=0;
    spans.forEach(function (s) {
      if (s.start>cursor) fragment.appendChild(document.createTextNode(text.slice(cursor,s.start)));
      var node=document.createElement('span');
      node.className='nfmt nfmt--'+s.kind;
      node.textContent=text.slice(s.start,s.end);
      fragment.appendChild(node); cursor=s.end;
    });
    if (cursor<text.length) fragment.appendChild(document.createTextNode(text.slice(cursor)));
    element.replaceChildren(fragment);
    return true;
  }
  // Expand old, valid cached highlights using only terms already identified
  // at that historical turn. This fixes the former quotas without exposing
  // identities learned in later turns or touching the underlying GM text.
  function completeCached(text, spans) {
    text = safeString(text);
    if (!valid(spans, text)) return dialogues(text);
    var historical = [];
    spans.forEach(function (span) {
      if (CATEGORIES.indexOf(span.kind) >= 0)
        add(historical, text.slice(span.start, span.end), span.kind, '');
    });
    return annotate(text, historical);
  }
  var SAFE_HISTORICAL = [{text:'Cao Minh',kind:'character',id:'cao-minh'}];
  function store(storage, id, value) {
    if (!storage || !id) return;
    try { storage.setItem('backroom-narrative-format-v1:'+id,JSON.stringify(value)); } catch (_) {}
  }
  function restore(storage,id) {
    if (!storage || !id) return null;
    try {
      var parsed=JSON.parse(storage.getItem('backroom-narrative-format-v1:'+id));
      return parsed && parsed.version===VERSION && Array.isArray(parsed.entries) ? parsed : null;
    } catch (_) { return null; }
  }
  function createController(storage) {
    var currentId='', cache=null, records=[], signature='';
    function sync(state) {
      var id=runId(state), log=Array.isArray(state && state.log)?state.log:[];
      // The first turn can arrive before Core projects a runId. Keep a
      // temporary, non-persistent cache instead of silently losing its styling.
      if (!id && currentId !== '@session') {
        currentId='@session';cache={version:VERSION,entries:[]};
      } else if (id && id!==currentId) {
        currentId=id;cache=restore(storage,id)||{version:VERSION,entries:[]};
      }
      // Prefix matching prevents old annotations leaking into a replaced or branched history.
      var prefix='narrative-v1', validEntries=[];
      for(var i=0;i<log.length;i++){
        var msg=log[i]||{};
        prefix=hash(prefix+'\u0000'+safeString(msg.role)+'\u0000'+safeString(msg.text));
        var entry=cache.entries[i];
        if (entry && entry.signature===prefix && entry.role===msg.role &&
          valid(entry.spans,safeString(msg.text))) validEntries[i]=entry;
        else validEntries[i]=null;
      }
      cache.entries=validEntries;
      // New entries are frozen at the first confirmed presentation. Reconstructed
      // history without cache is dialogue-only rather than applying future knowledge.
      records=list(state);
      signature=prefix;
    }
    function freezeNew(state, previousLength) {
      sync(state);
      var log=Array.isArray(state && state.log)?state.log:[];
      if (!cache) return;
      var prefix='narrative-v1';
      log.forEach(function (msg,i) {
        msg=msg||{};
        prefix=hash(prefix+'\u0000'+safeString(msg.role)+'\u0000'+safeString(msg.text));
        if (i>=previousLength && msg.role==='gm' && !cache.entries[i]) {
          cache.entries[i]={role:'gm',signature:prefix,spans:annotate(msg.text,records)};
        }
      });
      if (cache.entries.length>LIMIT) { for (var j=0;j<cache.entries.length-LIMIT;j++) cache.entries[j]=null; }
      if (currentId !== '@session') store(storage,currentId,cache);
    }
    function paint(logEl,state) {
      if (!logEl) return;
      var log=Array.isArray(state && state.log)?state.log:[], items=logEl.querySelectorAll('.message.gm .text');
      var g=0;
      log.forEach(function (entry,i) {
        if (!entry || entry.role!=='gm') return;
        var el=items[g++];if (!el) return;
        var text=safeString(entry.text), stored=cache&&cache.entries[i];
        // Missing historical snapshots must not be inferred using future
        // knowledge. Cao Minh's known identity is the sole safe baseline.
        var spans=stored ? completeCached(text, stored.spans) : annotate(text, SAFE_HISTORICAL);
        // On confirmed defeat the GM may reveal the real Entity name, even if
        // the player did not identify it before combat. No earlier log is changed.
        if (entry.kind==='entity_defeated' && safeString(entry.entityName) &&
            text.indexOf(entry.entityName+' đã bị tiêu diệt.')===0)
          spans=annotate(text,[{text:entry.entityName,kind:'entity',id:entry.entityName}]);
        decorate(el,text,spans);
      });
    }
    return {sync:sync,freezeNew:freezeNew,paint:paint};
  }
  return {list:list,annotate:annotate,dialogues:dialogues,decorate:decorate,
    createController:createController,logSignature:logSignature,valid:valid};
});
