const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/item-detail-actions.js'),'utf8');
function boot(itemId='training:stat-point-book'){
 const ids={},requests=[];let renders=0;
 const doc={activeElement:null,getElementById(id){return ids[id]},createElement:element};
 function element(tag='div'){
  const n={tagName:tag,children:[],dataset:{},attributes:{},listeners:{},hidden:false,disabled:false,value:'',textContent:'',
   appendChild(c){this.children.push(c);return c},replaceChildren(...children){this.children=children},
   setAttribute(k,v){this.attributes[k]=String(v)},getAttribute(k){return this.attributes[k]},
   addEventListener(k,f){this.listeners[k]=f},click(){if(!this.disabled)this.listeners.click?.({})},focus(){doc.activeElement=this},scrollIntoView(){},
   querySelectorAll(selector){const found=[];const walk=e=>e.children.forEach(c=>{if(selector==='button'&&c.tagName==='button')found.push(c);walk(c)});walk(this);return found},
   querySelector(selector){if(selector==='.equipment-detail-sheet')return sheet;return this.querySelectorAll(selector)[0]}};
  Object.defineProperty(n,'id',{get(){return this._id},set(value){this._id=value;ids[value]=this}});
  Object.defineProperty(n,'options',{get(){return n.children}});
  Object.defineProperty(n,'innerHTML',{set(value){
   this.html=value;
   for(const m of value.matchAll(/<(\w+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){
    const child=element(m[1]);child.id=m[3];child.hidden=/\bhidden\b/.test(m[2]);child.disabled=/\bdisabled\b/.test(m[2]);ids[child.id]=child;this.children.push(child);
   }
  }});return n;
 }
 const sheet=element();ids.characterInventoryView=element();ids.characterInventoryView.dataset.characterId='cao_minh';
 ids.equipmentDetailModal=element();
 const item={id:itemId,name:'Sách + Stats',quantity:3,equipped:false};
 const owner={id:'cao_minh',name:'Cao Minh',presence:'ACTIVE',stats:Object.fromEntries(['STR','DEX','AGI','VIT','EVA'].map(k=>[k,{base:k==='VIT'?999:5}])),inventory:[item]};
 const scope={document:doc,state:{partyDetails:{members:[owner,{id:'iris',name:'Iris',presence:'ACTIVE'}]}},busy:false,syncPrimaryActions(){},
  Android:{itemAction(state,request){requests.push(JSON.parse(request))}},backroomTurn(json){renders++;scope.state=JSON.parse(json)}};scope.window=scope;
 vm.runInNewContext(source,scope);
 ids.characterInventoryView.listeners.click({target:{closest(){return {getAttribute(){return itemId}}}}});
 return {ids,scope,item,owner,requests,renders(){return renders},choose(stat){ids.statBookOptions.children.find(e=>e.dataset.stat===stat).click()}};
}

test('Use opens a stat picker without spending a book, cancel sends nothing',()=>{
 const r=boot();assert.equal(r.ids.statBookPicker.hidden,true);r.ids.itemActionUse.click();
 assert.equal(r.ids.statBookPicker.hidden,false);assert.equal(r.ids.statBookOptions.children.length,5);
 assert.equal(r.ids.statBookConfirm.disabled,true);assert.equal(r.requests.length,0);
 r.ids.statBookCancel.click();assert.equal(r.ids.statBookPicker.hidden,true);assert.equal(r.requests.length,0);assert.equal(r.item.quantity,3);
});

test('confirm sends exactly one book with the explicit chosen stat and prevents double submission',()=>{
 const r=boot();r.ids.itemActionQuantity.value='3';r.ids.itemActionUse.click();
 r.choose('STR');assert.equal(r.ids.statBookConfirm.disabled,false);r.ids.statBookConfirm.click();r.ids.statBookConfirm.click();
 assert.equal(r.requests.length,1);assert.equal(r.requests[0].quantity,1);assert.equal(r.requests[0].targetStat,'STR');assert.equal(r.requests[0].actorId,'cao_minh');
 assert.equal(r.scope.busy,true);
 r.scope.backroomItemAction(JSON.stringify({handled:true,state:r.scope.state}));
 assert.equal(r.ids.equipmentDetailModal.hidden,true);assert.equal(r.ids.statBookPicker.hidden,true);assert.equal(r.renders(),1);
});

test('capped stats cannot be selected and failed Core use keeps the choice retryable',()=>{
 const r=boot();r.ids.itemActionUse.click();const capped=r.ids.statBookOptions.children.find(e=>e.dataset.stat==='VIT');
 assert.equal(capped.disabled,true);capped.click();assert.equal(r.ids.statBookConfirm.disabled,true);
 r.choose('DEX');r.ids.statBookConfirm.click();r.scope.backroomItemAction('{"handled":false,"reply":"Sách không bị tiêu hao"}');
 assert.equal(r.scope.busy,false);assert.equal(r.ids.statBookPicker.hidden,false);assert.equal(r.item.quantity,3);
 assert.equal(capped.disabled,true);assert.equal(r.ids.statBookOptions.children.find(e=>e.dataset.stat==='STR').disabled,false);
 assert.match(r.ids.itemActionMessage.textContent,/không bị tiêu hao/);
});

test('ordinary consumables retain their existing quantity and never send a stat selection',()=>{
 const r=boot('almond-water');r.ids.itemActionQuantity.value='2';r.ids.itemActionUse.click();
 assert.equal(r.ids.statBookPicker.hidden,true);assert.equal(r.requests.length,1);assert.equal(r.requests[0].quantity,2);assert.equal('targetStat' in r.requests[0],false);
});
