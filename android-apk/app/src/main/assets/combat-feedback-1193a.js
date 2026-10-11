/* COMBAT_FEEDBACK_1193A: presentation from BR-1.1.93a, typed runtime events only. */
/* COMBAT_TRANSIENT_LOG_R01: in-memory combat log; never writes GameState, save metadata or GM narrative log. */
(function(){
  'use strict';
  var seen=[],animationToken=0;
  var combatLogEncounter='',combatPanel=null,combatPanelMeta=null,combatPanelList=null;
  var turnIndicator=null,turnName=null,turnDetail=null,lastPartyActor='Cao Minh';

  function activeCombat(){
    return typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true?state.combat:null;
  }
  // One visual actor per action; the Entity overlay stays independently visible.
  function focusCombatActor(actorId){
    var snapshot=document.getElementById('snapshot');if(!snapshot)return;
    var valid={cao_minh:true,lucia:true,iris:true,syvial:true,an_nhien:true,entity:true};
    snapshot.dataset.combatFocus=valid[actorId]?actorId:'cao_minh';
  }
  function clearCombatFocus(){
    var snapshot=document.getElementById('snapshot');
    if(snapshot&&snapshot.dataset)delete snapshot.dataset.combatFocus;
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
      var title=document.createElement('h2');title.textContent='COMBAT LOG';
      combatPanelMeta=document.createElement('div');combatPanelMeta.id='combatFeedbackMeta';combatPanelMeta.className='combat-feedback-meta';
      head.appendChild(title);head.appendChild(combatPanelMeta);
      combatPanelList=document.createElement('div');combatPanelList.id='combatFeedbackEntries';combatPanelList.className='combat-feedback-entries';
      turnIndicator=document.createElement('div');
      turnIndicator.id='combatTurnIndicator';
      turnIndicator.className='combat-turn-indicator';
      turnIndicator.hidden=true;
      turnIndicator.setAttribute('role','status');
      turnIndicator.setAttribute('aria-live','polite');
      var spinner=document.createElement('span');
      spinner.className='combat-turn-spinner';
      spinner.setAttribute('aria-hidden','true');
      var info=document.createElement('div');info.className='combat-turn-info';
      turnName=document.createElement('div');turnName.id='combatTurnName';turnName.className='combat-turn-name';
      turnDetail=document.createElement('div');turnDetail.id='combatTurnDetail';turnDetail.className='combat-turn-detail';
      info.appendChild(turnName);info.appendChild(turnDetail);
      turnIndicator.appendChild(spinner);turnIndicator.appendChild(info);
      combatPanel.appendChild(head);combatPanel.appendChild(turnIndicator);combatPanel.appendChild(combatPanelList);
    }else{
      combatPanelMeta=document.getElementById('combatFeedbackMeta');
      combatPanelList=document.getElementById('combatFeedbackEntries');
      turnIndicator=document.getElementById('combatTurnIndicator');
      turnName=document.getElementById('combatTurnName');
      turnDetail=document.getElementById('combatTurnDetail');
    }
    var dice=document.getElementById('pokerDiceModal');
    var anchor=dice&&dice.isConnected!==false?dice:snapshot;
    if(anchor.nextElementSibling!==combatPanel)anchor.insertAdjacentElement('afterend',combatPanel);
    return combatPanel;
  }

  function showNow(name){
    lastPartyActor=String(name||'Cao Minh');
    if(combatPanelMeta)combatPanelMeta.textContent='NOW : '+lastPartyActor;
  }
  function appendCombatLine(kind,text){
    text=String(text||'').trim();if(!text)return;
    if(!ensureCombatPanel()||!combatPanelList)return;
    var entry=document.createElement('div');entry.className='combat-feedback-entry combat-feedback-entry--'+kind;
    entry.textContent=text;combatPanelList.appendChild(entry);
    combatPanelList.scrollTop=combatPanelList.scrollHeight;
  }
  function resetCombatPanel(){
    combatLogEncounter='';
    if(combatPanelList)combatPanelList.textContent='';
    if(combatPanel)combatPanel.hidden=true;
    if(turnIndicator)turnIndicator.hidden=true;
    var snapshot=document.getElementById('snapshot');
    if(snapshot&&snapshot.dataset)delete snapshot.dataset.combatSwitching;
    clearCombatFocus();showNow('Cao Minh');
  }
  function beginCombatLog(combat){
    if(!combat||combat.active!==true)return;
    var encounter=String(combat.encounterId||'');if(!encounter)return;
    if(!ensureCombatPanel())return;
    if(combatLogEncounter!==encounter){
      combatLogEncounter=encounter;seen=[];
      if(combatPanelList)combatPanelList.textContent='';
      if(turnIndicator)turnIndicator.hidden=true;
      showNow('Cao Minh');focusCombatActor('cao_minh');
      appendCombatLine('system','Bắt đầu giao chiến với '+prettyEntity(combat.entityName||combat.entityKey)+'.');
    }
    combatPanel.hidden=false;
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
    // Typed events are revealed per actor, not all at once.
    if(Array.isArray(packet.turns)&&packet.turns.length)return;
    (Array.isArray(packet.events)?packet.events:[]).forEach(appendDamageLine);
  }
  function appendDamageLine(e){
    if(!e||e.target!=='entity')return;
    var match=String(e.text||'').match(/^-([0-9]+) HP$/);
    var damage=match?Number(match[1]):0;if(!damage)return;
    var name=String(e.sourceName||lastPartyActor);
    var skill=String(e.skill||'đánh thường');
    var target=String(e.entityName||activeCombat()&&activeCombat().entityName||'Entity');
    var hp=Number(e.remainingHp),max=Number(e.maxHp);
    var suffix=e.remainingHp!=null&&e.maxHp!=null&&Number.isFinite(hp)&&max>0?' ['+hp+'/'+max+' HP]':'';
    var status=combatStatusVisual(e.status);
    appendCombatLine('actor',name+' sử dụng '+(skill==='đánh thường'?'đánh thường':'['+skill+']')+
      ' gây -'+damage+' HP lên '+target+suffix+(status?' ['+status.label+']':''));
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


  // COMBAT_TURN_SPINNER_R01: timeline owned by Kotlin, never guessed from damage.

  function showTurnCue(cue){
    if(!ensureCombatPanel()||!turnIndicator)return;
    var kind=cue.kind;
    if(kind==='actor'){showNow(cue.actorName||'Cao Minh');focusCombatActor(cue.actorId);}
    // Keep the last Party fighter in NOW while Entity counterattacks.
    turnIndicator.hidden=true;turnIndicator.dataset.kind=kind;
    if(turnName)turnName.textContent=kind==='actor'?String(cue.actorName||'Cao Minh'):String(cue.entityName||'Entity');
    if(turnDetail)turnDetail.textContent='';
    if(kind==='wave'){
      appendCombatLine('system','Bắt đầu giao chiến với '+String(cue.entityName||'Entity')+'.');
      return;
    }
    var details=Array.isArray(cue.details)?cue.details:[];
    if(kind==='actor'){
      var hasSupportSkill=details.some(function(v){return /Quăng Đại Cái Gì Đó/.test(String(v));});
      details.forEach(function(d){
        var line=String(d||'');
        if(/(?:trượt|né đòn|không trúng|tránh được)/i.test(line))
          appendCombatLine('actor',String(cue.actorName||'Đồng đội')+' tấn công Trượt '+String(cue.entityName||'Entity'));
        else if(/Quăng Đại Cái Gì Đó/.test(line))
          appendCombatLine('actor','An Nhiên sử dụng [Quăng Đại Cái Gì Đó] hỗ trợ cả nhóm');
        else if(/An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ/.test(line)&&!hasSupportSkill)
          appendCombatLine('actor','An Nhiên hỗ trợ cả nhóm');
      });
    }else if(kind==='entity'){
      var attack=details.find(function(v){return /phản công: Party chịu/.test(String(v));});
      if(attack){
        var hit=String(attack).match(/Party chịu -(\d+) HP \((\d+)\/(\d+)\)/);
        var skill=details.find(function(v){return / proc:/.test(String(v));});
        var action=skill?String(skill).split(' proc:')[0]:
          details.some(function(v){return /Devils And Gold kích hoạt/.test(String(v));})?'Devils And Gold':'đánh thường';
        if(hit)appendCombatLine('entity',String(cue.entityName||'Entity')+' sử dụng '+
          (action==='đánh thường'?'đánh thường':'['+action+']')+' gây -'+hit[1]+
          ' HP lên cả nhóm ['+hit[2]+'/'+hit[3]+' HP]');
      }else if(details.some(function(v){return /không xuyên|hụt đòn/.test(String(v));}))
        appendCombatLine('entity',String(cue.entityName||'Entity')+' tấn công Trượt cả nhóm');
    }
  }
  function playTypedTurnTimeline(packet,token,anchors,slots){
    var events=packet.events;
    var cues=packet.turns.filter(function(c){
      return c&&(c.kind==='actor'||c.kind==='entity'||c.kind==='wave'||c.kind==='party')&&
        Number.isInteger(c.eventIndex)&&c.eventIndex>=0&&c.eventIndex<=events.length;
    }).slice(0,12);
    if(!cues.length)return null;
    var reduced=typeof window.matchMedia==='function'&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var spinTime=reduced?90:800,clock=0,endTime=0,covered=0;
    function damageAt(e,when){
      if(!e||(e.target!=='actor'&&e.target!=='entity'))return;
      var available=slots[e.target],lane=0;
      for(var i=1;i<available.length;i++)if(available[i]<available[lane])lane=i;
      var delay=Math.max(when,available[lane]);
      available[lane]=delay+1800;
      endTime=Math.max(endTime,delay+1800);
      setTimeout(function(){
        if(token!==animationToken)return;
        appendDamageLine(e);
        window.backroomPlayCombatFeedback(e,anchors[e.target]);
      },delay);
    }
    cues.forEach(function(cue,index){
      var start=Math.max(covered,cue.eventIndex);
      if(index===0&&start>0){
        for(var pre=0;pre<start;pre++)damageAt(events[pre],pre*150);
        clock=Math.max(clock,Math.min(start*150,450));
      }
      var end=events.length;
      if(index+1<cues.length)end=Math.max(start,Math.min(events.length,cues[index+1].eventIndex));
      (function(c,at){
        setTimeout(function(){
          if(token!==animationToken)return;
          var snapshot=document.getElementById('snapshot');
          if(snapshot&&c.kind==='actor'&&snapshot.dataset.combatFocus!==c.actorId)snapshot.dataset.combatSwitching='true';
          showTurnCue(c);
        },at);
        setTimeout(function(){
          if(token!==animationToken)return;
          var snapshot=document.getElementById('snapshot');
          if(snapshot&&snapshot.dataset)delete snapshot.dataset.combatSwitching;
        },at+spinTime);
      })(cue,clock);
      var actionStart=clock+spinTime;
      for(var j=start;j<end;j++)damageAt(events[j],actionStart+(j-start)*150);
      covered=end;
      clock=actionStart+Math.max(260,(end-start)*150)+150;
    });
    for(var trailing=covered;trailing<events.length;trailing++)damageAt(events[trailing],clock+(trailing-covered)*150);
    return Math.max(clock,endTime)+50;
  }

  var previousTurn=window.backroomTurn;
  if(typeof previousTurn!=='function')return;
  window.backroomTurn=function(json){
    var before=typeof state!=='undefined'&&state&&state.combat;
    var anchors={entity:targetAnchor('entity'),actor:targetAnchor('actor')};
    // persistPresentation() replaces the in-memory state with a fresh Core projection.
    // Capture the transient packet before that replacement; never write it into save.
    var rawPacket=null;
    try{var incoming=JSON.parse(json);rawPacket=incoming&&incoming.combatFeedback;}catch(_){}
    var result=previousTurn.call(this,json);
    var next=typeof state!=='undefined'&&state;
    var after=next&&next.combat,packet=rawPacket||(next&&next.combatFeedback);

    if(!after||after.active!==true){
      ++animationToken;resetCombatPanel();setFeedbackBusy(false);
      return result;
    }
    beginCombatLog(after);

    if(before&&after&&before.encounterId!==after.encounterId){++animationToken;setFeedbackBusy(false);}
    if(!before&&after&&after.active===true&&!packet){seen=[];++animationToken;setFeedbackBusy(false);}
    if(!after&&!packet){++animationToken;setFeedbackBusy(false);}
    if(!packet||typeof packet.id!=='string'||!Array.isArray(packet.events)||seen.indexOf(packet.id)!==-1)return result;

    var encounter=String(packet.encounterId||'');
    var activeEncounter=String(after&&after.encounterId||before&&before.encounterId||'');
    if(!encounter||encounter!==activeEncounter)return result;

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
      var typedMs=Array.isArray(packet.turns)&&packet.turns.length?
        playTypedTurnTimeline(packet,token,anchors,slots):null;
      if(typedMs!==null){
        setTimeout(function(){
          if(token!==animationToken)return;
          setFeedbackBusy(false);
          if(activeCombat())focusCombatActor('cao_minh');
          showNow('Cao Minh');
        },typedMs);
        return;
      }
      // Legacy hit-only packets keep their existing stagger and phase timing.
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
            appendDamageLine(e);
            window.backroomPlayCombatFeedback(e,anchors[e.target]);
          },delay);
        });
      });
      setTimeout(function(){
        if(token!==animationToken)return;
        setFeedbackBusy(false);
          if(activeCombat())focusCombatActor('cao_minh');
        showNow('Cao Minh');
      },lastDelay+1800);
    });
    return result;
  };

  var initial=activeCombat();
  if(initial)beginCombatLog(initial);
})();
