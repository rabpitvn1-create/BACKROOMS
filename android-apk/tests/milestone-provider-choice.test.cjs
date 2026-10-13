const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const loading=fs.readFileSync(path.join(assets,'milestone-loading.js'),'utf8');
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');

test('loading has exactly two locally packaged, named provider logo buttons',()=>{
  assert.match(html,/id="milestoneChooseGemini"[^>]*aria-label="Gemini"/);
  assert.match(html,/id="milestoneChooseSol"[^>]*aria-label="GPT SOL 6.1"/);
  for(const file of ['gemini.svg','chatgpt.svg']){
    const logo=fs.readFileSync(path.join(assets,'provider-logos',file),'utf8');
    assert.match(logo,/<svg[^>]*viewBox=/);
    assert.doesNotMatch(logo,/<script|onload=|<foreignObject/i);
  }
});

test('chooser blocks API calls until selected, and never reappears after selection',()=>{
  const elements={};
  for(const id of ['milestoneLoading','milestoneLoadingTitle','milestoneLoadingMessage',
    'milestoneLoadingProgress','milestoneLoadingRetry','milestoneProviderChoices',
    'milestoneChooseGemini','milestoneChooseSol']){
    elements[id]={hidden:true,textContent:'',listeners:{},addEventListener(event,fn){this.listeners[event]=fn;}};
  }
  const window={setInterval:()=>1,clearInterval:()=>{}};
  vm.runInNewContext(loading,{window,document:{getElementById:id=>elements[id]},Date});
  const state={story:{runToken:'run1',checkpointId:'CP-L00-01',hasPlan:false,waiting:false}};
  let selected='',requests=0;
  const hooks={getState:()=>state,provider:()=>selected,selectProvider:value=>(selected=value,{ok:true,provider:value}),
    request:()=>{requests++;},refresh:()=>{state.story.hasPlan=true;},available:()=>true,changed:()=>{}};
  const popup=window.BackroomMilestoneLoading.install(hooks);
  popup.sync();
  assert.equal(requests,0);
  assert.equal(elements.milestoneProviderChoices.hidden,false);
  assert.equal(elements.milestoneLoadingProgress.hidden,true);
  assert.equal(window.__milestoneBlocked,true);
  elements.milestoneChooseGemini.listeners.click();
  assert.equal(selected,'GEMINI');
  assert.equal(requests,1);
  assert.equal(elements.milestoneProviderChoices.hidden,true);
  popup.ready({runToken:'run1',checkpointId:'CP-L00-01'});
  assert.equal(elements.milestoneLoading.hidden,true);
  assert.equal(window.__milestoneBlocked,false);
  state.story={runToken:'run1',checkpointId:'CP-L00-02',hasPlan:false,waiting:false};
  popup.sync();
  assert.equal(requests,2);
  assert.equal(elements.milestoneProviderChoices.hidden,true);
});

test('planner selection is native persistent, exclusive and does not affect Gehihi GM',()=>{
  assert.match(native,/getSharedPreferences\(PLANNER_SETTINGS, MODE_PRIVATE\)/);
  assert.match(native,/putString\(PLANNER_SETTING_KEY, requested\)\.commit\(\)/);
  assert.match(native,/if \(!current\.isEmpty\(\)\)/);
  assert.match(native,/if \(BuildConfig\.TWO_PROVIDER_GAMEPLAY_TEST\) return PLANNER_SOL/);
  assert.match(native,/geminiModelMatrixPolicy\(prompt, new int\[\] \{0, 1, 2\}, -1, 6144, false, 120_000L, false, true\)/);
  assert.match(native,/private String generateText\(String prompt\) throws Exception \{\s*emit\("backroomProvider", "Gehihi"\);/);
  assert.doesNotMatch(native,/verifyMilestoneNarrative\(|auditsForRisk\(/);
});
