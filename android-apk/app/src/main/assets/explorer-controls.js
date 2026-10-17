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
    if(!scene.pendingActionId)writerPending('Gemini');
    try{if(scene.pendingActionId)Android.submitSceneAction(JSON.stringify(state),'RETRY');else Android.prepareScene(JSON.stringify(state));}
    catch(e){global.backroomError('Không gửi được yêu cầu tới Core.');}
  };
  panel.append(row,retry);bar.appendChild(panel);
  const dialog=document.createElement('dialog');dialog.id='explorerChoices';dialog.setAttribute('aria-label','Chọn tương tác');
  const header=document.createElement('div');header.className='explorer-dialog-header';
  const heading=document.createElement('strong');heading.textContent='Chọn tương tác';
  const close=document.createElement('button');close.type='button';close.className='explorer-dialog-close';close.textContent='×';close.setAttribute('aria-label','Đóng lựa chọn');close.onclick=()=>dialog.close();
  header.append(heading,close);
  const choices=document.createElement('div');choices.className='explorer-choice-list';dialog.append(header,choices);document.body.appendChild(dialog);
  let preparing=false,failed=false;
  function writerPending(provider){
    const log=document.getElementById('log');if(!log)return;
    let pending=log.querySelector('[data-scene-preparing="1"]');
    if(!pending){
      pending=document.createElement('article');pending.className='message gm pending scene-generation-pending';
      pending.setAttribute('data-scene-preparing','1');pending.setAttribute('data-pending','1');
      const role=document.createElement('div');role.className='role';role.textContent='BIÊN KỊCH';
      const line=document.createElement('div');line.className='text';
      const indicator=document.createElement('div');indicator.className='gm-thinking';indicator.setAttribute('role','status');
      const mark=document.createElement('img');mark.src='provider-logos/gemini.svg';mark.alt='';mark.className='scene-provider-logo';
      const label=document.createElement('span');label.className='gm-thinking-name';
      indicator.append(mark,label);line.append(indicator);pending.append(role,line);log.appendChild(pending);
    }
    const name=provider==='Gehihi'?'Gehihi':'Gemini';
    const indicator=pending.querySelector('.gm-thinking');
    if(indicator)indicator.setAttribute('aria-label',name+' đang tạo mini-map');
    const label=pending.querySelector('.gm-thinking-name');if(label)label.textContent=name+' đang tạo mini-map…';
    const mark=pending.querySelector('.scene-provider-logo');if(mark)mark.hidden=name!=='Gemini';
    log.scrollTop=log.scrollHeight;
  }
  function clearWriterPending(){
    const log=document.getElementById('log');
    if(log)log.querySelectorAll('[data-scene-preparing="1"]').forEach(n=>n.remove());
  }
  global.backroomSceneWriterProvider=provider=>{if(preparing)writerPending(provider);};
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
    const found=Array.isArray(scene.records)?scene.records.length:0;
    const searchCount=(scene.availableActions||[]).filter(a=>a.kind==='SEARCH').length;
    const hint=scene.completed?' · Mục tiêu đã hoàn thành. Hãy tìm đúng lối ra.':
      (' · Đã ghi '+found+' manh mối.'+(searchCount?' Phòng này còn '+searchCount+' thao tác tìm kiếm.':' Hãy kiểm tra phòng khác nếu còn thiếu manh mối.'));
    objective.textContent=`${scene.nodeTitle||'Khu vực hiện tại'} · Phân đoạn ${scene.miniIndex}/10 · ${scene.objective||'Đang chuẩn bị kịch bản'}`+(!scene.generationNeeded?hint:'')+(scene.awaitingEnding?' · Đã hoàn thành khu 6.1. Chờ kết thúc.':'');
    records.replaceChildren();[...(scene.records||[])].forEach(r=>{const p=document.createElement('p');p.textContent=r.text;records.appendChild(p);});
    if(scene.generationNeeded){const p=document.createElement('p');p.textContent='Đang tạo nhiệm vụ mới; thử lại nếu nhà cung cấp tạm lỗi.';records.appendChild(p);}
    else if(scene.completed){const p=document.createElement('p');p.textContent='Đã giải xong nhiệm vụ. Hãy tìm đường tới phòng thoát để tiếp tục.';records.appendChild(p);}
    else if(!found){const p=document.createElement('p');p.textContent='Chưa có chứng cứ: hãy dùng Tìm kiếm trong phòng, kiểm tra các phòng kề và đối chiếu manh mối trước khi chọn đáp án.';records.appendChild(p);}
    Object.entries(buttons).forEach(([kind,b])=>{b.disabled=locked()||!(scene.availableActions||[]).some(a=>a.kind===kind);});
    retry.hidden=!(scene.generationNeeded||scene.pendingActionId);retry.disabled=busy||coreUnavailable;
    if(scene.generationNeeded&&!scene.pendingActionId&&!locked()&&!preparing&&!failed){
      preparing=true;busy=true;retry.disabled=true;
      writerPending('Gemini');
      try{Android.prepareScene(JSON.stringify(state));}catch(e){global.backroomError('Không thể chuẩn bị mini-map.');}
    }
  }
  // The combat system still owns its direct action form; Explorer has no text input.
  form.addEventListener('submit',e=>{if(!(state.combat&&state.combat.active)){e.preventDefault();e.stopImmediatePropagation();}},true);
  const turn=global.backroomTurn,error=global.backroomError;
  global.backroomTurn=function(json){preparing=false;failed=false;clearWriterPending();if(turn)turn(json);refresh();};
  global.backroomError=function(message){preparing=false;failed=true;clearWriterPending();if(error)error(message);refresh();};
  global.backroomSyncExplorer=refresh;
  refresh();
})(window);
