import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const root=new URL('./',import.meta.url);
const java=fs.readFileSync(new URL('app/src/main/java/com/rabpit/backroom/MainActivity.java',root),'utf8');
const lines=java.split('\n');
const start=lines.findIndex(line=>line.includes('LUCIA_COMBAT_OVERLAY_R01'));
assert(start>=0,'Lucia runtime is packaged directly in MainActivity');
const js=lines.slice(start,start+4).map(line=>JSON.parse(line.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/)[1])).join('');
const cao={className:'snapshot-character'},entity={className:'snapshot-entity'};
let children=[];
const box={querySelector(selector){return children.find(x=>'.'+x.className===selector)},appendChild(x){children.push(x)}};
const context={state:{},document:{getElementById(id){return id==='snapshot'?box:null},createElement(){const node={style:{},remove(){children=children.filter(x=>x!==node)}};return node}},renderSnapshot(){children=[cao,entity]}};
vm.createContext(context);vm.runInContext(js,context);
const cases=[
 [{combat:{active:false},partyDetails:{members:[{id:'lucia',presence:'ACTIVE'}]}},false],
 [{combat:{active:true},partyDetails:{members:[{id:'lucia',presence:'ACTIVE'}]}},true],
 [{combat:{active:true},partyDetails:{members:[{id:'lucia',presence:'DEAD'}]}},false],
 [{combat:{active:true},partyDetails:{members:[]},party:['lucia']},false],
 [{combat:{active:true},party:['lucia']},true],
 [{combat:{active:true},party:[]},false]
];
for(const [state,expected] of cases){context.state=state;context.renderSnapshot();context.appendLuciaCombatOverlay();const overlays=children.filter(x=>x.className==='snapshot-lucia');assert.equal(overlays.length,expected?1:0);assert(children.includes(cao)&&children.includes(entity));if(expected){assert.equal(overlays[0].src,'file:///android_asset/LUCIA_LUC_OVERLAY.png');assert.equal(overlays[0].style.pointerEvents,'none')}}
const png=fs.readFileSync(new URL('app/src/main/assets/LUCIA_LUC_OVERLAY.png',root));
assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
console.log('Lucia overlay: combat and Party eligibility, duplicate removal, existing overlays and PNG verified.');
