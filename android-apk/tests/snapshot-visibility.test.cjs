const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/snapshot-ui.js'),'utf8');
const geometry=require('../app/src/main/assets/snapshot-ui.js');
function boot({unknown=false,unloaded=false,canvasContextMissing=false,stateOverride=null}={}){
 const elements=[],styles=[],pending=[],warnings=[];let reads=0;
 const box={clientWidth:350,clientHeight:250,appendChild(el){el.parentElement=this;elements.push(el);},querySelectorAll(selector){return elements.filter(e=>selector.includes('img.')&&e.tagName==='IMG'&&e.className.includes('snapshot-grounded'));}};
 Object.defineProperty(box,'textContent',{set(){elements.length=0;}});
 const document={readyState:'complete',head:{appendChild(el){styles.push(el.textContent);}},getElementById(id){return id==='snapshot'?box:null;},querySelectorAll(){return [];},createElement(tag){
  if(tag==='canvas')return {getContext(){reads++;if(canvasContextMissing)return null;return {drawImage(){},getImageData(){throw new Error('SecurityError: canvas has been tainted by cross-origin data');}};}};
  return {tagName:tag.toUpperCase(),style:{},dataset:{},className:'',complete:!unloaded,naturalWidth:0,naturalHeight:0,setAttribute(){},addEventListener(type,cb){if(type==='load')pending.push({el:this,cb});},set src(url){this.url=url;const m=geometry.assetMetric(url)||geometry.assetMetric('cao_minh_snapshot_overlay.png');this.naturalWidth=m.width;this.naturalHeight=m.height;},get src(){return this.url;}};
 }};
 const ctx={document,console:{warn(...args){warnings.push(args);}},localStorage:{removeItem(){}},setTimeout,clearTimeout,Image:function(){throw Error('Detached image preload must not gate overlays');},state:stateOverride||{flags:{},combat:{active:true,participants:[{id:'cao_minh'},{id:'luc_tram'}]}}};
 ctx.window=ctx;ctx.addEventListener=()=>{};
 vm.createContext(ctx);
 let js=source;if(unknown)js=js.replaceAll('file:///android_asset/cao_minh_snapshot_overlay.png','file:///android_asset/unregistered.png');
 vm.runInContext(js,ctx);
 return {ctx,box,elements,styles,pending,warnings,reads:()=>reads};
}
function visible(img){assert.notEqual(img.style.visibility,'hidden');assert.ok(parseFloat(img.style.width)>0);assert.ok(parseFloat(img.style.height)>0);}
test('bundled standing/combat/Entity render without any readable canvas or detached preloads',()=>{
 const r=boot();visible(r.elements.find(e=>e.className.includes('snapshot-character')));
 for(const actor of [0,1]){r.ctx.backroomSetCombatVisualActor(actor,'deathmoth');r.elements.filter(e=>e.className.includes('snapshot-grounded')).forEach(visible);}
 assert.equal(r.reads(),0);assert.ok(!r.styles.join('').includes('visibility:hidden'));
});
test('same combat actor and Entity keep the existing snapshot DOM across dice updates',()=>{
 const r=boot();
 r.ctx.backroomSetCombatVisualActor(0,'deathmoth');
 const firstActor=r.elements.find(e=>e.className.includes('snapshot-combat-character'));
 const firstEntity=r.elements.find(e=>e.className.includes('snapshot-entity'));
 assert.ok(firstActor);assert.ok(firstEntity);
 r.ctx.backroomSetCombatVisualActor(0,'deathmoth');
 assert.equal(r.elements.find(e=>e.className.includes('snapshot-combat-character')),firstActor);
 assert.equal(r.elements.find(e=>e.className.includes('snapshot-entity')),firstEntity);
 r.ctx.backroomSetCombatVisualActor(1,'deathmoth');
 assert.notEqual(r.elements.find(e=>e.className.includes('snapshot-combat-character')),firstActor);
});

test('new humanoid Entities and Tâm Ma share one visual scale while Cao Minh geometry is untouched',()=>{
 const family=geometry.envelope(geometry.characterMetrics());
 const keys=['tam_ma_cao_minh','async_member_rifle_aim_right_01','the_lifeform_bacteria_01','the_lifeform_bacteria_02','the_lifeform_bacteria_03'];
 const heights=keys.map(key=>{const m=geometry.assetMetric('file:///android_asset/entity/'+key+'.webp');return geometry.layout(m,350,250,'left','entity',family,geometry.entityPolicy(key)).bodyHeight;});
 heights.forEach(h=>assert.ok(Math.abs(h-heights[0])<1e-7));
 const cao=geometry.layout(geometry.assetMetric('file:///android_asset/cao_minh_entity_overlay.png'),350,250,'right','character',family);
 assert.ok(heights[0]>cao.bodyHeight);
 const css=boot().styles.join('');assert.match(css,/snapshot>img\.snapshot-character\{z-index:4/);assert.match(css,/snapshot>img\.snapshot-entity\{[^}]*z-index:3/);
});

test('combat actor changes use rotation transitions instead of snapshot blinking',()=>{assert.match(source,/function rotateCombatActor\(/);assert.match(source,/@keyframes combat-turn-out/);assert.match(source,/@keyframes combat-turn-in/);});
test('Entity victory exposes deterministic glass-shatter fragments',()=>{assert.match(source,/window\.backroomShatterEntity=function/);assert.match(source,/combat-shard-break/);const b=source.slice(source.indexOf('window.backroomShatterEntity=function'),source.indexOf('window.backroomPlayCombatFeedback=function'));assert.match(b,/var clips=\[/);assert.doesNotMatch(b,/Math\.random/);});
test('unknown sprite remains visible when canvas throws SecurityError',()=>{
 const r=boot({unknown:true});visible(r.elements.find(e=>e.className.includes('snapshot-character')));assert.equal(r.reads(),1);
});
test('unknown sprite remains visible when canvas context is unavailable',()=>{
 const r=boot({unknown:true,canvasContextMissing:true});visible(r.elements.find(e=>e.className.includes('snapshot-character')));
});
test('late image load aligns independently, without waiting for other characters',()=>{
 const r=boot({unloaded:true});assert.ok(r.pending.length>0);
 for(const {el,cb} of r.pending){el.complete=true;cb();visible(el);}
 assert.equal(r.reads(),0);
});
test('generated bounds exist for all registered overlays',()=>{
 const assets=path.join(__dirname,'../app/src/main/assets');
 const names=['cao_minh_snapshot_overlay.png','cao_minh_entity_overlay.png','lucia_overlay.png','luctram_overlay.png',...fs.readdirSync(path.join(assets,'entity')).filter(n=>n.endsWith('.webp')).map(n=>'entity/'+n)];
 for(const name of names){const m=geometry.assetMetric('file:///android_asset/'+name),data=fs.readFileSync(path.join(assets,name));assert.ok(m,name);assert.ok(data.length>0,name);if(name.endsWith('.png')){assert.equal(m.width,data.readUInt32BE(16),name+' width');assert.equal(m.height,data.readUInt32BE(20),name+' height');}else{assert.ok(m.width>0&&m.height>0,name);}assert.ok(m.paint.right<=m.width&&m.body.bottom<=m.height);}
});


test('new active Entity sprites resolve through the snapshot allowlist',()=>{
 for(const key of ['async_member_rifle_aim_right_01','the_lifeform_bacteria_01','the_lifeform_bacteria_02','the_lifeform_bacteria_03']){
  const r=boot({stateOverride:{flags:{entityEncounterKey:key},combat:{active:false}}});
  const img=r.elements.find(e=>e.className.includes('snapshot-entity'));
  assert.ok(img,key);
  assert.equal(img.url,'file:///android_asset/entity/'+key+'.webp');
  visible(img);
 }
});

test('snapshot has no combat status icon HUD and uses smaller HP damage text',()=>{
 assert.doesNotMatch(source,/combat-status-hud/);
 assert.doesNotMatch(source,/__combatStatusIcons/);
 assert.doesNotMatch(source,/android_asset\/status\/status_/);
 assert.match(source,/\.combat-float\{[^}]*font-size:19px/);
});

test('combat floating overlay only accepts numeric HP damage',()=>{
 assert.match(source,/if\(!\/\^-\\d\+ HP\$\/i\.test\(text\)\)return;/);
 assert.doesNotMatch(source,/floater\.textContent=.*PROC/);
});


test('combat floating damage stays readable long enough on mobile',()=>{
 assert.match(source,/animation:combat-float-up 1\.6s ease-out forwards/);
 assert.match(source,/12%\{opacity:1\}80%\{opacity:1\}100%\{opacity:0/);
 assert.match(source,/setTimeout\(function\(\)\{floater\.remove\(\);\},1800\)/);
});


test('simultaneous floating damage uses separate vertical lanes instead of one anchor',()=>{
 assert.match(source,/querySelectorAll\('\.combat-float\[data-target="'\+target\+'"\]'\)\.length/);
 assert.match(source,/floater\.dataset\.target=target/);
 assert.match(source,/floater\.style\.top=\(anchor\.y-lane\*26\)\+'px'/);
});




test('snapshot runtime has no scripted NPC overlay hooks',()=>{
 assert.doesNotMatch(source,/snapshot-npc|storyPrimaryNpc|__backroomStoryVisuals/);
});
