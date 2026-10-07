"""Final combat presentation patch; source feedback remains authoritative."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent
path = root / 'app/src/main/assets/index.html'
html = path.read_text()
if 'COMBAT_93_SNAPSHOT_V1' not in html:
    html, count = re.subn(r'<script>\s*/\* SNAPSHOT_COMBAT_DAMAGE_RUNTIME_R01 \*/.*?</script>', '', html, flags=re.S)
    if count not in (0, 1):
        raise RuntimeError('Unexpected retired HP-delta feedback script count: ' + str(count))
    if count == 0 and 'combat-feedback-1193a.js' not in html:
        raise RuntimeError('Expected retired HP-delta feedback or 1.1.93a feedback bridge')
    html = html.replace('function ensureDirectCombatDice(){', 'function ensureDirectCombatDice(){\n    if(window.__combatFeedbackBusy){show();renderDice();return;}', 1)
    html = html.replace('button.disabled=d.finalized===true||window.__combatDiceBusy;', 'button.disabled=d.finalized===true||window.__combatDiceBusy||window.__combatFeedbackBusy;')
    html = html.replace('window.__combatDiceBusy||', 'window.__combatDiceBusy||window.__combatFeedbackBusy||')
    html = html.replace('var actor=state&&state.combat&&state.combat.currentActor;', 'var actor=state&&state.combat&&(window.__combatFeedbackBusy?state.combat.resolvedActorName:state.combat.currentActor);\n    var c=state&&state.combat||{};')
    anchor = 'meta.textContent="Lượt Quay "+String(rerolls)+"/"+String(maxRerolls)+" - Chạm Vào Xúc Xắc Để Giữ";'
    if anchor not in html:
        raise RuntimeError('Actor dice metadata anchor missing')
    html = html.replace(anchor, anchor+'\n    meta.textContent="Vòng "+String(c.round||1)+" · "+meta.textContent+" · Skill: "+String(c.currentSkill&&c.currentSkill.name||"—")+" · Ultimate: "+String(c.currentUltimate&&c.currentUltimate.name||"—");', 1)
    html = html.replace('    row.textContent="";\n    var values=', '    if(window.__combatFeedbackBusy)meta.textContent="Đang diễn lượt đánh và phản công…";\n    row.textContent="";\n    var values=', 1)
    # Callback reset still runs while feedback is playing, so each UI guard checks the separate flag.
    css = '''<style>
    .poker-dice-modal,.poker-dice-backdrop{pointer-events:none}.poker-dice-sheet{pointer-events:auto}
    #snapshot.combat93-active{position:relative;min-height:290px;overflow:hidden}
    #snapshot.combat93-active .snapshot-character,#snapshot.combat93-active .snapshot-entity,#snapshot.combat93-active .snapshot-placeholder{visibility:hidden}
    .combat93-scene{z-index:5;position:absolute;inset:0;display:flex;align-items:end;padding:12px;gap:12px;background:linear-gradient(transparent,#0009);color:#fff}
    .combat93-round{position:absolute;top:8px;left:12px;background:#101722dd;padding:5px 9px;border-radius:6px}
    .combat93-unit{position:relative;flex:1;min-width:65px;text-align:center;padding:7px;border:1px solid transparent;border-radius:10px;background:#10172288}
    .combat93-unit.actor{max-width:35%}.combat93-entities{display:flex;flex:1;gap:8px;align-items:end}
    .combat93-scene .combat93-sprite{width:100%;height:160px;object-fit:contain;filter:drop-shadow(0 5px 5px #000)}
    .combat93-silhouette{height:160px;display:flex;align-items:center;justify-content:center;background:linear-gradient(150deg,#33445c,#101722);border-radius:50% 50% 15% 15%;font-size:14px}
    .combat93-silhouette[hidden]{display:none}.combat93-name{font-weight:bold;font-size:13px}.combat93-hp{font-size:12px;color:#a7efce}.combat93-status{font-size:10px;color:#ffc069;min-height:14px}
    .combat93-target{font-size:10px;padding:4px 6px}.combat93-unit.selected{border-color:#e8c96c}.combat93-unit.responding{outline:2px solid #ef7777}.combat93-unit.hit{filter:brightness(1.9)}
    .combat93-float-stack{position:absolute;z-index:12;top:8px;left:6px;right:6px;display:flex;flex-direction:column;align-items:center;gap:4px;pointer-events:none}
    .combat93-float{position:relative;max-width:100%;padding:3px 7px;border-radius:999px;background:#080d14e6;font-weight:800;font-size:12px;line-height:1.15;text-align:center;color:#ff9a95;text-shadow:0 1px 2px #000;box-shadow:0 2px 8px #0008;animation:combat93-rise .85s ease-out forwards;pointer-events:none}
    .combat93-float.critical{color:#ffdf5e;font-size:13px}.combat93-float.heal{color:#83edb6}.combat93-float.miss{color:#ddd}
    @keyframes combat93-rise{from{transform:translateY(10px);opacity:1}to{transform:translateY(-4px);opacity:0}}
    </style>'''
    script = '<script>\n'+(root/'app/src/main/assets/combat-93-snapshot.js').read_text()+'\n</script>'
    html = html.replace('</body>', css+'\n'+script+'\n</body>', 1)
    path.write_text(html)
print('Installed 1.1.93a snapshot feedback and actor dice labels')
