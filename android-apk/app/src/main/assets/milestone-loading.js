(function(global){
  'use strict';
  global.BackroomMilestoneLoading={install:function(hooks){
    const panel=document.getElementById('milestoneLoading');
    const message=document.getElementById('milestoneLoadingMessage');
    const retry=document.getElementById('milestoneLoadingRetry');
    let currentKey='',requested=false,phase='loading',detail='';
    function key(story){return story&&story.runToken&&story.checkpointId?story.runToken+':'+story.checkpointId:'';}
    function matches(event){return key(event)===key(hooks.getState().story);}
    function sync(){
      const state=hooks.getState(),story=state.story,k=key(story);
      if(k!==currentKey){currentKey=k;requested=false;phase='loading';detail='';}
      const blocked=hooks.available()&&!!k&&(story.waiting===true||story.hasPlan===false);
      panel.hidden=!blocked;global.__milestoneBlocked=blocked;
      retry.hidden=phase!=='error';
      message.textContent=detail||(story&&story.waiting?'Đã thắng Boss. Đang nạp Milestone cho Checkpoint tiếp theo…':'Đang nạp Milestone. Anh vui lòng chờ…');
      hooks.changed();
      if(blocked&&!requested&&phase!=='error'){
        requested=true;
        try{hooks.request();}catch(error){phase='error';detail='Không thể kết nối bộ nạp Milestone. Nhấn Thử lại.';sync();}
      }
    }
    retry.addEventListener('click',function(){phase='loading';detail='';requested=false;sync();});
    return {sync,
      status:function(event){
        if(!matches(event))return;
        // Align the new run before retaining its status. Ignore old-run callbacks.
        sync();phase=event.status;detail=event.message||'';sync();
      },
      ready:function(event){
        if(!matches(event))return;
        try{hooks.refresh();sync();}catch(error){phase='error';detail='Milestone đã nạp nhưng chưa cập nhật được giao diện. Nhấn Thử lại.';sync();}
      }
    };
  }};
})(window);
