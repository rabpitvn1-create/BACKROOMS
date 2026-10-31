/* COMBAT_FEEDBACK_1193A: presentation from BR-1.1.93a, typed runtime events only. */
/* COMBAT_TRANSIENT_LOG_R01: in-memory combat log; never writes GameState, save metadata or GM narrative log. */
(function(){
  'use strict';
  var seen=[],animationToken=0;
  var motionEffects=[],overlayMotions=new WeakMap();
  var HIT_STEP_MS=630,FLOAT_MS=1200,CONTACT_MS=180;
  var ENTITY_SHATTER_MS=1320,entityShatters=[],shatteredEntities=new WeakSet();
  var combatLogEncounter='',combatPanel=null,combatPanelMeta=null,combatPanelList=null;
  var turnIndicator=null,turnName=null,turnDetail=null,lastPartyActor='Cao Minh';

  function activeCombat(){
    return typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true?state.combat:null;
  }
  // Party and Entity are independent visual slots; turn authority remains Core.
  var partyFocusEpoch=0;
  var knownPortraits={};
  function fitCombatPortrait(image,snapshot){
    if(window.backroomSpriteLayout)window.backroomSpriteLayout.fit(image,snapshot,'right');
  }
  function prepareCombatPortrait(image,snapshot){
    fitCombatPortrait(image,snapshot);
    if(!image||image.dataset.combatSizingBound)return;
    image.dataset.combatSizingBound='true';
    image.addEventListener('load',function(){
      if(image.isConnected!==false)fitCombatPortrait(image,snapshot);
    });
  }
  function syncCombatPortraitSizes(snapshot){
    Array.prototype.forEach.call(snapshot.querySelectorAll('.snapshot-character,.snapshot-lucia,.combat-turn-avatar,.combat-turn-exit'),function(image){prepareCombatPortrait(image,snapshot);});
  }
  if(typeof window.ResizeObserver==='function'){
    var portraitResize=new ResizeObserver(function(){var box=document.getElementById('snapshot');if(box&&activeCombat())syncCombatPortraitSizes(box);});
    var portraitStage=document.getElementById('snapshot');if(portraitStage)portraitResize.observe(portraitStage);
  }
  // Runtime IDs and the APK's animation IDs have different separators.
  function normalizeActorId(id){
    if(id==='cao-minh')return 'cao_minh';
    if(id==='luc-tram')return 'luc_tram';
    return String(id||'');
  }
  function combatPortraitSource(actorId,snapshot){
    var key=normalizeActorId(actorId).replace(/_/g,'-');
    if(key==='nguyen-quoc-nam')key='nguyen_quoc_nam';
    if(key==='hua-tieu-lan')key='hua_tieu_lan';
    if(key==='trac-lam')key='trac_lam';
    var resolver=window.backroomCloudinary;
    // Character art is read from today's Cloudinary file, never APK sprites.
    if(resolver){
      var whole=resolver.characterOverlay(key,true);
      if(whole)return whole;
      var avatar=resolver.characterAvatar(key);
      if(avatar)return avatar;
    }
    return '';
  }
  function removeCombatPortraits(snapshot){
    Array.prototype.forEach.call(snapshot.querySelectorAll('.combat-turn-exit,.combat-turn-avatar'),function(el){
      if(/(?:^|\s)combat-turn-(?:exit|avatar)(?:\s|$)/.test(String(el.className||'')))el.remove();
    });
  }
  function ensureActiveCombatAvatar(snapshot,actorId){
    actorId=normalizeActorId(actorId);
    if(actorId==='none'||actorId==='cao_minh'||actorId==='lucia')return;
    if(snapshot.querySelector('.combat-turn-avatar'))return;
    var source=combatPortraitSource(actorId,snapshot);
    if(!source)return;
    var portrait=document.createElement('img');
    portrait.className='combat-turn-avatar';portrait.src=source;portrait.alt='';
    portrait.dataset.cloudinaryActor=actorId;
    portrait.setAttribute('aria-hidden','true');
    snapshot.appendChild(portrait);
    prepareCombatPortrait(portrait,snapshot);
  }
  function focusCombatActor(actorId){
    var snapshot=document.getElementById('snapshot');if(!snapshot)return;
    // An Entity attack never enters the Party slot or hides Cao Minh.
    var next=normalizeActorId(actorId||'cao_minh');
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
      prepareCombatPortrait(departing,snapshot);
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
    syncCombatPortraitSizes(snapshot);
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
    if(dice&&snapshot.nextElementSibling!==dice)snapshot.insertAdjacentElement('afterend',dice);
    var anchor=dice&&dice.isConnected!==false?dice:snapshot;
    if(anchor.nextElementSibling!==combatPanel)anchor.insertAdjacentElement('afterend',combatPanel);
    return combatPanel;
  }

  window.backroomSyncCombatLog=function(){
    var combat=activeCombat();
    if(combat)beginCombatLog(combat);else resetCombatPanel();
  };

  function showNow(name){
    lastPartyActor=String(name||'Cao Minh');
    if(combatPanelMeta)combatPanelMeta.textContent='NOW : '+lastPartyActor;
  }
  // Combat events already contain verified names, skills, statuses and HP values.
  // Render their typed fields as safe text nodes, never parse arbitrary HTML or guess names.
  function combatSemantic(kind,text){return {kind:kind,text:String(text==null?'':text)};}
  function appendCombatLine(kind,parts){
    var segments=Array.isArray(parts)?parts:[String(parts==null?'':parts)];
    var full=segments.map(function(p){return typeof p==='string'?p:String(p&&p.text||'');}).join('');
    if(!full.trim()||!ensureCombatPanel()||!combatPanelList)return;
    var entry=document.createElement('div');entry.className='combat-feedback-entry combat-feedback-entry--'+kind;
    segments.forEach(function(part){
      var span=document.createElement('span'),semantic=part&&typeof part==='object';
      var role=semantic?String(part.kind||''):'';
      if(!/^(?:character|entity|skill|damage|hp|miss|status-(?:poison|bleed|stun|armor|disorient))$/.test(role))role='';
      if(role)span.className='combat-semantic combat-semantic--'+role;
      span.textContent=semantic?String(part.text||''):String(part);
      entry.appendChild(span);
    });
    combatPanelList.appendChild(entry);
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
      appendCombatLine('system',['Bắt đầu giao chiến với ',
        combatSemantic('entity',prettyEntity(combat.entityName||combat.entityKey)),'.']);
    }
    combatPanel.hidden=false;
  }
  function combatStatusVisual(status){
    if(status==='Chảy máu')return {label:'CHẢY MÁU',key:'bleed'};
    if(status==='Trúng độc')return {label:'TRÚNG ĐỘC',key:'poison'};
    if(status==='Choáng')return {label:'CHOÁNG',key:'stun'};
    if(status==='Xuyên giáp'||status==='Phá giáp')return {label:'XUYÊN GIÁP',key:'armor'};
    if(status==='Mất phương hướng')return {label:'MẤT PHƯƠNG HƯỚNG',key:'disorient'};
    // Extend the existing five effects without changing any prior mappings.
    if(status==='Khóa lối thoát')return {label:'KHÓA LỐI THOÁT',key:'pin'};
    if(status==='Trọng kích')return {label:'TRỌNG KÍCH',key:'heavy'};
    if(status==='Hộ giáp')return {label:'HỘ GIÁP',key:'ward'};
    return null;
  }

  function appendPacketToCombatLog(packet){
    // Typed events are revealed per actor, not all at once.
    if(Array.isArray(packet.turns)&&packet.turns.length)return;
    (Array.isArray(packet.events)?packet.events:[]).forEach(appendDamageLine);
  }
  function appendDamageLine(e){
    var parts=window.backroomCombatLog&&window.backroomCombatLog.format(e);
    if(parts)appendCombatLine(e.target==='entity'?'actor':'entity',parts);
  }

  function appendPacketToCombatLog(packet){
    // The animation timeline reveals each verified event exactly once.
  }

  function setFeedbackBusy(value){
    window.__combatFeedbackBusy=!!value;
    if(typeof window.ensureDirectCombatDice==='function')window.ensureDirectCombatDice();
  }
  function reducedMotion(){
    return typeof window.matchMedia==='function'&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  function stopCombatMotion(){
    entityShatters.slice().forEach(function(effect){effect.finish(true);});
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
    var wanted=normalizeActorId(actorId||'cao_minh');
    var selector=target==='entity'?'.snapshot-entity':
      wanted===focus?(wanted==='cao_minh'?'.snapshot-character':wanted==='lucia'?'.snapshot-lucia':'.combat-turn-avatar'):'';
    var el=selector&&box.querySelector(selector);
    // Off-screen Party damage belongs to that member's card, never Cao Minh's overlay.
    if(!el&&target==='actor'&&typeof document.querySelectorAll==='function'){
      var cards=document.querySelectorAll('.party-member[data-character]');
      for(var i=0;i<cards.length;i++)if(normalizeActorId(cards[i].dataset.character)===wanted){
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
  function lethalEntityHit(e){
    return !!e&&e.target==='entity'&&e.outcome!=='miss'&&
      typeof e.remainingHp==='number'&&e.remainingHp===0;
  }
  function afterEntityShatter(token,callback){
    if(token!==animationToken)return;
    var effect=entityShatters.find(function(item){return item.token===token&&item.active;});
    if(effect)effect.waiters.push(callback);else callback();
  }
  function shatterEntity(e){
    if(!lethalEntityHit(e))return;
    var box=document.getElementById('snapshot'),source=box&&box.querySelector('.snapshot-entity');
    if(!source||source.isConnected===false||shatteredEntities.has(source))return;
    var url=source.currentSrc||source.src;if(!url)return;
    // Freeze only this defeated overlay; Party motion and the background remain intact.
    if(typeof source.getAnimations==='function')source.getAnimations().forEach(function(a){a.cancel();});
    var rect=source.getBoundingClientRect(),base=box.getBoundingClientRect();
    if(rect.width<=0||rect.height<=0)return;
    var reduced=reducedMotion(),duration=reduced?180:ENTITY_SHATTER_MS;
    var layer=document.createElement('div');layer.className='entity-shatter'+(reduced?' entity-shatter--reduced':'');
    layer.setAttribute('aria-hidden','true');
    layer.style.cssText='left:'+(rect.left-base.left)+'px;top:'+(rect.top-base.top)+'px;width:'+rect.width+'px;height:'+rect.height+'px';
    function image(parent){
      var img=document.createElement('img');img.src=url;img.alt='';img.draggable=false;parent.appendChild(img);return img;
    }
    if(reduced){
      var ghost=document.createElement('div');ghost.className='entity-shatter-ghost';image(ghost);layer.appendChild(ghost);
    }else{
      // Fresh branching fissures and irregular mesh on every defeat; no radial pie slices.
      var paths=[],cx=47+Math.random()*6,cy=44+Math.random()*8;
      for(var ray=0;ray<9;ray++){
        var angle=(ray+Math.random()*.7)*Math.PI*2/9,x=cx,y=cy,d='M'+x.toFixed(2)+' '+y.toFixed(2);
        for(var step=0;step<7;step++){
          var bend=angle+(Math.random()-.5)*.9,len=5+Math.random()*5;
          x+=Math.cos(bend)*len;y+=Math.sin(bend)*len;d+='L'+x.toFixed(2)+' '+y.toFixed(2);
          if(step>1&&step<6){
            var fork=bend+(Math.random()<.5?-1:1)*(.5+Math.random()*.8);
            paths.push('M'+x.toFixed(2)+' '+y.toFixed(2)+'l'+(Math.cos(fork)*7).toFixed(2)+' '+(Math.sin(fork)*7).toFixed(2)+' '+(Math.cos(fork+.35)*5).toFixed(2)+' '+(Math.sin(fork+.35)*5).toFixed(2));
          }
        }
        paths.push(d);
      }
      var pathMarkup=paths.map(function(d){return '<path d="'+d+'"/>';}).join('');
      var crackSvg='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><g fill="none" stroke-linecap="round" stroke-linejoin="round"><g stroke="#8cddff" stroke-width="3.4" opacity=".16">'+pathMarkup+'</g><g stroke="#a6e6ff" stroke-width="1.5" opacity=".24">'+pathMarkup+'</g><g stroke="#e0f7ff" stroke-width=".55" opacity=".7">'+pathMarkup+'</g><g stroke="white" stroke-width=".18">'+pathMarkup+'</g></g></svg>';
      var cracks=document.createElement('div');cracks.className='entity-shatter-cracks';
      cracks.style.maskImage=cracks.style.webkitMaskImage='url('+JSON.stringify(url)+')';
      cracks.style.backgroundImage='url("data:image/svg+xml,'+encodeURIComponent(crackSvg)+'")';
      cracks.style.cssText+=';--core-x:'+cx+'%;--core-y:'+cy+'%';
      // One masked charge restores the core flash without per-shard brightness filters.
      var charge=document.createElement('div');charge.className='entity-shatter-charge';
      charge.style.maskImage=charge.style.webkitMaskImage='url('+JSON.stringify(url)+')';
      charge.style.cssText+=';--core-x:'+cx+'%;--core-y:'+cy+'%';
      layer.appendChild(charge);
      layer.appendChild(cracks);
      ['core','shockwave','flare'].forEach(function(name){
        var node=document.createElement('div');node.className='entity-shatter-'+name;
        node.style.left=cx+'%';node.style.top=cy+'%';layer.appendChild(node);
      });
      var grid=[],cols=5,rows=7,spread=Math.min(100,Math.min(rect.width,rect.height)*.4);
      for(var gy=0;gy<=rows;gy++){
        grid[gy]=[];
        for(var gx=0;gx<=cols;gx++)grid[gy][gx]=[
          gx===0?0:gx===cols?100:(gx+(Math.random()-.5)*.7)*100/cols,
          gy===0?0:gy===rows?100:(gy+(Math.random()-.5)*.7)*100/rows];
      }
      function fragment(points){
        var mx=0,my=0;points.forEach(function(p){mx+=p[0]/points.length;my+=p[1]/points.length;});
        var angle=Math.atan2((my-cy)*rect.height,(mx-cx)*rect.width)+(Math.random()-.5)*.7;
        var travel=spread*(.35+Math.random()*.8),dx=Math.cos(angle)*travel,dy=Math.sin(angle)*travel;
        // Limit paint to the shard's bounds while rendering an actual Entity image.
        // WebView can silently skip a clipped CSS background with strict containment.
        var xs=points.map(function(p){return p[0];}),ys=points.map(function(p){return p[1];});
        var left=Math.min.apply(Math,xs)*rect.width/100,top=Math.min.apply(Math,ys)*rect.height/100;
        var width=(Math.max.apply(Math,xs)-Math.min.apply(Math,xs))*rect.width/100;
        var height=(Math.max.apply(Math,ys)-Math.min.apply(Math,ys))*rect.height/100;
        var shard=document.createElement('div');shard.className='entity-shatter-shard';
        shard.style.cssText='left:'+left.toFixed(2)+'px;top:'+top.toFixed(2)+'px;width:'+width.toFixed(2)+'px;height:'+height.toFixed(2)+'px;clip-path:polygon('+points.map(function(p){return ((p[0]*rect.width/100-left)/width*100).toFixed(2)+'% '+((p[1]*rect.height/100-top)/height*100).toFixed(2)+'%';}).join(',')+');transform-origin:'+((mx*rect.width/100-left)/width*100).toFixed(2)+'% '+((my*rect.height/100-top)/height*100).toFixed(2)+'%;--shard-x:'+dx.toFixed(1)+'px;--shard-y:'+dy.toFixed(1)+'px;--shard-drop:'+(dy+15+Math.random()*35).toFixed(1)+'px;--shard-rotate:'+((Math.random()-.5)*240).toFixed(1)+'deg;--shard-tilt:'+Math.cos((Math.random()-.5)*130*Math.PI/180).toFixed(2)+';--shard-scale:'+(.25+Math.random()*.45).toFixed(2)+';animation-delay:460ms;animation-duration:'+(680+Math.random()*160).toFixed(0)+'ms';
        // Use the image element pipeline that already displays the Entity correctly.
        // All shards share a decoded source, but each wrapper clips to its small bounds.
        var pieceImage=image(shard);
        pieceImage.style.cssText='position:absolute;left:'+(-left).toFixed(2)+'px;top:'+(-top).toFixed(2)+'px;width:'+rect.width+'px;height:'+rect.height+'px';
        layer.appendChild(shard);
      }
      for(var row=0;row<rows;row++)for(var col=0;col<cols;col++){
        var a=grid[row][col],b=grid[row][col+1],c=grid[row+1][col+1],d=grid[row+1][col];
        if(Math.random()<.45){
          if(Math.random()<.5){fragment([a,b,c]);fragment([a,c,d]);}
          else{fragment([a,b,d]);fragment([b,c,d]);}
        }else fragment([a,b,c,d]);
      }
      for(var spark=0;spark<26;spark++){
        var theta=Math.random()*Math.PI*2,distance=spread*(.25+Math.random()*.85);
        var mote=document.createElement('i');mote.className='entity-shatter-spark';
        mote.style.cssText='left:'+cx+'%;top:'+cy+'%;--spark-x:'+(Math.cos(theta)*distance).toFixed(1)+'px;--spark-y:'+(Math.sin(theta)*distance).toFixed(1)+'px;--spark-angle:'+theta+'rad;--spark-length:'+(2+Math.random()*7).toFixed(1)+'px;animation-delay:'+(450+Math.random()*60).toFixed(0)+'ms;animation-duration:'+(300+Math.random()*450).toFixed(0)+'ms';
        layer.appendChild(mote);
      }
    }
    var priorOpacity=source.style.opacity;
    var effect={source:source,token:animationToken,active:true,waiters:[],finish:function(cancelled){
      if(!effect.active)return;effect.active=false;layer.remove();
      var index=entityShatters.indexOf(effect);if(index!==-1)entityShatters.splice(index,1);
      if(cancelled){source.style.opacity=priorOpacity;shatteredEntities.delete(source);return;}
      source.style.opacity='0';
      effect.waiters.splice(0).forEach(function(callback){afterEntityShatter(effect.token,callback);});
    }};
    shatteredEntities.add(source);entityShatters.push(effect);box.appendChild(layer);
    if(reduced)source.style.opacity='0';else{
      setTimeout(function(){if(effect.active&&effect.token===animationToken)source.style.opacity='0';},480);
    }
    setTimeout(function(){effect.finish(false);},duration);
  }
  window.backroomPlayCombatFeedback=function(event,fallback){
    var e=event||{},text=String(e.text||'').trim(),miss=e.outcome==='miss';
    // A shield-only cue is not damage; preserve all existing hit validation.
    var buff=e.outcome==='buff'&&e.status==='Hộ giáp';
    if(buff?text!=='HỘ GIÁP':(miss?text!=='MISS':(!/^-\d+ HP$/i.test(text)||Number(text.slice(1,-3))<=0)))return;
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
    var value=document.createElement('span');value.className='combat-float-value';value.textContent=buff?'':text;floater.appendChild(value);
    var status=combatStatusVisual(e.status);
    // WARD belongs to the Entity, never to the Party member receiving damage.
    if(!buff&&status&&status.key==='ward')status=null;
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
    if(!buff)shatterEntity(e);
    if(!buff&&!miss&&target==='actor'&&e.status==='Hộ giáp')
      window.backroomPlayCombatFeedback({target:'entity',outcome:'buff',text:'HỘ GIÁP',status:'Hộ giáp'});
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
    // Preserve the Party portrait, but show a distinct, truthful Entity-turn cue.
    var isFightingTurn=kind==='actor'||kind==='entity';
    turnIndicator.hidden=!isFightingTurn;turnIndicator.dataset.kind=kind;
    turnIndicator.classList.toggle('combat-turn-indicator--loading',isFightingTurn);
    if(turnName)turnName.textContent=kind==='actor'?String(cue.actorName||'Cao Minh'):String(cue.entityName||'Entity');
    if(turnDetail)turnDetail.textContent=kind==='actor'?'LƯỢT PARTY':kind==='entity'?'LƯỢT ENTITY':'';
    if(kind==='entity')appendCombatLine('entity',[
      combatSemantic('entity',String(cue.entityName||'Entity')),' đến lượt.']);
    if(kind==='wave'){
      appendCombatLine('system',['Bắt đầu giao chiến với ',
        combatSemantic('entity',cue.entityName||'Entity'),'.']);
      return;
    }
    var details=Array.isArray(cue.details)?cue.details:[];
    if(kind==='actor'){
      var hasSupportSkill=details.some(function(v){return /Quăng Đại Cái Gì Đó/.test(String(v));});
      details.forEach(function(d){
        var line=String(d||'');
        if(/Quăng Đại Cái Gì Đó/.test(line))
          appendCombatLine('actor',[combatSemantic('character','An Nhiên'),' dùng tuyệt kĩ [',
            combatSemantic('skill','Quăng Đại Cái Gì Đó'),'] hỗ trợ cả nhóm']);
        else if(/An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ/.test(line)&&!hasSupportSkill)
          appendCombatLine('actor',[combatSemantic('character','An Nhiên'),' hỗ trợ cả nhóm']);
      });
    }
  }
  function appendEntityResult(cue){
    // Every defender is logged from its own typed damage event, never prose.
  }

  function playTypedTurnTimeline(packet,token,anchors,slots){
    if(!Array.isArray(packet.turns))return null;
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
      var death=lethalEntityHit(e)?(reduced?180:ENTITY_SHATTER_MS):0;
      available[lane]=delay+contact+Math.max(FLOAT_MS,death);
      endTime=Math.max(endTime,available[lane]);
      var attackMotion;
      setTimeout(function(){
        afterEntityShatter(token,function(){attackMotion=playAttackMotion(e);});
      },delay);
      function revealContact(){
        if(token!==animationToken)return;
        if(entityShatters.some(function(item){return item.active&&item.token===token;})){
          afterEntityShatter(token,revealContact);return;
        }
        // WebView may start the animation after its wall-clock timer. Reveal
        // impact and damage only when that animation reaches actual contact.
        if(attackMotion){
          if(attackMotion.playState==='idle')return;
          if(attackMotion.currentTime==null||attackMotion.currentTime<contact){
            requestAnimationFrame(revealContact);return;
          }
        }
        appendDamageLine(e);
        if(onContact)onContact();
        window.backroomPlayCombatFeedback(e,anchors[e.target]);
      }
      setTimeout(revealContact,delay+contact);
      return delay+(death?contact+death+90:e.outcome==='tick'?250:reduced?90:HIT_STEP_MS);
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
          afterEntityShatter(token,function(){showTurnCue(c);});
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
            group.forEach(function(hit){appendDamageLine(hit);window.backroomPlayCombatFeedback(hit,anchors[hit.target]);});
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

  var lastContinuationId='';
  function continueAfterVictory(incoming) {
    if(!incoming||incoming.postCombatContinuation!==true)return;
    if(!window.Android||typeof Android.continueAfterCombat!=='function')return;
    var id=incoming.combatFeedback&&incoming.combatFeedback.id;
    if(!id||id===lastContinuationId)return;
    lastContinuationId=id;
    busy=true;
    if(typeof syncPrimaryActions==='function')syncPrimaryActions();
    try{
      if(typeof window.backroomPostCombatPending==='function')window.backroomPostCombatPending();
      Android.continueAfterCombat(JSON.stringify(state));
    }
    catch(error){lastContinuationId='';busy=false;if(typeof syncPrimaryActions==='function')syncPrimaryActions();
      if(typeof window.backroomError==='function')window.backroomError('Chưa tiếp tục được sau giao tranh. Nhấn Retry.');
      else if(typeof statusEl!=='undefined')statusEl.textContent='Chưa tiếp tục được sau giao tranh. Nhấn Retry.';}
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
    var incoming=null,incomingCombat=null;
    try{incoming=JSON.parse(json);incomingCombat=incoming&&incoming.combat;}catch(_){}
    if(before&&before.active===true&&rawPacket&&Array.isArray(rawPacket.turns)&&
       rawPacket.turns.some(function(c){return c&&c.kind==='wave';})){
      window.backroomCombatEntityVisualKey=String(before.entityKey||'');
    }
    var completing=before&&before.active===true&&
      (!incomingCombat||incomingCombat.active!==true)&&
      rawPacket&&String(rawPacket.encounterId||'')===String(before.encounterId||'')&&
      Array.isArray(rawPacket.events)&&
      (rawPacket.events.some(lethalEntityHit)||(Array.isArray(rawPacket.turns)&&
       rawPacket.turns.some(function(c){return c&&c.kind==='actor';})));
    if(completing){
      if(typeof rawPacket.id==='string'&&seen.indexOf(rawPacket.id)!==-1)return;
      if(typeof rawPacket.id==='string'){seen.push(rawPacket.id);if(seen.length>64)seen.shift();}
      var completionToken=++animationToken,context=this;
      stopCombatMotion();
      beginCombatLog(before);
      setFeedbackBusy(true);
      requestAnimationFrame(function(){
        if(completionToken!==animationToken)return;
        var slots={entity:[0],actor:[0]};
        var anchors={entity:targetAnchor('entity'),actor:targetAnchor('actor')};
        var elapsed=playTypedTurnTimeline(rawPacket,completionToken,anchors,slots);
        if(elapsed===null){
          rawPacket.events.forEach(function(e,index){
            setTimeout(function(){afterEntityShatter(completionToken,function(){
              appendDamageLine(e);window.backroomPlayCombatFeedback(e);
            });},index*150);
          });
          elapsed=rawPacket.events.length*150+Math.max(FLOAT_MS,reducedMotion()?180:ENTITY_SHATTER_MS)+50;
        }
        setTimeout(function(){
          afterEntityShatter(completionToken,function(){
            previousTurn.call(context,json);
            resetCombatPanel();
            setFeedbackBusy(false);
            continueAfterVictory(incoming);
          });
        },elapsed===null?0:elapsed);
      });
      return;
    }
    // Lock the next dice phase before render() can re-enter the combat UI.
    // The Core's typed timeline releases the lock only after Entity cues finish.
    if(before&&before.active===true&&rawPacket&&typeof rawPacket.id==='string'&&
       Array.isArray(rawPacket.events)&&seen.indexOf(rawPacket.id)===-1)
      setFeedbackBusy(true);
    var result=previousTurn.call(this,json);
    var next=typeof state!=='undefined'&&state;
    var after=next&&next.combat,packet=rawPacket||(next&&next.combatFeedback);

    if(!after||after.active!==true){
      // The Dice and Combat Log close together on combat completion.
      // Never leave the last packet queued behind a hidden panel.
      ++animationToken;stopCombatMotion();resetCombatPanel();setFeedbackBusy(false);
      continueAfterVictory(incoming);
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
          afterEntityShatter(token,function(){
            focusCombatActor('cao_minh');
            showNow('Cao Minh');
            window.backroomCombatEntityVisualKey='';
            setFeedbackBusy(false);
          });
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
        afterEntityShatter(token,function(){
          focusCombatActor('cao_minh');
          showNow('Cao Minh');
          setFeedbackBusy(false);
        });
      },lastDelay+1800);
    });
    return result;
  };

  var initial=activeCombat();
  if(initial)beginCombatLog(initial);
  else resetCombatPanel();
})();
