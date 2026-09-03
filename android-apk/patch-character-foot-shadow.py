from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

main = MAIN.read_text(encoding="utf-8")

SHADOW_CLASS = ".snapshot .snapshot-character-shadow{"
SHADOW_NODE = "var kaiShadow=document.createElement('div');kaiShadow.className='snapshot-character-shadow';kaiShadow.setAttribute('aria-hidden','true');box.appendChild(kaiShadow);"
KAI_NODE = "var kai=document.createElement('img');kai.className='snapshot-character';"

if SHADOW_CLASS not in main:
    matches = list(re.finditer(r"\.snapshot \.snapshot-character\{[^}]+\}", main))
    if len(matches) != 1:
        raise RuntimeError(f"Character foot shadow CSS anchor: expected 1 snapshot-character rule, found {len(matches)}")

    current = matches[0].group(0)
    if "z-index:2" not in current:
        raise RuntimeError("Character foot shadow CSS anchor: snapshot-character must still be on z-index 2")

    raised = current.replace("z-index:2", "z-index:3", 1)
    shadow_css = (
        ".snapshot .snapshot-character-shadow{position:absolute;right:5%;bottom:1.5%;width:38%;height:7%;"
        "max-width:210px;background:radial-gradient(ellipse at center,rgba(0,0,0,.56) 0%,"
        "rgba(0,0,0,.34) 42%,rgba(0,0,0,0) 74%);border-radius:50%;filter:blur(3px);"
        "transform:scaleY(.72);transform-origin:center;z-index:2;pointer-events:none}"
    )
    main = main[:matches[0].start()] + raised + shadow_css + main[matches[0].end():]

if SHADOW_NODE not in main:
    count = main.count(KAI_NODE)
    if count != 1:
        raise RuntimeError(f"Character foot shadow DOM anchor: expected 1 Kai overlay node, found {count}")
    main = main.replace(KAI_NODE, SHADOW_NODE + KAI_NODE, 1)

if main.count(SHADOW_CLASS) != 1:
    raise RuntimeError("Character foot shadow CSS must exist exactly once")
if main.count(SHADOW_NODE) != 1:
    raise RuntimeError("Character foot shadow DOM node must exist exactly once")
if ".snapshot .snapshot-character{" not in main or "z-index:3" not in main:
    raise RuntimeError("Character overlay must render above the foot shadow")
if main.find(SHADOW_NODE) > main.find(KAI_NODE):
    raise RuntimeError("Character foot shadow must be appended before the character overlay")

MAIN.write_text(main, encoding="utf-8")
print("Character foot shadow enabled: soft grounded ellipse below the snapshot character overlay.")
