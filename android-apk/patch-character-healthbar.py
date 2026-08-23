from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)

profile_old = '''    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div><div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Inventory của nhân vật đang chọn</div></div>
'''
profile_new = '''    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div>
      <div class="character-hp" aria-label="Health">
        <div class="character-hp-track"><div class="character-hp-fill" id="characterHpFill"></div><div class="character-hp-segments" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div></div>
        <div class="character-hp-value"><span class="character-hp-heart">♥</span><strong id="characterHpValue">100/100</strong></div>
      </div>
      <div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Inventory của nhân vật đang chọn</div>
    </div>
'''
if 'id="characterHpFill"' not in html:
    html = replace_once(html, profile_old, profile_new, "character healthbar markup")

css_anchor = '.character-profile img{width:110px;height:110px;object-fit:cover;border:1px solid #3b444c}'
css_extra = '''.character-hp{margin:0 0 14px;max-width:520px}.character-hp-track{position:relative;height:22px;border:2px solid #30383d;background:#090c0d;box-shadow:0 0 0 2px rgba(0,0,0,.45),0 0 8px rgba(80,210,145,.16);overflow:hidden}.character-hp-fill{position:absolute;inset:0 auto 0 0;width:100%;background:linear-gradient(90deg,#a8e4c7 0%,#68c997 35%,#20b978 68%,#008f58 100%);transition:width .2s ease}.character-hp-segments{position:absolute;inset:0;display:grid;grid-template-columns:repeat(5,1fr);pointer-events:none}.character-hp-segments i{border-right:2px solid rgba(8,22,16,.23)}.character-hp-segments i:last-child{border-right:0}.character-hp-value{display:flex;align-items:center;gap:8px;margin-top:6px;font-size:18px;line-height:1}.character-hp-heart{color:#e6394f;font-size:28px;text-shadow:0 1px 2px #000}.character-hp-value strong{font-weight:900;letter-spacing:.02em;color:#dce4e7}'''
if css_extra not in html:
    html = replace_once(html, css_anchor, css_anchor + css_extra, "character healthbar CSS")

refs_old = '''  const detailAvatar=document.getElementById('characterInventoryAvatar');
  const equipment=document.getElementById('characterEquipmentList');
'''
refs_new = '''  const detailAvatar=document.getElementById('characterInventoryAvatar');
  const hpFill=document.getElementById('characterHpFill');
  const hpValue=document.getElementById('characterHpValue');
  const equipment=document.getElementById('characterEquipmentList');
'''
if "const hpFill=document.getElementById('characterHpFill');" not in html:
    html = replace_once(html, refs_old, refs_new, "character healthbar JS refs")

fallback_old = "const fallback=[{id:'kai',name:'Kai Akechi',avatar:'avatars/kai_avatar.png',presence:'ACTIVE',isLeader:true,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:kaiItems(),equipment:signatureEquipment,statuses:[],injuries:[]}];"
fallback_new = "const fallback=[{id:'kai',name:'Kai Akechi',avatar:'avatars/kai_avatar.png',presence:'ACTIVE',isLeader:true,currentHp:100,maxHp:100,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:kaiItems(),equipment:signatureEquipment,statuses:[],injuries:[]}];"
if fallback_new not in html:
    html = replace_once(html, fallback_old, fallback_new, "Kai fallback HP")

party_fallback_old = "fallback.push({id:x.id,name:x.name||x.id,avatar:x.avatar,presence:x.presence||'ACTIVE',isLeader:false,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:[],equipment:{},statuses:[],injuries:[]})"
party_fallback_new = "fallback.push({id:x.id,name:x.name||x.id,avatar:x.avatar,presence:x.presence||'ACTIVE',isLeader:false,currentHp:Number(x.currentHp)||100,maxHp:Number(x.maxHp)||100,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:[],equipment:{},statuses:[],injuries:[]})"
if party_fallback_new not in html:
    html = replace_once(html, party_fallback_old, party_fallback_new, "party fallback HP")

render_anchor = "    detailAvatar.alt=member.name||member.id||'Nhân vật';\n"
health_render = '''    const maxHp=Math.max(1,Number(member.maxHp)||100);
    const currentHp=Math.max(0,Math.min(maxHp,Number(member.currentHp)==null?maxHp:Number(member.currentHp)));
    const hpPercent=Math.max(0,Math.min(100,currentHp*100/maxHp));
    if(hpFill)hpFill.style.width=hpPercent+'%';
    if(hpValue)hpValue.textContent=Math.round(currentHp)+'/'+Math.round(maxHp);
'''
if "if(hpFill)hpFill.style.width=hpPercent+'%';" not in html:
    html = replace_once(html, render_anchor, render_anchor + health_render, "selected character HP render")

for marker in (
    'id="characterHpFill"',
    'id="characterHpValue"',
    'character-hp-segments',
    "const hpFill=document.getElementById('characterHpFill');",
    "if(hpFill)hpFill.style.width=hpPercent+'%';",
    "hpValue.textContent=Math.round(currentHp)+'/'+Math.round(maxHp)",
):
    if marker not in html:
        raise RuntimeError("Character healthbar contract missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Character Inventory healthbar installed for the selected party member.")
