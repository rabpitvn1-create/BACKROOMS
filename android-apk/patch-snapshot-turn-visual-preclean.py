from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = ".snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,7%)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:left bottom!important;scale:-1 1!important}"
new = ".snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:right bottom!important;scale:1 1!important}"
if new not in html:
    if html.count(old) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one reversed Character CSS rule, found {html.count(old)}")
    html = html.replace(old, new, 1)
if old in html:
    raise RuntimeError("Snapshot turn preclean failed to remove reversed Character CSS")

INDEX.write_text(html, encoding="utf-8")
print("Snapshot turn preclean normalized the legacy reversed Character encounter CSS before the final contract guard.")
