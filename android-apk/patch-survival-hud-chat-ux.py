from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

# Character Detail: add a dedicated survival HUD above the remaining status rows.
old_status = '<div class="character-section"><h3>Status</h3><div class="character-status-list" id="characterStatusList"></div></div>'
new_status = '<div class="character-section"><h3>Status</h3><div class="survival-hud" id="characterSurvivalHud"></div><div class="character-status-list" id="characterStatusList"></div></div>'
if new_status not in html:
    if old_status not in html:
        raise RuntimeError("Character Status section anchor not found")
    html = html.replace(old_status, new_status, 1)

# Give Game Master messages an explicit class so the visual difference is stable.
old_role_class = '(x.role==="player"?"player":"")'
new_role_class = '(x.role==="player"?"player":"gm")'
if new_role_class not in html:
    if old_role_class not in html:
        raise RuntimeError("Message role class anchor not found")
    html = html.replace(old_role_class, new_role_class, 1)

# New GM replies should land at the start of the reply. Android's native WebView enhancement
# also schedules a scroll-to-bottom callback after backroomTurn(), so the final correction must
# run one frame later than those callbacks instead of racing them in the same animation frame.
old_busy = 'let busy=false;'
scroll_helper = '''let busy=false;
function focusLatestGmStart(){requestAnimationFrame(()=>requestAnimationFrame(()=>{const gmRows=logEl.querySelectorAll('.message.gm');const latest=gmRows[gmRows.length-1];if(!latest)return;const top=latest.getBoundingClientRect().top-logEl.getBoundingClientRect().top+logEl.scrollTop;logEl.scrollTop=Math.max(0,top)}))}'''
if scroll_helper not in html:
    if old_busy not in html:
        raise RuntimeError("busy state anchor not found")
    html = html.replace(old_busy, scroll_helper, 1)

old_turn = 'window.backroomTurn=json=>{state=JSON.parse(json);actionEl.value="";busy=false;submitEl.disabled=false;save();statusEl.textContent="Turn "+state.turn+" đã lưu trên máy.";render()};'
new_turn = 'window.backroomTurn=json=>{state=JSON.parse(json);actionEl.value="";busy=false;submitEl.disabled=false;save();statusEl.textContent="Turn "+state.turn+" đã lưu trên máy.";render();focusLatestGmStart()};'
if new_turn not in html:
    if old_turn not in html:
        raise RuntimeError("backroomTurn anchor not found")
    html = html.replace(old_turn, new_turn, 1)

# CSS: HUD inspired by the attached futuristic gauge, without requiring new image assets.
css_anchor = '.status-danger{color:#df9a9a}'
css_extra = '''.message.gm{border-left-color:#71808a;background:#1a2025}.survival-hud{display:grid;gap:10px;margin:0 0 13px}.survival-meter{border:1px solid #34444d;background:#0b1014;padding:8px 10px;clip-path:polygon(0 12%,4% 0,92% 0,100% 28%,100% 72%,96% 100%,4% 100%,0 88%)}.survival-meter-head{display:grid;grid-template-columns:42px minmax(0,1fr) auto;gap:9px;align-items:center;margin-bottom:6px}.survival-icon{display:grid;place-items:center;min-width:36px;height:30px;border:1px solid #4a626d;background:#111a1f;font-weight:900}.survival-label{font-size:11px;font-weight:800;letter-spacing:.08em}.survival-value{font-size:13px;font-weight:900}.survival-track{height:14px;border:1px solid #4b6570;background:#070a0c;padding:2px}.survival-fill{display:block;height:100%;background:linear-gradient(90deg,#5f8290,#b9e8f0);box-shadow:0 0 8px #8ddbe855;transition:width .2s ease}.survival-state{margin-top:5px;color:#87939b;font-size:10px;text-align:right}.survival-meter.unknown .survival-value,.survival-meter.unknown .survival-state{color:#78838c}.survival-meter.unknown .survival-fill{width:0!important}#characterEquipmentList span,#characterInventoryItems span{text-transform:uppercase;letter-spacing:.035em}'''
if css_extra not in html:
    if css_anchor not in html:
        raise RuntimeError("Character status CSS anchor not found")
    html = html.replace(css_anchor, css_anchor + css_extra, 1)

# Character Detail JS additions.
old_status_const = "const statusList=document.getElementById('characterStatusList');"
new_status_const = "const statusList=document.getElementById('characterStatusList');\n  const survivalHud=document.getElementById('characterSurvivalHud');"
if new_status_const not in html:
    if old_status_const not in html:
        raise RuntimeError("Character status JS anchor not found")
    html = html.replace(old_status_const, new_status_const, 1)

# Fresh fallback should match the authoritative fresh-run baseline; NPC fallback remains unknown.
old_kai_fallback = "physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'}"
new_kai_fallback = "physiology:{hunger:'NORMAL',thirst:'NORMAL',sleepDeprivation:'NORMAL',foodPercent:100,waterPercent:100,restPercent:100}"
if new_kai_fallback not in html:
    if old_kai_fallback not in html:
        raise RuntimeError("Kai fallback physiology anchor not found")
    html = html.replace(old_kai_fallback, new_kai_fallback, 1)

# Remove the three duplicate text rows; the HUD owns these now.
for row in [
    "    rows.push(['Đói',bandLabel(p.hunger),bandClass(p.hunger)]);\n",
    "    rows.push(['Khát',bandLabel(p.thirst),bandClass(p.thirst)]);\n",
    "    rows.push(['Thiếu ngủ',bandLabel(p.sleepDeprivation),bandClass(p.sleepDeprivation)]);\n",
]:
    html = html.replace(row, '', 1)

hud_anchor = '  function equipmentRows(member){'
hud_functions = r'''  function meterValue(raw){if(raw==null||raw==='')return null;const n=Number(raw);return Number.isFinite(n)?Math.max(0,Math.min(100,Math.round(n))):null}
  function survivalMeter(icon,label,percent,band){
    const value=meterValue(percent),shown=value==null?'--':value+'%',width=value==null?0:value,stateLabel=bandLabel(band);
    return '<div class="survival-meter '+(value==null?'unknown':'')+'"><div class="survival-meter-head"><div class="survival-icon">'+esc(icon)+'</div><div class="survival-label">'+esc(label)+'</div><div class="survival-value">'+esc(shown)+'</div></div><div class="survival-track"><span class="survival-fill" style="width:'+width+'%"></span></div><div class="survival-state">'+esc(stateLabel)+'</div></div>';
  }
  function renderSurvivalHud(member){
    if(!survivalHud)return;
    const p=member&&member.physiology||{};
    survivalHud.innerHTML=[survivalMeter('🥩','THỨC ĂN',p.foodPercent,p.hunger),survivalMeter('💧','NƯỚC',p.waterPercent,p.thirst),survivalMeter('Zzz','NGỦ',p.restPercent,p.sleepDeprivation)].join('');
  }
'''
if hud_functions not in html:
    if hud_anchor not in html:
        raise RuntimeError("equipmentRows anchor not found")
    html = html.replace(hud_anchor, hud_functions + hud_anchor, 1)

render_anchor = '    const inv=Array.isArray(member.inventory)?member.inventory:(member.id===\'kai\'?kaiItems():[]);'
render_with_hud = "    renderSurvivalHud(member);\n" + render_anchor
if render_with_hud not in html:
    if render_anchor not in html:
        raise RuntimeError("Character detail render anchor not found")
    html = html.replace(render_anchor, render_with_hud, 1)

# Contract guards.
required = [
    'id="characterSurvivalHud"',
    "survivalMeter('🥩'",
    "survivalMeter('💧'",
    "survivalMeter('Zzz'",
    '.message.gm{',
    'focusLatestGmStart()',
    'requestAnimationFrame(()=>requestAnimationFrame(()=>',
    "querySelectorAll('.message.gm')",
    'getBoundingClientRect().top-logEl.getBoundingClientRect().top+logEl.scrollTop',
    '#characterEquipmentList span,#characterInventoryItems span{text-transform:uppercase',
]
for token in required:
    if token not in html:
        raise RuntimeError(f"Required UX contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")
print("Survival HUD, uppercase character items, differentiated GM messages and stable reply-start scrolling applied.")
