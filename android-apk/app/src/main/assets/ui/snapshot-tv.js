/* Cosmetic TV OSD only. Native renderSnapshot clears #snapshot on every render;
   observe that DOM boundary instead of wrapping/replaying any gameplay callback. */
(function(){
  'use strict';
  const box=document.getElementById('snapshot');
  if(!box||!document.body.classList.contains('industrial-ui'))return;
  const started=performance.now();
  function clock(){
    const hud=box.querySelector('.snapshot-tv-hud');if(!hud)return;
    const seconds=Math.max(0,Math.floor((performance.now()-started)/1000));
    const pad=n=>String(n).padStart(2,'0');
    hud.querySelector('[data-tv-tape-time]').textContent=pad(Math.floor(seconds/60))+':'+pad(seconds%60);
    hud.querySelector('[data-tv-view-time]').textContent=pad(Math.floor(seconds/3600))+':'+pad(Math.floor(seconds/60)%60)+':'+pad(seconds%60);
  }
  function mount(){
    if(box.querySelector('.snapshot-tv-hud'))return;
    const hud=document.createElement('div');hud.className='snapshot-tv-hud';
    hud.setAttribute('aria-hidden','true');
    hud.innerHTML='<i class="tv-glass"></i><span class="tv-play">PLAY ▶</span><span class="tv-rec">REC <i></i></span>'+
      '<span class="tv-format"><span class="tv-rec-label">REC:</span><span data-tv-tape-time>00:00</span></span><span class="tv-channel" data-tv-view-time>00:00:00</span>';
    box.appendChild(hud);clock();
  }
  new MutationObserver(mount).observe(box,{childList:true});
  mount();
  // Cosmetic page-view time only. Never persisted or sent to Core/GM.
  setInterval(clock,1000);
})();
