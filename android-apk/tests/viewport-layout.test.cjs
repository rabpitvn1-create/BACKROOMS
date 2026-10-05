'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const playerAction=fs.readFileSync(path.join(assets,'player-action-ui.js'),'utf8');
const main=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');

test('game shell uses one visual viewport height and keeps page scrolling locked',()=>{
  assert.match(index,/html,body\{height:100%;overflow:hidden;overscroll-behavior:none\}/);
  assert.match(index,/\.shell\{height:var\(--app-height,100dvh\);min-height:0;overflow:hidden;/);
  assert.match(index,/\.shell\{height:var\(--app-height,100dvh\);min-height:0;overflow:hidden;padding:0;/);
  assert.match(index,/function syncViewportHeight\(\)/);
  assert.match(index,/window\.visualViewport/);
  assert.match(index,/setProperty\("--app-height",height\+"px"\)/);
});

test('runtime entrypoint avoids optional chaining that breaks the Android 10 WebView baseline',()=>{
  assert.doesNotMatch(index,/\\b(?:state|s)\\?\\./);
  assert.doesNotMatch(index,/state\\.player\\?\\./);
});

test('gameplay frame is fixed while only the narrative log consumes leftover height',()=>{
  assert.match(index,/\.game\{height:100%;min-height:0;display:grid;grid-template-rows:auto auto minmax\(0,1fr\) auto;overflow:hidden\}/);
  assert.match(index,/\.log\{height:auto;min-height:0;overflow:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch;/);
  assert.doesNotMatch(index,/\.log\{height:clamp\(320px,48vh,540px\)/);
});

test('keyboard-sensitive controls use visual viewport plus native IME insets',()=>{
  assert.match(index,/textarea\{min-height:150px;max-height:calc\(var\(--app-height,100dvh\) - 150px\);/);
  assert.match(index,/\.game-menu-sheet\{[^}]*max-height:min\(calc\(var\(--app-height,100dvh\) - 12px\),760px\)/);
  assert.match(playerAction,/window\.visualViewport\.addEventListener\('resize', fitVisualViewport\)/);
  assert.match(playerAction,/window\.visualViewport\.addEventListener\('scroll', fitVisualViewport\)/);
  assert.match(playerAction,/window\.backroomSetImeInset = function\(physicalPixels\)/);
  assert.match(playerAction,/imeInsetCss = pixels \/ ratio/);
  assert.match(playerAction,/usableHeight = Math\.min\(visualHeight, Math\.max\(1, baselineHeight - imeInsetCss\)\)/);
  assert.match(main,/installImeInsetBridge\(\)/);
  assert.match(main,/WindowInsets\.Type\.ime\(\)/);
  assert.match(main,/backroomSetImeInset/);
});

test('header stays compact while horizontal and bottom controls respect curved-screen safe areas',()=>{
  assert.doesNotMatch(index,/\.topbar\{[^}]*env\(safe-area-inset-top\)/);
  assert.match(index,/\.topbar\{[^}]*padding:10px max\(14px,env\(safe-area-inset-right\)\) 8px max\(14px,env\(safe-area-inset-left\)\)/);
  assert.match(index,/\.topbar\{[^}]*env\(safe-area-inset-left\)/);
  assert.match(index,/\.topbar\{[^}]*env\(safe-area-inset-right\)/);
  assert.match(index,/\.player-action-bar\{[^}]*env\(safe-area-inset-bottom\)/);
  assert.match(index,/\.snapshot\{height:300px;/);
});

test('permanent turn status no longer consumes a gameplay grid row',()=>{
  assert.match(index,/\.status\{position:absolute;width:1px;height:1px;/);
  assert.doesNotMatch(index,/Save được lưu riêng trên thiết bị này/);
  assert.doesNotMatch(index,/Turn "\+state\.turn/);
});

test('Play stays on special UI while narrative body uses Pretendard Std',()=>{
  assert.match(index,/@font-face\{font-family:'Play'[^}]*Play-Regular\.ttf/);
  assert.match(index,/@font-face\{font-family:'Pretendard Std'[^}]*PretendardStd-Regular\.woff2/);
  assert.match(index,/\.topbar,\.game-menu-trigger,\.player-action-bar button[^}]*font-family:'Play','Pretendard Std'/);
  assert.match(index,/body\{[^}]*font:15px 'Pretendard Std'/);
});

test('gameplay background reaches all four screen edges',()=>{
  assert.match(index,/\.shell\{height:var\(--app-height,100dvh\);min-height:0;overflow:hidden;padding:0;/);
  assert.match(index,/\.game\{border:0;border-radius:0;box-shadow:none\}/);
  assert.doesNotMatch(index,/\.shell\{[^}]*safe-area-inset-(?:left|right|bottom)/);
});
