const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.join(__dirname,'../app/src/main');
const html=fs.readFileSync(path.join(root,'assets/index.html'),'utf8');
const prologueDeclaration=html.match(/const prologue=`[\s\S]*?`;/)[0];
const setup=html.slice(html.indexOf('function setupPrologue(){'),html.indexOf('\nsetupPrologue();'));
const compact=html.slice(html.indexOf('function compactReaderContent('),html.indexOf('function syncClientFromCore('));

test('opening reader starts collapsed before the game and full prose is absent from initial state',()=>{
  const tag=html.match(/<details\b[^>]*id="prologuePanel"[^>]*>/)[0];
  assert.doesNotMatch(tag,/\bopen\b/);
  assert.ok(html.indexOf('id="prologuePanel"')<html.indexOf('id="snapshot"'));
  assert.match(html,/<summary><span id="prologueToggleLabel">Đọc mở đầu<\/span><\/summary>/);
  const initial=html.slice(html.indexOf('const initial={'),html.indexOf('const byId='));
  const ctx={};vm.runInNewContext(prologueDeclaration+'\n'+initial+'\nthis.start=initial;this.story=prologue;',ctx);
  assert.ok(ctx.story.length>30000,'the original authored prose is retained for the reader');
  assert.ok(JSON.stringify(ctx.start).length<2000);
  assert.equal(ctx.start.log.length,1);assert.match(ctx.start.log[0].text,/LƯỢT 1/);
  assert.equal(JSON.stringify(ctx.start).includes(ctx.story),false);
});
test('native disclosure expands full text on demand and closes without touching game state',()=>{
  let toggle,writes=0,text='';
  const panel={open:false,addEventListener(type,fn){assert.equal(type,'toggle');toggle=fn;}};
  const body={set textContent(v){writes++;text=v;},get textContent(){return text;}};
  const label={textContent:'Đọc mở đầu'};
  const state={turn:9,log:[{role:'player',text:'Giữ nguyên lượt này.'}]};
  const ctx={state,document:{getElementById:id=>({prologuePanel:panel,prologueBody:body,prologueToggleLabel:label})[id]}};
  vm.runInNewContext(prologueDeclaration+'\n'+setup+'\nsetupPrologue();this.story=prologue;',ctx);
  const before=JSON.stringify(state);
  assert.equal(writes,0);
  panel.open=true;toggle();assert.equal(text,ctx.story);assert.equal(label.textContent,'Thu gọn mở đầu');
  panel.open=false;toggle();assert.equal(label.textContent,'Đọc mở đầu');
  panel.open=true;toggle();assert.equal(writes,1,'do not rebuild the novel on every game render or reopen');
  assert.equal(JSON.stringify(state),before);
});
test('reader content is omitted from bridge/save presentation without dropping player history',()=>{
  const ctx={};vm.runInNewContext(prologueDeclaration+'\n'+compact+'\nthis.story=prologue;',ctx);
  const opening={role:'gm',text:'MỞ ĐẦU\n\n'+ctx.story};
  const player={role:'player',text:opening.text};
  const ordinary={role:'gm',text:'MỞ ĐẦU\n\nMột câu khác của GM.'};
  const s={turn:4,log:[opening,player,ordinary],scene:{room:{id:'current'}}};
  ctx.compactReaderContent(s);
  assert.deepEqual(s.log,[player,ordinary]);assert.equal(s.turn,4);assert.equal(s.scene.room.id,'current');
  assert.match(html,/function syncClientFromCore\(presentation\) \{\s*compactReaderContent\(presentation\)/);
});
test('both writer paths receive the compact historical anchor, never the reader novel',()=>{
  const java=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.equal((java.match(/OPENING_CONTEXT=/g)||[]).length,2);
  assert.equal((java.match(/GmNarrativeVoicePolicy\.openingContext\(\)/g)||[]).length,2);
  assert.doesNotMatch(java,/getString\("prologue"\)|BackroomPrologueText/);
  const staged=java.slice(java.indexOf('private String sceneWriterPrompt()'),java.indexOf('private String narrateStagedSceneAction('));
  const mixed=java.slice(java.indexOf('private String narrateMixedPlayerAction('),java.indexOf('/** Retry stored public events only.'));
  for(const writer of [staged,mixed]){
    assert.match(writer,/OPENING_CONTEXT=" \+ com\.rabpit\.backroom\.core\.knowledge\.GmNarrativeVoicePolicy\.openingContext\(\)\s*\+ "\\nCANON=/,
      'each narration request appends the full native context outside the clipped recent-history view');
    assert.doesNotMatch(writer,/openingContext\(\)\.(?:substring|take|slice)/);
  }
});
