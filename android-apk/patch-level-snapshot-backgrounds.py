from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = "var refs={0:'https://backrooms-wiki.wikidot.com/local--files/level-0/OGLevel0.jpg',1:'https://backrooms-wiki.wdfiles.com/local--files/level-1/artistlivesinme.jpg',2:'https://backrooms-wiki.wdfiles.com/local--files/level-2/2first.jpg',3:'https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Antique-Electrical-Room.jpg/960px-Antique-Electrical-Room.jpg',4:'https://backrooms-wiki.wdfiles.com/local--files/level-4/Level-4-new',5:'https://backrooms-wiki.wdfiles.com/local--files/level-5/Level-5-1-cc.png',6:'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Dark_Hallway.jpg/500px-Dark_Hallway.jpg'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:(refs[lv]||refs[0]);bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' reference';if(!r&&lv===6)bg.style.filter='brightness(.16) contrast(1.25)';if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

MAIN.write_text(main, encoding="utf-8")
print("Level 0-6 reference backgrounds enabled inside the existing Snapshot frame; Kai stays overlaid on top.")
