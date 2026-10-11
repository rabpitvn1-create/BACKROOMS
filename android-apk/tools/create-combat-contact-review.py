from pathlib import Path
import base64,mimetypes,re,json
import argparse
parser=argparse.ArgumentParser(description='Export an offline review of the production combat effects.')
parser.add_argument('output',type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[2];assets=root/'android-apk/app/src/main/assets'
def data(name):
 p=assets/name
 return 'data:'+ (mimetypes.guess_type(p.name)[0] or 'application/octet-stream')+';base64,'+base64.b64encode(p.read_bytes()).decode()
css=(assets/'combat-feedback-1193a.css').read_text()
css=re.sub(r"url\('([^']+)'\)",lambda m:"url('"+data(m[1])+"')" if (assets/m[1]).exists() else m[0],css)
js=(assets/'combat-feedback-1193a.js').read_text()
java=(root/'android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java').read_text()
parts=[]
for line in java.splitlines():
 if '__entityVisualBounds=' in line or 'function normalizeEntityVisual(' in line:
  m=re.match(r'\s*("(?:[^"\\]|\\.)*")\s*\+',line)
  if m:parts.append(json.loads(m[1]))
normalizer='\n'.join(parts)
images={k:data(v) for k,v in {'background':'level_snapshots/level_0.webp','player':'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png','lucia':'LUCIA_LUC_OVERLAY.png','entity':'entity/hound.webp'}.items()}
page='''<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Backroom · Combat contact review</title><style>
*{box-sizing:border-box}body{margin:0;background:#080b0e;color:#edf1f3;font:14px system-ui,sans-serif}main{max-width:440px;margin:0 auto;padding:16px 10px}header{padding:6px 4px 14px}header small{color:#a99b72;letter-spacing:.16em;font-size:10px}h1{font-size:20px;margin:6px 0}p{color:#a8b0b5;line-height:1.5;margin:6px 0;font-size:12px}.snapshot{position:relative;height:300px;overflow:hidden;border:1px solid #3a3c32;border-radius:10px;background:#10130f}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character,.snapshot .snapshot-lucia{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:100%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none}.snapshot .snapshot-entity{position:absolute;left:0;bottom:0;height:97%;width:auto;max-width:55%;object-fit:contain;object-position:left bottom;transform-origin:left bottom;z-index:2;pointer-events:none}.controls{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}button{padding:11px 7px;border:1px solid #454739;border-radius:7px;background:#20261f;color:#e7eddf;font:600 12px system-ui;cursor:pointer}button:disabled{opacity:.45;cursor:wait}button.wide{grid-column:1/-1}.status{font-size:12px;color:#c3b78f;min-height:20px;margin:10px 2px}.timing{padding:12px;border:1px solid #30372f;border-radius:8px;margin-top:12px;font-size:12px;line-height:1.7;color:#b5beb3}.combat-feedback-panel{width:100%;margin-top:12px!important}.combat-feedback-entries{max-height:150px}.combat-feedback-entry{font-size:12px}.party-grid{display:flex;gap:8px;margin-top:10px}.party-member{position:relative;width:72px;padding:5px;background:#131b17;border:1px solid #343d35;border-radius:6px;text-align:center;font-size:10px}.party-member img{width:100%;height:58px;object-fit:cover}.party-member strong{display:block}.party-grid:empty{display:none}
'''+css+'''</style><main><header><small>BACKROOM · COMBAT REVIEW</small><h1>Va chạm & né tránh</h1><p>Chọn một tình huống để xem chuyển động overlay và phản hồi tại mục tiêu.</p></header><div id="snapshot" class="snapshot"></div><div id="pokerDiceModal" hidden></div><div id="partyCards" class="party-grid"></div><div class="status" id="reviewStatus">Sẵn sàng</div><div class="controls">
<button data-demo="hit">Player đánh trúng</button><button data-demo="crit">Player chí mạng</button><button data-demo="entityHit">Entity đánh trúng</button><button data-demo="entityMiss">Player né · MISS</button><button data-demo="playerMiss">Entity né · MISS</button><button data-demo="lucia">Lucia đánh trúng</button><button data-demo="luciaHit">Lucia nhận đòn</button><button data-demo="tick">Poison định kỳ</button><button class="wide" data-demo="sequence">Xem chuỗi: trúng → né → phản công</button><button class="wide" id="reset">Đặt lại</button></div><div class="timing">Tấn công: 180 ms lao tới · 70 ms giữ · 230 ms trở về<br>Trúng đòn: 400 ms · Né: 420 ms · Impact: 320 ms<br>Số damage / MISS: 1,2 giây</div></main><script>
const ART='''+json.dumps(images)+''';
var state={combat:{active:true,encounterId:'review',entityKey:'hound',entityName:'Hound',round:1},partyDetails:{members:[{id:'cao_minh',name:'Cao Minh'},{id:'lucia',name:'Lucia'}]}};
var counter=0;var __entityOverlay={key:'hound'};
'''+normalizer+'''
function setup(){
 const box=document.getElementById('snapshot');box.innerHTML='';
 for(const [key,cls] of [['background','snapshot-bg'],['entity','snapshot-entity'],['player','snapshot-character'],['lucia','snapshot-lucia']]){const img=document.createElement('img');img.className=cls;img.src=ART[key];if(key==='entity'){img.alt='hound';img.onload=()=>normalizeEntityVisual(img)}box.appendChild(img)}
 box.dataset.combatFocus='cao_minh';
}
setup();
window.backroomTurn=function(json){state=JSON.parse(json)};
window.ensureDirectCombatDice=function(){document.querySelectorAll('[data-demo]').forEach(b=>b.disabled=!!window.__combatFeedbackBusy);document.getElementById('reviewStatus').textContent=window.__combatFeedbackBusy?'Đang trình diễn…':'Sẵn sàng'};
</script><script>'''+js+'''</script><script>
function reviewEvent(target,actorId,outcome='hit',critical=false){return {target,actorId,outcome,critical,text:outcome==='miss'?'MISS':'-24 HP',flash:outcome==='hit',phase:target==='actor'?'entity':'actor',sourceName:actorId==='lucia'?'Lucia':'Cao Minh',skill:'đánh thường',entityName:'Hound',remainingHp:110,maxHp:134}}
window.runDemo=function(kind){
 const c={active:true,encounterId:'review',entityKey:'hound',entityName:'Hound',round:++counter};
 let events=[],turns=[];
 const cue=(id,at)=>({kind:id==='entity'?'entity':'actor',actorId:id==='entity'?'':id,actorName:id==='lucia'?'Lucia':'Cao Minh',eventIndex:at,entityName:'Hound'});
 if(kind==='sequence'){events=[reviewEvent('entity','cao_minh'),reviewEvent('entity','cao_minh','miss'),reviewEvent('actor','cao_minh')];turns=[cue('cao_minh',0),cue('entity',2)]}
 else if(kind==='tick'){events=[{...reviewEvent('entity','cao_minh','tick'),status:'Trúng độc',text:'-3 HP'}];turns=[cue('cao_minh',0)]}
 else if(kind==='luciaHit'){events=[reviewEvent('actor','lucia')];turns=[cue('lucia',0)]}
 else {const target=kind.startsWith('entity')?'actor':'entity',id=kind==='lucia'?'lucia':'cao_minh';events=[reviewEvent(target,id,kind.endsWith('Miss')?'miss':'hit',kind==='crit')];turns=[cue(target==='actor'?'entity':id,0)]}
 window.backroomTurn(JSON.stringify({combat:c,partyDetails:state.partyDetails,combatFeedback:{id:'review:'+counter,encounterId:'review',events,turns}}));
};
document.querySelectorAll('[data-demo]').forEach(b=>b.onclick=()=>runDemo(b.dataset.demo));
document.getElementById('reset').onclick=()=>{window.backroomTurn(JSON.stringify({combat:null}));setup();state={combat:{active:true,encounterId:'review',entityKey:'hound',entityName:'Hound',round:1},partyDetails:{members:[{id:'cao_minh',name:'Cao Minh'},{id:'lucia',name:'Lucia'}]}};document.getElementById('reviewStatus').textContent='Sẵn sàng'};
</script></html>'''
out=args.output.resolve();out.write_text(page);print(out, out.stat().st_size)
