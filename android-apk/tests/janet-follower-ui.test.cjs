const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const main = path.resolve(__dirname, '../app/src/main');
const java = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const canon = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/JanetCanon.kt'), 'utf8');
const skill = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/CompanionSkillCatalog.kt'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(main, 'assets/item-icons/manifest.json'), 'utf8'));

test('Janet artwork and face portrait are separate real packaged assets', () => {
  assert.ok(fs.statSync(path.join(main, 'assets/Janet.webp')).size <= 350000);
  assert.ok(fs.statSync(path.join(main, 'assets/avatars/Janet_avatar.webp')).size > 1000);
  assert.match(canon, /avatars\/Janet_avatar\.webp/);
  assert.match(canon, /Janet\.webp/);
});
test('Janet snapshot is wired through the existing render hook and respects encounter presence', () => {
  assert.match(java, /JANET_FOLLOWER_OVERLAY_R01/);
  assert.match(java, /function appendJanetVisual/);
  assert.match(java, /encounteredCharacters/);
  assert.match(java, /file:\/\/\/android_asset\/Janet\.webp/);
  assert.match(java, /__janetBaseRenderSnapshot\(\);appendJanetVisual\(\);/);
});
test('Janet has five skill entries plus one Ultimate and four distinct equipment icons', () => {
  for (const name of ["Ragin' Cajun", 'Bayou Swing', 'Barbed Encore',
    'Redline Sidestep', 'Ride or Die', 'Last Inning // Bayou Thunder']) {
    assert.ok(skill.includes(name), 'Missing skill: ' + name);
  }
  const listed = manifest.items.filter(item => item.item_id && item.item_id.startsWith('janet:'));
  assert.equal(listed.length, 4);
  assert.equal(new Set(listed.map(x => x.key)).size, 4);
  for (const item of listed) {
    const file = path.join(main, 'assets/item-icons', item.file);
    assert.equal(fs.statSync(file).size, item.bytes);
    assert.equal(item.width, 128);
    assert.equal(item.height, 128);
  }
});


test('Janet encounter temporarily shows Cao Minh on the left, then restores him immediately on the right',()=>{
  const line=java.split('\n').find(source=>source.includes('SNAPSHOT_PARTY_LAYOUT_R03'));
  assert.ok(line,'Follower encounter layout must exist');
  const injected=JSON.parse(line.trim().replace(/\s*\+\s*$/,''));
  const createLayout=new Function('window','document','state','renderSnapshot',injected+
    ';return window.backroomRefreshSnapshotPartyLayout;');
  const hero={style:{}},janet={style:{}},box={dataset:{},
    querySelector:selector=>selector==='.snapshot-character'?hero:null,
    querySelectorAll:()=>[janet]};
  const state={
    combat:{active:false},
    encounteredCharacters:[{id:'janet',present:true}],
    specialDialogueProgress:{pending:{id:'janet',kind:'intro'}}
  };
  const win={};
  const layout=createLayout(win,{getElementById:id=>id==='snapshot'?box:null},
    state,()=>{});
  layout();
  assert.equal(box.dataset.partyLayout,'encounter');
  assert.equal(hero.style.left,'6%');
  assert.equal(hero.style.height,'84%');
  assert.equal(janet.style.left,'50%');
  assert.equal(janet.style.height,'84%');
  // The Core is allowed to keep Janet present after her first scene.
  state.specialDialogueProgress.pending=null;
  layout();
  assert.equal(box.dataset.partyLayout,'exploration');
  assert.equal(hero.style.left,'auto');
  assert.equal(hero.style.right,'0');
  assert.equal(hero.style.height,'97%');
  assert.equal(hero.style.width,'auto');
  assert.equal(hero.style.maxWidth,'100%');
  assert.equal(janet.style.left,'3%');
  // An Entity encounter cannot be mistaken for a Follower first contact.
  state.pendingEncounter={entityKey:'diep_minh',encounterId:'entity-1'};
  layout();
  assert.equal(box.dataset.partyLayout,'exploration');
  assert.equal(hero.style.right,'0');
  // Combat has its own unmodified rendering rules.
  state.combat.active=true;
  layout();
  assert.equal(box.dataset.partyLayout,'combat');
});
