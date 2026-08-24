from html.parser import HTMLParser
from pathlib import Path
import re
import sys


class StructureParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.shell_main = 0
        self.game_sections = 0
        self.side_asides = 0
        self.ids = set()

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        classes = set(values.get("class", "").split())
        if tag == "main" and "shell" in classes:
            self.shell_main += 1
        if tag == "section" and "game" in classes:
            self.game_sections += 1
        if tag == "aside" and "side" in classes:
            self.side_asides += 1
        if values.get("id"):
            self.ids.add(values["id"])


ROOT = Path(__file__).resolve().parent
path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/index.html"
html = path.read_text(encoding="utf-8")

parser = StructureParser()
parser.feed(html)
assert parser.shell_main == 1, parser.shell_main
assert parser.game_sections == 1, parser.game_sections
assert parser.side_asides == 1, parser.side_asides

for element_id in (
    "searchActionButton",
    "submit",
    "exploreActionButton",
    "saveButton",
    "loadButton",
    "newGameButton",
    "deleteSaveButton",
    "party",
    "partyTime",
    "characterInventoryView",
):
    assert element_id in parser.ids, element_id

required = (
    "TWO_PAGE_SWIPE_UI_V1",
    "TWO_PAGE_SWIPE_RUNTIME_V1",
    "body.two-page-body{overflow:hidden}",
    ".shell.two-page-shell[data-page=\"game\"]>.game{left:10px}",
    ".shell.two-page-shell[data-page=\"status\"]>.side{left:10px}",
    "overflow-y:auto",
    "status.insertBefore(partyCard,saveCard)",
    "const SWIPE_DISTANCE=56",
    "Math.abs(dx)<SWIPE_DISTANCE",
    "Math.abs(dx)<=Math.abs(dy)*1.2",
    "textarea,input,select,button,a",
    ".party-member,#characterInventoryView,#equipmentDetailModal,#characterSkillsModal",
    "window.backroomPagePager={setPage,getPage:()=>shell.dataset.page}",
    "setPage('game')",
    "COMBAT_ACTION_BAR_V2",
    "function combatActive(){return !!(typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true);}",
)
for marker in required:
    assert marker in html, marker

assert html.count("TWO_PAGE_SWIPE_UI_V1") == 1
assert html.count("TWO_PAGE_SWIPE_RUNTIME_V1") == 1
assert html.count("window.backroomPagePager={setPage,getPage:()=>shell.dataset.page}") == 1

# The pager must remain presentation-only. It can expose navigation state, but it must not
# create, clone, serialize or replace the authoritative gameplay state.
match = re.search(r"<script>\s*// TWO_PAGE_SWIPE_RUNTIME_V1(?P<body>.*?)</script>", html, re.S)
assert match, "two-page runtime script not found"
runtime = match.group("body")
for forbidden in (
    "localStorage.setItem",
    "localStorage.removeItem",
    "JSON.stringify(state)",
    "state=",
    "Android.submitAction",
    "Android.clearCoreState",
):
    assert forbidden not in runtime, forbidden

print("Two-page swipe UI contract verified: shared state, two independent vertical pages, guarded horizontal swipe, Party-before-Save management order.")
