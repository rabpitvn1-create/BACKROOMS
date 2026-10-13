const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/milestone-loading.js'),'utf8');
function boot(story){
  let state={story},requests=0,next;
  const elements={};
  for(const id of ['milestoneLoading','milestoneLoadingMessage','milestoneLoadingProgress','milestoneLoadingRetry'])
    elements[id]={hidden:true,textContent:'',addEventListener(_,fn){this.click=fn}};
  const env={document:{getElementById:id=>elements[id]}};env.window=env;
  vm.runInNewContext(source,env);
  const api=env.BackroomMilestoneLoading.install({getState:()=>state,available:()=>true,
    request:()=>requests++,changed:()=>{},refresh:()=>{state={story:next};api.sync()}});
  return {api,env,elements,get requests(){return requests},set(story){state={story};api.sync()},next(story){next=story}};
}
const initial={runToken:'run1',checkpointId:'CP1',hasPlan:false,waiting:false};
test('new game blocks actions and eagerly loads once; ready unblocks without submitting a turn',()=>{
  const x=boot(initial);x.api.sync();x.api.sync();
  assert.equal(x.requests,1);assert.equal(x.env.__milestoneBlocked,true);
  assert.equal(x.elements.milestoneLoading.hidden,false);
  assert.equal(x.elements.milestoneLoadingProgress.hidden,false);
  x.next({...initial,hasPlan:true});x.api.ready(initial);
  assert.equal(x.env.__milestoneBlocked,false);assert.equal(x.requests,1);
});
test('victory waits for next plan; handoff closes popup',()=>{
  const x=boot({...initial,hasPlan:true});x.api.sync();assert.equal(x.requests,0);
  x.set({...initial,hasPlan:true,waiting:true});assert.equal(x.requests,1);
  assert.match(x.elements.milestoneLoadingMessage.textContent,/thắng Boss/);
  x.next({...initial,checkpointId:'CP2',hasPlan:true});x.api.ready(initial);
  assert.equal(x.elements.milestoneLoading.hidden,true);
});
test('planner failure stays visible and retry does not duplicate in-flight requests',()=>{
  const x=boot(initial);x.api.sync();
  x.api.status({...initial,status:'error',message:'Nạp thất bại'});
  assert.equal(x.elements.milestoneLoadingRetry.hidden,false);
  assert.equal(x.elements.milestoneLoadingProgress.hidden,true);
  assert.equal(x.elements.milestoneLoadingMessage.textContent,'Nạp thất bại');
  x.api.sync();assert.equal(x.requests,1);
  x.elements.milestoneLoadingRetry.click();x.api.sync();assert.equal(x.requests,2);
  assert.equal(x.elements.milestoneLoadingRetry.hidden,true);
});
test('late callbacks from previous run or checkpoint cannot dismiss new loading',()=>{
  const x=boot(initial);x.api.sync();x.set({...initial,runToken:'run2'});
  x.api.ready(initial);x.api.status({...initial,status:'error',message:'old error'});
  assert.equal(x.env.__milestoneBlocked,true);assert.equal(x.elements.milestoneLoadingRetry.hidden,true);
  assert.equal(x.requests,2);
});

test('ready callback must not unlock gameplay without a Core-confirmed plan',()=>{
  const x=boot(initial);x.api.sync();
  x.next({...initial,hasPlan:false});x.api.ready(initial);
  assert.equal(x.env.__milestoneBlocked,true);
  assert.equal(x.elements.milestoneLoadingRetry.hidden,false);
  assert.match(x.elements.milestoneLoadingMessage.textContent,/Game Core chưa xác nhận/);
});
