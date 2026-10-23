/* Native encounter identities, offline speech, and presentation-only narrative cursors.
   No automatic Party join, Core spawn changes, or GM narrative injection. */
(function(global){
  'use strict';
  const catalog=global.BackroomSpecialDialogueData||{};
  const entityCatalog=global.BackroomSpecialEntityDialogue||{};
  const root=document.createElement('div');
  root.id='specialDialogue';root.hidden=true;
  root.setAttribute('aria-label','Hội thoại nhân vật');
  root.innerHTML='<section class="special-dialogue-card" role="dialog" aria-modal="true" aria-labelledby="specialDialogueName">'+
    '<header class="special-dialogue-top" id="specialDialogueName"></header>'+
    '<div class="special-dialogue-middle"><div class="special-dialogue-text" id="specialDialogueText" aria-live="polite"></div>'+
    '<div class="special-dialogue-avatar" id="specialDialogueAvatar"></div></div>'+
    '<div class="special-dialogue-progress" id="specialDialogueProgress"></div>'+
    '<div class="special-dialogue-actions" id="specialDialogueActions"></div></section>';
  document.body.appendChild(root);
  const name=root.querySelector('#specialDialogueName');
  const text=root.querySelector('#specialDialogueText');
  const image=root.querySelector('#specialDialogueAvatar');
  const actions=root.querySelector('#specialDialogueActions');
  const counter=root.querySelector('#specialDialogueProgress');
  const STORY_CHANCE=0.10; // Presentation RNG, separate from the Core's encounter tickets.
  let active=null;
  function progress(){
    if(!state.specialDialogueProgress||typeof state.specialDialogueProgress!=='object')
      state.specialDialogueProgress={introduced:{},chapters:{},lastRollTurn:state.turn,shownEncounters:{},pending:null};
    const p=state.specialDialogueProgress;
    for(const k of ['introduced','introRevisions','chapters','pastVisits','shownEncounters','entityBeats','entityCross','entityRepeats'])
      if(!p[k]||typeof p[k]!=='object')p[k]={};
    return p;
  }
  function store(){if(typeof persistPresentation==='function')persistPresentation();}
  function visibleCharacter(id){
    const party=(state.partyDetails&&state.partyDetails.members)||[];
    return party.find(c=>String(c.id)===String(id))||
      (state.encounteredCharacters||[]).find(c=>String(c.id)===String(id))||
      (state.party||[]).find(c=>String(c.id)===String(id))||
      (state.scene&&state.scene.npcs||[]).find(c=>String(c.id||c.characterId)===String(id))||
      (state.scene&&state.scene.availableActions||[]).filter(c=>c.kind==='TALK')
        .map(c=>({id:c.targetId,name:c.targetName||c.label}))
        .find(c=>String(c.id)===String(id))||null;
  }
  function label(id){
    if(id==='cao_minh')return state.player&&state.player.name||'Cao Minh';
    const c=visibleCharacter(id);return c&&c.name||entityCatalog[id]&&entityCatalog[id].name||id;
  }
  function entityEncounter(){
    const combat=state.combat;
    const encounter=combat&&combat.active===true?combat:state.pendingEncounter;
    if(!encounter||!entityCatalog[encounter.entityKey]||!encounter.encounterId)return null;
    // Pending confirmation and the first combat wave share one dialogue receipt.
    // Later waves have their own Core instance identity, even in the same battle.
    const wave=Number(encounter.waveNumber||1);
    return {entityKey:encounter.entityKey,encounterId:wave>1?
      encounter.entityInstanceId||encounter.encounterId+':wave:'+(wave-1):encounter.encounterId};
  }
  function avatar(id){
    if(id==='cao_minh')return 'avatars/CAO_MINH_AVATAR.jpg';
    if(entityCatalog[id])return entityCatalog[id].overlay;
    const c=visibleCharacter(id);
    const ref=c&&(c.avatar||c.avatarRef);
    return typeof ref==='string'&&/^(?:avatars\/)[a-z0-9_./-]+\.(?:jpg|jpeg|png|webp)$/i.test(ref)&&!ref.includes('..')?ref:'';
  }
  function button(caption,fn){
    const b=document.createElement('button');b.type='button';b.textContent=caption;b.addEventListener('click',fn);
    actions.appendChild(b);return b;
  }
  function showSpeaker(id){
    name.textContent=label(id);
    image.replaceChildren();
    const src=avatar(id);
    if(src){
      const img=document.createElement('img');img.alt='Chân dung '+label(id);
      img.src=src;img.className=entityCatalog[id]?'special-dialogue-entity':'';
      img.onerror=()=>{img.remove();const mark=document.createElement('span');mark.textContent='?';image.appendChild(mark);};
      image.appendChild(img);
    }else{const mark=document.createElement('span');mark.textContent='?';image.appendChild(mark);}
  }
  function eligibleInvite(id){
    const c=(state.encounteredCharacters||[]).find(x=>x.id===id);
    return c&&c.present&&c.joinEligible&&!c.inParty&&!state.pendingEncounter;
  }
  // These characters previously only had a generic fallback greeting. Track
  // authored introductions without affecting other followers' current progress.
  const AUTHORED_INTRO_REVISIONS={
    lucia:'lucia-intro-r01',
    catherine:'catherine-intro-r01',
    janet:'janet-intro-r01',
    nguyen_quoc_nam:'nguyen-quoc-nam-intro-r01',
    iris:'iris-intro-r01',
    syvial:'syvial-intro-r01',
    'an-nhien':'an-nhien-intro-r01'
  };
  function coreConfirmedNpc(id){
    // Do not create encounters from old names, GM prose, or historical roster.
    return (state.encounteredCharacters||[]).some(c=>c.id===id&&c.present===true)||
      (state.party||[]).some(c=>c.id===id&&c.presence!=='DEAD'&&c.presence!=='SEPARATED');
  }
  function needsIntro(id,p){
    const revision=AUTHORED_INTRO_REVISIONS[id];
    return !p.introduced[id]||(revision&&p.introRevisions[id]!==revision);
  }
  // The Core encounter confirms *who is present*. Presentation can track
  // conversation beats, but encounter count alone never proves a story secret.
  function entityScene(id, p, saved){
    const d=entityCatalog[id]||{};
    const episodes=Array.isArray(d.episodes)?d.episodes:[];
    const repeat=Array.isArray(d.repeat)?d.repeat:[];
    const route=saved&&saved.routeKind;
    const index=saved&&Number.isInteger(saved.routeIndex)?saved.routeIndex:0;
    function scene(kind,n){
      const source=kind==='episode'?episodes:kind==='cross'?d.cross:repeat;
      const picked=source[n];
      if(!Array.isArray(picked)||!picked.length)return null;
      return {routeKind:kind,routeIndex:n,lines:picked};
    }
    if(route){
      const restored=scene(route,index);
      if(restored)return restored;
    }
    const beat=Math.max(0,Number(p.entityBeats[id]||0));
    const other=id==='diep_minh'?'luc_tram_hac_hoa':'diep_minh';
    const otherBeat=Math.max(0,Number(p.entityBeats[other]||0));
    const crossIndex=Math.max(0,Number(p.entityCross[id]||0));
    // Every interlude requires encounters with BOTH rivals. Later interludes
    // require richer context, but never certify a clue or restore memories.
    const crossThresholds=[1,3,6];
    if(Array.isArray(d.cross)&&crossIndex<d.cross.length&&
      beat>=crossThresholds[crossIndex]&&otherBeat>=crossThresholds[crossIndex])
      return scene('cross',crossIndex);
    if(beat<episodes.length)return scene('episode',beat);
    if(repeat.length)return scene('repeat',Math.max(0,Number(p.entityRepeats[id]||0))%repeat.length);
    return {routeKind:'fallback',routeIndex:0,
      lines:(d.encounter||[]).map(text=>({speaker:id,text}))};
  }
  function finish(){
    if(!active)return;
    const wasFollowerScene=active.kind==='intro'||active.kind==='story';
    const p=progress();
    if(active.kind==='intro'){
      p.introduced[active.id]=true;
      const revision=AUTHORED_INTRO_REVISIONS[active.id];
      if(revision)p.introRevisions[active.id]=revision;
    }
    if(active.kind==='story')p.chapters[active.id]=Math.max(Number(p.chapters[active.id]||0),active.chapter+1);
    if(active.kind==='entity'){
      p.shownEncounters[active.encounterId]=true;
      if(active.routeKind==='episode')p.entityBeats[active.id]=Math.max(
        Number(p.entityBeats[active.id]||0),active.routeIndex+1);
      if(active.routeKind==='cross')p.entityCross[active.id]=Math.max(
        Number(p.entityCross[active.id]||0),active.routeIndex+1);
      if(active.routeKind==='repeat')p.entityRepeats[active.id]=
        Math.max(0,Number(p.entityRepeats[active.id]||0))+1;
      const keys=Object.keys(p.shownEncounters);
      if(keys.length>24)keys.slice(0,keys.length-24).forEach(key=>delete p.shownEncounters[key]);
    }
    p.pending=null;
    active=null;root.hidden=true;document.body.classList.remove('special-dialogue-active');
    store();
    // The Core can still report the NPC as present after the popup closes.
    // Restore Cao Minh now, not on the next encounter or movement turn.
    if(wasFollowerScene){
      if(typeof global.backroomRefreshSnapshotVisuals==='function')
        global.backroomRefreshSnapshotVisuals();
      else if(typeof global.backroomRefreshSnapshotPartyLayout==='function')
        global.backroomRefreshSnapshotPartyLayout();
    }
    if(typeof global.backroomSyncExplorer==='function')global.backroomSyncExplorer();
    // Core may deliver an Entity while a Follower modal is still open.
    applyIncoming(state);
  }
  function invite(){
    if(!active||!eligibleInvite(active.id)||!global.Android||typeof Android.recruitFollower!=='function')return;
    const id=active.id;
    const buttons=actions.querySelectorAll('button');buttons.forEach(b=>b.disabled=true);
    // Party membership still requires the existing Core recruitment confirmation.
    try{Android.recruitFollower(JSON.stringify(state),id);}catch(_){buttons.forEach(b=>b.disabled=false);}
    finish();
  }
  function render(){
    if(!active)return;
    const p=progress();
    if(active.lines&&active.lines.length){
      const line=active.lines[active.index]||active.lines[active.lines.length-1];
      const speaker=line.speaker||active.id;
      showSpeaker(speaker);text.textContent=String(line.text||'');
      counter.textContent=(active.index+1)+' / '+active.lines.length;
      actions.replaceChildren();
      if(active.index<active.lines.length-1)button('Tiếp tục ›',()=>{
        active.index++;if(p.pending)p.pending.index=active.index;store();render();
      });
      else{
        if(active.kind==='intro'&&eligibleInvite(active.id))button('Mời vào Party',invite);
        button(active.kind==='intro'?'Không mời / Kết thúc':'Kết thúc',finish);
      }
    }else{showSpeaker(active.id);text.textContent='';counter.textContent='';actions.replaceChildren();button('Đóng',finish);}
    const first=actions.querySelectorAll('button')[0];
    if(first&&typeof first.focus==='function')first.focus();
  }
  function open(id,kind,lines,opts){
    if(active)return false;
    const typed=(lines||[]).filter(x=>x&&typeof x.text==='string'&&x.text.trim());
    active={id,kind,lines:typed,index:0,chapter:opts&&opts.chapter||0,
      encounterId:opts&&opts.encounterId||'',
      routeKind:opts&&opts.routeKind||'',routeIndex:opts&&opts.routeIndex||0};
    if(kind==='intro'||kind==='story'||kind==='entity'){
      progress().pending={id,kind,index:0,chapter:active.chapter,
        encounterId:active.encounterId,routeKind:active.routeKind,routeIndex:active.routeIndex};
      store();
    }
    root.hidden=false;document.body.classList.add('special-dialogue-active');
    if(kind==='intro'||kind==='story'){
      if(typeof global.backroomRefreshSnapshotVisuals==='function')
        global.backroomRefreshSnapshotVisuals();
      else if(typeof global.backroomRefreshSnapshotPartyLayout==='function')
        global.backroomRefreshSnapshotPartyLayout();
    }
    render();
    return true;
  }
  function lines(id,kind){
    const d=catalog[id]||{};
    if(kind==='past'){
      const chapter=Number(progress().chapters[id]||0);
      const base=Array.isArray(d.past)?d.past:[];
      // Reveal no more than one new recollection for each finished story
      // chapter. The Past button never grants progression or reads a future
      // chapter; repeated requests cycle through the currently known memories.
      const unlocked=base.slice(0,Math.min(base.length,1+chapter));
      const visits=Math.max(0,Number(progress().pastVisits[id]||0));
      const memory=unlocked.length?unlocked[visits%unlocked.length]:'Tôi chưa muốn nhắc đến chuyện trước kia.';
      progress().pastVisits[id]=visits+1;
      store();
      return [{speaker:id,text:memory}];
    }
    const ambient=Array.isArray(d.ambient)&&d.ambient.length?d.ambient:
      ['Tôi đang nghe. Có điều gì cần trao đổi không?','Chúng ta nên cẩn trọng khi di chuyển ở đây.'];
    return [{speaker:id,text:ambient[Math.floor(Math.random()*ambient.length)]}];
  }
  function openCharacter(id,mode){
    if(active||!visibleCharacter(id)||!String(id))return false;
    if(mode==='hint'){
      if(!open(id,'hint',[{speaker:id,text:'Đang xem xét manh mối tại khu vực hiện tại…'}]))return false;
      actions.replaceChildren();
      const talk=(state.scene&&state.scene.availableActions||[]).find(a=>a.kind==='TALK'&&
        (String(a.targetId)===String(id)||String(a.targetId||'').startsWith('character:'+id+':')));
      if(talk&&global.Android&&typeof Android.requestDialogueTalk==='function'){
        const command={actionId:talk.actionId,sceneVersion:talk.sceneVersion,roomId:talk.roomId,kind:talk.kind,targetId:talk.targetId};
        Android.requestDialogueTalk(JSON.stringify(state),JSON.stringify(command),id);
      }else if(global.Android&&typeof Android.requestDialogueHint==='function')
        Android.requestDialogueHint(JSON.stringify(state),id);
      else{active.lines=[{speaker:id,text:'Chưa kết nối được chức năng gợi ý của GM.'}];render();}
      return true;
    }
    const p=progress();
    if(needsIntro(id,p)){
      const d=catalog[id];
      const intro=d&&Array.isArray(d.intro)&&d.intro.length?d.intro:
        [{speaker:id,text:'Xin chào. Có điều gì muốn trao đổi với tôi không?'}];
      return open(id,'intro',intro);
    }
    return open(id,mode,lines(id,mode));
  }
  function handleHint(raw){
    let answer;try{answer=JSON.parse(raw);}catch(_){return;}
    if(!active||active.kind!=='hint'||String(active.id)!==String(answer.id))return;
    if(answer.state&&typeof global.backroomTurn==='function')global.backroomTurn(JSON.stringify(answer.state));
    active.lines=[{speaker:active.id,text:String(answer.text||'Chưa tìm được manh mối đáng tin cậy.').slice(0,1000)}];
    active.index=0;render();
  }
  function invalidateAction(raw){
    const normalized=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
    const input=normalized(raw);
    if(!/\b(hoi|noi|chao|tro chuyen|tam su|bat chuyen|ke chuyen|xin goi y|tha thiet|to tinh|nhan tin)\b/.test(input))return false;
    const known=(state.encounteredCharacters||[]).concat(state.party||[]);
    const unique=[...new Set(known.flatMap(c=>{
      const full=normalized(c.name).replace(/["']/g,' ').trim();
      const short=full.split(/\s+/)[0];
      return [full,short].filter(s=>s.length>=4);
    }))];
    if(!unique.some(n=>input.includes(n)))return false;
    if(!Array.isArray(state.log))state.log=[];
    state.log.push({role:'gm',text:'HÀNH ĐỘNG KHÔNG HỢP LỆ! Vui lòng tương tác với nhân vật khác qua [Giao Tiếp].'});
    if(typeof global.render==='function')global.render();
    const log=document.getElementById('log');if(log)log.scrollTop=log.scrollHeight;
    store();return true;
  }
  function applyIncoming(old){
    const p=progress();
    const encounter=entityEncounter();
    if(active&&active.kind==='entity'&&(!encounter||active.id!==encounter.entityKey||
        active.encounterId!==encounter.encounterId)){
      active=null;p.pending=null;root.hidden=true;document.body.classList.remove('special-dialogue-active');store();
    }
    if(active)return;
    // Resume persisted modal after reload. Its state is stored with the current-version presentation.
    if(p.pending&&p.pending.kind){
      const saved=p.pending;
      if(saved.kind==='entity'&&(!encounter||saved.id!==encounter.entityKey||
          saved.encounterId!==encounter.encounterId||p.shownEncounters[saved.encounterId])){p.pending=null;store();}
      else if(saved.kind==='intro'&&!coreConfirmedNpc(saved.id))p.pending=null;
      else {
        const d=catalog[saved.id]||{};
        const entityRoute=saved.kind==='entity'?entityScene(saved.id,p,saved):null;
        const seq=saved.kind==='intro'?(d.intro||[{speaker:saved.id,text:'Xin chào.'}]):
          saved.kind==='story'?(d.story||[])[saved.chapter]||[]:
          entityRoute&&entityRoute.lines||[];
        if(open(saved.id,saved.kind,seq,{chapter:saved.chapter,encounterId:saved.encounterId,
          routeKind:entityRoute&&entityRoute.routeKind,routeIndex:entityRoute&&entityRoute.routeIndex})){
          active.index=Math.min(Math.max(0,saved.index||0),active.lines.length-1);
          if(p.pending)p.pending.index=active.index;
          store();render();return;
        }
      }
    }
    if(encounter&&!p.shownEncounters[encounter.encounterId]){
      const selected=entityScene(encounter.entityKey,p);
      open(encounter.entityKey,'entity',selected.lines,{encounterId:encounter.encounterId,
        routeKind:selected.routeKind,routeIndex:selected.routeIndex});return;
    }
    // The encounter might have been committed before the UI registered the
    // callback (reload, Android projection, or first render). Core presence +
    // an unfinished introduction are the authority, not a roster diff.
    const newcomer=(state.encounteredCharacters||[]).find(c=>
      c.present===true&&needsIntro(c.id,p)&&coreConfirmedNpc(c.id))||
      (state.party||[]).find(c=>coreConfirmedNpc(c.id)&&needsIntro(c.id,p));
    if(newcomer){
      const d=catalog[newcomer.id];
      open(newcomer.id,'intro',d&&d.intro||[{speaker:newcomer.id,text:'Xin chào. Có điều gì muốn trao đổi không?'}]);return;
    }
    if(!(old&&old.turn<state.turn&&state.scene&&!state.pendingEncounter&&!(state.combat&&state.combat.active)))return;
    if(p.lastRollTurn>=state.turn)return;
    p.lastRollTurn=state.turn;
    // Story rolls only after a committed Explorer action. No roll on render, TALK or meta panels.
    const entries=state.log||[];
    const latest=[...entries].reverse().find(e=>e&&e.role==='player');
    if(!latest||!/(?:di chuy[eể]n|bước|đi qua|đi tới|đi đến|tìm kiếm|tìm hiểu|quan sát|kiểm tra|khám phá|thao tác|tiến vào|băng qua)/i.test(latest.text||'')){store();return;}
    const eligible=(state.party||[]).filter(c=>{
      const detail=(state.partyDetails&&state.partyDetails.members||[])
        .find(m=>String(m.id)===String(c.id));
      // Do not stage a portrait-less story for an absent or incapacitated member.
      if(c.presence==='DEAD'||c.presence==='SEPARATED'||(detail&&
        (detail.presence==='DEAD'||detail.presence==='SEPARATED'||
          (detail.currentHp!=null&&Number(detail.currentHp)<=0))))return false;
      const d=catalog[c.id];return d&&Array.isArray(d.story)&&
        Number(p.chapters[c.id]||0)<d.story.length&&p.introduced[c.id];
    });
    if(eligible.length&&Math.random()<STORY_CHANCE){
      const member=eligible[Math.floor(Math.random()*eligible.length)];
      open(member.id,'story',catalog[member.id].story[Number(p.chapters[member.id]||0)],
        {chapter:Number(p.chapters[member.id]||0)});
    }
    store();
  }
  global.backroomDialogueHint=handleHint;
  global.backroomDialogueInvalidAction=function(message){
    if(!Array.isArray(state.log))state.log=[];
    state.log.push({role:'gm',text:String(message||'HÀNH ĐỘNG KHÔNG HỢP LỆ! Vui lòng sử dụng [Giao Tiếp].')});
    if(typeof global.render==='function')global.render();
    const log=document.getElementById('log');if(log)log.scrollTop=log.scrollHeight;
    store();
    // Native pre-planner rejection must release the Explorer batch lock.
    if(typeof global.backroomPlayerActionFinished==='function')
      global.backroomPlayerActionFinished(JSON.stringify({executed:0,completed:true}));
  };
  global.SpecialDialogue={openCharacter,isOpen:()=>!!active,rejectPlayerAction:invalidateAction,
    offerEntity:()=>applyIncoming(null)};
  const previousTurn=global.backroomTurn;
  if(typeof previousTurn==='function')global.backroomTurn=function(json){
    const before=state&&JSON.parse(JSON.stringify(state));
    const result=previousTurn.call(this,json);
    applyIncoming(before);return result;
  };
  const previousRender=global.render;
  if(typeof previousRender==='function')global.render=function(){
    const result=previousRender.apply(this,arguments);
    // Also recover a first encounter that became present before this render,
    // even when there is no persisted modal cursor yet.
    applyIncoming(state);
    return result;
  };
  root.addEventListener('keydown',event=>{
    if(event.key!=='Escape'||!active)return;
    event.preventDefault();
    if(active.kind==='talk'||active.kind==='past'||active.kind==='hint')finish();
  });
  progress();
  applyIncoming(state);
})(window);
