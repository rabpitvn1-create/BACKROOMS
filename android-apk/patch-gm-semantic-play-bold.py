"""Final GM semantic typography: authority-first local spans, no GM/API contract."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
PLAY_BOLD = ROOT / "app/src/main/assets/fonts/Play-Bold.ttf"
COMBAT_FEEDBACK_CSS = ROOT / "app/src/main/assets/combat-feedback-1193a.css"
MARKER = "GM_SEMANTIC_PLAY_BOLD_R04"

if not PLAY_BOLD.is_file() or PLAY_BOLD.stat().st_size <= 0:
    raise RuntimeError("Play-Bold.ttf is missing or empty")

def clean(values):
    return sorted({value.strip() for value in values if value and value.strip()}, key=lambda value: (-len(value), value.casefold()))

combat = (CORE / "CombatRuntime.kt").read_text(encoding="utf-8")
entity_names = clean(re.findall(r'Profile\(\s*"[^"]+"\s*,\s*"([^"]+)"', combat))

item_name_set = set()
equipment_path = CORE / "CharacterEquipmentSystem.kt"
if equipment_path.is_file():
    equipment = equipment_path.read_text(encoding="utf-8")
    item_name_set.update(re.findall(r'EquipmentDefinition\(\s*id\s*=\s*[^,\n]+,\s*name\s*=\s*"([^"]+)"', equipment, flags=re.MULTILINE))
healing_path = CORE / "HealingItems.kt"
if healing_path.is_file():
    healing = healing_path.read_text(encoding="utf-8")
    item_name_set.update(re.findall(r'const val [A-Z0-9_]+_NAME\s*=\s*"([^"]+)"', healing))

knowledge_path = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
knowledge = {"records": []}
if knowledge_path.is_file():
    knowledge = json.loads(knowledge_path.read_text(encoding="utf-8"))
    for record in knowledge.get("records", []):
        if record.get("domain") == "ITEM" and record.get("kind") == "item":
            tags = record.get("tags") or []
            if tags:
                item_name_set.add(str(tags[0]))
item_names = clean(item_name_set)

skill_path = CORE / "CompanionSkillCatalog.kt"
skills = skill_path.read_text(encoding="utf-8") if skill_path.is_file() else ""
skill_names = clean(re.findall(r'\bs\("([^"]+)"\s*,', skills))

character_names = set()
for record in knowledge.get("records", []):
    if record.get("domain") == "CHARACTER" and record.get("kind") == "runtime-card":
        display = str(record.get("text") or "").split(":", 1)[0].strip()
        for alias in display.split("/"):
            if alias.strip():
                character_names.add(alias.strip())
for path in CORE.glob("*.kt"):
    source = path.read_text(encoding="utf-8")
    for match in re.finditer(r'CharacterState\s*\((.{0,1800}?)\)', source, flags=re.DOTALL):
        name = re.search(r'\bname\s*=\s*"([^"]+)"', match.group(1))
        if name:
            character_names.add(name.group(1))
    if "CharacterState" in source:
        for raw_name in re.findall(r'const val NAME\s*=\s*"((?:\\.|[^"\\])*)"', source):
            try:
                character_names.add(json.loads('"' + raw_name + '"'))
            except json.JSONDecodeError:
                raise RuntimeError(f"Invalid character NAME literal in {path.name}: {raw_name}")

# Legacy opening prose has characters that predate structured state. Discover actor names
# by grammar instead of maintaining a typography name list.
prologue_path = ROOT / "cao-minh-prologue.txt"
if prologue_path.is_file():
    prologue = prologue_path.read_text(encoding="utf-8")
    actor_verbs = re.compile(r'^\s*(?:đứng|nhìn|nói|hỏi|đáp|cười|quát|bước|siết|gật|lắc|thở|động|chờ|uống|ngẩng|cúi|lao|xoay|giơ|khuỵu|bật)\b', re.IGNORECASE)
    tokens = list(re.finditer(r"[\wÀ-ỹĐđ'’\"-]+", prologue, flags=re.UNICODE))
    for index, token in enumerate(tokens):
        word = token.group(0)
        if not word or not word[0].isupper():
            continue
        end_index = index
        while end_index + 1 < len(tokens) and end_index - index < 3:
            gap = prologue[tokens[end_index].end():tokens[end_index + 1].start()]
            next_word = tokens[end_index + 1].group(0)
            if not gap.isspace() or not next_word or not next_word[0].isupper():
                break
            end_index += 1
        if end_index == index:
            continue
        candidate = prologue[token.start():tokens[end_index].end()].strip()
        right = prologue[tokens[end_index].end():tokens[end_index].end() + 48]
        if actor_verbs.match(right):
            character_names.add(candidate)
character_names = clean(character_names)

if not character_names or not entity_names or not item_names or not skill_names:
    raise RuntimeError("Final semantic compatibility lexicon is incomplete")

compatibility_terms = {
    "character": character_names,
    "entity": entity_names,
    "item": item_names,
    "skill": skill_names,
}
terms_json = json.dumps(compatibility_terms, ensure_ascii=False, separators=(",", ":"))

feedback_css = COMBAT_FEEDBACK_CSS.read_text(encoding="utf-8")
for effect, color in {
    "critical": "#ffd166", "bleed": "#ff7777", "poison": "#c38cff",
    "stun": "#ffe066", "armor": "#72c7ff", "disorient": "#8fe3d1",
}.items():
    if color not in feedback_css:
        raise RuntimeError(f"Combat feedback color contract missing for {effect}: {color}")

html = INDEX.read_text(encoding="utf-8")
if MARKER in html:
    print("GM semantic local spans already applied.")
    raise SystemExit(0)

style = r'''<style id="gmSemanticPlayBoldStyle">
/* GM_SEMANTIC_PLAY_BOLD_R04 */
@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('fonts/Play-Bold.ttf') format('truetype');font-display:swap}
.gm-semantic{font-family:'Play',"Pretendard Std",system-ui,sans-serif;font-weight:700}
</style>'''
if html.count("</head>") != 1:
    raise RuntimeError("Final index.html must contain exactly one </head>")
html = html.replace("</head>", style + "\n</head>", 1)

runtime = r'''const SEMANTIC_COMPATIBILITY_TERMS=__TERMS__;
const SEMANTIC_KINDS=["character","entity","item","skill"];
function semanticKind(value){return SEMANTIC_KINDS.includes(value)?value:""}
function semanticFold(value){return String(value==null?"":value).toLocaleLowerCase("vi-VN")}
function semanticWordChar(ch){if(!ch)return false;if(/[0-9_]/.test(ch))return true;return ch.toLocaleLowerCase("vi-VN")!==ch.toLocaleUpperCase("vi-VN")}
function semanticBoundary(source,start,end){if(semanticWordChar(source[start])&&semanticWordChar(source[start-1]))return false;if(semanticWordChar(source[end-1])&&semanticWordChar(source[end]))return false;return true}
function semanticAddTerm(map,value,kind,priority){const text=String(value==null?"":value).trim();if(!semanticKind(kind)||text.length<2||text.length>160)return;const key=kind+"\u0000"+semanticFold(text),old=map.get(key);if(!old||priority>old.priority)map.set(key,{text,key:semanticFold(text),kind,priority})}
function semanticAddNamed(map,value,kind,priority){if(typeof value==="string"){semanticAddTerm(map,value,kind,priority);return}if(!value||typeof value!=="object")return;semanticAddTerm(map,value.name||value.displayName||value.label,kind,priority)}
function semanticCollectContainer(map,value,kind,priority){if(Array.isArray(value)){value.forEach(v=>semanticCollectContainer(map,v,kind,priority));return}if(!value||typeof value!=="object"){semanticAddNamed(map,value,kind,priority);return}if(value.name||value.displayName||value.label){semanticAddNamed(map,value,kind,priority);return}Object.keys(value).forEach(key=>semanticCollectContainer(map,value[key],kind,priority))}
function semanticCollectMember(map,member,priority){if(!member||typeof member!=="object")return;semanticAddNamed(map,member,"character",priority);semanticCollectContainer(map,member.inventory,"item",priority);semanticCollectContainer(map,member.equipmentItems,"item",priority);semanticCollectContainer(map,member.skills,"skill",priority)}
function semanticCollectState(map,s,priority){if(!s||typeof s!=="object")return;semanticAddNamed(map,s.player,"character",priority);if(Array.isArray(s.party))s.party.forEach(member=>typeof member==="object"?semanticCollectMember(map,member,priority):semanticAddNamed(map,member,"character",priority));if(s.partyDetails&&Array.isArray(s.partyDetails.members))s.partyDetails.members.forEach(member=>semanticCollectMember(map,member,priority));semanticCollectContainer(map,s.inventory,"item",priority);semanticCollectContainer(map,s.equipmentItems,"item",priority);semanticCollectContainer(map,s.worldItems,"item",priority);if(s.flags){semanticCollectContainer(map,s.flags.worldItems,"item",priority);semanticCollectContainer(map,s.flags.survivorRegistry,"character",priority);semanticCollectContainer(map,s.flags.survivorsConfirmed,"character",priority);semanticCollectContainer(map,s.flags.entityRegistry,"entity",priority);semanticCollectContainer(map,s.flags.entitiesConfirmedLocal,"entity",priority)}if(s.combat)semanticAddTerm(map,s.combat.entityName||s.combat.name||s.combat.displayName,"entity",priority)}
function semanticNegative(before,after){const set=new Set(["backrooms","game master","black blood","level"]);[before,after].forEach(s=>{if(!s||typeof s!=="object")return;[s.location,s.title,s.level&&s.level.name,s.flags&&s.flags.currentLevel&&s.flags.currentLevel.name].forEach(v=>{const x=semanticFold(v).trim();if(x)set.add(x)})});return set}
function semanticTerms(before,after,legacy){const map=new Map();Object.keys(SEMANTIC_COMPATIBILITY_TERMS).forEach(kind=>(SEMANTIC_COMPATIBILITY_TERMS[kind]||[]).forEach(text=>semanticAddTerm(map,text,kind,200)));semanticCollectState(map,before,250);semanticCollectState(map,after,300);if(legacy&&typeof legacy==="object")SEMANTIC_KINDS.forEach(kind=>{if(Array.isArray(legacy[kind]))legacy[kind].forEach(text=>semanticAddTerm(map,text,kind,240))});return Array.from(map.values())}
function semanticExactCandidates(source,terms){const lower=semanticFold(source),hits=[];terms.forEach(term=>{let at=0;while((at=lower.indexOf(term.key,at))!==-1){const end=at+term.text.length;if(semanticBoundary(source,at,end))hits.push({start:at,end,kind:term.kind,priority:term.priority,source:"authority"});at+=Math.max(1,term.key.length)}});return hits}
function semanticUpperWord(word){if(!word)return false;const first=word.charAt(0);return first.toLocaleUpperCase("vi-VN")===first&&first.toLocaleLowerCase("vi-VN")!==first}
function semanticContextKind(left,right){if(/(?:kỹ năng|chiêu|skill|thi triển|kích hoạt)\s*$/.test(left))return "skill";if(/(?:vật phẩm|trang bị|thanh kiếm|vũ khí|súng|áo giáp|nhẫn|nhặt|cầm|rút)\s*$/.test(left))return "item";if(/(?:entity|thực thể|sinh vật|quái)\s*$/.test(left))return "entity";if(/(?:người sống sót|nhân vật|đồng đội|cô gái|người đàn ông|người phụ nữ|tên)\s*$/.test(left))return "character";if(/^(?:đứng|nhìn|nói|hỏi|đáp|cười|quát|bước|siết|gật|lắc|thở|động|chờ|uống|ngẩng|cúi|lao|xoay|giơ|bật)\b/.test(right.trim()))return "character";return ""}
function semanticContextCandidates(source,negative){const hits=[],tokens=[],rx=/[A-Za-zÀ-ỹĐđ][A-Za-zÀ-ỹĐđ'’"-]*/g;let m;while((m=rx.exec(source)))tokens.push({text:m[0],start:m.index,end:m.index+m[0].length});for(let i=0;i<tokens.length;i++){if(!semanticUpperWord(tokens[i].text))continue;let j=i;while(j+1<tokens.length&&j-i<3&&source.slice(tokens[j].end,tokens[j+1].start).trim()===""&&semanticUpperWord(tokens[j+1].text))j++;for(let k=j;k>=i;k--){const start=tokens[i].start,end=tokens[k].end,value=source.slice(start,end),key=semanticFold(value);if(negative.has(key))continue;const left=semanticFold(source.slice(Math.max(0,start-72),start)),right=semanticFold(source.slice(end,Math.min(source.length,end+48))),kind=semanticContextKind(left,right);if(kind){hits.push({start,end,kind,priority:80,source:"context"});break}}}return hits}
function semanticSelect(source,hits){hits.sort((a,b)=>a.start-b.start||(b.end-b.start)-(a.end-a.start)||b.priority-a.priority);const spans=[];let cursor=0;hits.forEach(hit=>{if(hit.start<cursor||hit.start<0||hit.end<=hit.start||hit.end>source.length)return;spans.push({start:hit.start,end:hit.end,kind:hit.kind,source:hit.source});cursor=hit.end});return spans}
function semanticResolve(source,before,after,legacy){const text=String(source||"");const terms=semanticTerms(before,after,legacy),hits=semanticExactCandidates(text,terms).concat(semanticContextCandidates(text,semanticNegative(before,after)));return semanticSelect(text,hits)}
function semanticPrepareNextState(before,json){const next=JSON.parse(json),oldLength=before&&Array.isArray(before.log)?before.log.length:0;if(Array.isArray(next.log))next.log.forEach((entry,index)=>{if(!entry||entry.role==="player"||Array.isArray(entry.semanticSpans))return;if(index<oldLength)entry.semanticSpans=semanticResolve(entry.text,null,null,entry.semantic);else entry.semanticSpans=semanticResolve(entry.text,before,next,entry.semantic);entry.semanticVersion=2});return next}
function normalizedSemanticSpans(entry){if(!entry||entry.role==="player")return [];if(!Array.isArray(entry.semanticSpans)){entry.semanticSpans=semanticResolve(entry.text,null,null,entry.semantic);entry.semanticVersion=2}const source=String(entry.text||""),spans=[];let cursor=0;entry.semanticSpans.forEach(raw=>{const start=Number(raw&&raw.start),end=Number(raw&&raw.end),kind=semanticKind(raw&&raw.kind);if(!kind||!Number.isInteger(start)||!Number.isInteger(end)||start<cursor||end<=start||end>source.length)return;spans.push({start,end,kind});cursor=end});return spans}
function renderSemanticText(entry){const source=String(entry&&entry.text||"");if(!entry||entry.role==="player")return esc(source);const spans=normalizedSemanticSpans(entry);if(!spans.length)return esc(source);let out="",cursor=0;spans.forEach(span=>{if(span.start>cursor)out+=esc(source.slice(cursor,span.start));out+="<span class='gm-semantic gm-semantic-"+span.kind+"' data-semantic-kind='"+span.kind+"'>"+esc(source.slice(span.start,span.end))+"</span>";cursor=span.end});if(cursor<source.length)out+=esc(source.slice(cursor));return out}
'''.replace("__TERMS__", terms_json)

render_anchor = "function levelHeader(s){"
if render_anchor not in html:
    raise RuntimeError("Final render anchor missing")
html = html.replace(render_anchor, runtime + "\n" + render_anchor, 1)
if html.count('"+esc(x.text)+"') != 1:
    raise RuntimeError(f"Final text renderer anchor expected once, found {html.count(chr(34) + '+esc(x.text)+' + chr(34))}")
html = html.replace('"+esc(x.text)+"', '"+renderSemanticText(x)+"', 1)
turn_anchor = 'window.backroomTurn=json=>{state=JSON.parse(json);'
if html.count(turn_anchor) != 1:
    raise RuntimeError(f"Primary backroomTurn anchor expected once, found {html.count(turn_anchor)}")
html = html.replace(turn_anchor, 'window.backroomTurn=json=>{state=semanticPrepareNextState(state,json);', 1)

for required in (
    MARKER, "fonts/Play-Bold.ttf", ".gm-semantic{", "SEMANTIC_COMPATIBILITY_TERMS=",
    "function semanticPrepareNextState(before,json)", "function renderSemanticText(entry)",
    '"+renderSemanticText(x)+"', "state=semanticPrepareNextState(state,json)", "entry.semanticSpans",
):
    if required not in html:
        raise RuntimeError("GM semantic local-span contract missing: " + required)
for forbidden in (
    "gm-semantic-location", "gm-semantic-status", "gm-semantic-effect", r"\p{L}", r"\p{N}",
    "gmSemanticPlayBoldRuntime", "__gmSemanticPlayBoldInstalled", "collectTerms(extraSemantic)",
    'document.querySelectorAll(".message.gm .text")', "termsVersion(terms)",
):
    if forbidden in html:
        raise RuntimeError("Retired semantic re-scan/compat syntax survived: " + forbidden)
semantic_css = style.split("/* GM_SEMANTIC_PLAY_BOLD_R04 */", 1)[1]
if "color:" in semantic_css:
    raise RuntimeError("GM semantic typography must not introduce semantic colors")
INDEX.write_text(html, encoding="utf-8")

print(
    "GM semantic local spans applied: "
    f"{len(character_names)} character, {len(entity_names)} Entity, "
    f"{len(item_names)} item/equipment, {len(skill_names)} skill compatibility terms; GM/API unchanged."
)
