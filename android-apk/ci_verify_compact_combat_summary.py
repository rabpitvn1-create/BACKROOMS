from pathlib import Path

root = Path(__file__).resolve().parent
party = (root / "app/src/main/java/com/rabpit/backroom/core/PartyTurnCombat.kt").read_text(encoding="utf-8")
fx = (root / "app/src/main/assets/combat-overlay-feedback.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/combat-overlay-feedback.css").read_text(encoding="utf-8")

for marker in (
    'val compactAttack = skillName == null && !locked && displayAction.contains(" tấn công ")',
    'private fun compactAttackReply(',
    'private fun passiveStartLines(',
    'joinToString("\\n") { "• $it" }',
    '"• Action Point : $newAp/$MAX_AP."',
    '"${passive.name} kích hoạt: [DEVIL TRIGGER] trong ${passive.turns} lượt."',
):
    assert marker in party, marker

for forbidden in (
    'compactAttack -> playerFacingReply(engine.reply)',
    '"HÀNH ĐỘNG CỦA ĐỘI"',
):
    assert forbidden not in party, forbidden

for marker in (
    ".combat-nameplate-hp",
    ".combat-nameplate-stacked",
    "font-size:clamp(9px,2.4vw,13px)",
    "font-size:clamp(8px,2.15vw,11.5px)",
):
    assert marker in css, marker

for marker in (
    "function appendNameplate",
    "combat-nameplate-hp",
    "combat-nameplate-stacked",
    "name.length > 13",
):
    assert marker in fx, marker

print("Compact combat summary and HP nameplate contracts verified.")
