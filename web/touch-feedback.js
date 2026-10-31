(function(){'use strict';if(window.backroomActionToast)return;
var toast=document.createElement('div'),timer,pressed;toast.id='backroomActionToast';toast.hidden=true;toast.setAttribute('role','status');toast.setAttribute('aria-live','polite');document.body.appendChild(toast);
window.backroomActionToast=function(message){if(!message)return;clearTimeout(timer);toast.textContent=String(message);toast.hidden=false;timer=setTimeout(function(){toast.hidden=true},1800)};
document.addEventListener('pointerdown',function(e){var el=e.target.closest&&e.target.closest('button');if(!el||el.disabled)return;if(pressed)pressed.classList.remove('backroom-pressed');pressed=el;el.classList.add('backroom-pressed')},{capture:true,passive:true});
function release(){var el=pressed;pressed=null;if(el)setTimeout(function(){el.classList.remove('backroom-pressed')},160)}
document.addEventListener('pointerup',release,{capture:true,passive:true});document.addEventListener('pointercancel',release,{capture:true,passive:true});
document.addEventListener('click',function(e){var el=e.target.closest&&e.target.closest('button');if(!el||el.disabled)return;var messages={mobileMenuButton:'Đã mở menu',mobileMenuClose:'Đã đóng menu',searchActionButton:'Đang tìm kiếm…',exploreActionButton:'Đang khám phá…',submit:'Nhập hành động'};if(messages[el.id])window.backroomActionToast(messages[el.id])},true);
})();