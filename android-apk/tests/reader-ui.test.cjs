const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
function runtime(reader={requestId:'PAGE:2'}){
  function element(){return {children:[],disabled:false,hidden:false,value:'',className:'',textContent:'',setAttribute(){},addEventListener(type,fn){this[type]=fn},appendChild(node){this.children.push(node)},remove(){}}}
  const last=element(),log=element(),elements={readerSettings:element(),readerTone:element(),readerDifficulty:element(),readerConfigure:element(),nextPage:element()};
  log.querySelectorAll=selector=>selector==='.message.gm'?[last]:selector==='.reader-options button'?last.children.flatMap(x=>x.children):[];
  const calls=[],ctx={state:{reader},busy:false,coreUnavailable:false,nextPageEl:elements.nextPage,logEl:log,statusEl:{},
    byId:id=>elements[id],document:{createElement:element},Android:{nextReaderPage:(state,request)=>calls.push(JSON.parse(request)),configureReaderLevel:(...args)=>calls.push(args)},backroomError:()=>{ctx.busy=false}};
  ctx.window=ctx;
  const code=html.slice(html.indexOf('function readerBlocked()'),html.indexOf('window.backroomTurn=json=>'));
  vm.runInNewContext(code,ctx);return {ctx,calls,last,elements};
}
test('Explorer has one next-page control and no player input or chat role rendering',()=>{
  assert.doesNotMatch(html,/<textarea|id="action"|id="form"|player-composer\.(js|css)/);
  assert.match(html,/id="nextPage">Trang tiếp/);
  assert.match(html,/filter\(x=>x.role!=="player"\)/);
});
test('double tap sends one stable Core token and no player action',()=>{
  const r=runtime();r.ctx.requestReaderPage();r.ctx.requestReaderPage();assert.equal(r.calls.length,1);assert.deepEqual(r.calls[0],{requestId:'PAGE:2'});
});
test('unresolved gate blocks next page, renders choices in the story, and submits one exact option',()=>{
  const r=runtime({requestId:'PAGE:4',gate:{id:'GATE',options:[{id:'A',label:'Điều tra'},{id:'B',label:'Tránh xa'}]}});
  r.ctx.renderReaderControls();assert.equal(r.elements.nextPage.disabled,true);assert.equal(r.last.children[0].children.length,2);
  r.ctx.requestReaderPage();assert.equal(r.calls.length,0);r.last.children[0].children[1].click();r.last.children[0].children[0].click();
  assert.equal(r.calls.length,1);assert.deepEqual(r.calls[0],{requestId:'PAGE:4',gateId:'GATE',optionId:'B'});
});
test('reload after provider error retries the reserved branch while its options stay locked',()=>{
  const request={requestId:'PAGE:4',gateId:'GATE',optionId:'B'};
  const r=runtime({requestId:'PAGE:4',pendingPage:true,retryRequest:request,gate:{id:'GATE',options:[{id:'A',label:'A'},{id:'B',label:'B'}]}});
  r.ctx.renderReaderControls();assert.equal(r.last.children[0].children[0].disabled,true);assert.equal(r.elements.nextPage.disabled,false);
  r.ctx.requestReaderPage();assert.deepEqual(r.calls[0],request);
});
test('Combat and confirmed encounters block the reader; a reserved narration can recover',()=>{
  for(const field of [{combat:{active:true}},{pendingEncounter:{encounterId:'E'}}]){
    const r=runtime();Object.assign(r.ctx.state,field);r.ctx.requestReaderPage();assert.equal(r.calls.length,0);
  }
  const r=runtime({requestId:'PAGE:4',pendingPage:true,retryRequest:{requestId:'PAGE:4'}});
  r.ctx.state.pendingEncounter={encounterId:'E'};r.ctx.requestReaderPage();assert.equal(r.calls.length,1);
});
test('settings block reading and submit tone/difficulty independently',()=>{
  const r=runtime({requestId:'PAGE:1',settingsRequired:true,tone:'BRIGHT',difficulty:'HARD'});
  r.ctx.renderReaderControls();assert.equal(r.elements.readerSettings.hidden,false);r.ctx.requestReaderPage();assert.equal(r.calls.length,0);
  r.elements.readerConfigure.click();assert.equal(r.calls[0][1],'BRIGHT');assert.equal(r.calls[0][2],'HARD');
});
