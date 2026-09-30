(function(){
  'use strict';
  if(window.__gameMenuUiInstalled)return;
  window.__gameMenuUiInstalled=true;

  var modal=document.getElementById('gameMenuModal');
  var openButton=document.getElementById('gameMenuOpen');
  var closeButton=document.getElementById('gameMenuClose');
  var backdrop=document.getElementById('gameMenuBackdrop');
  if(!modal||!openButton||!closeButton)return;

  function isOpen(){
    return !modal.hidden;
  }

  function openMenu(){
    if(isOpen())return;
    modal.hidden=false;
    modal.setAttribute('aria-hidden','false');
    openButton.setAttribute('aria-expanded','true');
    document.body.classList.add('game-menu-open');
    requestAnimationFrame(function(){
      try{closeButton.focus({preventScroll:true});}catch(_){try{closeButton.focus();}catch(__){}}
    });
  }

  function closeMenu(restoreFocus){
    if(!isOpen())return;
    modal.hidden=true;
    modal.setAttribute('aria-hidden','true');
    openButton.setAttribute('aria-expanded','false');
    document.body.classList.remove('game-menu-open');
    if(restoreFocus!==false){
      requestAnimationFrame(function(){
        try{openButton.focus({preventScroll:true});}catch(_){try{openButton.focus();}catch(__){}}
      });
    }
  }

  openButton.addEventListener('click',openMenu);
  closeButton.addEventListener('click',function(){closeMenu(true);});
  if(backdrop)backdrop.addEventListener('click',function(){closeMenu(true);});
  document.addEventListener('keydown',function(event){
    if(event.key==='Escape'&&isOpen()){
      event.preventDefault();
      closeMenu(true);
    }
  });

  window.backroomOpenGameMenu=openMenu;
  window.backroomCloseGameMenu=closeMenu;
})();
