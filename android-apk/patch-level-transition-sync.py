from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# A committed level transition must update the human-readable location too.
# A later set_location op may still refine this to a more specific Level-N sub-area.
old_level_commit = '        state.put("level", safeLevel).put("title", "Level " + number + " – " + levelName(number));\n'
new_level_commit = (
    '        String canonicalLevelLocation = "Level " + number + " – " + levelName(number);\n'
    '        state.put("level", safeLevel).put("title", canonicalLevelLocation).put("location", canonicalLevelLocation);\n'
)
main = replace_once(main, old_level_commit, new_level_commit, "level transition location synchronization")

# The Information page treats state.level as authoritative. This also repairs display
# for existing saves where level was committed but the legacy location string stayed stale.
old_render = 'function render(){titleEl.textContent=levelHeader(state);turnEl.textContent=state.turn;locationEl.textContent=state.location;'
new_render = (
    'function displayLocation(s){'
    'var raw=String(s&&s.location||"").trim();'
    'if(!(s&&s.level&&s.level.number!=null))return raw;'
    'var n=Number(s.level.number);var name=String(s.level.name||"").trim();'
    'var canonical="Level "+n+(name?" – "+name:"");'
    'var m=raw.match(/Level[^0-9]*([0-6])/i);'
    'return m&&Number(m[1])!==n?canonical:(raw||canonical);'
    '}'
    'function render(){titleEl.textContent=levelHeader(state);turnEl.textContent=state.turn;locationEl.textContent=displayLocation(state);'
)
index = replace_once(index, old_render, new_render, "Information location renderer")

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Level transition UI sync enabled: state.level is authoritative for snapshot and Information display, and committed transitions refresh location.")
