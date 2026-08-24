from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = "function combatActive(){return !!(window.state&&state.combat&&state.combat.active===true);}"
new = "function combatActive(){return !!(typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true);}"

# `state` is declared with top-level `let`, so it lives in the page's global lexical
# environment and is intentionally not exposed as `window.state`. Requiring window.state
# makes every real Entity encounter look non-combat to the action bar.
if new not in html:
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"Combat action state gate: expected exactly 1 legacy window.state check, found {count}")
    html = html.replace(old, new, 1)

if "let state=" not in html:
    raise RuntimeError("Combat action state gate: lexical game state declaration missing")
if old in html:
    raise RuntimeError("Combat action state gate: legacy window.state dependency survived")
if html.count(new) != 1:
    raise RuntimeError("Combat action state gate: fixed combatActive contract must exist exactly once")

INDEX.write_text(html, encoding="utf-8")
print("Combat action bar state gate fixed: active Entity combat now reads the lexical game state directly.")

# Rest/sleep physiology authority is applied after every earlier gameplay/UI patch so a GM
# narration about resting cannot leave the authoritative sleep counter unchanged.
runpy.run_path(str(ROOT / "patch-rest-physiology-state-finalize.py"), run_name="__main__")
