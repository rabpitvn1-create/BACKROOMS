from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def replace_regex_once(source: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, source, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 semantic match, found {count}")
    return updated


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

# The pre-redesign Character Detail renderer still owns the first Party-card tap. Do not depend on
# exact whitespace or on later HUD patches. Insert the Core refresh immediately before detailMembers().
refresh_helper = '''  function refreshCharacterDetailsFromCore(){
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
'''
if 'function refreshCharacterDetailsFromCore()' not in html:
    html = replace_regex_once(
        html,
        r'(\s{2}function\s+detailMembers\s*\(\s*\)\s*\{)',
        refresh_helper + r'\1',
        "authoritative Character Detail refresh helper",
    )

# Inject live Core data as the first source in detailMembers(), regardless of how its old fallback body
# was formatted by survival/avatar patches.
if 'const liveMembers=refreshCharacterDetailsFromCore();' not in html:
    html = replace_regex_once(
        html,
        r'(\s{2}function\s+detailMembers\s*\(\s*\)\s*\{\s*)',
        r'''\1const liveMembers=refreshCharacterDetailsFromCore();
    if(liveMembers&&liveMembers.length)return liveMembers;
    ''',
        "authoritative Character Detail refresh call",
    )

# After the legacy renderer has drawn Survival HUD/status rows, let the redesign replace only the
# Character Status, Equipment and Inventory surfaces with authoritative clickable cards.
if 'window.renderCharacterStatusEquipment(member);' not in html:
    html = replace_regex_once(
        html,
        r'''(statusList\.innerHTML\s*=\s*statusRows\(member\).*?\.join\(['"]{2}\)\s*;)(\s*\n\s*\})''',
        r'''\1
    if(typeof window.renderCharacterStatusEquipment==='function')window.renderCharacterStatusEquipment(member);\2''',
        "Character Detail redesigned renderer hook",
    )

# The redesigned renderer itself must not require a prior gameplay turn. Replace its compact members()
# function semantically. This is intentionally idempotent and does not care about exact quote spacing.
if 'function members(){\n    try{\n      if(window.Android&&typeof Android.getPartyDetails' not in html:
    new_members = '''  function members(){
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
    html = replace_regex_once(
        html,
        r'''\s{2}function\s+members\s*\(\s*\)\s*\{\s*return\s+state&&state\.partyDetails&&Array\.isArray\(state\.partyDetails\.members\)\?state\.partyDetails\.members:\[\]\s*\}\s*''',
        '\n' + new_members,
        "new Character renderer authoritative fallback",
    )

# Some Android WebView builds defer layout until hidden=false. Preserve the old openMember behavior and
# add one final authoritative render after making the view visible, without relying on exact one-line text.
if "view.hidden=false;if(typeof window.renderCharacterStatusEquipment==='function')window.renderCharacterStatusEquipment(member)" not in html:
    html = replace_regex_once(
        html,
        r'''function\s+openMember\s*\(\s*id\s*\)\s*\{\s*const\s+member\s*=\s*memberById\(id\)\s*;\s*renderMemberDetail\(member\)\s*;\s*view\.hidden\s*=\s*false\s*\}''',
        "function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false;if(typeof window.renderCharacterStatusEquipment==='function')window.renderCharacterStatusEquipment(member)}",
        "openMember live renderer",
    )

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
