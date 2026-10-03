const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const uiPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'gm-choice-ui.js');
const source = fs.readFileSync(uiPath, 'utf8');
const coreFacadePath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'rabpit', 'backroom', 'core', 'GameCoreFacade.java');
const coreFacadeSource = fs.readFileSync(coreFacadePath, 'utf8');

test('Poker Dice uses one reusable inline panel inside the GM log', () => {
  assert.equal((source.match(/dicePanel=document\.createElement\('section'\)/g) || []).length, 1);
  assert.match(source, /article\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /document\.body\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /combat-dice-modal/);
  assert.doesNotMatch(source, /\.combat-dice-panel\{[^}]*position:fixed/);
});

test('combat hides the two fixed Explorer choices while the inline dice panel is active', () => {
  const start = source.indexOf('function appendExplorerChoices');
  const end = source.indexOf('function renderSemanticLog', start);
  assert.ok(start >= 0 && end > start);
  assert.match(source.slice(start, end), /if \(!entry \|\| \(state\.combat && state\.combat\.active\)\) return;/);
});

test('reroll UI does not append GM messages', () => {
  const start = source.indexOf('function sendCombatRoll()');
  const end = source.indexOf('function sendCombatFinish()', start);
  assert.ok(start >= 0 && end > start);
  const rollBlock = source.slice(start, end);
  assert.match(rollBlock, /Android\.combatRoll/);
  assert.doesNotMatch(rollBlock, /state\.log|appendBattleSection|appendLog/);
});

test('current hand is shown before FINISH', () => {
  assert.match(source, /diceResult\.textContent=hasRolled\?handLabel\(dice\.hand\):'';/);
  assert.match(source, /'TWO PAIR':'Two Pair'/);
});

test('ROLL and FINISH are separate controls', () => {
  assert.match(source, /id="combatDiceRoll">ROLL<\/button>/);
  assert.match(source, /id="combatDiceFinish">FINISH<\/button>/);
  assert.match(source, /Android\.combatFinish/);
  assert.match(source, /diceRoll\.disabled=.*rerolls>=maxRerolls\|\|allHeld\(held\)/);
});

test('Poker Dice meta is fully localized to Vietnamese', () => {
  assert.ok(source.includes("diceMeta.textContent='Lượt Quay '+String(rerolls)+'/'+String(maxRerolls)+' - Chạm Vào Xúc Xắc Để Giữ';"));
  assert.doesNotMatch(source, /Reroll .*chạm die để HOLD/);
});

test('production dice UI uses local assets and no Unicode dice glyphs', () => {
  assert.match(source, /file:\/\/\/android_asset\/dice\/die-/);
  assert.doesNotMatch(source, /[⚀⚁⚂⚃⚄⚅]/);
  assert.doesNotMatch(source, /Math\.random/);
});

test('finished hand waits two seconds before Core resolve', () => {
  assert.match(source, /setTimeout\(function\(\)[\s\S]*Android\.combatResolve\(JSON\.stringify\(state\)\)[\s\S]*},2000\)/);
});


test('battle log uses semantic font for Cao Minh title and compact hand tokens', () => {
  assert.match(source, /Vạn Giới Ma Tôn/);
  assert.match(source, /\[F\.O\.A\.K\]/);
  assert.match(source, /knownHandTokens\.forEach\(function\(x\)\{ addTerm\(map,x,'stat'\); \}\)/);
  assert.match(source, /Trúng độc/);
  assert.match(source, /Xuyên giáp/);
});


test('combat actor handoff delegates to snapshot transition without clearing the active Entity first', () => {
  assert.match(source, /backroomSetCombatVisualActor\(actorIndex,entityKey\)/);
  const sync=source.slice(source.indexOf('function syncCombatSnapshotActor'),source.indexOf('window.backroomCombatDiceState=function'));
  assert.doesNotMatch(sync, /backroomClearCombatVisualActor\(\);[\s\S]{0,100}backroomSetCombatVisualActor/);
});

test('combat completion scrolls to the start of the next GM narration', () => {
  const start = source.indexOf('function finishCombatAnimation');
  const end = source.indexOf('window.backroomCombatTurn', start);
  assert.ok(start >= 0 && end > start);
  const finishBlock = source.slice(start, end);
  assert.match(finishBlock, /scrollForCurrentMode\(\)/);
  assert.doesNotMatch(finishBlock, /scrollCombatToBottom\(\)/);
});

test('Core upgrades use live state and are locked only by active combat', () => {
  const start = coreFacadeSource.indexOf('public synchronized String processCoreUpgrade');
  const end = coreFacadeSource.indexOf('public synchronized String levelSnapshotDescriptor', start);
  assert.ok(start >= 0 && end > start);
  const upgradeBlock = coreFacadeSource.slice(start, end);
  assert.match(upgradeBlock, /JSONObject persisted = parseState\(liveStateJson\)/);
  assert.match(upgradeBlock, /if \(persisted\.length\(\) == 0\) persisted = submitted;/);
  assert.match(upgradeBlock, /CombatChoiceEngine\.isActive\(persisted\)/);
});


test('dice fill their cells and animate visibly while ROLL is resolving', () => {
  assert.match(source, /\.combat-dice-row\{[^}]*gap:5px;perspective:720px/);
  assert.match(source, /\.combat-die\{padding:1px;/);
  assert.match(source, /\.combat-die img\{width:104%;height:104%/);
  assert.match(source, /@keyframes combat-die-roll/);
  assert.match(source, /var DICE_ROLL_ANIMATION_MS=650;/);
  assert.match(source, /diceRollAnimating=true;/);
  assert.match(source, /rolling=diceRollAnimating&&held\[index\]!==true/);
  assert.match(source, /animationDelay=String\(index\*-55\)\+'ms'/);
  assert.match(source, /DICE_ROLL_ANIMATION_MS-\(Date\.now\(\)-diceRollStartedAt\)/);
});


test('GM effect highlights keep the normal narration font', () => {
  assert.match(source, /\.message\.gm \.semantic-effect,\.message\.gm \.semantic-damage,\.message\.gm \.semantic-buff\{font-family:inherit\}/);
  assert.match(source, /\.semantic-effect\{color:#ff9f43\}/);
  assert.match(source, /\.semantic-damage\{color:#ff5c5c\}/);
  assert.match(source, /\.semantic-buff\{color:#73e6a2\}/);
});


test('multi-Entity combat exposes a compact target queue and Core-owned target mutation', () => {
  assert.match(source, /id="combatTargets"/);
  assert.match(source, /function combatEntities\(combat\)/);
  assert.match(source, /function sendCombatTarget\(index\)/);
  assert.match(source, /Android\.combatTarget\(Number\(index\)\)/);
  assert.match(source, /targetEntityIndex/);
  assert.match(coreFacadeSource, /public synchronized String combatTargetRuntime\(int entityIndex\)/);
  assert.match(coreFacadeSource, /CombatChoiceEngine\.setTargetEntity\(working, entityIndex\)/);
});

test('active Entity handoff is separate from player actor handoff', () => {
  assert.match(source, /resolvedEntityTurns/);
  assert.match(source, /backroomSetCombatVisualActor\(combat\.resolvedActorIndex,entityKey\)/);
  assert.match(source, /playCombatPhase\(events,'entity',entityIndex\)/);
});
