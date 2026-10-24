/* Combat scene visualization reads Entity identity from authoritative state.
 * Missing Cloudinary records never trigger a local-APK asset substitute. */
(function(){
  'use strict';
  var serial='', visualKey='';
  function entityKey(){
    if(typeof state==='undefined'||!state)return '';
    var c=state.combat;
    if(!c||c.active!==true)return '';
    var session=String(c.encounterId||'');
    if(serial!==session){serial=session;visualKey='';}
    return visualKey||String(c.entityKey||'');
  }
  function update(){
    var box=document.getElementById('snapshot');
    if(!box||!window.backroomCloudinary)return;
    var key=entityKey(),url=key&&window.backroomCloudinary.entity(key);
    var old=box.querySelector('.snapshot-entity');
    if(!url){if(old)old.remove();return;}
    if(old&&old.dataset.entityKey===key&&old.getAttribute('src')===url)return;
    if(old)old.remove();
    var img=document.createElement('img');
    img.className='snapshot-entity snapshot-entity--wave-enter';
    img.dataset.entityKey=key;
    img.alt=key.replace(/_/g,' ');
    img.src=url;
    img.decoding='async';
    img.style.cssText='position:absolute;left:2%;bottom:0;height:92%;max-width:55%;width:auto;object-fit:contain;object-position:left bottom;z-index:3;pointer-events:none';
    box.appendChild(img);
  }
  window.backroomCombatEntityVisualKey='';
  window.backroomSwitchCombatEntityWave=function(key,index){
    if(typeof state==='undefined'||!state||!state.combat||state.combat.active!==true)return;
    if(typeof key!=='string'||!window.backroomCloudinary||!window.backroomCloudinary.entity(key))return;
    visualKey=key;
    window.backroomCombatEntityVisualKey=key;
    update();
  };
  window.backroomRestoreEntityOverlay=update;
  var render=window.render;
  if(typeof render==='function')window.render=function(){
    var result=render.apply(this,arguments);update();return result;
  };
  var turn=window.backroomTurn;
  if(typeof turn==='function')window.backroomTurn=function(payload){
    var result=turn.apply(this,arguments);update();return result;
  };
  var stage=document.getElementById('snapshot');
  if(stage && typeof MutationObserver==='function'){
    var rendering=false;
    new MutationObserver(function(){
      if(rendering)return;
      rendering=true;
      try{update();}finally{rendering=false;}
    }).observe(stage,{childList:true});
  }
  update();
})();
