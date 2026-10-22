const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const script=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(x=>x[1]).find(x=>x.includes("const modal=document.getElementById('characterSkillsModal')"));
function setup(){
 const nodes={};
 function node(id){return nodes[id]||(nodes[id]={id,dataset:{},hidden:true,innerHTML:'',listeners:{},addEventListener(e,fn){this.listeners[e]=fn},setAttribute(){},appendChild(n){nodes[n.id]=n},closest(){return null}})}
 ['characterInventoryView','characterSkillsModal','characterSkillsClose','characterSkillsTitle','characterSkillsList','characterStatusList'].forEach(node);
 nodes.characterInventoryView.dataset.characterId='iris';
 const member={id:'iris',name:'Iris',skills:[{name:'Passive',kind:'PASSIVE',locked:false,unlockLevel:0},{name:'First',kind:'AUTO',locked:false,unlockLevel:0},{name:'Next',kind:'AUTO',locked:true,unlockLevel:10},{name:'Ultimate',kind:'ULTIMATE',locked:true,unlockLevel:50}]};
 const context={state:{partyDetails:{members:[member]}},document:{getElementById:id=>nodes[id]||null,createElement:()=>node('new')},window:{renderCharacterStatusEquipment(){}},MutationObserver:class{observe(){}}};
 vm.runInNewContext(script,context);
 return {nodes,member,context,open(){nodes.characterSkillsButton.listeners.click();return nodes.characterSkillsList.innerHTML}};
}
test('Locked cards show a lock, level requirement and disabled state; passive and first skill stay open',()=>{
 const app=setup();const output=app.open();
 assert.equal((output.match(/is-locked/g)||[]).length,2);
 assert.equal((output.match(/character-skill-lock-icon/g)||[]).length,2);
 assert.match(output,/Đang khóa · Mở ở cấp 10/);assert.match(output,/Đang khóa · Mở ở cấp 50/);
 assert.match(output,/Nội tại · luôn mở/);assert.match(output,/Đã mở từ đầu/);
 assert.match(output,/aria-disabled="true"/);
 assert.match(html,/is-locked \.character-skill-content\{opacity:\.42;filter:grayscale\(1\)/);
 app.nodes.characterSkillsClose.listeners.click();assert.equal(app.nodes.characterSkillsModal.hidden,true);
});
test('Open sheet refreshes from Core projection at level-up without choosing or spending anything',()=>{
 const app=setup();app.open();app.member.skills[2].locked=false;
 app.context.window.renderCharacterStatusEquipment(app.member);
 const output=app.nodes.characterSkillsList.innerHTML;
 assert.equal((output.match(/is-locked/g)||[]).length,1);assert.match(output,/Đã mở · cấp 10/);
});
test('Missing unlock fields fail closed and labels escape markup',()=>{
 const app=setup();app.member.skills=[{name:'<img>',kind:'AUTO'},{name:'Passive',kind:'PASSIVE',locked:true}];
 const output=app.open();assert.equal((output.match(/is-locked/g)||[]).length,1);
 assert.match(output,/&lt;img&gt;/);assert.doesNotMatch(output,/<img>/);assert.match(output,/Chưa có điều kiện mở khóa/);
});
