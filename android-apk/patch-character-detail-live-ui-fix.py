from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# Expose one authoritative Character Detail projection that is valid even before the first gameplay turn.
facade = FACADE.read_text(encoding="utf-8")
core_anchor = '  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n'
party_method = '''  fun currentPartyDetails(): String {
    val state = CharacterEquipmentSystem.normalize(repository.load())
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }

'''
if 'fun currentPartyDetails(): String' not in facade:
    if core_anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(core_anchor, party_method + core_anchor, 1)
FACADE.write_text(facade, encoding="utf-8")

# Make the authoritative projection synchronously readable by the local WebView.
main = MAIN.read_text(encoding="utf-8")
bridge_anchor = '  private class GameBridge {\n'
bridge_method = '''  private class GameBridge {
    @JavascriptInterface public String getPartyDetails() {
      try {
        return gameCore.currentPartyDetails();
      } catch (Exception e) {
        return "{\\\"members\\\":[]}";
      }
    }

'''
if '@JavascriptInterface public String getPartyDetails()' not in main:
    if bridge_anchor not in main:
        raise RuntimeError("MainActivity GameBridge anchor missing")
    main = main.replace(bridge_anchor, bridge_method, 1)
MAIN.write_text(main, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")

# The pre-redesign Character Detail renderer was still the function that ran when a party card was tapped.
# It fell back to static Kai data before the first turn, which caused 100/100 HP, 0/9 Inventory and the
# three old Equipment text rows. Refresh state.partyDetails from Core before resolving any member.
old_members = '''  function detailMembers(){
    const members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:null;
    if(members&&members.length)return members;
'''
new_members = '''  function refreshCharacterDetailsFromCore(){
    try{
      if(window.Android&&typeof Android.getPartyDetails==='function'){
        const raw=Android.getPartyDetails();
        const details=JSON.parse(raw||'{}');
        if(details&&Array.isArray(details.members)&&details.members.length){
          state.partyDetails=details;
          return details.members;
        }
      }
    }catch(ignore){}
    return null;
  }
  function detailMembers(){
    const liveMembers=refreshCharacterDetailsFromCore();
    if(liveMembers&&liveMembers.length)return liveMembers;
    const members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:null;
    if(members&&members.length)return members;
'''
if 'function refreshCharacterDetailsFromCore()' not in html:
    html = replace_once(html, old_members, new_members, "authoritative Character Detail refresh")

# After the legacy renderer has drawn the Survival HUD, immediately let the redesigned renderer replace
# only Character Status / Equipment / Inventory with the new cards and clickable Item Detail data.
old_render_tail = '''    statusList.innerHTML=statusRows(member).map(row=>'<div class="character-status-row '+row[2]+'"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');
  }
  function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false}
'''
new_render_tail = '''    statusList.innerHTML=statusRows(member).map(row=>'<div class="character-status-row '+row[2]+'"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');
    if(typeof window.renderCharacterStatusEquipment==='function')window.renderCharacterStatusEquipment(member);
  }
  function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false}
'''
if "window.renderCharacterStatusEquipment(member);" not in html:
    html = replace_once(html, old_render_tail, new_render_tail, "Character Detail redesigned renderer hook")

# The redesigned renderer used only state.partyDetails. Make it resilient if another UI path invokes it
# before the legacy Party helper has refreshed the state.
old_new_members = "  function members(){return state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[]}\n"
new_new_members = '''  function members(){
    try{
      if(window.Android&&typeof Android.getPartyDetails==='function'){
        const raw=Android.getPartyDetails();
        const details=JSON.parse(raw||'{}');
        if(details&&Array.isArray(details.members)&&details.members.length){state.partyDetails=details;return details.members}
      }
    }catch(ignore){}
    return state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[]
  }
'''
if 'if(window.Android&&typeof Android.getPartyDetails' not in html[html.find('window.renderCharacterStatusEquipment')-8000:]:
    html = replace_once(html, old_new_members, new_new_members, "new Character renderer authoritative fallback")

# Ensure opening a member always renders the live projection after view visibility changes as well. This
# avoids a stale frame on some Android WebView versions where layout is deferred until hidden=false.
old_open = "  function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false}\n"
new_open = "  function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false;if(typeof window.renderCharacterStatusEquipment==='function')window.renderCharacterStatusEquipment(member)}\n"
if new_open not in html:
    html = replace_once(html, old_open, new_open, "openMember live renderer")

for marker in (
    'fun currentPartyDetails(): String',
    '@JavascriptInterface public String getPartyDetails()',
):
    source = facade + main
    if marker not in source:
        raise RuntimeError("Character Detail live bridge missing: " + marker)

for marker in (
    'function refreshCharacterDetailsFromCore()',
    "typeof Android.getPartyDetails==='function'",
    'window.renderCharacterStatusEquipment(member)',
    'data-item-id',
    'id="equipmentDetailModal"',
):
    if marker not in html:
        raise RuntimeError("Character Detail live UI contract missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Character Detail live UI fixed: authoritative Status/HP/Inventory on first open and clickable Item Detail cards.")
