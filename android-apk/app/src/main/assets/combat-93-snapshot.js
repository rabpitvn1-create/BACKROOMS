/* COMBAT_93_SNAPSHOT_V1: presentation only; Core owns all combat mutations. */
(function(){
  'use strict';
  var seen=new Set(), generation=0, visualActor=null, combat=null, currentPhase=null;
  var duration=850;
  function phases(c){
    var events=Array.isArray(c.feedbackEvents)?c.feedbackEvents:[];
    var responseStart=events.findIndex(function(e){return e.phase==='entity';});
    if(responseStart<0)responseStart=events.length;
    var result=[{phase:'actor',events:events.slice(0,responseStart)}];
    (c.resolvedEntityTurns||[]).forEach(function(turn){
      result.push({phase:'entity',entityIndex:turn.entityIndex,events:events.filter(function(e){return e.phase==='entity'&&e.entityIndex===turn.entityIndex;})});
    });
    var lastResponse=-1;
    events.forEach(function(e,i){if(e.phase==='entity')lastResponse=i;});
    if(lastResponse>=0&&lastResponse+1<events.length)result.push({phase:'after',events:events.slice(lastResponse+1)});
    return result;
  }
  function token(c){return String(c.encounterId)+':'+c.resolvedRound+':'+c.resolvedActorIndex;}
  function eligible(c){return !!c&&Number.isInteger(c.resolvedActorIndex)&&Number.isInteger(c.resolvedRound)&&Array.isArray(c.feedbackEvents)&&!seen.has(token(c));}
  function el(tag,cls,text){var n=document.createElement(tag);n.className=cls;if(text!==undefined)n.textContent=String(text);return n;}
  function statuses(value){
    var labels={bleed:'Chảy máu',poison:'Độc',stun:'Choáng',armorBreak:'Xuyên giáp',accuracyPenalty:'Giảm chính xác'};
    return Object.keys(labels).filter(function(k){return Number(value[k+'Turns']||value[k])>0;}).map(function(k){return labels[k];}).join(' · ');
  }
  function unit(value,side,index){
    var n=el('div','combat93-unit '+side);n.dataset.index=index;
    var picture=el('img','combat93-sprite');picture.alt='';
    picture.src=side==='entity'?'entity/'+value.key+'.webp':value.id==='kai'||value.id==='cao_minh'?'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png':value.id==='lucia'?'lucia_overlay.png':value.id==='syvial'?'syvial_overlay.png':'';
    var fallback=el('div','combat93-silhouette',value.name||'Nhân vật');fallback.hidden=true;
    picture.onerror=function(){picture.hidden=true;fallback.hidden=false;};
    if(!picture.getAttribute('src')){picture.hidden=true;fallback.hidden=false;}
    n.appendChild(picture);n.appendChild(fallback);
    n.appendChild(el('div','combat93-name',value.name));
    n.appendChild(el('div','combat93-hp',Math.max(0,Number(value.hp)||0)+' / '+value.maxHp+' HP'));
    n.appendChild(el('div','combat93-status',statuses(value)));
    if(side==='entity'&&combat.active===true){
      n.classList.toggle('selected',index===combat.targetEntityIndex);
      var target=el('button','combat93-target','Chọn mục tiêu');target.type='button';
      target.disabled=window.__combatFeedbackBusy||combat.active!==true||value.hp<=0||!!(combat.diceState&&combat.diceState.finalized);
      target.onclick=function(){if(!target.disabled&&window.Android&&typeof Android.combatTarget==='function')Android.combatTarget(JSON.stringify(state),index);};
      n.appendChild(target);
    }
    return n;
  }
  function render(){
    var box=document.getElementById('snapshot');if(!box)return;
    combat=typeof state!=='undefined'&&state?state.combat:null;
    var old=box.querySelector('.combat93-scene');
    if(!combat||(!combat.active&&!window.__combatFeedbackBusy)){if(old)old.remove();box.classList.remove('combat93-active');return;}
    var actor=visualActor===null?combat.actorIndex:visualActor;
    var signature=String(combat.revision)+':'+actor+':'+!!window.__combatFeedbackBusy;
    if(old&&old.dataset.signature===signature)return;
    if(old)old.remove();box.classList.add('combat93-active');
    var scene=el('div','combat93-scene');scene.dataset.signature=signature;
    scene.appendChild(el('div','combat93-round','Vòng '+(visualActor===null?combat.round:combat.resolvedRound)));
    var participant=(combat.participants||[])[actor];if(participant)scene.appendChild(unit(participant,'actor',actor));
    var foes=el('div','combat93-entities');
    (combat.entities||[]).forEach(function(e,i){if(e.hp>0||window.__combatFeedbackBusy)foes.appendChild(unit(e,'entity',i));});
    scene.appendChild(foes);box.appendChild(scene);
    if(window.__combatFeedbackBusy&&currentPhase)showPhase(currentPhase);
  }
  function showPhase(phase){
    var scene=document.querySelector('.combat93-scene');if(!scene)return;
    scene.querySelectorAll('.responding').forEach(function(n){n.classList.remove('responding');});
    scene.querySelectorAll('.combat93-float-stack').forEach(function(n){n.remove();});
    if(phase.phase==='entity'){
      var responder=scene.querySelector('.entity[data-index="'+phase.entityIndex+'"]');if(responder)responder.classList.add('responding');
    }
    phase.events.forEach(function(event){
      var anchor=event.target==='actor'?scene.querySelector('.actor'):scene.querySelector('.entity[data-index="'+event.entityIndex+'"]');
      if(!anchor)return;
      var text=event.kind==='miss'?'MISS':String(event.text||event.status||'');if(!text)return;
      var stack=anchor.querySelector('.combat93-float-stack');
      if(!stack){stack=el('div','combat93-float-stack');anchor.appendChild(stack);}
      var parts=[];if(event.critical)parts.push('CRIT');parts.push(text);
      if(event.status&&text.indexOf(String(event.status))<0)parts.push(String(event.status));
      var node=el('span','combat93-float '+(event.critical?'critical ':'')+event.kind,parts.join(' · '));
      stack.appendChild(node);if(event.flash)anchor.classList.add('hit');
      setTimeout(function(){node.remove();if(!stack.querySelector('.combat93-float'))stack.remove();anchor.classList.remove('hit');},duration);
    });
  }
  function play(c){
    var run=++generation, queue=phases(c), index=0;
    function next(){
      if(run!==generation)return;
      if(index<queue.length){render();currentPhase=queue[index++];showPhase(currentPhase);setTimeout(next,duration);return;}
      currentPhase=null;visualActor=null;window.__combatFeedbackBusy=false;render();
      if(typeof window.render==='function')window.render();
      if(typeof window.ensureDirectCombatDice==='function')window.ensureDirectCombatDice();
    }
    next();
  }
  var previousTurn=window.backroomTurn;
  window.backroomTurn=function(json){
    var incoming;try{incoming=JSON.parse(json);}catch(_){return previousTurn.apply(this,arguments);}
    var c=incoming.combat, fresh=eligible(c);
    if(fresh){seen.add(token(c));visualActor=c.resolvedActorIndex;window.__combatFeedbackBusy=true;}
    var result=previousTurn.apply(this,arguments);render();if(fresh)requestAnimationFrame(function(){play(c);});return result;
  };
  var previousRender=window.render;
  if(typeof previousRender==='function')window.render=function(){var result=previousRender.apply(this,arguments);render();return result;};
  var box=document.getElementById('snapshot');
  if(box)new MutationObserver(function(){render();}).observe(box,{childList:true});
  window.Combat93Snapshot={phases:phases,token:token,render:render};
  render();
})();
