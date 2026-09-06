from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatCore.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Snapshot turn visual contract {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def png_alpha(path: Path) -> tuple[int, int, list[int]]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"not a PNG: {path}")
    pos = 8
    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    transparency = b""
    while pos + 12 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"tRNS":
            transparency = payload
        elif kind == b"IEND":
            break
    if not width or not height or bit_depth != 8 or interlace != 0:
        raise RuntimeError(f"unsupported PNG layout for stage profile: {path.name}")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color_type)
    if channels is None:
        raise RuntimeError(f"unsupported PNG color type {color_type}: {path.name}")

    raw = zlib.decompress(bytes(compressed))
    stride = width * channels
    rows = []
    cursor = 0
    previous = bytearray(stride)

    def paeth(a: int, b: int, c: int) -> int:
        p = a + b - c
        pa = abs(p - a)
        pb = abs(p - b)
        pc = abs(p - c)
        return a if pa <= pb and pa <= pc else b if pb <= pc else c

    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        scan = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for i in range(stride):
            left = scan[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                scan[i] = (scan[i] + left) & 0xff
            elif filter_type == 2:
                scan[i] = (scan[i] + up) & 0xff
            elif filter_type == 3:
                scan[i] = (scan[i] + ((left + up) // 2)) & 0xff
            elif filter_type == 4:
                scan[i] = (scan[i] + paeth(left, up, upper_left)) & 0xff
            elif filter_type != 0:
                raise RuntimeError(f"unsupported PNG filter {filter_type}: {path.name}")
        rows.append(scan)
        previous = scan

    alpha = [255] * (width * height)
    for y, row in enumerate(rows):
        for x in range(width):
            at = x * channels
            if color_type == 6:
                value = row[at + 3]
            elif color_type == 4:
                value = row[at + 1]
            elif color_type == 3:
                index = row[at]
                value = transparency[index] if index < len(transparency) else 255
            else:
                value = 255
            alpha[y * width + x] = value
    return width, height, alpha


def sprite_stage_profiles() -> dict[str, dict[str, float | int]]:
    paths = []
    for rel in ["kai_snapshot_overlay.png", "kai_snapshot_overlay_combat.png"]:
        path = ASSETS / rel
        if path.is_file():
            paths.append(path)
    for folder in [ASSETS / "entity_overlays", ASSETS / "party_entity_overlays"]:
        if folder.is_dir():
            paths.extend(sorted(folder.glob("*.png")))

    result: dict[str, dict[str, float | int]] = {}
    for path in paths:
        width, height, alpha = png_alpha(path)
        opaque = [(index % width, index // width) for index, value in enumerate(alpha) if value > 24]
        if not opaque:
            continue
        visible_min_x = min(x for x, _ in opaque)
        visible_max_x = max(x for x, _ in opaque)
        max_y = max(y for _, y in opaque)

        contact_depth = max(4, int(round(height * 0.045)))
        band_start = max(0, max_y - contact_depth)
        counts = [0] * width
        for x, y in opaque:
            if band_start <= y <= max_y:
                counts[x] += 1
        minimum_column_pixels = max(1, int(round(contact_depth * 0.10)))
        contact_x = [x for x, count in enumerate(counts) if count >= minimum_column_pixels]
        if not contact_x:
            contact_x = [x for x, y in opaque if band_start <= y <= max_y]
        if not contact_x:
            contact_x = [x for x, _ in opaque]
        contact_min_x = min(contact_x)
        contact_max_x = max(contact_x)

        key = str(path.relative_to(ASSETS)).replace("\\", "/")
        result[key] = {
            "centerX": round((contact_min_x + contact_max_x + 1) / (2.0 * width), 6),
            "bottomY": round((max_y + 1) / float(height), 6),
            "contactWidth": round((contact_max_x - contact_min_x + 1) / float(width), 6),
            "visibleMinX": round(visible_min_x / float(width), 6),
            "visibleMaxX": round((visible_max_x + 1) / float(width), 6),
            "sourceWidth": width,
            "sourceHeight": height,
        }
    if "kai_snapshot_overlay.png" not in result:
        raise RuntimeError("Kai idle stage profile missing")
    if not any(key.startswith("entity_overlays/") for key in result):
        raise RuntimeError("Entity stage profiles missing")
    return result


# ---------------------------------------------------------------------------
# 1) Core emits an authoritative focus event for every acting combatant.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(
    combat,
    '      timeline += CombatTimelineEvent("FOCUS", actorId = actor.id, enemyId = enemy.id, text = actor.name)\n',
    '      timeline += CombatTimelineEvent("FOCUS", actorId = actor.id, targetId = enemy.id, enemyId = enemy.id, text = actor.name)\n',
    "party focus target",
)
combat = replace_once(
    combat,
    '''      if (actor.alive()) {
        val (enemyStunned, enemyEffects) = CombatEffects.consumeStun(enemy.effects)
''',
    '''      if (actor.alive()) {
        timeline += CombatTimelineEvent(
          "FOCUS",
          actorId = enemy.id,
          targetId = actor.id,
          enemyId = enemy.id,
          text = enemy.name
        )
        val (enemyStunned, enemyEffects) = CombatEffects.consumeStun(enemy.effects)
''',
    "entity authoritative focus",
)
entity_focus = 'actorId = enemy.id,\n          targetId = actor.id,\n          enemyId = enemy.id'
if entity_focus not in combat:
    raise RuntimeError("Entity focus contract missing from final CombatCore")
focus_at = combat.index(entity_focus)
stun_at = combat.index("CombatEffects.consumeStun(enemy.effects)", focus_at)
if focus_at > stun_at:
    raise RuntimeError("Entity focus must precede stun/skip resolution")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Renderer owns visual state only. Character stays right, Entity stays left,
#    and all sprites share one alpha-derived ground line.
# ---------------------------------------------------------------------------
profiles = sprite_stage_profiles()
html = INDEX.read_text(encoding="utf-8")
if "<!-- SNAPSHOT_VISUAL_RUNTIME_V3 -->" not in html:
    raise RuntimeError("Snapshot v3 runtime must run before the turn visual contract")

html = replace_once(
    html,
    "  let playbackToken=0,currentActor='',currentEnemy='';",
    "  let playbackToken=0,currentActor='',currentTarget='',currentEnemy='';",
    "combat target state",
)

# The earlier Kai dual-overlay observer owns only encounter/idle mode. Once timeline
# playback starts, per-turn focus becomes the sole pose owner or the two observers can
# ping-pong the image src indefinitely.
old_kai_sync = '''  function syncKaiOverlay(){
    var box=document.getElementById('snapshot');
    if(!box)return;
    var kai=box.querySelector('.snapshot-character');
    if(!kai)return;
    var combat=hasEntityEncounter();
    var desired=combat?KAI_COMBAT:KAI_NORMAL;
    if(kai.getAttribute('src')!==desired)kai.setAttribute('src',desired);
    kai.dataset.kaiOverlayMode=combat?'entity':'normal';
  }
'''
new_kai_sync = '''  function syncKaiOverlay(){
    var box=document.getElementById('snapshot');
    if(!box)return;
    var kai=box.querySelector('.snapshot-character');
    if(!kai)return;
    if(box.classList.contains('combat-turn-managed')&&!box.classList.contains('combat-finished'))return;
    var combat=hasEntityEncounter()&&!box.classList.contains('combat-finished');
    var desired=combat?KAI_COMBAT:KAI_NORMAL;
    if(kai.getAttribute('src')!==desired)kai.setAttribute('src',desired);
    kai.dataset.kaiOverlayMode=combat?'entity':'normal';
  }
'''
html = replace_once(html, old_kai_sync, new_kai_sync, "Kai pose ownership")

old_focus_block = '''  function applyVisualFocus(){
    const root=box();if(!root)return;
    root.classList.add('combat-turn-managed');root.classList.remove('combat-finished');
    const kai=root.querySelector('.snapshot-character');
    if(kai){
      kai.classList.toggle('combat-focus',currentActor==='kai');
      if(currentActor==='kai')kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay_combat.png');
    }
    root.querySelectorAll('.snapshot-party-entity-overlay').forEach(img=>img.classList.toggle('combat-focus',img.dataset.partyEntityId===currentActor));
    root.querySelectorAll('.snapshot-entity-overlay').forEach(img=>img.classList.toggle('combat-active-entity',img.dataset.entityId===currentEnemy));
  }
  function focusTurn(actorId,enemyId){currentActor=String(actorId||'').toLowerCase();if(enemyId)currentEnemy=String(enemyId);applyVisualFocus()}
'''
new_focus_block = '''  function applyVisualFocus(){
    const root=box();if(!root)return;
    root.classList.add('combat-turn-managed');root.classList.remove('combat-finished');
    const actor=String(currentActor||'').toLowerCase(),target=String(currentTarget||'').toLowerCase(),enemy=String(currentEnemy||'').toLowerCase();
    root.dataset.combatActor=actor;root.dataset.combatTarget=target;root.dataset.combatEnemy=enemy;
    const kai=root.querySelector('.snapshot-character');
    if(kai){
      const visible=actor==='kai'||target==='kai';
      kai.classList.toggle('combat-focus',visible);
      kai.classList.toggle('combat-acting',actor==='kai');
      kai.classList.toggle('combat-target',target==='kai');
      const desired=actor==='kai'?'file:///android_asset/kai_snapshot_overlay_combat.png':'file:///android_asset/kai_snapshot_overlay.png';
      if(kai.getAttribute('src')!==desired)kai.setAttribute('src',desired);
    }
    root.querySelectorAll('.snapshot-party-entity-overlay').forEach(img=>{
      const id=String(img.dataset.partyEntityId||'').toLowerCase();
      img.classList.toggle('combat-focus',id===actor||id===target);
      img.classList.toggle('combat-acting',id===actor);
      img.classList.toggle('combat-target',id===target);
    });
    root.querySelectorAll('.snapshot-entity-overlay').forEach(img=>{
      const id=String(img.dataset.entityId||'').toLowerCase();
      img.classList.toggle('combat-active-entity',id===enemy);
      img.classList.toggle('combat-acting',id===actor);
      img.classList.toggle('combat-target',id===target);
    });
  }
  function focusTurn(actorId,targetId,enemyId){
    currentActor=String(actorId||'').toLowerCase();
    currentTarget=String(targetId||'').toLowerCase();
    if(enemyId)currentEnemy=String(enemyId);
    applyVisualFocus();
  }
'''
html = replace_once(html, old_focus_block, new_focus_block, "turn focus renderer")
html = replace_once(
    html,
    "focusTurn(event.actorId,event.enemyId);",
    "focusTurn(event.actorId,event.targetId,event.enemyId);",
    "timeline focus target wiring",
)

old_finish = '''  function finishVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentEnemy='';root.classList.add('combat-turn-managed','combat-finished');
    root.querySelectorAll('.combat-focus').forEach(node=>node.classList.remove('combat-focus'));
    root.querySelectorAll('.combat-active-entity').forEach(node=>node.classList.remove('combat-active-entity'));
    const kai=root.querySelector('.snapshot-character');if(kai)kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay.png');
  }
  function resetVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentEnemy='';root.classList.remove('combat-turn-managed','combat-finished');
  }
'''
new_finish = '''  function clearCombatActorClasses(root){
    root.querySelectorAll('.combat-focus,.combat-acting,.combat-target,.combat-active-entity').forEach(node=>{
      node.classList.remove('combat-focus','combat-acting','combat-target','combat-active-entity');
    });
    delete root.dataset.combatActor;delete root.dataset.combatTarget;delete root.dataset.combatEnemy;
  }
  function finishVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentTarget='';currentEnemy='';
    clearCombatActorClasses(root);
    root.classList.add('combat-turn-managed','combat-finished');
    root.classList.remove('entity-encounter-present');
    const kai=root.querySelector('.snapshot-character');if(kai)kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay.png');
  }
  function resetVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentTarget='';currentEnemy='';
    clearCombatActorClasses(root);
    root.classList.remove('combat-turn-managed','combat-finished','entity-encounter-present');
    const kai=root.querySelector('.snapshot-character');if(kai)kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay.png');
  }
'''
html = replace_once(html, old_finish, new_finish, "combat pose reset")

html = replace_once(
    html,
    '''  function visualCenterX(node,role,p){
    const raw=clamp(Number(p.centerX)||.5,0,1);
    return role==='kai'&&root()&&root().classList.contains('entity-encounter-present')?1-raw:raw;
  }
''',
    '''  function visualCenterX(node,role,p){
    return clamp(Number(p.centerX)||.5,0,1);
  }
''',
    "remove encounter mirroring",
)

profile_json = json.dumps(profiles, separators=(",", ":"))
ground_marker = "  const GROUND_PROFILES="
start = html.find(ground_marker)
if start < 0:
    raise RuntimeError("GROUND_PROFILES marker missing")
line_end = html.find(";\n", start)
if line_end < 0:
    raise RuntimeError("GROUND_PROFILES terminator missing")
stage_decl = "  const STAGE_PROFILES_V4=" + profile_json + ";\n"
if "  const STAGE_PROFILES_V4=" not in html:
    html = html[:line_end + 2] + stage_decl + html[line_end + 2:]

html = replace_once(
    html,
    "  function groundProfile(node){return GROUND_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.96,contactWidth:.20}}\n",
    "  function groundProfile(node){return GROUND_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.96,contactWidth:.20}}\n  function stageProfile(node){return STAGE_PROFILES_V4[assetKey(node)]||{centerX:.5,bottomY:.96,contactWidth:.20,visibleMinX:0,visibleMaxX:1,sourceWidth:1,sourceHeight:1}}\n",
    "stage profile lookup",
)

old_stage = '''  function syncStage(){
    const box=root();if(!box||!box.classList.contains('entity-encounter-present'))return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    actorNodes(box).forEach(([,node,role])=>{
      const rect=node.getBoundingClientRect();
      if(rect.width<3||rect.height<3)return;
      const p=groundProfile(node);
      const targetX=rr.width*(role==='entity'?.76:.24);
      const targetY=rr.height*.965;
      const centerX=visualCenterX(node,role,p);
      const left=targetX-rect.width*centerX;
      const bottom=rr.height-targetY-rect.height*(1-clamp(Number(p.bottomY)||.96,.55,1));
      node.style.setProperty('--stage-left',left.toFixed(2)+'px');
      node.style.setProperty('--stage-bottom',bottom.toFixed(2)+'px');
    });
  }
'''
new_stage = '''  function kaiIdleContactX(rr){
    const p=STAGE_PROFILES_V4['kai_snapshot_overlay.png'];
    if(!p)return rr.width*.80;
    const naturalW=Math.max(1,Number(p.sourceWidth)||1),naturalH=Math.max(1,Number(p.sourceHeight)||1);
    const renderH=rr.height*.97;
    const renderW=Math.min(rr.width*.55,renderH*naturalW/naturalH);
    return rr.width-renderW+renderW*clamp(Number(p.centerX)||.5,0,1);
  }

  function syncStage(){
    const box=root();if(!box||!box.classList.contains('entity-encounter-present'))return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    const groundY=rr.height*.965;
    const kaiContactX=kaiIdleContactX(rr);
    actorNodes(box).forEach(([,node,role])=>{
      const rect=node.getBoundingClientRect();
      if(rect.width<3||rect.height<3)return;
      const p=stageProfile(node);
      const centerX=visualCenterX(node,role,p);
      const visibleMinX=clamp(Number(p.visibleMinX)||0,0,1);
      const visibleMaxX=clamp(Number(p.visibleMaxX)||1,visibleMinX,1);
      let left;
      if(role==='entity'){
        left=rr.width*.018-rect.width*visibleMinX;
      }else if(role==='kai'){
        left=kaiContactX-rect.width*centerX;
      }else{
        left=rr.width*.982-rect.width*visibleMaxX;
      }
      const minLeft=rr.width*.01-rect.width*visibleMinX;
      const maxLeft=rr.width*.99-rect.width*visibleMaxX;
      left=clamp(left,minLeft,maxLeft);
      const bottom=rr.height-groundY-rect.height*(1-clamp(Number(p.bottomY)||.96,.55,1));
      node.style.setProperty('--stage-left',left.toFixed(2)+'px');
      node.style.setProperty('--stage-bottom',bottom.toFixed(2)+'px');
    });
  }
'''
html = replace_once(html, old_stage, new_stage, "right-character left-entity stage")

html = replace_once(
    html,
    '''  function encounterClass(){
    const box=root();if(!box)return;
    box.classList.toggle('entity-encounter-present',!!box.querySelector('.snapshot-entities .snapshot-entity-overlay'));
  }
''',
    '''  function encounterClass(){
    const box=root();if(!box)return;
    const hasEntity=!!box.querySelector('.snapshot-entities .snapshot-entity-overlay');
    const cleanFinished=box.classList.contains('combat-finished');
    box.classList.toggle('entity-encounter-present',hasEntity&&!cleanFinished);
  }
''',
    "post-combat encounter cleanup",
)

old_recoil = '''      const animation=target.animate([
        {offset:0,transform:'translateX(0)',filter:'brightness(1) contrast(1)'},
        {offset:.12,transform:'translateX('+(direction*3)+'px)',filter:'brightness(2.25) contrast(1.28)'},
        {offset:.34,transform:'translateX('+(direction*18)+'px)',filter:'brightness(1.38) contrast(1.12)'},
        {offset:.62,transform:'translateX('+(direction*7)+'px)',filter:'brightness(1.08) contrast(1.04)'},
        {offset:.82,transform:'translateX('+(direction*-3)+'px)',filter:'brightness(1.02) contrast(1.01)'},
        {offset:1,transform:'translateX(0)',filter:'brightness(1) contrast(1)'}
      ],{duration:560,easing:'cubic-bezier(.16,.82,.28,1)',fill:'none'});
'''
new_recoil = '''      const animation=target.animate([
        {offset:0,translate:'0 0',filter:'brightness(1) contrast(1)'},
        {offset:.12,translate:(direction*3)+'px 0',filter:'brightness(2.25) contrast(1.28)'},
        {offset:.34,translate:(direction*18)+'px 0',filter:'brightness(1.38) contrast(1.12)'},
        {offset:.62,translate:(direction*7)+'px 0',filter:'brightness(1.08) contrast(1.04)'},
        {offset:.82,translate:(direction*-3)+'px 0',filter:'brightness(1.02) contrast(1.01)'},
        {offset:1,translate:'0 0',filter:'brightness(1) contrast(1)'}
      ],{duration:560,easing:'cubic-bezier(.16,.82,.28,1)',fill:'none'});
'''
html = replace_once(html, old_recoil, new_recoil, "hit recoil transform isolation")

final_style = r'''<!-- SNAPSHOT_TURN_VISUAL_CONTRACT_V4 -->
<style id="snapshot-turn-visual-contract-v4-style">
/* Character side is invariant: RIGHT. Entity side is invariant: LEFT. Stage position is
   carried only by left/bottom coordinates so hit recoil never overwrites placement/facing. */
.snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:right bottom!important;scale:1 1!important}
.snapshot.entity-encounter-present .snapshot-party-entity-overlay{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:center bottom!important;scale:1 1!important}
.snapshot .snapshot-entity-overlay{left:var(--stage-left,0px)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important}
.snapshot.combat-turn-managed .snapshot-character.combat-focus,.snapshot.combat-turn-managed .snapshot-party-entity-overlay.combat-focus{opacity:1!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.combat-active-entity{opacity:1!important}
.snapshot.combat-finished .snapshot-character{opacity:1!important;transform:none!important;translate:none!important;scale:1 1!important}
.snapshot.combat-finished .snapshot-party-entity-layer,.snapshot.combat-finished .snapshot-entities{opacity:0!important;pointer-events:none!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.entity-slide-out-v3{opacity:0!important;transform:translateX(-82px) scale(.96)!important}
.snapshot .snapshot-entity-overlay.entity-slide-in-v3{animation:entitySlideInTurnContractV4 .48s cubic-bezier(.18,.82,.24,1) both!important}
@keyframes entitySlideInTurnContractV4{0%{opacity:0;transform:translateX(-86px) scale(.95)}66%{opacity:1;transform:translateX(4px) scale(1.01)}100%{opacity:1;transform:translateX(0) scale(1)}}
</style>'''
if "<!-- SNAPSHOT_TURN_VISUAL_CONTRACT_V4 -->" not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Snapshot turn visual contract expected exactly one </body>")
    html = html.replace("</body>", final_style + "\n</body>", 1)

for forbidden in [
    "targetX=rr.width*(role==='entity'?.76:.24)",
    ".snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,7%)",
    "return role==='kai'&&root()&&root().classList.contains('entity-encounter-present')?1-raw:raw",
]:
    if forbidden in html:
        raise RuntimeError("Old reversed Snapshot staging survived: " + forbidden)

for required in [
    "currentTarget=''",
    "if(box.classList.contains('combat-turn-managed')&&!box.classList.contains('combat-finished'))return",
    "focusTurn(event.actorId,event.targetId,event.enemyId)",
    "STAGE_PROFILES_V4=",
    "visibleMinX",
    "visibleMaxX",
    "kaiIdleContactX(rr)",
    "role==='entity'",
    "rr.width*.018-rect.width*visibleMinX",
    "rr.width*.982-rect.width*visibleMaxX",
    "translate:(direction*18)+'px 0'",
    "cleanFinished=box.classList.contains('combat-finished')",
    "const desired=actor==='kai'?'file:///android_asset/kai_snapshot_overlay_combat.png':'file:///android_asset/kai_snapshot_overlay.png'",
    "SNAPSHOT_TURN_VISUAL_CONTRACT_V4",
]:
    if required not in html:
        raise RuntimeError("Snapshot turn visual contract missing: " + required)
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Regression test proves party -> Entity -> next party focus/target semantics.
# ---------------------------------------------------------------------------
TESTS.mkdir(parents=True, exist_ok=True)
(TESTS / "SnapshotTurnVisualContractGeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTurnVisualContractGeneratedTest {
  private class ConstantRandom(private val value: Double) : CombatRandom {
    override fun nextDouble(): Double = value
  }

  @Test fun everyActorOwnsAnAuthoritativeFocusWithExactTarget() {
    val durable = CombatStats(currentHp = 500, defend = 500)
    val party = listOf(
      CombatantState("lucia", "Lucia", false, durable, baseDamage = 1),
      CombatantState("kai", "Kai", false, durable, baseDamage = 1)
    )
    val result = AutoTurnCombatEngine(ConstantRandom(0.99)).resolve(
      encounterId = "SNAPSHOT_TURN_VISUAL_CONTRACT",
      partyInput = party,
      entityIds = listOf("ENTITY.HOUND"),
      level = 0
    )

    val focuses = result.timeline.filter { it.kind == "FOCUS" }
    assertTrue(focuses.size >= 3)
    assertEquals("kai", focuses[0].actorId)
    assertEquals("ENTITY.HOUND", focuses[0].targetId)
    assertEquals("ENTITY.HOUND", focuses[0].enemyId)

    assertEquals("ENTITY.HOUND", focuses[1].actorId)
    assertEquals("kai", focuses[1].targetId)
    assertEquals("ENTITY.HOUND", focuses[1].enemyId)

    assertEquals("lucia", focuses[2].actorId)
    assertEquals("ENTITY.HOUND", focuses[2].targetId)
    assertEquals("ENTITY.HOUND", focuses[2].enemyId)
  }
}
''', encoding="utf-8")

print("Snapshot turn visual contract finalized: Character RIGHT, Entity LEFT, shared alpha-ground line, authoritative Entity turns, pose reset and isolated hit recoil.")
