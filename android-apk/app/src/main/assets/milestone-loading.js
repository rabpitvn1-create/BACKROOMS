(function(global){
  'use strict';
  global.BackroomMilestoneLoading={install:function(hooks){
    const panel=document.getElementById('milestoneLoading');
    const message=document.getElementById('milestoneLoadingMessage');
    const progress=document.getElementById('milestoneLoadingProgress');
    const retry=document.getElementById('milestoneLoadingRetry');
    let currentKey='',requested=false,phase='loading',detail='',startedAt=0,ticker=0;
    function key(story){return story&&story.runToken&&story.checkpointId?story.runToken+':'+story.checkpointId:'';}
    function matches(event){return key(event)===key(hooks.getState().story);}
    function updateMessage(story){
      const base=detail||(story.waiting?'Đã thắng Boss. Đang nạp Milestone cho Checkpoint tiếp theo…':'Đang nạp và kiểm định Milestone. Anh vui lòng chờ…');
      message.textContent=base+(phase==='error'||panel.hidden?'':' ('+Math.max(0,Math.floor((Date.now()-startedAt)/1000))+' giây)');
    }
    function sync(){
      const state=hooks.getState(),story=state.story||{},k=key(story);
      if(k!==currentKey){currentKey=k;requested=false;phase='loading';detail='';startedAt=Date.now();}
      // Only the authoritative Core snapshot can unlock gameplay.
      const blocked=hooks.available()&&!!k&&(story.waiting===true||story.hasPlan!==true);
      panel.hidden=!blocked;global.__milestoneBlocked=blocked;
      retry.hidden=phase!=='error';
      progress.hidden=phase==='error';
      if(blocked&&phase!=='error'&&!ticker){ticker=global.setInterval(function(){updateMessage((hooks.getState()||{}).story||{});},1000);}
      if((!blocked||phase==='error')&&ticker){global.clearInterval(ticker);ticker=0;}
      updateMessage(story);
      hooks.changed();
      if(blocked&&!requested&&phase!=='error'){
        requested=true;
        try{hooks.request();}catch(error){phase='error';detail='Không thể kết nối bộ nạp Milestone. Nhấn Thử lại.';sync();}
      }
    }
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
            phase='error';
            detail='Game Core chưa xác nhận Milestone đã nạp. Nhấn Thử lại.';
          }else{phase='loading';detail='';}
          sync();
        }catch(error){phase='error';detail='Không xác minh được Milestone trong Game Core. Nhấn Thử lại.';sync();}
      }
    };
  }};
})(window);
