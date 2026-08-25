from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

legacy_renderer = '''logEl.innerHTML=(state.log||[]).map(x=>"<article class='message "+(x.role==="player"?"player":"")+"'><div class='role'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class='text'>"+esc(x.text)+"</div></article>").join("")'''
base_renderer = '''logEl.innerHTML=(state.log||[]).map(x=>{const w=x.role!=="player"&&String(x.text||"").trim().startsWith("[Warning]");return "<article class='message "+(x.role==="player"?"player":"")+(w?" warning":"")+"'><div class='role'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class='text'>"+esc(x.text)+"</div></article>"}).join("")'''
final_renderer = '''logEl.innerHTML=(state.log||[]).map(x=>{const w=x.role!=="player"&&String(x.text||"").trim().startsWith("[Warning]");return "<article class='message "+(x.role==="player"?"player":"gm")+(w?" warning":"")+"'><div class='role'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class='text'>"+esc(x.text)+"</div></article>"}).join("")'''

new_renderer = '''function cleanEntityActionDebug(text){let t=String(text||"");t=t.replace(/ENTITY ACTION BUDGET:\\s*[^.]*?(?:no repeated target\\.|Entity turn\\.)\\s*/g,"");t=t.replace(/ENTITY ACTION \\d+\\/\\d+\\s*->\\s*[^:]+:\\s*SCP-173 primary UNOBSERVED action resolved\\.\\s*/g,"");t=t.replace(/ENTITY ACTION \\d+\\/\\d+\\s*->\\s*[^:]+:\\s*(?:HIT|MISS)\\.\\s*/g,"");return t.replace(/[ \\t]{2,}/g," ").trim()} logEl.innerHTML=(state.log||[]).map(x=>{const text=x.role==="player"?String(x.text||""):cleanEntityActionDebug(x.text);if(!String(text).trim())return "";const w=x.role!=="player"&&String(text).trim().startsWith("[Warning]");return "<article class='message "+(x.role==="player"?"player":"gm")+(w?" warning":"")+"'><div class='role'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class='text'>"+esc(text)+"</div></article>"}).join("")'''

if "function cleanEntityActionDebug(text)" not in html:
    matches = [renderer for renderer in (final_renderer, base_renderer, legacy_renderer) if renderer in html]
    if len(matches) == 1:
        html = html.replace(matches[0], new_renderer, 1)
    elif len(matches) == 0:
        pattern = re.compile(r'logEl\\.innerHTML=\\(state\\.log\\|\\|\\[\\]\\)\\.map\\(x=>.*?\\)\\.join\\(""\\)', re.DOTALL)
        structural_matches = list(pattern.finditer(html))
        if len(structural_matches) != 1:
            raise RuntimeError(
                "Entity action debug UI filter: expected one structural log renderer anchor, "
                f"found {len(structural_matches)}"
            )
        match = structural_matches[0]
        html = html[:match.start()] + new_renderer + html[match.end():]
    else:
        raise RuntimeError(f"Entity action debug UI filter: expected one known log renderer anchor, found {len(matches)}")

for marker in (
    "function cleanEntityActionDebug(text)",
    "ENTITY ACTION BUDGET:",
    "SCP-173 primary UNOBSERVED action resolved",
    "(?:HIT|MISS)",
    "if(!String(text).trim())return \"\"",
    "esc(text)",
    '(x.role==="player"?"player":"gm")',
):
    if marker not in html:
        raise RuntimeError("Entity action debug UI filter contract missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Entity action debug UI hidden: technical action-budget/action-slot prefixes are filtered while natural combat narration remains visible.")
