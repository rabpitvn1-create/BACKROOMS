(function(global){
  "use strict";
  const action=document.getElementById("action"),send=document.getElementById("submit");
  const form=document.getElementById("form"),bar=document.getElementById("playerActionBar");
  if(!action||!send||!form||!bar)return;
  const root=document.documentElement;
  function locked(){
    return !!(typeof busy!=="undefined"&&busy)||!!(typeof coreUnavailable!=="undefined"&&coreUnavailable)||
      !!(typeof state!=="undefined"&&state&&(state.pendingEncounter||(state.combat&&state.combat.active)))||
      !!global.__combatBusy||!!global.__combatDiceBusy||!!global.__combatFeedbackBusy;
  }
  function fit(){
    const viewport=global.visualViewport;
    const height=Math.max(1,Math.min(global.innerHeight,viewport?viewport.height:global.innerHeight));
    root.style.setProperty("--app-height",height+"px");
    document.body.classList.toggle("chat-keyboard-open",document.activeElement===action&&height<560);
    const maxHeight=Math.min(176,Math.max(64,Math.floor(height*.3)));
    action.style.maxHeight=maxHeight+"px";
    action.style.height="0px";
    const contentHeight=action.scrollHeight;
    action.style.height=Math.min(maxHeight,Math.max(42,contentHeight))+"px";
    action.style.overflowY=contentHeight>maxHeight?"auto":"hidden";
    root.style.setProperty("--composer-height",Math.ceil(bar.getBoundingClientRect().height)+12+"px");
  }
  function sync(){
    const blocked=locked();
    action.readOnly=blocked;send.disabled=blocked||!action.value.trim();
    form.dataset.locked=String(blocked);
    if(blocked&&typeof state!=="undefined"&&state.combat&&state.combat.active)action.blur();
    fit();
  }
  action.addEventListener("input",sync);
  action.addEventListener("focus",fit);
  action.addEventListener("blur",fit);
  // Keep Enter for multiline text and IME composition; Ctrl/Cmd+Enter sends on desktop.
  action.addEventListener("keydown",function(event){
    if(event.key==="Enter"&&(event.ctrlKey||event.metaKey)&&!event.isComposing){
      event.preventDefault();if(!send.disabled)form.requestSubmit(send);
    }
  });
  global.addEventListener("resize",fit);
  if(global.visualViewport){global.visualViewport.addEventListener("resize",fit);global.visualViewport.addEventListener("scroll",fit);}
  if(typeof ResizeObserver!=="undefined")new ResizeObserver(fit).observe(bar);
  global.backroomSyncComposer=sync;
  sync();
})(window);
