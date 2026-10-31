/* Visual feedback stays on the pressed control; no detached toast obscures the GM log. */
(function(){
  'use strict';
  const timers=new WeakMap();
  let held=null;
  function target(event){
    return event.target&&event.target.closest
      ?event.target.closest('button,[role="button"],.party-member:not(.empty),.equipment-card,.inventory-item-card')
      :null;
  }
  function available(el){
    return !!el&&!el.disabled&&el.getAttribute('aria-disabled')!=='true';
  }
  function pulse(el){
    if(!available(el))return;
    clearTimeout(timers.get(el));
    el.classList.add('backroom-activated');
    timers.set(el,setTimeout(()=>el.classList.remove('backroom-activated'),1200));
  }
  function release(){
    if(!held)return;
    const el=held;held=null;
    // Preserve a perceptible flash when the browser disables a button mid-click.
    pulse(el);
    setTimeout(()=>el.classList.remove('backroom-pressed'),180);
  }
  document.addEventListener('pointerdown',event=>{
    const el=target(event);
    if(!available(el))return;
    if(held&&held!==el)held.classList.remove('backroom-pressed');
    held=el;el.classList.add('backroom-pressed');
  },{capture:true,passive:true});
  document.addEventListener('pointerup',release,{capture:true,passive:true});
  document.addEventListener('pointercancel',release,{capture:true,passive:true});
  document.addEventListener('click',event=>pulse(target(event)),true);
  // The combat controller already renders hand/roll state; place extra text
  // inline in that same dice panel instead of an unrelated floating popup.
  window.backroomActionToast=function(message){
    const panel=document.getElementById('pokerDiceModal');
    const meta=document.getElementById('pokerDiceMeta');
    if(panel&&!panel.hidden&&meta&&message)meta.textContent=String(message);
  };
  window.backroomMenuFeedback=function(message){
    if(!document.body.classList.contains('mobile-menu-open')||!message)return;
    const actions=document.querySelector('#sidePanel .actions');
    const card=actions&&actions.closest('.card');
    if(!card)return;
    let label=document.getElementById('backroomMenuFeedback');
    if(!label){
      label=document.createElement('p');
      label.id='backroomMenuFeedback';
      label.setAttribute('role','status');
      label.setAttribute('aria-live','polite');
      card.appendChild(label);
    }
    label.textContent=String(message);
  };
})();
