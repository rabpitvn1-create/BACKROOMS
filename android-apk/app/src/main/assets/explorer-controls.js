(function(global){
  'use strict';
  const bar=document.getElementById('playerActionBar'),form=document.getElementById('form');
  const journal=document.getElementById('sceneJournal'),room=document.getElementById('sceneJournalRoom'),objective=document.getElementById('sceneJournalObjective'),records=document.getElementById('sceneJournalRecords');
  if(!bar||!form||!journal||!room||!objective||!records)return;
  const panel=document.createElement('section');panel.id='explorerControls';panel.setAttribute('aria-label','Khám phá');
  const row=document.createElement('div');row.className='explorer-buttons';
  const buttons={};
  Object.entries({MOVE:'Di chuyển',SEARCH:'Tìm kiếm',TALK:'Giao tiếp'}).forEach(([kind,label])=>{
    const b=document.createElement('button');b.type='button';b.textContent=label;b.onclick=()=>open(kind);buttons[kind]=b;row.appendChild(b);
  });
  const retry=document.createElement('button');retry.type='button';retry.textContent='Thử lại';retry.onclick=()=>{
    const scene=state&&state.scene;if(!scene||busy)return;
    busy=true;refresh();
    try{if(scene.pendingActionId)Android.submitSceneAction(JSON.stringify(state),'RETRY');else Android.prepareScene(JSON.stringify(state));}
    catch(e){global.backroomError('Không gửi được yêu cầu tới Core.');}
  };
  panel.append(row,retry);bar.appendChild(panel);
  const dialog=document.createElement('dialog');dialog.id='explorerChoices';dialog.setAttribute('aria-label','Chọn tương tác');
  const close=document.createElement('button');close.type='button';close.textContent='Đóng';close.onclick=()=>dialog.close();
  const choices=document.createElement('div');dialog.append(close,choices);document.body.appendChild(dialog);
  let preparing=false,failed=false;
  function locked(){return busy||coreUnavailable||!!state.pendingEncounter||!!(state.combat&&state.combat.active);}
  function open(kind){
    if(locked())return;choices.replaceChildren();
    const scene=state.scene;if(!scene)return;
    (scene.availableActions||[]).filter(a=>a.kind===kind).forEach(a=>{
      const b=document.createElement('button');b.type='button';b.textContent=a.label;
      b.onclick=()=>{
        if(locked())return;
        // Capture the exact versioned Core choice; a stale popup is rejected by Core.
        const command={actionId:a.actionId,sceneVersion:a.sceneVersion,roomId:a.roomId,kind:a.kind,targetId:a.targetId};
        dialog.close();busy=true;appendActionPending(a.label);refresh();
        try{Android.submitSceneAction(JSON.stringify(state),JSON.stringify(command));}
        catch(e){global.backroomError('Không gửi được hành động tới Core.');}
      };choices.appendChild(b);
    });
    dialog.showModal();
  }
  function refresh(){
    const scene=state&&state.scene,combat=!!(state&&state.combat&&state.combat.active);
    panel.hidden=combat||!scene;form.hidden=!combat;journal.hidden=!scene;
    if(!scene)return;
    room.textContent=scene.room?`${scene.room.name} · ${scene.room.description}`:'';
    objective.textContent=`${scene.nodeTitle||'Khu vực hiện tại'} · Mini-map ${scene.miniIndex}/10 · ${scene.objective||'Đang chuẩn bị kịch bản'}`+(scene.awaitingEnding?' · Đã hoàn thành Level 6. Chờ ending.':'');
    records.replaceChildren();[...(scene.previousObservations||[]),...(scene.records||[])].forEach(r=>{const p=document.createElement('p');p.textContent=r.text;records.appendChild(p);});
    Object.entries(buttons).forEach(([kind,b])=>{b.disabled=locked()||!(scene.availableActions||[]).some(a=>a.kind===kind);});
    retry.hidden=!(scene.generationNeeded||scene.pendingActionId);retry.disabled=busy||coreUnavailable;
    if(scene.generationNeeded&&!scene.pendingActionId&&!locked()&&!preparing&&!failed){
      preparing=true;busy=true;retry.disabled=true;
      try{Android.prepareScene(JSON.stringify(state));}catch(e){global.backroomError('Không thể chuẩn bị mini-map.');}
    }
  }
  // The combat system still owns its direct action form; Explorer has no text input.
  form.addEventListener('submit',e=>{if(!(state.combat&&state.combat.active)){e.preventDefault();e.stopImmediatePropagation();}},true);
  const turn=global.backroomTurn,error=global.backroomError;
  global.backroomTurn=function(json){preparing=false;failed=false;if(turn)turn(json);refresh();};
  global.backroomError=function(message){preparing=false;failed=true;if(error)error(message);refresh();};
  global.backroomSyncExplorer=refresh;
  refresh();
})(window);
