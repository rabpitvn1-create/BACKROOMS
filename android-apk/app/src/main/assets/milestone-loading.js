(function(global){
  'use strict';
  global.BackroomMilestoneLoading={install:function(hooks){
    const panel=document.getElementById('milestoneLoading');
    const title=document.getElementById('milestoneLoadingTitle');
    const message=document.getElementById('milestoneLoadingMessage');
    const progress=document.getElementById('milestoneLoadingProgress');
    const retry=document.getElementById('milestoneLoadingRetry');
    const choices=document.getElementById('milestoneProviderChoices');
    const gemini=document.getElementById('milestoneChooseGemini');
    const sol=document.getElementById('milestoneChooseSol');
    let currentKey='',requested=false,phase='loading',detail='',startedAt=0,ticker=0;
    function key(story){return story&&story.runToken&&story.checkpointId?story.runToken+':'+story.checkpointId:'';}
    function matches(event){return key(event)===key(hooks.getState().story);}
    function provider(){return hooks.provider();}
    function updateMessage(story,choose){
      const base=detail||(choose?'Chọn một API tạo khung Milestone. Gehihi vẫn là Game Master kể chuyện.':
        story.waiting?'Đã thắng Boss. Đang nạp Milestone cho Checkpoint tiếp theo…':
        'Đang tạo và lưu Milestone. Vui lòng chờ…');
      message.textContent=base+(choose||phase==='error'||panel.hidden?'':' ('+Math.max(0,Math.floor((Date.now()-startedAt)/1000))+' giây)');
    }
    function sync(){
      const state=hooks.getState(),story=state.story||{},k=key(story);
      if(k!==currentKey){currentKey=k;requested=false;phase='loading';detail='';startedAt=Date.now();}
      // Game Core alone unlocks gameplay after the plan is installed.
      const blocked=hooks.available()&&!!k&&(story.waiting===true||story.hasPlan!==true);
      const choose=blocked&&!provider();
      panel.hidden=!blocked;global.__milestoneBlocked=blocked;
      choices.hidden=!choose;
      title.textContent=choose?'Chọn API tạo Milestone':'Chuẩn bị Checkpoint';
      retry.hidden=choose||phase!=='error';
      progress.hidden=choose||phase==='error';
      if(blocked&&!choose&&phase!=='error'&&!ticker)
        ticker=global.setInterval(function(){updateMessage((hooks.getState()||{}).story||{},false);},1000);
      if((!blocked||choose||phase==='error')&&ticker){global.clearInterval(ticker);ticker=0;}
      updateMessage(story,choose);
      hooks.changed();
      if(blocked&&!choose&&!requested&&phase!=='error'){
        requested=true;
        try{hooks.request();}
        catch(error){phase='error';detail='Không thể kết nối bộ nạp Milestone. Nhấn Thử lại.';sync();}
      }
    }
    function chooseProvider(value){
      if(panel.hidden||provider())return;
      let answer;
      try{answer=hooks.selectProvider(value);}
      catch(error){answer={ok:false,message:'Không lưu được lựa chọn API.'};}
      if(!answer||answer.ok!==true){
        detail=answer&&answer.message||'API này không khả dụng. Vui lòng chọn lại.';
        sync();return;
      }
      phase='loading';detail='';requested=false;startedAt=Date.now();sync();
    }
    gemini.addEventListener('click',function(){chooseProvider('GEMINI');});
    sol.addEventListener('click',function(){chooseProvider('SOL');});
    retry.addEventListener('click',function(){phase='loading';detail='';requested=false;startedAt=Date.now();sync();});
    return {sync,
      status:function(event){
        if(!matches(event))return;
        sync();phase=event.status==='error'?'error':'loading';detail=event.message||'';sync();
      },
      ready:function(event){
        if(!matches(event))return;
        try{
          hooks.refresh();
          const story=(hooks.getState()||{}).story||{};
          if(story.hasPlan!==true||story.waiting===true){
            phase='error';detail='Game Core chưa xác nhận Milestone đã nạp. Nhấn Thử lại.';
          }else{phase='loading';detail='';}
          sync();
        }catch(error){phase='error';detail='Không đồng bộ được Milestone từ Game Core. Nhấn Thử lại.';sync();}
      }
    };
  }};
})(window);
