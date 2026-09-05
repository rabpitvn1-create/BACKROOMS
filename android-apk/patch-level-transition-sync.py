from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# A committed level transition must update the human-readable location too.
# A later set_location op may still refine this to a more specific Level-N sub-area.
old_level_commit = '        state.put("level", safeLevel).put("title", "Level " + number + " – " + levelName(number));\n'
new_level_commit = (
    '        String canonicalLevelLocation = "Level " + number + " – " + levelName(number);\n'
    '        state.put("level", safeLevel).put("title", canonicalLevelLocation).put("location", canonicalLevelLocation);\n'
)
if new_level_commit not in main:
    count = main.count(old_level_commit)
    if count != 1:
        raise RuntimeError(f"level transition location synchronization: expected 1 match, found {count}")
    main = main.replace(old_level_commit, new_level_commit, 1)

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
if 'locationEl.textContent=displayLocation(state);' not in index:
    count = index.count(old_render)
    if count != 1:
        raise RuntimeError(f"Information location renderer: expected 1 match, found {count}")
    index = index.replace(old_render, new_render, 1)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Level transition UI sync enabled: state.level is authoritative for snapshot and Information display, and committed transitions refresh location.")
