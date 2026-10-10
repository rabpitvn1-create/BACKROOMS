/* COMBAT_FEEDBACK_1193A: presentation from BR-1.1.93a, typed runtime events only. */
/* COMBAT_TRANSIENT_LOG_R01: in-memory combat log; never writes GameState, save metadata or GM narrative log. */
(function(){
  'use strict';
  var seen=[],animationToken=0;
  var combatLogEncounter='',combatLogRound=null,combatLogEntries=[],combatPanel=null,combatPanelMeta=null,combatPanelList=null;
  var pendingPanelClear=false;

  function activeCombat(){
    return typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true?state.combat:null;
  }
  function prettyEntity(value){
    var raw=String(value||'Entity').replace(/_/g,' ').trim();
    return raw.replace(/\b\w/g,function(c){return c.toUpperCase();});
  }
  function ensureCombatPanel(){
    var snapshot=document.getElementById('snapshot');if(!snapshot)return null;
    if(!combatPanel||combatPanel.isConnected===false)combatPanel=document.getElementById('combatFeedbackPanel');
    if(!combatPanel){
      combatPanel=document.createElement('section');
      combatPanel.id='combatFeedbackPanel';
      combatPanel.className='combat-feedback-panel';
      combatPanel.hidden=true;

      var head=document.createElement('div');head.className='combat-feedback-head';
      var titles=document.createElement('div');
      var kicker=document.createElement('div');kicker.className='combat-feedback-kicker';kicker.textContent='COMBAT';
      var title=document.createElement('h2');title.textContent='NHẬT KÝ CHIẾN ĐẤU';
      titles.appendChild(kicker);titles.appendChild(title);
      combatPanelMeta=document.createElement('div');combatPanelMeta.id='combatFeedbackMeta';combatPanelMeta.className='combat-feedback-meta';
      head.appendChild(titles);head.appendChild(combatPanelMeta);
      combatPanelList=document.createElement('div');combatPanelList.id='combatFeedbackEntries';combatPanelList.className='combat-feedback-entries';
      combatPanel.appendChild(head);combatPanel.appendChild(combatPanelList);
    }else{
      combatPanelMeta=document.getElementById('combatFeedbackMeta');
      combatPanelList=document.getElementById('combatFeedbackEntries');
    }
    var dice=document.getElementById('pokerDiceModal');
    var anchor=dice&&dice.isConnected!==false?dice:snapshot;
    if(anchor.nextElementSibling!==combatPanel)anchor.insertAdjacentElement('afterend',combatPanel);
    return combatPanel;
  }
  function renderCombatMeta(combat){
    if(!combatPanelMeta)return;
    var round=Math.max(1,Number(combat&&combat.round)||1);
    combatPanelMeta.textContent='Vòng '+round+' · log tạm thời';
  }
  function appendCombatLine(kind,text){
    text=String(text||'').trim();if(!text)return;
    var panel=ensureCombatPanel();if(!panel||!combatPanelList)return;
    var entry=document.createElement('div');entry.className='combat-feedback-entry combat-feedback-entry--'+kind;
    entry.textContent=text;combatPanelList.appendChild(entry);combatLogEntries.push({kind:kind,text:text});
    if(combatLogEntries.length>80){
      combatLogEntries.shift();
      if(combatPanelList.firstElementChild)combatPanelList.firstElementChild.remove();
    }
    combatPanelList.scrollTop=combatPanelList.scrollHeight;
  }
  function resetCombatPanel(){
    combatLogEncounter='';combatLogRound=null;combatLogEntries=[];pendingPanelClear=false;
    if(combatPanelList)combatPanelList.textContent='';
    if(combatPanelMeta)combatPanelMeta.textContent='';
    if(combatPanel)combatPanel.hidden=true;
  }
  function beginCombatLog(combat){
    if(!combat||combat.active!==true)return;
    var encounter=String(combat.encounterId||'');if(!encounter)return;
    var panel=ensureCombatPanel();if(!panel)return;
    if(combatLogEncounter!==encounter){
      combatLogEncounter=encounter;combatLogRound=null;combatLogEntries=[];pendingPanelClear=false;
      if(combatPanelList)combatPanelList.textContent='';
      appendCombatLine('system','Bắt đầu giao chiến · '+prettyEntity(combat.entityKey));
    }
    panel.hidden=false;renderCombatMeta(combat);
    var round=Math.max(1,Number(combat.round)||1);
    if(combatLogRound!==round){
      combatLogRound=round;
      appendCombatLine('round','Vòng '+round);
    }
  }
  function combatStatusVisual(status){
    if(status==='Chảy máu')return {label:'CHẢY MÁU',key:'bleed'};
    if(status==='Trúng độc')return {label:'TRÚNG ĐỘC',key:'poison'};
    if(status==='Choáng')return {label:'CHOÁNG',key:'stun'};
    if(status==='Xuyên giáp'||status==='Phá giáp')return {label:'XUYÊN GIÁP',key:'armor'};
    if(status==='Mất phương hướng')return {label:'MẤT PHƯƠNG HƯỚNG',key:'disorient'};
    return null;
  }
  function appendPacketToCombatLog(packet){
    var summary=String(packet&&packet.summary||'').trim();
    if(summary)appendCombatLine('summary',summary);
    var events=packet&&Array.isArray(packet.events)?packet.events:[];
    events.forEach(function(e){
      if(!e||(e.target!=='entity'&&e.target!=='actor'))return;
      var text=String(e.text||'').trim();if(!text)return;
      var actorPhase=e.phase==='entity'?'entity':'actor';
      var source=actorPhase==='entity'?'ENTITY':'PARTY';
      var target=e.target==='actor'?'PARTY':'ENTITY';
      var parts=[source+' → '+target,text];
      if(e.critical===true)parts.push('CRIT');
      var status=combatStatusVisual(e.status);if(status)parts.push(status.label);
      appendCombatLine(actorPhase,parts.join(' · '));
    });
  }
  function setFeedbackBusy(value){
    window.__combatFeedbackBusy=!!value;
    if(typeof window.ensureDirectCombatDice==='function')window.ensureDirectCombatDice();
  }
  function targetAnchor(target){
    var box=document.getElementById('snapshot');
    if(!box)return null;
    var el=box.querySelector(target==='entity'?'.snapshot-entity':'.snapshot-character');
    if(!el)return null;
    var br=box.getBoundingClientRect(),er=el.getBoundingClientRect();
    return {box:box,el:el,x:Math.max(16,Math.min(br.width-16,er.left-br.left+er.width/2)),
      y:Math.max(br.height*.34,Math.min(br.height-16,er.top-br.top+er.height*.34))};
  }
  window.backroomPlayCombatFeedback=function(event,fallback){
    var e=event||{},text=String(e.text||'').trim();
    if(!/^-\d+ HP$/i.test(text)||Number(text.slice(1,-3))<=0)return;
    if(e.target!=='entity'&&e.target!=='actor')return;
    // The current Snapshot displays Cao Minh. Do not attribute a teammate's damage to him.
    if(e.target==='actor'&&e.actorId&&e.actorId!=='cao_minh')return;
    var target=e.target,anchor=targetAnchor(target)||fallback;
    if(!anchor)return;
    if(e.flash&&anchor.el&&anchor.el.isConnected){
      anchor.el.classList.remove('combat-hit-flash');void anchor.el.offsetWidth;
      anchor.el.classList.add('combat-hit-flash');
      setTimeout(function(){anchor.el.classList.remove('combat-hit-flash')},170);
    }
    var active=anchor.box.querySelectorAll('.combat-float[data-target="'+target+'"]'),lane=0,occupied=[];
    for(var i=0;i<active.length;i++)occupied.push(Number(active[i].dataset.lane));
    while(occupied.indexOf(lane)!==-1)lane++;
    var floater=document.createElement('div');floater.className='combat-float';floater.dataset.target=target;floater.dataset.lane=String(lane);
    if(e.critical===true)floater.classList.add('combat-float--critical');
    var value=document.createElement('span');value.className='combat-float-value';value.textContent=text;floater.appendChild(value);
    var status=combatStatusVisual(e.status);
    if(status){
      floater.classList.add('combat-float--status','combat-float--status-'+status.key);
      var badge=document.createElement('span');badge.className='combat-float-status combat-float-status--'+status.key;
      badge.textContent=status.label;value.insertBefore(badge,value.firstChild);
    }else{
      floater.classList.add('combat-float--normal');
      var normalBadge=document.createElement('span');normalBadge.className='combat-float-status combat-float-status--normal';
      normalBadge.textContent='Đánh thường';value.insertBefore(normalBadge,value.firstChild);
    }
    floater.style.left=anchor.x+'px';floater.style.top=(anchor.y-lane*38)+'px';anchor.box.appendChild(floater);
    floater.addEventListener('animationend',function(){floater.remove()},{once:true});
    setTimeout(function(){floater.remove()},1800);
  };

  var previousTurn=window.backroomTurn;
  if(typeof previousTurn!=='function')return;
  window.backroomTurn=function(json){
    var before=typeof state!=='undefined'&&state&&state.combat;
    var anchors={entity:targetAnchor('entity'),actor:targetAnchor('actor')};
    var result=previousTurn.call(this,json);
    var next=typeof state!=='undefined'&&state;
    var after=next&&next.combat,packet=next&&next.combatFeedback;

    if(after&&after.active===true)beginCombatLog(after);
    else if(!packet)resetCombatPanel();

    if(!before&&after&&after.active===true&&!packet){seen=[];++animationToken;setFeedbackBusy(false);}
    if(!after&&!packet){++animationToken;setFeedbackBusy(false);}
    if(!packet||typeof packet.id!=='string'||!Array.isArray(packet.events)||seen.indexOf(packet.id)!==-1)return result;

    var encounter=String(packet.encounterId||'');
    var activeEncounter=String(after&&after.encounterId||before&&before.encounterId||'');
    if(!encounter||encounter!==activeEncounter)return result;

    if(!after&&before&&before.active===true){
      beginCombatLog(before);
      pendingPanelClear=true;
    }
    appendPacketToCombatLog(packet);

    var token=++animationToken;
    seen.push(packet.id);if(seen.length>64)seen.shift();
    setFeedbackBusy(true);
    requestAnimationFrame(function(){
      var slots={},lastDelay=0;
      ['entity','actor'].forEach(function(target){
        var anchor=targetAnchor(target)||anchors[target];
        var capacity=anchor?Math.max(1,Math.floor((anchor.y-12)/38)+1):1;
        slots[target]=[];for(var i=0;i<capacity;i++)slots[target].push(0);
      });
      // BR-1.1.93a: actor phase first, enemy phase after 1000ms; hits stagger 150ms, capped at 450ms.
      ['actor','entity'].forEach(function(phase){
        var events=packet.events.filter(function(e){return e&&(e.phase==='entity'?'entity':'actor')===phase});
        events.forEach(function(e,index){
          if(e.target!=='entity'&&e.target!=='actor')return;
          var available=slots[e.target],slot=0;
          for(var i=1;i<available.length;i++)if(available[i]<available[slot])slot=i;
          // Queue overflow for the simultaneous Party runtime instead of clipping labels above the Snapshot.
          var delay=Math.max((phase==='entity'?1000:0)+Math.min(index*150,450),available[slot]);
          available[slot]=delay+1800;lastDelay=Math.max(lastDelay,delay);
          setTimeout(function(){
            if(token!==animationToken)return;
            window.backroomPlayCombatFeedback(e,anchors[e.target]);
          },delay);
        });
      });
      setTimeout(function(){
        if(token!==animationToken)return;
        setFeedbackBusy(false);
        if(pendingPanelClear&&combatLogEncounter===encounter&&!activeCombat())resetCombatPanel();
      },lastDelay+1800);
    });
    return result;
  };

  var initial=activeCombat();
  if(initial)beginCombatLog(initial);
})();
