from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MANIFEST = ROOT / "inventory_icon_manifest.json"
MARKER = "INVENTORY_ICON_HARD_LOCK_R01"

payload = json.loads(MANIFEST.read_text(encoding="utf-8"))
item_ids = [str(item["id"]).strip() for item in payload["items"]]
if not item_ids or len(item_ids) != len(set(item_ids)):
    raise RuntimeError("inventory icon manifest IDs are blank or duplicated")

html = INDEX.read_text(encoding="utf-8")

if 'id="inventoryIconStyle"' not in html:
    style = '''<style id="inventoryIconStyle">
/* INVENTORY_ICON_HARD_LOCK_R01 */
.inventory-card-icon{overflow:hidden;padding:2px}.inventory-item-icon-image{display:block;width:100%;height:100%;object-fit:contain}
</style>
'''
    if "</head>" not in html:
        raise RuntimeError("inventory icon style insertion anchor missing")
    html = html.replace("</head>", style + "</head>", 1)

if "const INVENTORY_ICON_IDS=" not in html:
    pattern = re.compile(r"(  function iconFor\(item\)\{[^\n]+\}\n)")
    match = pattern.search(html)
    if not match:
        raise RuntimeError("inventory card iconFor() anchor missing")
    encoded_ids = json.dumps(item_ids, ensure_ascii=False, separators=(",", ":"))
    helper = (
        match.group(1)
        + f"  /* {MARKER} */\n"
        + f"  const INVENTORY_ICON_IDS=new Set({encoded_ids});\n"
        + "  function inventoryIconMarkup(item){const id=String(item&&item.id||'').trim().toLowerCase();const key=INVENTORY_ICON_IDS.has(id)?id:'generic';return '<div class=\\\"equipment-card-icon inventory-card-icon\\\"><img class=\\\"inventory-item-icon-image\\\" src=\\\"inventory-icons/'+encodeURIComponent(key)+'.webp\\\" alt=\\\"\\\" aria-hidden=\\\"true\\\" loading=\\\"lazy\\\" decoding=\\\"async\\\"></div>'}\n"
    )
    html = html[:match.start()] + helper + html[match.end():]

if "inventoryIconMarkup(item)" not in html.split("function card(item,slot)", 1)[-1]:
    candidates = [
        '<div class=\\"equipment-card-icon\\">\'+e(iconFor(item))+\'</div>',
        '<div class="equipment-card-icon">\'+e(iconFor(item))+\'</div>',
    ]
    replacement = "'+(slot?'<div class=\\\"equipment-card-icon\\\">'+e(iconFor(item))+'</div>':inventoryIconMarkup(item))+'"
    replaced = False
    for old in candidates:
        if old in html:
            html = html.replace(old, replacement, 1)
            replaced = True
            break
    if not replaced:
        raise RuntimeError("inventory card legacy icon markup anchor missing")

for required in (
    MARKER,
    'id="inventoryIconStyle"',
    "const INVENTORY_ICON_IDS=new Set(",
    "function inventoryIconMarkup(item)",
    "inventory-icons/'+encodeURIComponent(key)+'.webp",
    'class=\\"inventory-item-icon-image\\"',
    'alt=\\"\\"',
    "inventoryIconMarkup(item)",
):
    if required not in html:
        raise RuntimeError("inventory icon runtime contract missing: " + required)

helper_start = html.index(f"/* {MARKER} */")
helper_end = html.index("function card(item,slot)", helper_start)
helper_block = html[helper_start:helper_end]
for forbidden in ("data:image", "base64,", "<svg", "<text"):
    if forbidden in helper_block:
        raise RuntimeError("inventory icon runtime embeds forbidden image payload: " + forbidden)

INDEX.write_text(html, encoding="utf-8")
print(f"INVENTORY_ICONS_PATCH|items={len(item_ids)}|fallback=generic")
