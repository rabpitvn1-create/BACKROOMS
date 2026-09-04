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

card_pos = html.find("function card(item,slot)")
if card_pos < 0:
    raise RuntimeError("inventory card renderer missing")

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
    card_pos = html.find("function card(item,slot)")

card_tail = html[card_pos:]
if "inventoryIconMarkup(item)" not in card_tail:
    candidates = [
        '<div class=\\"equipment-card-icon\\">\'+e(iconFor(item))+\'</div>',
        '<div class="equipment-card-icon">\'+e(iconFor(item))+\'</div>',
    ]
    replacement = "'+(slot?'<div class=\\\"equipment-card-icon\\\">'+e(iconFor(item))+'</div>':inventoryIconMarkup(item))+'"
    replaced = False
    for old in candidates:
        absolute = html.find(old, card_pos)
        if absolute >= 0:
            html = html[:absolute] + replacement + html[absolute + len(old):]
            replaced = True
            break
    if not replaced:
        raise RuntimeError("inventory card legacy icon markup anchor missing")

card_pos = html.find("function card(item,slot)")
if "inventoryIconMarkup(item)" not in html[card_pos:]:
    raise RuntimeError("inventory card renderer did not adopt generated icon markup")

for required in (
    MARKER,
    'id="inventoryIconStyle"',
    "const INVENTORY_ICON_IDS=new Set(",
    "function inventoryIconMarkup(item)",
    "inventory-icons/'+encodeURIComponent(key)+'.webp",
    'class=\\"inventory-item-icon-image\\"',
    'alt=\\"\\"',
):
    if required not in html:
        raise RuntimeError("inventory icon runtime contract missing: " + required)

# Inspect only the helper we inject. Existing runtime code legitimately contains SVG action icons;
# those are unrelated UI assets and must not trip the inventory-image hard lock.
helper_start = html.index("function inventoryIconMarkup(item)")
helper_end = html.index("\n", helper_start)
helper_block = html[helper_start:helper_end]
for forbidden in ("data:image", "base64,", "<svg", "<text"):
    if forbidden in helper_block:
        raise RuntimeError("inventory icon runtime embeds forbidden image payload: " + forbidden)

INDEX.write_text(html, encoding="utf-8")
print(f"INVENTORY_ICONS_PATCH|items={len(item_ids)}|fallback=generic")
