const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');
const root = path.join(__dirname, '..');

function fixture() {
  const box = {
    children: [], style: {},
    querySelector(selector) {
      if(selector === '.snapshot-lucia')return this.children.find(x => x.className === 'snapshot-lucia');
      const id = selector.match(/data-named-follower="([^"]+)"/);
      return id && this.children.find(x => x.attributes['data-named-follower'] === id[1]);
    },
    querySelectorAll() {return this.children.filter(x => ['snapshot-lucia','snapshot-named-follower'].includes(x.className));},
    appendChild(node) {this.children.push(node);},
    set textContent(_) {this.children = [];}
  };
  const context = {window: {}, localStorage: {getItem(){return null;}}, document: {
    getElementById(id) {return id === 'snapshot' ? box : null;},
    createElement() {
      const node = {style: {}, attributes: {}, setAttribute(k,v){this.attributes[k]=v;}};
      node.remove = () => {box.children = box.children.filter(x => x !== node);};
      return node;
    }
  }};
  vm.createContext(context);
  for(const file of ['lucia-visuals.js','named-follower-visuals.js']) {
    vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/assets',file),'utf8'),context);
  }
  return {box,context,render: context.window.backroomNamedFollowers.renderOverlay};
}
const nam = {id:'nguyen_quoc_nam',presence:'ACTIVE'};
const lan = {id:'hua_tieu_lan',presence:'ACTIVE'};

test('confirmed encounter appears before joining; flags or text never spawn artwork', () => {
  const {box,render} = fixture();
  render({flags:{nguyen_quoc_nam:{present:true}},log:[{text:'Nguyễn Quốc Nam'}]});
  assert.equal(box.children.length,0);
  render({partyDetails:{members:[]},followerEncounters:[nam]});
  assert.equal(box.children.length,1);
  assert.equal(box.children[0].src,'NguyenQuocNam.webp');
  render({partyDetails:{members:[]},followerEncounters:[{...nam,presence:'SEPARATED'}]});
  assert.equal(box.children.length,0);
});

test('party projection controls visibility and two followers coexist with Lucia', () => {
  const {box,context,render} = fixture();
  const state = {partyDetails:{members:[nam,lan,{id:'lucia',presence:'ACTIVE'}]}};
  context.window.backroomLuciaVisuals.renderOverlay(state);
  render(state);render(state);
  assert.equal(box.children.length,3);
  assert.equal(new Set(box.children.map(x=>x.src)).size,3);
  for(const image of box.children)assert.ok(parseFloat(image.style.maxWidth)<=100/3);
  render({partyDetails:{members:[]},party:[nam,lan]});
  assert.equal(box.children.filter(x=>x.className==='snapshot-named-follower').length,0);
});

test('Android snapshot clear and redraw retains both named characters without duplicates', () => {
  const {box,context} = fixture();
  context.state = {partyDetails:{members:[nam,lan]}};
  const java = fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const line = java.split('\n').find(line=>line.includes('function equippedItem(s)'));
  vm.runInContext(JSON.parse(line.trim().replace(/\s*\+$/,'')),context);
  vm.runInContext('renderSnapshot();renderSnapshot();',context);
  assert.equal(box.children.filter(x=>x.className==='snapshot-named-follower').length,2);
  context.state = {partyDetails:{members:[{...nam,presence:'DEAD'}]}};
  vm.runInContext('renderSnapshot();',context);
  assert.equal(box.children.filter(x=>x.className==='snapshot-named-follower').length,0);
});

test('all eight equipment icons match the packaged source manifest', () => {
  const crypto = require('node:crypto');
  const assets = path.join(root,'app/src/main/assets');
  const manifest = JSON.parse(fs.readFileSync(path.join(assets,'item-icons/manifest.json'),'utf8'));
  assert.equal(Object.keys(manifest.icons).length,8);
  for(const icon of Object.values(manifest.icons)) {
    const data=fs.readFileSync(path.join(assets,icon.path));
    assert.equal(crypto.createHash('sha256').update(data).digest('hex'),icon.sha256);
    assert.equal(data.toString('ascii',0,4),'RIFF');
  }
});

test('Luc Tram uses the supplied avatar and only confirmed active presence shows her overlay', () => {
  const {box,context,render} = fixture();
  const api = context.window.backroomNamedFollowers;
  const member = {id:'luc-tram',name:'Lục Trầm',presence:'ACTIVE',avatar:'old.png',currentHp:17};
  const before = JSON.stringify(member);
  const visual = api.withAvatar(member);
  assert.equal(visual.avatar,'characters/LUC_TRAM_AVATAR.png');
  assert.equal(visual.avatarRef,visual.avatar);
  assert.equal(JSON.stringify(member),before);
  assert.equal(visual.currentHp,17);
  render({partyDetails:{members:[]},party:[member],flags:{'luc-tram':{present:true}},log:[{text:'Lục Trầm'}]});
  assert.equal(box.children.length,0);
  render({partyDetails:{members:[member]}});
  render({partyDetails:{members:[member]}});
  assert.equal(box.children.length,1);
  assert.equal(box.children[0].src,'characters/LUC_TRAM_OVERLAY.png');
  for(const presence of ['MISSING','SEPARATED','DEAD',undefined]) {
    render({partyDetails:{members:[{...member,presence}]}});
    assert.equal(box.children.length,0);
    render({partyDetails:{members:[member]}});
  }
  // Unknown characters keep their own artwork; this update adds no other identities.
  const other={id:'trac-lam',avatar:'existing.jpg'};
  assert.equal(api.withAvatar(other),other);
  render({partyDetails:{members:[{...other,presence:'ACTIVE'}]}});
  assert.equal(box.children.length,0);
});

test('Luc Tram survives Android redraw and shares bounded columns with existing followers', () => {
  const {box,context} = fixture();
  context.state={partyDetails:{members:[nam,lan,{id:'luc-tram',presence:'ACTIVE'},{id:'lucia',presence:'ACTIVE'}]}};
  const java=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const line=java.split('\n').find(line=>line.includes('function equippedItem(s)'));
  vm.runInContext(JSON.parse(line.trim().replace(/\s*\+$/,'')),context);
  vm.runInContext('renderSnapshot();renderSnapshot();',context);
  assert.equal(box.children.length,4); // Character layers survive; no destructive Snapshot placeholder.
  const layers=box.children.filter(x=>x.className==='snapshot-lucia'||x.className==='snapshot-named-follower');
  assert.equal(layers.length,4);
  assert.equal(new Set(layers.map(x=>x.style.left)).size,4);
  for(const layer of layers)assert.equal(layer.style.maxWidth,'25%');
  context.state={partyDetails:{members:[]}};
  vm.runInContext('renderSnapshot()',context);
  assert.equal(box.children.length,0); // Renderer removes stale characters, not the scene container.
});

test('Luc Tram packaged artwork preserves both supplied Cloudinary sources exactly', () => {
  const crypto=require('node:crypto');
  const assets=path.join(root,'app/src/main/assets');
  const manifest=JSON.parse(fs.readFileSync(path.join(assets,'characters/manifest.json'),'utf8'));
  assert.equal(manifest.assets.length,2);
  for(const art of manifest.assets) {
    assert.match(art.source_name,/^LUC_TRAM_(AVATAR|OVERLAY)\.png$/);
    assert.match(art.url,/^https:\/\/res\.cloudinary\.com\/csz2me5b\//);
    const data=fs.readFileSync(path.join(assets,art.path));
    assert.equal(data.length,art.bytes);
    assert.equal(crypto.createHash('sha256').update(data).digest('hex'),art.sha256);
    assert.equal(data.toString('hex',0,8),'89504e470d0a1a0a');
  }
  const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
  assert.equal((html.match(/map\(window.backroomNamedFollowers.withAvatar\)/g)||[]).length,2);
});
