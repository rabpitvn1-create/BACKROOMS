from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
text = INDEX.read_text(encoding="utf-8")
old = 'function chips(items){return items&&items.length?items.map(x=>"<span>"+esc(typeof x==="string"?x:x.name||"—")+"</span>").join(""):"<span>Trống.</span>"}'
new = 'function chips(items){return items&&items.length?items.map(x=>{if(typeof x==="string")return "<span>"+esc(x)+"</span>";const q=Math.max(1,Number(x.quantity)||1);return "<span>"+esc(x.name||"—")+" ×"+q+"</span>"}).join(""):"<span>Trống.</span>"}'
if new not in text:
    if old not in text:
        raise RuntimeError("Inventory chips renderer anchor not found")
    text = text.replace(old, new, 1)
INDEX.write_text(text, encoding="utf-8")
print("Inventory quantity renderer applied.")
