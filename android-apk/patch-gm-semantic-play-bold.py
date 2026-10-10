"""Final GM semantic typography: Play Bold only for character/entity/item/skill names.

Runs after the complete runtime patch chain. It does not mutate GameState, save data,
GM prompts, combat math, or combat feedback colors.
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
PLAY_BOLD = ROOT / "app/src/main/assets/fonts/Play-Bold.ttf"
COMBAT_FEEDBACK_CSS = ROOT / "app/src/main/assets/combat-feedback-1193a.css"
MARKER = "GM_SEMANTIC_PLAY_BOLD_R03"

if not PLAY_BOLD.is_file() or PLAY_BOLD.stat().st_size <= 0:
    raise RuntimeError("Play-Bold.ttf is missing or empty")

html = INDEX.read_text(encoding="utf-8")
if MARKER in html:
    print("GM semantic Play Bold already applied.")
    raise SystemExit(0)

def clean(values):
    return sorted({value.strip() for value in values if value and value.strip()}, key=lambda value: (-len(value), value.casefold()))

combat = (CORE / "CombatRuntime.kt").read_text(encoding="utf-8")
entity_names = clean(re.findall(r'Profile\(\s*"[^"]+"\s*,\s*"([^"]+)"', combat))

equipment_path = CORE / "CharacterEquipmentSystem.kt"
equipment = equipment_path.read_text(encoding="utf-8") if equipment_path.is_file() else ""
item_name_set = set(re.findall(
    r'EquipmentDefinition\(\s*id\s*=\s*[^,\n]+,\s*name\s*=\s*"([^"]+)"',
    equipment,
    flags=re.MULTILINE,
))
healing_path = CORE / "HealingItems.kt"
if healing_path.is_file():
    healing = healing_path.read_text(encoding="utf-8")
    item_name_set.update(re.findall(r'const val [A-Z0-9_]+_NAME\s*=\s*"([^"]+)"', healing))

knowledge_path = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
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
if knowledge_path.is_file():
    for record in knowledge.get("records", []):
        if record.get("domain") != "CHARACTER" or record.get("kind") != "runtime-card":
            continue
        text = str(record.get("text") or "").strip()
        display = text.split(":", 1)[0].strip()
        if display:
            for alias in display.split("/"):
                alias = alias.strip()
                if alias:
                    character_names.add(alias)
player_match = re.search(r'player:\{name:"([^"]+)"', html)
if player_match:
    character_names.add(player_match.group(1))
for path in CORE.glob("*.kt"):
    source = path.read_text(encoding="utf-8")
    for match in re.finditer(r'CharacterState\s*\((.{0,1600}?)\)', source, flags=re.DOTALL):
        name = re.search(r'\bname\s*=\s*"([^"]+)"', match.group(1))
        if name:
            character_names.add(name.group(1))
    # Some canonical runtime characters intentionally route their display name
    # through a NAME constant (for example An Nhiên and Lucia "Lục").
    if "CharacterState" in source:
        for raw_name in re.findall(r'const val NAME\s*=\s*"((?:\\.|[^"\\])*)"', source):
            try:
                character_names.add(json.loads('"' + raw_name + '"'))
            except json.JSONDecodeError:
                raise RuntimeError(f"Invalid character NAME literal in {path.name}: {raw_name}")
prologue_path = ROOT / "cao-minh-prologue.txt"
if prologue_path.is_file():
    prologue = prologue_path.read_text(encoding="utf-8")
    for story_name in ("Lục Trầm", "Vô Lượng Đại Tôn Giả", "Vô Lượng"):
        if story_name in prologue:
            character_names.add(story_name)

character_names = clean(character_names)

if not character_names:
    raise RuntimeError("No canonical character display names found in final runtime")
if not entity_names:
    raise RuntimeError("No canonical Entity display names found in final CombatRuntime.kt")
if not skill_names:
    raise RuntimeError("No canonical character skill names found in final CompanionSkillCatalog.kt")
if not item_names:
    raise RuntimeError("No canonical equipment/item names found in final CharacterEquipmentSystem.kt")

# The finalized writer labels semantic names in the same JSON response as narration.
# A new name can therefore be highlighted on first appearance without adding it here.
main = MAIN.read_text(encoding="utf-8")
writer_sig = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
writer_start = main.find(writer_sig)
writer_end = main.find("\n  private ", writer_start + len(writer_sig))
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("Final writerPrompt boundary missing for semantic auto-label contract")
writer = main[writer_start:writer_end]
if "SEMANTIC AUTO LABEL CONTRACT:" not in writer:
    json_prompt = '      "JSON bắt buộc: '
    json_pos = writer.find(json_prompt)
    if json_pos < 0:
        raise RuntimeError("Final writerPrompt JSON contract anchor missing")
    semantic_line = (
        '      "SEMANTIC AUTO LABEL CONTRACT: Tự nhận diện mọi tên xuất hiện trong reply thuộc đúng 4 loại character/entity/item/skill, '
        'kể cả tên mới xuất hiện lần đầu. Bắt buộc thêm field top-level semantic gồm đúng bốn mảng character, entity, item, skill; '
        'mỗi mảng chứa chuỗi đúng nguyên văn như trong reply, không location, không trạng thái, không hiệu ứng DMG; không được bỏ sót tên thuộc 4 loại này. " +\n'
    )
    writer = writer[:json_pos] + semantic_line + writer[json_pos:]
    main = main[:writer_start] + writer + main[writer_end:]

helper_anchor = writer_sig
helper = r'''  private JSONObject sanitizeSemanticLabels(JSONObject generated, String reply) throws Exception {
    JSONObject source = generated.optJSONObject("semantic");
    JSONObject result = new JSONObject();
    String[] kinds = new String[] {"character", "entity", "item", "skill"};
    for (String kind : kinds) {
      JSONArray output = new JSONArray();
      JSONArray input = source != null ? source.optJSONArray(kind) : null;
      if (input != null) {
        for (int i = 0; i < input.length() && output.length() < 64; i++) {
          String value = input.optString(i, "").trim();
          if (value.length() < 2 || value.length() > 160 || !reply.contains(value)) continue;
          boolean duplicate = false;
          for (int j = 0; j < output.length(); j++) {
            if (value.equals(output.optString(j))) { duplicate = true; break; }
          }
          if (!duplicate) output.put(value);
        }
      }
      result.put(kind, output);
    }
    return result;
  }

'''
if "private JSONObject sanitizeSemanticLabels(" not in main:
    helper_pos = main.find(helper_anchor)
    if helper_pos < 0:
        raise RuntimeError("Final writerPrompt helper anchor missing")
    main = main[:helper_pos] + helper + main[helper_pos:]

gm_log_old = '          log.put(new JSONObject().put("role", "gm").put("text", reply));'
gm_log_new = '          log.put(new JSONObject().put("role", "gm").put("text", reply).put("semantic", sanitizeSemanticLabels(generated, reply)));'
if gm_log_new not in main:
    if main.count(gm_log_old) != 1:
        raise RuntimeError(f"GM log append anchor expected once, found {main.count(gm_log_old)}")
    main = main.replace(gm_log_old, gm_log_new, 1)

for required in (
    "SEMANTIC AUTO LABEL CONTRACT:",
    "private JSONObject sanitizeSemanticLabels(",
    '.put("semantic", sanitizeSemanticLabels(generated, reply))',
):
    if required not in main:
        raise RuntimeError("MainActivity semantic auto-label contract missing: " + required)
MAIN.write_text(main, encoding="utf-8")

# Preserve the existing combat-feedback palette as the only semantic color system.
feedback_css = COMBAT_FEEDBACK_CSS.read_text(encoding="utf-8")
required_effect_colors = {
    "critical": "#ffd166",
    "bleed": "#ff7777",
    "poison": "#c38cff",
    "stun": "#ffe066",
    "armor": "#72c7ff",
    "disorient": "#8fe3d1",
}
for effect, color in required_effect_colors.items():
    if color not in feedback_css:
        raise RuntimeError(f"Combat feedback color contract missing for {effect}: {color}")

static_terms = {
    "character": character_names,
    "entity": entity_names,
    "item": item_names,
    "skill": skill_names,
}
if set(static_terms) != {"character", "entity", "item", "skill"}:
    raise RuntimeError("GM semantic scope must remain character/entity/item/skill only")

style = r'''<style id="gmSemanticPlayBoldStyle">
/* GM_SEMANTIC_PLAY_BOLD_R03 */
@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('fonts/Play-Bold.ttf') format('truetype');font-display:swap}
.gm-semantic{font-family:'Play',"Pretendard Std",system-ui,sans-serif;font-weight:700}
</style>'''

script = r'''<script id="gmSemanticPlayBoldRuntime">
(function(){
  "use strict";
  if(window.__gmSemanticPlayBoldInstalled)return;
  window.__gmSemanticPlayBoldInstalled=true;
  const STATIC_TERMS=__STATIC_TERMS__;

  function addTerm(map,value,kind){
    const text=String(value==null?"":value).trim();
    if(text.length<2)return;
    const key=text.toLocaleLowerCase("vi-VN");
    const prior=map.get(key);
    if(!prior||text.length>prior.text.length)map.set(key,{text:text,key:key,kind:kind});
  }

  function addItem(map,item){
    if(typeof item==="string")addTerm(map,item,"item");
    else if(item&&typeof item==="object")addTerm(map,item.name||item.displayName||item.label,"item");
  }

  function addMember(map,member){
    if(!member||typeof member!=="object")return;
    addTerm(map,member.name||member.displayName||member.label,"character");
    if(Array.isArray(member.inventory))member.inventory.forEach(function(item){addItem(map,item)});
    if(Array.isArray(member.equipmentItems))member.equipmentItems.forEach(function(item){addItem(map,item)});
    if(Array.isArray(member.skills))member.skills.forEach(function(skill){
      if(typeof skill==="string")addTerm(map,skill,"skill");
      else if(skill&&typeof skill==="object")addTerm(map,skill.name||skill.displayName||skill.label,"skill");
    });
  }

  function addWorldItems(map,items){
    if(!Array.isArray(items))return;
    items.forEach(function(item){if(!item||item.available===false)return;addItem(map,item)});
  }

  function addSemanticLabels(map,semantic){
    if(!semantic||typeof semantic!=="object")return;
    ["character","entity","item","skill"].forEach(function(kind){
      const values=semantic[kind];
      if(Array.isArray(values))values.forEach(function(value){addTerm(map,value,kind)});
    });
  }

  function collectTerms(extraSemantic){
    const map=new Map();
    Object.keys(STATIC_TERMS).forEach(function(kind){
      (STATIC_TERMS[kind]||[]).forEach(function(term){addTerm(map,term,kind)});
    });
    const s=typeof state!=="undefined"&&state?state:null;
    if(s){
      if(s.player){
        if(typeof s.player==="string")addTerm(map,s.player,"character");
        else addTerm(map,s.player.name||s.player.displayName||s.player.label,"character");
      }
      if(Array.isArray(s.party))s.party.forEach(function(member){
        if(typeof member==="string")addTerm(map,member,"character");else addMember(map,member);
      });
      if(s.partyDetails&&Array.isArray(s.partyDetails.members))s.partyDetails.members.forEach(function(member){addMember(map,member)});
      if(Array.isArray(s.inventory))s.inventory.forEach(function(item){addItem(map,item)});
      if(Array.isArray(s.equipmentItems))s.equipmentItems.forEach(function(item){addItem(map,item)});
      addWorldItems(map,s.worldItems);
      if(s.flags)addWorldItems(map,s.flags.worldItems);
      if(s.combat)addTerm(map,s.combat.entityName||s.combat.name||s.combat.displayName,"entity");
    }
    addSemanticLabels(map,extraSemantic);
    return Array.from(map.values()).sort(function(a,b){return b.text.length-a.text.length||a.text.localeCompare(b.text,"vi")});
  }

  function wordChar(ch){
    if(!ch)return false;
    if(/[0-9_]/.test(ch))return true;
    return ch.toLocaleLowerCase("vi-VN")!==ch.toLocaleUpperCase("vi-VN");
  }

  function boundaryOk(source,start,end){
    if(wordChar(source[start])&&wordChar(source[start-1]))return false;
    if(wordChar(source[end-1])&&wordChar(source[end]))return false;
    return true;
  }

  function nextMatch(source,lower,start,terms){
    let best=null;
    for(let i=0;i<terms.length;i++){
      const term=terms[i];
      let at=lower.indexOf(term.key,start);
      while(at!==-1&&!boundaryOk(source,at,at+term.text.length))at=lower.indexOf(term.key,at+1);
      if(at===-1)continue;
      if(!best||at<best.at||(at===best.at&&term.text.length>best.term.text.length))best={at:at,term:term};
    }
    return best;
  }

  function stableHash(value){
    let hash=2166136261;
    for(let i=0;i<value.length;i++){hash^=value.charCodeAt(i);hash=Math.imul(hash,16777619)}
    return (hash>>>0).toString(36);
  }

  function termsVersion(terms){
    return stableHash(terms.map(function(term){return term.kind+"\u0000"+term.key}).join("\u0001"));
  }

  function decorate(node,terms,version){
    if(!node)return;
    const source=node.textContent||"";
    const signature=stableHash(source+"\u0002"+version);
    if(node.dataset.gmSemanticPlay===signature)return;
    if(!source){node.dataset.gmSemanticPlay=signature;return}
    const lower=source.toLocaleLowerCase("vi-VN");
    let cursor=0,matched=false;
    const fragment=document.createDocumentFragment();
    while(cursor<source.length){
      const hit=nextMatch(source,lower,cursor,terms);
      if(!hit)break;
      if(hit.at>cursor)fragment.appendChild(document.createTextNode(source.slice(cursor,hit.at)));
      const end=hit.at+hit.term.text.length;
      const span=document.createElement("span");
      span.className="gm-semantic gm-semantic-"+hit.term.kind;
      span.dataset.semanticKind=hit.term.kind;
      span.textContent=source.slice(hit.at,end);
      fragment.appendChild(span);
      cursor=end;
      matched=true;
    }
    if(!matched){node.dataset.gmSemanticPlay=signature;return}
    if(cursor<source.length)fragment.appendChild(document.createTextNode(source.slice(cursor)));
    node.textContent="";
    node.appendChild(fragment);
    node.dataset.gmSemanticPlay=signature;
  }

  function applySemanticPlay(){
    const nodes=Array.from(document.querySelectorAll(".message.gm .text"));
    if(!nodes.length)return;
    const entries=typeof state!=="undefined"&&state&&Array.isArray(state.log)
      ? state.log.filter(function(entry){return entry&&entry.role!=="player"})
      : [];
    nodes.forEach(function(node,index){
      const entry=entries[index]||null;
      const terms=collectTerms(entry&&entry.semantic);
      if(!terms.length)return;
      decorate(node,terms,termsVersion(terms));
    });
  }

  const previousRender=window.render;
  if(typeof previousRender==="function"){
    window.render=function(){
      const result=previousRender.apply(this,arguments);
      applySemanticPlay();
      return result;
    };
  }

  const log=document.getElementById("log");
  if(log&&typeof MutationObserver==="function"){
    let scheduled=false;
    new MutationObserver(function(){
      if(scheduled)return;
      scheduled=true;
      requestAnimationFrame(function(){scheduled=false;applySemanticPlay()});
    }).observe(log,{childList:true,subtree:true,characterData:true});
  }

  applySemanticPlay();
})();
</script>'''.replace("__STATIC_TERMS__", json.dumps(static_terms, ensure_ascii=False, separators=(",", ":")))

if html.count("</head>") != 1 or html.count("</body>") != 1:
    raise RuntimeError("Final index.html must contain exactly one </head> and </body>")

html = html.replace("</head>", style + "\n</head>", 1)
html = html.replace("</body>", script + "\n</body>", 1)

for required in (
    MARKER,
    "fonts/Play-Bold.ttf",
    ".gm-semantic{",
    'document.querySelectorAll(".message.gm .text")',
    "dataset.semanticKind",
    'const STATIC_TERMS={"character":',
    '"entity":',
    '"item":',
    '"skill":',
    'span.textContent=source.slice(hit.at,end)',
    'fragment.appendChild(document.createTextNode',
    'member.equipmentItems',
    's.flags.worldItems',
    'characterData:true',
    'termsVersion(terms)',
    'addSemanticLabels(map,extraSemantic)',
    'entry&&entry.semantic',
    'SEMANTIC AUTO LABEL CONTRACT:',
    'sanitizeSemanticLabels(generated, reply)',
    '"Lục Trầm"',
):
    if required not in html:
        raise RuntimeError("GM semantic typography contract missing: " + required)

for forbidden in (
    "gm-semantic-location", "gm-semantic-status", "gm-semantic-effect",
    r"\\p{L}", r"\\p{N}",
    'innerHTML=source.slice(hit.at,end)',
    'span.innerHTML=source.slice(hit.at,end)',
    'dataset.gmSemanticPlay==="1"',
    '.message:not(.player) .text',
):
    if forbidden in html:
        raise RuntimeError("GM semantic typography leaked forbidden scope/compat syntax: " + forbidden)

# Semantic categories inherit narration color. Combat feedback remains the only colored effect layer.
semantic_css = style.split("/* GM_SEMANTIC_PLAY_BOLD_R03 */", 1)[1]
if "color:" in semantic_css:
    raise RuntimeError("GM semantic typography must not introduce semantic colors")

INDEX.write_text(html, encoding="utf-8")
print(
    "GM semantic Play Bold applied: "
    f"{len(character_names)} character, {len(entity_names)} Entity, "
    f"{len(item_names)} item/equipment, {len(skill_names)} skill terms."
)
