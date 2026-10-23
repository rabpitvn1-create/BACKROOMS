const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const main=path.join(__dirname,'../app/src/main');
const java=fs.readFileSync(path.join(main,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const lines=java.split('\n');
const start=lines.findIndex(line=>line.includes('/* LUC_TRAM_FOLLOWER_OVERLAY_R01 */'));
assert.ok(start>=0);
const decode=line=>JSON.parse(line.trim().replace(/\s*\+\s*$/,''));
const renderer=lines.slice(start,start+4).map(decode).join('\n');
const layout=decode(lines.find(line=>line.includes('/* SNAPSHOT_PARTY_LAYOUT_R05 */')));

function boot(state={}){
  const hero={className:'snapshot-character',style:{}},children=[hero];
  const box={dataset:{},querySelector(selector){return children.find(c=>'.'+c.className===selector)||null;},
    querySelectorAll(selector){return children.filter(c=>selector.split(',').includes('.'+c.className));},
    appendChild(child){children.push(child);child.remove=()=>children.splice(children.indexOf(child),1);}};
  const ctx={state,document:{getElementById(){return box;},createElement(){return {style:{}};}},
    renderSnapshot(){ctx.baseCalls=(ctx.baseCalls||0)+1;}};
  ctx.window=ctx;
  vm.runInNewContext(renderer+'\n'+layout,ctx);
  return {ctx,box,hero,children};
}

test('Lục Trầm overlay requires Core presence and replaces stale sprites',()=>{
  const x=boot({encounteredCharacters:[{id:'luc_tram',present:false}]});
  x.ctx.renderSnapshot();
  assert.equal(x.children.length,1,'history alone must not create an NPC');
  x.ctx.state.encounteredCharacters[0].present=true;
  x.ctx.renderSnapshot();x.ctx.renderSnapshot();
  assert.equal(x.children.length,2,'rerender must not duplicate overlays');
  assert.equal(x.children[1].src,'file:///android_asset/LucTram.webp');
  assert.equal(x.children[1].alt,'Lục Trầm');
  x.ctx.state.encounteredCharacters[0].present=false;
  x.ctx.renderSnapshot();
  assert.equal(x.children.length,1,'leaving the scene must remove the old overlay');
});

test('Active Party Lục Trầm appears, but dead or separated members and hostile Entity do not',()=>{
  const member={id:'luc_tram',presence:'ACTIVE',currentHp:100};
  const x=boot({partyDetails:{members:[member]},encounteredCharacters:[{id:'luc_tram',present:true}]});
  x.ctx.renderSnapshot();assert.equal(x.children.length,2);
  for(const presence of ['DEAD','SEPARATED']){
    member.presence=presence;x.ctx.renderSnapshot();assert.equal(x.children.length,1);
  }
  member.presence='ACTIVE';member.currentHp=0;
  x.ctx.renderSnapshot();assert.equal(x.children.length,1);
  member.currentHp=100;x.ctx.state.pendingEncounter={entityKey:'luc_tram_hac_hoa'};
  x.ctx.renderSnapshot();assert.equal(x.children.length,1);
  x.ctx.state.pendingEncounter=null;x.ctx.state.combat={active:true,entityKey:'hound'};
  x.ctx.renderSnapshot();assert.equal(x.children.length,1,'combat owns the Party portrait slot');
  x.ctx.state.combat.active=false;x.ctx.renderSnapshot();assert.equal(x.children.length,2);
});

test('Lục Trầm dialogue uses the current two-person camera and restores exploration',()=>{
  const x=boot({encounteredCharacters:[{id:'luc_tram',present:true}],
    specialDialogueProgress:{pending:{id:'luc_tram',kind:'intro'}}});
  x.ctx.renderSnapshot();
  assert.equal(x.box.dataset.partyLayout,'encounter');
  assert.equal(x.hero.style.left,'6%');
  assert.equal(x.children[1].style.left,'50%');
  x.ctx.state.specialDialogueProgress.pending=null;
  x.ctx.renderSnapshot();
  assert.equal(x.box.dataset.partyLayout,'exploration');
  assert.equal(x.hero.style.right,'0');
  assert.equal(x.children[1].style.left,'3%');
});
