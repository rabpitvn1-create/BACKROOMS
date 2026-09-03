from pathlib import Path

root = Path(__file__).resolve().parent
core = root / "app/src/main/java/com/rabpit/backroom/core"
party = (core / "PartyTurnCombat.kt").read_text(encoding="utf-8")
combat = (core / "CombatRuntime.kt").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
fx = (root / "app/src/main/assets/combat-overlay-feedback.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/combat-overlay-feedback.css").read_text(encoding="utf-8")
light = (root / "app/src/main/assets/auto-light-flicker.js").read_text(encoding="utf-8")
font = root / "app/src/main/assets/DFVN Broad.otf"

for marker in (
    'skillName: String? = null',
    'private fun compactSkillReply(',
    'private fun compactSkillStatuses(',
    'private fun passiveActivationLines(',
    'private fun compactAttackReply(',
    'private fun passiveStartLines(',
    'joinToString("\\n") { "• $it" }',
    '"• Action Point : $newAp/$MAX_AP."',
    '"${passive.name} kích hoạt: [DEVIL TRIGGER] trong ${passive.turns} lượt."',
    '"$actorName sử dụng: [$selected] lên $entityName."',
    'if (kind.uppercase() == "ULTIMATE") 3 else 2',
):
    assert marker in party, marker
assert 'yêu cầu dùng' not in party
assert 'yêu cầu dùng' not in html

manual = (
    "The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step", "Guilty Crown Override",
    "Twosome Time", "Rain Storm", "Honeycomb Fire", "Charged Shot", "Dead Angle",
    "ARGUS // Thousandfold Execution", "Rift Sever", "Crimson Guillotine", "Lucifer Breaker",
    "Counterphase", "Spatial Dominion", "GodKiller Override // Twenty-Four Severance",
    "M4A1 Full Auto Burst", "Too Young To Die",
)
for line in combat.splitlines():
    if 'log +=' in line and any(name in line for name in manual):
        assert 'tự động kích hoạt' not in line, line
        assert 'tự kích hoạt' not in line, line

assert font.is_file() and font.stat().st_size > 0
for marker in (
    "@font-face", "DFVN Broad.otf", ".combat-nameplate", "text-shadow",
    ".combat-nameplate-hp", ".combat-nameplate-stacked", "font-size:clamp(9px,2.4vw,13px)",
):
    assert marker in css, marker
for marker in (
    "function renderNameplates", "function appendNameplate", "combat-nameplate-hp",
    "name.length > 13", "function impactMagnitude", "cameraKick", "hit(ghost, -1, entityHit.damage)",
):
    assert marker in fx, marker
assert "textOverflow" not in fx

for marker in ("pixels:pixels.slice()", "function paintMask", "native-mask", "canvas-mask"):
    assert marker in light, marker
assert "createRadialGradient" not in light

for marker in (
    ".game>.snapshot{flex:0 0 clamp(150px,30dvh,244px)",
    ".game>.topbar{padding:6px 8px}.game>.snapshot{flex-basis:clamp(136px,28dvh,204px)",
):
    assert marker in html, marker

print("Combat presentation contracts verified.")
