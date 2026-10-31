/* Clear, non-blocking touch feedback for combat and general game controls. */
(function () {
  'use strict';
  if (window.backroomActionToast) return;
  var toast=document.createElement('div'),hideTimer=null;
  toast.id='backroomActionToast';toast.setAttribute('role','status');
  toast.setAttribute('aria-live','polite');toast.hidden=true;
  document.body.appendChild(toast);
  window.backroomActionToast=function(message){
    if(!message)return;
    if(hideTimer!==null)clearTimeout(hideTimer);
    toast.textContent=String(message);toast.hidden=false;
    toast.classList.remove('backroom-toast-show');
    void toast.offsetWidth;
    toast.classList.add('backroom-toast-show');
    hideTimer=setTimeout(function(){toast.hidden=true;toast.classList.remove('backroom-toast-show');},1800);
  };
  var activeButton=null,pressTimer=null;
  function release(){
    if(pressTimer!==null)clearTimeout(pressTimer);
    var target=activeButton;activeButton=null;
    pressTimer=setTimeout(function(){if(target)target.classList.remove('backroom-pressed');},160);
  }
  document.addEventListener('pointerdown',function(event){
    var button=event.target.closest&&event.target.closest('button,[role="button"]');
    if(!button||button.disabled||button.getAttribute('aria-disabled')==='true')return;
    if(activeButton&&activeButton!==button)activeButton.classList.remove('backroom-pressed');
    if(pressTimer!==null)clearTimeout(pressTimer);
    activeButton=button;button.classList.add('backroom-pressed');
  },{capture:true,passive:true});
  document.addEventListener('pointerup',release,{capture:true,passive:true});
  document.addEventListener('pointercancel',release,{capture:true,passive:true});
  document.addEventListener('click',function(event){
    var button=event.target.closest&&event.target.closest('button');
    if(!button||button.disabled)return;
    var labels={mobileMenuButton:'Đã mở menu',mobileMenuClose:'Đã đóng menu',
      searchActionButton:'Đang tìm kiếm…',exploreActionButton:'Đang khám phá…',
      submit:'Nhập hành động của Cao Minh'};
    if(labels[button.id])window.backroomActionToast(labels[button.id]);
  },true);
})();
