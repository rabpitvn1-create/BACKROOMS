/* Presentation only: surface existing Save/Load feedback in the open sheets. */
(function(){
  'use strict';
  var source=document.getElementById('status');
  if(!source||window.__industrialCrtStatus)return;
  window.__industrialCrtStatus=true;
  var targets=[];
  ['.game-menu-sheet','.player-action-sheet'].forEach(function(selector){
    var sheet=document.querySelector(selector);
    if(!sheet)return;
    var status=document.createElement('p');
    status.className='industrial-crt-status';
    sheet.insertBefore(status,sheet.children[1]||null);
    targets.push(status);
  });
  function sync(){targets.forEach(function(target){target.textContent=source.textContent;});}
  sync();
  new MutationObserver(sync).observe(source,{childList:true,characterData:true,subtree:true});
})();
