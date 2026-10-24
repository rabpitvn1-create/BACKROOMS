const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.join(__dirname, '../app/src/main/assets');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(x => x[1]);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c =>
  ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));

function fixture() {
  const nodes = new Map();
  function element() {
    const node = {dataset:{}, hidden:false, innerHTML:'', textContent:'', listeners:{}, options:[],
      setAttribute(k,v) {this[k] = v;}, getAttribute(k) {return this[k];},
      addEventListener(k,fn) {const prior=this.listeners[k];this.listeners[k]=prior?event=>{prior(event);fn(event);}:fn;},
      appendChild(child) {
        if (child.value) {this.options.push(child); if(!this.value)this.value=child.value;}
        for(const match of (child.innerHTML||'').matchAll(/\bid="([^"]+)"/g)) {
          const nested=element();nested.id=match[1];nested.value='';
        }
      },
      replaceChildren() {this.options=[];this.value='';},
      querySelector(selector) {return selector==='.equipment-detail-sheet'?element():null;},
      insertAdjacentHTML(_, text) {this.innerHTML += text;}, insertAdjacentElement() {},
      querySelectorAll(selector) {
        if (selector !== '.party-member[data-character]') return [];
        this.cards = [...this.innerHTML.matchAll(/data-character="([^"]+)"/g)].map(match => {
          const card = element(); card['data-character'] = match[1]; return card;
        });
        return this.cards;
      }
    };
    Object.defineProperty(node, 'id', {get() {return this._id;}, set(v) {this._id = v; nodes.set(v,this);}});
    return node;
  }
  for (const id of [...html.matchAll(/\bid="([^"]+)"/g)].map(x => x[1])) {
    const node = element(); node.id = id;
  }
  nodes.get('characterInventoryView').hidden = true;
  nodes.get('equipmentDetailModal').hidden = true;
  const item = (id,name,slot) => ({id,name,slot,quantity:1,equipped:true,classification:'CANONICAL'});
  const gear = [item('cao-minh:huyet-ma-kiem','Huyết Ma Kiếm','weapon'),
    item('cao-minh:huyet-ma-chien-khai','Huyết Ma Chiến Khải','armor'),
    item('cao-minh:huyet-ma-gauntlets','Huyết Ma Gauntlets','gauntlets'),
    item('cao-minh:huyet-ma-boots','Huyết Ma Boots','greaves'),
    item('cao-minh:van-kho-gioi-chi','Vạn Khố Giới Chỉ','ring')];
  const member = {id:'cao-minh',name:'Cao Minh',presence:'ACTIVE',isLeader:true,
    currentHp:61,maxHp:70,energy:'∞',hpRegen:7,condition:'HEALTHY',
    stats:Object.fromEntries(['STR','DEF','SKL','VIT'].map((k,i) =>
      [k,{base:5,equipment:i,passive:4,effective:9+i}])),
    combatStatus:{criticalChancePercent:0,criticalDamagePercent:150,evasionPercent:12,
      criticalResistancePercent:13,evasionResistancePercent:14},
    passives:['Đại Đạo Ma Tôn','Đại Ma Đầu'],statuses:[],injuries:['Vết cắt'],
    physiology:{hunger:'NORMAL',thirst:'MILD',sleepDeprivation:'NORMAL'},
    inventory:[],inventoryCapacity:{used:0,max:9},equipmentItems:gear,
    equipment:Object.fromEntries(gear.map(x => [x.slot,x.id]))};
  const companion = {...member,id:'luc-tram',name:'Lục Trầm',isLeader:false,
    currentHp:17,equipment:{weapon:'luc-tram:tich-quang'},
    equipmentItems:[item('luc-tram:tich-quang','Tịch Quang','weapon')]};
  const context = {state:{turn:3,log:[],inventory:[],partyDetails:{members:[member,companion]}},
    initial:{log:[]},esc:escape,chips:()=>'',localStorage:{setItem(){}},render(){},
    document:{getElementById:id=>nodes.get(id),createElement:element,querySelectorAll:()=>[]}};
  context.window = context;
  context.backroomLuciaVisuals = {withAvatar:x=>x,renderOverlay(){}};
  context.backroomNamedFollowers = {withAvatar:x=>x,renderOverlay(){}};
  vm.createContext(context);
  vm.runInContext(scripts.find(x => x.includes("const party=document.getElementById('party')")),context);
  vm.runInContext(scripts.find(x => x.includes('window.renderCharacterStatusEquipment=render')),context);
  vm.runInContext(fs.readFileSync(path.join(assets,'cloudinary-gameplay.js'),'utf8'),context);
  return {context,nodes,member,enableItemActions() {
    const calls=[];context.busy=false;context.syncPrimaryActions=()=>{};
    context.Android={itemAction:(raw,request)=>calls.push(JSON.parse(request))};
    vm.runInContext(fs.readFileSync(path.join(assets,'item-detail-actions.js'),'utf8'),context);
    return calls;
  },open(id) {
    nodes.get('party').cards.find(x=>x['data-character']===id).listeners.click();
  },item(id) {
    const card = {getAttribute:()=>id};
    nodes.get('characterInventoryView').listeners.click({target:{closest:()=>card}});
  }};
}

test('first Party tap shows canonical stats, all combat rates, equipment names and icons', () => {
  const r=fixture(); const before=JSON.stringify(r.context.state.partyDetails);
  r.open('cao-minh');
  const status=r.nodes.get('characterStatusList').innerHTML;
  for (const key of ['HP','STR','DEF','SKL','VIT','CRIT %','CRIT DMG %','EVASION %','CRIT RES %','EVASION RES %'])
    assert.ok(status.includes('<b>'+key+'</b>'),key);
  assert.match(status,/61 \/ 70/); assert.match(status,/>0%<\/strong>/);
  assert.match(status,/Vết cắt/); assert.match(status,/Đói/); assert.match(status,/Thiếu ngủ/);
  assert.doesNotMatch(status,/<b>(DF|AGI|CRIT)<\/b>/);
  const equipment=r.nodes.get('characterEquipmentList').innerHTML;
  assert.equal((equipment.match(/class="equipment-card"/g)||[]).length,5);
  assert.equal((equipment.match(/<img src="https:\/\/res.cloudinary.com/g)||[]).length,4);
  assert.match(equipment,/<strong>Huyết Ma Kiếm<\/strong>/);
  assert.match(equipment,/<svg[^>]+aria-label="Nhẫn"/);
  assert.equal(JSON.stringify(r.context.state.partyDetails),before,'rendering cannot alter Core data');
});

test('switching a Party member and rerendering never restores raw equipment IDs', () => {
  const r=fixture();r.open('cao-minh');r.open('luc-tram');r.context.render();
  assert.match(r.nodes.get('characterStatusList').innerHTML,/17 \/ 70/);
  const equipment=r.nodes.get('characterEquipmentList').innerHTML;
  assert.match(equipment,/<strong>Tịch Quang<\/strong>/);
  assert.match(equipment,/Item_Tich_Quang.webp/);
  assert.doesNotMatch(equipment,/Huyết Ma Kiếm|<span>weapon:/);
});

test('equipment detail opens with the same resolved artwork on its first click', () => {
  const r=fixture();r.open('cao-minh');r.item('cao-minh:huyet-ma-kiem');
  assert.equal(r.nodes.get('equipmentDetailModal').hidden,false);
  assert.equal(r.nodes.get('equipmentDetailName').textContent,'Huyết Ma Kiếm');
  assert.match(r.nodes.get('equipmentDetailIcon').innerHTML,/Item_Huyet_Ma_Kiem.webp/);
});

test('item controls reach the typed bridge for equipped gear and show native rejection', () => {
  const r=fixture();const calls=r.enableItemActions();r.open('cao-minh');r.item('cao-minh:huyet-ma-kiem');
  const equip=r.nodes.get('itemActionEquip');
  assert.equal(equip.hidden,false);assert.equal(equip.textContent,'Tháo trang bị');
  equip.listeners.click();assert.equal(calls.length,1);assert.equal(calls[0].operation,'UNEQUIP');
  assert.equal(calls[0].actorId,'cao-minh');assert.equal(calls[0].itemId,'cao-minh:huyet-ma-kiem');
  r.context.backroomItemAction(JSON.stringify({handled:false,error:'combat_active',reply:'Không thể thao tác trong combat.'}));
  assert.equal(r.context.busy,false);assert.match(r.nodes.get('itemActionMessage').textContent,/combat/);
  assert.equal(r.nodes.get('equipmentDetailModal').hidden,false);
  assert.ok(html.includes('src="item-detail-actions.js"'),'the actual APK page must load the item bridge');
});
