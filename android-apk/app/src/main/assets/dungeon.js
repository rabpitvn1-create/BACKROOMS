(function () {
  'use strict';
  var openButton=document.getElementById('dungeonOpen');
  var modal=document.getElementById('dungeonModal');
  if(!openButton||!modal)return;
  var slots=document.getElementById('dungeonSlots'),status=document.getElementById('dungeonStatus');
  var pending=null,model=null,sequence=0,timer=null,receivedAt=0,returnFocus=null;
  var labels=['15 phút','30 phút','1 giờ','2 giờ','3 giờ'];
  var messages={dungeon_game_busy:'Hãy hoàn tất lượt hoặc trận chiến trước khi xếp Follower.',
    dungeon_follower_unavailable:'Follower chưa khả dụng hoặc đã ở trong Dungeon.',
    dungeon_slot_occupied:'Slot đã có Follower.',dungeon_slot_empty:'Follower đã được đưa về.',
    dungeon_stale_run:'Slot đã thay đổi. Hãy tải lại thông tin.',
    dungeon_request_failed:'Chưa cập nhật được Dungeon. Anh hãy thử lại.'};
  function node(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e;}
  function gameBusy(){return typeof busy!=='undefined'&&busy||window.__combatFeedbackBusy||
    typeof state!=='undefined'&&state&&(state.pendingEncounter||state.combat&&state.combat.active||state.scene&&state.scene.pendingActionId);}
  function lock(value){if(typeof busy!=='undefined')busy=value;if(typeof syncPrimaryActions==='function')syncPrimaryActions();}
  function request(operation,extra){
    if(pending)return;
    if(operation!=='view'&&gameBusy()){status.textContent=messages.dungeon_game_busy;return;}
    if(!window.Android||typeof Android.dungeonRequest!=='function'){
      status.textContent='Dungeon cần Game Core trên ứng dụng.';return;
    }
    var packet=Object.assign({operation:operation,requestId:String(++sequence)},extra||{});
    pending=packet;
    if(operation!=='view')lock(true);
    render();status.textContent=operation==='view'?'Đang cập nhật…':'Đang lưu…';
    timer=setTimeout(function(){
      if(!pending||pending.requestId!==packet.requestId)return;
      pending=null;if(operation!=='view')lock(false);render();
      status.textContent='Chưa nhận được phản hồi. Mở lại Dungeon để kiểm tra kết quả đã lưu.';
    },20000);
    try{Android.dungeonRequest(JSON.stringify(typeof state==='undefined'?{}:state),JSON.stringify(packet));}
    catch(error){clearTimeout(timer);pending=null;if(operation!=='view')lock(false);render();status.textContent=messages.dungeon_request_failed;}
  }
  function imageSource(path){return typeof path==='string'&&/^(?:avatars\/[A-Za-z0-9_.-]+\.(?:jpg|png|webp)|LUCIA_LUC_OVERLAY\.png)$/.test(path)?path:'';}
  function snapshot(card,slot){
    var scene=node('div','dungeon-scene');
    var mapping=window.BackroomSnapshotAssets;
    var file=mapping&&mapping.environmentFile(slot.environmentId);
    if(file&&/^[A-Za-z0-9_.-]+\.webp$/.test(file)){
      var bg=node('img','dungeon-background');bg.src='level_snapshots/'+file;bg.alt=slot.environmentName;scene.appendChild(bg);
    }
    var path=imageSource(slot.follower.avatar);
    if(path){var portrait=node('img','dungeon-follower');portrait.src=path;portrait.alt=slot.follower.name;scene.appendChild(portrait);}
    scene.appendChild(node('span','dungeon-scene-label',slot.environmentName));
    card.appendChild(scene);
  }
  function render(){
    slots.textContent='';
    var data=model&&model.slots||labels.map(function(label,index){return {slot:index,status:'empty'};});
    data.forEach(function(slot){
      var card=node('article','dungeon-slot');card.dataset.slot=String(slot.slot);
      card.appendChild(node('h3','dungeon-slot-title','SLOT '+(slot.slot+1)+' · '+labels[slot.slot]));
      if(slot.status==='empty'){
        var placeholder=node('div','dungeon-scene dungeon-scene--empty','Chưa có Follower');card.appendChild(placeholder);
        var select=node('select','dungeon-select');select.setAttribute('aria-label','Follower cho slot '+(slot.slot+1));
        select.appendChild(new Option('Chọn Follower',''));
        (model&&model.followers||[]).forEach(function(f){select.appendChild(new Option(f.name+' · LV. '+f.level+(f.inParty?' · Trong Party':''),f.id));});
        select.disabled=!!pending||!model||model.followers.length===0||!!gameBusy();
        var start=node('button','dungeon-start','Bắt đầu train');start.type='button';start.disabled=true;
        select.addEventListener('change',function(){start.disabled=!select.value||!!pending||!!gameBusy();});
        start.addEventListener('click',function(){if(select.value)request('start',{slot:slot.slot,followerId:select.value});});
        card.appendChild(select);card.appendChild(start);
      }else{
        snapshot(card,slot);
        card.appendChild(node('strong','dungeon-name',slot.follower.name+' · LV. '+slot.follower.level));
        card.appendChild(node('p','dungeon-exp','Đã nhận '+slot.earnedExp+' EXP · '+slot.creditedMinutes+'/'+slot.durationMinutes+' Entity'));
        var bar=node('progress','dungeon-progress');bar.max=slot.durationMinutes*60000;bar.value=0;
        bar.setAttribute('aria-label','Thời gian train '+slot.follower.name);card.appendChild(bar);
        var clock=node('p','dungeon-clock');clock.dataset.slot=String(slot.slot);card.appendChild(clock);
        var release=node('button','dungeon-release',slot.status==='complete'?'Hoàn tất · Đưa về':'Đưa về sớm');release.type='button';
        release.disabled=!!pending||!!gameBusy();release.addEventListener('click',function(){request('release',{slot:slot.slot,runId:slot.runId});});
        card.appendChild(release);
      }
      slots.appendChild(card);
    });
    updateClocks();
  }
  function updateClocks(){
    if(!model||modal.hidden)return;
    // Monotonic page time only animates the bar. All EXP/counts come from Core.
    var now=model.serverNowMs+Math.max(0,performance.now()-receivedAt);
    model.slots.forEach(function(slot){
      if(slot.status==='empty')return;
      var card=slots.querySelector('[data-slot="'+slot.slot+'"]');if(!card)return;
      var remaining=Math.max(0,Math.ceil((slot.endsAtMs-now)/1000));
      card.querySelector('progress').value=Math.min(slot.durationMinutes*60000,Math.max(0,now-slot.startedAtMs));
      var text=slot.status==='complete'?'Đã hoàn tất':remaining===0?'Đang xác nhận phút cuối…':
        'Còn '+Math.floor(remaining/3600).toString().padStart(2,'0')+':'+Math.floor(remaining%3600/60).toString().padStart(2,'0')+':'+(remaining%60).toString().padStart(2,'0');
      card.querySelector('.dungeon-clock').textContent=text;
    });
  }
  window.backroomDungeon=function(json){
    var result;
    try{result=JSON.parse(json);}catch(error){status.textContent=messages.dungeon_request_failed;return;}
    if(!pending||result.requestId!==pending.requestId)return;
    var operation=pending.operation;clearTimeout(timer);pending=null;
    if(operation!=='view')lock(false);
    if(result.ok===true&&result.dungeon){
      model=result.dungeon;receivedAt=performance.now();
      if(operation!=='view'&&result.state){
        // Refresh Party/stat UI without feeding a fake player turn into combat animation.
        state=semanticPrepareNextState(state,JSON.stringify(result.state));
        if(typeof window.render==='function')window.render();
        if(typeof window.backroomRenderEncounteredCharacters==='function')window.backroomRenderEncounteredCharacters();
      }
      status.textContent=operation==='start'?'Follower đã bắt đầu train.':operation==='release'?'Follower đã trở về; anh có thể xếp lại Party.':'';
    }else{status.textContent=messages[result.error]||'Không thể cập nhật Dungeon. Anh hãy thử lại.';}
    render();
  };
  function close(){modal.hidden=true;document.body.classList.remove('dungeon-open');if(returnFocus)returnFocus.focus();}
  openButton.addEventListener('click',function(){
    returnFocus=openButton;document.getElementById('gameMenuClose').click();modal.hidden=false;
    document.body.classList.add('dungeon-open');render();document.getElementById('dungeonClose').focus();request('view');
  });
  document.getElementById('dungeonClose').addEventListener('click',close);
  document.getElementById('dungeonRefresh').addEventListener('click',function(){request('view');});
  modal.addEventListener('keydown',function(event){
    if(event.key==='Escape'){event.preventDefault();close();return;}
    if(event.key==='Tab'){
      var items=Array.prototype.filter.call(modal.querySelectorAll('button,select'),function(e){return !e.disabled;});
      var first=items[0],last=items[items.length-1];
      if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
      else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
    }
  });
  document.addEventListener('visibilitychange',function(){if(!document.hidden&&!modal.hidden)request('view');});
  setInterval(function(){if(!modal.hidden){updateClocks();if(!pending&&performance.now()-receivedAt>=15000)request('view');}},1000);
})();
