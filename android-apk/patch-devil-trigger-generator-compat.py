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

source = "\n".join(lines) + "\n"

# CharacterStatusEquipmentSystem replaces the original fixed base attack with
# weapon/stat-based damage plus the established 70%-of-profile-max cap before
# this finalizer runs. Adapt only the Devil Trigger generator's expected anchor
# and matching self-check to that finalized form; the existing cap stays intact.
old_attack_anchor = "    '          val damage = max(1, base - profile.armor)\\n',\n"
new_attack_anchor = "    '          val damage = min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))\\n',\n"
old_attack_replacement = "    '          val damage = DevilTriggerPassive.damage(max(1, base - profile.armor), kaiDevilTriggerActive)\\n',\n"
new_attack_replacement = "    '          val damage = DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive)\\n',\n"
old_attack_marker = '    \'DevilTriggerPassive.damage(max(1, base - profile.armor), kaiDevilTriggerActive)\',\n'
new_attack_marker = '    \'DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive)\',\n'

for old, new, label in (
    (old_attack_anchor, new_attack_anchor, "Kai finalized base attack anchor"),
    (old_attack_replacement, new_attack_replacement, "Kai finalized base attack replacement"),
    (old_attack_marker, new_attack_marker, "Kai finalized base attack contract marker"),
):
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Devil Trigger generator compatibility {label}: expected 1 source assignment, found {count}")
    source = source.replace(old, new, 1)

# John Doe is applied later and intentionally inserts its poison helpers at the
# long-standing two-argument Party damage helper signature. Keep that public
# patch-chain anchor as a tiny overload while routing to the DT-aware 3-argument
# implementation. This preserves both existing downstream patches and DT evasion.
old_helper_new = "helper_sig_new = '  private fun damageActivePartyByPercent(state: GameState, percent: Int, evadingCharacterIds: Set<String> = emptySet()): PartyPercentDamage {\\n'\n"
new_helper_new = "helper_sig_new = '''  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {\n    return damageActivePartyByPercent(state, percent, emptySet())\n  }\n\n  private fun damageActivePartyByPercent(state: GameState, percent: Int, evadingCharacterIds: Set<String>): PartyPercentDamage {\n'''\n"
count = source.count(old_helper_new)
if count != 1:
    raise RuntimeError(f"Devil Trigger John Doe helper compatibility: expected 1 helper replacement assignment, found {count}")
source = source.replace(old_helper_new, new_helper_new, 1)

# Existing Diệp Minh verification treats the two-argument pulse call as a
# compatibility marker. Keep that exact baseline marker in generated runtime
# documentation while the live call uses the new evader set overload.
old_pulse_new = "pulse_new = '''      val devilTriggerEvaders = listOfNotNull(\n"
new_pulse_new = "pulse_new = '''      // Baseline pulse compatibility: damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)\n      val devilTriggerEvaders = listOfNotNull(\n"
count = source.count(old_pulse_new)
if count != 1:
    raise RuntimeError(f"Devil Trigger Diệp Minh marker compatibility: expected 1 pulse replacement assignment, found {count}")
source = source.replace(old_pulse_new, new_pulse_new, 1)

PATCH.write_text(source, encoding="utf-8")
print("Devil Trigger generator compatibility applied: Kai catalog/stat attack and downstream Party-damage/Diệp Minh anchors synchronized.")
