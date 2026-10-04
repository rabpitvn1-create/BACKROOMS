/* Shared sprite geometry. Logical bounds ignore faint export residue/shadows;
 * paint bounds retain translucent hair, weapons and clothing for safe-area fitting. */
var SnapshotOverlayLayout = (function(){
  var CHARACTER_HEIGHT=0.84,GROUND=0.92,SIDE_MARGIN=0.025,SAFE_EDGE=0.02;
  var ENTITY_LANE_WIDTH=0.46;
  // BEGIN GENERATED OVERLAY METRICS
  var bundledMetrics={
    "cao_minh_entity_overlay.png":{"width":1122,"height":1402,"paint":{"left":1,"top":0,"right":1122,"bottom":1386},"body":{"left":2,"top":3,"right":1122,"bottom":1376},"sha256":"980aaaf8a8575d41ab66b95d8ab24ca7052b66677a1a15e0eb6433c8b716eb47","feet":{"left":143,"right":1110,"bottom":1376}},
    "cao_minh_snapshot_overlay.png":{"width":1122,"height":1402,"paint":{"left":54,"top":16,"right":1020,"bottom":1389},"body":{"left":54,"top":17,"right":1017,"bottom":1385},"sha256":"e02f27c3125c3c4c3c3eb4b4314c93bab89d0ac18c92f8038ee2533d616bbd6e","feet":{"left":189,"right":812,"bottom":1385}},
    "entity/async_member_rifle_aim_right_01.webp":{"width":945,"height":1680,"paint":{"left":0,"top":0,"right":945,"bottom":1680},"body":{"left":0,"top":0,"right":945,"bottom":1680},"sha256":"e1ca5dfb1fddf7dba64bd3e1cef46d6a0d6f547f903010e1d61204cb7c356981","feet":{"left":55,"right":685,"bottom":1579}},
    "entity/async_rifleman.webp":{"width":941,"height":1672,"paint":{"left":140,"top":66,"right":841,"bottom":1623},"body":{"left":142,"top":67,"right":840,"bottom":1599},"sha256":"bd0b726251943b8f6afb18a5f2896bdbe44a8ad70c88bb18d50b2b075cf5621d","feet":{"left":209,"right":775,"bottom":1599}},
    "entity/biological_pipeline.webp":{"width":864,"height":1536,"paint":{"left":27,"top":104,"right":857,"bottom":1465},"body":{"left":55,"top":105,"right":856,"bottom":1458},"sha256":"4da8b62b404d4dbbb697dabbc695d3038f81a98943df8c252679be3d9256b8a5","feet":{"left":69,"right":461,"bottom":1458}},
    "entity/cable_mimic.webp":{"width":864,"height":1536,"paint":{"left":7,"top":0,"right":861,"bottom":1524},"body":{"left":8,"top":1,"right":859,"bottom":1516},"sha256":"5e187bae5df132448c3a156415efd4aa70b80afcb932879bdbf0141a4ad3c20e","feet":{"left":32,"right":742,"bottom":1516}},
    "entity/clump.webp":{"width":864,"height":1536,"paint":{"left":34,"top":267,"right":842,"bottom":1233},"body":{"left":38,"top":268,"right":839,"bottom":1225},"sha256":"ebc954d657b390c1f2be9be987c169a4554a92e1573b4b40692d291e99f87818","feet":{"left":291,"right":485,"bottom":1225}},
    "entity/copx.webp":{"width":864,"height":1536,"paint":{"left":13,"top":192,"right":858,"bottom":1334},"body":{"left":17,"top":194,"right":857,"bottom":1332},"sha256":"f12c367e2334a815710aea3ace059e4f782da2162f5b49f5bcc18610d1ce3eac","feet":{"left":26,"right":765,"bottom":1332}},
    "entity/deathmoth.webp":{"width":864,"height":1536,"paint":{"left":19,"top":260,"right":864,"bottom":1280},"body":{"left":20,"top":261,"right":860,"bottom":1278},"sha256":"cf14085cddbb89c754c7de596dc83d8b12dfa03785cf71155b76581f884b7c10","feet":{"left":46,"right":110,"bottom":1278}},
    "entity/diep_minh.webp":{"width":864,"height":1536,"paint":{"left":1,"top":213,"right":861,"bottom":1322},"body":{"left":6,"top":217,"right":858,"bottom":1251},"sha256":"fb378814008c8facaa53974c91bf274ab72970514ada9c0700287ca6f201b546","feet":{"left":243,"right":797,"bottom":1251}},
    "entity/duller.webp":{"width":864,"height":1536,"paint":{"left":212,"top":131,"right":611,"bottom":1407},"body":{"left":324,"top":133,"right":550,"bottom":1386},"sha256":"b949e284740fb40de1b368225fcc39294388b43333f0ca31c1be00da05980718","feet":{"left":353,"right":517,"bottom":1386}},
    "entity/false_puddle.webp":{"width":864,"height":1536,"paint":{"left":7,"top":92,"right":855,"bottom":1461},"body":{"left":8,"top":94,"right":854,"bottom":1460},"sha256":"8835d86c08d1a0dac02e6c70e281b6d26bbd8cb3c9c5e0894755f944d9e942b5","feet":{"left":74,"right":807,"bottom":1460}},
    "entity/hostile_faceling.webp":{"width":864,"height":1536,"paint":{"left":16,"top":153,"right":850,"bottom":1377},"body":{"left":17,"top":156,"right":849,"bottom":1366},"sha256":"2fe70c97e485e57d0a4a1da895d5b2f2e2c8057b6c57b1511bdc0d62c658bfd4","feet":{"left":481,"right":669,"bottom":1366}},
    "entity/hotel_corpse_lure.webp":{"width":864,"height":1536,"paint":{"left":86,"top":192,"right":864,"bottom":1318},"body":{"left":124,"top":195,"right":863,"bottom":1313},"sha256":"79ee4672aa483332fd7b81cb56443e1620052c09b10c7ff4aa96b97a5843477f","feet":{"left":197,"right":377,"bottom":1313}},
    "entity/hound.webp":{"width":864,"height":1536,"paint":{"left":18,"top":371,"right":860,"bottom":1179},"body":{"left":21,"top":372,"right":859,"bottom":1177},"sha256":"fef561b7273baf9b2038f0f321c87fbb7d7633f2885b43ff601313853c64c333","feet":{"left":653,"right":822,"bottom":1177}},
    "entity/jane_the_killer.webp":{"width":864,"height":1536,"paint":{"left":59,"top":234,"right":847,"bottom":1300},"body":{"left":80,"top":235,"right":846,"bottom":1293},"sha256":"c5522bbb452b69a0468171b5bf4cf2fe7568dafd25bd8b8ae7196ccd4e39f12b","feet":{"left":93,"right":435,"bottom":1293}},
    "entity/jeff_the_killer.webp":{"width":864,"height":1536,"paint":{"left":16,"top":40,"right":850,"bottom":1503},"body":{"left":17,"top":53,"right":849,"bottom":1481},"sha256":"926a81bf508f8eef786c8a2c727e0e7b8d92ebc42f1a714d1bfc980c60ee51bf","feet":{"left":172,"right":352,"bottom":1481}},
    "entity/paintings.webp":{"width":864,"height":1536,"paint":{"left":15,"top":82,"right":839,"bottom":1492},"body":{"left":16,"top":83,"right":838,"bottom":1441},"sha256":"76a52b8fca0e46700d3272a06b3e7633143e7fa10654760331c021a8f6db7de7","feet":{"left":505,"right":759,"bottom":1441}},
    "entity/predatory_window.webp":{"width":864,"height":1536,"paint":{"left":4,"top":275,"right":859,"bottom":1232},"body":{"left":5,"top":276,"right":858,"bottom":1214},"sha256":"07680f09729c8cfd62eaa062713adacd38acc9570bafa0c2158ada2f9df229a1","feet":{"left":123,"right":541,"bottom":1214}},
    "entity/skin-stealer.webp":{"width":864,"height":1536,"paint":{"left":28,"top":148,"right":794,"bottom":1364},"body":{"left":68,"top":149,"right":793,"bottom":1362},"sha256":"2deefdbdd7958fe25455a7496ac0354116e91b23fdcc51b4b242f481158413b8","feet":{"left":68,"right":583,"bottom":1362}},
    "entity/slenderman.webp":{"width":864,"height":1536,"paint":{"left":10,"top":192,"right":864,"bottom":1331},"body":{"left":15,"top":196,"right":863,"bottom":1316},"sha256":"0512fd3e43f907fe65b701bcabcbdb71f6dae73d4f51f45cff345d727a8a90d0","feet":{"left":552,"right":797,"bottom":1316}},
    "entity/smiler.webp":{"width":864,"height":1536,"paint":{"left":220,"top":200,"right":687,"bottom":1339},"body":{"left":250,"top":203,"right":618,"bottom":1322},"sha256":"bf827e46380613fbfdb2d8628972f0d29ff7bb560170fc8295a01e39a5d21a29","feet":{"left":290,"right":610,"bottom":1322}},
    "entity/tam_ma_cao_minh.webp":{"width":1085,"height":1450,"paint":{"left":195,"top":5,"right":911,"bottom":1418},"body":{"left":196,"top":6,"right":888,"bottom":1401},"sha256":"0f1a3b74ce780dc328285385e29a03433dd314c49a094a9c53f9a7dc16cade19","feet":{"left":302,"right":828,"bottom":1401}},
    "entity/the_beast_of_level_5.webp":{"width":864,"height":1536,"paint":{"left":17,"top":0,"right":864,"bottom":1496},"body":{"left":18,"top":0,"right":862,"bottom":1479},"sha256":"0d2e7d76b8d8acc18143c53f332583f252dee51c9a1358195f520b82a0c1dc75","feet":{"left":210,"right":597,"bottom":1479}},
    "entity/the_lifeform_bacteria_01.webp":{"width":945,"height":1680,"paint":{"left":0,"top":0,"right":945,"bottom":1680},"body":{"left":0,"top":0,"right":945,"bottom":1680},"sha256":"f6b85544d67e01ddf6d56c6a31578102ea7e28ed562e4993cb4c122379cb6ffd","feet":{"left":232,"right":738,"bottom":1645}},
    "entity/the_lifeform_bacteria_02.webp":{"width":945,"height":1680,"paint":{"left":0,"top":0,"right":945,"bottom":1680},"body":{"left":0,"top":0,"right":945,"bottom":1680},"sha256":"bc57c093a4666ae3b0aaf9a383b08a3cc5f0de6707fdcf3b96f9e237504293a3","feet":{"left":114,"right":799,"bottom":1548}},
    "entity/the_lifeform_bacteria_03.webp":{"width":945,"height":1680,"paint":{"left":0,"top":0,"right":945,"bottom":1680},"body":{"left":0,"top":0,"right":945,"bottom":1680},"sha256":"21299b1db4e229468b25676ab45480c83082ec1a82eaf1ba4e00858def041bc5","feet":{"left":119,"right":837,"bottom":1594}},
    "entity/wretch.webp":{"width":864,"height":1536,"paint":{"left":9,"top":207,"right":858,"bottom":1332},"body":{"left":22,"top":208,"right":852,"bottom":1325},"sha256":"21857546b2d789ee9d1831e02fd8132e718b7563a7ba02adf682dce8b5b0bc99","feet":{"left":34,"right":244,"bottom":1325}},
    "lucia_overlay.png":{"width":1024,"height":1536,"paint":{"left":17,"top":9,"right":1013,"bottom":1494},"body":{"left":17,"top":10,"right":1012,"bottom":1493},"sha256":"66ae60dcd2a08eb644ccc67b953e0b7de62c4c28ddfd24c26cea143db1d3961b","feet":{"left":134,"right":1012,"bottom":1493}},
    "luctram_overlay.png":{"width":941,"height":1672,"paint":{"left":6,"top":9,"right":941,"bottom":1650},"body":{"left":7,"top":11,"right":940,"bottom":1638},"sha256":"38b0501ec67e76a589bf83f63254d71cf5d8ab991a5c32393db0de49c01a1704","feet":{"left":421,"right":543,"bottom":1638}}
  };
  // END GENERATED OVERLAY METRICS
  function assetMetric(src){
    var clean=String(src||'').split(/[?#]/)[0],marker=clean.lastIndexOf('/entity/');
    var key=marker>=0?'entity/'+clean.slice(marker+8):clean.slice(clean.lastIndexOf('/')+1);
    return bundledMetrics[key]||null;
  }
  function characterMetrics(){
    return Object.keys(bundledMetrics).filter(function(key){return key.indexOf('entity/')!==0;}).map(function(key){return bundledMetrics[key];});
  }
  function canvasMetric(width,height){
    var box={left:0,top:0,right:width,bottom:height};
    return {width:width,height:height,paint:box,body:box};
  }
  function bounds(pixels,width,height){
    var paint={left:width,top:height,right:0,bottom:0};
    var body={left:width,top:height,right:0,bottom:0};
    function include(b,x,y){b.left=Math.min(b.left,x);b.top=Math.min(b.top,y);b.right=Math.max(b.right,x+1);b.bottom=Math.max(b.bottom,y+1);}
    for(var y=0;y<height;y++)for(var x=0;x<width;x++){
      var alpha=pixels[(y*width+x)*4+3];
      if(alpha>8)include(paint,x,y);
      if(alpha>128)include(body,x,y);
    }
    if(paint.right<=paint.left||paint.bottom<=paint.top)return null;
    if(body.right<=body.left||body.bottom<=body.top)body=paint;
    return {paint:paint,body:body,width:width,height:height};
  }
  function envelope(metrics){
    return metrics.reduce(function(e,m){
      var h=m.body.bottom-m.body.top;
      e.width=Math.max(e.width,(m.paint.right-m.paint.left)/h);
      e.above=Math.max(e.above,(m.body.bottom-m.paint.top)/h);
      e.below=Math.max(e.below,(m.paint.bottom-m.body.bottom)/h);
      return e;
    },{width:0,above:1,below:0});
  }
  function layout(metric,width,height,side,category,family,policy){
    if(!metric||!(width>0&&height>0))return null;
    policy=policy||{};
    var b=metric.body,p=metric.paint,h=b.bottom-b.top;
    var e=category==='character'?family:envelope([metric]);
    var heightRatio=Number(policy.heightRatio)||CHARACTER_HEIGHT;
    var laneWidth=Number(policy.laneWidth)||ENTITY_LANE_WIDTH;
    var groundRatio=Number(policy.ground)||GROUND;
    var safeEdge=Number.isFinite(Number(policy.safeEdge))?Number(policy.safeEdge):SAFE_EDGE;
    // A whole-snapshot width budget is shared by ALL Character poses. Narrow
    // viewports reduce the family together, never only the pose with a rifle.
    var target=Math.min(height*heightRatio,width*(category==='character'?1-2*SIDE_MARGIN:laneWidth)/e.width,height*(groundRatio-safeEdge)/e.above);
    if(e.below>0)target=Math.min(target,height*(1-groundRatio-safeEdge)/e.below);
    var scale=target/h,ground=height*groundRatio,margin=width*SIDE_MARGIN;
    return {scale:scale,width:metric.width*scale,height:metric.height*scale,
      top:ground-b.bottom*scale,
      left:side==='right'?width-margin-p.right*scale:margin-p.left*scale,
      baseline:ground,bodyHeight:target};
  }
  var STANDARD_HUMANOID_ENTITY_KEYS=[
    'tam_ma_cao_minh','async_member_rifle_aim_right_01',
    'the_lifeform_bacteria_01','the_lifeform_bacteria_02','the_lifeform_bacteria_03'
  ];
  function entityPolicy(key){
    return STANDARD_HUMANOID_ENTITY_KEYS.indexOf(String(key||''))>=0
      ?{heightRatio:.90,laneWidth:.62,ground:.94,safeEdge:.01}:null;
  }
  return {bounds:bounds,envelope:envelope,layout:layout,assetMetric:assetMetric,canvasMetric:canvasMetric,
    characterMetrics:characterMetrics,entityPolicy:entityPolicy};
})();
if(typeof module!=='undefined'&&module.exports)module.exports=SnapshotOverlayLayout;
(function(){
  if(typeof document==='undefined')return;
  if(window.__backroomEnhancements)return;
  window.__backroomEnhancements=true;
  var st=document.createElement('style');
  st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}.snapshot{position:relative;overflow:hidden;isolation:isolate}.snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot>img.snapshot-map{object-fit:contain;background:#050607}.snapshot-route-streak{position:absolute;left:10px;top:10px;z-index:6;width:118px;height:42px;box-sizing:border-box;display:grid;grid-template-columns:27px minmax(0,1fr);grid-template-rows:13px 19px 3px;column-gap:7px;align-items:center;padding:5px 9px 5px 7px;pointer-events:none;color:#e8dfbd;background:transparent;border:0;clip-path:none;text-shadow:0 1px 2px #000,0 0 5px rgba(0,0,0,.85);isolation:isolate}.snapshot-route-streak.is-ready{filter:drop-shadow(0 0 4px rgba(189,169,72,.22))}.snapshot-route-exit{grid-column:1;grid-row:1/4;width:25px;height:30px;align-self:center;color:#cfc592;opacity:.92;filter:drop-shadow(0 1px 1px #000)}.snapshot-route-exit svg{display:block;width:100%;height:100%;overflow:visible}.snapshot-route-label{grid-column:2;grid-row:1;font:700 7px/1 Play,"Pretendard Std",system-ui,sans-serif;letter-spacing:.18em;color:#bcb48d;text-transform:uppercase;white-space:nowrap}.snapshot-route-value{grid-column:2;grid-row:2;font:700 17px/.95 Play,"Pretendard Std",system-ui,sans-serif;letter-spacing:.02em;color:#eee6c6;white-space:nowrap}.snapshot-route-value small{font-size:10px;color:#aaa382;letter-spacing:.01em}.snapshot-route-track{grid-column:2;grid-row:3;align-self:end;height:1px;background:rgba(150,145,111,.26);overflow:hidden}.snapshot-route-fill{display:block;height:100%;background:linear-gradient(90deg,#8e874f,#d6c86f);box-shadow:0 0 4px rgba(214,200,111,.28)}@media(max-width:320px){.snapshot-route-streak{left:8px;top:8px;width:108px;height:39px;grid-template-columns:24px minmax(0,1fr);padding:4px 8px 4px 6px}.snapshot-route-exit{width:22px;height:27px}.snapshot-route-value{font-size:16px}}.snapshot-foot-shadow{position:absolute;z-index:2;border-radius:50%;pointer-events:none;background:radial-gradient(ellipse at center,rgba(0,0,0,.66) 0%,rgba(0,0,0,.4) 38%,rgba(0,0,0,.14) 66%,rgba(0,0,0,0) 78%);transition:opacity .18s ease}.snapshot>img.snapshot-grounded{position:absolute;left:auto;right:2.5%;top:8%;width:auto;height:84%;max-width:95%;max-height:none;object-fit:contain;pointer-events:none;transform:none}.snapshot>img.snapshot-character{z-index:4;filter:drop-shadow(0 0 10px rgba(0,0,0,.45))}.snapshot>img.snapshot-entity{left:2.5%;right:auto;max-width:46%;z-index:3;filter:drop-shadow(0 0 10px rgba(0,0,0,.55))}.snapshot-character-placeholder{position:absolute;right:2.5%;bottom:8%;width:38%;height:84%;z-index:4;pointer-events:none;opacity:.46;filter:drop-shadow(0 0 10px rgba(0,0,0,.5))}.snapshot-character-placeholder:before{content:"";position:absolute;left:50%;top:2%;width:27%;aspect-ratio:1;border-radius:50%;transform:translateX(-50%);background:#fff}.snapshot-character-placeholder:after{content:"";position:absolute;left:13%;right:13%;bottom:0;height:78%;background:#fff;clip-path:polygon(38% 0,62% 0,72% 10%,82% 25%,88% 51%,76% 100%,24% 100%,12% 51%,18% 25%,28% 10%);border-radius:18% 18% 9% 9%}.snapshot-combat-character{transform-origin:50% 62%;backface-visibility:hidden}.snapshot-combat-enter{animation:combat-overlay-enter .18s ease-out}.combat-turn-out{animation:combat-turn-out .24s ease-in forwards;transform-origin:50% 62%;backface-visibility:hidden}.combat-turn-in{animation:combat-turn-in .24s ease-out both;transform-origin:50% 62%;backface-visibility:hidden}.combat-shatter-layer{position:absolute;z-index:7;pointer-events:none;overflow:visible}.combat-shard{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;pointer-events:none;animation:combat-shard-break .72s cubic-bezier(.18,.68,.2,1) forwards;animation-delay:var(--delay,0ms)}.combat-hit-flash{animation:combat-hit-flash .14s ease-out!important}.combat-float{position:absolute;z-index:8;pointer-events:none;transform:translate(-50%,0);display:flex;flex-direction:column;align-items:center;gap:2px;font-family:Play,"Pretendard Std",system-ui,sans-serif;font-weight:700;font-size:19px;color:#f4f7fa;white-space:nowrap;text-shadow:0 2px 3px #000,0 0 6px #000;animation:combat-float-up 1.6s ease-out forwards}.combat-float-value{display:inline-flex;align-items:center;gap:5px;padding:1px 4px;border-radius:5px}.combat-float--critical{font-size:24px;font-weight:800;color:#ffd166;text-shadow:0 2px 3px #000,0 0 9px rgba(255,176,48,.72);animation:combat-float-crit 1.6s cubic-bezier(.16,.82,.24,1) forwards}.combat-float--critical .combat-float-value:before{content:"CRIT";font-size:9px;line-height:1;letter-spacing:.12em;color:#ffe7a3;background:#7b2d18;border:1px solid #ff9f43;padding:3px 4px;border-radius:4px;text-shadow:none}.combat-float-status{font:800 9px/1 Play,"Pretendard Std",system-ui,sans-serif;letter-spacing:.08em;padding:3px 6px;border-radius:999px;border:1px solid currentColor;background:rgba(8,10,12,.88);box-shadow:0 2px 7px rgba(0,0,0,.55);text-shadow:none}.combat-float--normal:not(.combat-float--critical) .combat-float-value{font-size:9px;line-height:1;font-weight:800}.combat-float-status--normal{color:#f4f7fa}.combat-float--status:not(.combat-float--critical) .combat-float-value{font-size:9px;line-height:1;font-weight:800}.combat-float--status-bleed:not(.combat-float--critical) .combat-float-value{color:#ff7777}.combat-float--status-poison:not(.combat-float--critical) .combat-float-value{color:#c38cff}.combat-float--status-stun:not(.combat-float--critical) .combat-float-value{color:#ffe066}.combat-float--status-armor:not(.combat-float--critical) .combat-float-value{color:#72c7ff}.combat-float--status-disorient:not(.combat-float--critical) .combat-float-value{color:#8fe3d1}.combat-float-status--bleed{color:#ff7777}.combat-float-status--poison{color:#c38cff}.combat-float-status--stun{color:#ffe066}.combat-float-status--armor{color:#72c7ff}.combat-float-status--disorient{color:#8fe3d1}@keyframes combat-hit-flash{0%,100%{opacity:1}50%{filter:brightness(0) invert(1) drop-shadow(0 0 8px #fff);opacity:1}}@keyframes combat-float-up{0%{opacity:0;transform:translate(-50%,8px) scale(.96)}12%{opacity:1}80%{opacity:1}100%{opacity:0;transform:translate(-50%,-34px) scale(1.04)}}@keyframes combat-float-crit{0%{opacity:0;transform:translate(-50%,10px) scale(.72)}9%{opacity:1;transform:translate(-50%,1px) scale(1.18)}20%{transform:translate(-50%,-3px) scale(1)}80%{opacity:1}100%{opacity:0;transform:translate(-50%,-42px) scale(1.05)}}@keyframes combat-overlay-enter{from{opacity:0}to{opacity:1}}@keyframes combat-turn-out{0%{opacity:1;transform:perspective(900px) rotateY(0deg) translateX(0)}100%{opacity:0;transform:perspective(900px) rotateY(-78deg) translateX(34px)}}@keyframes combat-turn-in{0%{opacity:0;transform:perspective(900px) rotateY(78deg) translateX(-34px)}100%{opacity:1;transform:perspective(900px) rotateY(0deg) translateX(0)}}@keyframes combat-shard-break{0%{opacity:1;transform:translate(0,0) rotate(0deg) scale(1);filter:brightness(1)}24%{filter:brightness(1.8)}100%{opacity:0;transform:translate(var(--tx),var(--ty)) rotate(var(--rot)) scale(.86);filter:brightness(.7)}}.snapshot>img.snapshot-chest{position:absolute;left:50%;bottom:-5%;transform:translateX(-50%);width:auto;max-width:58%;height:92%;object-fit:contain;object-position:center bottom;z-index:3;pointer-events:none;filter:drop-shadow(0 10px 16px rgba(0,0,0,.65))}';
  document.head.appendChild(st);
  var __overlayBoundsCache={};
  // Synchronous metadata avoids coupling any sprite to other images' load events.
  var __characterFamily=SnapshotOverlayLayout.envelope(SnapshotOverlayLayout.characterMetrics());
  function spriteMetric(img){
    var src=img.currentSrc||img.src;
    var bundled=SnapshotOverlayLayout.assetMetric(src);
    if(bundled&&bundled.width===img.naturalWidth&&bundled.height===img.naturalHeight)return bundled;
    if(__overlayBoundsCache[src])return __overlayBoundsCache[src];
    try{
      var canvas=document.createElement('canvas');
      canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
      var ctx=canvas.getContext('2d',{willReadFrequently:true});
      ctx.drawImage(img,0,0);
      var metric=SnapshotOverlayLayout.bounds(ctx.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height);
      if(metric){__overlayBoundsCache[src]=metric;return metric;}
    }catch(error){
      console.warn('Cannot measure overlay alpha bounds; using visible fallback',src,error);
    }
    // Unknown or replaced assets must remain visible even when canvas is tainted.
    var fallback=SnapshotOverlayLayout.canvasMetric(img.naturalWidth,img.naturalHeight);
    __overlayBoundsCache[src]=fallback;return fallback;
  }
  function placeFootShadow(el,box,center,baseline,width){
    var shadow=el.__footShadow;
    if(!shadow||shadow.parentElement!==box){
      shadow=document.createElement('div');shadow.className='snapshot-foot-shadow';
      shadow.setAttribute('aria-hidden','true');box.appendChild(shadow);el.__footShadow=shadow;
    }
    var height=Math.max(6,width*.22);
    shadow.style.left=(center-width/2)+'px';shadow.style.top=(baseline-height/2)+'px';
    shadow.style.width=width+'px';shadow.style.height=height+'px';
  }
  function removeGroundedOverlay(el){
    if(el.__footShadow&&typeof el.__footShadow.remove==='function')el.__footShadow.remove();
    if(typeof el.remove==='function')el.remove();
  }
  function alignOverlayToGround(img,side,category){
    img.dataset.groundSide=side;img.dataset.overlayCategory=category;
    function apply(){
      var box=img.parentElement;
      if(!box||!img.complete||!img.naturalWidth||!__characterFamily)return;
      var metric=spriteMetric(img);
      var policy=category==='entity'?SnapshotOverlayLayout.entityPolicy(img.dataset.entityKey):null;
      var result=SnapshotOverlayLayout.layout(metric,box.clientWidth,box.clientHeight,side,category,__characterFamily,policy);
      if(!result)return;
      img.style.maxWidth='none';img.style.right='auto';
      ['width','height','top','left'].forEach(function(key){img.style[key]=result[key]+'px';});
      var bounds=metric.paint;
      ['left','top','right','bottom'].forEach(function(key){img.dataset['visible'+key[0].toUpperCase()+key.slice(1)+'Px']=String(bounds[key]*result.scale);});
      var feet=metric.feet||metric.body,footWidth=(feet.right-feet.left)*result.scale;
      placeFootShadow(img,box,result.left+(feet.left+feet.right)*result.scale/2,result.top+feet.bottom*result.scale,Math.min(footWidth*1.25,result.bodyHeight*.48));
      img.style.visibility='visible';
    }
    if(img.complete&&img.naturalWidth)apply();else img.addEventListener('load',apply,{once:true});
  }
  function alignPlaceholder(el){
    var box=el.parentElement;if(!box||!__characterFamily)return;
    var metric={width:1,height:1,body:{left:0,top:0,right:1,bottom:1},paint:{left:0,top:0,right:1,bottom:1}};
    var result=SnapshotOverlayLayout.layout(metric,box.clientWidth,box.clientHeight,'right','character',__characterFamily);
    if(result){
      el.style.height=result.bodyHeight+'px';
      placeFootShadow(el,box,box.clientWidth*(1-.025-.38/2),box.clientHeight*.92,Math.min(box.clientWidth*.38*.72,result.bodyHeight*.48));
    }
  }
  function realignGroundedOverlays(){
    var box=document.getElementById('snapshot');if(!box)return;
    box.querySelectorAll('.snapshot-character-placeholder').forEach(alignPlaceholder);
    box.querySelectorAll('img.snapshot-grounded').forEach(function(img){alignOverlayToGround(img,img.dataset.groundSide||'left',img.dataset.overlayCategory||'entity');});
  }
  function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
  try{localStorage.removeItem('backroom-apk-snapshot');}catch(_){}
  function localLevelSnapshot(){try{if(!window.Android||typeof Android.levelSnapshot!=='function')return null;return JSON.parse(Android.levelSnapshot(JSON.stringify(state)));}catch(e){return null;}}
  var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','async_rifleman','async_member_rifle_aim_right_01','the_lifeform_bacteria_01','the_lifeform_bacteria_02','the_lifeform_bacteria_03','copx','tam_ma_cao_minh','jane_the_killer','slenderman','diep_minh'];
  var __combatCharacterOverlays={cao_minh:'file:///android_asset/cao_minh_entity_overlay.png',lucia:'file:///android_asset/lucia_overlay.png',luc_tram:'file:///android_asset/luctram_overlay.png'};
  window.__combatVisualActorIndex=null;
  window.__combatVisualEntityKey='';
  function normalizeEntityKey(v){if(v===null||v===undefined)return '';var k=String(v).trim().toLowerCase().replace(/\s+/g,'_');if(k==='skin_stealer')k='skin-stealer';return __entityKeys.indexOf(k)>=0?k:'';}
  function activeEntityKey(){try{var forced=normalizeEntityKey(window.__combatVisualEntityKey||'');if(forced)return forced;var s=(typeof state!=='undefined'&&state)?state:{};var f=s.flags||{},c=s.combat||{},combatKey='';if(c.active){var es=Array.isArray(c.entities)?c.entities:[],ei=Number(c.activeEntityIndex);if(!Number.isInteger(ei)||ei<0||ei>=es.length)ei=0;combatKey=(es[ei]&&es[ei].key)||((c.entity&&c.entity.key)||c.entityKey||c.enemyKey||c.enemy||'');}var encounterKeys=Array.isArray(f.entityEncounterKeys)?f.entityEncounterKeys:[];var k=normalizeEntityKey(combatKey||encounterKeys[0]||f.entityEncounterKey||f.currentEntityKey||s.entityEncounterKey||s.currentEntityKey);if(k)return k;if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'jeff_the_killer';if(f.jane&&(f.jane.present===true||f.jane.spawned===true))return 'jane_the_killer';return '';}catch(e){return '';}}
  function chestPresent(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !!(s.flags&&s.flags.chestPresent===true);}catch(e){return false;}}
  function shouldShowCaoMinhOverlay(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !(s.specialMode||s.debug||activeEntityKey()||chestPresent());}catch(e){return false;}}
  function normalizeActorId(v){var k=String(v||'').trim().toLowerCase();if(k.indexOf('cao_minh')>=0||k.indexOf('cao minh')>=0)return 'cao_minh';if(k.indexOf('lucia')>=0||k.indexOf('hứa thuý mai')>=0||k.indexOf('hứa thúy mai')>=0||k.indexOf('hua thuy mai')>=0)return 'lucia';if(k.indexOf('lục trầm')>=0||k.indexOf('luc tram')>=0||k.indexOf('luc_tram')>=0)return 'luc_tram';if(k.indexOf('syvial')>=0)return 'syvial';return k.replace(/\s+/g,'_');}
  function combatVisualParticipant(){try{var c=state&&state.combat;if(!c||!Array.isArray(c.participants)||!c.participants.length)return null;var idx;if(Number.isInteger(window.__combatVisualActorIndex)){idx=window.__combatVisualActorIndex;}else{var currentId=normalizeActorId(c.currentActor||'');if(currentId){for(var i=0;i<c.participants.length;i++){var candidate=c.participants[i];if(candidate&&normalizeActorId(candidate.id||candidate.name)===currentId)return candidate;}}idx=Number(c.actorIndex||0);}if(idx<0||idx>=c.participants.length)idx=0;return c.participants[idx]||null;}catch(_){return null;}}


  function appendCaoMinhSnapshot(box){
    var img=document.createElement('img');img.className='snapshot-character snapshot-grounded';img.src='file:///android_asset/cao_minh_snapshot_overlay.png';img.alt='Cao Minh';box.appendChild(img);alignOverlayToGround(img,'right','character');return img;
  }

  function appendCombatCharacter(box,participant,motionClass){
    var actor=participant||{id:'cao_minh',name:'Cao Minh'},id=normalizeActorId(actor.id||actor.name),src=__combatCharacterOverlays[id]||'';
    var motion=motionClass?' '+motionClass:'';
    if(src){
      var img=document.createElement('img');img.className='snapshot-character snapshot-grounded snapshot-combat-character'+motion;img.src=src;img.alt=actor.name||actor.id||'Nhân vật';img.dataset.combatActor=id;box.appendChild(img);alignOverlayToGround(img,'right','character');return img;
    }
    var placeholder=document.createElement('div');placeholder.className='snapshot-character-placeholder snapshot-combat-character'+motion;placeholder.setAttribute('role','img');placeholder.setAttribute('aria-label',actor.name||actor.id||'Nhân vật');box.appendChild(placeholder);alignPlaceholder(placeholder);return placeholder;
  }
  function appendCombatEntity(box,key,motionClass){
    var normalized=normalizeEntityKey(key),motion=motionClass?' '+motionClass:'';
    if(!normalized)return null;
    var img=document.createElement('img');
    img.className='snapshot-entity snapshot-grounded snapshot-combat-entity'+motion;
    img.dataset.entityKey=normalized;
    img.src='file:///android_asset/entity/'+normalized+'.webp';
    img.alt=normalized;
    box.appendChild(img);
    alignOverlayToGround(img,'left','entity');
    return img;
  }
  function appendRouteStreakHud(box,descriptor){
    try{
      var s=(typeof state!=='undefined'&&state)?state:{},route=s.levelRoute||{};
      var required=Number(descriptor&&descriptor.routeRequiredStreak);
      if(!Number.isFinite(required)||required<1)required=5;
      required=Math.max(1,Math.round(required));
      var current=descriptor&&descriptor.routeStreak!==undefined
        ?Number(descriptor.routeStreak):Number(route.streak||0);
      if(!Number.isFinite(current))current=0;
      current=Math.max(0,Math.min(required,Math.round(current||0)));
      var ready=descriptor&&descriptor.routeExitAvailable===true;
      if(!ready&&route&&route.exitAvailable===true)ready=true;
      var pct=Math.max(0,Math.min(100,Math.round(current*100/required)));
      var hud=document.createElement('div');
      hud.className='snapshot-route-streak'+(ready?' is-ready':'');
      hud.setAttribute('role','status');
      hud.setAttribute('aria-label','Exit streak '+current+' / '+required);
      hud.innerHTML='<span class="snapshot-route-exit" aria-hidden="true"><svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M5.5 3.5H21V28.5H5.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 7.5L18.5 5.5V26.5L10 24.5V7.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M15 16H29M25 12L29 16L25 20" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="15.2" cy="16" r="1" fill="currentColor"/></svg></span><span class="snapshot-route-label">STREAK</span><span class="snapshot-route-value">'+String(current).padStart(2,'0')+'<small>/'+required+'</small></span><span class="snapshot-route-track" aria-hidden="true"><span class="snapshot-route-fill" style="width:'+pct+'%"></span></span>';
      box.appendChild(hud);
    }catch(_){}
  }

  function appendSnapshotOverlay(box){
    var key=activeEntityKey(),img;
    if(key){
      appendCombatCharacter(box,combatVisualParticipant(),'snapshot-combat-enter');
      appendCombatEntity(box,key,'snapshot-combat-enter');
      return;
    }
    if(chestPresent()){
      img=document.createElement('img');
      img.className='snapshot-chest';
      img.src='file:///android_asset/chest_overlay.png';
      img.alt='Rương';
      box.appendChild(img);
      return;
    }
    if(shouldShowCaoMinhOverlay())appendCaoMinhSnapshot(box);
  }

  function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var local=localLevelSnapshot();if(local&&local.path){var img=document.createElement('img');img.className='snapshot-bg'+(local.visualType==='map'?' snapshot-map':'');img.src=local.path;img.alt='Level '+local.level+' Snapshot';box.appendChild(img);}else{var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>LEVEL SNAPSHOT</b><small>Không có ảnh local cho Level hiện tại.</small>';box.appendChild(p);}appendSnapshotOverlay(box);appendRouteStreakHud(box,local);}
  function combatTargetElement(target){var box=document.getElementById('snapshot');if(!box)return null;return target==='entity'?box.querySelector('.snapshot-entity'):box.querySelector('.snapshot-combat-character');}
  function targetAnchor(target){
    var box=document.getElementById('snapshot'),el=combatTargetElement(target);if(!box||!el)return null;
    var br=box.getBoundingClientRect(),er=el.getBoundingClientRect(),x=er.left-br.left+er.width/2,y=er.top-br.top+er.height*.34;
    if(el.tagName==='IMG'&&el.dataset.visibleLeftPx){
      var left=Number(el.dataset.visibleLeftPx||0),top=Number(el.dataset.visibleTopPx||0),right=Number(el.dataset.visibleRightPx||el.clientWidth),bottom=Number(el.dataset.visibleBottomPx||el.clientHeight);
      x=er.left-br.left+(left+right)/2;y=er.top-br.top+top+(bottom-top)*.34;
    }
    return {box:box,el:el,x:x,y:y};
  }
  function rotateCombatActor(nextIndex,nextEntity){
    var box=document.getElementById('snapshot');if(!box)return false;
    var existing=box.querySelectorAll('img.snapshot-combat-character,.snapshot-character-placeholder.snapshot-combat-character');
    var previous=existing&&existing.length?existing[0]:null;
    if(!previous)return false;
    var next=appendCombatCharacter(box,combatVisualParticipant(),'combat-turn-in');
    if(!next)return false;
    previous.className=String(previous.className||'')
      .replace(/\bsnapshot-combat-character\b/g,'').replace(/\bsnapshot-combat-enter\b/g,'')
      .replace(/\s+/g,' ').trim()+' combat-turn-out';
    setTimeout(function(){
      try{if(previous)removeGroundedOverlay(previous);}catch(_){}
      try{if(next)next.className=String(next.className||'').replace(/\bcombat-turn-in\b/g,'').replace(/\s+/g,' ').trim();}catch(_){}
    },270);
    return true;
  }
  function rotateCombatEntity(nextEntity){
    var box=document.getElementById('snapshot');if(!box)return false;
    var existing=box.querySelectorAll('img.snapshot-entity');
    var previous=existing&&existing.length?existing[0]:null;
    if(!previous)return false;
    var next=appendCombatEntity(box,nextEntity,'combat-turn-in');
    if(!next)return false;
    previous.className=String(previous.className||'')
      .replace(/\bsnapshot-entity\b/g,'').replace(/\bsnapshot-combat-entity\b/g,'')
      .replace(/\bsnapshot-combat-enter\b/g,'').replace(/\s+/g,' ').trim()+' combat-turn-out';
    setTimeout(function(){
      try{if(previous)removeGroundedOverlay(previous);}catch(_){}
      try{if(next)next.className=String(next.className||'').replace(/\bcombat-turn-in\b/g,'').replace(/\s+/g,' ').trim();}catch(_){}
    },270);
    return true;
  }
  window.backroomSetCombatVisualActor=function(index,entityKey){
    var nextIndex=Number(index),nextEntity=normalizeEntityKey(entityKey||'');
    if(!Number.isInteger(nextIndex))nextIndex=0;
    if(window.__combatVisualActorIndex===nextIndex&&window.__combatVisualEntityKey===nextEntity)return;
    var previousIndex=window.__combatVisualActorIndex,previousEntity=window.__combatVisualEntityKey;
    window.__combatVisualActorIndex=nextIndex;window.__combatVisualEntityKey=nextEntity;
    if(previousIndex!==null&&previousEntity){
      var changed=false,failed=false;
      if(previousIndex!==nextIndex){if(rotateCombatActor(nextIndex,nextEntity))changed=true;else failed=true;}
      if(previousEntity!==nextEntity){if(rotateCombatEntity(nextEntity))changed=true;else failed=true;}
      if(changed&&!failed)return;
    }
    renderSnapshot();
  };
  window.backroomClearCombatVisualActor=function(){
    if(window.__combatVisualActorIndex===null&&!window.__combatVisualEntityKey)return;
    window.__combatVisualActorIndex=null;window.__combatVisualEntityKey='';renderSnapshot();
  };
  window.backroomShatterEntity=function(entityKey,done){
    if(typeof entityKey==='function'){done=entityKey;entityKey='';}
    try{
      var requested=normalizeEntityKey(entityKey||''),box=document.getElementById('snapshot'),list=box?box.querySelectorAll('img.snapshot-entity'):null;
      var entity=null;
      if(list&&list.length){
        for(var n=0;n<list.length;n++){if(!requested||String(list[n].dataset.entityKey||'')===requested){entity=list[n];break;}}
      }
      if(!box||!entity){if(typeof done==='function')done();return;}
      var br=box.getBoundingClientRect(),er=entity.getBoundingClientRect();
      var layer=document.createElement('div');layer.className='combat-shatter-layer';
      layer.style.left=(er.left-br.left)+'px';layer.style.top=(er.top-br.top)+'px';
      layer.style.width=er.width+'px';layer.style.height=er.height+'px';
      // Uneven deterministic tessellation: large plates, medium chunks and narrow splinters
      // share the same source image, avoiding the old grid-like "four equal pieces" read.
      var clips=[
        'polygon(0 0,24% 0,17% 15%,0 31%)',
        'polygon(24% 0,49% 0,40% 17%,17% 15%)',
        'polygon(49% 0,72% 0,64% 22%,40% 17%)',
        'polygon(72% 0,100% 0,100% 15%,86% 28%,64% 22%)',
        'polygon(0 31%,17% 15%,27% 35%,11% 49%,0 46%)',
        'polygon(17% 15%,40% 17%,35% 33%,27% 35%)',
        'polygon(40% 17%,64% 22%,56% 40%,35% 33%)',
        'polygon(64% 22%,86% 28%,75% 44%,56% 40%)',
        'polygon(86% 28%,100% 15%,100% 43%,75% 44%)',
        'polygon(0 46%,11% 49%,21% 68%,0 73%)',
        'polygon(11% 49%,27% 35%,35% 33%,33% 58%,21% 68%)',
        'polygon(27% 35%,35% 33%,33% 58%)',
        'polygon(35% 33%,56% 40%,49% 62%,33% 58%)',
        'polygon(56% 40%,75% 44%,84% 64%,49% 62%)',
        'polygon(75% 44%,100% 43%,100% 69%,84% 64%)',
        'polygon(0 73%,21% 68%,18% 100%,0 100%)',
        'polygon(21% 68%,33% 58%,49% 62%,43% 83%,18% 100%)',
        'polygon(49% 62%,84% 64%,72% 85%,43% 83%)',
        'polygon(84% 64%,100% 69%,100% 100%,72% 85%)',
        'polygon(33% 58%,49% 62%,43% 83%)'
      ];
      var motion=[
        [-82,-58,-37,0],[-34,-74,-16,24],[18,-88,13,8],[76,-66,31,32],
        [-92,-18,-48,16],[-46,-36,22,48],[-12,-52,-29,0],[42,-44,38,40],[94,-20,53,16],
        [-88,34,-31,32],[-42,54,17,8],[-18,22,-62,56],[10,64,27,24],[58,48,-24,0],
        [98,38,46,40],[-70,82,-42,16],[-28,96,19,48],[36,92,-17,8],[86,78,39,32],[8,38,71,56]
      ];
      for(var i=0;i<clips.length;i++){var shard=document.createElement('img');shard.className='combat-shard';shard.src=entity.src;shard.alt='';shard.style.clipPath=clips[i];shard.style.webkitClipPath=clips[i];shard.style.setProperty('--tx',motion[i][0]+'px');shard.style.setProperty('--ty',motion[i][1]+'px');shard.style.setProperty('--rot',motion[i][2]+'deg');shard.style.setProperty('--delay',String(motion[i][3])+'ms');layer.appendChild(shard);}
      entity.style.opacity='0';if(entity.__footShadow)entity.__footShadow.style.opacity='0';box.appendChild(layer);
      setTimeout(function(){try{if(layer&&typeof layer.remove==='function')layer.remove();}catch(_){}if(typeof done==='function')done();},820);
    }catch(_){if(typeof done==='function')done();}
  };
  function combatStatusVisual(status){
    var value=String(status||'').trim();
    if(value==='Chảy máu')return {label:'CHẢY MÁU',key:'bleed'};
    if(value==='Trúng độc')return {label:'TRÚNG ĐỘC',key:'poison'};
    if(value==='Choáng')return {label:'CHOÁNG',key:'stun'};
    if(value==='Xuyên giáp'||value==='Phá giáp')return {label:'XUYÊN GIÁP',key:'armor'};
    if(value==='Mất phương hướng')return {label:'MẤT PHƯƠNG HƯỚNG',key:'disorient'};
    return null;
  }
  window.backroomPlayCombatFeedback=function(event){
    try{
      var e=event||{},text=String(e.text||'').trim();if(!/^-\d+ HP$/i.test(text))return;
      var target=e.target==='entity'?'entity':'actor',anchor=targetAnchor(target);if(!anchor)return;
      if(e.flash){
        anchor.el.classList.remove('combat-hit-flash');void anchor.el.offsetWidth;anchor.el.classList.add('combat-hit-flash');
        setTimeout(function(){anchor.el&&anchor.el.classList.remove('combat-hit-flash');},170);
      }
      var lane=anchor.box.querySelectorAll('.combat-float[data-target="'+target+'"]').length;
      var floater=document.createElement('div');floater.className='combat-float';floater.dataset.target=target;
      if(e.critical===true)floater.classList.add('combat-float--critical');
      var value=document.createElement('span');value.className='combat-float-value';value.textContent=text;floater.appendChild(value);
      var status=combatStatusVisual(e.status);
      if(status){
        floater.classList.add('combat-float--status','combat-float--status-'+status.key);
        var badge=document.createElement('span');badge.className='combat-float-status combat-float-status--'+status.key;badge.textContent=status.label;value.insertBefore(badge,value.firstChild);
      }else{
        floater.classList.add('combat-float--normal');
        var normalBadge=document.createElement('span');normalBadge.className='combat-float-status combat-float-status--normal';normalBadge.textContent='[Đánh thường]';value.insertBefore(normalBadge,value.firstChild);
      }
      floater.style.left=anchor.x+'px';floater.style.top=(anchor.y-lane*38)+'px';anchor.box.appendChild(floater);
      floater.addEventListener('animationend',function(){floater.remove();},{once:true});setTimeout(function(){floater.remove();},1800);
    }catch(_){}
  };
  var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});renderSnapshot();};
  var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){if(document.body.classList.contains('player-action-open'))return;if(state&&state.combat&&state.combat.active)return;var a=document.getElementById('action');var text=state&&state.__uiDisplayAction?String(state.__uiDisplayAction).trim():(a?a.value.trim():'');if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class="role">BẠN</div><div class="text"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class="role">GAME MASTER</div><div class="text">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}
  var __groundResizeTimer=0;
  window.addEventListener('resize',function(){clearTimeout(__groundResizeTimer);__groundResizeTimer=setTimeout(realignGroundedOverlays,80);});
  if(typeof ResizeObserver!=='undefined'){
    var snapshotBox=document.getElementById('snapshot');
    if(snapshotBox)new ResizeObserver(realignGroundedOverlays).observe(snapshotBox);
  }
  renderSnapshot();
})();
