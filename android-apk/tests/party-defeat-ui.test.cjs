const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const assets=path.join(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const helpers=html.slice(html.indexOf('function renderGameOver(){'),html.indexOf('function freshInitial(){'));
function runtime(state){
  const nodes={gameOverPanel:{hidden:true},prologuePrompt:{hidden:false},gameOverStatus:{}};
  const ctx={state,document:{getElementById:id=>nodes[id]},submitEl:{disabled:false},statusEl:{textContent:''}};
  vm.runInNewContext(helpers,ctx);return {ctx,nodes};
}
test('Core terminal projection shows Game Over, stops input and restores after checkpoint load',()=>{
  const {ctx,nodes}=runtime({gameOver:true});ctx.renderGameOver();
  assert.equal(nodes.gameOverPanel.hidden,false);assert.equal(ctx.submitEl.disabled,true);
  assert.equal(nodes.prologuePrompt.hidden,true);
  ctx.runGameOverAction(()=>{ctx.statusEl.textContent='Nhấn lần nữa để chơi lại';});
  assert.equal(nodes.gameOverStatus.textContent,'Nhấn lần nữa để chơi lại');
  ctx.state={gameOver:false};ctx.renderGameOver();assert.equal(nodes.gameOverPanel.hidden,true);
});
test('zero HP and explicit death use a real bundled tombstone; missing HP never implies death',()=>{
  const {ctx}=runtime({});
  for(const member of [{currentHp:0},{presence:'DEAD'},{condition:'DEAD'}])assert.equal(ctx.characterIsDead(member),true);
  for(const member of [{currentHp:1},{presence:'SEPARATED'},{},null])assert.equal(ctx.characterIsDead(member),false);
  assert.ok(fs.existsSync(path.join(assets,'avatars/tombstone.svg')));
  const cards=html.slice(html.indexOf('  function renderPartyCards(){'),html.indexOf('  if(back)'));
  const detail=html.slice(html.indexOf('  function renderMemberDetail(member){'),html.indexOf('  function openMember('));
  for(const render of [cards,detail])assert.match(render,/characterIsDead\(member\)\?'avatars\/tombstone\.svg'/);
  assert.match(cards,/is-dead/);
  assert.match(html,/runGameOverAction\(load\)/);assert.match(html,/runGameOverAction\(resetGame\)/);
});
test('Jeff and Jane match the party visible height and ground plane on narrow and wide snapshots',()=>{
  const java=fs.readFileSync(path.join(assets,'../java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const parts=java.split('\n').filter(row=>row.includes('__entityVisualBounds=')||row.includes('function normalizeEntityVisual('))
    .map(row=>JSON.parse(row.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/)[1]));
  const bounds={jeff_the_killer:[16,40,850,1498],jane_the_killer:[63,234,847,1298]};
  const css=fs.readFileSync(path.join(assets,'combat-feedback-1193a.css'),'utf8');
  const entityRule=css.match(/\.snapshot\[data-combat-focus\] \.snapshot-entity\{([^}]+)\}/)[1];
  assert.doesNotMatch(entityRule,/left:[^;]+!important/,'CSS must not override the measured alpha-bound anchor');
  for(const [w,h] of [[260,300],[360,260],[620,450]]){
    const ctx={document:{getElementById:()=>({clientWidth:w,clientHeight:h,getBoundingClientRect:()=>({width:w,height:h})})}};
    vm.runInNewContext(parts.join('\n'),ctx);
    for(const [key,b] of Object.entries(bounds)){
      const image={alt:key,style:{},naturalWidth:864,naturalHeight:1536};ctx.normalizeEntityVisual(image);
      const height=parseFloat(image.style.height),width=parseFloat(image.style.width);
      const visibleHeight=height*(b[3]-b[1])/1536;
      assert.ok(Math.abs(visibleHeight-Math.min(h*.90,w*.84/1.04))<.01,key);
      assert.ok(Math.abs(parseFloat(image.style.bottom)+height*(1-b[3]/1536)-h*.05)<.01,key);
      assert.ok(Math.abs(parseFloat(image.style.left)+width*b[0]/864-w*.05)<.01,key);
      assert.equal(image.style.transform,'none');assert.equal(image.style.maxWidth,'none');
    }
  }
});
test('Prologue offer and reader share the CRT surface without the obsolete brown gradient',()=>{
  const css=fs.readFileSync(path.join(assets,'ui/industrial-crt-v1.css'),'utf8');
  const offer=css.match(/\.industrial-ui #prologueOpenButton\{([^}]+)\}/)[1];
  assert.match(offer,/background-color:#101611/);assert.match(offer,/var\(--industrial-crt\)/);
  assert.doesNotMatch(offer,/#8b642e|#67451f|#c59a51/);
});
