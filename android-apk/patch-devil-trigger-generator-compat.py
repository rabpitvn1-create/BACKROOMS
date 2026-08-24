from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-devil-trigger-passive.py"

source = PATCH.read_text(encoding="utf-8")
lines = source.splitlines()
anchor_hits = 0
new_hits = 0
for index, line in enumerate(lines):
    if line.startswith("kai_anchor = "):
        lines[index] = "kai_anchor = '  private val kai = listOf(\\n'"
        anchor_hits += 1
    elif line.startswith("kai_new = "):
        lines[index] = "kai_new = '  private val kai = listOf(\\n    s(\"DEVIL TRIGGER — Sparda Core\", \"PASSIVE\", \"READY: 30% mỗi combat turn; ACTIVE 3 turn; sau đó COOLDOWN 5 turn không roll\", \"+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.\", \"Gameplay lock: READY → 30% Trigger → DEVIL TRIGGER (3 Turns) → COOLDOWN (5 Turns) → READY. Không thêm tiêu hao HP, phản phệ, mất kiểm soát, giới hạn quỷ lực hoặc debuff.\"),\\n'"
        new_hits += 1

if anchor_hits != 1 or new_hits != 1:
    raise RuntimeError(f"Devil Trigger Kai catalog generator compatibility expected one anchor/new assignment, got {anchor_hits}/{new_hits}")

PATCH.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("Devil Trigger generator compatibility applied: Kai passive insertion no longer depends on mutable Party-action trigger wording.")
