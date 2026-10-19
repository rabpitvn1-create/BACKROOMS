/* Cosmetic TV OSD only. Native renderSnapshot clears #snapshot on every render;
   observe that DOM boundary instead of wrapping/replaying any gameplay callback. */
(function(){
  'use strict';
  const box=document.getElementById('snapshot');
  if(!box||!document.body.classList.contains('industrial-ui'))return;
  function mount(){
    if(box.querySelector('.snapshot-tv-hud'))return;
    const hud=document.createElement('div');hud.className='snapshot-tv-hud';
    hud.setAttribute('aria-hidden','true');
    hud.innerHTML='<span class="tv-play">PLAY ▷</span><span class="tv-rec">REC <i></i></span>'+
      '<span class="tv-format"><i class="tv-tape"></i> SP</span><span class="tv-channel">CH 01</span>';
    box.appendChild(hud);
  }
  new MutationObserver(mount).observe(box,{childList:true});
  mount();
})();
