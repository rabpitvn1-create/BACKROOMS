(function(){
  'use strict';
  if(window.__narrativeV2UiInstalled)return;
  window.__narrativeV2UiInstalled=true;

  var loadingBusy=false;
  var loadingOverlay=document.createElement('div');
  loadingOverlay.className='narrative-v2-loading';
  loadingOverlay.hidden=true;
  loadingOverlay.innerHTML='<div class="narrative-v2-loading-card"><div class="narrative-v2-hourglass">⌛</div><b>ĐANG BIÊN TẬP HỒI</b><div class="narrative-v2-progress" id="narrativeV2Progress" role="progressbar" aria-label="Tiến độ biên tập hồi" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><i id="narrativeV2ProgressFill"></i></div><div class="narrative-v2-progress-meta"><span id="narrativeV2LoadingText">CHUẨN BỊ</span><strong id="narrativeV2ProgressPercent">0%</strong></div></div>';
  document.body.appendChild(loadingOverlay);

  var management=document.getElementById('managementPanel');
  var missionCard=document.createElement('div');
  missionCard.className='card narrative-v2-missions';
  missionCard.hidden=true;
  missionCard.innerHTML='<h2>HỆ THỐNG · NHIỆM VỤ</h2><div class="narrative-v2-act" id="narrativeV2Act"></div><div id="narrativeV2MissionList"></div>';
  if(management)management.insertBefore(missionCard,management.firstChild);

  var topTitle=document.getElementById('title');
  var missionSummary=document.createElement('div');
  missionSummary.className='narrative-v2-summary';
  missionSummary.hidden=true;
  if(topTitle&&topTitle.parentElement)topTitle.parentElement.appendChild(missionSummary);

  var style=document.createElement('style');
  style.textContent=[
    '.narrative-v2-loading[hidden]{display:none}',
    '.narrative-v2-loading{position:fixed;inset:0;z-index:10000;display:grid;place-items:center;background:radial-gradient(circle at center,#16170f 0,#080a08 54%,#020303 100%);padding:24px}',
    '.narrative-v2-loading:before{content:"";position:absolute;inset:0;opacity:.16;background-image:url("file:///android_asset/dice/level0-wallpaper.svg");background-size:64px 96px}',
    '.narrative-v2-loading-card{position:relative;z-index:1;width:min(360px,90vw);display:grid;gap:10px;text-align:center;padding:24px;border:1px solid #756d43;background:#0d100d;color:#eee7c7;border-radius:12px;box-shadow:0 16px 60px #000c}',
    '.narrative-v2-hourglass{font-size:30px;animation:narrative-v2-pulse 1.1s ease-in-out infinite}',
    '.narrative-v2-loading-card b{font:700 13px Play,"Pretendard Std",sans-serif;letter-spacing:.14em}',
    '.narrative-v2-progress{height:7px;overflow:hidden;border:1px solid #5d593d;border-radius:999px;background:#17180f;box-shadow:inset 0 1px 3px #0009}',
    '.narrative-v2-progress i{display:block;width:0;height:100%;border-radius:inherit;background:linear-gradient(90deg,#726b38,#d8c875);box-shadow:0 0 10px #c9b95b66;transition:width .22s ease-out}',
    '.narrative-v2-progress-meta{display:flex;justify-content:space-between;gap:12px;align-items:center;color:#aaa98f;font:700 9px/1.3 Play,"Pretendard Std",sans-serif;letter-spacing:.09em}',
    '.narrative-v2-progress-meta span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:left}',
    '.narrative-v2-progress-meta strong{color:#e8dfbd;font:700 10px/1 Play,"Pretendard Std",sans-serif}',
    '.narrative-v2-missions{border-color:#5e5637;background:linear-gradient(135deg,#171811,#0d1010 70%)}',
    '.narrative-v2-missions h2{color:#e6d391}',
    '.narrative-v2-act{margin-bottom:8px;color:#a9a78e;font:700 10px/1.3 Play,"Pretendard Std",sans-serif;letter-spacing:.08em}',
    '.narrative-v2-mission{display:grid;grid-template-columns:66px 1fr auto;gap:7px;align-items:start;padding:7px 0;border-bottom:1px solid #292a20;font-size:11px}',
    '.narrative-v2-mission:last-child{border-bottom:0}',
    '.narrative-v2-mission-type{color:#978e62;font:700 9px/1.4 Play,"Pretendard Std",sans-serif}',
    '.narrative-v2-mission-title{line-height:1.4}',
    '.narrative-v2-mission-state{font-size:9px;color:#8f9a91;white-space:nowrap}',
    '.narrative-v2-summary{margin-top:3px;color:#d5ca91;font:700 8px/1.2 Play,"Pretendard Std",sans-serif;letter-spacing:.1em;text-shadow:0 1px 2px #000}',
    '@keyframes narrative-v2-pulse{0%,100%{transform:scale(1);opacity:.7}50%{transform:scale(1.12);opacity:1}}',
    '@media(prefers-reduced-motion:reduce){.narrative-v2-hourglass{animation:none}}'
  ].join('');
  document.head.appendChild(style);

  var AVATARS={
    cao_minh:'file:///android_asset/avatars/cao_minh_avatar.jpg',
    lucia:'file:///android_asset/avatars/lucia_avatar.png',
    luc_tram:'file:///android_asset/avatars/luctram_avatar.png',
    syvial:'file:///android_asset/avatars/Syvial_avatar.jpg'
  };
  var OVERLAYS={
    cao_minh:'file:///android_asset/cao_minh_entity_overlay.png',
    lucia:'file:///android_asset/lucia_overlay.png',
    luc_tram:'file:///android_asset/luctram_overlay.png'
  };

  function rootOf(s){return s&&s.narrativeV2&&s.narrativeV2.enabled===true?s.narrativeV2:null;}
  function normalizeId(raw){
    var v=String(raw||'').trim().toLowerCase().replace(/[^a-z0-9_:-]+/g,'_');
    if(v.indexOf('cao_minh')>=0||v.indexOf('cao:minh')>=0)return 'cao_minh';
    if(v.indexOf('lucia')>=0)return 'lucia';
    if(v.indexOf('luc_tram')>=0||v.indexOf('luc:tram')>=0)return 'luc_tram';
    if(v.indexOf('syvial')>=0)return 'syvial';
    return v;
  }

  function missionStateLabel(m){
    if(m.status==='COMPLETE')return 'HOÀN THÀNH';
    if(m.status==='FAILED')return 'THẤT BẠI';
    var p=Number(m.progress)||0,t=Math.max(1,Number(m.target)||1);
    return p>0?String(p)+'/'+String(t):'ĐANG LÀM';
  }

  function renderMissionBoard(){
    var root=rootOf(state);
    missionCard.hidden=!root;
    missionSummary.hidden=!root;
    if(!root)return;
    var act=document.getElementById('narrativeV2Act');
    var list=document.getElementById('narrativeV2MissionList');
    var missions=Array.isArray(root.missionBoard)?root.missionBoard:[];
    if(act)act.textContent='ACT '+String(root.actIndex||1)+(root.levelResult&&root.levelResult.ending?' · '+String(root.levelResult.ending):'');
    if(list){
      list.textContent='';
      missions.forEach(function(m){
        if(!m)return;
        var row=document.createElement('div');row.className='narrative-v2-mission';
        var type=document.createElement('span');type.className='narrative-v2-mission-type';type.textContent=String(m.type||'MISSION');
        var title=document.createElement('span');title.className='narrative-v2-mission-title';
        title.textContent=(m.type==='HIDDEN'&&m.revealed!==true&&m.status!=='COMPLETE')?'???':String(m.title||'???');
        var stateEl=document.createElement('span');stateEl.className='narrative-v2-mission-state';stateEl.textContent=missionStateLabel(m);
        row.appendChild(type);row.appendChild(title);row.appendChild(stateEl);list.appendChild(row);
      });
    }
    var done=missions.filter(function(m){return m&&m.status==='COMPLETE';}).length;
    missionSummary.textContent='ACT '+String(root.actIndex||1)+' · NHIỆM VỤ '+String(done)+'/'+String(missions.length);
  }

  function syncLegacyControls(){
    var active=!!rootOf(state);
    var bar=document.querySelector('.player-action-bar');
    var modal=document.getElementById('playerActionModal');
    var route=document.querySelector('.snapshot-route-streak');
    if(bar)bar.hidden=active;
    if(active&&modal){modal.hidden=true;modal.setAttribute('aria-hidden','true');document.body.classList.remove('player-action-open');}
    if(route)route.hidden=active;
  }

  function assetPaths(nextState){
    var root=rootOf(nextState),paths=[],seen={};
    function add(path){if(path&&!seen[path]){seen[path]=true;paths.push(path);}}
    if(!root)return paths;
    var manifest=root.assetManifest||{};
    (Array.isArray(manifest.entityKeys)?manifest.entityKeys:[]).forEach(function(key){
      var clean=String(key||'').trim();
      if(clean)add('file:///android_asset/entity/'+clean+'.webp');
    });
    if(manifest.chest===true)add('file:///android_asset/chest_overlay.png');

    var ids=['cao_minh'];
    (Array.isArray(manifest.partyMemberIds)?manifest.partyMemberIds:[]).forEach(function(id){ids.push(normalizeId(id));});
    (Array.isArray(nextState.party)?nextState.party:[]).forEach(function(member){
      if(!member||member.joined!==true)return;
      ids.push(normalizeId(member.id||member.name));
      var customAvatar=member.avatar||member.avatarRef;
      if(customAvatar)add(String(customAvatar));
    });
    ids.forEach(function(id){
      id=normalizeId(id);
      if(AVATARS[id])add(AVATARS[id]);
      if(OVERLAYS[id])add(OVERLAYS[id]);
      if(id==='cao_minh')add('file:///android_asset/cao_minh_snapshot_overlay.png');
    });
    return paths;
  }

  function preloadAssets(nextState,onProgress){
    var paths=assetPaths(nextState),loaded=0;
    if(!paths.length){if(onProgress)onProgress(0,0);return Promise.resolve();}
    return Promise.all(paths.map(function(path){
      return new Promise(function(resolve){
        var img=new Image(),done=false;
        var finish=function(){
          if(done)return;
          done=true;loaded++;
          if(onProgress)onProgress(loaded,paths.length);
          resolve();
        };
        img.onload=finish;img.onerror=finish;img.src=path;
        setTimeout(finish,8000);
      });
    }));
  }

  var loadingPercent=0;
  function setLoadingProgress(percent,stage){
    var value=Math.max(0,Math.min(100,Math.round(Number(percent)||0)));
    if(value<loadingPercent)return;
    loadingPercent=value;
    var bar=document.getElementById('narrativeV2Progress');
    var fill=document.getElementById('narrativeV2ProgressFill');
    var text=document.getElementById('narrativeV2LoadingText');
    var label=document.getElementById('narrativeV2ProgressPercent');
    if(bar)bar.setAttribute('aria-valuenow',String(value));
    if(fill)fill.style.width=String(value)+'%';
    if(text&&stage)text.textContent=String(stage);
    if(label)label.textContent=String(value)+'%';
  }

  function showLoading(root){
    loadingPercent=0;
    setLoadingProgress(0,'CHUẨN BỊ ACT '+String(root&&root.actIndex||1));
    loadingOverlay.hidden=false;
  }
  function hideLoading(){loadingOverlay.hidden=true;}

  function maybePrepare(){
    var root=rootOf(state);
    if(!root||root.loadingRequired!==true||root.gameComplete===true||loadingBusy)return;
    if(state&&state.combat&&state.combat.active===true)return;
    if(!window.Android||typeof Android.prepareNarrativeAct!=='function')return;
    loadingBusy=true;
    if(typeof busy!=='undefined')busy=true;
    showLoading(root);
    try{Android.prepareNarrativeAct(JSON.stringify(state));}
    catch(error){loadingBusy=false;hideLoading();if(window.backroomError)window.backroomError(String(error&&error.message||error));}
  }

  window.backroomNarrativeProgress=function(json){
    try{
      var progress=JSON.parse(json);
      setLoadingProgress(progress.percent,progress.stage);
    }catch(_){}
  };

  window.backroomNarrativeReady=function(json){
    var next;
    try{next=JSON.parse(json);}catch(error){loadingBusy=false;hideLoading();if(window.backroomError)window.backroomError('Narrative V2 state không hợp lệ.');return;}
    setLoadingProgress(80,'NẠP ASSET');
    preloadAssets(next,function(done,total){
      var ratio=total>0?done/total:1;
      setLoadingProgress(80+Math.round(ratio*20),'NẠP ASSET '+String(done)+'/'+String(total));
    }).then(function(){
      setLoadingProgress(100,'SẴN SÀNG');
      state=next;
      try{if(typeof CURRENT_CHARACTER_CANON!=='undefined')state.characterCanon=CURRENT_CHARACTER_CANON;}catch(_){}
      loadingBusy=false;
      if(typeof busy!=='undefined')busy=false;
      hideLoading();
      if(typeof window.render==='function')window.render();
      renderMissionBoard();syncLegacyControls();maybePrepare();
    });
  };

  var previousTurn=window.backroomTurn;
  window.backroomTurn=function(json){
    if(typeof previousTurn==='function')previousTurn(json);
    renderMissionBoard();syncLegacyControls();maybePrepare();
  };

  var previousError=window.backroomError;
  window.backroomError=function(message){
    loadingBusy=false;hideLoading();
    if(typeof previousError==='function')previousError(message);
  };

  var previousRender=window.render;
  window.render=function(){
    if(typeof previousRender==='function')previousRender();
    renderMissionBoard();syncLegacyControls();
    setTimeout(maybePrepare,0);
  };

  renderMissionBoard();syncLegacyControls();setTimeout(maybePrepare,0);
})();
