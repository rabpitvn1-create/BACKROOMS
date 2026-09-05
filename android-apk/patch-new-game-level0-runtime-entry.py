from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

text = INDEX.read_text(encoding="utf-8")
marker = "const byId=id=>document.getElementById(id);"
bootstrap = (
    'initial.flags=initial.flags||{};'
    'initial.flags.exploration=Object.assign('
    '{areaId:"0",areaName:"The Lobby",parentLevel:0,levelTurns:0},'
    'initial.flags.exploration||{});\n'
)

initial_start = text.find("const initial={")
marker_index = text.find(marker, initial_start)
if initial_start < 0 or marker_index < 0:
    raise RuntimeError("new_game_level0_runtime_entry_anchor_missing")

initial_region = text[initial_start:marker_index]
if "initial.flags.exploration=Object.assign(" not in initial_region:
    text = text[:marker_index] + bootstrap + "\n" + text[marker_index:]

final_start = text.find("const initial={")
final_marker = text.find(marker, final_start)
final_region = text[final_start:final_marker]
for required in (
    'initial.flags.exploration=Object.assign(',
    'areaId:"0"',
    'areaName:"The Lobby"',
    'parentLevel:0',
    'levelTurns:0',
):
    if required not in final_region:
        raise RuntimeError("new_game_level0_runtime_entry_contract_missing:" + required)

INDEX.write_text(text, encoding="utf-8")
print("New Game now exposes Level 0 as the authoritative exploration area before Turn 1 actions.")
