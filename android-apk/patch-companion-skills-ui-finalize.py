from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-companion-skills-ui.py"
INDEX = ROOT / "app/src/main/assets/index.html"

source = PATCH.read_text(encoding="utf-8")
old = "skills_anchor = '    put(\"statuses\", JSONArray().apply {\\n'"
new = "skills_anchor = '    put(\"equipment\", JSONObject(c.equipment))\\n'"
if old not in source:
    raise RuntimeError("Companion skill finalizer could not locate Character Detail projection anchor")
source = source.replace(old, new, 1)
source = source.replace(
    'CompanionSkillCatalog.forCharacter(character.id)',
    'CompanionSkillCatalog.forCharacter(c.id)',
    1,
)

# The existing An Nhiên follower patch already owns a +2 percentage-point Exit bonus.
# Compose the new reading proc on top of that exact finalized block instead of overwriting it.
source = source.replace(
    """exit_old = '''    int exitThreshold = exitThresholdAndroid(state);\n    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search), \" discovery clue\");\n'''""",
    """exit_old = '''    int exitThreshold = exitThresholdAndroid(state);\n    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);\n    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search), anNhienFollowing ? \" discovery clue +2% An Nhiên\" : \" discovery clue\");\n'''""",
    1,
)
source = source.replace(
    """exit_new = '''    int exitThreshold = exitThresholdAndroid(state);\n    JSONObject anNhienRead = thresholdRoll(\"anNhienRead\", 10000, 2000, anNhienFollowing && search && exitIntent, \" Khoan, Để Tôi Đọc Cái Này\");\n    rolls.put(\"anNhienRead\", anNhienRead);\n    if (anNhienRead.optBoolean(\"success\", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);\n    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search),\n      anNhienRead.optBoolean(\"success\", false) ? \" +20% An Nhiên đọc dấu Exit\" : \" discovery clue\");\n'''""",
    """exit_new = '''    int exitThreshold = exitThresholdAndroid(state);\n    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);\n    JSONObject anNhienRead = thresholdRoll(\"anNhienRead\", 10000, 2000, anNhienFollowing && search && exitIntent, \" Khoan, Để Tôi Đọc Cái Này\");\n    rolls.put(\"anNhienRead\", anNhienRead);\n    if (anNhienRead.optBoolean(\"success\", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);\n    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search),\n      anNhienRead.optBoolean(\"success\", false) ? \" discovery clue +2% An Nhiên +20% đọc dấu Exit\" : (anNhienFollowing ? \" discovery clue +2% An Nhiên\" : \" discovery clue\"));\n'''""",
    1,
)

source = source.replace(
    'state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.toBooleanStrictOrNull() ?: false',
    'state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.equals("true", ignoreCase = true) == true',
    1,
)
source = source.replace(
    "if 'resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY' not in combat.split(countdown_anchor)[0][-1800:]:",
    "if 'irisAnalyzedTurns = max(0, irisAnalyzedTurns - 1)' not in combat:",
    1,
)

# Character Detail is rebuilt by later status/equipment/live-UI patches. Skip the early HTML splice in
# the base companion patch and attach one compact button + sheet after that final UI exists.
ui_start = source.find('# 5) Character Detail UI:')
ui_end = source.find('# 6) Regression coverage', ui_start)
if ui_start < 0 or ui_end < 0:
    raise RuntimeError("Companion skill finalizer could not isolate the early Character Detail UI section")
source = source[:ui_start] + '''# 5) Character Detail UI is attached by patch-companion-skills-ui-finalize.py after all final UI transforms.\nhtml = INDEX.read_text(encoding="utf-8")\nINDEX.write_text(html, encoding="utf-8")\n\n\n''' + source[ui_end:]

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})

html = INDEX.read_text(encoding="utf-8")
if 'id="characterSkillsModal"' not in html:
    body_anchor = '</body>'
    if html.count(body_anchor) != 1:
        raise RuntimeError(f"Character Skill final UI: expected exactly one </body>, found {html.count(body_anchor)}")
    addon = r'''
<style>
.character-skills-button{width:100%;margin:10px 0;border:1px solid #39434a;background:#12171b;color:#dce4e7;padding:10px 12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.character-skills-modal{position:fixed;inset:0;z-index:140;background:rgba(3,5,6,.92);padding:14px;overflow:auto}.character-skills-modal[hidden]{display:none}.character-skills-sheet{max-width:720px;margin:0 auto;border:1px solid #343d44;background:#0b0f12;padding:14px}.character-skills-head{display:flex;align-items:center;justify-content:space-between;gap:12px;border-bottom:1px solid #2b3137;padding-bottom:12px}.character-skills-head h2{margin:3px 0 0}.character-skills-head button{width:auto}.character-skills-list{display:grid;gap:9px;margin-top:12px}.character-skill-card{border:1px solid #2d363d;background:#101519;padding:11px}.character-skill-top{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.character-skill-name{font-weight:900}.character-skill-kind{border:1px solid #3a454d;padding:2px 6px;color:#9eabb4;font-size:9px;letter-spacing:.08em}.character-skill-trigger{margin-top:5px;color:#8e9aa3;font-size:11px}.character-skill-effect{margin-top:7px;line-height:1.45}.character-skill-note{margin-top:7px;color:#c5ad7b;font-size:11px;line-height:1.4}
</style>
<div id="characterSkillsModal" class="character-skills-modal" hidden>
  <div class="character-skills-sheet" role="dialog" aria-modal="true" aria-labelledby="characterSkillsTitle">
    <div class="character-skills-head"><div><div class="eyebrow">SKILL SET</div><h2 id="characterSkillsTitle">Kỹ năng</h2></div><button type="button" id="characterSkillsClose">Đóng</button></div>
    <div id="characterSkillsList" class="character-skills-list"></div>
  </div>
</div>
<script>
(function(){
  const view=document.getElementById('characterInventoryView');
  const modal=document.getElementById('characterSkillsModal');
  const close=document.getElementById('characterSkillsClose');
  const title=document.getElementById('characterSkillsTitle');
  const list=document.getElementById('characterSkillsList');
  if(!view||!modal||!close||!title||!list)return;
  const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  function members(){return typeof state!=='undefined'&&state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[]}
  function selected(){const id=view.dataset.characterId||'kai';return members().find(member=>String(member.id)===String(id))||members()[0]||null}
  function skills(){const member=selected();return member&&Array.isArray(member.skills)?member.skills:[]}
  function ensureButton(){
    let button=document.getElementById('characterSkillsButton');
    if(!button){
      const status=document.getElementById('characterStatusList');
      const section=status&&status.closest?status.closest('.character-section'):null;
      button=document.createElement('button');button.type='button';button.id='characterSkillsButton';button.className='character-skills-button';
      if(section&&section.parentNode)section.parentNode.insertBefore(button,section);else view.appendChild(button);
      button.addEventListener('click',openSkills);
    }
    const current=skills();button.hidden=current.length===0;button.textContent=current.length?'Kỹ năng · '+current.length:'Kỹ năng';
  }
  function openSkills(){
    const member=selected();const current=skills();
    title.textContent=(member&&member.name?member.name+' · ':'')+'Kỹ năng';
    list.innerHTML=current.length?current.map(skill=>'<div class="character-skill-card"><div class="character-skill-top"><div class="character-skill-name">'+escapeHtml(skill.name||'Kỹ năng')+'</div><span class="character-skill-kind">'+escapeHtml(skill.kind||'SKILL')+'</span></div><div class="character-skill-trigger">'+escapeHtml(skill.trigger||'')+'</div><div class="character-skill-effect">'+escapeHtml(skill.effect||'')+'</div>'+(skill.note?'<div class="character-skill-note">'+escapeHtml(skill.note)+'</div>':'')+'</div>').join(''):'<div class="character-skill-card">Chưa có kỹ năng được ghi nhận.</div>';
    modal.hidden=false;
  }
  close.addEventListener('click',()=>{modal.hidden=true});
  modal.addEventListener('click',event=>{if(event.target===modal)modal.hidden=true});
  new MutationObserver(ensureButton).observe(view,{attributes:true,attributeFilter:['data-character-id']});
  ensureButton();
})();
</script>
'''
    html = html.replace(body_anchor, addon + body_anchor, 1)

for marker in (
    'id="characterSkillsModal"',
    "button.id='characterSkillsButton'",
    'character-skills-list',
    'function openSkills()',
    "new MutationObserver(ensureButton)",
):
    if marker not in html:
        raise RuntimeError("Character Skill final UI contract missing: " + marker)
INDEX.write_text(html, encoding="utf-8")
print("Companion skill finalizer executed: final runtime projection plus compact Character Skill sheet attached.")
