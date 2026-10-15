/* COMBAT_FEEDBACK_1193A: presentation from BR-1.1.93a, typed runtime events only. */
/* COMBAT_TRANSIENT_LOG_R01: in-memory combat log; never writes GameState, save metadata or GM narrative log. */
(function(){
  'use strict';
  var seen=[],animationToken=0;
  var motionEffects=[],overlayMotions=new WeakMap();
  var HIT_STEP_MS=630,FLOAT_MS=1200,CONTACT_MS=180;
  var combatLogEncounter='',combatPanel=null,combatPanelMeta=null,combatPanelList=null;
  var turnIndicator=null,turnName=null,turnDetail=null,lastPartyActor='Cao Minh';

  function activeCombat(){
    return typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true?state.combat:null;
  }
  // Party and Entity are independent visual slots; turn authority remains Core.
  var partyFocusEpoch=0;
  var knownPortraits={
    iris:'avatars/Iris_avatar.jpg',
    syvial:'avatars/Syvial_avatar.jpg',
    an_nhien:'avatars/an_nhien_avatar.png'
  };
  function combatPortraitSource(actorId,snapshot){
    var selector=actorId==='cao_minh'?'.snapshot-character':
      actorId==='lucia'?'.snapshot-lucia':'';
    var image=selector&&snapshot.querySelector(selector);
    if(image&&image.src)return image.src;
    var members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?
      state.partyDetails.members:[];
    var member=members.find(function(m){return m&&String(m.id)===actorId;});
    var path=String(member&&(member.avatarRef||member.avatar)||knownPortraits[actorId]||'');
    // Only bundled avatar assets are legal here; never render an arbitrary URI from state.
    return /^avatars\/[a-zA-Z0-9_./-]+\.(?:png|jpe?g|webp)$/.test(path)&&
      path.indexOf('..')===-1?path:'';
  }
  function removeCombatPortraits(snapshot){
    Array.prototype.forEach.call(snapshot.querySelectorAll('.combat-turn-exit,.combat-turn-avatar'),function(el){
      if(/(?:^|\s)combat-turn-(?:exit|avatar)(?:\s|$)/.test(String(el.className||'')))el.remove();
    });
  }
  function ensureActiveCombatAvatar(snapshot,actorId){
    if(actorId==='none'||actorId==='cao_minh'||actorId==='lucia')return;
    if(snapshot.querySelector('.combat-turn-avatar'))return;
    var source=combatPortraitSource(actorId,snapshot);
    if(!source)return;
    var portrait=document.createElement('img');
    portrait.className='combat-turn-avatar';portrait.src=source;portrait.alt='';
    portrait.setAttribute('aria-hidden','true');
    snapshot.appendChild(portrait);
  }
  function focusCombatActor(actorId){
    var snapshot=document.getElementById('snapshot');if(!snapshot)return;
    // An Entity attack never enters the Party slot or hides Cao Minh.
    var next=String(actorId||'cao_minh');
    if(next==='none'||next==='entity')next='cao_minh';
    var prior=String(snapshot.dataset.combatFocus||'cao_minh');
    if(next===prior){snapshot.dataset.combatFocus=next;ensureActiveCombatAvatar(snapshot,next);return;}
    var oldSource=combatPortraitSource(prior,snapshot);
    removeCombatPortraits(snapshot);
    var reduced=typeof window.matchMedia==='function'&&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var epoch=++partyFocusEpoch;
    if(oldSource){
      var departing=document.createElement('img');
      departing.className='combat-turn-exit';departing.src=oldSource;departing.alt='';
      departing.setAttribute('aria-hidden','true');
      snapshot.appendChild(departing);
      departing.addEventListener('animationend',function(){departing.remove()},{once:true});
      setTimeout(function(){departing.remove()},reduced?90:800);
    }
    snapshot.dataset.combatFocus=next;
    snapshot.dataset.combatAnimating='true';
    snapshot.dataset.combatCycle=String(1-Number(snapshot.dataset.combatCycle||'0'));
    ensureActiveCombatAvatar(snapshot,next);
    setTimeout(function(){
      if(epoch===partyFocusEpoch&&snapshot.dataset)delete snapshot.dataset.combatAnimating;
    },reduced?90:800);
  }
  // Snapshot renderer replaces child nodes; reattach only the active Party image.
  // Image load callbacks must never change the Party focus or rotate the Entity.
  window.backroomSyncCombatPortrait=function(){
    var snapshot=document.getElementById('snapshot');
    if(!snapshot||!activeCombat())return;
    ensureActiveCombatAvatar(snapshot,String(snapshot.dataset.combatFocus||'cao_minh'));
  };
  function clearCombatFocus(){
    ++partyFocusEpoch;
    var snapshot=document.getElementById('snapshot');if(!snapshot)return;
    removeCombatPortraits(snapshot);
    if(snapshot.dataset){
      delete snapshot.dataset.combatFocus;
      delete snapshot.dataset.combatCycle;
      delete snapshot.dataset.combatAnimating;
    }
    window.backroomCombatEntityVisualKey='';
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
    if(e.outcome==='miss'){
      appendCombatLine('actor',String(e.sourceName||lastPartyActor)+' tấn công Trượt '+String(e.entityName||'Entity'));return;
    }
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
  function reducedMotion(){
    return typeof window.matchMedia==='function'&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  function stopCombatMotion(){
    motionEffects.splice(0).forEach(function(animation){animation.cancel();});
    var box=document.getElementById('snapshot');
    if(box)Array.prototype.forEach.call(box.querySelectorAll('.combat-impact,.combat-player-hit-edge,.combat-float'),function(n){n.remove();});
    if(typeof document.querySelectorAll==='function')Array.prototype.forEach.call(
      document.querySelectorAll('.party-member .combat-impact,.party-member .combat-float'),function(n){n.remove();});
  }
  function animateCombat(el,frames,duration){
    if(!el||!el.isConnected||typeof el.animate!=='function')return;
    var animation=el.animate(frames,{duration:duration,easing:'linear'});
    motionEffects.push(animation);
    animation.onfinish=animation.oncancel=function(){
      var at=motionEffects.indexOf(animation);if(at!==-1)motionEffects.splice(at,1);
    };
    return animation;
  }
  // Contact motion owns transform only; turn coverflow owns translate/rotate/scale.
  // Preserve the native Entity's resting transform instead of resetting its anchor.
  function moveCombatOverlay(el,amount,kind){
    if(reducedMotion()||!el||typeof window.getComputedStyle!=='function')return;
    var previous=overlayMotions.get(el);if(previous)previous.cancel();
    var base=window.getComputedStyle(el).transform;
    if(base==='none')base='';
    var rest='translateX(0px) '+base,peak='translateX('+amount+'px) '+base;
    var frames=kind==='attack'?
      [{transform:rest,offset:0},{transform:peak,offset:CONTACT_MS/480,easing:'ease-out'},
       {transform:peak,offset:(CONTACT_MS+70)/480,easing:'ease-out'},{transform:rest,offset:1}]:
      kind==='dodge'?
      [{transform:rest,offset:0,easing:'ease-out'},{transform:peak,offset:.42,easing:'ease-in-out'},
       {transform:rest,offset:1}]:
      [{transform:rest,offset:0,easing:'ease-out'},{transform:peak,offset:.275},
       {transform:peak,offset:.45,easing:'ease-out'},{transform:rest,offset:1}];
    var motion=animateCombat(el,frames,kind==='attack'?480:kind==='dodge'?420:400);
    if(motion)overlayMotions.set(el,motion);
    return motion;
  }
  function targetAnchor(target,actorId){
    var box=document.getElementById('snapshot');
    if(!box)return null;
    var focus=String(box.dataset.combatFocus||'cao_minh');
    var wanted=String(actorId||'cao_minh');
    var selector=target==='entity'?'.snapshot-entity':
      wanted===focus?(wanted==='cao_minh'?'.snapshot-character':wanted==='lucia'?'.snapshot-lucia':'.combat-turn-avatar'):'';
    var el=selector&&box.querySelector(selector);
    // Off-screen Party damage belongs to that member's card, never Cao Minh's overlay.
    if(!el&&target==='actor'&&typeof document.querySelectorAll==='function'){
      var cards=document.querySelectorAll('.party-member[data-character]');
      for(var i=0;i<cards.length;i++)if(cards[i].dataset.character===wanted){
        box=cards[i];el=box.querySelector('img')||box;break;
      }
    }
    if(!el||!el.isConnected||el.style.opacity==='0')return null;
    var br=box.getBoundingClientRect(),er=el.getBoundingClientRect();
    if(er.width<=0||er.height<=0)return null;
    return {box:box,el:el,x:Math.max(16,Math.min(br.width-16,er.left-br.left+er.width/2)),
      y:Math.max(br.height*.34,Math.min(br.height-16,er.top-br.top+er.height*.34))};
  }
  function playAttackMotion(e){
    if(e.outcome==='tick')return;
    var source=e.target==='entity'?targetAnchor('actor',e.actorId):targetAnchor('entity');
    if(source)return moveCombatOverlay(source.el,e.target==='entity'?-14:14,'attack');
  }
  function transientEffect(anchor,className,duration){
    var node=document.createElement('div');node.className=className;
    node.setAttribute('aria-hidden','true');
    node.style.left=anchor.x+'px';
    node.style.top=(anchor.y+(className.indexOf('combat-impact')===0?28:0))+'px';anchor.box.appendChild(node);
    node.addEventListener('animationend',function(){node.remove();},{once:true});
    setTimeout(function(){node.remove();},duration+50);
  }
  window.backroomPlayCombatFeedback=function(event,fallback){
    var e=event||{},text=String(e.text||'').trim(),miss=e.outcome==='miss';
    if(miss?text!=='MISS':(!/^-\d+ HP$/i.test(text)||Number(text.slice(1,-3))<=0))return;
    if(e.target!=='entity'&&e.target!=='actor')return;
    var target=e.target,anchor=targetAnchor(target,e.actorId);
    // A stale fallback from a previous render/wave must not receive new feedback.
    if(!anchor)return;
    if(miss){
      moveCombatOverlay(anchor.el,target==='entity'?-11:11,'dodge');
    }else if(e.outcome==='tick'){
      animateCombat(anchor.el,[{filter:'none'},{filter:e.status==='Trúng độc'?'sepia(1) saturate(2) hue-rotate(230deg)':'sepia(1) saturate(2) hue-rotate(320deg)'},{filter:'none'}],250);
    }else if(e.flash){
      moveCombatOverlay(anchor.el,(target==='entity'?-1:1)*(e.critical===true?10:6),'hit');
      animateCombat(anchor.el,[{filter:'none'},{filter:'brightness(2.4) drop-shadow(0 0 5px #fff)'},{filter:'none'}],140);
      transientEffect(anchor,'combat-impact'+(e.critical===true?' combat-impact--critical':''),320);
      if(target==='actor'&&anchor.box.id==='snapshot')transientEffect(anchor,'combat-player-hit-edge',200);
    }
    var active=anchor.box.querySelectorAll('.combat-float[data-target="'+target+'"]'),lane=0,occupied=[];
    for(var i=0;i<active.length;i++)occupied.push(Number(active[i].dataset.lane));
    while(occupied.indexOf(lane)!==-1)lane++;
    var floater=document.createElement('div');floater.className='combat-float';floater.dataset.target=target;floater.dataset.lane=String(lane);
    if(miss)floater.classList.add('combat-float--miss');
    else if(e.critical===true)floater.classList.add('combat-float--critical');
    var value=document.createElement('span');value.className='combat-float-value';value.textContent=text;floater.appendChild(value);
    var status=combatStatusVisual(e.status);
    if(miss){
      // MISS is the full label; do not attach an attack/status badge.
    }else if(status){
      floater.classList.add('combat-float--status','combat-float--status-'+status.key);
      var badge=document.createElement('span');badge.className='combat-float-status combat-float-status--'+status.key;
      badge.textContent=status.label;value.insertBefore(badge,value.firstChild);
    }else{
      var skill=typeof e.skill==='string'?e.skill.trim():'';
      var isSkill=!!skill&&skill.toLocaleLowerCase('vi-VN')!=='đánh thường';
      floater.classList.add(isSkill?'combat-float--skill':'combat-float--normal');
      var attackBadge=document.createElement('span');
      attackBadge.className='combat-float-status combat-float-status--'+(isSkill?'skill':'normal');
      attackBadge.textContent=isSkill?skill:'Đánh thường';value.insertBefore(attackBadge,value.firstChild);
    }
    floater.style.left=anchor.x+'px';floater.style.top=(anchor.y-18-lane*38)+'px';anchor.box.appendChild(floater);
    floater.addEventListener('animationend',function(){floater.remove()},{once:true});
    setTimeout(function(){floater.remove()},FLOAT_MS+50);
  };


  // COMBAT_TURN_SPINNER_R01: timeline owned by Kotlin, never guessed from damage.

  function showTurnCue(cue){
    if(!ensureCombatPanel()||!turnIndicator)return;
    var kind=cue.kind;
    if(kind==='actor'){showNow(cue.actorName||'Cao Minh');focusCombatActor(cue.actorId);}
    else if(kind==='entity'){showNow('Cao Minh');focusCombatActor('cao_minh');}
    else if(kind==='wave'&&cue.entityKey&&typeof window.backroomSwitchCombatEntityWave==='function'){
      stopCombatMotion();
      window.backroomSwitchCombatEntityWave(cue.entityKey,cue.waveNumber);
    }
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
        if(/Quăng Đại Cái Gì Đó/.test(line))
          appendCombatLine('actor','An Nhiên sử dụng [Quăng Đại Cái Gì Đó] hỗ trợ cả nhóm');
        else if(/An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ/.test(line)&&!hasSupportSkill)
          appendCombatLine('actor','An Nhiên hỗ trợ cả nhóm');
      });
    }
  }
  function appendEntityResult(cue){
    var details=Array.isArray(cue.details)?cue.details:[];
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

  function playTypedTurnTimeline(packet,token,anchors,slots){
    var events=packet.events;
    var cues=packet.turns.filter(function(c){
      return c&&(c.kind==='actor'||c.kind==='entity'||c.kind==='wave'||c.kind==='party')&&
        Number.isInteger(c.eventIndex)&&c.eventIndex>=0&&c.eventIndex<=events.length;
    }); // Keep every Party/Entity cue in long encounters; do not silently truncate turns.
    if(!cues.length)return null;
    var reduced=typeof window.matchMedia==='function'&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var spinTime=reduced?90:800,clock=0,endTime=0,covered=0;
    function damageAt(e,when,onContact){
      if(!e||(e.target!=='actor'&&e.target!=='entity'))return when;
      var available=slots[e.target],lane=0;
      for(var i=1;i<available.length;i++)if(available[i]<available[lane])lane=i;
      var delay=Math.max(when,available[lane]);
      var contact=e.outcome==='tick'||reduced?0:CONTACT_MS;
      available[lane]=delay+contact+FLOAT_MS;
      endTime=Math.max(endTime,available[lane]);
      var attackMotion;
      setTimeout(function(){
        if(token!==animationToken)return;
        attackMotion=playAttackMotion(e);
      },delay);
      function revealContact(){
        if(token!==animationToken)return;
        // WebView may start the animation after its wall-clock timer. Reveal
        // impact and damage only when that animation reaches actual contact.
        if(attackMotion){
          if(attackMotion.playState==='idle')return;
          if(attackMotion.currentTime==null||attackMotion.currentTime<contact){
            requestAnimationFrame(revealContact);return;
          }
        }
        if(onContact)onContact();
        appendDamageLine(e);
        window.backroomPlayCombatFeedback(e,anchors[e.target]);
      }
      setTimeout(revealContact,delay+contact);
      return delay+(e.outcome==='tick'?250:reduced?90:HIT_STEP_MS);
    }
    cues.forEach(function(cue,index){
      var entityResultShown=false;
      function revealEntityResult(){
        if(entityResultShown)return;
        entityResultShown=true;appendEntityResult(cue);
      }
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
          showTurnCue(c);
        },at);
      })(cue,clock);
      var actionStart=clock+spinTime,nextAction=actionStart;
      for(var j=start;j<end;j++){
        var e=events[j];if(!e)continue;
        if(e&&e.target==='entity'&&!e.actorId)e=Object.assign({},e,{actorId:cue.actorId||'cao_minh'});
        // Reactive Party counters can follow an Entity cue. Focus their source
        // before lunging; incoming group results keep each defender's own anchor.
        if(e.target==='entity'&&e.outcome!=='tick'&&String(e.actorId||'cao_minh')!==String(cue.actorId||'cao_minh')){
          (function(id,at){setTimeout(function(){if(token===animationToken)focusCombatActor(id);},at);})(e.actorId,nextAction);
          nextAction+=spinTime;
        }
        if(cue.kind==='entity'&&e.target==='actor'&&e.outcome!=='tick'){
          // One shared Entity hit has several defender outcomes, not several attacks.
          var groupStart=nextAction;
          var defenders=[];
          while(j+1<end&&events[j+1]&&events[j+1].target==='actor'&&events[j+1].outcome!=='tick'){
            defenders.push(events[++j]);
          }
          // Every defender belongs to the same physical impact, including
          // hit/MISS outcomes and when the lunge starts late in WebView.
          nextAction=damageAt(e,groupStart,(function(group,result){return function(){
            result();
            group.forEach(function(hit){window.backroomPlayCombatFeedback(hit,anchors[hit.target]);});
          };})(defenders,revealEntityResult));
        }else nextAction=damageAt(e,nextAction);
      }
      if(cue.kind==='entity'&&!events.slice(start,end).some(function(e){return e&&e.target==='actor'&&e.outcome!=='tick';})){
        setTimeout(function(){if(token===animationToken)revealEntityResult();},actionStart+(reduced?0:CONTACT_MS));
      }
      covered=end;
      clock=end>start?nextAction:actionStart+260+150;
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
    // Combat Core may settle the last Entity in the first Party action. Keep
    // the authoritative final turn cues on screen BEFORE projecting inactive
    // combat, otherwise the renderer removes Entity/Lucia overlays immediately.
    var incomingCombat;
    try{incomingCombat=JSON.parse(json).combat;}catch(_){}
    if(before&&before.active===true&&rawPacket&&Array.isArray(rawPacket.turns)&&
       rawPacket.turns.some(function(c){return c&&c.kind==='wave';})){
      window.backroomCombatEntityVisualKey=String(before.entityKey||'');
    }
    var completing=before&&before.active===true&&
      (!incomingCombat||incomingCombat.active!==true)&&
      rawPacket&&Array.isArray(rawPacket.events)&&Array.isArray(rawPacket.turns)&&
      rawPacket.turns.some(function(c){return c&&c.kind==='actor';});
    if(completing){
      var completionToken=++animationToken,context=this;
      stopCombatMotion();
      beginCombatLog(before);
      setFeedbackBusy(true);
      requestAnimationFrame(function(){
        if(completionToken!==animationToken)return;
        var slots={entity:[0],actor:[0]};
        var anchors={entity:targetAnchor('entity'),actor:targetAnchor('actor')};
        var elapsed=playTypedTurnTimeline(rawPacket,completionToken,anchors,slots);
        setTimeout(function(){
          if(completionToken!==animationToken)return;
          previousTurn.call(context,json);
          resetCombatPanel();
          setFeedbackBusy(false);
        },elapsed===null?0:elapsed);
      });
      return;
    }
    var result=previousTurn.call(this,json);
    var next=typeof state!=='undefined'&&state;
    var after=next&&next.combat,packet=rawPacket||(next&&next.combatFeedback);

    if(!after||after.active!==true){
      // The Dice and Combat Log close together on combat completion.
      // Never leave the last packet queued behind a hidden panel.
      ++animationToken;stopCombatMotion();resetCombatPanel();setFeedbackBusy(false);
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
    stopCombatMotion();
    seen.push(packet.id);if(seen.length>64)seen.shift();
    setFeedbackBusy(true);
    requestAnimationFrame(function(){
      if(token!==animationToken)return;
      var slots={},lastDelay=0;
      ['entity','actor'].forEach(function(target){
        var anchor=targetAnchor(target)||anchors[target];
        var capacity=anchor?Math.max(1,Math.floor((anchor.y-24)/38)+1):1;
        slots[target]=[];for(var i=0;i<capacity;i++)slots[target].push(0);
      });
      var typedMs=Array.isArray(packet.turns)&&packet.turns.length?
        playTypedTurnTimeline(packet,token,anchors,slots):null;
      if(typedMs!==null){
        setTimeout(function(){
          if(token!==animationToken)return;
          focusCombatActor('cao_minh');
          showNow('Cao Minh');
          window.backroomCombatEntityVisualKey='';
          setFeedbackBusy(false);
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
        focusCombatActor('cao_minh');
        showNow('Cao Minh');
        setFeedbackBusy(false);
      },lastDelay+1800);
    });
    return result;
  };

  var initial=activeCombat();
  if(initial)beginCombatLog(initial);
})();
