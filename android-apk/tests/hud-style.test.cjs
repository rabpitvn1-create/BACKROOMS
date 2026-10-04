const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const snapshot=fs.readFileSync(path.join(assets,'snapshot-ui.js'),'utf8');
const gmChoice=fs.readFileSync(path.join(assets,'gm-choice-ui.js'),'utf8');
const inventory=fs.readFileSync(path.join(assets,'inventory-ui.js'),'utf8');
const party=fs.readFileSync(path.join(assets,'party-ui.js'),'utf8');

function pngSize(file){
  const data=fs.readFileSync(file);
  assert.equal(data.toString('ascii',1,4),'PNG',file);
  return [data.readUInt32BE(16),data.readUInt32BE(20)];
}

test('legacy Tu Tien HUD frame and action plate assets are removed from disk',()=>{
  assert.equal(fs.existsSync(path.join(assets,'hud/snapshot_frame.png')),false);
  assert.equal(fs.existsSync(path.join(assets,'hud/action_plate.png')),false);
});

test('snapshot keeps the clean dark frame while allowing only the compact route streak HUD',()=>{
  assert.doesNotMatch(snapshot,/snapshot_frame\.png/);
  assert.doesNotMatch(snapshot,/\.snapshot-hud-frame/);
  assert.doesNotMatch(snapshot,/\.combat-status-hud/);
  assert.doesNotMatch(snapshot,/__combatStatusIcons/);
  assert.match(snapshot,/\.snapshot-route-streak\{[^}]*width:118px;[^}]*height:42px/);
  assert.match(snapshot,/snapshot-route-exit/);
  assert.match(snapshot,/String\(current\)\.padStart\(2,'0'\)/);
  assert.match(snapshot,/\.combat-float\{[^}]*z-index:8/);
});

test('PLAYER ACTION and THUC HIEN return to standard dark button style without action_plate.png background',()=>{
  assert.doesNotMatch(index,/action_plate\.png/);
  assert.match(index,/\.player-action-bar #playerActionOpen,#submit\{/);
  assert.match(index,/<button id="playerActionOpen" type="button">PLAYER ACTION<\/button>/);
  assert.match(index,/<button id="submit" type="submit">THỰC HIỆN<\/button>/);
  assert.match(index,/#playerActionOpen\{min-height:64px/);
  assert.match(index,/#submit\{min-height:52px/);
});

test('combat dice share an ivory silhouette and keep one clear value per local asset',()=>{
  assert.match(gmChoice,/\.combat-die-skin\{[^}]*object-fit:contain/);
  assert.match(gmChoice,/\.combat-die\.held\{[^}]*border-color:/);
  assert.match(gmChoice,/seal\.textContent='GIỮ'/);
  assert.doesNotMatch(gmChoice,/鎖|combat-die-hold-ring|combat-die-held-float/);
  let sharedBody;
  for(let i=1;i<=6;i++){
    const die=fs.readFileSync(path.join(assets,'dice','die-'+i+'.svg'),'utf8');
    const pips=die.match(/<g id="value-pips"[^>]*>([\s\S]*?)<\/g>/)[1];
    assert.equal((pips.match(/<circle\b/g)||[]).length,i);
    const silhouette=die.split('<defs>')[1].split('<g id="value-pips"')[0];
    if(sharedBody)assert.equal(silhouette,sharedBody);
    sharedBody=silhouette;
    assert.match(die,/<title id="title">Xúc xắc mặt /);
    assert.doesNotMatch(die,/<image\b|<script\b|href=/);
  }
});

test('normal rectangular UI elements use consistent light rounded corners while GM message frame remains square',()=>{
  // Normal UI boxes have rounded corners
  assert.match(index,/\.game,\.card\{[^}]*border-radius:/);
  assert.match(index,/\.snapshot\{[^}]*border-radius:[^;]+;overflow:hidden/);
  assert.match(index,/\.player-action-sheet\{[^}]*border-radius:/);
  assert.match(index,/textarea\{[^}]*border-radius:/);
  assert.match(index,/button\{[^}]*border-radius:/);
  assert.match(index,/\.chips span\{[^}]*border-radius:/);

  assert.match(gmChoice,/\.gm-choice\{[^}]*border-radius:/);
  assert.match(gmChoice,/\.combat-dice-panel\{[^}]*border-radius:/);
  assert.match(gmChoice,/\.combat-die\{[^}]*border-radius:/);

  assert.match(inventory,/\.inventory-item\{[^}]*border-radius:/);
  assert.match(inventory,/\.inventory-sheet\{[^}]*border-radius:/);

  assert.match(party,/\.party-member-card\{[^}]*border-radius:/);
  assert.match(party,/\.party-detail-section\{[^}]*border-radius:/);

  // GAME MASTER frame exception: explicit border-radius:0
  assert.match(index,/\.message\{[^}]*border-radius:0/);
  assert.match(gmChoice,/\.message\.gm\{[^}]*border-radius:0/);
});

test('mobile header uses packaged Backrooms artwork without changing snapshot assets',()=>{
  assert.match(index,/BACKROOM_HEADER_ART_V2/);
  assert.match(index,/url\('level_snapshots\/drive\/level_0\/01\.webp'\)/);
  assert.match(index,/\.topbar\{[^}]*background-image:/);
  assert.match(index,/\.topbar \.eyebrow,\.topbar h1\{[^}]*text-shadow:/);
  assert.doesNotMatch(snapshot,/BACKROOM_HEADER_ART_V2/);
});

test('PLAYER ACTION uses dedicated IMG_API-generated Backrooms artwork',()=>{
  const art=path.join(assets,'hud/player_action_backrooms.png');
  assert.equal(fs.existsSync(art),true);
  assert.equal(fs.statSync(art).size>10000,true);
  assert.match(index,/BACKROOM_ACTION_ART_V2/);
  assert.match(index,/url\('hud\/player_action_backrooms\.png'\)/);
  assert.match(index,/\.player-action-bar #playerActionOpen\{[^}]*background-image:/);
});
