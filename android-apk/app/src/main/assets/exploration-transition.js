/* Snapshot-only cinematic exploration. World state and frame selection belong to Game Core. */
(function(root){
  'use strict';
  var active=null,epoch=0;
  var timers=[];
  var assetPrefix='file:///android_asset/level_snapshots/';

  function clearTimers(){
    timers.forEach(function(id){clearTimeout(id);});
    timers=[];
  }
  function cleanup(){
    if(!active)return;
    var run=active;
    active=null;
    if(run.previous&&run.previous.remove)run.previous.remove();
    if(run.box&&run.box.dataset)delete run.box.dataset.explorePhase;
    if(run.box&&run.box.style)run.box.style.removeProperty('--explore-advance-x');
  }
  function cancel(){
    ++epoch;
    clearTimers();
    cleanup();
  }
  function sceneOf(state){
    var assets=root.BackroomSnapshotAssets;
    return assets&&typeof assets.resolve==='function'?assets.resolve(state):null;
  }
  function stepOf(state){
    var exploration=state&&state.flags&&state.flags.exploration;
    var step=Number(exploration&&exploration.snapshotStep);
    return Number.isSafeInteger(step)&&step>=0?step:0;
  }
  function combatActive(state){return !!(state&&state.combat&&state.combat.active===true);}
  function entityEncounter(state){
    var flags=state&&state.flags||{};
    var roll=flags.lastRolls&&flags.lastRolls.entityEncounter;
    var event=state&&state._snapshotEvent;
    return !!((roll&&roll.success===true&&roll.eligible!==false)||
      (flags.entityEncounterKey&&String(flags.entityEncounterKey).trim())||
      (event&&String(event.kind||'').toUpperCase()==='ENTITY_CONFIRMED'));
  }
  function capture(before){
    cancel();
    var box=document.getElementById('snapshot');
    var bg=box&&box.querySelector('.snapshot-bg');
    var scene=sceneOf(before);
    return {
      scene:scene,
      step:stepOf(before),
      turn:Number(before&&before.turn),
      combat:combatActive(before),
      src:bg?String(bg.currentSrc||bg.src||''):'',
      loaded:!!(bg&&bg.complete&&bg.naturalWidth>0)
    };
  }
  function currentState(){return typeof state==='undefined'?null:state;}
  function valid(run){
    return active===run&&run.token===epoch&&run.previous.isConnected&&
      run.bg.isConnected&&run.box.querySelector('.snapshot-bg')===run.bg&&
      !combatActive(currentState())&&!root.__combatFeedbackBusy;
  }
  function after(delay,fn){
    var id=setTimeout(fn,delay);
    timers.push(id);
  }
  function preload(url){
    return new Promise(function(resolve,reject){
      var image=new Image(),settled=false;
      function done(ok){if(settled)return;settled=true;ok?resolve():reject();}
      after(2500,function(){done(false);});
      image.onload=function(){
        if(typeof image.decode==='function')image.decode().then(function(){done(true);},function(){done(image.complete&&image.naturalWidth>0);});
        else done(true);
      };
      image.onerror=function(){done(false);};
      image.src=url;
    });
  }
  function play(prior,next){
    var scene=sceneOf(next),box=document.getElementById('snapshot');
    var bg=box&&box.querySelector('.snapshot-bg');
    if(!prior||!prior.scene||!scene||!box||!bg||!prior.loaded||
       !Number.isFinite(prior.turn)||Number(next&&next.turn)<=prior.turn||
       prior.combat||combatActive(next)||entityEncounter(next)||root.__combatFeedbackBusy||
       (root.matchMedia&&root.matchMedia('(prefers-reduced-motion: reduce)').matches)||
       !(prior.scene.nodeId!==scene.nodeId||prior.step!==stepOf(next))||
       prior.scene.src===scene.src||
       !prior.src.startsWith(assetPrefix)||!scene.src.startsWith(assetPrefix)||
       String(bg.src)!==scene.src)return false;

    cancel();
    var previous=document.createElement('img');
    previous.className='snapshot-explore-previous';
    previous.alt='';previous.setAttribute('aria-hidden','true');previous.src=prior.src;
    bg.insertAdjacentElement('afterend',previous);
    var actor=box.querySelector('.snapshot-character');
    var bounds=box.getBoundingClientRect&&box.getBoundingClientRect();
    var sprite=actor&&actor.getBoundingClientRect&&actor.getBoundingClientRect();
    var width=box.clientWidth||400;
    var center=bounds&&sprite?sprite.left-bounds.left+sprite.width/2:width*.77;
    var offset=Math.max(24,Math.min(140,center-width*.52));
    box.style.setProperty('--explore-advance-x','-'+Math.round(offset)+'px');
    box.dataset.explorePhase='prepare';
    var run={box:box,bg:bg,previous:previous,token:epoch};
    active=run;
    preload(scene.src).then(function(){
      if(!valid(run))return;
      // Layout flush ensures prepare -> advance animates even on a cached WebView image.
      void box.offsetWidth;
      box.dataset.explorePhase='advance';
      after(560,function(){
        if(!valid(run)){if(active===run)cancel();return;}
        box.dataset.explorePhase='fade';
        after(470,function(){
          if(!valid(run)){if(active===run)cancel();return;}
          box.dataset.explorePhase='settle';
          after(270,function(){if(active===run)cancel();});
        });
      });
    },function(){if(active===run)cancel();});
    return true;
  }
  root.backroomExplorationTransition=Object.freeze({capture:capture,play:play,cancel:cancel});
})(typeof window!=='undefined'?window:globalThis);
