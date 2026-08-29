from pathlib import Path
import base64
import hashlib

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"
FONT = ASSETS / "fonts/FVF_Fernando_08.ttf"
FONT_PARTS = ROOT / "font-fvf-fernando-08.b64.parts"
FONT_SHA256 = "9954a134f5a3da8ca497b2cae340e75ca431e6e351010c28e6a2724395999010"
MARKER = "RPG_COMBAT_TEXT_STYLE_V1"


def font_matches() -> bool:
    return FONT.is_file() and hashlib.sha256(FONT.read_bytes()).hexdigest() == FONT_SHA256


if not font_matches():
    parts = sorted(FONT_PARTS.glob("*.part"))
    if not parts:
        raise RuntimeError("FVF Fernando 08 source parts are missing")
    try:
        encoded = "".join(part.read_text(encoding="ascii").strip() for part in parts)
        font_raw = base64.b64decode(encoded, validate=True)
    except Exception as exc:
        raise RuntimeError("FVF Fernando 08 source parts are invalid") from exc
    if hashlib.sha256(font_raw).hexdigest() != FONT_SHA256:
        raise RuntimeError("FVF Fernando 08 reconstructed font does not match the user-supplied font")
    FONT.parent.mkdir(parents=True, exist_ok=True)
    FONT.write_bytes(font_raw)

font_raw = FONT.read_bytes()
if len(font_raw) < 10000:
    raise RuntimeError("FVF Fernando 08 font asset is too small")
if font_raw[:4] not in (b"\x00\x01\x00\x00", b"OTTO"):
    raise RuntimeError("FVF Fernando 08 asset is not a supported OpenType/TrueType font")
if hashlib.sha256(font_raw).hexdigest() != FONT_SHA256:
    raise RuntimeError("FVF Fernando 08 font asset does not match the user-supplied font")

html = INDEX.read_text(encoding="utf-8")
if MARKER not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("RPG typography finalizer expected exactly one closing body tag")

    payload = r'''
<style id="rpgCombatTextStyle">
/* RPG_COMBAT_TEXT_STYLE_V1 */
@font-face{font-family:'FVF Fernando 08';src:url('fonts/FVF_Fernando_08.ttf') format('truetype');font-style:normal;font-weight:400;font-display:swap}
.rpg-item-name,.rpg-hp-loss,.rpg-hp-heal{font-family:'FVF Fernando 08',system-ui,sans-serif;font-style:normal;font-weight:400;font-synthesis:none;letter-spacing:.02em}
.rpg-item-name{color:#46F5C6;text-shadow:0 0 8px rgba(70,245,198,.28)}
.rpg-hp-loss{color:#FF4D5A;text-shadow:0 0 8px rgba(255,77,90,.24);white-space:nowrap}
.rpg-hp-heal{color:#59FF82;text-shadow:0 0 8px rgba(89,255,130,.24);white-space:nowrap}
.rpg-skill-name{font-family:inherit;color:inherit;font-weight:800;text-shadow:none}
</style>
<script>
(function(){
  if(window.__rpgCombatTextStyleV1)return;
  window.__rpgCombatTextStyleV1=true;
  var decorating=false;

  function currentState(){
    try{return typeof state!=='undefined'&&state?state:null}catch(_){return null}
  }
  function uniqueNames(values){
    var seen=new Set(),out=[];
    (values||[]).forEach(function(value){
      var name=String(value||'').trim();if(!name)return;
      var key=name.toLocaleLowerCase('vi-VN');
      if(seen.has(key))return;seen.add(key);out.push(name);
    });
    return out.sort(function(a,b){return b.length-a.length});
  }
  function worldItemNames(){
    var current=currentState(),flags=current&&current.flags;
    var items=flags&&Array.isArray(flags.worldItems)?flags.worldItems:[];
    return uniqueNames(items.map(function(item){return item&&item.name}));
  }
  function skillNames(){
    var current=currentState(),details=current&&current.partyDetails;
    var members=details&&Array.isArray(details.members)?details.members:[];
    var names=[];
    members.forEach(function(member){
      var skills=member&&Array.isArray(member.skills)?member.skills:[];
      skills.forEach(function(skill){if(skill&&skill.name)names.push(skill.name)});
    });
    return uniqueNames(names);
  }
  function gainNames(text){
    var match=/^Gain\s+(.+?)\s*×\s*\d+\s+Item\b/i.exec(String(text||'').trim());
    return uniqueNames(match?[match[1]]:[]);
  }
  function addNamedRanges(text,names,type,priority,ranges){
    var lower=text.toLocaleLowerCase('vi-VN');
    names.forEach(function(name){
      var needle=name.toLocaleLowerCase('vi-VN'),offset=0,index;
      while(needle&&(index=lower.indexOf(needle,offset))!==-1){
        ranges.push({start:index,end:index+name.length,type:type,priority:priority});
        offset=index+name.length;
      }
    });
  }
  function decorateText(article,textEl){
    if(!textEl||textEl.dataset.rpgDecorated==='1')return;
    var text=String(textEl.textContent||'');
    textEl.dataset.rpgDecorated='1';
    if(!text)return;

    var ranges=[],hp=/[+-]\s*\d+(?:[.,]\d+)?\s*HP\b/gi,match;
    while((match=hp.exec(text))){
      ranges.push({start:match.index,end:match.index+match[0].length,type:match[0].trim().charAt(0)==='+'?'heal':'loss',priority:30});
    }

    var isPlayer=article.classList.contains('player');
    var role=String(article.querySelector('.role')&&article.querySelector('.role').textContent||'').trim().toUpperCase();
    var isGain=article.classList.contains('gain')||role==='GAIN';
    if(!isPlayer){
      addNamedRanges(text,worldItemNames(),'item',20,ranges);
      if(isGain)addNamedRanges(text,gainNames(text),'item',25,ranges);
    }
    addNamedRanges(text,skillNames(),'skill',10,ranges);
    if(!ranges.length)return;

    ranges.sort(function(a,b){return a.start-b.start||b.priority-a.priority||(b.end-b.start)-(a.end-a.start)});
    var chosen=[];
    ranges.forEach(function(range){
      var overlaps=chosen.some(function(keep){return range.start<keep.end&&range.end>keep.start});
      if(!overlaps)chosen.push(range);
    });
    chosen.sort(function(a,b){return a.start-b.start});

    var frag=document.createDocumentFragment(),cursor=0;
    chosen.forEach(function(range){
      if(range.start>cursor)frag.appendChild(document.createTextNode(text.slice(cursor,range.start)));
      var span=document.createElement('span');
      span.className=range.type==='item'?'rpg-item-name':range.type==='heal'?'rpg-hp-heal':range.type==='loss'?'rpg-hp-loss':'rpg-skill-name';
      span.textContent=text.slice(range.start,range.end);frag.appendChild(span);cursor=range.end;
    });
    if(cursor<text.length)frag.appendChild(document.createTextNode(text.slice(cursor)));
    textEl.textContent='';textEl.appendChild(frag);
  }
  function decorateRpgText(){
    if(decorating)return;decorating=true;
    try{
      document.querySelectorAll('#log .message').forEach(function(article){decorateText(article,article.querySelector('.text'))});
    }finally{decorating=false}
  }

  var previousRender=window.render;
  if(typeof previousRender==='function')window.render=function(){var value=previousRender.apply(this,arguments);decorateRpgText();return value};
  var previousTurn=window.backroomTurn;
  if(typeof previousTurn==='function')window.backroomTurn=function(json){var value=previousTurn.call(this,json);decorateRpgText();return value};

  var log=document.getElementById('log');
  if(log)new MutationObserver(function(){if(!decorating)requestAnimationFrame(decorateRpgText)}).observe(log,{childList:true,subtree:true});
  window.decorateRpgText=decorateRpgText;
  decorateRpgText();
})();
</script>
'''
    html = html.replace("</body>", payload + "\n</body>", 1)
    INDEX.write_text(html, encoding="utf-8")

final_html = INDEX.read_text(encoding="utf-8")
for marker in (
    "RPG_COMBAT_TEXT_STYLE_V1",
    "fonts/FVF_Fernando_08.ttf",
    "#46F5C6",
    "#FF4D5A",
    "#59FF82",
    "rpg-item-name",
    "rpg-hp-loss",
    "rpg-hp-heal",
    "rpg-skill-name",
    "worldItemNames()",
    "gainNames(text)",
    "skillNames()",
    "currentState()",
    "/[+-]\\s*\\d+(?:[.,]\\d+)?\\s*HP\\b/gi",
    "new MutationObserver",
):
    if marker not in final_html:
        raise RuntimeError("RPG typography final contract missing: " + marker)

print("RPG combat typography finalized: FVF Fernando 08 marks discovered/gained item names and signed HP deltas; skill names stay system-font bold.")
