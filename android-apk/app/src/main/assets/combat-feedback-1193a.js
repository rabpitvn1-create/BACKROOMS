/* COMBAT_FEEDBACK_1193A: presentation from BR-1.1.93a, typed runtime events only. */
(function(){
  'use strict';
  var seen=[],animationToken=0;
  function setFeedbackBusy(value){
    window.__combatFeedbackBusy=value;
    if(typeof busy!=='undefined')busy=value;
    if(value){
      var modal=document.getElementById('pokerDiceModal');
      if(modal){modal.hidden=true;modal.setAttribute('aria-hidden','true');}
      if(document.body)document.body.classList.remove('poker-dice-open');
    }else if(typeof window.ensureDirectCombatDice==='function')window.ensureDirectCombatDice();
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
  function combatStatusVisual(status){
    if(status==='Chảy máu')return {label:'CHẢY MÁU',key:'bleed'};
    if(status==='Trúng độc')return {label:'TRÚNG ĐỘC',key:'poison'};
    if(status==='Choáng')return {label:'CHOÁNG',key:'stun'};
    if(status==='Xuyên giáp'||status==='Phá giáp')return {label:'XUYÊN GIÁP',key:'armor'};
    if(status==='Mất phương hướng')return {label:'MẤT PHƯƠNG HƯỚNG',key:'disorient'};
    return null;
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
    if(!before&&after&&after.active===true&&!packet){seen=[];++animationToken;setFeedbackBusy(false);}
    if(!after&&!packet){++animationToken;setFeedbackBusy(false);}
    if(!packet||typeof packet.id!=='string'||!Array.isArray(packet.events)||seen.indexOf(packet.id)!==-1)return result;
    var encounter=String(packet.encounterId||'');
    if(!encounter||encounter!==String(after&&after.encounterId||before&&before.encounterId||''))return result;
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
          // Queue overflow for the new simultaneous Party runtime instead of clipping labels above the Snapshot.
          var delay=Math.max((phase==='entity'?1000:0)+Math.min(index*150,450),available[slot]);
          available[slot]=delay+1800;lastDelay=Math.max(lastDelay,delay);
          setTimeout(function(){
            if(token!==animationToken)return;
            window.backroomPlayCombatFeedback(e,anchors[e.target]);
          },delay);
        });
      });
      setTimeout(function(){if(token===animationToken)setFeedbackBusy(false)},lastDelay+1800);
    });
    return result;
  };
})();
